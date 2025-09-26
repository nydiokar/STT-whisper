# Unaccounted Desktop Problems in Android Port

This document identifies critical problems and edge cases that were solved in the desktop implementation but are missing or inadequately handled in the Android port.

## 🚨 Critical Issues

### 1. Audio Sample Rate Mismatch Handling ❌ **MISSING**

**Problem:** Android assumes the requested sample rate will work, but devices may not support 16kHz or may use different native rates.

**Desktop Solution:**
```python
# desktop/voice_input_service/core/audio.py lines 54-63
if device_index is not None:
    try:
        device_info = self.py_audio.get_device_info_by_index(device_index)
        self.device_sample_rate = int(device_info.get('defaultSampleRate', sample_rate))
        self.logger.info(f"Device sample rate: {self.device_sample_rate}Hz")
    except:
        self.device_sample_rate = sample_rate
else:
    self.device_sample_rate = sample_rate
```

**Desktop Resampling:**
```python
# lines 134-147
if self.device_sample_rate != self.sample_rate:
    try:
        audio_array = np.frombuffer(self.audio_data, dtype=np.int16)
        resampled = self._resample(audio_array, self.device_sample_rate, self.sample_rate)
        result = resampled.astype(np.int16).tobytes()
        self.logger.debug(f"Resampled audio from {self.device_sample_rate}Hz to {self.sample_rate}Hz")
    except Exception as e:
        self.logger.error(f"Failed to resample audio: {e}")
        result = bytes(self.audio_data)
```

**Android Problem:** 
- No device sample rate detection
- No resampling if device uses different rate
- VAD and Whisper may receive wrong sample rate audio

**Fix Needed:**
```kotlin
// Add to AudioRecorder.kt
private fun detectDeviceSampleRate(): Int {
    // Check if requested rate is supported, fallback to supported rate
}

private fun resampleAudio(audioData: ByteArray, fromRate: Int, toRate: Int): ByteArray {
    // Implement resampling similar to desktop
}
```

### 2. Real-time Audio Callback Resampling ❌ **MISSING**

**Problem:** Audio chunks sent to VAD/processor may be wrong sample rate.

**Desktop Solution:**
```python
# lines 184-189
if self.device_sample_rate != self.sample_rate:
    audio_array = np.frombuffer(in_data, dtype=np.int16)
    resampled = self._resample(audio_array, self.device_sample_rate, self.sample_rate)
    in_data = resampled.astype(np.int16).tobytes()
self.on_data_callback(in_data)
```

**Android Problem:** No real-time resampling in audio stream.

**Fix Needed:**
```kotlin
// In audioStream() method
if (deviceSampleRate != sampleRate) {
    val resampled = resampleAudio(chunk, deviceSampleRate, sampleRate)
    emit(resampled)
} else {
    emit(chunk)
}
```

### 3. Audio Device Selection ❌ **MISSING**

**Problem:** Android uses default device only, no way to select specific microphone.

**Desktop Solution:**
```python
# desktop/voice_input_service/core/audio.py lines 70-81
def get_input_devices(self) -> Dict[int, str]:
    """Get all available input devices."""
    devices = {}
    for i in range(self.py_audio.get_device_count()):
        device_info = self.py_audio.get_device_info_by_index(i)
        if device_info['maxInputChannels'] > 0:
            devices[i] = device_info['name']
    return devices
```

**Android Problem:** No device enumeration or selection.

**Fix Needed:**
```kotlin
// Add device enumeration
fun getAvailableMicrophones(): List<MicrophoneInfo> {
    // Use AudioManager.getMicrophones() for API 23+
}
```

### 4. Device Compatibility Checking ❌ **MISSING**

**Problem:** No validation that device supports requested audio parameters.

**Desktop Solution:** PyAudio automatically handles device capabilities.

**Android Problem:** AudioRecord may fail silently or with poor quality.

**Fix Needed:**
```kotlin
private fun isSampleRateSupported(rate: Int): Boolean {
    val channelConfig = if (channels == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
    val minSize = AudioRecord.getMinBufferSize(rate, channelConfig, ENCODING)
    return minSize != AudioRecord.ERROR_BAD_VALUE && minSize != AudioRecord.ERROR
}
```

## ⚠️ Partially Missing Issues

### 5. Error Recovery and Buffer Clearing ⚠️ **PARTIALLY MISSING**

**Desktop Solution:**
```python
# desktop/voice_input_service/core/processing.py lines 218-224
except Exception as e:
    self.logger.error(f"Error in worker thread loop: {e}", exc_info=True)
    # Decide whether to break or continue on error
    # For now, log and continue, but clear buffer to prevent reprocessing bad data
    with self.buffer_lock:
        active_speech_buffer.clear()
        total_processed_bytes = 0
```

**Android Problem:** Less robust error handling, missing detailed logging.

**Fix Needed:**
```kotlin
catch (e: Exception) {
    Log.e(TAG, "Error in worker loop: ${e.message}", e)
    // Clear buffer to prevent reprocessing bad data
    bufferState = bufferState.cleared()
    // Add retry logic or graceful degradation
}
```

### 6. Whisper.cpp Error Handling ⚠️ **DIFFERENT APPROACH**

**Desktop Solution:**
```python
# desktop/voice_input_service/core/whisper_cpp.py lines 172-179
except subprocess.CalledProcessError as e:
    logger.error(f"whisper.cpp command failed with exit code {e.returncode}")
    logger.error(f"Command stdout: {e.stdout}")
    logger.error(f"Command stderr: {e.stderr}")
    result_data = {"error": f"Command failed: {e.stderr or e.stdout}"}
```

**Android Problem:** Different error handling due to direct bindings vs subprocess.

**Fix Needed:** Ensure Android captures equivalent diagnostic information.

### 7. Configuration Validation ⚠️ **PARTIALLY MISSING**

**Desktop Solution:**
```python
# desktop/voice_input_service/config.py lines 28-48
@field_validator('sample_rate')
@classmethod
def validate_sample_rate(cls, v: int) -> int:
    valid_rates = [8000, 16000, 22050, 44100, 48000]
    if v not in valid_rates:
        raise ValueError(f"Sample rate must be one of {valid_rates}, got {v}")
    return v
```

**Android Problem:** Runtime validation only, missing some edge case validations.

**Fix Needed:** Add comprehensive validation similar to Pydantic.

## 🔧 Implementation Priority

### High Priority (Critical for Functionality)
1. **Audio Sample Rate Handling** - Most critical, affects core functionality
2. **Real-time Resampling** - Required for VAD accuracy
3. **Device Compatibility** - Prevents silent failures

### Medium Priority (Quality of Life)
4. **Error Recovery** - Improves robustness
5. **Device Selection** - User experience
6. **Configuration Validation** - Prevents configuration errors

### Low Priority (Nice to Have)
7. **Enhanced Error Handling** - Better diagnostics

## 📝 Implementation Notes

### Audio Resampling Algorithm
The desktop uses simple linear interpolation:
```python
def _resample(self, audio_array: np.ndarray, from_rate: int, to_rate: int) -> np.ndarray:
    duration = len(audio_array) / from_rate
    time_old = np.linspace(0, duration, len(audio_array))
    time_new = np.linspace(0, duration, int(len(audio_array) * to_rate / from_rate))
    return np.interp(time_new, time_old, audio_array)
```

For Android, consider using a more sophisticated resampling library or implement proper interpolation.

### Device Detection Strategy
1. Try requested sample rate first
2. If fails, try common rates (16000, 44100, 48000)
3. Fall back to device default
4. Log the actual rate being used

### Error Recovery Strategy
1. Clear buffers on error to prevent reprocessing bad data
2. Log detailed error information
3. Implement retry logic for transient failures
4. Graceful degradation when possible

## 🧪 Testing Recommendations

1. **Test on devices with different sample rates** (8kHz, 44.1kHz, 48kHz)
2. **Test with different microphones** (built-in, external, Bluetooth)
3. **Test error conditions** (no microphone, permission denied, etc.)
4. **Test resampling accuracy** (compare with desktop output)
5. **Test VAD accuracy** with resampled audio

## 📚 References

- Desktop AudioRecorder: `desktop/voice_input_service/core/audio.py`
- Desktop Processing: `desktop/voice_input_service/core/processing.py`
- Desktop Config: `desktop/voice_input_service/config.py`
- Android AudioRecorder: `android/app/src/main/kotlin/com/voiceinput/core/AudioRecorder.kt`
- Android AudioProcessor: `android/app/src/main/kotlin/com/voiceinput/core/AudioProcessor.kt`
