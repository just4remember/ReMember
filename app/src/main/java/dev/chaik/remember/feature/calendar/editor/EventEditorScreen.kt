package dev.chaik.remember.feature.calendar.editor

import dev.chaik.remember.core.ui.releaseFocus
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.model.EventColor
import dev.chaik.remember.core.model.Repeat
import dev.chaik.remember.core.ui.components.DueDatePickerDialog
import dev.chaik.remember.core.ui.components.EditorSection
import dev.chaik.remember.core.ui.components.PlainTextField
import dev.chaik.remember.core.ui.components.RepeatPicker
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.core.ui.components.TimePickerDialog
import dev.chaik.remember.feature.calendar.MINUTES_IN_DAY
import dev.chaik.remember.feature.calendar.MINUTES_IN_HOUR
import dev.chaik.remember.feature.calendar.RemindPresets
import dev.chaik.remember.feature.calendar.color
import dev.chaik.remember.feature.calendar.formatDay
import dev.chaik.remember.feature.calendar.remindLabel
import dev.chaik.remember.feature.calendar.yearsLabel
import dev.chaik.remember.feature.notes.ConfirmDialog
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** @param eventId 0 — новое событие на день [date] (номер дня от 1970-01-01). */
@Serializable
data class EventEditorRoute(val eventId: Long = 0, val date: Long = 0) : NavKey

@Composable
fun EventEditorScreen(
    route: EventEditorRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalContext.current.appContainer
    val viewModel = viewModel { EventEditorViewModel(container, route.eventId, LocalDate.ofEpochDay(route.date)) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }

    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = stringResource(if (viewModel.isNew) R.string.event_new else R.string.event_title),
        modifier = modifier,
        onBack = onBack,
        actions = {
            IconButton(onClick = { if (viewModel.isNew && viewModel.title.isBlank()) onBack() else confirmDelete = true }) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.notes_delete), tint = MaterialTheme.colorScheme.error)
            }
        },
    ) { padding ->
        if (!viewModel.isLoaded) return@ScreenScaffold
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)
                .padding(horizontal = 16.dp),
        ) {
            TitleRow(viewModel)
            BirthdaySection(viewModel)
            WhenSection(viewModel, onPickDate = { pickingDate = true }, onPickTime = { pickingTime = true })
            ColorSection(viewModel)
            EditorSection(stringResource(R.string.repeat_title)) {
                if (viewModel.isBirthday) {
                    // День рождения повторяется каждый год — выбирать нечего
                    OutlinedTextField(
                        value = stringResource(R.string.repeat_yearly),
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    RepeatPicker(
                        repeat = viewModel.repeat,
                        onChange = { viewModel.repeat = it },
                        defaultDay = { viewModel.date.dayOfWeek },
                    )
                }
            }
            RemindSection(viewModel)
            OutlinedTextField(
                value = viewModel.description,
                onValueChange = { viewModel.description = it },
                label = { Text(stringResource(R.string.event_description)) },
                minLines = 3,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (pickingDate) {
        DueDatePickerDialog(
            initial = viewModel.date,
            onPick = {
                viewModel.date = it
                pickingDate = false
            },
            onClear = null,
            onDismiss = { pickingDate = false },
        )
    }
    if (pickingTime) {
        TimePickerDialog(
            initial = viewModel.time,
            onPick = {
                viewModel.time = it
                pickingTime = false
            },
            onClear = null,
            onDismiss = { pickingTime = false },
        )
    }
    if (confirmDelete) {
        val repeating = viewModel.isBirthday || viewModel.repeat != Repeat.None
        ConfirmDialog(
            title = stringResource(R.string.event_delete_title),
            text = stringResource(if (repeating) R.string.event_delete_repeating_text else R.string.event_delete_text),
            confirmLabel = stringResource(R.string.notes_delete),
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun TitleRow(viewModel: EventEditorViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Box(
            Modifier
                .padding(end = 12.dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(viewModel.color.color()),
        )
        PlainTextField(
            value = viewModel.title,
            onChange = { viewModel.title = it },
            hint = stringResource(if (viewModel.isBirthday) R.string.event_person_hint else R.string.event_title_hint),
            style = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            ),
            capitalization = if (viewModel.isBirthday) KeyboardCapitalization.Words else KeyboardCapitalization.Sentences,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Переключатель «День рождения», а при включённом — год рождения и сколько исполнится. */
@Composable
private fun BirthdaySection(viewModel: EventEditorViewModel) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.switchBirthday(!viewModel.isBirthday) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.Outlined.Cake,
                contentDescription = null,
                tint = if (viewModel.isBirthday) viewModel.color.color() else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.event_birthday),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
            Switch(checked = viewModel.isBirthday, onCheckedChange = viewModel::switchBirthday)
        }
        if (viewModel.isBirthday) {
            val year = viewModel.validBirthYear()
            val today = LocalDate.now()
            // Ближайший день рождения: в этом году или, если уже прошёл, в следующем
            val next = viewModel.date.withYear(today.year).let { if (it.isBefore(today)) it.plusYears(1) else it }
            OutlinedTextField(
                value = viewModel.birthYear,
                onValueChange = { text -> if (text.length <= 4 && text.all(Char::isDigit)) viewModel.birthYear = text },
                label = { Text(stringResource(R.string.event_birth_year)) },
                supportingText = year?.let {
                    { Text(stringResource(R.string.event_turns_next, formatDay(next), yearsLabel(next.year - it))) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun WhenSection(viewModel: EventEditorViewModel, onPickDate: () -> Unit, onPickTime: () -> Unit) {
    EditorSection(stringResource(R.string.event_when)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = true,
                onClick = onPickDate,
                // У дня рождения год даты ничего не значит — показываем только день и месяц
                label = { Text(formatDay(viewModel.date, withYear = !viewModel.isBirthday)) },
                leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = true,
                onClick = onPickTime,
                label = { Text(viewModel.time.toString()) },
                leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSection(viewModel: EventEditorViewModel) {
    EditorSection(stringResource(R.string.event_color)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EventColor.entries.forEach { option ->
                val selected = option == viewModel.color
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(option.color())
                        .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        .clickable { viewModel.color = option },
                ) {
                    if (selected) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemindSection(viewModel: EventEditorViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }

    EditorSection(stringResource(R.string.event_remind)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = remindLabel(viewModel.remindMinutes),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                RemindPresets.forEach { minutes ->
                    DropdownMenuItem(
                        text = { Text(remindLabel(minutes)) },
                        onClick = {
                            viewModel.remindMinutes = minutes
                            expanded = false
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.event_remind_custom)) },
                    onClick = {
                        expanded = false
                        custom = true
                    },
                )
            }
        }
    }

    if (custom) {
        CustomRemindDialog(
            initial = viewModel.remindMinutes,
            onPick = {
                viewModel.remindMinutes = it
                custom = false
            },
            onDismiss = { custom = false },
        )
    }
}

/** «Предупредить за N минут / часов / дней». */
@Composable
private fun CustomRemindDialog(initial: Int?, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val units = listOf(
        1 to R.string.event_unit_minutes,
        MINUTES_IN_HOUR to R.string.event_unit_hours,
        MINUTES_IN_DAY to R.string.event_unit_days,
    )
    // Начинаем с текущего значения в самой крупной подходящей единице
    val start = initial?.takeIf { it > 0 }
    val startUnit = units.map { it.first }.lastOrNull { start != null && start % it == 0 } ?: MINUTES_IN_HOUR
    var amount by remember { mutableStateOf(start?.div(startUnit)?.toString().orEmpty()) }
    var unitIndex by remember { mutableIntStateOf(units.indexOfFirst { it.first == startUnit }) }
    val value = amount.toIntOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.event_remind_custom_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.releaseFocus()) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { text -> if (text.length <= 3 && text.all(Char::isDigit)) amount = text },
                    label = { Text(stringResource(R.string.event_remind_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    units.forEachIndexed { i, (_, label) ->
                        SegmentedButton(
                            selected = unitIndex == i,
                            onClick = { unitIndex = i },
                            shape = SegmentedButtonDefaults.itemShape(i, units.size),
                            label = { Text(stringResource(label), maxLines = 1) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = value != null, onClick = { value?.let { onPick(it * units[unitIndex].first) } }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
