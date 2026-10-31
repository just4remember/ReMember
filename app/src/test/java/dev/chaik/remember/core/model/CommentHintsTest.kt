package dev.chaik.remember.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CommentHintsTest {
    private val known = listOf("Пятёрочка", "Лента", "пятёрочка", "Магнит", "Перекрёсток")

    @Test
    fun `без текста — все без повторов в прежнем порядке`() {
        assertEquals(listOf("Пятёрочка", "Лента", "Магнит", "Перекрёсток"), commentHints(known, ""))
    }

    @Test
    fun `фильтр по части слова без учёта регистра`() {
        assertEquals(listOf("Пятёрочка"), commentHints(known, "ПЯТ"))
        assertEquals(listOf("Магнит"), commentHints(known, " агн "))
    }

    @Test
    fun `набранное целиком не предлагается`() {
        assertEquals(emptyList<String>(), commentHints(listOf("Лента"), "лента"))
    }

    @Test
    fun `подсказок не больше лимита`() {
        assertEquals(listOf("Пятёрочка", "Лента"), commentHints(known, "", limit = 2))
    }
}
