package dev.chaik.remember.core.ui

import android.text.TextUtils
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/**
 * Заглавная буква после многоточия. Клавиатура спрашивает у поля, нужна ли заглавная
 * (getCursorCapsMode), а Android считает «...» сокращением вроде «т.е.» и отвечает «нет».
 * Здесь ответ поправлен: после «...» или «…» и пробела начинается новое предложение.
 * Сам текст не меняется — клавиатура лишь включает Shift, как после точки.
 *
 * Перехват идёт через CompositionLocal, поэтому действует и на поля в диалогах.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EllipsisCapitalization(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            nextHandler.startInputMethod(
                object : PlatformTextInputMethodRequest {
                    override fun createInputConnection(outAttributes: EditorInfo): InputConnection =
                        EllipsisCapsConnection(request.createInputConnection(outAttributes))
                },
            )
        },
        content = content,
    )
}

private class EllipsisCapsConnection(target: InputConnection) : InputConnectionWrapper(target, false) {
    override fun getCursorCapsMode(reqModes: Int): Int {
        val mode = super.getCursorCapsMode(reqModes)
        if (reqModes and TextUtils.CAP_MODE_SENTENCES == 0 || mode and TextUtils.CAP_MODE_SENTENCES != 0) return mode
        val before = getTextBeforeCursor(LOOK_BACK, 0) ?: return mode
        return if (EllipsisCaps.startsSentence(before)) mode or TextUtils.CAP_MODE_SENTENCES else mode
    }

    private companion object {
        const val LOOK_BACK = 32
    }
}

object EllipsisCaps {
    /** После многоточия, возможно закрывающих кавычек или скобок, и пробела. */
    private val afterEllipsis = Regex("""(\.\.\.|…)["'»”)\]]*\s+$""")

    fun startsSentence(before: CharSequence): Boolean = afterEllipsis.containsMatchIn(before)
}
