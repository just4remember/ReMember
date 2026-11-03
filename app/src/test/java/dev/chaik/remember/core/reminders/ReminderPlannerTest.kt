package dev.chaik.remember.core.reminders

import dev.chaik.remember.core.model.Event
import dev.chaik.remember.core.model.Repeat
import dev.chaik.remember.core.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderPlannerTest {
    // Пятница
    private val today = LocalDate.of(2026, 10, 23)
    private fun at(day: LocalDate, h: Int, m: Int = 0): LocalDateTime = day.atTime(h, m)

    /** Только то, что включено явно, — чтобы проверки не мешали друг другу. */
    private val nothing = ReminderSettings(
        dayReport = DailyReminder(false, LocalTime.of(21, 0)),
        summary = DailyReminder(false, LocalTime.of(8, 0)),
        events = false,
        tasks = false,
    )

    private fun plan(settings: ReminderSettings, from: LocalDateTime, to: LocalDateTime, events: List<Event> = emptyList(), tasks: List<Task> = emptyList()) =
        ReminderPlanner.plan(from, to, settings, events, tasks)

    @Test
    fun `ежедневные — в своё время и только в выбранные дни`() {
        val settings = nothing.copy(
            dayReport = DailyReminder(true, LocalTime.of(21, 0), days = setOf(DayOfWeek.FRIDAY)),
            summary = DailyReminder(true, LocalTime.of(9, 0)),
        )
        val result = plan(settings, at(today, 0), at(today.plusDays(1), 23, 59))
        assertEquals(
            listOf(
                at(today, 9) to Reminder.Summary(today),
                at(today, 21) to Reminder.DayReport(today),
                at(today.plusDays(1), 9) to Reminder.Summary(today.plusDays(1)),
            ),
            result.map { it.at to it.reminder },
        )
    }

    @Test
    fun `промежуток не включает начало, но включает конец`() {
        val settings = nothing.copy(summary = DailyReminder(true, LocalTime.of(9, 0)))
        assertTrue(plan(settings, at(today, 9), at(today, 10)).isEmpty())
        assertEquals(1, plan(settings, at(today, 8), at(today, 9)).size)
    }

    @Test
    fun `общий выключатель глушит всё`() {
        val settings = nothing.copy(enabled = false, summary = DailyReminder(true, LocalTime.of(9, 0)))
        assertTrue(plan(settings, at(today, 0), at(today, 23)).isEmpty())
    }

    @Test
    fun `событие — за выбранное время, повторы тоже`() {
        val birthday = Event(id = 1, title = "Иван", date = LocalDate.of(2000, 10, 24), isBirthday = true, remindMinutes = 1440)
        val weekly = Event(id = 2, title = "Зал", date = today, time = LocalTime.of(19, 0), repeat = Repeat.Weekly, remindMinutes = 30)
        val silent = Event(id = 3, title = "Без напоминания", date = today)
        val result = plan(nothing.copy(events = true), at(today, 0), at(today, 23, 59), events = listOf(birthday, weekly, silent))
        assertEquals(
            listOf(
                at(today, 8) to Reminder.EventAlert(1, at(today.plusDays(1), 8)),
                at(today, 18, 30) to Reminder.EventAlert(2, at(today, 19)),
            ),
            result.map { it.at to it.reminder },
        )
    }

    @Test
    fun `задачи — только со временем и невыполненные`() {
        val tasks = listOf(
            Task(id = 1, title = "Позвонить", dueDate = today, dueTime = LocalTime.of(15, 0)),
            Task(id = 2, title = "Без времени", dueDate = today),
            Task(id = 3, title = "Сделано", dueDate = today, dueTime = LocalTime.of(16, 0), done = true),
        )
        val result = plan(nothing.copy(tasks = true), at(today, 0), at(today, 23), tasks = tasks)
        assertEquals(listOf(Reminder.TaskDue(1, at(today, 15))), result.map { it.reminder })
    }

    @Test
    fun `тихие часы — перенести, беззвучно или пропустить`() {
        val late = nothing.copy(dayReport = DailyReminder(true, LocalTime.of(23, 30)))
        val quiet = QuietHours(enabled = true, start = LocalTime.of(23, 0), end = LocalTime.of(7, 0))

        // Перенос на утро: вчерашнее напоминание попадает в промежуток сегодняшнего утра
        val postponed = plan(late.copy(quiet = quiet), at(today, 6), at(today, 8))
        assertEquals(listOf(at(today, 7) to Reminder.DayReport(today.minusDays(1))), postponed.map { it.at to it.reminder })

        val silent = plan(late.copy(quiet = quiet.copy(mode = QuietMode.SILENT)), at(today, 23), at(today, 23, 59)).single()
        assertEquals(at(today, 23, 30), silent.at)
        assertTrue(silent.silent)

        assertTrue(plan(late.copy(quiet = quiet.copy(mode = QuietMode.SKIP)), at(today, 0), at(today.plusDays(1), 23)).isEmpty())
    }

    @Test
    fun `тихие часы внутри дня и через полночь`() {
        val night = QuietHours(enabled = true, start = LocalTime.of(23, 0), end = LocalTime.of(7, 0))
        assertTrue(night.contains(LocalTime.of(2, 0)))
        assertTrue(night.contains(LocalTime.of(23, 0)))
        assertTrue(!night.contains(LocalTime.of(7, 0)))
        val lunch = QuietHours(enabled = true, start = LocalTime.of(13, 0), end = LocalTime.of(14, 0))
        assertTrue(lunch.contains(LocalTime.of(13, 30)))
        assertTrue(!lunch.contains(LocalTime.of(12, 0)))
    }
}
