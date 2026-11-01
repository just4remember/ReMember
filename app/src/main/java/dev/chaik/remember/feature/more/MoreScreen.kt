package dev.chaik.remember.feature.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.app.navigation.Destination
import dev.chaik.remember.app.navigation.moreTiles
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.ui.components.ScreenScaffold
import kotlinx.serialization.Serializable

@Serializable
data object MoreRoute : NavKey

/**
 * Полноэкранное «боковое меню»: плитки разделов, которых нет в островке, в порядке
 * из Настроек → Страницы; «Настройки» — последней плиткой на всю ширину.
 */
@Composable
fun MoreScreen(
    onOpen: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = LocalAppSettings.current
    val tiles = remember(settings.islandTabs, settings.moreOrder) { settings.moreTiles() }
    ScreenScaffold(title = stringResource(R.string.tab_more), modifier = modifier) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection) + 16.dp,
                end = padding.calculateEndPadding(layoutDirection) + 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = tiles,
                key = { it.name },
                // Настройки — последней плиткой на всю ширину
                span = { GridItemSpan(if (it == Destination.Settings) maxLineSpan else 1) },
            ) { section ->
                SectionTile(
                    icon = section.icon,
                    title = stringResource(section.label),
                    hint = stringResource(section.hint),
                    accentIndex = section.ordinal,
                    onClick = { onOpen(section.root) },
                )
            }
        }
    }
}

/** Плитка раздела (или игры): значок в цветном квадрате, название и подпись в две строки. */
@Composable
fun SectionTile(
    icon: ImageVector,
    title: String,
    hint: String,
    accentIndex: Int,
    onClick: () -> Unit,
    /** Заголовок с оформлением; если задан, показывается вместо [title]. */
    styledTitle: AnnotatedString? = null,
) {
    val (container, content) = MaterialTheme.colorScheme.accentFor(accentIndex)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = container,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = content)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = styledTitle ?: AnnotatedString(title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Чередуем акцентные цвета темы, чтобы плитки различались, но оставались в палитре. */
private fun ColorScheme.accentFor(index: Int): Pair<Color, Color> = when (index % 3) {
    0 -> primaryContainer to onPrimaryContainer
    1 -> secondaryContainer to onSecondaryContainer
    else -> tertiaryContainer to onTertiaryContainer
}
