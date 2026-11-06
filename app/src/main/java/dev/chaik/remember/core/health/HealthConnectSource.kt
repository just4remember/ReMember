package dev.chaik.remember.core.health

import java.time.Period
import java.time.LocalDateTime
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
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

    /** Чтение старше 30 дней до первого доступа — отдельное разрешение Health Connect. */
    val historyPermission: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

    /** Умеет ли Health Connect на этом телефоне отдавать историю старше 30 дней. */
    fun historyReadSupported(): Boolean =
        availability == HealthAvailability.AVAILABLE &&
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    suspend fun hasHistoryPermission(): Boolean = historyPermission in client.permissionController.getGrantedPermissions()

    /**
     * Активность по дням [from]..[to] (сегодня — до текущего момента) одним проходом: шаги и
     * калории — сводкой по суткам, активные минуты — по записям, как в [day]. Дни без данных — нули.
     */
    suspend fun days(from: LocalDate, to: LocalDate, weightKg: Double, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, DayActivity> {
        val now = Instant.now()
        val start = from.atStartOfDay(zone).toInstant()
        val end = minOf(to.plusDays(1).atStartOfDay(zone).toInstant(), now)
        if (!end.isAfter(start)) return emptyMap()

        // Сводка по суткам: Health Connect сам убирает повторы (телефон + часы)
        val totals = byDay(from, LocalDateTime.ofInstant(end, zone), setOf(StepsRecord.COUNT_TOTAL, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL))

        val range = TimeRangeFilter.between(start, end)
        val steps = readAll(StepsRecord::class, range).map { StepsSpan(TimeSpan(it.startTime, it.endTime), it.count) }
        val sessions = readAll(ExerciseSessionRecord::class, range).map { TimeSpan(it.startTime, it.endTime) }
        // Записи — по дням начала; кусок через полночь попадёт и в следующий день
        val stepsByDay = steps.groupBy { LocalDate.ofInstant(it.span.start, zone) }
        val sessionsByDay = sessions.groupBy { LocalDate.ofInstant(it.start, zone) }

        val result = LinkedHashMap<LocalDate, DayActivity>()
        var date = from
        while (!date.isAfter(to)) {
            val dayStart = date.atStartOfDay(zone).toInstant()
            val dayEnd = minOf(date.plusDays(1).atStartOfDay(zone).toInstant(), now)
            if (!dayEnd.isAfter(dayStart)) break
            val previous = date.minusDays(1)
            val time = ActivityMath.activeTime(
                steps = stepsByDay[date].orEmpty() + stepsByDay[previous].orEmpty().filter { it.span.end.isAfter(dayStart) },
                sessions = sessionsByDay[date].orEmpty() + sessionsByDay[previous].orEmpty().filter { it.end.isAfter(dayStart) },
                dayStart = dayStart,
                dayEnd = dayEnd,
            )
            val day = totals[date]
            val stepCount = day?.get(StepsRecord.COUNT_TOTAL) ?: 0
            result[date] = DayActivity(
                steps = stepCount,
                activeMinutes = time.total,
                calories = day?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories
                    ?: ActivityMath.estimateCalories(stepCount, time.workoutOnly, weightKg),
            )
            date = date.plusDays(1)
        }
        return result
    }

    /** Только шаги по дням — для тепловой карты года: одна сводка, без записей. */
    suspend fun dailySteps(from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, Long> {
        val end = minOf(to.plusDays(1).atStartOfDay(zone).toInstant(), Instant.now())
        if (!end.isAfter(from.atStartOfDay(zone).toInstant())) return emptyMap()
        return byDay(from, LocalDateTime.ofInstant(end, zone), setOf(StepsRecord.COUNT_TOTAL))
            .mapNotNull { (date, result) -> result[StepsRecord.COUNT_TOTAL]?.let { date to it } }.toMap()
    }

    /** Первый день с шагами начиная с [since]: сначала ищем месяц, потом день в нём. null — шагов нет. */
    suspend fun firstStepsDate(since: LocalDate, zone: ZoneId = ZoneId.systemDefault()): LocalDate? {
        val now = LocalDateTime.now(zone)
        val month = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(since.atStartOfDay(), now),
                timeRangeSlicer = Period.ofMonths(1),
            ),
        ).firstOrNull { (it.result[StepsRecord.COUNT_TOTAL] ?: 0) > 0 }?.startTime?.toLocalDate() ?: return null
        return dailySteps(month, month.plusMonths(1).minusDays(1), zone).filterValues { it > 0 }.keys.minOrNull() ?: month
    }

    /** Шаги по часам суток за период: [0] — с полуночи до часу ночи и т. д. */
    suspend fun stepsByHour(from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): LongArray {
        val hours = LongArray(24)
        val start = from.atStartOfDay(zone).toInstant()
        val end = minOf(to.plusDays(1).atStartOfDay(zone).toInstant(), Instant.now())
        if (!end.isAfter(start)) return hours
        // Кусками: на слишком много интервалов за раз Health Connect отвечает ошибкой
        var chunk = start
        while (chunk.isBefore(end)) {
            val chunkEnd = minOf(chunk.plus(Duration.ofDays(CHUNK_DAYS)), end)
            client.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(chunk, chunkEnd),
                    timeRangeSlicer = Duration.ofHours(1),
                ),
            ).forEach { group ->
                val count = group.result[StepsRecord.COUNT_TOTAL] ?: return@forEach
                hours[group.startTime.atZone(zone).hour] += count
            }
            chunk = chunkEnd
        }
        return hours
    }

    /** Сводка по суткам от [from] до [until] — кусками по [CHUNK_DAYS] дней. */
    private suspend fun byDay(
        from: LocalDate,
        until: LocalDateTime,
        metrics: Set<androidx.health.connect.client.aggregate.AggregateMetric<*>>,
    ): Map<LocalDate, androidx.health.connect.client.aggregate.AggregationResult> {
        val result = HashMap<LocalDate, androidx.health.connect.client.aggregate.AggregationResult>()
        var chunk = from.atStartOfDay()
        while (chunk.isBefore(until)) {
            val chunkEnd = minOf(chunk.plusDays(CHUNK_DAYS), until)
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(metrics, TimeRangeFilter.between(chunk, chunkEnd), Period.ofDays(1)),
            ).forEach { result[it.startTime.toLocalDate()] = it.result }
            chunk = chunkEnd
        }
        return result
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

        /** Сколько дней в одном запросе сводки: на большее число интервалов Health Connect отвечает ошибкой. */
        const val CHUNK_DAYS = 30L
    }
}
