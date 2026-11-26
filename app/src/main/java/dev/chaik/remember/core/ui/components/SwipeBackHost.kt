package dev.chaik.remember.core.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import dev.chaik.remember.core.settings.BackSwipe
import dev.chaik.remember.core.settings.LocalAppSettings
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlin.math.abs

/** Доля ширины экрана, после которой отпущенный свайп закрывает страницу. */
private const val CompleteThreshold = 0.25f

/** Скорость, начиная с которой даже короткий резкий свайп закрывает страницу. */
private val FlingVelocity = 400.dp

/** Пауза после закрытия, пока доигрывает анимация: новый жест в это время игнорируется. */
private const val CooldownMillis = 350L

/**
 * Свайп в любом месте экрана = «назад»: справа налево или, по настройке, слева направо.
 *
 * Жест передаётся в систему навигации как predictive back, поэтому
 * NavDisplay сам анимирует страницу за пальцем и показывает предыдущую под ней —
 * так же, как при системном жесте «назад».
 *
 * Решение «закрыть / вернуть» принимается при отпускании пальца:
 * закрываем, если страница сдвинута больше чем на четверть экрана
 * или если свайп был резким; иначе страница возвращается на место.
 */
@Composable
fun SwipeBackHost(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    val input = remember { DirectNavigationEventInput() }
    DisposableEffect(dispatcher, input) {
        dispatcher?.addInput(input)
        onDispose { dispatcher?.removeInput(input) }
    }
    // Пока держат запрет (например, настраивают фото), свайп не уводит со страницы
    val isEnabled by rememberUpdatedState(enabled && !BackGestureLock.isHeld)
    // Направление жеста: −1 — справа налево (палец идёт влево), +1 — слева направо
    val mode by rememberUpdatedState(LocalAppSettings.current.backSwipe)
    // −1 — справа налево (палец идёт влево), +1 — слева направо
    fun allowed(sign: Float) = when (mode) {
        BackSwipe.RIGHT_TO_LEFT -> sign < 0
        BackSwipe.LEFT_TO_RIGHT -> sign > 0
        BackSwipe.BOTH -> true
        BackSwipe.OFF -> false
    }

    Box(
        modifier = modifier.pointerInput(input) {
            val flingVelocity = FlingVelocity.toPx()
            var lockedUntil = 0L

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!isEnabled || mode == BackSwipe.OFF || down.uptimeMillis < lockedUntil) return@awaitEachGesture

                // Скорость считаем с самого касания: у короткого резкого свайпа
                // почти все точки приходятся на этап распознавания направления
                val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }

                // Распознаём направление: начинаем только с явного движения в сторону «назад»
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

                val width = size.width.toFloat()
                // Направление жеста — куда пошёл палец (при «оба» — любое)
                val sign = if (dx < 0) -1f else 1f
                var travel = dx * sign // пройденное в сторону «назад» расстояние, включая этап распознавания
                val edge = if (sign < 0) NavigationEvent.EDGE_RIGHT else NavigationEvent.EDGE_LEFT
                input.backStarted(backEvent(start.position.x, start.position.y, progress(travel, width), edge))

                val lifted = drag(start.id) { change ->
                    travel += change.positionChange().x * sign
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    input.backProgressed(backEvent(change.position.x, change.position.y, progress(travel, width), edge))
                }

                val velocity = tracker.calculateVelocity().x * sign // скорость в сторону «назад»
                val farEnough = travel / width > CompleteThreshold && velocity > -flingVelocity
                val flung = velocity > flingVelocity
                if (lifted && (farEnough || flung)) {
                    input.backCompleted()
                    lockedUntil = currentEvent.changes.first().uptimeMillis + CooldownMillis
                } else {
                    input.backCancelled()
                }
            }
        },
    ) {
        content()
    }
}

private fun progress(travel: Float, width: Float) = (travel / width).coerceIn(0f, 1f)

private fun backEvent(x: Float, y: Float, progress: Float, edge: Int) = NavigationEvent(
    touchX = x,
    touchY = y,
    progress = progress,
    swipeEdge = edge,
)
