package com.parfumecreator.domain.usecase

import com.parfumecreator.core.llm.CompanionRetrievalPolicy
import com.parfumecreator.core.llm.PerfumeAiGuardrail
import com.parfumecreator.core.llm.PerfumeIntent
import com.parfumecreator.core.llm.PerfumeIntentClassifier
import com.parfumecreator.core.llm.PerfumeEvidenceSelector
import com.parfumecreator.core.web.InternetQuerySanitizer
import com.parfumecreator.domain.engine.FormulaCalculationEngine
import com.parfumecreator.domain.model.*
import com.parfumecreator.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.CancellationException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject

class CalculateFormulaUseCase @Inject constructor() {
    operator fun invoke(items: List<FormulaIngredientInput>): FormulaCalculationResult {
        return FormulaCalculationEngine.calculateFormula(items)
    }
}

class SaveFormulaUseCase @Inject constructor(
    private val formulaRepository: FormulaRepository
) {
    suspend operator fun invoke(formula: Formula): Long {
        require(formula.name.isNotBlank()) { "Nama formula wajib diisi" }
        require(formula.ingredients.isNotEmpty() && formula.ingredients.all {
            it.ingredient.id > 0 && it.amount.isFinite() && it.amount > 0
        }) {
            "Formula harus memakai bahan katalog yang tersedia"
        }
        val result = FormulaCalculationEngine.calculateFormula(formula.ingredients.map {
            FormulaIngredientInput(it.ingredient, it.amount, it.unit)
        })
        require(result.validationError == null && result.totalWeightG > 0.0 && result.fragranceWeightG > 0.0) {
            result.validationError ?: "Formula harus memiliki bahan aroma dan total positif"
        }
        return formulaRepository.saveFormula(formula.copy(
            name = formula.name.trim(),
            totalWeightG = result.totalWeightG,
            concentrationPct = result.concentrationPct,
            concentrationType = result.concentrationType,
            spl = result.spl,
            splLabel = result.splLabel,
            fragranceWheelPrimary = result.fragranceWheelPrimary,
            fragranceWheelSub = result.dominantSubFamily,
            ingredients = formula.ingredients.zip(result.calculatedItems).map { (item, calculated) ->
                item.copy(amountInGram = calculated.amountInGram, percentage = calculated.percentageOfTotal)
            }
        ))
    }
}

class GetFormulasUseCase @Inject constructor(
    private val formulaRepository: FormulaRepository
) {
    operator fun invoke(): Flow<List<Formula>> = formulaRepository.getAllFormulas()
}

class GetFormulaByIdUseCase @Inject constructor(
    private val formulaRepository: FormulaRepository
) {
    suspend operator fun invoke(id: Long): Formula? = formulaRepository.getFormulaById(id)
}

class DeleteFormulaUseCase @Inject constructor(
    private val formulaRepository: FormulaRepository
) {
    suspend operator fun invoke(id: Long) = formulaRepository.deleteFormula(id)
}

class GetIngredientsUseCase @Inject constructor(
    private val ingredientRepository: IngredientRepository
) {
    operator fun invoke(): Flow<List<Ingredient>> = ingredientRepository.getAllIngredients()
}

class SearchIngredientsUseCase @Inject constructor(
    private val ingredientRepository: IngredientRepository
) {
    operator fun invoke(
        query: String,
        brand: String? = null,
        noteType: NoteType? = null,
        family: FragranceFamily? = null
    ): Flow<List<Ingredient>> = ingredientRepository.searchIngredients(query, brand, noteType, family)
}

class SaveCustomIngredientUseCase @Inject constructor(
    private val ingredientRepository: IngredientRepository
) {
    suspend operator fun invoke(ingredient: Ingredient): Long {
        require(ingredient.name.isNotBlank()) { "Nama bahan wajib diisi" }
        require(ingredient.densityGml.isFinite() && ingredient.densityGml > 0) {
            "Densitas harus berupa angka positif g/ml"
        }
        return ingredientRepository.saveIngredient(ingredient.copy(isCustom = true))
    }
}

class ImportIngredientsCsvUseCase @Inject constructor(
    private val ingredientRepository: IngredientRepository
) {
    suspend operator fun invoke(inputStream: InputStream): Result<Int> {
        return ingredientRepository.importIngredientsFromCsv(inputStream)
    }
}

class IndexDocumentUseCase @Inject constructor(
    private val ragRepository: RagRepository
) {
    suspend operator fun invoke(
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
    ): Result<RagDocument> = ragRepository.indexDocument(
        uri = uri,
        fileName = fileName,
        mimeType = mimeType,
        textContent = textContent,
        pageTexts = pageTexts,
        originalSizeBytes = originalSizeBytes,
        sourceType = sourceType,
        sourceUrl = sourceUrl,
        contentHash = contentHash,
        trustLevel = trustLevel,
        expiresAt = expiresAt
    )
}

class QueryRagUseCase @Inject constructor(
    private val ragRepository: RagRepository
) {
    suspend operator fun invoke(query: String, topK: Int = 5): List<RetrievedChunk> =
        ragRepository.querySimilarChunks(query, topK)
}

class GetDocumentsUseCase @Inject constructor(
    private val ragRepository: RagRepository
) {
    operator fun invoke(): Flow<List<RagDocument>> = ragRepository.getAllDocuments()
}

class GetDatasetReadinessUseCase @Inject constructor(
    private val ragRepository: RagRepository
) {
    operator fun invoke(): Flow<DatasetReadiness> = ragRepository.getReadiness()
}

class DeleteDocumentUseCase @Inject constructor(
    private val ragRepository: RagRepository
) {
    suspend operator fun invoke(id: Long) = ragRepository.deleteDocument(id)
}

class SendChatMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val ragRepository: RagRepository,
    private val llmRepository: LlmRepository,
    private val internetKnowledgeRepository: InternetKnowledgeRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend fun execute(
        sessionId: Long,
        userMessage: String,
        systemPrompt: String?,
        conversationId: Long? = sessionId
    ): ChatTurnResult {
        val safePrompt = PerfumeAiGuardrail.normalizePrompt(userMessage)
        val settings = try {
            settingsRepository.getSettings().firstOrNull() ?: UserSettings()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            UserSettings()
        }

        // Save the user turn before retrieval so the conversation remains
        // auditable even when a provider is unavailable.
        chatRepository.saveMessage(
            ChatMessage(
                sessionId = sessionId,
                role = MessageRole.USER,
                content = safePrompt
            )
        )

        val localResult = try {
            Result.success(ragRepository.querySimilarChunks(safePrompt, topK = 8))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
        val intent = PerfumeIntentClassifier.classify(safePrompt)
        val allRetrievedChunks = localResult.getOrElse { emptyList() }.let { chunks ->
            if (intent == PerfumeIntent.KNOWLEDGE) PerfumeEvidenceSelector.select(safePrompt, chunks) else chunks
        }
        val localEvidence = allRetrievedChunks.filter { it.sourceType != SourceType.WEB }
        // Web cache is still untrusted evidence and must never leak into a
        // request when the user has disabled internet fallback.
        val cachedWebEvidence = allRetrievedChunks
            .filter { it.sourceType == SourceType.WEB }
            .takeIf { settings.allowInternetFallback &&
                !PerfumeEvidenceSelector.hasSufficientEvidence(safePrompt, localEvidence) }
            .orEmpty()
        val localRagChunks = localEvidence + cachedWebEvidence

        val webChunks = mutableListOf<RetrievedChunk>()
        var webError: String? = null
        var fetchedPageCount = 0
        var webStatus = when {
            PerfumeIntentClassifier.classify(safePrompt) != PerfumeIntent.KNOWLEDGE -> RetrievalStatus.NOT_REQUESTED
            !settings.allowInternetFallback -> RetrievalStatus.WEB_DISABLED
            cachedWebEvidence.isNotEmpty() -> RetrievalStatus.WEB_CACHE_HIT
            else -> RetrievalStatus.LOCAL_EMPTY
        }

        val shouldSearch = CompanionRetrievalPolicy.shouldSearchInternet(
            prompt = safePrompt,
            localEvidence = localEvidence,
            allowInternetFallback = settings.allowInternetFallback
        ) && !PerfumeEvidenceSelector.hasSufficientEvidence(safePrompt, cachedWebEvidence)

        if (shouldSearch) {
            val searchResult = try {
                internetKnowledgeRepository.search(InternetQuerySanitizer.sanitize(safePrompt))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }

            searchResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            searchResult.fold(
                onSuccess = { hits ->
                    if (hits.isEmpty()) {
                        webStatus = RetrievalStatus.WEB_UNAVAILABLE
                        webError = "Provider web tidak mengembalikan hasil."
                    } else {
                        var pageFailureCount = 0
                        hits.take(MAX_WEB_PAGES).forEachIndexed { index, hit ->
                            val pageResult = try {
                                internetKnowledgeRepository.fetchPage(hit.url)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                Result.failure(error)
                            }

                            pageResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                            pageResult.fold(
                                onSuccess = { page ->
                                    val pageText = page.pageText?.trim().orEmpty()
                                    val passages = PerfumeEvidenceSelector.passages(safePrompt, pageText)
                                    if (passages.isNotEmpty()) {
                                        fetchedPageCount++
                                        webChunks += webChunk(
                                            id = -300_000L - index,
                                            hit = page,
                                            text = passages.joinToString("\n\n")
                                        )
                                        // Auto-ingest is intentionally best-effort:
                                        // it can never replace the current answer
                                        // or promote WEB_UNTRUSTED to safety data.
                                        val ingestResult = try {
                                            ragRepository.indexDocument(
                                                uri = page.url,
                                                fileName = page.title,
                                                mimeType = "text/html",
                                                textContent = pageText,
                                                originalSizeBytes = pageText.toByteArray(StandardCharsets.UTF_8).size.toLong(),
                                                sourceType = SourceType.WEB,
                                                sourceUrl = page.url,
                                                contentHash = page.contentHash ?: sha256(pageText),
                                                trustLevel = TrustLevel.WEB_UNTRUSTED,
                                                expiresAt = System.currentTimeMillis() + WEB_CACHE_TTL_MS
                                            )
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (error: Exception) {
                                            Result.failure(error)
                                        }
                                        ingestResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                                        if (ingestResult.isFailure && webError == null) {
                                            webError = "Hasil web dipakai untuk sesi ini; cache lokal gagal disimpan."
                                        }
                                    } else {
                                        pageFailureCount++
                                    }
                                },
                                onFailure = {
                                    pageFailureCount++
                                    // A search snippet is discovery metadata,
                                    // not a fetched source. Do not answer from
                                    // it when the bounded page fetch failed.
                                }
                            )
                        }
                        webStatus = if (fetchedPageCount > 0) {
                            RetrievalStatus.WEB_SEARCHED
                        } else {
                            RetrievalStatus.WEB_UNAVAILABLE
                        }
                        if (pageFailureCount > 0) {
                            webError = "${pageFailureCount} halaman web tidak dapat diambil; jawaban web tidak menggunakan cuplikan discovery."
                        }
                    }
                },
                onFailure = { error ->
                    webStatus = RetrievalStatus.WEB_UNAVAILABLE
                    webError = error.message?.take(180) ?: "Provider web tidak tersedia."
                }
            )
        }

        val ragChunks = localRagChunks + webChunks
        val citations = ragChunks
            .map(::toCitation)
            .distinctBy { it.url ?: it.uri ?: it.title }
        val modelStatus = try {
            if (llmRepository.isModelAvailable()) ModelReadiness.READY else ModelReadiness.UNAVAILABLE
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ModelReadiness.FAILED
        }
        val retrievalStatus = when {
            webChunks.isNotEmpty() -> webStatus
            shouldSearch && webStatus == RetrievalStatus.WEB_UNAVAILABLE -> RetrievalStatus.WEB_UNAVAILABLE
            cachedWebEvidence.isNotEmpty() -> RetrievalStatus.WEB_CACHE_HIT
            localEvidence.isNotEmpty() -> RetrievalStatus.LOCAL_HIT
            localResult.isFailure -> RetrievalStatus.FAILED
            else -> webStatus.takeIf { it != RetrievalStatus.LOCAL_EMPTY } ?: RetrievalStatus.LOCAL_EMPTY
        }

        val stream = llmRepository.streamGenerate(
            prompt = safePrompt,
            systemPrompt = systemPrompt,
            ragContext = ragChunks.ifEmpty { null },
            conversationId = conversationId
        )

        return ChatTurnResult(
            stream = stream,
            citations = citations,
            retrieval = RetrievalReport(
                status = retrievalStatus,
                localHitCount = localEvidence.size,
                webHitCount = webChunks.size + cachedWebEvidence.size,
                localBestScore = localEvidence.maxOfOrNull { it.score },
                webError = webError,
                localError = localResult.exceptionOrNull()?.message
            ),
            modelStatus = modelStatus
        )
    }

    suspend fun saveAssistantMessage(
        sessionId: Long,
        content: String,
        ragSources: List<String> = emptyList(),
        citations: List<SourceCitation> = emptyList(),
        retrievalMode: CompanionRetrievalMode? = null,
        modelStatus: ModelReadiness? = null
    ): Long {
        val parsedFormula = com.parfumecreator.core.llm.StructuredFormulaParser.parseJsonFormula(content)
        return chatRepository.saveMessage(
            ChatMessage(
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = content,
                ragSources = ragSources.ifEmpty { citations.map { it.title }.distinct() },
                citations = citations,
                retrievalMode = retrievalMode,
                modelStatus = modelStatus,
                recommendedFormula = parsedFormula
            )
        )
    }

    private fun toCitation(retrieved: RetrievedChunk): SourceCitation = SourceCitation(
        sourceType = retrieved.sourceType,
        title = retrieved.documentName,
        excerpt = retrieved.chunk.text.replace(Regex("\\s+"), " ").trim().take(MAX_CITATION_EXCERPT_CHARS),
        score = retrieved.score,
        uri = retrieved.sourceUri?.takeUnless { it.startsWith("https://") },
        url = retrieved.sourceUrl ?: retrieved.sourceUri?.takeIf { it.startsWith("https://") },
        retrievedAt = retrieved.retrievedAt,
        contentHash = retrieved.contentHash,
        trustLevel = retrieved.trustLevel,
        pageStart = retrieved.chunk.pageStart,
        pageEnd = retrieved.chunk.pageEnd
    )

    private fun webChunk(id: Long, hit: WebSearchHit, text: String): RetrievedChunk = RetrievedChunk(
        chunk = RagChunk(
            id = id,
            documentId = id - 100_000L,
            chunkIndex = 0,
            text = text.take(MAX_WEB_CONTEXT_CHARS),
            embedding = FloatArray(0)
        ),
        documentName = "${CompanionRetrievalPolicy.INTERNET_SOURCE_PREFIX}${hit.sourceName.removePrefix(CompanionRetrievalPolicy.INTERNET_SOURCE_PREFIX)}: ${hit.title}",
        score = 0.56f,
        sourceType = SourceType.WEB,
        sourceUri = hit.url,
        sourceUrl = hit.url,
        contentHash = hit.contentHash ?: sha256(text),
        trustLevel = TrustLevel.WEB_UNTRUSTED,
        retrievedAt = hit.retrievedAt
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val MAX_WEB_PAGES = 3
        const val MAX_WEB_CONTEXT_CHARS = 100_000
        const val MAX_CITATION_EXCERPT_CHARS = 360
        const val WEB_CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1_000L
    }
}

class GetChatHistoryUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(sessionId: Long): Flow<List<ChatMessage>> = chatRepository.getMessages(sessionId)
}

class GetChatSessionsUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(): Flow<List<ChatSession>> = chatRepository.getSessions()
}

class CreateChatSessionUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(title: String = "Konsultasi Parfum", modelName: String? = null): Long =
        chatRepository.createSession(title, modelName)
}

class DeleteChatSessionUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(sessionId: Long) = chatRepository.deleteSession(sessionId)
}

class DeleteAllChatSessionsUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke() = chatRepository.deleteAllSessions()
}

class UpdateChatSessionTitleUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(sessionId: Long, title: String) = chatRepository.updateSessionTitle(sessionId, title)
}

class GetDownloadsUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    operator fun invoke(): Flow<List<DownloadBundle>> = downloadRepository.getBundles()
}

class StartDownloadUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    suspend operator fun invoke(bundleId: String, targetFolderUri: String): Result<Unit> =
        downloadRepository.startDownload(bundleId, targetFolderUri)
}

class PauseDownloadUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    suspend operator fun invoke(bundleId: String) = downloadRepository.pauseDownload(bundleId)
}

class ResumeDownloadUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    suspend operator fun invoke(bundleId: String) = downloadRepository.resumeDownload(bundleId)
}

class CancelDownloadUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    suspend operator fun invoke(bundleId: String) = downloadRepository.cancelDownload(bundleId)
}

class GetSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(): Flow<UserSettings> = settingsRepository.getSettings()
}

class SaveSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(systemPrompt: String, defaultUnit: MeasurementUnit) {
        settingsRepository.saveSystemPrompt(systemPrompt)
        settingsRepository.saveDefaultUnit(defaultUnit)
    }
}

class SaveSafStorageUriUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(uri: String) {
        settingsRepository.saveSafStorageUri(uri)
    }
}

class SaveActiveModelPathUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(path: String) {
        settingsRepository.saveActiveModelPath(path)
    }
}

class SaveActiveModelIdUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(modelId: String) {
        settingsRepository.saveActiveModelId(modelId)
    }
}

class SaveAllowInternetFallbackUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        settingsRepository.saveAllowInternetFallback(enabled)
    }
}
