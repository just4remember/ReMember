package dev.chaik.remember.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.feature.stats.sleep.StatsPeriod
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs

// Общие детали страниц статистики: карточка, плитка с цифрой, выбор периода, тепловая карта года.

@Composable
fun StatsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

/** Плитка с одной цифрой: подпись сверху, значение крупно, пояснение снизу. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, note: String? = null) =
    StatTile(label, AnnotatedString(value), modifier, note)

/** То же, но значение с оформлением — например, единицы мельче чисел. */
@Composable
fun StatTile(label: String, value: AnnotatedString, modifier: Modifier = Modifier, note: String? = null) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall)
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Тап в любое место (не прокрутка) — [onTap]. Срабатывает раньше, чем вложенные элементы:
 * тап мимо столбца или клетки снимает выделение, а по столбцу — сразу выделяет новый.
 * Жест не перезапускается при перерисовке, а вызывает всегда свежий [onTap].
 */
@Composable
fun Modifier.onTapAnywhere(onTap: () -> Unit): Modifier {
    val current by rememberUpdatedState(onTap)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val up = waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
            if ((up.position - down.position).getDistance() < viewConfiguration.touchSlop) current()
        }
    }
}

private val RangeDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val RangeDateYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

/** Периоды одной строкой (не влезли — листаются вбок); выбранный — в акценте приложения. */
@Composable
fun PeriodChips(period: StatsPeriod, onPeriod: (StatsPeriod) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        StatsPeriod.entries.forEach { option ->
            FilterChip(
                selected = option == period,
                onClick = { onPeriod(option) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.primaryContainer,
                    selectedLabelColor = colors.onPrimaryContainer,
                ),
                label = {
                    Text(
                        stringResource(
                            when (option) {
                                StatsPeriod.WEEK -> R.string.stats_week
                                StatsPeriod.MONTH -> R.string.stats_month
                                StatsPeriod.QUARTER -> R.string.stats_quarter
                                StatsPeriod.YEAR -> R.string.stats_year
                                StatsPeriod.ALL -> R.string.stats_all
                            },
                        ),
                    )
                },
            )
        }
    }
}

/** Даты периода и стрелки на такой же отрезок назад или вперёд («всё время» не листается). */
@Composable
fun PeriodDates(range: ClosedRange<LocalDate>, canEarlier: Boolean, canLater: Boolean, onEarlier: () -> Unit, onLater: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onEarlier, enabled = canEarlier) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.stats_earlier))
        }
        val sameYear = range.start.year == range.endInclusive.year
        Text(
            text = "${range.start.format(if (sameYear) RangeDate else RangeDateYear)} - ${range.endInclusive.format(RangeDateYear)}",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onLater, enabled = canLater) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.stats_later))
        }
    }
}

/** Год со стрелками — для тепловой карты. */
@Composable
fun YearSwitcher(year: Int, canEarlier: Boolean, canLater: Boolean, onYear: (Int) -> Unit) {
    // Левая стрелка — вровень с текстом карточки: у кнопки свои поля
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(x = (-12).dp)) {
        IconButton(onClick = { onYear(year - 1) }, enabled = canEarlier) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.stats_earlier))
        }
        Text(year.toString(), style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = { onYear(year + 1) }, enabled = canLater) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.stats_later))
        }
    }
}

private val Cell = 13.dp
private val Gap = 3.dp

/**
 * Тепловая карта года, как на GitHub: столбец — неделя, строка — день недели, цвет — значение дня.
 * Год — с 1 января по 31 декабря; дни после [today] — бледные пустые клетки.
 * [colorFor] — цвет клетки дня или null, если в этот день ничего нет.
 */
@Composable
fun YearHeatmap(
    year: Int,
    today: LocalDate,
    colorFor: (LocalDate) -> Color?,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
) {
    val firstDay = LocalAppSettings.current.firstDayOfWeek
    val yearStart = LocalDate.of(year, 1, 1)
    val yearEnd = LocalDate.of(year, 12, 31)
    val start = yearStart.with(TemporalAdjusters.previousOrSame(firstDay))
    val weeks = (ChronoUnit.DAYS.between(start, yearEnd) / 7 + 1).toInt()
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val scroll = rememberScrollState()
    val stepPx = with(LocalDensity.current) { (Cell + Gap).toPx() }
    // Сегодняшняя неделя — ближе к правому краю; прошлый год — его конец
    LaunchedEffect(year, scroll.maxValue) {
        val todayWeek = if (today.year == year) ChronoUnit.DAYS.between(start, today) / 7 else weeks.toLong()
        val target = (todayWeek * stepPx - scroll.viewportSize * 0.8f).toInt()
        scroll.scrollTo(target.coerceIn(0, scroll.maxValue))
    }
    val weekdays = remember(firstDay) {
        (0..6).map { firstDay.plus(it.toLong()).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }

    Row {
        // Подписи дней недели — через одну, как на GitHub
        Column(Modifier.padding(top = 18.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(Gap)) {
            weekdays.forEachIndexed { i, name ->
                Box(Modifier.height(Cell), contentAlignment = Alignment.CenterStart) {
                    if (i % 2 == 0) Text(name, style = MaterialTheme.typography.labelSmall, color = labelColor)
                }
            }
        }
        // Запас справа — чтобы название последнего месяца не обрезалось
        Row(Modifier.horizontalScroll(scroll).padding(end = 20.dp), horizontalArrangement = Arrangement.spacedBy(Gap)) {
            (0 until weeks).forEach { week ->
                val weekStart = start.plusWeeks(week.toLong())
                val days = (0..6).map { weekStart.plusDays(it.toLong()) }
                Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
                    // Месяц — над неделей, где он начинается
                    val month = days.firstOrNull { it.dayOfMonth == 1 && it.year == year }?.month
                        ?: if (week == 0) yearStart.month else null
                    Box(Modifier.height(15.dp).width(Cell)) {
                        if (month != null) {
                            Text(
                                month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault()),
                                style = MaterialTheme.typography.labelSmall,
                                color = labelColor,
                                softWrap = false,
                                // Название месяца шире клетки — выходит вправо, над следующими неделями
                                modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
                            )
                        }
                    }
                    days.forEach { date ->
                        when {
                            date.year != year -> Spacer(Modifier.size(Cell))
                            date.isAfter(today) -> Box(Modifier.size(Cell).clip(RoundedCornerShape(3.dp)).background(empty.copy(alpha = 0.35f)))
                            else -> Box(
                                Modifier
                                    .size(Cell)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(colorFor(date) ?: empty)
                                    .then(if (date == selected) Modifier.background(Color.White.copy(alpha = 0.35f)) else Modifier)
                                    .clickable { onSelect(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Легенда под тепловой картой: «Хуже ▢▢▢▢▢ Лучше». */
@Composable
fun HeatmapLegend(low: String, high: String, colors: List<Color>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(low, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(3.dp))
        colors.forEach { Box(Modifier.size(11.dp).clip(RoundedCornerShape(2.dp)).background(it)) }
        Spacer(Modifier.width(3.dp))
        Text(high, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Ближайшая дата из [dates] к [date], если она не дальше [tolerance] дней. */
fun nearestWithin(dates: List<LocalDate>, date: LocalDate, tolerance: Long): LocalDate? =
    dates.minByOrNull { abs(ChronoUnit.DAYS.between(it, date)) }?.takeIf { abs(ChronoUnit.DAYS.between(it, date)) <= tolerance }
