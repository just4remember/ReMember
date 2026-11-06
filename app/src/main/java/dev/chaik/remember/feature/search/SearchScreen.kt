package dev.chaik.remember.feature.search

import dev.chaik.remember.feature.stats.onTapAnywhere
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import java.time.ZoneOffset
import java.time.Instant
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.reminders.OpenTarget
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.settings.logicalToday
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.feature.calendar.editor.EventEditorRoute
import dev.chaik.remember.feature.gym.workout.WorkoutRoute
import dev.chaik.remember.feature.map.MapRoute
import dev.chaik.remember.feature.notes.editor.NoteEditorRoute
import dev.chaik.remember.feature.tasks.editor.TaskEditorRoute
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
data object SearchRoute : NavKey

/** За какой срок искать. */
/** За какой срок искать; [CUSTOM] — отрезок, выбранный вручную. */
private enum class SearchPeriod(val days: Long?) { MONTH(30), YEAR(365), ALL(null), CUSTOM(null) }

/** Пауза перед фокусом поля: пока въезжает страница, фокус не держится. */
private const val FOCUS_DELAY_MS = 350L

/** Сколько результатов группы видно сразу; остальные — по «Ещё». */
private const val GROUP_PREVIEW = 3

// Каждый раз заново: после смены языка приложения даты должны быть на новом языке
private val ShortDate: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
// Каждый раз заново: после смены языка приложения даты должны быть на новом языке
private val ChipDate: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM yy", Locale.getDefault())

/**
 * Поиск по всему: дни, сны, заметки, задачи, события, траты, тренировки, места.
 * Результаты по мере набора, по разделам, с кусочком текста и подсветкой; тап — открыть на месте.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(onBack: (() -> Unit)?, onOpen: (NavKey) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = context.appContainer
    val settings = LocalAppSettings.current
    val today = settings.logicalToday()
    val workoutLabel = stringResource(R.string.search_workout_title)
    val labels = SearchLabels(workout = { workoutLabel.format(it) }, noCategory = stringResource(R.string.money_no_category))
    val items by remember { searchIndex(container, labels, settings.currency) }.collectAsStateWithLifecycle(emptyList())
    val history = remember { SearchHistory(context) }
    val recent = remember { mutableStateListOf<String>().apply { addAll(history.read()) } }

    var text by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf<SearchSource?>(null) }
    var period by rememberSaveable { mutableStateOf(SearchPeriod.ALL) }
    val expanded = remember { mutableStateListOf<SearchSource>() }
    val focus = remember { FocusRequester() }
    // Автофокус — один раз при открытии: поле в ленивом списке создаётся заново, когда
    // прокручиваешь обратно наверх, и без этого флага ловило бы фокус каждый раз
    var autoFocused by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    // Клавиатуру свернули — курсор уходит из поля (только при сворачивании, не при открытии экрана)
    val focusManager = LocalFocusManager.current
    val keyboardVisible = WindowInsets.isImeVisible
    var keyboardWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(keyboardVisible) {
        if (keyboardWasVisible && !keyboardVisible) focusManager.clearFocus()
        keyboardWasVisible = keyboardVisible
    }

    val query = remember(text) { SearchEngine.parse(text) }
    // Свой отрезок — дни с 1970-го, чтобы пережить поворот экрана
    var customFrom by rememberSaveable { mutableStateOf<Long?>(null) }
    var customTo by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickingRange by remember { mutableStateOf(false) }
    val range: ClosedRange<LocalDate>? = when (period) {
        SearchPeriod.CUSTOM -> customFrom?.let { f -> LocalDate.ofEpochDay(f)..LocalDate.ofEpochDay(customTo ?: f) }
        else -> period.days?.let { today.minusDays(it)..LocalDate.MAX }
    }
    val results = remember(items, query, source, range) {
        if (query.isEmpty) {
            emptyMap()
        } else {
            val pool = items.filter { (source == null || it.source == source) && (range == null || (it.date != null && it.date in range)) }
            SearchEngine.search(pool, query)
        }
    }
    LaunchedEffect(query) { expanded.clear() }

    fun saveQuery(q: String) {
        val clean = q.trim()
        if (clean.isEmpty()) return
        recent.remove(clean)
        recent.add(0, clean)
        while (recent.size > SearchHistory.LIMIT) recent.removeAt(recent.lastIndex)
        history.write(recent)
    }

    fun open(target: SearchTarget) {
        saveQuery(text)
        keyboard?.hide()
        when (target) {
            is SearchTarget.Day -> container.openRequests.value = OpenTarget.DayAt(target.date)
            is SearchTarget.Note -> onOpen(NoteEditorRoute(noteId = target.id))
            is SearchTarget.Task -> onOpen(TaskEditorRoute(target.id))
            is SearchTarget.Event -> onOpen(EventEditorRoute(eventId = target.id))
            is SearchTarget.Workout -> onOpen(WorkoutRoute(target.id))
            SearchTarget.Map -> onOpen(MapRoute)
        }
    }

    ScreenScaffold(title = stringResource(R.string.section_search), modifier = modifier, onBack = onBack) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            // Результаты не прячутся под клавиатурой — список заканчивается над ней
            // Тап по свободному месту тоже убирает курсор и клавиатуру
            modifier = Modifier
                .fillMaxSize()
                .onTapAnywhere { focusManager.clearFocus() }
                .imePadding(),
        ) {
            item(key = "field") {
                // Поле в ленивом списке появляется не сразу, а страница ещё въезжает —
                // фокус и клавиатура, когда переход закончился
                LaunchedEffect(Unit) {
                    if (autoFocused) return@LaunchedEffect
                    autoFocused = true
                    delay(FOCUS_DELAY_MS)
                    focus.requestFocus()
                    keyboard?.show()
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = if (text.isNotEmpty()) {
                        { IconButton(onClick = { text = "" }) { Icon(Icons.Outlined.Close, stringResource(R.string.search_clear)) } }
                    } else {
                        null
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        saveQuery(text)
                        keyboard?.hide()
                    }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            item(key = "sources") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(stringResource(R.string.search_all), source == null) { source = null }
                    SearchSource.entries.forEach { s -> Chip(stringResource(s.label()), source == s) { source = if (source == s) null else s } }
                }
            }
            item(key = "period") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SearchPeriod.entries.forEach { p ->
                        val label = when (p) {
                            SearchPeriod.MONTH -> stringResource(R.string.search_month)
                            SearchPeriod.YEAR -> stringResource(R.string.search_year)
                            SearchPeriod.ALL -> stringResource(R.string.stats_all)
                            // Выбранный отрезок — прямо на чипсе
                            SearchPeriod.CUSTOM -> range?.takeIf { period == SearchPeriod.CUSTOM }
                                ?.let { r -> "${r.start.format(ChipDate)} - ${r.endInclusive.format(ChipDate)}" }
                                ?: stringResource(R.string.search_custom)
                        }
                        Chip(label, period == p) {
                            if (p == SearchPeriod.CUSTOM) pickingRange = true else period = p
                        }
                    }
                }
            }

            if (query.isEmpty) {
                if (recent.isNotEmpty()) {
                    item(key = "recent-title") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(
                                stringResource(R.string.search_recent),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                recent.clear()
                                history.write(recent)
                            }) { Text(stringResource(R.string.search_recent_clear)) }
                        }
                    }
                    items(recent.toList(), key = { "recent-$it" }) { q ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { text = q }
                                .padding(start = 4.dp),
                        ) {
                            Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Text(q, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                            IconButton(onClick = {
                                recent.remove(q)
                                history.write(recent)
                            }) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.search_recent_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    item(key = "hint") {
                        Text(
                            stringResource(R.string.search_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp),
                        )
                    }
                }
            } else if (results.isEmpty()) {
                item(key = "nothing") {
                    Text(
                        stringResource(R.string.search_nothing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp),
                    )
                }
            } else {
                results.forEach { (group, hits) ->
                    item(key = "group-$group") {
                        Text(
                            "${stringResource(group.label())} · ${hits.size}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        )
                    }
                    val shown = if (group in expanded) hits else hits.take(GROUP_PREVIEW)
                    items(shown, key = { it.item.key }) { hit ->
                        ResultRow(hit, onOpen = { open(hit.item.target) }, onTag = { tag -> text = "#$tag" })
                    }
                    if (hits.size > GROUP_PREVIEW && group !in expanded) {
                        item(key = "more-$group") {
                            TextButton(onClick = { expanded += group }) {
                                Text(stringResource(R.string.search_more, hits.size - GROUP_PREVIEW))
                            }
                        }
                    }
                }
            }
        }
    }
    if (pickingRange) {
        RangeDialog(
            from = customFrom,
            to = customTo,
            onPick = { f, t ->
                customFrom = f
                customTo = t
                period = SearchPeriod.CUSTOM
                pickingRange = false
            },
            onDismiss = { pickingRange = false },
        )
    }
}

/** Выбор своего отрезка: календарь с началом и концом. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeDialog(from: Long?, to: Long?, onPick: (Long, Long) -> Unit, onDismiss: () -> Unit) {
    fun millis(day: Long?) = day?.let { LocalDate.ofEpochDay(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
    val state = rememberDateRangePickerState(initialSelectedStartDateMillis = millis(from), initialSelectedEndDateMillis = millis(to))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis ?: return@TextButton
                    // Выбрали один день — отрезок из одного дня
                    onPick(day(start), day(state.selectedEndDateMillis ?: start))
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        // Свой короткий заголовок: стандартный крупный в узком окне разъезжается по буквам
        val start = state.selectedStartDateMillis?.let { LocalDate.ofEpochDay(day(it)).format(ChipDate) } ?: "…"
        val end = state.selectedEndDateMillis?.let { LocalDate.ofEpochDay(day(it)).format(ChipDate) } ?: "…"
        DateRangePicker(
            state = state,
            showModeToggle = false,
            title = {
                Text(
                    stringResource(R.string.search_custom_title),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
            headline = {
                Text(
                    "$start - $end",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        label = { Text(label) },
    )
}

private fun SearchSource.label(): Int = when (this) {
    SearchSource.DAYS -> R.string.stats_days
    SearchSource.DREAMS -> R.string.search_dreams
    SearchSource.NOTES -> R.string.tab_notes
    SearchSource.TASKS -> R.string.tab_tasks
    SearchSource.EVENTS -> R.string.search_events
    SearchSource.MONEY -> R.string.stats_finance
    SearchSource.GYM -> R.string.stats_gym
    SearchSource.PLACES -> R.string.search_places
}

/** Найденное: заголовок и дата, кусочек текста с подсветкой, теги (тап — искать по тегу). */
@Composable
private fun ResultRow(hit: SearchHit, onOpen: () -> Unit, onTag: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val mark = SpanStyle(color = colors.primary, fontWeight = FontWeight.SemiBold)
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    highlighted(hit.item.title.ifBlank { stringResource(R.string.search_untitled) }, hit.titleHighlights, mark),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                hit.item.date?.let { d: LocalDate ->
                    Text(
                        d.format(ShortDate),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            when {
                hit.item.hidden -> Text(
                    stringResource(R.string.search_protected),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                hit.snippet != null -> Text(
                    highlighted(hit.snippet.text, hit.snippet.highlights, mark),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (hit.item.tags.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    hit.item.tags.forEach { tag -> AssistChip(onClick = { onTag(tag) }, label = { Text("#$tag") }) }
                }
            }
        }
    }
}

private fun highlighted(text: String, ranges: List<IntRange>, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(text)
    ranges.forEach { r -> if (r.first >= 0 && r.last < text.length) addStyle(style, r.first, r.last + 1) }
}

/** Последние запросы — только на этом телефоне, в настройках приложения. */
private class SearchHistory(context: Context) {
    private val prefs = context.getSharedPreferences("search", Context.MODE_PRIVATE)

    fun read(): List<String> = prefs.getString(KEY, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    fun write(list: List<String>) = prefs.edit { putString(KEY, list.joinToString("\n")) }

    companion object {
        const val LIMIT = 10
        private const val KEY = "recent"
    }
}
