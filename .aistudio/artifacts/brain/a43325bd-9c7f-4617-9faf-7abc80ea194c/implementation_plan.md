# Offline-First Android Notes & Tasks Application

A polished, high-performance, strictly offline Android application built with Kotlin, Jetpack Compose, and Room/SQLite. The app provides distraction-free note writing with checklists, a lightweight task manager with collapsible completed items, folders and tag organization, note pinning, a dedicated Trash bin for safe recovery, instant full-text search, and full dark/light theme support—operating completely without network access or cloud accounts.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following key decisions have been aligned based on your specifications and responses:

- **Note Pinning**: **Enabled**. Important notes can be pinned to the top of the notes list with a distinct visual pin indicator and dedicated sorting group.
- **Deletion & Recovery**: **Trash Folder (Soft-Delete)**. Deleted notes move to a dedicated Trash section with options to restore or permanently delete them. Also backed by an instant snackbar undo action.
- **Task Management**: **Lightweight with Collapsible Completed Section**. Dedicated task manager accessible from navigation with checkboxes, pending counts, and an expandable/collapsible history for completed tasks.
- **Local Storage Risk Notice**: **Persistent Drawer & Settings Notice**. The navigation drawer and app info dialog feature a clear, polite notice explaining that all notes are stored locally on-device and that clearing app storage or uninstalling will remove local records.
- **Network & Permissions**: **Zero Network Permissions**. No `INTERNET` permission in `AndroidManifest.xml`. No accounts, cloud APIs, telemetry, or external calls.

---

## 1. Overview & Core Concept

### What It Does
An all-in-one personal workspace designed exclusively for native Android devices. Users can capture thoughts, write plain-text notes, embed checklists, organize documents into folders and tags, pin priority items, track actionable tasks, and search their library instantly—even in airplane mode or remote locations.

### Target Audience & Persona
Users seeking a dependable, privacy-first notebook and daily task planner with zero latency, zero cloud tracking, and guaranteed offline reliability.

### Key Value
- **100% Offline Durability**: Every note, task, folder, and tag is stored in an encrypted/private SQLite database managed via Android Jetpack Room.
- **Distraction-Free Editing**: Fast note creation, clean typography, autosave on every keystroke, and undo/redo support.
- **Fast Organization**: Fluid navigation drawer with folder filtering, tag chips, recent notes carousel/list, and search.

---

## 2. User Experience & Visual Design

### Key User Flows

```
┌─────────────────────────────────────────────────────────────┐
│                       MAIN SCREEN                           │
│  [≡ Drawer]    [🔍 Search Notes & Tasks]      [☼ Theme]     │
├─────────────────────────────────────────────────────────────┤
│  Tabs: [📝 Notes]   [✓ Tasks]                              │
├─────────────────────────────────────────────────────────────┤
│  Active Folder / Tag Chips: [All] [📁 Work] [🏷 Project]   │
├─────────────────────────────────────────────────────────────┤
│  📌 PINNED NOTES                                            │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Project Architecture Notes                📌 Oct 23   │  │
│  │ Plain text excerpt...                                 │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                             │
│  📄 ALL NOTES (Chronological)                               │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Grocery & Supply List                    ☑ 2/5  Oct 22│  │
│  │ [✓] Oats   [ ] Coffee Beans...                        │  │
│  └───────────────────────────────────────────────────────┘  │
│                                           ┌──────────────┐  │
│                                           │ ＋ New Note  │  │
│                                           └──────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

1. **Quick Note Creation**: Tap the floating action button (FAB) or quick-entry bar to instantly open the editor.
2. **Note Editor**:
   - Clean top app bar with Back button, Undo / Redo controls, Pin toggle, Folder picker, and Note Options (Move to Trash, Add Tag, Switch between Plain Text / Checklist).
   - Minimalist Title field with auto-expansion.
   - Body area supporting continuous plain text writing or interactive checklists with strikethrough upon check.
   - Unobtrusive footer displaying subtle word count and last modified timestamp (no distraction, no ticking clocks).
   - **Autosave**: Content persists continuously to Room with debounce, and immediately flushes on back press or app pause.
3. **Task Manager View**:
   - Clean list with quick task input field: `[ + Add a new task... ] [Add]`.
   - Active tasks displayed with standard M3 checkboxes and swipe-to-delete or tap-to-edit.
   - Collapsible **Completed** accordion section displaying total completed count with clear/restore affordances.
4. **Navigation Drawer**:
   - Access to All Notes, Trash Bin, Folders management, Tags management, and App Info / Storage Disclaimer.
5. **Trash Screen**:
   - Lists soft-deleted notes with deletion date.
   - Per-note options: "Restore Note" or "Delete Permanently", with an "Empty Trash" global option.

### Visual Identity & Theme
- **Design Language**: Material Design 3 (M3) with rich tonal elevation, rounded containers (16dp-24dp), and expressive typography.
- **Color Palette**:
  - *Light Theme*: Warm slate/sand surface (`#FAF9F6`), primary deep indigo (`#2D3748`), container tones (`#EDF2F7`), subtle accents.
  - *Dark Theme*: Deep charcoal background (`#121417`), dark surface elevation (`#1E2228`), muted white primary text (`#E2E8F0`), slate blue accents (`#63B3ED`).
- **Typography**: Scalable typography using Material 3 `Typography` tokens (titleLarge, bodyMedium, labelSmall in `sp`).
- **Interactive Feedback**: Ripple effects on all card selections, animated transitions between checklist items, and smooth snackbar prompts for restore/undo.

---

## 3. Key Product Decisions & Trade-Offs

| Decision | Chosen Approach | Why & Alternatives Considered |
| :--- | :--- | :--- |
| **Persistence Engine** | Room Database (SQLite) with KSP | Native, highly optimized on-device relational storage with atomic transactions. Zero remote dependencies. Alternative raw SQLite was rejected due to lack of type safety and compile-time verification. |
| **Network Access** | Complete exclusion (`no INTERNET`) | Assures maximum privacy, removes security surface, and guarantees that the application works 100% offline under all circumstances. |
| **Deletion Mechanism** | Soft-Delete (`isDeleted = true`) into Trash | Prevents accidental data loss while giving users control over irreversible deletion. Backed by quick snackbar Undo. |
| **Autosave Strategy** | Reactive debounced flow + lifecycle hook | Notes automatically persist during typing without lagging the UI thread, and flush immediately on `onPause`/`BackHandler`. |
| **Checklist Format** | Hybrid parsed checklist items & JSON list | Stored directly within SQLite with task-item relationships, allowing seamless toggling, reordering, and instant rendering. |
| **Undo / Redo** | Stack-based state history in Editor ViewModel | Allows undoing text additions and checklist changes without storing unbounded database snapshots. |

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                        │
│  ┌─────────────────────────┐  ┌───────────────────────┐  ┌───────────┐ │
│  │ MainScreen (Notes/Tasks)│  │ NoteEditorScreen      │  │TrashScreen│ │
│  └───────────┬─────────────┘  └───────────┬───────────┘  └─────┬─────┘ │
└──────────────┼────────────────────────────┼────────────────────┼───────┘
               ▼                            ▼                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                         MVVM ViewModels Layer                          │
│  ┌─────────────────────────┐  ┌───────────────────────┐  ┌───────────┐ │
│  │ NotesViewModel          │  │ NoteEditorViewModel   │  │TasksVM    │ │
│  └───────────┬─────────────┘  └───────────┬───────────┘  └─────┬─────┘ │
└──────────────┼────────────────────────────┼────────────────────┼───────┘
               ▼                            ▼                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        Repository Layer                                │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ AppRepository (Coordinates DB operations, filtering, search)     │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
└─────────────────────────────────────┼──────────────────────────────────┘
                                      ▼
┌────────────────────────────────────────────────────────────────────────┐
│                         Room Database Layer                            │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ AppDatabase (version = 1, exportSchema = false)                  │  │
│  ├─────────────────┬────────────────┬────────────────┬──────────────┤  │
│  │ NoteDao         │ TaskDao        │ FolderDao      │ TagDao       │  │
│  └─────────────────┴────────────────┴────────────────┴──────────────┘  │
│                                     ▼                                  │
│                       Local SQLite Storage (App Sandboxed)             │
└────────────────────────────────────────────────────────────────────────┘
```

### Room Database Schema

1. **`NoteEntity`**:
   - `id: Long` (Auto-generated Primary Key)
   - `title: String`
   - `content: String`
   - `isChecklist: Boolean`
   - `checklistItemsJson: String` (Serialized JSON of checklist items: `{id, text, isChecked}`)
   - `folderId: Long?` (Nullable foreign key)
   - `tags: String` (Comma-separated tag names for fast indexing and local filtering)
   - `isPinned: Boolean`
   - `isDeleted: Boolean` (Trash flag)
   - `createdAt: Long` (Timestamp ms)
   - `updatedAt: Long` (Timestamp ms)

2. **`TaskEntity`**:
   - `id: Long` (Auto-generated Primary Key)
   - `title: String`
   - `isCompleted: Boolean`
   - `priority: Int` (0 = Normal, 1 = High)
   - `createdAt: Long`
   - `completedAt: Long?`

3. **`FolderEntity`**:
   - `id: Long` (Auto-generated Primary Key)
   - `name: String` (Unique)
   - `colorHex: String`
   - `createdAt: Long`

4. **`TagEntity`**:
   - `id: Long` (Auto-generated Primary Key)
   - `name: String` (Unique)

### Data Flow & Reactive State
- Room DAOs expose `Flow<List<NoteEntity>>` and `Flow<List<TaskEntity>>`.
- Queries automatically emit updated lists whenever records are added, edited, pinned, or moved to trash.
- Full-text search executes SQLite `LIKE %query%` or indexed text matches across both title and content entirely on-device with zero delay.

---

## 5. APK Build, Environment Verification & Next Steps

1. **Build Environment**: Android SDK with Kotlin DSL (`build.gradle.kts`) and Room compiler (`ksp`).
2. **Verification Protocol**:
   - Verify zero network permissions in `AndroidManifest.xml`.
   - Run compilation check using `compile_applet`.
   - Execute unit/Robolectric test checks for Room migrations and CRUD operations.
3. **Execution**: Proceed with clean implementation upon user approval.
