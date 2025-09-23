# Whisper.cpp Integration

This project uses the **OFFICIAL** whisper.cpp Android JNI implementation, not third-party wrappers.

## Architecture

```
android/
├── app/                          # Main application
│   └── uses WhisperEngine.kt
├── whisperlib/                   # Official whisper.cpp library module
│   ├── whisper.cpp/             # Full whisper.cpp source (git submodule)
│   ├── src/main/
│   │   ├── jni/whisper/         # JNI bridge (jni.c + CMakeLists.txt)
│   │   └── java/com/whispercpp/whisper/
│   │       ├── LibWhisper.kt    # Kotlin wrapper
│   │       └── WhisperCpuConfig.kt
│   └── build.gradle.kts
```

## Why Official Implementation?

- **Direct from whisper.cpp team**: No middleman for updates
- **Full control**: We build whisper.cpp ourselves
- **Security**: Local inference only, no third-party dependencies
- **Latest features**: Direct access to whisper.cpp improvements

## Build Process

1. whisper.cpp source is cloned in `whisperlib/whisper.cpp/`
2. CMake builds native libraries from source
3. JNI bridge in `jni.c` exposes C++ functions to Kotlin
4. `LibWhisper.kt` provides high-level Kotlin API
5. `WhisperEngine.kt` in app uses the library

## Usage

```kotlin
// Initialize
val whisperContext = WhisperContext.createContextFromFile(modelPath)

// Transcribe
val audioFloat = AudioUtils.bytesToFloat(audioBytes)
val text = whisperContext.transcribeData(audioFloat, printTimestamp = false)

// Release
whisperContext.release()
```

## Model Files

Place GGML model files in:
- `android/app/src/main/assets/models/` (bundled with app)
- Or download to `context.filesDir` at runtime

Supported formats: `ggml-*.bin` (base, small, medium, large, etc.)

## Native Library Variants

The build produces optimized libraries for each architecture:
- `arm64-v8a`: ARMv8 with fp16 support
- `armeabi-v7a`: ARMv7 with NEON VFPv4
- `x86`, `x86_64`: Intel architectures

Runtime detection selects the best variant automatically.