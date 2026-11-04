package dev.chaik.remember.core.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Префикс списка в начале строки: «- », «* », «1. », «- [ ] », «- [x] ». */
internal val ListPrefix = Regex("""^(\s*)(- \[[ xX]] |[-*] |(\d+)\. )""")
internal val HeadingPrefix = Regex("""^(#{1,3}) """)

enum class LineStyle(val prefix: String) {
    Bullet("- "),
    Numbered("1. "),
    Checkbox("- [ ] "),
}

/**
 * Действия панели форматирования. Все функции чистые: получают текущее
 * значение поля и возвращают новое, ничего не меняя сами.
 */
object MarkdownFormatting {

    /**
     * Обернуть выделение в [marker] (`**` — жирный, `_` — курсив) или снять обёртку.
     * Разметка — только на слова целиком: выделение дотягивается до краёв слов, а курсор
     * посреди слова оборачивает всё слово. Вне слова вставляет пару маркеров и ставит курсор между ними.
     */
    fun toggleWrap(value: TextFieldValue, marker: String): TextFieldValue {
        val text = value.text
        var start = value.selection.min
        var end = value.selection.max
        while (start > 0 && text[start - 1].isLetterOrDigit()) start--
        while (end < text.length && text[end].isLetterOrDigit()) end++
        val before = text.substring(0, start)
        val after = text.substring(end)

        if (before.endsWith(marker) && after.startsWith(marker)) {
            val newText = before.dropLast(marker.length) + text.substring(start, end) + after.drop(marker.length)
            return TextFieldValue(newText, TextRange(start - marker.length, end - marker.length))
        }
        val newText = before + marker + text.substring(start, end) + marker + after
        return TextFieldValue(newText, TextRange(start + marker.length, end + marker.length))
    }

    /** Черта отдельной строкой после строки с курсором; курсор — на новую строку под ней. */
    fun insertRule(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val start = text.lastIndexOf('\n', value.selection.min - 1) + 1
        val end = text.indexOf('\n', value.selection.max).let { if (it < 0) text.length else it }
        // Пустая строка сама становится чертой, иначе черта — новой строкой под текущей
        val (at, insert) = if (text.substring(start, end).isBlank()) start to "___" else end to "\n___"
        val rest = text.substring(if (at == start) end else at)
        val newText = text.substring(0, at) + insert + "\n" + rest.removePrefix("\n")
        return TextFieldValue(newText, TextRange(at + insert.length + 1))
    }

    /** Вставить ссылку вместо выделения; адрес без протокола считается https. Курсор — после ссылки. */
    fun insertLink(value: TextFieldValue, label: String, url: String): TextFieldValue {
        val address = url.trim().let { if (HasScheme.containsMatchIn(it)) it else "https://$it" }
        val link = "[${label.trim().ifEmpty { url.trim() }}]($address)"
        val text = value.text.replaceRange(value.selection.min, value.selection.max, link)
        return TextFieldValue(text, TextRange(value.selection.min + link.length))
    }

    private val HasScheme = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*:""")

    /**
     * Включить или выключить список для строк под выделением.
     * Если все строки уже такого вида — префикс снимается, иначе ставится (заменяя другой).
     */
    fun toggleLineStyle(value: TextFieldValue, style: LineStyle): TextFieldValue {
        val allStyled = linesOf(value).all { lineStyleOf(value.text, it) == style }
        var number = 1
        return mapLines(value) { line ->
            val content = line.removeListPrefix()
            when {
                allStyled -> content
                style == LineStyle.Numbered -> "${number++}. $content"
                else -> style.prefix + content
            }
        }
    }

    /** Заголовок текущей строки по кругу: нет → крупный → средний → нет. */
    fun cycleHeading(value: TextFieldValue): TextFieldValue = mapLines(value) { line ->
        val level = HeadingPrefix.find(line)?.groupValues?.get(1)?.length ?: 0
        val content = line.replaceFirst(HeadingPrefix, "")
        when (level) {
            0 -> "# $content"
            1 -> "## $content"
            else -> content
        }
    }

    /**
     * Продолжение списка по Enter: если в строке со списком нажали Enter,
     * новая строка тоже начинается с маркера. Enter на пустом пункте — выход из списка.
     * Клавиатура часто присылает дописанное (или исправленное Т9) слово и Enter одной правкой —
     * это тоже Enter, если вставка заканчивается переносом строки и он в ней один.
     * Если изменение — не Enter, возвращает [new] как есть.
     */
    fun continueList(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
        val cursor = new.selection.start
        if (!new.selection.collapsed || cursor == 0 || new.text[cursor - 1] != '\n') return new
        val after = new.text.substring(cursor)
        if (!old.text.endsWith(after)) return new
        val oldBefore = old.text.substring(0, old.text.length - after.length)
        // Перенос перед курсором должен быть новым (а не старым, к которому подвинулись удалением)
        val common = new.text.commonPrefixWith(oldBefore).length
        if (common > cursor - 1 || new.text.substring(common, cursor - 1).contains('\n')) return new

        val lineStart = new.text.lastIndexOf('\n', cursor - 2) + 1
        val line = new.text.substring(lineStart, cursor - 1)
        val match = ListPrefix.find(line) ?: return new
        val (indent, marker) = match.destructured

        if (line.length == match.value.length) {
            // Пустой пункт: убираем маркер и сам Enter — список закончился
            val newText = new.text.removeRange(lineStart, cursor)
            return TextFieldValue(newText, TextRange(lineStart))
        }
        val next = indent + when {
            marker.startsWith("- [") -> "- [ ] "
            match.groupValues[3].isNotEmpty() -> "${match.groupValues[3].toInt() + 1}. "
            else -> marker
        }
        val newText = new.text.substring(0, cursor) + next + new.text.substring(cursor)
        return TextFieldValue(newText, TextRange(cursor + next.length))
    }

    private fun lineStyleOf(text: String, range: IntRange): LineStyle? {
        val line = text.substring(range.first, range.last + 1)
        val marker = ListPrefix.find(line)?.groupValues?.get(2) ?: return null
        return when {
            marker.startsWith("- [") -> LineStyle.Checkbox
            marker.first().isDigit() -> LineStyle.Numbered
            else -> LineStyle.Bullet
        }
    }

    private fun String.removeListPrefix(): String = replaceFirst(ListPrefix, "$1")

    /** Диапазоны строк, которых касается выделение (включая строку курсора). */
    private fun linesOf(value: TextFieldValue): List<IntRange> {
        val text = value.text
        val from = text.lastIndexOf('\n', (value.selection.min - 1).coerceAtLeast(-1)) + 1
        val to = text.indexOf('\n', value.selection.max).let { if (it == -1) text.length else it }
        val result = mutableListOf<IntRange>()
        var start = from
        while (true) {
            val end = text.indexOf('\n', start).let { if (it == -1 || it > to) to else it }
            result += start until end
            if (end >= to) break
            start = end + 1
        }
        return result
    }

    /**
     * Заменить строки под выделением через [transform], сохранив курсор
     * на «том же месте» относительно конца затронутого куска.
     */
    private fun mapLines(value: TextFieldValue, transform: (String) -> String): TextFieldValue {
        val text = value.text
        val lines = linesOf(value)
        val from = lines.first().first
        val to = lines.last().last + 1
        val replaced = lines.joinToString("\n") { transform(text.substring(it.first, it.last + 1)) }
        val newText = text.substring(0, from) + replaced + text.substring(to)
        val shift = replaced.length - (to - from)
        val selection = if (value.selection.collapsed) {
            TextRange((value.selection.start + shift).coerceIn(from, from + replaced.length))
        } else {
            TextRange(from, from + replaced.length)
        }
        return TextFieldValue(newText, selection)
    }
}
