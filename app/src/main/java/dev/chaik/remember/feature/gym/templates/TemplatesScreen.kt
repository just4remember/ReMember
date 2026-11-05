package dev.chaik.remember.feature.gym.templates

import dev.chaik.remember.core.ui.components.SlidingText
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.core.ui.primaryCornerModifier
import dev.chaik.remember.feature.gym.templateTitle
import dev.chaik.remember.feature.gym.workout.AddExerciseDialog
import dev.chaik.remember.feature.notes.ConfirmDialog
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
data object TemplatesRoute : NavKey

/** @param templateId 0 — новый шаблон. */
@Serializable
data class TemplateEditorRoute(val templateId: Long = 0) : NavKey

/** Шаблоны тренировок: список, тап — редактирование. */
@Composable
fun TemplatesScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val templates by remember { container.workoutRepository.observeTemplates() }.collectAsStateWithLifecycle(emptyList())
    val colors = MaterialTheme.colorScheme

    ScreenScaffold(title = stringResource(R.string.gym_templates), modifier = modifier, onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize()) {
            if (templates.isEmpty()) {
                Text(
                    text = stringResource(R.string.gym_templates_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = padding.calculateTopPadding() + 32.dp, start = 32.dp, end = 32.dp),
                )
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(templates, key = { it.id }) { template ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.surfaceContainerLow)
                            .clickable { onOpen(template.id) }
                            .padding(16.dp),
                    ) {
                        Text(templateTitle(template), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = template.exercises.joinToString(", ") { it.name }.ifEmpty { stringResource(R.string.gym_no_exercises) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            FloatingActionButton(
                onClick = { onOpen(0) },
                modifier = primaryCornerModifier(bottom = padding.calculateBottomPadding() + 8.dp),
            ) {
                Icon(Icons.Outlined.Add, stringResource(R.string.gym_template_new))
            }
        }
    }
}

/** Шаблон: название и упражнения по порядку. Сохраняется сам, отдельной кнопки нет. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplateEditorScreen(route: TemplateEditorRoute, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val viewModel = viewModel { TemplateEditorViewModel(container, route.templateId) }
    val dictionary by viewModel.dictionary.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }

    // «Назад» прячет клавиатуру — вместе с ней уходит и курсор; тап мимо поля — тоже
    val focusManager = LocalFocusManager.current
    val keyboardVisible = WindowInsets.isImeVisible
    LaunchedEffect(keyboardVisible) { if (!keyboardVisible) focusManager.clearFocus() }

    ScreenScaffold(
        title = stringResource(R.string.gym_template),
        modifier = modifier,
        onBack = onBack,
        actions = {
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.notes_delete), tint = colors.error)
            }
        },
    ) { padding ->
        if (!viewModel.isLoaded) return@ScreenScaffold
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp)
                .padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text(stringResource(R.string.gym_template_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            viewModel.exercises.forEachIndexed { index, exercise ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceContainerLow)
                        .padding(start = 16.dp),
                ) {
                    SlidingText(exercise.name, Modifier.weight(1f))
                    IconButton(enabled = index > 0, onClick = { viewModel.move(index, -1) }) {
                        Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.gym_template_up))
                    }
                    IconButton(enabled = index < viewModel.exercises.lastIndex, onClick = { viewModel.move(index, 1) }) {
                        Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.gym_template_down))
                    }
                    IconButton(onClick = { viewModel.remove(index) }) {
                        Icon(Icons.Outlined.Close, stringResource(R.string.gym_remove_exercise), tint = colors.outline)
                    }
                }
            }
            FilledTonalButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Outlined.Add, null, Modifier.size(20.dp))
                Text(stringResource(R.string.gym_add_exercise), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    if (adding) {
        AddExerciseDialog(
            dictionary = dictionary,
            onAdd = { name, kind ->
                viewModel.add(name, kind)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.gym_template_delete),
            text = stringResource(R.string.gym_template_delete_text),
            confirmLabel = stringResource(R.string.notes_delete),
            onConfirm = { viewModel.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}
