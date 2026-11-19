package dev.chaik.remember.feature.notes.editor

import dev.chaik.remember.core.data.protectionAfterMove
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chaik.remember.app.AppContainer
import dev.chaik.remember.core.editor.EditorState
import dev.chaik.remember.core.model.Note
import dev.chaik.remember.core.model.NoteFolder
import dev.chaik.remember.core.model.Tag
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Редактор заметки: заголовок, текст с фото, теги, папка, закрепление.
 *
 * Сохраняется сам: через [AUTOSAVE_DELAY_MS] после изменения, при уходе в фон
 * и при закрытии экрана (в фоне приложения — даже если экран уже исчез).
 * Новая заметка появляется в базе, только когда в ней что-то написано.
 *
 * @param noteId 0 — новая заметка
 * @param newFolderId папка для новой заметки
 */
class NoteEditorViewModel(
    private val container: AppContainer,
    noteId: Long,
    newFolderId: Long?,
) : ViewModel() {
    private val notes = container.noteRepository
    private val photos = container.photoStorage

    val editor = EditorState()

    var title by mutableStateOf("")
    var tags by mutableStateOf<List<Tag>>(emptyList())
        private set
    var folderId by mutableStateOf(newFolderId)
        private set
    var pinned by mutableStateOf(false)
        private set
    var isProtected by mutableStateOf(false)
        private set

    /** Заметка в защищённой папке: открывается PIN-ом папки, своей защиты у неё нет. */
    var folderProtected by mutableStateOf(false)
        private set

    var isLoaded by mutableStateOf(false)
        private set
    var isImporting by mutableStateOf(false)
        private set
    var failedImports by mutableIntStateOf(0)
        private set

    /** Заметку удалили — больше ничего не сохраняем. */
    private var deleted = false
    private var id = noteId
    private var saved: Snapshot? = null
    private val lock = Mutex()

    val allTags: StateFlow<List<Tag>> =
        container.tagRepository.observeForNotes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders: StateFlow<List<NoteFolder>> =
        notes.observeFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Фото удалили в галерее — убрать и из открытой заметки, чтобы она не вернула ссылку
        viewModelScope.launch { container.photoLibrary.removed.collect(editor::removePhotos) }
        viewModelScope.launch {
            if (noteId != 0L) {
                notes.get(noteId)?.let { note ->
                    title = note.title
                    editor.load(note.text)
                    tags = note.tags
                    folderId = note.folderId
                    pinned = note.pinned
                    isProtected = note.isProtected
                    editor.applyPhotoSizes(photos.photos(editor.photoIds))
                }
            }
            folderProtected = notes.isFolderProtected(folderId)
            saved = snapshot()
            isLoaded = true
            autosave()
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun autosave() {
        snapshotFlow { snapshot() }
            .drop(1)
            .debounce(AUTOSAVE_DELAY_MS)
            .collect { save(it) }
    }

    // Теги

    fun addTag(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val tag = container.tagRepository.getOrCreate(name)
            if (tags.none { it.id == tag.id }) tags = tags + tag
        }
    }

    fun toggleTag(tag: Tag) {
        tags = if (tags.any { it.id == tag.id }) tags.filterNot { it.id == tag.id } else tags + tag
    }

    fun removeTag(tag: Tag) {
        tags = tags.filterNot { it.id == tag.id }
    }

    // Папка и закрепление

    fun togglePinned() {
        pinned = !pinned
    }

    fun changeProtected(value: Boolean) {
        isProtected = value
    }

    /** Перенести в папку; защита — по [protectionAfterMove], как и при переносе из списка. */
    fun moveToFolder(folderId: Long?) {
        val to = folders.value.firstOrNull { it.id == folderId }?.isProtected == true
        isProtected = protectionAfterMove(isProtected, folderProtected, to)
        folderProtected = to
        this.folderId = folderId
    }

    // Фото

    /**
     * Добавить выбранные фото и видео: под каждое сразу встаёт заглушка с ходом загрузки,
     * готовые появляются по одному, не дожидаясь остальных.
     */
    fun addPhotos(uris: List<Uri>) {
        // Выбор отменили — beginImport сбросит и цель «добавить в галерею»
        val handle = editor.beginImport(uris.size) ?: return
        viewModelScope.launch {
            isImporting = true
            var failed = 0
            uris.zip(handle.items).forEach { (uri, item) ->
                editor.setImportProgress(item, 0f)
                val photo = runCatching { photos.import(uri) { editor.setImportProgress(item, it) } }
                    .onFailure { Log.w(TAG, "Не удалось добавить фото $uri", it) }
                    .getOrNull()
                if (photo == null) failed++
                editor.finishImport(handle, item, photo)
            }
            failedImports = failed
            isImporting = false
        }
    }

    fun consumeImportError() {
        failedImports = 0
    }

    // Сохранение и удаление

    /** Сохранить сейчас (уход в фон). */
    fun flush() {
        val snapshot = snapshot()
        container.appScope.launch { save(snapshot) }
    }

    fun delete(onDone: () -> Unit) {
        deleted = true
        val noteId = id
        container.appScope.launch { if (noteId != 0L) notes.delete(noteId) }
        onDone()
    }

    override fun onCleared() {
        // viewModelScope уже отменён — дописываем в фоне приложения
        if (isLoaded) flush()
    }

    private fun snapshot() = Snapshot(title, editor.markdown, tags, folderId, pinned, isProtected)

    private suspend fun save(snapshot: Snapshot) = lock.withLock {
        if (deleted || !isLoaded || snapshot == saved) return@withLock
        // Пустую новую заметку не создаём
        if (id == 0L && snapshot.isEmpty) return@withLock
        id = notes.save(
            Note(
                id = id,
                title = snapshot.title.trim(),
                text = snapshot.markdown,
                folderId = snapshot.folderId,
                pinned = snapshot.pinned,
                isProtected = snapshot.isProtected,
                tags = snapshot.tags,
            ),
        )
        saved = snapshot
    }

    private data class Snapshot(
        val title: String,
        val markdown: String,
        val tags: List<Tag>,
        val folderId: Long?,
        val pinned: Boolean,
        val isProtected: Boolean,
    ) {
        val isEmpty get() = title.isBlank() && markdown.isBlank() && tags.isEmpty()
    }

    private companion object {
        const val TAG = "NoteEditor"
        const val AUTOSAVE_DELAY_MS = 700L
    }
}
