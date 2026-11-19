package dev.chaik.remember.feature.notes

import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R
import dev.chaik.remember.core.model.NoteFolder
import dev.chaik.remember.core.model.Tag
import dev.chaik.remember.core.ui.keepHorizontalScroll

/**
 * Лента папок: «Все», «Без папки», папки, «+».
 * Тап — фильтр; долгое нажатие на папке — переименовать, защитить или удалить.
 * У защищённой папки — замок; переименовать, удалить и снять защиту с закрытой можно
 * только после PIN ([onUnlockFirst]). Защитить без заданного PIN нельзя ([onNeedPin]).
 */
@Composable
fun NoteFolderChips(
    folders: List<NoteFolder>,
    selected: FolderFilter,
    onSelect: (FolderFilter) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (NoteFolder, String) -> Unit,
    onDelete: (NoteFolder) -> Unit,
    canProtect: Boolean,
    isOpen: (NoteFolder) -> Boolean,
    onSetProtected: (NoteFolder, Boolean) -> Unit,
    onNeedPin: () -> Unit,
    onUnlockFirst: (NoteFolder, () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    onCreateProtected: (String, Boolean) -> Unit = { name, _ -> onCreate(name) },
) {
    // Защищённую и закрытую папку сначала открыть PIN-ом, потом действие
    fun guarded(folder: NoteFolder, action: () -> Unit) {
        if (folder.isProtected && !isOpen(folder)) onUnlockFirst(folder, action) else action()
    }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<NoteFolder?>(null) }
    var deleting by remember { mutableStateOf<NoteFolder?>(null) }

    LazyRow(
        modifier = modifier.keepHorizontalScroll(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterPill(stringResource(R.string.notes_all), selected == FolderFilter.All, onClick = { onSelect(FolderFilter.All) })
        }
        item {
            FilterPill(
                stringResource(R.string.notes_no_folder),
                selected == FolderFilter.NoFolder,
                onClick = { onSelect(FolderFilter.NoFolder) },
            )
        }
        items(folders, key = { it.id }) { folder ->
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                FilterPill(
                    label = folder.name,
                    selected = selected == FolderFilter.Folder(folder.id),
                    icon = when {
                        !folder.isProtected -> Icons.Outlined.Folder
                        isOpen(folder) -> Icons.Outlined.LockOpen
                        else -> Icons.Outlined.Lock
                    },
                    onClick = { onSelect(FolderFilter.Folder(folder.id)) },
                    onLongClick = { menuOpen = true },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.notes_rename_folder)) },
                        leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                        onClick = {
                            menuOpen = false
                            guarded(folder) { renaming = folder }
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(if (folder.isProtected) R.string.notes_unprotect_folder else R.string.notes_protect_folder))
                        },
                        leadingIcon = { Icon(if (folder.isProtected) Icons.Outlined.LockOpen else Icons.Outlined.Lock, null) },
                        onClick = {
                            menuOpen = false
                            when {
                                folder.isProtected -> guarded(folder) { onSetProtected(folder, false) }
                                canProtect -> onSetProtected(folder, true)
                                else -> onNeedPin()
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.notes_delete_folder)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            guarded(folder) { deleting = folder }
                        },
                    )
                }
            }
        }
        item {
            FilterPill(
                label = stringResource(R.string.notes_new_folder),
                selected = false,
                icon = Icons.Outlined.Add,
                onClick = { creating = true },
            )
        }
    }

    if (creating) {
        NameDialog(
            title = stringResource(R.string.notes_new_folder),
            initial = "",
            confirmLabel = stringResource(R.string.tags_create),
            onConfirm = onCreate,
            onDismiss = { creating = false },
            protectLabel = stringResource(R.string.notes_folder_protected_option),
            canProtect = canProtect,
            protectHint = stringResource(R.string.notes_folder_need_pin),
            onConfirmProtected = onCreateProtected,
        )
    }
    renaming?.let { folder ->
        NameDialog(
            title = stringResource(R.string.notes_rename_folder),
            initial = folder.name,
            confirmLabel = stringResource(R.string.action_ok),
            onConfirm = { onRename(folder, it) },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { folder ->
        ConfirmDialog(
            title = stringResource(R.string.notes_delete_folder_title, folder.name),
            text = stringResource(if (folder.isProtected) R.string.notes_delete_folder_protected_text else R.string.notes_delete_folder_text),
            confirmLabel = stringResource(R.string.notes_delete),
            onConfirm = { onDelete(folder) },
            onDismiss = { deleting = null },
        )
    }
}

/**
 * Лента тегов-фильтров «#тег». Можно выбрать несколько — тогда показываются заметки,
 * где есть все выбранные теги. «×» в начале — сбросить фильтр.
 */
@Composable
fun NoteTagFilter(
    tags: List<Tag>,
    selected: Set<Long>,
    onToggle: (Tag) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.keepHorizontalScroll(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected.isNotEmpty()) {
            item(key = "clear") {
                FilterPill(label = "", icon = Icons.Outlined.Close, selected = false, small = true, onClick = onClear)
            }
        }
        items(tags, key = { it.id }) { tag ->
            FilterPill(label = "#${tag.name}", selected = tag.id in selected, small = true, onClick = { onToggle(tag) })
        }
    }
}

/** Чип-фильтр с поддержкой долгого нажатия (у FilterChip его нет). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    small: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colors.secondaryContainer else colors.surface,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick?.let {
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    it()
                }
            },
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(if (small) 28.dp else 34.dp)
                .padding(horizontal = if (small) 10.dp else 12.dp),
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(16.dp))
                if (label.isNotEmpty()) Box(Modifier.size(6.dp))
            }
            if (label.isNotEmpty()) {
                Text(label, style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge)
            }
        }
    }
}
