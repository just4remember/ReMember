package dev.chaik.remember.core.settings

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** Настройки приложения (Профиль и Персонализация). Язык хранит сам Android — см. [AppLanguage]. */
data class AppSettings(
    val nickname: String = "",
    val gender: Gender = Gender.UNSET,
    /** Код валюты ISO 4217: RUB, USD, EUR… */
    val currency: String = "RUB",
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.WALLPAPER,
    /** Свой акцентный цвет (ARGB), когда выбран [AccentColor.CUSTOM]. */
    val customAccent: Long = 0xFF5B6CF0,
    /** Кнопки в нижних углах — слева, веер мастеркнопки раскрывается вправо. */
    val leftHanded: Boolean = false,
    val backSwipe: BackSwipe = BackSwipe.RIGHT_TO_LEFT,
    val timePicker: TimePickerStyle = TimePickerStyle.DIAL,
    val font: AppFont = AppFont.SYSTEM,
    /** Приветствие при запуске (после PIN). */
    val welcome: Boolean = true,
    /** Сколько держать приветствие, мс ([WELCOME_MIN]…[WELCOME_MAX]). */
    val welcomeMillis: Int = 800,
    /** Вкладки островка (имена разделов, без «Ещё»); пусто — по умолчанию. */
    val islandTabs: List<String> = emptyList(),
    /** Порядок плиток «Ещё» (имена разделов); пусто — по умолчанию. */
    val moreOrder: List<String> = emptyList(),
    /** На «Сне» — кнопка подставить время по истории использования телефона. */
    val sleepGuess: Boolean = true,
    /** Как часто проверять шаги ради уведомления о цели, минут; 0 — не проверять. */
    val stepsGoalCheckMinutes: Int = 30,
    /** Во сколько сменяется день на «Сегодня», минут от полуночи; раньше этого времени — ещё вчерашний день. */
    val dayStartMinutes: Int = 0,
    /** Календарь выбора дня открывается списком дней с тегами, а не сеткой. */
    val dayPickerList: Boolean = false,
    /** Вибрация-отклик на действия во всём приложении (звонок будильника настраивается отдельно). */
    val haptics: Boolean = true,
    /** Вибрация в играх — при включённой [haptics]. */
    val gameHaptics: Boolean = true,
    /** Заметки: недавно изменённые — выше; выключено — по дате создания, новые сверху. */
    val notesByEdit: Boolean = true,
    /** Закрыли строку новой задачи (клавиатуру, тапом мимо) — набранное стирается. Иначе ждёт до следующего раза. */
    val tasksClearDraft: Boolean = false,
    /** Разделы, выключенные в «Страницах»: их нет ни в «Ещё», ни во вкладках, открыть их нельзя. */
    val disabledPages: List<String> = emptyList(),
    /** «Сегодня» при открытии: утром — сон, в остальное время — день; выключено — всегда день. */
    val todayAutoMode: Boolean = true,
    /** Слово, заменённое Т9, можно вернуть тапом по нему. */
    val t9Undo: Boolean = true,
    /** Линия под словом, заменённым Т9; [UnderlineStyle.NONE] — без линии (вернуть всё равно можно). */
    val t9Line: UnderlineStyle = UnderlineStyle.SOLID,
    /** Толщина линии в десятых долях dp ([T9_WIDTH_MIN]…[T9_WIDTH_MAX]). */
    val t9LineWidth: Int = 15,
    /** Непрозрачность линии, % ([T9_ALPHA_MIN]…100). */
    val t9LineAlpha: Int = 55,
    /** Красная строка в записях, десятые доли em (0…[INDENT_MAX]); 0 — без неё. */
    val paragraphIndent: Int = 12,
    /** Расстояние между абзацами, sp (0…[GAP_MAX]). */
    val paragraphGap: Int = 18,
)

enum class Gender { UNSET, MALE, FEMALE }

/**
 * Тема. ONE_DARK и NIGHT_OWL — готовые палитры со своими цветами; выбранный акцент меняет у них только основной цвет.
 * Имя константы хранится в настройках — переименовывать нельзя.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED,
    ONE_DARK,
    NIGHT_OWL,
    ;

    /** Палитра со своими цветами: «из обоев» для неё означает родной акцент темы. */
    val isPalette: Boolean get() = this == ONE_DARK || this == NIGHT_OWL
}

/** Акцентный цвет: из обоев (Material You) или свой. */
enum class AccentColor(val argb: Long?) {
    WALLPAPER(null),
    INDIGO(0xFF5B6CF0),
    BLUE(0xFF2F80ED),
    TEAL(0xFF14A38B),
    GREEN(0xFF3FA34D),
    AMBER(0xFFE0A21B),
    ORANGE(0xFFF0742E),
    RED(0xFFE5484D),
    PINK(0xFFE0569B),
    PURPLE(0xFF9A5CE6),

    /** Свой цвет из палитры — см. [AppSettings.customAccent]. */
    CUSTOM(null),
}

/** Каким свайпом закрывается вложенная страница. */
enum class BackSwipe { RIGHT_TO_LEFT, LEFT_TO_RIGHT, BOTH }

/** Границы длительности приветствия и шаг ползунка, мс. */
const val WELCOME_MIN = 300
const val WELCOME_MAX = 2000
const val WELCOME_STEP = 100

/** Варианты времени смены дня: с 00:00 до 08:00 через полчаса, минут от полуночи. */
val DayStartOptions = (0..480 step 30).toList()

/** Какой сейчас день для страницы «Сегодня»: до времени смены дня — ещё вчерашний. */
fun AppSettings.logicalToday(now: LocalDateTime = LocalDateTime.now()): LocalDate =
    now.minusMinutes(dayStartMinutes.toLong()).toLocalDate()

/** Варианты частоты проверки шагов, минут; 0 — выключено. */
val StepsCheckOptions = listOf(0, 15, 30, 60, 120)

/** Вид линии под словом, заменённым Т9. Имя хранится в настройках. */
enum class UnderlineStyle { NONE, SOLID, DASHED, DOTTED }

/** Границы толщины (десятые доли dp) и непрозрачности (%) линии под заменой Т9, с шагом ползунков. */
const val T9_WIDTH_MIN = 10
const val T9_WIDTH_MAX = 40
const val T9_WIDTH_STEP = 5
const val T9_ALPHA_MIN = 20
const val T9_ALPHA_STEP = 5

/** Границы красной строки (десятые доли em) и расстояния между абзацами (sp), с шагом ползунков. */
const val INDENT_MAX = 30
const val INDENT_STEP = 2
const val GAP_MAX = 32
const val GAP_STEP = 2

/** Шрифт приложения: системный или один из встроенных (res/font, лицензия OFL). */
enum class AppFont { SYSTEM, INTER, NUNITO, MEDEO }

/** Выбор времени: циферблат или ввод цифрами. */
enum class TimePickerStyle { DIAL, INPUT }

/** Валюты на выбор: код и знак. */
val Currencies: List<Pair<String, String>> = listOf(
    "RUB" to "₽",
    "USD" to "$",
    "EUR" to "€",
    "KZT" to "₸",
    "BYN" to "Br",
    "UAH" to "₴",
    "GBP" to "£",
    "CNY" to "¥",
)

/** Семь дней недели, начиная с [first]. */
fun weekDays(first: DayOfWeek): List<DayOfWeek> = List(7) { first.plus(it.toLong()) }

fun currencySymbol(code: String): String = Currencies.firstOrNull { it.first == code }?.second ?: code

/** Настройки для экранов; кладёт их [dev.chaik.remember.core.ui.theme.ReMemberTheme]. */
val LocalAppSettings = staticCompositionLocalOf { AppSettings() }
