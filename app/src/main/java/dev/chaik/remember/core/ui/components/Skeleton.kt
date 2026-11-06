package dev.chaik.remember.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Заготовка на месте того, что ещё грузится: серая плашка, по которой бежит блик.
 * Так видно, что идёт загрузка, а не что экран завис.
 */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val shine = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f).compositeOver(base)
    val progress by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shine",
    )
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .drawBehind {
                // Блик — полоса шириной в половину плашки, едет слева направо
                val band = size.width * 0.5f
                val x = progress * size.width
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(base, shine, base),
                        start = Offset(x - band, 0f),
                        end = Offset(x + band, size.height),
                    ),
                )
            },
    )
}
