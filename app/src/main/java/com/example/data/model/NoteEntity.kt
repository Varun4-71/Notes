package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["isDeleted"]),
        Index(value = ["isPinned"]),
        Index(value = ["updatedAt"]),
        Index(value = ["folderId"])
    ]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String = "",
    val content: String = "",
    val isChecklist: Boolean = false,
    val checklistItemsJson: String = "",
    val folderId: Long? = null,
    val folderName: String? = null,
    val tags: String = "", // Comma-separated tags
    val isPinned: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
