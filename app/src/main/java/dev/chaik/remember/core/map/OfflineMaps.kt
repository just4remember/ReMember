package dev.chaik.remember.core.map

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Цветная подробная карта OpenFreeMap Liberty: бесплатная, без ключей. Тёмная тема — она же,
 * перекрашенная на телефоне (см. DarkMapStyle), поэтому скачивать город нужно один раз.
 */
const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

/** Скачанный город. [bytes] — сколько места занимает. */
data class MapCity(val name: String, val bounds: LatLngBounds, val bytes: Long, val regions: List<OfflineRegion>)

/** Идёт скачивание: какой город и сколько готово (0…1). */
data class CityDownload(val name: String, val progress: Float, val error: Boolean = false)

/**
 * Города для офлайна: прямоугольная область карты со всеми подробностями. Пока на экране карты, а город скачан,
 * сеть картам не нужна вовсе — см. [setScreenOpen].
 *
 * MapLibre хранит скачанное в своей базе (не зашифрована: там только карта и названия городов).
 */
class OfflineMaps(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val manager by lazy {
        MapLibre.getInstance(context)
        OfflineManager.getInstance(context)
    }

    private val _cities = MutableStateFlow<List<MapCity>>(emptyList())
    val cities: StateFlow<List<MapCity>> = _cities

    private val _download = MutableStateFlow<CityDownload?>(null)
    val download: StateFlow<CityDownload?> = _download
    private var downloadJob: Job? = null

    /** Без сети: карта только из скачанного. Выключатель на экране карты. */
    private val _offlineOnly = MutableStateFlow(true)
    val offlineOnly: StateFlow<Boolean> = _offlineOnly

    /** Скрытые фильтром категории меток ([NO_CATEGORY] — метки без категории). Только в памяти. */
    val hiddenCategories = MutableStateFlow<Set<Long>>(emptySet())

    /** Тёмный вид карты: строится один раз за запуск. */
    var darkStyleJson: String? = null

    /** Где была карта, когда с неё ушли, — только в памяти. */
    var lastCamera: CameraPosition? = null

    private var screenOpen = false

    fun setScreenOpen(open: Boolean) {
        screenOpen = open
        applyConnectivity()
    }

    fun setOfflineOnly(offline: Boolean) {
        _offlineOnly.value = offline
        applyConnectivity()
    }

    /**
     * MapLibre сам ходит в сеть за свежими плитками. Пока открыта карта и есть скачанные города,
     * запрещаем это: всё берётся из скачанного, без трафика и ожидания. Вне экрана — как обычно.
     */
    private fun applyConnectivity() {
        val offline = screenOpen && _offlineOnly.value && _cities.value.isNotEmpty() && _download.value == null
        MapLibre.setConnected(if (offline) false else null)
    }

    /** Размер всей базы карт: скачанные города и то, что карта запомнила сама. */
    fun storageBytes(): Long = File(context.filesDir, "mbgl-offline.db").length()

    fun refresh() {
        scope.launch {
            val regions = runCatching { listRegions() }.getOrDefault(emptyList())
            _cities.value = regions
                .groupBy { metadata(it)?.name.orEmpty() }
                .mapNotNull { (name, parts) ->
                    val definition = parts.first().definition as? OfflineTilePyramidRegionDefinition ?: return@mapNotNull null
                    val bounds = definition.bounds ?: return@mapNotNull null
                    // Части одного города (старые версии качали по части на тему) делят плитки — не складываем
                    val bytes = parts.maxOf { runCatching { status(it).completedResourceSize }.getOrDefault(0) }
                    MapCity(name, bounds, bytes, parts)
                }
                .sortedBy { it.name.lowercase() }
            applyConnectivity()
        }
    }

    /** Скачать область [bounds] под именем [name]. Плитки — до 14-го приближения, дальше карта их увеличивает. */
    fun download(name: String, bounds: LatLngBounds) {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            _download.value = CityDownload(name, 0f)
            applyConnectivity()
            val created = mutableListOf<OfflineRegion>()
            try {
                val definition = OfflineTilePyramidRegionDefinition(
                    MAP_STYLE_URL, bounds, 0.0, MAX_ZOOM, context.resources.displayMetrics.density,
                )
                val region = create(definition, Json.encodeToString(Metadata.serializer(), Metadata(name)).encodeToByteArray())
                created += region
                fetch(region) { part -> _download.value = CityDownload(name, part) }
                _download.value = null
            } catch (e: Throwable) {
                // Недокачанный город не оставляем: он выглядел бы скачанным, а карты в нём нет
                withContext(NonCancellable) { created.forEach { runCatching { delete(it) } } }
                _download.value = if (e is CancellationException) null else CityDownload(name, 0f, error = true)
                if (e is CancellationException) {
                    refresh()
                    throw e
                }
            }
            refresh()
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    fun dismissError() {
        if (_download.value?.error == true) _download.value = null
        applyConnectivity()
    }

    fun delete(city: MapCity) {
        scope.launch {
            city.regions.forEach { runCatching { delete(it) } }
            refresh()
        }
    }

    private fun metadata(region: OfflineRegion): Metadata? =
        runCatching { Json.decodeFromString(Metadata.serializer(), region.metadata.decodeToString()) }.getOrNull()

    private suspend fun listRegions(): List<OfflineRegion> = suspendCancellableCoroutine { cont ->
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) = cont.resume(offlineRegions?.toList().orEmpty())
            override fun onError(error: String) = cont.resumeWithException(IllegalStateException(error))
        })
    }

    private suspend fun create(definition: OfflineTilePyramidRegionDefinition, metadata: ByteArray): OfflineRegion =
        suspendCancellableCoroutine { cont ->
            manager.createOfflineRegion(definition, metadata, object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) = cont.resume(offlineRegion)
                override fun onError(error: String) = cont.resumeWithException(IllegalStateException(error))
            })
        }

    /** Качать, пока не скачается всё. Обрыв сети MapLibre переждёт сам; отмена — останавливает. */
    private suspend fun fetch(region: OfflineRegion, onProgress: (Float) -> Unit) {
        try {
            suspendCancellableCoroutine { cont ->
                region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        if (status.requiredResourceCount > 0) {
                            onProgress(status.completedResourceCount.toFloat() / status.requiredResourceCount)
                        }
                        if (status.isComplete && cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(error: OfflineRegionError) {
                        // Нет такой плитки — не беда; без сети MapLibre повторит сам
                        if (error.reason == OfflineRegionError.REASON_OTHER && cont.isActive) {
                            cont.resumeWithException(IllegalStateException(error.message))
                        }
                    }

                    override fun mapboxTileCountLimitExceeded(limit: Long) {
                        if (cont.isActive) cont.resumeWithException(IllegalStateException("limit $limit"))
                    }
                })
                region.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }
        } finally {
            region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            region.setObserver(null)
        }
    }

    private suspend fun status(region: OfflineRegion): OfflineRegionStatus = suspendCancellableCoroutine { cont ->
        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) {
                if (status != null) cont.resume(status) else cont.resumeWithException(IllegalStateException())
            }
            override fun onError(error: String?) = cont.resumeWithException(IllegalStateException(error))
        })
    }

    private suspend fun delete(region: OfflineRegion): Unit = suspendCancellableCoroutine { cont ->
        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() = cont.resume(Unit)
            override fun onError(error: String) = cont.resumeWithException(IllegalStateException(error))
        })
    }

    @Serializable
    private data class Metadata(val name: String, val style: String = "")

    companion object {
        /** Метки без категории в фильтре. */
        const val NO_CATEGORY = -1L

        private const val MAX_ZOOM = 14.0

        /** Общая часть любого города: шрифты, значки и мир мелким масштабом (замерено: ~9,7 МБ). */
        private const val BASE_BYTES = 9_500_000L

        /** Средняя плитка — грубо: в центре города больше, в полях меньше. */
        private const val TILE_BYTES = 30_000L

        /** Примерный размер области: плитки всех приближений до 14-го плюс общая часть. */
        fun estimateBytes(north: Double, east: Double, south: Double, west: Double): Long {
            var tiles = 0L
            for (zoom in 0..MAX_ZOOM.toInt()) {
                val n = 1 shl zoom
                val x = (tileX(east, n) - tileX(west, n) + 1).coerceAtLeast(1)
                val y = (tileY(south, n) - tileY(north, n) + 1).coerceAtLeast(1)
                tiles += x.toLong() * y
            }
            return BASE_BYTES + tiles * TILE_BYTES
        }

        private fun tileX(longitude: Double, n: Int): Int = ((longitude + 180) / 360 * n).toInt().coerceIn(0, n - 1)

        private fun tileY(latitude: Double, n: Int): Int {
            val rad = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
            return ((1 - kotlin.math.ln(kotlin.math.tan(rad) + 1 / kotlin.math.cos(rad)) / Math.PI) / 2 * n).toInt().coerceIn(0, n - 1)
        }
    }
}
