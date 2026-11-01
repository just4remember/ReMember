package dev.chaik.remember.core.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineSpansTest {
    @Test
    fun `находит спойлер и ссылку с подписью`() {
        val text = "смотри [youtube](https://youtu.be/a) и ||секрет||"
        val spans = InlineSpans.find(text)
        val link = spans.filterIsInstance<LinkSpan>().single()
        assertEquals("https://youtu.be/a", link.url)
        assertEquals("youtube", text.substring(link.label))
        assertEquals("секрет", spans.filterIsInstance<SpoilerSpan>().single().text)
    }

    @Test
    fun `фото и обычные скобки не считаются ссылками`() {
        assertTrue(InlineSpans.find("![](photo:abc)").isEmpty())
        assertTrue(InlineSpans.find("[1](см. выше) и - [x] пункт").isEmpty())
        assertTrue(InlineSpans.find("|| пара\nстрок ||").isEmpty())
    }

    @Test
    fun `в карточке спойлер скрыт, а от ссылки остаётся подпись`() {
        assertEquals("это ••• и youtube", MarkdownPreview.plain("это ||секрет|| и [youtube](https://youtu.be/a)"))
    }

    @Test
    fun `ссылка заменяет выделение и получает протокол`() {
        val value = TextFieldValue("смотри видео", TextRange(7, 12))
        val result = MarkdownFormatting.insertLink(value, "видео", "youtu.be/a")
        assertEquals("смотри [видео](https://youtu.be/a)", result.text)
        assertEquals(TextRange(result.text.length), result.selection)
        // Без подписи показывается сам адрес, готовый протокол не дублируется
        assertEquals("[http://a.b](http://a.b)", MarkdownFormatting.insertLink(TextFieldValue(), " ", "http://a.b").text)
    }
}
