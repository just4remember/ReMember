package dev.chaik.remember.feature.settings.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R
import dev.chaik.remember.core.widgets.MAX_EXTRA_CITIES
import dev.chaik.remember.core.widgets.WidgetSettings
import dev.chaik.remember.core.widgets.WidgetSettingsStore
import dev.chaik.remember.feature.time.world.CityPickerPage
import dev.chaik.remember.feature.time.world.WorldCities
import dev.chaik.remember.feature.time.world.WorldPlace
import java.util.Locale

private enum class CityTarget { ANALOG, DIGITAL_MAIN, DIGITAL_EXTRA }

private val russian: Boolean get() = Locale.getDefault().language == "ru"

@Composable
private fun cityLabel(id: String?): String =
    id?.let(WorldCities::find)?.name(russian) ?: stringResource(R.string.widgets_city_local)

/** Город часов со стрелками. */
@Composable
fun AnalogCityRow(settings: WidgetSettings, store: WidgetSettingsStore) {
    var picking by remember { mutableStateOf(false) }
    ValueRow(
        title = stringResource(R.string.widgets_city_pick),
        value = cityLabel(settings.analogCity),
        hint = stringResource(R.string.widgets_analog_city_hint),
        onClick = { picking = true },
    )
    if (picking) {
        CityDialog(CityTarget.ANALOG, setOfNotNull(settings.analogCity ?: WorldPlace.LOCAL_ID), store) { picking = false }
    }
}

/** Основное время цифровых часов и до трёх других городов для таблицы. */
@Composable
fun DigitalCitiesRows(settings: WidgetSettings, store: WidgetSettingsStore) {
    var picking by remember { mutableStateOf<CityTarget?>(null) }
    ValueRow(
        title = stringResource(R.string.widgets_city),
        value = cityLabel(settings.digitalCity),
        hint = null,
        onClick = { picking = CityTarget.DIGITAL_MAIN },
    )
    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 4.dp)) {
        Text(stringResource(R.string.widgets_digital_cities), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.widgets_digital_cities_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        settings.digitalCities.forEach { id ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cityLabel(id), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { store.update { it.copy(digitalCities = it.digitalCities - id) } }) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.widgets_remove_city), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (settings.digitalCities.size < MAX_EXTRA_CITIES) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { picking = CityTarget.DIGITAL_EXTRA }
                    .padding(vertical = 10.dp),
            ) {
                Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.widgets_add_city),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
    picking?.let { target ->
        val taken = settings.digitalCities.toSet() + (settings.digitalCity ?: WorldPlace.LOCAL_ID)
        CityDialog(target, taken, store) { picking = null }
    }
}

@Composable
private fun CityDialog(target: CityTarget, taken: Set<String>, store: WidgetSettingsStore, onDismiss: () -> Unit) {
    CityPickerPage(
        title = stringResource(if (target == CityTarget.DIGITAL_EXTRA) R.string.widgets_add_city else R.string.widgets_city_pick),
        russian = russian,
        disabled = taken,
        // Местное время — только основным: в таблице оно и так первое, если основное не город
        withLocal = target != CityTarget.DIGITAL_EXTRA,
        onPick = { city ->
            val id = city?.id
            store.update {
                when (target) {
                    CityTarget.ANALOG -> it.copy(analogCity = id)
                    // Город стал основным — из других городов его убираем
                    CityTarget.DIGITAL_MAIN -> it.copy(digitalCity = id, digitalCities = it.digitalCities - setOfNotNull(id))
                    CityTarget.DIGITAL_EXTRA -> if (id == null) it else it.copy(digitalCities = (it.digitalCities + id).distinct().take(MAX_EXTRA_CITIES))
                }
            }
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun ValueRow(title: String, value: String, hint: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp))
    }
}
