package com.example.data.repository

import com.example.data.dao.FolderDao
import com.example.data.dao.NoteDao
import com.example.data.dao.TagDao
import com.example.data.dao.TaskDao
import com.example.data.model.FolderEntity
import com.example.data.model.NoteEntity
import com.example.data.model.TagEntity
import com.example.data.model.TaskEntity
import kotlinx.coroutines.flow.Flow

class NotesRepository(
    private val noteDao: NoteDao,
    private val taskDao: TaskDao,
    private val folderDao: FolderDao,
    private val tagDao: TagDao
) {
    // Notes
    val allActiveNotes: Flow<List<NoteEntity>> = noteDao.getAllActiveNotes()
    val trashNotes: Flow<List<NoteEntity>> = noteDao.getTrashNotes()

    fun getNoteById(id: Long): Flow<NoteEntity?> = noteDao.getNoteById(id)

    suspend fun getNoteByIdSync(id: Long): NoteEntity? = noteDao.getNoteByIdSync(id)

    fun searchNotes(query: String): Flow<List<NoteEntity>> = noteDao.searchNotes(query)

    suspend fun saveNote(note: NoteEntity): Long {
        return if (note.id == 0L) {
            noteDao.insertNote(note)
        } else {
            noteDao.updateNote(note)
            note.id
        }
    }

    suspend fun moveToTrash(id: Long) = noteDao.moveToTrash(id)

    suspend fun restoreFromTrash(id: Long) = noteDao.restoreFromTrash(id)

    suspend fun deletePermanently(id: Long) = noteDao.deletePermanently(id)

    suspend fun emptyTrash() = noteDao.emptyTrash()

    suspend fun togglePin(id: Long, currentPinState: Boolean) {
        noteDao.setPinned(id, !currentPinState)
    }

    suspend fun updateNoteFolder(id: Long, folderId: Long?, folderName: String?) {
        noteDao.updateFolder(id, folderId, folderName)
    }

    // Tasks
    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()
    val pendingTasks: Flow<List<TaskEntity>> = taskDao.getPendingTasks()
    val completedTasks: Flow<List<TaskEntity>> = taskDao.getCompletedTasks()

    suspend fun saveTask(task: TaskEntity): Long = taskDao.insertTask(task)

    suspend fun updateTask(task: TaskEntity) = taskDao.updateTask(task)

    suspend fun deleteTask(task: TaskEntity) = taskDao.deleteTask(task)

    suspend fun deleteTaskById(id: Long) = taskDao.deleteTaskById(id)

    suspend fun toggleTaskCompletion(id: Long, isCompleted: Boolean) {
        val completedAt = if (isCompleted) System.currentTimeMillis() else null
        taskDao.setTaskCompleted(id, isCompleted, completedAt)
    }

    suspend fun clearCompletedTasks() = taskDao.clearCompletedTasks()

    // Folders
    val allFolders: Flow<List<FolderEntity>> = folderDao.getAllFolders()

    suspend fun createFolder(name: String, colorHex: String): Long {
        return folderDao.insertFolder(FolderEntity(name = name, colorHex = colorHex))
    }

    suspend fun deleteFolder(id: Long) = folderDao.deleteFolder(id)

    // Tags
    val allTags: Flow<List<TagEntity>> = tagDao.getAllTags()

    suspend fun createTag(name: String): Long {
        return tagDao.insertTag(TagEntity(name = name))
    }

    suspend fun deleteTag(id: Long) = tagDao.deleteTag(id)
}
