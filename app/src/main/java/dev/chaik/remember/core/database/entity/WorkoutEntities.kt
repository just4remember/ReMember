package dev.chaik.remember.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Справочник упражнений; [nameKey] — название в нижнем регистре, чтобы «жим» и «Жим» были одним. */
@Entity(tableName = "exercise", indices = [Index("name_key", unique = true)])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "name_key") val nameKey: String,
    /** ExerciseKind.code: «WR», «R», «T». */
    val kind: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(tableName = "workout", indices = [Index("date"), Index("active")])
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    @ColumnInfo(name = "start_time") val start: LocalTime?,
    @ColumnInfo(name = "end_time") val end: LocalTime?,
    @ColumnInfo(name = "weight_before") val weightBefore: Double?,
    @ColumnInfo(name = "weight_after") val weightAfter: Double?,
    val note: String,
    val active: Boolean,
    val imported: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(
    tableName = "workout_exercise",
    foreignKeys = [
        ForeignKey(entity = WorkoutEntity::class, parentColumns = ["id"], childColumns = ["workout_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exercise_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("workout_id"), Index("exercise_id")],
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "workout_id") val workoutId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(
    tableName = "workout_set",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workout_exercise_id")],
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "workout_exercise_id") val workoutExerciseId: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val weight: Double?,
    val reps: Int?,
    val seconds: Int?,
    /** Скорость бега, км/ч; появилась в версии 5 базы. */
    val speed: Double? = null,
)

/** Шаблон тренировки: заранее заготовленный список упражнений. */
@Entity(tableName = "workout_template")
data class WorkoutTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(
    tableName = "workout_template_exercise",
    foreignKeys = [
        ForeignKey(entity = WorkoutTemplateEntity::class, parentColumns = ["id"], childColumns = ["template_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exercise_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("template_id"), Index("exercise_id")],
)
data class WorkoutTemplateExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "template_id") val templateId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

/** Упражнение тренировки вместе с названием — для списка и страницы тренировки. */
data class WorkoutExerciseRow(
    val id: Long,
    @ColumnInfo(name = "workout_id") val workoutId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val name: String,
    val kind: String,
)
