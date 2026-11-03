package dev.chaik.remember.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.chaik.remember.core.database.entity.ExerciseEntity
import dev.chaik.remember.core.database.entity.WorkoutEntity
import dev.chaik.remember.core.database.entity.WorkoutExerciseEntity
import dev.chaik.remember.core.database.entity.WorkoutExerciseRow
import dev.chaik.remember.core.database.entity.WorkoutSetEntity
import dev.chaik.remember.core.database.entity.WorkoutTemplateEntity
import dev.chaik.remember.core.database.entity.WorkoutTemplateExerciseEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

data class TemplateExerciseRow(val templateId: Long, val id: Long, val name: String, val kind: String)

data class ExerciseSetRow(
    val workoutId: Long,
    val date: LocalDate,
    val start: LocalTime?,
    val id: Long,
    val weight: Double?,
    val reps: Int?,
    val seconds: Int?,
    val speed: Double?,
    val distance: Double?,
)

@Dao
interface WorkoutDao {
    // Тренировки. Хронология — по дате, затем времени начала (без времени — раньше в тот же день)

    @Query("SELECT * FROM workout ORDER BY date, IFNULL(start_time, -1), id")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workout WHERE id = :id")
    fun observe(id: Long): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workout WHERE id = :id")
    suspend fun get(id: Long): WorkoutEntity?

    @Query("SELECT * FROM workout WHERE active = 1 LIMIT 1")
    fun observeActive(): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workout WHERE active = 1 LIMIT 1")
    suspend fun active(): WorkoutEntity?

    /** Номер тренировки: сколько тренировок не позже неё по хронологии. */
    @Query(
        """
        SELECT COUNT(*) FROM workout w,
            (SELECT date AS d, IFNULL(start_time, -1) AS s, id AS i FROM workout WHERE id = :id) t
        WHERE w.date < t.d
            OR (w.date = t.d AND (IFNULL(w.start_time, -1) < t.s OR (IFNULL(w.start_time, -1) = t.s AND w.id <= t.i)))
        """,
    )
    fun observeNumber(id: Long): Flow<Int>

    /** Последний записанный вес тела — подставить в форму. */
    @Query(
        "SELECT IFNULL(weight_after, weight_before) FROM workout " +
            "WHERE weight_after IS NOT NULL OR weight_before IS NOT NULL ORDER BY date DESC, IFNULL(start_time, -1) DESC LIMIT 1",
    )
    suspend fun lastBodyWeight(): Double?

    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    @Update
    suspend fun update(workout: WorkoutEntity)

    @Query("DELETE FROM workout WHERE id = :id")
    suspend fun delete(id: Long)

    // Упражнения тренировок

    @Query(
        "SELECT we.id, we.workout_id, we.exercise_id, we.sort_order, e.name, e.kind FROM workout_exercise we " +
            "JOIN exercise e ON e.id = we.exercise_id ORDER BY we.workout_id, we.sort_order",
    )
    fun observeAllExerciseRows(): Flow<List<WorkoutExerciseRow>>

    @Query(
        "SELECT we.id, we.workout_id, we.exercise_id, we.sort_order, e.name, e.kind FROM workout_exercise we " +
            "JOIN exercise e ON e.id = we.exercise_id WHERE we.workout_id = :workoutId ORDER BY we.sort_order",
    )
    fun observeExerciseRows(workoutId: Long): Flow<List<WorkoutExerciseRow>>

    @Query(
        "SELECT s.* FROM workout_set s JOIN workout_exercise we ON we.id = s.workout_exercise_id " +
            "WHERE we.workout_id = :workoutId ORDER BY s.workout_exercise_id, s.sort_order",
    )
    fun observeSets(workoutId: Long): Flow<List<WorkoutSetEntity>>

    /** Все подходы упражнения по хронологии — для подсказок и графика. */
    @Query(
        """
        SELECT w.id AS workoutId, w.date AS date, w.start_time AS start,
            s.id AS id, s.weight AS weight, s.reps AS reps, s.seconds AS seconds, s.speed AS speed, s.distance AS distance
        FROM workout_set s
        JOIN workout_exercise we ON we.id = s.workout_exercise_id
        JOIN workout w ON w.id = we.workout_id
        WHERE we.exercise_id = :exerciseId
        ORDER BY w.date, IFNULL(w.start_time, -1), w.id, we.sort_order, s.sort_order
        """,
    )
    fun observeExerciseSets(exerciseId: Long): Flow<List<ExerciseSetRow>>

    @Query("SELECT IFNULL(MAX(sort_order) + 1, 0) FROM workout_exercise WHERE workout_id = :workoutId")
    suspend fun nextExerciseOrder(workoutId: Long): Int

    @Insert
    suspend fun insertWorkoutExercise(entry: WorkoutExerciseEntity): Long

    @Query("DELETE FROM workout_exercise WHERE id = :id")
    suspend fun deleteWorkoutExercise(id: Long)

    // Подходы

    @Query("SELECT IFNULL(MAX(sort_order) + 1, 0) FROM workout_set WHERE workout_exercise_id = :workoutExerciseId")
    suspend fun nextSetOrder(workoutExerciseId: Long): Int

    @Insert
    suspend fun insertSet(set: WorkoutSetEntity): Long

    @Query("UPDATE workout_set SET weight = :weight, reps = :reps, seconds = :seconds, speed = :speed, distance = :distance WHERE id = :id")
    suspend fun updateSet(id: Long, weight: Double?, reps: Int?, seconds: Int?, speed: Double?, distance: Double?)

    @Query("DELETE FROM workout_set WHERE id = :id")
    suspend fun deleteSet(id: Long)

    // Шаблоны

    @Query("SELECT * FROM workout_template ORDER BY created_at, id")
    fun observeTemplates(): Flow<List<WorkoutTemplateEntity>>

    @Query(
        """
        SELECT te.template_id AS templateId, e.id AS id, e.name AS name, e.kind AS kind
        FROM workout_template_exercise te
        JOIN exercise e ON e.id = te.exercise_id
        ORDER BY te.template_id, te.sort_order
        """,
    )
    fun observeTemplateExercises(): Flow<List<TemplateExerciseRow>>

    @Query("SELECT exercise_id FROM workout_template_exercise WHERE template_id = :templateId ORDER BY sort_order")
    suspend fun templateExerciseIds(templateId: Long): List<Long>

    @Query("SELECT exercise_id FROM workout_exercise WHERE workout_id = :workoutId")
    suspend fun workoutExerciseIds(workoutId: Long): List<Long>

    @Insert
    suspend fun insertTemplate(template: WorkoutTemplateEntity): Long

    @Query("UPDATE workout_template SET name = :name WHERE id = :id")
    suspend fun renameTemplate(id: Long, name: String)

    @Query("DELETE FROM workout_template WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    @Query("DELETE FROM workout_template_exercise WHERE template_id = :templateId")
    suspend fun clearTemplateExercises(templateId: Long)

    @Insert
    suspend fun insertTemplateExercises(entries: List<WorkoutTemplateExerciseEntity>)

    // Справочник упражнений

    /** Сначала недавно использованные, потом остальные по алфавиту. */
    @Query(
        "SELECT e.* FROM exercise e LEFT JOIN workout_exercise we ON we.exercise_id = e.id " +
            "LEFT JOIN workout w ON w.id = we.workout_id GROUP BY e.id " +
            "ORDER BY MAX(w.date) IS NULL, MAX(w.date) DESC, e.name_key",
    )
    fun observeExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE name_key = :nameKey")
    suspend fun exerciseByKey(nameKey: String): ExerciseEntity?

    @Insert
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    @Query("UPDATE exercise SET kind = :kind WHERE id = :id")
    suspend fun setExerciseKind(id: Long, kind: String)
}
