package com.parfumecreator.core.llm

import com.parfumecreator.domain.model.RetrievedChunk

/** Extractive answers used by the real offline path when the LLM cannot run. */
object GroundedEvidenceAnswer {
    fun local(evidence: List<RetrievedChunk>, status: String? = null): String = buildString {
        append("### Jawaban dari referensi lokal\n\n")
        status?.let { append("> Status inference: ").append(it).append("\n\n") }
        append("Berikut kutipan sumber lokal yang relevan:\n\n")
        evidence.take(5).forEach { reference ->
            append("**").append(reference.documentName).append("**\n")
            append(reference.chunk.text.replace(Regex("\\s+"), " ").trim().take(1_200)).append("\n")
            reference.chunk.pageStart?.let { append("> Halaman ").append(it).append("\n") }
            append("\n")
        }
    }

    fun web(evidence: List<RetrievedChunk>, status: String? = null): String = buildString {
        append("### Referensi perfumery\n\n")
        status?.let { append("> Status inference: ").append(it).append("\n\n") }
        append("Referensi lokal belum cukup; berikut bagian sumber publik yang relevan.\n\n")
        evidence.take(3).forEach { reference ->
            append("**").append(reference.documentName.removePrefix(CompanionRetrievalPolicy.INTERNET_SOURCE_PREFIX))
                .append("**\n")
            append(reference.chunk.text.replace(Regex("\\s+"), " ").trim().take(2_400)).append("\n")
            val url = reference.sourceUrl ?: reference.sourceUri?.takeIf { it.startsWith("https://") }
            url?.let { append("> Sumber: [Buka artikel](").append(it).append(")\n") }
            append("\n")
        }
        append("Referensi web belum memverifikasi keamanan formula atau batas IFRA; periksa dokumen supplier.")
    }
}
