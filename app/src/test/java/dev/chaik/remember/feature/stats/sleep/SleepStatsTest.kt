package dev.chaik.remember.feature.stats.sleep

import dev.chaik.remember.core.model.SleepEntry
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepStatsTest {
    private val zone = ZoneOffset.UTC

    private fun entry(date: LocalDate, bed: String, wake: String, quality: Int? = null): SleepEntry {
        val (bh, bm) = bed.split(":").map(String::toInt)
        val (wh, wm) = wake.split(":").map(String::toInt)
        // Отбой после 18:00 — накануне, раньше — в день пробуждения
        val bedDay = if (bh >= 18) date.minusDays(1) else date
        return SleepEntry(
            date = date,
            bedTime = bedDay.atTime(bh, bm).toInstant(zone),
            wakeTime = date.atTime(wh, wm).toInstant(zone),
            quality = quality,
        )
    }

    @Test
    fun bedtimeAroundMidnightAveragesNearMidnight() {
        val monday = LocalDate.of(2026, 11, 2)
        val summary = SleepStats.summary(
            listOf(entry(monday, "23:30", "07:30"), entry(monday.plusDays(1), "00:30", "08:30")),
            zone,
        )
        val all = summary.all!!
        assertEquals("00:00", SleepStats.clock(all.bedMinute))
        assertEquals("08:00", SleepStats.clock(all.wakeMinute))
        assertEquals(8 * 60, all.durationMinutes)
        assertEquals(30, all.bedSpreadMinutes)
    }

    @Test
    fun weekendIsWakingOnSaturdayOrSunday() {
        val friday = LocalDate.of(2026, 11, 6)
        val summary = SleepStats.summary(
            listOf(
                entry(friday, "23:00", "07:00"),
                entry(friday.plusDays(1), "01:00", "10:00"),
                entry(friday.plusDays(2), "02:00", "11:00"),
            ),
            zone,
        )
        assertEquals(1, summary.weekdays!!.nights)
        assertEquals(2, summary.weekends!!.nights)
        assertEquals(9 * 60, summary.weekends!!.durationMinutes)
    }

    @Test
    fun deficitCountsOnlyShortNights() {
        val day = LocalDate.of(2026, 11, 2)
        val summary = SleepStats.summary(
            listOf(entry(day, "00:00", "06:00"), entry(day.plusDays(1), "22:00", "08:00")),
            zone,
        )
        assertEquals(120, summary.deficitMinutes)
    }

    @Test
    fun incompleteEntriesAreSkipped() {
        val day = LocalDate.of(2026, 11, 2)
        val summary = SleepStats.summary(listOf(SleepEntry(date = day, quality = 8)), zone)
        assertTrue(summary.nights.isEmpty())
        assertNull(summary.all)
        assertEquals(8.0, summary.averageQuality!!, 0.001)
    }

    @Test
    fun periodsFollowTheCalendar() {
        val today = LocalDate.of(2026, 11, 5) // четверг
        assertEquals(LocalDate.of(2026, 11, 2)..LocalDate.of(2026, 11, 8), StatsPeriod.WEEK.range(today, 0))
        assertEquals(LocalDate.of(2026, 10, 26)..LocalDate.of(2026, 11, 1), StatsPeriod.WEEK.range(today, 1))
        assertEquals(
            LocalDate.of(2026, 11, 1)..LocalDate.of(2026, 11, 7),
            StatsPeriod.WEEK.range(today, 0, firstDayOfWeek = java.time.DayOfWeek.SUNDAY),
        )
        assertEquals(LocalDate.of(2026, 10, 1)..LocalDate.of(2026, 10, 31), StatsPeriod.MONTH.range(today, 1))
        assertEquals(LocalDate.of(2026, 10, 1)..LocalDate.of(2026, 12, 31), StatsPeriod.QUARTER.range(today, 0))
        assertEquals(LocalDate.of(2026, 7, 1)..LocalDate.of(2026, 9, 30), StatsPeriod.QUARTER.range(today, 1))
        assertEquals(LocalDate.of(2025, 1, 1)..LocalDate.of(2025, 12, 31), StatsPeriod.YEAR.range(today, 1))
        assertEquals(LocalDate.of(2026, 3, 1)..today, StatsPeriod.ALL.range(today, 0, LocalDate.of(2026, 3, 1)))
    }
}
