package dev.chaik.remember.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chaik.remember.R

/** Цвет огонька с такого числа дней подряд. Следующая строка — следующий цвет. */
private class StreakLevel(val fromDays: Int, val color: Color)

private val StreakLevels = listOf(
    StreakLevel(1, Color(0xFFFFC83D)), // жёлтый
    StreakLevel(7, Color(0xFFFF8C2E)), // оранжевый
    StreakLevel(14, Color(0xFFF0504D)), // красный
    StreakLevel(30, Color(0xFF5CC8F5)), // голубой
    StreakLevel(60, Color(0xFF4A86F0)), // синий
    StreakLevel(100, Color(0xFFA25CE6)), // фиолетовый
    StreakLevel(200, Color(0xFF3FB868)), // зелёный
)

private fun streakLevel(days: Int): StreakLevel? = StreakLevels.lastOrNull { days >= it.fromDays }

/** Цвет огонька для серии такой длины; null — серии нет. */
fun streakColor(days: Int): Color? = streakLevel(days)?.color

/** Огонёк серии: значок и число одного цвета, цвет растёт вместе с серией. Тап — какие цвета бывают. */
@Composable
fun StreakFlame(days: Int, modifier: Modifier = Modifier, suffix: String? = null) {
    var showLevels by remember { mutableStateOf(false) }
    val description = pluralStringResource(R.plurals.today_streak, days, days)
    val color = streakLevel(days)?.color ?: MaterialTheme.colorScheme.outline
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = stringResource(R.string.today_streak_levels)) { showLevels = true }
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Icon(Icons.Filled.LocalFireDepartment, null, tint = color, modifier = Modifier.size(22.dp))
        Text(days.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        suffix?.let {
            Text(
                it,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
    if (showLevels) StreakLevelsDialog(days, description, onDismiss = { showLevels = false })
}

/** Все цвета огонька и сколько дней подряд нужно для каждого; текущий выделен. */
@Composable
private fun StreakLevelsDialog(days: Int, current: String, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val active = streakLevel(days)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.today_streak_levels)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = current,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                StreakLevels.forEachIndexed { index, level ->
                    val next = StreakLevels.getOrNull(index + 1)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .then(if (level === active) Modifier.background(colors.surfaceContainerHighest) else Modifier)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Filled.LocalFireDepartment, null, tint = level.color, modifier = Modifier.size(26.dp))
                        Text(
                            text = if (next != null) {
                                stringResource(R.string.today_streak_range, level.fromDays, next.fromDays - 1)
                            } else {
                                stringResource(R.string.today_streak_from, level.fromDays)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (level === active) FontWeight.Bold else null,
                            color = level.color,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}
