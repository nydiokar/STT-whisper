# Voice Input App Vision & Architecture

**Version**: 1.0
**Status**: Pre-IME Phase
**Last Updated**: 2025-10-11

---

## Table of Contents
1. [Core Concept](#core-concept)
2. [User Journey](#user-journey)
3. [UI/UX Design](#uiux-design)
4. [Database Architecture](#database-architecture)
5. [Technical Implementation](#technical-implementation)
6. [Future Evolution](#future-evolution)

---

## Core Concept

### What is This App?
A **standalone voice recording and transcription app** that will eventually evolve into a system-wide voice input keyboard (IME).

### Current Phase: Standalone App
Before implementing the IME functionality, we're building a fully-featured standalone app that:
- Records voice with a simple button press
- Transcribes speech using on-device AI (Whisper SMALL INT8)
- Stores transcriptions persistently with metadata
- Provides a clean, timeline-based interface to review recordings
- Works completely offline with APU acceleration

### Why Standalone First?
- **Test the core engine**: Validate Whisper performance, VAD, chunking strategy
- **Build user trust**: Let users experience the quality before system integration
- **Iterate UX**: Perfect the recording workflow without IME constraints
- **Data layer**: Establish robust database schema and recording management
- **Debugging**: Easier to test and fix issues in a contained app

---

## User Journey

### Primary Use Case
> **"I want to quickly capture my thoughts as voice notes and have them automatically transcribed for easy reference."**

### User Flow

```
1. Open App
   ↓
2. See Timeline of Past Recordings
   ↓
3. Tap Big Record Button (center bottom)
   ↓
4. Speak naturally
   ↓
5. Tap Button Again to Stop
   ↓
6. Transcription Appears in Timeline
   ↓
7. Tap Transcription Preview to View Full Text
```

### Example Scenarios

**Scenario 1: Quick Note**
- User opens app during meeting
- Taps record button
- Says: "Remember to follow up with Sarah about the Q3 budget"
- Taps stop
- Sees "Remember to follow up with Sarah..." in timeline
- Later: Taps to read full transcription, copy to email/notes

**Scenario 2: Brainstorming**
- User recording ideas for project
- Records 5 separate voice notes over 30 minutes
- Each appears in timeline with timestamp
- Can review all notes in chronological order
- Can search across all transcriptions

**Scenario 3: Long Recording**
- User records 2-minute thought process
- App processes in 20s chunks with overlap (invisible to user)
- Final transcription stitched seamlessly
- User sees complete text, unaware of backend chunking

---

## UI/UX Design

### Main Screen Layout

```
┌──────────────────────────────────────┐
│  ☰  Voice Notes         ⚙️  🧪      │  ← Header (menu, settings, test)
├──────────────────────────────────────┤
│                                      │
│  TODAY                               │  ← Timeline Section
│  ┌────────────────────────────────┐ │
│  │ 12:45 PM                     ⋮ │ │
│  │ Remember to follow up with... │ │  ← Recording Card
│  │ 45 seconds · 103 words       │ │
│  └────────────────────────────────┘ │
│                                      │
│  ┌────────────────────────────────┐ │
│  │ 11:30 AM                     ⋮ │ │
│  │ Project ideas: mobile app... │ │
│  │ 2 min 15 sec · 284 words    │ │
│  └────────────────────────────────┘ │
│                                      │
│  YESTERDAY                           │
│  ┌────────────────────────────────┐ │
│  │ 6:15 PM                      ⋮ │ │
│  │ Grocery list: milk, eggs...  │ │
│  │ 30 seconds · 42 words        │ │
│  └────────────────────────────────┘ │
│                                      │
│          ... (scroll)                │
│                                      │
├──────────────────────────────────────┤
│                                      │
│               ⏺️                     │  ← Big Record Button
│                                      │     (center, always visible)
└──────────────────────────────────────┘
```

### Key UI Elements

#### 1. Header Bar
- **☰ Menu Icon** (left): Opens sidebar with:
  - About
  - Help/Tutorial
  - Export Data
  - Privacy Policy
- **"Voice Notes" Title** (center)
- **⚙️ Settings Icon** (right): Configuration screen
- **🧪 Test Icon** (right): Developer testing UI (hide in production)

#### 2. Timeline Feed
- **Infinite scroll** of all recordings
- **Grouped by day**: "TODAY", "YESTERDAY", "Mon Oct 9", etc.
- **Recording Cards** show:
  - Timestamp (e.g., "12:45 PM")
  - First 5-7 words of transcription
  - Duration + word count (e.g., "45 seconds · 103 words")
  - **⋮ Menu** for actions (delete, share, copy)
- **Empty state** (no recordings):
  ```
  No recordings yet
  Tap the button below to record your first note
  ```

#### 3. Recording Button
- **Large circular button** (96dp like WhisperIME)
- **Center bottom** of screen, always visible
- **Two states**:
  - **Idle**: Red microphone icon ⏺️
  - **Recording**: Pulsing red circle with waveform animation
- **Haptic feedback** on press/release (following WhisperIME pattern)
- **Visual feedback**: Recording duration timer appears above button

#### 4. Recording Full View (Modal)
When user taps a recording card:
```
┌──────────────────────────────────────┐
│  ← Back                          ⋮  │  ← Nav + Menu
├──────────────────────────────────────┤
│  Monday, October 9, 2025             │
│  12:45 PM · 45 seconds               │
├──────────────────────────────────────┤
│                                      │
│  Remember to follow up with Sarah   │
│  about the Q3 budget. She mentioned │
│  needing the finalized numbers by   │  ← Full Transcription
│  Friday for the board presentation. │
│  Also need to check if marketing    │
│  team has their spending projections│
│  ready.                              │
│                                      │
├──────────────────────────────────────┤
│  📋 Copy    ↗️ Share    🗑️ Delete    │  ← Action Bar
└──────────────────────────────────────┘
```

### Recording Flow Visual Feedback

**State 1: Idle**
```
┌──────────────────────────────────────┐
│                                      │
│                                      │
│               ⏺️                     │  (static red mic)
│                                      │
└──────────────────────────────────────┘
```

**State 2: Recording**
```
┌──────────────────────────────────────┐
│           00:15                      │  (timer)
│                                      │
│            ◉━━◉━◉━━◉                 │  (waveform animation)
│               🔴                     │  (pulsing red circle)
│                                      │
└──────────────────────────────────────┘
```

**State 3: Processing**
```
┌──────────────────────────────────────┐
│                                      │
│        Transcribing...               │
│            ⏳                        │  (spinner)
│                                      │
└──────────────────────────────────────┘
```

**State 4: Complete**
```
┌──────────────────────────────────────┐
│  ✅ Transcription Complete           │  (brief toast)
│                                      │
│  (recording appears at top of feed)  │
└──────────────────────────────────────┘
```

### Design Principles

**1. Minimalism**
- No clutter, focus on the recording button
- Timeline is clean and scannable
- Use typography and whitespace effectively

**2. Discoverability**
- Recording button is obvious and central
- Cards clearly show they're tappable (elevation/shadow)
- Menu icons use standard Android patterns

**3. Speed**
- One tap to record, one tap to stop
- No confirmation dialogs for common actions
- Instant feedback (haptic + visual)

**4. Trust**
- Show processing progress
- Display metadata (duration, word count) for transparency
- Keep all data local (mention in UI)

**5. Accessibility**
- Large touch targets (min 48dp)
- High contrast text
- TalkBack support for vision-impaired users
- Haptic feedback for non-visual confirmation

---

## Database Architecture

### Why We Need a Database
- **Persistence**: Recordings survive app restarts
- **Metadata**: Track timestamps, duration, status
- **Search**: Find recordings by text content
- **Sync ready**: Future cloud sync capability
- **Performance**: Efficient queries for timeline

### Database Technology Choice

**Room Database** (Android Jetpack)
- Type-safe SQL queries
- Compile-time verification
- Coroutines support
- Migration support
- Battle-tested by millions of apps

### Schema Design

#### Table: `recordings`

```sql
CREATE TABLE recordings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    -- Audio metadata
    audio_path TEXT NOT NULL,           -- Path to audio file (WAV)
    duration_seconds REAL NOT NULL,     -- Audio duration
    file_size_bytes INTEGER NOT NULL,   -- Audio file size
    sample_rate INTEGER NOT NULL,       -- 16000 Hz

    -- Transcription data
    transcription_text TEXT,            -- Full transcribed text
    word_count INTEGER DEFAULT 0,       -- Number of words
    language TEXT DEFAULT 'en',         -- Detected/forced language

    -- Processing metadata
    status TEXT NOT NULL,               -- 'processing', 'completed', 'failed'
    processing_time_ms INTEGER,         -- Time to transcribe
    rtf REAL,                           -- Real-time factor
    chunks_count INTEGER DEFAULT 1,     -- Number of chunks processed

    -- Timestamps
    created_at INTEGER NOT NULL,        -- Unix timestamp (ms)
    completed_at INTEGER,               -- When transcription finished

    -- User metadata
    is_starred BOOLEAN DEFAULT 0,       -- User-flagged important
    is_deleted BOOLEAN DEFAULT 0,       -- Soft delete
    deleted_at INTEGER,                 -- Deletion timestamp

    -- Search optimization
    text_preview TEXT,                  -- First 100 chars for timeline

    -- Future features
    tags TEXT,                          -- JSON array of tags
    category TEXT,                      -- Optional categorization
    notes TEXT                          -- User-added notes
);

CREATE INDEX idx_created_at ON recordings(created_at DESC);
CREATE INDEX idx_deleted ON recordings(is_deleted, created_at DESC);
CREATE VIRTUAL TABLE recordings_fts USING fts4(transcription_text);  -- Full-text search
```

#### Table: `app_settings`

```sql
CREATE TABLE app_settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);
```

Stores:
- Last used configuration
- User preferences
- Feature flags

### Kotlin Data Models

```kotlin
@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Audio metadata
    @ColumnInfo(name = "audio_path")
    val audioPath: String,

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Float,

    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long,

    @ColumnInfo(name = "sample_rate")
    val sampleRate: Int = 16000,

    // Transcription data
    @ColumnInfo(name = "transcription_text")
    val transcriptionText: String? = null,

    @ColumnInfo(name = "word_count")
    val wordCount: Int = 0,

    @ColumnInfo(name = "language")
    val language: String = "en",

    // Processing metadata
    @ColumnInfo(name = "status")
    val status: RecordingStatus,

    @ColumnInfo(name = "processing_time_ms")
    val processingTimeMs: Long? = null,

    @ColumnInfo(name = "rtf")
    val rtf: Float? = null,

    @ColumnInfo(name = "chunks_count")
    val chunksCount: Int = 1,

    // Timestamps
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,

    // User metadata
    @ColumnInfo(name = "is_starred")
    val isStarred: Boolean = false,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,

    // UI optimization
    @ColumnInfo(name = "text_preview")
    val textPreview: String? = null,

    // Future features
    @ColumnInfo(name = "tags")
    val tags: String? = null,  // JSON array

    @ColumnInfo(name = "category")
    val category: String? = null,

    @ColumnInfo(name = "notes")
    val notes: String? = null
)

enum class RecordingStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
```

### Repository Pattern

```kotlin
class RecordingRepository(private val dao: RecordingDao) {

    // Get all recordings (timeline)
    fun getAllRecordings(): Flow<List<Recording>> {
        return dao.getAllRecordingsFlow()
            .map { recordings ->
                recordings.filter { !it.isDeleted }
            }
    }

    // Get recordings grouped by date
    fun getRecordingsGroupedByDate(): Flow<Map<String, List<Recording>>> {
        return getAllRecordings()
            .map { recordings ->
                recordings.groupBy { recording ->
                    formatDate(recording.createdAt)
                }
            }
    }

    // Create new recording
    suspend fun createRecording(audioPath: String, durationSeconds: Float, fileSizeBytes: Long): Long {
        val recording = Recording(
            audioPath = audioPath,
            durationSeconds = durationSeconds,
            fileSizeBytes = fileSizeBytes,
            status = RecordingStatus.PROCESSING
        )
        return dao.insert(recording)
    }

    // Update with transcription result
    suspend fun updateTranscription(
        id: Long,
        text: String,
        processingTimeMs: Long,
        rtf: Float,
        chunksCount: Int
    ) {
        dao.updateTranscription(
            id = id,
            text = text,
            preview = text.take(100),
            wordCount = text.split("\\s+".toRegex()).size,
            status = RecordingStatus.COMPLETED,
            completedAt = System.currentTimeMillis(),
            processingTimeMs = processingTimeMs,
            rtf = rtf,
            chunksCount = chunksCount
        )
    }

    // Soft delete
    suspend fun deleteRecording(id: Long) {
        dao.softDelete(id, System.currentTimeMillis())
    }

    // Search recordings
    fun searchRecordings(query: String): Flow<List<Recording>> {
        return dao.searchRecordings("%$query%")
    }
}
```

### File Storage Strategy

**Audio files stored separately from database:**

```
/data/data/com.voiceinput/files/
  ├── recordings/
  │   ├── 2025-10-11/
  │   │   ├── 20251011_124530_001.wav
  │   │   ├── 20251011_133045_002.wav
  │   │   └── ...
  │   ├── 2025-10-10/
  │   │   └── ...
  │   └── ...
  └── database/
      └── voice_notes.db
```

**Why separate audio files?**
- Keep database small and fast
- Easy to delete old audio while keeping transcriptions
- Enable future cloud backup of audio
- Users can export/share audio files

**File naming convention:**
```
YYYYMMDD_HHMMSS_XXX.wav
└─date─┘ └─time─┘ └seq

Example: 20251011_124530_001.wav
```

---

## Technical Implementation

### Architecture Layers

```
┌──────────────────────────────────────┐
│         UI Layer (Jetpack Compose)    │
│  - MainActivity                       │
│  - RecordingViewModel                 │
│  - TimelineScreen                     │
│  - RecordingDetailScreen              │
└──────────────┬───────────────────────┘
               │
┌──────────────▼───────────────────────┐
│         Business Logic Layer          │
│  - RecordingRepository                │
│  - AudioFileManager                   │
│  - RecordingService (background)      │
└──────────────┬───────────────────────┘
               │
┌──────────────▼───────────────────────┐
│         Core Engine Layer             │
│  - VoiceInputPipeline                 │
│  - WhisperEngine (ONNX)               │
│  - AudioProcessor (VAD)               │
│  - AudioRecorder                      │
└──────────────┬───────────────────────┘
               │
┌──────────────▼───────────────────────┐
│         Data Layer                    │
│  - Room Database                      │
│  - File System (audio storage)        │
│  - SharedPreferences (config)         │
└───────────────────────────────────────┘
```

### Key Components

#### 1. MainActivity
- Single activity with Compose UI
- Manages navigation (timeline ↔ detail view)
- Handles permissions (RECORD_AUDIO)

#### 2. RecordingViewModel
```kotlin
class RecordingViewModel(
    private val repository: RecordingRepository,
    private val pipeline: VoiceInputPipeline
) : ViewModel() {

    // Timeline state
    val recordings: StateFlow<List<Recording>> = repository
        .getRecordingsGroupedByDate()
        .stateIn(viewModelScope)

    // Recording state
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDuration = MutableStateFlow(0f)
    val recordingDuration: StateFlow<Float> = _recordingDuration

    // Start recording
    fun startRecording() {
        viewModelScope.launch {
            _isRecording.value = true
            pipeline.startListening()

            // Update duration every 100ms
            while (_isRecording.value) {
                _recordingDuration.value += 0.1f
                delay(100)
            }
        }
    }

    // Stop recording
    fun stopRecording() {
        viewModelScope.launch {
            val transcription = pipeline.stopListening()
            _isRecording.value = false

            // Save to database
            val recordingId = repository.createRecording(
                audioPath = "...",
                durationSeconds = _recordingDuration.value,
                fileSizeBytes = ...
            )

            // Update with transcription
            repository.updateTranscription(
                id = recordingId,
                text = transcription,
                ...
            )

            _recordingDuration.value = 0f
        }
    }
}
```

#### 3. RecordingService
- Foreground service for background recording (future)
- Allows recording to continue if user switches apps
- Shows notification with "Stop Recording" action

#### 4. AudioFileManager
```kotlin
class AudioFileManager(private val context: Context) {

    private val recordingsDir = File(context.filesDir, "recordings")

    fun createAudioFile(): File {
        val dateDir = File(recordingsDir, SimpleDateFormat("yyyy-MM-dd").format(Date()))
        dateDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val sequence = getNextSequence(dateDir)

        return File(dateDir, "${timestamp}_${sequence.toString().padStart(3, '0')}.wav")
    }

    fun deleteAudioFile(path: String) {
        File(path).delete()
    }

    fun getAudioFile(path: String): File? {
        val file = File(path)
        return if (file.exists()) file else null
    }
}
```

### State Management

**StateFlow for reactive UI:**
- Timeline updates automatically when new recording added
- Recording button state syncs with pipeline
- Progress indicators driven by state changes

**Single source of truth:**
- Database is authoritative for recordings list
- ViewModel exposes read-only states to UI
- User actions flow through ViewModel → Repository → Database

---

## Future Evolution

### Phase 1: Standalone App (Current)
**Goal**: Fully functional voice note-taking app

**Features:**
- ✅ Record voice with button press
- ✅ On-device transcription (Whisper SMALL INT8)
- ✅ Persistent storage with Room database
- ✅ Timeline UI with day grouping
- ✅ Full recording view
- 🔄 Search across transcriptions
- 🔄 Export recordings (text/audio)
- 🔄 Settings screen (VAD threshold, model, language)

**Success Metrics:**
- RTF < 0.6x consistently
- No memory leaks during 2-min recordings
- UI smooth 60fps scrolling
- Database queries < 50ms

---

### Phase 2: Enhanced Features
**Goal**: Power user capabilities

**New Features:**
- **Tags & Categories**: Organize recordings
- **Voice commands**: "New note", "Stop recording"
- **Audio playback**: Play original audio with transcription highlighting
- **Export formats**: Plain text, Markdown, JSON
- **Backup/Restore**: Local backup to SD card
- **Statistics**: Total recording time, word count, usage patterns

---

### Phase 3: IME Integration
**Goal**: System-wide voice input keyboard

**Major Changes:**
- Implement `InputMethodService`
- New keyboard layout XML (based on WhisperIME)
- Inject transcriptions into any text field
- Dual mode: App recordings + IME transcriptions
- Share transcription engine between app and IME

**IME Architecture:**
```
┌─────────────────────────────────────┐
│    System Text Field (Any App)      │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│   VoiceInputIME (InputMethodService)│
│   - Keyboard UI                     │
│   - Touch-and-hold recording        │
│   - Inject text via commitText()    │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│   Shared Transcription Engine       │
│   - VoiceInputPipeline              │
│   - WhisperEngine                   │
│   - AudioProcessor                  │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│   Unified Recording Database        │
│   - App recordings                  │
│   - IME transcriptions              │
│   - Searchable history              │
└─────────────────────────────────────┘
```

**IME-Specific Features:**
- Touch-and-hold mic button (walkie-talkie style)
- 30-second max recording timer
- Bluetooth headset support (SCO audio)
- Haptic feedback on press/release
- Quick delete last word
- Switch back to system keyboard

---

### Phase 4: Cloud & Sync (Optional)
**Goal**: Multi-device access

**Features:**
- End-to-end encrypted cloud storage
- Sync across devices
- Web interface for desktop access
- Shared recordings (teams/families)

---

## Implementation Priorities

### Immediate (Next 2 Weeks)
1. ✅ Fix configuration (chunk sizes) - DONE
2. ✅ Clean up debug logging - DONE
3. ✅ Create vision document - DONE
4. 🔄 Implement Room database schema
5. 🔄 Create repository pattern
6. 🔄 Build timeline UI (Compose)
7. 🔄 Integrate recording button with pipeline

### Short-Term (1 Month)
8. Recording detail view
9. Search functionality
10. Settings screen
11. Export feature
12. Polish UI/UX
13. Performance optimization

### Mid-Term (2-3 Months)
14. Enhanced features (tags, categories)
15. Audio playback
16. Voice commands
17. Statistics dashboard

### Long-Term (3-6 Months)
18. IME implementation
19. System keyboard layout
20. Touch-and-hold recording
21. Production release

---

## Open Questions & Decisions Needed

### Design Decisions
- [ ] **Color scheme**: Material Design 3? Custom brand colors?
- [ ] **Dark mode**: Support from day one?
- [ ] **Animations**: How much motion? (considering accessibility)
- [ ] **Icons**: Material Icons or custom?

### Feature Decisions
- [ ] **Audio playback**: Essential for Phase 1 or defer to Phase 2?
- [ ] **Editing**: Allow users to edit transcriptions?
- [ ] **Sharing**: Direct share to apps (WhatsApp, email) or just copy?
- [ ] **Deletion**: Permanent delete or 30-day trash?

### Technical Decisions
- [ ] **Compose vs XML**: Full Jetpack Compose or hybrid?
- [ ] **Min SDK**: Android 8.0 (API 26) or Android 9 (API 28)?
- [ ] **Audio format**: Keep WAV or compress to Opus?
- [ ] **Database encryption**: Encrypt recordings at rest?

---

## Success Criteria

### User Experience
- [ ] Recording starts within 200ms of button tap
- [ ] Transcription appears within 2s of stopping
- [ ] No crashes during 100 consecutive recordings
- [ ] Timeline scrolls smoothly with 1000+ recordings

### Performance
- [ ] RTF stays < 0.6x
- [ ] Memory usage < 250MB during recording
- [ ] Battery drain < 5% per 10 minutes of recording
- [ ] App cold start < 1 second

### Quality
- [ ] Transcription accuracy > 90% for clear speech
- [ ] No data loss (all recordings persist across app restarts)
- [ ] Graceful handling of errors (mic permission, low storage)

---

**Status**: Ready for implementation

**Next Steps**:
1. Implement Room database schema
2. Create repository pattern
3. Build timeline UI prototype
4. Connect recording button to pipeline + database

---

**End of Vision Document**
