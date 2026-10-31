package dev.chaik.remember.core.system

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.abs
import kotlin.math.roundToInt

/** Показатели на момент замера. null — система значение не отдала. */
data class PerformanceSnapshot(
    val ramBytes: Long,
    val ramTotalBytes: Long,
    /** Доля всех ядер, которую заняло приложение с прошлого замера, %; при первом замере null. */
    val cpuPercent: Double?,
    val cores: Int,
    val appDiskBytes: Long?,
    val diskFreeBytes: Long,
    val batteryLevel: Int?,
    /** Ток батареи телефона, мА: без знака. */
    val batteryMilliamps: Int?,
    val charging: Boolean,
)

/**
 * Показатели для страницы «Данные». Android отдаёт приложению только его собственные память,
 * процессор и диск. Расход батареи по приложениям закрыт, поэтому батарея — по всему телефону.
 */
class PerformanceMonitor(private val context: Context) {
    /** Замеры, пока на flow подписаны. Размер на диске считается дольше — раз в [DISK_EVERY] замеров. */
    fun observe(): Flow<PerformanceSnapshot> = flow {
        var lastCpu = Process.getElapsedCpuTime()
        var lastTime = SystemClock.elapsedRealtime()
        var cpu: Double? = null
        var disk: Long? = null
        var tick = 0
        while (true) {
            if (tick % DISK_EVERY == 0) disk = appDiskBytes()
            emit(read(cpu, disk))
            tick++
            delay(PERIOD_MS)
            val cpuNow = Process.getElapsedCpuTime()
            val timeNow = SystemClock.elapsedRealtime()
            cpu = cpuPercent(cpuNow - lastCpu, timeNow - lastTime, cores)
            lastCpu = cpuNow
            lastTime = timeNow
        }
    }.flowOn(Dispatchers.IO)

    private val cores = Runtime.getRuntime().availableProcessors()

    private fun read(cpu: Double?, disk: Long?): PerformanceSnapshot {
        val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        val device = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val battery = context.getSystemService(BatteryManager::class.java)
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return PerformanceSnapshot(
            ramBytes = memory.totalPss * 1024L,
            ramTotalBytes = device.totalMem,
            cpuPercent = cpu,
            cores = cores,
            appDiskBytes = disk,
            diskFreeBytes = StatFs(context.filesDir.path).availableBytes,
            batteryLevel = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 },
            batteryMilliamps = batteryMilliamps(battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)),
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
        )
    }

    /** Приложение, данные и кэш. Сведения о самом себе система отдаёт без разрешений. */
    private fun appDiskBytes(): Long? = runCatching {
        val stats = context.getSystemService(StorageStatsManager::class.java)
            .queryStatsForPackage(StorageManager.UUID_DEFAULT, context.packageName, Process.myUserHandle())
        stats.appBytes + stats.dataBytes + stats.cacheBytes
    }.getOrNull()

    companion object {
        private const val PERIOD_MS = 2_000L
        private const val DISK_EVERY = 15

        /** За [cpuMs] процессорного времени из [wallMs] обычного — доля от всех [cores] ядер, %. */
        fun cpuPercent(cpuMs: Long, wallMs: Long, cores: Int): Double? =
            if (wallMs <= 0 || cores <= 0) null else (cpuMs * 100.0 / wallMs / cores).coerceIn(0.0, 100.0)

        /** Телефоны отдают ток то в микро-, то в миллиамперах и с разным знаком: приводим к мА. */
        fun batteryMilliamps(raw: Int): Int? {
            if (raw == Int.MIN_VALUE || raw == 0) return null
            val value = abs(raw.toLong())
            return (if (value >= MICROAMP_THRESHOLD) value / 1000.0 else value.toDouble()).roundToInt()
        }

        private const val MICROAMP_THRESHOLD = 20_000L
    }
}
