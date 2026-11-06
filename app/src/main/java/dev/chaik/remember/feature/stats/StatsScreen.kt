package dev.chaik.remember.feature.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.app.navigation.Destination
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.feature.more.SectionTile
import dev.chaik.remember.feature.stats.days.DaysStatsRoute
import dev.chaik.remember.feature.stats.finance.FinanceStatsRoute
import dev.chaik.remember.feature.stats.gym.GymStatsRoute
import dev.chaik.remember.feature.stats.health.HealthStatsRoute
import dev.chaik.remember.feature.stats.sleep.SleepStatsRoute
import kotlinx.serialization.Serializable

@Serializable
data object StatsRoute : NavKey

/** Раздел статистики: плитка, подпись и страница с графиками; null — страница ещё не готова. */
private class StatsSection(val icon: ImageVector, val title: Int, val hint: Int, val route: NavKey?)

private val Sections = listOf(
    StatsSection(Icons.Outlined.Bedtime, R.string.stats_sleep, R.string.stats_sleep_hint, SleepStatsRoute),
    StatsSection(Destination.Today.icon, R.string.stats_days, R.string.stats_days_hint, DaysStatsRoute),
    StatsSection(Destination.Finance.icon, R.string.stats_finance, R.string.stats_finance_hint, FinanceStatsRoute),
    StatsSection(Destination.Gym.icon, R.string.stats_gym, R.string.stats_gym_hint, GymStatsRoute),
    StatsSection(Destination.Health.icon, R.string.stats_health, R.string.stats_health_hint, HealthStatsRoute),
)

/** Статистика: плитки разделов, тап — страница с графиками этого раздела. */
@Composable
fun StatsScreen(
    onBack: (() -> Unit)?,
    onOpen: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(title = stringResource(R.string.section_stats), modifier = modifier, onBack = onBack) { padding ->
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
            items(Sections, key = { it.title }) { section ->
                // Ещё не готовые разделы видны, но бледные: понятно, что будет дальше
                Box(Modifier.alpha(if (section.route != null) 1f else 0.45f), propagateMinConstraints = true) {
                    SectionTile(
                        icon = section.icon,
                        title = stringResource(section.title),
                        hint = stringResource(section.hint),
                        accentIndex = Sections.indexOf(section),
                        onClick = { section.route?.let(onOpen) },
                    )
                }
            }
        }
    }
}
