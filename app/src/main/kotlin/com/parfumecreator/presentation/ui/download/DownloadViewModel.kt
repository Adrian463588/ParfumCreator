package com.parfumecreator.presentation.ui.download

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parfumecreator.domain.model.DownloadBundle
import com.parfumecreator.domain.model.DownloadStatus
import com.parfumecreator.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadUiState(
    val bundles: List<DownloadBundle> = emptyList(),
    val safFolderUri: String? = null,
    val activeModelId: String = "llm_gemma_2b",
    val modelReadiness: Map<String, Boolean> = emptyMap(),
    val userMessage: String? = null
)

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val getDownloadsUseCase: GetDownloadsUseCase,
    private val startDownloadUseCase: StartDownloadUseCase,
    private val pauseDownloadUseCase: PauseDownloadUseCase,
    private val resumeDownloadUseCase: ResumeDownloadUseCase,
    private val cancelDownloadUseCase: CancelDownloadUseCase,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val saveSafStorageUriUseCase: SaveSafStorageUriUseCase,
    private val saveActiveModelIdUseCase: SaveActiveModelIdUseCase,
    private val saveActiveModelPathUseCase: SaveActiveModelPathUseCase,
    private val modelManager: com.parfumecreator.core.llm.ModelManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    init {
        // Observe persisted SAF storage folder and active model from Settings
        viewModelScope.launch {
            getSettingsUseCase().collect { settings ->
                modelManager.setActiveModelId(settings.activeModelId)
                settings.activeModelPath?.let { modelManager.setModelPath(it) }
                _uiState.update {
                    it.copy(
                        safFolderUri = settings.safStorageUri,
                        activeModelId = settings.activeModelId
                    )
                }
            }
        }

        viewModelScope.launch {
            getDownloadsUseCase().collect { bundleList ->
                val llmBundles = bundleList.filter { it.id.startsWith("llm_") }
                val readiness = llmBundles.associate { bundle ->
                    bundle.id to modelManager.isModelReady(bundle.id)
                }
                _uiState.update {
                    it.copy(
                        bundles = bundleList,
                        modelReadiness = readiness
                    )
                }
            }
        }
    }

    fun selectActiveModel(bundle: DownloadBundle) {
        viewModelScope.launch {
            if (!modelManager.isModelReady(bundle.id)) {
                _uiState.update {
                    it.copy(userMessage = "Model belum siap atau file belum lengkap. Selesaikan dan verifikasi download terlebih dahulu.")
                }
                return@launch
            }

            saveActiveModelIdUseCase(bundle.id)
            modelManager.setActiveModelId(bundle.id)
            bundle.destinationUri?.let { path ->
                saveActiveModelPathUseCase(path)
                modelManager.setModelPath(path)
            }
            _uiState.update {
                it.copy(
                    activeModelId = bundle.id,
                    modelReadiness = it.modelReadiness + (bundle.id to true),
                    userMessage = "Model '${bundle.name}' berhasil diaktifkan sebagai Agen AI Chatbot!"
                )
            }
        }
    }

    fun setSafFolder(uri: Uri) {
        val uriStr = uri.toString()
        viewModelScope.launch {
            saveSafStorageUriUseCase(uriStr)
            _uiState.update {
                it.copy(
                    safFolderUri = uriStr,
                    userMessage = "Folder penyimpanan model AI berhasil diatur dan disimpan ke SAF!"
                )
            }
        }
    }

    /**
     * Reports a folder that cannot be persisted for a background download.
     * The URI is deliberately not stored because a foreground-only grant
     * would disappear when WorkManager starts after the app is closed.
     */
    fun rejectSafFolder() {
        _uiState.update {
            it.copy(userMessage = "Folder tidak memberi izin SAF permanen baca/tulis. Pilih folder lain.")
        }
    }

    fun startOrResumeDownload(bundle: DownloadBundle) {
        viewModelScope.launch {
            val destFolder = _uiState.value.safFolderUri
            if (destFolder.isNullOrBlank()) {
                _uiState.update {
                    it.copy(userMessage = "Pilih folder SAF sebelum mengunduh model AI.")
                }
                return@launch
            }
            if (bundle.status == DownloadStatus.PAUSED) {
                resumeDownloadUseCase(bundle.id)
            } else {
                val result = startDownloadUseCase(bundle.id, destFolder)
                if (result.isFailure) {
                    _uiState.update { it.copy(userMessage = "Gagal memulai download: ${result.exceptionOrNull()?.localizedMessage}") }
                }
            }
        }
    }

    fun pauseDownload(bundleId: String) {
        viewModelScope.launch {
            pauseDownloadUseCase(bundleId)
        }
    }

    fun cancelDownload(bundleId: String) {
        viewModelScope.launch {
            cancelDownloadUseCase(bundleId)
        }
    }

    fun dismissUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
