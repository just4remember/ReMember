package dev.chaik.remember.core.model

import java.time.LocalDate

/** Отметка на карте. */
data class Place(
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val title: String = "",
    val note: String = "",
    val visitedOn: LocalDate? = null,
    val categoryId: Long? = null,
)

/** Категория меток. [color] — ARGB, [icon] — ключ значка. */
data class PlaceCategory(
    val id: Long = 0,
    val name: String,
    val color: Int,
    val icon: String,
    val position: Int = 0,
)
