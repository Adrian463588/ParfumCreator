package com.parfumecreator.core.llm

import com.parfumecreator.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class GroundedEvidenceAnswerTest {
    @Test
    fun actualOfflineRendererAnswersWithUploadTextAndPage() {
        val context = PerfumeEvidenceSelector.select("Berapa durasi maserasi parfum?", listOf(
            RetrievedChunk(RagChunk(1, 1, 0, "Durasi maceration parfum pada eksperimen ini 14 hari.", FloatArray(0), 3, 3),
                "uji-parfum.pdf", 0.8f, sourceUri = "content://documents/1")
        ))
        val answer = GroundedEvidenceAnswer.local(context, "LLM lokal belum tersedia")
        assertTrue(answer.contains("14 hari"))
        assertTrue(answer.contains("uji-parfum.pdf"))
        assertTrue(answer.contains("Halaman 3"))
        assertFalse(answer.contains("DATASET_UNAVAILABLE"))
    }

    @Test
    fun actualExtractiveWebAnswerRetainsTheSourceLink() {
        val answer = GroundedEvidenceAnswer.web(listOf(RetrievedChunk(
            RagChunk(1, 1, 0, "Ambroxan synthesis uses sclareol.", FloatArray(0)), "Ambroxide", 0.9f,
            sourceType = SourceType.WEB, sourceUrl = "https://en.wikipedia.org/wiki/Ambroxide",
            trustLevel = TrustLevel.WEB_UNTRUSTED
        )))
        assertTrue(answer.contains("sclareol"))
        assertTrue(answer.contains("[Buka artikel](https://en.wikipedia.org/wiki/Ambroxide)"))
    }
}
