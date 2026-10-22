package com.parfumecreator.presentation.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parfumecreator.core.llm.ModelManager
import com.parfumecreator.core.llm.StructuredFormulaParser
import com.parfumecreator.domain.model.*
import com.parfumecreator.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    val currentInput: String = "",
    val isStreaming: Boolean = false,
    val streamingContent: String = "",
    val activeRagSources: List<String> = emptyList(),
    val citations: List<SourceCitation> = emptyList(),
    val retrievalReport: RetrievalReport = RetrievalReport(),
    val retrievalMode: CompanionRetrievalMode = CompanionRetrievalMode.LOCAL_RAG,
    val formulaToImport: RecommendedFormula? = null,
    val activeModelId: String = "llm_gemma_2b",
    val activeModelName: String = "Gemma 4 E2B-it",
    val isLocalModelReady: Boolean = false,
    val modelReadiness: ModelReadiness = ModelReadiness.UNAVAILABLE,
    val datasetReadiness: DatasetReadiness = DatasetReadiness.SEEDING,
    val allowInternetFallback: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val getChatHistoryUseCase: GetChatHistoryUseCase,
    private val getChatSessionsUseCase: GetChatSessionsUseCase,
    private val createChatSessionUseCase: CreateChatSessionUseCase,
    private val deleteChatSessionUseCase: DeleteChatSessionUseCase,
    private val deleteAllChatSessionsUseCase: DeleteAllChatSessionsUseCase,
    private val updateChatSessionTitleUseCase: UpdateChatSessionTitleUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val modelManager: ModelManager,
    private val liteRtLmRunner: com.parfumecreator.core.llm.LiteRtLmRunner,
    private val getDatasetReadinessUseCase: GetDatasetReadinessUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentSystemPrompt: String? = null
    private var messageObservationJob: Job? = null
    private var sendJob: Job? = null

    init {
        // Observe settings for configured system prompt and active model
        viewModelScope.launch {
            getSettingsUseCase().collect { settings ->
                currentSystemPrompt = settings.systemPrompt
                modelManager.setActiveModelId(settings.activeModelId)
                settings.activeModelPath?.let { modelManager.setModelPath(it) }
                val modelName = when (settings.activeModelId) {
                    "llm_gemma_4b" -> "Gemma 4 E4B-it"
                    else -> "Gemma 4 E2B-it"
                }
                val modelReady = modelManager.isModelReady(settings.activeModelId)
                _uiState.update {
                    it.copy(
                        activeModelId = settings.activeModelId,
                        activeModelName = modelName,
                        isLocalModelReady = false,
                        modelReadiness = if (modelReady) ModelReadiness.VERIFYING else ModelReadiness.UNAVAILABLE,
                        allowInternetFallback = settings.allowInternetFallback
                    )
                }
                if (modelReady) {
                    // A checksum-valid file is only a candidate. The active
                    // badge is withheld until LiteRT can actually initialize
                    // the selected artifact on this device.
                    viewModelScope.launch {
                        val runtimeReady = liteRtLmRunner.verifyRuntimeReady(settings.activeModelId)
                        _uiState.update { state ->
                            if (state.activeModelId != settings.activeModelId) {
                                state
                            } else {
                                state.copy(
                                    isLocalModelReady = runtimeReady,
                                    modelReadiness = if (runtimeReady) {
                                        ModelReadiness.READY
                                    } else {
                                        ModelReadiness.FAILED
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            getDatasetReadinessUseCase().collect { readiness ->
                _uiState.update { it.copy(datasetReadiness = readiness) }
            }
        }

        // Load sessions and observe
        viewModelScope.launch {
            getChatSessionsUseCase().collect { sessionList ->
                _uiState.update { it.copy(sessions = sessionList) }
                val currentId = _uiState.value.currentSessionId
                if (currentId == null && sessionList.isNotEmpty()) {
                    selectSession(sessionList.first().id)
                } else if (currentId != null && sessionList.none { it.id == currentId }) {
                    if (sessionList.isNotEmpty()) {
                        selectSession(sessionList.first().id)
                    } else {
                        messageObservationJob?.cancel()
                        _uiState.update {
                            it.copy(
                                currentSessionId = null,
                                messages = emptyList(),
                                activeRagSources = emptyList(),
                                citations = emptyList(),
                                retrievalReport = RetrievalReport()
                            )
                        }
                    }
                }
            }
        }
    }

    fun selectSession(sessionId: Long) {
        _uiState.update { it.copy(currentSessionId = sessionId) }
        observeMessagesForSession(sessionId)
    }

    private fun observeMessagesForSession(sessionId: Long) {
        messageObservationJob?.cancel()
        messageObservationJob = viewModelScope.launch {
            getChatHistoryUseCase(sessionId).collect { msgList ->
                val latestSources = msgList
                    .asReversed()
                    .firstOrNull { it.role == MessageRole.ASSISTANT }
                    ?.ragSources
                    .orEmpty()
                val latestAssistant = msgList.asReversed().firstOrNull { it.role == MessageRole.ASSISTANT }
                val latestCitations = latestAssistant?.citations.orEmpty()
                _uiState.update {
                    it.copy(
                        messages = msgList,
                        activeRagSources = latestSources,
                        citations = latestCitations,
                        retrievalMode = latestAssistant?.retrievalMode
                            ?: if (latestCitations.isNotEmpty()) {
                                retrievalModeForCitations(latestCitations)
                            } else {
                                retrievalModeForLegacySources(latestSources)
                            },
                        // Message metadata is historical. It must not make a
                        // previously READY model look usable after restart;
                        // current readiness comes only from ModelManager plus
                        // LiteRT runtime verification above.
                        modelReadiness = it.modelReadiness
                    )
                }
            }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(currentInput = text) }
    }

    private fun retrievalModeForLegacySources(sources: List<String>): CompanionRetrievalMode {
        val hasInternetSource = sources.any { it.startsWith("Internet • ") }
        val hasLocalSource = sources.any { !it.startsWith("Internet • ") }
        return when {
            hasInternetSource && hasLocalSource -> CompanionRetrievalMode.LOCAL_AND_WEB
            hasInternetSource -> CompanionRetrievalMode.WEB_FALLBACK
            else -> CompanionRetrievalMode.LOCAL_RAG
        }
    }

    private fun retrievalModeForCitations(citations: List<SourceCitation>): CompanionRetrievalMode {
        val hasInternetSource = citations.any { it.sourceType == SourceType.WEB }
        val hasLocalSource = citations.any { it.sourceType != SourceType.WEB }
        return when {
            hasInternetSource && hasLocalSource -> CompanionRetrievalMode.LOCAL_AND_WEB
            hasInternetSource -> CompanionRetrievalMode.WEB_FALLBACK
            else -> CompanionRetrievalMode.LOCAL_RAG
        }
    }

    fun sendMessage() {
        val input = _uiState.value.currentInput.trim()
        if (input.isBlank() || _uiState.value.isStreaming) return

        _uiState.update {
            it.copy(
                currentInput = "",
                isStreaming = true,
                streamingContent = "",
                errorMessage = null
            )
        }

        sendJob = viewModelScope.launch {
            try {
                // Ensure session exists or create on demand from first message
                val sessionId = _uiState.value.currentSessionId ?: run {
                    val initialTitle = input.take(38).replace("\n", " ").trim().ifBlank { "Konsultasi Parfum" }
                    val newId = createChatSessionUseCase(initialTitle)
                    _uiState.update { it.copy(currentSessionId = newId) }
                    observeMessagesForSession(newId)
                    newId
                }

                // Auto-update default session title using first user message
                val currentSession = _uiState.value.sessions.find { it.id == sessionId }
                if (currentSession != null && (currentSession.title == "Konsultasi Parfum" || currentSession.title == "Konsultasi Baru" || currentSession.title == "New Consultation")) {
                    val dynamicTitle = input.take(38).replace("\n", " ").trim()
                    if (dynamicTitle.isNotBlank()) {
                        updateChatSessionTitleUseCase(sessionId, dynamicTitle)
                    }
                }

                val activeSettings = getSettingsUseCase().firstOrNull()
                activeSettings?.let { settings ->
                    modelManager.setActiveModelId(settings.activeModelId)
                    settings.activeModelPath?.let { modelManager.setModelPath(it) }
                    val ready = modelManager.isModelReady(settings.activeModelId)
                    _uiState.update {
                        it.copy(
                            isLocalModelReady = false,
                            modelReadiness = if (ready) ModelReadiness.VERIFYING else ModelReadiness.UNAVAILABLE
                        )
                    }
                }
                val activePrompt = activeSettings?.systemPrompt ?: currentSystemPrompt
                val turnResult = sendChatMessageUseCase.execute(
                    sessionId = sessionId,
                    userMessage = input,
                    systemPrompt = activePrompt,
                    conversationId = sessionId
                )

                val ragSources = turnResult.citations.map { it.title }.distinct()
                val retrievalMode = retrievalModeForCitations(turnResult.citations)
                _uiState.update {
                    it.copy(
                        activeRagSources = ragSources,
                        citations = turnResult.citations,
                        retrievalReport = turnResult.retrieval,
                        retrievalMode = retrievalMode,
                        modelReadiness = turnResult.modelStatus
                    )
                }

                val fullResponse = StringBuilder()
                turnResult.stream.collect { token ->
                    fullResponse.append(token)
                    _uiState.update { it.copy(streamingContent = fullResponse.toString()) }
                }

                // Persist the completed assistant message
                val finalContent = fullResponse.toString()
                if (finalContent.isNotBlank()) {
                    sendChatMessageUseCase.saveAssistantMessage(
                        sessionId = sessionId,
                        content = finalContent,
                        ragSources = ragSources,
                        citations = turnResult.citations,
                        retrievalMode = retrievalMode,
                        modelStatus = turnResult.modelStatus
                    )
                }

                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        streamingContent = ""
                    )
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                _uiState.update {
                    it.copy(isStreaming = false, streamingContent = "")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isStreaming = false,
                        streamingContent = "",
                        errorMessage = "Gagal memproses pesan: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun createNewSession() {
        // Reset to fresh draft mode (ChatGPT / Gemini style)
        messageObservationJob?.cancel()
        _uiState.update {
            it.copy(
                currentSessionId = null,
                messages = emptyList(),
                activeRagSources = emptyList(),
                citations = emptyList(),
                retrievalReport = RetrievalReport(),
                retrievalMode = CompanionRetrievalMode.LOCAL_RAG,
                currentInput = "",
                streamingContent = "",
                errorMessage = null
            )
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            deleteChatSessionUseCase(sessionId)
            val remaining = _uiState.value.sessions.filter { it.id != sessionId }
            if (_uiState.value.currentSessionId == sessionId) {
                if (remaining.isNotEmpty()) {
                    selectSession(remaining.first().id)
                } else {
                    messageObservationJob?.cancel()
                    _uiState.update {
                        it.copy(
                            currentSessionId = null,
                            messages = emptyList(),
                            activeRagSources = emptyList(),
                            citations = emptyList(),
                            retrievalReport = RetrievalReport(),
                            retrievalMode = CompanionRetrievalMode.LOCAL_RAG
                        )
                    }
                }
            }
        }
    }

    fun deleteAllSessions() {
        viewModelScope.launch {
            deleteAllChatSessionsUseCase()
            messageObservationJob?.cancel()
            _uiState.update {
                it.copy(
                    sessions = emptyList(),
                    currentSessionId = null,
                    messages = emptyList(),
                    activeRagSources = emptyList(),
                    citations = emptyList(),
                    retrievalReport = RetrievalReport(),
                    retrievalMode = CompanionRetrievalMode.LOCAL_RAG
                )
            }
        }
    }

    fun setFormulaToImport(formula: RecommendedFormula?) {
        _uiState.update { it.copy(formulaToImport = formula) }
    }

    fun dismissErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun cancelGeneration() {
        sendJob?.cancel()
        sendJob = null
        _uiState.update { it.copy(isStreaming = false, streamingContent = "") }
    }
}
