package dev.chaik.remember.core.map

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import org.maplibre.android.geometry.LatLng

internal data class MapSearchResult(
    val name: String,
    val detail: String,
    val location: LatLng,
    val zoom: Double,
)

/** Photon поддерживает поиск по мере ввода. Серверу передаётся только поисковый запрос и язык. */
internal object PlaceSearch {
    suspend fun search(query: String, language: String): List<MapSearchResult> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val connection = URI("https://photon.komoot.io/api/?limit=5&lang=$language&q=$encoded")
            .toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", "ReMember (https://github.com/just4remember/ReMember)")
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(body: String): List<MapSearchResult> {
        val features = (Json.parseToJsonElement(body) as? JsonObject)?.get("features") as? JsonArray
        return features.orEmpty().mapNotNull { element ->
            val feature = element as? JsonObject ?: return@mapNotNull null
            val geometry = feature["geometry"] as? JsonObject ?: return@mapNotNull null
            val coordinates = geometry["coordinates"] as? JsonArray ?: return@mapNotNull null
            val longitude = (coordinates.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val latitude = (coordinates.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return@mapNotNull null
            val properties = feature["properties"] as? JsonObject ?: return@mapNotNull null
            fun property(key: String) = (properties[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val street = listOf(property("street"), property("housenumber")).filter(String::isNotBlank).joinToString(" ")
            val name = property("name").ifBlank { street }.ifBlank { property("city") }
            if (name.isBlank()) return@mapNotNull null
            val detail = listOf(street, property("district"), property("city"), property("state"), property("country"))
                .filter { it.isNotBlank() && it != name }.distinct().joinToString(", ")
            val zoom = when (property("type")) {
                "country" -> 5.0
                "state", "county" -> 8.0
                "city" -> 12.0
                "district", "locality" -> 14.0
                else -> 16.0
            }
            MapSearchResult(name, detail, LatLng(latitude, longitude), zoom)
        }.distinct()
    }
}
