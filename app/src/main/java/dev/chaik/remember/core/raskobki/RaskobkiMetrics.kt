package dev.chaik.remember.core.raskobki

import android.content.Context
import androidx.core.content.edit
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Событие игры. Только игра: ни текста ответов, ни данных дневника, ни данных об устройстве. */
@Serializable
data class RaskobkiMetric(val name: String, val at: Long, val params: Map<String, String>)

/** Что уходит автору игры одним запросом. */
@Serializable
data class RaskobkiMetricBatch(
    val client: String,
    val version: String,
    val installId: String,
    val events: List<RaskobkiMetric>,
)

/** Очередь не растёт без конца: при переполнении теряются самые старые события. */
fun appendMetric(queue: List<RaskobkiMetric>, metric: RaskobkiMetric, cap: Int = MAX_QUEUE): List<RaskobkiMetric> =
    (queue + metric).takeLast(cap)

fun openedMetric(puzzle: RaskobkiPuzzle, at: Long) =
    RaskobkiMetric("puzzle_opened", at, mapOf("puzzle_id" to puzzle.puzzleId))

fun progressMetric(puzzle: RaskobkiPuzzle, nodeId: String, at: Long) = RaskobkiMetric(
    "puzzle_progress",
    at,
    mapOf(
        "puzzle_id" to puzzle.puzzleId,
        "bracket_id" to puzzle.analyticsId(nodeId).orEmpty(),
    ),
)

fun completedMetric(puzzle: RaskobkiPuzzle, state: RaskobkiState, at: Long) = RaskobkiMetric(
    "puzzle_completed",
    at,
    mapOf(
        "puzzle_id" to puzzle.puzzleId,
        "score" to state.score.toString(),
        "elapsed_seconds" to state.e.toString(),
        "wrong_attempts" to state.w.toString(),
        "peek_hints" to state.h.size.toString(),
        "reveal_hints" to state.rv.size.toString(),
    ),
)

private const val MAX_QUEUE = 500

/**
 * Метрики Ра[скобок] для автора игры. Собираются только после согласия, отказ стирает всё накопленное.
 * Пока автор не дал адрес ([ENDPOINT] пуст), события лежат на телефоне и никуда не уходят.
 */
class RaskobkiMetrics(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
    private val _consent = MutableStateFlow(if (prefs.contains(KEY_CONSENT)) prefs.getBoolean(KEY_CONSENT, false) else null)

    /** null — ещё не спрашивали. */
    val consent: StateFlow<Boolean?> = _consent

    fun accept() {
        prefs.edit {
            putBoolean(KEY_CONSENT, true)
            putString(KEY_INSTALL, UUID.randomUUID().toString())
        }
        _consent.value = true
    }

    fun decline() {
        prefs.edit {
            clear()
            putBoolean(KEY_CONSENT, false)
        }
        _consent.value = false
    }

    fun opened(puzzle: RaskobkiPuzzle) {
        val key = "$OPENED_PREFIX${puzzle.puzzleId}"
        if (_consent.value != true || prefs.getBoolean(key, false)) return
        prefs.edit { putBoolean(key, true) }
        record(openedMetric(puzzle, System.currentTimeMillis()))
    }

    fun progress(puzzle: RaskobkiPuzzle, nodeId: String) = record(progressMetric(puzzle, nodeId, System.currentTimeMillis()))

    fun completed(puzzle: RaskobkiPuzzle, state: RaskobkiState) = record(completedMetric(puzzle, state, System.currentTimeMillis()))

    /** Отправить накопленное, когда появится адрес. Неотправленное остаётся в очереди. */
    suspend fun flush() = withContext(Dispatchers.IO) {
        val endpoint = ENDPOINT ?: return@withContext
        if (_consent.value != true) return@withContext
        val events = queue()
        if (events.isEmpty()) return@withContext
        val batch = RaskobkiMetricBatch(CLIENT, version, prefs.getString(KEY_INSTALL, null).orEmpty(), events)
        runCatching {
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(json.encodeToString(RaskobkiMetricBatch.serializer(), batch).toByteArray()) }
                if (connection.responseCode in 200..299) save(queue().drop(events.size))
            } finally {
                connection.disconnect()
            }
        }
    }

    @Synchronized
    private fun record(metric: RaskobkiMetric) {
        if (_consent.value == true) save(appendMetric(queue(), metric))
    }

    private fun queue(): List<RaskobkiMetric> = prefs.getString(KEY_QUEUE, null)
        ?.let { runCatching { json.decodeFromString(QUEUE_SERIALIZER, it) }.getOrNull() }
        .orEmpty()

    private fun save(events: List<RaskobkiMetric>) = prefs.edit { putString(KEY_QUEUE, json.encodeToString(QUEUE_SERIALIZER, events)) }

    private companion object {
        /** Адрес от автора игры. Пока его нет, ничего не отправляется. */
        val ENDPOINT: String? = null
        const val CLIENT = "remember-android"
        const val PREFS = "raskobki_metrics"
        const val KEY_CONSENT = "consent"
        const val KEY_INSTALL = "install_id"
        const val KEY_QUEUE = "queue"
        const val OPENED_PREFIX = "opened_"
        const val TIMEOUT_MS = 15_000
        val QUEUE_SERIALIZER = ListSerializer(RaskobkiMetric.serializer())
        val json = Json { ignoreUnknownKeys = true }
    }
}
