package dev.chaik.remember.feature.today.day

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.chaik.remember.core.data.DayRepository
import dev.chaik.remember.core.data.TagRepository
import dev.chaik.remember.core.editor.EditorState
import dev.chaik.remember.core.model.DayEntry
import dev.chaik.remember.core.model.Tag
import dev.chaik.remember.core.photos.PhotoStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * Режим «День»: текст с фото, оценка дня и теги.
 *
 * Сохраняется сам: через [AUTOSAVE_DELAY_MS] после последнего изменения
 * и сразу — при смене даты или уходе приложения в фон ([flush]).
 * Если всё стёрли (текст, оценку, теги) — запись дня удаляется.
 */
class DayController(
    private val scope: CoroutineScope,
    private val days: DayRepository,
    private val tagRepository: TagRepository,
    private val photos: PhotoStorage,
) {
    val editor = EditorState()

    var mood by mutableStateOf<Int?>(null)
        private set

    /** Оценка закрыта замком, чтобы случайно не сбить её. Только что выставленная ещё открыта. */
    var ratingLocked by mutableStateOf(false)
        private set

    var tags by mutableStateOf<List<Tag>>(emptyList())
        private set

    var isLoaded by mutableStateOf(false)
        private set

    var isImporting by mutableStateOf(false)
        private set

    /** Сколько фото не удалось добавить в последний раз (для сообщения). */
    var failedImports by mutableIntStateOf(0)
        private set

    /** Все теги — подсказки при добавлении. */
    val allTags: StateFlow<List<Tag>> =
        tagRepository.observeForDays().stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var date: LocalDate? = null
    private var exists = false
    private var saved: Snapshot? = null
    private var autosaveJob: Job? = null
    private val lock = Mutex()

    /** Загрузить запись за [date], предварительно сохранив текущую. */
    suspend fun load(date: LocalDate) {
        autosaveJob?.cancel()
        flushNow()
        lock.withLock {
            isLoaded = false
            val entry = days.observe(date).first()
            this.date = date
            exists = entry != null
            editor.load(entry?.text.orEmpty())
            mood = entry?.mood
            lockRating()
            tags = entry?.tags.orEmpty()
            editor.applyPhotoSizes(photos.photos(editor.photoIds))
            saved = snapshot()
            isLoaded = true
        }
        autosaveJob = scope.launch { autosave() }
    }

    @OptIn(FlowPreview::class)
    private suspend fun autosave() {
        snapshotFlow { snapshot() }
            .debounce(AUTOSAVE_DELAY_MS)
            .collect { save(it) }
    }

    /** Закрыть оценку замком: ушли со страницы, сменили день или приложение. Без оценки замка нет. */
    fun lockRating() {
        ratingLocked = mood != null
    }

    fun unlockRating() {
        ratingLocked = false
    }

    /** 0 звёзд — оценки нет. */
    fun setMood(halves: Int) {
        mood = halves.takeIf { it > 0 }
    }

    /** Добавить тег по имени (новый создаётся). */
    fun addTag(name: String) {
        if (name.isBlank()) return
        scope.launch {
            val tag = tagRepository.getOrCreate(name)
            if (tags.none { it.id == tag.id }) tags = tags + tag
        }
    }

    fun toggleTag(tag: Tag) {
        tags = if (tags.any { it.id == tag.id }) tags.filterNot { it.id == tag.id } else tags + tag
    }

    fun removeTag(tag: Tag) {
        tags = tags.filterNot { it.id == tag.id }
    }

    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            isImporting = true
            val imported = uris.mapNotNull { uri ->
                runCatching { photos.import(uri) }
                    .onFailure { Log.w(TAG, "Не удалось добавить фото $uri", it) }
                    .getOrNull()
            }
            failedImports = uris.size - imported.size
            editor.insertPhotos(imported)
            isImporting = false
        }
    }

    fun consumeImportError() {
        failedImports = 0
    }

    /** Сохранить сейчас, не дожидаясь паузы в наборе. */
    suspend fun flushNow() {
        if (isLoaded) save(snapshot())
    }

    private fun snapshot() = Snapshot(editor.markdown, mood, tags)

    private suspend fun save(snapshot: Snapshot) = lock.withLock {
        val date = date ?: return@withLock
        if (!isLoaded || snapshot == saved) return@withLock
        when {
            !snapshot.isEmpty -> {
                days.save(DayEntry(date = date, mood = snapshot.mood, text = snapshot.markdown, tags = snapshot.tags))
                exists = true
            }
            // Всё стёрли — записи за этот день больше нет
            exists -> {
                days.delete(date)
                exists = false
            }
        }
        saved = snapshot
    }

    private data class Snapshot(val markdown: String, val mood: Int?, val tags: List<Tag>) {
        val isEmpty get() = markdown.isBlank() && mood == null && tags.isEmpty()
    }

    private companion object {
        const val TAG = "DayController"
        const val AUTOSAVE_DELAY_MS = 700L
    }
}
