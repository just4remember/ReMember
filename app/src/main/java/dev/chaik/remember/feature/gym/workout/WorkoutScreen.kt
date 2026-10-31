package dev.chaik.remember.feature.gym.workout

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.model.Exercise
import dev.chaik.remember.core.model.ExerciseHistory
import dev.chaik.remember.core.model.Workout
import dev.chaik.remember.core.model.WorkoutExercise
import dev.chaik.remember.core.model.WorkoutSet
import dev.chaik.remember.core.model.WorkoutTemplate
import dev.chaik.remember.core.model.formatKg
import dev.chaik.remember.core.ui.components.DueDatePickerDialog
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.core.ui.components.TimePickerDialog
import dev.chaik.remember.feature.calendar.formatDay
import dev.chaik.remember.feature.gym.BodyWeightDialog
import dev.chaik.remember.feature.gym.TemplatePickerDialog
import dev.chaik.remember.feature.gym.durationLabel
import dev.chaik.remember.feature.gym.formatTime
import dev.chaik.remember.feature.gym.workoutDuration
import dev.chaik.remember.feature.notes.ConfirmDialog
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalTime

/** @param finish открыть сразу с формой «закончить» (кнопка в уведомлении). */
@Serializable
data class WorkoutRoute(val workoutId: Long, val finish: Boolean = false) : NavKey

/** Что сейчас открыто поверх страницы. */
private sealed interface Sheet {
    data object Date : Sheet
    data object Start : Sheet
    data object End : Sheet
    data object WeightBefore : Sheet
    data object WeightAfter : Sheet

    /** Выбор, что править: начало или конец / вес до или после. */
    data object TimeRange : Sheet
    data object WeightRange : Sheet
    data class Finish(val end: LocalTime, val suggested: Double?) : Sheet
    data object AddExercise : Sheet
    data object ApplyTemplates : Sheet
    data class AddSet(val entry: WorkoutExercise) : Sheet
    data class EditSet(val entry: WorkoutExercise, val set: WorkoutSet) : Sheet
    data class Kind(val entry: WorkoutExercise) : Sheet
    data class Chart(val entry: WorkoutExercise) : Sheet
    data object Delete : Sheet
}

/**
 * Тренировка: дата, время, вес до и после, упражнения с подходами и заметка.
 * Пока тренировка идёт — внизу кнопка «Закончить тренировку».
 */
@Composable
fun WorkoutScreen(route: WorkoutRoute, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val viewModel = viewModel { WorkoutViewModel(container, route.workoutId) }
    val workout by viewModel.workout.collectAsStateWithLifecycle()
    val dictionary by viewModel.exercises.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val askFinish = {
        val end = LocalTime.now()
        scope.launch {
            val current = viewModel.workout.value ?: return@launch
            sheet = Sheet.Finish(end, current.weightAfter ?: current.weightBefore ?: viewModel.lastBodyWeight())
        }
    }
    // Из уведомления «Закончить» — форма сразу, но только один раз (не после поворота экрана)
    var finishHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(workout?.active) {
        if (route.finish && !finishHandled && workout?.active == true) {
            finishHandled = true
            askFinish()
        }
    }

    val current = workout
    ScreenScaffold(
        title = current?.let { stringResource(R.string.gym_number, it.number) } ?: "",
        modifier = modifier,
        onBack = onBack,
        actions = {
            IconButton(onClick = { sheet = Sheet.Delete }) { Icon(Icons.Outlined.Delete, stringResource(R.string.notes_delete), tint = MaterialTheme.colorScheme.error) }
        },
    ) { padding ->
        if (current == null || !viewModel.isLoaded) return@ScreenScaffold
        Box(Modifier.fillMaxSize()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 96.dp)
                    .padding(horizontal = 16.dp),
            ) {
                Header(current, onOpen = { sheet = it })
                current.exercises.forEach { entry ->
                    ExerciseCard(
                        entry = entry,
                        highlighted = entry.exercise.id in viewModel.highlighted,
                        onAddSet = { sheet = Sheet.AddSet(entry) },
                        onEditSet = { sheet = Sheet.EditSet(entry, it) },
                        onChangeKind = { sheet = Sheet.Kind(entry) },
                        onChart = { sheet = Sheet.Chart(entry) },
                        onRemove = { viewModel.removeExercise(entry.id) },
                    )
                }
                FilledTonalButton(onClick = { sheet = Sheet.AddExercise }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(20.dp))
                    Text(stringResource(R.string.gym_add_exercise), modifier = Modifier.padding(start = 8.dp))
                }
                if (templates.isNotEmpty()) {
                    OutlinedButton(onClick = { sheet = Sheet.ApplyTemplates }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ListAlt, null, Modifier.size(20.dp))
                        Text(stringResource(R.string.gym_templates_add), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                OutlinedTextField(
                    value = viewModel.note,
                    onValueChange = { viewModel.note = it },
                    label = { Text(stringResource(R.string.gym_note)) },
                    minLines = 2,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (current.imported) {
                    Text(
                        text = stringResource(R.string.gym_imported),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            if (current.active) {
                Button(
                    onClick = { askFinish() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = padding.calculateBottomPadding() + 8.dp)
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(stringResource(R.string.gym_finish))
                }
            }
        }
    }

    if (current != null) {
        Sheets(sheet, current, dictionary, templates, viewModel, onBack, open = { sheet = it }, close = { sheet = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(workout: Workout, onOpen: (Sheet) -> Unit) {
    val noTime = stringResource(R.string.gym_no_time)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = true,
                onClick = { onOpen(Sheet.Date) },
                label = { Text(formatDay(workout.date)) },
                leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)) },
            )
            // Пока идёт — конца и веса после ещё нет, их поставит «Закончить»
            val running = stringResource(R.string.gym_running_mark)
            FilterChip(
                selected = workout.start != null || workout.end != null,
                onClick = { onOpen(if (workout.active) Sheet.Start else Sheet.TimeRange) },
                label = {
                    Text(
                        stringResource(
                            R.string.gym_time_range,
                            workout.start?.let(::formatTime) ?: noTime,
                            if (workout.active) running else workout.end?.let(::formatTime) ?: noTime,
                        ),
                    )
                },
                leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = workout.weightBefore != null || workout.weightAfter != null,
                onClick = { onOpen(if (workout.active) Sheet.WeightBefore else Sheet.WeightRange) },
                label = {
                    Text(
                        stringResource(
                            R.string.gym_weight_range,
                            workout.weightBefore?.let(::formatKg) ?: noTime,
                            if (workout.active) running else workout.weightAfter?.let(::formatKg) ?: noTime,
                        ),
                    )
                },
                leadingIcon = { Icon(Icons.Outlined.MonitorWeight, null, Modifier.size(18.dp)) },
            )
        }
        workoutDuration(workout.start, workout.end)?.let {
            Text(
                text = durationLabel(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }
    }
}

/** Две строки на выбор: «Начало 14:30» / «Конец 15:50» и т. п. */
@Composable
private fun PickPartDialog(title: String, parts: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                parts.forEach { (label, onClick) ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onClick)
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseCard(
    entry: WorkoutExercise,
    highlighted: Boolean,
    onAddSet: () -> Unit,
    onEditSet: (WorkoutSet) -> Unit,
    onChangeKind: () -> Unit,
    onChart: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (highlighted) colors.errorContainer else colors.surfaceContainerLow)
    val nameColor by animateColorAsState(if (highlighted) colors.error else colors.onSurface)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.exercise.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = nameColor,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onChart) {
                Icon(Icons.AutoMirrored.Outlined.ShowChart, stringResource(R.string.gym_chart), tint = MaterialTheme.colorScheme.outline)
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, stringResource(R.string.gym_exercise_menu), tint = MaterialTheme.colorScheme.outline)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.gym_change_kind)) },
                        onClick = {
                            menu = false
                            onChangeKind()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.gym_remove_exercise)) },
                        onClick = {
                            menu = false
                            onRemove()
                        },
                    )
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 12.dp)) {
            entry.sets.forEach { set ->
                AssistChip(onClick = { onEditSet(set) }, label = { Text(set.format(entry.exercise.kind, stringResource(R.string.unit_kmh))) })
            }
            AssistChip(
                onClick = onAddSet,
                label = { Text(stringResource(R.string.gym_add_set)) },
                leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun Sheets(
    sheet: Sheet?,
    workout: Workout,
    dictionary: List<Exercise>,
    templates: List<WorkoutTemplate>,
    viewModel: WorkoutViewModel,
    onBack: () -> Unit,
    open: (Sheet) -> Unit,
    close: () -> Unit,
) {
    when (sheet) {
        null -> Unit
        Sheet.Date -> DueDatePickerDialog(
            initial = workout.date,
            onPick = { date ->
                viewModel.editHeader { it.copy(date = date) }
                close()
            },
            onClear = null,
            onDismiss = close,
        )
        Sheet.Start, Sheet.End -> {
            val isStart = sheet == Sheet.Start
            TimePickerDialog(
                initial = (if (isStart) workout.start else workout.end) ?: LocalTime.now().withSecond(0).withNano(0),
                onPick = { time ->
                    viewModel.editHeader { if (isStart) it.copy(start = time) else it.copy(end = time) }
                    close()
                },
                onClear = {
                    viewModel.editHeader { if (isStart) it.copy(start = null) else it.copy(end = null) }
                    close()
                },
                onDismiss = close,
            )
        }
        Sheet.WeightBefore, Sheet.WeightAfter -> {
            val before = sheet == Sheet.WeightBefore
            BodyWeightDialog(
                title = stringResource(if (before) R.string.gym_weight_before else R.string.gym_weight_after),
                initial = if (before) workout.weightBefore else workout.weightAfter ?: workout.weightBefore,
                onSave = { kg ->
                    viewModel.editHeader { if (before) it.copy(weightBefore = kg) else it.copy(weightAfter = kg) }
                    close()
                },
                onSkip = close,
            )
        }
        is Sheet.Finish -> BodyWeightDialog(
            title = stringResource(R.string.gym_weight_after),
            initial = sheet.suggested,
            onSave = { kg ->
                viewModel.finish(kg, sheet.end)
                close()
                onBack()
            },
            onSkip = {
                viewModel.finish(null, sheet.end)
                close()
                onBack()
            },
        )
        Sheet.TimeRange -> {
            val noTime = stringResource(R.string.gym_no_time)
            PickPartDialog(
                title = stringResource(R.string.gym_time),
                parts = listOf(
                    stringResource(R.string.gym_start_time) + "  " + (workout.start?.let(::formatTime) ?: noTime) to { open(Sheet.Start) },
                    stringResource(R.string.gym_end_time) + "  " + (workout.end?.let(::formatTime) ?: noTime) to { open(Sheet.End) },
                ),
                onDismiss = close,
            )
        }
        Sheet.WeightRange -> {
            val none = stringResource(R.string.gym_no_time)
            PickPartDialog(
                title = stringResource(R.string.gym_weight_empty),
                parts = listOf(
                    stringResource(R.string.gym_weight_before) + "  " + (workout.weightBefore?.let(::formatKg) ?: none) to { open(Sheet.WeightBefore) },
                    stringResource(R.string.gym_weight_after) + "  " + (workout.weightAfter?.let(::formatKg) ?: none) to { open(Sheet.WeightAfter) },
                ),
                onDismiss = close,
            )
        }
        Sheet.AddExercise -> AddExerciseDialog(
            dictionary = dictionary,
            onAdd = { name, kind ->
                viewModel.addExercise(name, kind)
                close()
            },
            onDismiss = close,
        )
        Sheet.ApplyTemplates -> TemplatePickerDialog(
            templates = templates,
            onConfirm = { ids ->
                viewModel.applyTemplates(ids)
                close()
            },
            onDismiss = close,
        )
        is Sheet.AddSet -> {
            val exerciseId = sheet.entry.exercise.id
            val history by remember(exerciseId) { viewModel.history(exerciseId) }.collectAsStateWithLifecycle(emptyList())
            val hint = ExerciseHistory.hint(ExerciseHistory.before(history, workout), sheet.entry.sets.size)
            val kmh = stringResource(R.string.unit_kmh)
            SetDialog(
                kind = sheet.entry.exercise.kind,
                initial = null,
                // В прошлый раз такого подхода не было — подсказываем предыдущий из этой тренировки
                hint = hint?.set ?: sheet.entry.sets.lastOrNull(),
                note = hint?.let {
                    {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.gym_last_time, formatDay(it.date), it.set.format(sheet.entry.exercise.kind, kmh)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = pluralStringResource(R.plurals.gym_result_held, it.heldFor, it.heldFor),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                onSave = { set ->
                    viewModel.addSet(sheet.entry.id, set)
                    close()
                },
                onDismiss = close,
            )
        }
        is Sheet.Chart -> {
            val exerciseId = sheet.entry.exercise.id
            val history by remember(exerciseId) { viewModel.history(exerciseId) }.collectAsStateWithLifecycle(emptyList())
            ExerciseChartDialog(
                name = sheet.entry.exercise.name,
                kind = sheet.entry.exercise.kind,
                points = ExerciseHistory.progress(history, sheet.entry.exercise.kind),
                onDismiss = close,
            )
        }
        is Sheet.EditSet -> SetDialog(
            kind = sheet.entry.exercise.kind,
            initial = sheet.set,
            onSave = { set ->
                viewModel.updateSet(set)
                close()
            },
            onDismiss = close,
            onDelete = {
                viewModel.deleteSet(sheet.set.id)
                close()
            },
        )
        is Sheet.Kind -> ExerciseKindDialog(
            current = sheet.entry.exercise.kind,
            onPick = { kind ->
                viewModel.setKind(sheet.entry.exercise, kind)
                close()
            },
            onDismiss = close,
        )
        Sheet.Delete -> ConfirmDialog(
            title = stringResource(R.string.gym_delete_title),
            text = stringResource(R.string.gym_delete_text),
            confirmLabel = stringResource(R.string.notes_delete),
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = close,
        )
    }
}
