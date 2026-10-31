package dev.chaik.remember.feature.gym

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.data.ImportProblem
import dev.chaik.remember.core.data.WorkoutImportException
import dev.chaik.remember.core.model.WorkoutSummary
import dev.chaik.remember.core.model.formatKg
import dev.chaik.remember.core.model.withExercise
import dev.chaik.remember.core.ui.components.DueDatePickerDialog
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.core.ui.components.TimePickerDialog
import dev.chaik.remember.core.ui.primaryCornerModifier
import dev.chaik.remember.feature.calendar.formatDay
import dev.chaik.remember.feature.notes.ConfirmDialog
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object GymRoute : NavKey

/**
 * Зал: тренировки, новые сверху, и кнопка «Начать тренировку».
 * В меню — запись прошлой тренировки вручную.
 */
@Composable
fun GymScreen(
    onBack: (() -> Unit)?,
    onOpen: (workoutId: Long) -> Unit,
    onTemplates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalContext.current.appContainer
    val viewModel = viewModel { GymViewModel(container) }
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    val active = summaries?.firstOrNull { it.active }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = summaries.orEmpty().withExercise(query)

    // Тренировку закончили — по возвращении в список прокручиваем к самому верху, к ней.
    // Последнюю идущую помним в сохраняемом состоянии: пока открыта тренировка, этот экран не на виду
    val listState = rememberLazyListState()
    var lastActive by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(summaries != null, active?.id) {
        if (summaries == null) return@LaunchedEffect
        if (lastActive != null && active == null) listState.animateScrollToItem(0)
        lastActive = active?.id
    }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readImport(uri)
    }

    var menu by remember { mutableStateOf(false) }
    var addingPast by remember { mutableStateOf(false) }
    // Момент нажатия «Начать» и подставленный вес — пока открыта форма веса
    var starting by remember { mutableStateOf<Pair<LocalDateTime, Double?>?>(null) }

    // Выделение: долгое нажатие на тренировку. Пока что-то выделено — тап выделяет, а не открывает
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val selecting = selected.isNotEmpty()
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(enabled = selecting) { selected = emptySet() }
    // Удалённые из базы — убираем и из выделения
    LaunchedEffect(summaries) {
        val ids = summaries.orEmpty().map { it.id }.toSet()
        if (!ids.containsAll(selected)) selected = selected intersect ids
    }
    // «Выбрать все» при поиске — только найденные
    val allIds = shown.map { it.id }.toSet()

    ScreenScaffold(
        title = if (selecting) stringResource(R.string.gym_selected, selected.size) else stringResource(R.string.section_gym),
        modifier = modifier,
        onBack = onBack,
        navigationIcon = if (selecting) {
            {
                TextButton(onClick = { selected = if (selected == allIds) emptySet() else allIds }) {
                    Text(stringResource(if (selected == allIds) R.string.gym_select_none else R.string.gym_select_all))
                }
            }
        } else {
            null
        },
        actions = {
            if (selecting) {
                IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Outlined.Close, stringResource(R.string.gym_select_cancel)) }
                return@ScreenScaffold
            }
            IconButton(onClick = onTemplates) { Icon(Icons.AutoMirrored.Outlined.ListAlt, stringResource(R.string.gym_templates)) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.gym_more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.gym_import)) },
                        leadingIcon = { Icon(Icons.Outlined.FileOpen, null) },
                        onClick = {
                            menu = false
                            // Samsung показывает .json как «любой файл» — разрешаем всё, проверим при чтении
                            container.appLock.allowExternal()
                            pickFile.launch(arrayOf("application/json", "text/*", "*/*"))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.gym_add_past)) },
                        leadingIcon = { Icon(Icons.Outlined.History, null) },
                        onClick = {
                            menu = false
                            addingPast = true
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            val list = summaries
            when {
                list == null -> Unit
                list.isEmpty() -> Text(
                    text = stringResource(R.string.gym_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = padding.calculateTopPadding() + 32.dp, start = 32.dp, end = 32.dp),
                )
                else -> LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 96.dp,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "search") {
                        val focus = LocalFocusManager.current
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(R.string.gym_search)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            trailingIcon = if (query.isEmpty()) {
                                null
                            } else {
                                {
                                    IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, stringResource(R.string.gym_search_clear)) }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (shown.isEmpty()) {
                        item(key = "nothing") {
                            Text(
                                text = stringResource(R.string.gym_search_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp),
                            )
                        }
                    }
                    items(shown.asReversed(), key = { it.id }) { summary ->
                        val isSelected = summary.id in selected
                        WorkoutCard(
                            summary = summary,
                            selected = isSelected,
                            onClick = {
                                when {
                                    !selecting -> onOpen(summary.id)
                                    isSelected -> selected = selected - summary.id
                                    else -> selected = selected + summary.id
                                }
                            },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                selected = selected + summary.id
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = selecting,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = primaryCornerModifier(bottom = padding.calculateBottomPadding() + 8.dp),
            ) {
                FloatingActionButton(
                    onClick = { confirmDelete = true },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Icon(Icons.Outlined.Delete, stringResource(R.string.notes_delete), tint = MaterialTheme.colorScheme.error)
                }
            }

            if (!selecting) ExtendedFloatingActionButton(
                onClick = {
                    if (active != null) {
                        onOpen(active.id)
                    } else {
                        val now = LocalDateTime.now()
                        scope.launch { starting = now to viewModel.lastBodyWeight() }
                    }
                },
                icon = { Icon(Icons.Outlined.FitnessCenter, null) },
                text = { Text(stringResource(if (active != null) R.string.gym_continue else R.string.gym_start)) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = padding.calculateBottomPadding() + 8.dp),
            )
        }
    }

    starting?.let { (startedAt, lastWeight) ->
        val start = { weight: Double? ->
            starting = null
            viewModel.start(startedAt, weight, onOpen)
        }
        BodyWeightDialog(
            title = stringResource(R.string.gym_weight_before),
            initial = lastWeight,
            onSave = { start(it) },
            onSkip = { start(null) },
        )
    }

    viewModel.importState?.let { state -> ImportDialog(state, onConfirm = viewModel::confirmImport, onDismiss = viewModel::dismissImport) }

    if (confirmDelete) {
        ConfirmDialog(
            title = pluralStringResource(R.plurals.gym_delete_many, selected.size, selected.size),
            text = stringResource(R.string.gym_delete_text),
            confirmLabel = stringResource(R.string.notes_delete),
            onConfirm = {
                viewModel.delete(selected)
                selected = emptySet()
                confirmDelete = false
            },
            onDismiss = { confirmDelete = false },
        )
    }

    if (addingPast) {
        PastWorkoutDialog(
            onCreate = { date, start, end ->
                addingPast = false
                viewModel.createPast(date, start, end, onOpen)
            },
            onDismiss = { addingPast = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkoutCard(
    summary: WorkoutSummary,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val today = LocalDate.now()
    val times = listOfNotNull(
        summary.start?.let { start -> formatTime(start) + (summary.end?.let { "–" + formatTime(it) } ?: "") },
        workoutDuration(summary.start, summary.end)?.let { durationLabel(it) },
    ).joinToString(" · ")
    val weight = when {
        summary.weightBefore != null && summary.weightAfter != null ->
            stringResource(R.string.gym_weight_change, formatKg(summary.weightBefore), formatKg(summary.weightAfter))
        summary.weightBefore != null -> stringResource(R.string.unit_kg_value, formatKg(summary.weightBefore))
        summary.weightAfter != null -> stringResource(R.string.gym_weight_after_only, formatKg(summary.weightAfter))
        else -> null
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    selected -> colors.secondaryContainer
                    summary.active -> colors.primaryContainer
                    else -> colors.surfaceContainerLow
                },
            )
            .then(if (selected) Modifier.border(2.dp, colors.primary, RoundedCornerShape(20.dp)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.gym_number_short, summary.number),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (summary.active) colors.onPrimaryContainer else colors.primary,
            )
            Text(
                text = formatDay(summary.date, withYear = summary.date.year != today.year),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
            if (weight != null) {
                Text(weight, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            if (selected) {
                Icon(Icons.Filled.CheckCircle, null, tint = colors.primary, modifier = Modifier.padding(start = 8.dp).size(22.dp))
            }
        }
        val status = if (summary.active) stringResource(R.string.gym_in_progress) else null
        val line = listOfNotNull(status, times.ifBlank { null }).joinToString(" · ")
        if (line.isNotEmpty()) {
            Text(line, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Text(
            text = summary.exerciseNames.joinToString(", ").ifEmpty { stringResource(R.string.gym_no_exercises) },
            style = MaterialTheme.typography.bodyMedium,
            color = if (summary.exerciseNames.isEmpty()) colors.outline else colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (summary.imported) {
            Text(
                text = stringResource(R.string.gym_imported),
                style = MaterialTheme.typography.labelSmall,
                color = colors.outline,
            )
        }
    }
}

/** Ошибки импорта по строчке; в конце — «…и ещё N». */
@Composable
private fun importErrorText(error: WorkoutImportException): String {
    val lines = error.problems.map { problemText(it) }
    val more = if (error.more > 0) listOf(stringResource(R.string.gym_import_more_errors, error.more)) else emptyList()
    return (lines + more).joinToString("\n")
}

@Composable
private fun problemText(problem: ImportProblem): String = when (problem) {
    ImportProblem.NotWorkoutFile -> stringResource(R.string.gym_import_not_file)
    is ImportProblem.UnknownVersion -> stringResource(R.string.gym_import_bad_version, problem.version)
    is ImportProblem.BadDate -> stringResource(R.string.gym_import_bad_date, problem.value)
    is ImportProblem.BadTime -> stringResource(
        if (problem.isStart) R.string.gym_import_bad_start else R.string.gym_import_bad_end,
        problem.value,
    )
    ImportProblem.NoName -> stringResource(R.string.gym_import_no_name)
    is ImportProblem.BadDuration -> stringResource(R.string.gym_import_bad_duration, problem.exercise, problem.value)
    is ImportProblem.BadKind -> stringResource(R.string.gym_import_bad_kind, problem.exercise, problem.value)
    is ImportProblem.InWorkout -> stringResource(R.string.gym_import_in_workout, problem.index, problem.date, problemText(problem.problem))
}

@Composable
private fun ImportDialog(state: ImportState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (state !is ImportState.Reading) onDismiss() },
        title = { Text(stringResource(R.string.gym_import_title)) },
        text = {
            when (state) {
                ImportState.Reading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(R.string.gym_import_reading), modifier = Modifier.padding(start = 16.dp))
                }
                is ImportState.Failed -> Text(stringResource(R.string.gym_import_failed, importErrorText(state.error)))
                is ImportState.Done -> Text(pluralStringResource(R.plurals.gym_import_done, state.count, state.count))
                is ImportState.Ready -> {
                    val p = state.preview
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (p.workouts.isEmpty()) {
                            Text(stringResource(R.string.gym_import_nothing))
                        } else {
                            Text(pluralStringResource(R.plurals.gym_import_found, p.workouts.size, p.workouts.size))
                            Text(
                                text = formatDay(p.workouts.first().date) + " – " + formatDay(p.workouts.last().date),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (p.newExercises.isNotEmpty()) {
                                Text(pluralStringResource(R.plurals.gym_import_new_exercises, p.newExercises.size, p.newExercises.size))
                            }
                        }
                        if (p.duplicates > 0) {
                            Text(
                                text = pluralStringResource(R.plurals.gym_import_duplicates, p.duplicates, p.duplicates),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                state is ImportState.Ready && state.preview.workouts.isNotEmpty() ->
                    TextButton(onClick = onConfirm) { Text(stringResource(R.string.gym_import_confirm)) }
                state !is ImportState.Reading ->
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
            }
        },
        dismissButton = {
            if (state is ImportState.Ready && state.preview.workouts.isNotEmpty()) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/** Прошлая тренировка вручную: дата и, если помнится, время начала и конца. */
@Composable
private fun PastWorkoutDialog(
    onCreate: (LocalDate, LocalTime?, LocalTime?) -> Unit,
    onDismiss: () -> Unit,
) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var start by remember { mutableStateOf<LocalTime?>(null) }
    var end by remember { mutableStateOf<LocalTime?>(null) }
    var picking by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gym_past_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = true,
                    onClick = { picking = "date" },
                    label = { Text(formatDay(date)) },
                    leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = start != null,
                        onClick = { picking = "start" },
                        label = { Text(stringResource(R.string.gym_start_time) + " " + (start?.let(::formatTime) ?: stringResource(R.string.gym_no_time))) },
                        leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)) },
                    )
                    FilterChip(
                        selected = end != null,
                        onClick = { picking = "end" },
                        label = { Text(stringResource(R.string.gym_end_time) + " " + (end?.let(::formatTime) ?: stringResource(R.string.gym_no_time))) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(date, start, end) }) { Text(stringResource(R.string.gym_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    when (picking) {
        "date" -> DueDatePickerDialog(
            initial = date,
            onPick = {
                date = it
                picking = null
            },
            onClear = null,
            onDismiss = { picking = null },
        )
        "start", "end" -> TimePickerDialog(
            initial = (if (picking == "start") start else end) ?: LocalTime.of(17, 0),
            onPick = {
                if (picking == "start") start = it else end = it
                picking = null
            },
            onClear = {
                if (picking == "start") start = null else end = null
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}
