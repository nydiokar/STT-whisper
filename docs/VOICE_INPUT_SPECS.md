# Voice Input Specifications

**Version**: 1.0
**Last Updated**: 2025-10-11
**Status**: Active Development

---

VERY IMPORTANT - implement smaller models - base and tiny are available. 

## 1. Core Use Case

### Primary Interaction Model
**Simple Button-Based Recording**

1. User presses a button to **start recording**
2. User speaks into the microphone
3. User presses the button again to **stop recording**
4. App transcribes the recorded audio and displays text in a text box

**Target Output Locations:**
- Message boxes (SMS, chat apps)
- Note-taking applications
- In-app note creation (future enhancement)

### Key Design Principles
- **Simplicity First**: One button, clear feedback, predictable behavior
- **Performance**: Fast transcription with low latency
- **Context Preservation**: Smart chunking with overlap to maintain sentence coherence
- **User Control**: Manual start/stop, no automatic interruptions

---

## 2. Technical Requirements

### Audio Configuration

```kotlin
AudioConfig(
    sampleRate: 16000 Hz,           // Standard Whisper input
    channels: 1 (mono),              // Single channel for efficiency
    maxChunkDurationSec: 20.0s,      // 20-second chunks for proper context
    minAudioLengthSec: 1.5s,         // Minimum before processing
    silenceDurationSec: 1.5s,        // Natural pause detection
    overlapDurationSec: 3.0s,        // 3s overlap between chunks for context
    enableVAD: false                 // Disabled initially (Phase 2 feature)
)
```

### Recording Duration
- **Maximum continuous recording**: 2 minutes
- **Processing strategy**: Split into 20-second chunks with 3-second overlap
- **Why 20 seconds?**:
  - Whisper is trained on 30-second audio clips
  - 20s provides enough context for coherent transcription
  - Keeps memory usage reasonable on mobile devices
  - Prevents the catastrophic 1-second chunking that caused 4x slowdown

### Transcription Configuration

```kotlin
TranscriptionConfig(
    modelName: "whisper-small-int8",  // RTranslator's quantized model (244M params)
    language: "en",
    minChunkSizeBytes: 24000,         // ~0.75s minimum
    runtime: "ONNX Runtime",          // APU-accelerated inference
    enableVAD: false                  // Manual recording for now
)
```

### Performance Targets
- **Real-Time Factor (RTF)**: < 0.6x (target: 0.5x like BareWhisperBenchmark)
- **Latency**: < 2 seconds from stop button to first text display
- **Memory**: < 200MB during active transcription
- **Accuracy**: Match or exceed full-audio transcription quality

---

## 3. Architecture Overview

### Component Stack

```
┌─────────────────────────────────────────────────┐
│              User Interface (UI)                 │
│  - Record Button (start/stop)                   │
│  - Text Display Box                             │
│  - Visual Feedback (waveform, progress)         │
└─────────────────┬───────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────┐
│         VoiceInputPipeline (Core)               │
│  - Coordinates audio → text flow                │
│  - Manages component lifecycle                  │
│  - Handles chunking + overlap strategy          │
└─────────────────┬───────────────────────────────┘
                  │
      ┌───────────┼───────────┐
      │           │           │
┌─────▼─────┐ ┌──▼──────┐ ┌──▼────────────┐
│ Audio     │ │ Whisper │ │ Text          │
│ Recorder  │ │ Engine  │ │ Processor     │
│           │ │ (ONNX)  │ │               │
│ - Capture │ │ - APU   │ │ - Overlap     │
│ - Buffer  │ │ - INT8  │ │   detection   │
│ - Stream  │ │ - 0.5xRT│ │ - Formatting  │
└───────────┘ └─────────┘ └───────────────┘
```

### Data Flow

```
Microphone Audio (16kHz mono)
    ↓
AudioRecorder (2048-byte chunks from hardware)
    ↓
Buffer Accumulation (until 20s or stop button)
    ↓
VoiceInputPipeline (chunk with 3s overlap)
    ↓
WhisperEngine (ONNX Runtime + APU)
    ↓
TextProcessor (overlap detection + merge)
    ↓
UI Display (text box update)
```

---

## 4. Smart Patterns from Reference Projects

### From WhisperIME (Battle-Tested Keyboard)

**1. Touch-and-Hold Recording Pattern**
```java
// Alternative interaction: hold to record, release to transcribe
micButton.setOnTouchListener((v, event) -> {
    if (event.getAction() == MotionEvent.ACTION_DOWN) {
        startRecording();
        hapticFeedback();
    } else if (event.getAction() == MotionEvent.ACTION_UP) {
        stopRecording();
        hapticFeedback();
    }
});
```
**Benefit**: Intuitive walkie-talkie-style interaction, natural for voice input

**2. Maximum Recording Timer**
```java
// Safety limit: 30-second max recording
recordingTimer = new CountDownTimer(30000, 1000) {
    public void onTick(long millisUntilFinished) {
        updateProgress((30000 - millisUntilFinished) / 1000);
    }
    public void onFinish() {
        stopRecording();
    }
};
```
**Benefit**: Prevents runaway recordings, guides user behavior

**3. Haptic Feedback**
```java
// System vibration on mic press/release
if (Settings.System.getInt(contentResolver,
    Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) == 1) {
    vibrator.vibrate(VibrationEffect.createOneShot(50,
        VibrationEffect.DEFAULT_AMPLITUDE));
}
```
**Benefit**: Tactile confirmation of recording state changes

**4. Lazy Model Initialization**
```java
// Only initialize model when IME starts, not at app launch
@Override
public void onStartInputView(EditorInfo info, boolean restarting) {
    if (!whisperModel.isInitialized()) {
        whisperModel.init();  // First-time lazy load
    }
}
```
**Benefit**: Faster app startup, deferred resource allocation

**5. Bluetooth Audio Support**
```java
// Handle Bluetooth SCO for wireless headsets
if (isBluetoothHeadsetConnected()) {
    audioManager.startBluetoothSco();
    audioManager.setBluetoothScoOn(true);
}
```
**Benefit**: Professional use case support (wireless headsets)

---

### From RTranslator (Production-Quality STT)




**1. Whisper SMALL INT8 Model**
```
Model: whisper-small.en (244M parameters)
Format: ONNX INT8 quantized
Performance: 4x faster than FP32, minimal accuracy loss
APU Acceleration: MediaTek/Samsung AI chip support
```
**Benefit**: Best balance of speed/quality for mobile (proven in production)

**2. ONNX Runtime Configuration**
```kotlin
val options = SessionOptions().apply {
    setIntraOpNumThreads(4)           // CPU parallelism
    setInterOpNumThreads(2)           // Operator parallelism
    registerCustomOpLibrary(nnapi)    // APU acceleration
    setOptimizationLevel(ALL)         // Full optimization
}
```
**Benefit**: Squeezes every drop of performance from hardware

**3. Audio Preprocessing Pipeline**
```kotlin
// Ensure audio matches Whisper's training data
fun preprocessAudio(raw: FloatArray): FloatArray {
    return raw
        .resample(to = 16000)         // Ensure 16kHz
        .normalize()                   // [-1.0, 1.0] range
        .applyMelSpectrogram()        // Whisper's expected input
        .padOrTruncate(3000)          // 30s fixed length
}
```
**Benefit**: Maximizes transcription accuracy by matching training distribution

**4. Chunk Overlap Strategy**
```kotlin
// Smart overlap prevents mid-word cuts
val chunkSize = 20 * 16000        // 20 seconds
val overlap = 3 * 16000           // 3-second overlap
val stride = chunkSize - overlap  // Advance by 17 seconds

for (i in 0 until audio.size step stride) {
    val chunk = audio.slice(i until min(i + chunkSize, audio.size))
    transcribe(chunk)
}
```
**Benefit**: Maintains context across chunks, prevents sentence fragmentation

**5. Memory-Aware Processing**
```kotlin
// Monitor memory and adapt behavior
if (memoryManager.isMemoryWarning()) {
    // Reduce buffer sizes
    // Request garbage collection
    // Pause non-critical operations
}
```
**Benefit**: Prevents OOM crashes during long recordings

---

## 5. Implementation Phases

### Phase 1: Core Functionality (CURRENT)
**Goal**: Get basic button recording → transcription working perfectly

**Tasks:**
- [x] Fix audio chunk configuration (1s → 20s)
- [x] Implement chunking with overlap
- [ ] Test with JFK audio (verify 0.5x RTF)
- [ ] Create simple UI with record button
- [ ] Add visual recording indicator
- [ ] Display transcribed text in text box
- [ ] Add basic error handling

**Success Criteria:**
- RTF < 0.6x (matches BareWhisperBenchmark)
- Coherent sentence transcription (no word salad)
- < 2 second latency from stop to text display
- Stable for 2-minute recordings

---

### Phase 2: Smart Features (FUTURE)
**Goal**: Add intelligent VAD-based automatic transcription

**Features to Add:**
- Silero VAD for automatic pause detection
- Continuous recording with smart stopping
- Background transcription during silence
- Real-time partial results display

**Configuration Changes:**
```kotlin
AudioConfig(
    enableVAD: true,                  // Enable VAD
    vadThreshold: 0.3,                // Balanced sensitivity
    silenceDurationSec: 1.5,          // Stop after 1.5s silence
    continuousMode: true              // Keep recording after transcription
)
```

**New Capabilities:**
- Hands-free operation (speak naturally, auto-stops at pauses)
- Real-time streaming transcription
- Multi-turn conversation support

---

### Phase 3: IME Integration (FUTURE)
**Goal**: Full keyboard replacement functionality

*PLAN FOR MODEL DL AND MODEL PICKER*

Short answer
Following the current plan mostly adds features/UX; it won’t significantly improve speed on your A34 unless we offer smaller models and let users choose them.
You can source ONNX tiny/base models reliably from sherpa-onnx and Hugging Face:
sherpa-onnx provides pre-exported ONNX Whisper models (tiny/base/...); see “Export Whisper to ONNX” and model repos list source.
Hugging Face has ONNX community builds, e.g. tiny and base variants source, source.
What to implement (minimal, safe, and device-wide useful)
Model selection and on-device downloads are easy to add without big refactors. The only caveat: sherpa-onnx models are encoder/decoder pairs with different file names/IO than your current 5-part RTranslator split. So:
Phase 1: Enable file-based loading for your existing split model (done below).
Phase 2: Add a model catalog + downloader + settings to pick a variant.
Phase 3: Add a second engine for sherpa-onnx (or normalize ONNX I/O), so tiny/base can run.
Minimal code change you already have now
Your engine now prefers filesDir/models/*.onnx if present, else falls back to assets. That lets us drop replacement files later without touching code. (I kept the filenames consistent with your current split: Whisper_initializer.onnx, Whisper_encoder.onnx, Whisper_cache_initializer.onnx, Whisper_decoder.onnx, Whisper_detokenizer.onnx.)
Proposed integration plan (practical and incremental)
Model catalog (static map or JSON):
Entries: id, displayName, engineType, file list, URLs, expected sizes/hashes.
Start with:
small-int8-split (current, assets; no download needed)
tiny.en-sherpa (encoder.onnx/decoder.onnx/tokens.txt) source
base.en-sherpa (encoder.onnx/decoder.onnx/tokens.txt) source
Downloader:
Use Android DownloadManager for big files, background and resumable.
Verify file size/hash after download; store under context.filesDir/voice_input_data/models/{modelId}/....
Settings:
Add a simple model picker (just a list by displayName). Store choice in AppConfig.transcription.modelName (already supported).
Engine selection:
Keep current WhisperEngine for split small model.
Add WhisperEngineSherpa later for sherpa-onnx (expects encoder/decoder ONNX and tokens). This avoids risky changes in the current engine and lets you ship tiny/base fast once the new engine is added.
Why this works
You’ll be able to ship with the current “small” (assets), and later let users download tiny/base and switch engines at runtime. Smaller models (tiny/base) are what will actually speed up the encoder on your device.
The sources linked offer ready-to-use ONNX exports:
sherpa-onnx’s tiny/base/… variants with tested encoder/decoder ONNX artifacts and tokens source.
ONNX-community model trees on Hugging Face for tiny/base variants source, source.
If you want, I can add:
A ModelCatalog.kt (static list), a thin ModelDownloader using DownloadManager, and a simple “Model” setting to switch between “Small (built-in)” and “Tiny/Base (downloadable)”. This is low-risk and keeps your current engine intact until the sherpa engine is added.
Implemented: reverted risky decoder changes; restored overlap as unused config; file-based model loading preference added in your engine.
Next (optional, safe): add model catalog + downloader + model selection UI; later add a sherpa-compatible engine so tiny/base ONNX can run.

**Implementation Based on WhisperIME:**
- InputMethodService subclass
- Custom keyboard layout (XML)
- Touch-and-hold mic button
- Haptic feedback
- Bluetooth headset support
- System-wide text injection

**Reference Implementation:**
```kotlin
class VoiceInputIME : InputMethodService() {
    override fun onCreateInputView(): View {
        return inflater.inflate(R.layout.keyboard_layout, null)
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        // Initialize components on-demand
        initializePipelineIfNeeded()
    }

    private fun insertTranscription(text: String) {
        currentInputConnection?.commitText(text, 1)
    }
}
```

---

## 6. Configuration Reference

### Current Settings (AppConfig.kt)

```kotlin
data class AudioConfig(
    val sampleRate: Int = 16000,
    val chunkSize: Int = 2048,
    val channels: Int = 1,
    val minAudioLengthSec: Float = 1.5f,
    val minProcessInterval: Float = 0.5f,

    // VAD Settings
    val vadMode: String = "silero",
    val vadThreshold: Float = 0.3f,
    val silenceDurationSec: Float = 1.5f,
    val maxChunkDurationSec: Float = 20.0f,    // FIXED: was 1.0s
    val overlapDurationSec: Float = 3.0f,      // NEW: overlap
    val enableVAD: Boolean = false              // NEW: manual control
)

data class TranscriptionConfig(
    val modelName: String = "whisper-small-int8",
    val language: String = "en",
    val translate: Boolean = false,
    val minChunkSizeBytes: Int = 24000          // ~0.75s minimum
)
```

### Why These Values?

**maxChunkDurationSec = 20.0s**
- Whisper trained on 30s clips, needs context
- 20s is safe margin below max
- Prevents catastrophic 1s chunking that caused 4x slowdown

**overlapDurationSec = 3.0s**
- Captures sentence boundaries
- Prevents mid-word cuts
- TextProcessor deduplicates overlapping text

**silenceDurationSec = 1.5s**
- Natural pause length in speech
- Not too short (cuts off slow speakers)
- Not too long (delays transcription)

**enableVAD = false**
- Focus on manual recording first
- VAD adds complexity (state management, false triggers)
- Once basic flow works, VAD is easy to add

---

## 7. Testing Strategy

### Unit Tests
```kotlin
// Test chunking logic
@Test
fun testChunkingWithOverlap() {
    val audio = generateTestAudio(60.0)  // 60s audio
    val chunks = pipeline.chunkAudio(audio,
        chunkSize = 20.0, overlap = 3.0)

    assertEquals(4, chunks.size)         // 3 full + 1 partial
    assertEquals(20.0, chunks[0].duration)
    assertEquals(3.0, chunks[0].overlap(chunks[1]))
}

// Test overlap detection
@Test
fun testOverlapTextMerging() {
    val text1 = "Hello world this is"
    val text2 = "this is a test"
    val merged = textProcessor.mergeWithOverlap(text1, text2)
    assertEquals("Hello world this is a test", merged)
}
```

### Integration Tests
```kotlin
// Test full pipeline with known audio
@Test
fun testJFKTranscription() {
    val audio = loadTestFile("jfk_11s.wav")
    val result = pipeline.transcribe(audio)

    assertTrue(result.rtf < 0.6)  // Performance
    assertTrue(result.text.contains("ask not"))  // Accuracy
}
```

### Performance Benchmarks
```kotlin
// Compare bare Whisper vs pipeline
@Test
fun testPerformanceRegression() {
    val audio = loadTestFile("jfk_11s.wav")

    val bareTime = measureTimeMillis { whisperEngine.transcribe(audio) }
    val pipelineTime = measureTimeMillis { pipeline.transcribe(audio) }

    // Pipeline should not be significantly slower than bare engine
    assertTrue(pipelineTime < bareTime * 1.5)  // Allow 50% overhead
}
```

---

## 8. Known Issues & Solutions

### Issue 1: Catastrophic 1-Second Chunking
**Problem**: Original config used 1s chunks, causing 4x slowdown and gibberish output
**Root Cause**: Whisper needs 10-30s context, 1s loses semantic information
**Solution**: Increased to 20s chunks with 3s overlap
**Status**: ✅ FIXED in AppConfig.kt (lines 25-27)

### Issue 2: VAD Breaking on Small Frames
**Problem**: VAD expected 512-sample frames, received incomplete chunks
**Symptoms**: "Ignoring 64 remaining bytes (incomplete VAD frame)" spam
**Solution**: Disabled VAD for Phase 1, will buffer properly in Phase 2
**Status**: ✅ FIXED (enableVAD = false)

### Issue 3: Memory Leaks in SileroVAD
**Problem**: Resources not cleaned if one cleanup step failed
**Solution**: Independent try-finally blocks for each resource
**Status**: ✅ FIXED in SileroVAD.kt (lines 419-446)

### Issue 4: Resource Leaks in WhisperEngine
**Problem**: ONNX tensors not closed on exceptions
**Solution**: Refactored with guaranteed cleanup in finally blocks
**Status**: ✅ FIXED in WhisperEngine.kt (lines 239-327)

---

## 9. Future Enhancements

### Near-Term (Phase 2)
- [ ] Real-time partial results display
- [ ] Background transcription service
- [ ] Multiple language support
- [ ] Custom vocabulary/proper nouns
- [ ] Audio visualization (waveform)

### Mid-Term (Phase 3)
- [ ] Full IME keyboard implementation
- [ ] Cloud model fallback (large model)
- [ ] Offline model downloads
- [ ] User dictation commands ("new line", "period")
- [ ] Punctuation restoration model

### Long-Term
- [ ] Speaker diarization (multi-speaker)
- [ ] Real-time translation
- [ ] Custom wake word detection
- [ ] Cross-app dictation service
- [ ] Accessibility features (hearing impaired)

---

## 10. Resources & References

### Models
- **Current**: `whisper-small-int8.onnx` (244M params, INT8 quantized)
- **Source**: RTranslator project (proven production quality)
- **Location**: `android/app/src/main/assets/models/`

### Reference Projects
- **WhisperIME**: Battle-tested keyboard implementation
  - Path: `C:\Users\Cicada38\Projects\whisperIME`
  - Key patterns: Touch-and-hold, haptic feedback, lazy init

- **RTranslator**: Production STT with ONNX Runtime
  - Path: `C:\Users\Cicada38\Projects\RTranslator`
  - Key patterns: APU acceleration, chunk overlap, memory management

### Documentation
- [ONNX Runtime Android Docs](https://onnxruntime.ai/docs/tutorials/mobile/)
- [Whisper Model Card](https://github.com/openai/whisper)
- [Android InputMethodService Guide](https://developer.android.com/reference/android/inputmethodservice/InputMethodService)

---

## Appendix: Performance Baseline

### BareWhisperBenchmark (Target)
```
Audio: jfk_11s.wav (11.1 seconds)
Processing: 5.4 seconds
RTF: 0.49x (2x real-time)
Output: "And so my fellow Americans ask not what your country can do for you, ask what you can do for your country."
Quality: ✅ Perfect transcription
```

### Old Pipeline (Broken)
```
Audio: jfk_11s.wav (11.1 seconds)
Processing: 46.8 seconds
RTF: 4.2x (4x slower than real-time!)
Output: "So Americans. K NOT! [BLANK_AUDIO] . your Country Country."
Quality: ❌ Complete gibberish
```

### New Pipeline (Target)
```
Audio: jfk_11s.wav (11.1 seconds)
Processing: < 6.5 seconds (target)
RTF: < 0.6x (target)
Output: Match BareWhisperBenchmark quality
Quality: ✅ Coherent sentences
```

---

**Last Updated**: 2025-10-11
**Next Review**: After Phase 1 completion
