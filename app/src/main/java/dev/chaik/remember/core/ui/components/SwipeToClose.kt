package dev.chaik.remember.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import dev.chaik.remember.core.settings.BackSwipe
import dev.chaik.remember.core.settings.LocalAppSettings
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Закрыть окно на весь экран свайпом вбок — как страницы приложения: в ту сторону, что
 * выбрана в настройках. Окно едет за пальцем; отпустили дальше четверти ширины —
 * закрывается, иначе возвращается на место.
 */
@Composable
fun Modifier.swipeToClose(onClose: () -> Unit): Modifier {
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
