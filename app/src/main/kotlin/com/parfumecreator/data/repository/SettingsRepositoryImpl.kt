package com.parfumecreator.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.parfumecreator.domain.model.MeasurementUnit
import com.parfumecreator.domain.model.UserSettings
import com.parfumecreator.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private object Keys {
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val DEFAULT_UNIT = stringPreferencesKey("default_unit")
        val SAF_STORAGE_URI = stringPreferencesKey("saf_storage_uri")
        val ACTIVE_MODEL_PATH = stringPreferencesKey("active_model_path")
        val ACTIVE_MODEL_ID = stringPreferencesKey("active_model_id")
        val ALLOW_INTERNET_FALLBACK = booleanPreferencesKey("allow_internet_fallback")
    }

    override fun getSettings(): Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            systemPrompt = prefs[Keys.SYSTEM_PROMPT] ?: UserSettings().systemPrompt,
            defaultUnit = prefs[Keys.DEFAULT_UNIT]?.let { MeasurementUnit.fromString(it) } ?: MeasurementUnit.GRAM,
            safStorageUri = prefs[Keys.SAF_STORAGE_URI],
            activeModelPath = prefs[Keys.ACTIVE_MODEL_PATH],
            activeModelId = prefs[Keys.ACTIVE_MODEL_ID] ?: "llm_gemma_2b",
            allowInternetFallback = prefs[Keys.ALLOW_INTERNET_FALLBACK] ?: false
        )
    }

    override suspend fun saveSystemPrompt(prompt: String) {
        dataStore.edit { it[Keys.SYSTEM_PROMPT] = prompt }
    }

    override suspend fun saveDefaultUnit(unit: MeasurementUnit) {
        dataStore.edit { it[Keys.DEFAULT_UNIT] = unit.name }
    }

    override suspend fun saveSafStorageUri(uri: String) {
        dataStore.edit { it[Keys.SAF_STORAGE_URI] = uri }
    }

    override suspend fun saveActiveModelPath(path: String) {
        dataStore.edit { it[Keys.ACTIVE_MODEL_PATH] = path }
    }

    override suspend fun saveActiveModelId(modelId: String) {
        dataStore.edit { it[Keys.ACTIVE_MODEL_ID] = modelId }
    }

    override suspend fun saveAllowInternetFallback(enabled: Boolean) {
        dataStore.edit { it[Keys.ALLOW_INTERNET_FALLBACK] = enabled }
    }
}
