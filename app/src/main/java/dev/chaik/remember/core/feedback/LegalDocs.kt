package dev.chaik.remember.core.feedback

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dev.chaik.remember.appContainer

/**
 * Политика конфиденциальности и пользовательское соглашение. Их публикует сервер отзывов
 * (страницы /privacy и /terms), поэтому адрес берётся из адреса отправки отзывов.
 */
enum class LegalDoc(val path: String) {
    PRIVACY("privacy"),
    TERMS("terms"),
    CONSENT("consent"),
    ;

    /** Адрес документа на языке приложения; null — сервер отзывов не настроен. */
    fun url(context: Context): String? {
        val site = FeedbackSender.ENDPOINT.substringBefore("/api/").trimEnd('/')
        if (site.isEmpty()) return null
        val lang = if (context.resources.configuration.locales[0].language == "ru") "ru" else "en"
        return "$site/$path?lang=$lang"
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
