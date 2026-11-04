package dev.chaik.remember.core.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class RuleSpanTest {
    private fun rules(text: String) = InlineSpans.find(text).filterIsInstance<RuleSpan>().map { text.substring(it.range) }

    @Test
    fun `черта — только строка из подчёркиваний`() {
        assertEquals(listOf("___", "_____"), rules("до\n___\nпосле\n_____"))
        assertEquals(emptyList<String>(), rules("___ a\n a___\n__\n_ _ _"))
    }

    @Test
    fun `в карточке черты нет`() {
        assertEquals("до\nпосле", MarkdownPreview.plain("до\n___\nпосле"))
    }
}
