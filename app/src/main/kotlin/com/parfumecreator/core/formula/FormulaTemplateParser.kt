package com.parfumecreator.core.formula

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.parfumecreator.domain.model.*
import java.net.URI
import java.util.Locale
import kotlin.math.abs

/** Pure, all-or-nothing parser for bundled legacy and supplied concentrate datasets. */
object FormulaTemplateParser {
    val assetFiles = listOf(
        "formula_templates.json",
        "100_resep_parfum_natural_eksperimental_2026.json",
        "100_racikan_bibit_luzi_macbrame_parfarome_2026.json"
    )

    fun parse(json: String, sourceFile: String): List<FormulaTemplate> {
        val root = JsonParser.parseString(json)
        val templates = if (root.isJsonArray) {
            root.asJsonArray.map { parseLegacy(it.asJsonObject, sourceFile) }
        } else {
            val document = root.asJsonObject
            val metadata = document.getAsJsonObject("metadata")
            val type = when (metadata.text("dataset_type")) {
                "natural" -> IngredientType.NATURAL
                "commercial_fragrance_concentrate" -> IngredientType.SYNTHETIC
                else -> error("Jenis dataset resep tidak didukung.")
            }
            val recipes = document.getAsJsonArray("recipes")
            require(metadata.number("records") == recipes.size().toDouble()) { "Jumlah record tidak sesuai metadata." }
            recipes.map { parseStudy(it.asJsonObject, type, sourceFile) }
        }
        require(templates.isNotEmpty() && templates.size <= 1_000) { "Jumlah template tidak valid." }
        require(templates.map { it.id }.distinct().size == templates.size) { "ID template berulang." }
        return templates
    }

    private fun parseStudy(recipe: JsonObject, type: IngredientType, file: String): FormulaTemplate {
        val sourceId = recipe.text("id")
        val prefix = if (type == IngredientType.NATURAL) "NAT" else "BIB"
        require(Regex("$prefix-[0-9]{3}").matches(sourceId)) { "ID resep tidak valid: $sourceId" }
        val number = sourceId.substringAfter('-').toInt()
        require(number in 1..999) { "Nomor resep tidak valid." }
        val referenceGrams = recipe.number("reference_batch_concentrate_grams")
        require(referenceGrams in 0.000001..100_000.0) { "Batch referensi tidak valid." }
        require(abs(recipe.number("concentrate_total_percent") - 100.0) < 0.000001) { "Total konsentrat harus 100%." }
        val ingredients = recipe.getAsJsonArray("ingredients")
        require(ingredients.size() in 1..100) { "Jumlah bahan resep tidak valid." }
        val percentages = ingredients.map { it.asJsonObject.number("weight_percent_in_concentrate") }
        require(percentages.all { it > 0.0 && it <= 100.0 } && abs(percentages.sum() - 100.0) < 0.000001) {
            "Persentase bahan $sourceId tidak berjumlah 100%."
        }
        val redundantGrams = if (type == IngredientType.SYNTHETIC) {
            requireNotNull(recipe.getAsJsonArray("formulation_weights_grams")) { "Gram referensi bibit wajib ada." }
        } else null
        require(redundantGrams == null || redundantGrams.size() == ingredients.size()) { "Jumlah data gram tidak cocok." }
        val items = ingredients.mapIndexed { index, element ->
            val item = element.asJsonObject
            val grams = referenceGrams * percentages[index] / 100.0
            require(grams.isFinite() && grams > 0.0) { "Gram bahan $sourceId di luar batas perhitungan." }
            if (type == IngredientType.NATURAL) {
                require(abs(item.number("grams_for_10g_concentrate") - percentages[index] / 10.0) < 0.000001) {
                    "Gram 10 g $sourceId tidak konsisten."
                }
            }
            redundantGrams?.get(index)?.let {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber) { "Gram referensi harus berupa angka." }
                require(abs(it.asDouble - grams) < 0.000001) { "Gram referensi $sourceId tidak konsisten." }
            }
            val verification = item.optionalText("catalog_verification")
            if (type == IngredientType.SYNTHETIC) {
                require(verification in setOf("public_catalog_verified", "aroma_requirement_only_NO_PRODUCT_SKU_VERIFIED")) {
                    "Status katalog $sourceId tidak dikenali."
                }
                require(!item.optionalText("brand").isNullOrBlank()) { "Brand bibit wajib ada." }
            }
            FormulaTemplateItem(
                name = item.text("material"), amount = grams, type = type,
                brand = item.optionalText("brand"), catalogCode = item.optionalText("catalog_code"),
                requiresSelection = verification == "aroma_requirement_only_NO_PRODUCT_SKU_VERIFIED"
            )
        }
        val family = recipe.text("family")
        val sourceLinks = recipe.getAsJsonArray(if (type == IngredientType.NATURAL) "source_urls" else "sources")
            .map { it.asString.also(::validateUrl) }.distinct()
        require(sourceLinks.isNotEmpty()) { "Sumber resep wajib ada." }
        require(recipe.text("safety_status") == "NOT_IFRA_EVALUATED_NOT_SKIN_READY") { "Status keselamatan tidak dikenali." }
        val selectionNote = if (type == IngredientType.SYNTHETIC) {
            "Accord tanpa SKU harus dipilih dari produk katalog nyata; pilihan merupakan adaptasi."
        } else "Grade bahan harus sesuai; penggantian merupakan adaptasi."
        return FormulaTemplate(
            id = (if (type == IngredientType.NATURAL) 1_000 else 2_000) + number,
            name = recipe.text("formula_name"), category = type.name,
            fragranceFamily = FragranceFamily.fromString(family.substringBefore(' ')),
            concentrationPct = 100.0, concentrationType = ConcentrationType.PARFUM_EXTRAIT,
            description = "Studi konsentrat eksperimental, bukan resep resmi supplier/perfumer. " +
                "Belum diuji sensorik/ketahanan, belum dievaluasi IFRA, tidak siap dipakai di kulit. " +
                "Dilusi tidak ditentukan. $selectionNote",
            ingredients = items, sourceId = sourceId, sourceFile = file,
            sourceUrls = sourceLinks, familyLabel = family, isExperimental = true
        )
    }

    private fun parseLegacy(recipe: JsonObject, file: String): FormulaTemplate {
        val id = recipe.number("id").toInt()
        require(id in 1..999 && recipe.number("id") == id.toDouble()) { "ID template lama tidak valid." }
        val concentration = recipe.number("concentrationPct")
        require(concentration > 0.0 && concentration <= 100.0) { "Konsentrasi template tidak valid." }
        val category = IngredientType.valueOf(recipe.text("category").uppercase(Locale.ROOT)).name
        val items = recipe.getAsJsonArray("ingredients").map { element ->
            val item = element.asJsonObject
            val amount = item.number("amount")
            require(amount > 0.0 && amount <= 100_000.0) { "Jumlah bahan template tidak valid." }
            val grams = when (item.optionalText("unit")?.lowercase(Locale.ROOT) ?: "g") {
                "g", "gram" -> amount
                "mg", "milligram" -> amount / 1_000.0
                else -> error("Template wajib menggunakan g/mg; mL tanpa density tidak diterima.")
            }
            require(grams.isFinite() && grams > 0.0) { "Gram bahan template di luar batas perhitungan." }
            FormulaTemplateItem(
                name = item.text("name"), amount = grams,
                type = IngredientType.valueOf(item.text("type").uppercase(Locale.ROOT)),
                noteType = NoteType.fromString(item.optionalText("noteType")),
                fragranceFamily = FragranceFamily.fromString(item.optionalText("fragranceFamily")),
                brand = item.optionalText("brand")
            )
        }
        require(items.isNotEmpty() && items.sumOf { it.amount }.isFinite()) { "Bahan template kosong/tidak valid." }
        return FormulaTemplate(
            id, recipe.text("name"), category, FragranceFamily.fromString(recipe.optionalText("fragranceFamily")),
            concentration, ConcentrationType.fromConcentration(concentration), recipe.optionalText("description").orEmpty(),
            items, sourceFile = file
        )
    }

    private fun JsonObject.text(key: String): String = optionalText(key)
        ?.takeIf { it.isNotBlank() } ?: error("Teks $key wajib ada.")

    private fun JsonObject.optionalText(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.let {
        require(it.isJsonPrimitive && it.asJsonPrimitive.isString) { "$key harus berupa teks." }
        it.asString.trim()
    }

    private fun JsonObject.number(key: String): Double {
        val value = get(key) ?: error("Angka $key wajib ada.")
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "$key harus berupa angka." }
        return value.asDouble.also { require(it.isFinite()) { "$key harus hingga." } }
    }

    private fun validateUrl(value: String) {
        val uri = URI(value)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.port == -1) {
            "Tautan sumber harus HTTPS tanpa credential/port khusus."
        }
    }
}
