package com.parfumecreator.core.formula

import android.content.Context
import android.util.Log
import com.parfumecreator.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FormulaTemplateLoader @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private var cachedTemplates: List<FormulaTemplate>? = null

    fun getTemplates(): List<FormulaTemplate> {
        cachedTemplates?.let { return it }

        val list = mutableListOf<FormulaTemplate>()
        try {
            val jsonString = context.assets.open("data/formula_templates.json").use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).readText()
            }
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optInt("id", i + 1)
                val name = obj.getString("name")
                val category = obj.optString("category", "NATURAL")
                val familyStr = obj.optString("fragranceFamily", "FRESH")
                val concPct = obj.optDouble("concentrationPct", 20.0)
                val concTypeStr = obj.optString("concentrationType", "EDP")
                val desc = obj.optString("description", "")

                require(id > 0 && name.isNotBlank() && concPct.isFinite() && concPct > 0.0 && concPct <= 100.0) {
                    "Template $i memiliki metadata tidak valid"
                }

                val ingArray = obj.getJSONArray("ingredients")
                require(ingArray.length() > 0) { "Template $id tidak memiliki bahan" }
                val items = mutableListOf<FormulaTemplateItem>()
                for (j in 0 until ingArray.length()) {
                    val ingObj = ingArray.getJSONObject(j)
                    val ingName = ingObj.getString("name").trim()
                    require(ingName.isNotBlank()) { "Nama bahan template $id/$j kosong" }
                    val rawAmount = ingObj.getDouble("amount")
                    require(rawAmount.isFinite() && rawAmount > 0.0 && rawAmount <= 100_000.0) {
                        "Jumlah template $id/$j tidak valid"
                    }
                    val (amount, unit) = when (ingObj.optString("unit", "g").trim().lowercase()) {
                        "g", "gram" -> rawAmount to "g"
                        "mg", "milligram" -> (rawAmount / 1_000.0) to "g"
                        // A template item has no density metadata, so accepting
                        // mL would make its batch total unverifiable.
                        "ml", "milliliter" -> error(
                            "Unit template $id/$j memakai mL tanpa density katalog"
                        )
                        else -> error("Unit template $id/$j tidak valid")
                    }
                    require(amount.isFinite() && amount > 0.0 && amount <= 100_000.0) {
                        "Jumlah template $id/$j tidak valid setelah konversi"
                    }
                    val typeStr = ingObj.optString("type", "NATURAL")
                    val noteStr = ingObj.optString("noteType", "MIDDLE")
                    val ingFamStr = ingObj.optString("fragranceFamily", "FRESH")

                    items.add(
                        FormulaTemplateItem(
                            name = ingName,
                            amount = amount,
                            unit = unit,
                            type = if (typeStr.equals("SYNTHETIC", true)) IngredientType.SYNTHETIC else IngredientType.NATURAL,
                            noteType = NoteType.fromString(noteStr),
                            fragranceFamily = FragranceFamily.fromString(ingFamStr),
                            brand = ingObj.optString("brand").takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                }

                require(items.sumOf { it.amount }.isFinite() && items.sumOf { it.amount } > 0.0) {
                    "Total template $id tidak valid"
                }

                list.add(
                    FormulaTemplate(
                        id = id,
                        name = name,
                        category = category,
                        fragranceFamily = FragranceFamily.fromString(familyStr),
                        concentrationPct = concPct,
                        concentrationType = ConcentrationType.fromString(concTypeStr),
                        description = desc,
                        ingredients = items
                    )
                )
            }
            require(list.map { it.id }.distinct().size == list.size) { "ID template harus unik" }
            cachedTemplates = list
        } catch (e: Exception) {
            Log.w(TAG, "Formula template asset invalid or unavailable", e)
            return emptyList()
        }

        return list
    }

    private companion object {
        const val TAG = "FormulaTemplateLoader"
    }
}
