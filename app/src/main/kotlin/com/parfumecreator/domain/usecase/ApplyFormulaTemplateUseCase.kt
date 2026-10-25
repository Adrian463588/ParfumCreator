package com.parfumecreator.domain.usecase

import com.parfumecreator.domain.engine.CatalogIngredientResolver
import com.parfumecreator.domain.engine.FormulaCalculationEngine
import com.parfumecreator.domain.model.*
import javax.inject.Inject
import java.util.Locale
import kotlin.math.abs

/** A validated editor draft. No catalog mutation or persistence is performed by template application. */
data class TemplateApplication(
    val inputs: List<FormulaIngredientInput>,
    val calculation: FormulaCalculationResult,
    val notes: String,
    val isAdaptation: Boolean
)

/** Resolves identities and validates a full mass-scaled recipe before the editor changes. */
class ApplyFormulaTemplateUseCase @Inject constructor() {
    fun initialBindings(template: FormulaTemplate, catalog: List<Ingredient>): Map<Int, Long> =
        template.ingredients.mapIndexedNotNull { index, item ->
            resolve(item, catalog)?.let { index to it.id }
        }.toMap()

    /** Manual selection may change aroma identity, never the source type, supplier or carrier role. */
    fun eligibleIngredients(item: FormulaTemplateItem, catalog: List<Ingredient>): List<Ingredient> =
        catalog.filter { ingredient ->
            ingredient.id > 0 && ingredient.name.isNotBlank() && ingredient.type == item.type &&
                (item.brand == null || CatalogIngredientResolver.matchesBrand(ingredient.brand, item.brand)) &&
                ingredient.isSolventOrDiluent == Ingredient.isSolventName(item.name)
        }

    operator fun invoke(
        template: FormulaTemplate,
        catalog: List<Ingredient>,
        bindings: Map<Int, Long>,
        targetGrams: Double,
        acknowledgeAdaptation: Boolean = false
    ): Result<TemplateApplication> = try {
        require(template.id > 0 && template.name.isNotBlank() && template.ingredients.isNotEmpty()) { "Template kosong/tidak valid." }
        require(targetGrams.isFinite() && targetGrams in 0.000001..100_000.0) { "Batch harus 0,000001–100.000 g." }
        require(bindings.keys.all { it in template.ingredients.indices }) { "Slot bahan tidak valid." }
        require(template.concentrationPct.isFinite() && template.concentrationPct > 0 && template.concentrationPct <= 100) {
            "Konsentrasi template tidak valid."
        }
        val referenceWeights = template.ingredients.map { item ->
            require(item.amount.isFinite() && item.amount > 0.0) { "Jumlah bahan template harus positif." }
            when (item.unit.trim().lowercase(Locale.ROOT)) {
                "g", "gram" -> item.amount
                "mg", "milligram" -> item.amount / 1_000.0
                else -> throw IllegalArgumentException("Template harus memakai g/mg tanpa asumsi density.")
            }
        }
        val referenceTotal = referenceWeights.sum()
        require(referenceTotal.isFinite() && referenceTotal > 0) { "Total referensi tidak valid." }
        val adaptations = mutableListOf<String>()
        val selected = template.ingredients.mapIndexed { index, item ->
            val automatic = resolve(item, catalog)
            val id = bindings[index] ?: automatic?.id
            val ingredient = catalog.singleOrNull { it.id == id && it.id > 0 }
            require(ingredient != null) { "Pilih bahan katalog untuk ${item.name}." }
            require(eligibleIngredients(item, listOf(ingredient)).isNotEmpty()) { "Tipe, brand atau peran bahan ${item.name} tidak sesuai." }
            if (automatic?.id != ingredient.id) {
                adaptations.add("${item.name}${item.brand?.let { " ($it)" }.orEmpty()} => ${ingredient.name} " +
                    "[ID ${ingredient.id}${ingredient.brand?.let { ", $it" }.orEmpty()}]")
            }
            FormulaIngredientInput(ingredient, targetGrams * (referenceWeights[index] / referenceTotal), MeasurementUnit.GRAM)
        }
        val inputs = selected.groupBy { it.ingredient.id }.values.map { group ->
            group.first().copy(amount = group.sumOf { it.amount })
        }
        val calculation = FormulaCalculationEngine.calculateFormula(inputs)
        require(calculation.validationError == null && inputs.all { it.amount.isFinite() && it.amount > 0 }) {
            calculation.validationError ?: "Jumlah hasil skala tidak valid."
        }
        require(calculation.fragranceWeightG > 0 && abs(calculation.totalWeightG - targetGrams) <= 0.01) { "Total batch tidak sesuai." }
        require(abs(calculation.concentrationPct - template.concentrationPct) <= 0.1) {
            "Konsentrasi tidak sesuai komposisi katalog. Tidak ada dilusi otomatis."
        }
        require(adaptations.isEmpty() || acknowledgeAdaptation) { "Setujui adaptasi bahan sebelum menggunakan resep." }
        val notes = buildString {
            append(template.description)
            append("\nSumber: ").append(template.sourceFile).append(" #").append(template.sourceId ?: template.id)
            append("\nBatch referensi: ").append(referenceTotal).append(" g; batch draft: ").append(targetGrams).append(" g.")
            template.sourceUrls.forEach { append("\n").append(it) }
            if (adaptations.isNotEmpty()) {
                append("\nADAPTASI PILIHAN PENGGUNA, bukan formula sumber persis:\n")
                append(adaptations.joinToString("\n"))
            }
            append("\nBelum dievaluasi keselamatannya; bukan persetujuan penggunaan di kulit.")
        }
        Result.success(TemplateApplication(inputs, calculation, notes, adaptations.isNotEmpty()))
    } catch (error: IllegalArgumentException) {
        Result.failure(error)
    }

    private fun resolve(item: FormulaTemplateItem, catalog: List<Ingredient>): Ingredient? =
        if (item.requiresSelection) null else CatalogIngredientResolver.resolve(item.name, item.type, catalog, item.brand)
}
