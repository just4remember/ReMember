package dev.chaik.remember.core.alarm

import dev.chaik.remember.core.wordgame.WordGameState
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

/** Будильник. Хранится в настройках (см. [TimeStore]), а не в базе: нужен сразу после перезагрузки. */
@Serializable
data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    /** Дни недели (1 — понедельник … 7 — воскресенье); пусто — сработает один раз. */
    val days: Set<Int> = emptySet(),
    val label: String = "",
    val enabled: Boolean = true,
    /** Мелодия (content://…); null — мелодия будильника телефона. */
    val ringtone: String? = null,
    /** Название мелодии — показать в настройках будильника. */
    val ringtoneTitle: String? = null,
    val vibrate: Boolean = true,
    /** Громкость нарастает за [GRADUAL_SECONDS] секунд. */
    val gradual: Boolean = true,
    /** Можно отложить на [snoozeMinutes] минут. */
    val snooze: Boolean = false,
    val snoozeMinutes: Int = 10,
    /** Усиленный: выключается только игрой. */
    val hard: Boolean = false,
    /** Усиленный: через сколько минут бездействия в игре звонок повторится. */
    val againMinutes: Int = 5,
    /** На когда поставлен (мс с 1970): после выключения телефона видно, что звонок пропущен. */
    val ringAt: Long? = null,
    /** До какого момента отложен (мс с 1970): после перезагрузки отложенный звонок ставится заново. */
    val snoozeAt: Long? = null,
) {
    val time: LocalTime get() = LocalTime.of(hour, minute)
    val weekDays: Set<DayOfWeek> get() = days.map(DayOfWeek::of).toSet()

    companion object {
        const val GRADUAL_SECONDS = 30
        val SnoozeOptions = listOf(5, 10, 15, 20, 30)
        val AgainOptions = listOf(1, 2, 3, 5, 10, 15)
    }
}

/** Когда будильник сработает в следующий раз после [now]. */
fun Alarm.nextRing(now: ZonedDateTime): ZonedDateTime {
    val today = now.toLocalDate()
    for (offset in 0L..7L) {
        val date = today.plusDays(offset)
        if (days.isNotEmpty() && date.dayOfWeek.value !in days) continue
        val at = date.atTime(time).atZone(now.zone)
        if (at.isAfter(now)) return at
    }
    // Сюда не попасть: за 8 дней подходящий день недели найдётся всегда
    return today.plusDays(1).atTime(time).atZone(now.zone)
}

/** Что делать с будильником, срок которого прошёл, пока телефон был выключен. */
enum class MissedRing { NONE, RING_NOW, TOO_LATE }

/**
 * Пропущен ли звонок к моменту [now]. [margin] — запас: при обычном запуске приложения системный
 * будильник может как раз сработать сам. Опоздание больше [MISSED_GRACE_MS] — звонить уже незачем.
 */
fun Alarm.missedRing(now: Long, margin: Long): MissedRing {
    val at = ringAt?.takeIf { enabled } ?: return MissedRing.NONE
    return when {
        now - at <= margin || now <= at -> MissedRing.NONE
        now - at <= MISSED_GRACE_MS -> MissedRing.RING_NOW
        else -> MissedRing.TOO_LATE
    }
}

const val MISSED_GRACE_MS = 2 * 60 * 60_000L

/** Что звонит. */
enum class RingKind { ALARM, TIMER }

/**
 * Идущий звонок — или игра усиленного будильника, которая ещё не пройдена.
 * Хранится, чтобы пережить закрытие приложения: повторный звонок продолжает ту же игру.
 */
@Serializable
data class RingSession(
    val kind: RingKind,
    /** Будильник на момент звонка (для таймера — null). */
    val alarm: Alarm? = null,
    /** Звонит сейчас; false — звонок выключен, идёт игра. */
    val ringing: Boolean = true,
    /** Звонок повторный — после бездействия в игре: «отложить» уже нельзя. */
    val again: Boolean = false,
    val game: WordGameState? = null,
    /** Когда будильник зазвонит снова, если в игре ничего не делать (мс с 1970). */
    val againAt: Long? = null,
    /** Когда начался звонок (мс с 1970). */
    val startedAt: Long = 0,
) {
    val hard: Boolean get() = kind == RingKind.ALARM && alarm?.hard == true
    val canSnooze: Boolean get() = kind == RingKind.ALARM && alarm?.snooze == true && !again && game == null
}

/** Таймер: идёт — [endsAt], на паузе — [leftMs], иначе стоит на [durationMs]. */
@Serializable
data class TimerState(
    val durationMs: Long = 5 * 60_000L,
    /** Когда закончится (мс с 1970), если идёт. */
    val endsAt: Long? = null,
    /** Сколько осталось, если на паузе. */
    val leftMs: Long? = null,
) {
    val running: Boolean get() = endsAt != null
    val paused: Boolean get() = endsAt == null && leftMs != null
    val idle: Boolean get() = endsAt == null && leftMs == null

    fun left(now: Long): Long = when {
        endsAt != null -> (endsAt - now).coerceAtLeast(0)
        leftMs != null -> leftMs
        else -> durationMs
    }
}

/** Секундомер: идёт с [startedAt] (мс с 1970) плюс уже набежавшее [accumulatedMs]. */
@Serializable
data class StopwatchState(
    val startedAt: Long? = null,
    val accumulatedMs: Long = 0,
    /** Отсечки — общее время на момент каждой. */
    val laps: List<Long> = emptyList(),
) {
    val running: Boolean get() = startedAt != null
    val idle: Boolean get() = startedAt == null && accumulatedMs == 0L

    fun elapsed(now: Long): Long = accumulatedMs + (startedAt?.let { (now - it).coerceAtLeast(0) } ?: 0)
}
