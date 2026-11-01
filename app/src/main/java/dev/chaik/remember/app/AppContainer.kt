package dev.chaik.remember.app

import android.content.Context
import dev.chaik.remember.core.alarm.AlarmController
import dev.chaik.remember.core.alarm.AlarmScheduler
import dev.chaik.remember.core.alarm.TimeNotifications
import dev.chaik.remember.core.alarm.TimeStore
import dev.chaik.remember.core.backup.BackupManager
import dev.chaik.remember.core.wordgame.AssetWordDictionary
import dev.chaik.remember.core.wordgame.GamesStore
import dev.chaik.remember.core.data.DayRepository
import dev.chaik.remember.core.data.EventRepository
import dev.chaik.remember.core.data.MoneyRepository
import dev.chaik.remember.core.data.NoteRepository
import dev.chaik.remember.core.data.SleepRepository
import dev.chaik.remember.core.data.TagRepository
import dev.chaik.remember.core.data.TaskRepository
import dev.chaik.remember.core.data.WorkoutRepository
import dev.chaik.remember.core.database.DatabaseFactory
import dev.chaik.remember.core.database.ReMemberDatabase
import dev.chaik.remember.core.health.HealthConnectSource
import dev.chaik.remember.core.health.HealthGoalsStore
import dev.chaik.remember.core.health.StepsGoalWatcher
import dev.chaik.remember.core.network.NetworkMonitor
import dev.chaik.remember.core.sleep.PhoneUsageHistory
import dev.chaik.remember.core.sleep.SleepFilledStore
import dev.chaik.remember.core.network.NetworkSettingsStore
import dev.chaik.remember.core.photos.PhotoLibrary
import dev.chaik.remember.core.photos.PhotoStorage
import dev.chaik.remember.core.reminders.Notifier
import dev.chaik.remember.core.reminders.OpenTarget
import dev.chaik.remember.core.reminders.ReminderScheduler
import dev.chaik.remember.core.reminders.ReminderSettingsStore
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.core.security.AppLock
import dev.chaik.remember.core.settings.AppSettingsStore
import dev.chaik.remember.core.settings.logicalToday
import dev.chaik.remember.core.health.DEFAULT_WEIGHT_KG
import dev.chaik.remember.feature.today.TodaySession
import dev.chaik.remember.core.security.DatabaseKeyManager
import dev.chaik.remember.core.security.PrefsLockStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Все общие объекты приложения в одном месте (ручной DI).
 * Создаются лениво — при первом обращении, а не при запуске.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    /**
     * Фоновые задачи уровня приложения — живут, пока жив процесс.
     * Сюда же — сохранения, которые должны доделаться, даже если экран уже закрыт.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val databaseKeyManager by lazy { DatabaseKeyManager(appContext) }

    /** Экспорт и восстановление резервной копии. */
    val backupManager by lazy { BackupManager(appContext, { database }, databaseKeyManager) }

    val database: ReMemberDatabase by lazy {
        DatabaseFactory.create(appContext, databaseKeyManager)
    }

    val photoStorage by lazy { PhotoStorage(appContext, database.photoDao()) }

    /** Галерея всех фото: список, удаление, копия в галерею телефона. */
    val photoLibrary by lazy { PhotoLibrary(database, photoStorage, dayRepository, noteRepository) }

    val appLock by lazy { AppLock(PrefsLockStore(appContext)) }

    /** Приветствие уже показано в этом запуске — при пересоздании экрана (смена темы) не повторять. */
    var welcomeShown = false

    /**
     * Открытые сейчас экраны с защищённым содержимым. При смене вкладки они убираются из её стека:
     * далёкая вкладка выгружается сразу, и сам экран об уходе узнать не успевает.
     */
    val protectedScreens = mutableSetOf<NavKey>()

    /** Режим страницы «Сегодня» — общий на весь запуск. */
    val todaySession by lazy {
        TodaySession(
            autoMode = { settings.settings.value.todayAutoMode },
            sleepFilled = { sleepFilled.isFilledToday() },
            dayStartMinutes = { settings.settings.value.dayStartMinutes },
        )
    }

    /** Записан ли сон за сегодня — читается при открытии приложения, пока база ещё закрыта. */
    val sleepFilled by lazy { SleepFilledStore(appContext) { settings.settings.value.logicalToday() } }

    /** Профиль и персонализация. */
    val settings by lazy { AppSettingsStore(appContext) }

    val dayRepository by lazy { DayRepository(database) }
    val sleepRepository by lazy { SleepRepository(database) }
    val moneyRepository by lazy { MoneyRepository(database) { settings.settings.value.currency } }
    val noteRepository by lazy { NoteRepository(database) }
    val tagRepository by lazy { TagRepository(database) }
    val taskRepository by lazy { TaskRepository(database) }
    val eventRepository by lazy { EventRepository(database) }
    val workoutRepository by lazy { WorkoutRepository(database) }

    val health by lazy { HealthConnectSource(appContext) }

    /** История использования телефона — подсказка времени сна. */
    val phoneUsage by lazy { PhoneUsageHistory(appContext) }

    /** Страница «Сеть»: настройки замера и текущая сеть. */
    val networkSettings by lazy { NetworkSettingsStore(appContext) }
    val networkMonitor by lazy { NetworkMonitor(appContext) }
    val healthGoals by lazy { HealthGoalsStore(appContext) }

    val reminderSettings by lazy { ReminderSettingsStore(appContext) }
    val notifier by lazy { Notifier(appContext) }
    val reminderScheduler by lazy {
        ReminderScheduler(
            appContext,
            reminderSettings,
            notifier,
            dayRepository,
            sleepRepository,
            eventRepository,
            taskRepository,
            workoutRepository,
            raskobki,
        )
    }

    /** Поздравление с целью по шагам — проверка в фоне. */
    val stepsGoalWatcher by lazy {
        StepsGoalWatcher(appContext, settings, reminderSettings, healthGoals, health, notifier) {
            workoutRepository.lastBodyWeight() ?: DEFAULT_WEIGHT_KG
        }
    }

    /** Раздел «Время»: будильники, звонок, таймер, секундомер. */
    val timeStore by lazy { TimeStore(appContext) }
    val timeNotifications by lazy { TimeNotifications(appContext, timeStore) }
    val wordDictionary by lazy { AssetWordDictionary(appContext) }

    /** Раздел «Игры»: незаконченные игры. */
    val gamesStore by lazy { GamesStore(appContext) }
    val raskobki by lazy { dev.chaik.remember.core.raskobki.RaskobkiRepository(appContext) }
    val raskobkiMetrics by lazy { dev.chaik.remember.core.raskobki.RaskobkiMetrics(appContext) }
    val alarmController by lazy {
        AlarmController(appContext, timeStore, AlarmScheduler(appContext), wordDictionary, timeNotifications) { dream ->
            appScope.launch { sleepRepository.appendDream(settings.settings.value.logicalToday(), dream) }
        }
    }

    /** Куда перейти по тапу на уведомление; экран, который это выполнил, обнуляет запрос. */
    val openRequests = MutableStateFlow<OpenTarget?>(null)
}
