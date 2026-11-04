package dev.chaik.remember.core.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorToolsTest {
    @Test
    fun `сохранённый порядок, новые кнопки — в конец`() {
        val tools = editorTools(listOf("Link", "Bold", "Unknown"))
        assertEquals(EditorTool.Link, tools[0])
        assertEquals(EditorTool.Bold, tools[1])
        assertEquals(EditorTool.entries.toSet(), tools.toSet())
    }

    @Test
    fun `черта — новой строкой, а пустая строка сама становится чертой`() {
        val after = MarkdownFormatting.insertRule(TextFieldValue("текст", TextRange(5)))
        assertEquals("текст\n___\n", after.text)
        assertEquals(TextRange(10), after.selection)
        assertEquals("а\n___\nб", MarkdownFormatting.insertRule(TextFieldValue("а\n\nб", TextRange(2))).text)
    }
}
