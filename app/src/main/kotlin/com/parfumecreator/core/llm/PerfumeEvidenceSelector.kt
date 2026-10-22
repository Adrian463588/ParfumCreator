package com.parfumecreator.core.llm

import com.parfumecreator.core.document.TextChunker
import com.parfumecreator.domain.model.RetrievedChunk
import com.parfumecreator.domain.model.SourceType

/** Shared relevance gate for retrieval, fallback decisions and extractive answers. */
object PerfumeEvidenceSelector {
    private val questionNoise = setOf(
        "parfum", "perfume", "fragrance", "aroma", "scent", "wewangian", "bahan",
        "produk", "katalog", "formula", "dokumen", "document", "upload", "file",
        "menurut", "berdasarkan", "jelaskan", "bagaimana", "mengapa", "kenapa",
        "berapa", "apakah", "informasi", "tersebut", "what", "how", "does", "the",
        "use", "about", "explain", "can", "you", "itu", "saya", "tolong",
        "beda", "perbedaan", "difference", "between", "cara", "proses"
    )

    fun queryTokens(query: String): Set<String> {
        val tokens = PerfumeTextNormalizer.tokens(query)
        return tokens.filterNot { it in questionNoise }.toSet().ifEmpty {
            tokens.filter { it in setOf("parfum", "perfume", "fragrance", "wewangian") }
                .map { "perfume" }.toSet()
        }
    }

    private fun textTokens(text: String): Set<String> = PerfumeTextNormalizer.tokens(text)
        .map { if (it in setOf("parfum", "fragrance", "wewangian")) "perfume" else it }
        .toSet()

    fun lexicalScore(queryTokens: Set<String>, text: String): Float {
        if (queryTokens.isEmpty()) return 0f
        val tokens = textTokens(text)
        return queryTokens.count(tokens::contains).toFloat() / queryTokens.size
    }

    /** Weak dense similarity alone cannot turn an unrelated row into evidence. */
    fun select(query: String, evidence: List<RetrievedChunk>, limit: Int = 8): List<RetrievedChunk> {
        val tokens = queryTokens(query)
        return evidence.mapNotNull { item ->
            if (!item.score.isFinite() || item.chunk.text.isBlank()) return@mapNotNull null
            if (tokens == setOf("perfume") && item.sourceType in setOf(SourceType.CATALOG, SourceType.TEMPLATE)) {
                return@mapNotNull null
            }
            val coverage = lexicalScore(tokens, item.chunk.text)
            if (coverage < MIN_PASSAGE_COVERAGE) return@mapNotNull null
            val score = maxOf(item.score.coerceIn(0f, 1f), coverage * 0.85f)
            item.copy(score = score) to coverage
        }.sortedWith(
            compareByDescending<Pair<RetrievedChunk, Float>> { it.second }
                .thenByDescending { it.first.sourceType == SourceType.UPLOAD }
                .thenByDescending { it.first.score }
        ).map { it.first }
            .distinctBy { Triple(it.sourceUri, it.documentName, it.chunk.text) }
            .take(limit.coerceAtLeast(1))
    }

    fun hasSufficientEvidence(query: String, evidence: List<RetrievedChunk>): Boolean {
        val tokens = queryTokens(query)
        return select(query, evidence).any {
            it.score.isFinite() && lexicalScore(tokens, it.chunk.text) >= MIN_SUFFICIENT_COVERAGE
        }
    }

    /** Rank the whole bounded page, including sections beyond the introduction. */
    fun passages(query: String, text: String, limit: Int = 3): List<String> {
        val tokens = queryTokens(query)
        return TextChunker.chunkText(text, chunkSize = 160, overlap = 32, maxChars = 1_200)
            .mapIndexed { index, passage -> Triple(passage, lexicalScore(tokens, passage), index) }
            .filter { it.second >= MIN_PASSAGE_COVERAGE }
            .sortedWith(compareByDescending<Triple<String, Float, Int>> { it.second }.thenBy { it.third })
            .take(limit.coerceIn(1, 8))
            .map { it.first }
    }

    private const val MIN_PASSAGE_COVERAGE = 0.60f
    private const val MIN_SUFFICIENT_COVERAGE = 0.75f
}
