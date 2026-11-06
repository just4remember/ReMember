package dev.chaik.remember.feature.search

import dev.chaik.remember.app.AppContainer
import dev.chaik.remember.core.editor.MarkdownPreview
import dev.chaik.remember.core.ui.format.MoneyFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/** Подписи, которые нужны индексу (берутся из ресурсов на экране). */
data class SearchLabels(
    val workout: (Int) -> String,
    val noCategory: String,
)

private val DayTitle = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())

/** Без ограничения длины: ищем по всему тексту, а не по превью. */
private fun plain(markdown: String) = MarkdownPreview.plain(markdown, maxChars = Int.MAX_VALUE)

/**
 * Всё, по чему ищем, одним списком. Данных у дневника немного — держим их в памяти и
 * фильтруем мгновенно; список обновляется сам, когда что-то меняется в базе.
 */
fun searchIndex(container: AppContainer, labels: SearchLabels, currency: String): Flow<List<SearchItem>> {
    val zone = ZoneId.systemDefault()
    val from = LocalDate.of(2000, 1, 1)
    val to = LocalDate.now().plusYears(1)

    val days = combine(container.dayRepository.observeRange(from, to), container.dayRepository.observeTags()) { entries, tags ->
        entries.filter { it.text.isNotBlank() || tags[it.date].orEmpty().isNotEmpty() }.map { e ->
            SearchItem(
                source = SearchSource.DAYS,
                key = "day-${e.date}",
                title = e.date.format(DayTitle),
                text = plain(e.text),
                date = e.date,
                target = SearchTarget.Day(e.date),
                tags = tags[e.date].orEmpty().map { it.name },
            )
        }
    }
    val dreams = container.sleepRepository.observeRange(from, to)
    val notes = combine(container.noteRepository.observeAll(), container.noteRepository.observeSummaries()) { all, summaries ->
        val tags = summaries.associate { it.id to it.tags }
        all.map { n ->
            SearchItem(
                source = SearchSource.NOTES,
                key = "note-${n.id}",
                title = n.title,
                text = if (n.isProtected) "" else plain(n.text),
                date = n.updatedAt?.atZone(zone)?.toLocalDate(),
                target = SearchTarget.Note(n.id),
                tags = if (n.isProtected) emptyList() else tags[n.id].orEmpty().map { it.name },
                hidden = n.isProtected,
            )
        }
    }
    val tasks = container.taskRepository.observeAll()
    val events = container.eventRepository.observeAll()
    val money = combine(container.moneyRepository.observeRange(from, to), container.moneyRepository.observeAllCategories()) { list, cats ->
        val names = cats.associate { it.id to it.name }
        list.map { t ->
            val category = t.categoryId?.let(names::get) ?: labels.noCategory
            SearchItem(
                source = SearchSource.MONEY,
                key = "money-${t.id}",
                title = "$category · ${MoneyFormat.format(t.amountMinor, t.currency.ifBlank { currency })}",
                text = t.comment,
                date = t.date,
                target = SearchTarget.Day(t.date),
            )
        }
    }
    val gym = container.workoutRepository.observeAllFull()
    val places = container.placeRepository.observeAll()

    val first = combine(days, dreams, notes, tasks) { d, sleep, n, t ->
        d + sleep.filter { it.dream.isNotBlank() }.map { s ->
            SearchItem(
                source = SearchSource.DREAMS,
                key = "dream-${s.date}",
                title = s.date.format(DayTitle),
                text = plain(s.dream),
                date = s.date,
                target = SearchTarget.Day(s.date),
            )
        } + n + t.map { task ->
            SearchItem(
                source = SearchSource.TASKS,
                key = "task-${task.id}",
                title = task.title,
                text = listOf(plain(task.notes), task.subtasks.joinToString("\n") { it.title }).filter { it.isNotBlank() }.joinToString("\n"),
                date = task.dueDate ?: task.createdAt?.atZone(zone)?.toLocalDate(),
                target = SearchTarget.Task(task.id),
            )
        }
    }
    val second = combine(events, money, gym, places) { e, m, g, p ->
        e.map { event ->
            SearchItem(
                source = SearchSource.EVENTS,
                key = "event-${event.id}",
                title = event.title,
                text = event.description,
                date = event.date,
                target = SearchTarget.Event(event.id),
            )
        } + m + g.map { w ->
            SearchItem(
                source = SearchSource.GYM,
                key = "workout-${w.id}",
                title = labels.workout(w.number),
                text = (w.exercises.map { it.exercise.name } + listOf(w.note)).filter { it.isNotBlank() }.joinToString(", "),
                date = w.date,
                target = SearchTarget.Workout(w.id),
            )
        } + p.map { place ->
            SearchItem(
                source = SearchSource.PLACES,
                key = "place-${place.id}",
                title = place.title,
                text = place.note,
                date = place.visitedOn,
                target = SearchTarget.Map,
            )
        }
    }
    return combine(first, second) { a, b -> a + b }.flowOn(Dispatchers.Default)
}
