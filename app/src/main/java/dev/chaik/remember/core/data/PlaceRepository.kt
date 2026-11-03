package dev.chaik.remember.core.data

import android.content.Context
import dev.chaik.remember.R
import dev.chaik.remember.core.database.ReMemberDatabase
import dev.chaik.remember.core.database.entity.PlaceCategoryEntity
import dev.chaik.remember.core.database.entity.PlaceEntity
import dev.chaik.remember.core.model.Place
import dev.chaik.remember.core.model.PlaceCategory
import androidx.room.withTransaction
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.time.Instant

/** Места на карте и их категории. */
class PlaceRepository(private val context: Context, private val db: ReMemberDatabase) {
    private val dao = db.placeDao()
    private val prefs = context.getSharedPreferences("places", Context.MODE_PRIVATE)

    fun observeAll(): Flow<List<Place>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    /** Категории; при первом открытии карты появляется готовый набор. */
    fun observeCategories(): Flow<List<PlaceCategory>> = dao.observeCategories()
        .onStart { seedDefaults() }
        .map { list -> list.map { PlaceCategory(it.id, it.name, it.color, it.icon, it.position) } }

    /** Сохранить и вернуть id (у нового места он появляется здесь). */
    suspend fun save(place: Place): Long {
        val now = Instant.now()
        val createdAt = if (place.id == 0L) now else dao.get(place.id)?.createdAt ?: now
        val id = dao.upsert(
            PlaceEntity(place.id, place.latitude, place.longitude, place.title, place.note, place.categoryId, place.visitedOn, createdAt, now),
        )
        // Upsert обновлённой строки возвращает -1
        return if (place.id == 0L) id else place.id
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun saveCategory(category: PlaceCategory) {
        dao.upsertCategory(PlaceCategoryEntity(category.id, category.name, category.color, category.icon, category.position))
    }

    /** Удалить категорию; её метки остаются без категории. */
    suspend fun deleteCategory(id: Long) {
        dao.clearCategory(id)
        dao.deleteCategory(id)
    }

    /**
     * Готовые категории — один раз: удалённые потом снова не появятся. Добавляются только те,
     * которых ещё нет по названию, — так и после восстановления копии они не задвоятся.
     * Целиком, одной транзакцией: уход с экрана посередине не должен оставить часть набора.
     */
    private suspend fun seedDefaults() = withContext(NonCancellable) {
        if (prefs.getBoolean(SEEDED, false)) return@withContext
        db.withTransaction {
            val existing = dao.categoryNames().toSet()
            Defaults.forEachIndexed { index, (name, color, icon) ->
                val title = context.getString(name)
                if (title !in existing) dao.upsertCategory(PlaceCategoryEntity(0, title, color, icon, index))
            }
        }
        prefs.edit().putBoolean(SEEDED, true).apply()
    }

    private fun PlaceEntity.toModel() = Place(id, latitude, longitude, title, note, visitedOn, categoryId)

    private companion object {
        // v2: первая версия могла добавить набор не целиком
        const val SEEDED = "categories_seeded_v2"

        val Defaults = listOf(
            Triple(R.string.place_category_food, 0xFFEF6C00.toInt(), "food"),
            Triple(R.string.place_category_walks, 0xFF2E7D32.toInt(), "park"),
            Triple(R.string.place_category_friends, 0xFF8E24AA.toInt(), "people"),
            Triple(R.string.place_category_travel, 0xFF1565C0.toInt(), "flight"),
            Triple(R.string.place_category_other, 0xFF546E7A.toInt(), "place"),
        )
    }
}
