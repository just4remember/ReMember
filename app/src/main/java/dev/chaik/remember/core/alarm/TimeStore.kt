package dev.chaik.remember.core.alarm

import android.content.Context
import android.os.UserManager
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Будильники, идущий звонок, таймер и секундомер — в SharedPreferences: из личного тут только
 * черновик сна до конца будильника, а читать это нужно сразу после перезагрузки. Хранилище привязано к устройству, а не к разблокировке:
 * телефон после ночного обновления стоит на экране блокировки, а будильник звонить уже должен.
 */
class TimeStore(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext().let { storage ->
        // Будильники из старых версий лежат в обычном хранилище. Оно закрыто до разблокировки — тогда перенос не удастся и повторится
        if (context.getSystemService(UserManager::class.java).isUserUnlocked) storage.moveSharedPreferencesFrom(context, PREFS)
        storage.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val _alarms = MutableStateFlow(read(KEY_ALARMS, ListSerializer(Alarm.serializer())) ?: emptyList())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    private val _session = MutableStateFlow(read(KEY_SESSION, RingSession.serializer()))
    val session: StateFlow<RingSession?> = _session.asStateFlow()

    private val _timer = MutableStateFlow(read(KEY_TIMER, TimerState.serializer()) ?: TimerState())
    val timer: StateFlow<TimerState> = _timer.asStateFlow()

    private val _stopwatch = MutableStateFlow(read(KEY_STOPWATCH, StopwatchState.serializer()) ?: StopwatchState())
    val stopwatch: StateFlow<StopwatchState> = _stopwatch.asStateFlow()

    fun alarm(id: Long): Alarm? = _alarms.value.firstOrNull { it.id == id }

    @Synchronized
    fun saveAlarm(alarm: Alarm) {
        val list = _alarms.value
        val next = if (list.any { it.id == alarm.id }) list.map { if (it.id == alarm.id) alarm else it } else list + alarm
        setAlarms(next)
    }

    @Synchronized
    fun deleteAlarm(id: Long) = setAlarms(_alarms.value.filterNot { it.id == id })

    fun newAlarmId(): Long = (_alarms.value.maxOfOrNull { it.id } ?: 0) + 1

    @Synchronized
    fun setSession(session: RingSession?) {
        _session.value = session
        write(KEY_SESSION, RingSession.serializer(), session)
    }

    @Synchronized
    fun updateSession(transform: (RingSession) -> RingSession) {
        _session.value?.let { setSession(transform(it)) }
    }

    @Synchronized
    fun setTimer(timer: TimerState) {
        _timer.value = timer
        write(KEY_TIMER, TimerState.serializer(), timer)
    }

    @Synchronized
    fun setStopwatch(stopwatch: StopwatchState) {
        _stopwatch.value = stopwatch
        write(KEY_STOPWATCH, StopwatchState.serializer(), stopwatch)
    }

    private fun setAlarms(list: List<Alarm>) {
        val sorted = list.sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
        _alarms.value = sorted
        write(KEY_ALARMS, ListSerializer(Alarm.serializer()), sorted)
    }

    private fun <T> read(key: String, serializer: KSerializer<T>): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }

    private fun <T> write(key: String, serializer: KSerializer<T>, value: T?) = prefs.edit(commit = true) {
        if (value == null) remove(key) else putString(key, json.encodeToString(serializer, value))
    }

    private companion object {
        const val PREFS = "time"
        const val KEY_ALARMS = "alarms"
        const val KEY_SESSION = "session"
        const val KEY_TIMER = "timer"
        const val KEY_STOPWATCH = "stopwatch"
        val json = Json { ignoreUnknownKeys = true }
    }
}
