package dev.chaik.remember.core.security

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    private class MemoryStore(
        override var enabled: Boolean = false,
        override var pinHash: String? = null,
        override var pinLength: Int = 4,
        override var biometric: Boolean = true,
        override var timeoutMillis: Long = AppLock.DEFAULT_TIMEOUT_MILLIS,
    ) : LockStore {
        override var lockApp = true
        override var failedAttempts = 0
        override var lockoutUntil = 0L
        override var backgroundSince = 0L
    }

    private var now = 1_000_000L

    /** Блокировка с PIN 1111 — как после «Установить PIN» в настройках. */
    private val store = MemoryStore(enabled = true, pinHash = PinHasher.hash(PIN))
    private val lock = AppLock(store, clock = { now })

    @Test
    fun `без входа приложение открыто, а PIN для заметок работает`() = runTest {
        store.lockApp = false
        val app = AppLock(store, clock = { now })
        assertFalse(app.locked.value)
        assertTrue(app.enabled)

        app.onBackground()
        now += AppLock.DEFAULT_TIMEOUT_MILLIS
        app.onForeground()
        assertFalse(app.locked.value)

        assertEquals(PinResult.Unlocked, app.unlockWithPin(PIN))
        assertTrue(app.unlockWithPin("9999") is PinResult.Wrong)
    }

    @Test
    fun `смена PIN не включает вход, а новый PIN включает`() = runTest {
        lock.setLockApp(false)
        lock.setPin("2222")
        assertFalse(store.lockApp)

        lock.disable()
        lock.setPin("3333")
        assertTrue(store.lockApp)
    }

    @Test
    fun `хэш PIN проверяется и не совпадает с другим PIN`() {
        val hash = PinHasher.hash("1111")
        assertTrue(PinHasher.verify("1111", hash))
        assertFalse(PinHasher.verify("1112", hash))
        // Соль случайная: один PIN — разные хэши
        assertNotEquals(hash, PinHasher.hash("1111"))
        assertFalse(hash.contains("1111"))
    }

    @Test
    fun `старый хэш пересчитывается после верного PIN`() = runTest {
        store.pinHash = PinHasher.hash(PIN, iterations = 1_000)
        assertTrue(PinHasher.needsRehash(store.pinHash!!))

        assertEquals(PinResult.Unlocked, lock.unlockWithPin(PIN))

        assertFalse(PinHasher.needsRehash(store.pinHash!!))
        assertTrue(PinHasher.verify(PIN, store.pinHash!!))
    }

    @Test
    fun `без PIN блокировки нет — даже если остался старый хэш`() {
        val off = AppLock(MemoryStore(enabled = false, pinHash = PinHasher.hash("1111")), clock = { now })
        assertFalse(off.locked.value)
        off.onBackground()
        now += AppLock.DEFAULT_TIMEOUT_MILLIS * 2
        off.onForeground()
        assertFalse(off.locked.value)
    }

    @Test
    fun `при запуске заблокировано, PIN открывает`() = runTest {
        assertTrue(lock.locked.value)
        assertEquals(PinResult.Unlocked, lock.unlockWithPin(PIN))
        assertFalse(lock.locked.value)
    }

    @Test
    fun `неверный PIN — попытки, потом пауза`() = runTest {
        repeat(AppLock.MAX_ATTEMPTS - 1) { i ->
            assertEquals(PinResult.Wrong(AppLock.MAX_ATTEMPTS - 1 - i), lock.unlockWithPin("0000"))
        }
        val lockedOut = lock.unlockWithPin("0000")
        assertTrue(lockedOut is PinResult.LockedOut)
        // Во время паузы не открывает даже верный PIN
        assertTrue(lock.unlockWithPin(PIN) is PinResult.LockedOut)

        now += AppLock.LOCKOUT_MILLIS
        assertEquals(PinResult.Unlocked, lock.unlockWithPin(PIN))
    }

    @Test
    fun `блокировка после долгого фона, но не после короткого`() = runTest {
        lock.unlockWithBiometric()

        lock.onBackground()
        now += AppLock.DEFAULT_TIMEOUT_MILLIS - 1
        lock.onForeground()
        assertFalse(lock.locked.value)

        lock.onBackground()
        now += AppLock.DEFAULT_TIMEOUT_MILLIS
        lock.onForeground()
        assertTrue(lock.locked.value)
    }

    @Test
    fun `новый PIN другой длины, свой таймаут, выключение`() = runTest {
        val fresh = MemoryStore()
        val app = AppLock(fresh, clock = { now })
        assertFalse(app.settings.value.enabled)

        // Задали PIN — блокировка включена, но сейчас приложение открыто
        app.setPin("123456")
        assertTrue(app.settings.value.enabled)
        assertEquals(6, app.pinLength)
        assertFalse(app.locked.value)

        app.setTimeout(30_000)
        app.onBackground()
        now += 30_000
        app.onForeground()
        assertTrue(app.locked.value)
        assertTrue(app.unlockWithPin("1111") is PinResult.Wrong)
        assertEquals(PinResult.Unlocked, app.unlockWithPin("123456"))

        app.disable()
        assertFalse(app.settings.value.enabled)
        assertNull(fresh.pinHash)
    }

    @Test
    fun `«сразу» запирает, но выбор фото — нет, если он недолгий`() = runTest {
        lock.unlockWithBiometric()
        lock.setTimeout(0)

        // Просто ушли из приложения — замок сразу
        lock.onBackground()
        now += 1_000
        lock.onForeground()
        assertTrue(lock.locked.value)
        lock.unlockWithBiometric()

        // Выбирали фото пять минут — без замка
        lock.allowExternal()
        lock.onBackground()
        now += 5 * 60_000
        lock.onForeground()
        assertFalse(lock.locked.value)

        // Открыли выбор фото и пропали на полчаса — замок
        lock.allowExternal()
        lock.onBackground()
        now += AppLock.EXTERNAL_GRACE_MILLIS
        lock.onForeground()
        assertTrue(lock.locked.value)
    }

    private companion object {
        const val PIN = "1111"
    }
}
