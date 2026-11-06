package dev.chaik.remember.feature.search

import java.time.LocalDate

/** Разделы, по которым ищем, — в этом порядке идут группы результатов. */
enum class SearchSource { DAYS, DREAMS, NOTES, TASKS, EVENTS, MONEY, GYM, PLACES }

/** Куда вести по тапу на результат. */
sealed interface SearchTarget {
    data class Day(val date: LocalDate) : SearchTarget
    data class Note(val id: Long) : SearchTarget
    data class Task(val id: Long) : SearchTarget
    data class Event(val id: Long) : SearchTarget
    data class Workout(val id: Long) : SearchTarget
    data object Map : SearchTarget
}

/**
 * Что можно найти: заголовок, текст без разметки и теги. [hidden] — защищённая заметка:
 * ищем только по заголовку, текст в результат не попадает.
 */
data class SearchItem(
    val source: SearchSource,
    val key: String,
    val title: String,
    val text: String,
    val date: LocalDate?,
    val target: SearchTarget,
    val tags: List<String> = emptyList(),
    val hidden: Boolean = false,
)

/** Запрос: слова и теги (#тег). */
data class SearchQuery(val words: List<String>, val tags: List<String>) {
    val isEmpty: Boolean get() = words.isEmpty() && tags.isEmpty()
}

/** Кусочек текста вокруг найденного и где в нём подсветить. */
data class Snippet(val text: String, val highlights: List<IntRange>)

/** Найденное: сам элемент и где подсветить в заголовке и кусочке текста. */
data class SearchHit(val item: SearchItem, val titleHighlights: List<IntRange>, val snippet: Snippet?)

object SearchEngine {
    /**
     * Строчные буквы и «ё» как «е». Длина строки не меняется — позиции в нормализованной
     * строке совпадают с исходной, по ним и подсвечиваем.
     */
    fun normalize(text: String): String = buildString(text.length) {
        text.forEach { c ->
            val lower = c.lowercaseChar()
            append(if (lower == 'ё') 'е' else lower)
        }
    }

    fun parse(raw: String): SearchQuery {
        val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val tags = tokens.filter { it.startsWith("#") && it.length > 1 }.map { normalize(it.drop(1)) }
        val words = tokens.filterNot { it.startsWith("#") }.map(::normalize)
        return SearchQuery(words, tags)
    }

    /**
     * Все слова запроса найдены — в заголовке или тексте — и каждое как начало слова:
     * «трен» находит «тренировка», но не «потренировался» изнутри. По тегам — только через «#»
     * и точным совпадением: «тест» не находит запись лишь потому, что у неё тег «тест».
     */
    fun matches(item: SearchItem, query: SearchQuery): Boolean {
        if (query.isEmpty) return false
        val tags = item.tags.map(::normalize)
        if (!query.tags.all { it in tags }) return false
        val title = normalize(item.title)
        val text = if (item.hidden) "" else normalize(item.text)
        return query.words.all { word ->
            wordStarts(title, word).isNotEmpty() || wordStarts(text, word).isNotEmpty()
        }
    }

    fun hit(item: SearchItem, query: SearchQuery): SearchHit {
        val titleNorm = normalize(item.title)
        val titleHighlights = query.words.flatMap { w -> wordStarts(titleNorm, w).map { it until it + w.length } }
        val snippet = if (item.hidden || item.text.isBlank()) null else snippet(item.text, query.words)
        return SearchHit(item, titleHighlights, snippet)
    }

    /** Где в нормализованном [haystack] начинается слово, начинающееся с [word]. */
    fun wordStarts(haystack: String, word: String): List<Int> {
        if (word.isEmpty()) return emptyList()
        val result = ArrayList<Int>()
        var from = 0
        while (true) {
            val i = haystack.indexOf(word, from)
            if (i < 0) break
            if (i == 0 || !haystack[i - 1].isLetterOrDigit()) result += i
            from = i + 1
        }
        return result
    }

    /**
     * Кусочек текста вокруг первого найденного слова: немного до и побольше после, по границам слов,
     * с многоточиями по краям. Нет совпадений в тексте (нашлось по заголовку) — начало текста.
     */
    fun snippet(text: String, words: List<String>, before: Int = 40, after: Int = 110): Snippet {
        val flat = text.replace('\n', ' ')
        val norm = normalize(flat)
        val first = words.flatMap { wordStarts(norm, it) }.minOrNull() ?: 0
        var start = (first - before).coerceAtLeast(0)
        var end = (first + after).coerceAtMost(flat.length)
        // Не резать слово пополам
        if (start > 0) start = flat.indexOf(' ', start).takeIf { it in start until first }?.plus(1) ?: start
        if (end < flat.length) end = flat.lastIndexOf(' ', end).takeIf { it > first } ?: end
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < flat.length) "…" else ""
        val body = flat.substring(start, end).trim()
        // Подсветка — по самому кусочку: так она не съезжает из-за обрезки и пробелов
        val bodyNorm = normalize(body)
        val highlights = words.flatMap { w -> wordStarts(bodyNorm, w).map { it + prefix.length until it + prefix.length + w.length } }
        return Snippet(prefix + body + suffix, highlights)
    }

    /** Найденное по разделам, внутри — новые первыми (без даты — в конце). */
    fun search(items: List<SearchItem>, query: SearchQuery): Map<SearchSource, List<SearchHit>> =
        items.filter { matches(it, query) }
            .sortedWith(compareBy<SearchItem> { it.source.ordinal }.thenByDescending { it.date ?: LocalDate.MIN })
            .map { hit(it, query) }
            .groupBy { it.item.source }
}
