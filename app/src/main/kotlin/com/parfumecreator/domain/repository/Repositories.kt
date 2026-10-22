package com.parfumecreator.domain.repository

import com.parfumecreator.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.InputStream

interface IngredientRepository {
    fun getAllIngredients(): Flow<List<Ingredient>>
    fun searchIngredients(query: String, brand: String?, noteType: NoteType?, family: FragranceFamily?): Flow<List<Ingredient>>
    suspend fun getIngredientById(id: Long): Ingredient?
    suspend fun saveIngredient(ingredient: Ingredient): Long
    suspend fun deleteIngredient(ingredient: Ingredient)
    suspend fun importIngredientsFromCsv(inputStream: InputStream): Result<Int>
}

interface FormulaRepository {
    fun getAllFormulas(): Flow<List<Formula>>
    suspend fun getFormulaById(id: Long): Formula?
    suspend fun saveFormula(formula: Formula): Long
    suspend fun deleteFormula(id: Long)
}

interface RagRepository {
    fun getAllDocuments(): Flow<List<RagDocument>>
    fun getReadiness(): Flow<DatasetReadiness> = flowOf(DatasetReadiness.SEEDING)
    suspend fun indexDocument(
        uri: String,
        fileName: String,
        mimeType: String?,
        textContent: String,
        pageTexts: List<String>? = null,
        originalSizeBytes: Long? = null,
        sourceType: SourceType = SourceType.UPLOAD,
        sourceUrl: String? = null,
        contentHash: String? = null,
        trustLevel: TrustLevel = TrustLevel.USER_DOCUMENT,
        expiresAt: Long? = null
    ): Result<RagDocument>
    suspend fun deleteDocument(id: Long)
    suspend fun querySimilarChunks(query: String, topK: Int = 5): List<RetrievedChunk>
}

interface InternetKnowledgeRepository {
    suspend fun search(query: String): Result<List<WebSearchHit>>
    suspend fun fetchPage(url: String): Result<WebSearchHit>
}

interface ChatRepository {
    fun getSessions(): Flow<List<ChatSession>>
    fun getMessages(sessionId: Long): Flow<List<ChatMessage>>
    suspend fun createSession(title: String, modelName: String?): Long
    suspend fun deleteSession(sessionId: Long)
    suspend fun deleteAllSessions()
    suspend fun updateSessionTitle(sessionId: Long, title: String)
    suspend fun saveMessage(message: ChatMessage): Long
    suspend fun updateMessageContent(messageId: Long, content: String, isStreaming: Boolean)
}

interface DownloadRepository {
    fun getBundles(): Flow<List<DownloadBundle>>
    suspend fun startDownload(bundleId: String, targetFolderUri: String): Result<Unit>
    suspend fun pauseDownload(bundleId: String)
    suspend fun resumeDownload(bundleId: String)
    suspend fun cancelDownload(bundleId: String)
}

interface LlmRepository {
    fun streamGenerate(
        prompt: String,
        systemPrompt: String?,
        ragContext: List<RetrievedChunk>?,
        conversationId: Long? = null
    ): Flow<String>
    suspend fun isModelAvailable(): Boolean
    suspend fun getActiveModelPath(): String?
}

interface SettingsRepository {
    fun getSettings(): Flow<UserSettings>
    suspend fun saveSystemPrompt(prompt: String)
    suspend fun saveDefaultUnit(unit: MeasurementUnit)
    suspend fun saveSafStorageUri(uri: String)
    suspend fun saveActiveModelPath(path: String)
    suspend fun saveActiveModelId(modelId: String)
    suspend fun saveAllowInternetFallback(enabled: Boolean)
}
