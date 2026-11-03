package dev.chaik.remember.core.sleep

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Событие из истории телефона: экран погас или телефон разблокировали. */
data class PhoneEvent(val at: Instant, val type: Type) {
    enum class Type { SCREEN_OFF, UNLOCK }
}

/** Похоже, спал с [bed] до [wake]. */
data class SleepGuess(val bed: Instant, val wake: Instant)

/** Перерывы, похожие на сон или его части, по времени; [selected] — отмеченные сразу. */
data class SleepOptions(val windows: List<SleepGuess>, val selected: Set<Int>)

/**
 * Сон по истории телефона: самый длинный ночной перерыв, когда телефоном не пользовались.
 * Отбой — экран погас после последнего использования, подъём — первая разблокировка после
 * перерыва. Экран, загоревшийся сам (уведомление), без разблокировки — не использование,
 * поэтому перерыв он не прерывает. Ночью «глянуть время» (до [NIGHT_GLANCE]) посреди сна
 * сон тоже не прерывает: такие перерывы склеиваются в один.
 */
object SleepGuesser {
    /** Короче — не сон, а просто отложенный телефон. */
    val MIN_SLEEP: Duration = Duration.ofHours(3)

    /** Часть сна для склейки: короче — просто отложил телефон. */
    val MIN_PART: Duration = Duration.ofHours(1)

    /** Больше вариантов не показываем: остальные — короткие и только мешают. */
    private const val MAX_OPTIONS = 5

    /** Столько можно посидеть в телефоне ночью, не «просыпаясь» для расчёта. */
    val NIGHT_GLANCE: Duration = Duration.ofMinutes(15)

    /** Склеиваем, только если до этого уже спал столько — иначе вечерняя пауза «отложил на полчаса» стала бы началом сна. */
    private val ASLEEP_BEFORE_GLANCE: Duration = Duration.ofHours(1)

    /** Сон, который закончился утром [date]: ищем с 18:00 накануне до 14:00 этого дня. */
    fun window(date: LocalDate, zone: ZoneId): Pair<Instant, Instant> =
        date.minusDays(1).atTime(EVENING).atZone(zone).toInstant() to date.atTime(AFTERNOON).atZone(zone).toInstant()

    fun guess(events: List<PhoneEvent>, date: LocalDate, zone: ZoneId): SleepGuess? {
        // Лечь позже 6 утра — уже не ночной сон, а дневной
        val latestBed = date.atTime(LATEST_BED).atZone(zone).toInstant()
        return mergeGlances(idleGaps(events, date, zone))
            .filter { it.length >= MIN_SLEEP && !it.bed.isAfter(latestBed) }
            .maxByOrNull { it.length }
    }

    /**
     * Варианты на выбор — каждый перерыв от часа отдельно, без склейки: сон бывает по частям
     * (будильник, посидел, лёг снова), а вечер за компьютером без телефона тоже выглядит перерывом.
     * Отмечен сразу самый длинный ночной перерыв от [MIN_SLEEP], а к нему — соседи через короткий
     * «взгляд на часы», если начались не раньше [EARLIEST_NIGHT]: так вечер к сну сам не приклеится.
     */
    fun options(events: List<PhoneEvent>, date: LocalDate, zone: ZoneId): SleepOptions {
        val latestBed = date.atTime(LATEST_BED).atZone(zone).toInstant()
        val earliestNight = date.minusDays(1).atTime(EARLIEST_NIGHT).atZone(zone).toInstant()
        val windows = idleGaps(events, date, zone)
            .filter { it.length >= MIN_PART }
            .sortedByDescending { it.length }
            .take(MAX_OPTIONS)
            .sortedBy { it.bed }
        val best = windows.indices
            .filter { windows[it].length >= MIN_SLEEP && !windows[it].bed.isAfter(latestBed) }
            .maxByOrNull { windows[it].length }
            ?: return SleepOptions(windows, emptySet())
        var first = best
        var last = best
        fun glance(before: SleepGuess, after: SleepGuess) = Duration.between(before.wake, after.bed) <= NIGHT_GLANCE
        while (first > 0 && glance(windows[first - 1], windows[first]) && !windows[first - 1].bed.isBefore(earliestNight)) first--
        while (last < windows.lastIndex && glance(windows[last], windows[last + 1])) last++
        return SleepOptions(windows, (first..last).toSet())
    }

    /** Перерывы «экран погас после использования → следующая разблокировка». */
    private fun idleGaps(events: List<PhoneEvent>, date: LocalDate, zone: ZoneId): List<SleepGuess> {
        val (from, to) = window(date, zone)
        val gaps = mutableListOf<SleepGuess>()
        var inUse = false
        var idleSince: Instant? = null
        for (event in events.filter { it.at in from..to }.sortedBy { it.at }) {
            when (event.type) {
                PhoneEvent.Type.UNLOCK -> {
                    val start = idleSince
                    if (!inUse && start != null) gaps += SleepGuess(start, event.at)
                    inUse = true
                    idleSince = null
                }
                PhoneEvent.Type.SCREEN_OFF -> if (inUse) {
                    inUse = false
                    idleSince = event.at
                }
            }
        }
        return gaps
    }

    private fun mergeGlances(gaps: List<SleepGuess>): List<SleepGuess> {
        val merged = mutableListOf<SleepGuess>()
        for (gap in gaps) {
            val last = merged.lastOrNull()
            if (last != null && last.length >= ASLEEP_BEFORE_GLANCE && Duration.between(last.wake, gap.bed) <= NIGHT_GLANCE) {
                merged[merged.lastIndex] = SleepGuess(last.bed, gap.wake)
            } else {
                merged += gap
            }
        }
        return merged
    }

    private val SleepGuess.length: Duration get() = Duration.between(bed, wake)

    private val EVENING = LocalTime.of(18, 0)
    private val AFTERNOON = LocalTime.of(14, 0)
    private val LATEST_BED = LocalTime.of(6, 0)

    /** Раньше этого перерыв — вечер, а не начало сна: сам к сну не приклеивается. */
    private val EARLIEST_NIGHT = LocalTime.of(21, 0)
}
