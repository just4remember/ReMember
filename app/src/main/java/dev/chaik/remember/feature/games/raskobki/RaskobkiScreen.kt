package dev.chaik.remember.feature.games.raskobki

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.raskobki.RaskobkiGrade
import dev.chaik.remember.core.raskobki.RaskobkiLoad
import dev.chaik.remember.core.raskobki.RaskobkiStart
import dev.chaik.remember.core.raskobki.RaskobkiState
import dev.chaik.remember.core.raskobki.RaskobkiZone
import dev.chaik.remember.core.raskobki.raskobkiStreak
import dev.chaik.remember.core.raskobki.raskobkiToday
import dev.chaik.remember.core.ui.components.ScreenScaffold
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object RaskobkiRoute : NavKey

private val CellShape = RoundedCornerShape(10.dp)
private val CellSpacing = 4.dp
private const val WEEKS_IN_GRID = 6

/** Сайт игры: выпуски, которых нет в приложении, имя и аватар. */
internal fun openRaskobkiSite(context: Context, path: String = "") {
    context.appContainer.appLock.allowExternal()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, "https://raskobki.app/$path".toUri()))
    } catch (_: ActivityNotFoundException) {
        // Браузера нет — остаёмся на месте
    }
}

/** Цвет скобок: свой из профиля или акцент темы. */
@Composable
internal fun raskobkiBracketColor(): Color {
    val custom by LocalContext.current.appContainer.raskobki.bracketColor.collectAsState()
    return custom?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
}

/** Цвет верных ответов: свой из профиля или цвет темы. */
@Composable
internal fun raskobkiAnswerColor(): Color {
    val custom by LocalContext.current.appContainer.raskobki.answerColor.collectAsState()
    return custom?.let { Color(it) } ?: MaterialTheme.colorScheme.tertiary
}

/** Название игры с толстыми скобками, как в тексте загадки. */
internal fun raskobkiTitle(title: String, accent: Color): AnnotatedString = buildAnnotatedString {
    title.forEach { char ->
        if (char == '[' || char == ']') withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Black)) { append(char) }
        else append(char)
    }
}

/** Ра[скобки]: находка дня, серия, календарь выпусков. */
@Composable
fun RaskobkiScreen(
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = LocalContext.current.appContainer.raskobki
    val progress by repository.progress.collectAsState()
    val account by repository.account.collectAsState()
    var calendar by remember { mutableStateOf(false) }
    val today by produceState(raskobkiToday()) {
        while (true) {
            value = raskobkiToday()
            delay(1_000)
        }
    }
    val load by produceState<RaskobkiLoad?>(null, today) { value = repository.puzzle(today) }
    val streak = remember(progress, today) { raskobkiStreak(progress, today) }
    // Обновляемся и при открытии экрана, и при возврате в приложение, например с сайта
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val metrics = LocalContext.current.appContainer.raskobkiMetrics
    LaunchedEffect(account?.uid, resumed) {
        if (resumed) {
            runCatching { repository.sync() }
            metrics.flush()
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.game_raskobki),
        styledTitle = raskobkiTitle(stringResource(R.string.game_raskobki), raskobkiBracketColor()),
        modifier = modifier,
        onBack = onBack,
        actions = {
            val current = account
            if (current == null) {
                TextButton(onClick = onOpenProfile) { Text(stringResource(R.string.raskobki_sign_in)) }
            } else {
                IconButton(onClick = onOpenProfile) { RaskobkiAvatar(current, 32) }
            }
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(
                stringResource(R.string.raskobki_home_title),
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
            )
            Text(
                stringResource(R.string.raskobki_home_lead),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StreakCard(streak.size, Modifier.weight(1f))
                CountdownCard(Modifier.weight(1f))
            }
            OutlinedButton(onClick = { calendar = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.raskobki_calendar))
            }
            DayCard(today, load, progress[today], onOpen = { onOpenDay(today) })
        }
    }
    if (calendar) {
        CalendarDialog(
            today = today,
            progress = progress,
            streak = streak,
            onPick = {
                calendar = false
                onOpenDay(it)
            },
            onDismiss = { calendar = false },
        )
    }
}

@Composable
private fun RaskobkiCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
        content = content,
    )
}

@Composable
internal fun RaskobkiKicker(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        modifier = modifier,
    )
}

@Composable
private fun StreakCard(days: Int, modifier: Modifier = Modifier) {
    RaskobkiCard(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RaskobkiKicker(stringResource(R.string.raskobki_streak))
            Text(
                "🔥 " + pluralStringResource(R.plurals.raskobki_days, days, days),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
            )
        }
    }
}

@Composable
private fun CountdownCard(modifier: Modifier = Modifier) {
    var left by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = ZonedDateTime.now(RaskobkiZone)
            left = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(RaskobkiZone)).seconds
            delay(1_000)
        }
    }
    RaskobkiCard(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RaskobkiKicker(stringResource(R.string.raskobki_next_layer))
            Text(
                "%02d:%02d:%02d".format(left / 3600, left % 3600 / 60, left % 60),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
        }
    }
}

@Composable
private fun DayCard(day: LocalDate, load: RaskobkiLoad?, state: RaskobkiState?, onOpen: () -> Unit) {
    val puzzle = (load as? RaskobkiLoad.Ready)?.puzzle
    RaskobkiCard(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onOpen)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RaskobkiKicker(day.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))))
            Text(
                stringResource(if (state?.c == true) R.string.raskobki_found else R.string.raskobki_waiting),
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
            puzzle?.cardText?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (puzzle != null) {
                Text(
                    "${puzzle.era} · ${puzzle.region}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                val label = when {
                    state?.c == true -> R.string.raskobki_show_result
                    state?.s.isNullOrEmpty() -> R.string.raskobki_start
                    else -> R.string.raskobki_continue
                }
                Text(stringResource(label))
                Spacer(Modifier.size(8.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarDialog(
    today: LocalDate,
    progress: Map<LocalDate, RaskobkiState>,
    streak: List<LocalDate>,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = YearMonth.from(RaskobkiStart)
    val months = ChronoUnit.MONTHS.between(first, YearMonth.from(today)).toInt() + 1
    val pager = rememberPagerState(initialPage = months - 1) { months }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    // Сколько всего скобок, узнаём из выпуска; незаконченные дни редки, а выпуски лежат в кеше
    val repository = LocalContext.current.appContainer.raskobki
    val unfinished = remember(progress) { progress.filterValues { !it.c && it.s.isNotEmpty() }.keys }
    val totals by produceState(emptyMap<LocalDate, Int>(), unfinished) {
        unfinished.forEach { day ->
            (repository.puzzle(day) as? RaskobkiLoad.Ready)?.let { value = value + (day to it.puzzle.total) }
        }
    }

    BasicAlertDialog(onDismissRequest = onDismiss, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Surface(color = colors.surfaceContainerLow, shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(12.dp)) {
                val month = first.plusMonths(pager.currentPage.toLong())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                        enabled = pager.currentPage > 0,
                    ) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.raskobki_previous_month)) }
                    Text(
                        month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())).replaceFirstChar { it.titlecase() },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        enabled = pager.currentPage < months - 1,
                    ) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.raskobki_next_month)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(CellSpacing), modifier = Modifier.padding(vertical = 6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        Text(
                            day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.titlecase() },
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cell = (maxWidth - CellSpacing * 6) / 7
                    // Высота постоянная — 6 недель, окно не прыгает при смене месяца
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.height(cell * WEEKS_IN_GRID + CellSpacing * (WEEKS_IN_GRID - 1)),
                        key = { it },
                    ) { page ->
                        MonthCells(first.plusMonths(page.toLong()), cell, today, progress, totals, streak, onPick)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.editor_close))
                }
            }
        }
    }
}

@Composable
private fun MonthCells(
    month: YearMonth,
    cell: Dp,
    today: LocalDate,
    progress: Map<LocalDate, RaskobkiState>,
    totals: Map<LocalDate, Int>,
    streak: List<LocalDate>,
    onPick: (LocalDate) -> Unit,
) {
    val offset = month.atDay(1).dayOfWeek.value - 1
    val days = List(offset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(verticalArrangement = Arrangement.spacedBy(CellSpacing)) {
        days.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(CellSpacing)) {
                week.forEach { day ->
                    if (day == null) Spacer(Modifier.size(cell))
                    else CalendarCell(day, today, progress[day], totals[day], day in streak, onPick, Modifier.size(cell))
                }
            }
        }
    }
}

/** Квадратная ячейка дня, как в большом календаре приложения. */
@Composable
private fun CalendarCell(
    day: LocalDate,
    today: LocalDate,
    state: RaskobkiState?,
    total: Int?,
    inStreak: Boolean,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val open = day in RaskobkiStart..today
    val done = state?.c == true
    val marker = when {
        inStreak -> "🔥"
        done -> RaskobkiGrade.of(state!!.score).roman
        !state?.s.isNullOrEmpty() && total != null -> "${state.s.size}/$total"
        else -> null
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(CellShape)
            .background(
                when {
                    done -> colors.primary.copy(alpha = 0.25f)
                    open -> colors.surfaceContainerHighest
                    else -> Color.Transparent
                },
            )
            .then(if (day == today) Modifier.border(1.5.dp, colors.primary, CellShape) else Modifier)
            .then(if (open) Modifier.clickable { onPick(day) } else Modifier),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (day == today) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    !open -> colors.outline.copy(alpha = 0.6f)
                    else -> colors.onSurface
                },
            )
            if (marker != null) {
                Text(marker, fontSize = 9.sp, lineHeight = 10.sp, color = colors.primary)
            }
        }
    }
}
