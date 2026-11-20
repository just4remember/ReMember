package dev.chaik.remember.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Сохранение кликера: одна строка, состояние игры — JSON (формат — дело игры), и когда
 * сохранено: по разнице с «сейчас» при следующем входе считается доход вне игры.
 * Появилось в версии 16 базы.
 */
@Entity(tableName = "clicker_save")
data class ClickerSaveEntity(
    @PrimaryKey val id: Int = 1,
    val state: String,
    /** Когда сохранено, миллисекунды Unix. */
    @ColumnInfo(name = "saved_at") val savedAt: Long,
)
