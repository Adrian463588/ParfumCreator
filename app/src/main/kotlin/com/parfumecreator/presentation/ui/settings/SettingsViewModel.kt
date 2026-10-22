package com.parfumecreator.presentation.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parfumecreator.core.llm.LiteRtLmRunner
import com.parfumecreator.domain.model.DownloadBundle
import com.parfumecreator.domain.model.MeasurementUnit
import com.parfumecreator.domain.model.ModelReadiness
import com.parfumecreator.domain.usecase.GetDownloadsUseCase
import com.parfumecreator.domain.usecase.GetSettingsUseCase
import com.parfumecreator.domain.usecase.SaveActiveModelIdUseCase
import com.parfumecreator.domain.usecase.SaveAllowInternetFallbackUseCase
import com.parfumecreator.domain.usecase.SaveSafStorageUriUseCase
import com.parfumecreator.domain.usecase.SaveSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

enum class PersonaPreset(val title: String, val prompt: String) {
    MASTER_NICHE(
        title = "Niche Perfumer",
        prompt = "Kamu adalah ParfumAI, Master Perfumer Niche terkemuka. Keahlianmu adalah meracik formula parfum elegan dan kompleks dari natural essential oil dan fragrance oil sintetis (Luzi, Parfarome, Macbrame). Jelaskan harmoni top/heart/base notes, prediksi SPL, dan sertakan blok data terstruktur JSON formula."
    ),
    COMMERCIAL(
        title = "Komersial & Mass",
        prompt = "Kamu adalah ParfumAI, Konsultan Perfumery Komersial. Fokus pada racikan parfum modern, wangi mewah tahan lama, mass-appealing, dan efisiensi biaya racikan. Berikan panduan persentase pelarut, fixative DPG, dan sertakan data JSON formula."
    ),
    NATURAL_ARTISAN(
        title = "Natural Artisan",
        prompt = "Kamu adalah ParfumAI, Artisan Perfumer minyak atsiri alami murni (Java Vetiver, Nilam/Patchouli, Cendana, Lavender, Bergamot). Jelaskan karakter terapeutik bahan alami, kestabilan aroma, dan paduan natural isolates dengan bibit konsentrat."
    )
}

data class SettingsUiState(
    val systemPrompt: String = LiteRtLmRunner.DEFAULT_SYSTEM_PROMPT,
    val defaultUnit: MeasurementUnit = MeasurementUnit.GRAM,
    val safStorageUri: String? = null,
    val safFolderName: String? = null,
    val activeModelId: String = "llm_gemma_2b",
    val availableModels: List<DownloadBundle> = emptyList(),
    val modelReadiness: Map<String, Boolean> = emptyMap(),
    val modelStatuses: Map<String, ModelReadiness> = emptyMap(),
    val isActiveModelReady: Boolean = false,
    val allowInternetFallback: Boolean = false,
    val isSavedMessageVisible: Boolean = false,
    val userMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getSettingsUseCase: GetSettingsUseCase,
    private val saveSettingsUseCase: SaveSettingsUseCase,
    private val saveSafStorageUriUseCase: SaveSafStorageUriUseCase,
    private val saveActiveModelIdUseCase: SaveActiveModelIdUseCase,
    private val saveActiveModelPathUseCase: com.parfumecreator.domain.usecase.SaveActiveModelPathUseCase,
    private val saveAllowInternetFallbackUseCase: SaveAllowInternetFallbackUseCase,
    private val getDownloadsUseCase: GetDownloadsUseCase,
    private val modelManager: com.parfumecreator.core.llm.ModelManager,
    private val liteRtLmRunner: LiteRtLmRunner
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getSettingsUseCase().collect { settings ->
                modelManager.setActiveModelId(settings.activeModelId)
                settings.activeModelPath?.let { modelManager.setModelPath(it) }
                val modelReady = modelManager.isModelReady(settings.activeModelId)
                val modelStatus = if (modelReady) ModelReadiness.VERIFYING else ModelReadiness.SELECTED
                _uiState.update {
                    it.copy(
                        systemPrompt = settings.systemPrompt,
                        defaultUnit = settings.defaultUnit,
                        safStorageUri = settings.safStorageUri,
                        activeModelId = settings.activeModelId,
                        modelReadiness = it.modelReadiness + (settings.activeModelId to modelReady),
                        modelStatuses = it.modelStatuses + (settings.activeModelId to modelStatus),
                        isActiveModelReady = modelStatus == ModelReadiness.READY,
                        allowInternetFallback = settings.allowInternetFallback,
                        safFolderName = settings.safStorageUri?.let { uriStr ->
                            try {
                                Uri.parse(uriStr).lastPathSegment ?: "Folder Terpilih"
                            } catch (e: Exception) {
                                "Folder Terpilih"
                            }
                        }
                    )
                }
                if (modelReady) {
                    // Keep the model visibly in VERIFYING until the native
                    // LiteRT runtime accepts the checksum-verified artifact.
                    viewModelScope.launch {
                        val runtimeReady = liteRtLmRunner.verifyRuntimeReady(settings.activeModelId)
                        _uiState.update { state ->
                            if (state.activeModelId != settings.activeModelId) {
                                state
                            } else {
                                state.copy(
                                    modelStatuses = state.modelStatuses + (
                                        settings.activeModelId to if (runtimeReady) {
                                            ModelReadiness.READY
                                        } else {
                                            ModelReadiness.FAILED
                                        }
                                    ),
                                    isActiveModelReady = runtimeReady
                                )
                            }
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            getDownloadsUseCase().collect { bundles ->
                val llmBundles = bundles.filter { b -> b.id.startsWith("llm_") }
                val readiness = llmBundles.associate { bundle ->
                    bundle.id to modelManager.isModelReady(bundle.id)
                }
                val statuses = llmBundles.associate { bundle ->
                    val checksumReady = readiness[bundle.id] == true
                    val status = when {
                        bundle.status == com.parfumecreator.domain.model.DownloadStatus.DOWNLOADING ||
                            bundle.status == com.parfumecreator.domain.model.DownloadStatus.QUEUED ||
                            bundle.status == com.parfumecreator.domain.model.DownloadStatus.PAUSED -> ModelReadiness.DOWNLOADING
                        bundle.status == com.parfumecreator.domain.model.DownloadStatus.FAILED -> ModelReadiness.FAILED
                        checksumReady -> if (bundle.id == _uiState.value.activeModelId) {
                            _uiState.value.modelStatuses[bundle.id] ?: ModelReadiness.VERIFYING
                        } else ModelReadiness.VERIFYING
                        else -> ModelReadiness.UNAVAILABLE
                    }
                    bundle.id to status
                }
                _uiState.update {
                    val mergedStatuses = it.modelStatuses + statuses
                    it.copy(
                        availableModels = llmBundles,
                        modelReadiness = readiness,
                        modelStatuses = mergedStatuses,
                        isActiveModelReady = mergedStatuses[it.activeModelId] == ModelReadiness.READY
                    )
                }
            }
        }
    }

    fun selectActiveModel(modelId: String) {
        viewModelScope.launch {
            if (!modelManager.isModelReady(modelId)) {
                _uiState.update {
                    it.copy(userMessage = "Model belum siap atau file belum lengkap. Selesaikan download model terlebih dahulu.")
                }
                return@launch
            }

            _uiState.update {
                it.copy(modelStatuses = it.modelStatuses + (modelId to ModelReadiness.VERIFYING))
            }
            val runtimeReady = liteRtLmRunner.verifyRuntimeReady(modelId)
            if (!runtimeReady) {
                _uiState.update {
                    it.copy(
                        modelStatuses = it.modelStatuses + (modelId to ModelReadiness.FAILED),
                        userMessage = "Checksum model valid, tetapi runtime LiteRT belum dapat diinisialisasi. Model tidak diaktifkan."
                    )
                }
                return@launch
            }

            saveActiveModelIdUseCase(modelId)
            modelManager.setActiveModelId(modelId)
            val matchedBundle = _uiState.value.availableModels.find { it.id == modelId }
            matchedBundle?.destinationUri?.let { uri ->
                saveActiveModelPathUseCase(uri)
                modelManager.setModelPath(uri)
            }
            _uiState.update {
                it.copy(
                    activeModelId = modelId,
                    modelReadiness = it.modelReadiness + (modelId to true),
                    modelStatuses = it.modelStatuses + (modelId to ModelReadiness.READY),
                    isActiveModelReady = true,
                    userMessage = "Model Chatbot aktif berhasil diubah!"
                )
            }
        }
    }

    fun updateSystemPrompt(newPrompt: String) {
        _uiState.update { it.copy(systemPrompt = newPrompt) }
    }

    fun updateDefaultUnit(unit: MeasurementUnit) {
        _uiState.update { it.copy(defaultUnit = unit) }
        viewModelScope.launch(Dispatchers.IO) {
            saveSettingsUseCase(_uiState.value.systemPrompt, unit)
            _uiState.update { it.copy(userMessage = "Satuan default disimpan: ${unit.name} (${unit.symbol})") }
        }
    }

    fun resetSystemPrompt() {
        _uiState.update { it.copy(systemPrompt = LiteRtLmRunner.DEFAULT_SYSTEM_PROMPT) }
    }

    fun setAllowInternetFallback(enabled: Boolean) {
        _uiState.update { it.copy(allowInternetFallback = enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            saveAllowInternetFallbackUseCase(enabled)
            _uiState.update {
                it.copy(
                    userMessage = if (enabled) {
                        "Internet fallback aktif: hanya host kurasi, dengan citation dan warning trust."
                    } else {
                        "Internet fallback dimatikan. Chatbot hanya memakai sumber lokal."
                    }
                )
            }
        }
    }

    fun applyPreset(preset: PersonaPreset) {
        _uiState.update { it.copy(systemPrompt = preset.prompt) }
    }

    fun savePersonaPrompt(context: Context? = null) {
        val prompt = _uiState.value.systemPrompt
        val unit = _uiState.value.defaultUnit
        val safUriStr = _uiState.value.safStorageUri

        viewModelScope.launch(Dispatchers.IO) {
            saveSettingsUseCase(prompt, unit)

            var safSaved = false
            if (context != null && !safUriStr.isNullOrBlank()) {
                try {
                    val treeUri = Uri.parse(safUriStr)
                    val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
                    if (pickedDir != null && pickedDir.canWrite()) {
                        val existingFile = pickedDir.findFile("persona_parfum_ai.json")
                        val file = existingFile ?: pickedDir.createFile("application/json", "persona_parfum_ai.json")
                        file?.uri?.let { fileUri ->
                            val json = createPersonaJson(prompt)
                            context.contentResolver.openOutputStream(fileUri)?.use { out ->
                                out.write(json.toByteArray(Charsets.UTF_8))
                                out.flush()
                            }
                            safSaved = true
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SettingsViewModel", "Unable to export persona to SAF", e)
                }
            }

            _uiState.update {
                it.copy(
                    userMessage = if (safSaved) {
                        "Persona AI berhasil disimpan ke database & folder SAF!"
                    } else {
                        "Persona AI berhasil disimpan & aktif untuk Chatbot!"
                    }
                )
            }
        }
    }

    fun onSafFolderSelected(context: Context, treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Persist folder access permission
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)

                saveSafStorageUriUseCase(treeUri.toString())

                val folderDoc = DocumentFile.fromTreeUri(context, treeUri)
                val folderName = folderDoc?.name ?: treeUri.lastPathSegment ?: "Folder SAF"

                // Auto-save persona into this SAF folder
                val prompt = _uiState.value.systemPrompt
                val existingFile = folderDoc?.findFile("persona_parfum_ai.json")
                val file = existingFile ?: folderDoc?.createFile("application/json", "persona_parfum_ai.json")
                file?.uri?.let { fileUri ->
                    val json = createPersonaJson(prompt)
                    context.contentResolver.openOutputStream(fileUri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                }

                _uiState.update {
                    it.copy(
                        safStorageUri = treeUri.toString(),
                        safFolderName = folderName,
                        userMessage = "Folder SAF ($folderName) berhasil dipilih & Persona AI otomatis tersimpan!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(userMessage = "Gagal mengakses folder SAF: ${e.localizedMessage}") }
            }
        }
    }

    fun exportPersonaToUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prompt = _uiState.value.systemPrompt
                val json = createPersonaJson(prompt)

                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(json.toByteArray(Charsets.UTF_8))
                    output.flush()
                }
                _uiState.update { it.copy(userMessage = "Persona AI berhasil diekspor ke file SAF!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(userMessage = "Gagal mengekspor persona: ${e.localizedMessage}") }
            }
        }
    }

    fun importPersonaFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader().use { it.readText() }
                } ?: throw Exception("File kosong atau tidak dapat dibaca")

                val importedPrompt = if (content.trim().startsWith("{")) {
                    val json = JSONObject(content)
                    json.optString("systemPrompt", content.trim())
                } else {
                    content.trim()
                }

                if (importedPrompt.isNotBlank()) {
                    val unit = _uiState.value.defaultUnit
                    saveSettingsUseCase(importedPrompt, unit)
                    _uiState.update {
                        it.copy(
                            systemPrompt = importedPrompt,
                            userMessage = "Persona AI berhasil diimpor & diaktifkan!"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(userMessage = "Gagal mengimpor persona: ${e.localizedMessage}") }
            }
        }
    }

    private fun createPersonaJson(prompt: String): String {
        return JSONObject().apply {
            put("version", 1)
            put("type", "ai_perfumer_persona")
            put("title", "AI Perfumer Persona Configuration")
            put("systemPrompt", prompt)
            put("exportedAt", System.currentTimeMillis())
        }.toString(2)
    }

    fun saveSettings() {
        val state = _uiState.value
        viewModelScope.launch {
            saveSettingsUseCase(state.systemPrompt, state.defaultUnit)
            _uiState.update { it.copy(isSavedMessageVisible = true) }
        }
    }

    fun dismissSavedMessage() {
        _uiState.update { it.copy(isSavedMessageVisible = false) }
    }

    fun dismissUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
