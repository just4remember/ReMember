package dev.chaik.remember.feature.time.world

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZoneId

/** Место на вкладке «Мир»: город или местное время телефона ([city] = null). */
data class WorldPlace(val city: WorldCity?) {
    val id: String get() = city?.id ?: LOCAL_ID
    val zoneId: ZoneId get() = city?.zoneId ?: ZoneId.systemDefault()

    companion object {
        const val LOCAL_ID = "local"
        val Local = WorldPlace(null)
    }
}

/**
 * Места вкладки «Мир» по порядку: первое — основное время (крупная карточка, от него считается
 * разница). Местное время всегда в списке, его можно сдвинуть, но не удалить.
 * Хранятся id через перевод строки (prefs «world_clock», едут в резервной копии).
 */
class WorldClockStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _places = MutableStateFlow(read())
    val places: StateFlow<List<WorldPlace>> = _places.asStateFlow()

    fun add(city: WorldCity) {
        if (_places.value.any { it.id == city.id }) return
        write(_places.value + WorldPlace(city))
    }

    fun remove(id: String) {
        if (id == WorldPlace.LOCAL_ID) return
        write(_places.value.filterNot { it.id == id })
    }

    /** Сдвинуть на одну позицию вверх или вниз; на первое место — значит сделать основным. */
    fun move(id: String, up: Boolean) {
        val list = _places.value.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = from + if (up) -1 else 1
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
        write(list)
    }

    /** Новый порядок после перетаскивания. */
    fun setOrder(ids: List<String>) {
        val byId = _places.value.associateBy { it.id }
        val ordered = ids.mapNotNull(byId::get)
        write(ordered + _places.value.filterNot { it.id in ids })
    }

    private fun read(): List<WorldPlace> {
        val places = prefs.getString(KEY, "").orEmpty().lines().map(String::trim).mapNotNull { id ->
            if (id == WorldPlace.LOCAL_ID) WorldPlace.Local else WorldCities.find(id)?.let(::WorldPlace)
        }.distinctBy { it.id }
        // Раньше местного времени в списке не было: оно было основным
        return if (places.none { it.city == null }) listOf(WorldPlace.Local) + places else places
    }

    private fun write(list: List<WorldPlace>) {
        prefs.edit { putString(KEY, list.joinToString("\n") { it.id }) }
        _places.value = list
    }

    companion object {
        const val PREFS = "world_clock"
        private const val KEY = "cities"

        @Volatile private var instance: WorldClockStore? = null

        fun get(context: Context): WorldClockStore =
            instance ?: synchronized(this) { instance ?: WorldClockStore(context.applicationContext).also { instance = it } }
    }
}
