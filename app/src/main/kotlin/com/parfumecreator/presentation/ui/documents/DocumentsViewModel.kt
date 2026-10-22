package com.parfumecreator.presentation.ui.documents

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.documentfile.provider.DocumentFile
import com.parfumecreator.core.document.DocumentProcessingLimits
import com.parfumecreator.core.document.PdfTextExtractor
import com.parfumecreator.domain.model.RagDocument
import com.parfumecreator.domain.model.SourceType
import com.parfumecreator.domain.model.TrustLevel
import com.parfumecreator.domain.model.DocumentAvailability
import com.parfumecreator.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.util.Log
import javax.inject.Inject

data class DocumentsUiState(
    val documents: List<RagDocument> = emptyList(),
    val isIndexing: Boolean = false,
    val indexingFileName: String? = null,
    val userMessage: String? = null
)

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val getDocumentsUseCase: GetDocumentsUseCase,
    private val indexDocumentUseCase: IndexDocumentUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase,
    private val pdfTextExtractor: PdfTextExtractor
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentsUiState())
    val uiState: StateFlow<DocumentsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getDocumentsUseCase().collect { docList ->
                val documents = withContext(Dispatchers.IO) { docList.map(::refreshAvailability) }
                _uiState.update { it.copy(documents = documents) }
            }
        }
    }

    fun indexDocumentFromUri(uri: Uri) {
        if (_uiState.value.isIndexing) return
        _uiState.update { it.copy(isIndexing = true) }
        viewModelScope.launch {
            try {
                val (resolvedFileName, mimeType, sizeBytes) = withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document.txt"
                    Triple(name, resolver.getType(uri), resolveSizeBytes(uri))
                }
                require(DocumentProcessingLimits.isSupportedMimeOrName(mimeType, resolvedFileName)) {
                    "Format tidak didukung. Gunakan PDF, TXT, MD, CSV, atau JSON."
                }
                require(sizeBytes == null || sizeBytes in 1L..DocumentProcessingLimits.MAX_DOCUMENT_BYTES) {
                    "Dokumen harus berukuran 1-${DocumentProcessingLimits.MAX_DOCUMENT_BYTES / (1024L * 1024L)} MB."
                }
                _uiState.update { it.copy(indexingFileName = resolvedFileName) }
                val extractedResult = if (
                    mimeType == "application/pdf" ||
                    resolvedFileName.endsWith(".pdf", ignoreCase = true)
                ) {
                    pdfTextExtractor.extractDocumentFromUri(uri)
                } else {
                    pdfTextExtractor.extractDocumentFromTxtUri(uri)
                }
                val extracted = extractedResult.getOrElse { error ->
                    if (error is CancellationException) throw error
                    throw IllegalArgumentException(error.localizedMessage ?: "Dokumen tidak dapat diekstrak", error)
                }
                val textContent = extracted.text

                if (textContent.isNotBlank()) {
                    val result = indexDocumentUseCase(
                        uri = uri.toString(),
                        fileName = resolvedFileName,
                        mimeType = mimeType,
                        textContent = textContent,
                        pageTexts = extracted.pageTexts,
                        originalSizeBytes = sizeBytes,
                        sourceType = SourceType.UPLOAD,
                        trustLevel = TrustLevel.USER_DOCUMENT
                    )
                    result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                    _uiState.update {
                        it.copy(
                            isIndexing = false,
                            indexingFileName = null,
                            userMessage = result.fold(
                                onSuccess = { doc ->
                                    val sizeKb = (doc.sizeBytes / 1024L).coerceAtLeast(1L)
                                    "Berhasil mengindeks '${doc.fileName}' (${doc.chunkCount} chunks, $sizeKb KB)!"
                                },
                                onFailure = { err -> "Gagal mengindeks: ${err.localizedMessage}" }
                            )
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isIndexing = false,
                            indexingFileName = null,
                            userMessage = "Dokumen kosong atau teks tidak dapat diekstraksi."
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isIndexing = false,
                        indexingFileName = null,
                        userMessage = "Terjadi kesalahan saat memproses dokumen: ${e.localizedMessage}"
                    )
                }
            } finally {
                _uiState.update { it.copy(isIndexing = false, indexingFileName = null) }
            }
        }
    }

    fun rejectDocumentPermission() {
        _uiState.update { it.copy(userMessage = "Dokumen tidak memberi izin baca permanen. Pilih melalui penyimpanan lain.") }
    }

    private fun resolveSizeBytes(uri: Uri): Long? {
        val queriedSize = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)) else -1L
            }
        }.getOrNull()?.takeIf { it > 0L }
        return queriedSize ?: DocumentFile.fromSingleUri(context, uri)?.length()?.takeIf { it > 0L }
    }

    private fun refreshAvailability(document: RagDocument): RagDocument {
        if (!document.uri.startsWith("content://")) return document
        val available = runCatching {
            DocumentFile.fromSingleUri(context, Uri.parse(document.uri))?.let { file ->
                file.isFile && file.canRead()
            } == true
        }.getOrDefault(false)
        return document.copy(
            availability = if (available) DocumentAvailability.AVAILABLE else DocumentAvailability.UNAVAILABLE
        )
    }

    /**
     * Seeds standard perfume formulation reference into RAG database.
     */
    fun indexBuiltInGuide(type: String) {
        if (_uiState.value.isIndexing) return
        val (fileName, content) = when (type) {
            "ifra" -> Pair(
                "Referensi_IFRA_dan_Safety.txt",
                """
                REFERENSI KESELAMATAN PARFUM — SUMBER PRIMER

                Dokumen ini hanya membantu navigasi sumber. Ia bukan tabel batas IFRA dan tidak menggantikan standar, SDS, atau penilaian supplier.

                1. Untuk produk parfum yang diaplikasikan ke kulit, tentukan kategori produk dan gunakan versi IFRA Standards yang berlaku pada tanggal pemeriksaan.
                2. Periksa sertifikat IFRA, SDS, alergen, spesifikasi bahan, dan status bahan dari supplier. Batas bergantung pada bahan, kategori produk, dan amendment; jangan menyalin angka dari ringkasan lama.
                3. Informasi regulator harus diverifikasi pada sumber resmi sebelum formula diproduksi atau dipakai pada kulit. Keberadaan bahan di katalog atau database kosmetik bukan bukti bahwa formula tertentu aman.
                4. Uji blotter dan sampel terkontrol tetap diperlukan. SPL di aplikasi hanyalah indeks kalkulasi, bukan klaim performa atau keamanan.

                Sumber primer untuk verifikasi IFRA:
                https://ifrafragrance.org/initiatives-positions/safe-use-fragrance-science/ifra-standards

                Sumber regulator tambahan:
                https://www.fda.gov/cosmetics/cosmetic-ingredients/fragrances-cosmetics
                https://single-market-economy.ec.europa.eu/sectors/cosmetics/cosmetic-ingredient-database_en
                """.trimIndent()
            )
            else -> Pair(
                "Formulasi_Accords_Perfumer_Guide.txt",
                """
                PANDUAN MENYUSUN ACCORD PARFUM — PANDUAN APLIKASI

                Dokumen ini tidak berisi resep angka atau klaim keselamatan. Ia hanya menjelaskan alur kerja untuk memakai katalog lokal secara terukur.

                1. Tentukan arah olfaktif dan pilih family dari metadata katalog.
                2. Pilih bahan dengan ID katalog nyata dan kelompokkan sebagai Top, Heart/Middle, atau Base.
                3. Minta ParfumAI menghitung formula baru dengan ukuran batch dan konsentrasi yang jelas.
                4. Tinjau total gram, density, sumber bahan, SDS, kategori produk, dan hasil blotter sebelum membuat batch.
                5. Bahan atau rasio yang hanya disebut oleh internet tidak otomatis dapat menjadi ingredient formula.

                Catatan: contoh accord dari sumber luar harus diunggah pengguna sebagai dokumen dan tetap diperlakukan sebagai referensi tidak tepercaya sampai diverifikasi.
                """.trimIndent()
            )
        }

        _uiState.update {
            it.copy(isIndexing = true, indexingFileName = fileName)
        }

        viewModelScope.launch {
            val result = try {
                indexDocumentUseCase(
                    uri = "asset://embedded/$fileName",
                    fileName = fileName,
                    mimeType = "text/plain",
                    textContent = content,
                    sourceType = SourceType.KNOWLEDGE,
                    trustLevel = if (type == "ifra") {
                        TrustLevel.CURATED_PUBLIC_SOURCE
                    } else {
                        TrustLevel.APP_CURATED_GUIDANCE
                    },
                    sourceUrl = if (type == "ifra") {
                        "https://ifrafragrance.org/initiatives-positions/safe-use-fragrance-science/ifra-standards"
                    } else null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            _uiState.update {
                it.copy(
                    isIndexing = false,
                    indexingFileName = null,
                    userMessage = result.fold(
                        onSuccess = { doc -> "Berhasil mengindeks '${doc.fileName}' (${doc.chunkCount} chunks) ke Knowledge Base!" },
                        onFailure = { err -> "Gagal mengindeks: ${err.localizedMessage}" }
                    )
                )
            }
        }
    }

    fun deleteDocument(id: Long) {
        viewModelScope.launch {
            deleteDocumentUseCase(id)
            _uiState.update { it.copy(userMessage = "Dokumen dan embedding berhasil dihapus.") }
        }
    }

    fun dismissUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private companion object {
        const val TAG = "DocumentsViewModel"
    }
}
