package dev.chaik.remember.core.editor

import dev.chaik.remember.core.editor.DocumentBlock.Photos
import dev.chaik.remember.core.editor.DocumentBlock.Text
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorDocumentTest {
    private val p1 = "3f2b8c1e-0d4a-4b6f-9e21-7c5a0b9d1e42"
    private val p2 = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"
    private val p3 = "0c9d8e7f-6a5b-4c3d-9e2f-1a0b9c8d7e6f"

    private fun roundTrip(vararg blocks: DocumentBlock) {
        val list = blocks.toList()
        assertEquals(list, EditorDocument.parse(EditorDocument.serialize(list)))
    }

    @Test
    fun `обычный текст без фото`() {
        assertEquals(listOf(Text("Привет\nмир")), EditorDocument.parse("Привет\nмир"))
        roundTrip(Text(""))
        roundTrip(Text("Привет\n\nмир\n"))
    }

    @Test
    fun `фото между абзацами`() {
        val markdown = "Утром гулял.\n![](photo:$p1)\nВечером кино."
        val blocks = listOf(Text("Утром гулял."), Photos(listOf(p1)), Text("Вечером кино."))

        assertEquals(blocks, EditorDocument.parse(markdown))
        assertEquals(markdown, EditorDocument.serialize(blocks))
    }

    @Test
    fun `удаление фото из текста`() {
        // Одиночное фото — пропадает, абзацы вокруг склеиваются
        assertEquals("Утром гулял.\nВечером кино.", EditorDocument.removePhotos("Утром гулял.\n![](photo:$p1)\nВечером кино.", setOf(p1)))
        // Из сетки уходит только оно; параметры сетки остаются
        assertEquals(
            "Фото:\n![](photo:$p2?w=75) ![](photo:$p3)\n",
            EditorDocument.removePhotos("Фото:\n![](photo:$p1?w=75) ![](photo:$p2) ![](photo:$p3)\n", setOf(p1)),
        )
        // Фото в начале и в конце
        assertEquals("Текст", EditorDocument.removePhotos("![](photo:$p1)\nТекст\n![](photo:$p2)", setOf(p1, p2)))
        // Чужого фото в тексте нет — текст не трогаем
        val text = "Привет\n![](photo:$p2)\n"
        assertEquals(text, EditorDocument.removePhotos(text, setOf(p1)))
    }

    @Test
    fun `ширина и положение фото`() {
        assertEquals(
            listOf(Text("a"), Photos(listOf(p1), 50, PhotoAlign.End), Text("b")),
            EditorDocument.parse("a\n![](photo:$p1?w=50&a=right)\nb"),
        )
        assertEquals(
            "![](photo:$p1?w=75&a=left)",
            EditorDocument.serialize(listOf(Photos(listOf(p1), 75, PhotoAlign.Start))),
        )
        // Слишком маленькая ширина поднимается до минимума, неизвестное положение — по центру
        assertEquals(
            Photos(listOf(p1), DocumentBlock.MIN_WIDTH, PhotoAlign.Center),
            EditorDocument.parse("![](photo:$p1?w=5&a=diagonal)")[1],
        )
    }

    @Test
    fun `несколько фото в одной строке — сетка`() {
        val markdown = "До\n![](photo:$p1?w=75) ![](photo:$p2) ![](photo:$p3)\nПосле"
        val blocks = listOf(Text("До"), Photos(listOf(p1, p2, p3), 75), Text("После"))

        assertEquals(blocks, EditorDocument.parse(markdown))
        assertEquals(markdown, EditorDocument.serialize(blocks))
    }

    @Test
    fun `развёрнутая галерея`() {
        val markdown = "До\n![](photo:$p1?a=left&e=1) ![](photo:$p2)\nПосле"
        val blocks = listOf(Text("До"), Photos(listOf(p1, p2), align = PhotoAlign.Start, expanded = true), Text("После"))
        assertEquals(blocks, EditorDocument.parse(markdown))
        assertEquals(markdown, EditorDocument.serialize(blocks))
        // Без e=1 — свёрнута
        assertEquals(false, (EditorDocument.parse("![](photo:$p1) ![](photo:$p2)")[1] as Photos).expanded)
    }

    @Test
    fun `крайние случаи сохраняются без потерь`() {
        roundTrip(Text(""), Photos(listOf(p1)), Text(""))
        roundTrip(Text(""), Photos(listOf(p1)), Text(""), Photos(listOf(p2, p3), 75, PhotoAlign.End), Text(""))
        roundTrip(Text("до\n"), Photos(listOf(p1)), Text("\nпосле"))
        roundTrip(Text("\n\n"), Photos(listOf(p1), 30, PhotoAlign.Start), Text("\n\n"))
    }

    @Test
    fun `нормализация склеивает тексты и окружает фото текстом`() {
        assertEquals(
            listOf(Text(""), Photos(listOf(p1)), Text(""), Photos(listOf(p2)), Text("a\nb")),
            EditorDocument.normalize(listOf(Photos(listOf(p1)), Photos(listOf(p2)), Text("a"), Text("b"))),
        )
    }
}
