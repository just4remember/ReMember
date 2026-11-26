package dev.chaik.remember.core.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.model.Place
import dev.chaik.remember.feature.map.MarkerBadge
import java.text.Collator
import java.util.Locale

private const val PLACE_LINK_PREFIX = "remember://place/"

internal fun placeLinkUrl(id: Long): String = "$PLACE_LINK_PREFIX$id"

internal fun placeLinkId(url: String): Long? =
    url.takeIf { it.startsWith(PLACE_LINK_PREFIX) }
        ?.removePrefix(PLACE_LINK_PREFIX)?.toLongOrNull()?.takeIf { it > 0 }

private fun placeTitleGroup(title: String): Int {
    val firstLetter = title.firstOrNull(Char::isLetter) ?: return 2
    return when (Character.UnicodeScript.of(firstLetter.code)) {
        Character.UnicodeScript.LATIN -> 0
        Character.UnicodeScript.CYRILLIC -> 1
        else -> 2
    }
}

@Composable
internal fun PlaceLinkPicker(onDismiss: () -> Unit, onPick: (Place, String) -> Unit) {
    val repository = LocalContext.current.appContainer.placeRepository
    val places by remember(repository) { repository.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val categories by remember(repository) { repository.observeCategories() }.collectAsStateWithLifecycle(emptyList())
    val categoriesById = remember(categories) { categories.associateBy { it.id } }
    var query by remember { mutableStateOf("") }
    val untitled = stringResource(R.string.place_untitled)
    val sortedPlaces = remember(places) {
        val alphabet = Collator.getInstance(Locale.forLanguageTag("ru")).apply { strength = Collator.SECONDARY }
        places.sortedWith(compareBy<Place> { placeTitleGroup(it.title) }.thenBy(alphabet) {
            it.title.trimEnd().trimStart { char -> !char.isLetterOrDigit() }
        })
    }
    val matching = remember(sortedPlaces, query, untitled) {
        sortedPlaces.filter { it.title.ifBlank { untitled }.contains(query.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_link_pick_place)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.place_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (matching.isEmpty()) Text(stringResource(R.string.search_nothing))
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(matching, key = { it.id }) { place ->
                        val name = place.title.ifBlank { untitled }
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onPick(place, name) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MarkerBadge(categoriesById[place.categoryId], Modifier.size(32.dp))
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_close)) }
        },
    )
}

@Composable
internal fun PlaceLinkDialog(id: Long, onDismiss: () -> Unit, onOpen: () -> Unit) {
    val repository = LocalContext.current.appContainer.placeRepository
    val places by remember(repository) { repository.observeAll() }.collectAsStateWithLifecycle(initialValue = null)
    val place = places?.firstOrNull { it.id == id }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_link_place_title)) },
        text = {
            if (place != null) {
                Text(place.title.ifBlank { stringResource(R.string.place_untitled) })
            } else if (places != null) {
                Text(stringResource(R.string.editor_link_place_missing))
            }
        },
        confirmButton = {
            if (place != null) TextButton(onClick = onOpen) {
                Text(stringResource(R.string.editor_link_go))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_close)) }
        },
    )
}
