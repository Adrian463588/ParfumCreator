package com.parfumecreator.core.llm

import com.parfumecreator.domain.model.FragranceFamily
import java.util.Locale

/**
 * Routes a user turn to the domain operation that owns the answer contract.
 *
 * Formula requests are deliberately separated from educational questions. A
 * formula must be grounded in catalog/template rows, while a knowledge answer
 * can be composed from the curated perfumery reference snapshot and the local
 * model. Keeping the routing deterministic prevents a generic chat response
 * from being mistaken for a usable recipe.
 */
enum class PerfumeIntent {
    KNOWLEDGE,
    TEMPLATE_FORMULA,
    NOVEL_FORMULA,
    CATALOG_RECOMMENDATION
}

object PerfumeIntentClassifier {
    private val templateTerms = setOf(
        "template", "templat", "persis", "exact", "formula_id", "formulaid"
    )

    private val formulaTerms = setOf(
        "buatkan", "buat", "bikin", "racik", "racikan", "formulasi", "formulation",
        "komposisi", "campur", "blend", "resep", "recipe", "formula", "meracik"
    )

    private val recommendationTerms = setOf(
        "rekomendasi", "rekomendasikan", "pilihkan", "produk", "katalog",
        "brand", "merek", "cocok", "pilihan", "recommend", "recommendation"
    )

    fun classify(prompt: String): PerfumeIntent {
        val normalized = prompt.lowercase(Locale.ROOT).replace('_', ' ')
        val tokens = PerfumeTextNormalizer.tokens(normalized)
        val rawTokens = PerfumeTextNormalizer.rawTokens(normalized)
        fun hasTerm(term: String): Boolean {
            val termTokens = PerfumeTextNormalizer.rawTokens(term)
            return if (termTokens.size <= 1) {
                termTokens.firstOrNull()?.let(rawTokens::contains) == true
            } else {
                normalized.contains(
                    Regex(
                        "(?<![\\p{L}\\p{N}])${Regex.escape(term)}(?![\\p{L}\\p{N}])",
                        RegexOption.IGNORE_CASE
                    )
                )
            }
        }
        val mentionsTemplate = templateTerms.any { term ->
            hasTerm(term)
        }
        val mentionsFormula = formulaTerms.any { it in rawTokens || it in tokens } || hasTerm("formula id")
        val mentionsRecommendation = recommendationTerms.any { it in rawTokens || it in tokens } ||
            Regex("(?<![\\p{L}\\p{N}])cari\\s+(produk|bahan|brand|merek|katalog)(?![\\p{L}\\p{N}])")
                .containsMatchIn(normalized)

        // Asking how a blend works is an educational turn, not an order to
        // produce an importable recipe. Explicit creation verbs retain priority.
        val creationRequested = rawTokens.any {
            it in setOf("buat", "buatkan", "bikin", "racik", "create", "generate", "blend")
        }
        val educational = !creationRequested && rawTokens.any {
            it in setOf("apa", "apakah", "bagaimana", "mengapa", "kenapa", "jelaskan", "what", "why", "how", "explain")
        }

        return when {
            educational -> PerfumeIntent.KNOWLEDGE
            mentionsTemplate -> PerfumeIntent.TEMPLATE_FORMULA
            mentionsFormula -> PerfumeIntent.NOVEL_FORMULA
            mentionsRecommendation -> PerfumeIntent.CATALOG_RECOMMENDATION
            else -> PerfumeIntent.KNOWLEDGE
        }
    }

    fun requiresGroundedOutput(intent: PerfumeIntent): Boolean = when (intent) {
        PerfumeIntent.TEMPLATE_FORMULA,
        PerfumeIntent.NOVEL_FORMULA,
        PerfumeIntent.CATALOG_RECOMMENDATION -> true

        PerfumeIntent.KNOWLEDGE -> false
    }
}

/**
 * Local-first companion policy. Web snippets are allowed for knowledge turns
 * only when local evidence is absent or below the relevance threshold.
 */
object CompanionRetrievalPolicy {
    const val INTERNET_SOURCE_PREFIX = "Internet • "

    fun shouldSearchInternet(
        prompt: String,
        localEvidence: List<com.parfumecreator.domain.model.RetrievedChunk>,
        allowInternetFallback: Boolean = false
    ): Boolean {
        if (!allowInternetFallback) return false
        if (PerfumeIntentClassifier.classify(prompt) != PerfumeIntent.KNOWLEDGE) return false
        if (PerfumeAiGuardrail.refusalFor(prompt, localEvidence.isNotEmpty()) != null) return false

        return !PerfumeEvidenceSelector.hasSufficientEvidence(prompt, localEvidence)
    }
}

/**
 * Shared token normalization for lexical RAG, knowledge lookup, and formula
 * candidate ranking. Domain terms such as EDP, top, and IFRA are retained;
 * dropping them was the reason many perfume questions previously had no
 * useful lexical match.
 */
object PerfumeTextNormalizer {
    private val stopWords = setOf(
        "aku", "saya", "kami", "kamu", "anda", "buat", "buatkan", "bikin", "tolong",
        "mau", "ingin", "carikan", "pilihkan", "untuk", "dengan", "yang", "dan", "atau",
        "ini", "itu", "sebuah", "halo", "hai", "please", "apa", "adalah", "dari", "ke",
        "pada", "dalam", "tentang", "terkait", "bisa", "akan", "ada", "tidak", "saja",
        "g", "gram", "ml"
    )

    fun rawTokens(text: String): Set<String> = Regex("[\\p{L}\\p{N}]+")
        .findAll(text.lowercase(Locale.ROOT))
        .map { it.value }
        .toSet()

    fun tokens(text: String): Set<String> {
        var normalized = text.lowercase(Locale.ROOT)
        val synonyms = linkedMapOf(
            "maserasi" to "maceration",
            "konsentrasi" to "concentration",
            "pelarut" to "solvent",
            "densitas" to "density",
            "sintesis" to "synthesis",
            "keamanan" to "safety",
            "alergi" to "allergy",
            "alergen" to "allergen",
            "pengenceran" to "dilution",
            "penyimpanan" to "storage",
            "ketahanan" to "longevity",
            "tahan lama" to "longevity",
            "segar" to "fresh",
            "jeruk" to "citrus",
            "bunga" to "floral",
            "melati" to "jasmine",
            "mawar" to "rose",
            "kayu" to "woody",
            "cendana" to "sandalwood",
            "nilam" to "patchouli",
            "manis" to "gourmand",
            "laut" to "aquatic marine",
            "rempah" to "spicy",
            "olahraga" to "sport",
            "gym" to "sport",
            "fitness" to "sport",
            "tropis" to "tropical",
            "siang" to "daytime"
        )
        synonyms.forEach { (from, to) ->
            normalized = normalized.replace(
                Regex("(?<![\\p{L}\\p{N}])${Regex.escape(from)}(?![\\p{L}\\p{N}])"),
                to
            )
        }

        return rawTokens(normalized)
            .filter { it.length > 2 && it !in stopWords }
            .toSet()
    }

    fun familySignals(): Map<FragranceFamily, Set<String>> = mapOf(
        FragranceFamily.CITRUS to setOf("citrus", "bergamot", "lemon", "orange", "grapefruit", "lime", "mandarin", "tangerine"),
        FragranceFamily.FLORAL to setOf("floral", "flower", "rose", "jasmine", "lavender", "ylang", "bunga"),
        FragranceFamily.WOODY to setOf("woody", "wood", "cedar", "sandalwood", "vetiver", "oud", "patchouli"),
        FragranceFamily.ORIENTAL to setOf("oriental", "amber", "ambery", "vanilla", "vanila", "musk", "musky"),
        FragranceFamily.FRESH to setOf("fresh", "sport", "gym", "daytime", "tropical", "segar"),
        FragranceFamily.FOUGERE to setOf("fougere", "aromatic", "herbal", "lavender"),
        FragranceFamily.CHYPRE to setOf("chypre", "moss", "oakmoss", "labdanum"),
        FragranceFamily.GOURMAND to setOf("gourmand", "sweet", "caramel", "chocolate", "coffee", "vanilla", "vanila"),
        FragranceFamily.AQUATIC to setOf("aquatic", "marine", "ocean", "ozonic", "water"),
        FragranceFamily.SPICY to setOf("spicy", "pepper", "cinnamon", "clove", "cardamom", "rempah"),
        FragranceFamily.EARTHY to setOf("earthy", "green", "earth", "soil", "moss", "vetiver"),
        FragranceFamily.MUSKY to setOf("musky", "musk", "powdery"),
        FragranceFamily.LEATHER to setOf("leather", "smoky", "smoke", "tobacco"),
        FragranceFamily.FRUITY to setOf("fruity", "fruit", "apple", "peach", "berry", "pear"),
        FragranceFamily.GREEN to setOf("green", "leaf", "grass", "galbanum", "herbal"),
        FragranceFamily.ALDEHYDIC to setOf("aldehydic", "aldehyde", "soapy", "clean")
    )
}
