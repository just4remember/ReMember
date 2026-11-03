package dev.chaik.remember.core.sleep

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * История использования телефона (экран погас / разблокировали) — из журнала Android.
 * Нужно разрешение «Доступ к истории использования», его включают вручную в настройках.
 * Ничего не хранит и никуда не отправляет: читает журнал, когда спросили.
 */
class PhoneUsageHistory(private val context: Context) {

    /** Есть ли доступ к истории использования. */
    fun hasAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Экран настроек Android, где включается доступ (сразу на этом приложении, если телефон умеет). */
    fun accessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Сон, который закончился утром [date]; null — не нашли или нет доступа. */
    suspend fun guessSleep(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): SleepOptions? = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext null
        val (from, to) = SleepGuesser.window(date, zone)
        SleepGuesser.options(events(from, minOf(to, Instant.now())), date, zone)
    }

    private fun events(from: Instant, to: Instant): List<PhoneEvent> {
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val raw = manager.queryEvents(from.toEpochMilli(), to.toEpochMilli())
        val event = UsageEvents.Event()
        val screenOn = mutableListOf<PhoneEvent>()
        val result = mutableListOf<PhoneEvent>()
        var hasKeyguard = false
        while (raw.hasNextEvent()) {
            raw.getNextEvent(event)
            val at = Instant.ofEpochMilli(event.timeStamp)
            when (event.eventType) {
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> result += PhoneEvent(at, PhoneEvent.Type.SCREEN_OFF)
                UsageEvents.Event.KEYGUARD_HIDDEN -> {
                    hasKeyguard = true
                    result += PhoneEvent(at, PhoneEvent.Type.UNLOCK)
                }
                UsageEvents.Event.SCREEN_INTERACTIVE -> screenOn += PhoneEvent(at, PhoneEvent.Type.UNLOCK)
            }
        }
        // Без блокировки экрана разблокировок нет — тогда «использование» начинается со включения экрана
        if (!hasKeyguard) result += screenOn
        return result
    }
}
