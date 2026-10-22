package com.parfumecreator.core.download

import android.net.Uri
import java.util.Locale

/** Immutable allowlisted metadata for the Gemma LiteRT-LM artifacts. */
data class ModelArtifact(
    val id: String,
    val fileName: String,
    val expectedBytes: Long,
    val sha256: String,
    val pinnedUrl: String,
    val displayName: String
)

object ModelArtifactRegistry {
    private val artifacts = listOf(
        ModelArtifact(
            id = "llm_gemma_2b",
            fileName = "gemma-4-E2B-it.litertlm",
            expectedBytes = 2_588_147_712L,
            sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
            pinnedUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm",
            displayName = "Gemma 4 E2B-it (LiteRT-LM)"
        ),
        ModelArtifact(
            id = "llm_gemma_4b",
            fileName = "gemma-4-E4B-it.litertlm",
            expectedBytes = 3_659_530_240L,
            sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
            pinnedUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/28299f30ee4d43294517a4ac93abd6163412f07f/gemma-4-E4B-it.litertlm",
            displayName = "Gemma 4 E4B-it (High-Capability)"
        )
    )

    private val allowedHosts = setOf(
        "huggingface.co",
        "cdn-lfs.huggingface.co",
        "cdn-lfs-us-1.hf.co",
        "cdn-lfs-us-1.huggingface.co",
        // Current Hugging Face Xet-backed model artifacts redirect here.
        // Keep this exact-host allowlist; do not widen it to *.hf.co.
        "us.aws.cdn.hf.co"
    )

    fun forId(id: String): ModelArtifact? = artifacts.firstOrNull { it.id == id }

    fun all(): List<ModelArtifact> = artifacts

    /** Validates every redirect target before a worker follows it. */
    fun isAllowedDownloadUrl(rawUrl: String): Boolean {
        val uri = runCatching { Uri.parse(rawUrl.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT)
        if (
            scheme != "https" ||
            host !in allowedHosts ||
            (uri.port != -1 && uri.port != 443) ||
            uri.encodedAuthority?.contains('@') == true
        ) {
            return false
        }
        return true
    }

    fun expectedHash(bundleId: String): String? = forId(bundleId)?.sha256
}
