package dev.chaik.remember.feature.stats.gym

import dev.chaik.remember.core.model.Exercise
import dev.chaik.remember.core.model.ExerciseKind
import dev.chaik.remember.core.model.Workout
import dev.chaik.remember.core.model.WorkoutExercise
import dev.chaik.remember.core.model.WorkoutSet
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GymStatsTest {
    private val hammer = Exercise(1, "Хаммер", ExerciseKind.WEIGHT_REPS)
    private val plank = Exercise(2, "Планка", ExerciseKind.TIME)
    private val monday = LocalDate.of(2026, 11, 2)

    private fun workout(
        id: Long,
        date: LocalDate,
        start: String? = "18:00",
        end: String? = "19:30",
        weight: Double? = null,
        vararg entries: Pair<Exercise, List<WorkoutSet>>,
    ) = Workout(
        id = id,
        date = date,
        start = start?.let(LocalTime::parse),
        end = end?.let(LocalTime::parse),
        weightBefore = weight,
        exercises = entries.mapIndexed { i, (e, sets) -> WorkoutExercise(id * 10 + i, e, sets) },
    )

    private fun kg(vararg w: Double) = w.map { WorkoutSet(weight = it, reps = 10) }

    @Test
    fun summaryCountsDurationsAndExercises() {
        val all = listOf(
            workout(1, monday, weight = 66.5, entries = arrayOf(hammer to kg(30.0, 30.0), plank to listOf(WorkoutSet(seconds = 60)))),
            workout(2, monday.plusDays(2), "23:30", "00:30", entries = arrayOf(hammer to kg(32.5))),
            workout(3, monday.plusDays(4), null, null, 66.0),
        )
        val s = GymStats.summary(all, monday..monday.plusDays(6), monday.plusDays(6))
        assertEquals(3, s.workouts.size)
        assertEquals(3.0, s.perWeek, 0.01)
        assertEquals((90 + 60) / 2, s.averageMinutes)
        assertEquals(150, s.totalMinutes)
        assertEquals(listOf(66.5, 66.0), s.weights.map { it.kg })
        assertEquals(hammer, s.exercises.first().exercise)
        assertEquals(2, s.exercises.first().workouts)
        assertEquals(3, s.exercises.first().sets)
        assertEquals(1, s.byWeekday[DayOfWeek.MONDAY])
        assertEquals(1, s.startHours[18])
        assertEquals(1, s.startHours[23])
    }

    @Test
    fun recordsNeedAPreviousBest() {
        val all = listOf(
            workout(1, monday.minusDays(7), entries = arrayOf(hammer to kg(30.0), plank to listOf(WorkoutSet(seconds = 60)))),
            workout(2, monday, entries = arrayOf(hammer to kg(30.0), plank to listOf(WorkoutSet(seconds = 50)))),
            workout(3, monday.plusDays(3), entries = arrayOf(hammer to kg(32.5, 35.0))),
            workout(4, monday.plusDays(5), entries = arrayOf(Exercise(3, "Новое", ExerciseKind.REPS) to listOf(WorkoutSet(reps = 20)))),
        )
        val records = GymStats.records(all, monday..monday.plusDays(6))
        assertEquals(1, records.size)
        assertEquals(30.0, records[0].before, 0.0)
        assertEquals(35.0, records[0].best, 0.0)
        assertEquals(monday.plusDays(3), records[0].date)
    }

    @Test
    fun countsByWeekOrMonthAndLevels() {
        val all = listOf(workout(1, monday), workout(2, monday.plusDays(1)), workout(3, monday.plusDays(9)))
        val weekly = GymStats.counts(all, monday..monday.plusDays(20), DayOfWeek.MONDAY)
        assertEquals(listOf(2, 1, 0), weekly.map { it.count })
        val monthly = GymStats.counts(all, LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 12, 31), DayOfWeek.MONDAY)
        assertEquals(12, monthly.size)
        assertEquals(3, monthly[10].count)
        assertEquals(1, GymStats.level(60, 90))
        assertEquals(2, GymStats.level(90, 90))
        assertEquals(3, GymStats.level(120, 90))
        assertEquals(2, GymStats.level(null, 90))
        assertNull(GymStats.minutes(workout(9, monday, null, "10:00")))
        assertTrue(GymStats.history(all, hammer.id).isEmpty())
    }
}
