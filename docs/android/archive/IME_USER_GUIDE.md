# 🎙️ Voice Input IME - User Guide

## Overview

The Voice Input IME (Input Method Editor) is a system-level voice keyboard that allows you to dictate text into any app on your Android device. It uses on-device AI models for privacy and works offline.

---

## 🚀 Getting Started

### Installation & Setup

1. **Install the app** (build and install via Android Studio or `./gradlew installDebug`)
2. **Enable the keyboard**:
   - Go to **Settings** → **System** → **Languages & Input** → **On-screen keyboard** → **Manage keyboards**
   - Enable "Voice Input"
3. **Switch to Voice Input**:
   - Tap any text field
   - Tap the keyboard switcher icon (usually bottom-right)
   - Select "Voice Input"

---

## 📱 How to Use

### Recording Flow

1. **Tap the microphone button** to start recording
   - The button turns red 🔴
   - A timer shows elapsed time
   - Status: "🔴 Recording... 5s"

2. **Speak clearly** into your device
   - The microphone will capture your voice
   - Keep the device at a comfortable distance

3. **Tap the microphone again** to stop recording
   - Status changes to "⏳ Transcribing..."
   - Cancel button remains available if needed

4. **Text appears** in your input field
   - Success status: "✅ Ready" (with green checkmark)
   - Transcription time shown in logs

### Canceling

- **During recording**: Tap the red microphone button to stop and discard
- **During transcription**: Tap the cancel button to abort processing

---

## ⚠️ Limitations & Rules

### Recording Duration

| Limit | Value | What Happens |
|-------|-------|--------------|
| **Maximum Duration** | 60 seconds | Auto-stops with warning "Max duration reached (60s)" |
| **Warning Threshold** | Last 10 seconds | Timer turns red: "🔴 Recording... 5s remaining" |

**Best Practice**: Keep recordings under 30 seconds for faster transcription (~2-5 seconds processing time)

### Memory Limits

| Limit | Value | Purpose |
|-------|-------|---------|
| **Maximum Buffer** | 20 MB | Prevents memory bloat on device |

**What This Means**: For 16kHz 16-bit mono audio, 20MB ≈ ~80 seconds of audio (more than the 60s duration limit)

### Performance Expectations

| Recording Length | Transcription Time | Notes |
|------------------|-------------------|-------|
| 5-10 seconds | ~2 seconds | Optimal user experience |
| 15-20 seconds | ~3-4 seconds | Good user experience |
| 30 seconds | ~5 seconds | Acceptable for longer dictation |
| 60 seconds | ~10 seconds | Maximum supported duration |

*Times are approximate and vary by device hardware (APU/CPU performance)*

---

## ✅ Best Practices

### For Best Results

1. **Speak clearly and at a moderate pace**
   - Whisper model works best with natural speech
   - Avoid mumbling or speaking too fast

2. **Use in quiet environments**
   - Background noise can affect accuracy
   - Hold device 6-12 inches from mouth

3. **Keep recordings concise**
   - Under 30 seconds for best UX
   - Break long dictation into segments

4. **Wait for processing to complete**
   - Don't close the keyboard while "Transcribing..."
   - Cancel button is available if needed

5. **Check the status indicator**
   - 🔴 = Recording
   - ⏳ = Processing
   - ✅ = Ready
   - ❌ = Error (tap to retry)

---

## 🔧 Troubleshooting

### "Please wait, initializing..." appears for a long time

**Cause**: First-time model loading (Whisper + Silero VAD)  
**Solution**: Wait 5-10 seconds. Subsequent uses are faster.

### "Failed to initialize audio recorder"

**Cause**: Microphone permission denied or in use by another app  
**Solutions**:
- Grant microphone permission in Settings
- Close other apps using the microphone
- Restart the device

### "Transcription failed"

**Cause**: Model processing error  
**Solutions**:
- Try recording again
- Reduce background noise
- Ensure recording is not too short (<1 second)

### "Max duration reached (60s)"

**Cause**: Recording exceeded 60-second limit  
**Solution**: Your audio up to 60s was captured. Tap to transcribe, or record in shorter segments.

### Text doesn't appear in the field

**Cause**: Input connection lost (app closed text field)  
**Solution**: Tap the text field again to reopen the keyboard

---

## 🎨 Visual States

| State | Indicator | Description |
|-------|-----------|-------------|
| **Ready** | ✅ Green microphone | Tap to start recording |
| **Initializing** | ⏳ Animated dots | Loading models (first time) |
| **Recording** | 🔴 Red microphone + timer | Capturing audio |
| **Processing** | ⏳ "Transcribing..." | AI processing audio |
| **Success** | ✅ "Ready" | Text inserted, ready for next |
| **Error** | ❌ Red status | Something went wrong |

---

## 🔐 Privacy & Security

- **100% On-Device Processing**: All transcription happens locally on your device
- **No Internet Required**: Works completely offline
- **No Data Sent**: Your voice never leaves your device
- **No Cloud Services**: No third-party servers involved

---

## 🛠️ Technical Details

### Models Used

- **Whisper Small INT8 (ONNX)**: 161 MB speech-to-text model
- **Silero VAD v5 (ONNX)**: Voice activity detection (future feature)

### Audio Format

- **Sample Rate**: 16,000 Hz (16 kHz)
- **Bit Depth**: 16-bit
- **Channels**: Mono
- **Format**: PCM (Pulse Code Modulation)

### Acceleration

- **Primary**: APU (AI Processing Unit) via NNAPI
- **Fallback**: CPU if APU fails
- **Framework**: ONNX Runtime Mobile

---

## 🚧 Future Enhancements

- **Streaming Transcription**: Process audio in 30-second segments for unlimited recording
- **Real-time Display**: Show partial transcriptions as you speak
- **Settings Panel**: Configure model, language, and behavior
- **Custom Prompts**: Improve accuracy for specific domains
- **Punctuation Control**: Auto-punctuation toggle

See `IME_STREAMING_TRANSCRIPTION.md` for streaming implementation details.

---

## 📝 Quick Reference Card

```
┌─────────────────────────────────────────┐
│      Voice Input IME Cheat Sheet       │
├─────────────────────────────────────────┤
│  🎤 Tap → Start Recording               │
│  🔴 Tap → Stop Recording                │
│  ❌ Tap → Cancel                         │
│  ⏱️  Timer → Shows elapsed/remaining     │
│  ⚠️  Red Timer → Last 10s warning       │
│  ✅ Green → Ready to record again       │
├─────────────────────────────────────────┤
│  Max Duration: 60 seconds               │
│  Optimal Length: 15-30 seconds          │
│  Processing Time: ~2-5s for 15s audio   │
├─────────────────────────────────────────┤
│  100% On-Device • No Internet Required  │
│  Private • Secure • Offline             │
└─────────────────────────────────────────┘
```

---

## 📞 Support

If you encounter issues not covered here, check the logs:
```bash
adb logcat -s VoiceInputIME VoiceKeyboardView WhisperEngine AudioRecorder
```

Report issues with:
- Device model
- Android version
- Recording length
- Error message
- Logcat output

---

**Enjoy hands-free typing! 🎙️✨**

