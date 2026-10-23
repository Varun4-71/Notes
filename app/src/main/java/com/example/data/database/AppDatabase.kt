package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.FolderDao
import com.example.data.dao.NoteDao
import com.example.data.dao.TagDao
import com.example.data.dao.TaskDao
import com.example.data.model.ChecklistItem
import com.example.data.model.FolderEntity
import com.example.data.model.NoteEntity
import com.example.data.model.TagEntity
import com.example.data.model.TaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        NoteEntity::class,
        TaskEntity::class,
        FolderEntity::class,
        TagEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun taskDao(): TaskDao
    abstract fun folderDao(): FolderDao
    abstract fun tagDao(): TagDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "offline_notes_db"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    seedInitialData(database)
                }
            }
        }

        private suspend fun seedInitialData(database: AppDatabase) {
            val personalFolderId = database.folderDao().insertFolder(
                FolderEntity(name = "Personal", colorHex = "#10B981")
            )
            val workFolderId = database.folderDao().insertFolder(
                FolderEntity(name = "Work", colorHex = "#3B82F6")
            )
            database.tagDao().insertTag(TagEntity(name = "Important"))
            database.tagDao().insertTag(TagEntity(name = "Ideas"))
            database.tagDao().insertTag(TagEntity(name = "Todo"))

            // Sample Welcome Note
            database.noteDao().insertNote(
                NoteEntity(
                    title = "Welcome to Offline Notes 🔒",
                    content = "This app is 100% offline-first. Your notes, tasks, folders, and tags never leave your device.\n\nKey features:\n• Create plain text notes or interactive checklists\n• Pin important notes to the top\n• Organize with folders and tags\n• Tasks manager with collapsible completed history\n• Safe Trash bin with instant recovery\n• Fast instant search across all your records\n\nTip: Data is stored securely on your local device storage. If you uninstall the app or clear app data, local notes will be removed.",
                    isPinned = true,
                    folderId = personalFolderId,
                    folderName = "Personal",
                    tags = "Important,Ideas"
                )
            )

            // Sample Checklist Note
            val checklist = listOf(
                ChecklistItem(text = "Try writing a new note", isChecked = true),
                ChecklistItem(text = "Toggle dark / light theme", isChecked = true),
                ChecklistItem(text = "Create a custom folder and tag", isChecked = false),
                ChecklistItem(text = "Add a quick daily task in Tasks tab", isChecked = false)
            )
            database.noteDao().insertNote(
                NoteEntity(
                    title = "Getting Started Checklist",
                    content = "",
                    isChecklist = true,
                    checklistItemsJson = ChecklistItem.listToJson(checklist),
                    isPinned = false,
                    folderId = personalFolderId,
                    folderName = "Personal",
                    tags = "Todo"
                )
            )

            // Sample Tasks
            database.taskDao().insertTask(
                TaskEntity(
                    title = "Plan week goals in Offline Notes",
                    isCompleted = false,
                    priority = 1
                )
            )
            database.taskDao().insertTask(
                TaskEntity(
                    title = "Explore app settings & offline features",
                    isCompleted = true,
                    completedAt = System.currentTimeMillis() - 3600000
                )
            )
        }
    }
}
