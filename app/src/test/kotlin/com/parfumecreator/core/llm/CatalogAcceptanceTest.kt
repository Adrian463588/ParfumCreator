package com.parfumecreator.core.llm

import com.google.gson.Gson
import com.parfumecreator.data.local.database.IngredientEntity
import com.parfumecreator.data.local.database.IngredientNameCleaner
import com.parfumecreator.domain.engine.FormulaCalculationEngine
import com.parfumecreator.domain.engine.CatalogIngredientResolver
import com.parfumecreator.domain.model.FormulaTemplate
import com.parfumecreator.domain.model.FormulaIngredientInput
import com.parfumecreator.domain.model.MeasurementUnit
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Exercises the bundled catalog rather than only small synthetic test fixtures. */
class CatalogAcceptanceTest {
    private data class Asset(val ingredients: List<IngredientEntity>)

    @Test
    fun listingAndPackagingRowsAreNotPerfumeMaterials() {
        assertEquals("", IngredientNameCleaner.clean("All LUZI Packing"))
        assertEquals("", IngredientNameCleaner.clean("All Product By PARFAROME Repack 100ml Part 1"))
        assertEquals("", IngredientNameCleaner.clean("Botol Parfum Kosong"))
        assertTrue(IngredientNameCleaner.clean("Bergamot Essential Oil").isNotBlank())
    }

    private fun catalog(): List<IngredientEntity> {
        val root = listOf(File("src/main/assets/data"), File("app/src/main/assets/data"))
            .firstOrNull(File::isDirectory) ?: error("Bundled data assets must exist for acceptance test")
        return listOf("natural", "luzi", "parfarome", "macrabame")
            .flatMap { group ->
                File(root, "ingredients_$group.json").reader().use { Gson().fromJson(it, Asset::class.java).ingredients }
            }.mapIndexed { index, item -> item.copy(id = index + 1L, name = IngredientNameCleaner.clean(item.name)) }
            .filter { it.name.isNotBlank() }
    }

    private fun verify(prompt: String, requiredType: String, brand: String? = null) {
        val catalog = catalog()
        val plan = requireNotNull(GroundedFormulaPlanner().create(prompt, catalog)) { prompt }
        val inputs = plan.formula.ingredients.map { item ->
            val actual = catalog.single { it.id == item.resolvedIngredientId }
            assertEquals(actual.name, item.ingredientName)
            if (!actual.toDomain().isSolventOrDiluent) {
                assertEquals(requiredType, actual.type)
                if (brand != null) assertEquals(brand, actual.brand)
            }
            FormulaIngredientInput(actual.toDomain(), item.amount, MeasurementUnit.GRAM)
        }
        val calculation = FormulaCalculationEngine.calculateFormula(inputs)
        assertEquals(100.0, calculation.totalWeightG, 0.01)
        assertEquals(20.0, calculation.concentrationPct, 0.01)
        assertEquals(plan.formula.ingredients.size, plan.formula.ingredients.map { it.resolvedIngredientId }.distinct().size)
    }

    @Test
    fun realNaturalCatalogSupportsANaturalFormula() = verify("buatkan parfum natural citrus 100g 20%", "NATURAL")

    @Test
    fun realBrandedCatalogSupportsBibitFormulas() {
        listOf("LUZI", "PARFAROME", "MACRABAME").forEach { brand ->
            verify("buatkan parfum bibit $brand 100g 20%", "SYNTHETIC", brand)
        }
    }

    @Test
    fun bundledTemplatesIncludeCompletelyResolvedCatalogRecipes() {
        val ingredients = catalog().map { it.toDomain() }
        val asset = listOf(File("src/main/assets/data/formula_templates.json"), File("app/src/main/assets/data/formula_templates.json"))
            .first(File::isFile)
        val templates = asset.reader().use { Gson().fromJson(it, Array<FormulaTemplate>::class.java).toList() }
        val resolved = templates.filter { template -> template.ingredients.all {
            CatalogIngredientResolver.resolve(it.name, it.type, ingredients, it.brand) != null
        } }
        assertTrue("Both explicitly calculated starter recipes must resolve", resolved.map { it.id }.containsAll(listOf(31, 32)))
        resolved.forEach { template ->
            val inputs = template.ingredients.map {
                FormulaIngredientInput(requireNotNull(CatalogIngredientResolver.resolve(it.name, it.type, ingredients, it.brand)),
                    it.amount, MeasurementUnit.GRAM)
            }
            val result = FormulaCalculationEngine.calculateFormula(inputs)
            assertNull(result.validationError)
            assertEquals(template.concentrationPct, result.concentrationPct, 0.01)
        }
    }
}
