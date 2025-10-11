# Development Notes & Future Work

**Last Updated:** 2025-10-10

This document contains:
- What to work on next (IME development)
- Known issues and limitations
- Performance considerations
- Future enhancements

---

## 🎯 Next Phase: IME Implementation

### What is an IME?
**Input Method Editor** - A system-level keyboard that can replace the default keyboard in any app (WhatsApp, Chrome, Notes, etc.).

### Goal
Build a "lazy keyboard" where users speak instead of type. The voice input should work everywhere, just like typing.

### Research Required

**Android IME Basics:**
1. How does `InputMethodService` work?
2. How to register as system input method?
3. How does `InputConnection` insert text?
4. What's the IME lifecycle?

**Reference Apps to Study:**
- Google Gboard (voice input button)
- WhisperInput (open source IME)
- Voice Input (simple voice keyboard)

**Useful Resources:**
- [InputMethodService docs](https://developer.android.com/reference/android/inputmethodservice/InputMethodService)
- [Creating an IME guide](https://developer.android.com/guide/topics/text/creating-input-method)
- WhisperInput GitHub (see how they structure IME)

### Implementation Plan

**Step 1: Basic IME Structure (Day 1)**
```kotlin
// Create VoiceInputIME.kt
class VoiceInputIME : InputMethodService() {
    override fun onCreateInputView(): View {
        // Create keyboard layout with microphone button
    }

    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        // Called when keyboard opens
    }

    override fun onFinishInput() {
        // Called when keyboard closes
    }
}
```

**Step 2: Manifest Declaration**
```xml
<service
    android:name=".ime.VoiceInputIME"
    android:permission="android.permission.BIND_INPUT_METHOD"
    android:exported="true">
    <intent-filter>
        <action android:name="android.view.InputMethod" />
    </intent-filter>
    <meta-data
        android:name="android.view.im"
        android:resource="@xml/method" />
</service>
```

**Step 3: Keyboard Layout (Day 2)**
- Microphone button (main action)
- Recording indicator (visual feedback)
- Text preview area (show transcription before commit)
- Cancel/Delete buttons
- Settings button

**Step 4: Connect to VoiceInputPipeline (Day 3)**
```kotlin
class VoiceInputIME : InputMethodService() {
    private lateinit var pipeline: VoiceInputPipeline

    override fun onCreate() {
        super.onCreate()
        // Initialize pipeline
        pipeline = VoiceInputPipeline(...)
    }

    private fun startRecording() {
        lifecycleScope.launch {
            pipeline.startListening()
            // Show recording UI
        }
    }

    private fun stopRecording() {
        lifecycleScope.launch {
            val text = pipeline.stopListening()
            // Insert text into text field
            currentInputConnection?.commitText(text, 1)
        }
    }
}
```

**Step 5: Text Insertion**
```kotlin
private fun insertText(text: String) {
    val ic = currentInputConnection ?: return
    ic.beginBatchEdit()
    ic.commitText(text, 1)
    ic.endBatchEdit()
}
```

**Step 6: Testing**
- Enable IME in device settings
- Switch to voice keyboard in any app
- Test in: WhatsApp, Chrome, Notes, Gmail
- Verify text insertion works correctly

---

## ⚠️ Known Issues & Limitations

### Critical (Must Fix Eventually)

**1. No Audio Sample Rate Mismatch Handling**
- **Problem:** Assumes device supports 16kHz, but some devices don't
- **Impact:** VAD and Whisper may receive wrong sample rate audio
- **Fix:** Add sample rate detection and resampling
```kotlin
// Add to AudioRecorder.kt
private fun detectDeviceSampleRate(): Int {
    // Try requested rate, fallback to supported rates
    val rates = listOf(16000, 44100, 48000, 22050, 8000)
    for (rate in rates) {
        if (isSampleRateSupported(rate)) return rate
    }
    return 16000 // fallback
}

private fun isSampleRateSupported(rate: Int): Boolean {
    val minSize = AudioRecord.getMinBufferSize(
        rate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    return minSize != AudioRecord.ERROR_BAD_VALUE && minSize != AudioRecord.ERROR
}
```

**2. No Microphone Device Selection**
- **Problem:** Always uses default microphone
- **Impact:** Can't switch to external/Bluetooth mic
- **Fix:** Implement device enumeration
```kotlin
fun getAvailableMicrophones(): List<MicrophoneInfo> {
    val audioManager = getSystemService(AudioManager::class.java)
    return audioManager.microphones // API 23+
}
```

### Medium Priority (Nice to Have)

**3. APK Size (275MB with models in assets)**
- **Problem:** Too large for Google Play
- **Solution:** Runtime model download (like RTranslator)
- **Implementation:**
  1. Ship APK without models
  2. Download on first run from GitHub releases
  3. Store in `context.filesDir`
  4. Load from filesDir instead of assets

**Code Changes:**
```kotlin
// In WhisperEngine.kt
private fun loadEncoderSession() {
    // BEFORE: Load from assets
    // context.assets.open(encoderPath).use { ... }

    // AFTER: Load from filesDir
    val modelFile = File(context.filesDir, "Whisper_encoder.onnx")
    if (!modelFile.exists()) {
        throw Exception("Model not downloaded. Run setup first.")
    }
    encoderSession = ortEnvironment!!.createSession(modelFile.path, sessionOptions)
}
```

**Download UI:** Copy RTranslator's `DownloadFragment.kt`

**4. English-Only Support**
- **Problem:** Hardcoded English token
- **Solution:** Add language parameter
```kotlin
// In WhisperEngine.kt
private fun getLanguageTokenId(language: String): Int {
    return when (language) {
        "en" -> 50259
        "es" -> 50260
        "fr" -> 50261
        // Add more languages...
        else -> 50259 // default to English
    }
}
```

### Low Priority (Future)

**5. Limited APU Usage (8/573 nodes)**
- MediaTek NPU limitation, not much we can do
- Still 6x faster than CPU-only

**6. No Batch Processing**
- Not needed for single transcription use case

**7. Missing Tensor Cleanup**
- ONNX Runtime handles automatically
- Monitor for memory leaks if issues arise

---

## 🚀 Performance Considerations

### Current Performance
- **RTF:** 0.44x (faster than real-time)
- **11s audio:** ~4.8s processing
- **5min audio:** Would take ~2.2 minutes to process

### Bottleneck Analysis
- 99% of time in encoder forward pass (matrix multiplication)
- APU helps but limited (8/573 nodes)
- Cannot optimize further without:
  - Better hardware (high-end Snapdragon)
  - Different model (smaller/quantized)
  - Cloud offloading

### For IME Use Case
**Acceptable:** Short utterances (5-10 seconds) process in 2-4 seconds
**Not Acceptable:** Real-time dictation (lag would be noticeable)

**Solution:** Set user expectations
- Show "Processing..." with progress
- Disable keyboard until transcription complete
- Consider cloud fallback for long audio

---

## 🔮 Future Enhancements

### Phase 2: User Experience
- [ ] Model download on first run
- [ ] Multiple model size options (tiny/base/small)
- [ ] Language selection UI
- [ ] Confidence threshold setting
- [ ] Hallucination pattern customization

### Phase 3: Advanced Features
- [ ] Continuous mode (auto-detect sentence boundaries)
- [ ] Punctuation commands ("comma", "period", "new line")
- [ ] Voice commands ("delete that", "send", "cancel")
- [ ] Speaker adaptation (learn user's voice)
- [ ] Offline dictionary for OOV words

### Phase 4: Optimization
- [ ] Model quantization experiments (INT4)
- [ ] TensorFlow Lite comparison benchmark
- [ ] Cloud API hybrid mode
- [ ] Streaming inference (partial results)

### Phase 5: Production
- [ ] Crash reporting (Firebase Crashlytics)
- [ ] Usage analytics (respecting privacy)
- [ ] A/B testing framework
- [ ] Beta testing program
- [ ] Google Play release

---

## 🧪 Testing Strategy

### Current Tests
- ✅ BareWhisperBenchmark - Pure ONNX performance
- ✅ StreamingPerformanceTest - Full pipeline
- ✅ AudioTestActivity - Interactive UI

### Tests Needed for IME
- [ ] IME lifecycle tests
- [ ] Text insertion in various apps (WhatsApp, Chrome, Gmail)
- [ ] Different text field types (single line, multi-line, password)
- [ ] Edge cases (empty input, very long input, special characters)
- [ ] Memory leak tests (long sessions)
- [ ] Battery usage tests
- [ ] Concurrent keyboard switches

### Test Devices
- Mid-range Samsung (current test device)
- High-end Snapdragon (for performance comparison)
- Low-end device (minimum spec validation)
- Different Android versions (7.0 - 14.0)

---

## 📊 Metrics to Track

### Performance Metrics
- RTF (Real-Time Factor)
- Processing time per audio length
- Memory usage during inference
- Battery drain per hour of use
- APK size

### Quality Metrics
- Word Error Rate (WER)
- Hallucination frequency
- VAD accuracy (false positives/negatives)
- User satisfaction scores

### Usage Metrics (Privacy-Respecting)
- Average session duration
- Transcription success rate
- Crash rate
- Model download success rate

---

## 🐛 Debugging Tips

### Check ONNX Inference
```
I/WhisperEngine: ⚡ NNAPI (APU) acceleration enabled for encoder
I/WhisperEngine: ✅ Encoder model loaded
I/WhisperEngine: Encoding: 1400ms
I/WhisperEngine: RTF: 0.44x
```

### Check VAD
```
I/AudioProcessor: 🎤 Speech detected in chunk X
I/AudioProcessor: Processing chunk: 8000 bytes (silence)
```

### Check Pipeline
```
I/VoiceInputPipeline: Pipeline started with VAD
I/VoiceInputPipeline: Transcription #5: X chars (500ms)
I/VoiceInputPipeline: 🏁 Pipeline stopped. Performance: 10 transcriptions, avg 450ms
```

### Common Issues
1. **No output:** Check microphone permission
2. **Slow performance:** Check APU enabled in logs
3. **Empty transcription:** Check audio format (must be 16kHz PCM)
4. **Hallucinations:** Check TextProcessor is filtering
5. **Memory errors:** Models too large for device

---

## 📚 Useful Code Snippets

### Get Device Info
```kotlin
val runtime = Runtime.getRuntime()
Log.i(TAG, "Cores: ${runtime.availableProcessors()}")
Log.i(TAG, "Max Memory: ${runtime.maxMemory() / (1024 * 1024)}MB")
Log.i(TAG, "Free Memory: ${runtime.freeMemory() / (1024 * 1024)}MB")
```

### Monitor Memory
```kotlin
private fun logMemory(tag: String) {
    val runtime = Runtime.getRuntime()
    val used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    val max = runtime.maxMemory() / (1024 * 1024)
    Log.i(TAG, "[$tag] Memory: $used / $max MB (${used * 100 / max}%)")
}
```

### Benchmark Template
```kotlin
suspend fun benchmark(audioData: ByteArray): BenchmarkResult {
    val startTime = System.currentTimeMillis()

    val result = whisperEngine.transcribe(audioData)

    val elapsedMs = System.currentTimeMillis() - startTime
    val audioDurationSec = audioData.size / (16000 * 2).toFloat()
    val rtf = elapsedMs / (audioDurationSec * 1000)

    return BenchmarkResult(
        processingTimeMs = elapsedMs,
        audioDurationSec = audioDurationSec,
        rtf = rtf,
        text = result.text
    )
}
```

---

## 🔗 Important Links

**Android Documentation:**
- [InputMethodService](https://developer.android.com/reference/android/inputmethodservice/InputMethodService)
- [Creating an IME](https://developer.android.com/guide/topics/text/creating-input-method)
- [AudioRecord](https://developer.android.com/reference/android/media/AudioRecord)

**ONNX Runtime:**
- [Android Guide](https://onnxruntime.ai/docs/tutorials/mobile/android.html)
- [Performance Tuning](https://onnxruntime.ai/docs/performance/)

**Reference Projects:**
- [RTranslator](https://github.com/niedev/RTranslator) - ONNX Whisper on Android
- [whisper.android](https://github.com/chidiwilliams/whisper.android) - whisper.cpp wrapper
- [WhisperInput](https://github.com/alex-vt/WhisperInput) - IME structure reference

---

## 📝 Decision Log

### Why ONNX over whisper.cpp?
- 6x performance improvement with APU acceleration
- Proven by RTranslator project
- Better mobile optimization

### Why Single SMALL Model?
- Only model size available from RTranslator
- Good balance of accuracy vs speed
- 0.44x RTF is acceptable for most use cases

### Why No Model Selection?
- RTranslator only provides SMALL model
- Would need to convert tiny/base to ONNX ourselves
- Can add later if needed

### Why Assets Instead of Runtime Download?
- Faster for testing and benchmarking
- Runtime download planned for production
- 275MB acceptable for development

---

**Questions? Check PROJECT_STATUS.md or search codebase!**
