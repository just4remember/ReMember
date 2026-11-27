package dev.chaik.remember.core.feedback

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dev.chaik.remember.BuildConfig
import dev.chaik.remember.appContainer

/** Ссылки на документы задаются в server.properties. */
enum class LegalDoc(private val documentUrl: String) {
    PRIVACY(BuildConfig.LEGAL_PRIVACY_URL),
    TERMS(BuildConfig.LEGAL_TERMS_URL),
    CONSENT(BuildConfig.LEGAL_CONSENT_URL),
    ;

    /** Адрес документа на языке приложения; null — ссылка не задана. */
    fun url(context: Context): String? {
        if (documentUrl.isBlank()) return null
        val lang = if (context.resources.configuration.locales[0].language == "ru") "ru" else "en"
        return documentUrl.toUri().buildUpon().appendQueryParameter("lang", lang).build().toString()
    }

    fun open(context: Context) {
        url(context)?.let { openExternalLink(context, it) }
    }
}

/** Открыть ссылку в браузере (блокировка приложения при возвращении не срабатывает). */
fun openExternalLink(context: Context, url: String) {
    context.appContainer.appLock.allowExternal()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // Браузера нет — остаёмся на месте
    }
}
