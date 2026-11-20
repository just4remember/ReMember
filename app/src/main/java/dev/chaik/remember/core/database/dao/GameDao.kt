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

    /** Снимок игры для отладки (строка id = 2): запомнить перед проверками и вернуть после. */
    @Query("SELECT * FROM clicker_save WHERE id = 2")
    suspend fun clickerSnapshot(): ClickerSaveEntity?

    @Upsert
    suspend fun saveClicker(save: ClickerSaveEntity)

    /** Сбросить игру; снимок для отладки остаётся. */
    @Query("DELETE FROM clicker_save WHERE id = 1")
    suspend fun deleteClicker()
}
