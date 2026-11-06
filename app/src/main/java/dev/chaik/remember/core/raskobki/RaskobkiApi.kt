package dev.chaik.remember.core.raskobki

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Ответ сервера с ошибкой: код HTTP и текст Firebase, например INVALID_OOB_CODE. */
class RaskobkiApiException(val code: Int, message: String) : IOException(message)

/** Токены аккаунта. */
data class RaskobkiTokens(val uid: String, val idToken: String, val refreshToken: String, val expiresAt: Long)

/** Запросы к Firebase игры — те же, что делает сайт. Все вызовы блокирующие, запускать на IO. */
internal object RaskobkiApi {
    private const val API_KEY = "AIzaSyALhiu0gaw4s1gqgwcvu62Ql-QC72jVkEs"
    private const val DATABASE = "projects/raskobki/databases/raskobki-content/documents"
    private const val FIRESTORE = "https://firestore.googleapis.com/v1/$DATABASE"
    private const val AUTH = "https://identitytoolkit.googleapis.com/v1/accounts"
    private const val TOKEN = "https://securetoken.googleapis.com/v1/token"
    private const val FUNCTIONS = "https://me-west1-raskobki.cloudfunctions.net"
    private const val CONTINUE_URL = "https://raskobki.app/?auth=finish&returnTo=%2F"
    private const val TIMEOUT_MS = 15_000

    private val json = Json { ignoreUnknownKeys = true }

    fun sendSignInMail(email: String) {
        request(
            "$AUTH:sendOobCode?key=$API_KEY",
            buildJsonObject {
                put("requestType", "EMAIL_SIGNIN")
                put("email", email)
                put("continueUrl", CONTINUE_URL)
                put("canHandleCodeInApp", true)
            }.toString(),
        )
    }

    fun signIn(email: String, oobCode: String): RaskobkiTokens {
        val answer = request(
            "$AUTH:signInWithEmailLink?key=$API_KEY",
            buildJsonObject {
                put("email", email)
                put("oobCode", oobCode)
            }.toString(),
        )
        return RaskobkiTokens(
            uid = answer.text("localId"),
            idToken = answer.text("idToken"),
            refreshToken = answer.text("refreshToken"),
            expiresAt = expiry(answer.text("expiresIn")),
        )
    }

    fun refresh(refreshToken: String): RaskobkiTokens {
        val token = URLEncoder.encode(refreshToken, "UTF-8")
        val answer = request(
            "$TOKEN?key=$API_KEY",
            "grant_type=refresh_token&refresh_token=$token",
            contentType = "application/x-www-form-urlencoded",
        )
        return RaskobkiTokens(
            uid = answer.text("user_id"),
            idToken = answer.text("id_token"),
            refreshToken = answer.text("refresh_token"),
            expiresAt = expiry(answer.text("expires_in")),
        )
    }

    /** Поля документа в обычном JSON. */
    fun document(path: String, bearer: String? = null): JsonObject =
        firestoreFields(request("$FIRESTORE/$path", bearer = bearer)["fields"]?.jsonObject)

    /** Все документы коллекции [name] внутри [parent]: имя → поля. Запрос тот же, что у сайта. */
    fun collection(parent: String, name: String, bearer: String): Map<String, JsonObject> {
        val body = buildJsonObject {
            put("structuredQuery", buildJsonObject {
                put("from", JsonArray(listOf(buildJsonObject { put("collectionId", name) })))
            })
        }
        val answer = requestText("$FIRESTORE/$parent:runQuery", body.toString(), bearer)
        return json.parseToJsonElement(answer).jsonArray.mapNotNull { (it.jsonObject["document"] as? JsonObject) }
            .associate { it.text("name").substringAfterLast('/') to firestoreFields(it["fields"]?.jsonObject) }
    }

    /** Записать документ целиком; updatedAt ставит сервер. */
    fun write(path: String, fields: JsonObject, bearer: String) {
        val write = buildJsonObject {
            put("update", buildJsonObject {
                put("name", "$DATABASE/$path")
                put("fields", fields)
            })
            put("updateTransforms", kotlinx.serialization.json.buildJsonArray {
                add(buildJsonObject {
                    put("fieldPath", "updatedAt")
                    put("setToServerValue", "REQUEST_TIME")
                })
            })
        }
        val body = buildJsonObject { put("writes", JsonArray(listOf(write))) }
        request("$FIRESTORE:commit", body.toString(), bearer = bearer)
    }

    /** Вызов серверной функции; результат лежит в поле result. */
    fun call(function: String, data: JsonObject, bearer: String?): JsonObject {
        val body = buildJsonObject { put("data", data) }
        return request("$FUNCTIONS/$function", body.toString(), bearer = bearer)["result"] as? JsonObject ?: JsonObject(emptyMap())
    }

    private fun request(
        url: String,
        body: String? = null,
        bearer: String? = null,
        contentType: String = "application/json",
    ): JsonObject = json.parseToJsonElement(requestText(url, body, bearer, contentType)).jsonObject

    /** Скачать файл не больше [maxBytes] — картинку выпуска. */
    fun download(url: String, maxBytes: Int): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw RaskobkiApiException(code, "")
            connection.inputStream.use { input ->
                val bytes = input.readNBytes(maxBytes + 1)
                if (bytes.size > maxBytes) throw IOException("слишком большой файл")
                return bytes
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun requestText(
        url: String,
        body: String? = null,
        bearer: String? = null,
        contentType: String = "application/json",
    ): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        bearer?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        try {
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val text = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val error = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.text("message") }.getOrNull()
                throw RaskobkiApiException(code, error.orEmpty())
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun expiry(seconds: String): Long =
        System.currentTimeMillis() + (JsonPrimitive(seconds).longOrNull ?: seconds.toLongOrNull() ?: 3600L) * 1000

    private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
}
