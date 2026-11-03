package dev.chaik.remember.feature.tasks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chaik.remember.core.data.TaskRepository
import dev.chaik.remember.core.model.Task
import dev.chaik.remember.core.model.TaskPriority
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Список задач: данные остаются в памяти, пока жив экран (как и у заметок). */
class TasksViewModel(private val tasks: TaskRepository) : ViewModel() {

    /** null — ещё грузится. */
    val all: StateFlow<List<Task>?> = tasks.observeAll().stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** Набранная, но ещё не добавленная задача: строку добавления закрыли — она не пропадает. */
    var draft by mutableStateOf(TaskDraft())

    fun add(title: String, dueDate: LocalDate?, priority: TaskPriority) {
        if (title.isBlank()) return
        viewModelScope.launch { tasks.save(Task(title = title.trim(), dueDate = dueDate, priority = priority)) }
    }

    fun toggleDone(task: Task) {
        viewModelScope.launch { tasks.setDone(task, !task.done) }
    }

    fun deleteDone() {
        viewModelScope.launch { tasks.deleteDone() }
    }
}

/** Строка новой задачи: название, срок и приоритет. */
data class TaskDraft(
    val title: String = "",
    val dueDate: LocalDate? = null,
    val priority: TaskPriority = TaskPriority.NONE,
)
