package dev.chaik.remember.feature.settings.feedback

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.EmojiEvents
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R

/** Сколько очков за ошибку: найти её труднее, чем придумать улучшение. */
const val BUG_POINTS = 3

/** Отправленные отзывы: ошибки и предложения. */
data class FeedbackCounts(val bugs: Int, val ideas: Int) {
    val points: Int get() = bugs * BUG_POINTS + ideas
}

/** Уровень значка: с какого числа очков, название, значок и цвет. Следующий — круче. */
enum class FeedbackRank(val fromPoints: Int, @StringRes val title: Int, val icon: ImageVector, val color: Color?) {
    /** Ещё ничего не отправлено: контур, цвет темы. */
    NEWCOMER(0, R.string.feedback_rank_newcomer, Icons.Outlined.EmojiEvents, null),
    BRONZE(1, R.string.feedback_rank_bronze, Icons.Filled.MilitaryTech, Color(0xFFD08A4E)),
    SILVER(10, R.string.feedback_rank_silver, Icons.Filled.EmojiEvents, Color(0xFFB8C4D0)),
    GOLD(25, R.string.feedback_rank_gold, Icons.Filled.EmojiEvents, Color(0xFFFFC83D)),
    LEGEND(60, R.string.feedback_rank_legend, Icons.Filled.WorkspacePremium, Color(0xFFA25CE6)),
    ;

    val next: FeedbackRank? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun of(points: Int): FeedbackRank = entries.last { points >= it.fromPoints }
    }
}

/** Карточка вверху страницы: значок (тап — какие бывают), его название и два счётчика. */
@Composable
fun FeedbackRewardsCard(counts: FeedbackCounts) {
    var showRanks by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val rank = FeedbackRank.of(counts.points)
    val tint = rank.color ?: colors.outline
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerLow)
            .clickable(onClickLabel = stringResource(R.string.feedback_ranks_title)) { showRanks = true }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f)),
        ) {
            Icon(rank.icon, null, tint = tint, modifier = Modifier.size(30.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(rank.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                pluralStringResource(R.plurals.feedback_points, counts.points, counts.points),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Counter(counts.bugs, stringResource(R.string.feedback_counter_bugs), colors.error)
        Counter(counts.ideas, stringResource(R.string.feedback_counter_ideas), colors.primary)
    }
    if (showRanks) FeedbackRanksDialog(counts, onDismiss = { showRanks = false })
}

@Composable
private fun Counter(value: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Все уровни и сколько очков нужно для каждого; текущий выделен, внизу — сколько до следующего. */
@Composable
private fun FeedbackRanksDialog(counts: FeedbackCounts, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val active = FeedbackRank.of(counts.points)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feedback_ranks_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.feedback_ranks_text, BUG_POINTS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                FeedbackRank.entries.forEach { rank ->
                    val tint = rank.color ?: colors.outline
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .then(if (rank == active) Modifier.background(colors.surfaceContainerHighest) else Modifier)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(rank.icon, null, tint = tint, modifier = Modifier.size(26.dp))
                        Text(
                            stringResource(rank.title),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (rank == active) FontWeight.Bold else null,
                            modifier = Modifier.padding(start = 12.dp).weight(1f),
                        )
                        Text(
                            pluralStringResource(R.plurals.feedback_points, rank.fromPoints, rank.fromPoints),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = active.next?.let { next ->
                        val left = next.fromPoints - counts.points
                        pluralStringResource(R.plurals.feedback_ranks_next, left, left, stringResource(next.title))
                    } ?: stringResource(R.string.feedback_ranks_top),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}
