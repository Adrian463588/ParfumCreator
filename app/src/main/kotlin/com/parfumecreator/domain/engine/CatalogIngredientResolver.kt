package com.parfumecreator.domain.engine

import com.parfumecreator.domain.model.Ingredient
import com.parfumecreator.domain.model.IngredientType
import java.util.Locale

/** Resolves a material by identity; descriptions and aroma similarity never authorize substitution. */
object CatalogIngredientResolver {
    fun resolve(name: String, type: IngredientType, catalog: List<Ingredient>, brand: String? = null): Ingredient? {
        val typed = catalog.filter { it.id > 0 && it.type == type &&
            (brand == null || matchesBrand(it.brand, brand)) }
        val exact = typed.filter { it.name.trim().equals(name.trim(), ignoreCase = true) }
        if (exact.isNotEmpty()) return exact.singleOrNull()
        val key = identityTokens(name)
        if (key.isEmpty()) return null
        return typed.filter { identityTokens(it.name) == key }.singleOrNull()
    }

    /** Catalog spelling aliases only, not permission to substitute another supplier. */
    fun matchesBrand(actual: String?, requested: String): Boolean =
        normalizeBrand(actual) == normalizeBrand(requested)

    private fun normalizeBrand(brand: String?): String? = brand?.trim()?.uppercase(Locale.ROOT)
        ?.let { if (it == "MACBRAME") "MACRABAME" else it }

    private fun identityTokens(name: String): List<String> = Regex("[\\p{L}\\p{N}]+")
        .findAll(name.lowercase(Locale.ROOT))
        .map { it.value }
        .filterNot { it in setOf("essential", "oil", "minyak", "esensial", "eo") }
        .toList()
}
