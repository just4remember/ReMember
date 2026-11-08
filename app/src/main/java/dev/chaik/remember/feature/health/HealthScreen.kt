package dev.chaik.remember.feature.health

import dev.chaik.remember.core.ui.releaseFocus
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.health.DayActivity
import dev.chaik.remember.core.health.HealthGoals
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.core.ui.components.daySwipeOffset
import dev.chaik.remember.core.ui.components.rememberDaySwipe
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
data object HealthRoute : NavKey

/** Сколько недель назад можно пролистать — 10 лет. */
private const val WEEK_PAGES = 520

/**
 * Здоровье: сверху неделя с мини-кольцами (листается в прошлое), по центру три кольца
 * выбранного дня — шаги, активность, калории, — под ними цифры. В «⋮» — цели.
 */
@Composable
fun HealthScreen(onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = context.appContainer
    val viewModel = viewModel { HealthViewModel(container) }
    val goals by viewModel.goals.collectAsStateWithLifecycle()

    // Вернулись из Health Connect или из фона — перечитать (сегодняшние шаги растут)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val askPermission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.refresh()
    }

    var menu by remember { mutableStateOf(false) }
    var editingGoals by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = stringResource(R.string.section_health),
        modifier = modifier,
        onBack = onBack,
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.gym_more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.health_goals)) },
                        leadingIcon = { Icon(Icons.Outlined.Flag, null) },
                        onClick = {
                            menu = false
                            editingGoals = true
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            when (val state = viewModel.state) {
                HealthState.Loading -> Unit
                HealthState.Ready -> {
                    WeekStrip(viewModel, goals)
                    // Свайп вбок по дню (заголовок, кольца, цифры) — соседний день; едет только дата
                    val (swipe, swipeGesture) = rememberDaySwipe(canGoForward = viewModel.selected.isBefore(LocalDate.now())) { days ->
                        viewModel.select(viewModel.selected.plusDays(days))
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(swipeGesture),
                    ) {
                        DayDetails(
                            day = viewModel.selected,
                            activity = viewModel.days[viewModel.selected],
                            goals = goals,
                            titleModifier = Modifier.daySwipeOffset(swipe),
                        )
                    }
                }
                HealthState.NoPermission -> Message(
                    title = stringResource(R.string.health_connect_title),
                    text = stringResource(R.string.health_connect_text),
                    button = stringResource(R.string.health_connect_button),
                    onClick = {
                        container.appLock.allowExternal()
                        askPermission.launch(container.health.permissions)
                    },
                )
                is HealthState.Unavailable -> Message(
                    title = stringResource(if (state.needsUpdate) R.string.health_update else R.string.health_unavailable),
                    text = "",
                    button = if (state.needsUpdate) stringResource(R.string.health_update_button) else null,
                    onClick = {
                        container.appLock.allowExternal()
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")),
                        )
                    },
                )
                is HealthState.Failed -> Message(
                    title = stringResource(R.string.health_error, state.message),
                    text = "",
                    button = null,
                    onClick = {},
                )
            }
        }
    }

    if (editingGoals) {
        GoalsDialog(
            goals = goals,
            onSave = {
                viewModel.saveGoals(it)
                editingGoals = false
            },
            onDismiss = { editingGoals = false },
        )
    }
}

/** Неделя с понедельника: день недели, мини-кольца, число. Свайп — прошлые недели. */
@Composable
private fun WeekStrip(viewModel: HealthViewModel, goals: HealthGoals) {
    val today = LocalDate.now()
    val firstDay = LocalAppSettings.current.firstDayOfWeek
    val currentMonday = remember(today, firstDay) { weekStart(today, firstDay) }
    val pager = rememberPagerState(initialPage = WEEK_PAGES - 1 - weeksBetween(weekStart(viewModel.selected, firstDay), currentMonday)) { WEEK_PAGES }
    val mondayOf = { page: Int -> currentMonday.minusWeeks((WEEK_PAGES - 1 - page).toLong()) }

    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page -> viewModel.loadWeek(mondayOf(page)) }
    }
    // Свайпом по кольцам ушли в другую неделю — полоска недели листается следом
    LaunchedEffect(viewModel.selected) {
        val page = WEEK_PAGES - 1 - weeksBetween(weekStart(viewModel.selected, firstDay), currentMonday)
        if (page != pager.currentPage) pager.animateScrollToPage(page)
    }

    HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth(), key = { it }) { page ->
        val monday = mondayOf(page)
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            (0L..6L).map { monday.plusDays(it) }.forEach { day ->
                DayCell(
                    day = day,
                    activity = viewModel.days[day],
                    goals = goals,
                    selected = day == viewModel.selected,
                    isToday = day == today,
                    enabled = !day.isAfter(today),
                    onClick = { viewModel.select(day) },
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    activity: DayActivity?,
    goals: HealthGoals,
    selected: Boolean,
    isToday: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.secondaryContainer else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .alpha(if (enabled) 1f else 0.35f),
    ) {
        Text(
            text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.titlecase() },
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
        ActivityRings(
            progress = activity?.progress(goals) ?: listOf(0f, 0f, 0f),
            stroke = 3.5.dp,
            gap = 1.5.dp,
            animate = false,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = day.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) colors.primary else colors.onSurface,
        )
    }
}

/** Заголовок дня, большие кольца и три цифры под ними. */
@Composable
private fun DayDetails(day: LocalDate, activity: DayActivity?, goals: HealthGoals, titleModifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val locale = Locale.getDefault()
    val title = if (day == today) {
        stringResource(R.string.calendar_today_day, day.format(DateTimeFormatter.ofPattern("d MMMM", locale)))
    } else {
        day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "EEEE, d MMMM" else "EEEE, d MMMM yyyy", locale))
            .replaceFirstChar { it.titlecase() }
    }
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = titleModifier.padding(top = 12.dp, bottom = 20.dp),
    )
    ActivityRings(
        progress = activity?.progress(goals) ?: listOf(0f, 0f, 0f),
        modifier = Modifier
            .widthIn(max = 280.dp)
            .fillMaxWidth(0.7f)
            .aspectRatio(1f),
    )
    val number = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, start = 8.dp, end = 8.dp),
    ) {
        Stat(
            color = StepsColor,
            label = stringResource(R.string.health_steps),
            value = activity?.let { number.format(it.steps) } ?: "—",
            goal = stringResource(R.string.health_of_steps, number.format(goals.steps)),
        )
        Stat(
            color = MinutesColor,
            label = stringResource(R.string.health_minutes),
            value = activity?.let { "${it.activeMinutes} ${stringResource(R.string.health_unit_minutes)}" } ?: "—",
            goal = stringResource(R.string.health_of_minutes, number.format(goals.activeMinutes)),
        )
        Stat(
            color = CaloriesColor,
            label = stringResource(R.string.health_calories),
            value = activity?.let { "${number.format(Math.round(it.calories))} ${stringResource(R.string.health_unit_calories)}" } ?: "—",
            goal = stringResource(R.string.health_of_calories, number.format(goals.calories)),
        )
    }
}

@Composable
private fun Stat(color: Color, label: String, value: String, goal: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        Text(goal, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Message(title: String, text: String, button: String?, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 48.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (text.isNotEmpty()) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (button != null) Button(onClick = onClick) { Text(button) }
    }
}

/** Цели: шаги, минуты активности, активные калории. */
@Composable
private fun GoalsDialog(goals: HealthGoals, onSave: (HealthGoals) -> Unit, onDismiss: () -> Unit) {
    var steps by remember { mutableStateOf(goals.steps.toString()) }
    var minutes by remember { mutableStateOf(goals.activeMinutes.toString()) }
    var calories by remember { mutableStateOf(goals.calories.toString()) }
    val result = HealthGoals(
        steps = steps.toLongOrNull()?.takeIf { it > 0 } ?: 0,
        activeMinutes = minutes.toIntOrNull()?.takeIf { it > 0 } ?: 0,
        calories = calories.toIntOrNull()?.takeIf { it > 0 } ?: 0,
    )
    val valid = result.steps > 0 && result.activeMinutes > 0 && result.calories > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.health_goals_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.releaseFocus()) {
                GoalField(stringResource(R.string.health_goal_steps), steps, StepsColor) { steps = it }
                GoalField(stringResource(R.string.health_goal_minutes), minutes, MinutesColor) { minutes = it }
                GoalField(stringResource(R.string.health_goal_calories), calories, CaloriesColor) { calories = it }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(result) }) { Text(stringResource(R.string.gym_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun GoalField(label: String, value: String, color: Color, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.length <= 6 && v.all(Char::isDigit)) onChange(v) },
        label = { Text(label) },
        leadingIcon = {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Доли от целей: шаги, минуты, калории. */
private fun DayActivity.progress(goals: HealthGoals): List<Float> = listOf(
    steps.toFloat() / goals.steps.coerceAtLeast(1),
    activeMinutes.toFloat() / goals.activeMinutes.coerceAtLeast(1),
    (calories / goals.calories.coerceAtLeast(1)).toFloat(),
)

private fun weeksBetween(from: LocalDate, to: LocalDate): Int =
    ChronoUnit.WEEKS.between(from, to).toInt().coerceIn(0, WEEK_PAGES - 1)
