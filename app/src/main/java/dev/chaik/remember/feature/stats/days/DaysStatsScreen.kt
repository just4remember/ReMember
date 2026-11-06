package dev.chaik.remember.feature.stats.days

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.model.Rating
import dev.chaik.remember.core.reminders.OpenTarget
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.settings.logicalToday
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.core.ui.components.daySwipeOffset
import dev.chaik.remember.core.ui.components.rememberDaySwipe
import dev.chaik.remember.core.ui.theme.RatingBad
import dev.chaik.remember.core.ui.theme.RatingGood
import dev.chaik.remember.core.ui.theme.RatingOkay
import dev.chaik.remember.core.ui.theme.RatingPerfect
import dev.chaik.remember.core.ui.theme.RatingSecret
import dev.chaik.remember.core.ui.theme.ratingColor
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
data object DaysStatsRoute : NavKey

/** Раньше этой даты статистику не листаем. */
private val EARLIEST: LocalDate = LocalDate.of(2000, 1, 1)

// Каждый раз заново: после смены языка приложения даты должны быть на новом языке
private val DayDate: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
// Каждый раз заново: после смены языка приложения даты должны быть на новом языке
private val ShortDate: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/** Сортировка тегов: самые частые или с лучшей средней оценкой. */
private enum class TagOrder { FREQUENT, BEST }

/**
 * Статистика дней: средняя оценка, записанные дни, лучший и худший день,
 * оценки по дням, их распределение, дни недели, теги и год тепловой картой в цветах оценок.
 */
@Composable
fun DaysStatsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val settings = LocalAppSettings.current
    val today = settings.logicalToday()
    var period by rememberSaveable { mutableStateOf(StatsPeriod.MONTH) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val written by remember { container.dayRepository.observeWrittenDates() }.collectAsStateWithLifecycle(emptySet())
    val first = written.minOrNull()
    val range = period.range(today, offset, first, settings.firstDayOfWeek)
    val entries by remember(range) { container.dayRepository.observeRange(range.start, range.endInclusive) }
        .collectAsStateWithLifecycle(emptyList())
    val tags by remember { container.dayRepository.observeTags() }.collectAsStateWithLifecycle(emptyMap())
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    val marks by remember(year) { container.dayRepository.observeMarks(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)) }
        .collectAsStateWithLifecycle(emptyMap())
    val summary = remember(entries, written, tags, range) { DaysStats.summary(entries, written, tags, range) }
    var selected by remember(range) { mutableStateOf<LocalDate?>(null) }
    var selectedMapDay by remember(year) { mutableStateOf<LocalDate?>(null) }
    var tagOrder by rememberSaveable { mutableStateOf(TagOrder.FREQUENT) }

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
    fun openDay(date: LocalDate) {
        container.openRequests.value = OpenTarget.DayAt(date)
    }

    ScreenScaffold(title = stringResource(R.string.stats_days), modifier = modifier, onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                // Тап мимо столбца или клетки снимает выделение
                .onTapAnywhere {
                    selected = null
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
                if (summary.writtenDays == 0 && summary.ratings.isEmpty()) {
                    Text(
                        stringResource(R.string.stats_sleep_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                } else {
                    val average = stringResource(R.string.stats_average)
                    val days = ChronoUnit.DAYS.between(range.start, range.endInclusive.coerceAtMost(today)) + 1
                    TileRow {
                        StatTile(
                            "${stringResource(R.string.stats_days_rating)} $average",
                            summary.averageRating?.let { withUnit(stars(it), "/${Rating.DAY_STARS}") } ?: withUnit("-", ""),
                            it,
                        )
                        StatTile(stringResource(R.string.stats_days_written), "${summary.writtenDays} / $days", it)
                    }
                    TileRow {
                        val best = summary.best
                        StatTile(
                            stringResource(R.string.stats_days_best),
                            best?.let { withUnit(rating(it.rating), "/${Rating.DAY_STARS}") } ?: withUnit("-", ""),
                            it,
                            note = best?.date?.format(ShortDate),
                            onClick = best?.let { b -> { openDay(b.date) } },
                        )
                        val worst = summary.worst
                        StatTile(
                            stringResource(R.string.stats_days_worst),
                            worst?.let { withUnit(rating(it.rating), "/${Rating.DAY_STARS}") } ?: withUnit("-", ""),
                            it,
                            note = worst?.date?.format(ShortDate),
                            onClick = worst?.let { w -> { openDay(w.date) } },
                        )
                    }
                }
            }

            if (summary.ratings.isNotEmpty()) {
                val rating = selected?.let { date -> summary.ratings[date]?.let { date to it } }
                StatsCard(stringResource(R.string.stats_days_by_day)) {
                    Caption(
                        rating?.let { (date, value) -> "${date.format(DayDate)}: ${rating(value)} / ${Rating.DAY_STARS}" }
                            ?: stringResource(R.string.stats_days_by_day_hint),
                    )
                    RatingChart(summary.ratings, summary.averageRating!!, range, selected) { selected = it }
                    RangeLabels(range)
                }
                StatsCard(stringResource(R.string.stats_days_distribution)) {
                    Caption(stringResource(R.string.stats_days_distribution_hint))
                    DistributionChart(summary.distribution)
                }
                StatsCard(stringResource(R.string.stats_days_weekdays)) {
                    WeekdayChart(summary.byWeekday, settings.firstDayOfWeek)
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        listOf(
                            R.string.stats_weekdays to summary.weekdaysAverage,
                            R.string.stats_weekends to summary.weekendsAverage,
                        ).forEach { (label, value) ->
                            Column {
                                Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    value?.let { withUnit(stars(it), "/${Rating.DAY_STARS}") } ?: withUnit("-", ""),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }
                        }
                    }
                }
            }

            if (summary.tags.isNotEmpty()) {
                StatsCard(stringResource(R.string.stats_days_tags)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TagOrder.entries.forEach { order ->
                            FilterChip(
                                selected = tagOrder == order,
                                onClick = { tagOrder = order },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                label = {
                                    Text(stringResource(if (order == TagOrder.FREQUENT) R.string.stats_days_tags_frequent else R.string.stats_days_tags_best))
                                },
                            )
                        }
                    }
                    val sorted = when (tagOrder) {
                        TagOrder.FREQUENT -> summary.tags
                        TagOrder.BEST -> summary.tags.sortedWith(
                            compareByDescending<TagStat> { it.averageRating ?: -1.0 }.thenByDescending { it.days },
                        )
                    }
                    TagBars(sorted)
                }
            }

            StatsCard(stringResource(R.string.stats_days_year)) {
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
                val rated = marks.values.filterNotNull().filter { it > 0 }
                Caption(
                    when {
                        picked != null -> "${picked.format(DayDate)}: " + when {
                            picked !in marks -> stringResource(R.string.stats_days_no_entry)
                            marks[picked]?.takeIf { it > 0 } == null -> stringResource(R.string.stats_days_no_rating)
                            else -> "${rating(marks.getValue(picked)!!)} / ${Rating.DAY_STARS}"
                        }
                        rated.isNotEmpty() -> stringResource(R.string.stats_days_year_avg, stars(rated.average()), Rating.DAY_STARS, marks.size)
                        else -> stringResource(R.string.stats_days_year_empty)
                    },
                )
                // Запись без оценки — бледная клетка: видно, что день записан
                val unrated = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                YearHeatmap(
                    year = year,
                    today = today,
                    colorFor = { date ->
                        if (date !in marks) null else marks[date]?.takeIf { it > 0 }?.let(::ratingColor) ?: unrated
                    },
                    selected = picked,
                    onSelect = { selectedMapDay = it },
                )
                HeatmapLegend(
                    stringResource(R.string.stats_quality_low),
                    stringResource(R.string.stats_quality_high),
                    listOf(RatingBad, RatingOkay, RatingGood, RatingPerfect, RatingSecret),
                )
            }
        }
    }
}

/** Средняя оценка из половинок: «7,4». */
private fun stars(halves: Double): String = "%.1f".format(halves / 2)

/** Оценка одного дня: «8,5», «7» — запятая, как в средних. */
private fun rating(halves: Int): String = if (halves % 2 == 0) (halves / 2).toString() else "%.1f".format(halves / 2.0)

/**
 * Оценка по дням: столбец в цвете оценки, бледная линия — среднее. Тап по дню или палец,
 * ведомый вбок, выбирает день; тап мимо — снимает выбор (это делает страница).
 */
@Composable
private fun RatingChart(
    ratings: Map<LocalDate, Int>,
    average: Double,
    range: ClosedRange<LocalDate>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val axisPx = with(LocalDensity.current) { AXIS_WIDTH.dp.toPx() }
    val days = ChronoUnit.DAYS.between(range.start, range.endInclusive).toInt() + 1
    val dates = ratings.keys.toList()
    // Шкала до 10; если есть секретные 11 из 10 — до 11
    val max = if (ratings.values.any(Rating::isSecret)) 22 else 20
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .pointerInput(ratings, range) {
                detectTapGestures { offset ->
                    val slot = ((offset.x - axisPx) / ((size.width - axisPx) / days)).toInt().coerceIn(0, days - 1)
                    nearestWithin(dates, range.start.plusDays(slot.toLong()), maxOf(1L, days / 60L))?.let(onSelect)
                }
            }
            .pointerInput(ratings, range) {
                detectHorizontalDragGestures { change, _ ->
                    if (dates.isEmpty()) return@detectHorizontalDragGestures
                    change.consume()
                    val slot = ((change.position.x - axisPx) / ((size.width - axisPx) / days)).toInt().coerceIn(0, days - 1)
                    nearestWithin(dates, range.start.plusDays(slot.toLong()), days.toLong())?.let(onSelect)
                }
            },
    ) {
        fun y(halves: Double) = size.height - (halves / max * size.height).toFloat()
        listOf(0, 10, 20).forEach { halves ->
            val yy = y(halves.toDouble())
            drawLine(colors.outlineVariant, Offset(axisPx, yy), Offset(size.width, yy), strokeWidth = 1f)
            val label = measurer.measure((halves / 2).toString(), labelStyle)
            drawText(label, topLeft = Offset(0f, (yy - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)))
        }
        drawLine(
            colors.onSurfaceVariant.copy(alpha = 0.7f),
            Offset(axisPx, y(average)),
            Offset(size.width, y(average)),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
        val slot = (size.width - axisPx) / days
        val width = (slot * 0.66f).coerceAtLeast(1.5f)
        ratings.forEach { (date, halves) ->
            val index = ChronoUnit.DAYS.between(range.start, date).toInt()
            if (index !in 0 until days) return@forEach
            val top = y(halves.toDouble())
            val alpha = if (selected == null || selected == date) 1f else 0.35f
            drawRoundRect(
                ratingColor(halves).copy(alpha = alpha),
                topLeft = Offset(axisPx + slot * index + (slot - width) / 2, top),
                size = Size(width, (size.height - top).coerceAtLeast(2f)),
                cornerRadius = CornerRadius(width / 2, width / 2),
            )
        }
    }
}

/** Сколько дней с каждой оценкой: столбцы 1…10 (и 11, если была), над столбцом — число дней. */
@Composable
private fun DistributionChart(distribution: Map<Int, Int>) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val countStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurface)
    val buckets = (if (0 in distribution) 0 else 1)..(if (11 in distribution) 11 else 10)
    val max = distribution.values.maxOrNull() ?: 1
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val labelHeight = 18.dp.toPx()
        val countHeight = 16.dp.toPx()
        val chartHeight = size.height - labelHeight - countHeight
        val slot = size.width / buckets.count()
        val width = slot * 0.6f
        buckets.forEachIndexed { i, bucket ->
            val count = distribution[bucket] ?: 0
            val x = slot * i + (slot - width) / 2
            val barHeight = if (count == 0) 0f else (count.toFloat() / max * chartHeight).coerceAtLeast(3f)
            val top = countHeight + chartHeight - barHeight
            val color = if (bucket == 11) RatingSecret else ratingColor(bucket * 2)
            drawRoundRect(color, Offset(x, top), Size(width, barHeight), CornerRadius(6f, 6f))
            if (count > 0) {
                val text = measurer.measure(count.toString(), countStyle)
                drawText(text, topLeft = Offset(slot * i + (slot - text.size.width) / 2, top - text.size.height))
            }
            val label = measurer.measure(bucket.toString(), labelStyle)
            drawText(label, topLeft = Offset(slot * i + (slot - label.size.width) / 2, size.height - label.size.height))
        }
    }
}

/** Средняя оценка по дням недели, с первого дня недели из настроек; над столбцом — значение. */
@Composable
private fun WeekdayChart(byWeekday: Map<DayOfWeek, Double>, firstDay: DayOfWeek) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val valueStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurface)
    val order = (0..6).map { firstDay.plus(it.toLong()) }
    val names = order.map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val labelHeight = 18.dp.toPx()
        val valueHeight = 16.dp.toPx()
        val chartHeight = size.height - labelHeight - valueHeight
        val slot = size.width / 7
        val width = slot * 0.55f
        order.forEachIndexed { i, day ->
            val value = byWeekday[day]
            val x = slot * i + (slot - width) / 2
            if (value != null) {
                val barHeight = (value / 22 * chartHeight).toFloat().coerceAtLeast(3f)
                val top = valueHeight + chartHeight - barHeight
                drawRoundRect(ratingColor(value.toInt()), Offset(x, top), Size(width, barHeight), CornerRadius(8f, 8f))
                val text = measurer.measure(stars(value), valueStyle)
                drawText(text, topLeft = Offset(slot * i + (slot - text.size.width) / 2, top - text.size.height))
            }
            val label = measurer.measure(names[i], labelStyle)
            drawText(label, topLeft = Offset(slot * i + (slot - label.size.width) / 2, size.height - label.size.height))
        }
    }
}

/** Забирает остаток прокрутки и разгона, чтобы он не уходил странице. */
private val KeepScroll = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/** Высота строки тега в линейчатой диаграмме. */
private val TagRow = 32.dp

/** Сколько тегов видно сразу; остальные — прокруткой внутри карточки. */
private const val VISIBLE_TAGS = 5

/**
 * Теги линейчатой диаграммой: название, полоса по числу дней (число на ней) и средняя оценка
 * таких дней в её цвете. Видно первые 10, дальше — прокрутка внутри карточки.
 */
@Composable
private fun TagBars(tags: List<TagStat>) {
    val colors = MaterialTheme.colorScheme
    val max = tags.maxOf { it.days }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = TagRow * VISIBLE_TAGS + 4.dp * (VISIBLE_TAGS - 1))
            // Долистали теги до края — страница дальше не едет: остаток прокрутки и разгона забираем себе
            .nestedScroll(KeepScroll)
            .verticalScroll(rememberScrollState()),
    ) {
        tags.forEach { stat ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(TagRow)) {
                Text(
                    stat.tag.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(96.dp).padding(end = 8.dp),
                )
                // Полоса по числу дней, число — сразу за ней
                val share = stat.days.toFloat() / max
                Row(Modifier.weight(1f).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(share)
                            .height(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.primary.copy(alpha = 0.75f)),
                    )
                    Text(
                        stat.days.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    if (share < 1f) Spacer(Modifier.weight(1f - share))
                }
                Text(
                    stat.averageRating?.let(::stars) ?: "-",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (stat.averageRating != null) colors.onSurface else colors.onSurfaceVariant,
                    modifier = Modifier.width(44.dp).padding(start = 8.dp),
                )
            }
        }
    }
}
