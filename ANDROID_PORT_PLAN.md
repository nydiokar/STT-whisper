# Android Port Execution Plan

> **Mission**: Port Voice Input Service to Android as a system-wide IME (Input Method Editor) while preserving our advanced hallucination filtering, Silero VAD, and text processing logic.

---

## 📁 Repository Restructuring Plan

### Current Structure Issues
- Everything is in root (Python-specific)
- Docs are desktop-focused
- `.venv` clutters root
- No separation of concerns

### New Monorepo Structure

```
STT/
├── .github/
│   └── workflows/           # CI/CD for both platforms
├── android/                 # NEW: Android application
│   ├── app/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── kotlin/com/voiceinput/
│   │   │   │   │   ├── core/           # Ported logic
│   │   │   │   │   │   ├── TextProcessor.kt
│   │   │   │   │   │   ├── WhisperEngine.kt
│   │   │   │   │   │   ├── SileroVAD.kt
│   │   │   │   │   │   └── AudioProcessor.kt
│   │   │   │   │   ├── ime/            # IME implementation
│   │   │   │   │   │   ├── VoiceInputIME.kt
│   │   │   │   │   │   └── VoiceKeyboardView.kt
│   │   │   │   │   ├── service/        # Background service
│   │   │   │   │   │   └── TranscriptionService.kt
│   │   │   │   │   ├── config/         # Configuration
│   │   │   │   │   │   └── AppConfig.kt
│   │   │   │   │   └── ui/             # Settings UI
│   │   │   │   │       └── SettingsActivity.kt
│   │   │   │   ├── res/
│   │   │   │   └── AndroidManifest.xml
│   │   │   └── test/
│   │   ├── build.gradle.kts
│   │   └── whisper-jni/     # Whisper.cpp JNI bindings
│   ├── gradle/
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── desktop/                 # RENAMED: voice_input_service → desktop
│   ├── .venv/              # Python venv (moved here)
│   ├── voice_input_service/
│   ├── tests/
│   ├── examples/
│   ├── requirements.txt
│   └── setup.py
├── docs/                    # REORGANIZED: Platform documentation
│   ├── README.md           # Docs index
│   ├── core/               # NEW: Shared algorithm docs
│   │   ├── text-processing.md
│   │   ├── vad-algorithm.md
│   │   ├── hallucination-patterns.md
│   │   └── audio-processing.md
│   ├── desktop/            # Desktop-specific docs
│   │   ├── architecture/
│   │   ├── api/
│   │   ├── user_guide/
│   │   └── development/
│   └── android/            # NEW: Android-specific docs
│       ├── setup.md
│       ├── architecture.md
│       ├── ime-integration.md
│       └── building.md
├── .gitignore              # Update to handle both platforms
├── README.md               # Update: Multi-platform intro
└── ANDROID_PORT_PLAN.md    # This file

```

---

## 🔄 Repository Migration Steps

### Step 1: Restructure Existing Code (30 min)

```bash
# 1. Move Python code to desktop/
mkdir desktop
git mv voice_input_service desktop/
git mv tests desktop/
git mv examples desktop/
git mv requirements.txt desktop/
git mv setup.py desktop/

# 2. Move venv (if exists)
mv .venv desktop/.venv

# 3. Reorganize docs
mkdir -p docs/core docs/android
git mv docs/architecture docs/desktop/architecture
git mv docs/api docs/desktop/api
git mv docs/user_guide docs/desktop/user_guide
git mv docs/development docs/desktop/development
git mv docs/plans docs/desktop/plans

# Keep docs/index.md and update it

# 4. Commit restructuring
git add .
git commit -m "Restructure repo for monorepo (desktop + android)"
```

### Step 2: Update References (15 min)

**Files to update:**
- `README.md` - Add monorepo structure, multi-platform intro
- `desktop/README.md` - Create desktop-specific readme
- `.gitignore` - Add Android-specific ignores
- `docs/index.md` - Update paths to new structure

### Step 3: Create Android Project Structure (1 hour)

Use Android Studio:
1. File → New → New Project
2. Choose "Empty Activity"
3. Location: `STT/android/`
4. Package name: `com.voiceinput.stt`
5. Language: Kotlin
6. Minimum SDK: 24 (Android 7.0 - covers 95% devices)

---

## 📋 Core Component Porting Guide

### Component 1: TextProcessor (PRIORITY 1)

**Source**: `desktop/voice_input_service/utils/text_processor.py`
**Target**: `android/app/src/main/kotlin/com/voiceinput/core/TextProcessor.kt`
**Effort**: 2-3 hours
**Dependencies**: None (pure logic)

**Implementation checklist:**
- [ ] Port `hallucination_patterns` list (lines 20-42)
- [ ] Port `remove_timestamps()` regex (lines 47-69)
- [ ] Port `filter_hallucinations()` logic (lines 71-166)
  - [ ] Exact match filtering
  - [ ] Prefix/suffix detection
  - [ ] Long text trimming
- [ ] Port `append_text()` overlap detection (lines 238-352)
  - [ ] Overlap detection algorithm
  - [ ] Smart spacing logic
  - [ ] Capitalization rules
- [ ] Port `format_transcript()` (lines 191-236)
- [ ] Write Kotlin unit tests mirroring Python tests

**Key translation notes:**
- Python `re.compile()` → Kotlin `Regex()`
- Python `str.lower()` → Kotlin `lowercase()`
- Python `str.strip()` → Kotlin `trim()`
- Python list comprehension → Kotlin `filter`, `map`

### Component 2: Configuration (PRIORITY 1)

**Source**: `desktop/voice_input_service/config.py`
**Target**: `android/app/src/main/kotlin/com/voiceinput/config/AppConfig.kt`
**Effort**: 2 hours
**Dependencies**: SharedPreferences

**Implementation checklist:**
- [ ] Create `AudioConfig` data class (lines 12-48)
  - [ ] sampleRate, chunkSize, channels, format
  - [ ] VAD settings: vadThreshold, silenceDuration, maxChunkDuration
- [ ] Create `TranscriptionConfig` data class (lines 50-80)
  - [ ] modelName, language, minChunkSize
  - [ ] Whisper.cpp paths (Android assets)
- [ ] Create `AppConfig` wrapper
- [ ] Implement `ConfigRepository` using SharedPreferences
  - [ ] Save/load methods
  - [ ] Default values
  - [ ] Validation

**Storage strategy:**
- Use `SharedPreferences` for user settings
- Use `datastore-preferences` for modern approach (optional upgrade)
- Store model paths relative to app data directory

### Component 3: Silence Detection / VAD (PRIORITY 2)

**Source**: `desktop/voice_input_service/utils/silence_detection.py`
**Target**: `android/app/src/main/kotlin/com/voiceinput/core/SileroVAD.kt`
**Effort**: 4-6 hours
**Dependencies**: ONNX Runtime Android, Silero VAD model

**Implementation checklist:**
- [ ] Download Silero VAD ONNX model
  - Model: `https://github.com/snakers4/silero-vad/blob/master/files/silero_vad.onnx`
  - Place in: `android/app/src/main/assets/models/silero_vad.onnx`
- [ ] Add ONNX Runtime dependency
  ```kotlin
  implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.0")
  ```
- [ ] Port core VAD logic (lines 113-151)
  - [ ] Load ONNX model from assets
  - [ ] Convert audio bytes to float32 tensor
  - [ ] Run inference
  - [ ] Apply threshold from config
- [ ] Implement frame-based processing (30ms frames)
- [ ] Handle device selection (CPU/NNAPI/GPU)

**Android-specific adaptations:**
- Use `OrtEnvironment.getEnvironment()` instead of PyTorch
- Load model from assets: `context.assets.open("models/silero_vad.onnx")`
- Convert `ByteArray` → `FloatArray` for ONNX input

### Component 4: Audio Recording (PRIORITY 2)

**Source**: `desktop/voice_input_service/core/audio.py`
**Target**: `android/app/src/main/kotlin/com/voiceinput/core/AudioRecorder.kt`
**Effort**: 4-6 hours
**Dependencies**: android.media.AudioRecord

**Implementation checklist:**
- [ ] Create `AudioRecorder` class
- [ ] Initialize `AudioRecord` with config
  ```kotlin
  AudioRecord(
      MediaRecorder.AudioSource.VOICE_RECOGNITION,
      sampleRate = 16000,
      channelConfig = AudioFormat.CHANNEL_IN_MONO,
      audioFormat = AudioFormat.ENCODING_PCM_16BIT,
      bufferSize = AudioRecord.getMinBufferSize(...)
  )
  ```
- [ ] Implement start/stop lifecycle
- [ ] Implement chunked reading (emit audio chunks)
- [ ] Use Kotlin Flow for audio stream
  ```kotlin
  fun audioStream(): Flow<ByteArray> = flow {
      while (isRecording) {
          val buffer = ByteArray(chunkSize)
          audioRecord.read(buffer, 0, buffer.size)
          emit(buffer)
      }
  }
  ```
- [ ] Handle permissions (RECORD_AUDIO)
- [ ] Error handling (device busy, permission denied)

### Component 5: Whisper Engine (PRIORITY 2)

**Source**: `desktop/voice_input_service/core/transcription.py`
**Target**: `android/app/src/main/kotlin/com/voiceinput/core/WhisperEngine.kt`
**Effort**: 6-8 hours
**Dependencies**: whisper.cpp Android bindings (JNI)

**Implementation checklist:**
- [ ] Add whisper.cpp Android library
  - Option A: Use `whisper.android` library (if exists)
  - Option B: Build JNI bindings from whisper.cpp source
- [ ] Port model loading logic (lines 134-172)
- [ ] Port transcription logic (lines 174-324)
  - [ ] Audio preprocessing (bytes → float32)
  - [ ] Call whisper.cpp via JNI
  - [ ] Parse result JSON
  - [ ] Standardize output format
- [ ] Handle model selection (tiny, base, small)
- [ ] Store models in app internal storage or assets

**Model management:**
- Download models on first run
- Store in: `context.filesDir/models/ggml-base.en.bin`
- Provide model downloader utility

### Component 6: Audio Processor / Worker (PRIORITY 3)

**Source**: `desktop/voice_input_service/core/processing.py`
**Target**: `android/app/src/main/kotlin/com/voiceinput/core/AudioProcessor.kt`
**Effort**: 6-8 hours
**Dependencies**: Coroutines, Flow

**Implementation checklist:**
- [ ] Create `AudioProcessor` class using Coroutines
- [ ] Implement buffer management (lines 141-204)
  - [ ] Speech buffer accumulation
  - [ ] Silence detection triggers
  - [ ] Max chunk duration handling
- [ ] Implement processing loop using `Flow`
  ```kotlin
  audioRecorder.audioStream()
      .map { chunk -> vadDetector.isSilent(chunk) to chunk }
      .scan(BufferState()) { state, (isSilent, chunk) ->
          // Buffer logic here
      }
      .filter { it.shouldProcess }
      .map { whisperEngine.transcribe(it.buffer) }
      .collect { result -> onResult(result) }
  ```
- [ ] Port buffer logic (active speech vs silence)
- [ ] Implement callbacks for results
- [ ] Thread-safe state management

**Kotlin/Android adaptations:**
- Replace `threading.Thread` with `CoroutineScope`
- Replace `queue.Queue` with `Channel` or `Flow`
- Use `Mutex` for thread safety instead of `RLock`

### Component 7: IME Implementation (PRIORITY 4)

**Source**: `desktop/voice_input_service/service.py` (main orchestrator)
**Target**: `android/app/src/main/kotlin/com/voiceinput/ime/VoiceInputIME.kt`
**Effort**: 12-16 hours
**Dependencies**: InputMethodService

**Implementation checklist:**
- [ ] Create `VoiceInputIME` extending `InputMethodService`
- [ ] Implement keyboard layout XML
  - [ ] Microphone button
  - [ ] Recording indicator
  - [ ] Text preview area
- [ ] Implement recording lifecycle
  ```kotlin
  override fun onStartInput() { }
  override fun onFinishInput() { }
  ```
- [ ] Connect to audio pipeline
  - [ ] Start recording on button press
  - [ ] Stop on silence detection (continuous mode)
  - [ ] Stop on button release (push-to-talk mode)
- [ ] Text insertion logic
  ```kotlin
  currentInputConnection?.commitText(filteredText, 1)
  ```
- [ ] Mode switching (continuous vs manual)
- [ ] Settings integration

**IME-specific requirements:**
- Declare in `AndroidManifest.xml`:
  ```xml
  <service
      android:name=".ime.VoiceInputIME"
      android:permission="android.permission.BIND_INPUT_METHOD">
      <intent-filter>
          <action android:name="android.view.InputMethod" />
      </intent-filter>
      <meta-data
          android:name="android.view.im"
          android:resource="@xml/method" />
  </service>
  ```

### Component 8: Settings UI (PRIORITY 5)

**Target**: `android/app/src/main/kotlin/com/voiceinput/ui/SettingsActivity.kt`
**Effort**: 4-6 hours

**Implementation checklist:**
- [ ] Create PreferenceScreen XML
  - [ ] Model selection (tiny, base, small)
  - [ ] VAD threshold slider
  - [ ] Silence duration setting
  - [ ] Language selection
  - [ ] Mode preference (continuous/manual)
- [ ] Implement SettingsActivity
- [ ] Bind preferences to SharedPreferences
- [ ] Add IME settings launcher
- [ ] Model download UI (if needed)

---

## 📅 Phased Execution Timeline

### Phase 0: Repository Setup (Day 1 - 2-3 hours)

**Tasks:**
- [ ] Restructure repository (migrate desktop code)
- [ ] Update .gitignore for Android
- [ ] Create Android project in `android/`
- [ ] Set up basic Gradle dependencies
- [ ] Update README.md with monorepo structure
- [ ] Commit restructuring changes

**Deliverable:** Clean monorepo structure ready for development

---

### Phase 1: Core Logic MVP (Day 2-3 - 6-8 hours)

**Goal:** Prove core algorithms work on Android

**Tasks:**
- [ ] Port `TextProcessor.kt` (2-3h)
  - [ ] Copy hallucination patterns
  - [ ] Translate filter logic
  - [ ] Write unit tests
- [ ] Port `AppConfig.kt` (2h)
  - [ ] Data classes
  - [ ] SharedPreferences integration
- [ ] Create test Activity (1h)
  - [ ] Simple UI with EditText
  - [ ] Button to test filtering
  - [ ] Display before/after text
- [ ] Verify hallucination filtering works identically

**Deliverable:** Working text processor with proof it works on Android

---

### Phase 2: Audio + Transcription (Day 4-6 - 12-16 hours)

**Goal:** End-to-end audio → transcription pipeline

**Tasks:**
- [ ] Implement `AudioRecorder.kt` (4-6h)
  - [ ] AudioRecord setup
  - [ ] Flow-based audio streaming
  - [ ] Permission handling
- [ ] Integrate whisper.cpp (6-8h)
  - [ ] Add JNI bindings or library
  - [ ] Implement `WhisperEngine.kt`
  - [ ] Download/bundle base model
  - [ ] Test transcription
- [ ] Connect recorder → whisper (2h)
  - [ ] Record audio
  - [ ] Pass to Whisper
  - [ ] Display transcription
- [ ] Test: Record voice → see text output

**Deliverable:** Working audio recording + transcription (no filtering yet)

---

### Phase 3: VAD Integration (Day 7-8 - 6-8 hours)

**Goal:** Add intelligent silence detection

**Tasks:**
- [ ] Download Silero VAD ONNX model (30min)
- [ ] Add ONNX Runtime dependency (30min)
- [ ] Port `SileroVAD.kt` (4-6h)
  - [ ] Model loading from assets
  - [ ] Inference implementation
  - [ ] Frame-based processing
- [ ] Implement `AudioProcessor.kt` (4-6h)
  - [ ] Buffer management with VAD
  - [ ] Silence-triggered processing
  - [ ] Coroutine-based flow
- [ ] Test: Record → detect silence → transcribe chunk

**Deliverable:** Smart audio segmentation based on speech detection

---

### Phase 4: Full Integration (Day 9-10 - 8-10 hours)

**Goal:** Connect all components with filtering

**Tasks:**
- [ ] Wire AudioProcessor → WhisperEngine → TextProcessor (2h)
- [ ] Implement result callback chain (2h)
  ```kotlin
  audioProcessor.start { rawResult ->
      val filtered = textProcessor.filterHallucinations(rawResult.text)
      val accumulated = textProcessor.appendText(currentText, filtered)
      updateUI(accumulated)
  }
  ```
- [ ] Add mode switching (continuous vs manual) (2h)
- [ ] Test full pipeline (2h)
- [ ] Handle edge cases and errors (2h)

**Deliverable:** Complete processing pipeline in test app

---

### Phase 5: IME Implementation (Day 11-14 - 16-20 hours)

**Goal:** Build system-wide keyboard

**Tasks:**
- [ ] Create `VoiceInputIME` service (4h)
  - [ ] Extend InputMethodService
  - [ ] Basic keyboard layout
  - [ ] Microphone button
- [ ] Implement keyboard view (4h)
  - [ ] Custom View for keyboard UI
  - [ ] Recording animation
  - [ ] Text preview
- [ ] Connect IME to processing pipeline (4h)
  - [ ] Start/stop recording from IME
  - [ ] Receive transcription results
  - [ ] Insert text via InputConnection
- [ ] Handle IME lifecycle (2h)
  - [ ] Foreground service for recording
  - [ ] Notification for active recording
  - [ ] Proper cleanup on dismiss
- [ ] Test in various apps (2h)
  - [ ] WhatsApp, Notes, Chrome, etc.
  - [ ] Fix compatibility issues

**Deliverable:** Working system-wide voice keyboard

---

### Phase 6: Settings & Polish (Day 15-16 - 8-10 hours)

**Goal:** User configuration and final touches

**Tasks:**
- [ ] Create Settings UI (4h)
  - [ ] PreferenceScreen
  - [ ] Model selection
  - [ ] VAD threshold slider
  - [ ] Language picker
- [ ] Implement model management (2h)
  - [ ] Download models on demand
  - [ ] Model switching
  - [ ] Storage management
- [ ] Add onboarding (2h)
  - [ ] First-run tutorial
  - [ ] Permission requests
  - [ ] IME selection guide
- [ ] Polish UI/UX (2h)
  - [ ] Icons, animations
  - [ ] Error messages
  - [ ] Loading states

**Deliverable:** Production-ready app with full settings

---

### Phase 7: Testing & Optimization (Day 17-18 - 8-10 hours)

**Goal:** Ensure quality and performance

**Tasks:**
- [ ] Write instrumentation tests (3h)
- [ ] Battery optimization (2h)
  - [ ] Efficient audio processing
  - [ ] Stop service when not in use
  - [ ] Wake lock management
- [ ] Memory optimization (2h)
  - [ ] Model memory usage
  - [ ] Audio buffer size tuning
  - [ ] Leak detection
- [ ] Edge case handling (3h)
  - [ ] No internet (offline)
  - [ ] No microphone permission
  - [ ] Model download failure
  - [ ] Very long recordings

**Deliverable:** Stable, optimized application

---

## 🔧 Technical Setup Details

### Android Dependencies (build.gradle.kts)

```kotlin
dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Preferences
    implementation("androidx.preference:preference-ktx:1.2.1")

    // ONNX Runtime (for Silero VAD)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.0")

    // Whisper.cpp (choose one):
    // Option A: Use existing library (if available)
    // implementation("io.github.ggerganov:whisper-android:1.5.4")
    // Option B: Build JNI bindings yourself (add as module)

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
```

### Whisper.cpp Integration Options

**Option A: Use pre-built library**
- Search Maven Central for `whisper-android` or `whisper-cpp-android`
- Add as dependency if exists
- Pros: Fast setup
- Cons: May not exist or be outdated

**Option B: Build JNI yourself** (Recommended for control)
1. Clone whisper.cpp: `git submodule add https://github.com/ggerganov/whisper.cpp android/whisper-cpp`
2. Create JNI wrapper module in `android/whisper-jni/`
3. Use CMake to build:
   ```cmake
   # android/whisper-jni/CMakeLists.txt
   cmake_minimum_required(VERSION 3.18)
   project(whisper-jni)

   add_subdirectory(../whisper-cpp whisper-cpp)

   add_library(whisper-jni SHARED
       src/main/cpp/whisper_jni.cpp
   )

   target_link_libraries(whisper-jni
       whisper
       log
   )
   ```

**Option C: Use whisper.android from chidiwilliams**
- `https://github.com/chidiwilliams/whisper.android`
- More maintained Android wrapper
- Check if active in 2025

### Model Storage Strategy

**Models location:**
- Assets (bundled): `android/app/src/main/assets/models/` (tiny model only)
- Downloaded: `context.filesDir/models/` (larger models)

**Model files needed:**
- `ggml-tiny.en.bin` (~75 MB) - Bundle in assets
- `ggml-base.en.bin` (~142 MB) - Download on demand
- `silero_vad.onnx` (~2 MB) - Bundle in assets

**Download implementation:**
```kotlin
suspend fun downloadModel(modelName: String) {
    val url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$modelName"
    val outputFile = File(context.filesDir, "models/$modelName")

    // Use DownloadManager or OkHttp to download
    // Show progress notification
    // Verify checksum
}
```

---

## 📝 Documentation Updates Required

### Update: `README.md` (Root)

**Add sections:**
- Multi-platform support (Desktop + Android)
- Links to platform-specific READMEs
- Architecture overview (shared algorithms)
- Contributing guide for both platforms

### Create: `desktop/README.md`

Move current README content here, update paths to reference `desktop/` structure

### Create: `android/README.md`

**Include:**
- Building instructions
- Installing from source
- Architecture specific to Android
- Contributing to Android version

### Create: `docs/core/` Documentation

**Files to create:**

1. **`docs/core/text-processing.md`**
   - Hallucination filtering algorithm
   - Pattern matching logic
   - Overlap detection explanation
   - Append logic with examples

2. **`docs/core/vad-algorithm.md`**
   - Silero VAD explanation
   - Threshold tuning guide
   - Frame-based processing
   - Platform-specific implementations

3. **`docs/core/hallucination-patterns.md`**
   - List of all patterns
   - Why each pattern is included
   - How to add new patterns
   - Testing methodology

4. **`docs/core/audio-processing.md`**
   - Buffer management strategy
   - Chunk size calculations
   - Silence detection triggering
   - Continuous vs manual mode

### Create: `docs/android/` Documentation

**Files to create:**

1. **`docs/android/setup.md`**
   - Development environment setup
   - Building from source
   - Installing on device
   - Enabling IME

2. **`docs/android/architecture.md`**
   - Android-specific architecture
   - Component diagram
   - Data flow
   - Threading model (Coroutines)

3. **`docs/android/ime-integration.md`**
   - How IME works
   - Text insertion mechanism
   - Keyboard lifecycle
   - Supporting different apps

4. **`docs/android/building.md`**
   - Build instructions
   - Signing configurations
   - Release process
   - CI/CD setup

---

## 🚀 Session Starter Checklist

**Use this checklist when starting a new session:**

### Before Coding
- [ ] Pull latest changes: `git pull`
- [ ] Check current phase in this document
- [ ] Review previous session's work
- [ ] Set up environment (Android Studio / Gradle sync)

### Start of Session
- [ ] Identify current phase and tasks
- [ ] Review component mapping for current task
- [ ] Check Python source files for reference
- [ ] Open relevant Kotlin files

### During Development
- [ ] Follow porting checklist for current component
- [ ] Write unit tests as you go
- [ ] Test on actual device/emulator regularly
- [ ] Update this document if plans change
- [ ] Commit frequently with clear messages

### End of Session
- [ ] Mark completed tasks in checklist
- [ ] Document any blockers or decisions
- [ ] Push changes: `git push`
- [ ] Update next session's starting point

---

## 🎯 Current Status & Next Steps

**Status**: Planning complete, ready to execute
**Current Phase**: Phase 0 - Repository Setup
**Next Session Start Here**:

### Immediate Actions (Next Session):

1. **Restructure Repository** (30 min)
   ```bash
   cd STT
   mkdir desktop android
   # Follow Step 1 from Repository Migration Steps above
   ```

2. **Create Android Project** (30 min)
   - Open Android Studio
   - New Project → Empty Activity
   - Location: `STT/android/`
   - Package: `com.voiceinput.stt`
   - Sync Gradle

3. **Port TextProcessor** (2-3 hours)
   - Create `TextProcessor.kt`
   - Copy logic from `desktop/voice_input_service/utils/text_processor.py`
   - Write tests
   - Verify filtering works

**First Milestone Goal:** Working `TextProcessor.kt` with passing tests proving hallucination filtering works on Android.

---

## 📌 Key Decisions & Reminders

### Technical Decisions
- **Language**: Kotlin (not Java)
- **Min SDK**: 24 (Android 7.0)
- **Architecture**: MVVM with Coroutines + Flow
- **DI**: None initially (add Hilt later if needed)
- **Whisper**: Use whisper.cpp (not Python)
- **VAD**: Silero ONNX (not WebRTC initially)

### Important Notes
- Keep Python code as reference, don't delete
- Test on real device (audio features)
- IME requires device/emulator restart to activate
- Model files are large, don't commit to git
- Use `.gitattributes` for Git LFS if bundling models

### Success Criteria
- Hallucination filtering works identically to Python version
- VAD detects speech/silence accurately
- Text insertion works in all apps (WhatsApp, Chrome, etc.)
- No crashes or ANRs
- Battery efficient (<5% per hour of use)

---

## 🔗 Useful Resources

**Android Development:**
- [InputMethodService docs](https://developer.android.com/reference/android/inputmethodservice/InputMethodService)
- [AudioRecord docs](https://developer.android.com/reference/android/media/AudioRecord)
- [Kotlin Coroutines guide](https://kotlinlang.org/docs/coroutines-guide.html)

**Whisper & VAD:**
- [whisper.cpp GitHub](https://github.com/ggerganov/whisper.cpp)
- [Silero VAD models](https://github.com/snakers4/silero-vad)
- [ONNX Runtime Android](https://onnxruntime.ai/docs/tutorials/mobile/)

**Reference Implementations:**
- [WhisperInput](https://github.com/alex-vt/WhisperInput) - For IME structure ideas
- [whisper.android](https://github.com/chidiwilliams/whisper.android) - For JNI integration

---

## 📊 Progress Tracking

**Update this table as you complete phases:**

| Phase | Status | Start Date | End Date | Notes |
|-------|--------|-----------|----------|-------|
| Phase 0: Setup | ⬜ Not Started | - | - | Repository restructuring |
| Phase 1: Core Logic | ⬜ Not Started | - | - | TextProcessor + Config |
| Phase 2: Audio/Transcription | ⬜ Not Started | - | - | Record + Whisper |
| Phase 3: VAD | ⬜ Not Started | - | - | Silero integration |
| Phase 4: Full Integration | ⬜ Not Started | - | - | Pipeline connection |
| Phase 5: IME | ⬜ Not Started | - | - | Keyboard implementation |
| Phase 6: Settings | ⬜ Not Started | - | - | UI + Polish |
| Phase 7: Testing | ⬜ Not Started | - | - | QA + Optimization |

**Legend:**
- ⬜ Not Started
- 🟡 In Progress
- ✅ Complete
- ⚠️ Blocked

---

## 🎉 Final Notes

This plan is your roadmap. Trust the process:
1. **Restructure first** - Clean foundation
2. **Port core logic** - Prove it works
3. **Build pipeline** - Audio → Text
4. **Create IME** - System integration
5. **Polish** - Make it shine

Your desktop implementation has unique value (hallucination filtering, smart VAD, overlap detection). The Android world needs this quality level.

**Remember**: This is YOUR service. You built something special on desktop. Now bring that quality to Android. No shortcuts, no compromises.

When in doubt, refer to your Python implementation - it's the gold standard.

---

**Document Version**: 1.0
**Last Updated**: 2025-09-23
**Author**: Claude + Cicada38
**Next Review**: After Phase 0 completion