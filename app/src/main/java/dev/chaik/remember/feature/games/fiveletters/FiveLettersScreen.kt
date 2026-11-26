package dev.chaik.remember.feature.games.fiveletters

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.ui.components.ScreenScaffold
import kotlinx.serialization.Serializable

@Serializable
data object FiveLettersRoute : NavKey

/**
 * «Пять букв» в разделе «Игры»: та же игра, что у будильника, только без отсчёта
 * и с кнопкой «Следующее слово». Незаконченное слово сохраняется — можно вернуться позже.
 */
@Composable
fun FiveLettersScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val store = container.gamesStore
    val dictionary = container.wordDictionary
    var game by remember { mutableStateOf(store.fiveLetters ?: dictionary.newRound()) }

    ScreenScaffold(title = stringResource(R.string.game_title), modifier = modifier, onBack = onBack) { padding ->
        WordGameScreen(
            state = game,
            onChange = {
                game = it
                store.fiveLetters = it
            },
            onDone = {
                game = dictionary.newRound()
                store.fiveLetters = game
            },
            mode = WordGameMode.FREE,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
        )
    }
}
