package dev.chaik.remember.feature.stats.gym

import dev.chaik.remember.core.model.Exercise
import dev.chaik.remember.core.model.ExerciseKind
import dev.chaik.remember.core.model.ExerciseSession
import dev.chaik.remember.core.model.Workout
import dev.chaik.remember.core.model.WorkoutSet
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Сколько тренировок в шаг графика: неделю или месяц. */
data class CountPoint(val start: LocalDate, val end: LocalDate, val count: Int)

/** Вес тела в тренировку. */
data class WeightPoint(val date: LocalDate, val kg: Double)

/** Упражнение за период: в скольких тренировках было и сколько подходов. */
data class ExerciseUse(val exercise: Exercise, val workouts: Int, val sets: Int)

/** Рекорд: в периоде упражнение превзошло лучший результат до периода. */
data class GymRecord(val exercise: Exercise, val before: Double, val best: Double, val date: LocalDate)

data class GymSummary(
    val workouts: List<Workout>,
    /** Тренировок в неделю в среднем за прошедшую часть периода. */
    val perWeek: Double,
    /** Средняя длительность, минут; null — ни у одной нет времени начала и конца. */
    val averageMinutes: Int?,
    val totalMinutes: Int,
    val weights: List<WeightPoint>,
    val exercises: List<ExerciseUse>,
    val records: List<GymRecord>,
    val byWeekday: Map<DayOfWeek, Int>,
    /** Сколько тренировок начиналось в каждый час суток. */
    val startHours: IntArray,
)

object GymStats {
    /** Длительность тренировки в минутах; через полночь — тоже; null — нет начала или конца. */
    fun minutes(w: Workout): Int? {
        val start = w.start ?: return null
        val end = w.end ?: return null
        var m = Duration.between(start, end).toMinutes()
        if (m < 0) m += 24 * 60
        return m.toInt().takeIf { it > 0 }
    }

    fun summary(all: List<Workout>, range: ClosedRange<LocalDate>, today: LocalDate): GymSummary {
        val workouts = all.filter { it.date in range }
        val durations = workouts.mapNotNull(::minutes)
        val elapsed = (ChronoUnit.DAYS.between(range.start, minOf(range.endInclusive, today)) + 1).coerceAtLeast(1)
        val hours = IntArray(24)
        workouts.forEach { w -> w.start?.let { hours[it.hour]++ } }
        return GymSummary(
            workouts = workouts,
            perWeek = workouts.size / (elapsed / 7.0).coerceAtLeast(1.0),
            averageMinutes = durations.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            totalMinutes = durations.sum(),
            weights = workouts.mapNotNull { w -> (w.weightBefore ?: w.weightAfter)?.let { WeightPoint(w.date, it) } },
            exercises = workouts.flatMap { w -> w.exercises.map { w.id to it } }
                .groupBy { it.second.exercise.id }
                .map { (_, list) ->
                    ExerciseUse(list.first().second.exercise, list.map { it.first }.distinct().size, list.sumOf { it.second.sets.size })
                }
                .sortedWith(compareByDescending<ExerciseUse> { it.workouts }.thenByDescending { it.sets }.thenBy { it.exercise.name.lowercase() }),
            records = records(all, range),
            byWeekday = workouts.groupingBy { it.date.dayOfWeek }.eachCount(),
            startHours = hours,
        )
    }

    /** Неделя/месяц/3 месяца — по неделям, длиннее — по месяцам; шаги без тренировок — с нулём. */
    fun counts(workouts: List<Workout>, range: ClosedRange<LocalDate>, firstDayOfWeek: DayOfWeek): List<CountPoint> {
        val days = ChronoUnit.DAYS.between(range.start, range.endInclusive) + 1
        val weekly = days <= 92
        fun bucket(d: LocalDate) = if (weekly) d.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)) else d.withDayOfMonth(1)
        fun next(d: LocalDate) = if (weekly) d.plusWeeks(1) else d.plusMonths(1)
        val grouped = workouts.filter { it.date in range }.groupingBy { bucket(it.date) }.eachCount()
        val points = ArrayList<CountPoint>()
        var start = bucket(range.start)
        while (!start.isAfter(range.endInclusive)) {
            points += CountPoint(maxOf(start, range.start), minOf(next(start).minusDays(1), range.endInclusive), grouped[start] ?: 0)
            start = next(start)
        }
        return points
    }

    /** Лучший результат подхода: вес, повторы, время, скорость или дистанция — как записывается упражнение. */
    fun metric(set: WorkoutSet, kind: ExerciseKind): Double? = when (kind) {
        ExerciseKind.WEIGHT_REPS, ExerciseKind.WEIGHT_TIME -> set.weight
        ExerciseKind.REPS -> set.reps?.toDouble()
        ExerciseKind.TIME -> set.seconds?.toDouble()
        ExerciseKind.SPEED_TIME -> set.speed
        ExerciseKind.DISTANCE_TIME, ExerciseKind.DISTANCE -> set.distance
    }

    /**
     * Рекорды периода: упражнения, где лучший результат в периоде выше лучшего до него.
     * Упражнения, которых до периода не было, — не рекорды: сравнивать не с чем.
     */
    fun records(all: List<Workout>, range: ClosedRange<LocalDate>): List<GymRecord> {
        val entries = all.flatMap { w -> w.exercises.map { w.date to it } }.groupBy { it.second.exercise.id }
        return entries.mapNotNull { (_, list) ->
            val exercise = list.first().second.exercise
            fun best(items: List<Pair<LocalDate, dev.chaik.remember.core.model.WorkoutExercise>>) =
                items.flatMap { (date, e) -> e.sets.mapNotNull { s -> metric(s, exercise.kind)?.let { date to it } } }
                    .maxWithOrNull(compareBy({ it.second }, { it.first }))
            val before = best(list.filter { it.first < range.start }) ?: return@mapNotNull null
            val inside = list.filter { it.first in range }
            // Дата рекорда — первая тренировка периода, где побит прошлый лучший
            val record = best(inside)?.takeIf { it.second > before.second } ?: return@mapNotNull null
            val firstBeat = inside.sortedBy { it.first }.firstOrNull { (_, e) ->
                e.sets.any { s -> (metric(s, exercise.kind) ?: 0.0) > before.second }
            }?.first ?: record.first
            GymRecord(exercise, before.second, record.second, firstBeat)
        }.sortedByDescending { it.date }
    }

    /** Подходы упражнения по всем тренировкам — для графика прогресса, как в зале. */
    fun history(all: List<Workout>, exerciseId: Long): List<ExerciseSession> =
        all.mapNotNull { w ->
            w.exercises.filter { it.exercise.id == exerciseId }.flatMap { it.sets }.takeIf { it.isNotEmpty() }
                ?.let { ExerciseSession(w.id, w.date, w.start, it) }
        }

    /**
     * Ступень клетки тепловой карты: 1 — короче обычного (меньше 80% средней длительности),
     * 2 — обычная (или длительность неизвестна), 3 — долгая (больше 120%).
     */
    fun level(minutes: Int?, average: Int?): Int {
        if (minutes == null || average == null || average <= 0) return 2
        val share = minutes.toDouble() / average
        return when {
            share < 0.8 -> 1
            share > 1.2 -> 3
            else -> 2
        }
    }
}
