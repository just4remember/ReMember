package dev.chaik.remember.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class PublicIps(val v4: String? = null, val v6: String? = null) {
    val isEmpty: Boolean get() = v4 == null && v6 == null
}

/**
 * Внешние IP — какими адресами телефон виден в интернете (с VPN — адреса VPN). Узнать их можно только
 * спросив сервер. Обычный адрес сайта ответил бы по одной версии, той, что выбрал телефон, поэтому
 * у каждой версии свой адрес запроса: сначала Cloudflare, а если он недоступен (белые списки) — Яндекс.
 */
object PublicIp {
    private const val CLOUDFLARE_V4 = "https://1.1.1.1/cdn-cgi/trace"
    private const val CLOUDFLARE_V6 = "https://[2606:4700:4700::1111]/cdn-cgi/trace"
    private const val YANDEX_V4 = "https://ipv4-internet.yandex.net/api/v0/ip"
    private const val YANDEX_V6 = "https://ipv6-internet.yandex.net/api/v0/ip"
    private const val TIMEOUT_MS = 5_000

    /** Оба адреса разом; версии, которой у сети нет, не будет. */
    suspend fun fetch(): PublicIps = coroutineScope {
        val v4 = async { fetchOne(CLOUDFLARE_V4, YANDEX_V4)?.takeIf { '.' in it } }
        val v6 = async { fetchOne(CLOUDFLARE_V6, YANDEX_V6)?.takeIf { ':' in it } }
        PublicIps(v4.await(), v6.await())
    }

    private suspend fun fetchOne(cloudflare: String, yandex: String): String? = withContext(Dispatchers.IO) {
        runCatching { fromTrace(get(cloudflare)) }.getOrNull()
            ?: runCatching { fromYandex(get(yandex)) }.getOrNull()
    }

    /** Ответ Cloudflare — строки вида `ip=1.2.3.4`. */
    internal fun fromTrace(body: String): String? =
        body.lineSequence().firstOrNull { it.startsWith("ip=") }?.removePrefix("ip=")?.trim()?.takeIf { it.isNotEmpty() }

    /** Ответ Яндекса — адрес в кавычках. */
    internal fun fromYandex(body: String): String? = body.trim().trim('"').takeIf { it.isNotEmpty() }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
