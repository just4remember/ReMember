package dev.chaik.remember.feature.games.fiveletters

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.core.wordgame.GuessError
import dev.chaik.remember.core.wordgame.LetterMark
import dev.chaik.remember.core.wordgame.WordGame
import dev.chaik.remember.core.wordgame.WordGameState
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** Цвета клеток — одинаковые в любой теме, чтобы подсказки читались сразу, спросонья. */
private val HitColor = Color(0xFF2E9E6B)
private val PresentColor = Color(0xFFD99A1E)

/** Переворот одной клетки, мс; следующая начинает, когда предыдущая прошла [FLIP_STAGGER]. */
private const val FLIP_MS = 320f
private const val FLIP_STAGGER = 0.6f
private const val FlipUnits = 1f + (WordGameState.WORD_LENGTH - 1) * FLIP_STAGGER

/** Подпрыгивание одной клетки при победе, мс, и шаг волны. */
private const val BOUNCE_MS = 280f
private const val BOUNCE_STAGGER = 0.3f
private const val BounceUnits = 1f + (WordGameState.WORD_LENGTH - 1) * BOUNCE_STAGGER
private val BounceHeight = 16.dp

/** Высота клавиатуры (3 ряда по 54 dp, промежутки, отступ снизу) — столько же занимает кнопка после игры. */
private val KeyboardHeight = 54.dp * 3 + 6.dp * 2 + 8.dp
private val TopHeight = 48.dp

private const val KeyEnter = '\n'
private const val KeyErase = '\b'
private val KeyRows = listOf("qwertyuiop", "asdfghjkl", "${KeyEnter}zxcvbnm$KeyErase")

/** Где идёт игра — от этого зависят подписи и кнопка после конца игры. */
enum class WordGameMode {
    /** Усиленный будильник: победа или проигрыш выключают его. */
    ALARM,

    /** «Попробовать игру» из настроек будильника. */
    PRACTICE,

    /** Раздел «Игры»: после конца — следующее слово. Заголовок — у страницы. */
    FREE,
}

/**
 * «Пять букв»: поле 6×5 и своя клавиатура (системная на экране блокировки не нужна).
 * Один экран для будильника и раздела «Игры» — всё, что меняется в игре, меняется и там, и там.
 * [onChange] — каждое изменение (набор, попытка), [onDone] — кнопка после конца игры
 * («Готово» или, в разделе «Игры», «Следующее слово»).
 */
@Composable
fun WordGameScreen(
    state: WordGameState,
    onChange: (WordGameState) -> Unit,
    onDone: () -> Unit,
    mode: WordGameMode,
    modifier: Modifier = Modifier,
) {
    val dictionary = LocalContext.current.appContainer.wordDictionary
    val game = remember(dictionary) { WordGame(dictionary) }
    val haptics = LocalHapticFeedback.current
    val gameHaptics = LocalAppSettings.current.gameHaptics
    var error by remember { mutableStateOf<GuessError?>(null) }
    var shakes by remember { mutableIntStateOf(0) }
    LaunchedEffect(error, shakes) {
        if (error != null) {
            delay(1_800)
            error = null
        }
    }

    // Принятое слово открывается переворотом клеток слева направо, а угаданное — ещё и волной.
    // Пока клетки переворачиваются, ввода нет: следующая строка появится после
    var revealing by remember { mutableStateOf<Int?>(null) }
    var bouncing by remember { mutableStateOf<Int?>(null) }
    val flip = remember { Animatable(0f) }
    val bounce = remember { Animatable(0f) }
    val current by rememberUpdatedState(state)
    // Какую строку анимация уже ведёт. Пока flip не сброшен в 0, в нём остаток прошлого
    // переворота (1 = «открыта») — без этой проверки строка на кадр показывалась уже цветной
    var flipRow by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(revealing) {
        val row = revealing ?: return@LaunchedEffect
        flip.snapTo(0f)
        flipRow = row
        flip.animateTo(FlipUnits, tween((FlipUnits * FLIP_MS).toInt(), easing = LinearEasing))
        // Волна — отдельно (ниже): смена revealing отменяет этот эффект
        if (current.won && row == current.guesses.lastIndex) bouncing = row
        revealing = null
    }
    LaunchedEffect(bouncing) {
        if (bouncing == null) return@LaunchedEffect
        bounce.snapTo(0f)
        bounce.animateTo(BounceUnits, tween((BounceUnits * BOUNCE_MS).toInt(), easing = LinearEasing))
        bouncing = null
    }
    // Что уже открыто: строка, которая переворачивается, ещё не считается
    val revealed = revealing ?: state.guesses.size
    val settled = revealing == null
    val finished = state.finished && settled

    fun press(key: Char) {
        if (!settled) return
        if (gameHaptics) haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        when (key) {
            KeyEnter -> {
                val (next, problem) = game.submit(state)
                if (problem != null) {
                    error = problem
                    shakes++
                } else {
                    onChange(next)
                    flipRow = null
                    revealing = next.guesses.lastIndex
                }
            }
            KeyErase -> onChange(game.erase(state))
            else -> onChange(game.type(state, key))
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (mode != WordGameMode.FREE) {
            Text(
                text = stringResource(R.string.game_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        // Подсказка, а после конца игры — итог. Высота постоянная, чтобы поле не прыгало
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(TopHeight)) {
            if (finished) {
                Result(state, mode)
            } else {
                Text(
                    text = stringResource(if (mode == WordGameMode.PRACTICE) R.string.game_practice_hint else R.string.game_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Board(
            state = state,
            revealed = revealed,
            revealing = revealing,
            flip = if (flipRow == revealing) flip.value else 0f,
            bouncing = bouncing,
            bounce = bounce.value,
            shakes = shakes,
            modifier = Modifier.weight(1f),
        )

        // Сообщение о неверной попытке — между полем и клавиатурой
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(40.dp)) {
            if (!finished && error != null) {
                Text(
                    text = stringResource(if (error == GuessError.TOO_SHORT) R.string.game_too_short else R.string.game_not_a_word),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        // Внизу клавиатура, после конца игры — на её месте большая кнопка
        if (finished) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(KeyboardHeight)) {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                    Text(
                        text = stringResource(if (mode == WordGameMode.FREE) R.string.game_next else R.string.game_done),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        } else {
            // Цвета клавиш — только по уже открытым словам
            val marks = remember(state.answer, state.guesses, revealed) {
                state.copy(guesses = state.guesses.take(revealed)).keyMarks()
            }
            Keyboard(marks, onKey = ::press)
        }
    }
}

/**
 * Поле: открытые слова, переворачивающееся слово, строка ввода и пустые строки.
 * В строке ввода бледно видны буквы, место которых уже найдено.
 */
@Composable
private fun Board(
    state: WordGameState,
    revealed: Int,
    revealing: Int?,
    flip: Float,
    bouncing: Int?,
    bounce: Float,
    shakes: Int,
    modifier: Modifier,
) {
    // Неверное слово — строка вздрагивает
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakes) {
        if (shakes == 0) return@LaunchedEffect
        for (x in listOf(-14f, 12f, -8f, 6f, -3f, 0f)) shake.animateTo(x, tween(45))
    }
    // Найденные места: позиция → буква
    val found = remember(state.answer, state.guesses, revealed) {
        val hits = mutableMapOf<Int, Char>()
        state.guesses.take(revealed).forEach { guess ->
            WordGameState.mark(guess, state.answer).forEachIndexed { i, mark -> if (mark == LetterMark.HIT) hits[i] = guess[i] }
        }
        hits
    }
    // Клетка — сколько влезает и по ширине, и по высоте (не больше 64 dp)
    BoxWithConstraints(contentAlignment = Alignment.Center, modifier = modifier.fillMaxWidth()) {
        val gap = 6.dp
        val byWidth = (maxWidth - gap * (WordGameState.WORD_LENGTH - 1)) / WordGameState.WORD_LENGTH
        val byHeight = (maxHeight - gap * (WordGameState.MAX_ATTEMPTS - 1)) / WordGameState.MAX_ATTEMPTS
        val tile = minOf(byWidth, byHeight, 64.dp)
        Column(verticalArrangement = Arrangement.spacedBy(gap), horizontalAlignment = Alignment.CenterHorizontally) {
            repeat(WordGameState.MAX_ATTEMPTS) { row ->
                val guess = state.guesses.getOrNull(row)
                val input = row == state.guesses.size && !state.finished && revealing == null
                val letters = guess ?: if (input) state.input else ""
                val marks = guess?.let { WordGameState.mark(it, state.answer) }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    modifier = Modifier.graphicsLayer { if (input) translationX = shake.value * density },
                ) {
                    repeat(WordGameState.WORD_LENGTH) { i ->
                        Tile(
                            letter = letters.getOrNull(i),
                            mark = marks?.get(i),
                            hint = if (input) found[i] else null,
                            flip = if (row == revealing) stagger(flip, i, FLIP_STAGGER) else 1f,
                            lift = if (row == bouncing) stagger(bounce, i, BOUNCE_STAGGER) else 0f,
                            modifier = Modifier.size(tile),
                        )
                    }
                }
            }
        }
    }
}

/** Сколько анимации клетки [index] пройдено (0…1), если клетки идут одна за другой с шагом [step]. */
private fun stagger(progress: Float, index: Int, step: Float): Float = (progress - index * step).coerceIn(0f, 1f)

/**
 * Клетка. [flip] — переворот (0 — ещё не открыта, 1 — открыта): первую половину видна лицевая
 * сторона, вторую — уже цветная. [lift] — подпрыгивание (0…1). [hint] — бледная найденная буква.
 */
@Composable
private fun Tile(letter: Char?, mark: LetterMark?, hint: Char?, flip: Float, lift: Float, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val open = mark != null && flip >= 0.5f
    val background = when {
        !open -> colors.surface
        mark == LetterMark.HIT -> HitColor
        mark == LetterMark.PRESENT -> PresentColor
        else -> colors.surfaceContainerHighest
    }
    val shape = RoundedCornerShape(12.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                // До середины — лицевая сторона уходит, после — цветная появляется (не зеркально)
                rotationX = if (flip < 0.5f) flip * 180f else (flip - 1f) * 180f
                cameraDistance = 12f * density
                translationY = -sin(PI * lift).toFloat() * BounceHeight.toPx()
            }
            .clip(shape)
            .background(background)
            .then(
                if (!open) Modifier.border(2.dp, if (letter != null) colors.outline else colors.outlineVariant, shape) else Modifier,
            ),
    ) {
        val shown = letter ?: hint
        if (shown != null) {
            Text(
                text = shown.uppercase(),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    letter == null -> colors.onSurface.copy(alpha = 0.25f)
                    !open -> colors.onSurface
                    mark == LetterMark.MISS -> colors.onSurfaceVariant
                    else -> Color.White
                },
            )
        }
    }
}

@Composable
private fun Keyboard(marks: Map<Char, LetterMark>, onKey: (Char) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        KeyRows.forEachIndexed { index, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                // Средний ряд короче — по краям отступ в полклавиши
                if (index == 1) Spacer(Modifier.weight(0.5f))
                row.forEach { key -> Key(key, marks[key], onKey) }
                if (index == 1) Spacer(Modifier.weight(0.5f))
            }
        }
    }
}

@Composable
private fun RowScope.Key(key: Char, mark: LetterMark?, onKey: (Char) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val wide = key == KeyEnter || key == KeyErase
    val background = when (mark) {
        LetterMark.HIT -> HitColor
        LetterMark.PRESENT -> PresentColor
        LetterMark.MISS -> colors.surfaceContainerLowest
        null -> colors.surfaceContainerHighest
    }
    val content = when (mark) {
        LetterMark.HIT, LetterMark.PRESENT -> Color.White
        LetterMark.MISS -> colors.outline
        null -> colors.onSurface
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(if (wide) 1.5f else 1f)
            .height(54.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (key == KeyEnter) colors.primary else background)
            .clickable { onKey(key) },
    ) {
        when (key) {
            KeyEnter -> Icon(Icons.AutoMirrored.Outlined.KeyboardReturn, stringResource(R.string.game_enter), tint = colors.onPrimary)
            KeyErase -> Icon(Icons.AutoMirrored.Outlined.Backspace, stringResource(R.string.game_erase), tint = colors.onSurface)
            else -> Text(key.uppercase(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = content)
        }
    }
}

@Composable
private fun Result(state: WordGameState, mode: WordGameMode) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = when {
                !state.won -> stringResource(R.string.game_lost, state.answer.uppercase())
                mode == WordGameMode.ALARM -> stringResource(R.string.game_won)
                else -> stringResource(R.string.game_won_plain)
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (mode == WordGameMode.ALARM) {
            Text(
                text = stringResource(R.string.game_alarm_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
