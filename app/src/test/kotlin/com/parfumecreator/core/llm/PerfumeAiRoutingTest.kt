package com.parfumecreator.core.llm

import com.parfumecreator.data.local.database.IngredientEntity
import com.parfumecreator.domain.model.RagChunk
import com.parfumecreator.domain.model.RetrievedChunk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerfumeAiRoutingTest {
    @Test
    fun routesFormulaKnowledgeAndCatalogTurnsSeparately() {
        assertEquals(
            PerfumeIntent.NOVEL_FORMULA,
            PerfumeIntentClassifier.classify("buatkan parfum citrus 100g edp")
        )
        assertEquals(
            PerfumeIntent.TEMPLATE_FORMULA,
            PerfumeIntentClassifier.classify("gunakan template citrus")
        )
        assertEquals(
            PerfumeIntent.CATALOG_RECOMMENDATION,
            PerfumeIntentClassifier.classify("rekomendasikan produk citrus")
        )
        assertEquals(
            PerfumeIntent.KNOWLEDGE,
            PerfumeIntentClassifier.classify("apa beda EDP dan EDT")
        )
        assertEquals(
            PerfumeIntent.KNOWLEDGE,
            PerfumeIntentClassifier.classify("cari informasi parfum di internet")
        )
        assertEquals(
            PerfumeIntent.CATALOG_RECOMMENDATION,
            PerfumeIntentClassifier.classify("cari produk citrus")
        )
        assertEquals(PerfumeIntent.KNOWLEDGE, PerfumeIntentClassifier.classify("Bagaimana cara meracik parfum natural menurut dokumen?"))
        assertEquals(PerfumeIntent.KNOWLEDGE, PerfumeIntentClassifier.classify("Apa beda formula dan accord parfum?"))
    }

    @Test
    fun keepsCorePerfumeTermsForHybridSearch() {
        val tokens = PerfumeTextNormalizer.tokens("apa itu parfum dan fragrance")

        assertTrue(tokens.contains("parfum"))
        assertTrue(tokens.contains("fragrance"))
    }

    @Test
    fun internetFallbackOnlyAppliesToKnowledgeWithWeakLocalEvidence() {
        assertTrue(
            CompanionRetrievalPolicy.shouldSearchInternet(
                "apa itu parfum?",
                emptyList(),
                allowInternetFallback = true
            )
        )
        assertTrue(
            CompanionRetrievalPolicy.shouldSearchInternet(
                "jelaskan bahan parfum",
                listOf(
                    RetrievedChunk(
                        chunk = RagChunk(1, 1, 0, "local evidence", FloatArray(0)),
                        documentName = "upload.txt",
                        score = 0.20f
                    )
                ),
                allowInternetFallback = true
            )
        )
        assertTrue(
            !CompanionRetrievalPolicy.shouldSearchInternet(
                "buatkan parfum citrus",
                emptyList()
            )
        )
    }

    @Test
    fun newFormulaUsesCatalogIdsAndAddsCatalogDiluent() {
        val catalog = listOf(
            IngredientEntity(
                id = 1,
                name = "Bergamot Essential Oil",
                type = "NATURAL",
                noteType = "TOP",
                fragranceFamily = "CITRUS",
                densityGml = 0.882
            ),
            IngredientEntity(
                id = 2,
                name = "Lavender Essential Oil (40/42)",
                type = "NATURAL",
                noteType = "HEART",
                fragranceFamily = "FLORAL",
                densityGml = 0.885
            ),
            IngredientEntity(
                id = 3,
                name = "Linalool Natural Pure Isolates",
                type = "NATURAL",
                noteType = "MIDDLE",
                fragranceFamily = "FRESH",
                densityGml = 0.95
            ),
            IngredientEntity(
                id = 4,
                name = "Sandalwood Essential Oil",
                type = "NATURAL",
                noteType = "BASE",
                fragranceFamily = "WOODY",
                densityGml = 0.97
            ),
            IngredientEntity(
                id = 5,
                name = "Perfumer Alcohol 96%",
                type = "NATURAL",
                noteType = "MIDDLE",
                fragranceFamily = "FRESH",
                densityGml = 0.80
            )
        )

        val plan = GroundedFormulaPlanner().create("buatkan parfum citrus 100g edp", catalog)

        requireNotNull(plan)
        assertEquals("15.00% Eau de Parfum (EDP)", plan.formula.concentration)
        assertEquals(100.0, plan.totalWeightG, 0.0001)
        assertTrue(plan.formula.ingredients.any { it.noteType == "DILUENT" })
        assertEquals(100.0, plan.formula.ingredients.sumOf { it.amount }, 0.001)
        assertTrue(plan.formula.ingredients.all { (it.resolvedIngredientId ?: 0L) > 0L })
    }

    @Test
    fun newFormulaConvertsVolumeOnlyWhenDensityIsAvailable() {
        val catalog = listOf(
            IngredientEntity(1, "Bergamot", "NATURAL", noteType = "TOP", fragranceFamily = "CITRUS", densityGml = 0.88),
            IngredientEntity(2, "Lavender", "NATURAL", noteType = "HEART", fragranceFamily = "FLORAL", densityGml = 0.89),
            IngredientEntity(3, "Linalool", "SYNTHETIC", noteType = "MIDDLE", fragranceFamily = "FRESH", densityGml = 0.95),
            IngredientEntity(4, "Cedarwood", "NATURAL", noteType = "BASE", fragranceFamily = "WOODY", densityGml = 0.97),
            IngredientEntity(5, "Perfumer Alcohol", "SYNTHETIC", noteType = "MIDDLE", fragranceFamily = "FRESH", densityGml = 0.80)
        )

        val plan = GroundedFormulaPlanner().create("buatkan parfum citrus 100ml edp", catalog)

        assertNotNull(plan)
        requireNotNull(plan)
        assertTrue(plan.totalWeightG > 0.0)
        assertTrue(plan.totalWeightG < 100.0)
        assertEquals(plan.totalWeightG, plan.formula.ingredients.sumOf { it.amount }, 0.01)
        assertTrue(plan.formula.ingredients.all { it.unit == "g" && (it.resolvedIngredientId ?: 0L) > 0L })
    }

    @Test
    fun newFormulaWithoutDiluentDoesNotReplaceRequestedConcentration() {
        val catalog = listOf(
            IngredientEntity(1, "Bergamot", "NATURAL", noteType = "TOP", fragranceFamily = "CITRUS", densityGml = 0.88),
            IngredientEntity(2, "Lavender", "NATURAL", noteType = "HEART", fragranceFamily = "FLORAL", densityGml = 0.89),
            IngredientEntity(3, "Linalool", "SYNTHETIC", noteType = "MIDDLE", fragranceFamily = "FRESH", densityGml = 0.95),
            IngredientEntity(4, "Cedarwood", "NATURAL", noteType = "BASE", fragranceFamily = "WOODY", densityGml = 0.97)
        )

        val plan = GroundedFormulaPlanner().create("buatkan parfum citrus 10g edp", catalog)

        org.junit.Assert.assertNull(plan)
    }
}
