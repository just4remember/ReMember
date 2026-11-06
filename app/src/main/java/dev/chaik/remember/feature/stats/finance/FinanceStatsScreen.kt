package dev.chaik.remember.feature.stats.finance

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import dev.chaik.remember.core.model.MoneyCategory
import dev.chaik.remember.core.model.MoneyType
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.settings.logicalToday
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.core.ui.components.daySwipeOffset
import dev.chaik.remember.core.ui.components.rememberDaySwipe
import dev.chaik.remember.core.ui.format.MoneyFormat
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
data object FinanceStatsRoute : NavKey

/** Раньше этой даты статистику не листаем. */
private val EARLIEST: LocalDate = LocalDate.of(2000, 1, 1)

/** Доходы и расходы — зелёный и красный, как в деньгах на «Сегодня». */
private val IncomeColor = Color(0xFF4CAF50)
private val ExpenseColor = Color(0xFFEF5350)

/** Цвета категорий без своего цвета — по очереди. */
private val CategoryPalette = listOf(
    0xFF5B6CF0, 0xFF26A69A, 0xFFFFB300, 0xFFEC407A, 0xFF8D6E63, 0xFF42A5F5, 0xFFAB47BC, 0xFF66BB6A, 0xFFFF7043, 0xFF78909C,
).map { Color(it) }

// Каждый раз заново: после смены языка приложения даты должны быть на новом языке
private val DayDate: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
// Каждый раз заново: после смены языка приложения даты должны быть на новом языке
private val ShortDate: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
// Каждый раз заново: после смены языка приложения даты должны быть на новом языке
private val MonthName: DateTimeFormatter get() = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())

/**
 * Статистика финансов: расходы, доходы, баланс, средний расход в день, сравнение с прошлым
 * периодом, самая крупная трата; доходы и расходы по времени, категории, темп трат за месяц,
 * дни недели и год тепловой картой расходов.
 */
@Composable
fun FinanceStatsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val settings = LocalAppSettings.current
    val today = settings.logicalToday()
    val currency = settings.currency
    val repository = container.moneyRepository
    var period by rememberSaveable { mutableStateOf(StatsPeriod.MONTH) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    var categoryType by rememberSaveable { mutableStateOf(MoneyType.EXPENSE) }

    // Всё время — с первой операции; ищем её во всём, что есть
    val everything by remember { repository.observeRange(EARLIEST, today.plusYears(1)) }.collectAsStateWithLifecycle(emptyList())
    val first = everything.minOfOrNull { it.date }
    val range = period.range(today, offset, first, settings.firstDayOfWeek)
    val previousRange = if (period == StatsPeriod.ALL) null else period.range(today, offset + 1, first, settings.firstDayOfWeek)
    val categories by remember { repository.observeAllCategories() }.collectAsStateWithLifecycle(emptyList())
    val categoryById = remember(categories) { categories.associateBy { it.id } }

    val inCurrency = remember(everything, currency) { everything.filter { it.currency == currency } }
    val summary = remember(inCurrency, range, today) { FinanceStats.summary(inCurrency, range, today) }
    val previousExpense = remember(inCurrency, previousRange) {
        previousRange?.let { r -> inCurrency.filter { it.type == MoneyType.EXPENSE && it.date in r }.sumOf { it.amountMinor } }
    }
    val step = FinanceStats.stepFor(range)
    val flow = remember(inCurrency, range, step) { FinanceStats.flow(inCurrency, range, step, settings.firstDayOfWeek) }

    var selectedFlow by remember(range) { mutableStateOf<Int?>(null) }
    var selectedPaceDay by remember(range) { mutableStateOf<Int?>(null) }
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
    val (dateSwipe, dateGesture) = rememberDaySwipe(canGoForward = canLater) { s -> if (s < 0) earlier() else later() }
    val (yearSwipe, yearGesture) = rememberDaySwipe(canGoForward = year < today.year) { s ->
        year = (year + s.toInt()).coerceIn(EARLIEST.year, today.year)
    }
    fun money(minor: Long) = MoneyFormat.format(minor, currency)
    // В плитках — без копеек: «−10 186 ₽» помещается в одну строку
    fun whole(minor: Long) = MoneyFormat.format(Math.round(minor / 100.0) * 100, currency)

    ScreenScaffold(title = stringResource(R.string.stats_finance), modifier = modifier, onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .onTapAnywhere {
                    selectedFlow = null
                    selectedPaceDay = null
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

            if (summary.expenseMinor == 0L && summary.incomeMinor == 0L) {
                Text(
                    stringResource(R.string.stats_sleep_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            } else {
                TileRow {
                    StatTile(stringResource(R.string.stats_finance_expense), whole(summary.expenseMinor), it)
                    StatTile(stringResource(R.string.stats_finance_income), whole(summary.incomeMinor), it)
                }
                TileRow {
                    val balance = summary.balanceMinor
                    StatTile(
                        stringResource(R.string.stats_finance_balance),
                        (if (balance > 0) "+" else if (balance < 0) "−" else "") + whole(abs(balance)),
                        it,
                    )
                    StatTile("${stringResource(R.string.stats_finance_daily)} ${stringResource(R.string.stats_average)}", whole(summary.dailyExpenseMinor), it)
                }
                TileRow {
                    val change = previousExpense?.let { FinanceStats.change(summary.expenseMinor, it) }
                    StatTile(
                        stringResource(R.string.stats_finance_vs_previous),
                        withUnit(change?.let { c -> (if (c > 0) "+" else if (c < 0) "−" else "") + abs(c) } ?: "-", if (change != null) "%" else ""),
                        it,
                        note = previousExpense?.takeIf { e -> e > 0 }?.let { e -> stringResource(R.string.stats_finance_previous_was, whole(e)) },
                    )
                    val biggest = summary.biggestExpense
                    StatTile(
                        stringResource(R.string.stats_finance_biggest),
                        biggest?.let { b -> whole(b.amountMinor) } ?: "-",
                        it,
                        note = biggest?.let { b ->
                            listOfNotNull(b.categoryId?.let { id -> categoryById[id]?.name }, b.date.format(ShortDate)).joinToString(" · ")
                        },
                    )
                }

                StatsCard(stringResource(R.string.stats_finance_flow)) {
                    val point = selectedFlow?.let(flow::getOrNull)
                    Caption(
                        point?.let {
                            val label = when (step) {
                                FlowStep.DAY -> it.start.format(DayDate)
                                FlowStep.WEEK -> "${it.start.format(ShortDate)} - ${it.end.format(ShortDate)}"
                                FlowStep.MONTH -> it.start.format(MonthName)
                            }
                            stringResource(R.string.stats_finance_flow_selected, label, money(it.incomeMinor), money(it.expenseMinor))
                        } ?: stringResource(
                            when (step) {
                                FlowStep.DAY -> R.string.stats_finance_flow_days
                                FlowStep.WEEK -> R.string.stats_finance_flow_weeks
                                FlowStep.MONTH -> R.string.stats_finance_flow_months
                            },
                        ),
                    )
                    FlowChart(flow, selectedFlow) { selectedFlow = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(IncomeColor, stringResource(R.string.stats_finance_income))
                        LegendDot(ExpenseColor, stringResource(R.string.stats_finance_expense))
                        LegendDot(MaterialTheme.colorScheme.onSurface, stringResource(R.string.stats_finance_balance))
                    }
                }

                StatsCard(stringResource(R.string.stats_finance_categories)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(MoneyType.EXPENSE to R.string.stats_finance_expense, MoneyType.INCOME to R.string.stats_finance_income)
                            .forEach { (type, label) ->
                                FilterChip(
                                    selected = categoryType == type,
                                    onClick = { categoryType = type },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                    label = { Text(stringResource(label)) },
                                )
                            }
                    }
                    val shares = if (categoryType == MoneyType.EXPENSE) summary.expenseCategories else summary.incomeCategories
                    if (shares.isEmpty()) {
                        Caption(stringResource(R.string.stats_no_data))
                    } else {
                        val colors = shares.mapIndexed { i, share -> categoryColor(share.categoryId?.let(categoryById::get), i) }
                        Donut(shares.map { it.amountMinor }, colors)
                        CategoryBars(shares, colors, categoryById, ::money)
                    }
                }

                // Темп трат — только на месяце: сравниваем с прошлым месяцем день в день
                if (period == StatsPeriod.MONTH) {
                    val current = remember(inCurrency, range, today) { FinanceStats.cumulative(inCurrency, range.start, today) }
                    val previous = remember(inCurrency, range) {
                        FinanceStats.cumulative(inCurrency, range.start.minusMonths(1), range.start.minusDays(1))
                    }
                    if (current.isNotEmpty()) {
                        StatsCard(stringResource(R.string.stats_finance_pace)) {
                            val day = (selectedPaceDay ?: current.lastIndex).coerceAtMost(current.lastIndex)
                            val then = previous.getOrNull(day) ?: previous.lastOrNull()
                            Caption(
                                if (then == null || then == 0L) {
                                    stringResource(R.string.stats_finance_pace_alone, day + 1, money(current[day]))
                                } else {
                                    val diff = FinanceStats.change(current[day], then) ?: 0
                                    stringResource(
                                        if (diff <= 0) R.string.stats_finance_pace_less else R.string.stats_finance_pace_more,
                                        day + 1,
                                        money(current[day]),
                                        abs(diff),
                                    )
                                },
                            )
                            PaceChart(current, previous, range.start.lengthOfMonth(), selectedPaceDay) { selectedPaceDay = it }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                LegendDot(MaterialTheme.colorScheme.primary, stringResource(R.string.stats_finance_this_month))
                                LegendDot(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), stringResource(R.string.stats_finance_last_month))
                            }
                        }
                    }
                }

                StatsCard(stringResource(R.string.stats_finance_weekdays)) {
                    WeekdayChart(summary.byWeekday, settings.firstDayOfWeek)
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        listOf(R.string.stats_weekdays to summary.weekdaysDailyMinor, R.string.stats_weekends to summary.weekendsDailyMinor)
                            .forEach { (label, value) ->
                                Column {
                                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(value?.let(::money) ?: "-", style = MaterialTheme.typography.titleLarge)
                                }
                            }
                    }
                }
            }

            StatsCard(stringResource(R.string.stats_finance_year)) {
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
                val spent = remember(inCurrency, year) {
                    inCurrency.filter { it.type == MoneyType.EXPENSE && it.date.year == year }
                        .groupBy { it.date }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }
                }
                val thresholds = remember(spent) { FinanceStats.quartiles(spent.values) }
                val picked = selectedMapDay
                Caption(
                    when {
                        picked != null -> "${picked.format(DayDate)}: " + (spent[picked]?.let(::money) ?: stringResource(R.string.stats_finance_no_spend))
                        spent.isNotEmpty() -> stringResource(R.string.stats_finance_year_summary, money(spent.values.sum()), spent.size)
                        else -> stringResource(R.string.stats_finance_year_empty)
                    },
                )
                val levels = listOf(0.25f, 0.5f, 0.75f, 1f).map { MaterialTheme.colorScheme.primary.copy(alpha = it) }
                YearHeatmap(
                    year = year,
                    today = today,
                    colorFor = { date -> FinanceStats.level(spent[date] ?: 0, thresholds).takeIf { it > 0 }?.let { levels[it - 1] } },
                    selected = picked,
                    onSelect = { selectedMapDay = it },
                )
                HeatmapLegend(stringResource(R.string.stats_finance_less), stringResource(R.string.stats_finance_more), levels)
            }
        }
    }
}

private fun categoryColor(category: MoneyCategory?, index: Int): Color =
    category?.color?.let { Color(it.toInt()) } ?: CategoryPalette[index % CategoryPalette.size]

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Короткая подпись суммы для осей: 12 500 ₽ → «12,5k». */
private fun compact(minor: Long): String {
    val units = minor / 100.0
    return when {
        abs(units) >= 1_000_000 -> "%.1fM".format(units / 1_000_000)
        abs(units) >= 10_000 -> "${Math.round(units / 1000)}k"
        abs(units) >= 1000 -> "%.1fk".format(units / 1000)
        else -> Math.round(units).toString()
    }
}

/**
 * Доходы и расходы по шагам: пара столбцов (зелёный — доход, красный — расход)
 * и линия баланса по шагам. Тап по шагу — его суммы.
 */
@Composable
private fun FlowChart(points: List<FlowPoint>, selected: Int?, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val axisPx = with(androidx.compose.ui.platform.LocalDensity.current) { AXIS_WIDTH.dp.toPx() }
    val top = maxOf(points.maxOfOrNull { maxOf(it.incomeMinor, it.expenseMinor, it.balanceMinor) } ?: 0, 1)
    val bottom = minOf(points.minOfOrNull { it.balanceMinor } ?: 0, 0)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(points) {
                fun pick(x: Float) = ((x - axisPx) / ((size.width - axisPx) / points.size)).toInt().coerceIn(0, points.lastIndex)
                detectTapGestures { onSelect(pick(it.x)) }
            }
            .pointerInput(points) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onSelect(((change.position.x - axisPx) / ((size.width - axisPx) / points.size)).toInt().coerceIn(0, points.lastIndex))
                }
            },
    ) {
        val span = (top - bottom).toFloat()
        fun y(v: Long) = (top - v) / span * size.height
        listOf(top, 0L, bottom).distinct().forEach { v ->
            val yy = y(v)
            drawLine(colors.outlineVariant, Offset(axisPx, yy), Offset(size.width, yy), strokeWidth = 1f)
            val label = measurer.measure(compact(v), labelStyle)
            drawText(label, topLeft = Offset(0f, (yy - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)))
        }
        val slot = (size.width - axisPx) / points.size
        val bar = (slot * 0.36f).coerceAtLeast(1f)
        val zero = y(0)
        val line = Path()
        points.forEachIndexed { i, p ->
            val alpha = if (selected == null || selected == i) 1f else 0.35f
            val x = axisPx + slot * i + slot / 2
            if (p.incomeMinor > 0) {
                drawRoundRect(IncomeColor.copy(alpha = alpha), Offset(x - bar, y(p.incomeMinor)), Size(bar, zero - y(p.incomeMinor)), CornerRadius(bar / 3))
            }
            if (p.expenseMinor > 0) {
                drawRoundRect(ExpenseColor.copy(alpha = alpha), Offset(x, y(p.expenseMinor)), Size(bar, zero - y(p.expenseMinor)), CornerRadius(bar / 3))
            }
            if (i == 0) line.moveTo(x, y(p.balanceMinor)) else line.lineTo(x, y(p.balanceMinor))
        }
        if (points.size > 1) drawPath(line, colors.onSurface.copy(alpha = 0.8f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Кольцо долей категорий. */
@Composable
private fun Donut(amounts: List<Long>, colors: List<Color>) {
    val total = amounts.sum().coerceAtLeast(1).toFloat()
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(170.dp)) {
            val stroke = 26.dp.toPx()
            val inset = stroke / 2
            var start = -90f
            amounts.forEachIndexed { i, amount ->
                val sweep = amount / total * 360f
                drawArc(
                    colors[i],
                    startAngle = start,
                    // Тонкий зазор между долями, если долей больше одной
                    sweepAngle = if (amounts.size > 1) (sweep - 1.5f).coerceAtLeast(0.5f) else sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke),
                )
                start += sweep
            }
        }
    }
}

/** Забирает остаток прокрутки и разгона, чтобы он не уходил странице. */
private val KeepScroll = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/** Высота строки категории. */
private val CategoryRow = 40.dp

/** Сколько категорий видно сразу; остальные — прокруткой внутри карточки. */
private const val VISIBLE_CATEGORIES = 5

/** Категории полосами: цвет, название, полоса по сумме, сумма и доля, число операций. */
@Composable
private fun CategoryBars(
    shares: List<CategoryShare>,
    colors: List<Color>,
    categories: Map<Long, MoneyCategory>,
    money: (Long) -> String,
) {
    val total = shares.sumOf { it.amountMinor }.coerceAtLeast(1)
    val max = shares.maxOf { it.amountMinor }.coerceAtLeast(1)
    val noCategory = stringResource(R.string.money_no_category)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = CategoryRow * VISIBLE_CATEGORIES + 4.dp * (VISIBLE_CATEGORIES - 1))
            .nestedScroll(KeepScroll)
            .verticalScroll(rememberScrollState()),
    ) {
        shares.forEachIndexed { i, share ->
            val category = share.categoryId?.let(categories::get)
            Column(Modifier.height(CategoryRow), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i]))
                    Text(
                        listOfNotNull(category?.icon, category?.name ?: noCategory).joinToString(" "),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(
                        "${money(share.amountMinor)} · ${share.amountMinor * 100 / total}%",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 18.dp, top = 3.dp)) {
                    val fraction = share.amountMinor.toFloat() / max
                    Box(Modifier.weight(fraction.coerceAtLeast(0.01f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors[i]))
                    Text(
                        pluralCount(share.count),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    if (fraction < 1f) Spacer(Modifier.weight(1f - fraction))
                }
            }
        }
    }
}

@Composable
private fun pluralCount(count: Int): String =
    androidx.compose.ui.res.pluralStringResource(R.plurals.stats_finance_operations, count, count)

/** Накопленные траты по дням: этот месяц — акцентом, прошлый — бледной линией. Тап — день. */
@Composable
private fun PaceChart(current: List<Long>, previous: List<Long>, days: Int, selected: Int?, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val axisPx = with(androidx.compose.ui.platform.LocalDensity.current) { AXIS_WIDTH.dp.toPx() }
    val max = maxOf(current.maxOrNull() ?: 0, previous.maxOrNull() ?: 0, 1)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .pointerInput(current) {
                fun pick(x: Float) = ((x - axisPx) / ((size.width - axisPx) / (days - 1).coerceAtLeast(1)) + 0.5f).toInt().coerceIn(0, current.lastIndex)
                detectTapGestures { onSelect(pick(it.x)) }
            }
            .pointerInput(current) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onSelect(((change.position.x - axisPx) / ((size.width - axisPx) / (days - 1).coerceAtLeast(1)) + 0.5f).toInt().coerceIn(0, current.lastIndex))
                }
            },
    ) {
        fun x(day: Int) = axisPx + (size.width - axisPx) * day / (days - 1).coerceAtLeast(1)
        fun y(v: Long) = size.height - v.toFloat() / max * size.height
        listOf(0L, max / 2, max).forEach { v ->
            val yy = y(v)
            drawLine(colors.outlineVariant, Offset(axisPx, yy), Offset(size.width, yy), strokeWidth = 1f)
            val label = measurer.measure(compact(v), labelStyle)
            drawText(label, topLeft = Offset(0f, (yy - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)))
        }
        fun line(values: List<Long>, color: Color, width: Float) {
            if (values.isEmpty()) return
            val path = Path()
            values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round))
        }
        line(previous, colors.onSurfaceVariant.copy(alpha = 0.5f), 2.dp.toPx())
        line(current, colors.primary, 3.dp.toPx())
        val day = selected ?: current.lastIndex
        drawCircle(colors.primary, radius = 5.dp.toPx(), center = Offset(x(day), y(current[day])))
    }
}

/** Средний расход в день по дням недели, с первого дня недели из настроек. */
@Composable
private fun WeekdayChart(byWeekday: Map<DayOfWeek, Long>, firstDay: DayOfWeek) {
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
            val value = byWeekday[day] ?: 0
            if (value > 0) {
                val barHeight = (value.toFloat() / max * chartHeight).coerceAtLeast(3f)
                val top = valueHeight + chartHeight - barHeight
                drawRoundRect(colors.primary, Offset(slot * i + (slot - width) / 2, top), Size(width, barHeight), CornerRadius(8f, 8f))
                val text = measurer.measure(compact(value), valueStyle)
                drawText(text, topLeft = Offset(slot * i + (slot - text.size.width) / 2, top - text.size.height))
            }
            val label = measurer.measure(names[i], labelStyle)
            drawText(label, topLeft = Offset(slot * i + (slot - label.size.width) / 2, size.height - label.size.height))
        }
    }
}
