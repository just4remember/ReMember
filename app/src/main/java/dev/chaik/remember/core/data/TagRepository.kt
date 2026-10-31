package dev.chaik.remember.core.data

import dev.chaik.remember.core.database.ReMemberDatabase
import dev.chaik.remember.core.model.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Теги дней и заметок. Списки у них независимые: тег попадает в список вкладки, только если
 * используется на ней. Таблица имён общая, но снаружи это незаметно.
 */
class TagRepository(db: ReMemberDatabase) {
    private val dao = db.tagDao()

    /** Теги дней: чаще всего используемые — первыми. */
    fun observeForDays(): Flow<List<Tag>> = dao.observeDayTagsByUse().map { list -> list.map { it.toModel() } }

    /** Теги заметок: чаще всего используемые — первыми. */
    fun observeForNotes(): Flow<List<Tag>> = dao.observeNoteTagsByUse().map { list -> list.map { it.toModel() } }

    /** Тег по имени (без учёта регистра); если такого нет — создаётся. */
    suspend fun getOrCreate(name: String): Tag = dao.getOrCreate(name.trim()).toModel()

    /** Удалить теги, которые больше нигде не используются. */
    suspend fun deleteUnused() = dao.deleteUnused()
}
