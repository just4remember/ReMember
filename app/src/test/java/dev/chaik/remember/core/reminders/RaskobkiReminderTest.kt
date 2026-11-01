package dev.chaik.remember.core.reminders

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RaskobkiReminderTest {
    private val day = LocalDate.of(2026, 11, 2)
    private val quietOthers = ReminderSettings(
        dayReport = DailyReminder(false, LocalTime.NOON),
        sleep = DailyReminder(false, LocalTime.NOON),
    )

    private fun plan(settings: ReminderSettings) =
        ReminderPlanner.plan(day.atTime(10, 0), day.atTime(14, 0), settings, emptyList(), emptyList())

    @Test
    fun `по умолчанию напоминания о раскобках нет`() {
        assertTrue(plan(quietOthers).isEmpty())
    }

    @Test
    fun `включённое приходит в выбранное время`() {
        val settings = quietOthers.copy(raskobki = DailyReminder(true, LocalTime.of(13, 30)))
        val planned = plan(settings).single()
        assertEquals(day.atTime(13, 30), planned.at)
        assertEquals(Reminder.Raskobki(day), planned.reminder)
    }
}
