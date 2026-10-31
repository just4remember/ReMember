package dev.chaik.remember.feature.today.day

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chaik.remember.R
import dev.chaik.remember.core.model.Rating
import dev.chaik.remember.core.ui.components.StarColor
import dev.chaik.remember.core.ui.components.StarRating
import dev.chaik.remember.core.ui.components.TagChips

/** Оценка дня: звёзды и справа число «9.5/10». 0 звёзд — оценки нет. */
@Composable
fun DayRating(controller: DayController, modifier: Modifier = Modifier) {
    val mood = controller.mood
    // Ушли с этой страницы (другой режим или вкладка) — оценка закрывается
    DisposableEffect(controller) { onDispose { controller.lockRating() } }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StarRating(
            halves = mood,
            onChange = controller::setMood,
            secret = true,
            starSize = 24.dp,
            enabled = !controller.ratingLocked,
        )
        Spacer(Modifier.weight(1f))
        if (controller.ratingLocked) {
            IconButton(onClick = controller::unlockRating, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = stringResource(R.string.day_rating_unlock),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = when {
                mood == null -> stringResource(R.string.day_rating_none)
                Rating.isSecret(mood) -> stringResource(R.string.day_rating_secret)
                else -> stringResource(R.string.day_rating_value, Rating.format(mood))
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (mood != null) StarColor else MaterialTheme.colorScheme.outline,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** Теги дня. */
@Composable
fun DayTags(controller: DayController, modifier: Modifier = Modifier) {
    val allTags by controller.allTags.collectAsStateWithLifecycle()
    TagChips(
        tags = controller.tags,
        allTags = allTags,
        onRemove = controller::removeTag,
        onToggle = controller::toggleTag,
        onCreate = controller::addTag,
        modifier = modifier,
    )
}
