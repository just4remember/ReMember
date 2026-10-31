package dev.chaik.remember.feature.today.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ListedDaysTest {
    @Test
    fun `от сегодня до начала первого месяца, новые сверху`() {
        val today = LocalDate.of(2026, 10, 2)
        val days = listedDays(today, YearMonth.of(2026, 9))
        assertEquals(today, days.first())
        assertEquals(LocalDate.of(2026, 9, 1), days.last())
        assertEquals(32, days.size)
    }
}
