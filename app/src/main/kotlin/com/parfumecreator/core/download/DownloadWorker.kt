package com.parfumecreator.core.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.parfumecreator.BuildConfig
import com.parfumecreator.R
import com.parfumecreator.data.local.dao.DownloadDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DownloadWorkerEntryPoint {
    fun downloadDao(): DownloadDao
}

/**
 * Resumable downloader for model files selected through Android's Storage Access Framework.
 *
 * The worker writes only inside the user-selected tree. A completed model is promoted
 * from the temporary document after the byte count is verified; no app-private fallback
 * or arbitrary filesystem path is used.
 */
class DownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val downloadDao: DownloadDao by lazy {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            DownloadWorkerEntryPoint::class.java
        )
        entryPoint.downloadDao()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val bundleId = inputData.getString(KEY_BUNDLE_ID) ?: return@withContext Result.failure()
        val urlStr = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext Result.failure()
        val destinationFolder = inputData.getString(KEY_DEST_FOLDER)
            ?.takeIf { it.startsWith("content://") }
            ?: return@withContext Result.failure()
        val expectedInputHash = inputData.getString(KEY_EXPECTED_SHA256)

        val artifact = ModelArtifactRegistry.forId(bundleId)
            ?: return@withContext Result.failure()
        if (fileName != artifact.fileName ||
            urlStr != artifact.pinnedUrl ||
            !ModelArtifactRegistry.isAllowedDownloadUrl(urlStr) ||
            (expectedInputHash != null && !expectedInputHash.equals(artifact.sha256, ignoreCase = true))
        ) {
            downloadDao.updateStatus(bundleId, "FAILED", null)
            return@withContext Result.failure()
        }

        val treeUri = runCatching { Uri.parse(destinationFolder) }.getOrNull()
            ?: return@withContext Result.failure()
        val tree = DocumentFile.fromTreeUri(applicationContext, treeUri)
            ?.takeIf { it.canWrite() }
            ?: return@withContext Result.failure()

        val bundle = downloadDao.getDownloadById(bundleId)
            ?: return@withContext Result.failure()
        val finalDocument = tree.findFile(fileName)
        if (
            bundle.status == "COMPLETED" &&
            finalDocument?.isFile == true &&
            finalDocument.length() == artifact.expectedBytes &&
            sha256Document(finalDocument, artifact.expectedBytes) == artifact.sha256
        ) {
            downloadDao.updateStatus(bundleId, "COMPLETED", finalDocument.uri.toString())
            return@withContext Result.success()
        }

        var temporaryDocument = tree.findFile("$fileName.tmp")
            ?: tree.createFile("application/octet-stream", "$fileName.tmp")
            ?: return@withContext Result.failure()

        var downloadedBytes = temporaryDocument.length().coerceAtLeast(0L)
        if (downloadedBytes > artifact.expectedBytes) {
            if (!temporaryDocument.delete()) {
                downloadDao.updateStatus(bundleId, "FAILED", null)
                return@withContext Result.failure()
            }
            temporaryDocument = tree.createFile("application/octet-stream", "$fileName.tmp")
                ?: run {
                    downloadDao.updateStatus(bundleId, "FAILED", null)
                    return@withContext Result.failure()
                }
            downloadedBytes = 0L
        }

        // A process stop can happen after the last byte is written but before
        // the rename. Promote that verified temp file without issuing a
        // Range request that would otherwise receive HTTP 416.
        if (
            downloadedBytes == artifact.expectedBytes &&
            sha256Document(temporaryDocument, artifact.expectedBytes) == artifact.sha256
        ) {
            val existingFinal = tree.findFile(fileName)
            if (existingFinal != null) {
                if (isVerifiedModel(existingFinal, artifact)) {
                    // Keep the already verified final artifact intact.
                    temporaryDocument.delete()
                    downloadDao.updateProgress(bundleId, artifact.expectedBytes, "COMPLETED")
                    downloadDao.updateStatus(bundleId, "COMPLETED", existingFinal.uri.toString())
                    return@withContext Result.success()
                }
                if (!existingFinal.delete()) {
                    downloadDao.updateStatus(bundleId, "FAILED", null)
                    return@withContext Result.failure()
                }
            }
            if (!temporaryDocument.renameTo(fileName)) {
                downloadDao.updateStatus(bundleId, "FAILED", null)
                return@withContext Result.failure()
            }
            val promoted = tree.findFile(fileName)
                ?.takeIf { it.isFile && it.length() == artifact.expectedBytes }
                ?: run {
                    downloadDao.updateStatus(bundleId, "FAILED", null)
                    return@withContext Result.failure()
                }
            downloadDao.updateProgress(bundleId, artifact.expectedBytes, "COMPLETED")
            downloadDao.updateStatus(bundleId, "COMPLETED", promoted.uri.toString())
            return@withContext Result.success()
        }
        if (downloadedBytes == artifact.expectedBytes) {
            // Never send Range: bytes=<EOF>- for a corrupt temp file. That
            // commonly produces HTTP 416 and used to surface as a terminal
            // failure instead of allowing a clean restart.
            if (!temporaryDocument.delete()) {
                downloadDao.updateStatus(bundleId, "FAILED", null)
                return@withContext Result.failure()
            }
            temporaryDocument = tree.createFile("application/octet-stream", "$fileName.tmp")
                ?: run {
                    downloadDao.updateStatus(bundleId, "FAILED", null)
                    return@withContext Result.failure()
                }
            downloadedBytes = 0L
        }

        downloadDao.updateStatus(bundleId, "DOWNLOADING", destinationFolder)
        downloadDao.updateProgress(bundleId, downloadedBytes, "DOWNLOADING")

        var connection: HttpURLConnection? = null
        val notificationId = NOTIFICATION_ID_BASE + (bundleId.hashCode() and 0x0FFF)
        try {
            setForeground(createForegroundInfo(bundleId, downloadedBytes, artifact.expectedBytes, notificationId))

            var currentUrl = urlStr
            var redirects = 0
            var lastProgressUpdate = System.currentTimeMillis()
            val requestedOffset = downloadedBytes

            while (redirects < MAX_REDIRECTS) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "ParfumeCreator/${BuildConfig.VERSION_NAME} (Android; Mobile)")
                    setRequestProperty("Accept", "*/*")
                    setRequestProperty("Accept-Encoding", "identity")
                    if (downloadedBytes > 0L) {
                        setRequestProperty("Range", "bytes=$downloadedBytes-")
                    }
                }

                val responseCode = connection.responseCode
                Log.d(TAG, "HTTP status $responseCode for model download")

                if (responseCode in REDIRECT_CODES) {
                    val newLocation = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (newLocation.isNullOrBlank()) break
                    val resolvedUrl = if (
                        newLocation.startsWith("http://") ||
                        newLocation.startsWith("https://")
                    ) {
                        newLocation
                    } else {
                        URL(URL(currentUrl), newLocation).toString()
                    }
                    if (!ModelArtifactRegistry.isAllowedDownloadUrl(resolvedUrl)) {
                        throw PermanentDownloadException("Redirect download host tidak diizinkan")
                    }
                    currentUrl = resolvedUrl
                    redirects++
                    continue
                }
                break
            }

            if (redirects >= MAX_REDIRECTS && connection?.responseCode?.let { it in REDIRECT_CODES } == true) {
                throw PermanentDownloadException("Terlalu banyak redirect saat mengunduh model")
            }

            val activeConnection = connection
                ?: throw IllegalStateException("Tidak dapat membuat koneksi download")
            val responseCode = activeConnection.responseCode
            val isPartial = responseCode == HttpURLConnection.HTTP_PARTIAL
            val isFull = responseCode == HttpURLConnection.HTTP_OK
            if (!isPartial && !isFull) {
                activeConnection.disconnect()
                if (responseCode in RETRYABLE_HTTP_CODES) {
                    throw RetryableDownloadException("Provider sementara tidak tersedia (HTTP $responseCode)")
                }
                throw PermanentDownloadException("Provider menolak download (HTTP $responseCode)")
            }

            val appendMode = isPartial && downloadedBytes > 0L
            val responseBaseOffset = if (isPartial) downloadedBytes else 0L
            if (isPartial) {
                val contentRange = parseContentRange(activeConnection.getHeaderField("Content-Range"))
                val isValidRange = contentRange != null &&
                    contentRange.start == requestedOffset &&
                    contentRange.end >= contentRange.start &&
                    contentRange.total == artifact.expectedBytes
                if (!isValidRange) {
                    activeConnection.disconnect()
                    if (!temporaryDocument.delete()) {
                        throw PermanentDownloadException("Partial file tidak konsisten dan tidak dapat direset")
                    }
                    throw RetryableDownloadException("Provider mengembalikan Content-Range yang tidak cocok; partial file direset")
                }
                val validContentRange = contentRange
                    ?: throw PermanentDownloadException("Content-Range model tidak valid")
                val responseLength = activeConnection.contentLengthLong
                if (responseLength >= 0L && responseLength != validContentRange.end - validContentRange.start + 1L) {
                    activeConnection.disconnect()
                    if (!temporaryDocument.delete()) {
                        throw PermanentDownloadException("Partial file tidak konsisten dan tidak dapat direset")
                    }
                    throw RetryableDownloadException("Ukuran respons partial tidak cocok dengan Content-Range")
                }
            }
            if (!appendMode) {
                downloadedBytes = 0L
            }
            val responseLength = activeConnection.contentLengthLong.coerceAtLeast(0L)
            val expectedTotal = artifact.expectedBytes
            if (responseLength > 0L && responseBaseOffset + responseLength > expectedTotal) {
                activeConnection.disconnect()
                throw PermanentDownloadException("Response download melebihi ukuran artefak")
            }
            if (activeConnection.contentType.orEmpty().lowercase().contains("text/html")) {
                activeConnection.disconnect()
                throw PermanentDownloadException("Provider mengembalikan HTML, bukan model")
            }

            val input = activeConnection.inputStream
            val output = openDocumentOutput(temporaryDocument.uri, appendMode)
                ?: throw PermanentDownloadException("Folder SAF tidak dapat ditulis")

            input.use { source ->
                output.use { destination ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val bytesRead = source.read(buffer)
                        if (bytesRead == -1) break

                        if (isStopped) {
                            downloadDao.updateStatus(bundleId, "PAUSED", destinationFolder)
                            activeConnection.disconnect()
                            return@withContext Result.success()
                        }

                        destination.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (downloadedBytes > expectedTotal) {
                            throw PermanentDownloadException("Download melebihi ukuran artefak")
                        }

                        if (System.currentTimeMillis() - lastProgressUpdate > PROGRESS_INTERVAL_MS) {
                            lastProgressUpdate = System.currentTimeMillis()
                            downloadDao.updateProgress(bundleId, downloadedBytes, "DOWNLOADING")
                            setProgress(
                                workDataOf(
                                    PROGRESS_BYTES to downloadedBytes,
                                    TOTAL_BYTES to expectedTotal
                                )
                            )
                        }
                    }
                }
            }
            activeConnection.disconnect()

            if (downloadedBytes != expectedTotal) {
                throw RetryableDownloadException(
                    "Koneksi berhenti sebelum model selesai ($downloadedBytes/$expectedTotal byte)"
                )
            }

            val completeSize = temporaryDocument.length()
            if (completeSize != expectedTotal) {
                throw RetryableDownloadException(
                    "Provider SAF belum menulis ukuran model yang diharapkan ($completeSize/$expectedTotal byte)"
                )
            }

            if (sha256Document(temporaryDocument, expectedTotal) != artifact.sha256) {
                if (!temporaryDocument.delete()) {
                    throw PermanentDownloadException("Checksum salah dan partial file tidak dapat direset")
                }
                throw RetryableDownloadException("Checksum model tidak cocok; partial file direset")
            }

            val currentFinal = tree.findFile(fileName)
            if (currentFinal != null) {
                if (isVerifiedModel(currentFinal, artifact)) {
                    // Do not delete a good model merely because the Room row
                    // was stale or the temp file was produced twice.
                    temporaryDocument.delete()
                    downloadDao.updateProgress(bundleId, completeSize, "COMPLETED")
                    downloadDao.updateStatus(bundleId, "COMPLETED", currentFinal.uri.toString())
                    return@withContext Result.success()
                }
                if (!currentFinal.delete()) {
                    downloadDao.updateStatus(bundleId, "FAILED", null)
                    return@withContext Result.failure()
                }
            }
            if (!temporaryDocument.renameTo(fileName)) {
                downloadDao.updateStatus(bundleId, "FAILED", null)
                return@withContext Result.failure()
            }

            val promotedDocument = tree.findFile(fileName)
                ?.takeIf { it.isFile && it.length() >= completeSize }
                ?: run {
                    downloadDao.updateStatus(bundleId, "FAILED", null)
                    return@withContext Result.failure()
                }

            downloadDao.updateProgress(bundleId, completeSize, "COMPLETED")
            downloadDao.updateStatus(bundleId, "COMPLETED", promotedDocument.uri.toString())
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Download error", error)
            val retryable = isRetryable(error) && runAttemptCount < MAX_RETRY_ATTEMPTS - 1
            if (retryable) {
                val persistedBytes = runCatching { temporaryDocument.length() }
                    .getOrDefault(downloadedBytes)
                    .coerceIn(0L, artifact.expectedBytes)
                downloadDao.updateProgress(bundleId, persistedBytes, "DOWNLOADING")
                downloadDao.updateStatus(bundleId, "DOWNLOADING", destinationFolder)
                Log.w(
                    TAG,
                    "Transient model download failure; retry ${runAttemptCount + 1}/$MAX_RETRY_ATTEMPTS"
                )
                Result.retry()
            } else {
                val persistedBytes = runCatching { temporaryDocument.length() }
                    .getOrDefault(downloadedBytes)
                    .coerceIn(0L, artifact.expectedBytes)
                downloadDao.updateProgress(bundleId, persistedBytes, "FAILED")
                downloadDao.updateStatus(bundleId, "FAILED", null)
                Result.failure(
                    workDataOf(KEY_ERROR to (error.message ?: "Download model gagal"))
                )
            }
        } finally {
            // Covers stream/open/write failures as well as cancellation and
            // keeps a redirect connection from surviving the worker.
            connection?.disconnect()
            notificationManager().cancel(notificationId)
        }
    }

    private fun openDocumentOutput(uri: Uri, append: Boolean): OutputStream? {
        val mode = if (append) "wa" else "rwt"
        val descriptor = runCatching {
            applicationContext.contentResolver.openFileDescriptor(uri, mode)
        }.getOrNull()
        if (descriptor != null) {
            return ParcelFileDescriptor.AutoCloseOutputStream(descriptor)
        }
        return applicationContext.contentResolver.openOutputStream(
            uri,
            if (append) "wa" else "wt"
        )
    }

    private fun parseContentRange(value: String?): ContentRange? {
        val match = CONTENT_RANGE_PATTERN.matchEntire(value?.trim().orEmpty()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].toLongOrNull() ?: return null
        return ContentRange(start, end, total)
    }

    private fun isRetryable(error: Throwable): Boolean {
        if (error is PermanentDownloadException) return false
        return error is RetryableDownloadException ||
            error is SocketTimeoutException ||
            error is ConnectException ||
            error is UnknownHostException ||
            error is IOException
    }

    private fun createForegroundInfo(
        bundleId: String,
        downloadedBytes: Long,
        expectedBytes: Long,
        notificationId: Int
    ): ForegroundInfo = ForegroundInfo(
        notificationId,
        createNotification(bundleId, downloadedBytes, expectedBytes)
    )

    private fun createNotification(
        bundleId: String,
        downloadedBytes: Long,
        expectedBytes: Long
    ): android.app.Notification {
        notificationManager()
        return NotificationCompat.Builder(applicationContext, DOWNLOAD_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Mengunduh model AI")
        .setContentText(bundleId)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(
            100,
            ((downloadedBytes.toDouble() / expectedBytes.toDouble()) * 100.0)
                .toInt()
                .coerceIn(0, 100),
            false
        )
        .build()
    }

    private fun notificationManager(): NotificationManager =
        applicationContext.getSystemService(NotificationManager::class.java).also { manager ->
            if (manager.getNotificationChannel(DOWNLOAD_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        DOWNLOAD_CHANNEL_ID,
                        "Download model AI",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }

    private data class ContentRange(val start: Long, val end: Long, val total: Long)

    private class RetryableDownloadException(message: String) : IOException(message)

    private class PermanentDownloadException(message: String) : IOException(message)

    private fun sha256Document(document: DocumentFile, expectedBytes: Long): String? {
        if (!document.isFile || document.length() != expectedBytes) return null
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            applicationContext.contentResolver.openInputStream(document.uri)?.use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > expectedBytes) return@runCatching null
                    digest.update(buffer, 0, read)
                }
                if (total != expectedBytes) return@runCatching null
            } ?: return null
            digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }.getOrNull()
    }

    private fun isVerifiedModel(document: DocumentFile, artifact: ModelArtifact): Boolean =
        document.isFile &&
            document.length() == artifact.expectedBytes &&
            sha256Document(document, artifact.expectedBytes) == artifact.sha256

    companion object {
        private const val TAG = "DownloadWorker"
        private const val BUFFER_SIZE = 32 * 1024
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val MAX_REDIRECTS = 10
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val PROGRESS_INTERVAL_MS = 300L
        private const val DOWNLOAD_CHANNEL_ID = "model_downloads"
        private const val NOTIFICATION_ID_BASE = 7_300
        private const val KEY_ERROR = "error"
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private val RETRYABLE_HTTP_CODES = setOf(408, 425, 429, 500, 502, 503, 504)
        private val CONTENT_RANGE_PATTERN = Regex("""bytes (\d+)-(\d+)/(\d+)""", RegexOption.IGNORE_CASE)

        const val KEY_BUNDLE_ID = "bundle_id"
        const val KEY_URL = "url"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_DEST_FOLDER = "dest_folder"
        const val KEY_EXPECTED_SHA256 = "expected_sha256"
        const val PROGRESS_BYTES = "progress_bytes"
        const val TOTAL_BYTES = "total_bytes"
    }
}
