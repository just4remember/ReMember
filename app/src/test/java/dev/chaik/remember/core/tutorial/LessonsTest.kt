package dev.chaik.remember.core.tutorial

import dev.chaik.remember.app.navigation.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LessonsTest {
    @Test
    fun `у каждого видимого раздела есть урок`() {
        Destination.entries.filterNot { it.hidden }.forEach { destination ->
            assertNotNull("нет урока у ${destination.name}", Lessons.of(destination))
        }
    }

    @Test
    fun `у скрытых разделов уроков нет`() {
        Destination.entries.filter { it.hidden }.forEach { assertNull(Lessons.of(it)) }
    }

    @Test
    fun `ключи уроков не повторяются и не меняются`() {
        val keys = Destination.entries.mapNotNull { Lessons.of(it)?.key } + Lessons.Intro.key
        assertEquals(keys.size, keys.toSet().size)
        // Ключи хранятся в отметках у пользователей: смена ключа покажет карточку снова
        assertEquals("today", Lessons.of(Destination.Today)?.key)
        assertEquals("places", Lessons.of(Destination.Places)?.key)
    }
}
