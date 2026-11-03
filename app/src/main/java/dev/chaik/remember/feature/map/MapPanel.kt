package dev.chaik.remember.feature.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R
import dev.chaik.remember.core.map.CityDownload
import dev.chaik.remember.core.map.OfflineMaps
import dev.chaik.remember.core.model.Place
import dev.chaik.remember.core.model.PlaceCategory
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Панель карты вместо островка. Свёрнутая — фильтр по категориям; развёрнутая (тап по ручке или
 * свайп вверх) — ещё поиск и список меток. Нажатие на метку в списке переносит к ней карту.
 */
@Composable
internal fun MapPanel(
    categories: List<PlaceCategory>,
    places: List<Place>,
    hidden: Set<Long>,
    download: CityDownload?,
    onToggle: (Long) -> Unit,
    onManage: () -> Unit,
    onOpen: (Place) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val byId = remember(categories) { categories.associateBy { it.id } }
    val hasUncategorized = places.any { it.categoryId == null || it.categoryId !in byId }

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            // Ручка: тап или свайп разворачивает и сворачивает
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clickable(interactionSource = null, indication = null) { expanded = !expanded }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -8) expanded = true
                            if (dragAmount > 8) expanded = false
                        }
                    },
            ) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
            }
            download?.takeIf { !it.error }?.let {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(
                        stringResource(R.string.map_downloading, it.name, (it.progress * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LinearProgressIndicator(progress = { it.progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
            // Категории приходят из базы позже «Без категории» — без этого строка оставалась бы
            // прокрученной к нему, в самый конец
            val chips = rememberLazyListState()
            LaunchedEffect(categories.isNotEmpty()) { chips.scrollToItem(0) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LazyRow(
                    state = chips,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(categories, key = { it.id }) { category ->
                        CategoryChip(category, selected = category.id !in hidden) { onToggle(category.id) }
                    }
                    if (hasUncategorized) {
                        item(key = "none") {
                            CategoryChip(null, selected = OfflineMaps.NO_CATEGORY !in hidden) { onToggle(OfflineMaps.NO_CATEGORY) }
                        }
                    }
                }
                IconButton(onClick = onManage) {
                    Icon(Icons.Outlined.Tune, stringResource(R.string.place_categories))
                }
            }
            AnimatedVisibility(expanded) {
                val found = remember(places, hidden, query, byId) {
                    val text = query.trim()
                    places
                        .filter { (it.categoryId?.takeIf { id -> id in byId } ?: OfflineMaps.NO_CATEGORY) !in hidden }
                        .filter { text.isEmpty() || it.title.contains(text, ignoreCase = true) || it.note.contains(text, ignoreCase = true) }
                        .sortedWith(compareByDescending<Place> { it.visitedOn }.thenByDescending { it.id })
                }
                Column(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.place_search)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    if (found.isEmpty()) {
                        Text(
                            stringResource(R.string.place_list_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.heightIn(max = 320.dp),
                    ) {
                        items(found, key = { it.id }) { place ->
                            PlaceRow(place, byId[place.categoryId]) {
                                expanded = false
                                onOpen(place)
                            }
                        }
                    }
                }
            }
            if (!expanded) Box(Modifier.height(8.dp))
        }
    }
}

/** Чип категории в фильтре: значок в её цвете; [category] = null — «Без категории». */
@Composable
internal fun CategoryChip(category: PlaceCategory?, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(category?.name ?: stringResource(R.string.place_no_category)) },
        leadingIcon = { MarkerBadge(category, Modifier.size(20.dp)) },
    )
}

/** Кружок цвета категории с её значком — как метка на карте. */
@Composable
internal fun MarkerBadge(category: PlaceCategory?, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.background(Color(category.markerColor()), CircleShape),
    ) {
        Icon(placeIcon(category?.icon), null, tint = Color.White, modifier = Modifier.padding(3.dp))
    }
}

@Composable
private fun PlaceRow(place: Place, category: PlaceCategory?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        MarkerBadge(category, Modifier.size(32.dp))
        Box(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                place.title.ifBlank { stringResource(R.string.place_untitled) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                place.visitedOn?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                place.note.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
