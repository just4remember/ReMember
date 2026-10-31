package dev.chaik.remember.core.sleep

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate

/**
 * За какой день уже записан сон. Отдельно от базы: при открытии приложения вкладку «Сегодня»
 * надо выбрать сразу, а зашифрованная база к этому моменту может быть ещё закрыта.
 */
class SleepFilledStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isFilledToday(): Boolean = prefs.getLong(KEY_DAY, -1) == LocalDate.now().toEpochDay()

    /** Запись за [date] заполнена или очищена. Важен только сегодняшний день. */
    fun set(date: LocalDate, filled: Boolean) {
        if (date != LocalDate.now()) return
        prefs.edit { if (filled) putLong(KEY_DAY, date.toEpochDay()) else remove(KEY_DAY) }
    }

    private companion object {
        const val PREFS = "sleep_filled"
        const val KEY_DAY = "day"
    }
}
