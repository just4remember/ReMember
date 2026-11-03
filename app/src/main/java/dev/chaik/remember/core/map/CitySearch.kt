package dev.chaik.remember.core.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.android.geometry.LatLngBounds
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Найденное место: короткое имя, полное описание и границы. */
data class CityResult(val name: String, val label: String, val bounds: LatLngBounds)

/**
 * Поиск города по названию — сервис OpenStreetMap (Nominatim). Уходит только набранный текст.
 * По правилам сервиса — с подписью приложения и не чаще запроса в секунду: ищем только по кнопке.
 */
object CitySearch {
    suspend fun search(query: String): List<CityResult> = withContext(Dispatchers.IO) {
        val url = URL(
            "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&accept-language=ru&q=" +
                URLEncoder.encode(query.trim(), "UTF-8"),
        )
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "ReMember/1.0 (personal diary app)")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${connection.responseCode}")
            parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(body: String): List<CityResult> = Json.parseToJsonElement(body).jsonArray.mapNotNull { element ->
        val obj = element.jsonObject
        // boundingbox: юг, север, запад, восток
        val box = obj["boundingbox"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content.toDoubleOrNull() }
        if (box == null || box.size != 4) return@mapNotNull null
        val label = obj["display_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val name = obj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: label.substringBefore(',')
        CityResult(name, label, LatLngBounds.from(box[1], box[3], box[0], box[2]))
    }

    private const val TIMEOUT_MS = 10_000
}
