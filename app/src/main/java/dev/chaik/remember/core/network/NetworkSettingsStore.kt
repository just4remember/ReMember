package dev.chaik.remember.core.network

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Настройки страницы «Сеть»: свой сервер замера и сайты для проверки белых списков. */
data class NetworkSettings(
    /** Замерять через Selectel (Россия); свой сервер при этом не используется. */
    val selectelServer: Boolean = false,
    val customServer: Boolean = false,
    val downloadUrl: String = "",
    val uploadUrl: String = "",
    val allowedSites: List<String> = DefaultAllowedSites,
    val otherSites: List<String> = DefaultOtherSites,
    /** Сколько секунд длятся загрузка и отдача (каждая). */
    val testSeconds: Int = 10,
    /** Сколько мегабайт можно потратить на замер по мобильному интернету; 0 — без ограничения. */
    val mobileLimitMb: Int = 0,
    /** Спрашивать перед замером по мобильному интернету. */
    val askOnMobile: Boolean = true,
) {
    /** Время и лимит для замера; лимит — только на платном трафике, поровну на загрузку и отдачу. */
    fun speedOptions(metered: Boolean) = SpeedOptions(
        durationMs = testSeconds * 1000L,
        byteLimit = if (metered && mobileLimitMb > 0) mobileLimitMb * 1_000_000L / 2 else null,
    )

    val server: SpeedServer
        get() = when {
            selectelServer -> SelectelServer
            customServer && downloadUrl.isNotBlank() -> SpeedServer.Custom(downloadUrl, uploadUrl.ifBlank { null })
            else -> SpeedServer.Cloudflare
        }
}

/** Открытый тестовый файл Selectel. Адреса для отдачи у него нет — замеряется только загрузка. */
val SelectelServer = SpeedServer.Custom(downloadUrl = "https://speedtest.selectel.ru/1GB", uploadUrl = null)

/** Варианты длительности загрузки и отдачи, секунд. */
val TestSecondsOptions = listOf(5, 10, 15, 20, 30)

/** Варианты лимита трафика на замер по мобильному интернету, МБ; 0 — без ограничения. */
val MobileLimitOptions = listOf(0, 25, 50, 100, 250)

/** Обычно открываются и при белых списках: отечественные сервисы. */
val DefaultAllowedSites = listOf("ya.ru", "vk.com", "gosuslugi.ru", "mail.ru", "ozon.ru", "wildberries.ru")

/** Обычно не входят в белые списки. */
val DefaultOtherSites = listOf("google.com", "wikipedia.org", "github.com", "cloudflare.com", "amazon.com")

class NetworkSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val settings: StateFlow<NetworkSettings> = state.asStateFlow()

    fun update(transform: (NetworkSettings) -> NetworkSettings) {
        val next = transform(state.value)
        if (next == state.value) return
        prefs.edit {
            putBoolean(KEY_SELECTEL, next.selectelServer)
            putBoolean(KEY_CUSTOM, next.customServer)
            putString(KEY_DOWNLOAD, next.downloadUrl)
            putString(KEY_UPLOAD, next.uploadUrl)
            putString(KEY_ALLOWED, next.allowedSites.joinToString(","))
            putString(KEY_OTHER, next.otherSites.joinToString(","))
            putInt(KEY_SECONDS, next.testSeconds)
            putInt(KEY_LIMIT, next.mobileLimitMb)
            putBoolean(KEY_ASK, next.askOnMobile)
        }
        state.value = next
    }

    private fun read(): NetworkSettings {
        val d = NetworkSettings()
        return NetworkSettings(
            selectelServer = prefs.getBoolean(KEY_SELECTEL, d.selectelServer),
            customServer = prefs.getBoolean(KEY_CUSTOM, d.customServer),
            downloadUrl = prefs.getString(KEY_DOWNLOAD, d.downloadUrl).orEmpty(),
            uploadUrl = prefs.getString(KEY_UPLOAD, d.uploadUrl).orEmpty(),
            // Список ещё не меняли — готовый; пустой сохранённый — пустой
            allowedSites = prefs.getString(KEY_ALLOWED, null)?.let(::split) ?: d.allowedSites,
            otherSites = prefs.getString(KEY_OTHER, null)?.let(::split) ?: d.otherSites,
            testSeconds = prefs.getInt(KEY_SECONDS, d.testSeconds).takeIf { it in TestSecondsOptions } ?: d.testSeconds,
            mobileLimitMb = prefs.getInt(KEY_LIMIT, d.mobileLimitMb).takeIf { it in MobileLimitOptions } ?: d.mobileLimitMb,
            askOnMobile = prefs.getBoolean(KEY_ASK, d.askOnMobile),
        )
    }

    private fun split(value: String) = value.split(',').filter { it.isNotBlank() }

    private companion object {
        const val PREFS = "network"
        const val KEY_SELECTEL = "selectel_server"
        const val KEY_CUSTOM = "custom_server"
        const val KEY_DOWNLOAD = "download_url"
        const val KEY_UPLOAD = "upload_url"
        const val KEY_ALLOWED = "allowed_sites"
        const val KEY_OTHER = "other_sites"
        const val KEY_SECONDS = "test_seconds"
        const val KEY_LIMIT = "mobile_limit_mb"
        const val KEY_ASK = "ask_on_mobile"
    }
}
