package com.parfumecreator.domain

import com.parfumecreator.domain.engine.CatalogIngredientResolver
import com.parfumecreator.domain.model.Ingredient
import com.parfumecreator.domain.model.IngredientType
import org.junit.Assert.*
import org.junit.Test

class CatalogIngredientResolverTest {
    @Test
    fun aliasesRequireTheSameMaterialIdentity() {
        val bergamot = Ingredient(1, "Bergamot Essential Oil", IngredientType.NATURAL)
        val fcf = Ingredient(2, "Bergamot FCF Oil", IngredientType.NATURAL)
        assertEquals(bergamot, CatalogIngredientResolver.resolve("Bergamot Oil", IngredientType.NATURAL, listOf(bergamot, fcf)))
        assertEquals(fcf, CatalogIngredientResolver.resolve("Bergamot FCF", IngredientType.NATURAL, listOf(bergamot, fcf)))
        assertNull(CatalogIngredientResolver.resolve("Bergamot Absolute", IngredientType.NATURAL, listOf(bergamot, fcf)))
    }

    @Test
    fun descriptionsTypesAndAmbiguousNamesCannotReplaceIdentity() {
        val material = Ingredient(1, "Rose", IngredientType.NATURAL)
        val perfume = Ingredient(2, "Synthetic Rose Blend", IngredientType.SYNTHETIC, description = "Rose Absolute")
        assertNull(CatalogIngredientResolver.resolve("Rose Absolute", IngredientType.NATURAL, listOf(material, perfume)))
        assertNull(CatalogIngredientResolver.resolve("Rose", IngredientType.SYNTHETIC, listOf(material)))
        assertNull(CatalogIngredientResolver.resolve("Rose", IngredientType.NATURAL, listOf(material, material.copy(id = 3))))
        val branded = listOf(perfume.copy(name = "Rose", brand = "LUZI"), perfume.copy(id = 3, name = "Rose", brand = "PARFAROME"))
        assertEquals(2L, CatalogIngredientResolver.resolve("Rose", IngredientType.SYNTHETIC, branded, "LUZI")?.id)
    }
}
