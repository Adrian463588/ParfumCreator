package com.parfumecreator.domain

import com.parfumecreator.domain.model.*
import com.parfumecreator.domain.repository.FormulaRepository
import com.parfumecreator.domain.repository.IngredientRepository
import com.parfumecreator.domain.usecase.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class FakeFormulaRepository : FormulaRepository {
    val formulas = mutableListOf<Formula>()
    private var nextId = 1L

    override fun getAllFormulas(): Flow<List<Formula>> = flowOf(formulas)

    override suspend fun getFormulaById(id: Long): Formula? = formulas.find { it.id == id }

    override suspend fun saveFormula(formula: Formula): Long {
        val id = if (formula.id == 0L) nextId++ else formula.id
        formulas.removeAll { it.id == id }
        formulas.add(formula.copy(id = id))
        return id
    }

    override suspend fun deleteFormula(id: Long) {
        formulas.removeAll { it.id == id }
    }
}

class FakeIngredientRepository : IngredientRepository {
    val ingredients = mutableListOf<Ingredient>()
    private var nextId = 1L

    override fun getAllIngredients(): Flow<List<Ingredient>> = flowOf(ingredients)

    override fun searchIngredients(
        query: String,
        brand: String?,
        noteType: NoteType?,
        family: FragranceFamily?
    ): Flow<List<Ingredient>> {
        val filtered = ingredients.filter {
            (query.isBlank() || it.name.contains(query, ignoreCase = true)) &&
                    (brand == null || it.brand == brand) &&
                    (noteType == null || it.noteType == noteType) &&
                    (family == null || it.fragranceFamily == family)
        }
        return flowOf(filtered)
    }

    override suspend fun getIngredientById(id: Long): Ingredient? = ingredients.find { it.id == id }

    override suspend fun saveIngredient(ingredient: Ingredient): Long {
        val id = if (ingredient.id == 0L) nextId++ else ingredient.id
        ingredients.removeAll { it.id == id }
        ingredients.add(ingredient.copy(id = id))
        return id
    }

    override suspend fun deleteIngredient(ingredient: Ingredient) {
        ingredients.removeAll { it.id == ingredient.id }
    }

    override suspend fun importIngredientsFromCsv(inputStream: InputStream): Result<Int> {
        val text = inputStream.bufferedReader().readText()
        val lines = text.lines().filter { it.isNotBlank() }
        var count = 0
        lines.drop(1).forEach { line ->
            val parts = line.split(",")
            if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                saveIngredient(
                    Ingredient(
                        id = 0,
                        name = parts[0].trim(),
                        type = IngredientType.NATURAL,
                        noteType = NoteType.MIDDLE,
                        fragranceFamily = FragranceFamily.FLORAL,
                        densityGml = 1.0,
                        isCustom = true
                    )
                )
                count++
            }
        }
        return Result.success(count)
    }
}

class FormulaUseCasesTest {

    private lateinit var formulaRepo: FakeFormulaRepository
    private lateinit var ingredientRepo: FakeIngredientRepository

    private lateinit var calculateFormulaUseCase: CalculateFormulaUseCase
    private lateinit var saveFormulaUseCase: SaveFormulaUseCase
    private lateinit var getFormulasUseCase: GetFormulasUseCase
    private lateinit var getFormulaByIdUseCase: GetFormulaByIdUseCase
    private lateinit var deleteFormulaUseCase: DeleteFormulaUseCase
    private lateinit var searchIngredientsUseCase: SearchIngredientsUseCase
    private lateinit var saveCustomIngredientUseCase: SaveCustomIngredientUseCase
    private lateinit var importCsvUseCase: ImportIngredientsCsvUseCase

    @Before
    fun setup() {
        formulaRepo = FakeFormulaRepository()
        ingredientRepo = FakeIngredientRepository()

        calculateFormulaUseCase = CalculateFormulaUseCase()
        saveFormulaUseCase = SaveFormulaUseCase(formulaRepo)
        getFormulasUseCase = GetFormulasUseCase(formulaRepo)
        getFormulaByIdUseCase = GetFormulaByIdUseCase(formulaRepo)
        deleteFormulaUseCase = DeleteFormulaUseCase(formulaRepo)
        searchIngredientsUseCase = SearchIngredientsUseCase(ingredientRepo)
        saveCustomIngredientUseCase = SaveCustomIngredientUseCase(ingredientRepo)
        importCsvUseCase = ImportIngredientsCsvUseCase(ingredientRepo)
    }

    @Test
    fun testSaveAndGetFormula() = runTest {
        val formula = Formula(
            id = 0,
            name = "Rose Extrait",
            totalWeightG = 10.0,
            concentrationPct = 20.0,
            concentrationType = ConcentrationType.PARFUM_EXTRAIT,
            spl = 40.0,
            splLabel = SplLabel.MODERATE,
            fragranceWheelPrimary = PrimaryFragranceWheel.FLORAL,
            fragranceWheelSub = FragranceFamily.FLORAL,
            ingredients = listOf(
                FormulaIngredient(
                    ingredient = Ingredient(id = 1, name = "Rose Absolute", type = IngredientType.NATURAL,
                        fragranceFamily = FragranceFamily.FLORAL),
                    amount = 2.0
                ),
                FormulaIngredient(
                    ingredient = Ingredient(id = 2, name = "Ethanol 96%", type = IngredientType.SYNTHETIC),
                    amount = 8.0
                )
            )
        )

        val id = saveFormulaUseCase(formula)
        assertTrue(id > 0)

        val fetched = getFormulaByIdUseCase(id)
        assertNotNull(fetched)
        assertEquals("Rose Extrait", fetched?.name)
        assertEquals(ConcentrationType.PARFUM_EXTRAIT, fetched?.concentrationType)

        // Delete test
        deleteFormulaUseCase(id)
        assertNull(getFormulaByIdUseCase(id))
    }

    @Test
    fun calculateUseCaseUsesMassForMixedUnitsAndExcludesCarrier() {
        val oil = Ingredient(id = 1, name = "Bergamot Oil", type = IngredientType.NATURAL,
            noteType = NoteType.TOP, fragranceFamily = FragranceFamily.CITRUS, densityGml = 0.8)
        val alcohol = Ingredient(id = 2, name = "Ethanol 96%", type = IngredientType.SYNTHETIC)
        val result = calculateFormulaUseCase(listOf(
            FormulaIngredientInput(oil, 25.0, MeasurementUnit.MILLILITER),
            FormulaIngredientInput(alcohol, 80_000.0, MeasurementUnit.MILLIGRAM)
        ))

        assertNull(result.validationError)
        assertEquals(100.0, result.totalWeightG, 0.0001)
        assertEquals(20.0, result.fragranceWeightG, 0.0001)
        assertEquals(20.0, result.concentrationPct, 0.0001)
        assertEquals(1.0, result.topRatio, 0.0)
        assertEquals(0.0, result.middleRatio, 0.0)
        assertEquals(0.0, result.calculatedItems.last().percentageOfFragrance, 0.0)
    }

    @Test
    fun calculateUseCaseSurfacesInvalidMlDensityWithoutBreakingResultContract() {
        val oil = Ingredient(id = 1, name = "Rose Absolute", type = IngredientType.NATURAL, densityGml = Double.NaN)
        val invalid = calculateFormulaUseCase(listOf(FormulaIngredientInput(oil, 5.0, MeasurementUnit.MILLILITER)))
        assertNotNull(invalid.validationError)
        assertEquals(0.0, invalid.totalWeightG, 0.0)

        // Density is unnecessary when the user weighs the material directly.
        val valid = calculateFormulaUseCase(listOf(FormulaIngredientInput(oil, 5.0, MeasurementUnit.GRAM)))
        assertNull(valid.validationError)
        assertEquals(5.0, valid.totalWeightG, 0.0)
    }

    @Test
    fun saveRecalculatesMetricsInsteadOfPersistingStaleValues() = runTest {
        val oil = Ingredient(id = 1, name = "Rose Absolute", type = IngredientType.NATURAL,
            fragranceFamily = FragranceFamily.FLORAL)
        val solvent = Ingredient(id = 2, name = "Ethanol 96%", type = IngredientType.SYNTHETIC)
        val formula = Formula(name = "Rose blend", totalWeightG = 999.0, concentrationPct = 99.0,
            spl = 99.0, fragranceWheelPrimary = PrimaryFragranceWheel.WOODY,
            ingredients = listOf(
                FormulaIngredient(ingredient = oil, amount = 2.0, amountInGram = 999.0, percentage = 99.0),
                FormulaIngredient(ingredient = solvent, amount = 8.0)
            ))

        val id = saveFormulaUseCase(formula)
        val saved = requireNotNull(getFormulaByIdUseCase(id))
        assertEquals(10.0, saved.totalWeightG, 0.0001)
        assertEquals(20.0, saved.concentrationPct, 0.0001)
        assertEquals(PrimaryFragranceWheel.FLORAL, saved.fragranceWheelPrimary)
        assertEquals(2.0, saved.ingredients.first().amountInGram, 0.0001)
        assertEquals(20.0, saved.ingredients.first().percentage, 0.0001)
        val actual = calculateFormulaUseCase(formula.ingredients.map {
            FormulaIngredientInput(it.ingredient, it.amount, it.unit)
        })
        assertEquals(actual.spl, saved.spl, 0.0001)
    }

    @Test
    fun saveRejectsInvalidOrCarrierOnlyFormulasBeforePersistence() = runTest {
        val oil = Ingredient(id = 1, name = "Rose Absolute", type = IngredientType.NATURAL)
        val solvent = Ingredient(id = 2, name = "Ethanol 96%", type = IngredientType.SYNTHETIC)
        val invalidInputs = listOf(
            emptyList(),
            listOf(FormulaIngredient(ingredient = oil.copy(id = 0), amount = 1.0)),
            listOf(FormulaIngredient(ingredient = oil, amount = Double.NaN)),
            listOf(FormulaIngredient(ingredient = oil, amount = 0.0)),
            listOf(FormulaIngredient(ingredient = oil.copy(densityGml = 0.0), amount = 1.0,
                unit = MeasurementUnit.MILLILITER)),
            listOf(FormulaIngredient(ingredient = solvent, amount = 10.0))
        )
        invalidInputs.forEach { inputs ->
            val failure = runCatching { saveFormulaUseCase(Formula(name = "Invalid", ingredients = inputs)) }
            assertTrue("Invalid formula must fail validation", failure.exceptionOrNull() is IllegalArgumentException)
            assertTrue("Invalid formula must not reach storage", formulaRepo.formulas.isEmpty())
        }
    }

    @Test
    fun testSearchIngredients_and_SaveCustom() = runTest {
        val ing = Ingredient(
            id = 0,
            name = "Custom Oud Resin",
            type = IngredientType.NATURAL,
            noteType = NoteType.BASE,
            fragranceFamily = FragranceFamily.WOODY,
            densityGml = 1.05
        )

        val id = saveCustomIngredientUseCase(ing)
        assertTrue(id > 0)

        val results = searchIngredientsUseCase("Oud").toList().flatten()
        assertEquals(1, results.size)
        assertEquals("Custom Oud Resin", results[0].name)
        assertTrue(results[0].isCustom)
    }

    @Test
    fun testImportIngredientsCsv() = runTest {
        val csvData = """
            name,type,note,family,density
            Lavender Oil,NATURAL,TOP,FRESH,0.89
            Vanilla Absolute,NATURAL,BASE,ORIENTAL,1.02
        """.trimIndent()

        val result = importCsvUseCase(ByteArrayInputStream(csvData.toByteArray()))
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull())

        val all = searchIngredientsUseCase("").toList().flatten()
        assertEquals(2, all.size)
    }
}
