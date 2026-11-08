package dev.chaik.remember.core.tutorial

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Состояние обучения.
 *
 * [seen] — ключи страниц, чьи карточки уже показаны. У новой страницы в новой версии свой ключ,
 * которого здесь нет, — её карточка покажется сама. Сильно изменилась страница — новый ключ
 * («today.v2»), и карточка покажется снова.
 */
data class TutorialState(
    /** Крестик на карточке выключает обучение целиком; включается в Персонализации. */
    val enabled: Boolean = true,
    /** Тур по вкладкам при первом запуске пройден или закрыт. */
    val tourDone: Boolean = false,
    val seen: Set<String> = emptySet(),
)

/** Хранит состояние обучения; файл «tutorial» входит в резервную копию (BackupSettings). */
class TutorialStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val tutorial: StateFlow<TutorialState> = state

    private fun read() = TutorialState(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        tourDone = prefs.getBoolean(KEY_TOUR_DONE, false),
        seen = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty().toSet(),
    )

    private fun update(change: (TutorialState) -> TutorialState) {
        val next = change(state.value)
        prefs.edit {
            putBoolean(KEY_ENABLED, next.enabled)
            putBoolean(KEY_TOUR_DONE, next.tourDone)
            putStringSet(KEY_SEEN, next.seen)
        }
        state.value = next
    }

    /** Карточки этих страниц показаны. */
    fun markSeen(vararg keys: String) = update { it.copy(seen = it.seen + keys) }

    /** Тур закончен: его страницы тоже считаются показанными. */
    fun finishTour(keys: Collection<String>) = update { it.copy(tourDone = true, seen = it.seen + keys) }

    /** Крестик: больше никаких карточек, в том числе для новых страниц. */
    fun disable() = update { it.copy(enabled = false, tourDone = true) }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    /** «Пройти обучение заново»: тур с начала и все карточки снова. */
    fun restart() = update { TutorialState() }

    companion object {
        const val PREFS = "tutorial"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_TOUR_DONE = "tour_done"
        private const val KEY_SEEN = "seen"
    }
}
