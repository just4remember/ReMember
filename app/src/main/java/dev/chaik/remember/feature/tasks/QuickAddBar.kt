package dev.chaik.remember.feature.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R
import dev.chaik.remember.core.model.TaskPriority
import dev.chaik.remember.core.ui.components.DueDatePickerDialog
import dev.chaik.remember.core.ui.keepHorizontalScroll
import java.time.LocalDate

/**
 * Быстрое добавление над клавиатурой: название, «сегодня / завтра / дата», флажок приоритета.
 * Enter или стрелка — добавить; строка остаётся открытой для следующей задачи.
 * Клавиатуру закрыли (системное «назад») — закрывается и строка: [onDismiss].
 * Набранное хранит экран ([draft]): так оно переживает закрытие строки.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddBar(
    draft: TaskDraft,
    onDraftChange: (TaskDraft) -> Unit,
    onAdd: (title: String, dueDate: LocalDate?, priority: TaskPriority) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = draft.title
    // Своё поле с курсором: вернулись к черновику — курсор в конце, а не в начале
    var field by remember { mutableStateOf(TextFieldValue(title, TextRange(title.length))) }
    LaunchedEffect(title) {
        if (title != field.text) field = TextFieldValue(title, TextRange(title.length))
    }
    val dueDate = draft.dueDate
    val priority = draft.priority
    var pickingDate by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val today = LocalDate.now()
    // Строка живёт вместе с клавиатурой. Первое «назад» Android тратит на то, чтобы спрятать
    // клавиатуру, — без этого строка оставалась висеть внизу под островком.
    // Пока открыт календарь, клавиатуры нет — это не повод закрываться; после него вернём курсор.
    val keyboardVisible = WindowInsets.isImeVisible
    var keyboardShown by remember { mutableStateOf(false) }
    LaunchedEffect(keyboardVisible, pickingDate) {
        when {
            pickingDate -> keyboardShown = false
            keyboardVisible -> keyboardShown = true
            keyboardShown -> onDismiss()
            else -> focus.requestFocus()
        }
    }

    val submit = {
        if (title.isNotBlank()) {
            onAdd(title, dueDate, priority)
            onDraftChange(draft.copy(title = ""))
        }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = field,
                    onValueChange = {
                        field = it
                        onDraftChange(draft.copy(title = it.text))
                    },
                    placeholder = { Text(stringResource(R.string.tasks_quick_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus),
                )
                FilledIconButton(onClick = submit, enabled = title.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.tasks_add))
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .keepHorizontalScroll()
                    .horizontalScroll(rememberScrollState()),
            ) {
                DayChip(stringResource(R.string.tasks_today), dueDate == today) {
                    onDraftChange(draft.copy(dueDate = if (dueDate == today) null else today))
                }
                DayChip(stringResource(R.string.tasks_tomorrow), dueDate == today.plusDays(1)) {
                    onDraftChange(draft.copy(dueDate = if (dueDate == today.plusDays(1)) null else today.plusDays(1)))
                }
                val other = dueDate != null && dueDate != today && dueDate != today.plusDays(1)
                FilterChip(
                    selected = other,
                    onClick = { pickingDate = true },
                    label = { Text(if (other) dueLabel(dueDate!!, null, today) else stringResource(R.string.tasks_pick_date)) },
                    leadingIcon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)) },
                )
                // Флажок приоритета — по кругу: нет → низкий → средний → высокий
                FilterChip(
                    selected = priority != TaskPriority.NONE,
                    onClick = { onDraftChange(draft.copy(priority = TaskPriority.fromLevel((priority.level + 1) % 4))) },
                    label = { Icon(Icons.Outlined.Flag, stringResource(R.string.tasks_priority), tint = priority.color(), modifier = Modifier.size(18.dp)) },
                )
            }
        }
    }

    if (pickingDate) {
        DueDatePickerDialog(
            initial = dueDate,
            onPick = {
                onDraftChange(draft.copy(dueDate = it))
                pickingDate = false
            },
            onClear = {
                onDraftChange(draft.copy(dueDate = null))
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

@Composable
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
