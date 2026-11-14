package com.parfumecreator.core.nativebridge

import com.parfumecreator.domain.engine.FormulaCalculationEngine
import org.junit.Assert.*
import org.junit.Test

class NativeSecurityBridgeTest {

    @Test
    fun testCalculateSplStandardValues() {
        val concentration = 20.0
        val top = 2.0
        val mid = 3.0
        val base = 5.0

        val spl = NativeSecurityBridge.calculateSpl(concentration, top, mid, base)
        assertTrue("SPL must be positive", spl > 0.0)
        assertTrue("SPL must be <= 100.0", spl <= 100.0)

        // Manual expected:
        // maxRatio = 5.0
        // normTop = 2/5 = 0.4
        // normMid = 3/5 = 0.6
        // normBase = 5/5 = 1.0
        // sum = 2.0
        // normTop = 0.4/2 = 0.2
        // normMid = 0.6/2 = 0.3
        // normBase = 1.0/2 = 0.5
        // noteWeightFactor = (0.5 * 1.5 + 0.3 * 1.0 + 0.2 * 0.5) / 3.0
        // noteWeightFactor = (0.75 + 0.30 + 0.10) / 3.0 = 1.15 / 3.0 = 0.3833333333
        // spl = 20.0 * 0.3833333333 = 7.6666666667
        val expectedSpl = 20.0 * ((0.5 * 1.5 + 0.3 * 1.0 + 0.2 * 0.5) / 3.0)
        assertEquals(expectedSpl, spl, 0.0001)
    }

    @Test
    fun testCalculateSplInvalidInputs() {
        assertEquals(0.0, NativeSecurityBridge.calculateSpl(0.0, 1.0, 1.0, 1.0), 0.0001)
        assertEquals(0.0, NativeSecurityBridge.calculateSpl(-10.0, 1.0, 1.0, 1.0), 0.0001)
        assertEquals(0.0, NativeSecurityBridge.calculateSpl(Double.NaN, 1.0, 1.0, 1.0), 0.0001)
        assertEquals(0.0, NativeSecurityBridge.calculateSpl(Double.POSITIVE_INFINITY, 1.0, 1.0, 1.0), 0.0001)
        assertEquals(0.0, NativeSecurityBridge.calculateSpl(20.0, 0.0, 0.0, 0.0), 0.0001)
        assertEquals(0.0, NativeSecurityBridge.calculateSpl(20.0, -1.0, -2.0, -3.0), 0.0001)
    }

    @Test
    fun testCalculateSplClamping() {
        // Massive concentration with all base notes
        val spl = NativeSecurityBridge.calculateSpl(500.0, 0.0, 0.0, 10.0)
        assertEquals(100.0, spl, 0.0001)
    }

    @Test
    fun testCalculateConcentrationStandard() {
        val pct = NativeSecurityBridge.calculateConcentration(20.0, 100.0)
        assertEquals(20.0, pct, 0.0001)
    }

    @Test
    fun testCalculateConcentrationInvalidInputs() {
        assertEquals(0.0, NativeSecurityBridge.calculateConcentration(0.0, 100.0), 0.0001)
        assertEquals(0.0, NativeSecurityBridge.calculateConcentration(-5.0, 100.0), 0.0001)
        assertEquals(0.0, NativeSecurityBridge.calculateConcentration(20.0, 0.0), 0.0001)
        assertEquals(0.0, NativeSecurityBridge.calculateConcentration(20.0, -50.0), 0.0001)
        assertEquals(0.0, NativeSecurityBridge.calculateConcentration(Double.NaN, 100.0), 0.0001)
        assertEquals(0.0, NativeSecurityBridge.calculateConcentration(20.0, Double.POSITIVE_INFINITY), 0.0001)
    }

    @Test
    fun testAntiDebugAndHookingSafeFallback() {
        // When running in host JVM without native binary or debugger, should return false safely
        val antiDebug = NativeSecurityBridge.checkAntiDebug()
        val hooking = NativeSecurityBridge.checkHooking()
        assertFalse(antiDebug)
        assertFalse(hooking)
    }

    @Test
    fun testParityWithFormulaCalculationEngine() {
        val conc = 18.5
        val top = 3.2
        val mid = 4.1
        val base = 6.7

        val bridgeSpl = NativeSecurityBridge.calculateSpl(conc, top, mid, base)
        val engineSpl = FormulaCalculationEngine.calculateSpl(conc, top, mid, base)
        assertEquals(bridgeSpl, engineSpl, 0.000001)

        val bridgeConc = NativeSecurityBridge.calculateConcentration(18.5, 100.0)
        val engineConc = FormulaCalculationEngine.calculateConcentration(18.5, 100.0)
        assertEquals(bridgeConc, engineConc, 0.000001)
    }
}
