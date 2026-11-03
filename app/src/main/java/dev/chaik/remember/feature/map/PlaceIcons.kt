package dev.chaik.remember.feature.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Museum
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Значки категорий. Ключ хранится в базе — менять ключи нельзя. */
internal val PlaceIcons: Map<String, ImageVector> = linkedMapOf(
    "place" to Icons.Outlined.Place,
    "food" to Icons.Outlined.Restaurant,
    "cafe" to Icons.Outlined.LocalCafe,
    "park" to Icons.Outlined.Park,
    "people" to Icons.Outlined.People,
    "flight" to Icons.Outlined.Flight,
    "home" to Icons.Outlined.Home,
    "work" to Icons.Outlined.Work,
    "study" to Icons.Outlined.School,
    "museum" to Icons.Outlined.Museum,
    "music" to Icons.Outlined.MusicNote,
    "party" to Icons.Outlined.Celebration,
    "sport" to Icons.Outlined.SportsSoccer,
    "shop" to Icons.Outlined.ShoppingBag,
    "star" to Icons.Outlined.Star,
)

internal fun placeIcon(key: String?): ImageVector = PlaceIcons[key] ?: Icons.Outlined.Place

/** Цвета категорий: насыщенные, чтобы метка читалась и на светлой, и на тёмной карте. */
internal val PlaceColors: List<Int> = listOf(
    0xFFEF6C00, 0xFFD32F2F, 0xFFC2185B, 0xFF8E24AA, 0xFF5E35B1, 0xFF1565C0,
    0xFF0288D1, 0xFF00897B, 0xFF2E7D32, 0xFF9E9D24, 0xFF6D4C41, 0xFF546E7A,
).map { it.toInt() }
