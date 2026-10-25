package com.parfumecreator.domain.model

import java.util.Locale

data class Ingredient(
    val id: Long = 0,
    val name: String,
    val type: IngredientType,
    val brand: String? = null,
    val noteType: NoteType = NoteType.MIDDLE,
    val fragranceFamily: FragranceFamily = FragranceFamily.FRESH,
    val densityGml: Double = 1.0,
    val casNumber: String? = null,
    val inspiredBy: String? = null,
    val description: String? = null,
    val sourcePlant: String? = null,
    val ifraLimit: Double? = null,
    val isCustom: Boolean = false
) {
    /**
     * Recognizes named carriers, not any material containing "alcohol" or "absolute".
     * A scented material diluted in DPG remains a fragrance material: the catalog
     * does not provide the dilution fraction needed to split its mass further.
     */
    val isSolventOrDiluent: Boolean
        get() = isSolventName(name)

    companion object {
        /** Shared name-based carrier classification; does not imply purity or safety approval. */
        fun isSolventName(name: String): Boolean {
            val normalized = NAME_SEPARATORS.replace(name.lowercase(Locale.ROOT), " ").trim()
            return CARRIER_NAME.containsMatchIn(normalized) ||
                (normalized.startsWith("absolute ") &&
                    ABSOLUTE_CARRIER_QUALIFIER.containsMatchIn(normalized))
        }

        private val NAME_SEPARATORS = Regex("""[^\p{L}\p{N}]+""")
        private val CARRIER_NAME = Regex(
            """^(ethanol|ethyl alcohol|alkohol|alcohol|perfumer(?: s)? alcohol|""" +
                """dipropylene glycol|dipropilen glikol|dpg|triethyl citrate|tec|""" +
                """aquadest|aquades|distilled water|deionized water|purified water|air suling|""" +
                """isopropyl myristate|ipm|fractionated coconut oil|caprylic capric triglyceride|""" +
                """mct oil|jojoba oil|minyak jojoba|carrier oil|solvent|pelarut|""" +
                """perfume base|perfumer base|perfumer s base)\b"""
        )
        private val ABSOLUTE_CARRIER_QUALIFIER = Regex("""\b(96|food grade|pelarut|solva|solvablend)\b""")
    }
}

data class FormulaIngredient(
    val id: Long = 0,
    val formulaId: Long = 0,
    val ingredient: Ingredient,
    val amount: Double,
    val unit: MeasurementUnit = MeasurementUnit.GRAM,
    val amountInGram: Double = 0.0,
    val percentage: Double = 0.0
)

data class Formula(
    val id: Long = 0,
    val name: String,
    val totalWeightG: Double = 0.0,
    val concentrationPct: Double = 0.0,
    val concentrationType: ConcentrationType = ConcentrationType.EDP,
    val spl: Double = 0.0,
    val splLabel: SplLabel = SplLabel.MODERATE,
    val fragranceWheelPrimary: PrimaryFragranceWheel = PrimaryFragranceWheel.FRESH,
    val fragranceWheelSub: FragranceFamily = FragranceFamily.FRESH,
    val notes: String? = null,
    val ingredients: List<FormulaIngredient> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class FormulaIngredientInput(
    val ingredient: Ingredient,
    val amount: Double,
    val unit: MeasurementUnit
)

data class FormulaCalculationResult(
    val totalWeightG: Double,
    val fragranceWeightG: Double,
    val concentrationPct: Double,
    val concentrationType: ConcentrationType,
    val spl: Double,
    val splLabel: SplLabel,
    val fragranceWheelPrimary: PrimaryFragranceWheel,
    val dominantSubFamily: FragranceFamily,
    val topRatio: Double,
    val middleRatio: Double,
    val baseRatio: Double,
    val calculatedItems: List<CalculatedItemResult>,
    val familyScores: Map<FragranceFamily, Double>,
    val fragranceWheelResult: FragranceWheelResult = FragranceWheelResult(
        primary = fragranceWheelPrimary,
        dominantSubFamily = dominantSubFamily,
        scores = familyScores
    )
) {
    /** First invalid input/total; callers must not display or save invalid results. */
    val validationError: String?
        get() {
            calculatedItems.forEach { item ->
                if (!item.amount.isFinite() || item.amount < 0.0) {
                    return "Jumlah ${item.ingredient.name} harus berupa angka hingga dan tidak negatif."
                }
                if (item.unit == MeasurementUnit.MILLILITER &&
                    (!item.ingredient.densityGml.isFinite() || item.ingredient.densityGml <= 0.0)
                ) {
                    return "Densitas ${item.ingredient.name} tidak valid. Gunakan gram atau lengkapi densitas."
                }
                if (!item.amountInGram.isFinite() || item.amountInGram < 0.0 ||
                    (item.amount > 0.0 && item.amountInGram == 0.0)
                ) {
                    return "Jumlah ${item.ingredient.name} di luar batas perhitungan."
                }
            }
            return if (!totalWeightG.isFinite() || !fragranceWeightG.isFinite()) {
                "Total berat di luar batas perhitungan."
            } else {
                null
            }
        }
}

data class CalculatedItemResult(
    val ingredient: Ingredient,
    val amount: Double,
    val unit: MeasurementUnit,
    val amountInGram: Double,
    val percentageOfTotal: Double,
    val percentageOfFragrance: Double
)

data class FragranceWheelResult(
    val primary: PrimaryFragranceWheel,
    val dominantSubFamily: FragranceFamily,
    val primaryAxisScores: Map<PrimaryFragranceWheel, Double> = emptyMap(),
    val primaryAxisPercentages: Map<PrimaryFragranceWheel, Double> = emptyMap(),
    val secondaryWheel: PrimaryFragranceWheel? = null,
    val secondarySubFamily: FragranceFamily? = null,
    val olfactoryDirection: String = primary.displayName,
    val scores: Map<FragranceFamily, Double>
) {
    /** False for empty/solvent-only input, irrespective of the stored enum defaults. */
    val hasFragranceProfile: Boolean
        get() = scores.values.any { it.isFinite() && it > 0.0 }
}

data class FormulaTemplate(
    val id: Int,
    val name: String,
    val category: String, // "NATURAL" or "SYNTHETIC"
    val fragranceFamily: FragranceFamily,
    val concentrationPct: Double,
    val concentrationType: ConcentrationType,
    val description: String,
    val ingredients: List<FormulaTemplateItem>,
    val sourceId: String? = null,
    val sourceFile: String = "formula_templates.json",
    val sourceUrls: List<String> = emptyList(),
    val familyLabel: String = fragranceFamily.displayName,
    val isExperimental: Boolean = false
)

data class FormulaTemplateItem(
    val name: String,
    val amount: Double,
    val unit: String = "g",
    val type: IngredientType = IngredientType.NATURAL,
    val noteType: NoteType = NoteType.MIDDLE,
    val fragranceFamily: FragranceFamily = FragranceFamily.FRESH,
    val brand: String? = null,
    val catalogCode: String? = null,
    val requiresSelection: Boolean = false
)
