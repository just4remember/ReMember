package dev.chaik.remember.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Запрет жестов, уводящих со страницы: свайпа «назад» (SwipeBackHost) и листания вкладок.
 * Нужен, пока идёт тонкая работа, где палец легко съезжает вбок, — например, пока
 * настраиваешь размер выделенного фото в заметке.
 */
object BackGestureLock {
    private var holders by mutableIntStateOf(0)

    /** Сейчас кто-то держит запрет. */
    val isHeld: Boolean get() = holders > 0

    /** Держать запрет, пока [hold] и этот элемент на экране. */
    @Composable
    fun Hold(hold: Boolean) {
        DisposableEffect(hold) {
            if (hold) holders++
            onDispose { if (hold) holders-- }
        }
    }
}
