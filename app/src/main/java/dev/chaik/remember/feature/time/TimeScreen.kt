package dev.chaik.remember.feature.time

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.alarm.Alarm
import dev.chaik.remember.core.alarm.nextRing
import dev.chaik.remember.core.reminders.OpenTarget
import dev.chaik.remember.feature.time.world.WorldClockTab
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.settings.weekDays
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.core.ui.primaryCornerModifier
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

@Serializable
data object TimeRoute : NavKey

private enum class TimeTab(val label: Int) {
    ALARMS(R.string.time_tab_alarms),
    TIMER(R.string.time_tab_timer),
    STOPWATCH(R.string.time_tab_stopwatch),
    WORLD(R.string.time_tab_world),
}

/** «Время»: будильники, таймер, секундомер и мировое время — вкладки листаются и свайпом вбок. */
@Composable
fun TimeScreen(onBack: (() -> Unit)?, onOpenAlarm: (Long) -> Unit, onOpenWidgets: () -> Unit, modifier: Modifier = Modifier) {
    val pager = rememberPagerState { TimeTab.entries.size }
    val scope = rememberCoroutineScope()
    // Тап по виджету часов — сразу на мировое время
    val container = LocalContext.current.appContainer
    val openRequest by container.openRequests.collectAsStateWithLifecycle()
    LaunchedEffect(openRequest) {
        if (openRequest == OpenTarget.WorldClock) {
            pager.scrollToPage(TimeTab.WORLD.ordinal)
            container.openRequests.value = null
        }
    }
    ScreenScaffold(
        title = stringResource(R.string.section_time),
        modifier = modifier,
        onBack = onBack,
        actions = {
            // На вкладке «Мир» — быстрый переход к виджетам часов: там выбираются их города
            if (pager.currentPage == TimeTab.WORLD.ordinal) {
                IconButton(onClick = onOpenWidgets) {
                    Icon(Icons.Outlined.Widgets, stringResource(R.string.time_widgets_settings))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                TimeTab.entries.forEach { entry ->
                    SegmentedButton(
                        selected = pager.currentPage == entry.ordinal,
                        onClick = { scope.launch { pager.animateScrollToPage(entry.ordinal) } },
                        shape = SegmentedButtonDefaults.itemShape(entry.ordinal, TimeTab.entries.size),
                        icon = {},
                    ) { Text(stringResource(entry.label), maxLines = 1) }
                }
            }
            val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
            HorizontalPager(
                state = pager,
                // Листаем сами (см. pageSwipe): у края жест достаётся свайпу «назад»
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxSize()
                    .pageSwipe(pager, scope),
            ) { page ->
                when (TimeTab.entries[page]) {
                    TimeTab.ALARMS -> AlarmsTab(bottom, onOpenAlarm)
                    TimeTab.TIMER -> TimerTab(bottom)
                    TimeTab.STOPWATCH -> StopwatchTab(bottom)
                    TimeTab.WORLD -> WorldClockTab(bottom)
                }
            }
        }
    }
}

/**
 * Свайп вбок листает вкладки. Если в ту сторону вкладок больше нет, жест не трогаем —
 * его подхватывает свайп «назад» (SwipeBackHost), если он включён в эту сторону.
 */
private fun Modifier.pageSwipe(pager: PagerState, scope: CoroutineScope): Modifier = pointerInput(pager) {
    val flingVelocity = 400.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
        val slop = viewConfiguration.touchSlop
        var dx = 0f
        var dy = 0f
        var start: PointerInputChange? = null
        while (start == null) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (!change.pressed || change.isConsumed) return@awaitEachGesture
            tracker.addPosition(change.uptimeMillis, change.position)
            dx += change.positionChange().x
            dy += change.positionChange().y
            if (abs(dx) > slop || abs(dy) > slop) {
                // Вертикально — это прокрутка списка или барабана
                if (abs(dx) <= abs(dy) * 1.5f) return@awaitEachGesture
                // Палец влево — следующая вкладка, вправо — предыдущая; за краем — не наш жест
                val target = pager.currentPage + if (dx < 0) 1 else -1
                if (target !in 0 until pager.pageCount) return@awaitEachGesture
                change.consume()
                pager.dispatchRawDelta(-dx)
                start = change
            }
        }
        drag(start.id) { change ->
            pager.dispatchRawDelta(-change.positionChange().x)
            tracker.addPosition(change.uptimeMillis, change.position)
            change.consume()
        }
        // Отпустили: на ближайшую вкладку, а резкий свайп — дальше в его сторону
        val velocity = tracker.calculateVelocity().x
        val fraction = pager.currentPageOffsetFraction
        val page = when {
            velocity < -flingVelocity && fraction > 0f -> pager.currentPage + 1
            velocity > flingVelocity && fraction < 0f -> pager.currentPage - 1
            else -> pager.currentPage
        }.coerceIn(0, pager.pageCount - 1)
        scope.launch { pager.animateScrollToPage(page) }
    }
}

@Composable
private fun AlarmsTab(padding: PaddingValues, onOpenAlarm: (Long) -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val alarms by container.timeStore.alarms.collectAsStateWithLifecycle()
    // Разрешения могли выдать в настройках Android — проверяем при каждом возвращении
    var canNotify by remember { mutableStateOf(true) }
    var canFullScreen by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        canNotify = NotificationManagerCompat.from(context).areNotificationsEnabled()
        canFullScreen = container.timeNotifications.canUseFullScreen
        onPauseOrDispose {}
    }
    // Раз в минуту обновить «зазвонит через…»
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick++
        }
    }

    val next = remember(alarms, tick) {
        val now = ZonedDateTime.now()
        alarms.filter { it.enabled }.minOfOrNull { it.nextRing(now) }?.let { Duration.between(now, it) }
    }
    val nextText = next?.let { untilText(it) } ?: stringResource(R.string.time_no_alarms_on)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = padding.calculateBottomPadding() + 88.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!canNotify) {
                item {
                    PermissionBanner(stringResource(R.string.time_need_notifications)) {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }
                }
            } else if (!canFullScreen) {
                item {
                    PermissionBanner(stringResource(R.string.time_need_full_screen)) {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri()),
                        )
                    }
                }
            }
            item {
                Text(
                    text = nextText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            items(alarms, key = { it.id }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    onClick = { onOpenAlarm(alarm.id) },
                    onToggle = { container.alarmController.setEnabled(alarm.id, it) },
                )
            }
        }
        FloatingActionButton(
            onClick = { onOpenAlarm(0) },
            modifier = primaryCornerModifier(bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            Icon(Icons.Outlined.Add, stringResource(R.string.time_add_alarm))
        }
    }
}

@Composable
private fun untilText(left: Duration): String {
    val minutes = (left.toMinutes() + 1).coerceAtLeast(1)
    val hours = minutes / 60
    val rest = minutes % 60
    val h = pluralStringResource(R.plurals.time_hours, hours.toInt(), hours.toInt())
    val m = pluralStringResource(R.plurals.time_minutes, rest.toInt(), rest.toInt())
    val parts = listOfNotNull(h.takeIf { hours > 0 }, m.takeIf { rest > 0 || hours == 0L })
    return stringResource(R.string.time_rings_in, parts.joinToString(" "))
}

@Composable
private fun AlarmCard(alarm: Alarm, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val faded = if (alarm.enabled) colors.onSurface else colors.outline
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "%02d:%02d".format(alarm.hour, alarm.minute),
                fontSize = 40.sp,
                fontWeight = FontWeight.Light,
                color = faded,
            )
            val details = listOfNotNull(alarm.label.takeIf { it.isNotBlank() }, daysText(alarm.days))
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        if (alarm.hard) Icon(Icons.Outlined.Extension, stringResource(R.string.time_hard), tint = faded, modifier = Modifier.padding(end = 8.dp).size(20.dp))
        if (alarm.snooze) Icon(Icons.Outlined.Snooze, stringResource(R.string.time_snooze), tint = faded, modifier = Modifier.padding(end = 12.dp).size(20.dp))
        Switch(checked = alarm.enabled, onCheckedChange = onToggle)
    }
}

/** «Каждый день», «По будням», «Пн, Ср, Пт» или «Один раз». */
@Composable
fun daysText(days: Set<Int>): String {
    val weekdays = (1..5).toSet()
    return when (days) {
        emptySet<Int>() -> stringResource(R.string.time_once)
        (1..7).toSet() -> stringResource(R.string.time_every_day)
        weekdays -> stringResource(R.string.time_weekdays)
        setOf(6, 7) -> stringResource(R.string.time_weekends)
        else -> weekDays(LocalAppSettings.current.firstDayOfWeek)
            .filter { it.value in days }
            .joinToString(", ") { dayName(it) }
    }
}

fun dayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.titlecase() }

@Composable
private fun PermissionBanner(text: String, onAllow: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.errorContainer)
            .padding(16.dp),
    ) {
        Icon(Icons.Outlined.NotificationsOff, null, tint = colors.onErrorContainer)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onErrorContainer,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Button(onClick = onAllow) { Text(stringResource(R.string.reminders_allow)) }
    }
}
