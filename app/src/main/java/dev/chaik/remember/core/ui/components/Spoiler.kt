package dev.chaik.remember.core.ui.components

import dev.chaik.remember.core.settings.SpoilerStyle
import dev.chaik.remember.core.settings.LocalAppSettings
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Спойлер: вместо скрытого текста — мерцающая «пыль» из мелких дрейфующих точек (как в
 * Телеграме) или сплошная полоса. Сам текст не рисуется вовсе, поэтому очертаний букв не
 * видно (размытие их оставляло).
 */

/** Время спойлера: доля круга 0..1, по кругу за [SPOILER_PERIOD_MS]. Читать — в фазе рисования. */
@Composable
fun rememberSpoilerTime(): State<Float> =
    rememberInfiniteTransition(label = "spoiler").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SPOILER_PERIOD_MS, easing = LinearEasing)),
        label = "spoilerTime",
    )

/**
 * Скрыть содержимое спойлером ([hidden]): вместо него — частицы или полоса цвета текста
 * (вид — в «Персонализации»). Скрытое не читается и вслух (у экранного диктора).
 */
@Composable
fun Modifier.spoiler(hidden: Boolean): Modifier {
    if (!hidden) return this
    val style = LocalAppSettings.current.spoilerStyle
    val time = if (style == SpoilerStyle.PARTICLES) rememberSpoilerTime() else null
    val color = LocalContentColor.current
    return clearAndSetSemantics {}.drawWithContent {
        drawSpoiler(Rect(Offset.Zero, size), time?.value ?: 0f, color, seed = 1, style = style)
    }
}

/**
 * Сплошная полоса — как в рассекреченных документах: строка закрыта лентой цвета текста.
 * Сверху и снизу — небольшой зазор, чтобы соседние строки не слипались в один блок.
 */
private fun DrawScope.drawSpoilerBar(rect: Rect, color: Color) {
    val inset = rect.height * 0.1f
    drawRoundRect(
        color = color.copy(alpha = color.alpha * 0.9f),
        topLeft = Offset(rect.left, rect.top + inset),
        size = Size(rect.width, rect.height - inset * 2),
        cornerRadius = CornerRadius(2.dp.toPx()),
    )
}

/** Псевдослучайное 0..1 — одно и то же для одной частицы в каждом кадре. */
private fun hash(i: Int, salt: Int): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777215f
}

/**
 * Нарисовать спойлер в прямоугольнике [rect] (например, строке): «пыль» или, если [style] —
 * полоса, сплошную ленту. [time] — из [rememberSpoilerTime], [seed] — чтобы соседние
 * прямоугольники не мерцали одинаково.
 */
fun DrawScope.drawSpoiler(rect: Rect, time: Float, color: Color, seed: Int, style: SpoilerStyle = SpoilerStyle.PARTICLES) {
    if (rect.width <= 0f || rect.height <= 0f) return
    if (style == SpoilerStyle.BAR) return drawSpoilerBar(rect, color)
    val cell = 3.2.dp.toPx()
    val count = min(4000, (rect.width * rect.height / (cell * cell)).toInt().coerceAtLeast(8))
    val drift = 1.6.dp.toPx()
    val dot = 1.4.dp.toPx()
    // Четыре яркости — четыре вызова рисования вместо тысяч
    val buckets = Array(BUCKETS) { ArrayList<Offset>(count / BUCKETS + 1) }
    for (i in 0 until count) {
        val baseX = rect.left + hash(i, seed) * rect.width
        val baseY = rect.top + hash(i, seed + 101) * rect.height
        // Целые множители времени — движение замыкается в круг без скачка
        val kx = 1 + (hash(i, seed + 202) * 3).toInt()
        val ky = 1 + (hash(i, seed + 303) * 3).toInt()
        val a = 2 * PI * (kx * time + hash(i, seed + 404))
        val b = 2 * PI * (ky * time + hash(i, seed + 505))
        val x = baseX + (cos(a) * drift).toFloat()
        val y = baseY + (sin(b) * drift).toFloat()
        val twinkle = abs(sin(PI * ((1 + (hash(i, seed + 606) * 2).toInt()) * time + hash(i, seed + 707)))).toFloat()
        buckets[(twinkle * (BUCKETS - 0.001f)).toInt()] += Offset(x, y)
    }
    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
        buckets.forEachIndexed { level, points ->
            if (points.isEmpty()) return@forEachIndexed
            drawPoints(
                points = points,
                pointMode = PointMode.Points,
                color = color.copy(alpha = color.alpha * (0.2f + 0.8f * (level + 1) / BUCKETS)),
                strokeWidth = dot,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Что стоит под спойлером вместо скрытого значения (IP, результат): спойлер всегда одной
 * длины — по нему не угадать, длинное значение или короткое.
 */
const val SPOILER_PLACEHOLDER = "00000000"

private const val BUCKETS = 4
private const val SPOILER_PERIOD_MS = 6_000
