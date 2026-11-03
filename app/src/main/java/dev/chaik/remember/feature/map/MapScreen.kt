package dev.chaik.remember.feature.map

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.map.DarkMapStyle
import dev.chaik.remember.core.map.MAP_STYLE_URL
import dev.chaik.remember.core.map.OfflineMaps
import dev.chaik.remember.core.model.Place
import dev.chaik.remember.core.model.PlaceCategory
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.core.ui.theme.isDarkTheme
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.time.LocalDate
import kotlin.coroutines.resume

@Serializable
data object MapRoute : NavKey

private const val PLACES_SOURCE = "places"
private const val PLACES_DOT = "places-dot"
private const val PLACES_ICON = "places-icon"
private const val PLACES_LABEL = "places-label"

/** Цвет и значок меток без категории. */
private const val NO_CATEGORY_COLOR = 0xFF546E7A.toInt()

/**
 * Карта мест. Долгое нажатие ставит метку, нажатие на метку открывает её. Внизу вместо островка —
 * своя панель: фильтр по категориям и список меток. Города можно скачать — тогда карта работает
 * без сети (см. OfflineMaps).
 */
@Composable
fun MapScreen(onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = context.appContainer
    val maps = container.offlineMaps
    val repository = container.placeRepository
    // Потоки — один раз: новый поток на каждой перерисовке перезапускал бы чтение базы
    val places by remember { repository.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val categories by remember { repository.observeCategories() }.collectAsStateWithLifecycle(emptyList())
    val hidden by maps.hiddenCategories.collectAsState()
    val cities by maps.cities.collectAsState()
    val offlineOnly by maps.offlineOnly.collectAsState()
    val download by maps.download.collectAsState()
    val dark = isDarkTheme(LocalAppSettings.current.theme)
    val haptics = LocalHapticFeedback.current

    var editing by remember { mutableStateOf<Place?>(null) }
    var citiesOpen by remember { mutableStateOf(false) }
    var managingCategories by remember { mutableStateOf(false) }
    var pickingArea by remember { mutableStateOf(false) }

    val shown = remember(places, hidden) { places.filter { (it.categoryId ?: OfflineMaps.NO_CATEGORY) !in hidden } }

    // Пока экран открыт, а город скачан, карта не ходит в сеть
    DisposableEffect(Unit) {
        maps.refresh()
        maps.setScreenOpen(true)
        onDispose { maps.setScreenOpen(false) }
    }

    val mapView = rememberMapView()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    LaunchedEffect(mapView) { map = mapView.awaitMap() }

    val currentShown by rememberUpdatedState(shown)
    val picking by rememberUpdatedState(pickingArea)
    val outline = MaterialTheme.colorScheme.surface.toArgb()
    val labelColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val markerIcons = rememberMarkerIcons()

    LaunchedEffect(map) {
        val current = map ?: return@LaunchedEffect
        current.uiSettings.isLogoEnabled = false
        current.uiSettings.isRotateGesturesEnabled = false
        current.uiSettings.isCompassEnabled = false
        // Откуда начать: где карту оставили, иначе первый город, иначе первая метка, иначе весь мир
        val camera = maps.lastCamera
        val city = cities.firstOrNull()
        val place = places.firstOrNull()
        when {
            camera != null -> current.moveCamera(CameraUpdateFactory.newCameraPosition(camera))
            city != null -> current.moveCamera(CameraUpdateFactory.newLatLngBounds(city.bounds, 0))
            place != null -> current.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(place.latitude, place.longitude), 12.0))
            else -> current.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(50.0, 40.0), 2.0))
        }
        current.addOnCameraIdleListener { maps.lastCamera = current.cameraPosition }
        current.addOnMapClickListener { point ->
            if (picking) return@addOnMapClickListener false
            val screen = current.projection.toScreenLocation(point)
            val id = current.queryRenderedFeatures(screen, PLACES_DOT, PLACES_ICON, PLACES_LABEL)
                .firstOrNull()?.getNumberProperty("id")?.toLong()
            val place = currentShown.firstOrNull { it.id == id } ?: return@addOnMapClickListener false
            editing = place
            true
        }
        current.addOnMapLongClickListener { point ->
            if (picking) return@addOnMapLongClickListener false
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            editing = Place(latitude = point.latitude, longitude = point.longitude, visitedOn = LocalDate.now())
            true
        }
    }

    // Смена темы — другой вид карты; метки добавляются в каждый заново. Тёмная карта — та же цветная,
    // перекрашенная: её JSON берётся из уже загруженной (с диска, если город скачан)
    LaunchedEffect(map, dark) {
        val current = map ?: return@LaunchedEffect
        loadedStyle = null
        fun ready(loaded: Style) {
            loaded.useRussianNames()
            markerIcons.forEach { (key, bitmap) -> loaded.addImage(key, bitmap) }
            loaded.addPlaceLayers(outline, labelColor)
            loadedStyle = loaded
        }
        val darkJson = maps.darkStyleJson
        when {
            !dark -> current.setStyle(Style.Builder().fromUri(MAP_STYLE_URL), ::ready)
            darkJson != null -> current.setStyle(Style.Builder().fromJson(darkJson), ::ready)
            else -> current.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { light ->
                val json = DarkMapStyle.darken(light.json).also { maps.darkStyleJson = it }
                current.setStyle(Style.Builder().fromJson(json), ::ready)
            }
        }
    }
    LaunchedEffect(loadedStyle, shown, categories) {
        val source = loadedStyle?.getSourceAs<GeoJsonSource>(PLACES_SOURCE) ?: return@LaunchedEffect
        val byId = categories.associateBy { it.id }
        source.setGeoJson(FeatureCollection.fromFeatures(shown.map { it.toFeature(byId[it.categoryId]) }))
    }

    ScreenScaffold(
        title = stringResource(R.string.section_map),
        modifier = modifier,
        onBack = onBack,
        actions = {
            if (cities.isNotEmpty()) {
                IconButton(onClick = { maps.setOfflineOnly(!offlineOnly) }) {
                    Icon(
                        if (offlineOnly) Icons.Outlined.CloudOff else Icons.Outlined.Cloud,
                        stringResource(if (offlineOnly) R.string.map_offline_on else R.string.map_offline_off),
                    )
                }
            }
            IconButton(onClick = { citiesOpen = true }) {
                Icon(Icons.Outlined.DownloadForOffline, stringResource(R.string.map_cities))
            }
        },
    ) { padding ->
        // Значок «i» с авторами карты (его требует лицензия OpenStreetMap) — над панелью
        val density = LocalDensity.current
        var panelHeight by remember { mutableIntStateOf(0) }
        LaunchedEffect(map, panelHeight, pickingArea) {
            val gap = with(density) { 12.dp.roundToPx() }
            map?.uiSettings?.setAttributionMargins(gap, 0, 0, if (pickingArea) gap else panelHeight + gap)
        }
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
            // Пока вид не готов, не показываем промежуточный (в тёмной теме — светлую карту)
            if (loadedStyle == null) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            }
            val current = map
            if (pickingArea && current != null) {
                AreaPicker(
                    map = current,
                    onCancel = { pickingArea = false },
                    onDownload = { name, bounds ->
                        maps.download(name, bounds)
                        pickingArea = false
                    },
                )
            } else {
                if (places.isEmpty() && editing == null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp),
                    ) {
                        Text(
                            stringResource(R.string.map_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
                MapPanel(
                    categories = categories,
                    places = places,
                    hidden = hidden,
                    download = download,
                    onToggle = { id -> maps.hiddenCategories.value = if (id in hidden) hidden - id else hidden + id },
                    onManage = { managingCategories = true },
                    onOpen = { place ->
                        current?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(place.latitude, place.longitude), maxOf(current.cameraPosition.zoom, 15.0)),
                        )
                        editing = place
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { panelHeight = it.height },
                )
            }
        }
    }

    editing?.let { place ->
        PlaceSheet(place = place, categories = categories, onDismiss = { editing = null })
    }
    if (citiesOpen) {
        CitiesSheet(
            cities = cities,
            download = download,
            onPickArea = {
                citiesOpen = false
                pickingArea = true
            },
            onDismiss = { citiesOpen = false },
        )
    }
    if (managingCategories) {
        CategoriesSheet(categories = categories, onDismiss = { managingCategories = false })
    }
}

/** Цвет метки: категории или нейтральный. */
internal fun PlaceCategory?.markerColor(): Int = this?.color ?: NO_CATEGORY_COLOR

/** MapView живёт вместе с экраном: старт, пауза и уничтожение — по жизненному циклу. */
@Composable
private fun rememberMapView(): MapView {
    val context = LocalContext.current
    val view = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).also { it.onCreate(null) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, view) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStop()
            view.onDestroy()
        }
    }
    return view
}

private suspend fun MapView.awaitMap(): MapLibreMap = suspendCancellableCoroutine { cont ->
    getMapAsync { cont.resume(it) }
}

/** Значки категорий картинками для карты: белые, рисуются поверх цветного кружка. */
@Composable
private fun rememberMarkerIcons(): Map<String, Bitmap> {
    val density = LocalDensity.current
    val painters = PlaceIcons.mapValues { (_, icon) -> rememberVectorPainter(icon) }
    return remember(density) { painters.mapValues { (_, painter) -> painter.toBitmap(density) } }
}

private fun VectorPainter.toBitmap(density: Density): Bitmap {
    val px = with(density) { MARKER_ICON.roundToPx() }
    val image = ImageBitmap(px, px)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(px.toFloat(), px.toFloat())) {
        draw(size, colorFilter = ColorFilter.tint(Color.White))
    }
    return image.asAndroidBitmap()
}

private val MARKER_ICON = 16.dp

/** Подписи карты — по-русски, а где русского названия нет — местное. */
private fun Style.useRussianNames() {
    val name = Expression.coalesce(Expression.get("name:ru"), Expression.get("name"))
    layers.filterIsInstance<SymbolLayer>().forEach { layer ->
        if (layer.textField.expression?.toString()?.contains("name") == true) {
            layer.setProperties(PropertyFactory.textField(name))
        }
    }
}

/** Метки: кружок цвета категории, её значок и название под ним. */
private fun Style.addPlaceLayers(outline: Int, label: Int) {
    addSource(GeoJsonSource(PLACES_SOURCE))
    addLayer(
        CircleLayer(PLACES_DOT, PLACES_SOURCE).withProperties(
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleRadius(13f),
            PropertyFactory.circleStrokeColor(outline),
            PropertyFactory.circleStrokeWidth(2.5f),
        ),
    )
    addLayer(
        SymbolLayer(PLACES_ICON, PLACES_SOURCE).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ),
    )
    addLayer(
        SymbolLayer(PLACES_LABEL, PLACES_SOURCE).withProperties(
            PropertyFactory.textField(Expression.get("title")),
            // Тот же шрифт, что у карты: он уже скачан вместе с городом
            PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
            PropertyFactory.textSize(13f),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
            PropertyFactory.textOffset(arrayOf(0f, 1.3f)),
            PropertyFactory.textColor(label),
            PropertyFactory.textHaloColor(outline),
            PropertyFactory.textHaloWidth(1.5f),
            PropertyFactory.textOptional(true),
        ),
    )
}

private fun Place.toFeature(category: PlaceCategory?): Feature =
    Feature.fromGeometry(Point.fromLngLat(longitude, latitude)).apply {
        addNumberProperty("id", id)
        addStringProperty("title", title)
        addStringProperty("color", "#%06X".format(category.markerColor() and 0xFFFFFF))
        addStringProperty("icon", if (category != null && category.icon in PlaceIcons) category.icon else "place")
    }
