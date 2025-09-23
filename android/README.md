# Voice Input Service - Android

> System-wide voice keyboard (IME) with advanced hallucination filtering and Silero VAD

**Status**: 🚧 Under Development (Phase 0 complete)

## Overview

Android port of the Voice Input Service, bringing desktop-quality voice transcription to mobile as an Input Method Editor (IME/keyboard).

## Features (Planned)

- ✨ Advanced hallucination filtering (40+ patterns)
- 🎯 Silero VAD for intelligent silence detection
- 🔄 Smart text processing with overlap detection
- ⌨️ System-wide keyboard integration
- 🎤 Continuous and manual recording modes
- 🌍 Multi-language support

## Development Status

See [PROGRESS.md](../PROGRESS.md) for current implementation status.

**Current Phase**: Setting up Android project structure

## Building from Source

> ⚠️ Project setup in progress. Build instructions coming soon.

### Prerequisites

- Android Studio Arctic Fox or later
- Android SDK 24+ (Android 7.0)
- Kotlin 1.9+
- Gradle 8.0+

### Setup

```bash
# Clone repository
git clone https://github.com/yourusername/STT
cd STT/android

# Open in Android Studio
# File → Open → select STT/android

# Wait for Gradle sync
# Build → Make Project
```

## Architecture

```
AudioRecord → AudioProcessor (VAD) → WhisperEngine → TextProcessor → IME
```

### Core Components (To Be Implemented)

1. **TextProcessor.kt** - Port of Python hallucination filtering logic
2. **SileroVAD.kt** - Voice activity detection using ONNX Runtime
3. **WhisperEngine.kt** - whisper.cpp JNI wrapper
4. **AudioProcessor.kt** - Audio buffering and processing pipeline
5. **VoiceInputIME.kt** - Input Method Editor implementation

## Contributing

See [Android Port Plan](../ANDROID_PORT_PLAN.md) for detailed implementation roadmap.

### Current Needs

- [ ] Android project setup
- [ ] Component porting (TextProcessor first)
- [ ] Whisper.cpp integration
- [ ] IME implementation

## Documentation

- [Port Plan](../ANDROID_PORT_PLAN.md) - Complete execution roadmap
- [Quick Start](../QUICKSTART.md) - Developer onboarding
- [Core Algorithms](../docs/core/) - Shared logic documentation

## License

MIT License - Same as desktop version