package dev.chaik.remember.core.model

import java.time.LocalDate
import java.time.LocalTime

/** Подходы одного упражнения в одной тренировке. */
data class ExerciseSession(
    val workoutId: Long,
    val date: LocalDate,
    val start: LocalTime?,
    val sets: List<WorkoutSet>,
)

/** Подсказка к подходу: как он прошёл в прошлый раз и сколько тренировок подряд результат не меняется. */
data class SetHint(val set: WorkoutSet, val date: LocalDate, val heldFor: Int)

/** Точка графика: лучший результат упражнения в тренировке и все её подходы. */
data class ProgressPoint(val date: LocalDate, val value: Double, val sets: List<WorkoutSet> = emptyList())

/** За какой срок показывать график; null — за всё время. */
enum class ChartRange(val months: Long?) {
    MONTH(1),
    QUARTER(3),
    YEAR(12),
    ALL(null),
}

object ExerciseHistory {
    /** Тренировки раньше [workout] по той же хронологии, что и номера: дата, время начала, id. */
    fun before(history: List<ExerciseSession>, workout: Workout): List<ExerciseSession> =
        history.filter { order.compare(it, ExerciseSession(workout.id, workout.date, workout.start, emptyList())) < 0 }

    /**
     * Подсказка к подходу номер [index] (с нуля) по прошлым тренировкам [previous], от старых к новым.
     * null — в прошлый раз такого подхода не было.
     */
    fun hint(previous: List<ExerciseSession>, index: Int): SetHint? {
        val last = previous.lastOrNull() ?: return null
        val set = last.sets.getOrNull(index)?.copy(id = 0) ?: return null
        val heldFor = previous.asReversed().takeWhile { it.sets.getOrNull(index)?.copy(id = 0) == set }.size
        return SetHint(set, last.date, heldFor)
    }

    /** Лучший результат каждой тренировки: вес, повторы, время или скорость — смотря как записывается упражнение. */
    fun progress(history: List<ExerciseSession>, kind: ExerciseKind): List<ProgressPoint> =
        history.mapNotNull { session ->
            session.sets.mapNotNull { it.metric(kind) }.maxOrNull()?.let { ProgressPoint(session.date, it, session.sets) }
        }

    /** Точки за выбранный срок, считая от [today]. */
    fun within(points: List<ProgressPoint>, range: ChartRange, today: LocalDate): List<ProgressPoint> {
        val since = today.minusMonths(range.months ?: return points)
        return points.filter { it.date >= since }
    }

    private fun WorkoutSet.metric(kind: ExerciseKind): Double? = when (kind) {
        ExerciseKind.WEIGHT_REPS -> weight
        ExerciseKind.REPS -> reps?.toDouble()
        ExerciseKind.TIME -> seconds?.toDouble()
        ExerciseKind.SPEED_TIME -> speed
    }

    private val order = compareBy<ExerciseSession>({ it.date }, { it.start?.toSecondOfDay() ?: -1 }, { it.workoutId })
}
