package dev.chaik.remember.feature.settings.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.data.DayStreak
import dev.chaik.remember.core.data.withPause
import dev.chaik.remember.core.data.withoutPause
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.settings.logicalToday
import dev.chaik.remember.feature.settings.SettingsCard
import dev.chaik.remember.feature.settings.SettingsGroupTitle
import dev.chaik.remember.feature.settings.SettingsSwitchRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Самая длинная пауза серии, дней от сегодня. */
private const val PAUSE_MAX_DAYS = 365L

/** Огонёк серии дневника: показывать ли, запасной день и пауза. */
@Composable
fun StreakSettings() {
    val store = LocalContext.current.appContainer.settings
    val settings = LocalAppSettings.current
    val today = settings.logicalToday()
    val pause = DayStreak.activePause(settings.streakRules, today)
    var picking by rememberSaveable { mutableStateOf(false) }

    SettingsGroupTitle(stringResource(R.string.profile_streak_title))
    SettingsCard {
        SettingsSwitchRow(
            title = stringResource(R.string.profile_streak_show),
            hint = stringResource(R.string.profile_streak_show_hint),
            checked = settings.streak,
            onChange = { on -> store.update { it.copy(streak = on) } },
        )
        if (settings.streak) {
            SettingsSwitchRow(
                title = stringResource(R.string.profile_streak_spare),
                hint = stringResource(R.string.profile_streak_spare_hint),
                checked = settings.streakSpare,
                onChange = { on -> store.update { it.copy(streakSpare = on) } },
            )
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 4.dp)) {
                Text(stringResource(R.string.profile_streak_pause), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (pause != null) {
                        stringResource(R.string.profile_streak_pause_until, pause.end.format(DateTimeFormatter.ofPattern("d MMMM")))
                    } else {
                        stringResource(R.string.profile_streak_pause_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                // Текст кнопок вровень с подписью: у TextButton свой отступ 12dp
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.offset(x = (-12).dp),
                ) {
                    TextButton(onClick = { picking = true }) {
                        Text(stringResource(if (pause != null) R.string.profile_streak_pause_change else R.string.profile_streak_pause_start))
                    }
                    if (pause != null) {
                        TextButton(onClick = { store.update { it.copy(streakPauses = it.streakPauses.withoutPause(today)) } }) {
                            Text(stringResource(R.string.profile_streak_pause_stop))
                        }
                    }
                }
            }
        }
    }
    if (picking) {
        PauseEndDialog(
            today = today,
            initial = pause?.end ?: today.plusDays(6),
            onPick = { end ->
                picking = false
                store.update { it.copy(streakPauses = it.streakPauses.withPause(today, end)) }
            },
            onDismiss = { picking = false },
        )
    }
}

/** До какого дня пауза: с сегодня и не дальше года. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PauseEndDialog(today: LocalDate, initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // DatePicker работает с полночью по UTC
    fun millis(day: LocalDate) = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val first = millis(today)
    val last = millis(today.plusDays(PAUSE_MAX_DAYS))
    val selectable = remember(first, last) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in first..last
            override fun isSelectableYear(year: Int) = year in today.year..today.plusDays(PAUSE_MAX_DAYS).year
        }
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = millis(initial), selectableDates = selectable)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(
            state = state,
            title = { Text(stringResource(R.string.profile_streak_pause_pick), modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
        )
    }
}
