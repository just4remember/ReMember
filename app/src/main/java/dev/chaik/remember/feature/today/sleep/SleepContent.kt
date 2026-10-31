package dev.chaik.remember.feature.today.sleep

import dev.chaik.remember.core.editor.RichEditor
import java.io.File
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.model.Rating
import dev.chaik.remember.core.ui.components.StarColor
import dev.chaik.remember.core.ui.components.StarRating
import dev.chaik.remember.core.ui.components.TimePickerDialog
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

/** Режим «Сон»: время, качество и что снилось. */
@Composable
fun SleepContent(controller: SleepController, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf<TimeField?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeButton(
                        label = stringResource(R.string.sleep_bed),
                        icon = Icons.Outlined.Bedtime,
                        time = controller.bedTime,
                        onClick = { picking = TimeField.Bed },
                        modifier = Modifier.weight(1f),
                    )
                    TimeButton(
                        label = stringResource(R.string.sleep_wake),
                        icon = Icons.Outlined.WbTwilight,
                        time = controller.wakeTime,
                        onClick = { picking = TimeField.Wake },
                        modifier = Modifier.weight(1f),
                    )
                }
                controller.duration?.let { duration ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.sleep_duration, duration.toHours().toInt(), duration.toMinutesPart()),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                SleepGuessRow(controller)
            }
        }

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.sleep_quality),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.sleep_quality_value, controller.quality?.let(Rating::format) ?: "0"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (controller.quality != null) StarColor else MaterialTheme.colorScheme.outline,
                )
            }
            StarRating(
                halves = controller.quality,
                onChange = { controller.quality = it.takeIf { halves -> halves > 0 } }, // 0 звёзд — не указано
                stars = Rating.SLEEP_STARS,
                starSize = 36.dp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Column {
            Text(
                text = stringResource(R.string.sleep_dream),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            RichEditor(
                state = controller.dream,
                // Фото в запись сна не вставляются
                photoFile = { File("") },
                onOpenPhotos = { _, _ -> },
                placeholder = stringResource(R.string.sleep_dream_hint),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    picking?.let { field ->
        val initial = when (field) {
            TimeField.Bed -> controller.bedTime ?: LocalTime.of(23, 0)
            TimeField.Wake -> controller.wakeTime ?: LocalTime.of(7, 30)
        }
        TimePickerDialog(
            initial = initial,
            onPick = { time ->
                when (field) {
                    TimeField.Bed -> controller.bedTime = time
                    TimeField.Wake -> controller.wakeTime = time
                }
                picking = null
            },
            onClear = {
                when (field) {
                    TimeField.Bed -> controller.bedTime = null
                    TimeField.Wake -> controller.wakeTime = null
                }
                picking = null
            },
            onDismiss = { picking = null },
            clearLabel = stringResource(R.string.sleep_clear_time),
        )
    }
}

private enum class TimeField { Bed, Wake }

/**
 * Подсказка под полями времени: подставить сон, найденный по истории телефона, или
 * предложить дать доступ к истории. Если время уже такое же — кнопка не нужна.
 */
@Composable
private fun SleepGuessRow(controller: SleepController) {
    val context = LocalContext.current
    val container = context.appContainer
    // Вернулись из настроек Android (могли дать доступ) или в приложение утром — пересчитать
    LifecycleResumeEffect(Unit) {
        controller.refreshGuess()
        onPauseOrDispose { }
    }
    when (val guess = controller.guess) {
        is SleepGuessState.Found -> if (controller.bedTime != guess.bed || controller.wakeTime != guess.wake) {
            FilledTonalButton(
                onClick = controller::applyGuess,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.sleep_guess_apply, guess.bed.format(TimeFormat), guess.wake.format(TimeFormat)),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        SleepGuessState.NoAccess -> Column(Modifier.padding(top = 12.dp)) {
            OutlinedButton(
                onClick = {
                    // Экран настроек Android — не повод блокировать приложение
                    container.appLock.allowExternal()
                    context.startActivity(container.phoneUsage.accessSettingsIntent())
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.sleep_guess_allow), modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                text = stringResource(R.string.sleep_guess_allow_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        SleepGuessState.NotFound -> if (controller.bedTime == null && controller.wakeTime == null) {
            Text(
                text = stringResource(R.string.sleep_guess_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        SleepGuessState.Off -> Unit
    }
}

@Composable
private fun TimeButton(
    label: String,
    icon: ImageVector,
    time: LocalTime?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                text = time?.format(TimeFormat) ?: stringResource(R.string.sleep_not_set),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
