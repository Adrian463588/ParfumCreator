package com.parfumecreator.core.llm

import com.parfumecreator.data.local.database.IngredientEntity
import com.parfumecreator.domain.engine.FormulaCalculationEngine
import com.parfumecreator.domain.model.ConcentrationType
import com.parfumecreator.domain.model.FormulaIngredientInput
import com.parfumecreator.domain.model.FragranceFamily
import com.parfumecreator.domain.model.Ingredient
import com.parfumecreator.domain.model.IngredientType
import com.parfumecreator.domain.model.MeasurementUnit
import com.parfumecreator.domain.model.NoteType
import com.parfumecreator.domain.model.RecommendedFormula
import com.parfumecreator.domain.model.RecommendedIngredient
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.round

/**
 * Plans catalog-only formulas. Type, brand and named materials constrain selection
 * before ranking; proportions are calculation heuristics, not safety clearance.
 */
@Singleton
class GroundedFormulaPlanner @Inject constructor() {
    data class Plan(
        val formula: RecommendedFormula,
        val totalWeightG: Double,
        val concentrationPct: Double,
        val targetFamily: FragranceFamily,
        val usedDefaultBatch: Boolean,
        val selectedCatalogSize: Int,
        val coverageMessage: String
    )

    private data class Material(
        val entity: IngredientEntity,
        val ingredient: Ingredient,
        val aliases: List<Set<String>>,
        val searchTokens: Set<String>
    ) {
        val identityTokens: Set<String> = aliases.flatten().toSet()
    }

    private data class Constraints(
        val allowedTypes: Set<String>,
        val typeSpecified: Boolean,
        val brands: Set<String>,
        val requiredTokens: Set<String>,
        val queryTokens: Set<String>,
        val family: FragranceFamily?
    )

    private data class BatchSpec(val amount: Double, val unit: String, val explicit: Boolean)

    /** Returns null when the request cannot be satisfied without substitutions. */
    fun create(prompt: String, catalog: List<IngredientEntity>): Plan? =
        createValidated(prompt, catalog).getOrNull()

    /**
     * Same planner as [create], with an actionable validation reason on failure.
     * A requested dilution never becomes a concentrate when solvent is missing.
     */
    fun createValidated(prompt: String, catalog: List<IngredientEntity>): Result<Plan> {
        val materials = catalogMaterials(catalog)
        val request = constraints(prompt, materials).getOrElse { return failure(it.message.orEmpty()) }
        val eligible = materials.filter { matchesConstraints(it, request) }
        val named = selectNamed(request.requiredTokens, eligible).getOrElse { return failure(it.message.orEmpty()) }
        val namedAromas = named.filterNot { it.ingredient.isSolventOrDiluent }
        val selected = if (namedAromas.isNotEmpty()) {
            namedAromas
        } else {
            selectRelevant(eligible.filterNot { it.ingredient.isSolventOrDiluent }, request)
        }
        if (selected.isEmpty()) return failure("Tidak ada bahan aroma katalog yang memenuhi permintaan. Sebutkan bahan, jenis, brand atau family parfum.")
        if (!request.brands.all { brand -> selected.any { canonicalBrand(it.ingredient.brand) == brand } }) {
            return failure("Bahan aroma tidak tersedia untuk setiap brand pilihan; sebutkan produk atau brand lain.")
        }

        val concentration = parseConcentration(prompt)
            ?: return failure("Konsentrasi tidak valid atau ambigu. Gunakan satu nilai lebih dari 0 sampai 100% w/w.")
        val batch = parseBatchSpec(prompt, materials)
            ?: return failure("Batch atau unit tidak valid/ambigu. Gunakan satu total positif dalam g, mg, kg, mL atau L tanpa pemisah ribuan ambigu.")
        val namedSolvents = named.filter { it.ingredient.isSolventOrDiluent }
        if (namedSolvents.size > 1) return failure("Beberapa pelarut disebutkan; tentukan satu pelarut terlebih dahulu.")
        if (concentration == 100.0 && namedSolvents.isNotEmpty()) {
            return failure("Konsentrat 100% tidak dapat mengandung pelarut. Tentukan konsentrasi di bawah 100%.")
        }
        val diluent = if (concentration < 100.0) {
            namedSolvents.singleOrNull() ?: materials
                .filter { it.ingredient.isSolventOrDiluent }
                .sortedWith(compareByDescending<Material> { solventPriority(it) }.thenBy { it.entity.id })
                .firstOrNull()
                ?: return failure("Pelarut katalog tidak tersedia untuk konsentrasi ${format4(concentration)}%. Tambahkan pelarut atau minta konsentrat 100% secara eksplisit.")
        } else {
            null
        }
        val fractions = aromaFractions(selected)
        val targetWeight = resolveBatchWeight(batch, selected, fractions, diluent, concentration)
            ?: return failure("Batch mL/L memerlukan density positif dan finite setiap bahan; target juga harus di bawah $MAX_BATCH_G g.")
        val aromaWeight = targetWeight * concentration / 100.0
        val amounts = fractions.map { round4(aromaWeight * it) }.toMutableList()
        amounts[amounts.lastIndex] = round4(aromaWeight - amounts.dropLast(1).sum())
        val items = selected.mapIndexed { index, material -> material.toRecommended(amounts[index]) }.toMutableList()
        if (diluent != null) items.add(diluent.toRecommended(round4(targetWeight - amounts.sum()), "DILUENT"))
        if (items.any { !it.amount.isFinite() || it.amount <= 0.0 } ||
            abs(items.sumOf { it.amount } - targetWeight) > WEIGHT_TOLERANCE_G
        ) return failure("Jumlah terlalu kecil untuk presisi 0,0001 g atau total tidak memenuhi target. Perbesar batch.")

        val byId = materials.associateBy { it.entity.id }
        val calculation = FormulaCalculationEngine.calculateFormula(items.map { item ->
            FormulaIngredientInput(
                byId.getValue(requireNotNull(item.resolvedIngredientId)).ingredient,
                item.amount, MeasurementUnit.GRAM
            )
        })
        if (!calculation.totalWeightG.isFinite() || !calculation.spl.isFinite() ||
            !calculation.concentrationPct.isFinite() ||
            abs(calculation.totalWeightG - targetWeight) > WEIGHT_TOLERANCE_G ||
            abs(calculation.concentrationPct - concentration) > CONCENTRATION_TOLERANCE_PCT
        ) return failure("Hasil perhitungan tidak memenuhi total atau konsentrasi yang diminta.")

        val family = request.family ?: calculation.dominantSubFamily
        val familyCount = selected.count { it.ingredient.fragranceFamily == family }
        val solventMessage = if (diluent == null) {
            "Konsentrat aroma 100%, belum menjadi batch siap pakai."
        } else {
            "Pelarut berasal dari katalog."
        }
        val coverage = "$familyCount/${selected.size} bahan aroma memiliki family ${family.displayName} di katalog. $solventMessage"
        val concentrationLabel = if (diluent == null) {
            "100.00% Konsentrat aroma (tanpa pelarut)"
        } else {
            "%.2f%% %s".format(
                Locale.US, calculation.concentrationPct,
                ConcentrationType.fromConcentration(calculation.concentrationPct).displayName
            )
        }
        val formula = RecommendedFormula(
            name = "Racikan Baru — ${family.displayName}",
            concentration = concentrationLabel,
            totalWeightG = calculation.totalWeightG,
            predictedSpl = calculation.spl,
            predictedFamily = calculation.dominantSubFamily.displayName,
            description = "Formula baru dari katalog. Batch aktual ${format4(calculation.totalWeightG)} g. " +
                "$coverage Proporsi dan SPL adalah heuristik, bukan verifikasi keamanan atau performa.",
            ingredients = items
        )
        return Result.success(Plan(
            formula, calculation.totalWeightG, calculation.concentrationPct, family,
            !batch.explicit, items.size, coverage
        ))
    }

    /** Returns products supported by request evidence, never a positive default score. */
    fun recommend(prompt: String, catalog: List<IngredientEntity>, limit: Int = 6): List<IngredientEntity> {
        if (limit <= 0) return emptyList()
        val materials = catalogMaterials(catalog)
        val request = constraints(prompt, materials).getOrNull() ?: return emptyList()
        val eligible = materials.filter { matchesConstraints(it, request) }
        if (request.requiredTokens.isNotEmpty()) {
            val named = selectNamed(request.requiredTokens, eligible).getOrNull() ?: return emptyList()
            val aromas = named.filterNot { it.ingredient.isSolventOrDiluent }
            if (!request.brands.all { brand -> aromas.any { canonicalBrand(it.ingredient.brand) == brand } }) return emptyList()
            return aromas.map { it.entity }.take(limit)
        }
        return eligible.asSequence().filterNot { it.ingredient.isSolventOrDiluent }
            .map { it to relevance(it, request) }.filter { it.second > 0.0 }
            .sortedWith(compareByDescending<Pair<Material, Double>> { it.second }.thenBy { it.first.entity.id })
            .map { it.first.entity }.take(limit).toList()
    }

    private fun catalogMaterials(catalog: List<IngredientEntity>): List<Material> = catalog
        .filter { it.id > 0L && it.type.uppercase(Locale.ROOT) in TYPE_TERMS.keys }
        .distinctBy { it.id }
        .mapNotNull { entity ->
            val ingredient = entity.toDomain()
            val brandTokens = PerfumeTextNormalizer.rawTokens(canonicalBrand(ingredient.brand))
            val aliases = listOfNotNull(ingredient.name, ingredient.inspiredBy, ingredient.sourcePlant)
                .map {
                    val tokens = identityTokens(it) - brandTokens
                    // Alcohol "absolute" is not the botanical extraction identity.
                    if (ingredient.isSolventOrDiluent) tokens - setOf("absolute", "extract", "premium") else tokens
                }.filter { it.isNotEmpty() }
            if (ingredient.name.isBlank() || (aliases.isEmpty() && !ingredient.isSolventOrDiluent)) return@mapNotNull null
            Material(
                entity, ingredient, aliases,
                PerfumeTextNormalizer.tokens(listOfNotNull(ingredient.name, ingredient.description, ingredient.inspiredBy).joinToString(" ")) - REQUEST_WORDS
            )
        }

    private fun constraints(prompt: String, materials: List<Material>): Result<Constraints> {
        val input = normalizeSigns(prompt)
        val excluded = TYPE_TERMS.filterValues { terms ->
            terms.any { term -> NEGATIONS.any { containsPhrase(input, "$it $term") } }
        }.keys
        var positiveInput = input
        TYPE_TERMS.values.flatten().forEach { term ->
            NEGATIONS.forEach { negation -> positiveInput = phrasePattern("$negation $term").replace(positiveInput, " ") }
        }
        val remainingNegations = phrasePattern("tanpa pelarut").replace(
            phrasePattern("without solvent").replace(positiveInput, " "), " "
        )
        if (Regex("(?i)(?<![\\p{L}\\p{N}])(?:tanpa|bukan|without|no)\\s+[\\p{L}]").containsMatchIn(remainingNegations)) {
            return failure("Pengecualian bahan perlu diperjelas; sebutkan daftar bahan yang boleh dipakai agar tidak terjadi substitusi.")
        }
        val specified = TYPE_TERMS.filterValues { terms -> terms.any { containsPhrase(positiveInput, it) } }.keys
        val allowed = specified.ifEmpty { TYPE_TERMS.keys } - excluded
        if (allowed.isEmpty()) return failure("Batasan natural/synthetic saling bertentangan; pilih jenis bahan aroma.")
        val brands = materials.mapNotNull { it.ingredient.brand }.map(::canonicalBrand).toSet() +
            setOf("luzi", "parfarome", "macrabame")
        val brandInput = input.replace(phrasePattern("macbrame"), "macrabame")
        val requestedBrands = brands.filter { it.isNotBlank() && containsPhrase(brandInput, it) }.toSet()
        val explicitBrand = Regex("(?i)(?:brand|merk|merek)\\s+([\\p{L}\\p{N}]+)").find(brandInput)?.groupValues?.get(1)
        if (explicitBrand != null && brands.none { containsPhrase(it, explicitBrand) }) {
            return failure("Brand '$explicitBrand' tidak tersedia di katalog.")
        }
        val withoutNumbers = withoutQuantities(percentPattern.replace(positiveInput, " "), materials)
        val tokens = identityTokens(withoutNumbers) - requestedBrands.flatMap { PerfumeTextNormalizer.rawTokens(it) }.toSet()
        val required = (tokens - FAMILY_WORDS).toMutableSet()
        // An exact catalog alias may itself be a family name, e.g. a bibit named Amber.
        materials.filterNot { it.ingredient.isSolventOrDiluent }.forEach { material ->
            material.aliases.filter { tokens.containsAll(it) }.forEach { required.addAll(it) }
        }
        val queryTokens = PerfumeTextNormalizer.tokens(withoutNumbers) - REQUEST_WORDS
        val family = PerfumeTextNormalizer.familySignals().entries
            .map { (family, signals) -> family to queryTokens.count { it in signals } }
            .maxByOrNull { it.second }?.takeIf { it.second > 0 }?.first
        return Result.success(Constraints(allowed, specified.isNotEmpty() || excluded.isNotEmpty(), requestedBrands, required, queryTokens, family))
    }

    private fun matchesConstraints(material: Material, request: Constraints): Boolean =
        material.ingredient.isSolventOrDiluent ||
            (material.entity.type.uppercase(Locale.ROOT) in request.allowedTypes &&
                (request.brands.isEmpty() || canonicalBrand(material.ingredient.brand) in request.brands))

    private fun selectNamed(required: Set<String>, materials: List<Material>): Result<List<Material>> {
        val remaining = required.toMutableSet()
        val selected = mutableListOf<Material>()
        while (remaining.isNotEmpty()) {
            val candidates = materials.filter { candidate -> selected.none { it.entity.id == candidate.entity.id } }
                .map { it to (remaining intersect it.identityTokens) }.filter { it.second.isNotEmpty() }
                .sortedWith(
                    compareByDescending<Pair<Material, Set<String>>> { it.second.size }
                        .thenBy { (material, _) -> material.aliases.minOf { (it - required).size } }
                )
            val best = candidates.firstOrNull()
                ?: return failure("Bahan tidak tersedia dengan jenis/brand pilihan: ${remaining.joinToString()}. Tidak dilakukan substitusi.")
            val ambiguity = candidates.drop(1).firstOrNull {
                it.second == best.second && it.first.aliases.minOf { alias -> (alias - required).size } ==
                    best.first.aliases.minOf { alias -> (alias - required).size }
            }
            if (ambiguity != null) {
                return failure("Bahan ambigu: ${best.first.ingredient.name} (${best.first.ingredient.brand.orEmpty()}) atau ${ambiguity.first.ingredient.name} (${ambiguity.first.ingredient.brand.orEmpty()}). Sebutkan nama/brand lengkap.")
            }
            selected.add(best.first)
            remaining.removeAll(best.second)
        }
        return Result.success(selected)
    }

    private fun selectRelevant(materials: List<Material>, request: Constraints): List<Material> {
        val ranked = materials.map { it to relevance(it, request) }.filter { it.second > 0.0 }
            .sortedWith(
                compareByDescending<Pair<Material, Double>> { it.second }.thenBy { it.first.entity.id }
            )
            .map { it.first }
        val selected = mutableListOf<Material>()
        request.brands.forEach { brand ->
            ranked.firstOrNull { canonicalBrand(it.ingredient.brand) == brand }?.let { selected.add(it) }
        }
        if (materials.isNotEmpty() && materials.all { it.ingredient.type == IngredientType.SYNTHETIC }) {
            return (selected + ranked).distinctBy { it.entity.id }.take(maxOf(2, request.brands.size))
        }
        listOf(setOf(NoteType.TOP), setOf(NoteType.MIDDLE, NoteType.HEART), setOf(NoteType.BASE)).forEach { notes ->
            ranked.firstOrNull { it.ingredient.noteType in notes }?.let { selected.add(it) }
        }
        return (selected + ranked).distinctBy { it.entity.id }.take(4)
    }

    private fun relevance(material: Material, request: Constraints): Double {
        val overlap = request.queryTokens.count { it in material.searchTokens }.toDouble()
        val familyScore = when {
            request.family == null -> 0.0
            material.ingredient.fragranceFamily == request.family -> 3.0
            material.ingredient.fragranceFamily.toPrimaryWheel() == request.family.toPrimaryWheel() -> 1.0
            else -> 0.0
        }
        val constraintScore = if (request.family == null && (request.typeSpecified || request.brands.isNotEmpty())) 1.0 else 0.0
        return overlap + familyScore + constraintScore
    }

    private fun aromaFractions(materials: List<Material>): List<Double> {
        // Complete bibit oils need no invented top/heart/base coverage. Equal mass
        // is the starting heuristic when no ingredient proportions are supplied.
        val weights = if (materials.all { it.ingredient.type == IngredientType.SYNTHETIC }) {
            materials.map { 1.0 }
        } else {
            materials.map {
                when (it.ingredient.noteType) {
                    NoteType.TOP -> 0.25
                    NoteType.MIDDLE, NoteType.HEART -> 0.45
                    NoteType.BASE -> 0.30
                }
            }
        }
        val total = weights.sum()
        return weights.map { it / total }
    }

    private fun parseConcentration(prompt: String): Double? {
        val input = normalizeSigns(prompt)
        val matches = percentPattern.findAll(input).toList()
        val concentrate = listOf("konsentrat", "concentrate", "parfum murni", "tanpa pelarut", "without solvent")
            .any { containsPhrase(input, it) }
        if (invalidNumberPattern.containsMatchIn(input)) return null
        if (matches.isNotEmpty()) {
            if (matches.size != 1) return null
            val value = parseNumber(matches.single().groupValues[1]) ?: return null
            return value.takeIf { it > 0.0 && it <= 100.0 && (!concentrate || it == 100.0) }
        }
        if (Regex("(?i)(?:konsentrasi|concentration)\\s*[:=]?\\s*[+\\-\\d.,]").containsMatchIn(input)) return null
        if (concentrate) return 100.0
        return when {
            containsPhrase(input, "extrait") -> 20.0
            containsPhrase(input, "edt") || containsPhrase(input, "eau de toilette") -> 10.0
            containsPhrase(input, "edc") || containsPhrase(input, "cologne") || containsPhrase(input, "eau de cologne") -> 5.0
            else -> 15.0
        }
    }

    private fun parseBatchSpec(prompt: String, materials: List<Material>): BatchSpec? {
        val input = percentPattern.replace(normalizeSigns(prompt), " ")
        if (invalidNumberPattern.containsMatchIn(input)) return null
        val matches = quantityPattern.findAll(input).filterNot { isProductIdentity(it.value, materials) }.toList()
        if (matches.isEmpty()) return BatchSpec(DEFAULT_BATCH_G, "g", false)
        if (matches.size != 1) return null
        val match = matches.single()
        val amount = parseNumber(match.groupValues[1]) ?: return null
        val (unit, factor) = batchUnit(match.groupValues[2]) ?: return null
        val normalized = amount * factor
        return BatchSpec(normalized, unit, true).takeIf { normalized.isFinite() && normalized > 0.0 && normalized <= MAX_BATCH_G }
    }

    private fun withoutQuantities(input: String, materials: List<Material>): String = quantityPattern.replace(input) {
        if (isProductIdentity(it.value, materials)) it.value else " "
    }

    private fun isProductIdentity(text: String, materials: List<Material>): Boolean {
        val quantity = quantityPattern.matchEntire(text) ?: return false
        // Package sizes can remain in legacy aliases. A recognized unit always
        // denotes an amount; only nonunit names such as 212 VIP are preserved.
        if (batchUnit(quantity.groupValues[2]) != null) return false
        val tokens = identityTokens(text)
        return tokens.isNotEmpty() && materials.any { material -> material.aliases.any { it.containsAll(tokens) } }
    }

    private fun batchUnit(value: String): Pair<String, Double>? = when (value.lowercase(Locale.ROOT)) {
        "g", "gr", "gram", "grams" -> "g" to 1.0
        "mg", "milligram", "milligrams", "miligram" -> "g" to 0.001
        "kg", "kilogram", "kilograms" -> "g" to 1000.0
        "ml", "milliliter", "milliliters", "mililiter", "millilitre", "millilitres" -> "ml" to 1.0
        "l", "liter", "liters", "litre", "litres" -> "ml" to 1000.0
        else -> null
    }

    private fun resolveBatchWeight(
        batch: BatchSpec,
        selected: List<Material>,
        fractions: List<Double>,
        diluent: Material?,
        concentration: Double
    ): Double? {
        if (batch.unit == "g") return batch.amount
        // Sum component volumes using mass fractions; arithmetic mean density
        // gives a different final volume when component densities differ.
        val aromaVolumePerGram = selected.zip(fractions).sumOf { (material, fraction) ->
            val density = material.ingredient.densityGml
            if (!density.isFinite() || density <= 0.0) return null
            fraction / density
        }
        val fraction = concentration / 100.0
        val solventVolumePerGram = if (diluent != null) {
            val density = diluent.ingredient.densityGml
            if (!density.isFinite() || density <= 0.0) return null
            (1.0 - fraction) / density
        } else 0.0
        val weight = batch.amount / (fraction * aromaVolumePerGram + solventVolumePerGram)
        return weight.takeIf { it.isFinite() && it > 0.0 && it <= MAX_BATCH_G }
    }

    private fun Material.toRecommended(amount: Double, note: String = ingredient.noteType.name) =
        RecommendedIngredient(ingredient.name, amount, "g", note, entity.id)

    private fun solventPriority(material: Material): Int = when {
        "ethanol" in material.identityTokens -> 3
        "dpg" in material.identityTokens -> 2
        else -> 1
    }

    private fun identityTokens(text: String): Set<String> {
        val tokens = (PerfumeTextNormalizer.tokens(percentPattern.replace(text, " ")) - REQUEST_WORDS).toMutableSet()
        if (tokens.removeAll(setOf("ethanol", "alcohol", "alkohol"))) tokens.add("ethanol")
        if (tokens.remove("macbrame")) tokens.add("macrabame")
        if (tokens.remove("ekstrak")) tokens.add("extract")
        if (tokens.containsAll(setOf("dipropylene", "glycol"))) {
            tokens.removeAll(setOf("dipropylene", "glycol"))
            tokens.add("dpg")
        }
        return tokens
    }

    private fun canonicalBrand(brand: String?): String =
        brand.orEmpty().trim().lowercase(Locale.ROOT).let { if (it == "macbrame") "macrabame" else it }
    private fun normalizeSigns(text: String): String = text.replace('−', '-').replace('－', '-').replace('﹣', '-').replace('＋', '+')
    private fun containsPhrase(text: String, phrase: String): Boolean = phrasePattern(phrase).containsMatchIn(text)
    private fun phrasePattern(phrase: String): Regex = Regex(
        "(?<![\\p{L}\\p{N}])${phrase.split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }}(?![\\p{L}\\p{N}])",
        RegexOption.IGNORE_CASE
    )

    private fun parseNumber(raw: String): Double? {
        val input = raw.replace(Regex("\\s+"), "")
        val normalized = if ('.' in input && ',' in input) {
            val decimal = if (input.lastIndexOf('.') > input.lastIndexOf(',')) '.' else ','
            val grouping = if (decimal == '.') ',' else '.'
            val parts = input.split(decimal)
            if (parts.size != 2 || !Regex("[+\\-]?\\d{1,3}(?:${Regex.escape(grouping.toString())}\\d{3})+").matches(parts[0]) ||
                !Regex("\\d+").matches(parts[1])
            ) return null
            parts[0].replace(grouping.toString(), "") + "." + parts[1]
        } else {
            if (!Regex("[+\\-]?(?:\\d+(?:[.,]\\d+)?|[.,]\\d+)").matches(input)) return null
            val parts = input.trimStart('+', '-').split('.', ',')
            // A lone 1,000 / 1.000 is ambiguous between decimal and grouping.
            if (parts.size == 2 && parts[1].length == 3 && parts[0].toLongOrNull()?.let { it > 0L } == true) return null
            input.replace(',', '.')
        }
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private fun round4(value: Double): Double = round(value * 10_000.0) / 10_000.0
    private fun format4(value: Double): String = "%.4f".format(Locale.US, value).trimEnd('0').trimEnd('.')
    private fun <T> failure(reason: String): Result<T> = Result.failure(IllegalArgumentException(reason))

    companion object {
        private const val DEFAULT_BATCH_G = 100.0
        private const val MAX_BATCH_G = 100_000.0
        private const val WEIGHT_TOLERANCE_G = 0.01
        private const val CONCENTRATION_TOLERANCE_PCT = 0.01
        private const val NUMBER = "(?:[+\\-]\\s*)*(?:\\d[\\d.,+\\-]*|[.,]\\d[\\d.,+\\-]*)"
        private val percentPattern = Regex("(?<![\\p{L}\\p{N}.,+\\-])($NUMBER)\\s*(?:%|persen\\b|percent\\b)", RegexOption.IGNORE_CASE)
        private val quantityPattern = Regex("(?<![\\p{L}\\p{N}.,+\\-])($NUMBER)\\s*([\\p{L}µμ]+)\\b", RegexOption.IGNORE_CASE)
        private val invalidNumberPattern = Regex("(?<![\\p{L}\\p{N}])(?:nan|[+\\-]?(?:infinity|inf)|∞)\\s*(?:%|g\\b|kg\\b|mg\\b|ml\\b|l\\b)", RegexOption.IGNORE_CASE)
        private val NEGATIONS = setOf("tanpa", "bukan", "without", "no")
        private val TYPE_TERMS = mapOf(
            "NATURAL" to setOf("natural", "alami", "botanical", "essential oil", "essential oils", "minyak atsiri", "minyak esensial"),
            "SYNTHETIC" to setOf("synthetic", "sintetis", "sintetik", "bibit", "fragrance oil", "fragrance oils")
        )
        private val FAMILY_WORDS = setOf(
            "citrus", "floral", "flower", "woody", "wood", "oriental", "ambery", "fresh", "fougere", "fougère", "aromatic", "herbal",
            "chypre", "gourmand", "sweet", "aquatic", "marine", "ocean", "ozonic", "water", "spicy", "earthy", "green", "earth", "soil",
            "musky", "powdery", "leather", "smoky", "smoke", "fruity", "fruit", "aldehydic", "soapy", "clean", "sport", "daytime", "tropical"
        )
        private val REQUEST_WORDS = setOf(
            "parfum", "perfume", "perfumery", "fragrance", "formula", "racikan", "racik", "meracik", "campur", "campurkan", "campuran", "blend",
            "baru", "new", "create", "make", "using", "with", "and", "from", "the", "buatkan", "resep", "recipe",
            "rekomendasi", "rekomendasikan", "recommend", "recommendation", "produk", "product", "bahan", "material", "materials",
            "pakai", "gunakan", "menggunakan", "berbahan", "berbasis", "hanya", "only", "saja", "murni", "alami",
            "natural", "synthetic", "sintetis", "sintetik", "bibit", "botanical", "essential", "esensial", "essensial", "oil", "oils", "minyak", "atsiri",
            "essence", "perfumer", "perfumers", "for", "women", "men", "unisex", "female", "male", "packing", "segel", "all",
            "brand", "merk", "merek", "katalog", "catalog", "dataset", "data", "batch", "total", "berat", "weight", "ukuran", "volume", "sebanyak", "sebesar", "target",
            "konsentrasi", "concentration", "kadar", "persen", "percent", "edp", "edt", "edc", "eau", "toilette", "cologne", "extrait",
            "konsentrat", "concentrate", "tanpa", "without", "bukan", "pelarut", "solvent", "top", "middle", "heart", "base", "note",
            "yang", "lembut", "ringan", "kuat", "cocok", "harian", "pria", "wanita", "dewasa", "anak", "malam"
        )
    }
}
