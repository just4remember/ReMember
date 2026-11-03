package dev.chaik.remember.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.chaik.remember.core.database.entity.PlaceCategoryEntity
import dev.chaik.remember.core.database.entity.PlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Query("SELECT * FROM place ORDER BY created_at")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM place WHERE id = :id")
    suspend fun get(id: Long): PlaceEntity?

    @Upsert
    suspend fun upsert(place: PlaceEntity): Long

    @Query("DELETE FROM place WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM place_category ORDER BY position, id")
    fun observeCategories(): Flow<List<PlaceCategoryEntity>>

    @Query("SELECT name FROM place_category")
    suspend fun categoryNames(): List<String>

    @Upsert
    suspend fun upsertCategory(category: PlaceCategoryEntity): Long

    @Query("DELETE FROM place_category WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    /** Метки удалённой категории остаются, только без категории. */
    @Query("UPDATE place SET category_id = NULL WHERE category_id = :id")
    suspend fun clearCategory(id: Long)
}
