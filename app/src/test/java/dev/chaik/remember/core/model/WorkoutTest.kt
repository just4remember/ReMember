package dev.chaik.remember.core.model

import dev.chaik.remember.core.reminders.ActiveWorkout
import dev.chaik.remember.core.reminders.DailyReminder
import dev.chaik.remember.core.reminders.Reminder
import dev.chaik.remember.core.reminders.ReminderPlanner
import dev.chaik.remember.core.reminders.ReminderSettings
import dev.chaik.remember.feature.gym.workoutDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

class WorkoutTest {
    @Before
    fun russian() = Locale.setDefault(Locale.forLanguageTag("ru-RU"))

    @Test
    fun `подходы записываются как в блокноте`() {
        assertEquals("40 × 12", WorkoutSet(weight = 40.0, reps = 12).format(ExerciseKind.WEIGHT_REPS))
        assertEquals("17,5 × 10", WorkoutSet(weight = 17.5, reps = 10).format(ExerciseKind.WEIGHT_REPS))
        assertEquals("— × 12", WorkoutSet(reps = 12).format(ExerciseKind.WEIGHT_REPS))
        assertEquals("30", WorkoutSet(reps = 30).format(ExerciseKind.REPS))
        assertEquals("1:30", WorkoutSet(seconds = 90).format(ExerciseKind.TIME))
    }

    @Test
    fun `вес и время разбираются из ввода`() {
        assertEquals(17.5, parseKg("17,5")!!, 0.0)
        assertEquals(65.7, parseKg("65.7")!!, 0.0)
        assertNull(parseKg("abc"))
        assertEquals(90, parseDuration("1:30"))
        assertEquals(90, parseDuration("90"))
        assertEquals(3725, parseDuration("1:02:05"))
        assertNull(parseDuration("1::30"))
        assertNull(parseDuration("0"))
        assertEquals("1:02:05", formatDuration(3725))
    }

    @Test
    fun `длительность — и через полночь`() {
        assertEquals(Duration.ofMinutes(90), workoutDuration(LocalTime.of(17, 0), LocalTime.of(18, 30)))
        assertEquals(Duration.ofMinutes(60), workoutDuration(LocalTime.of(23, 30), LocalTime.of(0, 30)))
        assertNull(workoutDuration(LocalTime.of(17, 0), null))
    }

    @Test
    fun `через два часа после начала — напоминание закончить`() {
        val day = LocalDate.of(2026, 10, 23)
        val settings = ReminderSettings(
            dayReport = DailyReminder(false, LocalTime.of(21, 0)),
            events = false,
            tasks = false,
        )
        val active = ActiveWorkout(7, day.atTime(17, 0))
        val result = ReminderPlanner.plan(day.atTime(17, 0), day.atTime(23, 0), settings, emptyList(), emptyList(), active)
        assertEquals(listOf(day.atTime(19, 0) to Reminder.WorkoutRunning(7)), result.map { it.at to it.reminder })
    }
}
