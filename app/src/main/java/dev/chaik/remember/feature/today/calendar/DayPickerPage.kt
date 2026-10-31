package dev.chaik.remember.feature.today.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.model.Rating
import dev.chaik.remember.core.model.Tag
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.settings.logicalToday
import dev.chaik.remember.core.ui.components.FullScreenPage
import dev.chaik.remember.core.ui.theme.ratingColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private val CellShape = RoundedCornerShape(10.dp)
private val RowShape = RoundedCornerShape(16.dp)

/**
 * Выбор дня — полноэкранная страница: месяцы лентой сверху вниз (старые сверху),
 * прокрутка вверх — в прошлое. Тап по дню сразу открывает его. Будущие дни недоступны.
 * В клетках — цвет и подпись оценки дня; заполненный день без оценки слегка подсвечен.
 * Кнопка в шапке переключает сетку и список дней с тегами (выбор запоминается).
 * Закрыть — крестик, «назад» или свайп.
 *
 * Раньше это был нижний лист (ModalBottomSheet), но на резком свайпе вверх
 * он «пружинил» вверх-вниз — поэтому отдельная страница.
 *
 * @param marks дни с записью → оценка в половинках (null — без оценки)
 * @param tags дни с тегами → их теги
 * @param firstMonth самый ранний месяц в ленте
 */
@Composable
fun DayPickerPage(
    selected: LocalDate,
    marks: Map<LocalDate, Int?>,
    tags: Map<LocalDate, List<Tag>>,
    firstMonth: YearMonth,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val store = LocalContext.current.appContainer.settings
    val asList = LocalAppSettings.current.dayPickerList
    val today = LocalAppSettings.current.logicalToday()
    val months = remember(firstMonth, today) {
        generateSequence(firstMonth) { it.plusMonths(1) }.takeWhile { it <= YearMonth.from(today) }.toList()
    }
    // Открываемся на месяце выбранного дня; у конца ленты список сам упрётся так,
    // что текущий месяц окажется внизу экрана
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = months.indexOf(YearMonth.from(selected)).coerceAtLeast(0),
    )
    val firstDayOfWeek = LocalAppSettings.current.firstDayOfWeek
    val haptics = LocalHapticFeedback.current

    FullScreenPage(
        title = stringResource(R.string.today_pick_date),
        onDismiss = onDismiss,
        actions = {
            IconButton(onClick = { store.update { it.copy(dayPickerList = !asList) } }) {
                Icon(
                    imageVector = if (asList) Icons.Outlined.CalendarMonth else Icons.AutoMirrored.Outlined.ViewList,
                    contentDescription = stringResource(if (asList) R.string.today_calendar_grid else R.string.today_calendar_list),
                )
            }
        },
    ) {
        if (asList) {
            DayList(
                selected = selected,
                today = today,
                firstMonth = firstMonth,
                marks = marks,
                tags = tags,
                onPick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onPick(it)
                },
                modifier = Modifier.weight(1f),
            )
            return@FullScreenPage
        }
        WeekdayHeader(firstDayOfWeek, Modifier.padding(horizontal = 16.dp))
        HorizontalDivider(Modifier.padding(top = 8.dp))
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding(),
        ) {
            items(months, key = { it.toString() }) { month ->
                MonthGrid(
                    month = month,
                    firstDayOfWeek = firstDayOfWeek,
                    today = today,
                    selected = selected,
                    marks = marks,
                    onPick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onPick(it)
                    },
                )
            }
        }
    }
}

/** Дни списка — те же границы, что у сетки: от начала [firstMonth] до сегодня, новые сверху. */
internal fun listedDays(today: LocalDate, firstMonth: YearMonth): List<LocalDate> =
    generateSequence(today) { it.minusDays(1) }.takeWhile { !it.isBefore(firstMonth.atDay(1)) }.toList()

/** Список дней по месяцам, новые сверху: дата, оценка и теги под ней. */
@Composable
private fun DayList(
    selected: LocalDate,
    today: LocalDate,
    firstMonth: YearMonth,
    marks: Map<LocalDate, Int?>,
    tags: Map<LocalDate, List<Tag>>,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val months = remember(today, firstMonth) { listedDays(today, firstMonth).groupBy(YearMonth::from).toList() }
    // Сразу у выбранного дня: заголовки месяцев — тоже элементы списка
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = remember(months) {
            var index = 0
            for ((_, days) in months) {
                val at = days.indexOf(selected)
                if (at >= 0) return@remember index + 1 + at
                index += 1 + days.size
            }
            0
        },
    )
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.navigationBarsPadding(),
    ) {
        months.forEach { (month, days) ->
            item(key = month.toString()) {
                Text(
                    text = month.format(DateTimeFormatter.ofPattern("LLLL yyyy")).replaceFirstChar { it.titlecase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                )
            }
            items(days, key = { it.toString() }) { date ->
                DayRow(date, marks[date], tags[date].orEmpty(), date == selected, onClick = { onPick(date) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayRow(date: LocalDate, rating: Int?, tags: List<Tag>, isSelected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val accent = rating?.let(::ratingColor)
    val title = remember(date) {
        date.format(DateTimeFormatter.ofPattern("d MMMM, EEEE"))
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(colors.surfaceContainerHigh)
            .then(if (isSelected) Modifier.border(2.dp, colors.primary, RowShape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (rating != null && accent != null) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Text(
                    text = "${Rating.format(rating)}/10",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { tag ->
                    Text(
                        text = tag.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSecondaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.secondaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader(firstDayOfWeek: DayOfWeek, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(7) { i ->
            val day = firstDayOfWeek.plus(i.toLong())
            Text(
                text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.labelMedium,
                color = if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek,
    today: LocalDate,
    selected: LocalDate,
    marks: Map<LocalDate, Int?>,
    onPick: (LocalDate) -> Unit,
) {
    val title = remember(month) {
        month.format(DateTimeFormatter.ofPattern("LLLL yyyy")).replaceFirstChar { it.titlecase() }
    }
    // Сколько пустых клеток до первого числа
    val offset = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val cells: List<LocalDate?> = List(offset) { null } + (1..month.lengthOfMonth()).map(month::atDay)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
        )
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        DayCell(
                            date = date,
                            hasEntry = marks.containsKey(date),
                            rating = marks[date],
                            isToday = date == today,
                            isSelected = date == selected,
                            enabled = !date.isAfter(today),
                            onClick = { onPick(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    hasEntry: Boolean,
    rating: Int?,
    isToday: Boolean,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val accent = rating?.let(::ratingColor)
    // Фон листа — surfaceContainerLow, поэтому пустые клетки на тон светлее, чтобы квадраты были видны
    val background = when {
        accent != null -> accent.copy(alpha = 0.3f)
        hasEntry -> colors.surfaceContainerHighest
        enabled -> colors.surfaceContainerHigh
        else -> Color.Transparent
    }
    val border = when {
        isSelected -> Modifier.border(2.dp, colors.primary, CellShape)
        isToday -> Modifier.border(1.dp, colors.outline, CellShape)
        else -> Modifier
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(0.85f)
            .clip(CellShape)
            .background(background)
            .then(border)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.35f),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isToday || hasEntry) FontWeight.Bold else null,
            )
            if (rating != null && accent != null) {
                RatingLabel(rating, accent)
            }
        }
    }
}

/** «★ 7.5/10» мелким шрифтом под числом. */
@Composable
private fun RatingLabel(rating: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = color, modifier = Modifier.size(9.dp))
        Text(
            text = "${Rating.format(rating)}/10",
            color = color,
            fontSize = 8.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
    }
}
