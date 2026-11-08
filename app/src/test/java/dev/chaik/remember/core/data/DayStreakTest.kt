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

    private val spare = StreakRules(spareDay = true)

    @Test
    fun `запасной день прощает один пропуск в неделю`() {
        // Пропущен позавчерашний день — серия держится
        assertEquals(4, DayStreak.length(days(0, 1, 3, 4), today, spare))
        // Два пропуска подряд обрывают
        assertEquals(2, DayStreak.length(days(0, 1, 4, 5), today, spare))
        // Второй пропуск в ту же неделю обрывает, через неделю прощается снова
        assertEquals(3, DayStreak.length(days(0, 2, 3, 5, 6), today, spare))
        assertEquals(9, DayStreak.length(days(0, 2, 3, 4, 5, 6, 7, 8, 10), today, spare))
    }

    @Test
    fun `пустое сегодня и пропущенное вчера держатся на запасном дне`() {
        assertEquals(2, DayStreak.length(days(2, 3), today, spare))
        assertEquals(0, DayStreak.length(days(3, 4), today, spare))
    }

    @Test
    fun `дни паузы не обрывают серию и не считаются`() {
        val rules = StreakRules(pauses = listOf(StreakPause(today.minusDays(5), today.minusDays(1))))
        assertEquals(3, DayStreak.length(days(0, 6, 7), today, rules))
        // Запись в день паузы засчитывается
        assertEquals(4, DayStreak.length(days(0, 3, 6, 7), today, rules))
        assertEquals(0, DayStreak.length(days(0, 7, 8), today.plusDays(2), rules))
    }

    @Test
    fun `до какого дня доживёт серия`() {
        // Завтра серия ещё жива: завтрашний день можно успеть записать
        assertEquals(today.plusDays(1), DayStreak.aliveThrough(days(0, 1), today))
        assertEquals(today.plusDays(2), DayStreak.aliveThrough(days(0, 1), today, spare))
        assertEquals(null, DayStreak.aliveThrough(days(3), today))
        val rules = StreakRules(pauses = listOf(StreakPause(today, today.plusDays(10))))
        assertEquals(today.plusDays(11), DayStreak.aliveThrough(days(1), today, rules))
    }

    @Test
    fun `пауза ставится, продлевается и снимается`() {
        val started = emptyList<StreakPause>().withPause(today, today.plusDays(7))
        assertEquals(listOf(StreakPause(today, today.plusDays(7))), started)
        assertEquals(listOf(StreakPause(today, today.plusDays(3))), started.withPause(today, today.plusDays(3)))
        // Снять в первый же день — паузы как не было
        assertEquals(emptyList<StreakPause>(), started.withoutPause(today))
        // Снять позже — прошедшие дни паузы остаются
        assertEquals(listOf(StreakPause(today, today.plusDays(1))), started.withoutPause(today.plusDays(2)))
    }
}
