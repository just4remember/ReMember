package dev.chaik.remember.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.chaik.remember.core.database.dao.DayEntryDao
import dev.chaik.remember.core.database.dao.EventDao
import dev.chaik.remember.core.database.dao.MoneyDao
import dev.chaik.remember.core.database.dao.NoteDao
import dev.chaik.remember.core.database.dao.PhotoDao
import dev.chaik.remember.core.database.dao.PlaceDao
import dev.chaik.remember.core.database.dao.SleepDao
import dev.chaik.remember.core.database.dao.TagDao
import dev.chaik.remember.core.database.dao.TaskDao
import dev.chaik.remember.core.database.dao.WorkoutDao
import dev.chaik.remember.core.database.entity.DayEntryEntity
import dev.chaik.remember.core.database.entity.DayEntryTagEntity
import dev.chaik.remember.core.database.entity.EventEntity
import dev.chaik.remember.core.database.entity.ExerciseEntity
import dev.chaik.remember.core.database.entity.MoneyCategoryEntity
import dev.chaik.remember.core.database.entity.MoneyTransactionEntity
import dev.chaik.remember.core.database.entity.NoteEntity
import dev.chaik.remember.core.database.entity.NoteFolderEntity
import dev.chaik.remember.core.database.entity.NoteTagEntity
import dev.chaik.remember.core.database.entity.PhotoEntity
import dev.chaik.remember.core.database.entity.PhotoRefEntity
import dev.chaik.remember.core.database.entity.PlaceCategoryEntity
import dev.chaik.remember.core.database.entity.PlaceEntity
import dev.chaik.remember.core.database.entity.SleepEntryEntity
import dev.chaik.remember.core.database.entity.SubtaskEntity
import dev.chaik.remember.core.database.entity.TagEntity
import dev.chaik.remember.core.database.entity.TaskEntity
import dev.chaik.remember.core.database.entity.WorkoutEntity
import dev.chaik.remember.core.database.entity.WorkoutExerciseEntity
import dev.chaik.remember.core.database.entity.WorkoutSetEntity
import dev.chaik.remember.core.database.entity.WorkoutTemplateEntity
import dev.chaik.remember.core.database.entity.WorkoutTemplateExerciseEntity

/**
 * Вся база приложения.
 *
 * При изменении таблиц: поднять [version] и добавить миграцию
 * (`autoMigrations` — для простых случаев). Схемы лежат в app/schemas.
 */
@Database(
    version = 12,
    entities = [
        DayEntryEntity::class,
        SleepEntryEntity::class,
        MoneyCategoryEntity::class,
        MoneyTransactionEntity::class,
        NoteFolderEntity::class,
        NoteEntity::class,
        TagEntity::class,
        DayEntryTagEntity::class,
        NoteTagEntity::class,
        PhotoEntity::class,
        PhotoRefEntity::class,
        TaskEntity::class,
        SubtaskEntity::class,
        EventEntity::class,
        ExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
        WorkoutTemplateEntity::class,
        WorkoutTemplateExerciseEntity::class,
        PlaceEntity::class,
        PlaceCategoryEntity::class,
    ],
    autoMigrations = [
        // 2: задачи и подзадачи — только новые таблицы
        AutoMigration(from = 1, to = 2),
        // 3: события календаря — новая таблица
        AutoMigration(from = 2, to = 3),
        // 4: зал — упражнения, тренировки, подходы
        AutoMigration(from = 3, to = 4),
        // 5: скорость в подходе — для бега
        AutoMigration(from = 4, to = 5),
        // 6: защищённые заметки
        AutoMigration(from = 5, to = 6),
        // 7: шаблоны тренировок — только новые таблицы
        AutoMigration(from = 6, to = 7),
        // 8: места на карте — новая таблица
        AutoMigration(from = 7, to = 8),
        // 9: категории меток — новая таблица и колонка
        AutoMigration(from = 8, to = 9),
        // 10: дистанция в подходе — для бега, вело, плавания
        AutoMigration(from = 9, to = 10),
        // 11: ручной порядок заметок
        AutoMigration(from = 10, to = 11),
        // 12: заметка у упражнения
        AutoMigration(from = 11, to = 12),
    ],
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ReMemberDatabase : RoomDatabase() {
    abstract fun dayEntryDao(): DayEntryDao
    abstract fun sleepDao(): SleepDao
    abstract fun moneyDao(): MoneyDao
    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao
    abstract fun photoDao(): PhotoDao
    abstract fun taskDao(): TaskDao
    abstract fun eventDao(): EventDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun placeDao(): PlaceDao
}
