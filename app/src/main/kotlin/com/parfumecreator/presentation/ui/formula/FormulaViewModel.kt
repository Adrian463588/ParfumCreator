package com.parfumecreator.presentation.ui.formula

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parfumecreator.core.formula.FormulaTemplateLoader
import com.parfumecreator.domain.engine.FormulaCalculationEngine
import com.parfumecreator.domain.model.*
import com.parfumecreator.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

/** Unsaved preview; changing bindings or batch never changes the active formula. */
data class TemplateDraft(
    val template: FormulaTemplate,
    val batchText: String,
    val bindings: Map<Int, Long>,
    val acknowledgeAdaptation: Boolean = false,
    val application: TemplateApplication? = null,
    val error: String? = null
)

data class FormulaUiState(
    val formulaName: String = "Untitled Formulation",
    val items: List<FormulaIngredientInput> = emptyList(),
    val calculation: FormulaCalculationResult? = null,
    val savedFormulas: List<Formula> = emptyList(),
    val templates: List<FormulaTemplate> = emptyList(),
    val isLoadingTemplates: Boolean = true,
    val templateLoadErrors: List<String> = emptyList(),
    val templateDraft: TemplateDraft? = null,
    val templatePickerIndex: Int? = null,
    val templateCandidates: List<Ingredient> = emptyList(),
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
    private val validateRecommendedFormulaUseCase: ValidateRecommendedFormulaUseCase,
    private val applyFormulaTemplateUseCase: ApplyFormulaTemplateUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(FormulaUiState())
    val uiState: StateFlow<FormulaUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val (templates, errors) = withContext(Dispatchers.IO) {
                templateLoader.getTemplates() to templateLoader.errors
            }
            _uiState.update { it.copy(templates = templates, templateLoadErrors = errors, isLoadingTemplates = false) }
        }

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
                _uiState.value.templateDraft?.let(::updateTemplateDraft)
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
        _uiState.update { it.copy(
            isTemplatesDialogVisible = show, templateDraft = null,
            templatePickerIndex = null, templateCandidates = emptyList()
        ) }
    }

    fun previewTemplate(template: FormulaTemplate) {
        val batch = String.format(Locale.ROOT, "%.6f", template.ingredients.sumOf { it.amount })
            .trimEnd('0').trimEnd('.')
        updateTemplateDraft(TemplateDraft(
            template, batch, applyFormulaTemplateUseCase.initialBindings(template, _uiState.value.allIngredients)
        ))
    }

    fun closeTemplatePreview() {
        _uiState.update { it.copy(templateDraft = null, templatePickerIndex = null, templateCandidates = emptyList()) }
    }

    fun setTemplateBatch(value: String) {
        _uiState.value.templateDraft?.let { updateTemplateDraft(it.copy(batchText = value)) }
    }

    fun acknowledgeTemplateAdaptation(value: Boolean) {
        _uiState.value.templateDraft?.let { updateTemplateDraft(it.copy(acknowledgeAdaptation = value)) }
    }

    fun chooseTemplateIngredient(index: Int) {
        val item = _uiState.value.templateDraft?.template?.ingredients?.getOrNull(index) ?: return
        val candidates = applyFormulaTemplateUseCase.eligibleIngredients(item, _uiState.value.allIngredients)
        _uiState.update { it.copy(templatePickerIndex = index, templateCandidates = candidates) }
    }

    fun dismissTemplateIngredientPicker() {
        _uiState.update { it.copy(templatePickerIndex = null, templateCandidates = emptyList()) }
    }

    fun bindTemplateIngredient(ingredient: Ingredient) {
        val state = _uiState.value
        val draft = state.templateDraft ?: return
        val index = state.templatePickerIndex ?: return
        if (ingredient.id !in state.templateCandidates.map { it.id }) return
        dismissTemplateIngredientPicker()
        updateTemplateDraft(draft.copy(
            bindings = draft.bindings + (index to ingredient.id), acknowledgeAdaptation = false
        ))
    }

    fun applyTemplate() {
        val state = _uiState.value
        val draft = state.templateDraft ?: return
        val result = applyFormulaTemplateUseCase(
            draft.template, state.allIngredients, draft.bindings,
            draft.batchText.replace(',', '.').toDoubleOrNull() ?: Double.NaN, draft.acknowledgeAdaptation
        )
        result.fold(
            onSuccess = { application ->
                _uiState.update { it.copy(
                    formulaName = draft.template.name + if (application.isAdaptation) " (Adaptasi)" else "",
                    items = application.inputs, calculation = application.calculation, calculationError = null,
                    notes = application.notes, activeFormulaId = null, isTemplatesDialogVisible = false,
                    templateDraft = null, templatePickerIndex = null, templateCandidates = emptyList(),
                    isSavedFormulasSheetVisible = false, isWheelDetailVisible = false,
                    userMessage = "Resep dimuat ke editor. Tinjau sebelum menyimpan; belum siap digunakan di kulit."
                ) }
            },
            onFailure = { error ->
                _uiState.update { it.copy(templateDraft = draft.copy(error = error.message)) }
            }
        )
    }

    private fun updateTemplateDraft(draft: TemplateDraft) {
        val bindings = applyFormulaTemplateUseCase.initialBindings(draft.template, _uiState.value.allIngredients) + draft.bindings
        val result = applyFormulaTemplateUseCase(
            draft.template, _uiState.value.allIngredients, bindings,
            draft.batchText.replace(',', '.').toDoubleOrNull() ?: Double.NaN,
            acknowledgeAdaptation = true
        )
        _uiState.update { it.copy(templateDraft = draft.copy(
            bindings = bindings, application = result.getOrNull(), error = result.exceptionOrNull()?.message
        )) }
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
