package com.parfumecreator.domain.model

enum class MeasurementUnit(val symbol: String) {
    GRAM("g"),
    MILLIGRAM("mg"),
    MILLILITER("ml");

    companion object {
        fun fromString(value: String): MeasurementUnit = when (value.uppercase().trim()) {
            "MG", "MILLIGRAM" -> MILLIGRAM
            "ML", "MILLILITER" -> MILLILITER
            else -> GRAM
        }
    }
}

enum class IngredientType {
    NATURAL,
    SYNTHETIC
}

enum class NoteType(val displayName: String) {
    TOP("Top Note"),
    MIDDLE("Middle Note"),
    HEART("Heart Note"),
    BASE("Base Note");

    fun isTop(): Boolean = this == TOP
    fun isMiddle(): Boolean = this == MIDDLE || this == HEART
    fun isBase(): Boolean = this == BASE

    companion object {
        fun fromString(value: String?): NoteType = when (value?.uppercase()?.trim()) {
            "TOP" -> TOP
            "BASE" -> BASE
            "HEART" -> HEART
            else -> MIDDLE
        }
    }
}

enum class PrimaryFragranceWheel(val displayName: String, val hexColor: String) {
    FLORAL("Floral", "#E8B4D0"),
    WOODY("Woody", "#8B6914"),
    ORIENTAL("Oriental", "#C4612A"),
    FRESH("Fresh", "#7BC8A4");

    companion object {
        fun fromString(value: String?): PrimaryFragranceWheel = when (value?.uppercase()?.trim()) {
            "FLORAL" -> FLORAL
            "WOODY" -> WOODY
            "ORIENTAL" -> ORIENTAL
            else -> FRESH
        }
    }
}

enum class FragranceFamily(
    val displayName: String,
    val hexColor: String,
    val description: String = ""
) {
    FLORAL("Floral", "#E8B4D0", "Bunga seperti mawar, melati, ylang-ylang"),
    WOODY("Woody", "#8B6914", "Kayu hangat seperti sandalwood, cedarwood, oud"),
    ORIENTAL("Amber / Oriental", "#C4612A", "Hangat manis eksotis, amber, damar, vanila"),
    FRESH("Fresh", "#7BC8A4", "Aroma segar, udara bersih, ozonik"),
    CITRUS("Citrus", "#F5C842", "Jeruk segar, bergamot, lemon, grapefruit"),
    FOUGERE("Fougère / Aromatic", "#5E8A6E", "Herbal aromatik, lavender, geranium, coumarin"),
    CHYPRE("Chypre", "#8B7355", "Klasik mewah berlumut, patchouli, bergamot, labdanum"),
    GOURMAND("Gourmand", "#D4874E", "Manis lezat karamel, kopi, cokelat, vanila"),
    AQUATIC("Aquatic / Marine", "#5DADE2", "Segar laut, air bersih, ozon, angin pantai"),
    SPICY("Spicy", "#C0392B", "Rempah hangat, kayu manis, lada, cengkeh, kapulaga"),
    EARTHY("Earthy / Green", "#795548", "Tanah basah, akar wangi / vetiver, lumut hutan"),
    MUSKY("Musky", "#A89180", "Sensual, halus lembut, bedak, white musk"),
    LEATHER("Leather / Smoky", "#5D4037", "Kulit samak, tembakau, birch tar, aroma asap"),
    FRUITY("Fruity", "#E91E63", "Buah segar manis, beri, apel, persik"),
    GREEN("Green", "#4CAF50", "Dedaunan hijau segar, rumput terpotong, galbanum"),
    ALDEHYDIC("Aldehydic", "#90A4AE", "Bersih berkilau, sabun mewah, klasik modern");

    fun toPrimaryWheel(): PrimaryFragranceWheel = when (this) {
        FLORAL, ALDEHYDIC -> PrimaryFragranceWheel.FLORAL
        WOODY, EARTHY, CHYPRE, LEATHER -> PrimaryFragranceWheel.WOODY
        ORIENTAL, GOURMAND, SPICY, MUSKY -> PrimaryFragranceWheel.ORIENTAL
        FRESH, CITRUS, FOUGERE, AQUATIC, FRUITY, GREEN -> PrimaryFragranceWheel.FRESH
    }

    companion object {
        fun fromString(value: String?): FragranceFamily = when (value?.uppercase()?.trim()) {
            "FLORAL" -> FLORAL
            "WOODY" -> WOODY
            "ORIENTAL", "AMBER" -> ORIENTAL
            "CITRUS" -> CITRUS
            "FOUGERE", "FOUGEREE", "AROMATIC" -> FOUGERE
            "CHYPRE", "CHYPREE" -> CHYPRE
            "GOURMAND" -> GOURMAND
            "AQUATIC", "WATER", "MARINE", "OZONE" -> AQUATIC
            "SPICY" -> SPICY
            "EARTHY" -> EARTHY
            "MUSKY", "MUSK" -> MUSKY
            "LEATHER", "SMOKY", "TOBACCO" -> LEATHER
            "FRUITY", "FRUIT" -> FRUITY
            "GREEN" -> GREEN
            "ALDEHYDIC", "ALDEHYDE" -> ALDEHYDIC
            else -> FRESH
        }
    }
}

enum class ConcentrationType(
    val displayName: String,
    val minPct: Double,
    val maxPct: Double,
    val longevityDesc: String
) {
    PARFUM_EXTRAIT("Parfum Extrait", 20.0, 100.0, "8-12 hours"),
    EDP("Eau de Parfum (EDP)", 15.0, 20.0, "6-8 hours"),
    EDT("Eau de Toilette (EDT)", 8.0, 15.0, "4-6 hours"),
    EDC("Eau de Cologne (EDC)", 4.0, 8.0, "2-4 hours"),
    EAU_FRAICHE("Eau Fraîche", 0.0, 4.0, "< 2 hours");

    companion object {
        fun fromConcentration(pct: Double): ConcentrationType = when {
            pct >= 20.0 -> PARFUM_EXTRAIT
            pct >= 15.0 -> EDP
            pct >= 8.0 -> EDT
            pct >= 4.0 -> EDC
            else -> EAU_FRAICHE
        }

        fun fromString(value: String?): ConcentrationType = when (value?.uppercase()?.trim()) {
            "PARFUM_EXTRAIT", "EXTRAIT", "EXTRAIT_DE_PARFUM" -> PARFUM_EXTRAIT
            "EDP", "EAU_DE_PARFUM" -> EDP
            "EDT", "EAU_DE_TOILETTE" -> EDT
            "EDC", "EAU_DE_COLOGNE", "COLOGNE" -> EDC
            "EAU_FRAICHE", "FRAICHE" -> EAU_FRAICHE
            else -> PARFUM_EXTRAIT
        }
    }
}

enum class SplLabel(val displayName: String, val rangeDesc: String) {
    SUBTLE("Subtle", "0 - 25"),
    MODERATE("Moderate", "26 - 50"),
    STRONG("Strong", "51 - 75"),
    INTENSE("Intense", "76 - 100");

    companion object {
        fun fromScore(score: Double): SplLabel = when {
            score <= 25.0 -> SUBTLE
            score <= 50.0 -> MODERATE
            score <= 75.0 -> STRONG
            else -> INTENSE
        }

        fun fromString(value: String?): SplLabel = when (value?.uppercase()?.trim()) {
            "SUBTLE" -> SUBTLE
            "MODERATE" -> MODERATE
            "STRONG" -> STRONG
            "INTENSE" -> INTENSE
            else -> MODERATE
        }
    }
}
