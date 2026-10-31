package dev.chaik.remember.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ExerciseHistoryTest {
    private val day = LocalDate.of(2026, 10, 1)

    private fun session(id: Long, vararg sets: Pair<Double, Int>) = ExerciseSession(
        workoutId = id,
        date = day.plusDays(id),
        start = null,
        sets = sets.mapIndexed { i, (kg, reps) -> WorkoutSet(id = id * 10 + i, weight = kg, reps = reps) },
    )

    @Test
    fun `подсказка берётся из того же подхода последней тренировки`() {
        val hint = ExerciseHistory.hint(listOf(session(1, 50.0 to 10), session(2, 60.0 to 10, 55.0 to 8)), 1)!!
        assertEquals(55.0, hint.set.weight)
        assertEquals(8, hint.set.reps)
        assertEquals(day.plusDays(2), hint.date)
    }

    @Test
    fun `результат держится, пока подход не менялся`() {
        val history = listOf(session(1, 60.0 to 10), session(2, 50.0 to 10), session(3, 60.0 to 10), session(4, 60.0 to 10))
        assertEquals(2, ExerciseHistory.hint(history, 0)!!.heldFor)
    }

    @Test
    fun `другие повторы и нехватка подхода прерывают серию`() {
        assertEquals(1, ExerciseHistory.hint(listOf(session(1, 60.0 to 8), session(2, 60.0 to 10)), 0)!!.heldFor)
        val shorter = listOf(session(1, 60.0 to 10), session(2, 70.0 to 5, 60.0 to 10))
        assertEquals(1, ExerciseHistory.hint(shorter, 1)!!.heldFor)
    }

    @Test
    fun `подхода не было в прошлый раз или истории нет`() {
        assertNull(ExerciseHistory.hint(listOf(session(1, 60.0 to 10)), 1))
        assertNull(ExerciseHistory.hint(emptyList(), 0))
    }

    @Test
    fun `в прошлое попадают только более ранние тренировки`() {
        val history = listOf(session(1, 50.0 to 10), session(2, 55.0 to 10), session(3, 60.0 to 10))
        val workout = Workout(id = 2, date = day.plusDays(2))
        assertEquals(listOf(1L), ExerciseHistory.before(history, workout).map { it.workoutId })
    }

    @Test
    fun `в один день порядок решает время начала`() {
        val morning = ExerciseSession(5, day, LocalTime.of(9, 0), emptyList())
        val evening = Workout(id = 1, date = day, start = LocalTime.of(18, 0))
        assertEquals(listOf(5L), ExerciseHistory.before(listOf(morning), evening).map { it.workoutId })
    }

    @Test
    fun `на графике лучший вес каждой тренировки`() {
        val points = ExerciseHistory.progress(listOf(session(1, 50.0 to 10, 60.0 to 5), session(2)), ExerciseKind.WEIGHT_REPS)
        assertEquals(listOf(day.plusDays(1) to 60.0), points.map { it.date to it.value })
        assertEquals(2, points.single().sets.size)
    }

    @Test
    fun `срок графика считается от сегодня, граница входит`() {
        val today = LocalDate.of(2026, 11, 1)
        val points = listOf(
            ProgressPoint(LocalDate.of(2025, 1, 1), 40.0),
            ProgressPoint(LocalDate.of(2026, 8, 1), 50.0),
            ProgressPoint(LocalDate.of(2026, 10, 1), 60.0),
        )
        fun values(range: ChartRange) = ExerciseHistory.within(points, range, today).map { it.value }
        assertEquals(listOf(60.0), values(ChartRange.MONTH))
        assertEquals(listOf(50.0, 60.0), values(ChartRange.QUARTER))
        assertEquals(listOf(50.0, 60.0), values(ChartRange.YEAR))
        assertEquals(listOf(40.0, 50.0, 60.0), values(ChartRange.ALL))
    }
}
