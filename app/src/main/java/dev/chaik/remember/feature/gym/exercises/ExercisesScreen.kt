package dev.chaik.remember.feature.gym.exercises

import dev.chaik.remember.core.ui.primaryCornerModifier
import androidx.compose.material3.FloatingActionButton
import dev.chaik.remember.core.model.ExerciseKind
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.Icons
import kotlinx.coroutines.flow.map
import java.util.Locale
import java.text.Collator
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.model.Exercise
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.feature.gym.label
import dev.chaik.remember.feature.gym.workout.ExerciseKindDialog
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object ExercisesRoute : NavKey

/**
 * Все упражнения из справочника. Тап — название, заметка и вид подходов. Новое название
 * сразу видно во всех тренировках и шаблонах: там хранится ссылка на упражнение, а не его имя.
 */
@Composable
fun ExercisesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val repository = LocalContext.current.appContainer.workoutRepository
    val exercises by remember { repository.observeExercises().map { it.sortedWith(AlphabetOrder) } }.collectAsStateWithLifecycle(null)
    var editing by remember { mutableStateOf<Exercise?>(null) }
    var adding by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = stringResource(R.string.gym_exercises),
        modifier = modifier,
        onBack = onBack,
    ) { padding ->
        val list = exercises
        Box(Modifier.fillMaxSize()) {
            when {
                list == null -> Unit
                list.isEmpty() -> Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding() + 32.dp, start = 32.dp, end = 32.dp)) {
                    Text(
                        stringResource(R.string.gym_exercises_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                        // Место под кнопку «+», чтобы подпись внизу не пряталась под ней
                        bottom = padding.calculateBottomPadding() + 96.dp,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { exercise ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().clickable { editing = exercise },
                        ) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    exercise.kind.label(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (exercise.note.isNotBlank()) {
                                    Text(
                                        exercise.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    item(key = "footer") {
                        Text(
                            stringResource(R.string.gym_exercises_footer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            FloatingActionButton(
                onClick = { adding = true },
                modifier = primaryCornerModifier(bottom = padding.calculateBottomPadding() + 8.dp),
            ) {
                Icon(Icons.Outlined.Add, stringResource(R.string.gym_exercise_add))
            }
        }
    }

    editing?.let { exercise -> ExerciseEditor(exercise, onDismiss = { editing = null }) }
    if (adding) NewExerciseDialog(onDismiss = { adding = false })
}

/** Сначала латиница, потом кириллица, остальное в конце; внутри — по алфавиту языка (ё рядом с е). */
private val AlphabetOrder: Comparator<Exercise> = run {
    val russian = Collator.getInstance(Locale.forLanguageTag("ru"))
    fun script(name: String): Int {
        val first = name.trimStart().firstOrNull { it.isLetter() } ?: return 2
        return when (Character.UnicodeScript.of(first.code)) {
            Character.UnicodeScript.LATIN -> 0
            Character.UnicodeScript.CYRILLIC -> 1
            else -> 2
        }
    }
    compareBy<Exercise> { script(it.name) }.then { a, b -> russian.compare(a.name, b.name) }
}

/** Название, заметка, вид подходов; удалить — только неиспользуемое. */
@Composable
private fun ExerciseEditor(exercise: Exercise, onDismiss: () -> Unit) {
    val container = LocalContext.current.appContainer
    val repository = container.workoutRepository
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(exercise.name) }
    var note by remember { mutableStateOf(exercise.note) }
    var taken by remember { mutableStateOf(false) }
    var pickingKind by remember { mutableStateOf(false) }
    var uses by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(exercise.id) { uses = repository.exerciseUses(exercise.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gym_exercise_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.replace('\n', ' ')
                        taken = false
                    },
                    label = { Text(stringResource(R.string.gym_exercise_name)) },
                    isError = taken,
                    supportingText = if (taken) ({ Text(stringResource(R.string.gym_exercise_taken)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.gym_exercise_note)) },
                    placeholder = { Text(stringResource(R.string.gym_exercise_note_hint)) },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { pickingKind = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.gym_exercise_kind_value, exercise.kind.label()))
                }
                uses?.let { count ->
                    Text(
                        if (count == 0) stringResource(R.string.gym_exercise_unused) else pluralStringResource(R.plurals.gym_exercise_uses, count, count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    scope.launch {
                        if (repository.updateExercise(exercise.copy(name = name, note = note))) onDismiss() else taken = true
                    }
                },
            ) { Text(stringResource(R.string.gym_save)) }
        },
        dismissButton = {
            Row {
                // Удалить — только то, чего нет ни в одной тренировке и шаблоне: иначе пропала бы история
                if (uses == 0) {
                    TextButton(onClick = {
                        scope.launch { if (repository.deleteExercise(exercise.id)) onDismiss() }
                    }) { Text(stringResource(R.string.notes_delete), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )

    if (pickingKind) {
        ExerciseKindDialog(
            current = exercise.kind,
            onPick = { kind ->
                pickingKind = false
                container.appScope.launch { repository.setExerciseKind(exercise.id, kind) }
                onDismiss()
            },
            onDismiss = { pickingKind = false },
        )
    }
}

/** Новое упражнение прямо в справочник: название, вид подходов и заметка. */
@Composable
private fun NewExerciseDialog(onDismiss: () -> Unit) {
    val repository = LocalContext.current.appContainer.workoutRepository
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ExerciseKind.WEIGHT_REPS) }
    var taken by remember { mutableStateOf(false) }
    var pickingKind by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gym_exercise_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.replace('\n', ' ')
                        taken = false
                    },
                    label = { Text(stringResource(R.string.gym_exercise_name)) },
                    isError = taken,
                    supportingText = if (taken) ({ Text(stringResource(R.string.gym_exercise_taken)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { pickingKind = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.gym_exercise_kind_value, kind.label()))
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.gym_exercise_note)) },
                    placeholder = { Text(stringResource(R.string.gym_exercise_note_hint)) },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { scope.launch { if (repository.createExercise(name, kind, note)) onDismiss() else taken = true } },
            ) { Text(stringResource(R.string.gym_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (pickingKind) {
        ExerciseKindDialog(
            current = kind,
            onPick = {
                kind = it
                pickingKind = false
            },
            onDismiss = { pickingKind = false },
        )
    }
}
