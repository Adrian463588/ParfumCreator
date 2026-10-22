package com.parfumecreator.domain

import com.parfumecreator.domain.model.*
import com.parfumecreator.domain.repository.ChatRepository
import com.parfumecreator.domain.repository.InternetKnowledgeRepository
import com.parfumecreator.domain.repository.LlmRepository
import com.parfumecreator.domain.repository.RagRepository
import com.parfumecreator.domain.repository.SettingsRepository
import com.parfumecreator.domain.usecase.SendChatMessageUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakeChatRepository : ChatRepository {
    val savedMessages = mutableListOf<ChatMessage>()
    val sessions = mutableListOf<ChatSession>()
    private var nextId = 1L

    override fun getSessions(): Flow<List<ChatSession>> = flowOf(sessions)
    override fun getMessages(sessionId: Long): Flow<List<ChatMessage>> = flowOf(savedMessages.filter { it.sessionId == sessionId })

    override suspend fun createSession(title: String, modelName: String?): Long {
        val id = nextId++
        sessions.add(ChatSession(id = id, title = title, modelName = modelName))
        return id
    }

    override suspend fun deleteSession(sessionId: Long) {
        sessions.removeAll { it.id == sessionId }
        savedMessages.removeAll { it.sessionId == sessionId }
    }

    override suspend fun deleteAllSessions() {
        sessions.clear()
        savedMessages.clear()
    }

    override suspend fun updateSessionTitle(sessionId: Long, title: String) {
        val idx = sessions.indexOfFirst { it.id == sessionId }
        if (idx >= 0) {
            sessions[idx] = sessions[idx].copy(title = title, updatedAt = System.currentTimeMillis())
        }
    }

    override suspend fun saveMessage(message: ChatMessage): Long {
        val id = if (message.id == 0L) nextId++ else message.id
        savedMessages.add(message.copy(id = id))
        return id
    }

    override suspend fun updateMessageContent(messageId: Long, content: String, isStreaming: Boolean) {
        val idx = savedMessages.indexOfFirst { it.id == messageId }
        if (idx >= 0) {
            savedMessages[idx] = savedMessages[idx].copy(content = content, isStreaming = isStreaming)
        }
    }
}

class FakeRagRepository : RagRepository {
    var chunksOverride: List<RetrievedChunk>? = null
    val indexedWeb = mutableListOf<RagDocument>()
    override fun getAllDocuments(): Flow<List<RagDocument>> = flowOf(emptyList())
    override suspend fun indexDocument(
        uri: String,
        fileName: String,
        mimeType: String?,
        textContent: String,
        pageTexts: List<String>?,
        originalSizeBytes: Long?,
        sourceType: SourceType,
        sourceUrl: String?,
        contentHash: String?,
        trustLevel: TrustLevel,
        expiresAt: Long?
    ): Result<RagDocument> {
        val doc = RagDocument(id = 1, fileName = fileName, uri = uri, sourceType = sourceType,
            sourceUrl = sourceUrl, trustLevel = trustLevel, expiresAt = expiresAt, contentHash = contentHash)
        if (sourceType == SourceType.WEB) indexedWeb += doc
        return Result.success(doc)
    }
    override suspend fun deleteDocument(id: Long) {}
    override suspend fun querySimilarChunks(query: String, topK: Int): List<RetrievedChunk> {
        chunksOverride?.let { return it }
        return if (query.contains("bergamot", ignoreCase = true)) {
            listOf(
                RetrievedChunk(
                    chunk = RagChunk(id = 1, documentId = 1, chunkIndex = 0, text = "Bergamot is citrus top note", embedding = floatArrayOf()),
                    documentName = "citrus_notes.pdf",
                    score = 0.85f
                )
            )
        } else emptyList()
    }
}

class FakeLlmRepository : LlmRepository {
    var lastConversationId: Long? = null
    var lastRagContext: List<RetrievedChunk>? = null

    override fun streamGenerate(
        prompt: String,
        systemPrompt: String?,
        ragContext: List<RetrievedChunk>?,
        conversationId: Long?
    ): Flow<String> {
        lastConversationId = conversationId
        lastRagContext = ragContext
        return flowOf("Token1 ", "Token2 ", "Token3")
    }

    override suspend fun isModelAvailable(): Boolean = true
    override suspend fun getActiveModelPath(): String? = "/path/to/model"
}

class FakeInternetKnowledgeRepository : InternetKnowledgeRepository {
    var queryCount = 0
    var failPageFetch = false
    var lastQuery: String? = null
    var pageText = "Perfume is a mixture of fragrant materials, fixatives, and solvents."
    var cancellationAsResult = false
    override suspend fun search(query: String): Result<List<WebSearchHit>> {
        queryCount++
        lastQuery = query
        if (cancellationAsResult) return Result.failure(kotlinx.coroutines.CancellationException("cancelled"))
        return Result.success(listOf(hit))
    }

    override suspend fun fetchPage(url: String): Result<WebSearchHit> = if (failPageFetch) {
        Result.failure(IllegalStateException("page fetch timeout"))
    } else {
        Result.success(hit.copy(pageText = pageText))
    }

    private val hit = WebSearchHit(
        title = "Perfume",
        snippet = "A mixture of fragrant materials, fixatives, and solvents.",
        url = "https://en.wikipedia.org/wiki/Perfume",
        sourceName = "Internet • Wikipedia"
    )
}

class FakeChatSettingsRepository : SettingsRepository {
    private var settings = UserSettings()

    override fun getSettings(): Flow<UserSettings> = flowOf(settings)
    override suspend fun saveSystemPrompt(prompt: String) { settings = settings.copy(systemPrompt = prompt) }
    override suspend fun saveDefaultUnit(unit: MeasurementUnit) { settings = settings.copy(defaultUnit = unit) }
    override suspend fun saveSafStorageUri(uri: String) { settings = settings.copy(safStorageUri = uri) }
    override suspend fun saveActiveModelPath(path: String) { settings = settings.copy(activeModelPath = path) }
    override suspend fun saveActiveModelId(modelId: String) { settings = settings.copy(activeModelId = modelId) }
    override suspend fun saveAllowInternetFallback(enabled: Boolean) {
        settings = settings.copy(allowInternetFallback = enabled)
    }
}

class SendChatMessageUseCaseTest {

    private lateinit var chatRepo: FakeChatRepository
    private lateinit var ragRepo: FakeRagRepository
    private lateinit var llmRepo: FakeLlmRepository
    private lateinit var internetRepo: FakeInternetKnowledgeRepository
    private lateinit var settingsRepo: FakeChatSettingsRepository
    private lateinit var useCase: SendChatMessageUseCase

    @Before
    fun setup() {
        chatRepo = FakeChatRepository()
        ragRepo = FakeRagRepository()
        llmRepo = FakeLlmRepository()
        internetRepo = FakeInternetKnowledgeRepository()
        settingsRepo = FakeChatSettingsRepository()
        useCase = SendChatMessageUseCase(chatRepo, ragRepo, llmRepo, internetRepo, settingsRepo)
    }

    @Test
    fun testExecute_savesUserMessage_andQueriesRag() = runTest {
        val result = useCase.execute(
            sessionId = 100L,
            userMessage = "How to use bergamot?",
            systemPrompt = "System Prompt"
        )

        // Verify user message was saved
        assertEquals(1, chatRepo.savedMessages.size)
        val userMsg = chatRepo.savedMessages[0]
        assertEquals(100L, userMsg.sessionId)
        assertEquals(MessageRole.USER, userMsg.role)
        assertEquals("How to use bergamot?", userMsg.content)

        // Verify RAG retrieved source
        assertEquals(1, result.citations.size)
        assertEquals("citrus_notes.pdf", result.citations[0].title)
        assertEquals(100L, llmRepo.lastConversationId)

        // Verify stream emission
        val tokens = result.stream.toList()
        assertEquals(listOf("Token1 ", "Token2 ", "Token3"), tokens)
        assertEquals(0, internetRepo.queryCount)
    }

    @Test
    fun testExecute_usesInternetOnlyWhenLocalEvidenceIsInsufficient() = runTest {
        settingsRepo.saveAllowInternetFallback(true)
        val result = useCase.execute(
            sessionId = 101L,
            userMessage = "apa itu parfum?",
            systemPrompt = "System Prompt"
        )

        result.stream.toList()

        assertEquals(1, internetRepo.queryCount)
        assertTrue(result.citations.any { it.sourceType == SourceType.WEB && it.url != null })
        assertTrue(llmRepo.lastRagContext.orEmpty().any { it.sourceType == SourceType.WEB })
    }

    @Test
    fun testExecute_doesNotCallInternetWhenConsentIsOff() = runTest {
        val result = useCase.execute(
            sessionId = 102L,
            userMessage = "apa itu parfum?",
            systemPrompt = "System Prompt"
        )

        result.stream.toList()

        assertEquals(0, internetRepo.queryCount)
        assertEquals(RetrievalStatus.WEB_DISABLED, result.retrieval.status)
        assertTrue(result.citations.isEmpty())
    }

    @Test
    fun testExecute_reportsWebUnavailableWhenPageFetchFails() = runTest {
        settingsRepo.saveAllowInternetFallback(true)
        internetRepo.failPageFetch = true

        val result = useCase.execute(
            sessionId = 103L,
            userMessage = "apa itu parfum?",
            systemPrompt = "System Prompt"
        )

        result.stream.toList()

        assertEquals(1, internetRepo.queryCount)
        assertEquals(RetrievalStatus.WEB_UNAVAILABLE, result.retrieval.status)
        assertTrue(result.retrieval.webError?.contains("tidak dapat diambil") == true)
        assertTrue(result.citations.isEmpty())
        assertFalse(llmRepo.lastRagContext.orEmpty().any { it.sourceType == SourceType.WEB })
    }

    @Test
    fun uploadAnswerOfflineRetainsPageCitationAndAvoidsInternet() = runTest {
        ragRepo.chunksOverride = listOf(RetrievedChunk(
            RagChunk(7, 2, 0, "Durasi maceration parfum adalah 14 hari.", FloatArray(0), 3, 3),
            "uji-parfum.pdf", 0.20f, sourceUri = "content://documents/2"
        ))
        val result = useCase.execute(200, "Berapa durasi maserasi parfum menurut dokumen?", null)
        assertEquals(RetrievalStatus.LOCAL_HIT, result.retrieval.status)
        assertEquals(3, result.citations.single().pageStart)
        assertEquals("content://documents/2", result.citations.single().uri)
        assertTrue(llmRepo.lastRagContext.orEmpty().single().chunk.text.contains("14 hari"))
        assertEquals(0, internetRepo.queryCount)
    }

    @Test
    fun genericLocalHitDoesNotSuppressSilentWebAndLatePassageIsUsed() = runTest {
        settingsRepo.saveAllowInternetFallback(true)
        ragRepo.chunksOverride = listOf(RetrievedChunk(
            RagChunk(1, 1, 0, "Parfum citrus bergamot fresh oil.", FloatArray(0)), "catalog", 0.99f,
            sourceType = SourceType.CATALOG
        ))
        internetRepo.pageText = "Navigation archive contact. ".repeat(200) +
            "Ambroxan synthesis uses sclareol as a starting material."
        val result = useCase.execute(201, "Jelaskan ambroxan synthesis parfum", null)
        assertEquals(1, internetRepo.queryCount)
        assertEquals(RetrievalStatus.WEB_SEARCHED, result.retrieval.status)
        assertTrue(llmRepo.lastRagContext.orEmpty().single().chunk.text.contains("sclareol"))
        assertEquals(TrustLevel.WEB_UNTRUSTED, result.citations.single().trustLevel)
        assertTrue(result.citations.single().contentHash?.length == 64)
        assertNotNull(ragRepo.indexedWeb.single().expiresAt)
    }

    @Test
    fun unrelatedFetchedPageNeverBecomesEvidence() = runTest {
        settingsRepo.saveAllowInternetFallback(true)
        internetRepo.pageText = "Contact menu and corporate information."
        val result = useCase.execute(202, "jelaskan ambroxan synthesis parfum", null)
        assertEquals(RetrievalStatus.WEB_UNAVAILABLE, result.retrieval.status)
        assertTrue(result.citations.isEmpty())
        assertTrue(ragRepo.indexedWeb.isEmpty())
    }

    @Test
    fun disabledInternetExcludesCachedWebAndSanitizesOutboundOnly() = runTest {
        ragRepo.chunksOverride = listOf(RetrievedChunk(
            RagChunk(1, 1, 0, "Ambroxan synthesis uses sclareol.", FloatArray(0)), "cached", 0.9f,
            sourceType = SourceType.WEB
        ))
        val disabled = useCase.execute(203, "ambroxan synthesis parfum", null)
        assertTrue(disabled.citations.isEmpty())
        assertEquals(0, internetRepo.queryCount)
        settingsRepo.saveAllowInternetFallback(true)
        ragRepo.chunksOverride = emptyList()
        useCase.execute(204, "parfum user@example.com https://private.example", null)
        assertFalse(internetRepo.lastQuery.orEmpty().contains("@"))
        assertFalse(internetRepo.lastQuery.orEmpty().contains("https://"))
        assertTrue(chatRepo.savedMessages.last().content.contains("user@example.com"))
    }

    @Test
    fun cancellationWrappedByProviderStillPropagates() = runTest {
        settingsRepo.saveAllowInternetFallback(true)
        internetRepo.cancellationAsResult = true
        try {
            useCase.execute(205, "apa itu parfum", null)
            fail("Cancellation must propagate")
        } catch (_: kotlinx.coroutines.CancellationException) {
            assertTrue(ragRepo.indexedWeb.isEmpty())
        }
    }

    @Test
    fun weakCachedWebDoesNotSuppressAFreshLookup() = runTest {
        settingsRepo.saveAllowInternetFallback(true)
        ragRepo.chunksOverride = listOf(RetrievedChunk(
            RagChunk(1, 1, 0, "Ambroxan synthesis notes.", FloatArray(0)), "old cache", 0.99f,
            sourceType = SourceType.WEB
        ))
        internetRepo.pageText = "Ambroxan synthesis uses sclareol."
        val result = useCase.execute(206, "Jelaskan ambroxan synthesis sclareol parfum", null)
        assertEquals(1, internetRepo.queryCount)
        assertEquals(RetrievalStatus.WEB_SEARCHED, result.retrieval.status)
    }

    @Test
    fun sufficientCachedWebAvoidsAnotherNetworkRequest() = runTest {
        settingsRepo.saveAllowInternetFallback(true)
        ragRepo.chunksOverride = listOf(RetrievedChunk(
            RagChunk(1, 1, 0, "Ambroxan synthesis uses sclareol.", FloatArray(0)), "fresh cache", 0.9f,
            sourceType = SourceType.WEB, sourceUrl = "https://en.wikipedia.org/wiki/Ambroxide"
        ))
        val result = useCase.execute(207, "Jelaskan ambroxan synthesis parfum", null)
        assertEquals(0, internetRepo.queryCount)
        assertEquals(RetrievalStatus.WEB_CACHE_HIT, result.retrieval.status)
    }

    @Test
    fun testSaveAssistantMessage_parsesFormulaAndPersists() = runTest {
        val assistantContent = """
            Berikut formula:
            ```json
            {
              "name": "Test Cologne",
              "concentration": "EDC",
              "totalWeightG": 5.0,
              "predictedSpl": 30.0,
              "predictedFamily": "CITRUS",
              "ingredients": [
                { "ingredientName": "Bergamot", "amount": 5.0, "unit": "g", "noteType": "TOP", "resolvedIngredientId": 1 }
              ]
            }
            ```
        """.trimIndent()

        val msgId = useCase.saveAssistantMessage(
            sessionId = 100L,
            content = assistantContent,
            ragSources = listOf("citrus_notes.pdf")
        )

        assertTrue(msgId > 0)
        assertEquals(1, chatRepo.savedMessages.size)
        val savedAssistant = chatRepo.savedMessages[0]
        assertEquals(MessageRole.ASSISTANT, savedAssistant.role)
        assertEquals(100L, savedAssistant.sessionId)
        assertEquals(listOf("citrus_notes.pdf"), savedAssistant.ragSources)
        assertNotNull(savedAssistant.recommendedFormula)
        assertEquals("Test Cologne", savedAssistant.recommendedFormula?.name)
        assertEquals("EDC", savedAssistant.recommendedFormula?.concentration)
        assertEquals(1, savedAssistant.recommendedFormula?.ingredients?.size)
    }
}
