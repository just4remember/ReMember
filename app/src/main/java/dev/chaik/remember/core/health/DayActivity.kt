package dev.chaik.remember.core.health

import java.time.Duration
import java.time.Instant

/** Активность за день: шаги, активные минуты, активные калории. */
data class DayActivity(
    val steps: Long = 0,
    val activeMinutes: Int = 0,
    val calories: Double = 0.0,
)

/** Вес для оценки калорий, пока он не записан в тренировках. */
const val DEFAULT_WEIGHT_KG = 70.0

/** Цели на день; меняются в «⋮ → Цели». */
data class HealthGoals(
    val steps: Long = 6000,
    val activeMinutes: Int = 90,
    val calories: Int = 500,
)

/** Отрезок времени: запись шагов или тренировка. */
data class TimeSpan(val start: Instant, val end: Instant)

/** Запись шагов из Health Connect: столько-то шагов за отрезок. */
data class StepsSpan(val span: TimeSpan, val count: Long)

/**
 * Активное время. Samsung Health не отдаёт своё «активное время» в Health Connect,
 * поэтому считаем сами: минуты бодрой ходьбы (темп не ниже [MIN_CADENCE] шагов в минуту)
 * плюс тренировки. Пересечения считаются один раз — каждая минута суток либо активна, либо нет.
 */
object ActivityMath {
    /**
     * Шагов в минуту — ниже этого считаем, что человек просто перемещается по дому.
     * Подобрано по Samsung Health: при 45 наши минуты совпали с его «активным временем».
     */
    const val MIN_CADENCE = 45

    /** Шаг взрослого, км. */
    private const val STRIDE_KM = 0.00072

    /** Ходьба сверх покоя, ккал на килограмм веса на километр; подобрано по Samsung Health. */
    private const val WALK_KCAL_PER_KG_KM = 0.71

    /** Силовая тренировка — около 5 MET, сверх покоя остаётся 4. */
    private const val WORKOUT_NET_MET = 4.0

    fun activeMinutes(steps: List<StepsSpan>, sessions: List<TimeSpan>, dayStart: Instant, dayEnd: Instant): Int =
        activeTime(steps, sessions, dayStart, dayEnd).total

    /** Активные минуты: всего и сколько из них — тренировка без бодрой ходьбы (для калорий). */
    fun activeTime(steps: List<StepsSpan>, sessions: List<TimeSpan>, dayStart: Instant, dayEnd: Instant): ActiveTime {
        val walking = HashSet<Long>()
        val workout = HashSet<Long>()
        fun minutesOf(span: TimeSpan): LongRange {
            val from = maxOf(span.start, dayStart).epochSecond / 60
            val to = minOf(span.end, dayEnd).epochSecond / 60
            return from until to
        }
        for (s in steps) {
            // Телефон пишет шаги кусками от нескольких секунд до нескольких минут — темп по секундам
            val seconds = Duration.between(s.span.start, s.span.end).seconds
            val cadence = s.count * 60.0 / seconds.coerceAtLeast(1)
            if (cadence < MIN_CADENCE) continue
            // Кусок короче минуты — одна минута, та, в которой он начался
            if (seconds < 60) {
                if (!s.span.start.isBefore(dayStart) && s.span.start.isBefore(dayEnd)) walking += s.span.start.epochSecond / 60
            } else {
                walking += minutesOf(s.span)
            }
        }
        sessions.forEach { workout += minutesOf(it) }
        return ActiveTime(total = (walking + workout).size, workoutOnly = (workout - walking).size)
    }

    /**
     * Активные калории, когда Health Connect их не даёт (ни телефон, ни Samsung Health их не пишут):
     * ходьба по шагам и весу плюс тренировки без ходьбы по времени и весу.
     */
    fun estimateCalories(steps: Long, workoutOnlyMinutes: Int, weightKg: Double): Double =
        steps * STRIDE_KM * WALK_KCAL_PER_KG_KM * weightKg + workoutOnlyMinutes / 60.0 * WORKOUT_NET_MET * weightKg
}

data class ActiveTime(val total: Int, val workoutOnly: Int)
