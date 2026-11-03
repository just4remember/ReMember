package dev.chaik.remember.feature.games.raskobki

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.ui.components.TimePickerDialog

/**
 * Напоминание об игре прямо в профиле — то же, что в «Настройки → Напоминания»: включить и выбрать
 * время. Не приходит, если выпуск дня уже решён; при входе в приложение убирается из шторки.
 */
@Composable
internal fun RaskobkiReminderSection() {
    val container = LocalContext.current.appContainer
    val store = container.reminderSettings
    val settings by store.settings.collectAsStateWithLifecycle()
    val reminder = settings.raskobki
    var picking by remember { mutableStateOf(false) }

    // Разрешение могли выдать или забрать в настройках Android — проверяем при каждом возвращении
    var canPost by remember { mutableStateOf(container.notifier.canPost) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canPost = container.notifier.canPost }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canPost = it }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.reminders_raskobki), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.raskobki_reminder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = reminder.enabled,
                onCheckedChange = { on ->
                    store.update { it.copy(raskobki = it.raskobki.copy(enabled = on)) }
                    if (on && !canPost) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        if (reminder.enabled) {
            OutlinedButton(onClick = { picking = true }) {
                Icon(Icons.Outlined.Schedule, null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.raskobki_reminder_time, reminder.time.toString()))
            }
            when {
                // Общий выключатель всех напоминаний — главнее этого
                !settings.enabled -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.raskobki_reminder_all_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { store.update { it.copy(enabled = true) } }) {
                        Text(stringResource(R.string.raskobki_reminder_turn_on))
                    }
                }
                !canPost -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.reminders_blocked_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text(stringResource(R.string.reminders_allow))
                    }
                }
            }
        }
    }

    if (picking) {
        TimePickerDialog(
            initial = reminder.time,
            onPick = { time ->
                store.update { it.copy(raskobki = it.raskobki.copy(time = time)) }
                picking = false
            },
            onClear = null,
            onDismiss = { picking = false },
        )
    }
}
