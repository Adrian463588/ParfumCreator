package com.parfumecreator.core.llm

import com.parfumecreator.domain.model.RagChunk
import com.parfumecreator.domain.model.RetrievedChunk
import com.parfumecreator.domain.model.SourceType
import org.junit.Assert.*
import org.junit.Test

class PerfumeEvidenceSelectorTest {
    private fun evidence(text: String, score: Float = 0.9f, type: SourceType = SourceType.UPLOAD) =
        RetrievedChunk(RagChunk(1, 1, 0, text, FloatArray(0)), "source.txt", score, sourceType = type)

    @Test
    fun genericHighDenseMatchCannotAnswerSpecificQuestion() {
        val chunks = listOf(evidence("Bergamot perfume is a fresh citrus fragrance oil."))
        assertTrue(PerfumeEvidenceSelector.select("Apa batas hydroxycitronellal IFRA parfum?", chunks).isEmpty())
        assertFalse(PerfumeEvidenceSelector.hasSufficientEvidence("Apa batas hydroxycitronellal IFRA parfum?", chunks))
        assertTrue(PerfumeEvidenceSelector.select("Apa itu parfum?", listOf(
            evidence("Bahan parfum dari dataset: Bergamot Oil", type = SourceType.CATALOG)
        )).isEmpty())
    }

    @Test
    fun exactUploadIsNotCrowdedOutByCatalogDenseScores() {
        val upload = evidence("Durasi maceration parfum menurut percobaan adalah 14 hari.", 0.20f)
        val catalog = evidence("Parfum citrus bergamot natural top note.", 0.99f, SourceType.CATALOG)
        val result = PerfumeEvidenceSelector.select("Berapa durasi maserasi parfum menurut dokumen?", listOf(catalog, upload))
        assertEquals(listOf(upload.copy(score = 0.85f)), result)
        assertTrue(PerfumeEvidenceSelector.hasSufficientEvidence("Berapa durasi maserasi parfum?", result))
    }

    @Test
    fun wholePageRankingFindsLateRelevantSectionAndRejectsUnrelatedPage() {
        val introduction = ("Navigation menu contact archive copyright. ").repeat(100)
        val answer = "Ambroxan synthesis uses sclareol as a starting material."
        val passages = PerfumeEvidenceSelector.passages("Jelaskan ambroxan synthesis parfum", introduction + answer)
        assertTrue(passages.first().contains(answer))
        assertTrue(PerfumeEvidenceSelector.passages("ambroxan synthesis parfum", introduction).isEmpty())
    }

    @Test
    fun unicodeAndInvalidScoresAreHandled() {
        assertTrue(PerfumeEvidenceSelector.select("Apa aroma bunga mawar?", listOf(evidence("floral rose"))).isNotEmpty())
        assertTrue(PerfumeEvidenceSelector.select("musk", listOf(evidence("musk", Float.NaN))).isEmpty())
        assertTrue(PerfumeEvidenceSelector.select("!", listOf(evidence("musk"))).isEmpty())
    }
}
