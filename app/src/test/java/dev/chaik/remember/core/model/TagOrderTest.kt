package dev.chaik.remember.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TagOrderTest {
    @Test
    fun `сначала английский алфавит, потом русский, потом остальное`() {
        val names = listOf("Тула", "gym", "2026", "Ёлка", "Ace", "Зал", "Sekai", "Авто")
        val tags = names.mapIndexed { i, name -> Tag(i.toLong(), name) }
        assertEquals(
            listOf("Ace", "gym", "Sekai", "Авто", "Ёлка", "Зал", "Тула", "2026"),
            TagOrder.alphabetical(tags).map { it.name },
        )
    }
}
