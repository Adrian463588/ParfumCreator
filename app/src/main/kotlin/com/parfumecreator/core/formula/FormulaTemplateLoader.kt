package com.parfumecreator.core.formula

import android.content.Context
import android.util.Log
import com.parfumecreator.domain.model.FormulaTemplate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Cached bundled templates; callers load on IO. Invalid files never produce a partial import. */
@Singleton
class FormulaTemplateLoader @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private var cachedTemplates: List<FormulaTemplate>? = null
    var errors: List<String> = emptyList()
        private set

    @Synchronized
    fun getTemplates(): List<FormulaTemplate> {
        cachedTemplates?.let { return it }
        val failures = mutableListOf<String>()
        val templates = FormulaTemplateParser.assetFiles.flatMap { file ->
            try {
                val json = context.assets.open("data/$file").bufferedReader(Charsets.UTF_8).use { it.readText() }
                FormulaTemplateParser.parse(json, file)
            } catch (error: Exception) {
                Log.w("FormulaTemplateLoader", "Invalid template asset: $file", error)
                failures.add("$file: ${error.message ?: "gagal dibaca"}")
                emptyList()
            }
        }
        errors = failures
        return templates.also { cachedTemplates = it }
    }
}
