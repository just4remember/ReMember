package dev.chaik.remember.app.navigation

import dev.chaik.remember.feature.gym.exercises.ExercisesScreen
import dev.chaik.remember.feature.gym.exercises.ExercisesRoute
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import dev.chaik.remember.feature.calendar.CalendarRoute
import dev.chaik.remember.feature.calendar.CalendarScreen
import dev.chaik.remember.feature.calendar.editor.EventEditorRoute
import dev.chaik.remember.feature.calendar.editor.EventEditorScreen
import dev.chaik.remember.feature.feed.FeedRoute
import dev.chaik.remember.feature.feed.FeedScreen
import dev.chaik.remember.feature.finance.FinanceRoute
import dev.chaik.remember.feature.finance.FinanceScreen
import dev.chaik.remember.feature.games.GamesRoute
import dev.chaik.remember.feature.games.GamesScreen
import dev.chaik.remember.feature.games.fiveletters.FiveLettersRoute
import dev.chaik.remember.feature.games.fiveletters.FiveLettersScreen
import dev.chaik.remember.feature.games.raskobki.RaskobkiPlayRoute
import dev.chaik.remember.feature.games.raskobki.RaskobkiPlayScreen
import dev.chaik.remember.feature.games.raskobki.RaskobkiProfileRoute
import dev.chaik.remember.feature.games.raskobki.RaskobkiProfileScreen
import dev.chaik.remember.feature.games.raskobki.RaskobkiRoute
import dev.chaik.remember.feature.games.raskobki.RaskobkiScreen
import dev.chaik.remember.feature.gym.GymRoute
import dev.chaik.remember.feature.gym.GymScreen
import dev.chaik.remember.feature.gym.templates.TemplateEditorRoute
import dev.chaik.remember.feature.gym.templates.TemplateEditorScreen
import dev.chaik.remember.feature.gym.templates.TemplatesRoute
import dev.chaik.remember.feature.gym.templates.TemplatesScreen
import dev.chaik.remember.feature.gym.workout.WorkoutRoute
import dev.chaik.remember.feature.gym.workout.WorkoutScreen
import dev.chaik.remember.feature.health.HealthRoute
import dev.chaik.remember.feature.health.HealthScreen
import dev.chaik.remember.feature.map.MapRoute
import dev.chaik.remember.feature.map.MapScreen
import dev.chaik.remember.feature.more.MoreRoute
import dev.chaik.remember.feature.more.MoreScreen
import dev.chaik.remember.feature.network.NetworkRoute
import dev.chaik.remember.feature.network.NetworkScreen
import dev.chaik.remember.feature.notes.NotesRoute
import dev.chaik.remember.feature.notes.NotesScreen
import dev.chaik.remember.feature.notes.editor.NoteEditorRoute
import dev.chaik.remember.feature.notes.editor.NoteEditorScreen
import dev.chaik.remember.feature.search.SearchRoute
import dev.chaik.remember.feature.search.SearchScreen
import dev.chaik.remember.feature.settings.SettingsRoute
import dev.chaik.remember.feature.settings.SettingsScreen
import dev.chaik.remember.feature.settings.appearance.PersonalizationRoute
import dev.chaik.remember.feature.settings.appearance.PersonalizationScreen
import dev.chaik.remember.feature.settings.about.AboutRoute
import dev.chaik.remember.feature.settings.about.AboutScreen
import dev.chaik.remember.feature.settings.data.DataRoute
import dev.chaik.remember.feature.settings.feedback.FeedbackRoute
import dev.chaik.remember.feature.settings.feedback.FeedbackScreen
import dev.chaik.remember.feature.settings.data.DataScreen
import dev.chaik.remember.feature.settings.data.gallery.GalleryRoute
import dev.chaik.remember.feature.settings.data.gallery.GalleryScreen
import dev.chaik.remember.feature.settings.pages.PagesRoute
import dev.chaik.remember.feature.settings.widgets.WidgetsRoute
import dev.chaik.remember.feature.settings.widgets.WidgetsScreen
import dev.chaik.remember.feature.settings.pages.PagesScreen
import dev.chaik.remember.feature.settings.profile.ProfileRoute
import dev.chaik.remember.feature.settings.profile.ProfileScreen
import dev.chaik.remember.feature.settings.reminders.RemindersRoute
import dev.chaik.remember.feature.settings.security.SecurityRoute
import dev.chaik.remember.feature.settings.security.SecurityScreen
import dev.chaik.remember.feature.settings.reminders.RemindersScreen
import dev.chaik.remember.feature.stats.StatsRoute
import dev.chaik.remember.feature.stats.StatsScreen
import dev.chaik.remember.feature.stats.days.DaysStatsRoute
import dev.chaik.remember.feature.stats.days.DaysStatsScreen
import dev.chaik.remember.feature.stats.finance.FinanceStatsRoute
import dev.chaik.remember.feature.stats.finance.FinanceStatsScreen
import dev.chaik.remember.feature.stats.gym.GymStatsRoute
import dev.chaik.remember.feature.stats.gym.GymStatsScreen
import dev.chaik.remember.feature.stats.health.HealthStatsRoute
import dev.chaik.remember.feature.stats.health.HealthStatsScreen
import dev.chaik.remember.feature.stats.sleep.SleepStatsRoute
import dev.chaik.remember.feature.stats.sleep.SleepStatsScreen
import dev.chaik.remember.feature.tasks.TasksRoute
import dev.chaik.remember.feature.tasks.TasksScreen
import dev.chaik.remember.feature.tasks.editor.TaskEditorRoute
import dev.chaik.remember.feature.tasks.editor.TaskEditorScreen
import dev.chaik.remember.feature.time.AlarmEditorRoute
import dev.chaik.remember.feature.time.AlarmEditorScreen
import dev.chaik.remember.feature.time.TimeRoute
import dev.chaik.remember.feature.time.TimeScreen
import dev.chaik.remember.feature.today.money.MoneyCategoriesRoute
import dev.chaik.remember.feature.today.money.MoneyCategoriesScreen
import dev.chaik.remember.feature.today.TodayRoute
import dev.chaik.remember.feature.today.TodayScreen

/** Связывает маршруты с экранами. Новый экран = новая строка здесь. */
fun appEntryProvider(backStack: NavBackStack<NavKey>): (NavKey) -> NavEntry<NavKey> {
    val back: () -> Unit = { backStack.removeLastOrNull() }

    /** «Назад» для раздела: у корня вкладки его нет, внутри «Ещё» — есть. */
    fun backFor(key: NavKey): (() -> Unit)? = if (backStack.firstOrNull() == key) null else back

    return entryProvider {
        // Корни вкладок
        entry<TodayRoute> { TodayScreen(onOpenMoneyCategories = { backStack.add(MoneyCategoriesRoute) }, onBack = backFor(it)) }
        entry<MoneyCategoriesRoute> { MoneyCategoriesScreen(onBack = back) }
        entry<NotesRoute> {
            NotesScreen(
                onBack = backFor(it),
                onOpen = { backStack.add(NoteEditorRoute(noteId = it)) },
                onCreate = { backStack.add(NoteEditorRoute(folderId = it, opening = System.nanoTime())) },
            )
        }
        entry<TasksRoute> { TasksScreen(onBack = backFor(it), onOpen = { backStack.add(TaskEditorRoute(it)) }) }
        entry<CalendarRoute> {
            CalendarScreen(
                onBack = backFor(it),
                onOpen = { backStack.add(EventEditorRoute(eventId = it)) },
                onCreate = { backStack.add(EventEditorRoute(date = it.toEpochDay())) },
            )
        }
        entry<MoreRoute> { MoreScreen(onOpen = { backStack.add(it) }) }

        // Вложенные страницы вкладок
        entry<NoteEditorRoute> { NoteEditorScreen(route = it, onBack = back) }
        entry<TaskEditorRoute> { TaskEditorScreen(route = it, onBack = back) }
        entry<EventEditorRoute> { EventEditorScreen(route = it, onBack = back) }

        // Разделы из «Ещё»
        entry<FeedRoute> { FeedScreen(onBack = backFor(it)) }
        entry<SearchRoute> { SearchScreen(onBack = backFor(it), onOpen = { backStack.add(it) }) }
        entry<StatsRoute> { StatsScreen(onBack = backFor(it), onOpen = { backStack.add(it) }) }
        entry<SleepStatsRoute> { SleepStatsScreen(onBack = back) }
        entry<DaysStatsRoute> { DaysStatsScreen(onBack = back) }
        entry<HealthStatsRoute> { HealthStatsScreen(onBack = back) }
        entry<FinanceStatsRoute> { FinanceStatsScreen(onBack = back) }
        entry<GymStatsRoute> { GymStatsScreen(onBack = back) }
        entry<FinanceRoute> { FinanceScreen(onBack = backFor(it)) }
        entry<HealthRoute> { HealthScreen(onBack = backFor(it)) }
        entry<GymRoute> {
            GymScreen(
                onBack = backFor(it),
                onOpen = { id -> backStack.add(WorkoutRoute(id)) },
                onTemplates = { backStack.add(TemplatesRoute) },
                onExercises = { backStack.add(ExercisesRoute) },
            )
        }
        entry<ExercisesRoute> { ExercisesScreen(onBack = back) }
        entry<TemplatesRoute> { TemplatesScreen(onBack = back, onOpen = { id -> backStack.add(TemplateEditorRoute(id)) }) }
        entry<TemplateEditorRoute> { TemplateEditorScreen(route = it, onBack = back) }
        entry<WorkoutRoute> { WorkoutScreen(route = it, onBack = back) }
        entry<NetworkRoute> { NetworkScreen(onBack = backFor(it)) }
        entry<TimeRoute> { TimeScreen(onBack = backFor(it), onOpenAlarm = { id -> backStack.add(AlarmEditorRoute(id)) }) }
        entry<AlarmEditorRoute> { AlarmEditorScreen(route = it, onBack = back) }
        entry<GamesRoute> { GamesScreen(onBack = backFor(it), onOpen = { route -> backStack.add(route) }) }
        entry<MapRoute> { MapScreen(onBack = backFor(it)) }
        entry<FiveLettersRoute> { FiveLettersScreen(onBack = back) }
        entry<RaskobkiRoute> {
            RaskobkiScreen(
                onBack = back,
                onOpenDay = { backStack.add(RaskobkiPlayRoute(it.toString())) },
                onOpenProfile = { backStack.add(RaskobkiProfileRoute) },
            )
        }
        entry<RaskobkiProfileRoute> { RaskobkiProfileScreen(onBack = back) }
        entry<RaskobkiPlayRoute> { RaskobkiPlayScreen(route = it, onBack = back) }
        entry<SettingsRoute> { SettingsScreen(onBack = back, onOpen = { backStack.add(it) }) }
        entry<RemindersRoute> { RemindersScreen(onBack = back) }
        entry<ProfileRoute> { ProfileScreen(onBack = back) }
        entry<PagesRoute> { PagesScreen(onBack = back) }
        entry<WidgetsRoute> { WidgetsScreen(onBack = back) }
        entry<SecurityRoute> { SecurityScreen(onBack = back) }
        entry<DataRoute> { DataScreen(onBack = back, onOpenGallery = { backStack.add(GalleryRoute) }) }
        entry<GalleryRoute> { GalleryScreen(onBack = back, onOpenNote = { backStack.add(NoteEditorRoute(noteId = it)) }) }
        entry<AboutRoute> { AboutScreen(onBack = back) }
        entry<FeedbackRoute> { FeedbackScreen(onBack = back) }
        entry<PersonalizationRoute> { PersonalizationScreen(onBack = back) }
    }
}
