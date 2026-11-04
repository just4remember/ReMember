package dev.chaik.remember.core.editor

/** Участок текста с разметкой: её видно, когда внутри курсор (или если так выбрано в настройках). */
sealed interface InlineSpan {
    val range: IntRange
}

/** `||текст||`: текст под блюром. */
data class SpoilerSpan(override val range: IntRange, val content: IntRange, val text: String) : InlineSpan

/** `[подпись](адрес)`. */
data class LinkSpan(override val range: IntRange, val label: IntRange, val url: String) : InlineSpan

/** Жирный, курсив или зачёркнутый: [marker] символов разметки с каждой стороны. */
data class StyleSpan(override val range: IntRange, val kind: Kind, val marker: Int) : InlineSpan {
    enum class Kind { BOLD, ITALIC, STRIKE }
}

/** Горизонтальная черта: строка только из подчёркиваний, не меньше трёх. */
data class RuleSpan(override val range: IntRange) : InlineSpan

/** Пункт чек-листа: [range] — «- [ ] » в начале строки, [box] — сами скобки «[ ]». */
data class CheckboxSpan(override val range: IntRange, val box: IntRange, val checked: Boolean) : InlineSpan

object InlineSpans {
    /**
     * Разметка — только на слова целиком: перед открывающими символами и после закрывающих
     * не может быть буквы или цифры. «**слово**» — жирное, «сло**во**» и «**сло**во» — просто текст.
     */
    private fun wrapped(marker: String): Regex {
        // Экранируем каждый символ: \Q…\E внутри [] не работает
        val m = marker.map { "\\$it" }.joinToString("")
        val c = "\\${marker.first()}"
        return Regex("""(?<![\p{L}\p{N}$c])$m(?=[^\s$c])([^$c\n]*?[^\s$c])$m(?![\p{L}\p{N}$c])""")
    }

    internal val Spoiler = wrapped("||")
    private val Bold = wrapped("**")
    private val Strike = wrapped("~~")
    private val ItalicUnderscore = wrapped("_")
    private val ItalicStar = wrapped("*")

    // Только с протоколом: обычный текст в скобках вроде [1](см. выше) ссылкой не станет. ![]() — это фото
    internal val Link = Regex("""(?<!!)\[([^\]\n]+)]\(([a-zA-Z][a-zA-Z0-9+.-]*:[^)\s]+)\)""")

    // Ровно подчёркивания и ничего больше: «___ a» — уже не черта
    private val Rule = Regex("""(?m)^_{3,}$""")

    private val Checkbox = Regex("""(?m)^(\s*)- (\[([ xX])]) """)

    fun find(text: String): List<InlineSpan> {
        val links = Link.findAll(text).map {
            LinkSpan(it.range, it.groups[1]!!.range, it.groupValues[2])
        }
        val spoilers = Spoiler.findAll(text).map {
            SpoilerSpan(it.range, it.groups[1]!!.range, it.groupValues[1])
        }
        val styles = sequenceOf(
            Bold to StyleSpan.Kind.BOLD,
            Strike to StyleSpan.Kind.STRIKE,
            ItalicUnderscore to StyleSpan.Kind.ITALIC,
            ItalicStar to StyleSpan.Kind.ITALIC,
        ).flatMap { (regex, kind) ->
            val marker = if (kind == StyleSpan.Kind.ITALIC) 1 else 2
            regex.findAll(text).map { StyleSpan(it.range, kind, marker) }
        }
        val boxes = Checkbox.findAll(text).map {
            val start = it.range.first + it.groupValues[1].length
            CheckboxSpan(start..it.range.last, it.groups[2]!!.range, it.groupValues[3] != " ")
        }
        val rules = Rule.findAll(text).map { RuleSpan(it.range) }
        return (links + spoilers + styles + boxes + rules).toList()
    }

    /** Отметить пункт или снять отметку. */
    fun toggle(text: String, box: CheckboxSpan): String {
        val at = box.box.first + 1
        return text.replaceRange(at, at + 1, if (box.checked) " " else "x")
    }
}
