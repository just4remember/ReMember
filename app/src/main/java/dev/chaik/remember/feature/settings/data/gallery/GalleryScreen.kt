package dev.chaik.remember.feature.settings.data.gallery

import dev.chaik.remember.core.model.formatVideoDuration
import dev.chaik.remember.core.editor.VideoBadge
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.editor.PhotoViewer
import dev.chaik.remember.core.photos.LibraryPhoto
import dev.chaik.remember.core.photos.PhotoUsage
import dev.chaik.remember.core.reminders.OpenTarget
import dev.chaik.remember.core.ui.components.ScreenScaffold
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.serialization.Serializable

@Serializable
data object GalleryRoute : NavKey

private val ThumbShape = RoundedCornerShape(8.dp)

/**
 * Все фото приложения сеткой по месяцам добавления, новые сверху. Тап — фото на весь экран
 * со сведениями: когда добавлено, размер, где стоит (тап — перейти туда), копия в галерею
 * телефона и удаление.
 */
@Composable
fun GalleryScreen(onBack: () -> Unit, onOpenNote: (Long) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = context.appContainer
    val viewModel = viewModel { GalleryViewModel(container) }
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    var openedAt by remember { mutableStateOf<Int?>(null) }
    var deleting by remember { mutableStateOf<LibraryPhoto?>(null) }

    ScreenScaffold(title = stringResource(R.string.data_gallery), modifier = modifier, onBack = onBack) { padding ->
        val list = photos
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.gallery_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> PhotoGrid(
                photos = list,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                onOpen = { openedAt = it },
            )
        }
    }

    val list = photos.orEmpty()
    // Удалили последнее фото — смотреть больше нечего
    LaunchedEffect(list.isEmpty()) { if (list.isEmpty()) openedAt = null }
    val opened = openedAt
    if (opened != null && list.isNotEmpty()) {
        val savedText = stringResource(R.string.gallery_saved)
        val savedVideoText = stringResource(R.string.gallery_saved_video)
        val failedText = stringResource(R.string.gallery_save_failed)
        val info: @Composable BoxScope.(Int, Modifier, Boolean) -> Unit = { page, bottomBlock, sheet ->
            val photo = list[page]
            PhotoInfo(
                photo = photo,
                modifier = bottomBlock,
                sheet = sheet,
                onOpenUsage = { usage ->
                    openedAt = null
                    when (usage) {
                        is PhotoUsage.Day -> container.openRequests.value = OpenTarget.DayAt(usage.date)
                        is PhotoUsage.Note -> onOpenNote(usage.id)
                    }
                },
                onSave = {
                    viewModel.exportToGallery(photo.id) { ok ->
                        val saved = if (photo.durationMs != null) savedVideoText else savedText
                        Toast.makeText(context, if (ok) saved else failedText, Toast.LENGTH_SHORT).show()
                    }
                },
                onDelete = { deleting = photo },
            )
        }
        PhotoViewer(
            files = list.map { container.photoStorage.file(it.id) },
            initialIndex = opened,
            onDismiss = { openedAt = null },
            overlay = { page, bottomBlock -> info(page, bottomBlock, false) },
            // У видео сведения не висят поверх ролика, а выезжают по свайпу вверх
            videoSheet = { page -> info(page, Modifier, true) },
        )
    }

    deleting?.let { photo ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(if (photo.durationMs != null) R.string.gallery_delete_title_video else R.string.gallery_delete_title)) },
            text = {
                val text = when {
                    photo.durationMs == null -> if (photo.usages.isEmpty()) R.string.gallery_delete_text else R.string.gallery_delete_text_used
                    else -> if (photo.usages.isEmpty()) R.string.gallery_delete_text_video else R.string.gallery_delete_text_used_video
                }
                Text(stringResource(text))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        viewModel.delete(photo.id)
                    },
                ) { Text(stringResource(R.string.gallery_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Сетка миниатюр с заголовком месяца перед каждой группой. */
@Composable
private fun PhotoGrid(photos: List<LibraryPhoto>, contentPadding: PaddingValues, onOpen: (index: Int) -> Unit) {
    val storage = LocalContext.current.appContainer.photoStorage
    val zone = remember { ZoneId.systemDefault() }
    val monthFormat = remember { DateTimeFormatter.ofPattern("LLLL yyyy") }
    val months = remember(photos) {
        photos.withIndex().groupBy { YearMonth.from(it.value.createdAt.atZone(zone)) }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        months.forEach { (month, items) ->
            item(key = month.toString(), span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)) {
                    Text(
                        text = month.format(monthFormat).replaceFirstChar { it.titlecase() },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = items.size.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items, key = { it.value.id }) { (index, photo) ->
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(ThumbShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onOpen(index) },
                ) {
                    AsyncImage(
                        model = storage.thumbnailFile(photo.id),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                    if (photo.durationMs != null) VideoBadge(photo.durationMs, compact = true)
                }
            }
        }
    }
}

/** Сведения и кнопки внизу просмотра: на тёмной подложке, чтобы читалось поверх любого фото. */
@Composable
private fun BoxScope.PhotoInfo(
    photo: LibraryPhoto,
    onOpenUsage: (PhotoUsage) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** Шторка у видео: сплошная подложка со скруглённым верхом и «ручкой». */
    sheet: Boolean = false,
) {
    val context = LocalContext.current
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT) }
    val dayFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG) }
    val added = photo.createdAt.atZone(ZoneId.systemDefault()).format(dateFormat)
    val muted = Color.White.copy(alpha = 0.7f)

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .then(
                if (sheet) {
                    Modifier.background(Color(0xF2202124), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                } else {
                    Modifier.background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                },
            )
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = if (sheet) 10.dp else 40.dp, bottom = 8.dp),
    ) {
        if (sheet) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(50)),
            )
        }
        Text(stringResource(R.string.gallery_added, added), color = Color.White, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = listOfNotNull(
                "${photo.width} × ${photo.height}",
                photo.durationMs?.let(::formatVideoDuration),
                Formatter.formatShortFileSize(context, photo.sizeBytes),
            ).joinToString(" · "),
            color = muted,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (photo.usages.isEmpty()) {
            Text(stringResource(if (photo.durationMs != null) R.string.gallery_unused_video else R.string.gallery_unused), color = muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        } else {
            photo.usages.forEach { usage ->
                val (icon, label) = when (usage) {
                    is PhotoUsage.Day -> Icons.Outlined.Today to stringResource(R.string.gallery_day, usage.date.format(dayFormat))
                    is PhotoUsage.Note -> Icons.AutoMirrored.Outlined.Notes to stringResource(
                        R.string.gallery_note,
                        usage.title.ifBlank { stringResource(R.string.gallery_untitled) },
                    )
                }
                UsageRow(icon, label) { onOpenUsage(usage) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            ActionButton(Icons.Outlined.Download, stringResource(R.string.gallery_save), Color.White, onSave)
            ActionButton(Icons.Outlined.Delete, stringResource(R.string.gallery_delete), Color(0xFFFF8A80), onDelete)
        }
    }
}

/** «Где стоит» — тап открывает эту запись или заметку. */
@Composable
private fun UsageRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Text(label, color = color, modifier = Modifier.padding(start = 6.dp))
    }
}
