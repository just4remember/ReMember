package dev.chaik.remember.feature.notes

import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.scrollBy
import dev.chaik.remember.core.settings.NotesOrder
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.model.NoteSummary
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.core.ui.components.shimmer
import dev.chaik.remember.core.ui.primaryCornerModifier
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
data object NotesRoute : NavKey

/**
 * Заметки: папки и теги-фильтры сверху, карточки в две колонки
 * (закреплённые — отдельным блоком), «+» — новая заметка в открытой папке.
 */
@Composable
fun NotesScreen(
    onBack: (() -> Unit)?,
    onOpen: (noteId: Long) -> Unit,
    onCreate: (folderId: Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalContext.current.appContainer
    val viewModel = viewModel { NotesViewModel(container.noteRepository, container.settings.settings) }
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val tags by viewModel.usedTags.collectAsStateWithLifecycle()
    val notes by viewModel.visibleNotes.collectAsStateWithLifecycle()

    var moving by remember { mutableStateOf<NoteSummary?>(null) }
    var deleting by remember { mutableStateOf<NoteSummary?>(null) }

    ScreenScaffold(title = stringResource(R.string.tab_notes), modifier = modifier, onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(top = padding.calculateTopPadding())) {
                // Блок фильтров: с отступами сверху и снизу и тонкой чертой — отделён от прокручиваемых карточек
                Column(Modifier.padding(top = 8.dp, bottom = 10.dp)) {
                    NoteFolderChips(
                        folders = folders,
                        selected = viewModel.folderFilter,
                        onSelect = viewModel::selectFolder,
                        onCreate = viewModel::createFolder,
                        onRename = viewModel::renameFolder,
                        onDelete = viewModel::deleteFolder,
                    )
                    if (tags.isNotEmpty()) {
                        NoteTagFilter(
                            tags = tags,
                            selected = viewModel.tagFilter,
                            onToggle = viewModel::toggleTagFilter,
                            onClear = viewModel::clearTagFilter,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                val list = notes
                when {
                    list == null -> NotesSkeleton(bottomPadding = padding.calculateBottomPadding())
                    list.isEmpty() -> EmptyNotes(Modifier.fillMaxWidth().padding(32.dp))
                    else -> NotesGrid(
                        notes = list,
                        // В «Все» на карточке видно, в какой папке заметка
                        folderNames = if (viewModel.folderFilter == FolderFilter.All) {
                            folders.associate { it.id to it.name }
                        } else {
                            emptyMap()
                        },
                        thumbnail = container.photoStorage::thumbnailFile,
                        bottomPadding = padding.calculateBottomPadding(),
                        onOpen = onOpen,
                        onTogglePin = viewModel::togglePinned,
                        onMove = { moving = it },
                        onReorder = { ids ->
                            viewModel.reorder(ids)
                            container.settings.update { it.copy(notesOrder = NotesOrder.MANUAL) }
                        },
                        onDelete = { deleting = it },
                    )
                }
            }

            FloatingActionButton(
                onClick = { onCreate(viewModel.currentFolderId) },
                modifier = primaryCornerModifier(bottom = padding.calculateBottomPadding()),
            ) {
                Icon(Icons.Outlined.Add, stringResource(R.string.notes_new))
            }
        }
    }

    moving?.let { note ->
        FolderPickerDialog(
            folders = folders,
            current = note.folderId,
            onPick = {
                viewModel.moveToFolder(note, it)
                moving = null
            },
            onDismiss = { moving = null },
        )
    }
    deleting?.let { note ->
        ConfirmDialog(
            title = stringResource(R.string.notes_delete_title),
            text = stringResource(R.string.notes_delete_text),
            confirmLabel = stringResource(R.string.notes_delete),
            onConfirm = { viewModel.delete(note) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun NotesGrid(
    notes: List<NoteSummary>,
    folderNames: Map<Long, String>,
    thumbnail: (String) -> File,
    bottomPadding: Dp,
    onOpen: (Long) -> Unit,
    onTogglePin: (NoteSummary) -> Unit,
    onMove: (NoteSummary) -> Unit,
    onDelete: (NoteSummary) -> Unit,
    onReorder: (List<Long>) -> Unit,
) {
    val gridState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()
    val edge = with(LocalDensity.current) { AUTOSCROLL_EDGE.toPx() }
    /*
     * Пока карточку тащат, сетка не перестраивается: в «лесенке» перестановка двигала соседей
     * по колонкам и заголовки, и карточка под пальцем то и дело менялась — всё прыгало.
     * Карточка просто едет за пальцем, а та, на чьё место она встанет, обведена.
     * Порядок меняется один раз — когда палец отпустили.
     */
    var dragId by remember { mutableStateOf<Long?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var dragTotal by remember { mutableStateOf(Offset.Zero) }
    var scrolled by remember { mutableFloatStateOf(0f) }
    var targetId by remember { mutableStateOf<Long?>(null) }
    // Новый порядок держим, пока база не вернёт его же: иначе на миг мелькнул бы старый
    var pending by remember { mutableStateOf<List<Long>?>(null) }
    LaunchedEffect(notes) { if (pending == notes.map { it.id }) pending = null }
    LaunchedEffect(pending) {
        if (pending != null) {
            delay(1_000)
            pending = null
        }
    }
    val byId = remember(notes) { notes.associateBy { it.id } }
    val shown = pending?.mapNotNull(byId::get)?.takeIf { it.size == notes.size } ?: notes
    val (pinned, others) = shown.partition { it.pinned }

    fun key(note: NoteSummary) = if (note.pinned) "pinned-${note.id}" else "other-${note.id}"
    fun idOf(key: Any): Long? = (key as? String)?.substringAfter('-')?.toLongOrNull()

    /** Карточка того же блока под пальцем. */
    fun hovered(dragged: NoteSummary): Long? = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
        val id = idOf(info.key) ?: return@firstOrNull false
        val other = byId[id] ?: return@firstOrNull false
        id != dragged.id && other.pinned == dragged.pinned &&
            finger.x in info.offset.x.toFloat()..(info.offset.x + info.size.width).toFloat() &&
            finger.y in info.offset.y.toFloat()..(info.offset.y + info.size.height).toFloat()
    }?.let { idOf(it.key) }

    fun start(note: NoteSummary, touch: Offset) {
        val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key(note) } ?: return
        dragId = note.id
        finger = Offset(info.offset.x.toFloat(), info.offset.y.toFloat()) + touch
        dragTotal = Offset.Zero
        scrolled = 0f
        targetId = null
    }

    fun drag(note: NoteSummary, delta: Offset) {
        if (dragId != note.id) return
        finger += delta
        dragTotal += delta
        targetId = hovered(note)
        // У края экрана — прокрутка, иначе до далёкой заметки не дотянуться
        val height = gridState.layoutInfo.viewportSize.height
        val step = when {
            finger.y < edge -> -AUTOSCROLL_STEP
            finger.y > height - edge -> AUTOSCROLL_STEP
            else -> 0f
        }
        if (step != 0f) {
            scope.launch {
                val moved = gridState.scrollBy(step)
                // Карточка — вместе со списком уехала бы; оставляем её под пальцем
                scrolled += moved
                targetId = hovered(note)
            }
        }
    }

    fun end(note: NoteSummary) {
        val target = targetId
        dragId = null
        targetId = null
        if (target == null) return
        val list = shown.map { it.id }.toMutableList()
        val to = list.indexOf(target)
        list.remove(note.id)
        list.add(to, note.id)
        pending = list
        onReorder(list)
    }

    @Composable
    fun Card(note: NoteSummary) {
        val isDragged = note.id == dragId
        NoteCard(
            note = note,
            folderName = note.folderId?.let(folderNames::get),
            thumbnail = thumbnail,
            onOpen = { onOpen(note.id) },
            onTogglePin = { onTogglePin(note) },
            onMove = { onMove(note) },
            onDelete = { onDelete(note) },
            onDragStart = { touch -> start(note, touch) },
            onDrag = { delta -> drag(note, delta) },
            onDragEnd = { end(note) },
            wiggle = dragId != null && !isDragged,
            target = note.id == targetId,
            modifier = if (isDragged) {
                Modifier
                    .graphicsLayer {
                        translationX = dragTotal.x
                        translationY = dragTotal.y + scrolled
                        scaleX = 1.04f
                        scaleY = 1.04f
                        // Непрозрачная, с тенью: текст соседа под ней не просвечивает
                        shadowElevation = 12.dp.toPx()
                        shape = RoundedCornerShape(18.dp)
                    }
            } else {
                Modifier
            },
        )
    }

    LazyVerticalStaggeredGrid(
        state = gridState,
        columns = StaggeredGridCells.Fixed(2),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomPadding + 88.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (pinned.isNotEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine, key = "pinnedHeader") {
                SectionTitle(stringResource(R.string.notes_pinned))
            }
            // Ключи разные для блоков: при закреплении карточка не «переезжает», а появляется заново —
            // с одним ключом staggered-сетка оставляла блок «Закреплённые» пустым до перерисовки
            items(pinned, key = { "pinned-${it.id}" }) { note ->
                Box(Modifier.animateItem().zIndex(if (note.id == dragId) 1f else 0f)) { Card(note) }
            }
            if (others.isNotEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine, key = "othersHeader") {
                    SectionTitle(stringResource(R.string.notes_others))
                }
            }
        }
        items(others, key = { "other-${it.id}" }) { note ->
            Box(Modifier.animateItem().zIndex(if (note.id == dragId) 1f else 0f)) { Card(note) }
        }
    }
}

/** У края списка на такой полосе перетаскиваемая карточка прокручивает его — на столько за шаг. */
private val AUTOSCROLL_EDGE = 72.dp
private const val AUTOSCROLL_STEP = 24f

/** Сколько ждём данных, прежде чем показать скелеты: быстрая загрузка не должна «мигать». */
private const val SKELETON_DELAY_MS = 150L

/** Высоты карточек-заглушек — разные, как у настоящих заметок. */
private val SkeletonHeights = listOf(150, 210, 120, 180, 230, 140)

/** Скелеты карточек с бликом, пока заметки читаются из базы (дольше [SKELETON_DELAY_MS]). */
@Composable
private fun NotesSkeleton(bottomPadding: Dp) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SKELETON_DELAY_MS)
        visible = true
    }
    if (!visible) return

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomPadding + 88.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(SkeletonHeights.size) { index ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(SkeletonHeights[index].dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkeletonLine(0.6f, 14.dp)
                SkeletonLine(1f, 10.dp)
                SkeletonLine(0.9f, 10.dp)
                SkeletonLine(0.7f, 10.dp)
                Spacer(Modifier.weight(1f))
                SkeletonLine(0.4f, 8.dp)
            }
        }
    }
}

@Composable
private fun SkeletonLine(widthFraction: Float, height: Dp) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .shimmer(),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun EmptyNotes(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.notes_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
