package dev.chaik.remember.core.widgets

import android.content.Context
import androidx.core.content.edit
import dev.chaik.remember.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Тёмная подложка виджетов по умолчанию — как карточки приложения. */
const val WIDGET_CARD_COLOR = 0xFF1C1F26

/** Настройки виджетов на рабочем столе («Настройки → Виджеты»); общие для всех копий виджета. */
@Serializable
data class WidgetSettings(
    /** Язык подписей виджетов: дата, «из 6 000» и т. п. */
    val language: WidgetLanguage = WidgetLanguage.APP,
    // Часы со стрелками
    val analogTheme: DialTheme = DialTheme.DARK,
    val analogSeconds: Boolean = true,
    /** Цвет главных рисок и секундной стрелки; null — акцент приложения. */
    val analogColor: Long? = null,
    val analogDate: Boolean = false,
    val analogStreak: Boolean = false,
    // Цифровые часы
    val digitalWeight: ClockWeight = ClockWeight.THIN,
    val digitalCard: Boolean = false,
    val digitalCardColor: Long = WIDGET_CARD_COLOR,
    val digitalDate: Boolean = true,
    val digitalStreak: Boolean = false,
    // Кольца здоровья
    val healthTextInside: Boolean = false,
    /** «из 6 000» рядом с цифрами; внутри колец целей нет — не хватает места. */
    val healthGoals: Boolean = true,
    val healthCardColor: Long = WIDGET_CARD_COLOR,
)

/**
 * Толщина цифр цифровых часов. Шрифт — системный: свои шрифты приложения рабочий стол
 * в виджетах не показывает. Толщину на лету тоже не сменить, поэтому у каждой свой макет
 * цифр — с лёгкой тенью для обоев и без тени для подложки.
 */
enum class ClockWeight(val label: Int, val weight: Int, val layout: Int, val plainLayout: Int) {
    THIN(R.string.widget_weight_thin, 100, R.layout.widget_digits_thin, R.layout.widget_digits_thin_plain),
    LIGHT(R.string.widget_weight_light, 300, R.layout.widget_digits_light, R.layout.widget_digits_light_plain),
    REGULAR(R.string.widget_weight_regular, 400, R.layout.widget_digits_regular, R.layout.widget_digits_regular_plain),
    MEDIUM(R.string.widget_weight_medium, 500, R.layout.widget_digits_medium, R.layout.widget_digits_medium_plain),
    BOLD(R.string.widget_weight_bold, 700, R.layout.widget_digits_bold, R.layout.widget_digits_bold_plain),
}

/** Язык виджетов. [tag] — язык; пусто — берётся из приложения или системы. */
enum class WidgetLanguage(val label: Int, val tag: String) {
    /** Как выбран язык в самом приложении. */
    APP(R.string.widget_language_app, ""),

    /** Как язык телефона. */
    SYSTEM(R.string.widget_language_system, ""),
    RU(R.string.widget_language_ru, "ru"),
    EN(R.string.widget_language_en, "en"),
}

/** Тема циферблата часов со стрелками. */
enum class DialTheme(val label: Int) {
    DARK(R.string.widget_dial_dark),
    LIGHT(R.string.widget_dial_light),

    /** Днём светлый, ночью тёмный — цвета из ресурсов, их выбирает сам рабочий стол. */
    SYSTEM(R.string.widget_dial_system),
}

class WidgetSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val state = MutableStateFlow(read())
    val settings: StateFlow<WidgetSettings> = state

    fun update(transform: (WidgetSettings) -> WidgetSettings) {
        val next = transform(state.value)
        prefs.edit { putString(KEY, json.encodeToString(WidgetSettings.serializer(), next)) }
        state.value = next
    }

    private fun read(): WidgetSettings = prefs.getString(KEY, null)
        ?.let { runCatching { json.decodeFromString(WidgetSettings.serializer(), it) }.getOrNull() }
        ?: WidgetSettings()

    private companion object {
        const val KEY = "settings"
    }
}
