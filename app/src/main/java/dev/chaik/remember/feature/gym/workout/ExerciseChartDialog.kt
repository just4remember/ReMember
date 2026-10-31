package dev.chaik.remember.feature.gym.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R
import dev.chaik.remember.core.model.ChartRange
import dev.chaik.remember.core.model.ExerciseHistory
import dev.chaik.remember.core.model.ExerciseKind
import dev.chaik.remember.core.model.ProgressPoint
import dev.chaik.remember.core.model.WorkoutSet
import dev.chaik.remember.core.model.formatDuration
import dev.chaik.remember.core.model.formatKg
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Сколько горизонтальных линий сетки. */
private const val GRID_LINES = 3

private val ChartDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/**
 * Окно с графиком упражнения: лучший результат каждой тренировки за выбранный срок.
 * Палец на графике выбирает ближайшую точку — можно вести, не отрывая; над графиком её подходы.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseChartDialog(name: String, kind: ExerciseKind, points: List<ProgressPoint>, onDismiss: () -> Unit) {
    var range by remember { mutableStateOf(ChartRange.ALL) }
    var selected by remember(range) { mutableStateOf<Int?>(null) }
    val valueLabel = chartValueLabel(kind)
    val colors = MaterialTheme.colorScheme
    val shownPoints = ExerciseHistory.within(points, range, LocalDate.now())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.editor_close))
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (shownPoints.isEmpty()) {
                    Text(
                        text = stringResource(if (points.isEmpty()) R.string.gym_chart_empty else R.string.gym_chart_empty_range),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = stringResource(
                            when (kind) {
                                ExerciseKind.WEIGHT_REPS -> R.string.gym_chart_weight
                                ExerciseKind.REPS -> R.string.gym_chart_reps
                                ExerciseKind.TIME -> R.string.gym_chart_time
                                ExerciseKind.SPEED_TIME -> R.string.gym_chart_speed
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    val index = (selected ?: shownPoints.lastIndex).coerceAtMost(shownPoints.lastIndex)
                    val shown = shownPoints[index]
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(valueLabel(shown.value), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(shown.date.format(ChartDate), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    SetsOfDay(shown.sets, shownPoints, kind)
                    ProgressChart(shownPoints, index, valueLabel, onSelect = { selected = it })
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(shownPoints.first().date.format(ChartDate), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        if (shownPoints.size > 1) {
                            Text(shownPoints.last().date.format(ChartDate), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChartRange.entries.forEach { option ->
                        FilterChip(
                            selected = range == option,
                            onClick = { range = option },
                            label = {
                                Text(
                                    stringResource(
                                        when (option) {
                                            ChartRange.MONTH -> R.string.gym_chart_month
                                            ChartRange.QUARTER -> R.string.gym_chart_quarter
                                            ChartRange.YEAR -> R.string.gym_chart_year
                                            ChartRange.ALL -> R.string.gym_chart_all
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {},
    )
}

/**
 * Подходы выбранной тренировки. Место занято под самую длинную тренировку за срок:
 * иначе окно меняло бы высоту, и график уезжал бы из-под пальца.
 */
@Composable
private fun SetsOfDay(sets: List<WorkoutSet>, points: List<ProgressPoint>, kind: ExerciseKind) {
    val kmh = stringResource(R.string.unit_kmh)
    fun labels(list: List<WorkoutSet>) = list.map { it.format(kind, kmh) }
    val longest = points.maxBy { point -> labels(point.sets).sumOf { it.length + 2 } }
    Box(Modifier.fillMaxWidth()) {
        SetPills(labels(longest.sets), Modifier.alpha(0f))
        SetPills(labels(sets))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetPills(labels: List<String>, modifier: Modifier = Modifier) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Подпись значения: «60 кг», «12», «1:30», «9,4 км/ч». */
@Composable
private fun chartValueLabel(kind: ExerciseKind): (Double) -> String {
    val kg = stringResource(R.string.gym_chart_kg)
    val kmh = stringResource(R.string.unit_kmh)
    return { value ->
        when (kind) {
            ExerciseKind.WEIGHT_REPS -> "${formatKg(value)} $kg"
            ExerciseKind.REPS -> value.toInt().toString()
            ExerciseKind.TIME -> formatDuration(value.toInt())
            ExerciseKind.SPEED_TIME -> "${formatKg(value)} $kmh"
        }
    }
}

@Composable
private fun ProgressChart(points: List<ProgressPoint>, selected: Int, label: (Double) -> String, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    // Ровный результат — линия посередине, а не деление на ноль
    val top = points.maxOf { it.value }.let { max -> if (max == points.minOf { it.value }) max + 1 else max }
    val bottom = points.minOf { it.value }.let { min -> if (top == min + 1 && points.all { it.value == min }) min - 1 else min }

    fun Float.xOf(index: Int, left: Float): Float =
        if (points.size == 1) (left + this) / 2 else left + (this - left) * index / points.lastIndex

    var chartLeft by remember { mutableStateOf(0f) }
    var chartRight by remember { mutableStateOf(0f) }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(points) {
                var last = -1
                fun pick(x: Float) {
                    val nearest = points.indices.minBy { abs(chartRight.xOf(it, chartLeft) - x) }
                    if (nearest != last) {
                        last = nearest
                        onSelect(nearest)
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    last = -1
                    pick(down.position.x)
                    // Ведём не отрывая пальца: жест не отдаём прокрутке окна
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        pick(change.position.x)
                    }
                }
            },
    ) {
        val inset = 8.dp.toPx()
        val labels = List(GRID_LINES) { i -> bottom + (top - bottom) * i / (GRID_LINES - 1) }
        val measured = labels.map { measurer.measure(label(it), labelStyle) }
        val left = measured.maxOf { it.size.width } + 8.dp.toPx()
        val right = size.width - inset
        chartLeft = left
        chartRight = right
        val textHeight = measured.first().size.height.toFloat()
        val chartTop = textHeight / 2
        val chartBottom = size.height - textHeight / 2
        fun yOf(value: Double) = chartBottom - ((value - bottom) / (top - bottom)).toFloat() * (chartBottom - chartTop)

        labels.forEachIndexed { i, value ->
            val y = yOf(value)
            drawLine(colors.outlineVariant, Offset(left, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            drawText(measured[i], topLeft = Offset(0f, y - measured[i].size.height / 2f))
        }
        val positions = points.mapIndexed { i, point -> Offset(right.xOf(i, left), yOf(point.value)) }
        val line = Path().apply {
            positions.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
        positions.getOrNull(selected)?.let { p ->
            drawLine(colors.outline, Offset(p.x, chartTop), Offset(p.x, chartBottom), strokeWidth = 1.dp.toPx())
        }
        drawPath(line, colors.primary, style = Stroke(width = 2.5.dp.toPx()))
        positions.forEachIndexed { i, p ->
            drawCircle(colors.primary, radius = if (i == selected) 6.dp.toPx() else 3.dp.toPx(), center = p)
        }
    }
}
