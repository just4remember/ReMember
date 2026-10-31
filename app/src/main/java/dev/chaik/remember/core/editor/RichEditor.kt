package dev.chaik.remember.core.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.chaik.remember.R
import dev.chaik.remember.core.settings.LocalAppSettings
import java.io.File
import kotlin.math.roundToInt

/** Размеры фото на кнопках панели. */
private val WidthPresets = listOf(50, 75, 100)

private val GridSpacing = 4.dp
private val PhotoShape = RoundedCornerShape(16.dp)
private val CellShape = RoundedCornerShape(8.dp)

/** Сколько места держать под строкой с курсором: панель форматирования и немного воздуха. */
private val CursorClearance = EditorToolbarHeight + 24.dp

/**
 * Редактор «текст + фото». Сам не прокручивается — кладётся в прокручиваемый контейнер экрана.
 *
 * @param photoFile файл фото по id (полноразмерный — Coil сам уменьшит под размер на экране)
 * @param onOpenPhotos открыть фото на весь экран: все фото блока и с какого начать
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RichEditor(
    state: EditorState,
    photoFile: (String) -> File,
    onOpenPhotos: (photoIds: List<String>, index: Int) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val colors = MaterialTheme.colorScheme
    val style = textStyle.copy(color = colors.onSurface)
    val settings = LocalAppSettings.current
    val highlighter = remember(colors, settings.paragraphIndent, settings.paragraphGap) {
        MarkdownHighlighter(colors.outline, colors.primary, settings.paragraphIndent, settings.paragraphGap)
    }

    // После вставки фото курсор переходит под него — но только если клавиатура уже была в работе
    LaunchedEffect(state.focusRequestKey) {
        val key = state.focusRequestKey ?: return@LaunchedEffect
        val block = state.blocks.firstOrNull { it.key == key } as? EditorState.TextBlock
        if (block != null && state.hasFocus) {
            withFrameNanos { } // дождаться, пока новый блок появится на экране
            block.focusRequester.requestFocus()
        }
        state.consumeFocusRequest()
    }

    // «Назад» прячет клавиатуру — курсор уходит вместе с ней, а не мигает дальше.
    // Если клавиатура пропала, потому что открылось окно выбора фото, курсор не трогаем:
    // после вставки он перейдёт под фото
    val keyboardVisible = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var keyboardWasShown by remember { mutableStateOf(false) }
    LaunchedEffect(keyboardVisible) {
        when {
            keyboardVisible -> keyboardWasShown = true
            keyboardWasShown -> {
                keyboardWasShown = false
                if (state.hasFocus && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) focusManager.clearFocus()
            }
        }
    }

    // «Назад» сначала снимает выделение с фото, а не закрывает экран
    BackHandler(enabled = state.selectedPhotoKey != null) { state.selectPhoto(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.blocks.forEachIndexed { index, block ->
            // key — чтобы при вставке фото поля ввода не путали своё состояние (курсор, фокус)
            key(block.key) {
                when (block) {
                    is EditorState.TextBlock -> TextBlockField(
                        block = block,
                        state = state,
                        style = style,
                        highlighter = highlighter,
                        // Подсказка только в пустом документе
                        placeholder = placeholder.takeIf { index == 0 && state.isEmpty },
                    )
                    is EditorState.PhotoBlock -> PhotoBlockView(
                        block = block,
                        state = state,
                        photoFile = photoFile,
                        onOpen = { i -> onOpenPhotos(block.photoIds, i) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TextBlockField(
    block: EditorState.TextBlock,
    state: EditorState,
    style: TextStyle,
    highlighter: MarkdownHighlighter,
    placeholder: String?,
) {
    // Строка с курсором должна оставаться над клавиатурой и панелью форматирования.
    // Поле само в прокручиваемый экран не подкручивает (оно не знает про панель) — делаем это сами
    // при каждом наборе, перемещении курсора и появлении клавиатуры.
    val bringIntoView = remember { BringIntoViewRequester() }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // В конце абзацев на экране невидимые символы — позиции в тексте и в раскладке различаются
    val mapping = remember(block.value.text, highlighter) { highlighter.offsetMapping(block.value.text) }
    var focused by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(focused, block.value.text, block.value.selection, layout, imeBottom) {
        val result = layout ?: return@LaunchedEffect
        if (!focused) return@LaunchedEffect
        val shownCursor = mapping.originalToTransformed(block.value.selection.end)
        val cursor = result.getCursorRect(shownCursor.coerceIn(0, result.layoutInput.text.length))
        val below = with(density) { CursorClearance.toPx() }
        bringIntoView.bringIntoView(cursor.copy(top = cursor.top - below / 4, bottom = cursor.bottom + below))
    }

    // Слова, заменённые Т9: подчёркнуты, тап по такому слову — окошко «вернуть как было».
    // Вид линии и сама возможность — в «Персонализации»
    val settings = LocalAppSettings.current
    val undoEnabled = settings.t9Undo
    val corrections by remember(block) {
        derivedStateOf { block.corrections.filter { it.isValidIn(block.value.text) } }
    }
    var shownCorrection by remember { mutableStateOf<AutoCorrection?>(null) }
    LaunchedEffect(block.value.text, undoEnabled) { shownCorrection = null }
    val underline = correctionLine(settings)

    SmartTextInput(onAutoCorrect = { if (undoEnabled) state.onAutoCorrect(block, it) }) {
        BasicTextField(
            value = block.value,
            onValueChange = { state.onTextChange(block, it) },
            textStyle = style,
            // Заглавные в начале предложения и пункта списка (см. SmartTextInput), исправление опечаток
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = true),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = highlighter,
            onTextLayout = { layout = it },
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoView)
                .focusRequester(block.focusRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (!it.isFocused) shownCorrection = null
                    state.onTextFocusChanged(block, it.isFocused)
                }
                .drawBehind {
                    val result = layout ?: return@drawBehind
                    if (!undoEnabled || underline == null) return@drawBehind
                    corrections.forEach {
                        drawCorrectionUnderline(result, mapping.originalToTransformed(it.start), mapping.originalToTransformed(it.end), underline)
                    }
                }
                .onCorrectionTap(layout = { layout }) { shownOffset ->
                    val offset = mapping.transformedToOriginal(shownOffset)
                    if (undoEnabled) shownCorrection = corrections.firstOrNull { offset in it.start..it.end }
                },
            decorationBox = { inner ->
                Box {
                    if (placeholder != null && block.value.text.isEmpty()) {
                        // На уровне красной строки: там же начнётся набранный текст
                        Text(
                            text = placeholder,
                            style = style.copy(
                                color = MaterialTheme.colorScheme.outline,
                                textIndent = TextIndent(firstLine = (settings.paragraphIndent / 10f).em),
                            ),
                        )
                    }
                    inner()
                    val shown = shownCorrection
                    val result = layout
                    if (shown != null && result != null) {
                        CorrectionPopup(
                            correction = shown,
                            shownStart = mapping.originalToTransformed(shown.start),
                            layout = result,
                            onRevert = {
                                shownCorrection = null
                                state.revertCorrection(block, shown)
                            },
                            onDismiss = { shownCorrection = null },
                        )
                    }
                }
            },
        )
    }
}

/**
 * Короткий тап по тексту: [onTap] получает позицию в тексте. Касание не забирает —
 * поле само ставит курсор, как обычно. Прокрутка и долгое нажатие не считаются.
 */
private fun Modifier.onCorrectionTap(layout: () -> TextLayoutResult?, onTap: (offset: Int) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val up = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
            val moved = (up.position - down.position).getDistance() > viewConfiguration.touchSlop
            val long = up.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis
            if (moved || long) return@awaitEachGesture
            val result = layout() ?: return@awaitEachGesture
            onTap(result.getOffsetForPosition(up.position))
        }
    }

/** Окошко над заменённым словом: набранное слово, тап — вернуть его. Клавиатуру не закрывает. */
@Composable
private fun CorrectionPopup(
    correction: AutoCorrection,
    /** Начало слова на экране (с учётом невидимых символов в конце абзацев). */
    shownStart: Int,
    layout: TextLayoutResult,
    onRevert: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    val line = layout.getLineForOffset(shownStart)
    val word = IntRect(
        left = layout.getBoundingBox(shownStart).left.roundToInt(),
        top = layout.getLineTop(line).roundToInt(),
        right = layout.getBoundingBox(shownStart).right.roundToInt(),
        bottom = layout.getLineBottom(line).roundToInt(),
    )
    Popup(
        popupPositionProvider = remember(word, gap) { AboveWordPosition(word, gap) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false),
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceContainerHighest, shadowElevation = 6.dp) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clickable(onClick = onRevert)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Undo,
                    contentDescription = stringResource(R.string.editor_revert_correction),
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(correction.original, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Над словом, а если сверху нет места — под строкой; не вылезая за края экрана. */
private class AboveWordPosition(private val word: IntRect, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.left + word.left).coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        val above = anchorBounds.top + word.top - popupContentSize.height - gap
        val y = if (above >= 0) above else anchorBounds.top + word.bottom + gap
        return IntOffset(x, y)
    }
}

/** Фото-блок: одно фото или сетка, с выравниванием, щипком и панелью действий. */
@Composable
private fun PhotoBlockView(
    block: EditorState.PhotoBlock,
    state: EditorState,
    photoFile: (String) -> File,
    onOpen: (index: Int) -> Unit,
) {
    val selected = state.selectedPhotoKey == block.key
    val colors = MaterialTheme.colorScheme

    // Ширина во время щипка — плавная; в документ записывается при отпускании
    var pinchWidth by remember { mutableFloatStateOf(-1f) }
    val animatedWidth by animateFloatAsState(block.widthPercent.toFloat(), label = "photoWidth")
    val width = if (pinchWidth > 0) pinchWidth else animatedWidth

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth(), contentAlignment = block.align.toAlignment()) {
            Box(
                Modifier
                    .fillMaxWidth(width / 100f)
                    .then(if (selected) Modifier.border(3.dp, colors.primary, PhotoShape) else Modifier)
                    .padding(if (selected) 3.dp else 0.dp)
                    .pinchToResize(block, state) { pinchWidth = it },
            ) {
                if (block.isGrid) {
                    PhotoGrid(block, state, photoFile, onOpen)
                } else {
                    PhotoCell(
                        file = photoFile(block.photoIds.first()),
                        shape = PhotoShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(state.aspectOf(block.photoIds.first()))
                            .photoTaps(block, 0, state, onOpen),
                    )
                }
            }
        }

        AnimatedVisibility(selected) {
            PhotoActions(block, state, onOpen = { onOpen(state.selectedPhotoIndex) })
        }
    }
}

/** Сетка: 2 фото — в ряд, 3 — в ряд, 4 — квадрат 2×2, больше — по 3 в ряд. */
@Composable
private fun PhotoGrid(
    block: EditorState.PhotoBlock,
    state: EditorState,
    photoFile: (String) -> File,
    onOpen: (index: Int) -> Unit,
) {
    val ids = block.photoIds
    val columns = if (ids.size == 2 || ids.size == 4) 2 else 3
    val markSelected = state.selectedPhotoKey == block.key

    Column(verticalArrangement = Arrangement.spacedBy(GridSpacing)) {
        ids.chunked(columns).forEachIndexed { row, rowIds ->
            Row(horizontalArrangement = Arrangement.spacedBy(GridSpacing)) {
                rowIds.forEachIndexed { column, id ->
                    val index = row * columns + column
                    PhotoCell(
                        file = photoFile(id),
                        shape = CellShape,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .then(
                                // В выделенной сетке отмечаем фото, к которому относятся «корзина» и «на весь экран»
                                if (markSelected && state.selectedPhotoIndex == index) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.tertiary, CellShape)
                                } else {
                                    Modifier
                                },
                            )
                            .photoTaps(block, index, state, onOpen),
                    )
                }
                // Неполный последний ряд: пустые места, чтобы ячейки были того же размера
                repeat(columns - rowIds.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PhotoCell(file: File, shape: RoundedCornerShape, modifier: Modifier) {
    Box(modifier.clip(shape)) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/**
 * Тап — открыть/закрыть панель (повторный тап по тому же фото закрывает),
 * двойной тап — на весь экран, долгое нажатие — открыть панель.
 */
@Composable
private fun Modifier.photoTaps(
    block: EditorState.PhotoBlock,
    index: Int,
    state: EditorState,
    onOpen: (Int) -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    return pointerInput(block.key, index) {
        detectTapGestures(
            // Состояние читается в момент тапа, а не при создании обработчика —
            // иначе повторный тап «не видел» бы, что панель уже открыта
            onTap = { state.togglePhoto(block.key, index) },
            onDoubleTap = { onOpen(index) },
            onLongPress = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                state.selectPhoto(block.key, index)
            },
        )
    }
}

/** Щипок двумя пальцами меняет размер блока; одним пальцем — обычная прокрутка. */
private fun Modifier.pinchToResize(
    block: EditorState.PhotoBlock,
    state: EditorState,
    onPinch: (Float) -> Unit,
): Modifier = pointerInput(block.key) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var current = block.widthPercent.toFloat()
        var pinched = false
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                current = (current * event.calculateZoom())
                    .coerceIn(DocumentBlock.MIN_WIDTH.toFloat(), DocumentBlock.FULL_WIDTH.toFloat())
                pinched = true
                onPinch(current)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (pinched) {
            // Округляем до 5 %, чтобы размеры не были «кривыми»
            state.setPhotoWidth(block.key, (current / 5).roundToInt() * 5)
            onPinch(-1f)
        }
    }
}

@Composable
private fun PhotoActions(
    block: EditorState.PhotoBlock,
    state: EditorState,
    onOpen: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.padding(top = 8.dp),
    ) {
        // Несколько строк: в одну на узком экране всё не помещается
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SingleChoiceSegmentedButtonRow {
                WidthPresets.forEachIndexed { i, preset ->
                    SegmentedButton(
                        selected = block.widthPercent == preset,
                        onClick = { state.setPhotoWidth(block.key, preset) },
                        shape = SegmentedButtonDefaults.itemShape(i, WidthPresets.size),
                        icon = {},
                        label = { Text("$preset%") },
                    )
                }
            }
            SingleChoiceSegmentedButtonRow {
                AlignOptions.forEachIndexed { i, (align, icon, label) ->
                    SegmentedButton(
                        selected = block.align == align,
                        onClick = { state.setPhotoAlign(block.key, align) },
                        shape = SegmentedButtonDefaults.itemShape(i, AlignOptions.size),
                        icon = {},
                        label = { Icon(icon, stringResource(label)) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { state.movePhoto(block.key, up = true) },
                    enabled = state.canMovePhoto(block.key, up = true),
                ) {
                    Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.editor_photo_up))
                }
                IconButton(
                    onClick = { state.movePhoto(block.key, up = false) },
                    enabled = state.canMovePhoto(block.key, up = false),
                ) {
                    Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.editor_photo_down))
                }
                IconButton(onClick = onOpen) {
                    Icon(Icons.Outlined.Fullscreen, stringResource(R.string.editor_photo_open))
                }
                FilledTonalIconButton(onClick = { state.removePhotoAt(block.key, state.selectedPhotoIndex) }) {
                    Icon(Icons.Outlined.Delete, stringResource(R.string.editor_photo_delete), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private data class AlignOption(val align: PhotoAlign, val icon: ImageVector, val label: Int)

private val AlignOptions = listOf(
    AlignOption(PhotoAlign.Start, Icons.AutoMirrored.Outlined.FormatAlignLeft, R.string.editor_align_left),
    AlignOption(PhotoAlign.Center, Icons.Outlined.FormatAlignCenter, R.string.editor_align_center),
    AlignOption(PhotoAlign.End, Icons.AutoMirrored.Outlined.FormatAlignRight, R.string.editor_align_right),
)

private fun PhotoAlign.toAlignment(): Alignment = when (this) {
    PhotoAlign.Start -> Alignment.TopStart
    PhotoAlign.Center -> Alignment.TopCenter
    PhotoAlign.End -> Alignment.TopEnd
}
