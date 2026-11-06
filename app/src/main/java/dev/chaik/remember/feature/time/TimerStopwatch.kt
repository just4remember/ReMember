package dev.chaik.remember.feature.time

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.alarm.formatDuration
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.ui.components.WheelPicker
import kotlinx.coroutines.delay

/** Текущее время, обновляется каждый кадр, пока [active]. */
@Composable
private fun rememberNow(active: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        now = System.currentTimeMillis()
        while (active) withFrameMillis { now = System.currentTimeMillis() }
    }
    return now
}

// ---- Таймер ----

/**
 * Таймер: стоит — часы, минуты и секунды барабанами, идёт или на паузе — обратный отсчёт с кольцом.
 */
@Composable
fun TimerTab(padding: PaddingValues) {
    val container = LocalContext.current.appContainer
    val controller = container.alarmController
    val timer by container.timeStore.timer.collectAsStateWithLifecycle()
    val now = rememberNow(timer.running)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = padding.calculateBottomPadding())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        if (timer.idle) {
            // Часы, минуты и секунды — отдельными барабанами
            val total = timer.durationMs / 1000
            var hours by rememberSaveable { mutableIntStateOf((total / 3600).toInt()) }
            var minutes by rememberSaveable { mutableIntStateOf((total % 3600 / 60).toInt()) }
            var seconds by rememberSaveable { mutableIntStateOf((total % 60).toInt()) }
            val duration = (hours * 3600L + minutes * 60L + seconds) * 1000
            // Запомнить выбранное время, когда барабаны остановились
            LaunchedEffect(duration) {
                delay(600)
                controller.setTimerDuration(duration)
            }
            Spacer(Modifier.weight(0.3f))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 360.dp)) {
                TimeWheel(hours, 0..23, stringResource(R.string.time_unit_h), Modifier.weight(1f)) { hours = it }
                TimeWheel(minutes, 0..59, stringResource(R.string.time_unit_m), Modifier.weight(1f)) { minutes = it }
                TimeWheel(seconds, 0..59, stringResource(R.string.time_unit_s), Modifier.weight(1f)) { seconds = it }
            }
            Spacer(Modifier.weight(0.4f))
            LargeFloatingActionButton(
                onClick = {
                    if (duration > 0) {
                        controller.setTimerDuration(duration)
                        controller.startTimer(duration)
                    }
                },
                containerColor = if (duration > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Icon(Icons.Outlined.PlayArrow, stringResource(R.string.time_start), modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(16.dp))
        } else {
            val left = timer.left(now)
            val total = timer.durationMs.coerceAtLeast(1)
            Spacer(Modifier.weight(0.3f))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f)) {
                CircularProgressIndicator(
                    progress = { (left.toFloat() / total).coerceIn(0f, 1f) },
                    strokeWidth = 10.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxSize(),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Округляем вверх: «0:01» до самого конца последней секунды
                    Text(formatDuration(left + 999), fontSize = 56.sp, fontWeight = FontWeight.Light)
                    if (timer.paused) Text(stringResource(R.string.time_paused), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.weight(0.4f))
            // Боковые кнопки — в равных ячейках по краям, главная остаётся ровно по центру
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.weight(1f)) {
                    FilledTonalIconButton(onClick = controller::resetTimer, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.time_reset))
                    }
                }
                LargeFloatingActionButton(onClick = if (timer.running) controller::pauseTimer else controller::resumeTimer) {
                    Icon(
                        if (timer.running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        stringResource(if (timer.running) R.string.time_pause else R.string.time_resume),
                        modifier = Modifier.size(36.dp),
                    )
                }
                Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.weight(1f)) {
                    FilledTonalButton(
                        onClick = controller::addMinute,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.height(56.dp),
                    ) {
                        Text(stringResource(R.string.time_plus_minute), maxLines = 1, softWrap = false)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Барабан с подписью единицы справа: «05 м». */
@Composable
private fun TimeWheel(value: Int, range: IntRange, unit: String, modifier: Modifier, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        WheelPicker(value = value, range = range, onChange = onChange, modifier = Modifier.weight(1f))
        Text(unit, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
    }
}

// ---- Секундомер ----

/** Секундомер с отсечками; идёт и в фоне — время считается от момента старта. */
@Composable
fun StopwatchTab(padding: PaddingValues) {
    val container = LocalContext.current.appContainer
    val controller = container.alarmController
    val sw by container.timeStore.stopwatch.collectAsStateWithLifecycle()
    val now = rememberNow(sw.running)
    val elapsed = sw.elapsed(now)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = padding.calculateBottomPadding())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text(stopwatchText(elapsed), fontSize = 64.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.Default)
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            val laps = sw.laps
            itemsIndexed(laps.reversed()) { index, total ->
                val number = laps.size - index
                val previous = laps.getOrNull(number - 2) ?: 0
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 8.dp)) {
                    Text(stringResource(R.string.time_lap, number), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(stopwatchText(total - previous), modifier = Modifier.weight(1f))
                    Text(stopwatchText(total), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // «Круг» / «Сброс» — справа от главной кнопки, у левши — слева; главная остаётся по центру
        val leftHanded = LocalAppSettings.current.leftHanded
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            val side = @Composable {
                RoundAction(
                    icon = if (sw.running) Icons.Outlined.Flag else Icons.Outlined.Refresh,
                    label = stringResource(if (sw.running) R.string.time_lap_action else R.string.time_reset),
                    enabled = !sw.idle,
                    onClick = if (sw.running) controller::lap else controller::resetStopwatch,
                )
            }
            if (leftHanded) side() else Spacer(Modifier.size(56.dp))
            LargeFloatingActionButton(onClick = if (sw.running) controller::pauseStopwatch else controller::startStopwatch) {
                Icon(
                    if (sw.running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    stringResource(if (sw.running) R.string.time_pause else R.string.time_start),
                    modifier = Modifier.size(36.dp),
                )
            }
            if (leftHanded) Spacer(Modifier.size(56.dp)) else side()
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Icon(icon, label, tint = if (enabled) colors.onSurface else colors.outline)
    }
}

/** 05:09.37 или 1:05:09.37 — с сотыми. */
private fun stopwatchText(ms: Long): String {
    val hundredths = ms / 10 % 100
    val total = ms / 1000
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, hundredths) else "%02d:%02d.%02d".format(m, s, hundredths)
}
