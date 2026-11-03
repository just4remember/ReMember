package dev.chaik.remember.feature.gym

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R
import dev.chaik.remember.core.model.ExerciseKind
import dev.chaik.remember.core.model.formatKg
import dev.chaik.remember.core.model.parseKg
import java.time.Duration
import kotlin.math.abs
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

/** «17:00» — без секунд, даже если время начала записано с ними. */
fun formatTime(time: LocalTime): String = time.format(TimeFormat)

/** Длительность от начала до конца; тренировка через полночь тоже считается. */
fun workoutDuration(start: LocalTime?, end: LocalTime?): Duration? {
    if (start == null || end == null) return null
    val d = Duration.between(start.withSecond(0).withNano(0), end.withSecond(0).withNano(0))
    return if (d.isNegative) d.plusDays(1) else d
}

/** «40 мин», «1 ч 30 мин». */
@Composable
fun durationLabel(duration: Duration): String {
    val minutes = duration.toMinutes().toInt()
    return if (minutes < 60) {
        stringResource(R.string.gym_minutes, minutes)
    } else {
        stringResource(R.string.gym_hours_minutes, minutes / 60, minutes % 60)
    }
}

@Composable
fun ExerciseKind.label(): String = stringResource(
    when (this) {
        ExerciseKind.WEIGHT_REPS -> R.string.gym_kind_weight_reps
        ExerciseKind.REPS -> R.string.gym_kind_reps
        ExerciseKind.TIME -> R.string.gym_kind_time
        ExerciseKind.SPEED_TIME -> R.string.gym_kind_speed_time
        ExerciseKind.DISTANCE_TIME -> R.string.gym_kind_distance_time
        ExerciseKind.WEIGHT_TIME -> R.string.gym_kind_weight_time
        ExerciseKind.DISTANCE -> R.string.gym_kind_distance
    },
)

/**
 * Форма веса тела до или после тренировки. Её всегда можно пропустить.
 * [initial] — подставленное значение (прошлый вес).
 */
@Composable
fun BodyWeightDialog(
    title: String,
    initial: Double?,
    onSave: (Double) -> Unit,
    onSkip: () -> Unit,
) {
    var text by remember { mutableStateOf(initial?.let(::formatKg).orEmpty()) }
    val value = parseKg(text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> if (new.length <= 6 && new.all { it.isDigit() || it == ',' || it == '.' }) text = new },
                    label = { Text(stringResource(R.string.gym_weight_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                // Вес меняется по чуть-чуть — подправить на 100 г без клавиатуры
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(-0.5, -0.1, 0.1, 0.5).forEach { step ->
                        FilledTonalButton(
                            onClick = { text = formatKg(((value ?: initial ?: 0.0) + step).coerceAtLeast(0.0).roundTo(0.1)) },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text((if (step > 0) "+" else "−") + formatKg(abs(step)))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = value != null, onClick = { value?.let(onSave) }) { Text(stringResource(R.string.gym_save)) }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.gym_skip)) }
        },
    )
}

private fun Double.roundTo(step: Double): Double = Math.round(this / step) * step
