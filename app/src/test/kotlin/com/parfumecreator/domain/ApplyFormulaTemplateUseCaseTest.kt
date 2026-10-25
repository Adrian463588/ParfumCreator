package com.parfumecreator.domain

import com.google.gson.Gson
import com.parfumecreator.core.formula.FormulaTemplateParser
import com.parfumecreator.data.local.database.IngredientEntity
import com.parfumecreator.data.local.database.IngredientNameCleaner
import com.parfumecreator.domain.model.*
import com.parfumecreator.domain.usecase.ApplyFormulaTemplateUseCase
import com.parfumecreator.domain.usecase.TemplateApplication
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Bundled-catalog checks precede synthetic fixtures; neither proves installed-device behavior. */
class ApplyFormulaTemplateUseCaseTest {
    private val apply = ApplyFormulaTemplateUseCase()
    private val rose = Ingredient(1, "Rose Absolute", IngredientType.NATURAL, noteType = NoteType.HEART, fragranceFamily = FragranceFamily.FLORAL)
    private val jasmine = Ingredient(2, "Jasmine Sambac Absolute", IngredientType.NATURAL, noteType = NoteType.HEART, fragranceFamily = FragranceFamily.FLORAL)

    @Test
    fun realAssetsBindOnlyFourNaturalIdentitiesAcross70SlotsAndNoCommercialSlots() {
        val catalog = realCatalog()
        val natural = parseAsset(NATURAL)
        val commercial = parseAsset(COMMERCIAL)
        val boundNames = mutableSetOf<String>()
        val naturalBindings = natural.map { template ->
            apply.initialBindings(template, catalog).also { bindings ->
                bindings.forEach { (slot, id) ->
                    val actual = catalog.single { it.id == id }
                    boundNames.add(template.ingredients[slot].name)
                    assertEquals(IngredientType.NATURAL, actual.type)
                    assertEquals(listOf(actual), apply.eligibleIngredients(template.ingredients[slot], listOf(actual)))
                }
                assertTrue(bindings.size < template.ingredients.size)
            }
        }
        assertEquals(100, natural.size)
        assertEquals(100, commercial.size)
        assertEquals(70, naturalBindings.sumOf { it.size })
        assertEquals(setOf("Cedarwood Atlas EO", "Ylang Ylang Extra EO", "Rose Absolute", "Jasmine Sambac Absolute"), boundNames)
        commercial.forEach { assertTrue(apply.initialBindings(it, catalog).isEmpty()) }
    }

    @Test
    fun realCommercialNameHitsRemainAmbiguousInsteadOfChoosingTheFirstCatalogRow() {
        val catalog = realCatalog()
        val sourceItems = parseAsset(COMMERCIAL).flatMap { it.ingredients }.distinctBy { it.brand to it.name }
        assertEquals(55, sourceItems.size)
        val hits = sourceItems.filter { item ->
            catalog.any { it.type == IngredientType.SYNTHETIC && it.brand == "MACRABAME" && it.name.equals(item.name, ignoreCase = true) }
        }
        assertEquals(14, hits.size)
        hits.forEach { item ->
            val candidates = catalog.filter { it.brand == "MACRABAME" && it.name.equals(item.name, ignoreCase = true) }
            assertTrue(candidates.size in 2..3)
            val template = fixtureTemplate(listOf(item)).copy(category = "SYNTHETIC")
            assertTrue(apply.initialBindings(template, catalog).isEmpty())
            assertFailed(apply(template, catalog, emptyMap(), 10.0))
        }
    }

    @Test
    fun realLegacyStartersApplyWithActualCatalogObjectsAndScaledMass() {
        val catalog = realCatalog()
        val starters = parseAsset(LEGACY).filter { it.id in setOf(31, 32) }
        assertEquals(2, starters.size)
        starters.forEach { template ->
            val bindings = apply.initialBindings(template, catalog)
            assertEquals(template.ingredients.size, bindings.size)
            val result = apply(template, catalog, bindings, 37.123456).getOrThrow()
            assertFalse(result.isAdaptation)
            assertEquals(37.123456, result.calculation.totalWeightG, 1e-9)
            assertEquals(template.concentrationPct, result.calculation.concentrationPct, 1e-9)
            assertNull(result.calculation.validationError)
            result.inputs.forEach { assertSame(catalog.single { actual -> actual.id == it.ingredient.id }, it.ingredient) }
            assertTrue(result.notes.contains(LEGACY))
        }
    }

    @Test
    fun realIncompleteStudyFailsWithoutChangingSourceTemplate() {
        val template = parseAsset(NATURAL).first()
        val original = template.copy(ingredients = template.ingredients.map { it.copy() }, sourceUrls = template.sourceUrls.toList())
        val catalog = realCatalog()
        assertFailed(apply(template, catalog, apply.initialBindings(template, catalog), 25.0, acknowledgeAdaptation = true))
        assertEquals(original, template)
    }

    @Test
    fun all200StudiesCanBecomeValidatedDraftsAfterExplicitTestSelections() {
        val catalog = realCatalog()
        val templates = parseAsset(NATURAL) + parseAsset(COMMERCIAL)
        assertEquals(200, templates.size)
        templates.forEach { template ->
            // These are explicit TEST choices to exercise application, not aroma recommendations.
            val bindings = template.ingredients.mapIndexed { index, source ->
                index to apply.eligibleIngredients(source, catalog).first().id
            }.toMap()
            assertFailed(apply(template, catalog, bindings, 23.456789, acknowledgeAdaptation = false))
            val result = apply(template, catalog, bindings, 23.456789, acknowledgeAdaptation = true).getOrThrow()
            assertTrue(result.isAdaptation)
            assertEquals(23.456789, result.calculation.totalWeightG, 1e-9)
            assertEquals(100.0, result.calculation.concentrationPct, 1e-9)
            assertNull(result.calculation.validationError)
            assertEquals(result.inputs.size, result.inputs.map { it.ingredient.id }.distinct().size)
            val referenceTotal = template.ingredients.sumOf { it.amount }
            result.inputs.forEach { input ->
                val expected = template.ingredients.mapIndexed { index, source ->
                    if (bindings[index] == input.ingredient.id) source.amount / referenceTotal * 23.456789 else 0.0
                }.sum()
                assertEquals(expected, input.amount, 1e-9)
                assertSame(catalog.single { it.id == input.ingredient.id }, input.ingredient)
            }
            assertTrue(result.notes.contains(requireNotNull(template.sourceId)))
        }
    }

    @Test
    fun fixtureScalesMixedGramAndMilligramWeightsWithoutDisplayRounding() {
        val template = fixtureTemplate(listOf(
            FormulaTemplateItem(rose.name, 2.5),
            FormulaTemplateItem(jasmine.name, 7500.0, unit = "mg")
        ))
        val target = 12.345678901
        val result = apply(template, listOf(rose, jasmine), emptyMap(), target).getOrThrow()
        assertEquals(target * 0.25, result.inputs.single { it.ingredient.id == rose.id }.amount, 1e-12)
        assertEquals(target * 0.75, result.inputs.single { it.ingredient.id == jasmine.id }.amount, 1e-12)
        assertTrue(result.inputs.all { it.unit == MeasurementUnit.GRAM })
        assertEquals(target, result.calculation.totalWeightG, 1e-9)
        assertEquals(100.0, result.calculation.concentrationPct, 1e-9)
        assertFalse(result.isAdaptation)
    }

    @Test
    fun fixturesNeverAutoSubstituteGradesSpeciesOrExtractionMethods() {
        val differentIdentities = listOf(
            "Bergamot FCF EO" to "Bergamot Essential Oil",
            "Lavender EO" to "Lavender Essential Oil (40/42)",
            "Patchouli EO" to "Patchouli Essential Oil (Dark)",
            "Vetiver Haiti EO" to "Vetiver Essential Oil (Java)",
            "Cedarwood Virginia EO" to "Cedarwood Atlas Oil",
            "Vanilla CO2" to "Vanilla Planifolia Absolute",
            "Rose Absolute" to "Rose Essential Oil"
        )
        differentIdentities.forEach { (source, available) ->
            val template = fixtureTemplate(listOf(FormulaTemplateItem(source, 10.0)))
            val catalog = listOf(rose.copy(name = available))
            assertTrue(source, apply.initialBindings(template, catalog).isEmpty())
            assertFailed(apply(template, catalog, emptyMap(), 10.0, acknowledgeAdaptation = true))
        }
    }

    @Test
    fun fixtureUnverifiedSlotStillRequiresExplicitSelectionAndAcknowledgementWhenNamesMatch() {
        val item = FormulaTemplateItem("Fixture floral accord", 10.0, type = IngredientType.SYNTHETIC, brand = "LUZI", requiresSelection = true)
        val ingredient = Ingredient(3, item.name, IngredientType.SYNTHETIC, brand = "LUZI")
        val template = fixtureTemplate(listOf(item)).copy(category = "SYNTHETIC")
        val catalog = listOf(ingredient)
        assertTrue(apply.initialBindings(template, catalog).isEmpty())
        assertFailed(apply(template, catalog, emptyMap(), 10.0, acknowledgeAdaptation = true))
        assertFailed(apply(template, catalog, mapOf(0 to ingredient.id), 10.0))
        val result = apply(template, catalog, mapOf(0 to ingredient.id), 10.0, acknowledgeAdaptation = true).getOrThrow()
        assertTrue(result.isAdaptation)
        assertSame(ingredient, result.inputs.single().ingredient)
    }

    @Test
    fun fixtureManualAdaptationKeepsMappingProvenanceAndOriginalTemplate() {
        val source = FormulaTemplateItem("Fixture unavailable botanical EO", 10.0)
        val template = fixtureTemplate(listOf(source))
        val original = template.copy(ingredients = template.ingredients.map { it.copy() }, sourceUrls = template.sourceUrls.toList())
        val bindings = mapOf(0 to rose.id)
        assertFailed(apply(template, listOf(rose), bindings, 20.0))
        val result = apply(template, listOf(rose), bindings, 20.0, acknowledgeAdaptation = true).getOrThrow()
        assertTrue(result.isAdaptation)
        assertTrue(result.notes.contains(source.name))
        assertTrue(result.notes.contains(rose.name))
        assertTrue(result.notes.contains("ID ${rose.id}"))
        assertTrue(result.notes.contains(template.sourceFile))
        assertTrue(result.notes.contains(requireNotNull(template.sourceId)))
        template.sourceUrls.forEach { assertTrue(result.notes.contains(it)) }
        assertSame(rose, result.inputs.single().ingredient)
        assertEquals(original, template)
        assertEquals(mapOf(0 to rose.id), bindings)
    }

    @Test
    fun fixtureUsesCurrentCatalogMetadataInsteadOfTemplateNoteAndFamily() {
        val item = FormulaTemplateItem(rose.name, 10.0, noteType = NoteType.TOP, fragranceFamily = FragranceFamily.CITRUS)
        val template = fixtureTemplate(listOf(item))
        val result = apply(template, listOf(rose), emptyMap(), 10.0).getOrThrow()
        assertSame(rose, result.inputs.single().ingredient)
        assertEquals(1.0, result.calculation.middleRatio, 0.0)
        assertEquals(0.0, result.calculation.topRatio, 0.0)
        assertEquals(FragranceFamily.FLORAL, result.calculation.dominantSubFamily)
    }

    @Test
    fun fixtureMacbrameSpellingAliasPreservesSupplierIdentity() {
        val item = FormulaTemplateItem("Fixture accord", 10.0, type = IngredientType.SYNTHETIC, brand = "Macbrame")
        val ingredient = Ingredient(3, item.name, IngredientType.SYNTHETIC, brand = "MACRABAME")
        val template = fixtureTemplate(listOf(item)).copy(category = "SYNTHETIC")
        assertEquals(mapOf(0 to ingredient.id), apply.initialBindings(template, listOf(ingredient)))
        assertFalse(apply(template, listOf(ingredient), emptyMap(), 10.0).getOrThrow().isAdaptation)
    }

    @Test
    fun fixtureRejectsTypeAndBrandMismatchesEvenWithAcknowledgement() {
        val natural = fixtureTemplate(listOf(FormulaTemplateItem(rose.name, 10.0)))
        assertFailed(apply(natural, listOf(rose.copy(type = IngredientType.SYNTHETIC)), mapOf(0 to rose.id), 10.0, true))
        val item = FormulaTemplateItem("Fixture accord", 10.0, type = IngredientType.SYNTHETIC, brand = "LUZI")
        val branded = fixtureTemplate(listOf(item)).copy(category = "SYNTHETIC")
        val wrongSupplier = Ingredient(3, item.name, IngredientType.SYNTHETIC, brand = "PARFAROME")
        assertTrue(apply.eligibleIngredients(item, listOf(wrongSupplier)).isEmpty())
        assertFailed(apply(branded, listOf(wrongSupplier), mapOf(0 to wrongSupplier.id), 10.0, true))
    }

    @Test
    fun fixtureRejectsFragranceToCarrierAndCarrierToFragranceBindings() {
        val ethanol = Ingredient(3, "Ethanol", IngredientType.NATURAL)
        listOf(FormulaTemplateItem(rose.name, 10.0) to ethanol, FormulaTemplateItem(ethanol.name, 10.0) to rose)
            .forEach { (source, selected) ->
                assertTrue(apply.eligibleIngredients(source, listOf(selected)).isEmpty())
                assertFailed(apply(fixtureTemplate(listOf(source)), listOf(selected), mapOf(0 to selected.id), 10.0, true))
            }
    }

    @Test
    fun fixtureEligiblePickerExcludesInvalidIdsBlankNamesWrongTypesSuppliersAndCarriers() {
        val source = FormulaTemplateItem("Fixture accord", 10.0, type = IngredientType.SYNTHETIC, brand = "LUZI")
        val valid = Ingredient(3, "Fixture floral", IngredientType.SYNTHETIC, brand = "LUZI")
        val candidates = listOf(
            valid, valid.copy(id = 0), valid.copy(id = 4, name = " "),
            valid.copy(id = 5, type = IngredientType.NATURAL), valid.copy(id = 6, brand = "PARFAROME"),
            valid.copy(id = 7, name = "Ethanol")
        )
        assertEquals(listOf(valid), apply.eligibleIngredients(source, candidates))
    }

    @Test
    fun fixtureUnknownNonpositiveAndDuplicateCatalogIdsReturnFailureResults() {
        val template = fixtureTemplate(listOf(FormulaTemplateItem(rose.name, 10.0)))
        listOf(0L, -1L, 999L).forEach { id ->
            assertFailed(apply(template, listOf(rose), mapOf(0 to id), 10.0, true))
        }
        assertFailed(apply(template, listOf(rose, rose.copy(name = "Fixture duplicate ID")), mapOf(0 to rose.id), 10.0, true))
        assertFailed(apply(template, listOf(rose.copy(id = 0)), emptyMap(), 10.0, true))
    }

    @Test
    fun fixtureOutOfRangeBindingSlotsFailEvenWhenAllRequiredSlotsAreBound() {
        val template = fixtureTemplate(listOf(FormulaTemplateItem(rose.name, 10.0)))
        listOf(-1, 1).forEach { extraSlot ->
            assertFailed(apply(template, listOf(rose), mapOf(0 to rose.id, extraSlot to rose.id), 10.0, true))
        }
    }

    @Test
    fun fixtureMergesRepeatedSelectedIdsWithoutLosingMassOrChangingSourceItems() {
        val template = fixtureTemplate()
        val original = template.copy(ingredients = template.ingredients.map { it.copy() })
        val result = apply(template, listOf(rose), mapOf(0 to rose.id, 1 to rose.id), 23.456789, true).getOrThrow()
        assertEquals(1, result.inputs.size)
        assertEquals(rose.id, result.inputs.single().ingredient.id)
        assertEquals(23.456789, result.inputs.single().amount, 1e-9)
        assertEquals(23.456789, result.calculation.totalWeightG, 1e-9)
        assertEquals(1, result.calculation.calculatedItems.size)
        assertTrue(result.isAdaptation)
        assertEquals(original, template)
        assertEquals(2, template.ingredients.size)
    }

    @Test
    fun fixtureInvalidTargetMassAlwaysReturnsFailureInsteadOfThrowing() {
        val template = fixtureTemplate()
        listOf(0.0, -1.0, 0.0000001, 100_001.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { mass ->
            assertFailed(apply(template, listOf(rose, jasmine), emptyMap(), mass))
        }
    }

    @Test
    fun fixtureInvalidSourceWeightsUnitsAndTemplatesReturnFailureResults() {
        val source = FormulaTemplateItem(rose.name, 10.0)
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { amount ->
            assertFailed(apply(fixtureTemplate(listOf(source.copy(amount = amount))), listOf(rose), emptyMap(), 10.0))
        }
        assertFailed(apply(fixtureTemplate(listOf(source.copy(unit = "ml"))), listOf(rose), emptyMap(), 10.0))
        val overflow = fixtureTemplate(listOf(source.copy(amount = Double.MAX_VALUE), source.copy(amount = Double.MAX_VALUE)))
        assertFailed(apply(overflow, listOf(rose), emptyMap(), 10.0))
        val valid = fixtureTemplate(listOf(source))
        listOf(valid.copy(id = 0), valid.copy(name = " "), valid.copy(ingredients = emptyList())).forEach {
            assertFailed(apply(it, listOf(rose), emptyMap(), 10.0))
        }
    }

    @Test
    fun fixtureRejectsConcentrationMismatchWithoutInventingDilution() {
        val template = fixtureTemplate().copy(concentrationPct = 20.0)
        assertFailed(apply(template, listOf(rose, jasmine), emptyMap(), 10.0))
        listOf(0.0, -1.0, 101.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { concentration ->
            assertFailed(apply(template.copy(concentrationPct = concentration), listOf(rose, jasmine), emptyMap(), 10.0))
        }
    }

    @Test
    fun fixturePreservesAnExplicitCarrierRatioWhenScalingFinishedFormula() {
        val ethanol = Ingredient(3, "Ethanol", IngredientType.NATURAL)
        val template = fixtureTemplate(listOf(FormulaTemplateItem(rose.name, 2.0), FormulaTemplateItem(ethanol.name, 8.0)))
            .copy(concentrationPct = 20.0, concentrationType = ConcentrationType.PARFUM_EXTRAIT)
        val result = apply(template, listOf(rose, ethanol), emptyMap(), 25.0).getOrThrow()
        assertEquals(5.0, result.inputs.single { it.ingredient.id == rose.id }.amount, 1e-9)
        assertEquals(20.0, result.inputs.single { it.ingredient.id == ethanol.id }.amount, 1e-9)
        assertEquals(25.0, result.calculation.totalWeightG, 1e-9)
        assertEquals(20.0, result.calculation.concentrationPct, 1e-9)
        assertFalse(result.isAdaptation)
    }

    private fun assertFailed(result: Result<TemplateApplication>) {
        assertTrue("Invalid application must return Result.failure", result.isFailure)
        assertNotNull(result.exceptionOrNull())
        assertNull(result.getOrNull())
    }

    private fun fixtureTemplate(items: List<FormulaTemplateItem> = listOf(
        FormulaTemplateItem(rose.name, 6.0), FormulaTemplateItem(jasmine.name, 4.0)
    )) = FormulaTemplate(
        id = 1001, name = "Fixture concentrate", category = "NATURAL", fragranceFamily = FragranceFamily.FLORAL,
        concentrationPct = 100.0, concentrationType = ConcentrationType.PARFUM_EXTRAIT,
        description = "Synthetic test fixture; no safety or supplier verification.", ingredients = items,
        sourceId = "FIXTURE-001", sourceFile = "fixture-source.json", sourceUrls = listOf("https://example.org/fixture"),
        isExperimental = true
    )

    private data class CatalogAsset(val ingredients: List<IngredientEntity>)

    private fun realCatalog(): List<Ingredient> = listOf("natural", "luzi", "parfarome", "macrabame")
        .flatMap { group ->
            asset("ingredients_$group.json").reader(Charsets.UTF_8).use { Gson().fromJson(it, CatalogAsset::class.java).ingredients }
        }
        // Only metadata is real here: positive IDs emulate Room allocation in this JVM test.
        .mapIndexed { index, entity -> entity.copy(id = index + 1L, name = IngredientNameCleaner.clean(entity.name)) }
        .filter { it.name.isNotBlank() }
        .map { it.toDomain() }

    private fun parseAsset(file: String) = FormulaTemplateParser.parse(asset(file).readText(Charsets.UTF_8), file)

    private fun asset(file: String): File = listOf(File("src/main/assets/data"), File("app/src/main/assets/data"))
        .firstOrNull(File::isDirectory)?.resolve(file)?.takeIf(File::isFile)
        ?: error("Real bundled asset required: $file")

    private companion object {
        const val LEGACY = "formula_templates.json"
        const val NATURAL = "100_resep_parfum_natural_eksperimental_2026.json"
        const val COMMERCIAL = "100_racikan_bibit_luzi_macbrame_parfarome_2026.json"
    }
}
