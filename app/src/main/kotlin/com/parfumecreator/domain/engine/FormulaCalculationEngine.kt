package com.parfumecreator.domain.engine

import com.parfumecreator.domain.model.*

object FormulaCalculationEngine {

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
        if (!fragranceWeightG.isFinite() || !totalWeightG.isFinite() || totalWeightG <= 0.0 || fragranceWeightG <= 0.0) {
            return 0.0
        }
        val pct = (fragranceWeightG / totalWeightG) * 100.0
        return pct.coerceIn(0.0, 100.0)
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
        if (!concentrationPct.isFinite() || concentrationPct <= 0.0) return 0.0

        val safeTop = if (topRatio.isFinite() && topRatio >= 0.0) topRatio else 0.0
        val safeMid = if (middleRatio.isFinite() && middleRatio >= 0.0) middleRatio else 0.0
        val safeBase = if (baseRatio.isFinite() && baseRatio >= 0.0) baseRatio else 0.0

        val maxRatio = maxOf(safeTop, safeMid, safeBase)
        if (maxRatio == 0.0) return 0.0
        val sumRatios = safeTop / maxRatio + safeMid / maxRatio + safeBase / maxRatio
        val normTop = (safeTop / maxRatio) / sumRatios
        val normMid = (safeMid / maxRatio) / sumRatios
        val normBase = (safeBase / maxRatio) / sumRatios

        val noteWeightFactor = ((normBase * 1.5) + (normMid * 1.0) + (normTop * 0.5)) / 3.0
        val rawSpl = concentrationPct * noteWeightFactor
        return rawSpl.coerceIn(0.0, 100.0)
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
