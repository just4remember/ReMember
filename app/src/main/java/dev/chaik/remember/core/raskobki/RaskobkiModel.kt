package dev.chaik.remember.core.raskobki

import java.net.URLDecoder
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Выпуски выходят по московскому времени. */
val RaskobkiZone: ZoneId = ZoneId.of("Europe/Moscow")
val RaskobkiStart: LocalDate = LocalDate.of(2026, 9, 1)

fun raskobkiToday(): LocalDate = LocalDate.now(RaskobkiZone)

sealed interface RaskobkiPart
data class RaskobkiText(val text: String) : RaskobkiPart
data class RaskobkiNode(
    val id: String,
    val parts: List<RaskobkiPart>,
    val hashes: List<String>,
    val firstLetter: String,
) : RaskobkiPart {
    val children: List<RaskobkiNode> get() = parts.filterIsInstance<RaskobkiNode>()
}

data class RaskobkiPuzzle(
    val day: LocalDate,
    val puzzleId: String,
    val artifactId: String,
    val contentRevision: Long,
    val scoringVersion: Long,
    val fact: String,
    val sourceUrl: String?,
    val era: String,
    val region: String,
    val cardText: String,
    val root: List<RaskobkiPart>,
) {
    val total: Int get() = nodes().size

    fun nodes(): List<RaskobkiNode> = buildList { walk(root) { add(it) } }

    /** Скобки, на которые уже можно ответить: сами не решены, а все вложенные решены. */
    fun ready(solved: Collection<String>): List<RaskobkiNode> =
        nodes().filter { node -> node.id !in solved && node.children.all { it.id in solved } }

    /** Скобка, к которой подходит ответ. */
    fun match(raw: String, solved: Collection<String>): RaskobkiNode? {
        val hash = sha256(normalizeAnswer(raw))
        return ready(solved).firstOrNull { hash in it.hashes }
    }

    /** Номер скобки по положению в дереве, как на сайте: b0.1. Текста ответа в нём нет. */
    fun analyticsId(nodeId: String): String? {
        fun find(parts: List<RaskobkiPart>, path: List<Int>): List<Int>? {
            parts.filterIsInstance<RaskobkiNode>().forEachIndexed { index, node ->
                if (node.id == nodeId) return path + index
                find(node.parts, path + index)?.let { return it }
            }
            return null
        }
        return find(root, emptyList())?.joinToString(".", prefix = "b")
    }

    private fun walk(parts: List<RaskobkiPart>, visit: (RaskobkiNode) -> Unit) {
        parts.filterIsInstance<RaskobkiNode>().forEach {
            visit(it)
            walk(it.parts, visit)
        }
    }
}

/** Прогресс выпуска; короткие имена полей — как на сайте. */
@Serializable
data class RaskobkiState(
    /** Решённые скобки. */
    val s: List<String> = emptyList(),
    /** Показанные первые буквы. */
    val h: List<String> = emptyList(),
    /** Открытые целиком скобки. */
    val rv: List<String> = emptyList(),
    /** Введённые ответы по скобкам. */
    val x: Map<String, String> = emptyMap(),
    /** Уже опробованные неверные слова. */
    val a: List<String> = emptyList(),
    /** Ошибок. */
    val w: Int = 0,
    /** Секунд в игре. */
    val e: Long = 0,
    val completedAt: Long? = null,
    val c: Boolean = false,
) {
    val score: Int get() = (100 - w * 2 - h.size * 5 - rv.size * 15).coerceAtLeast(0)
}

/** Сохранность находки по очкам. */
enum class RaskobkiGrade(val roman: String, val min: Int, val title: String, val note: String) {
    V("V", 95, "Исключительная сохранность", "Практически идеальное прохождение. Находка восстановлена почти целиком."),
    IV("IV", 85, "Отличная сохранность", "Небольшие потери, но основная форма находки почти полностью восстановлена."),
    III("III", 70, "Хорошая сохранность", "Основная форма восстановлена из нескольких уверенно собранных фрагментов."),
    II("II", 50, "Фрагментарная сохранность", "Смысл найден, хотя расчистка была непростой."),
    I("I", 0, "Следы находки", "Факт раскрыт с трудом — но это всё равно настоящая находка.");

    companion object {
        fun of(score: Int): RaskobkiGrade = entries.first { score >= it.min }
    }
}

fun normalizeAnswer(value: String): String =
    value.trim().lowercase().replace('ё', 'е').replace(Regex("\\s+"), " ")

/** Ответ — русские слова, разделённые пробелом или дефисом. */
fun isValidAnswer(value: String): Boolean =
    Regex("^[А-Яа-яЁё]+(?:[ -][А-Яа-яЁё]+)*$").matches(normalizeAnswer(value))

fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }

/** Ключ неверной попытки: повтор того же слова не считается новой ошибкой. */
fun attemptKey(normalized: String): String {
    var hash = 2166136261L
    normalized.forEach { hash = ((hash xor it.code.toLong()) * 16777619L) and 0xFFFFFFFFL }
    return hash.toString(36)
}

/**
 * Серия: дни подряд, закрытые в день выхода своего выпуска. Идёт, пока последний такой день —
 * сегодня или вчера. Возвращает длину текущей серии и дни, входящие в неё.
 */
fun raskobkiStreak(completed: Map<LocalDate, RaskobkiState>, today: LocalDate): List<LocalDate> {
    val days = completed.filter { (day, state) -> state.c && countsForStreak(day, state.completedAt) }.keys
    var cursor = if (today in days) today else today.minusDays(1)
    return buildList {
        while (cursor in days) {
            add(cursor)
            cursor = cursor.minusDays(1)
        }
    }
}

/** Результат аккаунта важнее незаконченного прогресса, законченный прогресс подробнее результата. */
fun mergeWithResults(results: Map<LocalDate, RaskobkiState>, local: Map<LocalDate, RaskobkiState>): Map<LocalDate, RaskobkiState> =
    results + local.filter { (day, state) -> state.c || day !in results }

/** Сохранённые состояния из записей вида «префикс + дата»; битые записи пропускаются. */
fun parseSavedStates(entries: Map<String, *>, prefix: String): Map<LocalDate, RaskobkiState> =
    entries.entries.mapNotNull { (key, value) ->
        if (!key.startsWith(prefix) || value !is String) return@mapNotNull null
        runCatching {
            LocalDate.parse(key.removePrefix(prefix)) to SavedStateJson.decodeFromString(RaskobkiState.serializer(), value)
        }.getOrNull()
    }.toMap()

private val SavedStateJson = Json { ignoreUnknownKeys = true }

/** Средний счёт и личный рекорд по решённым выпускам, как в профиле на сайте. */
data class RaskobkiStats(val average: Int, val best: Int)

fun raskobkiStats(states: Collection<RaskobkiState>): RaskobkiStats {
    val scores = states.filter { it.c }.map { it.score }
    return RaskobkiStats(
        average = if (scores.isEmpty()) 0 else scores.average().roundToInt(),
        best = scores.maxOrNull() ?: 0,
    )
}

private fun countsForStreak(day: LocalDate, completedAt: Long?): Boolean {
    if (completedAt == null) return true
    // Разовая поблажка сайта: выпуск 31 октября 2026 шёл в серию до 01:00 следующего дня
    if (day == GRACE_DAY && completedAt < GRACE_END) return true
    return Instant.ofEpochMilli(completedAt).atZone(RaskobkiZone).toLocalDate() == day
}

/** Объединение прогресса с двух устройств; ничего решённое не теряется. */
fun mergeStates(left: RaskobkiState, right: RaskobkiState): RaskobkiState = RaskobkiState(
    s = (left.s + right.s).distinct(),
    h = (left.h + right.h).distinct(),
    rv = (left.rv + right.rv).distinct(),
    x = left.x + right.x,
    a = (left.a + right.a).distinct().takeLast(32),
    w = maxOf(left.w, right.w),
    e = maxOf(left.e, right.e),
    completedAt = listOfNotNull(left.completedAt, right.completedAt).minOrNull(),
    c = left.c || right.c,
)

/** Код входа из ссылки в письме; ссылка может быть завёрнута в другую и закодирована. */
fun parseOobCode(link: String): String? {
    var text = link.trim()
    repeat(3) {
        Regex("[?&]oobCode=([^&#\\s]+)").find(text)?.let { return it.groupValues[1] }
        text = runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)
    }
    return null
}

/** Обычный JSON → значение Firestore. */
fun jsonToFirestore(value: JsonElement): JsonObject = when (value) {
    is JsonNull -> buildJsonObject { put("nullValue", JsonNull) }
    is JsonObject -> buildJsonObject { putJsonObject("mapValue") { put("fields", firestoreMap(value)) } }
    is JsonArray -> buildJsonObject { putJsonObject("arrayValue") { put("values", JsonArray(value.map(::jsonToFirestore))) } }
    is JsonPrimitive -> buildJsonObject {
        when {
            value.isString -> put("stringValue", value)
            value.booleanOrNull != null -> put("booleanValue", value)
            value.longOrNull != null -> put("integerValue", value.content)
            else -> put("doubleValue", value)
        }
    }
}

fun firestoreMap(value: JsonObject): JsonObject = JsonObject(value.mapValues { jsonToFirestore(it.value) })

/** Документ Firestore → обычный JSON. */
fun firestoreToJson(value: JsonObject): JsonElement {
    value["stringValue"]?.let { return it }
    value["integerValue"]?.let { return JsonPrimitive(it.jsonPrimitive.content.toLong()) }
    value["doubleValue"]?.let { return it }
    value["booleanValue"]?.let { return it }
    value["timestampValue"]?.let { return it }
    value["nullValue"]?.let { return JsonNull }
    value["arrayValue"]?.let { array ->
        return JsonArray(array.jsonObject["values"]?.jsonArray.orEmpty().map { firestoreToJson(it.jsonObject) })
    }
    value["mapValue"]?.let { map ->
        return firestoreFields(map.jsonObject["fields"]?.jsonObject)
    }
    return JsonNull
}

fun firestoreFields(fields: JsonObject?): JsonObject =
    JsonObject(fields.orEmpty().mapValues { firestoreToJson(it.value.jsonObject) })

/** Выпуск из JSON; null — выпуск в незнакомом виде, такой открывается на сайте. */
fun parseRaskobkiPuzzle(data: JsonObject): RaskobkiPuzzle? = runCatching {
    if (data["schemaVersion"]?.jsonPrimitive?.longOrNull != SCHEMA_VERSION) return null
    if (data["isReal"]?.jsonPrimitive?.booleanOrNull != true) return null
    // Партнёрский выпуск показывает свои материалы, их у нас нет
    if (data["partnership"].let { it != null && it !is JsonNull }) return null
    val template = data.getValue("template").jsonObject
    val root = template.getValue("root").jsonArray.map(::parsePart)
    if (root.none { it is RaskobkiNode }) return null
    RaskobkiPuzzle(
        day = LocalDate.parse(data.text("date")),
        puzzleId = data.text("puzzleId"),
        artifactId = data.text("artifactId").ifBlank { data.text("puzzleId") + "-artifact" },
        contentRevision = data["contentRevision"]?.jsonPrimitive?.longOrNull ?: 1,
        scoringVersion = data["scoringVersion"]?.jsonPrimitive?.longOrNull ?: 1,
        fact = template.text("fact"),
        sourceUrl = (template.text("sourceUrl").ifBlank { data.text("sourceUrl") }).takeIf { it.startsWith("https://") },
        era = data.text("era"),
        region = data.text("region"),
        cardText = data.text("cardText"),
        root = root,
    )
}.getOrNull()

private fun parsePart(element: JsonElement): RaskobkiPart {
    if (element is JsonPrimitive) return RaskobkiText(element.content)
    val node = element.jsonObject
    val hashes = node.getValue("acceptedHashes").jsonArray.map { it.jsonPrimitive.content }
    require(hashes.isNotEmpty())
    return RaskobkiNode(
        id = node.text("nodeId").also { require(it.isNotEmpty()) },
        parts = node.getValue("parts").jsonArray.map(::parsePart),
        hashes = hashes,
        firstLetter = node.text("firstLetter"),
    )
}

private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

private const val SCHEMA_VERSION = 1L
private val GRACE_DAY = LocalDate.of(2026, 10, 31)
private const val GRACE_END = 1793484000000L
