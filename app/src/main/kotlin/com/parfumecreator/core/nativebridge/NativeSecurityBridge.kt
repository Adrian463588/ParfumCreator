package com.parfumecreator.core.nativebridge

object NativeSecurityBridge {

    val isNativeLoaded: Boolean

    init {
        isNativeLoaded = try {
            System.loadLibrary("parfume_core")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    // JNI External Declarations
    @JvmStatic
    private external fun calculateSplNative(
        concentrationPct: Double,
        topRatio: Double,
        middleRatio: Double,
        baseRatio: Double
    ): Double

    @JvmStatic
    private external fun calculateConcentrationNative(
        fragranceWeightG: Double,
        totalWeightG: Double
    ): Double

    @JvmStatic
    private external fun checkNativeAntiDebug(): Boolean

    @JvmStatic
    private external fun checkNativeHooking(): Boolean

    /**
     * Calculates SPL using native C++ engine if available, or falls back to pure Kotlin calculation.
     */
    fun calculateSpl(
        concentrationPct: Double,
        topRatio: Double,
        middleRatio: Double,
        baseRatio: Double
    ): Double {
        return if (isNativeLoaded) {
            try {
                calculateSplNative(concentrationPct, topRatio, middleRatio, baseRatio)
            } catch (_: UnsatisfiedLinkError) {
                calculateSplFallback(concentrationPct, topRatio, middleRatio, baseRatio)
            }
        } else {
            calculateSplFallback(concentrationPct, topRatio, middleRatio, baseRatio)
        }
    }

    /**
     * Calculates fragrance concentration using native C++ engine if available, or falls back to pure Kotlin calculation.
     */
    fun calculateConcentration(fragranceWeightG: Double, totalWeightG: Double): Double {
        return if (isNativeLoaded) {
            try {
                calculateConcentrationNative(fragranceWeightG, totalWeightG)
            } catch (_: UnsatisfiedLinkError) {
                calculateConcentrationFallback(fragranceWeightG, totalWeightG)
            }
        } else {
            calculateConcentrationFallback(fragranceWeightG, totalWeightG)
        }
    }

    /**
     * Evaluates native anti-debugging checks (ptrace and direct /proc/self/status TracerPid).
     */
    fun checkAntiDebug(): Boolean {
        if (!isNativeLoaded) return false
        return try {
            checkNativeAntiDebug()
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Evaluates native low-level maps scanning for dynamic instrumentation agents (Frida/Xposed).
     */
    fun checkHooking(): Boolean {
        if (!isNativeLoaded) return false
        return try {
            checkNativeHooking()
        } catch (_: Throwable) {
            false
        }
    }

    // Kotlin Fallback Engine (ensures 100% velocity in host JVM unit tests)
    private fun calculateSplFallback(
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
        if (sumRatios <= 0.0) return 0.0
        val normTop = (safeTop / maxRatio) / sumRatios
        val normMid = (safeMid / maxRatio) / sumRatios
        val normBase = (safeBase / maxRatio) / sumRatios

        val noteWeightFactor = ((normBase * 1.5) + (normMid * 1.0) + (normTop * 0.5)) / 3.0
        val spl = concentrationPct * noteWeightFactor
        return spl.coerceIn(0.0, 100.0)
    }

    private fun calculateConcentrationFallback(fragranceWeightG: Double, totalWeightG: Double): Double {
        if (!fragranceWeightG.isFinite() || !totalWeightG.isFinite() || totalWeightG <= 0.0 || fragranceWeightG <= 0.0) {
            return 0.0
        }
        val pct = (fragranceWeightG / totalWeightG) * 100.0
        return pct.coerceIn(0.0, 100.0)
    }
}
