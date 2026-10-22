package com.parfumecreator.domain.usecase

import com.parfumecreator.domain.engine.FormulaCalculationEngine
import com.parfumecreator.domain.model.FormulaIngredientInput
import com.parfumecreator.domain.model.Ingredient
import com.parfumecreator.domain.model.MeasurementUnit
import com.parfumecreator.domain.model.RecommendedFormula
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

/** Validates the complete AI import against the unfiltered catalog before any editor mutation. */
class ValidateRecommendedFormulaUseCase @Inject constructor() {
    operator fun invoke(formula: RecommendedFormula, catalog: List<Ingredient>): Result<List<FormulaIngredientInput>> {
        return try {
            require(formula.name.isNotBlank() && formula.ingredients.isNotEmpty()) { "Rekomendasi formula kosong." }
            val byId = catalog.associateBy { it.id }
            val ids = mutableSetOf<Long>()
            val inputs = formula.ingredients.map { item ->
                val id = item.resolvedIngredientId
                require(id != null && id > 0 && ids.add(id)) { "ID bahan tidak valid atau berulang." }
                val ingredient = byId[id]
                require(ingredient != null && ingredient.name.equals(item.ingredientName, true)) {
                    "Bahan ${item.ingredientName} belum tersedia atau identitas katalog berbeda."
                }
                require(item.amount.isFinite() && item.amount > 0) { "Jumlah bahan harus positif." }
                val unit = when (item.unit.lowercase(Locale.ROOT)) {
                    "g", "gram" -> MeasurementUnit.GRAM
                    "mg", "milligram" -> MeasurementUnit.MILLIGRAM
                    "ml", "milliliter" -> MeasurementUnit.MILLILITER
                    else -> throw IllegalArgumentException("Satuan bahan tidak didukung.")
                }
                FormulaIngredientInput(ingredient, item.amount, unit)
            }
            val calculation = FormulaCalculationEngine.calculateFormula(inputs)
            require(calculation.validationError == null && calculation.fragranceWeightG > 0) {
                calculation.validationError ?: "Rekomendasi hanya berisi pelarut."
            }
            val total = formula.totalWeightG
            require(total != null && total.isFinite() && total > 0 && abs(total - calculation.totalWeightG) <= 0.01) {
                "Total rekomendasi tidak sesuai bahan katalog."
            }
            val concentration = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*%")
                .find(formula.concentration.orEmpty())?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
            require(concentration == null || abs(concentration - calculation.concentrationPct) <= 0.1) {
                "Konsentrasi rekomendasi tidak sesuai komposisi."
            }
            Result.success(inputs)
        } catch (error: IllegalArgumentException) {
            Result.failure(error)
        }
    }
}
