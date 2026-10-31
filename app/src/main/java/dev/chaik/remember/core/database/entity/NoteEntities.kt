package dev.chaik.remember.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "note_folder")
data class NoteFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(
    tableName = "note",
    foreignKeys = [
        ForeignKey(
            entity = NoteFolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            // Удалили папку — заметки остаются, просто без папки
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("folder_id"), Index("updated_at")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Markdown со ссылками на фото вида `photo:<id>`. */
    val text: String,
    @ColumnInfo(name = "folder_id") val folderId: Long?,
    val pinned: Boolean,
    /** Открывается только по PIN или отпечатку. */
    @ColumnInfo(name = "is_protected", defaultValue = "0") val isProtected: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)
