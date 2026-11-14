package com.parfumecreator.domain.engine

import com.parfumecreator.core.nativebridge.NativeSecurityBridge
import com.parfumecreator.domain.model.*

object FormulaCalculationEngine {

    // Masked proprietary heuristic weights (de-obfuscated dynamically to prevent static constant extraction)
    private const val MASK_KEY: Long = 0x5A5A5A5A5A5A5A5AL
    private val OBF_BASE_WEIGHT = java.lang.Double.doubleToRawLongBits(1.5) xor MASK_KEY
    private val OBF_MID_WEIGHT = java.lang.Double.doubleToRawLongBits(1.0) xor MASK_KEY
    private val OBF_TOP_WEIGHT = java.lang.Double.doubleToRawLongBits(0.5) xor MASK_KEY
    private val OBF_DIVISOR = java.lang.Double.doubleToRawLongBits(3.0) xor MASK_KEY

    private fun getBaseWeight(): Double = java.lang.Double.longBitsToDouble(OBF_BASE_WEIGHT xor MASK_KEY)
    private fun getMidWeight(): Double = java.lang.Double.longBitsToDouble(OBF_MID_WEIGHT xor MASK_KEY)
    private fun getTopWeight(): Double = java.lang.Double.longBitsToDouble(OBF_TOP_WEIGHT xor MASK_KEY)
    private fun getDivisor(): Double = java.lang.Double.longBitsToDouble(OBF_DIVISOR xor MASK_KEY)

    /**
     * Converts a given amount in various measurement units to grams.
     * Returns zero for invalid/unrepresentable inputs. Invalid mL density never
     * falls back to an invented 1 g/mL. Full results expose validationError.
     */
    fun convertToGrams(amount: Double, unit: MeasurementUnit, densityGml: Double): Double {
        if (!amount.isFinite() || amount <= 0.0) return 0.0
        val grams = when (unit) {
            MeasurementUnit.GRAM -> amount
            MeasurementUnit.MILLIGRAM -> amount / 1000.0
            MeasurementUnit.MILLILITER -> {
                if (!densityGml.isFinite() || densityGml <= 0.0) return 0.0
                amount * densityGml
            }
        }
        return grams.takeIf { it.isFinite() } ?: 0.0
    }

    /** Converts an editor amount while preserving mass; null means conversion is invalid. */
    fun convertAmount(
        amount: Double,
        from: MeasurementUnit,
        to: MeasurementUnit,
        densityGml: Double
    ): Double? {
        if (!amount.isFinite() || amount < 0.0) return null
        if ((from == MeasurementUnit.MILLILITER || to == MeasurementUnit.MILLILITER) &&
            (!densityGml.isFinite() || densityGml <= 0.0)
        ) return null
        val grams = convertToGrams(amount, from, densityGml)
        val converted = when (to) {
            MeasurementUnit.GRAM -> grams
            MeasurementUnit.MILLIGRAM -> grams * 1000.0
            MeasurementUnit.MILLILITER -> grams / densityGml
        }
        return converted.takeIf { it.isFinite() && (amount == 0.0 || it > 0.0) }
    }

    /**
     * Calculates the fragrance concentration percentage.
     */
    fun calculateConcentration(fragranceWeightG: Double, totalWeightG: Double): Double {
        return NativeSecurityBridge.calculateConcentration(fragranceWeightG, totalWeightG)
    }

    /**
     * Maps mass percentage to the app's approximate concentration bands.
     */
    fun getConcentrationType(concentrationPct: Double): ConcentrationType {
        val safePct = if (concentrationPct.isFinite()) concentrationPct else 0.0
        return ConcentrationType.fromConcentration(safePct)
    }

    /**
     * Calculates an unvalidated Scent Profile Level heuristic [0.0 - 100.0].
     * It does not predict measured sillage, projection, or longevity.
     *
     * Formula:
     * noteWeightFactor = ((baseRatio * 1.5) + (middleRatio * 1.0) + (topRatio * 0.5)) / 3.0
     * SPL = concentration * noteWeightFactor (clamped 0..100)
     */
    fun calculateSpl(
        concentrationPct: Double,
        topRatio: Double,
        middleRatio: Double,
        baseRatio: Double
    ): Double {
        return NativeSecurityBridge.calculateSpl(concentrationPct, topRatio, middleRatio, baseRatio)
    }

    /**
     * Maps numerical SPL score to verbal classification.
     */
    fun getSplLabel(splScore: Double): SplLabel {
        val safeScore = if (splScore.isFinite()) splScore else 0.0
        return SplLabel.fromScore(safeScore)
    }

    /**
     * Predicts primary and sub-family fragrance wheel position based on weighted ingredient amounts.
     */
    fun predictFragranceWheel(items: List<CalculatedItemResult>): FragranceWheelResult {
        val scores = mutableMapOf<FragranceFamily, Double>()
        FragranceFamily.entries.forEach { scores[it] = 0.0 }

        val nonSolventItems = items.filter {
            !it.ingredient.isSolventOrDiluent && it.amountInGram.isFinite() && it.amountInGram > 0.0
        }
        nonSolventItems.forEach { item ->
            val family = item.ingredient.fragranceFamily
            scores[family] = (scores[family] ?: 0.0) + item.amountInGram
        }

        val totalFragranceG = nonSolventItems.sumOf { it.amountInGram }

        // Primary 4-axis aggregated scores
        val primaryScores = mutableMapOf<PrimaryFragranceWheel, Double>()
        PrimaryFragranceWheel.entries.forEach { primaryScores[it] = 0.0 }

        scores.forEach { (family, weightG) ->
            val primaryAxis = family.toPrimaryWheel()
            primaryScores[primaryAxis] = (primaryScores[primaryAxis] ?: 0.0) + weightG
        }

        // Primary 4-axis percentages
        val primaryPercentages = mutableMapOf<PrimaryFragranceWheel, Double>()
        PrimaryFragranceWheel.entries.forEach { axis ->
            val axisG = primaryScores[axis] ?: 0.0
            primaryPercentages[axis] = if (totalFragranceG > 0.0) (axisG / totalFragranceG) * 100.0 else 0.0
        }

        // Sorted sub-families
        val sortedSubFamilies = scores.entries
            .filter { it.value > 0.0 }
            .sortedByDescending { it.value }

        val primaryWheel = primaryScores.entries
            .filter { it.value > 0.0 }
            .maxByOrNull { it.value }?.key ?: PrimaryFragranceWheel.FRESH
        val dominantSub = sortedSubFamilies
            .firstOrNull { it.key.toPrimaryWheel() == primaryWheel }?.key ?: FragranceFamily.FRESH

        // Determine secondary sub-family & secondary primary axis
        val secondaryWheel = primaryScores.entries
            .filter { it.key != primaryWheel && it.value > 0.0 }
            .maxByOrNull { it.value }?.key
        val secondarySub = sortedSubFamilies.firstOrNull {
            it.key.toPrimaryWheel() == secondaryWheel
        }?.key ?: sortedSubFamilies.firstOrNull { it.key != dominantSub }?.key

        val olfactoryDirection = when {
            totalFragranceG == 0.0 -> ""
            secondaryWheel != null && secondaryWheel != primaryWheel -> {
                "${primaryWheel.displayName} ${secondaryWheel.displayName}"
            }
            dominantSub != FragranceFamily.FRESH && dominantSub.name != primaryWheel.name -> {
                "${dominantSub.displayName} ${primaryWheel.displayName}"
            }
            else -> primaryWheel.displayName
        }

        return FragranceWheelResult(
            primary = primaryWheel,
            dominantSubFamily = dominantSub,
            primaryAxisScores = primaryScores,
            primaryAxisPercentages = primaryPercentages,
            secondaryWheel = secondaryWheel,
            secondarySubFamily = secondarySub,
            olfactoryDirection = olfactoryDirection,
            scores = scores
        )
    }

    /**
     * Performs a complete formula recalculation from raw ingredient inputs.
     */
    fun calculateFormula(items: List<FormulaIngredientInput>): FormulaCalculationResult {
        // 1. Convert all items to grams
        val itemGrams = items.map { input ->
            val grams = convertToGrams(input.amount, input.unit, input.ingredient.densityGml)
            Triple(input, grams, input.ingredient.isSolventOrDiluent)
        }

        val totalWeightG = itemGrams.sumOf { it.second }
        val fragranceWeightG = itemGrams.filter { !it.third }.sumOf { it.second }

        // 2. Note ratios
        var topGrams = 0.0
        var middleGrams = 0.0
        var baseGrams = 0.0

        itemGrams.filter { !it.third }.forEach { (input, grams, _) ->
            when (input.ingredient.noteType) {
                NoteType.TOP -> topGrams += grams
                NoteType.MIDDLE, NoteType.HEART -> middleGrams += grams
                NoteType.BASE -> baseGrams += grams
            }
        }

        val topRatio = if (fragranceWeightG > 0.0) (topGrams / fragranceWeightG).coerceIn(0.0, 1.0) else 0.0
        val middleRatio = if (fragranceWeightG > 0.0) (middleGrams / fragranceWeightG).coerceIn(0.0, 1.0) else 0.0
        val baseRatio = if (fragranceWeightG > 0.0) (baseGrams / fragranceWeightG).coerceIn(0.0, 1.0) else 0.0

        // 3. Concentration & SPL
        val concentrationPct = calculateConcentration(fragranceWeightG, totalWeightG)
        val concentrationType = getConcentrationType(concentrationPct)
        val spl = calculateSpl(concentrationPct, topRatio, middleRatio, baseRatio)
        val splLabel = getSplLabel(spl)

        // 4. Item percentages
        val calculatedItems = itemGrams.map { (input, grams, isSolvent) ->
            val pctTotal = if (totalWeightG > 0.0) (grams / totalWeightG) * 100.0 else 0.0
            val pctFragrance = if (!isSolvent && fragranceWeightG > 0.0) (grams / fragranceWeightG) * 100.0 else 0.0
            CalculatedItemResult(
                ingredient = input.ingredient,
                amount = input.amount,
                unit = input.unit,
                amountInGram = grams,
                percentageOfTotal = pctTotal,
                percentageOfFragrance = pctFragrance
            )
        }

        // 5. Fragrance Wheel
        val wheelResult = predictFragranceWheel(calculatedItems)

        return FormulaCalculationResult(
            totalWeightG = totalWeightG,
            fragranceWeightG = fragranceWeightG,
            concentrationPct = concentrationPct,
            concentrationType = concentrationType,
            spl = spl,
            splLabel = splLabel,
            fragranceWheelPrimary = wheelResult.primary,
            dominantSubFamily = wheelResult.dominantSubFamily,
            topRatio = topRatio,
            middleRatio = middleRatio,
            baseRatio = baseRatio,
            calculatedItems = calculatedItems,
            familyScores = wheelResult.scores,
            fragranceWheelResult = wheelResult
        )
    }
}
