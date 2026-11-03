package dev.chaik.remember.feature.today.sleep

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.chaik.remember.core.editor.EditorState
import dev.chaik.remember.core.data.SleepRepository
import dev.chaik.remember.core.model.SleepEntry
import dev.chaik.remember.core.sleep.PhoneUsageHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Режим «Сон»: когда лёг, когда встал, качество и что приснилось.
 * Запись привязана к дню пробуждения: «лёг в 23:30» — это вечер предыдущего дня.
 */
class SleepController(
    private val scope: CoroutineScope,
    private val repository: SleepRepository,
    private val usage: PhoneUsageHistory,
    /** Включена ли подсказка времени сна (Настройки → Страницы). */
    private val guessEnabled: () -> Boolean,
    /** Заполнена запись дня или нет — чтобы утром больше не открывать «Сон» сам (см. [TodaySession]). */
    private val onFilledChange: (LocalDate, Boolean) -> Unit = { _, _ -> },
) {
    var bedTime by mutableStateOf<LocalTime?>(null)
    var wakeTime by mutableStateOf<LocalTime?>(null)

    /** В половинках звезды из 5. */
    var quality by mutableStateOf<Int?>(null)

    /** Что приснилось — тот же редактор, что у записи дня, только без фото. */
    val dream = EditorState()

    var isLoaded by mutableStateOf(false)
        private set

    /** Подсказка времени сна по истории телефона для открытого дня. */
    var guess by mutableStateOf<SleepGuessState>(SleepGuessState.Off)
        private set

    /** Сколько спал; null — не указано время. */
    val duration: Duration?
        get() {
            val date = date ?: return null
            val bed = bedInstant(date, bedTime, wakeTime) ?: return null
            val wake = wakeInstant(date, wakeTime) ?: return null
            return Duration.between(bed, wake).takeIf { !it.isNegative }
        }

    private var date: LocalDate? = null
    private var exists = false
    private var saved: SleepEntry? = null
    private var autosaveJob: Job? = null
    private val lock = Mutex()
    private val zone get() = ZoneId.systemDefault()

    init {
        // Иначе следующая правка на этом экране затёрла бы сон, записанный из будильника
        scope.launch {
            repository.dreamAdded.collect { added -> if (added == date) load(added) }
        }
    }

    suspend fun load(date: LocalDate) {
        autosaveJob?.cancel()
        flushNow()
        lock.withLock {
            isLoaded = false
            val entry = repository.observe(date).first()
            this.date = date
            exists = entry != null
            bedTime = entry?.bedTime?.atZone(zone)?.toLocalTime()
            wakeTime = entry?.wakeTime?.atZone(zone)?.toLocalTime()
            quality = entry?.quality
            dream.load(entry?.dream.orEmpty())
            saved = snapshot(date)
            onFilledChange(date, entry != null)
            isLoaded = true
        }
        autosaveJob = scope.launch { autosave() }
        refreshGuess()
    }

    /**
     * Пересчитать подсказку: при открытии дня и при возвращении в приложение (могли дать доступ).
     * За сегодня перерывы меняются, пока телефоном пользуются, — поэтому отмеченное ищется заново
     * по самим перерывам, а не по их номерам: сначала то, что отмечено сейчас, иначе то,
     * что уже записано сном, и только потом — предложенное.
     */
    fun refreshGuess() {
        val date = date ?: return
        scope.launch {
            val before = (guess as? SleepGuessState.Found)?.let { found -> found.selected.map { found.windows[it] } }.orEmpty()
            guess = when {
                !guessEnabled() -> SleepGuessState.Off
                !usage.hasAccess() -> SleepGuessState.NoAccess
                else -> usage.guessSleep(date, zone)?.takeIf { it.windows.isNotEmpty() }?.let { found ->
                    val windows = found.windows.map { window ->
                        SleepWindow(
                            // До минуты: секунды в поле времени всё равно не видны
                            bed = window.bed.atZone(zone).toLocalTime().withSecond(0).withNano(0),
                            wake = window.wake.atZone(zone).toLocalTime().withSecond(0).withNano(0),
                            length = Duration.between(window.bed, window.wake),
                            bedAt = window.bed,
                            wakeAt = window.wake,
                        )
                    }
                    val kept = windows.indices.filter { i -> before.any { it.bedAt == windows[i].bedAt && it.wakeAt == windows[i].wakeAt } }
                    val recorded = recordedSelection(date, windows)
                    SleepGuessState.Found(
                        windows = windows,
                        selected = kept.toSet().ifEmpty { recorded }.ifEmpty { found.selected },
                    )
                } ?: SleepGuessState.NotFound
            }
        }
    }

    /** Перерывы, из которых, похоже, и собран уже записанный сон. */
    private fun recordedSelection(date: LocalDate, windows: List<SleepWindow>): Set<Int> {
        val bed = bedInstant(date, bedTime, wakeTime) ?: return emptySet()
        val wake = wakeInstant(date, wakeTime) ?: return emptySet()
        // В поле время до минуты, а перерыв — до секунды
        val slack = Duration.ofMinutes(1)
        return windows.indices.filter { i ->
            !windows[i].bedAt.isBefore(bed.minus(slack)) && !windows[i].wakeAt.isAfter(wake.plus(slack))
        }.toSet()
    }

    /** Отметить или снять перерыв: отмеченные склеиваются в один сон. */
    fun toggleGuess(index: Int) {
        val found = guess as? SleepGuessState.Found ?: return
        guess = found.copy(selected = if (index in found.selected) found.selected - index else found.selected + index)
    }

    /** Подставить в поля отмеченные перерывы: от первого отбоя до последнего подъёма. */
    fun applyGuess() {
        val found = guess as? SleepGuessState.Found ?: return
        val (bed, wake) = found.merged ?: return
        bedTime = bed
        wakeTime = wake
    }

    @OptIn(FlowPreview::class)
    private suspend fun autosave() {
        snapshotFlow { date?.let(::snapshot) }
            .debounce(AUTOSAVE_DELAY_MS)
            .collect { it?.let { save(it) } }
    }

    suspend fun flushNow() {
        val date = date ?: return
        if (isLoaded) save(snapshot(date))
    }

    private fun snapshot(date: LocalDate) = SleepEntry(
        date = date,
        bedTime = bedInstant(date, bedTime, wakeTime),
        wakeTime = wakeInstant(date, wakeTime),
        quality = quality,
        dream = dream.markdown,
    )

    private suspend fun save(entry: SleepEntry) = lock.withLock {
        if (!isLoaded || entry == saved || entry.date != date) return@withLock
        val empty = entry.bedTime == null && entry.wakeTime == null && entry.quality == null && entry.dream.isBlank()
        onFilledChange(entry.date, !empty)
        when {
            !empty -> {
                repository.save(entry)
                exists = true
            }
            exists -> {
                repository.delete(entry.date)
                exists = false
            }
        }
        saved = entry
    }

    private fun wakeInstant(date: LocalDate, wake: LocalTime?): Instant? =
        wake?.let { date.atTime(it).atZone(zone).toInstant() }

    /**
     * Время отхода ко сну. Если оно «позже» подъёма (лёг 23:30, встал 7:30) или
     * подъём не указан, а лёг после полудня — это вечер предыдущего дня.
     */
    private fun bedInstant(date: LocalDate, bed: LocalTime?, wake: LocalTime?): Instant? {
        bed ?: return null
        val previousEvening = if (wake != null) bed > wake else bed >= LocalTime.NOON
        val day = if (previousEvening) date.minusDays(1) else date
        return day.atTime(bed).atZone(zone).toInstant()
    }

    private companion object {
        const val AUTOSAVE_DELAY_MS = 700L
    }
}

/** Что с подсказкой времени сна. */
sealed interface SleepGuessState {
    /** Выключена в настройках. */
    data object Off : SleepGuessState

    /** Нет доступа к истории использования — предлагаем дать. */
    data object NoAccess : SleepGuessState

    /** Ночью не нашлось перерыва, похожего на сон. */
    data object NotFound : SleepGuessState

    /** Перерывы по времени; [selected] — отмеченные (сначала — самый правдоподобный). */
    data class Found(val windows: List<SleepWindow>, val selected: Set<Int>) : SleepGuessState {
        /** Отмеченное одним сном: перерывы идут по времени, поэтому от первого до последнего. */
        val merged: Pair<LocalTime, LocalTime>?
            get() = if (selected.isEmpty()) null else windows[selected.min()].bed to windows[selected.max()].wake
    }
}

/** Перерыв в использовании телефона, похожий на сон или его часть: [bed]/[wake] — для показа, [bedAt]/[wakeAt] — точно. */
data class SleepWindow(val bed: LocalTime, val wake: LocalTime, val length: Duration, val bedAt: Instant, val wakeAt: Instant)
