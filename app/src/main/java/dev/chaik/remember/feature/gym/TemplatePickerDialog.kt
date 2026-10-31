package dev.chaik.remember.feature.gym

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R
import dev.chaik.remember.core.model.WorkoutTemplate

/**
 * Выбор шаблонов. Номер в кружке — порядок нажатий: в этом порядке упражнения встанут в тренировку.
 */
@Composable
fun TemplatePickerDialog(
    templates: List<WorkoutTemplate>,
    onConfirm: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(emptyList<Long>()) }
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gym_templates_pick)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                templates.forEach { template ->
                    val number = picked.indexOf(template.id) + 1
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { picked = if (number > 0) picked - template.id else picked + template.id }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(26.dp)
                                .then(
                                    if (number > 0) {
                                        Modifier.background(colors.primary, CircleShape)
                                    } else {
                                        Modifier.border(2.dp, colors.outline, CircleShape)
                                    },
                                ),
                        ) {
                            if (number > 0) {
                                Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = colors.onPrimary)
                            }
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                        ) {
                            Text(templateTitle(template), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = template.exercises.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = picked.isNotEmpty(), onClick = { onConfirm(picked) }) { Text(stringResource(R.string.gym_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun templateTitle(template: WorkoutTemplate): String = template.name.ifBlank { stringResource(R.string.gym_template_untitled) }
