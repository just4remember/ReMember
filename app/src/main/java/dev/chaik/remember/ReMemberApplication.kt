package dev.chaik.remember

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.chaik.remember.app.AppContainer
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ReMemberApplication : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // После перезагрузки будильник может запустить процесс до первой разблокировки телефона:
        // база и настройки ещё закрыты, работает только звонок. Остальное — когда телефон разблокируют
        if (getSystemService(UserManager::class.java).isUserUnlocked) {
            startUp()
        } else {
            val unlocked = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    unregisterReceiver(this)
                    startUp()
                }
            }
            registerReceiver(unlocked, IntentFilter(Intent.ACTION_USER_UNLOCKED), RECEIVER_NOT_EXPORTED)
        }
    }

    @OptIn(FlowPreview::class)
    private fun startUp() {
        // Подтверждённое восстановление из копии — до того, как кто-нибудь откроет базу
        runCatching { container.backupManager.applyPendingRestore() }
            .onFailure { Log.e(TAG, "Не удалось восстановить данные из копии", it) }
        // Всё приложение ушло в фон (а не просто открылся выбор фото внутри него) — запоминаем когда
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    container.appLock.onBackground()
                    container.todaySession.onBackground()
                    // Ушли из приложения — заодно убрать фото, которые убрали из записей.
                    // Чуть ждём: открытые записи сохраняются при уходе в фон
                    cleanupPhotos(afterMs = BACKGROUND_CLEANUP_DELAY_MS)
                    cleanupTags(afterMs = BACKGROUND_CLEANUP_DELAY_MS)
                }

                // Вернулись после долгой паузы — «Сегодня» снова в режиме по времени суток
                override fun onStart(owner: LifecycleOwner) = container.todaySession.onForeground()
            },
        )
        container.appScope.launch {
            // Открыть зашифрованную базу заранее, пока на экране замок: SQLCipher при открытии
            // специально долго вычисляет ключ (~1–2 с), и после PIN запись дня появлялась с задержкой
            runCatching { container.database.openHelper.writableDatabase }
                .onFailure { Log.w(TAG, "Не удалось открыть базу", it) }
        }
        container.appScope.launch {
            // Сверить расписание копий на сервер с настройками
            runCatching { container.serverBackup.schedule() }
        }
        container.appScope.launch {
            // Стандартные категории трат: создать при первом запуске, обновить при смене набора
            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            runCatching {
                val applied = prefs.getInt(KEY_CATEGORY_DEFAULTS, 0)
                val version = container.moneyRepository.ensureDefaultCategories(applied)
                if (version != applied) prefs.edit().putInt(KEY_CATEGORY_DEFAULTS, version).apply()
            }.onFailure { Log.w(TAG, "Не удалось обновить категории", it) }
        }
        container.appScope.launch {
            // Расписание напоминаний пересчитывается при любом изменении событий, задач, настроек
            // и идущей тренировки
            // (и сразу при запуске). Пока приложение не запущено, его ведут будильник и ReminderReceiver
            combine(
                container.eventRepository.observeAll(),
                container.taskRepository.observeAll(),
                container.reminderSettings.settings,
                container.workoutRepository.observeActive(),
            ) { _, _, _, _ -> }
                .debounce(RESCHEDULE_DEBOUNCE_MS)
                .collect {
                    runCatching { container.reminderScheduler.check() }
                        .onFailure { Log.w(TAG, "Не удалось пересчитать напоминания", it) }
                }
        }
        container.appScope.launch {
            // Проверка шагов ради поздравления с целью: ставим при запуске и при смене частоты в настройках
            container.settings.settings
                .map { it.stepsGoalCheckMinutes }
                .distinctUntilChanged()
                .collect { container.stepsGoalWatcher.schedule() }
        }
        // Будильники, таймер и повтор усиленного будильника — на всякий случай заново при каждом запуске
        container.appScope.launch {
            runCatching { container.alarmController.rescheduleAll() }
                .onFailure { Log.w(TAG, "Не удалось поставить будильники", it) }
        }
        // Не мешаем запуску: уборка — через пару секунд после старта
        cleanupPhotos(afterMs = STARTUP_CLEANUP_DELAY_MS)
        cleanupTags(afterMs = STARTUP_CLEANUP_DELAY_MS)
    }

    /**
     * Удалить теги, которых нет ни у одного дня и ни у одной заметки. Не сразу после снятия тега,
     * а при уходе в фон и при запуске: иначе тег исчезал бы прямо из открытого окна выбора.
     */
    private fun cleanupTags(afterMs: Long) {
        container.appScope.launch {
            delay(afterMs)
            runCatching { container.tagRepository.deleteUnused() }
                .onFailure { Log.w(TAG, "Уборка тегов не удалась", it) }
        }
    }

    /** Удалить фото, которые ни в одной записи больше не стоят (см. PhotoStorage.UNUSED_GRACE). */
    private fun cleanupPhotos(afterMs: Long) {
        container.appScope.launch {
            delay(afterMs)
            runCatching { container.photoStorage.cleanupUnreferenced() }
                .onSuccess { if (it > 0) Log.i(TAG, "Удалено фото без ссылок: $it") }
                .onFailure { Log.w(TAG, "Уборка фото не удалась", it) }
        }
    }

    private companion object {
        const val TAG = "ReMember"
        const val PREFS = "app_state"
        const val KEY_CATEGORY_DEFAULTS = "category_defaults_version"
        const val RESCHEDULE_DEBOUNCE_MS = 1_000L
        const val STARTUP_CLEANUP_DELAY_MS = 3_000L
        const val BACKGROUND_CLEANUP_DELAY_MS = 2_000L
    }
}

/** Контейнер зависимостей из любого места, где есть Context. */
val Context.appContainer: AppContainer
    get() = (applicationContext as ReMemberApplication).container
