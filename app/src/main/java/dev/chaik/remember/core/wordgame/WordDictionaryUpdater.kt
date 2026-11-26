package dev.chaik.remember.core.wordgame

import android.content.Context
import android.os.UserManager
import android.util.Log
import androidx.core.content.edit
import dev.chaik.remember.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

internal class WordDictionaryUpdater(
    private val context: Context,
    private val dictionary: LocalWordDictionary,
    private val scope: CoroutineScope,
) {
    private val prefs by lazy { context.getSharedPreferences("word_dictionary_updates", Context.MODE_PRIVATE) }
    private val updateLock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    fun check() {
        if (BuildConfig.DICTIONARY_BASE_URL.isBlank()) return
        scope.launch {
            if (!context.getSystemService(UserManager::class.java).isUserUnlocked) return@launch
            if (!updateLock.tryLock()) return@launch
            try {
                refreshIfDue()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("WordDictionary", "Не удалось обновить словарь", e)
            } finally {
                updateLock.unlock()
            }
        }
    }

    private fun refreshIfDue() {
        val versionUrl = endpoint(BuildConfig.DICTIONARY_VERSION_PATH)
        val interval = TimeUnit.HOURS.toMillis(BuildConfig.DICTIONARY_CHECK_INTERVAL_HOURS.toLong())
        val now = System.currentTimeMillis()
        val previous = prefs.getLong("lastAttempt", 0)
        if (prefs.getString("versionUrl", null) == versionUrl && now - previous in 0 until interval) return
        prefs.edit(commit = true) {
            putLong("lastAttempt", now)
            putString("versionUrl", versionUrl)
        }

        val metadata = json.decodeFromString<DictionaryMetadata>(get(versionUrl, 8 * 1024).toString(Charsets.UTF_8))
        require(metadata.version > 0 && metadata.size in 1..DictionaryArchive.MAX_ARCHIVE)
        require(DictionaryArchive.isHash(metadata.sha256))
        if (dictionary.hasVersion(metadata.version, metadata.sha256)) return

        val archive = get(endpoint(BuildConfig.DICTIONARY_DOWNLOAD_PATH) + "?version=${metadata.version}", metadata.size)
        require(archive.size == metadata.size)
        val words = DictionaryArchive.parseVerified(archive, metadata.sha256)
        dictionary.install(metadata.version, metadata.sha256, archive, words)
    }

    private fun endpoint(path: String): String =
        BuildConfig.DICTIONARY_BASE_URL.trimEnd('/') + "/" + path.trimStart('/')

    private fun get(endpoint: String, limit: Int): ByteArray {
        val uri = URI(endpoint)
        require(uri.scheme == "https" && uri.host != null)
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "HTTP ${connection.responseCode}" }
            return connection.inputStream.use { it.readNBytes(limit + 1) }.also { require(it.size <= limit) }
        } finally {
            connection.disconnect()
        }
    }
}

@Serializable
private data class DictionaryMetadata(val version: Long, val sha256: String, val size: Int)
