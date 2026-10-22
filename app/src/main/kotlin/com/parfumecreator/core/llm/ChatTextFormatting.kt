package com.parfumecreator.core.llm

/** Formats display text without guessing sentence boundaries or rewriting numbers/URLs/JSON. */
object ChatTextFormatting {
    fun visibleText(raw: String): String {
        val withoutFormula = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            .replace(raw) { block ->
                if (StructuredFormulaParser.parseJsonFormula(block.value) != null) "" else block.value
            }
        return withoutFormula.replace("\r\n", "\n").lineSequence().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith('|') && trimmed.endsWith('|')) {
                val columns = trimmed.trim('|').split('|').map(String::trim)
                if (columns.all { it.matches(Regex(":?-{1,}:?")) }) null
                else columns.joinToString(" · ")
            } else line
        }.joinToString("\n").trim()
    }
}
