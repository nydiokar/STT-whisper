# 🎯 Model Strategy: Solving the 322MB APK Bloat Problem

## The Problem

**Current situation**: All 6 ONNX models in assets = **322.23 MB** in APK!

```
Whisper_initializer.onnx          71 KB
Whisper_encoder.onnx              88.2 MB  ⚠️
Whisper_decoder.onnx             173.32 MB ⚠️ HUGE!
Whisper_cache_initializer.onnx    13.65 MB
Whisper_cache_initializer_batch.onnx  13.65 MB (unused)
Whisper_detokenizer.onnx           0.45 MB
```

**Problem**: APK will be **300+ MB** which is:
- ❌ Too large for Google Play (150MB soft limit)
- ❌ Users won't download it
- ❌ Waste of space if models never used

---

## ✅ SOLUTION: Download Models at Runtime

### Strategy: First-Run Download (Like RTranslator)

**How RTranslator does it**:
1. APK ships with **NO models** (or just tiny/base for testing)
2. First time app runs → shows download screen
3. Downloads models from GitHub releases (322 MB)
4. Stores in app's private storage
5. Done! Never download again

**Benefits**:
- ✅ Small APK (< 10 MB without models)
- ✅ Users choose when to download
- ✅ Can update models without app update
- ✅ Can offer model selection (tiny/base/small)

---

## 📋 Implementation Options

### Option 1: Runtime Download (RECOMMENDED for Production)
**Best for**: Production app

**Implementation**:
1. Remove models from `assets/`
2. Create download screen on first launch
3. Download from GitHub releases
4. Save to `context.filesDir` (private storage)
5. Load models from filesDir

**Code changes needed**:
```kotlin
// In OnnxWhisperEngine.kt
private fun loadEncoderSession() {
    // BEFORE: Load from assets
    // context.assets.open(encoderPath).use { ... }

    // AFTER: Load from filesDir
    val modelFile = File(context.filesDir, "Whisper_encoder.onnx")
    if (!modelFile.exists()) {
        throw Exception("Model not downloaded. Please run setup.")
    }
    encoderSession = ortEnvironment!!.createSession(modelFile.path, sessionOptions)
}
```

**Download UI**: Copy RTranslator's `DownloadFragment.kt`

---

### Option 2: Keep ONLY Required Models in Assets (QUICK TEST)
**Best for**: Quick testing/benchmarking NOW

**Keep only**:
```
✅ Whisper_initializer.onnx        71 KB
✅ Whisper_encoder.onnx            88.2 MB
✅ Whisper_decoder.onnx           173.32 MB
✅ Whisper_cache_initializer.onnx  13.65 MB
✅ Whisper_detokenizer.onnx         0.45 MB
❌ Whisper_cache_initializer_batch.onnx  13.65 MB  DELETE (not used)
```

**Result**: ~289 MB in assets (still large, but works for testing)

**Delete the batch model**:
```bash
rm "C:\Users\Cicada38\Projects\STT\android\app\src\main\assets\models\Whisper_cache_initializer_batch.onnx"
```

---

### Option 3: Asset Packs (Google Play Feature)
**Best for**: Apps distributed via Google Play

**How it works**:
1. Models go in "asset pack" (separate download)
2. Google Play handles download/delivery
3. Can be on-demand or install-time
4. Free for apps on Play Store

**Limitations**:
- Only works for Play Store apps
- Complex to set up
- Overkill for testing

---

## 🎯 Recommended Approach for YOU

### Phase 1: Testing (RIGHT NOW)
**Goal**: Get it working and benchmark

**Action**:
1. Keep models in assets for now (yes, big APK, but testing only)
2. Delete unused batch model to save 14 MB
3. Run benchmark to verify it works
4. Test on real device

**Why**: Fastest path to validation. APK size doesn't matter for dev testing.

```bash
# Delete unused model
rm "C:\Users\Cicada38\Projects\STT\android\app\src\main\assets\models\Whisper_cache_initializer_batch.onnx"
```

---

### Phase 2: Production (AFTER successful test)
**Goal**: Ship to users

**Action**: Implement runtime download like RTranslator
1. Remove models from assets
2. Add download screen (copy from RTranslator)
3. Download models on first run
4. Store in `context.filesDir`

---

## 🔧 Wiring Check: Is Everything Connected?

### ❌ NO - BareWhisperBenchmark still uses OLD engine!

**Current code** (line 5):
```kotlin
import com.voiceinput.core.WhisperEngine  // ❌ OLD!
```

**Needs**:
```kotlin
import com.voiceinput.onnx.OnnxWhisperEngine  // ✅ NEW!
```

### Files That Need Updating for Benchmark:

1. **BareWhisperBenchmark.kt** - Still uses WhisperEngine
2. **AudioTestActivity.kt** - Still uses WhisperEngine
3. **VoiceInputService.kt** - Still uses WhisperEngine
4. **StreamingPerformanceTest.kt** - Still uses WhisperEngine

---

## ✅ To Make Benchmark Work RIGHT NOW

### Step 1: Create ONNX Benchmark
```kotlin
// New file: OnnxWhisperBenchmark.kt
class OnnxWhisperBenchmark(private val context: Context) {
    suspend fun benchmarkWithJFKAudio(): BenchmarkResult = withContext(Dispatchers.IO) {
        val engine = OnnxWhisperEngine(context)

        // Initialize
        engine.initialize()

        // Load JFK audio
        val audioData = loadJFKAudio()

        // Benchmark
        val startTime = System.currentTimeMillis()
        val result = engine.transcribe(audioData)
        val elapsedMs = System.currentTimeMillis() - startTime

        // Return results
        BenchmarkResult(
            processingTime = elapsedMs,
            rtf = elapsedMs / (audioData.size / 32000f),
            text = result.text
        )
    }
}
```

### Step 2: Wire it to your test/activity
Whatever calls `BareWhisperBenchmark`, update it to use `OnnxWhisperBenchmark` instead.

---

## Summary: What You Should Do RIGHT NOW

### For TESTING (recommended):
1. ✅ **Keep models in assets** (289 MB APK is OK for dev testing)
2. ✅ **Delete batch model** (saves 14 MB, not used anyway)
3. ✅ **Create OnnxWhisperBenchmark.kt** (new benchmark class)
4. ✅ **Run benchmark** to verify ONNX works
5. ✅ **Compare with whisper.cpp benchmark**

### For PRODUCTION (later):
1. ❌ Remove models from assets
2. ❌ Implement runtime download
3. ❌ Show download progress UI

---

## Model Loading Comparison

### whisper.cpp (OLD):
```kotlin
val engine = WhisperEngine(context)
engine.initializeFromAssets("models/ggml-tiny-q5_1.bin")
```

### ONNX Runtime (NEW):
```kotlin
val engine = OnnxWhisperEngine(context)
engine.initialize()  // Loads all 6 models from assets automatically
```

**Key difference**: ONNX needs ALL 6 models, whisper.cpp only needs 1 file.

---

## APK Size Comparison

| Configuration | APK Size | Notes |
|--------------|----------|-------|
| whisper.cpp (tiny) | ~35 MB | Single model file |
| ONNX (all models in assets) | ~320 MB | ❌ TOO BIG |
| ONNX (runtime download) | ~8 MB | ✅ IDEAL |
| ONNX (Play Asset Packs) | ~8 MB | ✅ IDEAL (Play Store only) |

---

## Final Recommendation

**For immediate testing**: Keep models in assets, deal with large APK
**For production**: Implement runtime download (1-2 hours work)

The runtime download is how ALL major apps handle large ML models (ChatGPT, Google Translate, etc.).
