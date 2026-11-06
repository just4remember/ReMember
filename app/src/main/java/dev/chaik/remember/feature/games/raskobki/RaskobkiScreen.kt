package dev.chaik.remember.feature.games.raskobki

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.layout.ContentScale
import coil3.svg.SvgDecoder
import coil3.request.ImageRequest
import coil3.compose.AsyncImage
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.core.reminders.NotificationIds
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.raskobki.RaskobkiGrade
import dev.chaik.remember.core.raskobki.RaskobkiLoad
import dev.chaik.remember.core.raskobki.RaskobkiStart
import dev.chaik.remember.core.raskobki.RaskobkiState
import dev.chaik.remember.core.raskobki.RaskobkiZone
import dev.chaik.remember.core.raskobki.raskobkiStreak
import dev.chaik.remember.core.raskobki.raskobkiToday
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.core.ui.components.StreakFlame
import dev.chaik.remember.core.ui.components.streakColor
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object RaskobkiRoute : NavKey

private val CellShape = RoundedCornerShape(10.dp)
private val CellSpacing = 4.dp
private const val WEEKS_IN_GRID = 6
private val SWIPE_THRESHOLD = 48.dp

/** Сайт игры: выпуски, которых нет в приложении, имя и аватар. */
internal fun openRaskobkiSite(context: Context, path: String = "") = openRaskobkiLink(context, "https://raskobki.app/$path")

/** Поддержать автора игры. */
internal const val RASKOBKI_PATREON = "https://www.patreon.com/cw/Raskobki"
internal const val RASKOBKI_BOOSTY = "https://boosty.to/raskobki"

/** Открыть ссылку в браузере (блокировка приложения при этом не срабатывает). */
internal fun openRaskobkiLink(context: Context, url: String) {
    context.appContainer.appLock.allowExternal()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
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
    val context = LocalContext.current
    val repository = context.appContainer.raskobki
    // Напоминание могло прийти, пока приложение уже было открыто: зашли в игру — оно не нужно
    LaunchedEffect(Unit) { NotificationManagerCompat.from(context).cancel(NotificationIds.RASKOBKI) }
    val progress by repository.progress.collectAsState()
    val account by repository.account.collectAsState()
    val ready by repository.ready.collectAsState()
    var calendar by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val today by produceState(raskobkiToday()) {
        while (true) {
            value = raskobkiToday()
            delay(1_000)
        }
    }
    val days = ChronoUnit.DAYS.between(RaskobkiStart, today).toInt() + 1
    val pager = rememberPagerState(initialPage = days - 1) { days }
    val haptics = LocalHapticFeedback.current
    val gameHaptics = LocalAppSettings.current.gameHaptics
    LaunchedEffect(pager, gameHaptics) {
        snapshotFlow { pager.currentPage }.drop(1).collect {
            if (gameHaptics) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(stringResource(R.string.raskobki_home_title), fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text(
                stringResource(R.string.raskobki_home_lead),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                StreakCard(streak.size, ready, Modifier.weight(1f).fillMaxHeight())
                CountdownCard(Modifier.weight(1f).fillMaxHeight())
            }
            DaySwitcher(
                pager = pager,
                onBack = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                onForward = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                onCalendar = { calendar = true },
            )
            // Двигается только дата, карточка стоит и обновляется, когда день выбран
            val day = RaskobkiStart.plusDays(pager.settledPage.toLong())
            DayCard(
                day = day,
                state = progress[day],
                ready = ready,
                modifier = Modifier.weight(1f),
                onOpen = { onOpenDay(day) },
                onSwipe = { step ->
                    val target = pager.currentPage + step
                    if (target in 0 until days) scope.launch { pager.animateScrollToPage(target) }
                },
            )
        }
    }
    if (calendar) {
        CalendarDialog(
            today = today,
            progress = progress,
            streak = streak,
            onPick = {
                calendar = false
                scope.launch { pager.scrollToPage(ChronoUnit.DAYS.between(RaskobkiStart, it).toInt()) }
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
        shape = RoundedCornerShape(16.dp),
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
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        modifier = modifier,
    )
}

@Composable
private fun StreakCard(days: Int, ready: Boolean, modifier: Modifier = Modifier) {
    RaskobkiCard(modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RaskobkiKicker(stringResource(R.string.raskobki_streak))
            if (ready) {
                StreakFlame(days, suffix = pluralStringResource(R.plurals.raskobki_days_word, days))
            } else {
                Text("—", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            CardHint(stringResource(R.string.raskobki_streak_hint))
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
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RaskobkiKicker(stringResource(R.string.raskobki_next_layer))
            Text(
                "%02d:%02d:%02d".format(left / 3600, left % 3600 / 60, left % 60),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(vertical = 2.dp),
            )
            CardHint(stringResource(R.string.raskobki_next_layer_hint))
        }
    }
}

/** Мелкая подпись карточки, как на сайте. */
@Composable
private fun CardHint(text: String) {
    Text(
        text,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        minLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Стрелки стоят на месте, свайпом листается сама дата; нажатие на дату открывает календарь. */
@Composable
private fun DaySwitcher(pager: PagerState, onBack: () -> Unit, onForward: () -> Unit, onCalendar: () -> Unit) {
    RaskobkiCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = pager.currentPage > 0) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.raskobki_previous_day))
            }
            HorizontalPager(state = pager, key = { it }, modifier = Modifier.weight(1f)) { page ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = stringResource(R.string.raskobki_calendar), onClick = onCalendar)
                        .padding(vertical = 10.dp),
                ) {
                    Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        RaskobkiStart.plusDays(page.toLong()).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault())),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            IconButton(onClick = onForward, enabled = pager.currentPage < pager.pageCount - 1) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.raskobki_next_day))
            }
        }
    }
}

@Composable
private fun DayCard(day: LocalDate, state: RaskobkiState?, ready: Boolean, modifier: Modifier, onOpen: () -> Unit, onSwipe: (Int) -> Unit) {
    val repository = LocalContext.current.appContainer.raskobki
    val load by produceState<RaskobkiLoad?>(null, day) { value = repository.puzzle(day) }
    val puzzle = (load as? RaskobkiLoad.Ready)?.puzzle
    // Картинка выпуска, как на карточке дня на сайте; скачивается один раз
    val art by produceState<java.io.File?>(null, puzzle) { value = puzzle?.let { repository.cardArt(it) } }
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val threshold = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
    val swipe by rememberUpdatedState(onSwipe)
    RaskobkiCard(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            // Сама карточка не едет, но свайп по ней тоже меняет день
            .pointerInput(Unit) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        if (abs(dragged) > threshold) swipe(if (dragged < 0) 1 else -1)
                    },
                ) { change, amount ->
                    change.consume()
                    dragged += amount
                }
            }
            .clickable(enabled = ready, onClick = onOpen),
    ) {
        // Кнопка всегда внизу карточки: длинный текст прокручивается над ней
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
            Text(
                when {
                    !ready -> "—"
                    state?.c == true -> stringResource(R.string.raskobki_found)
                    else -> stringResource(R.string.raskobki_waiting)
                },
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            puzzle?.cardText?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            // Картинка выпуска — как на сайте, без подложки; между текстом карточки и эпохой с регионом
            art?.let { file ->
                AsyncImage(
                    model = remember(file) { ImageRequest.Builder(context).data(file).decoderFactory(SvgDecoder.Factory()).build() },
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(ART_HEIGHT),
                )
            }
            if (puzzle != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PuzzleLabel(stringResource(R.string.raskobki_era), puzzle.era, Modifier.weight(1f))
                    PuzzleLabel(stringResource(R.string.raskobki_region), puzzle.region, Modifier.weight(1f))
                }
            }
            }
            Button(onClick = onOpen, enabled = ready, modifier = Modifier.fillMaxWidth()) {
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

@Composable
private fun PuzzleLabel(label: String, value: String, modifier: Modifier = Modifier) {
    // Подпись и значение одной строкой: «ЭПОХА: Девяностые»; не влезает — шрифт сам меньше
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)) {
                append(label.uppercase())
                append(":")
            }
            append(" ")
            withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(value) }
        },
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
        modifier = modifier,
    )
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
                    else CalendarCell(day, today, progress[day], totals[day], day in streak, streak.size, onPick, Modifier.size(cell))
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
    streakDays: Int,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val open = day in RaskobkiStart..today
    val done = state?.c == true
    val marker = when {
        inStreak -> null
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
            if (inStreak) {
                Icon(Icons.Filled.LocalFireDepartment, null, tint = streakColor(streakDays) ?: colors.primary, modifier = Modifier.size(16.dp))
            } else if (marker != null) {
                Text(marker, fontSize = 9.sp, lineHeight = 10.sp, color = colors.primary)
            }
        }
    }
}

/** Высота картинки выпуска на карточке дня. */
private val ART_HEIGHT = 100.dp
