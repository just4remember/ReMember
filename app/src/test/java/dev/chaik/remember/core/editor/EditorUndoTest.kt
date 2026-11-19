package dev.chaik.remember.core.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorUndoTest {
    private var now = 0L
    private val state = EditorState("", clock = { now })
    private val block get() = state.blocks.first() as EditorState.TextBlock

    private fun type(text: String) = text.forEach { c ->
        val v = block.value
        val t = v.text + c
        state.onTextChange(block, TextFieldValue(t, TextRange(t.length)))
        now += 100
    }

    private fun backspace(times: Int) = repeat(times) {
        val t = block.value.text.dropLast(1)
        state.onTextChange(block, TextFieldValue(t, TextRange(t.length)))
        now += 100
    }

    @Test
    fun `набор отменяется по словам`() {
        type("привет мир")
        assertTrue(state.canUndo)
        state.undo()
        assertEquals("привет ", block.value.text)
        state.undo()
        assertEquals("", block.value.text)
        assertFalse(state.canUndo)
        state.redo()
        state.redo()
        assertEquals("привет мир", block.value.text)
    }

    @Test
    fun `лишнее удаление возвращается`() {
        type("один два три")
        now += 5_000
        backspace(8) // « два три» — удалили лишнее слово
        assertEquals("один", block.value.text)
        state.undo()
        assertEquals("один два", block.value.text)
        state.undo()
        assertEquals("один два три", block.value.text)
    }

    @Test
    fun `пауза начинает новый шаг`() {
        type("аб")
        now += 2_000
        type("вг")
        state.undo()
        assertEquals("аб", block.value.text)
    }

    @Test
    fun `новая правка сбрасывает повтор`() {
        type("слово")
        state.undo()
        assertTrue(state.canRedo)
        type("x")
        assertFalse(state.canRedo)
    }

    @Test
    fun `загрузка документа очищает историю`() {
        type("текст")
        state.load("другое")
        assertFalse(state.canUndo)
        assertEquals("другое", block.value.text)
    }

    @Test
    fun `курсор возвращается на место`() {
        type("abc")
        now += 5_000
        state.onTextChange(block, TextFieldValue("abc", TextRange(1)))
        state.onTextChange(block, TextFieldValue("ac", TextRange(1)))
        state.undo()
        assertEquals("abc", block.value.text)
        assertEquals(TextRange(1), block.value.selection)
    }
}
