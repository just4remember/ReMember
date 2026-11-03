package dev.chaik.remember.feature.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import dev.chaik.remember.R
import dev.chaik.remember.core.settings.LocalAppSettings
import dev.chaik.remember.appContainer
import dev.chaik.remember.core.ui.components.ScreenScaffold
import dev.chaik.remember.core.ui.primaryCornerModifier
import dev.chaik.remember.feature.notes.ConfirmDialog
import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data object TasksRoute : NavKey

/**
 * Список дел одной лентой по срокам: просрочено, сегодня, завтра, позже, без срока
 * и свёрнутое «Выполнено». «+» открывает быструю строку над клавиатурой.
 */
@Composable
fun TasksScreen(
    onBack: (() -> Unit)?,
    onOpen: (taskId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalContext.current.appContainer
    val viewModel = viewModel { TasksViewModel(container.taskRepository) }
    val tasks by viewModel.all.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val groups = remember(tasks, today) { tasks?.let { groupTasks(it, today) } }

    var adding by remember { mutableStateOf(false) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    // Закрыли строку новой задачи: набранное стирается, только если так выбрано в «Страницах»
    val clearDraft = LocalAppSettings.current.tasksClearDraft
    fun closeAdding() {
        if (clearDraft) viewModel.draft = TaskDraft()
        adding = false
    }
    BackHandler(enabled = adding, onBack = ::closeAdding)

    ScreenScaffold(title = stringResource(R.string.tab_tasks), modifier = modifier, onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize()) {
            when {
                groups == null -> Unit
                groups.isEmpty() -> Text(
                    text = stringResource(R.string.tasks_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = padding.calculateTopPadding() + 32.dp, start = 32.dp, end = 32.dp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    groups.forEach { (group, list) ->
                        val isDone = group == TaskGroup.Done
                        item(key = "header-$group") {
                            if (isDone) {
                                DoneHeader(
                                    count = list.size,
                                    expanded = showDone,
                                    onToggle = { showDone = !showDone },
                                    onClear = { confirmClear = true },
                                    modifier = Modifier.animateItem(),
                                )
                            } else {
                                GroupHeader(group, Modifier.animateItem())
                            }
                        }
                        if (!isDone || showDone) {
                            items(list, key = { it.id }) { task ->
                                TaskRow(
                                    task = task,
                                    today = today,
                                    onToggle = { viewModel.toggleDone(task) },
                                    onOpen = { onOpen(task.id) },
                                    // Отмеченная задача плавно переезжает в свою группу
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !adding,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = primaryCornerModifier(bottom = padding.calculateBottomPadding()),
            ) {
                FloatingActionButton(onClick = { adding = true }) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.tasks_new))
                }
            }

            if (adding) {
                // Тап по затемнению — закрыть строку
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable(interactionSource = null, indication = null, onClick = ::closeAdding),
                )
            }
            AnimatedVisibility(
                visible = adding,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding(),
            ) {
                QuickAddBar(
                    draft = viewModel.draft,
                    onDraftChange = { viewModel.draft = it },
                    onAdd = viewModel::add,
                    onDismiss = ::closeAdding,
                )
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.tasks_clear_done_title),
            text = stringResource(R.string.tasks_clear_done_text),
            confirmLabel = stringResource(R.string.tasks_clear_done),
            onConfirm = viewModel::deleteDone,
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun GroupHeader(group: TaskGroup, modifier: Modifier = Modifier) {
    val title = stringResource(
        when (group) {
            TaskGroup.Overdue -> R.string.tasks_overdue
            TaskGroup.Today -> R.string.tasks_today
            TaskGroup.Tomorrow -> R.string.tasks_tomorrow
            TaskGroup.Later -> R.string.tasks_later
            TaskGroup.NoDate -> R.string.tasks_no_date
            TaskGroup.Done -> R.string.tasks_done
        },
    )
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = if (group == TaskGroup.Overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun DoneHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.tasks_done, count),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Box(Modifier.weight(1f))
        if (expanded) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.tasks_clear_done)) }
        }
    }
}
