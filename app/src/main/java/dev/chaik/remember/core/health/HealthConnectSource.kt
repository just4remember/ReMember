package dev.chaik.remember.core.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

/** Есть ли Health Connect на телефоне. */
enum class HealthAvailability { AVAILABLE, NEEDS_UPDATE, UNAVAILABLE }

/**
 * Чтение активности из Health Connect (туда пишет Samsung Health). Только чтение,
 * ничего не сохраняем у себя — данные всегда свежие из первоисточника.
 */
class HealthConnectSource(private val context: Context) {
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    val availability: HealthAvailability
        get() = when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NEEDS_UPDATE
            else -> HealthAvailability.UNAVAILABLE
        }

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    /** Отозвать у приложения все доступы Health Connect. */
    suspend fun revokeAllPermissions() = client.permissionController.revokeAllPermissions()

    suspend fun hasPermissions(): Boolean = client.permissionController.getGrantedPermissions().containsAll(permissions)

    /** Разрешение читать, пока приложение закрыто, — для уведомления о цели по шагам. */
    val backgroundPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
    )

    /** Умеет ли Health Connect на этом телефоне отдавать данные в фоне. */
    fun backgroundReadSupported(): Boolean =
        availability == HealthAvailability.AVAILABLE &&
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    suspend fun hasBackgroundPermission(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(backgroundPermissions)

    /** Шаги за сегодня до текущего момента — только сводка, без разбора записей. */
    suspend fun stepsToday(zone: ZoneId = ZoneId.systemDefault()): Long {
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val totals = client.aggregate(
            AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL), timeRangeFilter = TimeRangeFilter.between(start, Instant.now())),
        )
        return totals[StepsRecord.COUNT_TOTAL] ?: 0
    }

    /** Активность за [date]; сегодняшний день — до текущего момента. */
    /** [weightKg] — для оценки калорий, если Health Connect не знает активных калорий. */
    suspend fun day(date: LocalDate, weightKg: Double, zone: ZoneId = ZoneId.systemDefault()): DayActivity {
        val start = date.atStartOfDay(zone).toInstant()
        val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant(), Instant.now())
        if (!end.isAfter(start)) return DayActivity()
        val range = TimeRangeFilter.between(start, end)

        // Шаги и калории — сводкой: Health Connect сам убирает повторы (телефон + часы)
        val totals = client.aggregate(
            AggregateRequest(
                metrics = setOf(
                    StepsRecord.COUNT_TOTAL,
                    ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                ),
                timeRangeFilter = range,
            ),
        )
        val steps = readAll(StepsRecord::class, range)
        val sessions = readAll(ExerciseSessionRecord::class, range)
        if (date == LocalDate.now(zone)) logShape(steps, sessions)

        val stepCount = totals[StepsRecord.COUNT_TOTAL] ?: 0
        val time = ActivityMath.activeTime(
            steps = steps.map { StepsSpan(TimeSpan(it.startTime, it.endTime), it.count) },
            sessions = sessions.map { TimeSpan(it.startTime, it.endTime) },
            dayStart = start,
            dayEnd = end,
        )
        // Общие калории Health Connect не годятся: без записей он подставляет обмен веществ
        // в покое (~1500 ккал за день), и кольцо всегда было бы полным
        val active = totals[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
        return DayActivity(
            steps = stepCount,
            activeMinutes = time.total,
            calories = active ?: ActivityMath.estimateCalories(stepCount, time.workoutOnly, weightKg),
        )
    }

    private suspend fun <T : Record> readAll(type: KClass<T>, range: TimeRangeFilter): List<T> {
        val result = mutableListOf<T>()
        var token: String? = null
        do {
            val page = client.readRecords(ReadRecordsRequest(type, range, pageSize = PAGE_SIZE, pageToken = token))
            result += page.records
            token = page.pageToken
        } while (token != null)
        return result
    }

    /** Как Samsung Health нарезает шаги — по этому подбирается расчёт активного времени. */
    private fun logShape(steps: List<StepsRecord>, sessions: List<ExerciseSessionRecord>) {
        val lengths = steps.map { Duration.between(it.startTime, it.endTime).seconds }.sorted()
        Log.d(
            TAG,
            "steps records=${steps.size} sources=${steps.map { it.metadata.dataOrigin.packageName }.distinct()} " +
                "length s: min=${lengths.firstOrNull()} median=${lengths.getOrNull(lengths.size / 2)} max=${lengths.lastOrNull()}; " +
                "sessions=${sessions.size} types=${sessions.map { it.exerciseType }}",
        )
    }

    private companion object {
        const val TAG = "HealthData"
        const val PAGE_SIZE = 5000
    }
}
