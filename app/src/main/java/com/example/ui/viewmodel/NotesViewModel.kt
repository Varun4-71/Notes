package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.FolderEntity
import com.example.data.model.NoteEntity
import com.example.data.model.TagEntity
import com.example.data.model.TaskEntity
import com.example.data.repository.NotesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MainTab {
    NOTES,
    TASKS,
    TRASH
}

data class NotesUiState(
    val currentTab: MainTab = MainTab.NOTES,
    val searchQuery: String = "",
    val selectedFolderId: Long? = null,
    val selectedTag: String? = null,
    val isCompletedTasksExpanded: Boolean = true,
    val isStorageNoticeDismissed: Boolean = false,
    val showStorageInfoDialog: Boolean = false,
    val showAddFolderDialog: Boolean = false,
    val showAddTagDialog: Boolean = false
)

class NotesViewModel(
    private val repository: NotesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    // Base flows from repository
    val allFolders: StateFlow<List<FolderEntity>> = repository.allFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTags: StateFlow<List<TagEntity>> = repository.allTags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashNotes: StateFlow<List<NoteEntity>> = repository.trashNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingTasks: StateFlow<List<TaskEntity>> = repository.pendingTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasks: StateFlow<List<TaskEntity>> = repository.completedTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered notes based on tab, search query, selected folder, and selected tag
    val filteredNotes: StateFlow<List<NoteEntity>> = combine(
        repository.allActiveNotes,
        _uiState
    ) { notes, state ->
        notes.filter { note ->
            val matchesSearch = if (state.searchQuery.isBlank()) {
                true
            } else {
                note.title.contains(state.searchQuery, ignoreCase = true) ||
                        note.content.contains(state.searchQuery, ignoreCase = true) ||
                        note.tags.contains(state.searchQuery, ignoreCase = true)
            }

            val matchesFolder = if (state.selectedFolderId == null) {
                true
            } else {
                note.folderId == state.selectedFolderId
            }

            val matchesTag = if (state.selectedTag == null) {
                true
            } else {
                note.tags.split(",").map { it.trim() }.contains(state.selectedTag)
            }

            matchesSearch && matchesFolder && matchesTag
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Split into pinned and unpinned notes
    val pinnedNotes: StateFlow<List<NoteEntity>> = combine(filteredNotes) { notesArray ->
        notesArray[0].filter { it.isPinned }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val otherNotes: StateFlow<List<NoteEntity>> = combine(filteredNotes) { notesArray ->
        notesArray[0].filter { !it.isPinned }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Last deleted note id for quick undo
    var lastSoftDeletedNoteId: Long? = null
        private set

    fun setTab(tab: MainTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun selectFolder(folderId: Long?) {
        _uiState.value = _uiState.value.copy(
            selectedFolderId = if (_uiState.value.selectedFolderId == folderId) null else folderId
        )
    }

    fun selectTag(tag: String?) {
        _uiState.value = _uiState.value.copy(
            selectedTag = if (_uiState.value.selectedTag == tag) null else tag
        )
    }

    fun toggleCompletedTasksExpanded() {
        _uiState.value = _uiState.value.copy(
            isCompletedTasksExpanded = !_uiState.value.isCompletedTasksExpanded
        )
    }

    fun setShowStorageInfoDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showStorageInfoDialog = show)
    }

    fun setShowAddFolderDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAddFolderDialog = show)
    }

    fun setShowAddTagDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAddTagDialog = show)
    }

    fun togglePin(note: NoteEntity) {
        viewModelScope.launch {
            repository.togglePin(note.id, note.isPinned)
        }
    }

    fun moveToTrash(noteId: Long) {
        viewModelScope.launch {
            lastSoftDeletedNoteId = noteId
            repository.moveToTrash(noteId)
        }
    }

    fun undoMoveToTrash() {
        lastSoftDeletedNoteId?.let { id ->
            viewModelScope.launch {
                repository.restoreFromTrash(id)
                lastSoftDeletedNoteId = null
            }
        }
    }

    fun restoreFromTrash(noteId: Long) {
        viewModelScope.launch {
            repository.restoreFromTrash(noteId)
        }
    }

    fun deletePermanently(noteId: Long) {
        viewModelScope.launch {
            repository.deletePermanently(noteId)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
        }
    }

    // Tasks
    fun addTask(title: String, isHighPriority: Boolean = false) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.saveTask(
                TaskEntity(
                    title = title.trim(),
                    priority = if (isHighPriority) 1 else 0
                )
            )
        }
    }

    fun toggleTask(taskId: Long, currentCompleted: Boolean) {
        viewModelScope.launch {
            repository.toggleTaskCompletion(taskId, !currentCompleted)
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            repository.deleteTaskById(taskId)
        }
    }

    fun clearCompletedTasks() {
        viewModelScope.launch {
            repository.clearCompletedTasks()
        }
    }

    // Folders & Tags
    fun createFolder(name: String, colorHex: String = "#3B82F6") {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createFolder(name.trim(), colorHex)
            setShowAddFolderDialog(false)
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            repository.deleteFolder(folderId)
            if (_uiState.value.selectedFolderId == folderId) {
                _uiState.value = _uiState.value.copy(selectedFolderId = null)
            }
        }
    }

    fun createTag(name: String) {
        if (name.isBlank()) return
        val clean = name.trim().replace("#", "")
        viewModelScope.launch {
            repository.createTag(clean)
            setShowAddTagDialog(false)
        }
    }

    fun deleteTag(tagId: Long) {
        viewModelScope.launch {
            repository.deleteTag(tagId)
        }
    }
}

class NotesViewModelFactory(private val repository: NotesRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NotesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NotesViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
