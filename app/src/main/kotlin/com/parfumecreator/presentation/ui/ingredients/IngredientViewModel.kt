package com.parfumecreator.presentation.ui.ingredients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parfumecreator.domain.model.*
import com.parfumecreator.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.io.InputStream
import javax.inject.Inject

data class IngredientLibraryUiState(
    val ingredients: List<Ingredient> = emptyList(),
    val searchQuery: String = "",
    val selectedBrand: String? = null,
    val selectedNoteType: NoteType? = null,
    val selectedFamily: FragranceFamily? = null,
    val selectedIngredientForDetail: Ingredient? = null,
    val isAddDialogOpen: Boolean = false,
    val isImporting: Boolean = false,
    val userMessage: String? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class IngredientViewModel @Inject constructor(
    private val searchIngredientsUseCase: SearchIngredientsUseCase,
    private val saveCustomIngredientUseCase: SaveCustomIngredientUseCase,
    private val importIngredientsCsvUseCase: ImportIngredientsCsvUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedBrand = MutableStateFlow<String?>(null)
    private val _selectedNoteType = MutableStateFlow<NoteType?>(null)
    private val _selectedFamily = MutableStateFlow<FragranceFamily?>(null)

    private val _uiState = MutableStateFlow(IngredientLibraryUiState())
    val uiState: StateFlow<IngredientLibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                _searchQuery,
                _selectedBrand,
                _selectedNoteType,
                _selectedFamily
            ) { query, brand, noteType, family ->
                Tuple4(query, brand, noteType, family)
            }.flatMapLatest { (query, brand, noteType, family) ->
                searchIngredientsUseCase(query, brand, noteType, family)
            }.collect { list ->
                _uiState.update { it.copy(ingredients = list) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onBrandSelected(brand: String?) {
        _selectedBrand.value = brand
        _uiState.update { it.copy(selectedBrand = brand) }
    }

    fun onNoteTypeSelected(noteType: NoteType?) {
        _selectedNoteType.value = noteType
        _uiState.update { it.copy(selectedNoteType = noteType) }
    }

    fun onFamilySelected(family: FragranceFamily?) {
        _selectedFamily.value = family
        _uiState.update { it.copy(selectedFamily = family) }
    }

    fun openIngredientDetail(ingredient: Ingredient?) {
        _uiState.update { it.copy(selectedIngredientForDetail = ingredient) }
    }

    fun openAddDialog(open: Boolean) {
        _uiState.update { it.copy(isAddDialogOpen = open) }
    }

    fun saveCustomIngredient(
        name: String,
        type: IngredientType,
        brand: String?,
        noteType: NoteType,
        family: FragranceFamily,
        density: Double,
        description: String?
    ) {
        if (name.isBlank() || !density.isFinite() || density <= 0) {
            _uiState.update { it.copy(userMessage = "Nama bahan dan densitas positif g/ml wajib diisi.") }
            return
        }
        viewModelScope.launch {
            try {
                val newIngredient = Ingredient(
                    name = name.trim(),
                    type = type,
                    brand = brand?.trim()?.ifBlank { null },
                    noteType = noteType,
                    fragranceFamily = family,
                    densityGml = density,
                    description = description?.trim()?.ifBlank { null },
                    isCustom = true
                )
                saveCustomIngredientUseCase(newIngredient)
                _uiState.update {
                    it.copy(isAddDialogOpen = false, userMessage = "Bahan '${name}' berhasil ditambahkan!")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(userMessage = error.message ?: "Bahan tidak dapat disimpan.") }
            }
        }
    }

    fun importCsv(inputStream: InputStream) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            val result = importIngredientsCsvUseCase(inputStream)
            _uiState.update {
                it.copy(
                    isImporting = false,
                    userMessage = result.fold(
                        onSuccess = { count -> "Berhasil mengimpor $count bahan dari CSV!" },
                        onFailure = { err -> "Gagal impor: ${err.localizedMessage}" }
                    )
                )
            }
        }
    }

    fun dismissUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
