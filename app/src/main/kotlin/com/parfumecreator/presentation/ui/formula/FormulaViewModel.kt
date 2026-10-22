package com.parfumecreator.presentation.ui.formula

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parfumecreator.core.formula.FormulaTemplateLoader
import com.parfumecreator.domain.engine.CatalogIngredientResolver
import com.parfumecreator.domain.engine.FormulaCalculationEngine
import com.parfumecreator.domain.model.*
import com.parfumecreator.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

data class FormulaUiState(
    val formulaName: String = "Untitled Formulation",
    val items: List<FormulaIngredientInput> = emptyList(),
    val calculation: FormulaCalculationResult? = null,
    val savedFormulas: List<Formula> = emptyList(),
    val templates: List<FormulaTemplate> = emptyList(),
    val allIngredients: List<Ingredient> = emptyList(),
    val isIngredientPickerVisible: Boolean = false,
    val isSaveDialogVisible: Boolean = false,
    val isSavedFormulasSheetVisible: Boolean = false,
    val isTemplatesDialogVisible: Boolean = false,
    val isWheelDetailVisible: Boolean = false,
    val notes: String = "",
    val activeFormulaId: Long? = null,
    val userMessage: String? = null,
    val calculationError: String? = null,
    val isSaving: Boolean = false
)

@HiltViewModel
class FormulaViewModel @Inject constructor(
    private val calculateFormulaUseCase: CalculateFormulaUseCase,
    private val saveFormulaUseCase: SaveFormulaUseCase,
    private val getFormulasUseCase: GetFormulasUseCase,
    private val getIngredientsUseCase: GetIngredientsUseCase,
    private val deleteFormulaUseCase: DeleteFormulaUseCase,
    private val templateLoader: FormulaTemplateLoader,
    private val validateRecommendedFormulaUseCase: ValidateRecommendedFormulaUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(FormulaUiState())
    val uiState: StateFlow<FormulaUiState> = _uiState.asStateFlow()

    init {
        // Load master templates
        val loadedTemplates = templateLoader.getTemplates()
        _uiState.update { it.copy(templates = loadedTemplates) }

        // Load saved formulas
        viewModelScope.launch {
            getFormulasUseCase().collect { formulas ->
                _uiState.update { it.copy(savedFormulas = formulas) }
                if (_uiState.value.items.isEmpty() && formulas.isNotEmpty() && _uiState.value.activeFormulaId == null) {
                    loadSavedFormula(formulas.first())
                }
            }
        }
        // Load all available ingredients
        viewModelScope.launch {
            getIngredientsUseCase().collect { ingredients ->
                _uiState.update { it.copy(allIngredients = ingredients) }
            }
        }
    }

    fun setFormulaName(name: String) {
        _uiState.update { it.copy(formulaName = name) }
    }

    fun importRecommendation(recommendation: RecommendedFormula) {
        val result = validateRecommendedFormulaUseCase(recommendation, _uiState.value.allIngredients)
        result.fold(
            onSuccess = { inputs ->
                _uiState.update { it.copy(
                    formulaName = recommendation.name, notes = recommendation.description.orEmpty(),
                    activeFormulaId = null, userMessage = "Rekomendasi berhasil dimuat."
                ) }
                recalculate(inputs)
            },
            onFailure = { error ->
                _uiState.update { it.copy(userMessage = "Impor dibatalkan: ${error.message}") }
            }
        )
    }

    fun setNotes(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun addIngredient(ingredient: Ingredient, amount: Double = 1.0, unit: MeasurementUnit = MeasurementUnit.GRAM) {
        if (ingredient.id <= 0L || !amount.isFinite() || amount <= 0.0) {
            _uiState.update { it.copy(userMessage = "Pilih bahan katalog dengan jumlah positif yang valid.") }
            return
        }
        val currentItems = _uiState.value.items.toMutableList()
        val existingIndex = currentItems.indexOfFirst { it.ingredient.id == ingredient.id }
        if (existingIndex >= 0) {
            val existing = currentItems[existingIndex]
            val convertedAmount = FormulaCalculationEngine.convertAmount(
                amount, unit, existing.unit, ingredient.densityGml
            )
            if (convertedAmount == null || !(existing.amount + convertedAmount).isFinite()) {
                _uiState.update { it.copy(userMessage = "Jumlah tidak dapat dikonversi. Periksa jumlah dan densitas bahan.") }
                return
            }
            currentItems[existingIndex] = existing.copy(amount = existing.amount + convertedAmount)
        } else {
            currentItems.add(FormulaIngredientInput(ingredient, amount, unit))
        }
        val error = calculateFormulaUseCase(currentItems).validationError
        if (error != null) {
            _uiState.update { it.copy(userMessage = error) }
            return
        }
        recalculate(currentItems)
    }

    fun updateItemAmount(index: Int, amount: Double) {
        val currentItems = _uiState.value.items.toMutableList()
        if (index in currentItems.indices) {
            currentItems[index] = currentItems[index].copy(amount = amount)
            recalculate(currentItems)
        }
    }

    fun updateItemUnit(index: Int, unit: MeasurementUnit) {
        val currentItems = _uiState.value.items.toMutableList()
        if (index in currentItems.indices) {
            val item = currentItems[index]
            val converted = FormulaCalculationEngine.convertAmount(
                item.amount, item.unit, unit, item.ingredient.densityGml
            )
            if (converted == null) {
                _uiState.update { it.copy(userMessage = "Satuan belum diubah: periksa jumlah dan densitas bahan.") }
                return
            }
            currentItems[index] = item.copy(amount = converted, unit = unit)
            recalculate(currentItems)
        }
    }

    fun removeItem(index: Int) {
        val currentItems = _uiState.value.items.toMutableList()
        if (index in currentItems.indices) {
            currentItems.removeAt(index)
            recalculate(currentItems)
        }
    }

    fun clearFormula() {
        _uiState.update {
            it.copy(
                formulaName = "Untitled Formulation",
                items = emptyList(),
                calculation = null,
                calculationError = null,
                notes = "",
                activeFormulaId = null,
                isWheelDetailVisible = false
            )
        }
    }

    fun loadSavedFormula(formula: Formula) {
        val inputs = formula.ingredients.map {
            FormulaIngredientInput(
                ingredient = it.ingredient,
                amount = it.amount,
                unit = it.unit
            )
        }
        _uiState.update {
            it.copy(
                formulaName = formula.name,
                items = inputs,
                notes = formula.notes ?: "",
                activeFormulaId = formula.id,
                isSavedFormulasSheetVisible = false
            )
        }
        recalculate(inputs)
    }

    fun duplicateFormula(formula: Formula) {
        val inputs = formula.ingredients.map {
            FormulaIngredientInput(
                ingredient = it.ingredient,
                amount = it.amount,
                unit = it.unit
            )
        }
        _uiState.update {
            it.copy(
                formulaName = "${formula.name} (Salinan)",
                items = inputs,
                notes = formula.notes ?: "",
                activeFormulaId = null,
                isSavedFormulasSheetVisible = false,
                userMessage = "Formula disalin sebagai formula baru!"
            )
        }
        recalculate(inputs)
    }

    fun deleteFormula(formulaId: Long) {
        viewModelScope.launch {
            deleteFormulaUseCase(formulaId)
            if (_uiState.value.activeFormulaId == formulaId) {
                clearFormula()
            }
            _uiState.update { it.copy(userMessage = "Formula berhasil dihapus.") }
        }
    }

    fun showIngredientPicker(show: Boolean) {
        _uiState.update { it.copy(isIngredientPickerVisible = show) }
    }

    fun showSaveDialog(show: Boolean) {
        _uiState.update { it.copy(isSaveDialogVisible = show) }
    }

    fun showSavedFormulasSheet(show: Boolean) {
        _uiState.update { it.copy(isSavedFormulasSheetVisible = show) }
    }

    fun showTemplatesDialog(show: Boolean) {
        _uiState.update { it.copy(isTemplatesDialogVisible = show) }
    }

    fun loadTemplate(template: FormulaTemplate) {
        val availableIngredients = _uiState.value.allIngredients
        val resolved = template.ingredients.map { item ->
            item to CatalogIngredientResolver.resolve(item.name, item.type, availableIngredients, item.brand)
        }
        val unresolved = resolved.filter { it.second == null }.map { it.first.name }

        if (unresolved.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    isTemplatesDialogVisible = false,
                    userMessage = "Template belum dimuat: bahan katalog tidak tersedia atau ambigu (" +
                        unresolved.joinToString(", ") +
                        "). Tidak ada pengganti otomatis."
                )
            }
            return
        }

        val inputs = resolved.map { (item, ingredient) ->
            val unit = when (item.unit.trim().lowercase(Locale.ROOT)) {
                "g", "gram" -> MeasurementUnit.GRAM
                "mg", "milligram" -> MeasurementUnit.MILLIGRAM
                "ml", "milliliter" -> MeasurementUnit.MILLILITER
                else -> {
                    _uiState.update { it.copy(userMessage = "Template belum dimuat: satuan ${item.unit} tidak didukung.") }
                    return
                }
            }
            FormulaIngredientInput(ingredient ?: return, item.amount, unit)
        }
        val calculation = calculateFormulaUseCase(inputs)
        val error = calculation.validationError
        if (error != null || inputs.isEmpty() || inputs.any { it.amount <= 0.0 } ||
            !template.concentrationPct.isFinite() || template.concentrationPct <= 0.0 ||
            template.concentrationPct > 100.0 || calculation.fragranceWeightG <= 0.0 ||
            abs(calculation.concentrationPct - template.concentrationPct) > 0.1
        ) {
            _uiState.update {
                it.copy(userMessage = error ?: "Template belum dimuat: jumlah atau konsentrasi tidak sesuai komposisi katalog.")
            }
            return
        }
        _uiState.update {
            it.copy(
                formulaName = template.name,
                notes = template.description,
                activeFormulaId = null,
                isTemplatesDialogVisible = false,
                userMessage = "Template '${template.name}' berhasil dimuat!"
            )
        }
        recalculate(inputs)
    }

    fun showWheelDetail(show: Boolean) {
        _uiState.update { it.copy(isWheelDetailVisible = show) }
    }

    fun saveFormula() {
        val state = _uiState.value
        if (state.isSaving) return
        val calc = calculateFormulaUseCase(state.items)
        val error = calc.validationError ?: when {
            state.items.isEmpty() -> "Tambahkan bahan sebelum menyimpan formula."
            state.items.any { it.ingredient.id <= 0L || it.amount <= 0.0 } ->
                "Setiap bahan harus memiliki ID katalog dan jumlah positif."
            calc.fragranceWeightG <= 0.0 -> "Formula hanya berisi pelarut. Tambahkan bahan aroma."
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(userMessage = error) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val existingFormula = state.savedFormulas.firstOrNull { it.id == state.activeFormulaId }
                val formula = Formula(
                    id = state.activeFormulaId ?: 0,
                    name = state.formulaName.ifBlank { "Untitled Formulation" },
                    totalWeightG = calc.totalWeightG,
                    concentrationPct = calc.concentrationPct,
                    concentrationType = calc.concentrationType,
                    spl = calc.spl,
                    splLabel = calc.splLabel,
                    fragranceWheelPrimary = calc.fragranceWheelPrimary,
                    fragranceWheelSub = calc.dominantSubFamily,
                    notes = state.notes,
                    ingredients = calc.calculatedItems.map {
                        FormulaIngredient(
                            ingredient = it.ingredient,
                            amount = it.amount,
                            unit = it.unit,
                            amountInGram = it.amountInGram,
                            percentage = it.percentageOfTotal
                        )
                    },
                    createdAt = existingFormula?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                val savedId = saveFormulaUseCase(formula)
                _uiState.update {
                    val sameDraft = it.activeFormulaId == state.activeFormulaId && it.items == state.items &&
                        it.formulaName == state.formulaName && it.notes == state.notes
                    it.copy(
                        activeFormulaId = if (sameDraft) savedId else it.activeFormulaId,
                        isSaveDialogVisible = if (sameDraft) false else it.isSaveDialogVisible,
                        userMessage = "Formulasi berhasil disimpan ke database!"
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(userMessage = "Formula gagal disimpan. Silakan coba lagi.") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun dismissUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun recalculate(items: List<FormulaIngredientInput>) {
        val calc = calculateFormulaUseCase(items)
        val error = calc.validationError
        _uiState.update {
            it.copy(
                items = items,
                calculation = calc.takeIf { error == null },
                calculationError = error,
                isWheelDetailVisible = it.isWheelDetailVisible && error == null &&
                    calc.fragranceWheelResult.hasFragranceProfile
            )
        }
    }
}
