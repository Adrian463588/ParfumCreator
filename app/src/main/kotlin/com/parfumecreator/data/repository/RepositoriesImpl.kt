package com.parfumecreator.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.*
import androidx.documentfile.provider.DocumentFile
import com.parfumecreator.core.download.DownloadWorker
import com.parfumecreator.core.download.ModelArtifactRegistry
import com.parfumecreator.core.embedding.CosineSimilarity
import com.parfumecreator.core.embedding.TfLiteEmbeddingService
import com.parfumecreator.core.formula.FormulaTemplateLoader
import com.parfumecreator.core.llm.LiteRtLmRunner
import com.parfumecreator.core.llm.ModelManager
import com.parfumecreator.core.llm.PerfumeTextNormalizer
import com.parfumecreator.core.llm.PerfumeEvidenceSelector
import com.parfumecreator.core.llm.PerfumeryKnowledgeLoader
import com.parfumecreator.core.web.WikimediaInternetClient
import com.parfumecreator.data.local.dao.*
import com.parfumecreator.data.local.database.*
import com.parfumecreator.domain.model.*
import com.parfumecreator.domain.repository.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "Repositories"

@Singleton
class InternetKnowledgeRepositoryImpl @Inject constructor(
    private val wikimediaInternetClient: WikimediaInternetClient
) : InternetKnowledgeRepository {
    override suspend fun search(query: String): Result<List<WebSearchHit>> =
        wikimediaInternetClient.search(query)

    override suspend fun fetchPage(url: String): Result<WebSearchHit> =
        wikimediaInternetClient.fetchPage(url)
}

@Singleton
class IngredientRepositoryImpl @Inject constructor(
    private val ingredientDao: IngredientDao
) : IngredientRepository {

    override fun getAllIngredients(): Flow<List<Ingredient>> =
        ingredientDao.getAllIngredients().map { list -> list.map { it.toDomain() }.filter { it.name.isNotBlank() } }

    override fun searchIngredients(
        query: String,
        brand: String?,
        noteType: NoteType?,
        family: FragranceFamily?
    ): Flow<List<Ingredient>> =
        ingredientDao.searchIngredients(
            query = query.trim(),
            brand = brand,
            noteType = noteType?.name,
            family = family?.name
        ).map { list -> list.map { it.toDomain() }.filter { it.name.isNotBlank() } }

    override suspend fun getIngredientById(id: Long): Ingredient? = withContext(Dispatchers.IO) {
        ingredientDao.getIngredientById(id)?.toDomain()?.takeIf { it.name.isNotBlank() }
    }

    override suspend fun saveIngredient(ingredient: Ingredient): Long = withContext(Dispatchers.IO) {
        ingredientDao.insertIngredient(IngredientEntity.fromDomain(ingredient))
    }

    override suspend fun deleteIngredient(ingredient: Ingredient) = withContext(Dispatchers.IO) {
        ingredientDao.deleteIngredient(IngredientEntity.fromDomain(ingredient))
    }

    override suspend fun importIngredientsFromCsv(inputStream: InputStream): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(inputStream))
            var count = 0
            val entities = mutableListOf<IngredientEntity>()

            reader.useLines { lines ->
                lines.drop(1).forEach { line ->
                    val tokens = line.split(",")
                    if (tokens.isNotEmpty() && tokens[0].isNotBlank()) {
                        val name = tokens[0].trim().removeSurrounding("\"")
                        val type = if (tokens.size > 1 && tokens[1].contains("SYNTHETIC", true)) "SYNTHETIC" else "NATURAL"
                        val note = if (tokens.size > 2) tokens[2].trim() else "MIDDLE"
                        val family = if (tokens.size > 3) tokens[3].trim() else "FRESH"
                        val density = if (tokens.size > 4) tokens[4].toDoubleOrNull() ?: 1.0 else 1.0

                        val cleanedName = IngredientNameCleaner.clean(name)
                        entities.add(
                            IngredientEntity(
                                name = cleanedName,
                                type = type,
                                noteType = note,
                                fragranceFamily = family,
                                densityGml = density,
                                isCustom = true
                            )
                        )
                        count++
                    }
                }
            }

            if (entities.isNotEmpty()) {
                ingredientDao.insertAllIngredients(entities)
            }
            Result.success(count)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Singleton
class FormulaRepositoryImpl @Inject constructor(
    private val formulaDao: FormulaDao
) : FormulaRepository {

    override fun getAllFormulas(): Flow<List<Formula>> =
        formulaDao.getAllFormulasWithIngredients().map { list ->
            list.map { relation ->
                val entity = relation.formula
                val items = relation.formulaIngredients.map { item ->
                    FormulaIngredient(
                        id = item.link.id,
                        formulaId = item.link.formulaId,
                        ingredient = item.ingredient.toDomain(),
                        amount = item.link.amount,
                        unit = MeasurementUnit.fromString(item.link.unit),
                        amountInGram = item.link.amountInGram,
                        percentage = item.link.percentage
                    )
                }
                Formula(
                    id = entity.id,
                    name = entity.name,
                    totalWeightG = entity.totalWeightG,
                    concentrationPct = entity.concentrationPct,
                    concentrationType = ConcentrationType.fromString(entity.concentrationType),
                    spl = entity.spl,
                    splLabel = SplLabel.fromString(entity.splLabel),
                    fragranceWheelPrimary = PrimaryFragranceWheel.fromString(entity.fragranceWheelPrimary),
                    fragranceWheelSub = FragranceFamily.fromString(entity.fragranceWheelSub),
                    notes = entity.notes,
                    ingredients = items,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt
                )
            }
        }

    override suspend fun getFormulaById(id: Long): Formula? = withContext(Dispatchers.IO) {
        val relation = formulaDao.getFormulaWithIngredientsById(id) ?: return@withContext null
        val entity = relation.formula
        val items = relation.formulaIngredients.map { item ->
            FormulaIngredient(
                id = item.link.id,
                formulaId = item.link.formulaId,
                ingredient = item.ingredient.toDomain(),
                amount = item.link.amount,
                unit = MeasurementUnit.fromString(item.link.unit),
                amountInGram = item.link.amountInGram,
                percentage = item.link.percentage
            )
        }
        Formula(
            id = entity.id,
            name = entity.name,
            totalWeightG = entity.totalWeightG,
            concentrationPct = entity.concentrationPct,
            concentrationType = ConcentrationType.fromString(entity.concentrationType),
            spl = entity.spl,
            splLabel = SplLabel.fromString(entity.splLabel),
            fragranceWheelPrimary = PrimaryFragranceWheel.fromString(entity.fragranceWheelPrimary),
            fragranceWheelSub = FragranceFamily.fromString(entity.fragranceWheelSub),
            notes = entity.notes,
            ingredients = items,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    override suspend fun saveFormula(formula: Formula): Long = withContext(Dispatchers.IO) {
        val formulaEntity = FormulaEntity(
            id = formula.id,
            name = formula.name,
            totalWeightG = formula.totalWeightG,
            concentrationPct = formula.concentrationPct,
            concentrationType = formula.concentrationType.name,
            spl = formula.spl,
            splLabel = formula.splLabel.name,
            fragranceWheelPrimary = formula.fragranceWheelPrimary.name,
            fragranceWheelSub = formula.fragranceWheelSub.name,
            notes = formula.notes,
            createdAt = formula.createdAt,
            updatedAt = System.currentTimeMillis()
        )

        val linkEntities = formula.ingredients.map { item ->
            FormulaIngredientEntity(
                id = 0,
                formulaId = formula.id,
                ingredientId = item.ingredient.id,
                amount = item.amount,
                unit = item.unit.name,
                amountInGram = item.amountInGram,
                percentage = item.percentage
            )
        }

        formulaDao.saveFormulaWithIngredients(formulaEntity, linkEntities)
    }

    override suspend fun deleteFormula(id: Long) = withContext(Dispatchers.IO) {
        formulaDao.deleteFormulaById(id)
    }
}

@Singleton
class RagRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val ragDao: RagDao,
    private val embeddingService: TfLiteEmbeddingService,
    private val ingredientDao: IngredientDao,
    private val formulaTemplateLoader: FormulaTemplateLoader,
    private val knowledgeLoader: PerfumeryKnowledgeLoader,
    private val datasetReadinessStore: DatasetReadinessStore
) : RagRepository {

    private data class CatalogCandidate(
        val sourceType: SourceType,
        val sourceName: String,
        val sourceUri: String?,
        val text: String,
        val searchText: String
    )

    private data class EmbeddedCatalogCandidate(
        val candidate: CatalogCandidate,
        val embedding: FloatArray
    )

    private data class ChunkWithPage(
        val text: String,
        val pageStart: Int?,
        val pageEnd: Int?
    )

    private companion object {
        const val TEMPLATE_SOURCE = "Dataset Formula Templates"
        const val KNOWLEDGE_SOURCE = "Dataset Perfumery Knowledge"
        const val MAX_CATALOG_RESULTS = 64
        const val MAX_RETRIEVAL_RESULTS = 64
        const val MAX_CATALOG_CACHE_ENTRIES = 4_096
        const val MAX_CATALOG_CACHE_BYTES = 24L * 1024L * 1024L
        const val MAX_DOCUMENT_BYTES = 10L * 1024L * 1024L
        const val MAX_DOCUMENT_CHARS = 1_000_000
        const val EMBEDDING_DIMENSION = TfLiteEmbeddingService.EMBEDDING_DIM
    }

    private val catalogEmbeddingMutex = Mutex()
    @Volatile
    private var catalogEmbeddingCache: Pair<String, List<EmbeddedCatalogCandidate>>? = null

    override fun getAllDocuments(): Flow<List<RagDocument>> =
        ragDao.getAllDocuments().map { list -> list.map { it.toDomain() } }

    override fun getReadiness(): Flow<DatasetReadiness> = datasetReadinessStore.state

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
    ): Result<RagDocument> = withContext(Dispatchers.IO) {
        try {
            val normalizedUri = uri.trim()
            val normalizedName = fileName.trim().ifBlank { "document.txt" }
            val normalizedText = textContent.trim()
            require(normalizedUri.isNotBlank()) { "URI dokumen wajib diisi" }
            require(normalizedText.isNotBlank()) { "Dokumen kosong tidak dapat diindeks" }
            require(normalizedText.length <= MAX_DOCUMENT_CHARS) {
                "Dokumen melebihi batas teks ${MAX_DOCUMENT_CHARS} karakter"
            }
            require(
                sourceType == SourceType.WEB ||
                    com.parfumecreator.core.document.DocumentProcessingLimits.isSupportedMimeOrName(
                        mimeType,
                        normalizedName
                    )
            ) {
                "Format dokumen tidak didukung"
            }
            if (pageTexts != null) {
                require(pageTexts.isNotEmpty() && pageTexts.size <= 100) {
                    "Jumlah halaman dokumen tidak valid"
                }
                require(pageTexts.sumOf { it.length } <= MAX_DOCUMENT_CHARS) {
                    "Teks per halaman melebihi batas yang diizinkan"
                }
            }
            val encodedText = normalizedText.toByteArray(Charsets.UTF_8)
            val sizeBytes = originalSizeBytes ?: encodedText.size.toLong()
            require(sizeBytes in 1L..MAX_DOCUMENT_BYTES) {
                "Dokumen melebihi batas ukuran ${MAX_DOCUMENT_BYTES / (1024L * 1024L)} MB"
            }
            require(sourceType != SourceType.WEB || sourceUrl?.startsWith("https://") == true) {
                "Dokumen web harus memiliki URL HTTPS"
            }

            val actualHash = sha256(encodedText)
            val normalizedHash = contentHash?.trim()?.takeIf { it.isNotBlank() } ?: actualHash
            require(normalizedHash.matches(Regex("[a-fA-F0-9]{64}"))) {
                "contentHash harus berupa SHA-256"
            }
            require(normalizedHash.equals(actualHash, ignoreCase = true)) {
                "contentHash tidak cocok dengan isi dokumen"
            }
            val docEntity = RagDocumentEntity(
                fileName = normalizedName,
                uri = normalizedUri,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                chunkCount = 0,
                indexedAt = System.currentTimeMillis(),
                isActive = true,
                sourceType = sourceType.name,
                sourceUrl = sourceUrl?.trim()?.takeIf { it.isNotBlank() },
                contentHash = normalizedHash.lowercase(),
                fetchedAt = if (sourceType == SourceType.WEB) System.currentTimeMillis() else null,
                expiresAt = expiresAt,
                trustLevel = trustLevel.name,
                availability = DocumentAvailability.AVAILABLE.name
            )

            val chunks = if (pageTexts.isNullOrEmpty()) {
                com.parfumecreator.core.document.TextChunker.chunkText(
                    text = normalizedText,
                    chunkSize = 512,
                    overlap = 50
                ).map { ChunkWithPage(it, null, null) }
            } else {
                pageTexts.flatMapIndexed { pageIndex, pageText ->
                    com.parfumecreator.core.document.TextChunker.chunkText(
                        text = pageText,
                        chunkSize = 512,
                        overlap = 50
                    ).map { chunk -> ChunkWithPage(chunk, pageIndex + 1, pageIndex + 1) }
                }
            }
            require(chunks.isNotEmpty()) { "Dokumen tidak menghasilkan chunk" }

            val chunkEntities = mutableListOf<RagChunkEntity>()

            chunks.forEachIndexed { index, chunk ->
                val embeddingVec = embeddingService.embed(chunk.text)
                    ?: error("Model embedding lokal tidak tersedia; dokumen tidak diindeks")
                require(embeddingVec.size == EMBEDDING_DIMENSION && embeddingVec.all(Float::isFinite)) {
                    "Embedding dokumen tidak sesuai dimensi $EMBEDDING_DIMENSION"
                }
                val byteBuffer = ByteBuffer.allocate(embeddingVec.size * Float.SIZE_BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN)
                embeddingVec.forEach { byteBuffer.putFloat(it) }

                chunkEntities.add(
                    RagChunkEntity(
                        documentId = 0L,
                        chunkIndex = index,
                        text = chunk.text,
                        embedding = byteBuffer.array(),
                        pageStart = chunk.pageStart,
                        pageEnd = chunk.pageEnd
                    )
                )
            }

            // Complete validation and embedding before the one transaction so
            // a failed reindex keeps the previous document intact.
            Result.success(ragDao.replaceDocument(docEntity, chunkEntities).toDomain())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteDocument(id: Long) = withContext(Dispatchers.IO) {
        ragDao.deleteDocumentById(id)
    }

    override suspend fun querySimilarChunks(query: String, topK: Int): List<RetrievedChunk> = withContext(Dispatchers.IO) {
        val limit = topK.coerceIn(1, MAX_RETRIEVAL_RESULTS)
        val queryTokens = normalizedTokens(query).ifEmpty {
            // Keep a dense-search opportunity for generic questions such as
            // "apa itu parfum". Generic terms stay excluded from candidate
            // lexical scoring, so they cannot make every catalogue row a hit.
            PerfumeTextNormalizer.rawTokens(query).filterNot {
                it in setOf("apa", "itu", "yang", "adalah", "jelaskan", "tentang")
            }.toSet()
        }
        if (queryTokens.isEmpty()) return@withContext emptyList()
        val queryEmbedding = embeddingService.embed(query)
        val now = System.currentTimeMillis()

        val allActiveChunks = ragDao.getAllActiveChunks()
        val unavailableUris = allActiveChunks
            .asSequence()
            .filter { item -> item.docAvailability == DocumentAvailability.UNAVAILABLE.name }
            .map { it.docUri }
            .toMutableSet()
        allActiveChunks
            .asSequence()
            .filter { item -> item.docUri.startsWith("content://") }
            .map { it.docUri }
            .distinct()
            .forEach { documentUri ->
                val available = runCatching {
                    DocumentFile.fromSingleUri(context, Uri.parse(documentUri))?.let { file ->
                        file.isFile && file.canRead()
                    } == true
                }.getOrDefault(false)
                if (!available) unavailableUris += documentUri
            }
        if (unavailableUris.isNotEmpty()) {
            allActiveChunks
                .asSequence()
                .filter { it.docUri in unavailableUris }
                .map { it.chunk.documentId }
                .distinct()
                .forEach { documentId ->
                    runCatching {
                        ragDao.updateDocumentAvailability(
                            documentId,
                            DocumentAvailability.UNAVAILABLE.name
                        )
                    }
                }
        }

        val scoredChunks = allActiveChunks.mapNotNull { item ->
            if (item.docUri in unavailableUris) return@mapNotNull null
            if (item.docExpiresAt?.let { it <= now } == true) return@mapNotNull null
            val chunk = item.chunk
            if (chunk.embedding.isEmpty() || chunk.embedding.size % Float.SIZE_BYTES != 0) return@mapNotNull null
            val buffer = ByteBuffer.wrap(chunk.embedding).order(ByteOrder.LITTLE_ENDIAN)
            val floatArray = FloatArray(chunk.embedding.size / Float.SIZE_BYTES)
            for (i in floatArray.indices) {
                floatArray[i] = buffer.getFloat()
            }
            if (floatArray.size != EMBEDDING_DIMENSION || floatArray.any { !it.isFinite() }) {
                return@mapNotNull null
            }

            val keywordScore = lexicalScore(queryTokens, chunk.text)
            val denseScore = denseScore(queryEmbedding, floatArray)
            val hybridScore = if (queryEmbedding != null) {
                maxOf(0.70f * denseScore + 0.30f * keywordScore, 0.85f * keywordScore)
            } else {
                keywordScore
            }

            if (hybridScore >= 0.15f || keywordScore > 0.0f) {
                RetrievedChunk(
                    chunk = RagChunk(
                        id = chunk.id,
                        documentId = chunk.documentId,
                        chunkIndex = chunk.chunkIndex,
                        text = chunk.text,
                        embedding = floatArray,
                        pageStart = chunk.pageStart,
                        pageEnd = chunk.pageEnd
                    ),
                    documentName = item.docFileName,
                    score = hybridScore,
                    sourceType = SourceType.fromStorage(item.docSourceType),
                    sourceUri = item.docUri,
                    sourceUrl = item.docSourceUrl,
                    // Legacy Room rows may not have a document hash. Keep
                    // citations auditable by deriving a stable chunk hash
                    // until that document is reindexed.
                    contentHash = item.docContentHash ?: sha256(
                        chunk.text.toByteArray(Charsets.UTF_8)
                    ),
                    trustLevel = TrustLevel.fromStorage(item.docTrustLevel),
                    retrievedAt = item.docFetchedAt ?: now
                )
            } else null
        }

        val catalogCandidates = buildCatalogCandidates()
        val embeddedCatalog = getEmbeddedCatalogCandidates(catalogCandidates)
        val embeddedSourceUris = embeddedCatalog.mapNotNull { it.candidate.sourceUri }.toSet()
        val catalogResults = embeddedCatalog
            .map { candidate ->
                val keywordScore = lexicalScore(queryTokens, candidate.candidate.searchText)
                val denseScore = denseScore(queryEmbedding, candidate.embedding)
                val hybridScore = if (queryEmbedding != null) {
                    maxOf(0.70f * denseScore + 0.30f * keywordScore, 0.85f * keywordScore)
                } else {
                    keywordScore
                }
                candidate to hybridScore
            }
            .filter { (_, score) -> score > 0.0f }
            .sortedByDescending { it.second }
            .take(MAX_CATALOG_RESULTS)
            .mapIndexed { index, (embedded, score) ->
                val candidate = embedded.candidate
                RetrievedChunk(
                    chunk = RagChunk(
                        id = -100_000L - index,
                        documentId = -200_000L - index,
                        chunkIndex = index,
                        text = candidate.text,
                        embedding = embedded.embedding
                    ),
                    documentName = candidate.sourceName,
                    score = score,
                    sourceType = candidate.sourceType,
                    sourceUri = candidate.sourceUri,
                    sourceUrl = candidate.sourceUri?.takeIf { it.startsWith("https://") },
                    contentHash = sha256(candidate.text.toByteArray(Charsets.UTF_8)),
                    trustLevel = if (candidate.sourceType == SourceType.KNOWLEDGE) {
                        TrustLevel.CURATED_PUBLIC_SOURCE
                    } else {
                        TrustLevel.VERIFIED_DATASET
                    },
                    retrievedAt = now
                )
            }

        // A valid MiniLM embedding is preferred, but a temporary embedding
        // runtime failure must not make the deterministic catalogue disappear.
        // Missing vectors therefore retain a lexical-only, fully cited path.
        val lexicalCatalogResults = catalogCandidates
            .asSequence()
            .filterNot { it.sourceUri in embeddedSourceUris }
            .mapNotNull { candidate ->
                val score = lexicalScore(queryTokens, candidate.searchText)
                if (score <= 0.0f) return@mapNotNull null
                RetrievedChunk(
                    chunk = RagChunk(
                        id = -250_000L - candidate.hashCode().toLong().coerceAtLeast(0L),
                        documentId = -260_000L - candidate.hashCode().toLong().coerceAtLeast(0L),
                        chunkIndex = 0,
                        text = candidate.text,
                        embedding = FloatArray(0)
                    ),
                    documentName = candidate.sourceName,
                    score = score,
                    sourceType = candidate.sourceType,
                    sourceUri = candidate.sourceUri,
                    sourceUrl = candidate.sourceUri?.takeIf { it.startsWith("https://") },
                    contentHash = sha256(candidate.text.toByteArray(Charsets.UTF_8)),
                    trustLevel = if (candidate.sourceType == SourceType.KNOWLEDGE) {
                        TrustLevel.CURATED_PUBLIC_SOURCE
                    } else {
                        TrustLevel.VERIFIED_DATASET
                    },
                    retrievedAt = now
                )
            }
            .sortedByDescending { it.score }
            .take(MAX_CATALOG_RESULTS)
            .toList()

        // Apply the same relevance gate before top-K so weak catalog rows
        // cannot crowd out an uploaded passage with an exact lexical answer.
        PerfumeEvidenceSelector.select(query, scoredChunks + catalogResults + lexicalCatalogResults, limit)
    }

    private suspend fun buildCatalogCandidates(): List<CatalogCandidate> {
        val ingredients = ingredientDao.getAllIngredientsList()
            .filter { IngredientNameCleaner.clean(it.name).isNotBlank() }.map { ingredient ->
            val details = listOfNotNull(
                "nama ${ingredient.name}",
                "tipe ${ingredient.type}",
                ingredient.brand?.takeIf { it.isNotBlank() }?.let { "brand $it" },
                "note ${ingredient.noteType}",
                "family ${ingredient.fragranceFamily}",
                ingredient.inspiredBy?.takeIf { it.isNotBlank() }?.let { "inspired by $it" },
                ingredient.description?.takeIf { it.isNotBlank() }?.let { "deskripsi $it" },
                ingredient.sourcePlant?.takeIf { it.isNotBlank() }?.let { "sumber tanaman $it" },
                // Legacy IFRA numbers are intentionally not searchable as
                // safety facts: this dataset has no product category, date,
                // source URL, or verification status for those values.
                "density ${ingredient.densityGml} g/ml"
            ).joinToString(", ")

            CatalogCandidate(
                sourceType = SourceType.CATALOG,
                sourceName = "Dataset Ingredients",
                sourceUri = "catalog://ingredient/${ingredient.id}",
                text = "Bahan parfum dari dataset: $details.",
                searchText = details
            )
        }

        val templates = formulaTemplateLoader.getTemplates().map { template ->
            val items = template.ingredients.joinToString("; ") { item ->
                "${item.name} ${item.amount} ${item.unit}, ${item.type}, ${item.noteType}, ${item.fragranceFamily}"
            }
            val searchableItems = template.ingredients
                .filterNot { isDiluentName(it.name) }
                .joinToString(" ") { item ->
                    "${item.name} ${item.type} ${item.noteType} ${item.fragranceFamily}"
                }
            CatalogCandidate(
                sourceType = SourceType.TEMPLATE,
                sourceName = TEMPLATE_SOURCE,
                sourceUri = "asset://data/formula_templates.json#template-${template.id}",
                text = "Template formula parfum ${template.id}: ${template.name}; kategori ${template.category}; family ${template.fragranceFamily}; konsentrasi ${template.concentrationPct}% ${template.concentrationType}; deskripsi ${template.description}; bahan dataset: $items.",
                searchText = listOf(
                    template.name,
                    template.category,
                    template.fragranceFamily.name,
                    template.fragranceFamily.displayName,
                    template.description,
                    searchableItems
                ).joinToString(" ")
            )
        }

        val knowledge = knowledgeLoader.getEntries().map { entry ->
            CatalogCandidate(
                sourceType = SourceType.KNOWLEDGE,
                sourceName = KNOWLEDGE_SOURCE,
                sourceUri = entry.sourceUrl,
                text = "Knowledge parfum: ${entry.topic}; ${entry.answer}; catatan ${entry.caution.orEmpty()}; sumber ${entry.sourceName}; URL ${entry.sourceUrl}; snapshot ${entry.retrievedAt}.",
                searchText = listOf(entry.topic, entry.keywords.joinToString(" "), entry.answer, entry.caution.orEmpty()).joinToString(" ")
            )
        }

        return ingredients + templates + knowledge
    }

    private suspend fun getEmbeddedCatalogCandidates(
        candidates: List<CatalogCandidate>
    ): List<EmbeddedCatalogCandidate> {
        val datasetFingerprint = sha256(
            candidates.joinToString("\n") { candidate ->
                "${candidate.sourceType}|${candidate.sourceUri}|${candidate.text}"
            }.toByteArray(Charsets.UTF_8)
        )
        val cacheKey = "dataset-v1:$datasetFingerprint|embedding:${embeddingService.modelCacheKey()}"
        catalogEmbeddingCache?.takeIf { it.first == cacheKey }?.let { return it.second }

        readCatalogEmbeddingCache(cacheKey, candidates)?.let { cached ->
            catalogEmbeddingCache = cacheKey to cached
            return cached
        }

        return catalogEmbeddingMutex.withLock {
            catalogEmbeddingCache?.takeIf { it.first == cacheKey }?.let { return@withLock it.second }
            readCatalogEmbeddingCache(cacheKey, candidates)?.let { cached ->
                catalogEmbeddingCache = cacheKey to cached
                return@withLock cached
            }
            val embedded = candidates.mapNotNull { candidate ->
                val vector = embeddingService.embed(candidate.text)
                    ?: return@mapNotNull null
                if (vector.size != EMBEDDING_DIMENSION || vector.any { !it.isFinite() }) {
                    return@mapNotNull null
                }
                EmbeddedCatalogCandidate(candidate, vector)
            }
            catalogEmbeddingCache = cacheKey to embedded
            if (embedded.size == candidates.size) {
                writeCatalogEmbeddingCache(cacheKey, embedded)
            }
            embedded
        }
    }

    private fun readCatalogEmbeddingCache(
        cacheKey: String,
        candidates: List<CatalogCandidate>
    ): List<EmbeddedCatalogCandidate>? {
        val file = catalogCacheFile(cacheKey)
        if (!file.isFile || file.length() <= 0L || file.length() > MAX_CATALOG_CACHE_BYTES) return null
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                if (input.readUTF() != cacheKey) return null
                val count = input.readInt()
                if (count != candidates.size || count !in 1..MAX_CATALOG_CACHE_ENTRIES) return null
                val vectors = HashMap<String, FloatArray>(count)
                repeat(count) {
                    val candidateKey = input.readUTF()
                    val dimension = input.readInt()
                    if (dimension != EMBEDDING_DIMENSION) return null
                    val vector = FloatArray(dimension) { input.readFloat() }
                    if (vector.any { !it.isFinite() }) return null
                    vectors[candidateKey] = vector
                }
                candidates.mapNotNull { candidate ->
                    vectors[candidateCacheKey(candidate)]?.let { vector ->
                        EmbeddedCatalogCandidate(candidate, vector)
                    }
                }.takeIf { it.size == candidates.size }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCatalogEmbeddingCache(
        cacheKey: String,
        embedded: List<EmbeddedCatalogCandidate>
    ) {
        if (embedded.isEmpty() || embedded.size > MAX_CATALOG_CACHE_ENTRIES) return
        val directory = File(context.filesDir, "parfume-rag/catalog-embeddings")
        if (!directory.exists() && !directory.mkdirs()) return
        val destination = catalogCacheFile(cacheKey)
        val temporary = File(directory, "${destination.name}.tmp")
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(temporary, false))).use { output ->
                output.writeUTF(cacheKey)
                output.writeInt(embedded.size)
                embedded.forEach { item ->
                    output.writeUTF(candidateCacheKey(item.candidate))
                    output.writeInt(item.embedding.size)
                    item.embedding.forEach(output::writeFloat)
                }
            }
            if (destination.exists()) destination.delete()
            temporary.renameTo(destination)
        } catch (error: Exception) {
            Log.w(TAG, "Unable to persist catalogue embedding cache", error)
            temporary.delete()
        }
    }

    private fun catalogCacheFile(cacheKey: String): File = File(
        File(context.filesDir, "parfume-rag/catalog-embeddings"),
        "${sha256(cacheKey.toByteArray(Charsets.UTF_8))}.bin"
    )

    private fun candidateCacheKey(candidate: CatalogCandidate): String = sha256(
        "${candidate.sourceType}|${candidate.sourceUri}|${candidate.text}".toByteArray(Charsets.UTF_8)
    )

    private fun denseScore(queryEmbedding: FloatArray?, candidateEmbedding: FloatArray?): Float {
        if (queryEmbedding == null || candidateEmbedding == null) return 0.0f
        return CosineSimilarity.calculate(queryEmbedding, candidateEmbedding).coerceIn(0.0f, 1.0f)
    }

    private fun lexicalScore(queryTokens: Set<String>, text: String): Float {
        return PerfumeEvidenceSelector.lexicalScore(queryTokens, text)
    }

    private fun normalizedTokens(text: String): Set<String> {
        // Domain words are useful for intent/knowledge matching, but words
        // such as "parfum" and "bahan" occur in almost every catalogue row.
        // Exclude them only from lexical ranking so they cannot create a
        // false local hit and suppress the opt-in web fallback.
        return PerfumeEvidenceSelector.queryTokens(text)
    }

    private fun isDiluentName(name: String): Boolean {
        return Ingredient.isSolventName(name)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao
) : ChatRepository {

    private val gson = com.google.gson.Gson()
    private val citationListType = object : com.google.gson.reflect.TypeToken<List<SourceCitation>>() {}.type

    override fun getSessions(): Flow<List<ChatSession>> =
        chatDao.getAllSessions().map { list -> list.map { it.toDomain() } }

    override fun getMessages(sessionId: Long): Flow<List<ChatMessage>> =
        chatDao.getMessagesBySessionId(sessionId).map { list ->
            list.map { entity ->
                val cleanContent = if (entity.role.uppercase() == "ASSISTANT") {
                    com.parfumecreator.core.llm.LiteRtLmRunner.sanitizeLlmOutput(entity.content)
                } else {
                    entity.content
                }
                ChatMessage(
                    id = entity.id,
                    sessionId = entity.sessionId,
                    role = when (entity.role.uppercase()) {
                        "USER" -> MessageRole.USER
                        "SYSTEM" -> MessageRole.SYSTEM
                        else -> MessageRole.ASSISTANT
                    },
                    content = cleanContent,
                    ragSources = entity.ragSources?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                    citations = parseCitations(entity.citationsJson, entity.ragSources),
                    retrievalMode = entity.retrievalMode?.let { value ->
                        runCatching { CompanionRetrievalMode.valueOf(value) }.getOrNull()
                    },
                    modelStatus = entity.modelStatus?.let { value ->
                        runCatching { ModelReadiness.valueOf(value) }.getOrNull()
                    },
                    timestamp = entity.timestamp,
                    isStreaming = entity.isStreaming,
                    recommendedFormula = entity.recommendedFormulaJson?.let {
                        com.parfumecreator.core.llm.StructuredFormulaParser.parseJsonFormula(it)
                    }
                )
            }
        }

    override suspend fun createSession(title: String, modelName: String?): Long = withContext(Dispatchers.IO) {
        chatDao.insertSession(ChatSessionEntity(title = title, modelName = modelName))
    }

    override suspend fun deleteSession(sessionId: Long) = withContext(Dispatchers.IO) {
        chatDao.deleteSessionById(sessionId)
    }

    override suspend fun deleteAllSessions() = withContext(Dispatchers.IO) {
        chatDao.deleteAllSessions()
    }

    override suspend fun updateSessionTitle(sessionId: Long, title: String) = withContext(Dispatchers.IO) {
        chatDao.updateSessionTitle(sessionId, title)
    }

    override suspend fun saveMessage(message: ChatMessage): Long = withContext(Dispatchers.IO) {
        val cleanContent = if (message.role == MessageRole.ASSISTANT) {
            com.parfumecreator.core.llm.LiteRtLmRunner.sanitizeLlmOutput(message.content)
        } else {
            message.content
        }
        val entity = ChatMessageEntity(
            id = message.id,
            sessionId = message.sessionId,
            role = message.role.name,
            content = cleanContent,
            ragSources = message.ragSources.joinToString(","),
            citationsJson = message.citations.takeIf { it.isNotEmpty() }?.let { gson.toJson(it) },
            retrievalMode = message.retrievalMode?.name,
            modelStatus = message.modelStatus?.name,
            timestamp = message.timestamp,
            isStreaming = message.isStreaming,
            recommendedFormulaJson = message.recommendedFormula?.let {
                com.google.gson.Gson().toJson(it)
            }
        )
        val msgId = chatDao.insertMessage(entity)
        chatDao.updateSessionTimestamp(message.sessionId, System.currentTimeMillis())
        msgId
    }

    private fun parseCitations(json: String?, legacySources: String?): List<SourceCitation> {
        if (!json.isNullOrBlank()) {
            runCatching {
                gson.fromJson<List<SourceCitation>>(json, citationListType)
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return legacySources.orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { source ->
                val web = source.startsWith(com.parfumecreator.core.llm.CompanionRetrievalPolicy.INTERNET_SOURCE_PREFIX)
                SourceCitation(
                    sourceType = if (web) SourceType.WEB else SourceType.UPLOAD,
                    title = source.removePrefix(com.parfumecreator.core.llm.CompanionRetrievalPolicy.INTERNET_SOURCE_PREFIX),
                    excerpt = "Sumber historis dari format pesan lama.",
                    uri = if (web) null else source,
                    url = null,
                    trustLevel = if (web) TrustLevel.WEB_UNTRUSTED else TrustLevel.USER_DOCUMENT
                )
            }
    }

    override suspend fun updateMessageContent(messageId: Long, content: String, isStreaming: Boolean) = withContext(Dispatchers.IO) {
        chatDao.updateMessageContent(messageId, content, isStreaming)
    }
}

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val settingsRepository: SettingsRepository
) : DownloadRepository {

    private fun getCanonicalMetadata(id: String, currentUrl: String, currentBytes: Long): Pair<String, Long> {
        return when (id) {
            "llm_gemma_2b" -> ModelArtifactRegistry.forId(id)?.let { it.pinnedUrl to it.expectedBytes } ?: (currentUrl to currentBytes)
            "llm_gemma_4b" -> ModelArtifactRegistry.forId(id)?.let { it.pinnedUrl to it.expectedBytes } ?: (currentUrl to currentBytes)
            "embed_all_minilm" -> "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/model.tflite" to 23_000_000L
            else -> currentUrl to currentBytes
        }
    }

    override fun getBundles(): Flow<List<DownloadBundle>> =
        downloadDao.getAllDownloads().map { list ->
            list.map { entity ->
                val (canonicalUrl, canonicalBytes) = getCanonicalMetadata(entity.id, entity.url, entity.totalBytes)
                val fileName = when (entity.id) {
                    "llm_gemma_2b" -> "gemma-4-E2B-it.litertlm"
                    "llm_gemma_4b" -> "gemma-4-E4B-it.litertlm"
                    else -> entity.fileName
                }
                val name = when (entity.id) {
                    "llm_gemma_2b" -> "Gemma 4 E2B-it (LiteRT-LM)"
                    "llm_gemma_4b" -> "Gemma 4 E4B-it (High-Capability)"
                    else -> entity.name
                }
                DownloadBundle(
                    id = entity.id,
                    name = name,
                    fileName = fileName,
                    url = canonicalUrl,
                    totalBytes = canonicalBytes,
                    downloadedBytes = entity.downloadedBytes,
                    status = when (entity.status) {
                        "QUEUED" -> DownloadStatus.QUEUED
                        "DOWNLOADING" -> DownloadStatus.DOWNLOADING
                        "PAUSED" -> DownloadStatus.PAUSED
                        "COMPLETED" -> DownloadStatus.COMPLETED
                        "FAILED" -> DownloadStatus.FAILED
                        else -> DownloadStatus.NOT_DOWNLOADED
                    },
                    destinationUri = entity.destinationUri,
                    description = entity.description,
                    isRequired = entity.isRequired
                )
            }
        }

    override suspend fun startDownload(bundleId: String, targetFolderUri: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!targetFolderUri.startsWith("content://")) {
                return@withContext Result.failure(Exception("Pilih folder SAF terlebih dahulu"))
            }
            // Only pinned, checksum-verified model artifacts may enter the
            // download worker. Bundled embeddings are read-only assets and
            // must not be exposed as a fake downloadable bundle.
            if (ModelArtifactRegistry.forId(bundleId) == null) {
                return@withContext Result.failure(
                    IllegalArgumentException("Bundle $bundleId bukan artefak model terverifikasi")
                )
            }
            val entity = downloadDao.getDownloadById(bundleId) ?: return@withContext Result.failure(Exception("Bundle not found"))
            val (canonicalUrl, canonicalBytes) = getCanonicalMetadata(bundleId, entity.url, entity.totalBytes)
            val canonicalFileName = when (bundleId) {
                "llm_gemma_2b" -> "gemma-4-E2B-it.litertlm"
                "llm_gemma_4b" -> "gemma-4-E4B-it.litertlm"
                else -> entity.fileName
            }

            val updatedEntity = entity.copy(
                url = canonicalUrl,
                totalBytes = canonicalBytes,
                fileName = canonicalFileName
            )
            downloadDao.insertDownload(updatedEntity)

            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(Data.Builder().apply {
                    putString(DownloadWorker.KEY_BUNDLE_ID, bundleId)
                    putString(DownloadWorker.KEY_URL, canonicalUrl)
                    putString(DownloadWorker.KEY_FILE_NAME, canonicalFileName)
                    putString(DownloadWorker.KEY_DEST_FOLDER, targetFolderUri)
                    ModelArtifactRegistry.expectedHash(bundleId)?.let {
                        putString(DownloadWorker.KEY_EXPECTED_SHA256, it)
                    }
                }.build())
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                // A large model can lose the network for a moment. Let the
                // worker preserve its .tmp file and retry with bounded
                // exponential backoff instead of surfacing a permanent error.
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS
                )
                .addTag("download_$bundleId")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "download_$bundleId",
                // A repeated tap must not cancel a live multi-gigabyte worker.
                // KEEP still permits a new request after the previous work is
                // finished, including a user-initiated retry after FAILED.
                ExistingWorkPolicy.KEEP,
                workRequest
            )

            downloadDao.updateStatus(bundleId, "QUEUED", null)
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun pauseDownload(bundleId: String) = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelUniqueWork("download_$bundleId")
        downloadDao.updateStatus(bundleId, "PAUSED", null)
    }

    override suspend fun resumeDownload(bundleId: String) = withContext(Dispatchers.IO) {
        downloadDao.getDownloadById(bundleId) ?: return@withContext
        val folderUri = settingsRepository.getSettings().firstOrNull()?.safStorageUri
        if (folderUri.isNullOrBlank()) {
            downloadDao.updateStatus(bundleId, "FAILED", null)
            return@withContext
        }
        startDownload(bundleId, folderUri)
    }

    override suspend fun cancelDownload(bundleId: String) = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelUniqueWork("download_$bundleId")
        downloadDao.updateStatus(bundleId, "NOT_DOWNLOADED", null)
        downloadDao.updateProgress(bundleId, 0, "NOT_DOWNLOADED")
    }
}

@Singleton
class LlmRepositoryImpl @Inject constructor(
    private val liteRtLmRunner: LiteRtLmRunner,
    private val modelManager: ModelManager
) : LlmRepository {

    override fun streamGenerate(
        prompt: String,
        systemPrompt: String?,
        ragContext: List<RetrievedChunk>?,
        conversationId: Long?
    ): Flow<String> = liteRtLmRunner.generateStream(prompt, systemPrompt, ragContext, conversationId)

    override suspend fun isModelAvailable(): Boolean =
        liteRtLmRunner.verifyRuntimeReady()

    override suspend fun getActiveModelPath(): String? = modelManager.getModelPath()
}
