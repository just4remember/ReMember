package dev.chaik.remember.feature.time.world

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R
import dev.chaik.remember.core.ui.components.FullScreenPage
import java.text.Collator
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.abs

/** Города, сгруппированные по странам: страны и города внутри страны — по алфавиту языка интерфейса. */
fun citiesByCountry(cities: List<WorldCity>, russian: Boolean): List<Pair<String, List<WorldCity>>> {
    val collator = Collator.getInstance(if (russian) Locale("ru") else Locale.ENGLISH)
    return cities.groupBy { it.country(russian) }
        .toSortedMap(collator::compare)
        .map { (country, list) -> country to list.sortedWith { a, b -> collator.compare(a.name(russian), b.name(russian)) } }
}

/**
 * Выбор города: поиск по названию и стране, список по странам с подписями.
 * [disabled] — уже выбранные, отмечены и не выбираются. [withLocal] — первой строкой «Местное время» (null).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CityPickerPage(
    title: String,
    russian: Boolean,
    disabled: Set<String>,
    onPick: (WorldCity?) -> Unit,
    onDismiss: () -> Unit,
    withLocal: Boolean = false,
) {
    var query by remember { mutableStateOf("") }
    val groups = remember(query, russian) { citiesByCountry(WorldCities.all.filter { it.matches(query) }, russian) }
    val colors = MaterialTheme.colorScheme
    FullScreenPage(title = title, onDismiss = onDismiss) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(40) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            placeholder = { Text(stringResource(R.string.world_clock_search)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (groups.isEmpty()) {
            Text(
                stringResource(R.string.world_clock_not_found),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            if (withLocal && query.isBlank()) {
                item(key = WorldPlace.LOCAL_ID) {
                    CityRow(
                        name = stringResource(R.string.world_clock_local),
                        note = utcText(ZoneId.systemDefault()),
                        added = WorldPlace.LOCAL_ID in disabled,
                        onClick = { onPick(null) },
                    )
                }
            }
            groups.forEach { (country, cities) ->
                stickyHeader(key = "country:$country") {
                    Text(
                        country,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface)
                            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
                    )
                }
                items(cities, key = { it.id }) { city ->
                    CityRow(
                        name = city.name(russian),
                        note = utcText(city.zoneId),
                        added = city.id in disabled,
                        onClick = { onPick(city) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CityRow(name: String, note: String, added: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !added, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = if (added) colors.outline else colors.onSurface)
        }
        Text(
            if (added) stringResource(R.string.world_clock_added) else note,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
    }
    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = colors.surfaceContainerHigh)
}

/** «UTC+3», «UTC−3:30». */
fun utcText(zone: ZoneId): String {
    val seconds = ZonedDateTime.now(zone).offset.totalSeconds
    if (seconds == 0) return "UTC"
    val sign = if (seconds < 0) "−" else "+"
    val h = abs(seconds) / 3600
    val m = abs(seconds) % 3600 / 60
    return "UTC$sign$h" + if (m > 0) ":%02d".format(m) else ""
}
