package dev.chaik.remember.feature.games.raskobki

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import dev.chaik.remember.R
import dev.chaik.remember.core.raskobki.RaskobkiNode
import dev.chaik.remember.core.raskobki.RaskobkiPuzzle
import dev.chaik.remember.core.raskobki.RaskobkiStart
import dev.chaik.remember.core.raskobki.RaskobkiState
import dev.chaik.remember.core.raskobki.RaskobkiText
import dev.chaik.remember.core.raskobki.normalizeAnswer
import dev.chaik.remember.core.raskobki.sha256

private fun demoNode(id: String, answer: String, vararg parts: dev.chaik.remember.core.raskobki.RaskobkiPart) =
    RaskobkiNode(id, parts.toList(), listOf(sha256(normalizeAnswer(answer))), answer.take(1))

private const val HISTORIAN = "historian"
private const val TABLE = "table"
private const val PARIS = "paris"

/** Написание ответов примера: в настоящей игре его присылает сервер. */
private val DemoAnswers = mapOf(HISTORIAN to "Историк", TABLE to "стол", PARIS to "Париж")

/** Пример с сайта: [Учёный, изучающий прошлое] работает в [[за ним едят]ица Франции]е. */
private val DemoPuzzle = RaskobkiPuzzle(
    day = RaskobkiStart,
    puzzleId = "demo",
    artifactId = "demo",
    contentRevision = 1,
    scoringVersion = 1,
    fact = "",
    sourceUrl = null,
    era = "",
    region = "",
    cardText = "",
    root = listOf(
        demoNode(HISTORIAN, "Историк", RaskobkiText("Учёный, изучающий прошлое")),
        RaskobkiText(" работает в "),
        demoNode(PARIS, "Париж", demoNode(TABLE, "стол", RaskobkiText("за ним едят")), RaskobkiText("ица Франции")),
        RaskobkiText("е."),
    ),
)

/** Как выбранные цвета выглядят в игре: решённое слово, доступная и закрытая скобки. */
@Composable
internal fun RaskobkiColorPreview() {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(
            puzzleText(
                parts = DemoPuzzle.root,
                state = RaskobkiState(s = listOf(HISTORIAN), x = DemoAnswers),
                accent = raskobkiBracketColor(),
                locked = colors.onSurface.copy(alpha = 0.83f),
                solved = raskobkiAnswerColor(),
            ) {},
            fontSize = 18.sp,
            lineHeight = 28.sp,
        )
    }
}

/** «Как играть»: короткое объяснение и маленькая игра без штрафов. Открытое окно останавливает таймер. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RaskobkiTutorial(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var state by remember { mutableStateOf(RaskobkiState()) }
    var input by remember { mutableStateOf(TextFieldValue()) }
    @StringRes var note by remember { mutableIntStateOf(R.string.raskobki_demo_start) }
    var wrong by remember { mutableStateOf(false) }
    val done = state.s.size == DemoPuzzle.total

    fun solve(node: RaskobkiNode) {
        state = state.copy(s = state.s + node.id, x = state.x + (node.id to DemoAnswers.getValue(node.id)))
        input = TextFieldValue()
        wrong = false
        note = when (node.id) {
            HISTORIAN -> R.string.raskobki_demo_historian
            TABLE -> R.string.raskobki_demo_table
            else -> R.string.raskobki_demo_paris
        }
    }

    fun submit() {
        if (input.text.isBlank() || done) return
        val node = DemoPuzzle.match(input.text, state.s)
        if (node != null) {
            solve(node)
        } else {
            wrong = true
            input = input.copy(selection = TextRange(0, input.text.length))
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding(),
    ) {
        Surface(color = colors.surfaceContainerLow, shape = RoundedCornerShape(28.dp)) {
            Box {
                Column {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 12.dp),
                ) {
                    RaskobkiKicker(stringResource(R.string.raskobki_help))
                    Text(
                        stringResource(R.string.raskobki_demo_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 32.dp),
                    )
                    Text(stringResource(R.string.raskobki_help_text), style = MaterialTheme.typography.bodyMedium)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceContainerHighest, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                    ) {
                        Text(
                            puzzleText(
                                parts = DemoPuzzle.root,
                                state = state,
                                accent = raskobkiBracketColor(),
                                locked = colors.onSurface.copy(alpha = 0.83f),
                                solved = raskobkiAnswerColor(),
                            ) { node ->
                                // В примере подсказки бесплатны: сначала буква, потом слово целиком
                                if (node.id in state.h) {
                                    solve(node)
                                } else {
                                    state = state.copy(h = state.h + node.id)
                                    note = R.string.raskobki_demo_peek
                                    wrong = false
                                }
                            },
                            fontSize = 18.sp,
                            lineHeight = 28.sp,
                        )
                    }
                    Text(
                        stringResource(
                            when {
                                wrong -> R.string.raskobki_demo_wrong
                                done -> R.string.raskobki_demo_done
                                else -> note
                            },
                        ),
                        color = if (wrong) colors.error else colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                // Поле ввода вне прокрутки: отступ снизу виден и с клавиатурой
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                    if (done) {
                        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.raskobki_demo_play))
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                placeholder = { Text(stringResource(R.string.raskobki_answer_hint)) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    imeAction = ImeAction.Send,
                                ),
                                keyboardActions = KeyboardActions(onSend = { submit() }),
                                modifier = Modifier.weight(1f),
                            )
                            FilledIconButton(onClick = ::submit) {
                                Icon(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.raskobki_insert))
                            }
                        }
                    }
                }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.editor_close))
                }
            }
        }
    }
}
