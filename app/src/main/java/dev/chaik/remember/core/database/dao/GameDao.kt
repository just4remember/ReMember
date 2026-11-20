package dev.chaik.remember.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.chaik.remember.core.database.entity.ClickerSaveEntity

/** Сохранения игр, где прогресс хранится в базе. */
@Dao
interface GameDao {
    @Query("SELECT * FROM clicker_save WHERE id = 1")
    suspend fun clicker(): ClickerSaveEntity?

    @Upsert
    suspend fun saveClicker(save: ClickerSaveEntity)

    @Query("DELETE FROM clicker_save")
    suspend fun deleteClicker()
}
