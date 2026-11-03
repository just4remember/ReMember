package dev.chaik.remember.feature.today

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chaik.remember.app.AppContainer
import dev.chaik.remember.feature.today.day.DayController
import dev.chaik.remember.feature.today.money.MoneyController
import dev.chaik.remember.feature.today.sleep.SleepController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import dev.chaik.remember.core.model.Tag
import dev.chaik.remember.core.data.DayStreak
import dev.chaik.remember.core.settings.logicalToday
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/**
 * Страница «Сегодня»: выбранный день и режим (день / сон / траты).
 * Каждый режим — свой контроллер со своим сохранением.
 */
class TodayViewModel(private val container: AppContainer) : ViewModel() {

    /** Какой день сейчас с учётом времени смены дня (Профиль). */
    var currentDay by mutableStateOf(container.settings.settings.value.logicalToday())
        private set

    var date by mutableStateOf(currentDay)
        private set

    /** Режим хранится в приложении ([TodaySession]) — переживает пересоздание вкладки. */
    val mode: TodayMode get() = container.todaySession.mode

    val day = DayController(viewModelScope, container.dayRepository, container.tagRepository, container.photoStorage)
    val sleep = SleepController(
        scope = viewModelScope,
        repository = container.sleepRepository,
        usage = container.phoneUsage,
        guessEnabled = { container.settings.settings.value.sleepGuess },
        onFilledChange = container.sleepFilled::set,
    )
    val money = MoneyController(viewModelScope, container.moneyRepository)

    val isToday: Boolean get() = date == currentDay

    /** Сколько дней подряд написана запись дня. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val streak: StateFlow<Int> = combine(
        container.dayRepository.observeWrittenDates(),
        snapshotFlow { currentDay },
    ) { dates, today -> DayStreak.length(dates, today) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Самый ранний месяц в календаре выбора дня: январь 2000-го или раньше, если записи старше. */
    var calendarStart by mutableStateOf(CALENDAR_START)
        private set

    /** Заполненные дни и их оценки — для календаря выбора дня. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val calendarMarks: StateFlow<Map<LocalDate, Int?>> = snapshotFlow { calendarStart }
        .flatMapLatest { container.dayRepository.observeMarks(it.atDay(1), currentDay) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Дни с записанным сном — луна в календаре выбора дня. */
    val calendarSleep: StateFlow<Set<LocalDate>> = container.sleepRepository.observeDates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Теги дней — для списка в календаре выбора дня. */
    val calendarTags: StateFlow<Map<LocalDate, List<Tag>>> = container.dayRepository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // Смена дня: сначала сохранить старый, потом загрузить новый — строго по очереди
    private val switchLock = Mutex()

    init {
        load(date)
        // Фото удалили в галерее — убрать и из открытой записи, чтобы она не вернула ссылку
        viewModelScope.launch { container.photoLibrary.removed.collect(day.editor::removePhotos) }
        viewModelScope.launch {
            container.dayRepository.firstDate()
                ?.let(YearMonth::from)
                ?.takeIf { it < calendarStart }
                ?.let { calendarStart = it }
        }
    }

    /**
     * Наступил новый день: приложение могли оставить открытым. Если был открыт «сегодняшний» день,
     * переходим на новый; прошлый день, открытый специально, не трогаем.
     */
    fun refreshToday() {
        val today = container.settings.settings.value.logicalToday()
        if (today == currentDay) return
        val wasOnToday = isToday
        currentDay = today
        if (wasOnToday) selectDate(today)
    }

    fun selectDate(date: LocalDate) {
        if (date == this.date) return
        this.date = date
        load(date)
    }

    fun selectMode(mode: TodayMode) {
        container.todaySession.mode = mode
    }

    /** Сохранить всё сейчас (приложение уходит в фон). */
    fun flush() {
        day.lockRating()
        viewModelScope.launch {
            withContext(NonCancellable) {
                day.flushNow()
                sleep.flushNow()
            }
        }
    }

    private fun load(date: LocalDate) {
        money.setDate(date)
        viewModelScope.launch {
            switchLock.withLock {
                day.load(date)
                sleep.load(date)
            }
        }
    }

    private companion object {
        val CALENDAR_START: YearMonth = YearMonth.of(2000, 1)
    }
}
