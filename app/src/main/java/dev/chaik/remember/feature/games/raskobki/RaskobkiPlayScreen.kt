package dev.chaik.remember.feature.games.raskobki

import android.content.Intent
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.raskobki.RaskobkiGrade
import dev.chaik.remember.core.raskobki.RaskobkiLoad
import dev.chaik.remember.core.raskobki.RaskobkiNode
import dev.chaik.remember.core.raskobki.RaskobkiPart
import dev.chaik.remember.core.raskobki.RaskobkiPuzzle
import dev.chaik.remember.core.raskobki.RaskobkiRepository
import dev.chaik.remember.core.raskobki.RaskobkiState
import dev.chaik.remember.core.raskobki.RaskobkiText
import dev.chaik.remember.core.raskobki.attemptKey
import dev.chaik.remember.core.raskobki.isValidAnswer
import dev.chaik.remember.core.raskobki.normalizeAnswer
import dev.chaik.remember.core.ui.LocalTabVisible
import dev.chaik.remember.core.ui.components.ScreenScaffold
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class RaskobkiPlayRoute(val day: String) : NavKey

private const val MAX_ATTEMPTS = 32
private const val HEARTBEAT_MS = 10_000L

/** Выпуск конкретного дня: игра или, если он уже закрыт, результат. */
@Composable
fun RaskobkiPlayScreen(route: RaskobkiPlayRoute, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val day = remember(route) { LocalDate.parse(route.day) }
    val repository = context.appContainer.raskobki
    val metrics = context.appContainer.raskobkiMetrics
    val consent by metrics.consent.collectAsState()
    if (consent == null) {
        // Спрашиваем один раз, до начала игры
        RaskobkiConsentScreen(metrics, onBack, modifier)
        return
    }
    // Пока неизвестно, решён ли выпуск, игру не начинаем: таймер и события ушли бы на сервер
    val ready by repository.ready.collectAsState()
    LaunchedEffect(ready) {
        if (!ready) runCatching { repository.sync() }
    }
    if (!ready) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    var attempt by remember { mutableIntStateOf(0) }
    var load by remember { mutableStateOf<RaskobkiLoad?>(null) }
    LaunchedEffect(attempt) {
        load = null
        val result = repository.puzzle(day)
        load = result
        // Такой выпуск приложение показать не умеет — его открывает сайт
        if (result is RaskobkiLoad.OnlyOnSite) {
            openRaskobkiSite(context, "play/$day")
            onBack()
        }
    }

    when (val current = load) {
        is RaskobkiLoad.Ready -> RaskobkiGame(current.puzzle, repository, onBack, modifier)
        RaskobkiLoad.Failed -> ScreenScaffold(
            title = stringResource(R.string.game_raskobki),
            styledTitle = raskobkiTitle(stringResource(R.string.game_raskobki), raskobkiBracketColor()),
            modifier = modifier,
            onBack = onBack,
        ) { padding ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            ) {
                Text(stringResource(R.string.game_raskobki_error))
                Button(onClick = { attempt++ }) { Text(stringResource(R.string.game_raskobki_retry)) }
            }
        }
        else -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

@Composable
private fun RaskobkiGame(puzzle: RaskobkiPuzzle, repository: RaskobkiRepository, onBack: () -> Unit, modifier: Modifier) {
    val states by repository.progress.collectAsStateWithLifecycle()
    val current = states[puzzle.day] ?: RaskobkiState()
    var help by remember { mutableStateOf(false) }
    val metrics = LocalContext.current.appContainer.raskobkiMetrics
    LaunchedEffect(puzzle.day) {
        if (!current.c) metrics.opened(puzzle)
    }

    // Время идёт, только пока на экране открыта эта вкладка и не открыта справка
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.CREATED)
    val running = lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && LocalTabVisible.current && !current.c && !help
    var elapsed by remember(puzzle.day) { mutableDoubleStateOf(current.e.toDouble()) }
    var started by remember(puzzle.day) { mutableStateOf(false) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val onServerTime: (Double) -> Unit = { elapsed = it }
        repository.track(puzzle, if (started) "resume" else "start", elapsedMs = (elapsed * 1000).toLong(), onElapsed = onServerTime)
        started = true
        repository.syncTimer(puzzle, active = true, onElapsed = onServerTime)
        var last = SystemClock.elapsedRealtime()
        var saved = last
        var beat = last
        try {
            while (true) {
                delay(500)
                val now = SystemClock.elapsedRealtime()
                elapsed += (now - last) / 1000.0
                last = now
                if (now - saved >= 5_000) {
                    saved = now
                    val latest = repository.progress.value[puzzle.day] ?: RaskobkiState()
                    repository.save(puzzle.day, latest.copy(e = elapsed.toLong()))
                }
                if (now - beat >= HEARTBEAT_MS) {
                    beat = now
                    repository.syncTimer(puzzle, active = true, onElapsed = onServerTime)
                }
            }
        } finally {
            val latest = repository.progress.value[puzzle.day] ?: RaskobkiState()
            repository.syncTimer(puzzle, active = false)
            if (!latest.c) {
                repository.save(puzzle.day, latest.copy(e = elapsed.toLong()))
                repository.track(puzzle, "pause", elapsedMs = (elapsed * 1000).toLong())
                repository.push(puzzle)
            }
        }
    }

    ScreenScaffold(
        title = stringResource(if (current.c) R.string.raskobki_result_title else R.string.raskobki_play_title),
        modifier = modifier,
        onBack = onBack,
        actions = {
            if (!current.c) {
                Text(
                    raskobkiTime(elapsed.toLong()),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            IconButton(onClick = { help = true }) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.raskobki_help))
            }
        },
    ) { padding ->
        if (current.c) {
            RaskobkiResult(puzzle, current, onBack, Modifier.padding(padding))
        } else {
            RaskobkiField(puzzle, current, elapsed.toLong(), repository, padding)
        }
    }
    if (help) RaskobkiTutorial(onDismiss = { help = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RaskobkiField(puzzle: RaskobkiPuzzle, state: RaskobkiState, elapsed: Long, repository: RaskobkiRepository, padding: PaddingValues) {
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var message by remember { mutableStateOf<Int?>(null) }
    var hintNode by remember { mutableStateOf<RaskobkiNode?>(null) }
    var revealing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val metrics = LocalContext.current.appContainer.raskobkiMetrics

    fun solve(node: RaskobkiNode, answer: String, revealed: Boolean) {
        val solved = state.s + node.id
        val done = solved.size == puzzle.total
        val updated = state.copy(
            s = solved,
            rv = if (revealed) state.rv + node.id else state.rv,
            x = state.x + (node.id to answer),
            e = elapsed,
            c = done,
            completedAt = if (done) System.currentTimeMillis() else null,
        )
        repository.save(puzzle.day, updated)
        metrics.progress(puzzle, node.id)
        if (done) {
            repository.complete(puzzle, elapsed * 1000)
            metrics.completed(puzzle, updated)
        }
    }

    // Как на сайте: неподошедшее слово выделено, его стирает одно нажатие
    fun selectInput() {
        input = input.copy(selection = TextRange(0, input.text.length))
    }

    fun submit() {
        val raw = input.text.trim()
        if (raw.isEmpty()) return
        if (!isValidAnswer(raw)) {
            message = R.string.raskobki_cyrillic
            selectInput()
            return
        }
        val node = puzzle.match(raw, state.s)
        if (node != null) {
            val before = state.s
            solve(node, raw, revealed = false)
            repository.answerAccepted(puzzle, node.id, raw, before, elapsed * 1000)
            input = TextFieldValue()
            message = null
            return
        }
        repository.track(puzzle, "answer", input = raw, elapsedMs = elapsed * 1000)
        val key = attemptKey(normalizeAnswer(raw))
        if (key in state.a) {
            message = R.string.raskobki_already_tried
            selectInput()
            return
        }
        repository.save(puzzle.day, state.copy(a = (state.a + key).takeLast(MAX_ATTEMPTS), w = state.w + 1, e = elapsed))
        message = R.string.raskobki_wrong
        selectInput()
        scope.launch {
            delay(2_500)
            message = null
        }
    }

    // С клавиатурой островка нет: поле поджимается только над клавиатурой
    val keyboard = WindowInsets.isImeVisible
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding())
            .then(if (keyboard) Modifier.imePadding() else Modifier.padding(bottom = padding.calculateBottomPadding())),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            RaskobkiKicker(stringResource(R.string.raskobki_fragments, state.s.size, puzzle.total))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceContainerLow, RoundedCornerShape(20.dp))
                    .padding(16.dp),
            ) {
                Text(
                    puzzleText(
                        parts = puzzle.root,
                        state = state,
                        accent = raskobkiBracketColor(),
                        locked = colors.onSurface.copy(alpha = 0.83f),
                        solved = raskobkiAnswerColor(),
                    ) { hintNode = it },
                    fontSize = 18.sp,
                    lineHeight = 29.sp,
                )
            }
            Text(
                stringResource(R.string.raskobki_instruction),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        message?.let {
            Text(
                stringResource(it),
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { value ->
                    input = if (value.text.any(Char::isDigit)) {
                        val text = value.text.filterNot(Char::isDigit)
                        TextFieldValue(text, TextRange(text.length))
                    } else {
                        value
                    }
                },
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

    hintNode?.let { node ->
        val peeked = node.id in state.h
        AlertDialog(
            onDismissRequest = { hintNode = null },
            title = { Text(stringResource(if (peeked) R.string.raskobki_reveal_title else R.string.raskobki_hint_title)) },
            text = {
                Text(
                    if (peeked) stringResource(R.string.raskobki_reveal_text, node.firstLetter)
                    else stringResource(R.string.raskobki_hint_peek),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !revealing,
                    onClick = {
                        if (!peeked) {
                            repository.track(puzzle, "hint", nodeId = node.id, hintType = "peek", elapsedMs = elapsed * 1000)
                            repository.save(puzzle.day, state.copy(h = state.h + node.id, e = elapsed))
                            hintNode = null
                        } else {
                            revealing = true
                            scope.launch {
                                val answer = repository.reveal(puzzle, node.id, state.s, elapsed * 1000)
                                revealing = false
                                hintNode = null
                                // Без ответа показать нечего: штрафа нет
                                if (answer == null) message = R.string.raskobki_reveal_failed
                                else solve(node, answer, revealed = true)
                            }
                        }
                    },
                ) { Text(stringResource(if (peeked) R.string.raskobki_reveal_confirm else R.string.raskobki_hint_confirm)) }
            },
            dismissButton = { TextButton(onClick = { hintNode = null }) { Text(stringResource(R.string.editor_close)) } },
        )
    }
}

/** Текст со скобками: доступные выделены и нажимаются, решённые заменены ответом. */
internal fun puzzleText(
    parts: List<RaskobkiPart>,
    state: RaskobkiState,
    accent: Color,
    locked: Color,
    solved: Color,
    onReady: (RaskobkiNode) -> Unit,
): AnnotatedString {
    val bracket = SpanStyle(color = accent, fontWeight = FontWeight.Bold)
    val lockedStyle = SpanStyle(color = locked)
    val solvedStyle = SpanStyle(color = solved, fontWeight = FontWeight.Bold)
    // Первая буква в цвете скобок, на мягкой подложке
    val peek = SpanStyle(color = accent, background = accent.copy(alpha = 0.16f), fontWeight = FontWeight.ExtraBold)

    fun AnnotatedString.Builder.append(items: List<RaskobkiPart>) {
        items.forEach { part ->
            when (part) {
                is RaskobkiText -> append(part.text)
                is RaskobkiNode -> when {
                    part.id in state.s -> withStyle(solvedStyle) { append(state.x[part.id] ?: "…") }
                    part.children.all { it.id in state.s } -> withLink(
                        LinkAnnotation.Clickable(
                            tag = part.id,
                            styles = TextLinkStyles(bracket.copy(textDecoration = TextDecoration.None)),
                        ) { onReady(part) },
                    ) {
                        append("[")
                        if (part.id in state.h) withStyle(peek) { append(" ${part.firstLetter} ") }
                        append(part.parts)
                        append("]")
                    }
                    else -> withStyle(lockedStyle) {
                        withStyle(bracket) { append("[") }
                        if (part.id in state.h) withStyle(peek) { append(" ${part.firstLetter} ") }
                        append(part.parts)
                        withStyle(bracket) { append("]") }
                    }
                }
            }
        }
    }
    return buildAnnotatedString { append(parts) }
}

/** Пройденный выпуск, как на сайте: находка, сохранность, счёт и все фрагменты. */
@Composable
private fun RaskobkiResult(puzzle: RaskobkiPuzzle, state: RaskobkiState, onBack: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val grade = RaskobkiGrade.of(state.score)
    val shape = RoundedCornerShape(20.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().background(colors.surfaceContainerLow, shape).padding(16.dp),
        ) {
            RaskobkiKicker(stringResource(R.string.raskobki_fact))
            Text(puzzle.fact, fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)
            puzzle.sourceUrl?.let { url ->
                TextButton(onClick = {
                    context.appContainer.appLock.allowExternal()
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                }) { Text(stringResource(R.string.raskobki_source) + " ↗") }
            }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.raskobki_to_day)) }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().background(colors.surfaceContainerLow, shape).padding(16.dp),
        ) {
            Text(grade.roman, fontWeight = FontWeight.Bold, fontSize = 40.sp, color = colors.primary)
            Text(grade.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(grade.note, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            ResultRow(stringResource(R.string.raskobki_score), state.score.toString())
            ResultRow(stringResource(R.string.raskobki_time), raskobkiTime(state.e))
            ResultRow(stringResource(R.string.raskobki_mistakes), state.w.toString())
            ResultRow(stringResource(R.string.raskobki_peeks), state.h.size.toString())
            ResultRow(stringResource(R.string.raskobki_reveals), state.rv.size.toString())
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().background(colors.surfaceContainerLow, shape).padding(16.dp),
        ) {
            RaskobkiKicker(stringResource(R.string.raskobki_fragments_title))
            puzzle.nodes().forEachIndexed { index, node ->
                val marks = (if (node.id in state.h) " А" else "") + (if (node.id in state.rv) " ★" else "")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("%02d".format(index + 1), color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Column(Modifier.weight(1f)) {
                        Text("[" + assembledClue(node, state) + "]", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        Text((state.x[node.id] ?: "…") + marks, color = raskobkiAnswerColor(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun assembledClue(node: RaskobkiNode, state: RaskobkiState): String =
    node.parts.joinToString("") { part ->
        when (part) {
            is RaskobkiText -> part.text
            is RaskobkiNode -> state.x[part.id] ?: "…"
        }
    }.replace(Regex("\\s+"), " ").trim()

@Composable
private fun ResultRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold)
    }
}

private fun raskobkiTime(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)
