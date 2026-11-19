package dev.chaik.remember.core.editor

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
 * [overlay] — своё поверх фото (сведения и кнопки в галерее), получает номер открытого фото.
 */
@Composable
fun PhotoViewer(
    files: List<File>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    overlay: @Composable BoxScope.(page: Int) -> Unit = {},
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val pager = rememberPagerState(initialPage = initialIndex.coerceIn(0, files.lastIndex)) { files.size }
        // Пока фото приближено, свайп двигает его, а не листает
        var zoomed by remember { mutableStateOf(false) }

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
                    VideoPage(video, active = page == pager.currentPage)
                } else {
                    ZoomablePhoto(
                        file = files[page],
                        onZoomChange = { if (page == pager.currentPage) zoomed = it },
                    )
                }
            }

            if (files.isNotEmpty()) overlay(pager.currentPage.coerceAtMost(files.lastIndex))

            if (files.size > 1) {
                // Тёмная подложка — чтобы счётчик читался поверх светлого фото
                Text(
                    text = stringResource(R.string.editor_photo_counter, pager.currentPage + 1, files.size),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            IconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.4f),
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Icon(Icons.Outlined.Close, stringResource(R.string.editor_close))
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(file: File, onZoomChange: (Boolean) -> Unit) {
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
