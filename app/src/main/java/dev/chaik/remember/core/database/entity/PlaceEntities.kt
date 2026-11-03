package dev.chaik.remember.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/** Место на карте: где был и что там было. */
@Entity(tableName = "place")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    /** Что там было — обычный текст. */
    val note: String,
    /** Категория (см. [PlaceCategoryEntity]); null — без категории. */
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    /** Когда был; null — не указано. */
    @ColumnInfo(name = "visited_on") val visitedOn: LocalDate?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

/** Категория меток: своё название, цвет и значок. */
@Entity(tableName = "place_category")
data class PlaceCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Цвет метки, ARGB. */
    val color: Int,
    /** Ключ значка (см. PlaceIcons). */
    val icon: String,
    val position: Int,
)
