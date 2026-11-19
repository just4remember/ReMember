package dev.chaik.remember.core.editor

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import dev.chaik.remember.R
import java.io.File

/**
 * Фото и видео на весь экран. Если их несколько (сетка) — листаются свайпом; видео играет
 * в плеере, пока открыта его страница.
 * Щипок — масштаб, двойной тап — приблизить/отдалить, крестик или «назад» — закрыть.
 * Тап прячет и показывает всё поверх снимка (крестик, счётчик, [overlay], кнопки видео) —
 * чтобы ничто не мешало смотреть.
 * [overlay] — своё поверх фото (сведения и кнопки в галерее), получает номер открытого фото
 * и модификатор для своего нижнего блока: по нему кнопки видео встают выше этого блока.
 * [videoSheet] — сведения для страниц видео, как в YouTube: вместо [overlay] они спрятаны
 * и выезжают снизу поверх ролика и кнопок по свайпу вверх, убираются свайпом вниз.
 */
@Composable
fun PhotoViewer(
    files: List<File>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    overlay: @Composable BoxScope.(page: Int, bottomBlock: Modifier) -> Unit = { _, _ -> },
    videoSheet: (@Composable BoxScope.(page: Int) -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val pager = rememberPagerState(initialPage = initialIndex.coerceIn(0, files.lastIndex)) { files.size }
        // Пока фото приближено, свайп двигает его, а не листает
        var zoomed by remember { mutableStateOf(false) }
        // Высота нижнего блока [overlay] — чтобы полоса видео не пряталась под ним
        val density = LocalDensity.current
        var overlayHeight by remember { mutableIntStateOf(0) }
        var chrome by remember { mutableStateOf(true) }
        // Шторка сведений у видео; на другой странице — снова закрыта
        var sheet by remember { mutableStateOf(false) }
        LaunchedEffect(pager.currentPage) { sheet = false }
        BackHandler(enabled = sheet) { sheet = false }
        val current = pager.currentPage.coerceAtMost(files.lastIndex)
        val currentIsVideo = remember(current, files) { files.getOrNull(current)?.videoSibling() != null }
        val sheetMode = videoSheet != null && currentIsVideo
        val bottomBlock = Modifier.onSizeChanged { overlayHeight = it.height }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = !zoomed,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val video = files[page].videoSibling()
                if (video != null) {
                    VideoPage(
                        file = video,
                        active = page == pager.currentPage,
                        controls = chrome,
                        onControlsChange = { chrome = it },
                        bottomPadding = if (videoSheet != null) 0.dp else with(density) { overlayHeight.toDp() },
                        onSwipe = videoSheet?.let { { up: Boolean -> sheet = up } },
                    )
                } else {
                    ZoomablePhoto(
                        file = files[page],
                        onZoomChange = { if (page == pager.currentPage) zoomed = it },
                        onTap = { chrome = !chrome },
                    )
                }
            }

            AnimatedVisibility(chrome && !sheetMode, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
                Box(Modifier.fillMaxSize()) {
                    if (files.isNotEmpty()) overlay(current, bottomBlock)
                }
            }

            if (videoSheet != null) {
                VideoInfoSheet(
                    visible = sheet && sheetMode,
                    onClose = { sheet = false },
                ) { videoSheet(current) }
            }

            AnimatedVisibility(chrome && files.size > 1, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopStart)) {
                // Тёмная подложка — чтобы счётчик читался поверх светлого фото
                Text(
                    text = stringResource(R.string.editor_photo_counter, pager.currentPage + 1, files.size),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.4f),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(8.dp),
                ) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.editor_close))
                }
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(file: File, onZoomChange: (Boolean) -> Unit, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    fun update(newScale: Float, newOffset: Offset) {
        scale = newScale
        offset = if (newScale == 1f) Offset.Zero else newOffset
        onZoomChange(newScale > 1f)
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Свой детектор вместо detectTransformGestures: тот забирает любой свайп,
                // и листать фото было бы нельзя. Здесь жест берём, только если
                // пальцев два (масштаб) или фото уже приближено (двигаем его).
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || scale > 1f) {
                            update((scale * event.calculateZoom()).coerceIn(1f, 5f), offset + event.calculatePan())
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { if (scale > 1f) update(1f, Offset.Zero) else update(2.5f, offset) },
                )
            },
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

/**
 * Шторка снизу поверх видео и его кнопок: выезжает, едет за пальцем при свайпе вниз
 * и закрывается, если протянуть дальше порога.
 */
@Composable
private fun BoxScope.VideoInfoSheet(visible: Boolean, onClose: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    var drag by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(visible) { if (visible) drag = 0f }
    val close by rememberUpdatedState(onClose)
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(280)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(240)),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = drag }
                .pointerInput(Unit) {
                    val threshold = 64.dp.toPx()
                    detectVerticalDragGestures(
                        onDragEnd = { if (drag > threshold) close() else drag = 0f },
                        onDragCancel = { drag = 0f },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            drag = (drag + dy).coerceAtLeast(0f)
                        },
                    )
                },
        ) {
            content()
        }
    }
}
