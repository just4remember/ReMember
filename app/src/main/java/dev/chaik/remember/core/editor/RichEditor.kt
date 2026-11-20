package dev.chaik.remember.core.editor

import dev.chaik.remember.core.settings.SpoilerStyle
import dev.chaik.remember.core.ui.components.rememberSpoilerTime
import dev.chaik.remember.core.ui.components.drawSpoiler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.GridView
import dev.chaik.remember.core.ui.components.BackGestureLock
import dev.chaik.remember.core.ui.releaseFocus
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
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.ui.LocalTabVisible
import java.io.File
import kotlin.math.roundToInt

/** Размеры фото на кнопках панели. */
private val WidthPresets = listOf(50, 75, 100)

private val GridSpacing = 4.dp
private val PhotoRadius = 16.dp
private val CellRadius = 8.dp
private val PhotoShape = RoundedCornerShape(PhotoRadius)
private val CellShape = RoundedCornerShape(CellRadius)

/**
 * Тень выделенного блока: густая и широкая, заметная и на светлой, и на тёмной теме.
 * Обычная shadow() даёт слишком бледную тень — рисуем свою, размытым прямоугольником.
 */
private fun Modifier.focusShadow(radius: Dp): Modifier = drawBehind {
    val blur = 22.dp.toPx()
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb(150, 0, 0, 0)
        maskFilter = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    val r = radius.toPx()
    val dy = 4.dp.toPx()
    drawContext.canvas.nativeCanvas.drawRoundRect(0f, dy, size.width, size.height + dy, r, r, paint)
}
private val NoCorners = RoundedCornerShape(0.dp)

/** Сколько места держать под строкой с курсором: панель форматирования и немного воздуха. */
private val CursorClearance = EditorToolbarHeight + 24.dp

/**
 * Редактор «текст + фото». Сам не прокручивается — кладётся в прокручиваемый контейнер экрана.
 *
 * @param photoFile файл фото по id (полноразмерный — Coil сам уменьшит под размер на экране)
 * @param onOpenPhotos открыть фото на весь экран: все фото блока и с какого начать
 * @param onAddMedia открыть выбор фото и видео — для кнопки «добавить» в окне галереи
 *   (выбранное уйдёт в эту галерею: [EditorState.addToGalleryNext]); null — кнопки нет
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
    onAddMedia: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val style = textStyle.copy(color = colors.onSurface)
    val settings = LocalAppSettings.current
    val highlighter = remember(colors, settings.paragraphIndent, settings.paragraphGap, settings.showMarkup) {
        MarkdownHighlighter(colors.outline, colors.primary, settings.paragraphIndent, settings.paragraphGap, colors.onSurface, settings.showMarkup)
    }

    if (state.linkDialogOpen) LinkDialog(state)

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

    // «Назад» сначала снимает выделение с фото, а не закрывает экран. Свайпы «назад» и между
    // вкладками при этом работают — их запрещает только щипок по фото (pinchToResize)
    BackHandler(enabled = state.selectedPhotoKey != null) { state.selectPhoto(null) }

    // Открытое окно галереи — по ключу блока; блок удалили — окно закрывается само
    var galleryKey by remember { mutableStateOf<Long?>(null) }
    val galleryBlock = state.blocks.firstOrNull { it.key == galleryKey } as? EditorState.PhotoBlock
    if (galleryBlock != null) {
        MediaGalleryDialog(
            block = galleryBlock,
            state = state,
            photoFile = photoFile,
            onOpen = { i ->
                state.stopVideo()
                onOpenPhotos(galleryBlock.photoIds, i)
            },
            onAdd = onAddMedia?.let { add ->
                {
                    state.addToGalleryNext(galleryBlock.key)
                    add()
                }
            },
            onDismiss = { galleryKey = null },
        )
    }

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
                        onOpen = { i ->
                            state.stopVideo()
                            onOpenPhotos(block.photoIds, i)
                        },
                        onOpenGallery = {
                            state.stopVideo()
                            galleryKey = block.key
                        },
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

    // Спойлеры и ссылки. Открытый спойлер закрывается, стоит уйти с экрана
    val spans = remember(block.value.text) { InlineSpans.find(block.value.text) }
    var revealed by remember(block) { mutableStateOf(emptySet<String>()) }
    val tabVisible = LocalTabVisible.current
    LaunchedEffect(tabVisible) { if (!tabVisible) revealed = emptySet() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { revealed = emptySet() }
    val selection = block.value.selection
    val active = if (focused) {
        spans.filter { selection.min <= it.range.last && selection.max > it.range.first }.map { it.range }
    } else {
        emptyList()
    }
    val shownHighlighter = remember(highlighter, active, revealed) { highlighter.withInline(active, revealed) }
    // Закрытые спойлеры — «пыль» поверх строк вместо букв; часы анимации — только если они есть
    val hiddenSpoilers = spans.filterIsInstance<SpoilerSpan>().filter { it.range !in active && it.text !in revealed }
    val spoilerStyle = settings.spoilerStyle
    val spoilerTime = if (hiddenSpoilers.isNotEmpty() && spoilerStyle == SpoilerStyle.PARTICLES) rememberSpoilerTime() else null
    val spoilerColor = MaterialTheme.colorScheme.onSurface
    val context = LocalContext.current
    val boxColor = MaterialTheme.colorScheme.primary
    val ruleColor = MaterialTheme.colorScheme.outlineVariant
    val checkColor = MaterialTheme.colorScheme.onPrimary

    // Обработчик жеста живёт дольше одной перекомпозиции, а читать должен текущее состояние
    val onTap by rememberUpdatedState { shownOffset: Int, position: Offset ->
        val offset = mapping.transformedToOriginal(shownOffset)
        // Нажатие забирают только спойлер, ссылка и квадратик пункта; жирное и прочее — обычный текст
        val hits = layout?.takeIf { it.hasTextAt(position) }?.let {
            spans.filter { span -> span !is StyleSpan && span.range !in active && offset in span.range.first..span.range.last + 1 }
        }.orEmpty()
        val spoiler = hits.filterIsInstance<SpoilerSpan>().firstOrNull()
        val link = hits.filterIsInstance<LinkSpan>().firstOrNull()
        val box = hits.filterIsInstance<CheckboxSpan>().firstOrNull()
        when {
            box != null -> block.edit(block.value.copy(text = InlineSpans.toggle(block.value.text, box)))
            spoiler != null && spoiler.text !in revealed -> revealed = revealed + spoiler.text
            link != null -> {
                context.appContainer.appLock.allowExternal()
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, link.url.toUri())) }
            }
            spoiler != null -> revealed = revealed - spoiler.text
            undoEnabled -> shownCorrection = corrections.firstOrNull { offset in it.start..it.end }
        }
        hits.isNotEmpty()
    }

    SmartTextInput(onAutoCorrect = { if (undoEnabled) state.onAutoCorrect(block, it) }) {
        BasicTextField(
            value = block.value,
            onValueChange = { state.onTextChange(block, it) },
            textStyle = style,
            // Заглавные в начале предложения и пункта списка (см. SmartTextInput), исправление опечаток
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = true),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = shownHighlighter,
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
                    // Квадратики пунктов чек-листа — на месте прозрачных «- [ ]»
                    spans.filterIsInstance<RuleSpan>().filter { it.range !in active }.forEach { rule ->
                        drawRule(result, mapping.originalToTransformed(rule.range.first), ruleColor)
                    }
                    spans.filterIsInstance<CheckboxSpan>().filter { it.range !in active }.forEach { box ->
                        drawCheckbox(result, mapping.originalToTransformed(box.range.first), box.checked, boxColor, checkColor)
                    }
                    hiddenSpoilers.forEachIndexed { i, spoiler ->
                        val from = mapping.originalToTransformed(spoiler.content.first)
                        val to = mapping.originalToTransformed(spoiler.content.last + 1)
                        spoilerLines(result, from, to).forEachIndexed { line, rect ->
                            drawSpoiler(rect, spoilerTime?.value ?: 0f, spoilerColor, seed = i * 31 + line, style = spoilerStyle)
                        }
                    }
                    if (!undoEnabled || underline == null) return@drawBehind
                    corrections.forEach {
                        drawCorrectionUnderline(result, mapping.originalToTransformed(it.start), mapping.originalToTransformed(it.end), underline)
                    }
                }
                .onTextTap(layout = { layout }) { offset, position -> onTap(offset, position) },
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

/** Горизонтальная черта во всю ширину поля, посередине строки. */
private fun DrawScope.drawRule(layout: TextLayoutResult, start: Int, color: Color) {
    if (start >= layout.layoutInput.text.length) return
    val line = layout.getLineForOffset(start)
    val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2
    drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
}

/** Квадратик пункта: рамка, а у выполненного — заливка с галочкой. Слева, по высоте строки. */
private fun DrawScope.drawCheckbox(layout: TextLayoutResult, start: Int, checked: Boolean, color: Color, check: Color) {
    if (start >= layout.layoutInput.text.length) return
    val line = layout.getLineForOffset(start)
    val top = layout.getLineTop(line)
    val bottom = layout.getLineBottom(line)
    val side = (bottom - top) * 0.62f
    val left = layout.getBoundingBox(start).left
    val y = top + (bottom - top - side) / 2
    val corner = CornerRadius(side * 0.22f)
    if (checked) {
        drawRoundRect(color, Offset(left, y), Size(side, side), corner)
        val path = Path().apply {
            moveTo(left + side * 0.24f, y + side * 0.52f)
            lineTo(left + side * 0.43f, y + side * 0.70f)
            lineTo(left + side * 0.77f, y + side * 0.32f)
        }
        drawPath(path, check, style = Stroke(width = side * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    } else {
        drawRoundRect(color, Offset(left, y), Size(side, side), corner, style = Stroke(width = side * 0.11f))
    }
}

/** Попал ли палец в текст, а не на пустое место правее строки или ниже последней. */
private fun TextLayoutResult.hasTextAt(position: Offset): Boolean {
    if (position.y > getLineBottom(lineCount - 1)) return false
    val line = getLineForVerticalPosition(position.y)
    return position.x >= getLineLeft(line) && position.x <= getLineRight(line)
}

/** Подпись и адрес ссылки. Выделенный текст становится подписью. */
@Composable
private fun LinkDialog(state: EditorState) {
    val block = state.activeTextBlock()
    val selected = remember(block) { block?.value?.let { it.text.substring(it.selection.min, it.selection.max) }.orEmpty() }
    var label by remember { mutableStateOf(selected) }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { state.linkDialogOpen = false },
        title = { Text(stringResource(R.string.editor_link)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.releaseFocus()) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.replace('\n', ' ') },
                    label = { Text(stringResource(R.string.editor_link_text)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.editor_link_url)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank(),
                onClick = {
                    state.format { MarkdownFormatting.insertLink(it, label, url) }
                    state.linkDialogOpen = false
                },
            ) { Text(stringResource(R.string.editor_link_add)) }
        },
        dismissButton = {
            TextButton(onClick = { state.linkDialogOpen = false }) { Text(stringResource(R.string.editor_close)) }
        },
    )
}

/**
 * Короткий тап по тексту: [onTap] получает позицию в тексте и точку касания. Если он вернул true,
 * нажатие забирается — иначе поле, как обычно, ставит курсор. Прокрутка и долгое нажатие не считаются.
 */
private fun Modifier.onTextTap(layout: () -> TextLayoutResult?, onTap: (offset: Int, position: Offset) -> Boolean): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val up = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
            val moved = (up.position - down.position).getDistance() > viewConfiguration.touchSlop
            val long = up.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis
            if (moved || long) return@awaitEachGesture
            val result = layout() ?: return@awaitEachGesture
            if (onTap(result.getOffsetForPosition(up.position), up.position)) up.consume()
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

/** Ячейка блока: готовое фото (с номером среди готовых) или заглушка того, что ещё добавляется. */
private sealed interface Cell {
    data class Ready(val index: Int, val id: String) : Cell
    data class Waiting(val item: EditorState.PendingMedia) : Cell
}

private fun EditorState.PhotoBlock.cells(): List<Cell> =
    photoIds.mapIndexed { i, id -> Cell.Ready(i, id) } + pending.map { Cell.Waiting(it) }

/**
 * Фото-блок: одно фото, несколько (сеткой, столбиком или лентой) или свёрнутая галерея —
 * с панелью действий. Размер и положение в строке — только у одиночного фото или видео.
 */
@Composable
private fun PhotoBlockView(
    block: EditorState.PhotoBlock,
    state: EditorState,
    photoFile: (String) -> File,
    onOpen: (index: Int) -> Unit,
    onOpenGallery: () -> Unit,
) {
    val selected = state.selectedPhotoKey == block.key
    val colors = MaterialTheme.colorScheme
    val single = block.isSingle

    // Ширина во время щипка — плавная; в документ записывается при отпускании
    var pinchWidth by remember { mutableFloatStateOf(-1f) }
    val animatedWidth by animateFloatAsState(block.widthPercent.toFloat(), label = "photoWidth")
    val width = when {
        !single -> DocumentBlock.FULL_WIDTH.toFloat()
        pinchWidth > 0 -> pinchWidth
        else -> animatedWidth
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth(), contentAlignment = if (single) block.align.toAlignment() else Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth(width / 100f)
                    // Выделенный блок — тенью вокруг; выбранное в нём фото — обводкой
                    // Скругление тени — как у самих фото: у сетки, столбика и аккордеона углы мельче
                    .then(if (selected) Modifier.focusShadow(if (block.isGrid && !block.isGallery) CellRadius else PhotoRadius) else Modifier)
                    .then(if (single) Modifier.pinchToResize(block, state) { pinchWidth = it } else Modifier),
            ) {
                when {
                    block.isGallery -> GalleryCard(
                        block = block,
                        state = state,
                        photoFile = photoFile,
                        shape = PhotoShape,
                        onView = onOpenGallery,
                        // id "" — у карточки нет своей кнопки ▶; двойной тап открывает окно галереи
                        modifier = Modifier.photoTaps(block, 0, "", compact = false, state) { onOpenGallery() },
                    )
                    block.isGrid -> when (block.layout) {
                        PhotoLayout.Grid -> PhotoGrid(block, state, photoFile, onOpen)
                        PhotoLayout.Column -> PhotoColumn(block, state, photoFile, onOpen)
                        PhotoLayout.Row -> PhotoAccordion(block, state, photoFile, onOpen)
                    }
                    block.photoIds.isEmpty() -> block.pending.firstOrNull()?.let {
                        PendingCell(it, PhotoShape, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
                    }
                    else -> {
                        val id = block.photoIds.first()
                        PhotoCell(
                            id = id,
                            file = photoFile(id),
                            state = state,
                            shape = PhotoShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(state.aspectOf(id))
                                .then(if (selected) Modifier.border(2.dp, colors.primary, PhotoShape) else Modifier)
                                .photoTaps(block, 0, id, compact = false, state, onOpen = onOpen),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(selected) {
            PhotoActions(
                block = block,
                state = state,
                onOpen = { if (block.isGallery) onOpenGallery() else onOpen(state.selectedPhotoIndex) },
            )
        }
    }
}

/** Отметка фото, к которому относятся «корзина» и «на весь экран» в выделенном блоке. */
@Composable
private fun Modifier.selectedCell(block: EditorState.PhotoBlock, state: EditorState, index: Int, shape: RoundedCornerShape): Modifier =
    if (state.selectedPhotoKey == block.key && state.selectedPhotoIndex == index) {
        border(2.dp, MaterialTheme.colorScheme.primary, shape)
    } else {
        this
    }

/** Сетка: 2 фото — в ряд, 3 — в ряд, 4 — квадрат 2×2, больше — по 3 в ряд. */
@Composable
private fun PhotoGrid(
    block: EditorState.PhotoBlock,
    state: EditorState,
    photoFile: (String) -> File,
    onOpen: (index: Int) -> Unit,
) {
    val cells = block.cells()
    val columns = if (cells.size == 2 || cells.size == 4) 2 else 3

    Column(verticalArrangement = Arrangement.spacedBy(GridSpacing)) {
        cells.chunked(columns).forEach { rowCells ->
            Row(horizontalArrangement = Arrangement.spacedBy(GridSpacing)) {
                rowCells.forEach { cell ->
                    val cellModifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                    when (cell) {
                        is Cell.Ready -> PhotoCell(
                            id = cell.id,
                            file = photoFile(cell.id),
                            state = state,
                            compact = true,
                            shape = CellShape,
                            modifier = cellModifier
                                .selectedCell(block, state, cell.index, CellShape)
                                .photoTaps(block, cell.index, cell.id, compact = true, state, onOpen = onOpen),
                        )
                        is Cell.Waiting -> PendingCell(cell.item, CellShape, cellModifier, compact = true)
                    }
                }
                // Неполный последний ряд: пустые места, чтобы ячейки были того же размера
                repeat(columns - rowCells.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Столбик: каждое фото на всю ширину, в своих пропорциях, одно под другим. */
@Composable
private fun PhotoColumn(
    block: EditorState.PhotoBlock,
    state: EditorState,
    photoFile: (String) -> File,
    onOpen: (index: Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GridSpacing)) {
        block.cells().forEach { cell ->
            when (cell) {
                is Cell.Ready -> PhotoCell(
                    id = cell.id,
                    file = photoFile(cell.id),
                    state = state,
                    shape = CellShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(state.aspectOf(cell.id))
                        .photoTaps(block, cell.index, cell.id, compact = false, state, selectItem = false, onOpen = onOpen),
                )
                is Cell.Waiting -> PendingCell(cell.item, CellShape, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
            }
        }
    }
}

/**
 * Аккордеон: на виду одно фото или видео во всю ширину, свайп вбок — следующее, внизу
 * точки (не больше 7). Каждые 2 секунды само листается дальше — кроме моментов, когда
 * его листают пальцем или в нём играет видео. Видео сами не запускаются.
 */
@Composable
private fun PhotoAccordion(
    block: EditorState.PhotoBlock,
    state: EditorState,
    photoFile: (String) -> File,
    onOpen: (index: Int) -> Unit,
) {
    val cells = block.cells()
    val pager = rememberPagerState { block.itemCount }
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    val playingHere = state.playingVideoId != null && state.playingVideoId in block.photoIds
    // Ключ — устоявшаяся страница: currentPage меняется на полпути анимации, и перезапуск
    // эффекта обрывал её — на экране оставались половинки двух фото
    LaunchedEffect(pager.settledPage, dragged, playingHere, cells.size) {
        if (dragged || playingHere || cells.size < 2) return@LaunchedEffect
        delay(ACCORDION_STEP_MS)
        pager.animateScrollToPage(
            page = (pager.settledPage + 1) % cells.size,
            animationSpec = tween(ACCORDION_SCROLL_MS, easing = FastOutSlowInEasing),
        )
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(CellShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            when (val cell = cells.getOrNull(page)) {
                is Cell.Ready -> PhotoCell(
                    id = cell.id,
                    file = photoFile(cell.id),
                    state = state,
                    shape = NoCorners,
                    // Целиком, без обрезки: свободное место по краям остаётся пустым
                    fit = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .photoTaps(block, cell.index, cell.id, compact = false, state, selectItem = false, onOpen = onOpen),
                )
                is Cell.Waiting -> PendingCell(cell.item, NoCorners, Modifier.fillMaxSize())
                null -> Unit
            }
        }
        if (cells.size > 1) {
            PagerDots(
                count = cells.size,
                current = pager.currentPage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
            )
        }
    }
}

/**
 * Точки страниц: не больше [MAX_DOTS]; если страниц больше, окно точек едет за текущей,
 * а крайние точки меньше — видно, что дальше есть ещё. Не нажимаются.
 */
@Composable
private fun PagerDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val start = (current - MAX_DOTS / 2).coerceIn(0, (count - MAX_DOTS).coerceAtLeast(0))
    val end = minOf(count, start + MAX_DOTS)
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        for (i in start until end) {
            val edge = (i == start && start > 0) || (i == end - 1 && end < count)
            val size by animateDpAsState(
                when {
                    i == current -> 7.dp
                    edge -> 4.dp
                    else -> 5.dp
                },
                label = "dot",
            )
            Box(
                Modifier
                    .size(size)
                    .background(if (i == current) Color.White else Color.White.copy(alpha = 0.5f), CircleShape),
            )
        }
    }
}

private const val ACCORDION_STEP_MS = 2_000L
private const val ACCORDION_SCROLL_MS = 650
private const val MAX_DOTS = 7

/**
 * Фото или кадр-обложка видео; у видео поверх — ▶ и длительность, а после нажатия на ▶ —
 * мини-плеер прямо в ячейке.
 */
@Composable
private fun PhotoCell(
    id: String,
    file: File,
    state: EditorState,
    shape: RoundedCornerShape,
    modifier: Modifier,
    compact: Boolean = false,
    fit: Boolean = false,
) {
    val durationMs = state.durationOf(id)
    Box(modifier.clip(shape)) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        if (durationMs != null) {
            val video = if (state.playingVideoId == id) file.videoSibling() else null
            if (video != null) {
                InlineVideo(
                    file = video,
                    aspect = state.aspectOf(id),
                    paused = state.videoPaused,
                    compact = compact,
                    fit = fit,
                    onPause = state::pauseVideo,
                    onEnded = state::stopVideo,
                )
            } else {
                VideoBadge(durationMs, compact)
            }
        }
    }
}

/**
 * Тап — открыть/закрыть панель (повторный тап по тому же фото закрывает), у видео тап по
 * кнопке ▶ — запустить/приостановить мини-плеер. Двойной тап — на весь экран,
 * долгое нажатие — открыть панель.
 */
@Composable
private fun Modifier.photoTaps(
    block: EditorState.PhotoBlock,
    index: Int,
    id: String,
    compact: Boolean,
    state: EditorState,
    selectItem: Boolean = true,
    onOpen: (Int) -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    // В столбике и аккордеоне выделяется весь блок, а не отдельное фото
    val selectIndex = if (selectItem) index else 0
    return pointerInput(block.key, index, id, compact) {
        // Зона кнопки ▶ — сам круг и немного вокруг, чтобы попадать не целясь
        val playRadius = playCircleSize(compact).toPx() / 2 + 16.dp.toPx()
        detectTapGestures(
            // Состояние читается в момент тапа, а не при создании обработчика —
            // иначе повторный тап «не видел» бы, что панель уже открыта
            onTap = { position ->
                val center = Offset(size.width / 2f, size.height / 2f)
                if (state.durationOf(id) != null && (position - center).getDistance() <= playRadius) {
                    state.tapPlay(id)
                } else {
                    state.togglePhoto(block.key, selectIndex)
                }
            },
            onDoubleTap = { onOpen(index) },
            onLongPress = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                state.selectPhoto(block.key, selectIndex)
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
        try {
            do {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } >= 2) {
                    // Пока два пальца меняют размер, свайп не уводит со страницы и не листает вкладки
                    if (!pinched) BackGestureLock.acquire()
                    current = (current * event.calculateZoom())
                        .coerceIn(DocumentBlock.MIN_WIDTH.toFloat(), DocumentBlock.FULL_WIDTH.toFloat())
                    pinched = true
                    onPinch(current)
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        } finally {
            if (pinched) BackGestureLock.release()
        }
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
            if (block.isSingle) {
                // Размер и положение — только у одиночного фото или видео
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
            } else {
                // Несколько фото: альбомом (если их много), сеткой, столбиком или аккордеоном
                val options = if (block.canBeGallery) listOf(null) + LayoutOptions else LayoutOptions
                SingleChoiceSegmentedButtonRow {
                    options.forEachIndexed { i, option ->
                        SegmentedButton(
                            selected = if (option == null) block.isGallery else !block.isGallery && block.layout == option.layout,
                            onClick = {
                                if (option == null) {
                                    state.setPhotoExpanded(block.key, false)
                                } else {
                                    state.setPhotoLayout(block.key, option.layout)
                                    state.setPhotoExpanded(block.key, true)
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                            icon = {},
                            label = {
                                if (option == null) {
                                    Icon(Icons.Outlined.PhotoLibrary, stringResource(R.string.editor_gallery))
                                } else {
                                    Icon(option.icon, stringResource(option.label))
                                }
                            },
                        )
                    }
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
                // У альбома «корзина» удаляет его целиком, у сетки — выбранное фото. В столбике
                // и аккордеоне отдельное фото не выбирается — и удалять там нечего
                if (block.isSingle || block.isGallery || block.layout == PhotoLayout.Grid) IconButton(
                    onClick = {
                        if (block.isGallery) state.removePhoto(block.key) else state.removePhotoAt(block.key, state.selectedPhotoIndex)
                    },
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        stringResource(if (block.isGallery) R.string.editor_gallery_delete else R.string.editor_photo_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private data class LayoutOption(val layout: PhotoLayout, val icon: ImageVector, val label: Int)

private val LayoutOptions = listOf(
    LayoutOption(PhotoLayout.Grid, Icons.Outlined.GridView, R.string.editor_layout_grid),
    LayoutOption(PhotoLayout.Column, Icons.Outlined.ViewAgenda, R.string.editor_layout_column),
    LayoutOption(PhotoLayout.Row, Icons.Outlined.ViewCarousel, R.string.editor_layout_row),
)

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

/** Прямоугольники строк, которые занимает текст [from]..[to] (для «пыли» спойлера). */
private fun spoilerLines(layout: androidx.compose.ui.text.TextLayoutResult, from: Int, to: Int): List<androidx.compose.ui.geometry.Rect> {
    val length = layout.layoutInput.text.length
    val start = from.coerceIn(0, length)
    val end = to.coerceIn(start, length)
    if (end <= start) return emptyList()
    val first = layout.getLineForOffset(start)
    val last = layout.getLineForOffset(end - 1)
    return (first..last).map { line ->
        val left = if (line == first) layout.getHorizontalPosition(start, usePrimaryDirection = true) else layout.getLineLeft(line)
        val right = if (line == last) layout.getHorizontalPosition(end, usePrimaryDirection = true) else layout.getLineRight(line)
        androidx.compose.ui.geometry.Rect(minOf(left, right), layout.getLineTop(line), maxOf(left, right), layout.getLineBottom(line))
    }
}
