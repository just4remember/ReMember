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

    var date by mutableStateOf(LocalDate.now())
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

    val isToday: Boolean get() = date == LocalDate.now()

    /** Самый ранний месяц в календаре выбора дня: год назад или раньше, если записи старше. */
    var calendarStart by mutableStateOf(YearMonth.now().minusMonths(CALENDAR_MONTHS_BACK))
        private set

    /** Заполненные дни и их оценки — для календаря выбора дня. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val calendarMarks: StateFlow<Map<LocalDate, Int?>> = snapshotFlow { calendarStart }
        .flatMapLatest { container.dayRepository.observeMarks(it.atDay(1), LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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
        const val CALENDAR_MONTHS_BACK = 12L
    }
}
