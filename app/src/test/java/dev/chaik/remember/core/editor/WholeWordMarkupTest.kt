package dev.chaik.remember.core.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class WholeWordMarkupTest {
    private fun styles(text: String) = InlineSpans.find(text).filterIsInstance<StyleSpan>().map { text.substring(it.range) }

    @Test
    fun `разметка работает только на слово целиком`() {
        assertEquals(listOf("**слово**"), styles("это **слово** тут"))
        assertEquals(emptyList<String>(), styles("сло**во** и **сло**во"))
        assertEquals(setOf("_курсив_", "~~зачёркнуто~~"), styles("_курсив_ и ~~зачёркнуто~~").toSet())
        assertEquals(emptyList<String>(), styles("snake_case_name"))
    }

    @Test
    fun `кнопка оборачивает слово целиком`() {
        val inside = MarkdownFormatting.toggleWrap(TextFieldValue("это слово тут", TextRange(6)), "**")
        assertEquals("это **слово** тут", inside.text)
        val part = MarkdownFormatting.toggleWrap(TextFieldValue("это слово тут", TextRange(5, 7)), "_")
        assertEquals("это _слово_ тут", part.text)
    }

    @Test
    fun `пункт чек-листа отмечается и снимается`() {
        val text = "- [ ] хлеб\n  - [x] молоко"
        val boxes = InlineSpans.find(text).filterIsInstance<CheckboxSpan>()
        assertEquals(listOf(false, true), boxes.map { it.checked })
        assertEquals("- [x] хлеб\n  - [x] молоко", InlineSpans.toggle(text, boxes[0]))
        assertEquals("- [ ] хлеб\n  - [ ] молоко", InlineSpans.toggle(text, boxes[1]))
    }
}
