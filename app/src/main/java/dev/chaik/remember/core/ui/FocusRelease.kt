package dev.chaik.remember.core.ui

import android.view.ViewTreeObserver
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Курсор не остаётся в поле без клавиатуры: фокус снимается, когда клавиатуру свернули
 * и когда нажали на свободное место — туда, где нет ни кнопки, ни поля.
 *
 * Работает в пределах своего окна: стоит в корне приложения, а у окон и шторок с полями
 * ввода — на их содержимом (у них своё окно и свой фокус).
 */
@Composable
fun Modifier.releaseFocus(): Modifier {
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Клавиатуру свернули. Если она пропала, потому что открылось другое приложение (выбор фото,
    // камера), курсор не трогаем: экран сам решит, куда его вернуть
    DisposableEffect(view, focusManager) {
        var shown = imeVisible(view)
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val visible = imeVisible(view)
            if (shown && !visible && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) focusManager.clearFocus()
            shown = visible
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }

    // Нажатие по свободному месту: смотрим последними, когда кнопки и поля уже забрали своё
    return pointerInput(focusManager) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            if (down.isConsumed) return@awaitEachGesture
            val up = waitForUpOrCancellation(pass = PointerEventPass.Final) ?: return@awaitEachGesture
            if (!up.isConsumed && (up.position - down.position).getDistance() < viewConfiguration.touchSlop) {
                focusManager.clearFocus()
            }
        }
    }
}

private fun imeVisible(view: android.view.View): Boolean =
    ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
