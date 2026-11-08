package dev.chaik.remember.core.tutorial

import androidx.annotation.StringRes
import dev.chaik.remember.R
import dev.chaik.remember.app.navigation.Destination

/** Урок: заголовок и короткий текст карточки. [key] хранится в отметках — не переименовывать. */
data class Lesson(val key: String, @param:StringRes val title: Int, @param:StringRes val text: Int)

object Lessons {
    /** Первая карточка тура: что это за приложение и как устроен островок. */
    val Intro = Lesson("intro", R.string.tutorial_intro_title, R.string.tutorial_intro)

    /**
     * Урок раздела. Ключ — имя раздела в нижнем регистре; у скрытых разделов (Лента, Финансы)
     * уроков нет. Новый раздел — новая строка здесь, и его карточка покажется всем при первом входе.
     */
    fun of(destination: Destination): Lesson? {
        val text = when (destination) {
            Destination.Today -> R.string.tutorial_today
            Destination.Notes -> R.string.tutorial_notes
            Destination.Tasks -> R.string.tutorial_tasks
            Destination.Calendar -> R.string.tutorial_calendar
            Destination.More -> R.string.tutorial_more
            Destination.Search -> R.string.tutorial_search
            Destination.Stats -> R.string.tutorial_stats
            Destination.Health -> R.string.tutorial_health
            Destination.Gym -> R.string.tutorial_gym
            Destination.Network -> R.string.tutorial_network
            Destination.Time -> R.string.tutorial_time
            Destination.Games -> R.string.tutorial_games
            Destination.Places -> R.string.tutorial_map
            Destination.Settings -> R.string.tutorial_settings
            Destination.Feed, Destination.Finance -> return null
        }
        return Lesson(destination.name.lowercase(), destination.label, text)
    }
}
