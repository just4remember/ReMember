package dev.chaik.remember.feature.search

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {
    private fun item(
        title: String,
        text: String = "",
        tags: List<String> = emptyList(),
        hidden: Boolean = false,
        date: LocalDate? = null,
        source: SearchSource = SearchSource.DAYS,
    ) = SearchItem(source, title, title, text, date, SearchTarget.Map, tags, hidden)

    @Test
    fun caseAndYoDoNotMatter() {
        val q = SearchEngine.parse("ЁЛКА")
        assertTrue(SearchEngine.matches(item("Купил ёлку", "елка на балконе"), q))
        assertTrue(SearchEngine.matches(item("x", "Ёлка"), SearchEngine.parse("елка")))
    }

    @Test
    fun wordsInAnyOrderAndOnlyAtWordStart() {
        val it = item("Утро", "Выпил кофе и пошёл на тренировку")
        assertTrue(SearchEngine.matches(it, SearchEngine.parse("кофе утро")))
        assertTrue(SearchEngine.matches(it, SearchEngine.parse("трен")))
        assertFalse(SearchEngine.matches(it, SearchEngine.parse("ренир")))
        assertFalse(SearchEngine.matches(it, SearchEngine.parse("кофе чай")))
    }

    @Test
    fun tagsMatchExactlyWithHash() {
        val it = item("День", "текст", tags = listOf("Спорт", "кофе"))
        assertTrue(SearchEngine.matches(it, SearchEngine.parse("#спорт")))
        assertFalse(SearchEngine.matches(it, SearchEngine.parse("#спо")))
        // Без «#» теги не ищутся
        assertFalse(SearchEngine.matches(it, SearchEngine.parse("спорт")))
    }

    @Test
    fun hiddenNotesMatchOnlyByTitle() {
        val note = item("Пароли", "секретный текст", hidden = true, source = SearchSource.NOTES)
        assertFalse(SearchEngine.matches(note, SearchEngine.parse("секретный")))
        assertTrue(SearchEngine.matches(note, SearchEngine.parse("пароли")))
        assertNull(SearchEngine.hit(note, SearchEngine.parse("пароли")).snippet)
    }

    @Test
    fun snippetAroundFirstMatchWithHighlights() {
        val text = "Начало длинной записи. ".repeat(10) + "Сегодня был отличный кофе в новом месте. " + "Хвост. ".repeat(30)
        val snippet = SearchEngine.snippet(text, listOf("кофе"))
        assertTrue(snippet.text.startsWith("…"))
        assertTrue(snippet.text.endsWith("…"))
        val range = snippet.highlights.single()
        assertEquals("кофе", snippet.text.substring(range.first, range.last + 1))
    }

    @Test
    fun resultsGroupedNewestFirst() {
        val old = item("кофе старое", date = LocalDate.of(2026, 1, 1))
        val new = item("кофе новое", date = LocalDate.of(2026, 11, 1))
        val note = item("кофе заметка", source = SearchSource.NOTES)
        val result = SearchEngine.search(listOf(old, note, new), SearchEngine.parse("кофе"))
        assertEquals(listOf(SearchSource.DAYS, SearchSource.NOTES), result.keys.toList())
        assertEquals(listOf("кофе новое", "кофе старое"), result.getValue(SearchSource.DAYS).map { it.item.title })
    }
}
