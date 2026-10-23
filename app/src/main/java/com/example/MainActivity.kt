package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.AppDatabase
import com.example.data.repository.NotesRepository
import com.example.ui.components.AddFolderDialog
import com.example.ui.components.AddTagDialog
import com.example.ui.components.AppDrawerContent
import com.example.ui.components.StorageDisclaimerDialog
import com.example.ui.screens.MainNotesScreen
import com.example.ui.screens.NoteEditorScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.TrashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.NoteEditorViewModel
import com.example.ui.viewmodel.NoteEditorViewModelFactory
import com.example.ui.viewmodel.NotesViewModel
import com.example.ui.viewmodel.NotesViewModelFactory
import kotlinx.coroutines.launch

sealed interface AppScreen {
    object Main : AppScreen
    data class Editor(val noteId: Long?) : AppScreen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getInstance(applicationContext)
        val repository = NotesRepository(
            noteDao = database.noteDao(),
            taskDao = database.taskDao(),
            folderDao = database.folderDao(),
            tagDao = database.tagDao()
        )

        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkTheme by remember { mutableStateOf(systemDark) }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                NotesAppContent(
                    repository = repository,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme }
                )
            }
        }
    }
}

@Composable
fun NotesAppContent(
    repository: NotesRepository,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val notesViewModel: NotesViewModel = viewModel(
        factory = NotesViewModelFactory(repository)
    )

    val uiState by notesViewModel.uiState.collectAsStateWithLifecycle()
    val pinnedNotes by notesViewModel.pinnedNotes.collectAsStateWithLifecycle()
    val otherNotes by notesViewModel.otherNotes.collectAsStateWithLifecycle()
    val pendingTasks by notesViewModel.pendingTasks.collectAsStateWithLifecycle()
    val completedTasks by notesViewModel.completedTasks.collectAsStateWithLifecycle()
    val trashNotes by notesViewModel.trashNotes.collectAsStateWithLifecycle()
    val folders by notesViewModel.allFolders.collectAsStateWithLifecycle()
    val tags by notesViewModel.allTags.collectAsStateWithLifecycle()

    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Main) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog handlers
    if (uiState.showAddFolderDialog) {
        AddFolderDialog(
            onDismiss = { notesViewModel.setShowAddFolderDialog(false) },
            onConfirm = { name, colorHex -> notesViewModel.createFolder(name, colorHex) }
        )
    }

    if (uiState.showAddTagDialog) {
        AddTagDialog(
            onDismiss = { notesViewModel.setShowAddTagDialog(false) },
            onConfirm = { name -> notesViewModel.createTag(name) }
        )
    }

    if (uiState.showStorageInfoDialog) {
        StorageDisclaimerDialog(
            onDismiss = { notesViewModel.setShowStorageInfoDialog(false) }
        )
    }

    when (val screen = currentScreen) {
        is AppScreen.Editor -> {
            val editorViewModel: NoteEditorViewModel = viewModel(
                key = "editor_${screen.noteId ?: 0L}",
                factory = NoteEditorViewModelFactory(repository, screen.noteId)
            )
            val editorState by editorViewModel.uiState.collectAsStateWithLifecycle()

            NoteEditorScreen(
                state = editorState,
                folders = folders,
                tags = tags,
                onTitleChange = { editorViewModel.onTitleChange(it) },
                onContentChange = { editorViewModel.onContentChange(it) },
                onToggleMode = { editorViewModel.toggleMode(it) },
                onAddChecklistItem = { editorViewModel.addChecklistItem(it) },
                onUpdateChecklistItem = { index, text -> editorViewModel.updateChecklistItem(index, text) },
                onToggleChecklistItem = { editorViewModel.toggleChecklistItem(it) },
                onRemoveChecklistItem = { editorViewModel.removeChecklistItem(it) },
                onTogglePin = { editorViewModel.togglePin() },
                onSetFolder = { id, name -> editorViewModel.setFolder(id, name) },
                onToggleTag = { editorViewModel.toggleTag(it) },
                onUndo = { editorViewModel.undo() },
                onRedo = { editorViewModel.redo() },
                onSaveAndExit = {
                    editorViewModel.saveNoteNow()
                    currentScreen = AppScreen.Main
                },
                onMoveToTrash = {
                    editorViewModel.moveToTrash {
                        currentScreen = AppScreen.Main
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Note moved to trash",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                notesViewModel.undoMoveToTrash()
                            }
                        }
                    }
                }
            )
        }

        is AppScreen.Main -> {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    AppDrawerContent(
                        currentTab = uiState.currentTab,
                        onTabSelected = { tab ->
                            notesViewModel.setTab(tab)
                            scope.launch { drawerState.close() }
                        },
                        trashCount = trashNotes.size,
                        folders = folders,
                        selectedFolderId = uiState.selectedFolderId,
                        onSelectFolder = {
                            notesViewModel.selectFolder(it)
                            scope.launch { drawerState.close() }
                        },
                        onOpenAddFolder = { notesViewModel.setShowAddFolderDialog(true) },
                        onDeleteFolder = { notesViewModel.deleteFolder(it) },
                        tags = tags,
                        selectedTag = uiState.selectedTag,
                        onSelectTag = {
                            notesViewModel.selectTag(it)
                            scope.launch { drawerState.close() }
                        },
                        onOpenAddTag = { notesViewModel.setShowAddTagDialog(true) },
                        onDeleteTag = { notesViewModel.deleteTag(it) },
                        onOpenStorageInfo = {
                            notesViewModel.setShowStorageInfoDialog(true)
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    when (uiState.currentTab) {
                        MainTab.NOTES -> {
                            MainNotesScreen(
                                searchQuery = uiState.searchQuery,
                                onSearchQueryChange = { notesViewModel.setSearchQuery(it) },
                                pinnedNotes = pinnedNotes,
                                otherNotes = otherNotes,
                                folders = folders,
                                selectedFolderId = uiState.selectedFolderId,
                                onSelectFolder = { notesViewModel.selectFolder(it) },
                                selectedTag = uiState.selectedTag,
                                onSelectTag = { notesViewModel.selectTag(it) },
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                onOpenStorageInfo = { notesViewModel.setShowStorageInfoDialog(true) },
                                onToggleTheme = onToggleTheme,
                                isDarkTheme = isDarkTheme,
                                onNoteClick = { noteId ->
                                    currentScreen = AppScreen.Editor(noteId)
                                },
                                onTogglePin = { notesViewModel.togglePin(it) },
                                onMoveToTrash = { noteId ->
                                    notesViewModel.moveToTrash(noteId)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Note moved to trash",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            notesViewModel.undoMoveToTrash()
                                        }
                                    }
                                },
                                onCreateNote = {
                                    currentScreen = AppScreen.Editor(null)
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }

                        MainTab.TASKS -> {
                            TasksScreen(
                                pendingTasks = pendingTasks,
                                completedTasks = completedTasks,
                                isCompletedExpanded = uiState.isCompletedTasksExpanded,
                                onToggleCompletedExpanded = { notesViewModel.toggleCompletedTasksExpanded() },
                                onAddTask = { title, isHighPriority ->
                                    notesViewModel.addTask(title, isHighPriority)
                                },
                                onToggleTask = { id, completed ->
                                    notesViewModel.toggleTask(id, completed)
                                },
                                onDeleteTask = { notesViewModel.deleteTask(it) },
                                onClearCompleted = { notesViewModel.clearCompletedTasks() },
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }

                        MainTab.TRASH -> {
                            TrashScreen(
                                trashNotes = trashNotes,
                                onRestoreNote = { notesViewModel.restoreFromTrash(it) },
                                onDeletePermanently = { notesViewModel.deletePermanently(it) },
                                onEmptyTrash = { notesViewModel.emptyTrash() },
                                onOpenDrawer = { scope.launch { drawerState.open() } },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}
