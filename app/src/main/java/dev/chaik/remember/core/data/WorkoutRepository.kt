package dev.chaik.remember.core.data

import androidx.room.withTransaction
import dev.chaik.remember.core.database.ReMemberDatabase
import dev.chaik.remember.core.database.entity.ExerciseEntity
import dev.chaik.remember.core.database.entity.WorkoutEntity
import dev.chaik.remember.core.database.entity.WorkoutExerciseEntity
import dev.chaik.remember.core.database.entity.WorkoutExerciseRow
import dev.chaik.remember.core.database.entity.WorkoutSetEntity
import dev.chaik.remember.core.database.entity.WorkoutTemplateEntity
import dev.chaik.remember.core.database.entity.WorkoutTemplateExerciseEntity
import dev.chaik.remember.core.model.Exercise
import dev.chaik.remember.core.model.ExerciseKind
import dev.chaik.remember.core.model.ExerciseSession
import dev.chaik.remember.core.model.Workout
import dev.chaik.remember.core.model.WorkoutExercise
import dev.chaik.remember.core.model.WorkoutSet
import dev.chaik.remember.core.model.WorkoutSummary
import dev.chaik.remember.core.model.WorkoutTemplate
import dev.chaik.remember.core.model.repeatedInTemplates
import dev.chaik.remember.core.model.stackTemplates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Тренировки, их упражнения и подходы, справочник упражнений.
 * Страница тренировки пишет в базу сразу каждое действие — отдельного «сохранить» нет.
 */
class WorkoutRepository(private val db: ReMemberDatabase) {
    private val dao = db.workoutDao()

    /** Все тренировки по хронологии, с номерами и названиями упражнений. */
    fun observeSummaries(): Flow<List<WorkoutSummary>> =
        combine(dao.observeAll(), dao.observeAllExerciseRows()) { workouts, rows ->
            val names = rows.groupBy({ it.workoutId }, { it.name })
            workouts.mapIndexed { i, w ->
                WorkoutSummary(
                    id = w.id,
                    number = i + 1,
                    date = w.date,
                    start = w.start,
                    end = w.end,
                    weightBefore = w.weightBefore,
                    weightAfter = w.weightAfter,
                    active = w.active,
                    imported = w.imported,
                    exerciseNames = names[w.id].orEmpty(),
                )
            }
        }

    /** Тренировка целиком; null — удалена. */
    fun observe(id: Long): Flow<Workout?> =
        combine(dao.observe(id), dao.observeNumber(id), dao.observeExerciseRows(id), dao.observeSets(id)) { w, number, rows, sets ->
            w?.toModel(number, rows, sets)
        }

    /** Идущая сейчас тренировка (без упражнений). */
    fun observeActive(): Flow<Workout?> = dao.observeActive().map { it?.toModel(0, emptyList(), emptyList()) }

    suspend fun active(): Workout? = dao.active()?.let { it.toModel(dao.observeNumber(it.id).first(), emptyList(), emptyList()) }

    fun observeExercises(): Flow<List<Exercise>> = dao.observeExercises().map { list -> list.map { it.toModel() } }

    /** Тренировки с этим упражнением по хронологии; без подходов не считаются. */
    fun observeHistory(exerciseId: Long): Flow<List<ExerciseSession>> =
        dao.observeExerciseSets(exerciseId).map { rows ->
            rows.groupBy { it.workoutId }.map { (workoutId, sets) ->
                val first = sets.first()
                ExerciseSession(
                    workoutId = workoutId,
                    date = first.date,
                    start = first.start,
                    sets = sets.map { WorkoutSet(it.id, it.weight, it.reps, it.seconds, it.speed) },
                )
            }
        }

    /** Шаблоны в порядке создания. */
    fun observeTemplates(): Flow<List<WorkoutTemplate>> =
        combine(dao.observeTemplates(), dao.observeTemplateExercises()) { templates, rows ->
            val byTemplate = rows.groupBy { it.templateId }
            templates.map { template ->
                WorkoutTemplate(
                    id = template.id,
                    name = template.name,
                    exercises = byTemplate[template.id].orEmpty().map { Exercise(it.id, it.name, ExerciseKind.fromCode(it.kind)) },
                )
            }
        }

    /** Сохранить шаблон; [id] = 0 — новый. Возвращает id. */
    suspend fun saveTemplate(id: Long, name: String, exerciseIds: List<Long>): Long = db.withTransaction {
        val templateId = if (id == 0L) {
            dao.insertTemplate(WorkoutTemplateEntity(name = name, createdAt = Instant.now()))
        } else {
            dao.renameTemplate(id, name)
            id
        }
        dao.clearTemplateExercises(templateId)
        dao.insertTemplateExercises(exerciseIds.mapIndexed { i, exerciseId -> WorkoutTemplateExerciseEntity(0, templateId, exerciseId, i) })
        templateId
    }

    suspend fun deleteTemplate(id: Long) = dao.deleteTemplate(id)

    /** Добавить в конец тренировки упражнения шаблонов — в порядке [templateIds]. Возвращает пропущенные повторы. */
    suspend fun applyTemplates(workoutId: Long, templateIds: List<Long>): Set<Long> = db.withTransaction {
        val templates = templateIds.map { dao.templateExerciseIds(it) }
        val present = dao.workoutExerciseIds(workoutId)
        var order = dao.nextExerciseOrder(workoutId)
        stackTemplates(templates, present).forEach { exerciseId ->
            dao.insertWorkoutExercise(WorkoutExerciseEntity(0, workoutId, exerciseId, order++))
        }
        repeatedInTemplates(templates, present)
    }

    /** Упражнение из справочника; незнакомое название туда попадает. */
    suspend fun exerciseFor(name: String, kind: ExerciseKind): Exercise = db.withTransaction {
        val id = getOrCreateExercise(name, kind)
        Exercise(id, name.trim(), findExercise(name)?.kind ?: kind)
    }

    suspend fun findExercise(name: String): Exercise? = dao.exerciseByKey(name.trim().lowercase())?.toModel()

    suspend fun lastBodyWeight(): Double? = dao.lastBodyWeight()

    /** Начать тренировку сейчас. Если одна уже идёт — вернуть её, вторую не начинать. */
    suspend fun start(now: LocalDateTime = LocalDateTime.now(), weightBefore: Double? = null): Long = db.withTransaction {
        dao.active()?.let { return@withTransaction it.id }
        dao.insert(
            newEntity(now.toLocalDate(), now.toLocalTime().withNano(0), end = null, active = true).copy(weightBefore = weightBefore),
        )
    }

    /** Прошлая тренировка, записанная вручную. */
    suspend fun createPast(date: LocalDate, start: LocalTime?, end: LocalTime?): Long =
        dao.insert(newEntity(date, start, end, active = false))

    /**
     * Поменять шапку (дата, время, вес, заметка) свежей версией из базы — две правки подряд
     * не затрут друг друга. Упражнения не трогает.
     */
    suspend fun editHeader(id: Long, transform: (Workout) -> Workout) = db.withTransaction {
        val entity = dao.get(id) ?: return@withTransaction
        val workout = transform(entity.toModel(0, emptyList(), emptyList()))
        dao.update(
            entity.copy(
                date = workout.date,
                start = workout.start,
                end = workout.end,
                weightBefore = workout.weightBefore,
                weightAfter = workout.weightAfter,
                note = workout.note,
                updatedAt = Instant.now(),
            ),
        )
    }

    suspend fun finish(id: Long, end: LocalTime = LocalTime.now()) {
        val entity = dao.get(id) ?: return
        dao.update(entity.copy(end = end.withSecond(0).withNano(0), active = false, updatedAt = Instant.now()))
    }

    suspend fun delete(id: Long) = dao.delete(id) // упражнения и подходы — каскадом

    /** Добавить упражнение в тренировку; незнакомое название попадает в справочник. Возвращает id записи. */
    suspend fun addExercise(workoutId: Long, name: String, kind: ExerciseKind): Long = db.withTransaction {
        val exerciseId = getOrCreateExercise(name, kind)
        dao.insertWorkoutExercise(WorkoutExerciseEntity(0, workoutId, exerciseId, dao.nextExerciseOrder(workoutId)))
    }

    suspend fun removeExercise(workoutExerciseId: Long) = dao.deleteWorkoutExercise(workoutExerciseId)

    suspend fun setExerciseKind(exerciseId: Long, kind: ExerciseKind) = dao.setExerciseKind(exerciseId, kind.code)

    suspend fun addSet(workoutExerciseId: Long, set: WorkoutSet): Long = db.withTransaction {
        dao.insertSet(WorkoutSetEntity(0, workoutExerciseId, dao.nextSetOrder(workoutExerciseId), set.weight, set.reps, set.seconds, set.speed))
    }

    suspend fun updateSet(set: WorkoutSet) = dao.updateSet(set.id, set.weight, set.reps, set.seconds, set.speed)

    suspend fun deleteSet(id: Long) = dao.deleteSet(id)

    /**
     * Что получится из импорта: повторы — тренировки с той же датой и временем начала,
     * что уже есть в приложении (или раньше в этом же файле), — пропускаются.
     */
    suspend fun previewImport(parsed: List<Workout>): ImportPreview {
        val seen = dao.observeAll().first().map { it.date to it.start }.toMutableSet()
        val fresh = parsed.filter { seen.add(it.date to it.start) }
        val known = dao.observeExercises().first().map { it.nameKey }.toSet()
        val newExercises = fresh.flatMap { w -> w.exercises.map { it.exercise.name } }
            .distinctBy { it.lowercase() }
            .filter { it.lowercase() !in known }
        return ImportPreview(workouts = fresh, duplicates = parsed.size - fresh.size, newExercises = newExercises)
    }

    /** Сохранить тренировки из [ImportPreview.workouts] одним махом. Возвращает, сколько добавлено. */
    suspend fun import(workouts: List<Workout>): Int = db.withTransaction {
        for (w in workouts) {
            val id = dao.insert(newEntity(w.date, w.start, w.end, active = false).copy(
                weightBefore = w.weightBefore,
                weightAfter = w.weightAfter,
                note = w.note,
                imported = true,
            ))
            w.exercises.forEachIndexed { order, entry ->
                val exerciseId = getOrCreateExercise(entry.exercise.name, entry.exercise.kind)
                val entryId = dao.insertWorkoutExercise(WorkoutExerciseEntity(0, id, exerciseId, order))
                entry.sets.forEachIndexed { i, set ->
                    dao.insertSet(WorkoutSetEntity(0, entryId, i, set.weight, set.reps, set.seconds, set.speed))
                }
            }
        }
        workouts.size
    }

    private suspend fun getOrCreateExercise(name: String, kind: ExerciseKind): Long {
        val clean = name.trim()
        val key = clean.lowercase()
        return dao.exerciseByKey(key)?.id
            ?: dao.insertExercise(ExerciseEntity(name = clean, nameKey = key, kind = kind.code, createdAt = Instant.now()))
    }

    private fun newEntity(date: LocalDate, start: LocalTime?, end: LocalTime?, active: Boolean): WorkoutEntity {
        val now = Instant.now()
        return WorkoutEntity(
            date = date,
            start = start,
            end = end,
            weightBefore = null,
            weightAfter = null,
            note = "",
            active = active,
            imported = false,
            createdAt = now,
            updatedAt = now,
        )
    }
}

/** Итог разбора файла перед импортом. */
data class ImportPreview(
    val workouts: List<Workout>,
    val duplicates: Int,
    val newExercises: List<String>,
)

private fun ExerciseEntity.toModel() = Exercise(id = id, name = name, kind = ExerciseKind.fromCode(kind))

private fun WorkoutEntity.toModel(number: Int, rows: List<WorkoutExerciseRow>, sets: List<WorkoutSetEntity>): Workout {
    val setsByEntry = sets.groupBy { it.workoutExerciseId }
    return Workout(
        id = id,
        number = number,
        date = date,
        start = start,
        end = end,
        weightBefore = weightBefore,
        weightAfter = weightAfter,
        note = note,
        active = active,
        imported = imported,
        exercises = rows.map { row ->
            WorkoutExercise(
                id = row.id,
                exercise = Exercise(id = row.exerciseId, name = row.name, kind = ExerciseKind.fromCode(row.kind)),
                sets = setsByEntry[row.id].orEmpty().map { WorkoutSet(it.id, it.weight, it.reps, it.seconds, it.speed) },
            )
        },
    )
}
