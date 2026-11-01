package dev.chaik.remember.core.reminders

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/**
 * Настройки напоминаний в SharedPreferences: в них нет личных данных, а читать их
 * нужно и тогда, когда база ещё закрыта (после перезагрузки телефона).
 */
class ReminderSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val settings: StateFlow<ReminderSettings> = state.asStateFlow()

    fun update(transform: (ReminderSettings) -> ReminderSettings) {
        val next = transform(state.value)
        if (next == state.value) return
        write(next)
        state.value = next
    }

    /** До какого момента напоминания уже разосланы — чтобы не прислать одно дважды. */
    var lastCheck: Instant?
        get() = prefs.getLong(KEY_LAST_CHECK, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        set(value) = prefs.edit { putLong(KEY_LAST_CHECK, value?.toEpochMilli() ?: 0L) }

    /** Спрашивали ли разрешение на уведомления при первом запуске. */
    var permissionAsked: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_PERMISSION_ASKED, value) }

    private fun read(): ReminderSettings {
        val d = ReminderSettings()
        return ReminderSettings(
            enabled = prefs.getBoolean("enabled", d.enabled),
            dayReport = readDaily("day", d.dayReport),
            sleep = readDaily("sleep", d.sleep),
            summary = readDaily("summary", d.summary),
            raskobki = readDaily("raskobki", d.raskobki),
            events = prefs.getBoolean("events", d.events),
            tasks = prefs.getBoolean("tasks", d.tasks),
            quiet = QuietHours(
                enabled = prefs.getBoolean("quiet.enabled", d.quiet.enabled),
                start = readTime("quiet.start", d.quiet.start),
                end = readTime("quiet.end", d.quiet.end),
                mode = enumOr(prefs.getString("quiet.mode", null), d.quiet.mode),
            ),
            sound = enumOr(prefs.getString("sound", null), d.sound),
        )
    }

    private fun write(s: ReminderSettings) = prefs.edit {
        putBoolean("enabled", s.enabled)
        writeDaily(this, "day", s.dayReport)
        writeDaily(this, "sleep", s.sleep)
        writeDaily(this, "summary", s.summary)
        writeDaily(this, "raskobki", s.raskobki)
        putBoolean("events", s.events)
        putBoolean("tasks", s.tasks)
        putBoolean("quiet.enabled", s.quiet.enabled)
        putInt("quiet.start", s.quiet.start.toSecondOfDay())
        putInt("quiet.end", s.quiet.end.toSecondOfDay())
        putString("quiet.mode", s.quiet.mode.name)
        putString("sound", s.sound.name)
    }

    private fun readDaily(key: String, default: DailyReminder) = DailyReminder(
        enabled = prefs.getBoolean("$key.enabled", default.enabled),
        time = readTime("$key.time", default.time),
        days = prefs.getString("$key.days", null)
            ?.split(',')?.mapNotNull { it.toIntOrNull()?.takeIf { d -> d in 1..7 } }?.map(DayOfWeek::of)?.toSet()
            ?: default.days,
    )

    private fun writeDaily(editor: android.content.SharedPreferences.Editor, key: String, r: DailyReminder) {
        editor.putBoolean("$key.enabled", r.enabled)
        editor.putInt("$key.time", r.time.toSecondOfDay())
        editor.putString("$key.days", r.days.map { it.value }.sorted().joinToString(","))
    }

    private fun readTime(key: String, default: LocalTime): LocalTime =
        prefs.getInt(key, -1).takeIf { it >= 0 }?.let { LocalTime.ofSecondOfDay(it.toLong()) } ?: default

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val PREFS = "reminders"
        const val KEY_LAST_CHECK = "last_check"
        const val KEY_PERMISSION_ASKED = "permission_asked"
    }
}
