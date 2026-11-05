package dev.chaik.remember.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay

/** Сколько текст стоит у края, прежде чем поехать обратно. */
private const val SLIDE_PAUSE_MS = 1500L
private const val SLIDE_MS_PER_PX = 8

/**
 * Текст в одну строку; если не влез — плавно ездит туда и обратно. Высота всегда в одну строку:
 * длинная подпись не сдвигает то, что ниже.
 */
@Composable
fun SlidingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
) {
    val scroll = rememberScrollState()
    val distance = scroll.maxValue
    LaunchedEffect(distance, text) {
        // До первого замера maxValue равен Int.MAX_VALUE
        scroll.scrollTo(0)
        if (distance == 0 || distance == Int.MAX_VALUE) return@LaunchedEffect
        val slide = tween<Float>(durationMillis = distance * SLIDE_MS_PER_PX, easing = LinearEasing)
        while (true) {
            delay(SLIDE_PAUSE_MS)
            scroll.animateScrollTo(distance, slide)
            delay(SLIDE_PAUSE_MS)
            scroll.animateScrollTo(0, slide)
        }
    }
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.horizontalScroll(scroll, enabled = false),
    )
}
