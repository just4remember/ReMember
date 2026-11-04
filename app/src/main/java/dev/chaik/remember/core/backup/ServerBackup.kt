package dev.chaik.remember.core.backup

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import dev.chaik.remember.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Настройки копий на сервер. [intervalDays] = 0 — только вручную. */
data class ServerBackupSettings(
    val url: String = "",
    val intervalDays: Int = 0,
    /** Есть ли сохранённый пароль копии — сам пароль наружу не отдаётся. */
    val hasPassword: Boolean = false,
    val hasToken: Boolean = false,
    /** Когда последняя копия дошла до сервера; null — ещё ни разу. */
    val lastSuccess: Instant? = null,
    /** Почему не удалась последняя попытка; null — удалась или не было. */
    val lastError: String? = null,
) {
    val configured: Boolean get() = url.isNotBlank() && hasPassword
}

/**
 * Резервные копии на свой HTTP-сервер: тот же зашифрованный файл, что и при ручном экспорте,
 * уходит запросом POST на [ServerBackupSettings.url]:
 * - тело — сам файл (application/octet-stream);
 * - `X-Backup-Name` — имя файла вида `remember-2026-11-04_21-30.<расширение копии>`;
 * - `Authorization: Bearer <токен>` — если токен задан.
 * Сервер отвечает 2xx — копия сохранена. Пароль копии и токен хранятся зашифрованными ключом
 * из чипа телефона: без них копии по расписанию делать было бы нечем.
 */
class ServerBackup(private val context: Context, private val backup: BackupManager) {
    private val prefs = context.getSharedPreferences("server_backup", Context.MODE_PRIVATE)
    private val lock = Mutex()

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<ServerBackupSettings> = _settings

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    fun setUrl(url: String) = save { putString(KEY_URL, url.trim()) }

    /** Раз в сколько дней; 0 — только вручную. */
    fun setInterval(days: Int) {
        save { putInt(KEY_INTERVAL_DAYS, days.coerceIn(0, MAX_DAYS)) }
        schedule()
    }

    fun setPassword(password: CharArray) = save { putString(KEY_PASSWORD, encrypt(String(password))) }

    /** Пустой — без токена. */
    fun setToken(token: String) = save {
        if (token.isBlank()) remove(KEY_TOKEN) else putString(KEY_TOKEN, encrypt(token.trim()))
    }

    /**
     * Поставить или снять копирование по расписанию — по текущим настройкам. Расписанием ведает
     * сама система (JobScheduler): обычная корутина умерла бы вместе с приложением, а система
     * разбудит его сама, когда подойдёт срок и будет сеть, и после перезагрузки тоже.
     */
    fun schedule() {
        val jobs = context.getSystemService(JobScheduler::class.java)
        val current = _settings.value
        if (current.intervalDays <= 0 || !current.configured) {
            jobs.cancel(JOB_ID)
            return
        }
        val period = TimeUnit.DAYS.toMillis(current.intervalDays.toLong())
        // Уже стоит с тем же периодом — не трогаем, иначе отсчёт начался бы заново
        if (jobs.getPendingJob(JOB_ID)?.intervalMillis == period) return
        jobs.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(context, ServerBackupJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(period)
                .setPersisted(true)
                .build(),
        )
    }

    /** Сделать копию и отправить. true — сервер её принял. */
    suspend fun upload(): Boolean = lock.withLock {
        val current = _settings.value
        val password = prefs.getString(KEY_PASSWORD, null)?.let { runCatching { decrypt(it) }.getOrNull() }
        if (current.url.isBlank() || password == null) return@withLock false
        _running.value = true
        val file = File(context.cacheDir, "server-backup.${BackupManager.EXTENSION}")
        try {
            backup.export(Uri.fromFile(file), password.toCharArray())
            val token = prefs.getString(KEY_TOKEN, null)?.let { runCatching { decrypt(it) }.getOrNull() }
            send(current.url, file, token)
            save {
                putLong(KEY_LAST_SUCCESS, Instant.now().toEpochMilli())
                remove(KEY_LAST_ERROR)
            }
            true
        } catch (e: Exception) {
            save { putString(KEY_LAST_ERROR, e.message ?: e.javaClass.simpleName) }
            false
        } finally {
            file.delete()
            _running.value = false
        }
    }

    private suspend fun send(url: String, file: File, token: String?) = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setFixedLengthStreamingMode(file.length())
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.setRequestProperty("X-Backup-Name", "remember-${NameFormat.format(Instant.now())}.${BackupManager.EXTENSION}")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            file.inputStream().use { input -> connection.outputStream.use { input.copyTo(it) } }
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
        } finally {
            connection.disconnect()
        }
    }

    private fun save(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit(commit = true, action = block)
        _settings.value = read()
    }

    private fun read() = ServerBackupSettings(
        url = prefs.getString(KEY_URL, "").orEmpty(),
        // Сначала период хранился в часах, а предел был больше — приводим к нынешним дням
        intervalDays = (if (prefs.contains(KEY_INTERVAL_DAYS)) prefs.getInt(KEY_INTERVAL_DAYS, 0) else prefs.getInt(KEY_INTERVAL, 0) / 24)
            .coerceIn(0, MAX_DAYS),
        hasPassword = prefs.contains(KEY_PASSWORD),
        hasToken = prefs.contains(KEY_TOKEN),
        lastSuccess = prefs.getLong(KEY_LAST_SUCCESS, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        lastError = prefs.getString(KEY_LAST_ERROR, null),
    )

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    companion object {
        /** Самый редкий период, дней. */
        const val MAX_DAYS = 30

        /** Номер задачи в JobScheduler: у приложения он один. */
        private const val JOB_ID = 4201
        private const val TIMEOUT_MS = 60_000
        private const val KEY_URL = "url"
        private const val KEY_INTERVAL = "interval_hours"
        private const val KEY_INTERVAL_DAYS = "interval_days"
        private const val KEY_PASSWORD = "password"
        private const val KEY_TOKEN = "token"
        private const val KEY_LAST_SUCCESS = "last_success"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "server_backup"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val IV_BYTES = 12
        private val NameFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm").withZone(ZoneId.systemDefault())
    }
}

/** Копия по расписанию: систему будит этот сервис, когда подошёл срок и есть сеть. */
class ServerBackupJob : JobService() {
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val container = applicationContext.appContainer
        work = container.appScope.launch {
            val ok = container.serverBackup.upload()
            // Не дошла — система повторит позже сама, с паузой
            jobFinished(params, !ok)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        return true
    }
}
