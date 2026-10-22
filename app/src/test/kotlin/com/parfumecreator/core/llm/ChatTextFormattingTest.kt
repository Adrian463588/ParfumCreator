package com.parfumecreator.core.llm

import org.junit.Assert.*
import org.junit.Test

class ChatTextFormattingTest {
    @Test
    fun headingsNumbersAndCitationUrlsStayIntact() {
        val text = "### ParfumAI\n\nGunakan 100.00 g dan 2.5 mg.\nhttps://en.wikipedia.org/wiki/Perfume"
        assertEquals(text, ChatTextFormatting.visibleText(text))
        assertEquals(text, LiteRtLmRunner.sanitizeLlmOutput(text))
        assertEquals("modeling perfume", LiteRtLmRunner.sanitizeLlmOutput("modeling perfume"))
    }

    @Test
    fun tableSeparatorIsRemovedWithoutChangingAmounts() {
        val text = "| Bahan | Gram |\n|---|---:|\n| Bergamot | 3.00 g |"
        val visible = ChatTextFormatting.visibleText(text)
        assertTrue(visible.contains("Bergamot · 3.00 g"))
        assertFalse(visible.contains("---"))
    }

    @Test
    fun arbitraryJsonRemainsVisible() {
        val json = "```json\n{\"notes\": [\"rose\", \"musk\"]}\n```"
        assertEquals(json, ChatTextFormatting.visibleText(json))
    }
}
