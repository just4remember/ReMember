package dev.chaik.remember.feature.gym.templates

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chaik.remember.app.AppContainer
import dev.chaik.remember.core.model.Exercise
import dev.chaik.remember.core.model.ExerciseKind
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Редактор шаблона: правки сохраняются сами, через паузу и при уходе с экрана. */
class TemplateEditorViewModel(private val container: AppContainer, templateId: Long) : ViewModel() {
    private val workouts = container.workoutRepository

    var name by mutableStateOf("")
    var exercises by mutableStateOf(emptyList<Exercise>())
        private set
    var isLoaded by mutableStateOf(false)
        private set

    val dictionary: StateFlow<List<Exercise>> =
        workouts.observeExercises().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var id = templateId
    private var saved: Pair<String, List<Long>>? = null
    private var deleted = false
    private val lock = Mutex()

    init {
        viewModelScope.launch {
            if (templateId != 0L) {
                workouts.observeTemplates().first().firstOrNull { it.id == templateId }?.let {
                    name = it.name
                    exercises = it.exercises
                }
            }
            saved = snapshot()
            isLoaded = true
            autosave()
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun autosave() {
        snapshotFlow { snapshot() }.drop(1).debounce(SAVE_DELAY_MS).collect { save(it) }
    }

    fun add(name: String, kind: ExerciseKind) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val exercise = workouts.exerciseFor(name, kind)
            if (exercises.none { it.id == exercise.id }) exercises = exercises + exercise
        }
    }

    fun remove(index: Int) {
        exercises = exercises.filterIndexed { i, _ -> i != index }
    }

    /** Сдвинуть упражнение на [by] позиций. */
    fun move(index: Int, by: Int) {
        val target = index + by
        if (target !in exercises.indices) return
        exercises = exercises.toMutableList().apply { add(target, removeAt(index)) }
    }

    fun flush() {
        val snapshot = snapshot()
        container.appScope.launch { save(snapshot) }
    }

    fun delete(onDone: () -> Unit) {
        deleted = true
        val templateId = id
        container.appScope.launch { if (templateId != 0L) workouts.deleteTemplate(templateId) }
        onDone()
    }

    override fun onCleared() {
        // viewModelScope уже отменён — дописываем в фоне приложения
        if (isLoaded) flush()
    }

    private fun snapshot() = name.trim() to exercises.map { it.id }

    private suspend fun save(snapshot: Pair<String, List<Long>>) = lock.withLock {
        if (deleted || !isLoaded || snapshot == saved) return@withLock
        // Пустой новый шаблон не создаём
        if (id == 0L && snapshot.first.isEmpty() && snapshot.second.isEmpty()) return@withLock
        id = workouts.saveTemplate(id, snapshot.first, snapshot.second)
        saved = snapshot
    }

    private companion object {
        const val SAVE_DELAY_MS = 500L
    }
}
