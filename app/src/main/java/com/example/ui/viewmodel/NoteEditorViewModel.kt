package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChecklistItem
import com.example.data.model.NoteEntity
import com.example.data.repository.NotesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EditorSnapshot(
    val title: String,
    val content: String,
    val isChecklist: Boolean,
    val checklistItems: List<ChecklistItem>
)

data class NoteEditorUiState(
    val noteId: Long = 0L,
    val title: String = "",
    val content: String = "",
    val isChecklist: Boolean = false,
    val checklistItems: List<ChecklistItem> = emptyList(),
    val folderId: Long? = null,
    val folderName: String? = null,
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val lastModifiedFormatted: String = "",
    val isSaved: Boolean = true,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val wordCount: Int = 0,
    val charCount: Int = 0
)

class NoteEditorViewModel(
    private val repository: NotesRepository,
    private val initialNoteId: Long?
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    private val undoStack = mutableListOf<EditorSnapshot>()
    private val redoStack = mutableListOf<EditorSnapshot>()
    private var isUndoingOrRedoing = false

    private var autosaveJob: Job? = null

    init {
        if (initialNoteId != null && initialNoteId != 0L) {
            loadNote(initialNoteId)
        } else {
            // Fresh note
            recordSnapshot(EditorSnapshot("", "", false, emptyList()))
            updateCounts()
        }
    }

    private fun loadNote(id: Long) {
        viewModelScope.launch {
            val note = repository.getNoteByIdSync(id)
            if (note != null) {
                val items = ChecklistItem.jsonToList(note.checklistItemsJson)
                val formattedDate = formatDate(note.updatedAt)
                _uiState.value = NoteEditorUiState(
                    noteId = note.id,
                    title = note.title,
                    content = note.content,
                    isChecklist = note.isChecklist,
                    checklistItems = items,
                    folderId = note.folderId,
                    folderName = note.folderName,
                    tags = if (note.tags.isBlank()) emptyList() else note.tags.split(",").map { it.trim() },
                    isPinned = note.isPinned,
                    lastModifiedFormatted = formattedDate,
                    isSaved = true
                )
                undoStack.clear()
                redoStack.clear()
                recordSnapshot(EditorSnapshot(note.title, note.content, note.isChecklist, items))
                updateCounts()
            }
        }
    }

    private fun recordSnapshot(snapshot: EditorSnapshot) {
        if (isUndoingOrRedoing) return
        if (undoStack.isNotEmpty() && undoStack.last() == snapshot) return

        undoStack.add(snapshot)
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
        updateUndoRedoAvailability()
    }

    private fun updateUndoRedoAvailability() {
        _uiState.value = _uiState.value.copy(
            canUndo = undoStack.size > 1,
            canRedo = redoStack.isNotEmpty()
        )
    }

    fun onTitleChange(newTitle: String) {
        _uiState.value = _uiState.value.copy(title = newTitle, isSaved = false)
        updateCounts()
        scheduleAutosave()
    }

    fun onContentChange(newContent: String) {
        _uiState.value = _uiState.value.copy(content = newContent, isSaved = false)
        updateCounts()
        scheduleAutosave()
    }

    fun toggleMode(isChecklist: Boolean) {
        val currentItems = if (isChecklist && _uiState.value.checklistItems.isEmpty()) {
            if (_uiState.value.content.isNotBlank()) {
                _uiState.value.content.lines().filter { it.isNotBlank() }.map {
                    ChecklistItem(text = it.trim(), isChecked = false)
                }
            } else {
                listOf(ChecklistItem(text = "", isChecked = false))
            }
        } else {
            _uiState.value.checklistItems
        }

        _uiState.value = _uiState.value.copy(
            isChecklist = isChecklist,
            checklistItems = currentItems,
            isSaved = false
        )
        recordCurrentState()
        scheduleAutosave()
    }

    fun addChecklistItem(text: String = "") {
        val updated = _uiState.value.checklistItems.toMutableList()
        updated.add(ChecklistItem(text = text, isChecked = false))
        _uiState.value = _uiState.value.copy(checklistItems = updated, isSaved = false)
        recordCurrentState()
        scheduleAutosave()
    }

    fun updateChecklistItem(index: Int, text: String) {
        if (index in _uiState.value.checklistItems.indices) {
            val updated = _uiState.value.checklistItems.toMutableList()
            updated[index] = updated[index].copy(text = text)
            _uiState.value = _uiState.value.copy(checklistItems = updated, isSaved = false)
            scheduleAutosave()
        }
    }

    fun toggleChecklistItem(index: Int) {
        if (index in _uiState.value.checklistItems.indices) {
            val updated = _uiState.value.checklistItems.toMutableList()
            val current = updated[index]
            updated[index] = current.copy(isChecked = !current.isChecked)
            _uiState.value = _uiState.value.copy(checklistItems = updated, isSaved = false)
            recordCurrentState()
            scheduleAutosave()
        }
    }

    fun removeChecklistItem(index: Int) {
        if (index in _uiState.value.checklistItems.indices) {
            val updated = _uiState.value.checklistItems.toMutableList()
            updated.removeAt(index)
            _uiState.value = _uiState.value.copy(checklistItems = updated, isSaved = false)
            recordCurrentState()
            scheduleAutosave()
        }
    }

    fun togglePin() {
        val newPinned = !_uiState.value.isPinned
        _uiState.value = _uiState.value.copy(isPinned = newPinned, isSaved = false)
        saveNoteNow()
    }

    fun setFolder(folderId: Long?, folderName: String?) {
        _uiState.value = _uiState.value.copy(
            folderId = folderId,
            folderName = folderName,
            isSaved = false
        )
        saveNoteNow()
    }

    fun toggleTag(tagName: String) {
        val cleanTag = tagName.trim().replace("#", "")
        val currentTags = _uiState.value.tags.toMutableList()
        if (currentTags.contains(cleanTag)) {
            currentTags.remove(cleanTag)
        } else {
            currentTags.add(cleanTag)
        }
        _uiState.value = _uiState.value.copy(tags = currentTags, isSaved = false)
        saveNoteNow()
    }

    fun undo() {
        if (undoStack.size > 1) {
            isUndoingOrRedoing = true
            val current = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(current)
            val previous = undoStack.last()

            _uiState.value = _uiState.value.copy(
                title = previous.title,
                content = previous.content,
                isChecklist = previous.isChecklist,
                checklistItems = previous.checklistItems,
                isSaved = false
            )
            updateCounts()
            isUndoingOrRedoing = false
            updateUndoRedoAvailability()
            scheduleAutosave()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            isUndoingOrRedoing = true
            val target = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(target)

            _uiState.value = _uiState.value.copy(
                title = target.title,
                content = target.content,
                isChecklist = target.isChecklist,
                checklistItems = target.checklistItems,
                isSaved = false
            )
            updateCounts()
            isUndoingOrRedoing = false
            updateUndoRedoAvailability()
            scheduleAutosave()
        }
    }

    private fun recordCurrentState() {
        val current = _uiState.value
        recordSnapshot(
            EditorSnapshot(
                title = current.title,
                content = current.content,
                isChecklist = current.isChecklist,
                checklistItems = current.checklistItems
            )
        )
    }

    private fun updateCounts() {
        val state = _uiState.value
        val fullText = if (state.isChecklist) {
            state.checklistItems.joinToString(" ") { it.text }
        } else {
            state.content
        }
        val words = fullText.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size
        val chars = fullText.length
        _uiState.value = _uiState.value.copy(wordCount = words, charCount = chars)
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(800) // 800ms debounce
            recordCurrentState()
            saveNoteNow()
        }
    }

    fun saveNoteNow() {
        val state = _uiState.value
        // Only save if there is a title or content or checklist items
        val hasContent = state.title.isNotBlank() ||
                state.content.isNotBlank() ||
                state.checklistItems.any { it.text.isNotBlank() }

        if (!hasContent && state.noteId == 0L) {
            return
        }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val noteEntity = NoteEntity(
                id = state.noteId,
                title = state.title.trim(),
                content = state.content,
                isChecklist = state.isChecklist,
                checklistItemsJson = ChecklistItem.listToJson(state.checklistItems),
                folderId = state.folderId,
                folderName = state.folderName,
                tags = state.tags.joinToString(","),
                isPinned = state.isPinned,
                isDeleted = false,
                updatedAt = now
            )
            val newId = repository.saveNote(noteEntity)
            _uiState.value = _uiState.value.copy(
                noteId = newId,
                isSaved = true,
                lastModifiedFormatted = formatDate(now)
            )
        }
    }

    fun moveToTrash(onCompleted: () -> Unit) {
        val id = _uiState.value.noteId
        if (id != 0L) {
            viewModelScope.launch {
                repository.moveToTrash(id)
                onCompleted()
            }
        } else {
            onCompleted()
        }
    }

    private fun formatDate(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            ""
        }
    }
}

class NoteEditorViewModelFactory(
    private val repository: NotesRepository,
    private val noteId: Long?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteEditorViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NoteEditorViewModel(repository, noteId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
