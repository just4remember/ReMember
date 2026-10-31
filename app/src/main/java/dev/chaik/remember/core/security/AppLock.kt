package dev.chaik.remember.core.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Где хранится всё, что нужно блокировке (SharedPreferences или, в тестах, память). */
interface LockStore {
    /** Блокировка включена (PIN задан в Настройках → Безопасность). */
    var enabled: Boolean

    /** Спрашивать ли PIN при входе в приложение. Без этого PIN защищает только заметки. */
    var lockApp: Boolean
    var pinHash: String?

    /** Сколько цифр в PIN — экран блокировки проверяет PIN, как только набраны все. */
    var pinLength: Int
    var biometric: Boolean
    var timeoutMillis: Long
    var failedAttempts: Int

    /** До какого момента (мс) ввод PIN запрещён после череды ошибок; 0 — не запрещён. */
    var lockoutUntil: Long

    /** Когда приложение ушло в фон (мс); 0 — с тех пор не уходило. */
    var backgroundSince: Long
}

/** Чем закончилась попытка ввести PIN. */
sealed interface PinResult {
    data object Unlocked : PinResult
    data class Wrong(val attemptsLeft: Int) : PinResult
    data class LockedOut(val untilMillis: Long) : PinResult
}

/**
 * Блокировка приложения. Включается, когда в Настройках задан PIN. Вход можно выключить отдельно
 * ([setLockApp]): PIN останется для защищённых заметок.
 *
 * - при каждом запуске приложение заблокировано;
 * - ушли в фон больше чем на [timeoutMillis] — при возвращении снова блокировка;
 * - [MAX_ATTEMPTS] неверных PIN подряд — пауза [LOCKOUT_MILLIS].
 */
class AppLock(
    private val store: LockStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _settings = MutableStateFlow(readSettings())

    /** Включена ли блокировка, отпечаток, таймаут — для страницы настроек. */
    val settings: StateFlow<LockSettings> = _settings.asStateFlow()

    val enabled: Boolean get() = store.enabled
    val timeoutMillis: Long get() = store.timeoutMillis
    val pinLength: Int get() = store.pinLength
    val biometricEnabled: Boolean get() = store.biometric

    private val _locked = MutableStateFlow(store.enabled && store.lockApp)

    /** Ушли не «насовсем», а в системное окно по нашей просьбе (выбор фото, файла, разрешение). */
    @Volatile
    private var external = false

    /** Заблокировано ли сейчас. При запуске процесса — всегда да (если блокировка включена). */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /**
     * Сейчас откроется системное окно по нашей просьбе — выбор фото, файла, разрешения.
     * Для Android это уход приложения в фон, но блокировать по возвращении не нужно:
     * такой уход прощается до [EXTERNAL_GRACE_MILLIS] (дольше — значит, человек ушёл совсем).
     */
    fun allowExternal() {
        external = true
    }

    /** Приложение ушло в фон. */
    fun onBackground() {
        if (!_locked.value) store.backgroundSince = clock()
    }

    /** Приложение вернулось: если пробыло в фоне дольше таймаута — блокируем. */
    fun onForeground() {
        val wasExternal = external
        external = false
        if (!enabled || !store.lockApp) return
        val since = store.backgroundSince
        store.backgroundSince = 0
        val limit = if (wasExternal) maxOf(timeoutMillis, EXTERNAL_GRACE_MILLIS) else timeoutMillis
        if (since > 0 && clock() - since >= limit) _locked.value = true
    }

    /** Если ввод PIN сейчас запрещён — до какого момента; иначе null. */
    fun lockoutUntil(): Long? = store.lockoutUntil.takeIf { it > clock() }

    /** Проверить PIN на экране блокировки (или в настройках перед сменой и выключением). */
    suspend fun unlockWithPin(pin: String): PinResult {
        lockoutUntil()?.let { return PinResult.LockedOut(it) }
        val hash = store.pinHash ?: return PinResult.Wrong(MAX_ATTEMPTS)
        val correct = withContext(Dispatchers.Default) { PinHasher.verify(pin, hash) }
        if (correct) {
            unlock()
            if (PinHasher.needsRehash(hash)) {
                withContext(Dispatchers.Default) { store.pinHash = PinHasher.hash(pin) }
            }
            return PinResult.Unlocked
        }
        val failed = store.failedAttempts + 1
        if (failed >= MAX_ATTEMPTS) {
            store.failedAttempts = 0
            store.lockoutUntil = clock() + LOCKOUT_MILLIS
            return PinResult.LockedOut(store.lockoutUntil)
        }
        store.failedAttempts = failed
        return PinResult.Wrong(MAX_ATTEMPTS - failed)
    }

    /** Отпечаток подтверждён системой. */
    fun unlockWithBiometric() = unlock()

    /** Задать или сменить PIN — блокировка включается. Сейчас приложение остаётся открытым. */
    suspend fun setPin(pin: String) {
        require(pin.length in MIN_PIN..MAX_PIN && pin.all(Char::isDigit)) { "PIN — от $MIN_PIN до $MAX_PIN цифр" }
        store.pinHash = withContext(Dispatchers.Default) { PinHasher.hash(pin) }
        store.pinLength = pin.length
        // Новый PIN включает и вход; при смене PIN выбор остаётся прежним
        if (!store.enabled) store.lockApp = true
        store.enabled = true
        store.failedAttempts = 0
        store.lockoutUntil = 0
        _settings.value = readSettings()
    }

    /** Выключить блокировку и забыть PIN. */
    fun disable() {
        store.enabled = false
        store.pinHash = null
        store.backgroundSince = 0
        _locked.value = false
        _settings.value = readSettings()
    }

    /** Спрашивать ли PIN при входе в приложение. Сейчас приложение остаётся открытым. */
    fun setLockApp(on: Boolean) {
        store.lockApp = on
        store.backgroundSince = 0
        if (!on) _locked.value = false
        _settings.value = readSettings()
    }

    fun setBiometric(on: Boolean) {
        store.biometric = on
        _settings.value = readSettings()
    }

    fun setTimeout(millis: Long) {
        store.timeoutMillis = millis
        _settings.value = readSettings()
    }

    private fun unlock() {
        store.failedAttempts = 0
        store.lockoutUntil = 0
        store.backgroundSince = 0
        _locked.value = false
    }

    private fun readSettings() = LockSettings(store.enabled, store.lockApp, store.biometric, store.timeoutMillis)

    companion object {
        const val MIN_PIN = 4
        const val MAX_PIN = 8
        const val DEFAULT_TIMEOUT_MILLIS = 10 * 60 * 1000L
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MILLIS = 30_000L

        /** Через сколько блокировать после ухода в фон; 0 — сразу. */
        val TimeoutOptions = listOf(0L, 30_000L, 60_000L, 5 * 60_000L, 10 * 60_000L, 30 * 60_000L)

        /** Сколько можно пробыть в выборе фото или файла без блокировки по возвращении. */
        const val EXTERNAL_GRACE_MILLIS = 10 * 60_000L
    }
}

/** Что видно на странице «Безопасность». */
data class LockSettings(val enabled: Boolean, val lockApp: Boolean, val biometric: Boolean, val timeoutMillis: Long)
