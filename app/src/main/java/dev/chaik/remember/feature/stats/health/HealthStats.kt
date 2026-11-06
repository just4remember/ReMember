package dev.chaik.remember.feature.stats.health

import dev.chaik.remember.core.health.DayActivity
import dev.chaik.remember.core.health.HealthGoals
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToLong

/** Что показывает график по дням. */
enum class HealthMetric { STEPS, MINUTES, CALORIES }

/** Значение показателя за день. */
fun DayActivity.value(metric: HealthMetric): Double = when (metric) {
    HealthMetric.STEPS -> steps.toDouble()
    HealthMetric.MINUTES -> activeMinutes.toDouble()
    HealthMetric.CALORIES -> calories
}

/** Цель по показателю. */
fun HealthGoals.value(metric: HealthMetric): Double = when (metric) {
    HealthMetric.STEPS -> steps.toDouble()
    HealthMetric.MINUTES -> activeMinutes.toDouble()
    HealthMetric.CALORIES -> calories.toDouble()
}

data class HealthSummary(
    /** Дни периода с данными (хоть один шаг или активная минута). */
    val days: Map<LocalDate, DayActivity>,
    val averageSteps: Long,
    val averageMinutes: Long,
    val averageCalories: Long,
    /** В скольких днях выполнена цель по шагам. */
    val stepGoalDays: Int,
    /** В скольких днях закрыты все три кольца. */
    val allGoalsDays: Int,
    /** День с наибольшим числом шагов. */
    val record: Pair<LocalDate, Long>?,
    /** Средние шаги по дням недели (только дни с данными). */
    val byWeekday: Map<DayOfWeek, Long>,
    val weekdaysAverage: Long?,
    val weekendsAverage: Long?,
)

object HealthStats {
    /** Сводка по дням периода; дни без шагов и активности в средние не входят — телефона могло не быть с собой. */
    fun summary(all: Map<LocalDate, DayActivity>, goals: HealthGoals): HealthSummary {
        val days = all.filterValues { it.steps > 0 || it.activeMinutes > 0 }
        val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        fun avg(values: Collection<Number>): Long = if (values.isEmpty()) 0 else values.map { it.toDouble() }.average().roundToLong()
        return HealthSummary(
            days = days,
            averageSteps = avg(days.values.map { it.steps }),
            averageMinutes = avg(days.values.map { it.activeMinutes }),
            averageCalories = avg(days.values.map { it.calories }),
            stepGoalDays = days.values.count { it.steps >= goals.steps },
            allGoalsDays = days.values.count {
                it.steps >= goals.steps && it.activeMinutes >= goals.activeMinutes && it.calories >= goals.calories
            },
            record = days.maxByOrNull { it.value.steps }?.let { it.key to it.value.steps },
            byWeekday = days.entries.groupBy({ it.key.dayOfWeek }, { it.value.steps }).mapValues { (_, v) -> avg(v) },
            weekdaysAverage = days.filterKeys { it.dayOfWeek !in weekend }.values.takeIf { it.isNotEmpty() }?.let { avg(it.map { d -> d.steps }) },
            weekendsAverage = days.filterKeys { it.dayOfWeek in weekend }.values.takeIf { it.isNotEmpty() }?.let { avg(it.map { d -> d.steps }) },
        )
    }

    /**
     * Ступень клетки тепловой карты относительно цели по шагам: 0 — шагов нет,
     * 1 — меньше половины цели, 2 — до цели, 3 — цель выполнена, 4 — полторы цели и больше.
     */
    fun level(steps: Long, goal: Long): Int {
        if (steps <= 0) return 0
        val share = steps.toDouble() / goal.coerceAtLeast(1)
        return when {
            share < 0.5 -> 1
            share < 1.0 -> 2
            share < 1.5 -> 3
            else -> 4
        }
    }
}
