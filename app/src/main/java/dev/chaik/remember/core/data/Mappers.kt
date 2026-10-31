package dev.chaik.remember.core.data

import dev.chaik.remember.core.database.entity.DayEntryEntity
import dev.chaik.remember.core.database.entity.MoneyCategoryEntity
import dev.chaik.remember.core.database.entity.MoneyTransactionEntity
import dev.chaik.remember.core.database.entity.NoteEntity
import dev.chaik.remember.core.database.entity.NoteFolderEntity
import dev.chaik.remember.core.database.entity.SleepEntryEntity
import dev.chaik.remember.core.database.entity.TagEntity
import dev.chaik.remember.core.model.DayEntry
import dev.chaik.remember.core.model.MoneyCategory
import dev.chaik.remember.core.model.MoneyTransaction
import dev.chaik.remember.core.model.Note
import dev.chaik.remember.core.model.NoteFolder
import dev.chaik.remember.core.model.SleepEntry
import dev.chaik.remember.core.model.Tag
import java.time.Instant

// Преобразования «строка базы ↔ модель». Экраны работают только с моделями.

internal fun TagEntity.toModel() = Tag(id, name)

internal fun DayEntryEntity.toModel(tags: List<TagEntity>) = DayEntry(
    date = date,
    mood = mood,
    text = text,
    tags = tags.map { it.toModel() },
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun DayEntry.toEntity(createdAt: Instant, updatedAt: Instant) = DayEntryEntity(
    date = date,
    mood = mood,
    text = text,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun SleepEntryEntity.toModel() = SleepEntry(date, bedTime, wakeTime, quality, dream)

internal fun SleepEntry.toEntity(updatedAt: Instant) = SleepEntryEntity(
    date = date,
    bedTime = bedTime,
    wakeTime = wakeTime,
    quality = quality,
    dream = dream,
    updatedAt = updatedAt,
)

internal fun MoneyCategoryEntity.toModel() = MoneyCategory(id, name, type, icon, color, sortOrder, archived)

internal fun MoneyCategory.toEntity() = MoneyCategoryEntity(id, name, type, icon, color, sortOrder, archived)

internal fun MoneyTransactionEntity.toModel() = MoneyTransaction(
    id = id,
    date = date,
    type = type,
    amountMinor = amountMinor,
    currency = currency,
    categoryId = categoryId,
    comment = comment,
    createdAt = createdAt,
)

internal fun MoneyTransaction.toEntity(createdAt: Instant) = MoneyTransactionEntity(
    id = id,
    date = date,
    type = type,
    amountMinor = amountMinor,
    currency = currency,
    categoryId = categoryId,
    comment = comment,
    createdAt = createdAt,
)

internal fun NoteEntity.toModel(tags: List<TagEntity> = emptyList()) = Note(
    id = id,
    title = title,
    text = text,
    folderId = folderId,
    pinned = pinned,
    isProtected = isProtected,
    tags = tags.map { it.toModel() },
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun Note.toEntity(createdAt: Instant, updatedAt: Instant) = NoteEntity(
    id = id,
    title = title,
    text = text,
    folderId = folderId,
    pinned = pinned,
    isProtected = isProtected,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun NoteFolderEntity.toModel() = NoteFolder(id, name, sortOrder)
