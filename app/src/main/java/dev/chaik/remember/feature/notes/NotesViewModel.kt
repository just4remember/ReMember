package dev.chaik.remember.feature.notes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.chaik.remember.core.data.NoteRepository
import dev.chaik.remember.core.model.NoteSummary
import dev.chaik.remember.core.model.NoteFolder
import dev.chaik.remember.core.model.Tag
import dev.chaik.remember.core.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import dev.chaik.remember.core.settings.NotesOrder
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Какие заметки показывать по папке. */
sealed interface FolderFilter {
    data object All : FolderFilter
    data object NoFolder : FolderFilter
    data class Folder(val id: Long) : FolderFilter
}

/** Список заметок: фильтры по папке и тегу, действия над папками и заметками. */
class NotesViewModel(private val notes: NoteRepository, settings: Flow<AppSettings>) : ViewModel() {

    var folderFilter by mutableStateOf<FolderFilter>(FolderFilter.All)
        private set

    /** Выбранные теги-фильтры: показываются заметки, где есть все они сразу. Пусто — без фильтра. */
    var tagFilter by mutableStateOf<Set<Long>>(emptySet())
        private set

    val folders: StateFlow<List<NoteFolder>> =
        notes.observeFolders().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Одна подписка на базу на всех: и для карточек, и для ленты тегов.
    // Lazily — данные остаются в памяти, пока жива ViewModel: вернулись на вкладку — карточки уже готовы
    // (с WhileSubscribed они выгружались через 5 с и перечитывались при каждом возвращении)
    private val allNotes = notes.observeSummaries()
        .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    /** Теги, которые есть хотя бы у одной заметки — для строки фильтра. */
    val usedTags: StateFlow<List<Tag>> = allNotes
        .map { list -> list.flatMap { it.tags }.distinctBy { it.id }.sortedBy { it.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Заметки после фильтров. Закреплённые всегда сверху, дальше — недавно изменённые (порядок из базы)
     * или, если так выбрано в «Страницах», по дате создания: новые сверху, правка порядок не меняет.
     */
    val visibleNotes: StateFlow<List<NoteSummary>?> =
        combine(
            allNotes,
            settings.map { it.notesOrder }.distinctUntilChanged(),
            snapshotFlow { folderFilter to tagFilter },
        ) { list, order, (folder, tags) ->
            val ordered = when (order) {
                NotesOrder.EDITED -> list
                NotesOrder.CREATED -> list.sortedWith(compareByDescending<NoteSummary> { it.pinned }.thenByDescending { it.createdAt })
                NotesOrder.MANUAL -> list.sortedWith(compareByDescending<NoteSummary> { it.pinned }.thenBy { it.sortOrder }.thenByDescending { it.createdAt })
            }
            ordered.filter { note ->
                val folderOk = when (folder) {
                    FolderFilter.All -> true
                    FolderFilter.NoFolder -> note.folderId == null
                    is FolderFilter.Folder -> note.folderId == folder.id
                }
                folderOk && note.tags.map { it.id }.containsAll(tags)
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** Папка для новой заметки — та, что открыта в фильтре. */
    val currentFolderId: Long? get() = (folderFilter as? FolderFilter.Folder)?.id

    fun selectFolder(filter: FolderFilter) {
        folderFilter = filter
    }

    fun toggleTagFilter(tag: Tag) {
        tagFilter = if (tag.id in tagFilter) tagFilter - tag.id else tagFilter + tag.id
    }

    fun clearTagFilter() {
        tagFilter = emptySet()
    }

    // Папки

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = notes.saveFolder(NoteFolder(name = name.trim(), sortOrder = folders.value.size))
            folderFilter = FolderFilter.Folder(id)
        }
    }

    fun renameFolder(folder: NoteFolder, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { notes.saveFolder(folder.copy(name = name.trim())) }
    }

    /** Удалить папку; её заметки остаются без папки. */
    fun deleteFolder(folder: NoteFolder) {
        if (folderFilter == FolderFilter.Folder(folder.id)) folderFilter = FolderFilter.All
        viewModelScope.launch { notes.deleteFolder(folder.id) }
    }

    // Заметки

    /**
     * Заметки перетащили: [ids] — новый порядок показанных. Скрытые фильтром идут следом
     * в своём прежнем ручном порядке. Включить ручной порядок — дело экрана (настройки).
     */
    fun reorder(ids: List<Long>) {
        viewModelScope.launch {
            val shown = ids.toSet()
            val rest = allNotes.first().filterNot { it.id in shown }.sortedBy { it.sortOrder }.map { it.id }
            notes.reorder(ids + rest)
        }
    }

    fun togglePinned(note: NoteSummary) {
        viewModelScope.launch { notes.setPinned(note.id, !note.pinned) }
    }

    fun moveToFolder(note: NoteSummary, folderId: Long?) {
        viewModelScope.launch { notes.moveToFolder(note.id, folderId) }
    }

    fun delete(note: NoteSummary) {
        viewModelScope.launch { notes.delete(note.id) }
    }
}
