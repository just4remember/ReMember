package dev.chaik.remember.feature.gym.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.chaik.remember.R
import dev.chaik.remember.core.model.Exercise
import dev.chaik.remember.core.model.ExerciseKind
import dev.chaik.remember.core.model.WorkoutSet
import dev.chaik.remember.core.model.formatKg
import dev.chaik.remember.core.model.parseKg
import dev.chaik.remember.feature.gym.label

/** Сколько подсказок показывать под полем названия. */
private const val MAX_SUGGESTIONS = 6

/**
 * Подход: поля под вид упражнения. Новый подход приходит пустым, а в полях серым подсказка [hint]:
 * незаполненное поле при сохранении берёт значение из неё, поэтому повторить результат — просто «Сохранить».
 * [note] — пояснение под полями. Записанный подход приходит в [initial], текст первого поля выделен,
 * [onDelete] — только для него.
 */
@Composable
fun SetDialog(
    kind: ExerciseKind,
    initial: WorkoutSet?,
    onSave: (WorkoutSet) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    hint: WorkoutSet? = null,
    note: (@Composable () -> Unit)? = null,
) {
    fun selected(text: String) = TextFieldValue(text, TextRange(0, text.length))
    var weight by remember { mutableStateOf(selected(initial?.weight?.let(::formatKg).orEmpty())) }
    var reps by remember { mutableStateOf(selected(initial?.reps?.toString().orEmpty())) }
    // Время — двумя полями: на цифровой клавиатуре Samsung нет двоеточия
    var minutes by remember { mutableStateOf(selected(initial?.seconds?.let { (it / 60).toString() }.orEmpty())) }
    var seconds by remember { mutableStateOf(selected(initial?.seconds?.let { (it % 60).toString() }.orEmpty())) }
    var speed by remember { mutableStateOf(selected(initial?.speed?.let(::formatKg).orEmpty())) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val hintWeight = hint?.weight?.let(::formatKg).orEmpty()
    val hintReps = hint?.reps?.toString().orEmpty()
    val hintMinutes = hint?.seconds?.let { (it / 60).toString() }.orEmpty()
    val hintSeconds = hint?.seconds?.let { (it % 60).toString() }.orEmpty()
    val hintSpeed = hint?.speed?.let(::formatKg).orEmpty()
    val weightText = weight.text.ifEmpty { hintWeight }
    val repsText = reps.text.ifEmpty { hintReps }
    val minutesText = minutes.text.ifEmpty { hintMinutes }
    val secondsText = seconds.text.ifEmpty { hintSeconds }
    val speedText = speed.text.ifEmpty { hintSpeed }

    val result = when (kind) {
        ExerciseKind.WEIGHT_REPS -> WorkoutSet(weight = parseKg(weightText), reps = repsText.toIntOrNull())
            .takeIf { it.weight != null || it.reps != null }
        ExerciseKind.REPS -> repsText.toIntOrNull()?.let { WorkoutSet(reps = it) }
        ExerciseKind.TIME -> totalSeconds(minutesText, secondsText)?.let { WorkoutSet(seconds = it) }
        ExerciseKind.SPEED_TIME -> WorkoutSet(speed = parseKg(speedText), seconds = totalSeconds(minutesText, secondsText))
            .takeIf { it.speed != null || it.seconds != null }
    }
    val save = { result?.let { onSave(it.copy(id = initial?.id ?: 0)) } }
    val done = KeyboardActions(onDone = { save() })

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gym_edit_set)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (kind) {
                    ExerciseKind.WEIGHT_REPS -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SetField(
                            caption = stringResource(R.string.gym_set_weight),
                            value = weight,
                            onChange = { v -> if (v.text.length <= 6 && v.text.all { it.isDigit() || it == ',' || it == '.' }) weight = v },
                            hint = hintWeight,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            modifier = Modifier.weight(1f),
                            fieldModifier = Modifier.focusRequester(focus),
                        )
                        SetField(
                            caption = stringResource(R.string.gym_set_reps),
                            value = reps,
                            onChange = { v -> if (v.text.length <= 4 && v.text.all(Char::isDigit)) reps = v },
                            hint = hintReps,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = done,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ExerciseKind.REPS -> SetField(
                        caption = stringResource(R.string.gym_set_reps),
                        value = reps,
                        onChange = { v -> if (v.text.length <= 4 && v.text.all(Char::isDigit)) reps = v },
                        hint = hintReps,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = done,
                        modifier = Modifier.fillMaxWidth(),
                        fieldModifier = Modifier.focusRequester(focus),
                    )
                    ExerciseKind.TIME -> TimeFields(minutes, seconds, hintMinutes, hintSeconds, { minutes = it }, { seconds = it }, done, Modifier.focusRequester(focus))
                    ExerciseKind.SPEED_TIME -> {
                        SetField(
                            caption = stringResource(R.string.gym_set_speed),
                            value = speed,
                            onChange = { v -> if (v.text.length <= 5 && v.text.all { it.isDigit() || it == ',' || it == '.' }) speed = v },
                            hint = hintSpeed,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                            fieldModifier = Modifier.focusRequester(focus),
                        )
                        TimeFields(minutes, seconds, hintMinutes, hintSeconds, { minutes = it }, { seconds = it }, done)
                    }
                }
                note?.invoke()
            }
        },
        confirmButton = {
            TextButton(enabled = result != null, onClick = { save() }) { Text(stringResource(R.string.gym_save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.gym_delete_set), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/**
 * Поле подхода. Подпись над полем, а не внутри: подсказка должна быть видна сразу во всех полях,
 * а встроенная подпись показывает её только в поле с курсором.
 */
@Composable
private fun SetField(
    caption: String,
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    hint: String,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = if (hint.isEmpty()) null else ({ Text(hint) }),
            singleLine = true,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = fieldModifier.fillMaxWidth(),
        )
    }
}

/** Минуты и секунды двумя полями: на цифровой клавиатуре Samsung нет двоеточия. */
@Composable
private fun TimeFields(
    minutes: TextFieldValue,
    seconds: TextFieldValue,
    hintMinutes: String,
    hintSeconds: String,
    onMinutes: (TextFieldValue) -> Unit,
    onSeconds: (TextFieldValue) -> Unit,
    done: KeyboardActions,
    firstFieldModifier: Modifier = Modifier,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SetField(
            caption = stringResource(R.string.gym_set_minutes),
            value = minutes,
            onChange = { v -> if (v.text.length <= 3 && v.text.all(Char::isDigit)) onMinutes(v) },
            hint = hintMinutes,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f),
            fieldModifier = firstFieldModifier,
        )
        SetField(
            caption = stringResource(R.string.gym_set_seconds),
            value = seconds,
            onChange = { v -> if (v.text.length <= 2 && v.text.all(Char::isDigit)) onSeconds(v) },
            hint = hintSeconds,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = done,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Минуты + секунды в секундах; null — ничего не введено или ноль. */
private fun totalSeconds(minutes: String, seconds: String): Int? {
    if (minutes.isEmpty() && seconds.isEmpty()) return null
    return ((minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)).takeIf { it > 0 }
}

/**
 * Добавить упражнение: название с подсказками из справочника (недавние сверху).
 * Незнакомое название запомнится — для него спрашиваем, как записывать подходы.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddExerciseDialog(
    dictionary: List<Exercise>,
    onAdd: (name: String, kind: ExerciseKind) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ExerciseKind.WEIGHT_REPS) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val query = name.trim().lowercase()
    val known = dictionary.firstOrNull { it.name.lowercase() == query }
    val suggestions = dictionary
        .filter { query.isEmpty() || it.name.lowercase().contains(query) }
        .filter { it != known }
        .take(MAX_SUGGESTIONS)
    val add = { if (query.isNotEmpty()) onAdd(name.trim(), known?.kind ?: kind) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gym_new_exercise)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.gym_exercise_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                )
                suggestions.forEach { exercise ->
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAdd(exercise.name, exercise.kind) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    )
                }
                if (query.isNotEmpty() && known == null) {
                    Text(
                        text = stringResource(R.string.gym_exercise_kind),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    // Чипы, а не сегменты: четыре вида в одну строку не помещаются. Выбранный — цветом, без галочки
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExerciseKind.entries.forEach { option ->
                            FilterChip(
                                selected = kind == option,
                                onClick = { kind = option },
                                label = { Text(option.label()) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = query.isNotEmpty(), onClick = { add() }) { Text(stringResource(R.string.gym_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Сменить, как записываются подходы упражнения (во всех тренировках). */
@Composable
fun ExerciseKindDialog(current: ExerciseKind, onPick: (ExerciseKind) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gym_change_kind)) },
        text = {
            Column {
                ExerciseKind.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(option) },
                    ) {
                        RadioButton(selected = option == current, onClick = { onPick(option) })
                        Text(option.label(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
