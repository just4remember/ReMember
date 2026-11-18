package dev.chaik.remember.core.feedback

import java.time.LocalDate
import androidx.core.content.edit
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import dev.chaik.remember.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Что за отзыв. Код уходит на сервер как есть — не переименовывать. */
enum class FeedbackType(val code: String) { IDEA("idea"), BUG("bug") }

/** Отзыв перед отправкой. */
data class Feedback(
    val type: FeedbackType,
    val message: String,
    /** Как ответить (почта, телеграм); пусто — не отвечать. */
    val contact: String,
    /** Приложить версию приложения, модель телефона и Android — помогает разобраться с ошибкой. */
    val withDevice: Boolean,
    val photos: List<Uri>,
)

/**
 * Отправка отзыва разработчику: один запрос POST multipart/form-data на [ENDPOINT].
 *
 * Поля: `type` (idea | bug), `message`, `contact` (может быть пустым), `platform` (android),
 * при согласии — `appVersion`, `device`, `os`; файлы — `photo` (до [MAX_PHOTOS], JPEG, сторона до [PHOTO_SIDE]).
 * Заголовок `X-Feedback-Key` — ключ приложения [KEY] (APP_KEY сервера, см. FeedbackServer).
 * Заголовок `X-Install-Id` — случайный номер установки (не связан с человеком): по нему сервер
 * может ограничить число отзывов с одного телефона. Ответ 2xx — принято; 429 — лимит на сервере.
 * Больше ничего не уходит: ни записи, ни настройки.
 */
class FeedbackSender(private val context: Context) {
    private val prefs = context.getSharedPreferences("feedback", Context.MODE_PRIVATE)

    /** Случайный номер установки: создаётся один раз, ничего о человеке не говорит. */
    private val installId: String
        get() = prefs.getString(KEY_INSTALL, null) ?: UUID.randomUUID().toString().also { prefs.edit { putString(KEY_INSTALL, it) } }

    /** Сколько отзывов уже ушло сегодня. */
    fun sentToday(): Int =
        if (prefs.getLong(KEY_DAY, -1) == LocalDate.now().toEpochDay()) prefs.getInt(KEY_COUNT, 0) else 0

    /** Сколько ошибок и предложений отправлено за всё время — для значка на странице отзывов. */
    fun sentBugs(): Int = prefs.getInt(KEY_BUGS, 0)
    fun sentIdeas(): Int = prefs.getInt(KEY_IDEAS, 0)

    /** Есть ли куда слать: сервер появится позже. */
    val configured: Boolean get() = ENDPOINT.isNotBlank() && KEY.isNotBlank()

    suspend fun send(feedback: Feedback) = withContext(Dispatchers.IO) {
        check(configured) { "Сервер отзывов ещё не настроен" }
        check(sentToday() < DAILY_LIMIT) { "На сегодня отзывов достаточно" }
        val boundary = "----ReMember${UUID.randomUUID()}"
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setRequestProperty("X-Install-Id", installId)
            connection.setRequestProperty("X-Feedback-Key", KEY)
            // Фото сжимаются заранее, поэтому тело собираем целиком и знаем его длину
            val body = ByteArrayOutputStream()
            DataOutputStream(body).use { out ->
                fun field(name: String, value: String) {
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n")
                    out.writeBytes("Content-Type: text/plain; charset=UTF-8\r\n\r\n")
                    out.write(value.toByteArray(Charsets.UTF_8))
                    out.writeBytes("\r\n")
                }
                field("type", feedback.type.code)
                field("message", feedback.message.trim())
                field("contact", feedback.contact.trim())
                field("platform", "android")
                // Пользователь отметил согласие этой редакции: сервер без него отзыв не примет
                field("consent", CONSENT_VERSION)
                if (feedback.withDevice) {
                    field("appVersion", appVersion())
                    field("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                    field("os", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                }
                feedback.photos.take(MAX_PHOTOS).forEachIndexed { i, uri ->
                    val jpeg = compress(uri)
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"photo-${i + 1}.jpg\"\r\n")
                    out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                    out.write(jpeg)
                    out.writeBytes("\r\n")
                }
                out.writeBytes("--$boundary--\r\n")
            }
            val bytes = body.toByteArray()
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            // Принят — считаем в дневной лимит и в счётчик ошибок или предложений
            val today = LocalDate.now().toEpochDay()
            prefs.edit {
                putLong(KEY_DAY, today)
                putInt(KEY_COUNT, sentToday() + 1)
                when (feedback.type) {
                    FeedbackType.BUG -> putInt(KEY_BUGS, sentBugs() + 1)
                    FeedbackType.IDEA -> putInt(KEY_IDEAS, sentIdeas() + 1)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    /** Снимок — в JPEG поменьше: полноразмерные фото незачем гонять по сети. Поворот по EXIF учитывается. */
    private fun compress(uri: Uri): ByteArray {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > PHOTO_SIDE) {
                val scale = PHOTO_SIDE.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    companion object {
        /**
         * Адрес сервера отзывов и ключ приложения (APP_KEY сервера) — из secrets.properties
         * в корне проекта, файл в git не попадает. Пусто — кнопка «Отправить» объясняет,
         * что отправка недоступна.
         */
        val ENDPOINT: String = BuildConfig.FEEDBACK_ENDPOINT
        val KEY: String = BuildConfig.FEEDBACK_KEY

        const val MAX_PHOTOS = 10

        /**
         * Редакция согласия на обработку персональных данных (страница /consent сервера отзывов).
         * Меняется вместе с текстом согласия на сервере (ConsentVersion).
         */
        const val CONSENT_VERSION = "2026-11-09"

        /**
         * Больше стольких отзывов в день с телефона не отправить. Из secrets.properties
         * (feedback.dailyLimit), по умолчанию 5; на сервере свой предел — LIMIT_PER_INSTALL.
         */
        val DAILY_LIMIT: Int = BuildConfig.FEEDBACK_DAILY_LIMIT
        private const val KEY_INSTALL = "install_id"
        private const val KEY_DAY = "day"
        private const val KEY_COUNT = "count"
        /** Счётчики за всё время; едут в резервной копии (см. BackupSettings). */
        const val KEY_BUGS = "bugs"
        const val KEY_IDEAS = "ideas"
        const val MAX_MESSAGE = 4000
        /** Почта или ник в Telegram: длиннее не бывает. */
        const val MAX_CONTACT = 50
        private const val PHOTO_SIDE = 1600
        private const val JPEG_QUALITY = 82
        private const val TIMEOUT_MS = 60_000
    }
}
