package com.parfumecreator.core

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.parfumecreator.core.formula.FormulaTemplateParser
import com.parfumecreator.domain.model.IngredientType
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Real-asset integrity checks are separate from deliberately malformed, in-memory fixtures. */
class FormulaTemplateParserTest {
    @Test
    fun realAssetsContainAll200StudiesAnd32LegacyTemplatesWithDistinctIds() {
        assertEquals(listOf(LEGACY, NATURAL, COMMERCIAL), FormulaTemplateParser.assetFiles)
        val legacy = parseAsset(LEGACY)
        val natural = parseAsset(NATURAL)
        val commercial = parseAsset(COMMERCIAL)

        assertEquals((1..32).toSet(), legacy.map { it.id }.toSet())
        assertEquals(32, legacy.size)
        assertEquals(100, natural.size)
        assertEquals(100, commercial.size)
        assertEquals((1001..1100).toSet(), natural.map { it.id }.toSet())
        assertEquals((2001..2100).toSet(), commercial.map { it.id }.toSet())
        assertEquals((1..100).map { "NAT-%03d".format(it) }.toSet(), natural.map { it.sourceId }.toSet())
        assertEquals((1..100).map { "BIB-%03d".format(it) }.toSet(), commercial.map { it.sourceId }.toSet())
        assertEquals(232, (legacy + natural + commercial).map { it.id }.distinct().size)
        assertTrue(legacy.all { it.sourceFile == LEGACY && it.sourceId == null && !it.isExperimental })
    }

    @Test
    fun realSourceAssetsRetainTheExactAttachmentSha256() {
        // Digests come from the two supplied Downloads files, not reserialized JSON.
        val expected = mapOf(
            NATURAL to "4f719f36fce442075060a5095e32599b14cdbad03f4d1b403da657638ada300f",
            COMMERCIAL to "d80fc22655ffaa3621828f80e7198731907b9b28f33a1145e25aaf32932d1a48"
        )
        expected.forEach { (file, hash) ->
            val actual = MessageDigest.getInstance("SHA-256").digest(asset(file).readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            assertEquals(file, hash, actual)
        }
    }

    @Test
    fun realStudiesPreserveEverySourceWeightMaterialFamilyAndProvenance() {
        listOf(NATURAL, COMMERCIAL).forEach { file ->
            val natural = file == NATURAL
            val recipes = JsonParser.parseString(asset(file).readText(Charsets.UTF_8))
                .asJsonObject.getAsJsonArray("recipes")
            val templates = parseAsset(file).associateBy { it.sourceId }
            recipes.forEach { element ->
                val source = element.asJsonObject
                val template = templates.getValue(source.get("id").asString)
                val items = source.getAsJsonArray("ingredients")
                val reference = source.get("reference_batch_concentrate_grams").asDouble
                val links = source.getAsJsonArray(if (natural) "source_urls" else "sources").map { it.asString }
                assertEquals(source.get("formula_name").asString, template.name)
                assertEquals(source.get("family").asString, template.familyLabel)
                assertEquals(file, template.sourceFile)
                assertEquals(links, template.sourceUrls)
                assertTrue(template.isExperimental)
                assertTrue(template.description.contains("IFRA"))
                assertEquals(100.0, template.concentrationPct, 0.0)
                assertEquals(if (natural) 7 else 3, template.ingredients.size)
                assertEquals(reference, template.ingredients.sumOf { it.amount }, 1e-9)
                assertEquals(100.0, items.sumOf { it.asJsonObject.get("weight_percent_in_concentrate").asDouble }, 1e-9)
                template.ingredients.forEachIndexed { index, item ->
                    val raw = items[index].asJsonObject
                    val percent = raw.get("weight_percent_in_concentrate").asDouble
                    val sourceGrams = if (natural) raw.get("grams_for_10g_concentrate").asDouble
                        else source.getAsJsonArray("formulation_weights_grams")[index].asDouble
                    assertEquals(raw.get("material").asString, item.name)
                    assertEquals(reference * percent / 100.0, item.amount, 1e-9)
                    assertEquals(sourceGrams, item.amount, 1e-9)
                    assertEquals("g", item.unit)
                    assertTrue(item.amount.isFinite() && item.amount > 0.0)
                    assertEquals(if (natural) IngredientType.NATURAL else IngredientType.SYNTHETIC, item.type)
                    if (natural) {
                        assertNull(item.brand)
                        assertFalse(item.requiresSelection)
                    } else {
                        assertEquals(raw.get("brand").asString, item.brand)
                        val code = raw.get("catalog_code").takeUnless { it.isJsonNull }?.asString
                        assertEquals(code, item.catalogCode)
                        assertEquals(code == null, item.requiresSelection)
                    }
                }
            }
        }
        assertEquals(listOf(2.2, 1.5, 1.3, 1.2, 1.2, 1.4, 1.2), parseAsset(NATURAL).first().ingredients.map { it.amount })
        assertEquals(listOf(5.5, 2.5, 2.0), parseAsset(COMMERCIAL).first().ingredients.map { it.amount })
    }

    @Test
    fun mutationFixturesRejectWrongDeclaredTotalAndIngredientSum() {
        val declared = oneRecipe()
        recipe(declared).addProperty("concentrate_total_percent", 99)
        assertRejected(declared)
        val ingredients = oneRecipe()
        firstIngredient(ingredients).addProperty("weight_percent_in_concentrate", 23)
        assertRejected(ingredients)
    }

    @Test
    fun mutationFixturesRejectNonpositiveNonfiniteAndWronglyTypedPercentages() {
        val invalid = listOf<JsonElement>(
            JsonPrimitive(0), JsonPrimitive(-1), JsonPrimitive(101),
            JsonParser.parseString("1e309"), JsonPrimitive("22"), JsonPrimitive(true), JsonNull.INSTANCE
        )
        invalid.forEach { value ->
            val document = oneRecipe()
            firstIngredient(document).add("weight_percent_in_concentrate", value)
            assertRejected(document)
        }
    }

    @Test
    fun mutationFixturesRejectInvalidReferenceBatchMass() {
        listOf<JsonElement>(
            JsonPrimitive(0), JsonPrimitive(-10), JsonPrimitive(100_001),
            JsonParser.parseString("1e309"), JsonPrimitive("10"), JsonNull.INSTANCE
        ).forEach { value ->
            val document = oneRecipe()
            recipe(document).add("reference_batch_concentrate_grams", value)
            assertRejected(document)
        }
    }

    @Test
    fun mutationFixturesRejectInconsistentNaturalReferenceGrams() {
        val document = oneRecipe()
        firstIngredient(document).addProperty("grams_for_10g_concentrate", 2.3)
        assertRejected(document)
    }

    @Test
    fun mutationFixturesRejectInconsistentCommercialGramsAndArrayLength() {
        val wrongWeight = oneRecipe(COMMERCIAL)
        recipe(wrongWeight).getAsJsonArray("formulation_weights_grams").set(0, JsonPrimitive(5.6))
        assertRejected(wrongWeight, COMMERCIAL)
        val wrongLength = oneRecipe(COMMERCIAL)
        recipe(wrongLength).getAsJsonArray("formulation_weights_grams").remove(2)
        assertRejected(wrongLength, COMMERCIAL)
        val wrongBatch = oneRecipe(COMMERCIAL)
        recipe(wrongBatch).addProperty("reference_batch_concentrate_grams", 20)
        assertRejected(wrongBatch, COMMERCIAL)
    }

    @Test
    fun mutationFixturesRejectNumericStringsInNaturalRedundantGrams() {
        val document = oneRecipe()
        firstIngredient(document).addProperty("grams_for_10g_concentrate", "2.2")
        assertRejected(document)
    }

    @Test
    fun mutationFixturesRejectNumericStringsInCommercialRedundantGrams() {
        val document = oneRecipe(COMMERCIAL)
        recipe(document).getAsJsonArray("formulation_weights_grams").set(0, JsonPrimitive("5.5"))
        assertRejected(document, COMMERCIAL)
    }

    @Test
    fun mutationFixturesRejectDuplicateSourceAndLegacyIds() {
        val document = oneRecipe()
        document.getAsJsonArray("recipes").add(recipe(document).deepCopy())
        document.getAsJsonObject("metadata").addProperty("records", 2)
        assertRejected(document)
        val legacy = legacyFixture()
        legacy.add(legacy[0].deepCopy())
        assertRejected(legacy.toString(), LEGACY)
    }

    @Test
    fun mutationFixturesRejectMalformedSourceIds() {
        listOf("NAT-000", "NAT-1", "NAT-1000", "BIB-001", "NAT-ABC").forEach { id ->
            val document = oneRecipe()
            recipe(document).addProperty("id", id)
            assertRejected(document)
        }
    }

    @Test
    fun mutationFixturesRejectRecordCountMismatchAndWrongMetadataTypes() {
        listOf<JsonElement>(JsonPrimitive(2), JsonPrimitive("1"), JsonPrimitive(true)).forEach { value ->
            val document = oneRecipe()
            document.getAsJsonObject("metadata").add("records", value)
            assertRejected(document)
        }
        listOf<JsonElement>(JsonPrimitive("unknown"), JsonPrimitive(1)).forEach { value ->
            val document = oneRecipe()
            document.getAsJsonObject("metadata").add("dataset_type", value)
            assertRejected(document)
        }
    }

    @Test
    fun mutationFixturesRejectMissingOrWronglyTypedRequiredText() {
        listOf("id", "formula_name", "family", "safety_status").forEach { field ->
            val document = oneRecipe()
            recipe(document).add(field, JsonPrimitive(123))
            assertRejected(document)
        }
        listOf<JsonElement>(JsonPrimitive(" "), JsonPrimitive(123), JsonNull.INSTANCE).forEach { value ->
            val document = oneRecipe()
            firstIngredient(document).add("material", value)
            assertRejected(document)
        }
        val safety = oneRecipe()
        recipe(safety).addProperty("safety_status", "SKIN_READY")
        assertRejected(safety)
    }

    @Test
    fun mutationFixturesRejectWrongBrandCodeAndVerificationTypes() {
        listOf("brand", "catalog_code", "catalog_verification").forEach { field ->
            val document = oneRecipe(COMMERCIAL)
            firstIngredient(document).add(field, JsonPrimitive(123))
            assertRejected(document, COMMERCIAL)
        }
        val noBrand = oneRecipe(COMMERCIAL)
        firstIngredient(noBrand).addProperty("brand", " ")
        assertRejected(noBrand, COMMERCIAL)
        val unrecognized = oneRecipe(COMMERCIAL)
        firstIngredient(unrecognized).addProperty("catalog_verification", "verified_by_ai")
        assertRejected(unrecognized, COMMERCIAL)
    }

    @Test
    fun mutationFixturesRejectUnsafeOrMalformedSourceUrlsInBothSchemas() {
        val invalid = listOf(
            "http://example.org/source", "javascript:alert(1)", "data:text/html,<script>alert(1)</script>",
            "file:///tmp/source", "content://source/1", "intent://source", "https:///missing-host",
            "https://user:password@example.org/source", "https://example.org:8443/source",
            "https://exa mple.org/source", "https://example.org/\nsource"
        )
        listOf(NATURAL, COMMERCIAL).forEach { file ->
            invalid.forEach { url ->
                val document = oneRecipe(file)
                recipe(document).add(if (file == NATURAL) "source_urls" else "sources", JsonArray().apply { add(url) })
                assertRejected(document, file)
            }
        }
    }

    @Test
    fun mutationFixturesRejectEmptyOrWronglyTypedSourceCollections() {
        listOf<JsonElement>(JsonArray(), JsonPrimitive("https://example.org/source"), JsonNull.INSTANCE).forEach { value ->
            val document = oneRecipe()
            recipe(document).add("source_urls", value)
            assertRejected(document)
        }
    }

    @Test
    fun malformedLastRecordRejectsTheWholeFileAndOtherSourceStillParses() {
        val document = JsonParser.parseString(asset(NATURAL).readText(Charsets.UTF_8)).asJsonObject
        document.getAsJsonArray("recipes")[99].asJsonObject.addProperty("concentrate_total_percent", 99)
        assertRejected(document)
        assertEquals(100, parseAsset(COMMERCIAL).size)
    }

    @Test
    fun mutationFixturesRejectEmptyIngredientsAndMalformedJsonShapes() {
        val empty = oneRecipe()
        recipe(empty).add("ingredients", JsonArray())
        assertRejected(empty)
        listOf("", "null", "42", "{}", "[]", "{\"metadata\":").forEach { assertRejected(it) }
    }

    @Test
    fun legacyFixtureConvertsMilligramsToGramsWithoutDensityAssumptions() {
        val legacy = legacyFixture()
        legacy[0].asJsonObject.getAsJsonArray("ingredients")[0].asJsonObject.apply {
            addProperty("amount", 1250)
            addProperty("unit", "mg")
        }
        val item = FormulaTemplateParser.parse(legacy.toString(), LEGACY).single().ingredients.first()
        assertEquals(1.25, item.amount, 0.0)
        assertEquals("g", item.unit)
    }

    @Test
    fun legacyMutationFixturesRejectFractionalIdsUnsupportedTypesAndVolumeUnits() {
        val fractional = legacyFixture()
        fractional[0].asJsonObject.addProperty("id", 1.5)
        assertRejected(fractional.toString(), LEGACY)
        val wrongType = legacyFixture()
        wrongType[0].asJsonObject.getAsJsonArray("ingredients")[0].asJsonObject.addProperty("type", "UNKNOWN")
        assertRejected(wrongType.toString(), LEGACY)
        val volume = legacyFixture()
        volume[0].asJsonObject.getAsJsonArray("ingredients")[0].asJsonObject.addProperty("unit", "ml")
        assertRejected(volume.toString(), LEGACY)
    }

    private fun parseAsset(file: String) = FormulaTemplateParser.parse(asset(file).readText(Charsets.UTF_8), file)

    private fun asset(file: String): File = listOf(File("src/main/assets/data"), File("app/src/main/assets/data"))
        .firstOrNull(File::isDirectory)?.resolve(file)?.takeIf(File::isFile)
        ?: error("Real bundled asset required: $file")

    // Mutation fixtures use one real recipe; they do not stand in for the full-asset checks above.
    private fun oneRecipe(file: String = NATURAL): JsonObject {
        val document = JsonParser.parseString(asset(file).readText(Charsets.UTF_8)).asJsonObject
        val first = document.getAsJsonArray("recipes")[0].deepCopy()
        document.add("recipes", JsonArray().apply { add(first) })
        document.getAsJsonObject("metadata").addProperty("records", 1)
        return document
    }

    private fun recipe(document: JsonObject): JsonObject = document.getAsJsonArray("recipes")[0].asJsonObject

    private fun firstIngredient(document: JsonObject): JsonObject = recipe(document).getAsJsonArray("ingredients")[0].asJsonObject

    private fun legacyFixture(): JsonArray = JsonArray().apply {
        add(JsonParser.parseString(asset(LEGACY).readText(Charsets.UTF_8)).asJsonArray[0].deepCopy())
    }

    private fun assertRejected(document: JsonObject, file: String = NATURAL) = assertRejected(document.toString(), file)

    private fun assertRejected(json: String, file: String = NATURAL) {
        assertNotNull("Malformed $file must fail without a partial result", runCatching {
            FormulaTemplateParser.parse(json, file)
        }.exceptionOrNull())
    }

    private companion object {
        const val LEGACY = "formula_templates.json"
        const val NATURAL = "100_resep_parfum_natural_eksperimental_2026.json"
        const val COMMERCIAL = "100_racikan_bibit_luzi_macbrame_parfarome_2026.json"
    }
}
