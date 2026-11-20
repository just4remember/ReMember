package dev.chaik.remember.feature.games.clicker

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Всплывающее «+N» над местом тапа. */
private class Floater(val id: Long, val at: Offset, val text: String)

/**
 * Кнопка тапа — планета; с каждым уровнем игрока ([level], 1–8) она становится краше:
 * лавовый шар → каменный шар → океаны → трава → города → первый спутник → много спутников
 * → планетарное кольцо.
 * Ловит каждое касание отдельно (можно тапать несколькими пальцами), сжимается, слегка
 * вибрирует и показывает «+N» над пальцем. [onTap] возвращает подпись для всплывающего
 * текста; null — тап ничего не дал (нет энергии).
 */
@Composable
fun PlanetButton(level: Int, onTap: () -> String?, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    val floaters = remember { mutableStateListOf<Floater>() }
    val nextId = remember { longArrayOf(0) }
    val tap by rememberUpdatedState(onTap)
    val shapes = remember { PlanetShapes(Random(11)) }

    val time = rememberInfiniteTransition(label = "planet")
    val orbit by time.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "orbit")
    val pulse by time.animateFloat(0f, 1f, infiniteRepeatable(tween(1_600), RepeatMode.Reverse), label = "pulse")

    BoxWithConstraints(modifier) {
        Canvas(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (change in event.changes.filter { it.changedToDown() }) {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val radius = minOf(size.width, size.height) * PLANET_SHARE / 2f
                                if ((change.position - center).getDistance() > radius) continue
                                change.consume()
                                val label = tap() ?: continue
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                floaters += Floater(nextId[0]++, change.position, label)
                                scope.launch {
                                    scale.animateTo(0.94f, tween(50))
                                    scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 800f))
                                }
                            }
                        }
                    }
                },
        ) {
            drawPlanet(level.coerceIn(1, MAX_STAGE), shapes, orbit, pulse)
        }

        floaters.forEach { f -> key(f.id) { FloatingGain(f) { floaters.remove(f) } } }
    }
}

/** Материки, кратеры и огни городов — одни и те же при каждой отрисовке. */
private class PlanetShapes(rnd: Random) {
    /** Материк: точки контура в долях радиуса. */
    val continents: List<List<Offset>> = listOf(
        Offset(-0.35f, -0.30f) to 0.42f, Offset(0.40f, 0.05f) to 0.34f, Offset(-0.15f, 0.50f) to 0.30f, Offset(0.25f, -0.62f) to 0.20f,
    ).map { (center, size) ->
        val n = 9
        (0 until n).map { i ->
            val a = i * 2 * PI / n
            val r = size * (0.7f + rnd.nextFloat() * 0.5f)
            center + Offset((cos(a) * r).toFloat(), (sin(a) * r * 0.8f).toFloat())
        }
    }
    val craters = List(9) { Triple(rnd.nextFloat() * 1.4f - 0.7f, rnd.nextFloat() * 1.4f - 0.7f, 0.05f + rnd.nextFloat() * 0.09f) }
    val cracks = List(7) { List(4) { Offset(rnd.nextFloat() * 1.8f - 0.9f, rnd.nextFloat() * 1.8f - 0.9f) } }
    /** Огни городов — на материках. */
    val cities: List<Offset> = continents.flatMap { pts ->
        val cx = pts.map { it.x }.average().toFloat()
        val cy = pts.map { it.y }.average().toFloat()
        List(7) { Offset(cx + (rnd.nextFloat() - 0.5f) * 0.35f, cy + (rnd.nextFloat() - 0.5f) * 0.3f) }
    }
    /** Спутники: радиус орбиты (в радиусах планеты), наклон, сдвиг по фазе, скорость. */
    val satellites = List(10) { i -> floatArrayOf(1.25f + (i % 3) * 0.13f, -20f + (i % 3) * 25f, rnd.nextFloat() * 6.28f, 0.7f + rnd.nextFloat() * 0.8f) }
}

private fun smoothPath(points: List<Offset>, c: Offset, r: Float): Path = Path().apply {
    val p = points.map { c + it * r }
    moveTo((p.last().x + p[0].x) / 2, (p.last().y + p[0].y) / 2)
    for (i in p.indices) {
        val next = p[(i + 1) % p.size]
        quadraticTo(p[i].x, p[i].y, (p[i].x + next.x) / 2, (p[i].y + next.y) / 2)
    }
    close()
}

private fun DrawScope.drawPlanet(stage: Int, s: PlanetShapes, orbit: Float, pulse: Float) {
    val r = size.minDimension * PLANET_SHARE / 2f
    val c = center
    val lava = stage == 1
    val water = stage >= 3

    // Свечение: у лавы — горячее и пульсирует, у живой планеты — голубая атмосфера
    val glow = when {
        lava -> Color(0xFFFF6A2B).copy(alpha = 0.30f + 0.15f * pulse)
        stage == 2 -> Color(0xFFBFA88E).copy(alpha = 0.15f)
        else -> Color(0xFF6EC6FF).copy(alpha = 0.32f)
    }
    drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent), center = c, radius = r * 1.38f), radius = r * 1.38f, center = c)

    // Задняя половина кольца и спутники — за планетой
    if (stage >= 8) drawRing(c, r, front = false)
    if (stage >= 6) drawSatellites(s, stage, c, r, orbit, front = false)

    val (light, mid, dark) = when {
        lava -> Triple(Color(0xFFFF9A4A), Color(0xFFB8261A), Color(0xFF2E0704))
        stage == 2 -> Triple(Color(0xFFC9B39A), Color(0xFF7D6651), Color(0xFF241B14))
        else -> Triple(Color(0xFF7CC4F2), Color(0xFF1E62B0), Color(0xFF071F45))
    }
    drawCircle(Brush.radialGradient(listOf(light, mid, dark), center = c + Offset(-r * 0.35f, -r * 0.35f), radius = r * 1.6f), radius = r, center = c)

    val circle = Path().apply { addOval(Rect(c, r)) }
    clipPath(circle) {
        when {
            lava -> {
                // Тёмные остывшие плиты и светящиеся трещины
                s.craters.forEach { (x, y, k) -> drawCircle(Color(0xFF1C0603).copy(alpha = 0.55f), radius = r * k * 1.6f, center = c + Offset(r * x, r * y)) }
                s.cracks.forEach { pts ->
                    val path = Path().apply {
                        moveTo(c.x + pts[0].x * r, c.y + pts[0].y * r)
                        quadraticTo(c.x + pts[1].x * r, c.y + pts[1].y * r, c.x + pts[2].x * r, c.y + pts[2].y * r)
                        quadraticTo(c.x + pts[3].x * r * 0.5f, c.y + pts[3].y * r * 0.5f, c.x + pts[3].x * r, c.y + pts[3].y * r)
                    }
                    drawPath(path, Color(0xFFFFC24A).copy(alpha = 0.65f + 0.3f * pulse), style = Stroke(width = r * 0.035f, cap = StrokeCap.Round))
                }
            }
            stage == 2 -> {
                // Кратеры с тенью и светлой кромкой
                s.craters.forEach { (x, y, k) ->
                    val at = c + Offset(r * x, r * y)
                    drawCircle(Color(0xFF3A2C20).copy(alpha = 0.45f), radius = r * k, center = at)
                    drawCircle(Color(0xFFE3D2BE).copy(alpha = 0.25f), radius = r * k, center = at + Offset(-r * k * 0.25f, -r * k * 0.25f), style = Stroke(width = r * 0.015f))
                }
            }
            water -> {
                // Материки: на 3-м уровне — голая земля, с 4-го — трава с песчаной кромкой
                s.continents.forEach { pts ->
                    val path = smoothPath(pts, c, r)
                    if (stage >= 4) {
                        drawPath(path, Color(0xFFD9C38A), style = Stroke(width = r * 0.05f))
                        drawPath(path, Color(0xFF4E9A45))
                    } else {
                        drawPath(path, Color(0xFF8E6E4C))
                    }
                }
                if (stage >= 4) {
                    // Облака
                    val cloud = Color.White.copy(alpha = 0.33f)
                    listOf(-0.55f to 0.10f, 0.05f to 0.07f, 0.45f to 0.09f).forEach { (y, h) ->
                        drawOval(cloud, topLeft = Offset(c.x - r * 0.95f + r * y * 0.6f, c.y + r * y), size = Size(r * 1.2f, r * h))
                    }
                }
            }
        }
        // Тень с ночной стороны
        drawCircle(
            Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), center = c + Offset(-r * 0.45f, -r * 0.45f), radius = r * 1.9f),
            radius = r,
            center = c,
        )
        if (stage >= 5) {
            // Огни городов — ярче на ночной стороне
            s.cities.forEach { p ->
                val night = ((p.x + p.y) * 0.6f + 0.4f).coerceIn(0.25f, 1f)
                drawCircle(Color(0xFFFFE08A).copy(alpha = 0.9f * night), radius = r * 0.018f, center = c + p * r)
            }
        }
    }

    if (stage >= 8) drawRing(c, r, front = true)
    if (stage >= 6) drawSatellites(s, stage, c, r, orbit, front = true)
}

/** Планетарное кольцо: наклонный эллипс; [front] — нижняя половина, что перед планетой. */
private fun DrawScope.drawRing(c: Offset, r: Float, front: Boolean) {
    val rx = r * 1.55f
    val ry = r * 0.36f
    rotate(-16f, pivot = c) {
        val rect = Rect(Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2))
        val bands = listOf(Color(0xFFE9D3A6).copy(alpha = 0.75f) to 0.085f, Color(0xFFC8A36E).copy(alpha = 0.55f) to 0.04f)
        bands.forEachIndexed { i, (color, width) ->
            val inset = r * 0.07f * i
            drawArc(
                color = color,
                startAngle = if (front) 0f else 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(rect.left + inset, rect.top + inset * 0.25f),
                size = Size(rect.width - inset * 2, rect.height - inset * 0.5f),
                style = Stroke(width = r * width),
            )
        }
    }
}

/** Спутники на наклонных орбитах; [front] — те, что перед планетой (иначе — за ней). */
private fun DrawScope.drawSatellites(s: PlanetShapes, stage: Int, c: Offset, r: Float, orbit: Float, front: Boolean) {
    val count = if (stage >= 7) s.satellites.size else 1
    for (i in 0 until count) {
        val (radius, tilt, phase, speed) = s.satellites[i].let { arrayOf(it[0], it[1], it[2], it[3]) }
        val a = orbit * speed + phase
        val inFront = sin(a) > 0
        val rx = r * radius
        val ry = r * radius * 0.28f
        rotate(tilt, pivot = c) {
            if (!front && i < (if (stage >= 7) 3 else 1)) {
                // Сама орбита — тонкая линия (рисуем один раз, под планетой)
                drawOval(Color.White.copy(alpha = 0.16f), topLeft = Offset(c.x - rx, c.y - ry), size = Size(rx * 2, ry * 2), style = Stroke(width = r * 0.008f))
            }
            if (inFront != front) return@rotate
            val p = Offset(c.x + rx * cos(a), c.y + ry * sin(a))
            val body = r * 0.045f
            // Корпус и две солнечные панели
            drawRect(Color(0xFF3B6FD8), topLeft = Offset(p.x - body * 2.6f, p.y - body * 0.45f), size = Size(body * 1.7f, body * 0.9f))
            drawRect(Color(0xFF3B6FD8), topLeft = Offset(p.x + body * 0.9f, p.y - body * 0.45f), size = Size(body * 1.7f, body * 0.9f))
            drawCircle(Color(0xFFE6E6EA), radius = body * 0.8f, center = p)
        }
    }
}

@Composable
private fun FloatingGain(floater: Floater, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val rise = with(LocalDensity.current) { 72.dp.toPx() }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
        onDone()
    }
    Text(
        text = floater.text,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .offset { IntOffset((floater.at.x - 20.dp.toPx()).roundToInt(), (floater.at.y - 24.dp.toPx() - rise * progress.value).roundToInt()) }
            .graphicsLayer { alpha = 1f - progress.value },
    )
}

/** Какая доля ширины кнопки — сама планета (остальное — свечение и орбиты). */
private const val PLANET_SHARE = 0.62f

/** Сколько стадий у планеты — по числу уровней игрока. */
const val MAX_STAGE = 8
