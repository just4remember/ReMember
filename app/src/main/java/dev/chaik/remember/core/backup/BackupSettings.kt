package dev.chaik.remember.core.backup

import android.content.Context
import android.content.SharedPreferences
import dev.chaik.remember.core.settings.AppLanguage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Всё, что лежит вне базы, но принадлежит пользователю: настройки, будильники, статистика игр,
 * прогресс Ра[скобок] и язык приложения. Едет в копии файлом settings.json.
 *
 * Не едут: PIN и блокировка (на новом телефоне легко остаться без входа), токен и пароль копий
 * на сервер (зашифрованы ключом чипа этого телефона), вход в Ра[скобки] (токен не место в файле),
 * служебные отметки и кэши.
 */
internal object BackupSettings {
    /** Файл настроек и какие его ключи едут в копии. */
    private class Part(val file: String, val deviceProtected: Boolean = false, val keep: (String) -> Boolean = { true })

    private val RASKOBKI_ACCOUNT = setOf("uid", "email", "name", "refresh_token", "pending_email", "results_saved")

    private val parts = listOf(
        Part("settings") { it != "gender" },
        Part("reminders") { it != "last_check" && it != "permission_asked" },
        Part("health"),
        Part("network"),
        Part("widget_settings"),
        Part("games"),
        Part("raskobki") { it !in RASKOBKI_ACCOUNT },
        // Будильники хранятся там, где их видно до первой разблокировки; идущие таймер и секундомер — нет
        Part("time", deviceProtected = true) { it == "alarms" },
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** Снимок настроек этого телефона. */
    fun export(context: Context): String = encode(
        language = AppLanguage.current(context),
        prefs = parts.associate { part -> part.file to prefs(context, part).all.filterKeys(part.keep) },
    )

    /** Вернуть настройки из копии: ключи каждого файла из копии заменяют свои. Язык — тоже. */
    fun restore(context: Context, text: String) {
        val (language, files) = decode(text)
        parts.forEach { part ->
            val values = files[part.file] ?: return@forEach
            val prefs = prefs(context, part)
            val editor = prefs.edit()
            prefs.all.keys.filter(part.keep).forEach(editor::remove)
            values.filterKeys(part.keep).forEach { (key, value) -> editor.put(key, value) }
            editor.commit()
        }
        language?.let { AppLanguage.set(context, it) }
    }

    private fun prefs(context: Context, part: Part): SharedPreferences {
        val storage = if (part.deviceProtected) context.createDeviceProtectedStorageContext() else context
        return storage.getSharedPreferences(part.file, Context.MODE_PRIVATE)
    }

    private fun SharedPreferences.Editor.put(key: String, value: Any) {
        @Suppress("UNCHECKED_CAST")
        when (value) {
            is String -> putString(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Boolean -> putBoolean(key, value)
            is Set<*> -> putStringSet(key, value as Set<String>)
        }
    }

    /** Значения настроек с их типом: SharedPreferences различают Int и Long, JSON — нет. */
    fun encode(language: String, prefs: Map<String, Map<String, Any?>>): String = buildJsonObject {
        put("language", JsonPrimitive(language))
        put("prefs", buildJsonObject {
            prefs.forEach { (file, values) ->
                put(file, buildJsonObject {
                    values.forEach { (key, value) ->
                        val typed = when (value) {
                            is String -> "s" to JsonPrimitive(value)
                            is Int -> "i" to JsonPrimitive(value)
                            is Long -> "l" to JsonPrimitive(value)
                            is Float -> "f" to JsonPrimitive(value)
                            is Boolean -> "b" to JsonPrimitive(value)
                            is Set<*> -> "set" to JsonArray(value.map { JsonPrimitive(it as String) })
                            else -> null
                        } ?: return@forEach
                        put(key, buildJsonObject { put("t", JsonPrimitive(typed.first)); put("v", typed.second) })
                    }
                })
            }
        })
    }.toString()

    /** Язык (null — в копии его нет) и значения по файлам; непонятные значения пропускаются. */
    fun decode(text: String): Pair<String?, Map<String, Map<String, Any>>> {
        val root = json.parseToJsonElement(text).jsonObject
        val language = (root["language"] as? JsonPrimitive)?.contentOrNull
        val files = (root["prefs"] as? JsonObject).orEmpty().mapValues { (_, file) ->
            (file as? JsonObject).orEmpty().mapNotNull { (key, entry) ->
                val obj = entry as? JsonObject ?: return@mapNotNull null
                val v = obj["v"] ?: return@mapNotNull null
                val value: Any? = when ((obj["t"] as? JsonPrimitive)?.contentOrNull) {
                    "s" -> v.jsonPrimitive.contentOrNull
                    "i" -> v.jsonPrimitive.intOrNull
                    "l" -> v.jsonPrimitive.longOrNull
                    "f" -> v.jsonPrimitive.floatOrNull
                    "b" -> v.jsonPrimitive.booleanOrNull
                    "set" -> v.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
                    else -> null
                }
                value?.let { key to it }
            }.toMap()
        }
        return language to files
    }
}
