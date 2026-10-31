package dev.chaik.remember.core.model

import java.time.Instant

data class Note(
    val id: Long = 0,
    val title: String = "",
    /** Markdown; фото вставляются как `photo:<id>` (см. PhotoRefs). */
    val text: String = "",
    val folderId: Long? = null,
    val pinned: Boolean = false,
    val isProtected: Boolean = false,
    val tags: List<Tag> = emptyList(),
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)

data class NoteFolder(
    val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
)

/** Заметка для списка: без полного текста, только то, что видно на карточке. */
data class NoteSummary(
    val id: Long,
    val title: String,
    /** Начало текста без разметки, до ~150 символов. */
    val preview: String,
    /** Первое фото — обложка карточки; null — фото нет. */
    val coverPhotoId: String?,
    val folderId: Long?,
    val pinned: Boolean,
    /** Защищена: начало текста, обложка и теги в список не попадают. */
    val isProtected: Boolean,
    val tags: List<Tag>,
    val createdAt: Instant,
    val updatedAt: Instant,
)
