package dev.chaik.remember.feature.notes

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Checkbox
import dev.chaik.remember.core.ui.releaseFocus
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R
import dev.chaik.remember.core.model.NoteFolder

/**
 * Ввод названия (новая папка, переименование). [protectLabel] — показать под полем галочку
 * «защищённая» (для новой папки); [canProtect] = false — галочка выключена, а [protectHint]
 * объясняет почему. Выбор галочки приходит вторым аргументом [onConfirm].
 */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    protectLabel: String? = null,
    canProtect: Boolean = true,
    protectHint: String? = null,
    onConfirmProtected: ((String, Boolean) -> Unit)? = null,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    var protect by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val confirm = {
        if (value.text.isNotBlank()) {
            if (onConfirmProtected != null) onConfirmProtected(value.text, protect) else onConfirm(value.text)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier
                        .releaseFocus()
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                if (protectLabel != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .clickable(enabled = canProtect) { protect = !protect },
                    ) {
                        Checkbox(checked = protect, onCheckedChange = { protect = it }, enabled = canProtect)
                        Text(protectLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!canProtect && protectHint != null) {
                        Text(
                            protectHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = confirm, enabled = value.text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Подтверждение необратимого действия (удаление). */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Выбор папки для заметки; null — «Без папки». */
@Composable
fun FolderPickerDialog(
    folders: List<NoteFolder>,
    current: Long?,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notes_move_to_folder)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FolderOption(stringResource(R.string.notes_no_folder), current == null) { onPick(null) }
                folders.forEach { folder ->
                    FolderOption(folder.name, current == folder.id, locked = folder.isProtected) { onPick(folder.id) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun FolderOption(name: String, selected: Boolean, locked: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(name, style = MaterialTheme.typography.bodyLarge)
        if (locked) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = stringResource(R.string.notes_protected_folder),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(16.dp),
            )
        }
    }
}
