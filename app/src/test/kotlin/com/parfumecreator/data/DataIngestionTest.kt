package com.parfumecreator.data

import org.junit.Assert.*
import org.junit.Test

class DataIngestionTest {

    private val packagingKeywords = listOf(
        "kaleng kosong", "botol kosong", "botol parfum", "jerigen", "sumbu lilin",
        "candle wick", "cetakan", "mold", "spatula", "dropper", "pipet", "holder wood",
        "pot cream", "spray bottle", "silicone mold"
    )

    private val candleKeywords = listOf(
        "cetakan lilin", "sumbu lilin", "candle wick", "wood centering tool",
        "candle soap making", "plaster pudding", "wax sachet", "palm stearin", "stearic acid"
    )

    private fun isExcludedNoise(name: String): Boolean {
        val nameLower = name.lowercase().trim()
        if (nameLower.isEmpty()) return true
        if (packagingKeywords.any { nameLower.contains(it) }) return true
        if (candleKeywords.any { nameLower.contains(it) }) return true
        if (nameLower.startsWith("all product") && (nameLower.contains("part") || nameLower.contains("list"))) return true
        return false
    }

    private fun detectBrand(name: String): String? {
        val nameUpper = name.uppercase()
        return when {
            Regex("\\bLUZI\\b").containsMatchIn(nameUpper) -> "LUZI"
            Regex("\\bPARFAROME\\b").containsMatchIn(nameUpper) -> "PARFAROME"
            Regex("\\b(?:MACRABAME|MACBRAME)\\b").containsMatchIn(nameUpper) -> "MACRABAME"
            Regex("\\bBENBERG\\b").containsMatchIn(nameUpper) -> "BENBERG"
            Regex("\\bBELL\\b").containsMatchIn(nameUpper) -> "BELL"
            Regex("\\bELIE\\s+MOISE\\b").containsMatchIn(nameUpper) -> "ELIE MOISE"
            else -> null
        }
    }

    @Test
    fun testValidFragranceListing_accepted() {
        val raw = "BIANCO LATTE FOR WOMEN AND MEN By PARFAROME PREMIUM Segel 500gr"
        assertFalse(isExcludedNoise(raw))
        assertEquals("PARFAROME", detectBrand(raw))
    }

    @Test
    fun testPackagingListing_excluded() {
        val raw = "KALENG KOSONG Kemasan 500gr, 1Kg & Jerigen"
        assertTrue(isExcludedNoise(raw))
    }

    @Test
    fun testCandleProduct_excluded() {
        val raw = "(10 pcs) Elie Moise Cotton Candle Wicks / Candle Wick / Sumbu Lilin Katun small pack"
        assertTrue(isExcludedNoise(raw))
    }

    @Test
    fun testSiliconeMold_excluded() {
        val raw = "Cetakan Lilin Plaster Pudding Bunga Viral Puding Jelly Estetik Flower Cetakan Silikon - silicone mold"
        assertTrue(isExcludedNoise(raw))
    }

    @Test
    fun testBundleListing_excluded() {
        val raw = "All Product By PARFAROME PREMIUM Repack 100ml \"Part 1\""
        assertTrue(isExcludedNoise(raw))
    }

    @Test
    fun testKnownBrandDetection_Luzi() {
        val raw = "All Product By LUZI Segel 500 Dan 1kg"
        assertEquals("LUZI", detectBrand(raw))
    }

    @Test
    fun testKnownBrandDetection_MacbrameAlias() {
        val raw = "SCANDAL VICA BY MACBRAME BIBIT MURNI 100ML"
        assertEquals("MACRABAME", detectBrand(raw))
    }

    @Test
    fun testUnknownBrand_notFabricated() {
        val raw = "Unbranded Fresh Cologne Concentrate 100ml"
        assertNull(detectBrand(raw))
    }

    @Test
    fun testEmptyName_excluded() {
        assertTrue(isExcludedNoise(""))
        assertTrue(isExcludedNoise("   "))
    }
}
