# Whisper.cpp Android Integration Guide

> How to integrate whisper.cpp for on-device transcription

## 📋 Current Status

- ✅ AudioRecorder ready (records PCM 16-bit @ 16kHz)
- ⏳ WhisperEngine (needs whisper.cpp library)
- ⏳ Model files (need to download)

## 🔧 Integration Options

### Option A: Use whisper.android Library (RECOMMENDED)

**Fastest approach** - Use existing Android wrapper:

```gradle
// build.gradle.kts (app)
dependencies {
    implementation("com.whispercpp:whisper:1.5.4")
    // OR check for latest at: https://github.com/ggerganov/whisper.cpp
}
```

**Pros:**
- ✅ Pre-built JNI bindings
- ✅ Ready to use
- ✅ Maintained by whisper.cpp team

**Cons:**
- ⚠️ May not exist or be outdated
- ⚠️ Check Maven Central first

### Option B: Build JNI Bindings from Source

**Full control** - Build whisper.cpp yourself:

1. **Add whisper.cpp as submodule:**
   ```bash
   cd android
   git submodule add https://github.com/ggerganov/whisper.cpp libs/whisper.cpp
   ```

2. **Create JNI wrapper module:**
   ```
   android/whisper-jni/
   ├── CMakeLists.txt
   ├── src/main/cpp/
   │   └── whisper_jni.cpp
   └── build.gradle.kts
   ```

3. **Configure CMake:**
   ```cmake
   cmake_minimum_required(VERSION 3.18)
   project(whisper-jni)

   add_subdirectory(../libs/whisper.cpp whisper)

   add_library(whisper-jni SHARED
       src/main/cpp/whisper_jni.cpp
   )

   target_link_libraries(whisper-jni
       whisper
       log
       android
   )
   ```

**Pros:**
- ✅ Full control over build
- ✅ Latest whisper.cpp features
- ✅ Custom optimizations

**Cons:**
- ⏱️ Takes time to set up
- 🔨 Requires NDK

### Option C: Use Existing Android Implementations

**Check these projects:**

1. **whisper.android** (by chidiwilliams)
   - https://github.com/chidiwilliams/whisper.android
   - Well-maintained Android wrapper
   - Includes example app

2. **WhisperInput** (by alex-vt)
   - https://github.com/alex-vt/WhisperInput
   - Full IME implementation
   - Can extract their whisper integration

## 📥 Model Files

### Where to Get Models

Download from Hugging Face:
```
https://huggingface.co/ggerganov/whisper.cpp/tree/main
```

**Recommended models for Android:**

| Model | Size | Quality | Speed | Use Case |
|-------|------|---------|-------|----------|
| `ggml-tiny.en.bin` | 75 MB | Basic | Fast | Testing |
| `ggml-base.en.bin` | 142 MB | Good | Fast | Recommended |
| `ggml-small.en.bin` | 466 MB | Better | Medium | High quality |

### Model Storage Options

**Option 1: Bundle in Assets** (tiny model only)
```
android/app/src/main/assets/models/
└── ggml-tiny.en.bin  (bundle with app)
```

**Option 2: Download on First Run** (recommended for larger models)
```kotlin
// Store in app internal storage
val modelsDir = File(context.filesDir, "models")
val modelFile = File(modelsDir, "ggml-base.en.bin")

// Download if not exists
if (!modelFile.exists()) {
    downloadModel(
        url = "https://huggingface.co/.../ggml-base.en.bin",
        dest = modelFile
    )
}
```

## 🔌 WhisperEngine Implementation Plan

### 1. Define Interface

```kotlin
interface TranscriptionEngine {
    suspend fun initialize(modelPath: String): Boolean
    suspend fun transcribe(audioData: ByteArray): TranscriptionResult
    fun release()
}

data class TranscriptionResult(
    val text: String,
    val language: String,
    val segments: List<Segment>
)

data class Segment(
    val id: Int,
    val start: Float,
    val end: Float,
    val text: String
)
```

### 2. WhisperEngine Implementation

```kotlin
class WhisperEngine(private val context: Context) : TranscriptionEngine {

    private var whisperContext: WhisperContext? = null

    override suspend fun initialize(modelPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                whisperContext = WhisperContext.createContext(modelPath)
                whisperContext != null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Whisper", e)
                false
            }
        }
    }

    override suspend fun transcribe(audioData: ByteArray): TranscriptionResult {
        // Convert bytes to float
        val audioFloat = AudioUtils.bytesToFloat(audioData)

        // Call whisper.cpp via JNI
        val result = whisperContext?.transcribeAudio(audioFloat)
            ?: throw IllegalStateException("Whisper not initialized")

        // Parse result and return
        return parseWhisperResult(result)
    }

    override fun release() {
        whisperContext?.release()
        whisperContext = null
    }
}
```

### 3. Model Management

```kotlin
class ModelManager(private val context: Context) {

    suspend fun downloadModel(modelName: String): File {
        val modelsDir = File(context.filesDir, "models")
        modelsDir.mkdirs()

        val modelFile = File(modelsDir, "$modelName.bin")

        if (modelFile.exists()) {
            return modelFile
        }

        // Download from Hugging Face
        val url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$modelName.bin"

        withContext(Dispatchers.IO) {
            // Use OkHttp or DownloadManager
            downloadFile(url, modelFile)
        }

        return modelFile
    }

    fun getAvailableModels(): List<String> {
        val modelsDir = File(context.filesDir, "models")
        return modelsDir.listFiles()
            ?.filter { it.extension == "bin" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }
}
```

## ⚡ Quick Start (When Ready)

### Step 1: Choose Integration Method

**For now, create placeholder:**
```kotlin
// WhisperEngine.kt (stub for Phase 2)
class WhisperEngine {
    // TODO: Implement once we choose integration method
    suspend fun transcribe(audio: ByteArray): String {
        return "[Whisper placeholder - integrate whisper.cpp]"
    }
}
```

### Step 2: Add to Gradle (when library chosen)

```kotlin
// android/app/build.gradle.kts
android {
    // Enable NDK if building from source
    ndkVersion = "25.1.8937393"

    defaultConfig {
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }
}

dependencies {
    // Option A: Use library (if exists)
    implementation("com.whispercpp:whisper:1.5.4")

    // Option B: Build from source
    // implementation(project(":whisper-jni"))
}
```

### Step 3: Test

```kotlin
// In test activity
val engine = WhisperEngine(context)
engine.initialize("path/to/model.bin")

val audio = audioRecorder.stop()
val result = engine.transcribe(audio)

println("Transcription: ${result.text}")
```

## 🎯 Recommended Approach for Now

**For Phase 2 completion:**

1. ✅ Create WhisperEngine stub with interface
2. ✅ Implement model management (download logic)
3. ⏸️ Actual integration (requires choosing Option A/B/C)
4. ✅ Can complete Phase 2 with stub, integrate fully in Phase 3-4

**This allows us to:**
- Complete audio pipeline
- Test with mock transcription
- Integrate real whisper.cpp when Android project is fully set up

## 📚 Resources

- [whisper.cpp GitHub](https://github.com/ggerganov/whisper.cpp)
- [whisper.android wrapper](https://github.com/chidiwilliams/whisper.android)
- [Hugging Face models](https://huggingface.co/ggerganov/whisper.cpp)
- [Android NDK guide](https://developer.android.com/ndk/guides)

---

**Current Status**: Documented integration options, ready for implementation when Android Studio project is set up.