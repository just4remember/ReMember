package dev.chaik.remember.core.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * «Вдавленность»: заливка [fill] и мягкая тень внутрь от верхнего края, как будто элемент
 * утоплен в подложку. В Compose готовой внутренней тени нет — рисуем размытую рамку вокруг
 * фигуры, сдвинутую вниз, и обрезаем её по самой фигуре.
 */
fun Modifier.innerShadow(
    radius: Dp,
    fill: Color,
    shadow: Color = Color.Black.copy(alpha = 0.35f),
    blur: Dp = 6.dp,
    offsetY: Dp = 2.dp,
): Modifier = drawBehind {
    val r = radius.toPx()
    val w = size.width
    val h = size.height
    drawRoundRect(fill, cornerRadius = CornerRadius(r))
    val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r))) }
    clipPath(shape) {
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = shadow.toArgb()
                maskFilter = BlurMaskFilter(blur.toPx(), BlurMaskFilter.Blur.NORMAL)
            }
            val dy = offsetY.toPx()
            // Рамка: всё вокруг фигуры, кроме неё самой (сдвинутой вниз) — её размытый край и есть тень
            val frame = android.graphics.Path().apply {
                fillType = android.graphics.Path.FillType.EVEN_ODD
                addRect(-w, -h, w * 2, h * 2, android.graphics.Path.Direction.CW)
                addRoundRect(0f, dy, w, h + dy, r, r, android.graphics.Path.Direction.CW)
            }
            canvas.nativeCanvas.drawPath(frame, paint)
        }
    }
}
