package dev.chaik.remember.feature.map

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R
import dev.chaik.remember.core.map.CityResult
import dev.chaik.remember.core.map.CitySearch
import dev.chaik.remember.core.map.OfflineMaps
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap

/** Больше этого — предупреждаем: город с пригородами, а то и область. */
private const val LARGE_BYTES = 500L * 1024 * 1024

/**
 * Выбор области для скачивания: рамка поверх карты, карту двигают и масштабируют под ней.
 * Скачивается ровно то, что в рамке, со всеми подробностями. Город можно найти по названию —
 * тогда рамка встанет на его границы.
 */
@Composable
internal fun AreaPicker(
    map: MapLibreMap,
    onCancel: () -> Unit,
    onDownload: (name: String, bounds: LatLngBounds) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    // Рамка в координатах карты: обе меряем от корня окна и вычитаем начало карты
    var origin by remember { mutableStateOf(Offset.Zero) }
    var frameInRoot by remember { mutableStateOf<Rect?>(null) }
    val frame = frameInRoot?.translate(-origin)
    var bounds by remember { mutableStateOf<LatLngBounds?>(null) }
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CityResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }

    fun measure() {
        val rect = frame ?: return
        val projection = map.projection
        val topLeft = projection.fromScreenLocation(PointF(rect.left, rect.top))
        val bottomRight = projection.fromScreenLocation(PointF(rect.right, rect.bottom))
        bounds = LatLngBounds.Builder().include(topLeft).include(bottomRight).build()
    }

    DisposableEffect(map, frame) {
        measure()
        val listener = MapLibreMap.OnCameraIdleListener { measure() }
        map.addOnCameraIdleListener(listener)
        onDispose { map.removeOnCameraIdleListener(listener) }
    }

    fun search() {
        if (query.isBlank() || searching) return
        focus.clearFocus()
        searching = true
        searchFailed = false
        scope.launch {
            val found = runCatching { CitySearch.search(query) }
            results = found.getOrDefault(emptyList())
            searchFailed = found.isFailure
            searching = false
        }
    }

    fun choose(result: CityResult) {
        results = emptyList()
        name = result.name
        val rect = frame ?: return
        val width = map.width
        val height = map.height
        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(
                result.bounds,
                rect.left.toInt(),
                rect.top.toInt(),
                (width - rect.right).toInt(),
                (height - rect.bottom).toInt(),
            ),
        )
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.boundsInRoot().topLeft }) {
        // Затемнение вокруг рамки и сама рамка
        val dim = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f)
        val border = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxSize()) {
            val rect = frame ?: return@Canvas
            val corner = CornerRadius(20.dp.toPx())
            val hole = Path().apply { addRoundRect(RoundRect(rect, corner)) }
            clipPath(hole, clipOp = ClipOp.Difference) { drawRect(dim) }
            drawRoundRect(border, rect.topLeft, rect.size, corner, style = Stroke(3.dp.toPx()))
        }

        Column(Modifier.fillMaxSize()) {
            // Поиск города
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.replace('\n', ' ') },
                        placeholder = { Text(stringResource(R.string.map_search_city)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }),
                        trailingIcon = {
                            if (searching) {
                                CircularProgressIndicator(Modifier.padding(12.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = ::search) { Icon(Icons.Outlined.Search, stringResource(R.string.map_search_city)) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (searchFailed) {
                        Text(stringResource(R.string.map_search_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    results.forEach { result ->
                        Text(
                            result.label,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { choose(result) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
            // Сама рамка — всё свободное место между карточками
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .onGloballyPositioned { frameInRoot = it.boundsInRoot() },
            )
            // Название и размер
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.navigationBarsPadding().imePadding().padding(16.dp),
                ) {
                    val bytes = bounds?.let { OfflineMaps.estimateBytes(it.latitudeNorth, it.longitudeEast, it.latitudeSouth, it.longitudeWest) }
                    Text(
                        if (bytes == null) stringResource(R.string.map_area_hint) else stringResource(R.string.map_area_size, megabytesText(bytes)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (bytes != null && bytes > LARGE_BYTES) {
                        Text(stringResource(R.string.map_area_large), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.replace('\n', ' ') },
                        label = { Text(stringResource(R.string.map_city_name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
                        Button(
                            enabled = name.isNotBlank() && bounds != null,
                            onClick = { bounds?.let { onDownload(name.trim(), it) } },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.map_area_download)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun megabytesText(bytes: Long): String = stringResource(R.string.map_megabytes, bytes / 1_048_576.0)
