package com.parfumecreator.core.llm

import android.content.Context
import android.util.Log
import com.parfumecreator.core.embedding.CosineSimilarity
import com.parfumecreator.core.embedding.TfLiteEmbeddingService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

private const val KNOWLEDGE_TAG = "PerfumeryKnowledge"

/**
 * A small, versioned, offline snapshot of public perfumery references.
 *
 * The snapshot stores paraphrased facts plus provenance. It is intentionally
 * separate from the product catalog: a product row is evidence of an item the
 * user has, while this corpus is evidence for general perfumery questions.
 */
data class PerfumeryKnowledgeEntry(
    val id: String,
    val topic: String,
    val keywords: List<String>,
    val answer: String,
    val caution: String?,
    val sourceName: String,
    val sourceUrl: String,
    val retrievedAt: String
) {
    val searchableText: String
        get() = listOf(topic, keywords.joinToString(" "), answer, caution.orEmpty()).joinToString(" ")
}

@Singleton
class PerfumeryKnowledgeLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val embeddingService: TfLiteEmbeddingService
) {
    @Volatile
    private var cachedEntries: List<PerfumeryKnowledgeEntry>? = null
    @Volatile
    private var cachedEntryEmbeddings: Pair<String, Map<String, FloatArray>>? = null
    private val embeddingMutex = Mutex()

    fun getEntries(): List<PerfumeryKnowledgeEntry> {
        cachedEntries?.let { return it }
        synchronized(this) {
            cachedEntries?.let { return it }
            val loaded = loadEntries()
            cachedEntries = loaded
            return loaded
        }
    }

    suspend fun findRelevant(query: String, limit: Int = 3): List<PerfumeryKnowledgeEntry> {
        val queryTokens = PerfumeTextNormalizer.tokens(query).ifEmpty {
            // Generic words are intentionally removed from catalogue lexical
            // scoring, but they can still be meaningful for a knowledge
            // keyword such as "apa itu parfum".
            PerfumeTextNormalizer.rawTokens(query).filterNot {
                it in setOf("apa", "itu", "yang", "adalah", "jelaskan", "tentang")
            }.toSet()
        }
        if (queryTokens.isEmpty()) return emptyList()
        val normalizedQuery = query.lowercase(java.util.Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

        val queryEmbedding = embeddingService.embed(query)
        val entryEmbeddings = getEntryEmbeddings()

        return getEntries()
            .map { entry ->
                val entryTokens = PerfumeTextNormalizer.tokens(entry.searchableText)
                val lexicalScore = queryTokens.count { it in entryTokens }.toFloat() /
                    queryTokens.size.toFloat()
                val keywordHits = queryTokens.count { queryToken ->
                    entry.keywords.any { keyword ->
                        PerfumeTextNormalizer.tokens(keyword).contains(queryToken)
                    }
                }
                val exactKeyword = entry.keywords.any { keyword ->
                    val keywordTokens = PerfumeTextNormalizer.rawTokens(keyword)
                    val normalizedKeyword = keyword.lowercase(java.util.Locale.ROOT)
                        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
                        .trim()
                    keywordTokens.isNotEmpty() &&
                        queryTokens.containsAll(keywordTokens.filterNot {
                            it in setOf("apa", "itu", "yang", "adalah", "jelaskan", "tentang")
                        }) &&
                        normalizedKeyword.contains(normalizedQuery)
                }
                val keywordScore = (lexicalScore + (keywordHits * 0.20f) + if (exactKeyword) 0.55f else 0.0f)
                    .coerceIn(0.0f, 1.0f)
                val entryEmbedding = entryEmbeddings[entry.id]
                val denseScore = if (queryEmbedding != null && entryEmbedding != null) {
                    CosineSimilarity.calculate(queryEmbedding, entryEmbedding).coerceIn(0.0f, 1.0f)
                } else {
                    0.0f
                }
                val hybridScore = if (queryEmbedding != null && entryEmbedding != null) {
                    0.70f * denseScore + 0.30f * keywordScore
                } else {
                    keywordScore
                }
                entry to hybridScore
            }
            .filter { (entry, _) ->
                PerfumeEvidenceSelector.lexicalScore(
                    PerfumeEvidenceSelector.queryTokens(query), entry.searchableText
                ) >= 0.60f
            }
            .sortedByDescending { it.second }
            .take(limit.coerceAtLeast(1))
            .map { it.first }
    }

    private suspend fun getEntryEmbeddings(): Map<String, FloatArray> {
        val entries = getEntries()
        val key = "knowledge-v1:${entries.joinToString("|") { "${it.id}:${it.searchableText}" }}|${embeddingService.modelCacheKey()}"
        cachedEntryEmbeddings?.takeIf { it.first == key }?.let { return it.second }
        return embeddingMutex.withLock {
            cachedEntryEmbeddings?.takeIf { it.first == key }?.let { return@withLock it.second }
            val values = entries.mapNotNull { entry ->
                embeddingService.embed(entry.searchableText)?.let { vector -> entry.id to vector }
            }.toMap()
            cachedEntryEmbeddings = key to values
            values
        }
    }

    private fun loadEntries(): List<PerfumeryKnowledgeEntry> {
        return try {
            val json = context.assets.open("data/perfumery_knowledge.json").use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).readText()
            }
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val objectJson = array.optJSONObject(index) ?: continue
                    val id = objectJson.optString("id").trim()
                    val topic = objectJson.optString("topic").trim()
                    val answer = objectJson.optString("answer").trim()
                    val sourceUrl = objectJson.optString("sourceUrl").trim()
                    if (id.isBlank() || topic.isBlank() || answer.isBlank() || sourceUrl.isBlank()) {
                        continue
                    }

                    val keywords = objectJson.optJSONArray("keywords")?.let { values ->
                        buildList {
                            for (keywordIndex in 0 until values.length()) {
                                values.optString(keywordIndex).trim()
                                    .takeIf { it.isNotBlank() }
                                    ?.let(::add)
                            }
                        }
                    }.orEmpty()

                    add(
                        PerfumeryKnowledgeEntry(
                            id = id,
                            topic = topic,
                            keywords = keywords,
                            answer = answer,
                            caution = objectJson.optString("caution").trim().takeIf { it.isNotBlank() },
                            sourceName = objectJson.optString("sourceName", "Public perfumery reference").trim(),
                            sourceUrl = sourceUrl,
                            retrievedAt = objectJson.optString("retrievedAt", "unknown").trim()
                        )
                    )
                }
            }
        } catch (error: Exception) {
            Log.w(KNOWLEDGE_TAG, "Perfumery knowledge asset unavailable", error)
            emptyList()
        }
    }
}
