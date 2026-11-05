package dev.chaik.remember.feature.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import dev.chaik.remember.R

/** Светлота акцента: слишком тёмный или светлый цвет не годится для кнопок — держим середину. */
private const val MIN_LIGHTNESS = 0.25f
private const val MAX_LIGHTNESS = 0.75f

/**
 * Свой акцентный цвет: оттенок, насыщенность, светлота — тремя полосами,
 * или код вида #5B6CF0. Сверху — образец.
 */
@Composable
fun ColorPickerDialog(
    initial: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
    /** Пределы светлоты: для акцента — середина, для подложек виджетов можно и тёмные. */
    minLightness: Float = MIN_LIGHTNESS,
    maxLightness: Float = MAX_LIGHTNESS,
) {
    val start = FloatArray(3).also { ColorUtils.colorToHSL(initial.toInt(), it) }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var lightness by remember { mutableFloatStateOf(start[2].coerceIn(minLightness, maxLightness)) }
    val color = hsl(hue, saturation, lightness)
    var hex by remember { mutableStateOf(color.hex()) }
    fun syncHex() {
        hex = hsl(hue, saturation, lightness).hex()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title ?: stringResource(R.string.appearance_custom_color)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(color),
                )
                GradientSlider(
                    value = hue / 360f,
                    colors = (0..6).map { hsl(it * 60f, 0.85f, 0.5f) },
                    onChange = {
                        hue = it * 360f
                        syncHex()
                    },
                )
                GradientSlider(
                    value = saturation,
                    colors = listOf(hsl(hue, 0f, lightness), hsl(hue, 1f, lightness)),
                    onChange = {
                        saturation = it
                        syncHex()
                    },
                )
                GradientSlider(
                    value = (lightness - minLightness) / (maxLightness - minLightness),
                    colors = listOf(hsl(hue, saturation, minLightness), hsl(hue, saturation, 0.5f), hsl(hue, saturation, maxLightness)),
                    onChange = {
                        lightness = minLightness + it * (maxLightness - minLightness)
                        syncHex()
                    },
                )
                OutlinedTextField(
                    value = hex,
                    onValueChange = { text ->
                        val clean = text.uppercase().filter { it.isLetterOrDigit() || it == '#' }.take(7)
                        hex = clean
                        parseHex(clean)?.let { parsed ->
                            val out = FloatArray(3).also { ColorUtils.colorToHSL(parsed, it) }
                            hue = out[0]
                            saturation = out[1]
                            lightness = out[2].coerceIn(minLightness, maxLightness)
                        }
                    },
                    label = { Text(stringResource(R.string.appearance_color_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(color.toArgb().toLong() and 0xFFFFFFFFL) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Полоса с градиентом и кружком-ползунком; тап или перетаскивание задают [value] от 0 до 1. */
@Composable
private fun GradientSlider(value: Float, colors: List<Color>, onChange: (Float) -> Unit) {
    BoxWithConstraints(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
    ) {
        val width = constraints.maxWidth.toFloat()
        val thumb = 28.dp
        Box(
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.horizontalGradient(colors))
                .pointerInput(width) {
                    detectTapGestures { onChange((it.x / width).coerceIn(0f, 1f)) }
                }
                .pointerInput(width) {
                    detectHorizontalDragGestures { change, _ -> onChange((change.position.x / width).coerceIn(0f, 1f)) }
                },
        )
        Box(
            Modifier
                .offset(x = (maxWidth - thumb) * value.coerceIn(0f, 1f))
                .size(thumb)
                .border(3.dp, Color.White, CircleShape)
                .padding(3.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
    }
}

private fun hsl(h: Float, s: Float, l: Float) = Color(ColorUtils.HSLToColor(floatArrayOf(h.coerceIn(0f, 359.9f), s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))

private fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

/** «#5B6CF0» или «5B6CF0» → цвет; иначе null. */
private fun parseHex(text: String): Int? {
    val digits = text.removePrefix("#")
    if (digits.length != 6) return null
    return digits.toIntOrNull(16)?.let { it or 0xFF000000.toInt() }
}
