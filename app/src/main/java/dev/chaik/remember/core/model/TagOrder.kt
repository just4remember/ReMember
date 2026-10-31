package dev.chaik.remember.core.model

import java.text.Collator
import java.util.Locale

/** Порядок тегов в списке: сначала английский алфавит, потом русский, потом всё остальное. */
object TagOrder {
    private val collator = Collator.getInstance(Locale.forLanguageTag("ru"))

    fun alphabetical(tags: List<Tag>): List<Tag> =
        tags.sortedWith(compareBy<Tag> { alphabet(it.name) }.then { a, b -> collator.compare(a.name, b.name) })

    private fun alphabet(name: String): Int {
        val first = name.trim().firstOrNull()?.lowercaseChar() ?: return 2
        return when (first) {
            in 'a'..'z' -> 0
            in 'а'..'я', 'ё' -> 1
            else -> 2
        }
    }
}
