package dev.chaik.remember.core.editor

/** Участок текста, у которого разметка спрятана, пока внутри него нет курсора. */
sealed interface InlineSpan {
    val range: IntRange
}

/** `||текст||`: текст под блюром. */
data class SpoilerSpan(override val range: IntRange, val content: IntRange, val text: String) : InlineSpan

/** `[подпись](адрес)`. */
data class LinkSpan(override val range: IntRange, val label: IntRange, val url: String) : InlineSpan

object InlineSpans {
    internal val Spoiler = Regex("""\|\|([^|\n]+)\|\|""")

    // Только с протоколом: обычный текст в скобках вроде [1](см. выше) ссылкой не станет. ![]() — это фото
    internal val Link = Regex("""(?<!!)\[([^\]\n]+)]\(([a-zA-Z][a-zA-Z0-9+.-]*:[^)\s]+)\)""")

    fun find(text: String): List<InlineSpan> {
        val links = Link.findAll(text).map {
            LinkSpan(it.range, it.groups[1]!!.range, it.groupValues[2])
        }
        val spoilers = Spoiler.findAll(text).map {
            SpoilerSpan(it.range, it.groups[1]!!.range, it.groupValues[1])
        }
        return (links + spoilers).toList()
    }
}
