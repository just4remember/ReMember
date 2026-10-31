package dev.chaik.remember.core.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Отступ снизу, который нужен контенту экрана, чтобы последние элементы
 * не прятались под плавающим островком (включает системную панель навигации).
 */
val LocalIslandPadding = staticCompositionLocalOf<Dp> { 0.dp }

/** Видна ли вкладка этого экрана: соседние вкладки остаются в памяти и об уходе с них иначе не узнают. */
val LocalTabVisible = compositionLocalOf { true }
