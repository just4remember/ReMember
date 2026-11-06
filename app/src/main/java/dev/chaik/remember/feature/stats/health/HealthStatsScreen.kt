package dev.chaik.remember.feature.stats.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.health.DEFAULT_WEIGHT_KG
import dev.chaik.remember.core.health.DayActivity
import dev.chaik.remember.core.health.HealthAvailability
import dev.chaik.remember.core.health.HealthGoals
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.settings.logicalToday
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.core.ui.components.daySwipeOffset
import dev.chaik.remember.core.ui.components.rememberDaySwipe
import dev.chaik.remember.feature.stats.HeatmapLegend
import dev.chaik.remember.feature.stats.PeriodChips
import dev.chaik.remember.feature.stats.PeriodDates
import dev.chaik.remember.feature.stats.StatTile
import dev.chaik.remember.feature.stats.StatsCard
import dev.chaik.remember.feature.stats.YearHeatmap
import dev.chaik.remember.feature.stats.YearSwitcher
import dev.chaik.remember.feature.stats.nearestWithin
import dev.chaik.remember.feature.stats.onTapAnywhere
import dev.chaik.remember.feature.stats.sleep.AXIS_WIDTH
import dev.chaik.remember.feature.stats.sleep.Caption
import dev.chaik.remember.feature.stats.sleep.RangeLabels
import dev.chaik.remember.feature.stats.sleep.StatsPeriod
import dev.chaik.remember.feature.stats.sleep.TileRow
import dev.chaik.remember.feature.stats.sleep.fullWidth
import dev.chaik.remember.feature.stats.sleep.withUnit
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.serialization.Serializable

@Serializable
data object HealthStatsRoute : NavKey

/** Раньше этой даты статистику не листаем. */
private val EARLIEST: LocalDate = LocalDate.of(2000, 1, 1)

/** Без отдельного разрешения Health Connect отдаёт историю только за столько дней. */
private const val FREE_HISTORY_DAYS = 30L

// Каждый раз заново: после смены языка приложения даты должны быть на новом языке
private val DayDate: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
// Каждый раз заново: после смены языка приложения даты должны быть на новом языке
private val ShortDate: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/** Что удалось прочитать из Health Connect. */
private sealed interface HealthLoad {
    data object Loading : HealthLoad
    data object Unavailable : HealthLoad
    data object NoAccess : HealthLoad
    data object Failed : HealthLoad
    data class Ready(val days: Map<LocalDate, DayActivity>, val hours: LongArray, val needsHistory: Boolean) : HealthLoad
}

/**
 * Статистика здоровья: средние шаги, активность и калории, выполнение целей, рекорд, график по дням
 * с переключателем показателя, дни недели, шаги по часам суток и год тепловой картой по цели шагов.
 */
@Composable
fun HealthStatsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val settings = LocalAppSettings.current
    val health = container.health
    val today = settings.logicalToday()
    val goals by container.healthGoals.goals.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf(StatsPeriod.MONTH) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    var metric by rememberSaveable { mutableStateOf(HealthMetric.STEPS) }
    // Увеличивается после выдачи доступа — данные перечитываются
    var grants by remember { mutableIntStateOf(0) }
    val askPermission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { grants++ }

    // «Всё время» — с первого дня, где в Health Connect есть шаги
    val first by produceState<LocalDate?>(null, grants) {
        value = runCatching {
            if (health.availability == HealthAvailability.AVAILABLE && health.hasPermissions()) health.firstStepsDate(EARLIEST) else null
        }.getOrNull()
    }
    val range = period.range(today, offset, first, settings.firstDayOfWeek)
    val load by produceState<HealthLoad>(HealthLoad.Loading, range, grants) {
        value = HealthLoad.Loading
        value = when {
            health.availability != HealthAvailability.AVAILABLE -> HealthLoad.Unavailable
            !runCatching { health.hasPermissions() }.getOrDefault(false) -> HealthLoad.NoAccess
            else -> try {
                val weight = container.workoutRepository.lastBodyWeight() ?: DEFAULT_WEIGHT_KG
                val end = range.endInclusive.coerceAtMost(today)
                val needsHistory = range.start.isBefore(today.minusDays(FREE_HISTORY_DAYS)) &&
                    health.historyReadSupported() && !health.hasHistoryPermission()
                HealthLoad.Ready(health.days(range.start, end, weight), health.stepsByHour(range.start, end), needsHistory)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("HealthStats", "Не удалось прочитать Health Connect", e)
                HealthLoad.Failed
            }
        }
    }
    val yearSteps by produceState(emptyMap<LocalDate, Long>(), year, grants) {
        value = runCatching {
            if (health.availability == HealthAvailability.AVAILABLE && health.hasPermissions()) {
                health.dailySteps(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31).coerceAtMost(today))
            } else {
                emptyMap()
            }
        }.getOrDefault(emptyMap())
    }
    var selected by remember(range, metric) { mutableStateOf<LocalDate?>(null) }
    var selectedHour by remember(range) { mutableStateOf<Int?>(null) }
    var selectedMapDay by remember(year) { mutableStateOf<LocalDate?>(null) }

    val canEarlier = period != StatsPeriod.ALL && range.start.isAfter(EARLIEST)
    val canLater = period != StatsPeriod.ALL && offset > 0
    fun earlier() {
        if (canEarlier) offset++
    }
    fun later() {
        if (canLater) offset--
    }
    // Свайп по строке с датами листает период; едет за пальцем только дата
    val (dateSwipe, dateGesture) = rememberDaySwipe(canGoForward = canLater) { step -> if (step < 0) earlier() else later() }
    val (yearSwipe, yearGesture) = rememberDaySwipe(canGoForward = year < today.year) { step ->
        year = (year + step.toInt()).coerceIn(EARLIEST.year, today.year)
    }
    val number = remember { NumberFormat.getIntegerInstance() }

    ScreenScaffold(title = stringResource(R.string.stats_health), modifier = modifier, onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .onTapAnywhere {
                    selected = null
                    selectedHour = null
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

            when (val state = load) {
                HealthLoad.Loading -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                HealthLoad.Unavailable -> Message(stringResource(R.string.health_unavailable))
                HealthLoad.Failed -> Message(stringResource(R.string.stats_health_failed))
                HealthLoad.NoAccess -> StatsCard(stringResource(R.string.health_connect_title)) {
                    Text(stringResource(R.string.health_connect_text), style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = { askPermission.launch(health.permissions) }) {
                        Text(stringResource(R.string.health_connect_button))
                    }
                }
                is HealthLoad.Ready -> {
                    if (state.needsHistory) {
                        StatsCard(stringResource(R.string.stats_health_history_title)) {
                            Text(stringResource(R.string.stats_health_history_text), style = MaterialTheme.typography.bodyMedium)
                            FilledTonalButton(onClick = { askPermission.launch(setOf(health.historyPermission)) }) {
                                Text(stringResource(R.string.stats_health_history_button))
                            }
                        }
                    }
                    val summary = remember(state, goals) { HealthStats.summary(state.days, goals) }
                    if (summary.days.isEmpty()) {
                        Message(stringResource(R.string.stats_sleep_empty))
                    } else {
                        HealthContent(
                            summary = summary,
                            hours = state.hours,
                            goals = goals,
                            range = range,
                            today = today,
                            metric = metric,
                            onMetric = { metric = it },
                            selected = selected,
                            onSelect = { selected = it },
                            selectedHour = selectedHour,
                            onSelectHour = { selectedHour = it },
                            number = number,
                        )
                    }
                }
            }

            StatsCard(stringResource(R.string.stats_health_year)) {
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
                val picked = selectedMapDay
                val withSteps = yearSteps.values.filter { it > 0 }
                Caption(
                    when {
                        picked != null -> {
                            val steps = yearSteps[picked] ?: 0
                            "${picked.format(DayDate)}: " + if (steps > 0) {
                                stringResource(R.string.stats_health_steps_of_goal, number.format(steps), (steps * 100 / goals.steps.coerceAtLeast(1)))
                            } else {
                                stringResource(R.string.stats_no_data)
                            }
                        }
                        withSteps.isNotEmpty() -> stringResource(
                            R.string.stats_health_year_summary,
                            withSteps.count { it >= goals.steps },
                            withSteps.size,
                        )
                        else -> stringResource(R.string.stats_health_year_empty)
                    },
                )
                val levels = goalLevels()
                YearHeatmap(
                    year = year,
                    today = today,
                    colorFor = { date -> HealthStats.level(yearSteps[date] ?: 0, goals.steps).takeIf { it > 0 }?.let { levels[it - 1] } },
                    selected = picked,
                    onSelect = { selectedMapDay = it },
                )
                HeatmapLegend(stringResource(R.string.stats_health_below_goal), stringResource(R.string.stats_health_above_goal), levels)
            }
        }
    }
}

/** Ступени относительно цели шагов — оттенки акцента: меньше половины, до цели, цель, полторы цели. */
@Composable
private fun goalLevels() = listOf(0.25f, 0.5f, 0.8f, 1f).map { MaterialTheme.colorScheme.primary.copy(alpha = it) }

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    )
}

@Composable
private fun HealthContent(
    summary: HealthSummary,
    hours: LongArray,
    goals: HealthGoals,
    range: ClosedRange<LocalDate>,
    today: LocalDate,
    metric: HealthMetric,
    onMetric: (HealthMetric) -> Unit,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    selectedHour: Int?,
    onSelectHour: (Int) -> Unit,
    number: NumberFormat,
) {
    val average = stringResource(R.string.stats_average)
    val stepsUnit = stringResource(R.string.stats_health_steps_unit)
    val minutesUnit = stringResource(R.string.stats_min)
    val kcalUnit = stringResource(R.string.health_unit_calories)
    val days = summary.days.size
    TileRow {
        StatTile("${stringResource(R.string.health_steps)} $average", withUnit(number.format(summary.averageSteps), ""), it)
        StatTile("${stringResource(R.string.health_minutes)} $average", withUnit(summary.averageMinutes.toString(), minutesUnit), it)
    }
    TileRow {
        StatTile("${stringResource(R.string.health_calories)} $average", withUnit(number.format(summary.averageCalories), kcalUnit), it)
        StatTile(
            stringResource(R.string.stats_health_record),
            withUnit(summary.record?.second?.let(number::format) ?: "-", ""),
            it,
            note = summary.record?.first?.format(ShortDate),
        )
    }
    TileRow {
        StatTile(
            stringResource(R.string.stats_health_step_goal),
            withUnit("${summary.stepGoalDays} / $days", ""),
            it,
            note = stringResource(R.string.stats_health_days_hint),
        )
        StatTile(
            stringResource(R.string.stats_health_all_goals),
            withUnit("${summary.allGoalsDays} / $days", ""),
            it,
            note = stringResource(R.string.stats_health_days_hint),
        )
    }

    StatsCard(stringResource(R.string.stats_health_by_day)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HealthMetric.entries.forEach { option ->
                FilterChip(
                    selected = option == metric,
                    onClick = { onMetric(option) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    label = {
                        Text(
                            stringResource(
                                when (option) {
                                    HealthMetric.STEPS -> R.string.health_steps
                                    HealthMetric.MINUTES -> R.string.health_minutes
                                    HealthMetric.CALORIES -> R.string.health_calories
                                },
                            ),
                        )
                    },
                )
            }
        }
        val unit = when (metric) {
            HealthMetric.STEPS -> stepsUnit
            HealthMetric.MINUTES -> minutesUnit
            HealthMetric.CALORIES -> kcalUnit
        }
        val day = selected?.let { summary.days[it] }
        Caption(
            if (selected != null && day != null) {
                "${selected.format(DayDate)}: ${number.format(day.value(metric).roundToLong())} $unit"
            } else {
                stringResource(R.string.stats_health_goal_hint, number.format(goals.value(metric).roundToLong()), unit)
            },
        )
        MetricChart(summary.days, metric, goals.value(metric), range, selected, onSelect)
        RangeLabels(range)
    }

    StatsCard(stringResource(R.string.stats_days_weekdays)) {
        WeekdayChart(summary.byWeekday, LocalAppSettings.current.firstDayOfWeek, number)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            listOf(R.string.stats_weekdays to summary.weekdaysAverage, R.string.stats_weekends to summary.weekendsAverage)
                .forEach { (label, value) ->
                    Column {
                        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            withUnit(value?.let(number::format) ?: "-", if (value != null) " $stepsUnit" else ""),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
        }
    }

    StatsCard(stringResource(R.string.stats_health_hours)) {
        val counted = (ChronoUnit.DAYS.between(range.start, range.endInclusive.coerceAtMost(today)) + 1).coerceAtLeast(1)
        val peak = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0 }
        Caption(
            when {
                selectedHour != null -> stringResource(
                    R.string.stats_health_hour_selected,
                    selectedHour,
                    selectedHour + 1,
                    number.format(hours[selectedHour] / counted),
                )
                peak != null -> stringResource(R.string.stats_health_hours_peak, peak, peak + 1)
                else -> stringResource(R.string.stats_no_data)
            },
        )
        HourChart(hours, selectedHour, onSelectHour)
    }
}

/**
 * Показатель по дням: столбцы в акценте, пунктир — цель, бледная линия — среднее.
 * Тап по дню или палец, ведомый вбок, выбирает день.
 */
@Composable
private fun MetricChart(
    days: Map<LocalDate, DayActivity>,
    metric: HealthMetric,
    goal: Double,
    range: ClosedRange<LocalDate>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val axisPx = with(LocalDensity.current) { AXIS_WIDTH.dp.toPx() }
    val count = ChronoUnit.DAYS.between(range.start, range.endInclusive).toInt() + 1
    val dates = days.keys.toList()
    val values = days.mapValues { it.value.value(metric) }
    val average = values.values.average()
    val max = maxOf(values.values.maxOrNull() ?: 0.0, goal) * 1.1
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .pointerInput(days, range, metric) {
                detectTapGestures { offset ->
                    val slot = ((offset.x - axisPx) / ((size.width - axisPx) / count)).toInt().coerceIn(0, count - 1)
                    nearestWithin(dates, range.start.plusDays(slot.toLong()), maxOf(1L, count / 60L))?.let(onSelect)
                }
            }
            .pointerInput(days, range, metric) {
                detectHorizontalDragGestures { change, _ ->
                    if (dates.isEmpty()) return@detectHorizontalDragGestures
                    change.consume()
                    val slot = ((change.position.x - axisPx) / ((size.width - axisPx) / count)).toInt().coerceIn(0, count - 1)
                    nearestWithin(dates, range.start.plusDays(slot.toLong()), count.toLong())?.let(onSelect)
                }
            },
    ) {
        fun y(v: Double) = size.height - (v / max * size.height).toFloat()
        listOf(0.0, max / 2, max).forEach { v ->
            val yy = y(v)
            drawLine(colors.outlineVariant, Offset(axisPx, yy), Offset(size.width, yy), strokeWidth = 1f)
            val label = measurer.measure(compact(v), labelStyle)
            drawText(label, topLeft = Offset(0f, (yy - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)))
        }
        drawLine(
            colors.onSurfaceVariant.copy(alpha = 0.7f),
            Offset(axisPx, y(goal)),
            Offset(size.width, y(goal)),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
        drawLine(colors.primary.copy(alpha = 0.45f), Offset(axisPx, y(average)), Offset(size.width, y(average)), strokeWidth = 2f)
        val slot = (size.width - axisPx) / count
        val width = (slot * 0.66f).coerceAtLeast(1.5f)
        values.forEach { (date, v) ->
            val index = ChronoUnit.DAYS.between(range.start, date).toInt()
            if (index !in 0 until count || v <= 0) return@forEach
            val top = y(v)
            val alpha = if (selected == null || selected == date) 1f else 0.35f
            drawRoundRect(
                colors.primary.copy(alpha = alpha),
                topLeft = Offset(axisPx + slot * index + (slot - width) / 2, top),
                size = Size(width, (size.height - top).coerceAtLeast(2f)),
                cornerRadius = CornerRadius(width / 2, width / 2),
            )
        }
    }
}

/** Короткая подпись оси: «12 тыс.» не нужна — «12k» понятнее и уже. */
private fun compact(v: Double): String = when {
    v >= 10_000 -> "${(v / 1000).roundToLong()}k"
    v >= 1000 -> "%.1fk".format(v / 1000)
    else -> v.roundToLong().toString()
}

/** Средние шаги по дням недели, с первого дня недели из настроек; над столбцом — значение. */
@Composable
private fun WeekdayChart(byWeekday: Map<DayOfWeek, Long>, firstDay: DayOfWeek, number: NumberFormat) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val valueStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurface)
    val order = (0..6).map { firstDay.plus(it.toLong()) }
    val names = order.map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    val max = (byWeekday.values.maxOrNull() ?: 1).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val labelHeight = 18.dp.toPx()
        val valueHeight = 16.dp.toPx()
        val chartHeight = size.height - labelHeight - valueHeight
        val slot = size.width / 7
        val width = slot * 0.55f
        order.forEachIndexed { i, day ->
            val value = byWeekday[day]
            if (value != null && value > 0) {
                val barHeight = (value.toFloat() / max * chartHeight).coerceAtLeast(3f)
                val top = valueHeight + chartHeight - barHeight
                drawRoundRect(colors.primary, Offset(slot * i + (slot - width) / 2, top), Size(width, barHeight), CornerRadius(8f, 8f))
                val text = measurer.measure(compact(value.toDouble()), valueStyle)
                drawText(text, topLeft = Offset(slot * i + (slot - text.size.width) / 2, top - text.size.height))
            }
            val label = measurer.measure(names[i], labelStyle)
            drawText(label, topLeft = Offset(slot * i + (slot - label.size.width) / 2, size.height - label.size.height))
        }
    }
}

/** Шаги по часам суток: 24 столбца в акценте, подписи 0, 6, 12, 18; тап — час. */
@Composable
private fun HourChart(hours: LongArray, selected: Int?, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val max = (hours.maxOrNull() ?: 1).coerceAtLeast(1)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(140.dp)
            .pointerInput(hours.contentHashCode()) {
                detectTapGestures { offset ->
                    val hour = (offset.x / (size.width / 24)).toInt().coerceIn(0, 23)
                    if (hours[hour] > 0) onSelect(hour)
                }
            },
    ) {
        val labelHeight = 18.dp.toPx()
        val chartHeight = size.height - labelHeight
        val slot = size.width / 24
        val width = slot * 0.66f
        hours.forEachIndexed { hour, count ->
            if (count > 0) {
                val barHeight = (count.toFloat() / max * chartHeight).coerceAtLeast(2f)
                val alpha = if (selected == null || selected == hour) 1f else 0.35f
                drawRoundRect(
                    colors.primary.copy(alpha = alpha),
                    Offset(slot * hour + (slot - width) / 2, chartHeight - barHeight),
                    Size(width, barHeight),
                    CornerRadius(width / 2, width / 2),
                )
            }
            if (hour % 6 == 0) {
                val label = measurer.measure(hour.toString(), labelStyle)
                drawText(label, topLeft = Offset(slot * hour + (slot - label.size.width) / 2, size.height - label.size.height))
            }
        }
    }
}
