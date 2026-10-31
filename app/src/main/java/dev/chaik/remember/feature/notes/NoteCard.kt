package dev.chaik.remember.feature.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.chaik.remember.R
import dev.chaik.remember.core.model.NoteSummary
import dev.chaik.remember.core.ui.components.shimmer
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Карточка заметки: первое фото, заголовок (или первая строка), начало текста, теги, папка, дата.
 * Тап — открыть, долгое нажатие — меню: закрепить, в папку, удалить.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteSummary,
    /** Название папки — показывается в режиме «Все»; null — не показывать. */
    folderName: String?,
    thumbnail: (String) -> File,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    // Превью готово заранее (в фоне, при чтении из базы); без заголовка заголовком служит первая строка
    val title = note.title.ifBlank { note.preview.substringBefore('\n') }
    val body = if (note.title.isBlank()) note.preview.substringAfter('\n', "") else note.preview

    Box(modifier) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                ),
        ) {
            note.coverPhotoId?.let { photoId ->
                Cover(thumbnail(photoId))
            }
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = title.ifBlank { stringResource(R.string.notes_untitled) },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (title.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (note.isProtected) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = stringResource(R.string.notes_protected),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(16.dp),
                        )
                    }
                    if (note.pinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(16.dp),
                        )
                    }
                }
                if (body.isNotBlank()) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = BODY_LINES,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (note.tags.isNotEmpty()) {
                    Text(
                        text = note.tags.joinToString(" ") { "#${it.name}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (folderName != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = folderName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                Text(
                    text = formatUpdated(note.updatedAt.atZone(ZoneId.systemDefault()).toLocalDateTime()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (note.pinned) R.string.notes_unpin else R.string.notes_pin)) },
                leadingIcon = { Icon(if (note.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, null) },
                onClick = {
                    menuOpen = false
                    onTogglePin()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.notes_move_to_folder)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) },
                onClick = {
                    menuOpen = false
                    onMove()
                },
            )
            // Защищённую заметку удалить можно только изнутри, после PIN или отпечатка
            if (!note.isProtected) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.notes_delete), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/** Сколько строк текста на карточке. */
private const val BODY_LINES = 6

/** Обложка из первого фото; пока миниатюра грузится — на её месте переливающийся блик. */
@Composable
private fun Cover(file: File) {
    var loading by remember(file) { mutableStateOf(true) }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f),
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onSuccess = { loading = false },
            onError = { loading = false },
            modifier = Modifier.matchParentSize(),
        )
        if (loading) Box(Modifier.matchParentSize().shimmer())
    }
}

/** Когда последний раз правили: «сегодня, 14:05», «вчера, 9:30», «23 окт., 14:05», «23 окт. 2025 г., 14:05». */
@Composable
private fun formatUpdated(time: LocalDateTime): String {
    val date = time.toLocalDate()
    val today = LocalDate.now()
    val day = when (date) {
        today -> stringResource(R.string.date_today_lower)
        today.minusDays(1) -> stringResource(R.string.date_yesterday_lower)
        else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "d MMM" else "d MMM yyyy"))
    }
    return stringResource(R.string.date_time_pair, day, time.format(DateTimeFormatter.ofPattern("H:mm")))
}
