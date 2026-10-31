package dev.chaik.remember.core.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek

/** Настройки в SharedPreferences: читаются мгновенно при запуске, до открытия базы. */
class AppSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val settings: StateFlow<AppSettings> = state.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(state.value)
        if (next == state.value) return
        write(next)
        state.value = next
    }

    private fun read(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            nickname = prefs.getString("nickname", d.nickname).orEmpty(),
            gender = enumOr(prefs.getString("gender", null), d.gender),
            currency = prefs.getString("currency", d.currency) ?: d.currency,
            firstDayOfWeek = prefs.getInt("first_day", 0).takeIf { it in 1..7 }?.let(DayOfWeek::of) ?: d.firstDayOfWeek,
            theme = enumOr(prefs.getString("theme", null), d.theme),
            accent = enumOr(prefs.getString("accent", null), d.accent),
            customAccent = prefs.getLong("custom_accent", d.customAccent),
            leftHanded = prefs.getBoolean("left_handed", d.leftHanded),
            backSwipe = enumOr(prefs.getString("back_swipe", null), d.backSwipe),
            timePicker = enumOr(prefs.getString("time_picker", null), d.timePicker),
            font = enumOr(prefs.getString("font", null), d.font),
            welcome = prefs.getBoolean("welcome", d.welcome),
            welcomeMillis = prefs.getInt("welcome_millis", d.welcomeMillis).coerceIn(WELCOME_MIN, WELCOME_MAX),
            islandTabs = list("island_tabs"),
            moreOrder = list("more_order"),
            todayAutoMode = prefs.getBoolean("today_auto_mode", d.todayAutoMode),
            dayPickerList = prefs.getBoolean("day_picker_list", d.dayPickerList),
            haptics = prefs.getBoolean("haptics", d.haptics),
            gameHaptics = prefs.getBoolean("game_haptics", d.gameHaptics),
            notesByEdit = prefs.getBoolean("notes_by_edit", d.notesByEdit),
            disabledPages = list("disabled_pages"),
            sleepGuess = prefs.getBoolean("sleep_guess", d.sleepGuess),
            stepsGoalCheckMinutes = prefs.getInt("steps_goal_check", d.stepsGoalCheckMinutes).takeIf { it in StepsCheckOptions } ?: d.stepsGoalCheckMinutes,
            t9Undo = prefs.getBoolean("t9_undo", d.t9Undo),
            t9Line = enumOr(prefs.getString("t9_line", null), d.t9Line),
            t9LineWidth = prefs.getInt("t9_line_width", d.t9LineWidth).coerceIn(T9_WIDTH_MIN, T9_WIDTH_MAX),
            t9LineAlpha = prefs.getInt("t9_line_alpha", d.t9LineAlpha).coerceIn(T9_ALPHA_MIN, 100),
            paragraphIndent = prefs.getInt("paragraph_indent", d.paragraphIndent).coerceIn(0, INDENT_MAX),
            paragraphGap = prefs.getInt("paragraph_gap", d.paragraphGap).coerceIn(0, GAP_MAX),
        )
    }

    private fun write(s: AppSettings) = prefs.edit {
        putString("nickname", s.nickname)
        putString("gender", s.gender.name)
        putString("currency", s.currency)
        putInt("first_day", s.firstDayOfWeek.value)
        putString("theme", s.theme.name)
        putString("accent", s.accent.name)
        putLong("custom_accent", s.customAccent)
        putBoolean("left_handed", s.leftHanded)
        putString("back_swipe", s.backSwipe.name)
        putString("time_picker", s.timePicker.name)
        putString("font", s.font.name)
        putBoolean("welcome", s.welcome)
        putInt("welcome_millis", s.welcomeMillis)
        putString("island_tabs", s.islandTabs.joinToString(","))
        putString("more_order", s.moreOrder.joinToString(","))
        putBoolean("today_auto_mode", s.todayAutoMode)
        putBoolean("day_picker_list", s.dayPickerList)
        putBoolean("haptics", s.haptics)
        putBoolean("game_haptics", s.gameHaptics)
        putBoolean("notes_by_edit", s.notesByEdit)
        putString("disabled_pages", s.disabledPages.joinToString(","))
        putBoolean("sleep_guess", s.sleepGuess)
        putInt("steps_goal_check", s.stepsGoalCheckMinutes)
        putBoolean("t9_undo", s.t9Undo)
        putString("t9_line", s.t9Line.name)
        putInt("t9_line_width", s.t9LineWidth)
        putInt("t9_line_alpha", s.t9LineAlpha)
        putInt("paragraph_indent", s.paragraphIndent)
        putInt("paragraph_gap", s.paragraphGap)
    }

    private fun list(key: String): List<String> =
        prefs.getString(key, null).orEmpty().split(',').filter { it.isNotBlank() }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val PREFS = "settings"
    }
}
