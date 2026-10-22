package com.parfumecreator.domain

import com.parfumecreator.domain.model.MeasurementUnit
import com.parfumecreator.domain.model.UserSettings
import com.parfumecreator.domain.repository.SettingsRepository
import com.parfumecreator.domain.usecase.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FakeSettingsRepository : SettingsRepository {
    private val state = MutableStateFlow(UserSettings())

    override fun getSettings(): Flow<UserSettings> = state

    override suspend fun saveSystemPrompt(prompt: String) {
        state.value = state.value.copy(systemPrompt = prompt)
    }

    override suspend fun saveDefaultUnit(unit: MeasurementUnit) {
        state.value = state.value.copy(defaultUnit = unit)
    }

    override suspend fun saveSafStorageUri(uri: String) {
        state.value = state.value.copy(safStorageUri = uri)
    }

    override suspend fun saveActiveModelPath(path: String) {
        state.value = state.value.copy(activeModelPath = path)
    }

    override suspend fun saveActiveModelId(modelId: String) {
        state.value = state.value.copy(activeModelId = modelId)
    }

    override suspend fun saveAllowInternetFallback(enabled: Boolean) {
        state.value = state.value.copy(allowInternetFallback = enabled)
    }
}

class SettingsUseCaseTest {

    private lateinit var fakeRepo: FakeSettingsRepository
    private lateinit var getSettingsUseCase: GetSettingsUseCase
    private lateinit var saveSettingsUseCase: SaveSettingsUseCase
    private lateinit var saveSafStorageUriUseCase: SaveSafStorageUriUseCase
    private lateinit var saveActiveModelPathUseCase: SaveActiveModelPathUseCase

    @Before
    fun setup() {
        fakeRepo = FakeSettingsRepository()
        getSettingsUseCase = GetSettingsUseCase(fakeRepo)
        saveSettingsUseCase = SaveSettingsUseCase(fakeRepo)
        saveSafStorageUriUseCase = SaveSafStorageUriUseCase(fakeRepo)
        saveActiveModelPathUseCase = SaveActiveModelPathUseCase(fakeRepo)
    }

    @Test
    fun testDefaultSettings() = runTest {
        val settings = getSettingsUseCase().first()
        assertTrue(settings.systemPrompt.contains("ParfumAI"))
        assertEquals(MeasurementUnit.GRAM, settings.defaultUnit)
        assertNull(settings.safStorageUri)
        assertNull(settings.activeModelPath)
    }

    @Test
    fun testSaveSettings_updatesPromptAndUnit() = runTest {
        val customPrompt = "Custom Perfumer Persona"
        val customUnit = MeasurementUnit.MILLILITER

        saveSettingsUseCase(customPrompt, customUnit)

        val updated = getSettingsUseCase().first()
        assertEquals(customPrompt, updated.systemPrompt)
        assertEquals(customUnit, updated.defaultUnit)
    }

    @Test
    fun testSaveSafStorageUri_persistsUri() = runTest {
        val safUri = "content://com.android.externalstorage.documents/tree/primary%3AModels"
        saveSafStorageUriUseCase(safUri)

        val updated = getSettingsUseCase().first()
        assertEquals(safUri, updated.safStorageUri)
    }

    @Test
    fun testSaveActiveModelPath_persistsPath() = runTest {
        val modelPath = "/data/user/0/com.parfumecreator/files/gemma-4-E2B-it.litertlm"
        saveActiveModelPathUseCase(modelPath)

        val updated = getSettingsUseCase().first()
        assertEquals(modelPath, updated.activeModelPath)
    }

    @Test
    fun testInternetFallbackDefaultsOffAndCanBeEnabled() = runTest {
        assertFalse(getSettingsUseCase().first().allowInternetFallback)

        SaveAllowInternetFallbackUseCase(fakeRepo)(true)

        assertTrue(getSettingsUseCase().first().allowInternetFallback)
    }
}
