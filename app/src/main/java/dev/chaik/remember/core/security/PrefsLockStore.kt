package dev.chaik.remember.core.security

import android.content.Context
import androidx.core.content.edit

/** Состояние блокировки в SharedPreferences (как и всё приложение, исключено из бэкапов). */
class PrefsLockStore(context: Context) : LockStore {
    private val prefs = context.getSharedPreferences("security", Context.MODE_PRIVATE)

    // Отдельный флаг, а не «есть хэш»: от тестов экрана блокировки мог остаться хэш PIN 1111
    override var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    override var lockApp: Boolean
        get() = prefs.getBoolean(KEY_LOCK_APP, true)
        set(value) = prefs.edit { putBoolean(KEY_LOCK_APP, value) }

    override var pinHash: String?
        get() = prefs.getString(KEY_PIN_HASH, null)
        set(value) = prefs.edit { putString(KEY_PIN_HASH, value) }

    override var pinLength: Int
        get() = prefs.getInt(KEY_PIN_LENGTH, AppLock.MIN_PIN)
        set(value) = prefs.edit { putInt(KEY_PIN_LENGTH, value) }

    override var biometric: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, true)
        set(value) = prefs.edit { putBoolean(KEY_BIOMETRIC, value) }

    override var timeoutMillis: Long
        get() = prefs.getLong(KEY_TIMEOUT, AppLock.DEFAULT_TIMEOUT_MILLIS)
        set(value) = prefs.edit { putLong(KEY_TIMEOUT, value) }

    override var failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED, 0)
        set(value) = prefs.edit { putInt(KEY_FAILED, value) }

    override var lockoutUntil: Long
        get() = prefs.getLong(KEY_LOCKOUT, 0)
        set(value) = prefs.edit { putLong(KEY_LOCKOUT, value) }

    override var backgroundSince: Long
        get() = prefs.getLong(KEY_BACKGROUND, 0)
        set(value) = prefs.edit { putLong(KEY_BACKGROUND, value) }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_LOCK_APP = "lock_app"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_LENGTH = "pin_length"
        const val KEY_BIOMETRIC = "biometric"
        const val KEY_TIMEOUT = "timeout_millis"
        const val KEY_FAILED = "failed_attempts"
        const val KEY_LOCKOUT = "lockout_until"
        const val KEY_BACKGROUND = "background_since"
    }
}
