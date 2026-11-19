package dev.chaik.remember.core.editor

import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import dev.chaik.remember.R
import dev.chaik.remember.core.settings.BackSwipe
import dev.chaik.remember.core.settings.LocalAppSettings
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** «7 фото, 2 видео» — что уже готово в блоке. */
@Composable
private fun galleryCounter(ids: List<String>, state: EditorState): String {
    val videos = ids.count { state.durationOf(it) != null }
    val photos = ids.size - videos
    val parts = buildList {
        if (photos > 0) add(pluralStringResource(R.plurals.data_photos, photos, photos))
        if (videos > 0) add(pluralStringResource(R.plurals.data_videos, videos, videos))
    }
    return parts.joinToString(", ")
}

/**
 * Заглушка фото или видео, которое ещё добавляется: проценты и полоса загрузки.
 * Пока до него не дошла очередь — «В очереди».
 */
@Composable
internal fun PendingCell(
    item: EditorState.PendingMedia,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val progress = item.progress
    val shown by animateFloatAsState(progress ?: 0f, label = "importProgress")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 10.dp else 32.dp),
        ) {
            Text(
                text = if (progress == null) stringResource(R.string.editor_import_waiting) else "${(progress * 100).roundToInt()}%",
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { shown },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
    }
}

/**
 * Альбом в тексте: первый кадр под лёгким размытием, сколько внутри и кнопка «Смотреть»
 * по центру. Показать всё прямо в тексте — выбрать раскладку в панели действий.
 * Пока что-то добавляется — внизу сколько осталось и полоса загрузки текущего.
 * Остальные тапы по карточке ([modifier]) — как у фото: выделение и панель действий.
 */
@Composable
internal fun GalleryCard(
    block: EditorState.PhotoBlock,
    state: EditorState,
    photoFile: (String) -> File,
    shape: RoundedCornerShape,
    onView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        block.photoIds.firstOrNull()?.let { first ->
            AsyncImage(
                model = photoFile(first),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .blur(4.dp),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.3f)),
        )
        // Подпись на тёмной подложке — читается поверх любого кадра
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(Icons.Outlined.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(stringResource(R.string.editor_gallery), color = Color.White, style = MaterialTheme.typography.titleSmall)
                val counter = galleryCounter(block.photoIds, state)
                if (counter.isNotEmpty()) {
                    Text(counter, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        FilledTonalButton(onClick = onView, modifier = Modifier.align(Alignment.Center)) {
            Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.editor_gallery_view))
        }
        if (block.pending.isNotEmpty()) {
            val current = block.pending.firstOrNull { it.progress != null }?.progress ?: 0f
            val shown by animateFloatAsState(current, label = "galleryImport")
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    pluralStringResource(R.plurals.editor_import_left, block.pending.size, block.pending.size) +
                        " · ${(current * 100).roundToInt()}%",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
                LinearProgressIndicator(
                    progress = { shown },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
        }
    }
}

/** Плитка окна галереи: готовое фото (с номером) или заглушка загружаемого. */
private sealed interface Tile {
    data class Ready(val index: Int, val id: String) : Tile
    data class Waiting(val item: EditorState.PendingMedia) : Tile
}

/**
 * Окно галереи: все фото и видео блока плиткой. Тап — на весь экран с листанием.
 * Удержание — выбор (дальше тапами можно добавить ещё), вверху появляется корзина:
 * убрать выбранные из галереи (с подтверждением; отменяется как обычная правка).
 * «Добавить» — выбрать ещё фото и видео в конец этой галереи. Закрыть — «назад»
 * или свайпом, как страницы приложения (направление — из настроек).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MediaGalleryDialog(
    block: EditorState.PhotoBlock,
    state: EditorState,
    photoFile: (String) -> File,
    onOpen: (index: Int) -> Unit,
    onAdd: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    // На весь экран, под системными панелями — фон окна заходит и под кнопки «назад/домой»
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        SystemBarIcons(dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f)
        var selected by remember { mutableStateOf(emptySet<Int>()) }
        var confirming by remember { mutableStateOf(false) }
        val selecting = selected.isNotEmpty()
        val haptics = LocalHapticFeedback.current
        // Фото убрали (в том числе отменой) — выбор по старым номерам больше не верен
        val count = block.photoIds.size
        selected = selected.filterTo(mutableSetOf()) { it < count }

        BackHandler(enabled = selecting) { selected = emptySet() }

        fun toggle(index: Int) {
            selected = if (index in selected) selected - index else selected + index
        }

        Surface(
            Modifier
                .fillMaxSize()
                .swipeToClose(onDismiss),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                ) {
                    if (selecting) {
                        IconButton(onClick = { selected = emptySet() }) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.action_cancel))
                        }
                        Text(
                            stringResource(R.string.editor_gallery_selected, selected.size),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 4.dp),
                        )
                        IconButton(onClick = { confirming = true }) {
                            Icon(
                                Icons.Outlined.Delete,
                                stringResource(R.string.editor_gallery_delete_selected),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.editor_close))
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 4.dp),
                        ) {
                            Text(stringResource(R.string.editor_gallery), style = MaterialTheme.typography.titleLarge)
                            val counter = galleryCounter(block.photoIds, state)
                            if (counter.isNotEmpty()) {
                                Text(counter, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (onAdd != null) {
                            IconButton(onClick = onAdd) {
                                Icon(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.editor_gallery_add))
                            }
                        }
                    }
                }
                val tiles = block.photoIds.mapIndexed { i, id -> Tile.Ready(i, id) } + block.pending.map { Tile.Waiting(it) }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Одно и то же фото может стоять дважды — ключ с номером
                    items(tiles, key = {
                        when (it) {
                            is Tile.Ready -> "${it.index}:${it.id}"
                            is Tile.Waiting -> "pending:${it.item.id}"
                        }
                    }) { tile ->
                        val tileModifier = Modifier.aspectRatio(1f)
                        when (tile) {
                            is Tile.Waiting -> PendingCell(tile.item, TileShape, tileModifier, compact = true)
                            is Tile.Ready -> {
                                val isSelected = tile.index in selected
                                val scale by animateFloatAsState(if (isSelected) 0.88f else 1f, label = "tileScale")
                                Box(
                                    tileModifier
                                        .clip(TileShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .combinedClickable(
                                            onClick = { if (selecting) toggle(tile.index) else onOpen(tile.index) },
                                            onLongClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                toggle(tile.index)
                                            },
                                        ),
                                ) {
                                    Box(
                                        Modifier
                                            .matchParentSize()
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                            }
                                            .clip(TileShape),
                                    ) {
                                        AsyncImage(
                                            model = photoFile(tile.id),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.matchParentSize(),
                                        )
                                        state.durationOf(tile.id)?.let { VideoBadge(it, compact = true) }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(24.dp)
                                                .background(MaterialTheme.colorScheme.surface, CircleShape),
                                        )
                                    } else if (selecting) {
                                        Box(
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(22.dp)
                                                .border(2.dp, Color.White, CircleShape),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (confirming) {
            AlertDialog(
                onDismissRequest = { confirming = false },
                title = { Text(pluralStringResource(R.plurals.editor_gallery_delete_title, selected.size, selected.size)) },
                text = { Text(stringResource(R.string.editor_gallery_delete_text)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirming = false
                            state.removePhotosAt(block.key, selected)
                            selected = emptySet()
                        },
                    ) { Text(stringResource(R.string.gallery_delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

private val TileShape = RoundedCornerShape(8.dp)

/** Значки строки состояния и системных кнопок окна — тёмные на светлом фоне и наоборот. */
@Composable
private fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

/**
 * Закрыть окно свайпом вбок — как страницы приложения: в ту сторону, что выбрана
 * в настройках. Окно едет за пальцем; отпустили дальше четверти ширины — закрывается,
 * иначе возвращается на место.
 */
@Composable
private fun Modifier.swipeToClose(onClose: () -> Unit): Modifier {
    val mode = LocalAppSettings.current.backSwipe
    val close by rememberUpdatedState(onClose)
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    return graphicsLayer { translationX = offset.value }
        .pointerInput(mode) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (abs(offset.value) > size.width * 0.25f) close() else scope.launch { offset.animateTo(0f) }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f) } },
                onHorizontalDrag = { change, dx ->
                    val next = offset.value + dx
                    val allowed = when (mode) {
                        BackSwipe.RIGHT_TO_LEFT -> next.coerceAtMost(0f)
                        BackSwipe.LEFT_TO_RIGHT -> next.coerceAtLeast(0f)
                        BackSwipe.BOTH -> next
                    }
                    change.consume()
                    scope.launch { offset.snapTo(allowed) }
                },
            )
        }
}
