package dev.chaik.remember.feature.stats.sleep

import androidx.compose.foundation.layout.Box
import dev.chaik.remember.core.ui.components.daySwipeOffset
import dev.chaik.remember.core.ui.components.rememberDaySwipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import dev.chaik.remember.feature.stats.onTapAnywhere
import dev.chaik.remember.feature.stats.nearestWithin
import dev.chaik.remember.feature.stats.YearSwitcher
import dev.chaik.remember.feature.stats.PeriodDates
import dev.chaik.remember.feature.stats.PeriodChips
import dev.chaik.remember.core.ui.components.SlidingText
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.model.Rating
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.settings.logicalToday
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.feature.stats.HeatmapLegend
import dev.chaik.remember.feature.stats.StatTile
import dev.chaik.remember.feature.stats.StatsCard
import dev.chaik.remember.feature.stats.YearHeatmap
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlinx.serialization.Serializable

@Serializable
data object SleepStatsRoute : NavKey

/** Раньше этой даты статистику не листаем. */
private val EARLIEST: LocalDate = LocalDate.of(2000, 1, 1)

private val NightDate = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())

/**
 * Статистика сна за период: средние, окна сна по ночам, длительность, будни против выходных
 * и качество сна за год тепловой картой. Тап по ночи на графике — её время и длительность.
 */
@Composable
fun SleepStatsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val today = LocalAppSettings.current.logicalToday()
    var period by rememberSaveable { mutableStateOf(StatsPeriod.MONTH) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    // Дни с записанным сном: начало «всего времени» и первый год тепловой карты
    val dates by remember { container.sleepRepository.observeDates() }.collectAsStateWithLifecycle(emptySet())
    val first = dates.minOrNull()
    val range = period.range(today, offset, first, LocalAppSettings.current.firstDayOfWeek)
    val entries by remember(range) { container.sleepRepository.observeRange(range.start, range.endInclusive) }
        .collectAsStateWithLifecycle(emptyList())
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    val yearEntries by remember(year) { container.sleepRepository.observeRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)) }
        .collectAsStateWithLifecycle(emptyList())
    val summary = remember(entries) { SleepStats.summary(entries) }
    val qualityByDate = remember(yearEntries) { yearEntries.mapNotNull { e -> e.quality?.takeIf { it > 0 }?.let { e.date to it } }.toMap() }
    var selected by remember(range) { mutableStateOf<LocalDate?>(null) }
    var selectedQualityDay by remember(year) { mutableStateOf<LocalDate?>(null) }

    // Листать назад можно до 2000 года, даже если записей тогда ещё не было
    val canEarlier = period != StatsPeriod.ALL && range.start.isAfter(EARLIEST)
    val canLater = period != StatsPeriod.ALL && offset > 0
    fun earlier() {
        if (canEarlier) offset++
    }
    fun later() {
        if (canLater) offset--
    }

    // Свайп листает периоды, как на «Сегодня»: ловится по всему блоку, а едет за пальцем только дата
    val (dateSwipe, dateGesture) = rememberDaySwipe(canGoForward = canLater) { step -> if (step < 0) earlier() else later() }
    val (yearSwipe, yearGesture) = rememberDaySwipe(canGoForward = year < today.year) { step ->
        year = (year + step.toInt()).coerceIn(EARLIEST.year, today.year)
    }

    ScreenScaffold(title = stringResource(R.string.stats_sleep), modifier = modifier, onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                // Тап мимо столбца или клетки снимает выделение
                .onTapAnywhere {
                    selected = null
                    selectedQualityDay = null
                }
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 24.dp)
                .padding(horizontal = 16.dp),
        ) {
            PeriodChips(period) {
                period = it
                offset = 0
            }

            // Даты и плитки листаются свайпом вбок, как стрелками
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Свайп по строке с датами листает период и забирает жест себе («назад» не срабатывает);
                // ловится до самых краёв экрана. По карточкам ниже свайп работает как обычно
                Box(
                    Modifier
                        .fullWidth(16.dp)
                        .then(dateGesture)
                        .padding(horizontal = 16.dp),
                ) {
                    Box(Modifier.daySwipeOffset(dateSwipe)) {
                        PeriodDates(range, canEarlier, canLater, onEarlier = ::earlier, onLater = ::later)
                    }
                }
                val all = summary.all
                if (all == null) {
                    Text(
                        stringResource(R.string.stats_sleep_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                } else {
                    val average = stringResource(R.string.stats_average)
                    TileRow {
                        StatTile("${stringResource(R.string.stats_sleep_avg)} $average", compactDuration(all.durationMinutes), it)
                        StatTile(
                            stringResource(R.string.stats_sleep_regime),
                            withUnit("±${all.bedSpreadMinutes}", stringResource(R.string.stats_min)),
                            it,
                            note = stringResource(R.string.stats_sleep_regime_hint),
                        )
                    }
                    TileRow {
                        StatTile("${stringResource(R.string.stats_sleep_bed)} $average", SleepStats.clock(all.bedMinute), it)
                        StatTile("${stringResource(R.string.stats_sleep_wake)} $average", SleepStats.clock(all.wakeMinute), it)
                    }
                    TileRow {
                        StatTile(
                            stringResource(R.string.stats_sleep_deficit),
                            compactDuration(summary.deficitMinutes),
                            it,
                            note = stringResource(R.string.stats_sleep_deficit_hint, SLEEP_NORM_MINUTES / 60),
                        )
                        val days = ChronoUnit.DAYS.between(range.start, range.endInclusive.coerceAtMost(today)) + 1
                        StatTile(
                            stringResource(R.string.stats_sleep_nights),
                            "${all.nights} / $days",
                            it,
                            note = stringResource(R.string.stats_sleep_nights_hint),
                        )
                    }
                }
            }

            val all = summary.all
            if (all != null) {
                // Выбранная ночь — над графиками; оба графика выбирают одну и ту же
                val night = summary.nights.firstOrNull { it.date == selected }
                StatsCard(stringResource(R.string.stats_sleep_windows)) {
                    Caption(
                        night?.let {
                            "${it.date.format(NightDate)}: ${SleepStats.clock(it.bedMinute)} - ${SleepStats.clock(it.wakeMinute)} · ${duration(it.durationMinutes)}"
                        } ?: stringResource(R.string.stats_sleep_tap_hint),
                    )
                    WindowsChart(summary.nights, all, range, selected) { selected = it }
                    RangeLabels(range)
                }
                StatsCard(stringResource(R.string.stats_sleep_duration)) {
                    Caption(
                        night?.let { "${it.date.format(NightDate)}: ${duration(it.durationMinutes)}" }
                            ?: stringResource(R.string.stats_sleep_norm_hint, SLEEP_NORM_MINUTES / 60),
                    )
                    DurationChart(summary.nights, all, range, selected) { selected = it }
                    RangeLabels(range)
                }
                StatsCard(stringResource(R.string.stats_sleep_week_split)) {
                    WeekSplit(summary)
                }
            }

            StatsCard(stringResource(R.string.stats_sleep_quality)) {
                val average = yearEntries.mapNotNull { it.quality?.takeIf { q -> q > 0 } }.takeIf { it.isNotEmpty() }?.average()
                val picked = selectedQualityDay
                // Год — сразу под заголовком, подпись — под ним во всю ширину
                // Год листается и свайпом по этой строке; едет за пальцем только год
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
                run {
                    Caption(
                        when {
                            picked != null -> qualityByDate[picked]?.let { "${picked.format(NightDate)}: ${Rating.format(it)} / ${Rating.SLEEP_STARS}" }
                                ?: "${picked.format(NightDate)}: ${stringResource(R.string.stats_no_data)}"
                            average != null -> stringResource(R.string.stats_sleep_quality_avg, "%.1f".format(average / 2), Rating.SLEEP_STARS)
                            else -> stringResource(R.string.stats_sleep_quality_empty)
                        },
                    )
                }
                val levels = qualityLevels()
                YearHeatmap(
                    year = year,
                    today = today,
                    colorFor = { date -> qualityByDate[date]?.let { levels[((it + 1) / 2).coerceIn(1, 5) - 1] } },
                    selected = picked,
                    onSelect = { selectedQualityDay = it },
                )
                HeatmapLegend(stringResource(R.string.stats_quality_low), stringResource(R.string.stats_quality_high), levels)
            }
        }
    }
}

/** Строка плиток одной высоты — по самой высокой. */
@Composable
internal fun TileRow(content: @Composable (Modifier) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
        content(Modifier.weight(1f).fillMaxHeight())
    }
}

/** Подпись под названием графика: в одну строку; длинная — медленно ездит, не сдвигая страницу. */
@Composable
internal fun Caption(text: String, modifier: Modifier = Modifier) {
    SlidingText(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Пять ступеней качества — оттенки основного цвета, как зелёные клетки на GitHub. */
@Composable
private fun qualityLevels(): List<Color> {
    val primary = MaterialTheme.colorScheme.primary
    return listOf(0.22f, 0.4f, 0.58f, 0.78f, 1f).map { primary.copy(alpha = it) }
}

@Composable
private fun duration(minutes: Int): String = stringResource(R.string.gym_hours_minutes, minutes / 60, minutes % 60)

internal val AxisDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

@Composable
internal fun RangeLabels(range: ClosedRange<LocalDate>) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(start = AXIS_WIDTH.dp)) {
        Text(range.start.format(AxisDate), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(range.endInclusive.format(AxisDate), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Ширина колонки подписей слева у графиков, dp. */
internal const val AXIS_WIDTH = 40

/**
 * Палец над графиком выбирает ближайшую ночь: тапом или ведя, не отрывая.
 * Столбец — день периода; ночь стоит в столбце даты пробуждения.
 */
private fun Modifier.selectNight(
    nights: List<Night>,
    range: ClosedRange<LocalDate>,
    axisPx: Float,
    onSelect: (LocalDate) -> Unit,
): Modifier = pointerInput(nights, range) {
    val days = ChronoUnit.DAYS.between(range.start, range.endInclusive).toInt() + 1
    fun pick(x: Float) {
        if (nights.isEmpty()) return
        val slot = ((x - axisPx) / ((size.width - axisPx) / days)).toInt().coerceIn(0, days - 1)
        val date = range.start.plusDays(slot.toLong())
        // Тап — только по ночи рядом (на длинном периоде столбцы тонкие — допуск шире)
        nearestWithin(nights.map { it.date }, date, maxOf(1L, days / 60L))?.let(onSelect)
    }
    detectTapGestures { pick(it.x) }
}.pointerInput(nights, range) {
    val days = ChronoUnit.DAYS.between(range.start, range.endInclusive).toInt() + 1
    // Только вбок: вертикальный жест остаётся прокрутке страницы
    detectHorizontalDragGestures { change, _ ->
        if (nights.isEmpty()) return@detectHorizontalDragGestures
        val slot = ((change.position.x - axisPx) / ((size.width - axisPx) / days)).toInt().coerceIn(0, days - 1)
        val date = range.start.plusDays(slot.toLong())
        nights.minByOrNull { abs(ChronoUnit.DAYS.between(it.date, date)) }?.let { onSelect(it.date) }
    }
}

/**
 * Окна сна: по вертикали время суток (вечер сверху, утро снизу), по горизонтали дни.
 * Каждая ночь — полоса от отбоя до подъёма; пунктир — средние отбой и подъём.
 */
@Composable
private fun WindowsChart(
    nights: List<Night>,
    averages: SleepAverages,
    range: ClosedRange<LocalDate>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    // Шкала — по самым ранним отбою и поздним подъёму, с запасом до целого часа. Без рамок
    // «ночи»: и сон днём, и сон с вечера накануне помещаются целиком
    val rawTop = Math.floorDiv(nights.minOf { it.bedMinute } - 30, 60) * 60
    val rawBottom = (Math.floorDiv(nights.maxOf { it.wakeMinute } + 30, 60) + 1) * 60
    val step = when {
        rawBottom - rawTop > 24 * 60 -> 360
        rawBottom - rawTop > 12 * 60 -> 240
        else -> 120
    }
    // Края шкалы — по шагу сетки: сверху и снизу всегда подписанная линия
    val top = Math.floorDiv(rawTop, step) * step
    val bottom = -Math.floorDiv(-rawBottom, step) * step
    val axisPx = with(androidx.compose.ui.platform.LocalDensity.current) { AXIS_WIDTH.dp.toPx() }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .selectNight(nights, range, axisPx, onSelect),
    ) {
        val span = (bottom - top).toFloat()
        fun y(minute: Int) = (minute - top) / span * size.height
        hourGrid(top, bottom, step, axisPx, measurer, labelStyle, colors.outlineVariant) { y(it) }
        dashed(y(averages.bedMinute), axisPx, colors.onSurfaceVariant.copy(alpha = 0.7f))
        dashed(y(averages.wakeMinute), axisPx, colors.onSurfaceVariant.copy(alpha = 0.7f))
        bars(nights, range, axisPx, selected, colors.primary) { night -> y(night.bedMinute) to y(night.wakeMinute) }
    }
}

/** Длительность сна по ночам; пунктир — норма, сплошная бледная линия — среднее за период. */
@Composable
private fun DurationChart(
    nights: List<Night>,
    averages: SleepAverages,
    range: ClosedRange<LocalDate>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val hoursLabel = stringResource(R.string.stats_hours_short)
    val max = ((maxOf(nights.maxOf { it.durationMinutes }, SLEEP_NORM_MINUTES) + 59) / 60) * 60
    val axisPx = with(androidx.compose.ui.platform.LocalDensity.current) { AXIS_WIDTH.dp.toPx() }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .selectNight(nights, range, axisPx, onSelect),
    ) {
        fun y(minutes: Int) = size.height - minutes.toFloat() / max * size.height
        // Подписи — часы длительности: «0 ч», «4 ч», «8 ч»
        val step = if (max > 10 * 60) 240 else 120
        var m = 0
        while (m <= max) {
            val yy = y(m)
            drawLine(colors.outlineVariant, Offset(axisPx, yy), Offset(size.width, yy), strokeWidth = 1f)
            val label = measurer.measure(hoursLabel.format(m / 60), labelStyle)
            drawText(label, topLeft = Offset(0f, (yy - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)))
            m += step
        }
        dashed(y(SLEEP_NORM_MINUTES), axisPx, colors.onSurfaceVariant.copy(alpha = 0.7f))
        drawLine(colors.primary.copy(alpha = 0.45f), Offset(axisPx, y(averages.durationMinutes)), Offset(size.width, y(averages.durationMinutes)), strokeWidth = 2f)
        bars(nights, range, axisPx, selected, colors.primary) { night -> y(night.durationMinutes) to size.height }
    }
}

/** Линии сетки по часам с подписями «22:00» слева. */
private fun DrawScope.hourGrid(
    top: Int,
    bottom: Int,
    step: Int,
    axisPx: Float,
    measurer: TextMeasurer,
    style: TextStyle,
    color: Color,
    y: (Int) -> Float,
) {
    var minute = Math.floorDiv(top + step - 1, step) * step
    while (minute <= bottom) {
        val yy = y(minute)
        drawLine(color, Offset(axisPx, yy), Offset(size.width, yy), strokeWidth = 1f)
        val label = measurer.measure(SleepStats.clock(minute), style)
        drawText(label, topLeft = Offset(0f, (yy - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)))
        minute += step
    }
}

private fun DrawScope.dashed(y: Float, axisPx: Float, color: Color) {
    drawLine(
        color,
        Offset(axisPx, y),
        Offset(size.width, y),
        strokeWidth = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
    )
}

/** Столбцы ночей: [span] — верх и низ столбца; выбранная ночь яркая, остальные тогда приглушены. */
private fun DrawScope.bars(
    nights: List<Night>,
    range: ClosedRange<LocalDate>,
    axisPx: Float,
    selected: LocalDate?,
    color: Color,
    span: (Night) -> Pair<Float, Float>,
) {
    val days = ChronoUnit.DAYS.between(range.start, range.endInclusive).toInt() + 1
    val slot = (size.width - axisPx) / days
    val width = (slot * 0.66f).coerceAtLeast(1.5f)
    nights.forEach { night ->
        val index = ChronoUnit.DAYS.between(range.start, night.date).toInt()
        if (index !in 0 until days) return@forEach
        val (top, bottom) = span(night)
        val x = axisPx + slot * index + (slot - width) / 2
        val alpha = if (selected == null || selected == night.date) 1f else 0.4f
        drawRoundRect(
            color.copy(alpha = alpha),
            topLeft = Offset(x, top),
            size = Size(width, (bottom - top).coerceAtLeast(2f)),
            cornerRadius = CornerRadius(width / 2, width / 2),
        )
    }
}

/** Будни против выходных: длительность, отбой и подъём в две колонки. */
@Composable
private fun WeekSplit(summary: SleepSummary) {
    val weekdays = summary.weekdays
    val weekends = summary.weekends
    val dash = "-"
    val rows = listOf(
        stringResource(R.string.stats_sleep_avg) to listOf(weekdays?.durationMinutes, weekends?.durationMinutes)
            .map { it?.let { m -> compactDuration(m) } ?: AnnotatedString(dash) },
        stringResource(R.string.stats_sleep_bed) to listOf(weekdays?.bedMinute, weekends?.bedMinute).map { AnnotatedString(it?.let(SleepStats::clock) ?: dash) },
        stringResource(R.string.stats_sleep_wake) to listOf(weekdays?.wakeMinute, weekends?.wakeMinute).map { AnnotatedString(it?.let(SleepStats::clock) ?: dash) },
    )
    val header = listOf(stringResource(R.string.stats_weekdays), stringResource(R.string.stats_weekends))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text("", Modifier.weight(1.2f))
            header.forEach {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
        rows.forEach { (label, values) ->
            Row {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.2f))
                values.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** «97мин»: число обычное, единица — мельче и бледнее, вплотную к числу. */
@Composable
internal fun withUnit(value: String, unit: String): AnnotatedString = buildAnnotatedString {
    append(value)
    withStyle(SpanStyle(fontSize = MaterialTheme.typography.labelLarge.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(unit) }
}

/** «8ч 15мин»: числа обычные, единицы — мельче и бледнее, вплотную к числу. */
@Composable
private fun compactDuration(minutes: Int): AnnotatedString {
    val unit = SpanStyle(fontSize = MaterialTheme.typography.labelMedium.fontSize, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val hours = stringResource(R.string.stats_unit_h)
    val mins = stringResource(R.string.stats_min)
    return buildAnnotatedString {
        append((minutes / 60).toString())
        withStyle(unit) { append(hours) }
        append(" ")
        append((minutes % 60).toString())
        withStyle(unit) { append(mins) }
    }
}

/** Растянуть на [margin] за поля родителя с каждой стороны — чтобы жест ловился у самого края экрана. */
internal fun Modifier.fullWidth(margin: Dp): Modifier = layout { measurable, constraints ->
    val extra = margin.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra * 2, maxWidth = constraints.maxWidth + extra * 2),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}
