package dev.chaik.remember.feature.notes

import androidx.compose.foundation.border
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.ui.components.shimmer
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Карточка заметки: первое фото, заголовок, начало текста, теги, папка, дата.
 * Тап — открыть, долгое нажатие — меню: закрепить, в папку, удалить. Если, не отпуская,
 * повести пальцем — меню закрывается и карточку можно перетащить: [onDragStart], [onDrag], [onDragEnd].
 * [wiggle] — пока перетаскивают другую карточку, эта слегка покачивается; [target] — сюда встанет перетаскиваемая.
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
    /** Начали перетаскивать: где палец на карточке. */
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    wiggle: Boolean = false,
    target: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentDragEnd by rememberUpdatedState(onDragEnd)
    val angle = if (wiggle) {
        val transition = rememberInfiniteTransition(label = "wiggle")
        // Соседние карточки качаются не в такт — так живее
        val phase = remember { (note.id % 2).toInt() }
        transition.animateFloat(
            initialValue = if (phase == 0) -WIGGLE_DEGREES else WIGGLE_DEGREES,
            targetValue = if (phase == 0) WIGGLE_DEGREES else -WIGGLE_DEGREES,
            animationSpec = infiniteRepeatable(tween(WIGGLE_MS), RepeatMode.Reverse),
            label = "angle",
        ).value
    } else {
        0f
    }
    // Превью готово заранее (в фоне, при чтении из базы). Без заголовка текст остаётся обычным текстом,
    // а жирная подпись «Без названия» — только если показать совсем нечего
    val hasTitle = note.title.isNotBlank()
    val textOnly = !hasTitle && note.preview.isNotBlank()

    Box(modifier.graphicsLayer { rotationZ = angle }) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .then(if (target) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)) else Modifier)
                .clickable(onClick = onOpen)
                .pointerInput(note.id) {
                    var travel = Offset.Zero
                    var touch = Offset.Zero
                    var dragging = false
                    detectDragGesturesAfterLongPress(
                        onDragStart = { at ->
                            touch = at
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpen = true
                            travel = Offset.Zero
                            dragging = false
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            travel += delta
                            // Повёл пальцем — значит, переставляет, а не выбирает в меню
                            if (!dragging && travel.getDistance() > viewConfiguration.touchSlop) {
                                dragging = true
                                menuOpen = false
                                currentDragStart(touch)
                                currentDrag(travel)
                            } else if (dragging) {
                                currentDrag(delta)
                            }
                        },
                        onDragEnd = { if (dragging) currentDragEnd() },
                        onDragCancel = { if (dragging) currentDragEnd() },
                    )
                },
        ) {
            // Обложку можно выключить в «Страницах»
            note.coverPhotoId?.takeIf { LocalAppSettings.current.notesShowPhotos }?.let { photoId ->
                Cover(thumbnail(photoId))
            }
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = when {
                            hasTitle -> note.title
                            textOnly -> note.preview
                            else -> stringResource(R.string.notes_untitled)
                        },
                        style = if (textOnly) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleSmall,
                        fontWeight = if (textOnly) null else FontWeight.Bold,
                        color = when {
                            hasTitle -> MaterialTheme.colorScheme.onSurface
                            textOnly -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.outline
                        },
                        maxLines = if (textOnly) BODY_LINES else 2,
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
                if (hasTitle && note.preview.isNotBlank()) {
                    Text(
                        text = note.preview,
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

/** Покачивание карточек при перестановке: угол и полупериод. */
private const val WIGGLE_DEGREES = 0.6f
private const val WIGGLE_MS = 260

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
