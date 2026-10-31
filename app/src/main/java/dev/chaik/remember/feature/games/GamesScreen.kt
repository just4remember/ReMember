package dev.chaik.remember.feature.games

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Abc
import androidx.compose.material.icons.outlined.DataArray
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.feature.games.fiveletters.FiveLettersRoute
import dev.chaik.remember.feature.games.raskobki.RaskobkiRoute
import dev.chaik.remember.feature.more.SectionTile
import kotlinx.serialization.Serializable

@Serializable
data object GamesRoute : NavKey

/** Игра в списке раздела «Игры». Новая игра — новая строка в [AllGames]. */
private data class GameInfo(@param:StringRes val title: Int, @param:StringRes val hint: Int, val icon: ImageVector, val route: NavKey)

private val AllGames = listOf(
    GameInfo(R.string.game_title, R.string.game_five_letters_hint, Icons.Outlined.Abc, FiveLettersRoute),
    GameInfo(R.string.game_raskobki, R.string.game_raskobki_hint, Icons.Outlined.DataArray, RaskobkiRoute),
)

/** «Игры»: плитки игр, как плитки разделов в «Ещё». */
@Composable
fun GamesScreen(onBack: (() -> Unit)?, onOpen: (NavKey) -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(title = stringResource(R.string.section_games), modifier = modifier, onBack = onBack) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(AllGames) { game ->
                SectionTile(
                    icon = game.icon,
                    title = stringResource(game.title),
                    hint = stringResource(game.hint),
                    accentIndex = AllGames.indexOf(game),
                    onClick = { onOpen(game.route) },
                )
            }
        }
    }
}
