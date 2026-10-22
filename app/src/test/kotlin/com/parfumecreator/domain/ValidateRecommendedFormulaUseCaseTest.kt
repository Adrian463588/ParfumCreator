package com.parfumecreator.domain

import com.parfumecreator.domain.model.*
import com.parfumecreator.domain.usecase.ValidateRecommendedFormulaUseCase
import org.junit.Assert.*
import org.junit.Test

class ValidateRecommendedFormulaUseCaseTest {
    private val rose = Ingredient(1, "Rose Absolute", IngredientType.NATURAL)
    private val ethanol = Ingredient(2, "Ethanol 96%", IngredientType.SYNTHETIC)
    private val formula = RecommendedFormula("Rose EDP", "20% EDP", 10.0, ingredients = listOf(
        RecommendedIngredient(rose.name, 2.0, resolvedIngredientId = 1),
        RecommendedIngredient(ethanol.name, 8.0, resolvedIngredientId = 2)
    ))
    private val validate = ValidateRecommendedFormulaUseCase()

    @Test
    fun completeCatalogImportPreservesAllAmounts() {
        val inputs = validate(formula, listOf(rose, ethanol)).getOrThrow()
        assertEquals(2, inputs.size)
        assertEquals(listOf(2.0, 8.0), inputs.map { it.amount })
    }

    @Test
    fun missingFilteredOrRenamedIngredientCannotCreatePartialImport() {
        assertTrue(validate(formula, listOf(rose)).isFailure)
        assertTrue(validate(formula, listOf(rose.copy(name = "Rose Oil"), ethanol)).isFailure)
        assertTrue(validate(formula.copy(totalWeightG = 11.0), listOf(rose, ethanol)).isFailure)
        assertTrue(validate(formula.copy(concentration = "25% EDP"), listOf(rose, ethanol)).isFailure)
    }
}
