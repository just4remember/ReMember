package dev.chaik.remember.feature.settings.reminders

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.reminders.DailyReminder
import dev.chaik.remember.core.reminders.QuietMode
import dev.chaik.remember.core.reminders.SoundMode
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.settings.weekDays
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.core.ui.components.TimePickerDialog
import dev.chaik.remember.feature.settings.SettingsCard
import dev.chaik.remember.feature.settings.SettingsGroupTitle
import dev.chaik.remember.feature.settings.SettingsRadioRow
import dev.chaik.remember.feature.settings.SettingsSwitchRow
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
data object RemindersRoute : NavKey

/** Какое время сейчас выбирается в диалоге. */
private enum class TimeField { Day, Sleep, Summary, Raskobki, QuietStart, QuietEnd }

/**
 * Настройки напоминаний: отчёт за день, сон, события, задачи, утренняя сводка,
 * тихие часы и звук. Меняется сразу — расписание пересчитывается само.
 */
@Composable
fun RemindersScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = context.appContainer
    val store = container.reminderSettings
    val settings by store.settings.collectAsStateWithLifecycle()

    // Разрешение могли выдать или забрать в настройках Android — проверяем при каждом возвращении
    var canPost by remember { mutableStateOf(container.notifier.canPost) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canPost = container.notifier.canPost }
    val openSystemSettings = {
        container.appLock.allowExternal()
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canPost = granted
        // Android больше не показывает запрос после двух отказов — ведём в его настройки
        if (!granted) openSystemSettings()
    }

    var picking by remember { mutableStateOf<TimeField?>(null) }

    ScreenScaffold(title = stringResource(R.string.settings_reminders), modifier = modifier, onBack = onBack) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp)
                .padding(horizontal = 16.dp),
        ) {
            if (!canPost) {
                PermissionBanner(onAllow = { askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) })
            }

            SettingsCard {
                SettingsSwitchRow(
                    title = stringResource(R.string.reminders_all),
                    checked = settings.enabled,
                    onChange = { on -> store.update { it.copy(enabled = on) } },
                )
            }

            AnimatedVisibility(visible = settings.enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsGroupTitle(stringResource(R.string.reminders_group_records))
                    DailyCard(
                        title = stringResource(R.string.reminders_day),
                        hint = stringResource(R.string.reminders_day_hint),
                        reminder = settings.dayReport,
                        onChange = { r -> store.update { it.copy(dayReport = r) } },
                        onPickTime = { picking = TimeField.Day },
                    )
                    DailyCard(
                        title = stringResource(R.string.reminders_sleep),
                        hint = stringResource(R.string.reminders_sleep_hint),
                        reminder = settings.sleep,
                        onChange = { r -> store.update { it.copy(sleep = r) } },
                        onPickTime = { picking = TimeField.Sleep },
                    )

                    SettingsGroupTitle(stringResource(R.string.reminders_group_plans))
                    SettingsCard {
                        SettingsSwitchRow(
                            title = stringResource(R.string.reminders_events),
                            hint = stringResource(R.string.reminders_events_hint),
                            checked = settings.events,
                            onChange = { on -> store.update { it.copy(events = on) } },
                        )
                        SettingsSwitchRow(
                            title = stringResource(R.string.reminders_tasks),
                            hint = stringResource(R.string.reminders_tasks_hint),
                            checked = settings.tasks,
                            onChange = { on -> store.update { it.copy(tasks = on) } },
                        )
                    }
                    DailyCard(
                        title = stringResource(R.string.reminders_summary),
                        hint = stringResource(R.string.reminders_summary_hint),
                        reminder = settings.summary,
                        onChange = { r -> store.update { it.copy(summary = r) } },
                        onPickTime = { picking = TimeField.Summary },
                    )

                    SettingsGroupTitle(stringResource(R.string.reminders_group_games))
                    DailyCard(
                        title = stringResource(R.string.reminders_raskobki),
                        hint = stringResource(R.string.reminders_raskobki_hint),
                        reminder = settings.raskobki,
                        onChange = { r -> store.update { it.copy(raskobki = r) } },
                        onPickTime = { picking = TimeField.Raskobki },
                    )

                    SettingsGroupTitle(stringResource(R.string.reminders_group_quiet))
                    SettingsCard {
                        val quiet = settings.quiet
                        SettingsSwitchRow(
                            title = stringResource(R.string.reminders_quiet),
                            checked = quiet.enabled,
                            onChange = { on -> store.update { it.copy(quiet = it.quiet.copy(enabled = on)) } },
                        )
                        if (quiet.enabled) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 16.dp),
                            ) {
                                TimeChip(stringResource(R.string.reminders_quiet_from, quiet.start.toString())) { picking = TimeField.QuietStart }
                                TimeChip(stringResource(R.string.reminders_quiet_to, quiet.end.toString())) { picking = TimeField.QuietEnd }
                            }
                            listOf(
                                QuietMode.POSTPONE to R.string.reminders_quiet_postpone,
                                QuietMode.SILENT to R.string.reminders_quiet_silent,
                                QuietMode.SKIP to R.string.reminders_quiet_skip,
                            ).forEach { (mode, label) ->
                                SettingsRadioRow(stringResource(label), selected = quiet.mode == mode) {
                                    store.update { it.copy(quiet = it.quiet.copy(mode = mode)) }
                                }
                            }
                        }
                    }

                    SettingsGroupTitle(stringResource(R.string.reminders_group_sound))
                    SettingsCard {
                        listOf(
                            SoundMode.SOUND_AND_VIBRATION to R.string.reminders_sound_both,
                            SoundMode.SOUND_ONLY to R.string.reminders_sound_only,
                            SoundMode.VIBRATION_ONLY to R.string.reminders_vibration_only,
                            SoundMode.SILENT to R.string.reminders_silent,
                        ).forEach { (mode, label) ->
                            SettingsRadioRow(stringResource(label), selected = settings.sound == mode) {
                                store.update { it.copy(sound = mode) }
                            }
                        }
                        Text(
                            text = stringResource(R.string.reminders_sound_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        TextButton(onClick = openSystemSettings, modifier = Modifier.padding(horizontal = 4.dp)) {
                            Text(stringResource(R.string.reminders_system))
                        }
                    }

                    OutlinedButton(
                        onClick = container.reminderScheduler::test,
                        enabled = canPost,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        Text(stringResource(R.string.reminders_test))
                    }
                }
            }
        }
    }

    picking?.let { field ->
        val initial = when (field) {
            TimeField.Day -> settings.dayReport.time
            TimeField.Sleep -> settings.sleep.time
            TimeField.Summary -> settings.summary.time
            TimeField.Raskobki -> settings.raskobki.time
            TimeField.QuietStart -> settings.quiet.start
            TimeField.QuietEnd -> settings.quiet.end
        }
        TimePickerDialog(
            initial = initial,
            onPick = { time: LocalTime ->
                store.update {
                    when (field) {
                        TimeField.Day -> it.copy(dayReport = it.dayReport.copy(time = time))
                        TimeField.Sleep -> it.copy(sleep = it.sleep.copy(time = time))
                        TimeField.Summary -> it.copy(summary = it.summary.copy(time = time))
                        TimeField.Raskobki -> it.copy(raskobki = it.raskobki.copy(time = time))
                        TimeField.QuietStart -> it.copy(quiet = it.quiet.copy(start = time))
                        TimeField.QuietEnd -> it.copy(quiet = it.quiet.copy(end = time))
                    }
                }
                picking = null
            },
            onClear = null,
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun PermissionBanner(onAllow: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp),
    ) {
        Icon(Icons.Outlined.NotificationsOff, null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = stringResource(R.string.reminders_blocked_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.reminders_blocked_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Button(onClick = onAllow) { Text(stringResource(R.string.reminders_allow)) }
    }
}

/** Ежедневное напоминание: выключатель, время и дни недели. */
@Composable
private fun DailyCard(
    title: String,
    hint: String,
    reminder: DailyReminder,
    onChange: (DailyReminder) -> Unit,
    onPickTime: () -> Unit,
) {
    SettingsCard {
        SettingsSwitchRow(title = title, hint = hint, checked = reminder.enabled, onChange = { onChange(reminder.copy(enabled = it)) })
        if (reminder.enabled) {
            TimeChip(reminder.time.toString(), Modifier.padding(horizontal = 16.dp), onPickTime)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            ) {
                weekDays(LocalAppSettings.current.firstDayOfWeek).forEach { day ->
                    val selected = day in reminder.days
                    DayToggle(day, selected) {
                        val days = if (selected) reminder.days - day else reminder.days + day
                        // Без единого дня напоминание бессмысленно — для этого есть выключатель
                        if (days.isNotEmpty()) onChange(reminder.copy(days = days))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayToggle(day: DayOfWeek, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (selected) colors.primary else colors.surfaceContainerHighest)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.titlecase() },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else null,
            color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimeChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilterChip(
        selected = true,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)) },
        modifier = modifier,
    )
}
