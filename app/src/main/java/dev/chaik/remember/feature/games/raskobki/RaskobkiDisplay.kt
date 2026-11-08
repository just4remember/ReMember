package dev.chaik.remember.feature.games.raskobki

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.settings.LocalAppSettings

/** Что показывать в игре: серию, таймер и цифры результата. Серия и время всё равно считаются на сайте. */
@Composable
internal fun RaskobkiDisplaySection() {
    val store = LocalContext.current.appContainer.settings
    val settings = LocalAppSettings.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DisplaySwitch(R.string.profile_raskobki_streak, R.string.profile_raskobki_streak_hint, settings.raskobkiStreak) { on ->
            store.update { it.copy(raskobkiStreak = on) }
        }
        DisplaySwitch(R.string.profile_raskobki_timer, R.string.profile_raskobki_timer_hint, settings.raskobkiTimer) { on ->
            store.update { it.copy(raskobkiTimer = on) }
        }
        DisplaySwitch(R.string.profile_raskobki_results, R.string.profile_raskobki_results_hint, settings.raskobkiResults) { on ->
            store.update { it.copy(raskobkiResults = on) }
        }
        Text(
            stringResource(R.string.profile_raskobki_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DisplaySwitch(@StringRes title: Int, @StringRes hint: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) },
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.padding(start = 12.dp))
    }
}
