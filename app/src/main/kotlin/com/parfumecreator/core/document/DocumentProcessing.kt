package com.parfumecreator.core.document

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DocumentProcessing"

private const val MAX_DOCUMENT_BYTES = 10L * 1024L * 1024L
private const val MAX_PDF_PAGES = 100
private const val MAX_EXTRACTED_CHARS = 1_000_000

/** Limits shared by SAF validation and the local RAG indexer. */
object DocumentProcessingLimits {
    const val MAX_DOCUMENT_BYTES: Long = 10L * 1024L * 1024L
    const val MAX_PDF_PAGES: Int = 100
    const val MAX_EXTRACTED_CHARS: Int = 1_000_000

    fun isSupportedMimeOrName(mimeType: String?, fileName: String): Boolean {
        val mime = mimeType.orEmpty().lowercase()
        val name = fileName.trim().lowercase()
        val supportedExtension = listOf(".pdf", ".txt", ".md", ".csv", ".json")
            .any(name::endsWith)
        return mime == "application/pdf" ||
            mime == "text/plain" ||
            mime.startsWith("text/") ||
            mime == "application/json" ||
            supportedExtension
    }
}

data class ExtractedDocument(
    val text: String,
    val pageTexts: List<String>? = null
)

@Singleton
class PdfTextExtractor @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    init {
        try {
            PDFBoxResourceLoader.init(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PDFBoxResourceLoader: ${e.message}", e)
        }
    }

    /**
     * Extracts plain text from a PDF URI using PDFBox with cached file fallback.
     */
    suspend fun extractDocumentFromUri(uri: Uri): Result<ExtractedDocument> = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            // 1. Copy stream to cache file for stable random access reading
            tempFile = File.createTempFile("rag_pdf_", ".pdf", context.cacheDir)
            val copiedBytes = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    copyBounded(input, output, MAX_DOCUMENT_BYTES)
                }
            } ?: throw IllegalArgumentException("URI PDF tidak dapat dibaca atau akses SAF sudah dicabut")

            require(copiedBytes > 0L) { "PDF kosong" }

            PDDocument.load(tempFile).use { document ->
                require(document.numberOfPages in 1..MAX_PDF_PAGES) {
                    "PDF harus memiliki 1-$MAX_PDF_PAGES halaman"
                }
                if (document.isEncrypted) {
                    try {
                        document.setAllSecurityToBeRemoved(true)
                    } catch (secEx: Exception) {
                        throw IllegalArgumentException("PDF terenkripsi tidak dapat dibuka", secEx)
                    }
                }
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                }
                val pageTexts = buildList {
                    for (page in 1..document.numberOfPages) {
                        stripper.startPage = page
                        stripper.endPage = page
                        add(cleanExtractedText(stripper.getText(document)))
                    }
                }
                val text = pageTexts.joinToString("\n\n").trim()
                require(text.isNotBlank()) { "PDF tidak memiliki teks yang dapat diekstrak" }
                require(text.length <= MAX_EXTRACTED_CHARS) {
                    "Teks PDF melebihi batas $MAX_EXTRACTED_CHARS karakter"
                }
                Result.success(ExtractedDocument(text = text, pageTexts = pageTexts))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from PDF document", e)
            Result.failure(e)
        } finally {
            tempFile?.delete()
        }
    }

    /** Backwards-compatible text-only API for callers that do not need page citations. */
    suspend fun extractTextFromUri(uri: Uri): String = extractDocumentFromUri(uri).getOrNull()?.text.orEmpty()

    /**
     * Extracts plain text from TXT/MD/CSV/JSON URI with UTF-8 / multi-charset support.
     */
    suspend fun extractDocumentFromTxtUri(uri: Uri): Result<ExtractedDocument> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = readBounded(stream, MAX_DOCUMENT_BYTES)
                require(bytes.isNotEmpty()) { "Dokumen teks kosong" }
                // Strip UTF-8 BOM if present
                val cleanBytes = if (bytes.size >= 3 &&
                    bytes[0] == 0xEF.toByte() &&
                    bytes[1] == 0xBB.toByte() &&
                    bytes[2] == 0xBF.toByte()
                ) {
                    bytes.copyOfRange(3, bytes.size)
                } else {
                    bytes
                }

                val text = decodeText(cleanBytes)
                val cleanText = cleanExtractedText(text)
                require(cleanText.isNotBlank()) { "Dokumen teks kosong" }
                require(cleanText.length <= MAX_EXTRACTED_CHARS) {
                    "Teks dokumen melebihi batas $MAX_EXTRACTED_CHARS karakter"
                }
                Result.success(ExtractedDocument(text = cleanText))
            } ?: Result.failure(IllegalArgumentException("URI dokumen tidak dapat dibaca atau akses SAF sudah dicabut"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from text document", e)
            Result.failure(e)
        }
    }

    /** Backwards-compatible text-only API for callers that do not need extraction diagnostics. */
    suspend fun extractTextFromTxtUri(uri: Uri): String = extractDocumentFromTxtUri(uri).getOrNull()?.text.orEmpty()

    private fun decodeText(bytes: ByteArray): String {
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, StandardCharsets.ISO_8859_1)
        }
    }

    private fun copyBounded(input: InputStream, output: java.io.OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            if (total > maxBytes) error("Dokumen melebihi batas ukuran ${maxBytes / (1024 * 1024)} MB")
            output.write(buffer, 0, count)
        }
        return total
    }

    private fun readBounded(input: InputStream, maxBytes: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            if (total > maxBytes) error("Dokumen melebihi batas ukuran ${maxBytes / (1024 * 1024)} MB")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun cleanExtractedText(raw: String): String {
        return raw
            // Fix hyphenated line breaks e.g. "per-\nfume" -> "perfume"
            .replace(Regex("([\\p{L}\\p{N}]+)-\\r?\\n([\\p{L}\\p{N}]+)"), "$1$2")
            // Normalize CRLF to LF
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            // Normalize excessive blank lines
            .replace(Regex("\\n{3,}"), "\n\n")
            // Replace weird control characters (preserve tabs and newlines)
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), " ")
            // Trim leading/trailing whitespace
            .trim()
    }
}

/**
 * Best-practice Recursive Sentence-Boundary Aware Chunker for RAG.
 * Keeps semantic integrity by prioritizing paragraph -> sentence -> word boundaries.
 */
object TextChunker {

    private const val DEFAULT_CHUNK_WORD_SIZE = 512
    private const val DEFAULT_OVERLAP_WORD_SIZE = 50
    private const val DEFAULT_MAX_CHARS = 4_000

    fun chunkText(
        text: String,
        chunkSize: Int = DEFAULT_CHUNK_WORD_SIZE,
        overlap: Int = DEFAULT_OVERLAP_WORD_SIZE,
        maxChars: Int = DEFAULT_MAX_CHARS
    ): List<String> {
        require(chunkSize > 0) { "chunkSize harus lebih besar dari nol" }
        require(overlap >= 0 && overlap < chunkSize) { "overlap harus berada di antara nol dan chunkSize" }
        require(maxChars > 0) { "maxChars harus lebih besar dari nol" }

        val trimmed = text.trim()
        if (trimmed.isBlank()) return emptyList()

        // Bound both word count and character count. A single URL, hash, or
        // malformed token must never create an oversized chunk.
        val words = trimmed.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .flatMap { splitOversizedToken(it, maxChars) }
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < words.size) {
            var end = start
            var charCount = 0
            while (end < words.size && end - start < chunkSize) {
                val nextLength = charCount + words[end].length + if (charCount > 0) 1 else 0
                if (charCount > 0 && nextLength > maxChars) break
                charCount = nextLength
                end++
            }
            if (end == start) end = start + 1
            val chunk = words.subList(start, end).joinToString(" ").trim()
            if (chunk.isNotBlank() && chunks.lastOrNull() != chunk) chunks.add(chunk)
            if (end == words.size) break
            val overlapCount = minOf(overlap, (end - start - 1).coerceAtLeast(0))
            start = (end - overlapCount).coerceAtLeast(start + 1)
        }
        return chunks
    }

    private fun splitOversizedToken(token: String, maxChars: Int): List<String> {
        if (token.length <= maxChars) return listOf(token)
        val parts = mutableListOf<String>()
        var offset = 0
        while (offset < token.length) {
            var end = minOf(offset + maxChars, token.length)
            // Bound UTF-16 length without cutting a surrogate pair. This
            // keeps emoji/CJK text valid while still honoring maxChars.
            if (end < token.length && end > offset && Character.isHighSurrogate(token[end - 1])) {
                end--
            }
            if (end <= offset) end = minOf(offset + 1, token.length)
            parts.add(token.substring(offset, end))
            offset = end
        }
        return parts
    }
}
