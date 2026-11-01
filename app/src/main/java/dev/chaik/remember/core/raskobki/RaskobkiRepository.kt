package dev.chaik.remember.core.raskobki

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import java.security.KeyStore
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Как загрузился выпуск. */
sealed interface RaskobkiLoad {
    data class Ready(val puzzle: RaskobkiPuzzle) : RaskobkiLoad

    /** Выпуск закрыт или в незнакомом виде: его показывает только сайт. */
    data object OnlyOnSite : RaskobkiLoad

    /** Нет интернета или сервер не ответил. */
    data object Failed : RaskobkiLoad
}

data class RaskobkiAccount(val uid: String, val email: String, val name: String?)

/**
 * Ра[скобки]: выпуски, прогресс на телефоне и аккаунт сайта. Выпуск скачивается один раз и лежит
 * в кеше, данные аккаунта читаются не чаще раза в 5 минут — каждое чтение стоит автору игры денег.
 */
class RaskobkiRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var local = readProgress()
    private var results = emptyMap<LocalDate, RaskobkiState>()
    private val _progress = MutableStateFlow(local)
    private val _account = MutableStateFlow(readAccount())

    /** Прогресс по дням вместе с результатами аккаунта. */
    val progress: StateFlow<Map<LocalDate, RaskobkiState>> = _progress
    val account: StateFlow<RaskobkiAccount?> = _account

    private val _bracketColor = MutableStateFlow(readColor(KEY_BRACKET_COLOR))
    private val _answerColor = MutableStateFlow(readColor(KEY_ANSWER_COLOR))

    /** Цвет скобок ARGB, null — как акцент темы. */
    val bracketColor: StateFlow<Long?> = _bracketColor

    /** Цвет верных ответов ARGB, null — как в теме. */
    val answerColor: StateFlow<Long?> = _answerColor

    fun setBracketColor(color: Long?) {
        _bracketColor.value = color
        writeColor(KEY_BRACKET_COLOR, color)
    }

    fun setAnswerColor(color: Long?) {
        _answerColor.value = color
        writeColor(KEY_ANSWER_COLOR, color)
    }

    private fun readColor(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0) else null

    private fun writeColor(key: String, color: Long?) = prefs.edit {
        if (color == null) remove(key) else putLong(key, color)
    }

    /** Письмо ушло на этот адрес, ждём ссылку. Переживает уход в почту и перезапуск. */
    var pendingEmail: String?
        get() = prefs.getString(KEY_PENDING, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_PENDING) else putString(KEY_PENDING, value) }

    private val tokenLock = Mutex()
    private var tokens: RaskobkiTokens? = null
    private var syncedAt = 0L

    private val eventLock = Mutex()
    private val outbox = mutableMapOf<String, MutableList<JsonObject>>()
    private val sessions = mutableMapOf<String, Session>()
    private var lastServerTime = 0L
    private val clientId = UUID.randomUUID().toString().replace("-", "")

    private class Session(val id: String, val token: String, val expiresAt: Long)

    // ---- Выпуски ----

    suspend fun puzzle(day: LocalDate): RaskobkiLoad = withContext(Dispatchers.IO) {
        val cached = prefs.getString(puzzleKey(day), null)
        if (cached != null) {
            parseRaskobkiPuzzle(json.parseToJsonElement(cached).jsonObject)?.let { return@withContext RaskobkiLoad.Ready(it) }
        }
        val data = runCatching { RaskobkiApi.document("puzzles_public/$day-q1") }.getOrElse { error ->
            val closed = error is RaskobkiApiException && error.code in CLOSED_CODES
            return@withContext if (closed) RaskobkiLoad.OnlyOnSite else RaskobkiLoad.Failed
        }
        val puzzle = parseRaskobkiPuzzle(data) ?: return@withContext RaskobkiLoad.OnlyOnSite
        prefs.edit { putString(puzzleKey(day), data.toString()) }
        RaskobkiLoad.Ready(puzzle)
    }

    // ---- Прогресс ----

    fun save(day: LocalDate, state: RaskobkiState) {
        local = local + (day to state)
        publish()
        prefs.edit { putString(stateKey(day), json.encodeToString(RaskobkiState.serializer(), state)) }
    }

    /** Отправить прогресс выпуска в аккаунт. */
    fun push(puzzle: RaskobkiPuzzle) {
        if (_account.value == null) return
        scope.launch { runCatching { pushNow(puzzle) } }
    }

    /** Выпуск закрыт: сохранить результат и сообщить серверу. */
    fun complete(puzzle: RaskobkiPuzzle, elapsedMs: Long) {
        if (_account.value == null) return
        scope.launch {
            runCatching {
                trackNow(puzzle, "complete", elapsedMs = elapsedMs)
                pushNow(puzzle)
                publishResult(puzzle)
            }
        }
    }

    private fun publish() {
        // Результат аккаунта важнее незаконченного прогресса, законченный прогресс подробнее результата
        _progress.value = results + local.filter { (day, state) -> state.c || day !in results }
    }

    private fun pushNow(puzzle: RaskobkiPuzzle) {
        val account = _account.value ?: return
        val token = blockingToken() ?: return
        val state = local[puzzle.day] ?: return
        RaskobkiApi.write(
            "users_private/${account.uid}/progress/${puzzle.day}-0",
            firestoreMap(progressRecord(puzzle, state)),
            token,
        )
    }

    private fun publishResult(puzzle: RaskobkiPuzzle) {
        val account = _account.value ?: return
        val token = blockingToken() ?: return
        val state = local[puzzle.day] ?: return
        val grade = RaskobkiGrade.of(state.score)
        val fields = firestoreMap(
            buildJsonObject {
                put("schemaVersion", 1)
                put("puzzleId", puzzle.puzzleId)
                put("artifactId", puzzle.artifactId)
                put("contentRevision", puzzle.contentRevision)
                put("scoringVersion", puzzle.scoringVersion)
                put("completed", true)
                put("score", state.score)
                put("preservationRoman", grade.roman)
                put("preservationName", grade.title)
                put("elapsedSeconds", state.e)
                put("wrongAttempts", state.w)
                put("peekHints", state.h.size)
                put("revealHints", state.rv.size)
                put("peekHintNodeIds", strings(state.h))
                put("revealHintNodeIds", strings(state.rv))
                put("source", "client-unverified")
            },
        ) + ("completedAt" to timestamp(state.completedAt ?: System.currentTimeMillis()))
        RaskobkiApi.write("public_results/${account.uid}/puzzles/${puzzle.puzzleId}", JsonObject(fields), token)
    }

    private fun timestamp(millis: Long) = buildJsonObject { put("timestampValue", Instant.ofEpochMilli(millis).toString()) }

    private fun progressRecord(puzzle: RaskobkiPuzzle, state: RaskobkiState) = buildJsonObject {
        put("schemaVersion", 1)
        put("dayKey", puzzle.day.toString())
        put("slot", 0)
        put("puzzleId", puzzle.puzzleId)
        put("artifactId", puzzle.artifactId)
        put("contentRevision", puzzle.contentRevision)
        put("scoringVersion", puzzle.scoringVersion)
        put("state", buildJsonObject {
            put("s", strings(state.s))
            put("h", strings(state.h))
            put("rv", strings(state.rv))
            put("x", JsonObject(state.x.mapValues { JsonPrimitive(it.value) }))
            put("a", strings(state.a))
            put("w", state.w)
            put("e", state.e)
            put("c", state.c)
            state.completedAt?.let { put("completedAt", it) }
        })
        put("source", "local-client")
    }

    private fun strings(values: List<String>) = JsonArray(values.map(::JsonPrimitive))

    private fun readProgress(): Map<LocalDate, RaskobkiState> =
        prefs.all.entries.mapNotNull { (key, value) ->
            if (!key.startsWith(STATE_PREFIX) || value !is String) return@mapNotNull null
            runCatching {
                LocalDate.parse(key.removePrefix(STATE_PREFIX)) to json.decodeFromString(RaskobkiState.serializer(), value)
            }.getOrNull()
        }.toMap()

    // ---- Аккаунт ----

    suspend fun sendSignInMail(email: String) = withContext(Dispatchers.IO) {
        RaskobkiApi.sendSignInMail(email)
        pendingEmail = email
    }

    suspend fun signIn(email: String, oobCode: String) = withContext(Dispatchers.IO) {
        val signedIn = RaskobkiApi.signIn(email, oobCode)
        store(signedIn)
        pendingEmail = null
        prefs.edit {
            putString(KEY_UID, signedIn.uid)
            putString(KEY_EMAIL, email)
        }
        _account.value = RaskobkiAccount(signedIn.uid, email, null)
        syncedAt = 0L
        runCatching { sync() }
    }

    fun signOut() {
        tokens = null
        sessions.clear()
        outbox.clear()
        syncedAt = 0L
        local = emptyMap()
        results = emptyMap()
        publish()
        _account.value = null
        prefs.edit {
            prefs.all.keys.filter { it.startsWith(STATE_PREFIX) }.forEach(::remove)
            remove(KEY_UID)
            remove(KEY_EMAIL)
            remove(KEY_NAME)
            remove(KEY_TOKEN)
        }
    }

    /** Подтянуть данные аккаунта, но не чаще раза в [SYNC_INTERVAL_MS]: каждое чтение платное. */
    suspend fun sync() = withContext(Dispatchers.IO) {
        val account = _account.value ?: return@withContext
        if (SystemClock.elapsedRealtime() - syncedAt < SYNC_INTERVAL_MS && syncedAt != 0L) return@withContext
        val token = blockingToken() ?: return@withContext
        val name = runCatching {
            (RaskobkiApi.document("public_profiles/${account.uid}", token)["displayName"] as? JsonPrimitive)?.contentOrNull
        }.getOrNull()
        if (name != null) {
            prefs.edit { putString(KEY_NAME, name) }
            _account.value = account.copy(name = name)
        }
        val cloud = runCatching { RaskobkiApi.collection("users_private/${account.uid}", "progress", token) }
            .onFailure { logFailure("progress", it) }.getOrThrow()
        cloud.values.forEach { record ->
            val day = runCatching { LocalDate.parse((record["dayKey"] as? JsonPrimitive)?.contentOrNull) }.getOrNull() ?: return@forEach
            val state = parseCloudState(record["state"] as? JsonObject ?: return@forEach)
            val merged = local[day]?.let { mergeStates(it, state) } ?: state
            save(day, merged)
        }
        val published = runCatching { RaskobkiApi.collection("public_results/${account.uid}", "puzzles", token) }
            .onFailure { logFailure("results", it) }.getOrThrow()
        results = published.mapNotNull { (id, result) ->
            val day = runCatching { LocalDate.parse(id.take(DATE_LENGTH)) }.getOrNull() ?: return@mapNotNull null
            day to RaskobkiState(
                h = result.stringList("peekHintNodeIds"),
                rv = result.stringList("revealHintNodeIds"),
                w = result.number("wrongAttempts").toInt(),
                e = result.number("elapsedSeconds"),
                completedAt = millis(result["completedAt"]),
                c = true,
            )
        }.toMap()
        publish()
        syncedAt = SystemClock.elapsedRealtime()
    }

    /** В журнал идут только код и текст ошибки сервера. */
    private fun logFailure(what: String, error: Throwable) {
        val code = (error as? RaskobkiApiException)?.code
        Log.w(TAG, "sync $what failed: $code ${error.javaClass.simpleName} ${error.message}")
    }

    private fun readAccount(): RaskobkiAccount? {
        val uid = prefs.getString(KEY_UID, null) ?: return null
        return RaskobkiAccount(uid, prefs.getString(KEY_EMAIL, null).orEmpty(), prefs.getString(KEY_NAME, null))
    }

    private fun parseCloudState(state: JsonObject) = RaskobkiState(
        s = state.stringList("s"),
        h = state.stringList("h"),
        rv = state.stringList("rv"),
        x = (state["x"] as? JsonObject).orEmpty().mapNotNull { (key, value) ->
            (value as? JsonPrimitive)?.contentOrNull?.let { key to it }
        }.toMap(),
        a = state.stringList("a"),
        w = state.number("w").toInt(),
        e = state.number("e"),
        completedAt = millis(state["completedAt"]),
        c = (state["c"] as? JsonPrimitive)?.booleanOrNull == true,
    )

    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

    private fun JsonObject.number(key: String): Long =
        (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() } ?: 0

    private fun millis(value: JsonElement?): Long? {
        val primitive = value as? JsonPrimitive ?: return null
        if (value is JsonNull) return null
        return primitive.longOrNull ?: runCatching { Instant.parse(primitive.content).toEpochMilli() }.getOrNull()
    }

    // ---- Токены ----

    private fun blockingToken(): String? = kotlinx.coroutines.runBlocking { idToken() }

    private suspend fun idToken(): String? = tokenLock.withLock {
        tokens?.takeIf { it.expiresAt > System.currentTimeMillis() + TOKEN_MARGIN_MS }?.let { return it.idToken }
        val refresh = tokens?.refreshToken ?: readRefreshToken() ?: return null
        val fresh = try {
            RaskobkiApi.refresh(refresh)
        } catch (error: RaskobkiApiException) {
            // Вход отозван — нужно войти заново; прогресс на телефоне остаётся
            if (error.code == 400) {
                _account.value = null
                tokens = null
                prefs.edit {
                    remove(KEY_UID)
                    remove(KEY_TOKEN)
                }
            }
            return null
        } catch (_: Exception) {
            return null
        }
        store(fresh)
        fresh.idToken
    }

    private fun store(value: RaskobkiTokens) {
        tokens = value
        prefs.edit { putString(KEY_TOKEN, encrypt(value.refreshToken)) }
    }

    private fun readRefreshToken(): String? =
        prefs.getString(KEY_TOKEN, null)?.let { runCatching { decrypt(it) }.getOrNull() }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_SIZE))
        return String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE))
    }

    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    // ---- События игры, как на сайте: античит и общий таймер ----

    /** Записать событие игры. [onElapsed] получает время с сервера. */
    fun track(
        puzzle: RaskobkiPuzzle,
        action: String,
        input: String = "",
        nodeId: String? = null,
        hintType: String? = null,
        elapsedMs: Long,
        onElapsed: (Double) -> Unit = {},
    ) {
        if (_account.value == null) return
        scope.launch { runCatching { trackNow(puzzle, action, input, nodeId, hintType, elapsedMs)?.let { applyServer(puzzle, it, onElapsed) } } }
    }

    fun syncTimer(puzzle: RaskobkiPuzzle, active: Boolean, onElapsed: (Double) -> Unit = {}) {
        if (_account.value == null) return
        scope.launch {
            runCatching {
                val token = idToken() ?: return@launch
                val session = eventLock.withLock { session(puzzle, token) }
                val data = JsonObject(
                    identity(puzzle) + mapOf(
                        "sessionId" to JsonPrimitive(session.id),
                        "token" to JsonPrimitive(session.token),
                        "active" to JsonPrimitive(active),
                    ),
                )
                applyServer(puzzle, RaskobkiApi.call("syncProgressTimer", data, token), onElapsed)
            }
        }
    }

    /** Верный ответ принят: уточнить написание слова у сервера. */
    fun answerAccepted(puzzle: RaskobkiPuzzle, nodeId: String, raw: String, solvedBefore: List<String>, elapsedMs: Long) {
        scope.launch {
            val response = runCatching { trackNow(puzzle, "answer", raw, nodeId, null, elapsedMs) }.getOrNull()
            response?.let { applyServer(puzzle, it) {} }
            if (response != null && canonical(response, nodeId) != null) return@launch
            val data = JsonObject(
                identity(puzzle) + mapOf(
                    "nodeId" to JsonPrimitive(nodeId),
                    "input" to JsonPrimitive(raw.take(MAX_INPUT)),
                    "solvedNodeIds" to strings(solvedBefore),
                ),
            )
            val verified = runCatching { RaskobkiApi.call("verifyPuzzleAnswer", data, idToken()) }.getOrNull() ?: return@launch
            canonical(verified, nodeId)?.let { setAnswer(puzzle.day, nodeId, it) }
        }
    }

    /** Открыть скобку целиком: ответ знает только сервер. null — не удалось. */
    suspend fun reveal(puzzle: RaskobkiPuzzle, nodeId: String, solved: List<String>, elapsedMs: Long): String? = withContext(Dispatchers.IO) {
        val response = runCatching { trackNow(puzzle, "hint", "", nodeId, "reveal", elapsedMs) }.getOrNull()
        response?.let { canonical(it, nodeId) }?.let { return@withContext it }
        val data = JsonObject(
            identity(puzzle) + mapOf("nodeId" to JsonPrimitive(nodeId), "solvedNodeIds" to strings(solved)),
        )
        runCatching { RaskobkiApi.call("revealPuzzleHint", data, idToken()) }.getOrNull()
            ?.let { (it["answer"] as? JsonPrimitive)?.contentOrNull }
    }

    private suspend fun trackNow(
        puzzle: RaskobkiPuzzle,
        action: String,
        input: String = "",
        nodeId: String? = null,
        hintType: String? = null,
        elapsedMs: Long,
    ): JsonObject? = eventLock.withLock {
        if (_account.value == null) return null
        val token = idToken() ?: return null
        val pending = outbox.getOrPut(puzzle.puzzleId) { mutableListOf() }
        val state = local[puzzle.day] ?: RaskobkiState()
        pending += buildJsonObject {
            put("eventId", UUID.randomUUID().toString().replace("-", ""))
            put("action", action)
            put("input", input.take(MAX_INPUT))
            put("nodeId", nodeId)
            put("hintType", hintType)
            put("readyNodeIds", strings(puzzle.ready(state.s).map { it.id }))
            put("elapsedMs", elapsedMs)
            put("clientTimestamp", System.currentTimeMillis())
        }
        val session = session(puzzle, token)
        var last: JsonObject? = null
        while (pending.isNotEmpty()) {
            val batch = pending.take(MAX_BATCH)
            val data = JsonObject(
                identity(puzzle) + mapOf(
                    "sessionId" to JsonPrimitive(session.id),
                    "token" to JsonPrimitive(session.token),
                    "events" to JsonArray(batch),
                ),
            )
            val response = RaskobkiApi.call("recordProgressEvents", data, token)
            pending.removeAll(batch.toSet())
            last = response
        }
        last
    }

    private fun session(puzzle: RaskobkiPuzzle, token: String): Session {
        sessions[puzzle.puzzleId]?.takeIf { it.expiresAt > System.currentTimeMillis() + SESSION_MARGIN_MS }?.let { return it }
        val data = JsonObject(identity(puzzle) + ("clientInstanceId" to JsonPrimitive(clientId)))
        val response = RaskobkiApi.call("startProgressSession", data, token)
        val session = Session(
            id = response.getValue("sessionId").jsonPrimitive.content,
            token = response.getValue("token").jsonPrimitive.content,
            expiresAt = (response["expiresAt"] as? JsonPrimitive)?.longOrNull ?: (System.currentTimeMillis() + SESSION_FALLBACK_MS),
        )
        sessions[puzzle.puzzleId] = session
        return session
    }

    private fun identity(puzzle: RaskobkiPuzzle): JsonObject = buildJsonObject {
        put("puzzleId", puzzle.puzzleId)
        put("dayKey", puzzle.day.toString())
        put("slot", 0)
        put("artifactId", puzzle.artifactId)
        put("contentRevision", puzzle.contentRevision)
        put("scoringVersion", puzzle.scoringVersion)
    }

    private fun canonical(response: JsonObject, nodeId: String): String? {
        (response["accepted"] as? JsonArray)?.forEach { item ->
            val entry = item as? JsonObject ?: return@forEach
            if ((entry["matchedNodeId"] as? JsonPrimitive)?.contentOrNull == nodeId) {
                (entry["matchedAnswer"] as? JsonPrimitive)?.contentOrNull?.let { return it }
            }
        }
        if ((response["matchedNodeId"] as? JsonPrimitive)?.contentOrNull == nodeId) {
            (response["matchedAnswer"] as? JsonPrimitive)?.contentOrNull?.let { return it }
        }
        return ((response["canonicalAnswers"] as? JsonObject)?.get(nodeId) as? JsonPrimitive)?.contentOrNull
    }

    @Synchronized
    private fun setAnswer(day: LocalDate, nodeId: String, answer: String) {
        val state = local[day] ?: return
        save(day, state.copy(x = state.x + (nodeId to answer)))
    }

    /** Ответ сервера: отметки решённых скобок, написание слов, время. */
    @Synchronized
    private fun applyServer(puzzle: RaskobkiPuzzle, response: JsonObject, onElapsed: (Double) -> Unit) {
        val state = local[puzzle.day] ?: return
        val answers = (response["canonicalAnswers"] as? JsonObject).orEmpty().mapNotNull { (key, value) ->
            (value as? JsonPrimitive)?.contentOrNull?.let { key to it }
        }.toMap()
        val merged = state.copy(
            s = (state.s + response.stringList("solvedNodeIds")).distinct(),
            h = (state.h + response.stringList("hintedNodeIds")).distinct(),
            rv = (state.rv + response.stringList("revealedNodeIds")).distinct(),
            x = state.x + answers,
        )
        if (merged != state) save(puzzle.day, merged)
        val elapsed = (response["elapsedSeconds"] as? JsonPrimitive)?.doubleOrNull ?: return
        val serverTime = (response["serverTimeMs"] as? JsonPrimitive)?.longOrNull ?: 0
        if (state.c || serverTime != 0L && serverTime < lastServerTime) return
        if (serverTime != 0L) lastServerTime = serverTime
        onElapsed(elapsed)
    }

    private fun puzzleKey(day: LocalDate) = "$PUZZLE_PREFIX$day"
    private fun stateKey(day: LocalDate) = "$STATE_PREFIX$day"

    private companion object {
        const val TAG = "Raskobki"
        const val PREFS = "raskobki"
        const val PUZZLE_PREFIX = "puzzle_"
        const val STATE_PREFIX = "state_"
        const val KEY_PENDING = "pending_email"
        const val KEY_UID = "uid"
        const val KEY_EMAIL = "email"
        const val KEY_NAME = "name"
        const val KEY_TOKEN = "refresh_token"
        const val KEY_BRACKET_COLOR = "bracket_color"
        const val KEY_ANSWER_COLOR = "answer_color"
        const val DATE_LENGTH = 10
        const val MAX_INPUT = 160
        const val MAX_BATCH = 50
        const val SYNC_INTERVAL_MS = 5 * 60_000L
        const val TOKEN_MARGIN_MS = 60_000L
        const val SESSION_MARGIN_MS = 60_000L
        const val SESSION_FALLBACK_MS = 3_600_000L
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "remember_raskobki_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        val CLOSED_CODES = setOf(403, 404)
        val json = Json { ignoreUnknownKeys = true }
    }
}
