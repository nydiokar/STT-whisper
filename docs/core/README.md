# Core Algorithms Documentation

> Platform-agnostic algorithms powering Voice Input Service quality

This directory contains documentation for the core algorithms that make Voice Input Service unique, independent of platform implementation.

## 📚 Algorithm Documentation

### Text Processing
- [Hallucination Filtering](hallucination-filtering.md) - Pattern matching and removal
- [Text Appending Logic](text-appending.md) - Overlap detection and smart concatenation
- [Timestamp Removal](timestamp-removal.md) - Cleaning Whisper artifacts

### Voice Activity Detection
- [Silero VAD Overview](silero-vad.md) - Neural network-based VAD
- [Threshold Tuning](vad-tuning.md) - Configuring sensitivity
- [Frame Processing](vad-frames.md) - 30ms frame-based analysis

### Audio Processing
- [Buffer Management](buffer-management.md) - Audio chunk handling
- [Silence Detection](silence-detection.md) - Triggering transcription
- [Mode Comparison](processing-modes.md) - Continuous vs Manual

## 🔍 Algorithm Reference

### Hallucination Patterns (40+)

**Video artifacts**:
- "thanks for watching"
- "don't forget to subscribe"
- "like and subscribe"
- "see you in the next video"

**Generic closings**:
- "thank you"
- "goodbye"
- "see you"

**Greetings**:
- "hello everyone"
- "welcome"

Full list: See [hallucination-patterns.md](hallucination-patterns.md)

### VAD Configuration

```yaml
vad_threshold: 0.5        # 0.0 (sensitive) - 1.0 (strict)
frame_size_ms: 30         # Analysis window
silence_duration: 2.0     # Seconds to trigger processing
```

### Processing Pipeline

```
Audio Chunk
    ↓
VAD Analysis (Silero)
    ↓
Speech? → Buffer | Silence? → Check duration
    ↓                              ↓
Buffer full? → Process      Enough silence? → Process
    ↓                              ↓
Whisper Transcription          Whisper Transcription
    ↓                              ↓
Hallucination Filter ←──────────────┘
    ↓
Overlap Detection
    ↓
Smart Append
```

## 🛠️ Implementation Notes

### Python (Desktop)

**Location**: `desktop/voice_input_service/utils/`
- `text_processor.py` - Filtering & appending
- `silence_detection.py` - Silero VAD wrapper

### Kotlin (Android - In Progress)

**Location**: `android/app/src/main/kotlin/com/voiceinput/core/`
- `TextProcessor.kt` - Port of Python logic
- `SileroVAD.kt` - ONNX Runtime implementation

## 📖 How to Use This Documentation

### For Implementers

1. **Understand the algorithm** - Read the relevant doc
2. **Check reference implementation** - See Python code
3. **Port idiomatically** - Adapt to your platform
4. **Test against spec** - Ensure identical behavior

### For Contributors

1. **Document changes** - Update these docs when modifying algorithms
2. **Keep platforms in sync** - Core logic should match across platforms
3. **Add examples** - Show edge cases and expected behavior

## 🧪 Testing Core Algorithms

Each algorithm should have:
- **Unit tests** - Test pure logic
- **Edge case tests** - Handle weird inputs
- **Cross-platform validation** - Python and Kotlin should produce identical results

Example test cases in: `desktop/tests/test_text_processor.py`

## 📊 Performance Benchmarks

Coming soon - Will document:
- VAD latency
- Filtering performance
- Memory usage
- Accuracy metrics

## 🔗 Related Documentation

- [Desktop Architecture](../desktop/architecture/README.md)
- [Android Architecture](../android/architecture.md) (coming soon)
- [API Documentation](../desktop/api/README.md)

## 📝 TODO

- [ ] Create detailed algorithm docs (files listed above)
- [ ] Add flowcharts and diagrams
- [ ] Document edge cases
- [ ] Add performance metrics
- [ ] Cross-reference implementations