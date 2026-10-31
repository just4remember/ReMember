package dev.chaik.remember.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chaik.remember.core.model.Rating
import kotlin.math.abs

/** Цвет заполненных звёзд. */
val StarColor = Color(0xFFFFC107)

/** Сколько надо тапнуть по последней звезде при максимуме, чтобы открыть секрет. */
private const val SECRET_TAPS = 3
private val StarSpacing = 2.dp

/** Доля первой звезды слева, тап или слайд в которую ставит 0 звёзд. */
private const val ZERO_ZONE = 0.25f
private const val SECRET_TAP_WINDOW_MS = 700L

/**
 * Оценка звёздами. Тап по левой половине звезды — половинка, по правой — целая;
 * можно вести пальцем по звёздам — шкала заполняется следом.
 * Левый край первой звезды (или слайд левее неё) — 0 звёзд: [onChange] получает 0,
 * это значит «оценки нет».
 *
 * Значение — в половинках звезды ([Rating]): 19 из [stars] = 10 → 9.5 звезды.
 * Если [secret] = true: при максимуме три быстрых тапа по последней звезде
 * дают [Rating.SECRET] (11 из 10) — звёзды переливаются радугой.
 */
@Composable
fun StarRating(
    halves: Int?,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    stars: Int = Rating.DAY_STARS,
    allowHalf: Boolean = true,
    secret: Boolean = false,
    starSize: Dp = 28.dp,
    /** false — оценку видно, но нажатия и свайпы её не меняют. */
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val max = stars * 2
    val value = halves ?: 0
    val isSecret = secret && Rating.isSecret(halves)
    val current by rememberUpdatedState(value)

    // Счётчик тапов по последней звезде для секрета
    var secretTaps by remember { mutableIntStateOf(0) }
    var lastSecretTap by remember { mutableLongStateOf(0L) }

    // Радуга для 11 из 10: оттенок плывёт по кругу. Анимация живёт только при секрете —
    // иначе бесконечная анимация заставляла экран перерисовываться всё время
    val hueShift = if (isSecret) rainbowHue() else 0f
    val pulse by animateFloatAsState(
        targetValue = if (isSecret) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow),
        label = "pulse",
    )

    /** Тап: как раньше + счётчик для секрета. */
    fun onTap(tapped: Int, time: Long) {
        if (secret && tapped >= max - 1 && current >= max) {
            // Тап по последней звезде, когда 10 из 10 уже стоит
            secretTaps = if (time - lastSecretTap <= SECRET_TAP_WINDOW_MS) secretTaps + 1 else 1
            lastSecretTap = time
            if (secretTaps >= SECRET_TAPS && current == max) {
                secretTaps = 0
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onChange(Rating.SECRET)
                return
            }
        } else {
            secretTaps = 0
        }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        onChange(tapped)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(StarSpacing),
        modifier = modifier.pointerInput(stars, allowHalf, starSize, enabled) {
            if (!enabled) return@pointerInput
            val cell = (starSize + StarSpacing).toPx()
            val star = starSize.toPx()

            /** Сколько половинок соответствует точке под пальцем. */
            fun valueAt(x: Float): Int {
                // Левая четверть первой звезды (и всё левее) — 0, то есть «без оценки»
                if (x < star * ZERO_ZONE) return 0
                val index = (x / cell).toInt().coerceIn(0, stars - 1)
                val leftHalf = (x - index * cell) < star / 2
                val raw = if (allowHalf && leftHalf) index * 2 + 1 else index * 2 + 2
                return raw.coerceIn(if (allowHalf) 1 else 2, max)
            }

            // Тап — выставить; ведёшь пальцем по звёздам — шкала заполняется следом.
            // Вертикальное движение отдаём прокрутке страницы, горизонтальное забираем
            // себе, чтобы при слайде не перелистывались вкладки.
            awaitEachGesture {
                val down = awaitFirstDown()
                val slop = viewConfiguration.touchSlop
                var dx = 0f
                var dy = 0f
                var dragging = false
                var last = current
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (!dragging) onTap(valueAt(down.position.x), change.uptimeMillis)
                        break
                    }
                    val delta = change.positionChange()
                    dx += delta.x
                    dy += delta.y
                    if (!dragging) {
                        if (abs(dy) > slop && abs(dy) > abs(dx)) break
                        if (abs(dx) > slop) {
                            dragging = true
                            secretTaps = 0
                        }
                    }
                    if (dragging) {
                        change.consume()
                        val value = valueAt(change.position.x)
                        if (value != last) {
                            last = value
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onChange(value)
                        }
                    }
                }
            }
        },
    ) {
        repeat(stars) { index ->
            val filled = value - index * 2 // 2 — целая звезда, 1 — половинка
            val icon = when {
                isSecret || filled >= 2 -> Icons.Filled.Star
                filled == 1 -> Icons.AutoMirrored.Filled.StarHalf
                else -> Icons.Outlined.StarBorder
            }
            val tint = when {
                isSecret -> Color.hsv((hueShift + index * 36f) % 360f, 0.75f, 1f)
                filled >= 1 -> StarColor
                else -> MaterialTheme.colorScheme.outline
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(starSize)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    },
            )
        }
    }
}

/** Плывущий по кругу оттенок (0…360) для радужных звёзд. */
@Composable
private fun rainbowHue(): Float {
    val hue by rememberInfiniteTransition(label = "rainbow").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Restart),
        label = "hue",
    )
    return hue
}
