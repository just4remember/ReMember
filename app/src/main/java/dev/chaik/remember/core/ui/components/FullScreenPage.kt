package dev.chaik.remember.core.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.chaik.remember.R
import dev.chaik.remember.core.settings.BackSwipe
import dev.chaik.remember.core.settings.LocalAppSettings
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Доля ширины, после которой отпущенный свайп закрывает страницу. */
private const val CloseThreshold = 0.25f
private val FlingVelocity = 400.dp
private const val AnimationMillis = 260

/**
 * Полноэкранная страница поверх приложения: заголовок и крестик сверху.
 *
 * Въезжает справа; закрывается крестиком, кнопкой «назад» или свайпом — в ту сторону, что выбрана
 * в «Персонализации → Свайп назад» (справа налево, слева направо или оба). Страница уезжает
 * за пальцем, как вложенные страницы приложения.
 */
@Composable
fun FullScreenPage(
    title: String,
    onDismiss: () -> Unit,
    /** Кнопки в шапке слева от крестика. */
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val screenWidth = LocalWindowInfo.current.containerSize.width.toFloat()
    // До первого кадра страница за правым краем экрана — дальше въезжает
    val offset = remember { Animatable(screenWidth) }
    var closing by remember { mutableStateOf(false) }
    // −1 — справа налево (палец идёт влево), +1 — слева направо
    val mode by rememberUpdatedState(LocalAppSettings.current.backSwipe)
    fun allowed(sign: Float) = when (mode) {
        BackSwipe.RIGHT_TO_LEFT -> sign < 0
        BackSwipe.LEFT_TO_RIGHT -> sign > 0
        BackSwipe.BOTH -> true
    }

    LaunchedEffect(Unit) {
        offset.animateTo(0f, tween(AnimationMillis, easing = FastOutSlowInEasing))
    }

    /** Уезжает в сторону [sign]; крестик и «назад» — туда же, куда закрывает свайп. */
    fun close(sign: Float = if (mode == BackSwipe.LEFT_TO_RIGHT) 1f else -1f) {
        if (closing) return
        closing = true
        scope.launch {
            offset.animateTo(screenWidth * sign, tween(AnimationMillis, easing = FastOutSlowInEasing))
            dismiss()
        }
    }

    Dialog(
        onDismissRequest = { close() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        BackHandler { close() }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = offset.value }
                .pointerInput(Unit) {
                    val flingVelocity = FlingVelocity.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }

                        // Берём жест, только если он явно вбок в разрешённую сторону; вертикальный — прокрутке ленты
                        val slop = viewConfiguration.touchSlop
                        var dx = 0f
                        var dy = 0f
                        var start: PointerInputChange? = null
                        while (start == null) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed || change.isConsumed) return@awaitEachGesture
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val delta = change.positionChange()
                            dx += delta.x
                            dy += delta.y
                            if (abs(dx) > slop || abs(dy) > slop) {
                                if (abs(dx) <= slop || !allowed(if (dx < 0) -1f else 1f) || abs(dx) <= abs(dy) * 1.5f) return@awaitEachGesture
                                change.consume()
                                start = change
                            }
                        }

                        // Направление жеста — куда пошёл палец (при «оба» — любое)
                        val sign = if (dx < 0) -1f else 1f
                        var travel = dx * sign
                        scope.launch { offset.snapTo(travel * sign) }
                        val lifted = drag(start.id) { change ->
                            travel = (travel + change.positionChange().x * sign).coerceAtLeast(0f)
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            scope.launch { offset.snapTo(travel * sign) }
                        }
                        val velocity = tracker.calculateVelocity().x * sign // скорость в сторону закрытия
                        if (lifted && (travel > size.width * CloseThreshold || velocity > flingVelocity)) {
                            close(sign)
                        } else {
                            scope.launch { offset.animateTo(0f, tween(AnimationMillis)) }
                        }
                    }
                },
        ) {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    actions()
                    IconButton(onClick = { close() }) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.editor_close))
                    }
                }
                content()
            }
        }
    }
}
