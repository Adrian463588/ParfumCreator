package com.parfumecreator.data.local.database

import com.parfumecreator.domain.model.DatasetReadiness
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-local readiness signal for the bundled catalogue seed.
 * The database is usable before seeding finishes, but callers must not label
 * an empty catalogue as ready or fabricate a formula while this is seeding.
 */
@Singleton
class DatasetReadinessStore @Inject constructor() {
    private val _state = MutableStateFlow(DatasetReadiness.SEEDING)
    val state: StateFlow<DatasetReadiness> = _state.asStateFlow()

    fun markSeeding() {
        _state.value = DatasetReadiness.SEEDING
    }

    fun markReady() {
        _state.value = DatasetReadiness.READY
    }

    fun markEmpty() {
        _state.value = DatasetReadiness.EMPTY
    }

    fun markFailed() {
        _state.value = DatasetReadiness.FAILED
    }
}
