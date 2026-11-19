package dev.chaik.remember.core.editor

import dev.chaik.remember.core.model.Photo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.text.input.TextFieldValue

class EditorImportTest {
    private val p1 = Photo("3f2b8c1e-0d4a-4b6f-9e21-7c5a0b9d1e42", 400, 300)
    private val p2 = Photo("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d", 400, 300)

    private fun EditorState.photoBlock() = blocks.filterIsInstance<EditorState.PhotoBlock>().single()

    @Test
    fun `заглушки сразу в тексте, готовые встают по одному, в документ — только готовые`() {
        val state = EditorState("Утро")
        val handle = state.beginImport(2)!!
        val block = state.photoBlock()
        assertEquals(2, block.itemCount)
        // Пока ничего не готово — в документе фото нет
        assertEquals("Утро", state.markdown)

        state.finishImport(handle, handle.items[0], p1)
        assertEquals(listOf(p1.id), block.photoIds)
        assertEquals(1, block.pending.size)
        assertEquals("Утро\n![](photo:${p1.id})\n", state.markdown)

        state.finishImport(handle, handle.items[1], p2)
        assertEquals(listOf(p1.id, p2.id), block.photoIds)
        assertTrue(block.pending.isEmpty())
    }

    @Test
    fun `если ничего не добавилось, блок пропадает`() {
        val state = EditorState("Утро")
        val handle = state.beginImport(1)!!
        state.finishImport(handle, handle.items[0], null)
        assertTrue(state.blocks.none { it is EditorState.PhotoBlock })
        assertEquals("Утро", state.markdown)
    }

    @Test
    fun `добавление из окна галереи — в конец этой галереи`() {
        val state = EditorState("![](photo:${p1.id})")
        val key = state.photoBlock().key
        state.addToGalleryNext(key)
        val handle = state.beginImport(1)!!
        assertEquals(key, handle.blockKey)
        state.finishImport(handle, handle.items[0], p2)
        assertEquals(listOf(p1.id, p2.id), state.photoBlock().photoIds)
        // Цель сброшена: следующая вставка — новым блоком
        assertNull(state.galleryTargetKey)
    }

    @Test
    fun `раскладка сохраняется в документе`() {
        val state = EditorState("![](photo:${p1.id}) ![](photo:${p2.id})")
        state.setPhotoLayout(state.photoBlock().key, PhotoLayout.Row)
        assertEquals("![](photo:${p1.id}?l=row) ![](photo:${p2.id})", state.markdown.trim())
        assertEquals(PhotoLayout.Row, EditorState(state.markdown).photoBlock().layout)
    }

    @Test
    fun `отмена не возвращает удалённое фото`() {
        val state = EditorState(DOC)
        val evening = state.blocks.last() as EditorState.TextBlock
        state.onTextFocusChanged(evening, true)
        state.onTextChange(evening, TextFieldValue("Вечер!"))
        state.removePhoto(state.photoBlock().key)
        state.undo()
        assertTrue(state.blocks.none { it is EditorState.PhotoBlock })
    }

    @Test
    fun `отмена текста не трогает вид фото`() {
        val state = EditorState(DOC)
        val evening = state.blocks.last() as EditorState.TextBlock
        state.onTextFocusChanged(evening, true)
        state.onTextChange(evening, TextFieldValue("Вечер!"))
        state.setPhotoWidth(state.photoBlock().key, 50)
        state.undo()
        assertEquals("Вечер", (state.blocks.last() as EditorState.TextBlock).value.text)
        assertEquals(50, state.photoBlock().widthPercent)
    }

    private val DOC get() = "Утро\n![](photo:${p1.id})\nВечер"
}
