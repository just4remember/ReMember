package dev.chaik.remember.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.map.CityDownload
import dev.chaik.remember.core.map.MapCity
import dev.chaik.remember.core.model.Place
import dev.chaik.remember.core.model.PlaceCategory
import dev.chaik.remember.core.ui.components.DueDatePickerDialog
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Метка: название, категория, когда был, что там было. Сохраняется при закрытии;
 * новая метка без названия и текста не сохраняется.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PlaceSheet(place: Place, categories: List<PlaceCategory>, onDismiss: () -> Unit) {
    val container = LocalContext.current.appContainer
    var title by remember(place) { mutableStateOf(place.title) }
    var note by remember(place) { mutableStateOf(place.note) }
    var visitedOn by remember(place) { mutableStateOf(place.visitedOn) }
    var categoryId by remember(place) { mutableStateOf(place.categoryId) }
    var pickingDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }

    fun close() {
        val updated = place.copy(title = title.trim(), note = note.trim(), visitedOn = visitedOn, categoryId = categoryId)
        val empty = updated.title.isEmpty() && updated.note.isEmpty()
        if (!deleted && updated != place && !(place.id == 0L && empty)) {
            container.appScope.launch { container.placeRepository.save(updated) }
        }
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = ::close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.replace('\n', ' ') },
                placeholder = { Text(stringResource(R.string.place_title_hint)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            // Категория: повторное нажатие снимает выбор
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    CategoryChip(category, selected = category.id == categoryId) {
                        categoryId = if (categoryId == category.id) null else category.id
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { pickingDate = true }) {
                    Icon(Icons.Outlined.CalendarMonth, null, Modifier.padding(end = 8.dp))
                    Text(visitedOn?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)) ?: stringResource(R.string.place_no_date))
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    if (place.id != 0L) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.place_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text(stringResource(R.string.place_note_hint)) },
                minLines = 4,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = ::close, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_ok)) }
        }
    }

    if (pickingDate) {
        DueDatePickerDialog(
            initial = visitedOn,
            onPick = {
                visitedOn = it
                pickingDate = false
            },
            onClear = {
                visitedOn = null
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.place_delete_title)) },
            confirmButton = {
                TextButton(onClick = {
                    deleted = true
                    container.appScope.launch { container.placeRepository.delete(place.id) }
                    confirmDelete = false
                    onDismiss()
                }) { Text(stringResource(R.string.place_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Скачанные города; новая область выбирается рамкой на карте ([onPickArea]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CitiesSheet(
    cities: List<MapCity>,
    download: CityDownload?,
    onPickArea: () -> Unit,
    onDismiss: () -> Unit,
) {
    val maps = LocalContext.current.appContainer.offlineMaps
    var removing by remember { mutableStateOf<MapCity?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text(stringResource(R.string.map_cities), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.map_cities_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            cities.forEach { city ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(city.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            megabytesText(city.bytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { removing = city }) {
                        Icon(Icons.Outlined.Delete, stringResource(R.string.map_city_delete))
                    }
                }
            }
            if (cities.isNotEmpty()) {
                Text(
                    stringResource(R.string.map_storage, megabytesText(maps.storageBytes())),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                download != null && download.error -> {
                    Text(stringResource(R.string.map_download_error, download.name), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = maps::dismissError) { Text(stringResource(R.string.editor_close)) }
                }
                download != null -> {
                    Text(stringResource(R.string.map_downloading, download.name, (download.progress * 100).toInt()))
                    TextButton(onClick = maps::cancelDownload) { Text(stringResource(R.string.action_cancel)) }
                }
                else -> Button(onClick = onPickArea, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.map_pick_area))
                }
            }
        }
    }

    removing?.let { city ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.map_city_delete_title, city.name)) },
            text = { Text(stringResource(R.string.map_city_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    maps.delete(city)
                    removing = null
                }) { Text(stringResource(R.string.map_city_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Свои категории: изменить, добавить, удалить. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoriesSheet(categories: List<PlaceCategory>, onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf<PlaceCategory?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.place_categories),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            categories.forEach { category ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = category }
                        .padding(vertical = 8.dp),
                ) {
                    MarkerBadge(category, Modifier.size(32.dp))
                    Text(category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 14.dp))
                    Icon(Icons.Outlined.Edit, stringResource(R.string.place_category_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(
                onClick = {
                    editing = PlaceCategory(
                        name = "",
                        color = PlaceColors[categories.size % PlaceColors.size],
                        icon = "star",
                        position = (categories.maxOfOrNull { it.position } ?: -1) + 1,
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.place_category_add))
            }
        }
    }
    editing?.let { category -> CategoryEditor(category, onDismiss = { editing = null }) }
}

/** Название, цвет и значок категории; у существующей — ещё удаление. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditor(category: PlaceCategory, onDismiss: () -> Unit) {
    val container = LocalContext.current.appContainer
    var name by remember { mutableStateOf(category.name) }
    var color by remember { mutableStateOf(category.color) }
    var icon by remember { mutableStateOf(category.icon) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (category.id == 0L) R.string.place_category_add else R.string.place_category_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.replace('\n', ' ') },
                    label = { Text(stringResource(R.string.place_category_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaceColors.forEach { option ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(Color(option), CircleShape)
                                .then(if (option == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { color = option },
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaceIcons.forEach { (key, vector) ->
                        val selected = key == icon
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .background(if (selected) Color(color) else MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                                .clickable { icon = key },
                        ) {
                            Icon(vector, null, tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (category.id != 0L) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.place_category_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val saved = category.copy(name = name.trim(), color = color, icon = icon)
                    container.appScope.launch { container.placeRepository.saveCategory(saved) }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.place_category_delete_title, category.name)) },
            text = { Text(stringResource(R.string.place_category_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    container.appScope.launch { container.placeRepository.deleteCategory(category.id) }
                    confirmDelete = false
                    onDismiss()
                }) { Text(stringResource(R.string.place_category_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
