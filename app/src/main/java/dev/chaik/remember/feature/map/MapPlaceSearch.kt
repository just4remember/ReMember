package dev.chaik.remember.feature.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R
import dev.chaik.remember.core.map.MapSearchResult
import dev.chaik.remember.core.map.PlaceSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun MapPlaceSearch(
    focusRequest: Int,
    onPick: (MapSearchResult) -> Unit,
    canOpen: Boolean,
) {
    var query by remember { mutableStateOf("") }
    val term = query.trim()
    var submitted by remember(term) { mutableIntStateOf(0) }
    var openFirst by remember(term) { mutableStateOf(false) }
    var results by remember(term, submitted) { mutableStateOf<List<MapSearchResult>?>(null) }
    var loading by remember(term, submitted) { mutableStateOf(false) }
    var failed by remember(term, submitted) { mutableStateOf(false) }
    val language = if (LocalConfiguration.current.locales[0].language == "ru") "default" else "en"
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun find() {
        if (term.length < 2) return
        openFirst = true
        if (results.isNullOrEmpty() && !loading) submitted++
    }

    LaunchedEffect(term, submitted, language) {
        if (term.length < 3 && submitted == 0) return@LaunchedEffect
        loading = true
        if (submitted == 0) delay(700)
        try {
            results = PlaceSearch.search(term, language)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
        }
    }
    LaunchedEffect(openFirst, results, canOpen) {
        val first = results?.firstOrNull()
        if (openFirst && canOpen && first != null) onPick(first)
    }
    LaunchedEffect(focusRequest) {
        focus.requestFocus()
        keyboard?.show()
    }
    Column {
        MapPanelSearchField(
            value = query,
            onValueChange = { query = it },
            hint = stringResource(R.string.map_search_place),
            modifier = Modifier.focusRequester(focus),
            onSearch = ::find,
        )
        TextButton(onClick = ::find, enabled = term.length >= 2, modifier = Modifier.align(Alignment.End).padding(end = 16.dp)) {
            Text(stringResource(R.string.map_search_submit))
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (failed) Text(stringResource(R.string.map_search_failed), Modifier.padding(horizontal = 16.dp))
        if (results?.isEmpty() == true) Text(stringResource(R.string.search_nothing), Modifier.padding(horizontal = 16.dp))
        LazyColumn(Modifier.heightIn(max = 240.dp)) {
            items(results.orEmpty()) { result ->
                ListItem(
                    headlineContent = { Text(result.name) },
                    supportingContent = if (result.detail.isBlank()) null else ({ Text(result.detail) }),
                    modifier = Modifier.clickable(enabled = canOpen) { onPick(result) },
                )
            }
        }
        if (!results.isNullOrEmpty()) Text(
            "© OpenStreetMap contributors",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}
