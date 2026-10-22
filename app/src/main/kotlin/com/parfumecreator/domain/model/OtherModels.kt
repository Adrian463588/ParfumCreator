package com.parfumecreator.domain.model

data class RagDocument(
    val id: Long = 0,
    val fileName: String,
    val uri: String,
    val mimeType: String? = null,
    val sizeBytes: Long = 0,
    val chunkCount: Int = 0,
    val indexedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val sourceType: SourceType = SourceType.UPLOAD,
    val sourceUrl: String? = null,
    val contentHash: String? = null,
    val fetchedAt: Long? = null,
    val expiresAt: Long? = null,
    val trustLevel: TrustLevel = TrustLevel.USER_DOCUMENT,
    val availability: DocumentAvailability = DocumentAvailability.AVAILABLE
)

data class RagChunk(
    val id: Long = 0,
    val documentId: Long,
    val chunkIndex: Int,
    val text: String,
    val embedding: FloatArray,
    val pageStart: Int? = null,
    val pageEnd: Int? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RagChunk
        return id == other.id && documentId == other.documentId && chunkIndex == other.chunkIndex && text == other.text && embedding.contentEquals(other.embedding) && pageStart == other.pageStart && pageEnd == other.pageEnd
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + documentId.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + text.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + (pageStart ?: 0)
        result = 31 * result + (pageEnd ?: 0)
        return result
    }
}

data class RetrievedChunk(
    val chunk: RagChunk,
    val documentName: String,
    val score: Float,
    val sourceType: SourceType = SourceType.UPLOAD,
    val sourceUri: String? = null,
    val sourceUrl: String? = null,
    val contentHash: String? = null,
    val trustLevel: TrustLevel = TrustLevel.USER_DOCUMENT,
    val retrievedAt: Long = System.currentTimeMillis()
)

data class WebSearchHit(
    val title: String,
    val snippet: String,
    val url: String,
    val sourceName: String,
    val retrievedAt: Long = System.currentTimeMillis(),
    val pageText: String? = null,
    val contentHash: String? = null
)

enum class SourceType {
    DATASET,
    CATALOG,
    TEMPLATE,
    KNOWLEDGE,
    UPLOAD,
    WEB;

    companion object {
        fun fromStorage(value: String?): SourceType = values().firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: UPLOAD
    }
}

enum class TrustLevel {
    VERIFIED_DATASET,
    USER_DOCUMENT,
    CURATED_PUBLIC_SOURCE,
    WEB_UNTRUSTED,
    APP_CURATED_GUIDANCE;

    companion object {
        fun fromStorage(value: String?): TrustLevel = values().firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: USER_DOCUMENT
    }
}

enum class DocumentAvailability {
    AVAILABLE,
    UNAVAILABLE,
    UNKNOWN;

    companion object {
        fun fromStorage(value: String?): DocumentAvailability = values().firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: UNKNOWN
    }
}

data class SourceCitation(
    val sourceType: SourceType,
    val title: String,
    val excerpt: String,
    val score: Float? = null,
    val uri: String? = null,
    val url: String? = null,
    val retrievedAt: Long = System.currentTimeMillis(),
    val contentHash: String? = null,
    val trustLevel: TrustLevel = TrustLevel.USER_DOCUMENT,
    val pageStart: Int? = null,
    val pageEnd: Int? = null
)

enum class RetrievalStatus {
    NOT_REQUESTED,
    LOCAL_HIT,
    LOCAL_EMPTY,
    WEB_DISABLED,
    WEB_SEARCHED,
    WEB_CACHE_HIT,
    WEB_UNAVAILABLE,
    FAILED
}

enum class DatasetReadiness {
    SEEDING,
    READY,
    FAILED,
    EMPTY
}

data class RetrievalReport(
    val status: RetrievalStatus = RetrievalStatus.NOT_REQUESTED,
    val localHitCount: Int = 0,
    val webHitCount: Int = 0,
    val localBestScore: Float? = null,
    val webError: String? = null,
    val localError: String? = null
)

enum class ModelReadiness {
    SELECTED,
    DOWNLOADING,
    VERIFYING,
    READY,
    UNAVAILABLE,
    FAILED
}

data class ChatTurnResult(
    val stream: kotlinx.coroutines.flow.Flow<String>,
    val citations: List<SourceCitation> = emptyList(),
    val retrieval: RetrievalReport = RetrievalReport(),
    val modelStatus: ModelReadiness = ModelReadiness.UNAVAILABLE
)

enum class CompanionRetrievalMode(val label: String) {
    LOCAL_RAG("Local RAG"),
    LOCAL_AND_WEB("Local + Internet"),
    WEB_FALLBACK("Internet fallback")
}

data class ChatMessage(
    val id: Long = 0,
    val sessionId: Long,
    val role: MessageRole,
    val content: String,
    val ragSources: List<String> = emptyList(),
    val citations: List<SourceCitation> = emptyList(),
    val retrievalMode: CompanionRetrievalMode? = null,
    val modelStatus: ModelReadiness? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val recommendedFormula: RecommendedFormula? = null
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

data class ChatSession(
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val modelName: String? = null
)

data class RecommendedFormula(
    val name: String,
    val concentration: String? = null,
    val totalWeightG: Double? = null,
    val predictedSpl: Double? = null,
    val predictedFamily: String? = null,
    val description: String? = null,
    val ingredients: List<RecommendedIngredient> = emptyList()
)

data class RecommendedIngredient(
    val ingredientName: String,
    val amount: Double,
    val unit: String = "g",
    val noteType: String? = null,
    val resolvedIngredientId: Long? = null
)

enum class DownloadStatus {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

data class DownloadBundle(
    val id: String,
    val name: String,
    val fileName: String,
    val url: String,
    val totalBytes: Long,
    val downloadedBytes: Long = 0,
    val status: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    val destinationUri: String? = null,
    val speedBps: Long = 0,
    val etaSeconds: Long = 0,
    val description: String = "",
    val isRequired: Boolean = false
)

data class UserSettings(
    val systemPrompt: String = "Kamu adalah ParfumAI, asisten perfumer berpengalaman dengan keahlian dalam meracik parfum dari natural essential oil dan fragrance oil sintetis (Luzi, Parfarome, Macrabame). Saat merekomendasikan formula, selalu sertakan data terstruktur JSON dengan nama bahan, jumlah (gram), tipe konsentrasi, prediksi SPL, dan keluarga aroma.",
    val defaultUnit: MeasurementUnit = MeasurementUnit.GRAM,
    val safStorageUri: String? = null,
    val activeModelPath: String? = null,
    val activeModelId: String = "llm_gemma_2b",
    val allowInternetFallback: Boolean = false
)
