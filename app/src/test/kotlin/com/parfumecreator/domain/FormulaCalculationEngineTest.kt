package com.parfumecreator.domain

import com.parfumecreator.domain.engine.FormulaCalculationEngine
import com.parfumecreator.domain.engine.CatalogIngredientResolver
import com.parfumecreator.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class FormulaCalculationEngineTest {

    private val bergamot = Ingredient(
        id = 1,
        name = "Bergamot Oil",
        type = IngredientType.NATURAL,
        noteType = NoteType.TOP,
        fragranceFamily = FragranceFamily.CITRUS,
        densityGml = 0.88
    )

    private val rose = Ingredient(
        id = 2,
        name = "Rose Absolute",
        type = IngredientType.NATURAL,
        noteType = NoteType.HEART,
        fragranceFamily = FragranceFamily.FLORAL,
        densityGml = 0.98
    )

    private val sandalwood = Ingredient(
        id = 3,
        name = "Sandalwood Oil",
        type = IngredientType.NATURAL,
        noteType = NoteType.BASE,
        fragranceFamily = FragranceFamily.WOODY,
        densityGml = 0.97
    )

    private val ethanol = Ingredient(
        id = 4,
        name = "Alcohol / Ethanol 96%",
        type = IngredientType.SYNTHETIC,
        noteType = NoteType.MIDDLE,
        fragranceFamily = FragranceFamily.FRESH,
        densityGml = 0.81
    )

    @Test
    fun testUnitConversion_grams() {
        val result = FormulaCalculationEngine.convertToGrams(10.0, MeasurementUnit.GRAM, 0.95)
        assertEquals(10.0, result, 0.0001)
    }

    @Test
    fun testUnitConversion_milligrams() {
        val result = FormulaCalculationEngine.convertToGrams(500.0, MeasurementUnit.MILLIGRAM, 0.95)
        assertEquals(0.5, result, 0.0001)
    }

    @Test
    fun testUnitConversion_milliliters() {
        val result = FormulaCalculationEngine.convertToGrams(10.0, MeasurementUnit.MILLILITER, 0.90)
        assertEquals(9.0, result, 0.0001)
    }

    @Test
    fun testUnitConversion_edgeCases() {
        assertEquals(0.0, FormulaCalculationEngine.convertToGrams(-5.0, MeasurementUnit.GRAM, 0.95), 0.0001)
        assertEquals(0.0, FormulaCalculationEngine.convertToGrams(0.0, MeasurementUnit.GRAM, 0.95), 0.0001)
        assertEquals(0.0, FormulaCalculationEngine.convertToGrams(Double.NaN, MeasurementUnit.GRAM, 0.95), 0.0001)
        assertEquals(0.0, FormulaCalculationEngine.convertToGrams(Double.POSITIVE_INFINITY, MeasurementUnit.GRAM, 0.95), 0.0001)
        // Unknown density must never become an invented 1 g/mL conversion.
        assertEquals(0.0, FormulaCalculationEngine.convertToGrams(10.0, MeasurementUnit.MILLILITER, 0.0), 0.0001)
        assertEquals(0.0, FormulaCalculationEngine.convertToGrams(10.0, MeasurementUnit.MILLILITER, -0.5), 0.0001)
    }

    @Test
    fun testConcentrationCalculation_and_Types() {
        // Extrait: >= 20%
        val extraitPct = FormulaCalculationEngine.calculateConcentration(25.0, 100.0)
        assertEquals(25.0, extraitPct, 0.0001)
        assertEquals(ConcentrationType.PARFUM_EXTRAIT, FormulaCalculationEngine.getConcentrationType(extraitPct))

        // EDP: 15-20%
        val edpPct = FormulaCalculationEngine.calculateConcentration(18.0, 100.0)
        assertEquals(18.0, edpPct, 0.0001)
        assertEquals(ConcentrationType.EDP, FormulaCalculationEngine.getConcentrationType(edpPct))

        // EDT: 8-15%
        val edtPct = FormulaCalculationEngine.calculateConcentration(10.0, 100.0)
        assertEquals(10.0, edtPct, 0.0001)
        assertEquals(ConcentrationType.EDT, FormulaCalculationEngine.getConcentrationType(edtPct))

        // EDC: 4-8%
        val edcPct = FormulaCalculationEngine.calculateConcentration(5.0, 100.0)
        assertEquals(5.0, edcPct, 0.0001)
        assertEquals(ConcentrationType.EDC, FormulaCalculationEngine.getConcentrationType(edcPct))

        // Eau Fraiche: < 4%
        val efPct = FormulaCalculationEngine.calculateConcentration(2.0, 100.0)
        assertEquals(2.0, efPct, 0.0001)
        assertEquals(ConcentrationType.EAU_FRAICHE, FormulaCalculationEngine.getConcentrationType(efPct))

        // Edge case: zero total weight
        assertEquals(0.0, FormulaCalculationEngine.calculateConcentration(5.0, 0.0), 0.0001)
    }

    @Test
    fun testSolventAndBotanicalAbsoluteIdentification() {
        val alcohol1 = Ingredient(id = 101, name = "Alkohol Absolute 96% (Perfumer's Alcohol)", type = IngredientType.NATURAL, noteType = NoteType.MIDDLE, fragranceFamily = FragranceFamily.FRESH)
        val alcohol2 = Ingredient(id = 102, name = "ALKOHOL ABSOLUTE FOOD GRADE 96% Basis Untuk Parfum", type = IngredientType.NATURAL, noteType = NoteType.MIDDLE, fragranceFamily = FragranceFamily.FRESH)
        val dpg = Ingredient(id = 103, name = "Dipropylene Glycol (DPG Solvent)", type = IngredientType.NATURAL, noteType = NoteType.MIDDLE, fragranceFamily = FragranceFamily.FRESH)
        val water = Ingredient(id = 104, name = "Distilled Water / Aquadest", type = IngredientType.NATURAL, noteType = NoteType.TOP, fragranceFamily = FragranceFamily.FRESH)

        val roseAbs = Ingredient(id = 105, name = "Rose Absolute", type = IngredientType.NATURAL, noteType = NoteType.HEART, fragranceFamily = FragranceFamily.FLORAL)
        val vanillaAbs = Ingredient(id = 106, name = "Vanilla Bourbon Absolute", type = IngredientType.NATURAL, noteType = NoteType.BASE, fragranceFamily = FragranceFamily.GOURMAND)
        val jasmineAbs = Ingredient(id = 107, name = "Jasmine Sambac Absolute", type = IngredientType.NATURAL, noteType = NoteType.MIDDLE, fragranceFamily = FragranceFamily.FLORAL)

        assertTrue(alcohol1.isSolventOrDiluent)
        assertTrue(alcohol2.isSolventOrDiluent)
        assertTrue(dpg.isSolventOrDiluent)
        assertTrue(water.isSolventOrDiluent)

        assertFalse(roseAbs.isSolventOrDiluent)
        assertFalse(vanillaAbs.isSolventOrDiluent)
        assertFalse(jasmineAbs.isSolventOrDiluent)
    }

    @Test
    fun testSplCalculation_noteWeightFactor() {
        // Base-heavy formula (base = 1.0, mid = 0, top = 0)
        // factor = (1.0 * 1.5 + 0 + 0) / 3.0 = 0.5
        // At 20% concentration, SPL = 20 * 0.5 = 10.0
        val baseSpl = FormulaCalculationEngine.calculateSpl(20.0, 0.0, 0.0, 1.0)
        assertEquals(10.0, baseSpl, 0.01)

        // Top-heavy formula (top = 1.0, mid = 0, base = 0)
        // factor = (0 + 0 + 1.0 * 0.5) / 3.0 = 0.16667
        // At 20% concentration, SPL = 20 * 0.16667 = 3.333
        val topSpl = FormulaCalculationEngine.calculateSpl(20.0, 1.0, 0.0, 0.0)
        assertEquals(3.333, topSpl, 0.01)

        // High concentration strong SPL (e.g. 100% pure base oil)
        // SPL = 100 * 0.5 = 50.0 (Moderate)
        val pureBaseSpl = FormulaCalculationEngine.calculateSpl(100.0, 0.0, 0.0, 1.0)
        assertEquals(50.0, pureBaseSpl, 0.01)
        assertEquals(SplLabel.MODERATE, FormulaCalculationEngine.getSplLabel(pureBaseSpl))

        // Clamp tests: negative, zero, overflow
        assertEquals(0.0, FormulaCalculationEngine.calculateSpl(-10.0, 0.5, 0.5, 0.5), 0.0001)
        assertEquals(0.0, FormulaCalculationEngine.calculateSpl(0.0, 0.5, 0.5, 0.5), 0.0001)
    }

    @Test
    fun testFragranceWheelPrediction() {
        val items = listOf(
            CalculatedItemResult(bergamot, 2.0, MeasurementUnit.GRAM, 2.0, 20.0, 20.0),
            CalculatedItemResult(rose, 3.0, MeasurementUnit.GRAM, 3.0, 30.0, 30.0),
            CalculatedItemResult(sandalwood, 5.0, MeasurementUnit.GRAM, 5.0, 50.0, 50.0)
        )

        val result = FormulaCalculationEngine.predictFragranceWheel(items)
        assertEquals(FragranceFamily.WOODY, result.dominantSubFamily)
        assertEquals(PrimaryFragranceWheel.WOODY, result.primary)

        // 4-axis scores:
        // Woody (5g): Woody (50%)
        // Floral (3g): Floral (30%)
        // Fresh (2g Citrus -> Fresh): Fresh (20%)
        // Oriental (0g): 0%
        assertEquals(5.0, result.primaryAxisScores[PrimaryFragranceWheel.WOODY] ?: 0.0, 0.01)
        assertEquals(3.0, result.primaryAxisScores[PrimaryFragranceWheel.FLORAL] ?: 0.0, 0.01)
        assertEquals(2.0, result.primaryAxisScores[PrimaryFragranceWheel.FRESH] ?: 0.0, 0.01)
        assertEquals(0.0, result.primaryAxisScores[PrimaryFragranceWheel.ORIENTAL] ?: 0.0, 0.01)

        assertEquals(50.0, result.primaryAxisPercentages[PrimaryFragranceWheel.WOODY] ?: 0.0, 0.01)
        assertEquals(30.0, result.primaryAxisPercentages[PrimaryFragranceWheel.FLORAL] ?: 0.0, 0.01)
        assertEquals(20.0, result.primaryAxisPercentages[PrimaryFragranceWheel.FRESH] ?: 0.0, 0.01)

        // Secondary wheel is Floral
        assertEquals(PrimaryFragranceWheel.FLORAL, result.secondaryWheel)
        assertEquals("Woody Floral", result.olfactoryDirection)
    }

    @Test
    fun testEmptyFormulaCalculation() {
        val result = FormulaCalculationEngine.calculateFormula(emptyList())
        assertEquals(0.0, result.totalWeightG, 0.001)
        assertEquals(0.0, result.fragranceWeightG, 0.001)
        assertEquals(0.0, result.concentrationPct, 0.001)
        assertEquals(ConcentrationType.EAU_FRAICHE, result.concentrationType)
        assertEquals(0.0, result.spl, 0.001)
        assertEquals(SplLabel.SUBTLE, result.splLabel)
        assertEquals(PrimaryFragranceWheel.FRESH, result.fragranceWheelPrimary)
        assertNotNull(result.fragranceWheelResult)
        assertFalse(result.fragranceWheelResult.hasFragranceProfile)
        assertEquals("", result.fragranceWheelResult.olfactoryDirection)
        assertTrue(result.fragranceWheelResult.primaryAxisPercentages.values.all { it == 0.0 })
    }

    @Test
    fun testCompleteFormulaCalculation() {
        val inputs = listOf(
            FormulaIngredientInput(bergamot, 3.0, MeasurementUnit.GRAM),
            FormulaIngredientInput(rose, 5.0, MeasurementUnit.GRAM),
            FormulaIngredientInput(sandalwood, 12.0, MeasurementUnit.GRAM),
            FormulaIngredientInput(ethanol, 80.0, MeasurementUnit.GRAM)
        )

        val result = FormulaCalculationEngine.calculateFormula(inputs)

        // Total weight = 3 + 5 + 12 + 80 = 100g
        assertEquals(100.0, result.totalWeightG, 0.01)
        // Fragrance weight = 3 + 5 + 12 = 20g
        assertEquals(20.0, result.fragranceWeightG, 0.01)
        // Concentration = 20% -> Parfum Extrait
        assertEquals(20.0, result.concentrationPct, 0.01)
        assertEquals(ConcentrationType.PARFUM_EXTRAIT, result.concentrationType)

        // Note breakdown:
        // Top: 3/20 = 0.15
        // Middle: 5/20 = 0.25
        // Base: 12/20 = 0.60
        assertEquals(0.15, result.topRatio, 0.01)
        assertEquals(0.25, result.middleRatio, 0.01)
        assertEquals(0.60, result.baseRatio, 0.01)

        // Fragrance wheel dominant: Sandalwood (12g Woody)
        assertEquals(PrimaryFragranceWheel.WOODY, result.fragranceWheelPrimary)
        assertEquals(FragranceFamily.WOODY, result.dominantSubFamily)

        // Check individual calculated percentages
        val calcEthanol = result.calculatedItems.first { it.ingredient.id == 4L }
        assertEquals(80.0, calcEthanol.percentageOfTotal, 0.01)
        assertEquals(0.0, calcEthanol.percentageOfFragrance, 0.01) // solvent has 0% of fragrance

        val calcBergamot = result.calculatedItems.first { it.ingredient.id == 1L }
        assertEquals(3.0, calcBergamot.percentageOfTotal, 0.01)
        assertEquals(15.0, calcBergamot.percentageOfFragrance, 0.01)
    }

    @Test
    fun aggregatePrimaryAxisWinsOverLargestIndividualFamily() {
        val aquatic = bergamot.copy(id = 5, name = "Aquatic Accord", fragranceFamily = FragranceFamily.AQUATIC)
        val result = FormulaCalculationEngine.calculateFormula(listOf(
            FormulaIngredientInput(bergamot, 30.0, MeasurementUnit.GRAM),
            FormulaIngredientInput(aquatic, 30.0, MeasurementUnit.GRAM),
            FormulaIngredientInput(rose, 40.0, MeasurementUnit.GRAM)
        ))

        val wheel = result.fragranceWheelResult
        assertEquals(PrimaryFragranceWheel.FRESH, wheel.primary)
        assertEquals(wheel.primary, wheel.dominantSubFamily.toPrimaryWheel())
        assertEquals(60.0, wheel.primaryAxisPercentages.getValue(PrimaryFragranceWheel.FRESH), 0.0001)
        assertEquals(40.0, wheel.primaryAxisPercentages.getValue(PrimaryFragranceWheel.FLORAL), 0.0001)
        assertEquals(PrimaryFragranceWheel.FLORAL, wheel.secondaryWheel)
        assertEquals(FragranceFamily.FLORAL, wheel.secondarySubFamily)
        assertEquals("Fresh Floral", wheel.olfactoryDirection)
    }

    @Test
    fun zeroAndSolventOnlyFormulasDoNotClaimAnOlfactoryProfile() {
        val inputs = listOf(
            listOf(FormulaIngredientInput(ethanol, 100.0, MeasurementUnit.GRAM)),
            listOf(FormulaIngredientInput(rose, 0.0, MeasurementUnit.GRAM))
        )
        inputs.forEach { formula ->
            val result = FormulaCalculationEngine.calculateFormula(formula)
            assertEquals(0.0, result.fragranceWeightG, 0.0)
            assertEquals(0.0, result.concentrationPct, 0.0)
            assertEquals(0.0, result.topRatio + result.middleRatio + result.baseRatio, 0.0)
            assertEquals(0.0, result.spl, 0.0)
            assertFalse(result.fragranceWheelResult.hasFragranceProfile)
            assertEquals("", result.fragranceWheelResult.olfactoryDirection)
            assertTrue(result.familyScores.values.all { it == 0.0 })
            assertTrue(result.fragranceWheelResult.primaryAxisPercentages.values.all { it == 0.0 })
        }
    }

    @Test
    fun aromaticAlcoholsAndDilutedAbsolutesAreNotEntirelyCarrier() {
        listOf(
            "Phenethyl Alcohol", "Phenylethyl Alcohol", "Benzyl Alcohol", "Cinnamyl Alcohol",
            "Rose Absolute 10% in DPG", "Orange Blossom Absolute", "Watermelon Fragrance Oil",
            "Alcoholic Amber Accord", "100% Pure Vanilla Absolute Essential Oil"
        ).forEach { name -> assertFalse(name, Ingredient.isSolventName(name)) }

        listOf(
            "Perfumer's Alcohol", "Ethanol 96%", "DPG", "Triethyl Citrate (TEC)",
            "Isopropyl Myristate (IPM)", "Fractionated Coconut Oil", "Jojoba Oil",
            "Distilled Water / Aquadest", "Perfume Base / Absolute / Alkohol Campuran Parfum",
            "Absolute Food Grade 96% Super SOLVA BLEND | SOLVABLEND | Pelarut Parfum Super"
        ).forEach { name -> assertTrue(name, Ingredient.isSolventName(name)) }
    }

    @Test
    fun aromaticAlcoholContributesToConcentrationAndWheel() {
        val phenethylAlcohol = rose.copy(id = 5, name = "Phenethyl Alcohol", type = IngredientType.SYNTHETIC)
        val result = FormulaCalculationEngine.calculateFormula(listOf(
            FormulaIngredientInput(phenethylAlcohol, 20.0, MeasurementUnit.GRAM),
            FormulaIngredientInput(ethanol, 80.0, MeasurementUnit.GRAM)
        ))
        assertEquals(20.0, result.concentrationPct, 0.0)
        assertEquals(1.0, result.middleRatio, 0.0)
        assertEquals(100.0, result.fragranceWheelResult.primaryAxisPercentages.getValue(PrimaryFragranceWheel.FLORAL), 0.0)
        assertEquals(0.0, result.calculatedItems.last().percentageOfFragrance, 0.0)
    }

    @Test
    fun invalidMlDensitiesAreReportedAndNeverAssumedToBeOne() {
        listOf(0.0, -0.5, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { density ->
            val result = FormulaCalculationEngine.calculateFormula(listOf(
                FormulaIngredientInput(bergamot.copy(densityGml = density), 10.0, MeasurementUnit.MILLILITER)
            ))
            assertNotNull(result.validationError)
            assertTrue(result.validationError.orEmpty().contains("Densitas"))
            assertEquals(0.0, result.totalWeightG, 0.0)
            assertFalse(result.fragranceWheelResult.hasFragranceProfile)
        }
    }

    @Test
    fun invalidAmountsAndConversionOverflowRemainExplicitlyInvalid() {
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { amount ->
            val result = FormulaCalculationEngine.calculateFormula(listOf(
                FormulaIngredientInput(bergamot, amount, MeasurementUnit.GRAM)
            ))
            assertNotNull(result.validationError)
            assertEquals(0.0, result.totalWeightG, 0.0)
        }
        val overflow = FormulaCalculationEngine.calculateFormula(listOf(
            FormulaIngredientInput(bergamot.copy(densityGml = 2.0), Double.MAX_VALUE, MeasurementUnit.MILLILITER)
        ))
        assertNotNull(overflow.validationError)
        val aggregateOverflow = FormulaCalculationEngine.calculateFormula(listOf(
            FormulaIngredientInput(bergamot, Double.MAX_VALUE, MeasurementUnit.GRAM),
            FormulaIngredientInput(rose, Double.MAX_VALUE, MeasurementUnit.GRAM)
        ))
        assertNotNull(aggregateOverflow.validationError)
    }

    @Test
    fun unitChangesPreserveMassAcrossGramsMilligramsAndMilliliters() {
        val milligrams = requireNotNull(FormulaCalculationEngine.convertAmount(
            2.0, MeasurementUnit.GRAM, MeasurementUnit.MILLIGRAM, 0.8
        ))
        assertEquals(2000.0, milligrams, 0.0001)
        val milliliters = requireNotNull(FormulaCalculationEngine.convertAmount(
            milligrams, MeasurementUnit.MILLIGRAM, MeasurementUnit.MILLILITER, 0.8
        ))
        assertEquals(2.5, milliliters, 0.0001)
        val grams = requireNotNull(FormulaCalculationEngine.convertAmount(
            milliliters, MeasurementUnit.MILLILITER, MeasurementUnit.GRAM, 0.8
        ))
        assertEquals(2.0, grams, 0.0001)
        assertNull(FormulaCalculationEngine.convertAmount(2.0, MeasurementUnit.GRAM, MeasurementUnit.MILLILITER, Double.NaN))
        assertNull(FormulaCalculationEngine.convertAmount(Double.MAX_VALUE, MeasurementUnit.GRAM, MeasurementUnit.MILLIGRAM, 0.8))
        assertEquals(2000.0, requireNotNull(FormulaCalculationEngine.convertAmount(
            2.0, MeasurementUnit.GRAM, MeasurementUnit.MILLIGRAM, Double.NaN
        )), 0.0001)
    }

    @Test
    fun templateMatchingPreservesIdentityAndRejectsArbitrarySubstitution() {
        assertEquals(bergamot, CatalogIngredientResolver.resolve("Bergamot Essential Oil", IngredientType.NATURAL, listOf(bergamot)))
        assertNull(CatalogIngredientResolver.resolve("Bergamot FCF", IngredientType.NATURAL, listOf(bergamot)))
        assertNull(CatalogIngredientResolver.resolve("Rose", IngredientType.NATURAL, listOf(rose)))
        assertNull(CatalogIngredientResolver.resolve("Ethanol 96%", IngredientType.NATURAL, listOf(ethanol)))
        assertNull(CatalogIngredientResolver.resolve("Bergamot Oil", IngredientType.NATURAL, listOf(bergamot.copy(id = 0))))
        assertNull(CatalogIngredientResolver.resolve("Bergamot Essential Oil", IngredientType.NATURAL,
            listOf(bergamot, bergamot.copy(id = 20))))
    }

    @Test
    fun heuristicHasNoScoreWithoutNotesAndStaysFiniteForLargeRatios() {
        assertEquals(0.0, FormulaCalculationEngine.calculateSpl(20.0, 0.0, 0.0, 0.0), 0.0)
        assertEquals(0.0, FormulaCalculationEngine.calculateSpl(20.0, Double.NaN, -1.0, Double.POSITIVE_INFINITY), 0.0)
        val score = FormulaCalculationEngine.calculateSpl(20.0, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE)
        assertTrue(score.isFinite())
        assertEquals(20.0 / 3.0, score, 0.0001)
    }
}
