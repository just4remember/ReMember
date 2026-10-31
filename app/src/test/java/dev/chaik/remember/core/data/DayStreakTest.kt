package dev.chaik.remember.core.data

import dev.chaik.remember.core.settings.AppSettings
import dev.chaik.remember.core.settings.logicalToday
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DayStreakTest {
    private val today = LocalDate.of(2026, 10, 31)
    private fun days(vararg back: Long) = back.map(today::minusDays).toSet()

    @Test
    fun `серия считается подряд и включает сегодня`() {
        assertEquals(3, DayStreak.length(days(0, 1, 2, 4), today))
    }

    @Test
    fun `пустое сегодня серию не обрывает, пропущенное вчера обрывает`() {
        assertEquals(2, DayStreak.length(days(1, 2), today))
        assertEquals(0, DayStreak.length(days(2, 3), today))
        assertEquals(0, DayStreak.length(emptySet(), today))
    }

    @Test
    fun `день сменяется в заданное время`() {
        val settings = AppSettings(dayStartMinutes = 240)
        assertEquals(today.minusDays(1), settings.logicalToday(LocalDateTime.of(2026, 10, 31, 3, 59)))
        assertEquals(today, settings.logicalToday(LocalDateTime.of(2026, 10, 31, 4, 0)))
        assertEquals(today, AppSettings().logicalToday(LocalDateTime.of(2026, 10, 31, 0, 0)))
    }
}
