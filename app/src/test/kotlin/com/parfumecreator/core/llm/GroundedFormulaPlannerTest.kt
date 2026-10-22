package com.parfumecreator.core.llm

import com.parfumecreator.data.local.database.IngredientEntity
import com.parfumecreator.domain.engine.FormulaCalculationEngine
import com.parfumecreator.domain.model.FormulaIngredientInput
import com.parfumecreator.domain.model.MeasurementUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Explicit unit-test fixtures model catalog rows; none are application/runtime data. */
class GroundedFormulaPlannerTest {
    private val planner = GroundedFormulaPlanner()
    private val bergamot = material(101, "Bergamot Essential Oil", "NATURAL", "TOP", "CITRUS", 0.882)
    private val lavender = material(102, "Lavender Essential Oil", "NATURAL", "HEART", "FLORAL", 0.885)
    private val cedar = material(103, "Cedarwood Essential Oil", "NATURAL", "BASE", "WOODY", 0.94)
    private val lemon = material(104, "Lemon Essential Oil", "NATURAL", "TOP", "CITRUS", 0.85)
    private val sauvage = material(201, "Sauvage For Men By LUZI", "SYNTHETIC", "MIDDLE", "ORIENTAL", 0.95, "LUZI", "Sauvage")
    private val baccarat = material(202, "Baccarat Rouge By LUZI", "SYNTHETIC", "MIDDLE", "ORIENTAL", 1.05, "LUZI", "Baccarat Rouge")
    private val parfarome = material(301, "Sauvage By PARFAROME", "SYNTHETIC", "MIDDLE", "ORIENTAL", 0.96, "PARFAROME", "Sauvage")
    private val macrabame = material(401, "Pink Chiffon By MACBRAME", "SYNTHETIC", "MIDDLE", "ORIENTAL", 0.95, "MACRABAME", "Pink Chiffon")
    private val alcohol = material(901, "Perfumer Alcohol", "SYNTHETIC", "MIDDLE", "FRESH", 0.80)
    private val dpg = material(902, "Dipropylene Glycol", "SYNTHETIC", "MIDDLE", "FRESH", 1.02)
    private val catalog = listOf(bergamot, lavender, cedar, lemon, sauvage, baccarat, parfarome, macrabame, alcohol, dpg)

    @Test
    fun naturalCitrusConstrainsTypeAndDoesNotInventMissingNoteCoverage() {
        val plan = requireNotNull(planner.create("buat parfum natural citrus 100g 20%", catalog))

        assertEquals(setOf(bergamot.id, lemon.id), aromaIds(plan))
        assertTrue(aromas(plan).all { it.type == "NATURAL" })
        assertTrue(plan.formula.ingredients.any { it.resolvedIngredientId == alcohol.id })
        assertValid(plan, catalog, 100.0, 20.0)
    }

    @Test
    fun naturalBlendUsesExactlyNamedMaterialsIncludingIndonesianAlias() {
        val patchouli = material(105, "Patchouli Essential Oil", "NATURAL", "BASE", "WOODY", 0.96)
        val rows = catalog + patchouli
        val plan = requireNotNull(planner.create("campur parfum natural lavender dan nilam 80g 25%", rows))

        assertEquals(setOf(lavender.id, patchouli.id), aromaIds(plan))
        assertValid(plan, rows, 80.0, 25.0)
    }

    @Test
    fun oneBibitNeedsNeitherThreeAromasNorTopBaseMetadata() {
        val rows = listOf(sauvage, alcohol)
        val plan = requireNotNull(planner.create("buat parfum bibit LUZI Sauvage 100g 20%", rows))

        assertEquals(setOf(sauvage.id), aromaIds(plan))
        assertEquals(20.0, plan.formula.ingredients.single { it.resolvedIngredientId == sauvage.id }.amount, 0.0001)
        assertValid(plan, rows, 100.0, 20.0)
    }

    @Test
    fun twoNamedBibitOilsGetNoUnrequestedAromas() {
        val plan = requireNotNull(planner.create("campur bibit LUZI Sauvage dan Baccarat Rouge 100g 20%", catalog))

        assertEquals(setOf(sauvage.id, baccarat.id), aromaIds(plan))
        assertTrue(plan.formula.ingredients.filter { it.noteType != "DILUENT" }.all { it.amount == 10.0 })
        assertValid(plan, catalog, 100.0, 20.0)
    }

    @Test
    fun brandOnlyBibitRequestUsesThatBrandEvenWithAllMiddleNotes() {
        listOf("LUZI", "PARFAROME", "MACRABAME", "MACBRAME").forEach { brand ->
            val plan = requireNotNull(planner.create("buat parfum bibit $brand 100g 20%", catalog)) { brand }
            val expected = if (brand == "MACBRAME") "MACRABAME" else brand
            assertTrue(aromas(plan).all { it.type == "SYNTHETIC" && it.brand == expected })
            assertValid(plan, catalog, 100.0, 20.0)
        }
    }

    @Test
    fun absentNamedAromaIsNeverReplacedByRelatedFamilyMaterial() {
        val result = planner.createValidated("buat parfum natural bergamot dan neroli 100g 20%", catalog)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("neroli"))
        assertTrue(planner.recommend("rekomendasi natural bergamot dan neroli", catalog).isEmpty())
    }

    @Test
    fun missingMaterialAtRequestedBrandDoesNotLeakAnotherBrand() {
        assertNull(planner.create("buat bibit PARFAROME Baccarat Rouge 100g 20%", catalog))
        assertTrue(planner.recommend("rekomendasi bibit PARFAROME Baccarat Rouge", catalog).isEmpty())
        assertNull(planner.create("buat parfum merek TIDAKADA citrus 100g 20%", catalog))
        assertNull(planner.create("buat parfum bibit PARFAROME 100g 20%", listOf(sauvage, alcohol)))
    }

    @Test
    fun naturalRequestDoesNotAdmitAnExplicitSyntheticAroma() {
        assertNull(planner.create("buat parfum natural Sauvage 100g 20%", catalog))
        assertTrue(planner.recommend("rekomendasi natural Sauvage", catalog).isEmpty())
    }

    @Test
    fun ambiguousMaterialAsksForBrandInsteadOfChoosingFirstRow() {
        val result = planner.createValidated("buat bibit Sauvage 100g 20%", catalog)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("ambigu"))
        assertTrue(planner.recommend("rekomendasi bibit Sauvage", catalog).isEmpty())
    }

    @Test
    fun descriptionMentionOfMissingMaterialIsNotAnIngredientSubstitute() {
        val row = sauvage.copy(description = "Neroli, rose and citrus impression")

        assertNull(planner.create("buat bibit LUZI neroli 100g 20%", listOf(row, alcohol)))
    }

    @Test
    fun solventMustExistAtRequestedDilution() {
        val result = planner.createValidated("buat parfum natural bergamot 100g edp", listOf(bergamot))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Pelarut"))
        assertNull(planner.create("buat bibit LUZI Sauvage 100g 20%", listOf(sauvage)))
    }

    @Test
    fun explicitConcentrateWorksWithOrWithoutSolventInCatalog() {
        listOf(listOf(sauvage), catalog).forEach { rows ->
            listOf("konsentrat", "100%", "tanpa pelarut").forEach { concentration ->
                val plan = requireNotNull(planner.create("buat bibit LUZI Sauvage 50g $concentration", rows))
                assertEquals(setOf(sauvage.id), aromaIds(plan))
                assertFalse(plan.formula.ingredients.any { it.noteType == "DILUENT" })
                assertValid(plan, rows, 50.0, 100.0)
            }
        }
    }

    @Test
    fun requestedSolventIsHonoredAndMissingOneCannotBeSubstituted() {
        val plan = requireNotNull(planner.create("buat bibit LUZI Sauvage dengan DPG 100g 20%", catalog))

        assertEquals(dpg.id, plan.formula.ingredients.single { it.noteType == "DILUENT" }.resolvedIngredientId)
        assertNull(planner.create("buat bibit LUZI Sauvage dengan DPG 100g 20%", listOf(sauvage, alcohol)))
        assertNull(planner.create("buat bibit LUZI Sauvage dengan ethanol 100g 100%", catalog))
        assertValid(plan, catalog, 100.0, 20.0)
    }

    @Test
    fun conflictingConcentrateAndPercentageIsRejected() {
        assertNull(planner.create("buat bibit LUZI Sauvage tanpa pelarut 100g 20%", catalog))
    }

    @Test
    fun signedLocaleDecimalsAndMassUnitsPreserveRequestedBatch() {
        val requests = mapOf("+100,5 gram" to 100.5, "100.5g" to 100.5, "0,1 kg" to 100.0, "100000 mg" to 100.0,
            "1.000,5g" to 1000.5, "1,000.5g" to 1000.5, "0,001g" to 0.001)
        requests.forEach { (batch, expected) ->
            val concentration = if (expected < 0.01) "100%" else "+12,5 persen"
            val plan = requireNotNull(planner.create("buat bibit LUZI Sauvage $batch $concentration", catalog)) { batch }
            assertValid(plan, catalog, expected, if (expected < 0.01) 100.0 else 12.5)
            assertFalse(plan.usedDefaultBatch)
        }
    }

    @Test
    fun invalidAndAmbiguousNumbersOrUnitsCannotBecomeDefaultBatch() {
        listOf("-100g", "−100g", "- 100g", "--100g", "- +100g", "0g", "100001g", "1..5g",
            "1,000g", "100oz", "100 drops", "10g 20g", "NaNg", "Infinityml").forEach { batch ->
            assertNull(batch, planner.create("buat bibit LUZI Sauvage $batch 20%", catalog))
        }
    }

    @Test
    fun invalidConcentrationNeverBecomesDefaultEdp() {
        listOf("-20%", "−20%", "- 20%", "--20%", "0%", "101%", "NaN%", "Infinity%", "10% 20%",
            "konsentrasi 20", "1..5%").forEach { value ->
            assertNull(value, planner.create("buat bibit LUZI Sauvage 100g $value", catalog))
        }
    }

    @Test
    fun maximumBatchHasFinitePositiveRoundedIngredients() {
        val plan = requireNotNull(planner.create("buat bibit LUZI Sauvage dan Baccarat Rouge 100000g 20%", catalog))

        assertValid(plan, catalog, 100000.0, 20.0)
    }

    @Test
    fun volumeUsesSumOfComponentVolumesNotArithmeticMeanDensity() {
        val plan = requireNotNull(planner.create("buat bibit LUZI Sauvage dan Baccarat Rouge 100ml 20%", catalog))
        val expected = 100.0 / (0.2 * (0.5 / sauvage.densityGml + 0.5 / baccarat.densityGml) + 0.8 / alcohol.densityGml)
        val volume = plan.formula.ingredients.sumOf { item ->
            item.amount / catalog.single { it.id == item.resolvedIngredientId }.densityGml
        }

        assertValid(plan, catalog, expected, 20.0)
        assertEquals(100.0, volume, 0.001)
        val litres = requireNotNull(planner.create("buat bibit LUZI Sauvage dan Baccarat Rouge 0,1 liter 20%", catalog))
        assertEquals(plan.totalWeightG, litres.totalWeightG, 0.0001)
    }

    @Test
    fun volumeRequiresKnownDensityForEverySelectedIngredientAndSolvent() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -0.95).forEach { density ->
            assertNull(planner.create("buat bibit LUZI Sauvage 100ml 20%", listOf(sauvage.copy(densityGml = density), alcohol)))
            assertNull(planner.create("buat bibit LUZI Sauvage 100ml 20%", listOf(sauvage, alcohol.copy(densityGml = density))))
            assertNull(planner.create("buat bibit LUZI Sauvage 100ml 100%", listOf(sauvage.copy(densityGml = density))))
        }
    }

    @Test
    fun massDoesNotFabricateOrRequireUnknownDensity() {
        val rows = listOf(sauvage.copy(densityGml = Double.NaN), alcohol.copy(densityGml = 0.0))
        val plan = requireNotNull(planner.create("buat bibit LUZI Sauvage 100g 20%", rows))

        assertValid(plan, rows, 100.0, 20.0)
    }

    @Test
    fun unspecifiedBatchUsesExplicitlyReportedDefault() {
        val plan = requireNotNull(planner.create("buat bibit LUZI Sauvage 20%", catalog))

        assertTrue(plan.usedDefaultBatch)
        assertValid(plan, catalog, 100.0, 20.0)
    }

    @Test
    fun recommendationRequiresEvidenceAndHonorsLimitsAndConstraints() {
        assertTrue(planner.recommend("rekomendasi parfum", catalog).isEmpty())
        assertTrue(planner.recommend("rekomendasi asteroid", catalog).isEmpty())
        assertTrue(planner.recommend("rekomendasi bibit LUZI", catalog, 0).isEmpty())
        assertTrue(planner.recommend("rekomendasi bibit LUZI", catalog, -1).isEmpty())
        assertEquals(setOf(sauvage.id, baccarat.id), planner.recommend("rekomendasi bibit LUZI", catalog).map { it.id }.toSet())
        assertEquals(setOf(bergamot.id, lemon.id), planner.recommend("rekomendasi natural citrus", catalog).map { it.id }.toSet())
        assertEquals(1, planner.recommend("rekomendasi bibit LUZI", catalog, 1).size)
    }

    @Test
    fun negatedTypeIsRespectedAtTokenBoundaries() {
        val plan = requireNotNull(planner.create("buat parfum natural citrus tanpa bibit 100g 20%", catalog))

        assertTrue(aromas(plan).all { it.type == "NATURAL" })
        assertTrue(planner.recommend("rekomendasi supernatural asteroid", catalog).isEmpty())
        assertNull(planner.create("buat parfum tanpa natural tanpa bibit citrus 100g 20%", catalog))
    }

    @Test
    fun unparsedMaterialExclusionsCannotAccidentallyIncludeExcludedMaterial() {
        assertNull(planner.create("buat parfum natural citrus tanpa lemon 100g 20%", catalog))
    }

    @Test
    fun multiWordTypeSupportsWhitespaceAndPlural() {
        val plan = requireNotNull(planner.create("buat parfum essential   oils citrus 100g 20%", catalog))

        assertTrue(aromas(plan).all { it.type == "NATURAL" })
        assertValid(plan, catalog, 100.0, 20.0)
    }

    @Test
    fun numericProductIdentityIsNotMisreadAsBatchUnit() {
        val vip = material(203, "212 VIP By LUZI", "SYNTHETIC", "MIDDLE", "ORIENTAL", 0.95, "LUZI", "212 VIP")
        val rows = listOf(vip, alcohol)
        val plan = requireNotNull(planner.create("buat bibit LUZI 212 VIP 50g 20%", rows))

        assertEquals(setOf(vip.id), aromaIds(plan))
        assertValid(plan, rows, 50.0, 20.0)
    }

    @Test
    fun packagingInLegacyAliasesNeverHidesRecognizedBatchUnits() {
        val packaged = sauvage.copy(inspiredBy = "Sauvage 500 g 500g 50ml 100ml")
        val rows = listOf(packaged, alcohol)
        listOf("50 g" to 50.0, "500 g" to 500.0, "500g" to 500.0).forEach { (batch, total) ->
            val plan = requireNotNull(planner.create("buat bibit LUZI Sauvage $batch 20%", rows))
            assertFalse(plan.usedDefaultBatch)
            assertValid(plan, rows, total, 20.0)
        }
        listOf("50ml" to 50.0, "100ml" to 100.0).forEach { (batch, volume) ->
            val plan = requireNotNull(planner.create("buat bibit LUZI Sauvage $batch 20%", rows))
            assertFalse(plan.usedDefaultBatch)
            val actualVolume = plan.formula.ingredients.sumOf { item ->
                item.amount / rows.single { it.id == item.resolvedIngredientId }.densityGml
            }
            assertEquals(volume, actualVolume, 0.001)
        }
    }

    @Test
    fun absoluteAndGradeIdentityCannotBeReplacedByOrdinaryOil() {
        val roseOil = material(501, "Rose Essential Oil", "NATURAL", "HEART", "FLORAL", 0.90)
        val roseAbsolute = material(502, "Rose Absolute", "NATURAL", "HEART", "FLORAL", 0.98)
        val rows = listOf(roseOil, roseAbsolute, alcohol)
        val plan = requireNotNull(planner.create("buat parfum natural Rose Absolute 100g 20%", rows))

        assertEquals(setOf(roseAbsolute.id), aromaIds(plan))
        assertValid(plan, rows, 100.0, 20.0)
        assertNull(planner.create("buat parfum natural Rose Absolute 100g 20%", listOf(roseOil, alcohol)))
        assertNull(planner.create("buat parfum natural Rose Absolute 100g 20%", listOf(roseOil, alcohol.copy(name = "Absolute 96% Alcohol"))))
        assertNull(planner.create("buat parfum natural bergamot FCF 100g 20%", catalog))
        assertNull(planner.create("buat bibit LUZI Sauvage premium 100g 20%", catalog))
    }

    @Test
    fun invalidIdsAndUnknownTypesCannotBecomeRecipeMaterials() {
        listOf(sauvage.copy(id = 0), sauvage.copy(id = -2), sauvage.copy(type = "UNKNOWN")).forEach { invalid ->
            assertNull(planner.create("buat bibit LUZI Sauvage 100g 20%", listOf(invalid, alcohol)))
            assertTrue(planner.recommend("rekomendasi bibit LUZI Sauvage", listOf(invalid, alcohol)).isEmpty())
        }
        assertNull(planner.create("buat parfum natural citrus 100g 20%", emptyList()))
    }

    private fun aromas(plan: GroundedFormulaPlanner.Plan): List<IngredientEntity> =
        catalog.filter { row -> row.id in aromaIds(plan) }

    private fun aromaIds(plan: GroundedFormulaPlanner.Plan): Set<Long?> =
        plan.formula.ingredients.filter { it.noteType != "DILUENT" }.map { it.resolvedIngredientId }.toSet()

    private fun assertValid(plan: GroundedFormulaPlanner.Plan, rows: List<IngredientEntity>, total: Double, concentration: Double) {
        val items = plan.formula.ingredients
        assertTrue(items.isNotEmpty())
        assertTrue(items.all { (it.resolvedIngredientId ?: 0L) > 0L && it.amount.isFinite() && it.amount > 0.0 && it.unit == "g" })
        assertEquals(items.size, items.map { it.resolvedIngredientId }.distinct().size)
        val inputs = items.map { item ->
            val row = rows.single { it.id == item.resolvedIngredientId }
            assertEquals(row.toDomain().name, item.ingredientName)
            FormulaIngredientInput(row.toDomain(), item.amount, MeasurementUnit.GRAM)
        }
        val actual = FormulaCalculationEngine.calculateFormula(inputs)
        assertEquals(total, items.sumOf { it.amount }, 0.01)
        assertEquals(total, plan.totalWeightG, 0.01)
        assertEquals(total, requireNotNull(plan.formula.totalWeightG), 0.01)
        assertEquals(concentration, plan.concentrationPct, 0.01)
        assertEquals(concentration, actual.concentrationPct, 0.01)
    }

    private fun material(
        id: Long, name: String, type: String, note: String, family: String, density: Double,
        brand: String? = null, inspiredBy: String? = null
    ) = IngredientEntity(
        id = id, name = name, type = type, brand = brand, noteType = note,
        fragranceFamily = family, densityGml = density, inspiredBy = inspiredBy
    )
}
