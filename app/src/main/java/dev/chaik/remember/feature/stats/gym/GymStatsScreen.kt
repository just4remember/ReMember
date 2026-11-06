package dev.chaik.remember.feature.stats.gym

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.model.ExerciseHistory
import dev.chaik.remember.core.model.ExerciseKind
import dev.chaik.remember.core.model.formatDuration
import dev.chaik.remember.core.model.formatKg
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.settings.logicalToday
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.core.ui.components.daySwipeOffset
import dev.chaik.remember.core.ui.components.rememberDaySwipe
import dev.chaik.remember.feature.gym.workout.ExerciseChartDialog
import dev.chaik.remember.feature.stats.HeatmapLegend
import dev.chaik.remember.feature.stats.PeriodChips
import dev.chaik.remember.feature.stats.PeriodDates
import dev.chaik.remember.feature.stats.StatTile
import dev.chaik.remember.feature.stats.StatsCard
import dev.chaik.remember.feature.stats.YearHeatmap
import dev.chaik.remember.feature.stats.YearSwitcher
import dev.chaik.remember.feature.stats.onTapAnywhere
import dev.chaik.remember.feature.stats.sleep.AXIS_WIDTH
import dev.chaik.remember.feature.stats.sleep.Caption
import dev.chaik.remember.feature.stats.sleep.RangeLabels
import dev.chaik.remember.feature.stats.sleep.StatsPeriod
import dev.chaik.remember.feature.stats.sleep.TileRow
import dev.chaik.remember.feature.stats.sleep.fullWidth
import dev.chaik.remember.feature.stats.sleep.withUnit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlinx.serialization.Serializable

@Serializable
data object GymStatsRoute : NavKey

/** Раньше этой даты статистику не листаем. */
private val EARLIEST: LocalDate = LocalDate.of(2000, 1, 1)

// Каждый раз заново: после смены языка приложения даты должны быть на новом языке
private val DayDate: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
// Каждый раз заново: после смены языка приложения даты должны быть на новом языке
private val ShortDate: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
// Каждый раз заново: после смены языка приложения даты должны быть на новом языке
private val MonthName: DateTimeFormatter get() = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())

/**
 * Статистика зала: число тренировок, их частота и длительность, вес тела, упражнения
 * (тап — график прогресса, как в зале), рекорды периода, дни недели и час начала, год тепловой картой.
 */
@Composable
fun GymStatsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val settings = LocalAppSettings.current
    val today = settings.logicalToday()
    var period by rememberSaveable { mutableStateOf(StatsPeriod.MONTH) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var year by rememberSaveable { mutableIntStateOf(today.year) }

    val all by remember { container.workoutRepository.observeAllFull() }.collectAsStateWithLifecycle(emptyList())
    val first = all.minOfOrNull { it.date }
    val range = period.range(today, offset, first, settings.firstDayOfWeek)
    val summary = remember(all, range, today) { GymStats.summary(all, range, today) }
    val counts = remember(all, range) { GymStats.counts(all, range, settings.firstDayOfWeek) }

    var selectedCount by remember(range) { mutableStateOf<Int?>(null) }
    var selectedWeight by remember(range) { mutableStateOf<Int?>(null) }
    var selectedMapDay by remember(year) { mutableStateOf<LocalDate?>(null) }
    var chartExercise by remember { mutableStateOf<ExerciseUse?>(null) }

    val canEarlier = period != StatsPeriod.ALL && range.start.isAfter(EARLIEST)
    val canLater = period != StatsPeriod.ALL && offset > 0
    fun earlier() {
        if (canEarlier) offset++
    }
    fun later() {
        if (canLater) offset--
    }
    // Свайп по строке с датами листает период; едет за пальцем только дата
    val (dateSwipe, dateGesture) = rememberDaySwipe(canGoForward = canLater) { s -> if (s < 0) earlier() else later() }
    val (yearSwipe, yearGesture) = rememberDaySwipe(canGoForward = year < today.year) { s ->
        year = (year + s.toInt()).coerceIn(EARLIEST.year, today.year)
    }
    val hoursUnit = stringResource(R.string.stats_unit_h)
    val minUnit = stringResource(R.string.stats_min)
    val kgUnit = stringResource(R.string.gym_chart_kg)

    ScreenScaffold(title = stringResource(R.string.stats_gym), modifier = modifier, onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .onTapAnywhere {
                    selectedCount = null
                    selectedWeight = null
                    selectedMapDay = null
                }
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 24.dp)
                .padding(horizontal = 16.dp),
        ) {
            PeriodChips(period) {
                period = it
                offset = 0
            }
            // Свайп по строке с датами забирает жест себе («назад» не срабатывает), ловится до краёв экрана
            Box(Modifier.fullWidth(16.dp).then(dateGesture).padding(horizontal = 16.dp)) {
                Box(Modifier.daySwipeOffset(dateSwipe)) {
                    PeriodDates(range, canEarlier, canLater, onEarlier = ::earlier, onLater = ::later)
                }
            }

            if (summary.workouts.isEmpty()) {
                Text(
                    stringResource(R.string.stats_sleep_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            } else {
                val average = stringResource(R.string.stats_average)
                TileRow {
                    StatTile(stringResource(R.string.stats_gym_workouts), summary.workouts.size.toString(), it)
                    StatTile("${stringResource(R.string.stats_gym_per_week)} $average", "%.1f".format(summary.perWeek), it)
                }
                TileRow {
                    StatTile(
                        "${stringResource(R.string.stats_gym_duration)} $average",
                        summary.averageMinutes?.let { m -> hm(m, hoursUnit, minUnit) } ?: withUnit("-", ""),
                        it,
                    )
                    StatTile(stringResource(R.string.stats_gym_total_time), hm(summary.totalMinutes, hoursUnit, minUnit), it)
                }
                summary.weights.lastOrNull()?.let { last ->
                    TileRow {
                        val change = last.kg - summary.weights.first().kg
                        StatTile(
                            stringResource(R.string.stats_gym_body_weight),
                            withUnit(formatKg(last.kg), " $kgUnit"),
                            it,
                            note = if (summary.weights.size > 1) {
                                stringResource(
                                    R.string.stats_gym_weight_change,
                                    (if (change > 0) "+" else if (change < 0) "−" else "") + formatKg(abs(change)),
                                    kgUnit,
                                )
                            } else {
                                null
                            },
                        )
                    }
                }

                StatsCard(stringResource(R.string.stats_gym_frequency)) {
                    val weekly = counts.size > 1 && counts[0].end.toEpochDay() - counts[0].start.toEpochDay() < 7
                    val point = selectedCount?.let(counts::getOrNull)
                    Caption(
                        point?.let {
                            val label = if (weekly || it.start.dayOfMonth != 1) "${it.start.format(ShortDate)} - ${it.end.format(ShortDate)}" else it.start.format(MonthName)
                            stringResource(R.string.stats_gym_frequency_selected, label, it.count)
                        } ?: stringResource(if (weekly) R.string.stats_gym_frequency_weeks else R.string.stats_gym_frequency_months),
                    )
                    CountChart(counts.map { it.count }, selectedCount) { selectedCount = it }
                    RangeLabels(range)
                }

                if (summary.weights.size > 1) {
                    StatsCard(stringResource(R.string.stats_gym_body_weight)) {
                        val point = selectedWeight?.let(summary.weights::getOrNull) ?: summary.weights.last()
                        Caption("${point.date.format(DayDate)}: ${formatKg(point.kg)} $kgUnit")
                        WeightChart(summary.weights.map { it.kg }, selectedWeight) { selectedWeight = it }
                        RangeLabels(summary.weights.first().date..summary.weights.last().date)
                    }
                }

                StatsCard(stringResource(R.string.stats_gym_exercises)) {
                    Caption(stringResource(R.string.stats_gym_exercises_hint))
                    ExerciseBars(summary.exercises) { chartExercise = it }
                }

                StatsCard(stringResource(R.string.stats_gym_records)) {
                    if (summary.records.isEmpty()) {
                        Caption(stringResource(R.string.stats_gym_records_empty))
                    } else {
                        val unitKmh = stringResource(R.string.unit_kmh)
                        val unitKm = stringResource(R.string.unit_km)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            summary.records.forEach { record ->
                                fun value(v: Double) = when (record.exercise.kind) {
                                    ExerciseKind.WEIGHT_REPS, ExerciseKind.WEIGHT_TIME -> "${formatKg(v)} $kgUnit"
                                    ExerciseKind.REPS -> v.toInt().toString()
                                    ExerciseKind.TIME -> formatDuration(v.toInt())
                                    ExerciseKind.SPEED_TIME -> "${formatKg(v)} $unitKmh"
                                    ExerciseKind.DISTANCE_TIME, ExerciseKind.DISTANCE -> "${formatKg(v)} $unitKm"
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        record.exercise.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    )
                                    Text("${value(record.before)} → ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(value(record.best), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        " · ${record.date.format(ShortDate)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                StatsCard(stringResource(R.string.stats_gym_when)) {
                    Caption(stringResource(R.string.stats_gym_weekdays_hint))
                    WeekdayCounts(summary.byWeekday, settings.firstDayOfWeek)
                    val peak = summary.startHours.indices.maxByOrNull { summary.startHours[it] }?.takeIf { summary.startHours[it] > 0 }
                    Caption(
                        peak?.let { stringResource(R.string.stats_gym_hours_peak, it, it + 1) } ?: stringResource(R.string.stats_no_data),
                    )
                    HourCounts(summary.startHours)
                }
            }

            StatsCard(stringResource(R.string.stats_gym_year)) {
                Box(Modifier.fillMaxWidth().then(yearGesture)) {
                    Box(Modifier.daySwipeOffset(yearSwipe)) {
                        YearSwitcher(
                            year = year,
                            canEarlier = year > EARLIEST.year,
                            canLater = year < today.year,
                            onYear = { year = it.coerceIn(EARLIEST.year, today.year) },
                        )
                    }
                }
                val ofYear = remember(all, year) { all.filter { it.date.year == year } }
                val byDate = remember(ofYear) { ofYear.groupBy { it.date } }
                val averageMinutes = remember(ofYear) { ofYear.mapNotNull(GymStats::minutes).takeIf { it.isNotEmpty() }?.average()?.toInt() }
                val picked = selectedMapDay
                Caption(
                    when {
                        picked != null -> "${picked.format(DayDate)}: " + (
                            byDate[picked]?.joinToString(", ") { w ->
                                "№${w.number}" + (GymStats.minutes(w)?.let { m -> ", ${(m / 60)} $hoursUnit ${(m % 60)} $minUnit" } ?: "")
                            } ?: stringResource(R.string.stats_gym_no_workout)
                            )
                        ofYear.isNotEmpty() -> stringResource(R.string.stats_gym_year_summary, ofYear.size)
                        else -> stringResource(R.string.stats_gym_year_empty)
                    },
                )
                val levels = listOf(0.4f, 0.7f, 1f).map { MaterialTheme.colorScheme.primary.copy(alpha = it) }
                YearHeatmap(
                    year = year,
                    today = today,
                    colorFor = { date ->
                        byDate[date]?.let { list -> levels[GymStats.level(list.mapNotNull(GymStats::minutes).maxOrNull(), averageMinutes) - 1] }
                    },
                    selected = picked,
                    onSelect = { selectedMapDay = it },
                )
                HeatmapLegend(stringResource(R.string.stats_gym_shorter), stringResource(R.string.stats_gym_longer), levels)
            }
        }
    }

    chartExercise?.let { use ->
        val points = remember(all, use) { ExerciseHistory.progress(GymStats.history(all, use.exercise.id), use.exercise.kind) }
        ExerciseChartDialog(use.exercise.name, use.exercise.kind, points, onDismiss = { chartExercise = null })
    }
}

/** «1ч 48мин»: единицы мельче и бледнее, вплотную к числу. */
@Composable
private fun hm(minutes: Int, h: String, m: String) =
    withUnit("${minutes / 60}", h).let { it + withUnit(" ${minutes % 60}", m) }

/** Сколько тренировок в шаг: столбцы в акценте, число над столбцом. Тап — шаг. */
@Composable
private fun CountChart(counts: List<Int>, selected: Int?, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val valueStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurface)
    val axisPx = with(LocalDensity.current) { AXIS_WIDTH.dp.toPx() }
    val max = (counts.maxOrNull() ?: 1).coerceAtLeast(1)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .pointerInput(counts) {
                fun pick(x: Float) = ((x - axisPx) / ((size.width - axisPx) / counts.size)).toInt().coerceIn(0, counts.lastIndex)
                detectTapGestures { onSelect(pick(it.x)) }
            }
            .pointerInput(counts) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onSelect(((change.position.x - axisPx) / ((size.width - axisPx) / counts.size)).toInt().coerceIn(0, counts.lastIndex))
                }
            },
    ) {
        val valueHeight = 16.dp.toPx()
        val chartHeight = size.height - valueHeight
        drawLine(colors.outlineVariant, Offset(axisPx, size.height), Offset(size.width, size.height), strokeWidth = 1f)
        val slot = (size.width - axisPx) / counts.size
        val width = (slot * 0.6f).coerceAtLeast(2f)
        counts.forEachIndexed { i, count ->
            if (count == 0) return@forEachIndexed
            val barHeight = count.toFloat() / max * chartHeight
            val top = size.height - barHeight
            val alpha = if (selected == null || selected == i) 1f else 0.35f
            drawRoundRect(colors.primary.copy(alpha = alpha), Offset(axisPx + slot * i + (slot - width) / 2, top), Size(width, barHeight), CornerRadius(width / 4))
            if (slot > 14.dp.toPx()) {
                val text = measurer.measure(count.toString(), valueStyle)
                drawText(text, topLeft = Offset(axisPx + slot * i + (slot - text.size.width) / 2, top - text.size.height))
            }
        }
    }
}

/** Вес тела по тренировкам: линия в акценте с точками, ось — от минимума до максимума. */
@Composable
private fun WeightChart(weights: List<Double>, selected: Int?, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val axisPx = with(LocalDensity.current) { AXIS_WIDTH.dp.toPx() }
    val low = (weights.min() - 0.5)
    val high = (weights.max() + 0.5)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .pointerInput(weights) {
                fun pick(x: Float) = (((x - axisPx) / (size.width - axisPx)) * (weights.size - 1) + 0.5f).toInt().coerceIn(0, weights.lastIndex)
                detectTapGestures { onSelect(pick(it.x)) }
            }
            .pointerInput(weights) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onSelect((((change.position.x - axisPx) / (size.width - axisPx)) * (weights.size - 1) + 0.5f).toInt().coerceIn(0, weights.lastIndex))
                }
            },
    ) {
        fun x(i: Int) = axisPx + (size.width - axisPx) * i / (weights.size - 1).coerceAtLeast(1)
        fun y(v: Double) = ((high - v) / (high - low) * size.height).toFloat()
        listOf(high, (high + low) / 2, low).forEach { v ->
            val yy = y(v)
            drawLine(colors.outlineVariant, Offset(axisPx, yy), Offset(size.width, yy), strokeWidth = 1f)
            val label = measurer.measure(formatKg(Math.round(v * 10) / 10.0), labelStyle)
            drawText(label, topLeft = Offset(0f, (yy - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)))
        }
        val path = Path()
        weights.forEachIndexed { i, w -> if (i == 0) path.moveTo(x(i), y(w)) else path.lineTo(x(i), y(w)) }
        drawPath(path, colors.primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        weights.forEachIndexed { i, w ->
            val big = selected == i || (selected == null && i == weights.lastIndex)
            drawCircle(colors.primary, radius = (if (big) 5 else 3).dp.toPx(), center = Offset(x(i), y(w)))
        }
    }
}

/** Забирает остаток прокрутки и разгона, чтобы он не уходил странице. */
private val KeepScroll = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

private val ExerciseRow = 40.dp
private const val VISIBLE_EXERCISES = 5

/** Упражнения полосами по числу тренировок; тап — график прогресса. Видно 5, дальше — прокрутка внутри. */
@Composable
private fun ExerciseBars(uses: List<ExerciseUse>, onOpen: (ExerciseUse) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val max = uses.maxOfOrNull { it.workouts }?.coerceAtLeast(1) ?: 1
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = ExerciseRow * VISIBLE_EXERCISES + 4.dp * (VISIBLE_EXERCISES - 1))
            .nestedScroll(KeepScroll)
            .verticalScroll(rememberScrollState()),
    ) {
        uses.forEach { use ->
            Column(
                Modifier
                    .height(ExerciseRow)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpen(use) },
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        use.exercise.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    Text(
                        stringResource(R.string.stats_gym_exercise_counts, use.workouts, use.sets),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                Row(Modifier.padding(top = 3.dp)) {
                    val fraction = use.workouts.toFloat() / max
                    Box(Modifier.weight(fraction.coerceAtLeast(0.01f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.primary))
                    if (fraction < 1f) Spacer(Modifier.weight(1f - fraction))
                }
            }
        }
    }
}

/** Сколько тренировок по дням недели. */
@Composable
private fun WeekdayCounts(byWeekday: Map<DayOfWeek, Int>, firstDay: DayOfWeek) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val valueStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurface)
    val order = (0..6).map { firstDay.plus(it.toLong()) }
    val names = order.map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    val max = (byWeekday.values.maxOrNull() ?: 1).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        val labelHeight = 18.dp.toPx()
        val valueHeight = 16.dp.toPx()
        val chartHeight = size.height - labelHeight - valueHeight
        val slot = size.width / 7
        val width = slot * 0.55f
        order.forEachIndexed { i, day ->
            val value = byWeekday[day] ?: 0
            if (value > 0) {
                val barHeight = value.toFloat() / max * chartHeight
                val top = valueHeight + chartHeight - barHeight
                drawRoundRect(colors.primary, Offset(slot * i + (slot - width) / 2, top), Size(width, barHeight), CornerRadius(8f, 8f))
                val text = measurer.measure(value.toString(), valueStyle)
                drawText(text, topLeft = Offset(slot * i + (slot - text.size.width) / 2, top - text.size.height))
            }
            val label = measurer.measure(names[i], labelStyle)
            drawText(label, topLeft = Offset(slot * i + (slot - label.size.width) / 2, size.height - label.size.height))
        }
    }
}

/** Во сколько начинались тренировки: 24 столбца, подписи 0, 6, 12, 18. */
@Composable
private fun HourCounts(hours: IntArray) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val max = (hours.maxOrNull() ?: 1).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val labelHeight = 18.dp.toPx()
        val chartHeight = size.height - labelHeight
        val slot = size.width / 24
        val width = slot * 0.66f
        hours.forEachIndexed { hour, count ->
            if (count > 0) {
                val barHeight = count.toFloat() / max * chartHeight
                drawRoundRect(colors.primary, Offset(slot * hour + (slot - width) / 2, chartHeight - barHeight), Size(width, barHeight), CornerRadius(width / 2))
            }
            if (hour % 6 == 0) {
                val label = measurer.measure(hour.toString(), labelStyle)
                drawText(label, topLeft = Offset(slot * hour + (slot - label.size.width) / 2, size.height - label.size.height))
            }
        }
    }
}
