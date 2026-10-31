package dev.chaik.remember.feature.gym.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chaik.remember.app.AppContainer
import dev.chaik.remember.core.model.Exercise
import dev.chaik.remember.core.model.ExerciseKind
import dev.chaik.remember.core.model.ExerciseSession
import dev.chaik.remember.core.model.Workout
import dev.chaik.remember.core.model.WorkoutSet
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Страница тренировки. Каждое действие сразу пишется в базу (в фоне приложения —
 * чтобы доделалось, даже если страницу закрыли); заметка — через паузу в наборе.
 */
class WorkoutViewModel(private val container: AppContainer, private val workoutId: Long) : ViewModel() {
    private val workouts = container.workoutRepository
    private val scope = container.appScope

    /** null — грузится или тренировку удалили. */
    val workout: StateFlow<Workout?> = workouts.observe(workoutId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val exercises: StateFlow<List<Exercise>> =
        workouts.observeExercises().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    var note by mutableStateOf("")
    var isLoaded by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            note = workout.filterNotNull().first().note
            isLoaded = true
            autosaveNote()
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun autosaveNote() {
        snapshotFlow { note }
            .drop(1)
            .debounce(NOTE_SAVE_DELAY_MS)
            .collect { editHeader { it } }
    }

    /** Поменять шапку. Заметка всегда берётся из поля — в базе она могла ещё не обновиться. */
    fun editHeader(transform: (Workout) -> Workout) {
        val text = note.trim()
        scope.launch { workouts.editHeader(workoutId) { transform(it).copy(note = text) } }
    }

    fun finish(weightAfter: Double?, end: LocalTime) {
        val text = note.trim()
        scope.launch {
            workouts.editHeader(workoutId) { it.copy(weightAfter = weightAfter ?: it.weightAfter, note = text) }
            workouts.finish(workoutId, end)
        }
    }

    fun delete(onDone: () -> Unit) {
        scope.launch { workouts.delete(workoutId) }
        onDone()
    }

    fun addExercise(name: String, kind: ExerciseKind) {
        if (name.isBlank()) return
        scope.launch { workouts.addExercise(workoutId, name, kind) }
    }

    fun removeExercise(workoutExerciseId: Long) {
        scope.launch { workouts.removeExercise(workoutExerciseId) }
    }

    fun setKind(exercise: Exercise, kind: ExerciseKind) {
        scope.launch { workouts.setExerciseKind(exercise.id, kind) }
    }

    fun addSet(workoutExerciseId: Long, set: WorkoutSet) {
        scope.launch { workouts.addSet(workoutExerciseId, set) }
    }

    fun updateSet(set: WorkoutSet) {
        scope.launch { workouts.updateSet(set) }
    }

    fun deleteSet(setId: Long) {
        scope.launch { workouts.deleteSet(setId) }
    }

    fun history(exerciseId: Long): Flow<List<ExerciseSession>> = workouts.observeHistory(exerciseId)

    /** Прошлый вес для формы «после», если «до» не записан. */
    suspend fun lastBodyWeight(): Double? = workouts.lastBodyWeight()

    override fun onCleared() {
        // Недописанная заметка — сохранить сразу
        if (isLoaded && note.trim() != workout.value?.note) editHeader { it }
    }

    private companion object {
        const val NOTE_SAVE_DELAY_MS = 600L
    }
}
