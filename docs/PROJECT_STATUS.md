# Voice Input STT - Project Status

**Last Updated:** 2025-10-10
**Status:** ✅ ONNX Migration Complete - Ready for IME Development

---

## 🎯 What Is This Project?

A voice-to-text Android app using **ONNX Runtime with Samsung AI chip acceleration** for fast speech recognition. Goal: Build an IME (Input Method Editor) - a "lazy keyboard" where you speak instead of type.

---

## 📊 Current State (October 2025)

### ✅ What Works

**ONNX Whisper Integration:**
- Model: Whisper SMALL INT8 (244M parameters)
- Performance: 0.44x RTF (~4.8s for 11s audio) - 6x faster than old whisper.cpp
- Hardware: NNAPI acceleration via Samsung APU (MediaTek)
- Quality: Perfect transcription, no hallucinations

**Full Pipeline:**
- AudioRecorder → AudioProcessor (VAD) → WhisperEngine (ONNX) → TextProcessor → Output
- Silero VAD for voice activity detection
- Real-time streaming support
- File processing capability
- Hallucination filtering working

**Tests:**
- BareWhisperBenchmark - Performance testing ✅
- StreamingPerformanceTest - Pipeline validation ✅
- AudioTestActivity - Interactive UI testing ✅

### 🎛️ Architecture

```
[Microphone/File]
       ↓
[AudioRecorder] → 16kHz PCM audio chunks
       ↓
[AudioProcessor] → VAD-based buffering
       ↓
[SileroVAD] → Speech/silence detection
       ↓
[WhisperEngine] → ONNX Runtime inference (APU accelerated)
       ↓
[TextProcessor] → Hallucination filtering
       ↓
[Output] → Clean transcribed text
```

---

## 📁 Key Files

### Core Engine
- `android/app/src/main/kotlin/com/voiceinput/core/WhisperEngine.kt` - ONNX Whisper (drop-in replacement)
- `android/app/src/main/kotlin/com/voiceinput/core/AudioProcessor.kt` - VAD + buffering
- `android/app/src/main/kotlin/com/voiceinput/core/VoiceInputPipeline.kt` - Full pipeline
- `android/app/src/main/kotlin/com/voiceinput/core/SileroVAD.kt` - Voice activity detection
- `android/app/src/main/kotlin/com/voiceinput/core/TextProcessor.kt` - Hallucination filtering

### Helpers
- `android/app/src/main/kotlin/com/voiceinput/onnx/TensorUtils.kt` - ONNX tensor operations
- `android/app/src/main/kotlin/com/voiceinput/onnx/OnnxUtils.kt` - Math utils (softmax, argmax)

### Testing
- `android/app/src/main/kotlin/com/voiceinput/BareWhisperBenchmark.kt` - Performance benchmark
- `android/app/src/main/kotlin/com/voiceinput/StreamingPerformanceTest.kt` - Pipeline test
- `android/app/src/main/kotlin/com/voiceinput/AudioTestActivity.kt` - UI testing

### Models (assets/models/)
- `Whisper_initializer.onnx` (71 KB)
- `Whisper_encoder.onnx` (88.2 MB) - APU accelerated
- `Whisper_decoder.onnx` (173.32 MB)
- `Whisper_cache_initializer.onnx` (13.65 MB)
- `Whisper_detokenizer.onnx` (0.45 MB)
- `silero_vad.onnx` - VAD model

**Total:** ~275 MB

---

## 🔧 Dependencies (build.gradle.kts)

```kotlin
// ONNX Runtime
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.0")
implementation("com.microsoft.onnxruntime:onnxruntime-extensions-android:0.12.4")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
```

---

## 🚀 Next Phase: IME Development

### Goal
Transform app into a system keyboard (IME) for voice-to-text input in any app (WhatsApp, Chrome, Notes, etc.).

### What Needs to be Built

1. **IME Service**
   - Extend `InputMethodService`
   - Keyboard layout with microphone button
   - Recording lifecycle management

2. **Integration**
   - Connect VoiceInputPipeline to IME
   - Text insertion via `InputConnection`
   - Handle different text field contexts

3. **UX Design**
   - Mode switching (voice vs keyboard)
   - Recording indicator
   - Text preview area
   - Error handling UI

4. **Permissions**
   - `BIND_INPUT_METHOD` permission
   - IME manifest declaration
   - User setup guide

### Research Needed
- Android IME APIs and lifecycle
- How to register as system input method
- InputConnection best practices
- Compatibility across different apps

See **DEVELOPMENT_NOTES.md** for detailed implementation plan.

---

## 📈 Performance Summary

### Before (whisper.cpp)
- RTF: ~2.9x (31.7s for 11s audio)
- Model: Base Q5_1
- CPU-only processing

### After (ONNX Runtime)
- RTF: ~0.44x (4.8s for 11s audio)
- Model: Small INT8
- APU acceleration (8/573 nodes on MediaTek)

**Improvement:** ~6x faster!

---

## ⚠️ Known Limitations

1. **Model Selection**
   - Only SMALL model available (RTranslator limitation)
   - Cannot switch between tiny/base/small
   - For different models, need to find/convert to ONNX

2. **APU Acceleration**
   - Only 8/573 nodes on APU (MediaTek limitation)
   - Still faster than CPU-only despite limited usage

3. **APK Size**
   - ~275 MB in assets (acceptable for testing)
   - **Production:** Implement runtime download like RTranslator

4. **Language Support**
   - English-only currently
   - Can be extended to other languages (architecture supports it)

---

## 🧪 How to Test

### Run Benchmark
```kotlin
val benchmark = BareWhisperBenchmark(context)
val result = benchmark.benchmarkWithJFKAudio()
// Expected: ~4-5s for 11s audio (0.44x RTF)
```

### Test Full Pipeline
Use AudioTestActivity:
1. Grant microphone permission
2. Click "BARE WHISPER BENCHMARK" for pure ONNX test
3. Click "Test Streaming Performance" for full pipeline
4. Click "Live JFK Demo" for file processing

### Check Logs
```
I/WhisperEngine: ⚡ Encoding: 1400ms
I/WhisperEngine: Decoding: 180ms (45 tokens)
I/WhisperEngine: Total time: 4800ms
I/WhisperEngine: RTF: 0.44x
```

---

## 🐛 Potential Issues (From Code Review)

Most issues from old whisper.cpp are **not relevant** for ONNX, but keep in mind:

### Resolved
- ✅ Decoder loop bug (fixed during migration)
- ✅ Model loading (all 5 ONNX models working)
- ✅ Thread utilization (ONNX handles internally)

### Still Relevant
- ⚠️ English-only (needs language parameter support)
- ⚠️ No audio sample rate mismatch handling (AudioRecorder assumes 16kHz works)
- ⚠️ No device selection (uses default microphone)

### Not Critical for Current Phase
- Batch processing support (not needed for single transcription)
- Session optimization levels (already optimal)
- Tensor cleanup (ONNX handles automatically)

---

## 📚 Documentation

### Essential Docs (READ THESE)
- **PROJECT_STATUS.md** (this file) - Current state, what works, what's next
- **DEVELOPMENT_NOTES.md** - How to build features, known issues, future work
- **docs/ONNX_CLEANUP_COMPLETE.md** - What was cleaned up in migration

### Archived (for reference only)
- `CRITICAL_ISSUES_FOUND.md` - Old whisper.cpp issues (mostly not relevant)
- `ANDROID_PORT_PLAN.md` - Initial port plan (superseded by current state)
- `docs/PERFORMANCE_INVESTIGATION.md` - Old whisper.cpp performance analysis
- `docs/MIGRATION_TO_ONNX_RUNTIME.md` - Migration planning doc
- `docs/ONNX_MIGRATION_COMPLETE.md` - Initial migration completion
- `MODEL_STRATEGY.md` - Model download strategy discussion
- `docs/possible_unaccounted.md` - Desktop vs Android differences

---

## 🎯 Quick Start (Coming back after a break)

1. **Check current phase:** IME Development (see DEVELOPMENT_NOTES.md)
2. **Pull latest:** `git pull`
3. **Open Android Studio:** Sync Gradle
4. **Run tests:** Check BareWhisperBenchmark still works
5. **Start coding:** Follow IME implementation plan

---

## 📞 Important Context

### Why ONNX?
- Samsung AI chip (APU) acceleration via NNAPI
- 6x faster than old whisper.cpp
- Proven by RTranslator project

### Why This Architecture?
- VAD prevents processing silence
- TextProcessor filters hallucinations
- Pipeline design allows streaming

### Design Decisions
- Kotlin (not Java)
- Coroutines + Flow (not RxJava)
- ONNX Runtime (not TensorFlow Lite)
- Single SMALL model (only one available)

---

## ✅ Success Criteria Achieved

- [x] ONNX migration complete
- [x] 6x performance improvement
- [x] Perfect transcription quality
- [x] Full pipeline working
- [x] VAD integration
- [x] File and streaming support
- [x] Comprehensive testing
- [ ] IME implementation (next phase)

---

**Ready to build the IME! 🚀**
