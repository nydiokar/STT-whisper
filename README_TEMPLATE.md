# Voice Input Service

> **High-quality voice-to-text with advanced hallucination filtering and intelligent silence detection**

A multi-platform voice input solution featuring superior transcription quality through advanced text processing and voice activity detection.

## 🌟 What Makes This Special

Unlike basic Whisper wrappers, this service includes:

- ✨ **Advanced Hallucination Filtering** - Removes common Whisper artifacts ("thanks for watching", timestamps, etc.)
- 🎯 **Silero VAD Integration** - State-of-the-art voice activity detection
- 🔄 **Smart Text Processing** - Intelligent overlap detection and text appending
- 🎤 **Multiple Modes** - Continuous (auto-pause) and manual recording modes
- 🌍 **Multi-language Support** - Works with all Whisper-supported languages

## 📱 Platforms

### Desktop (Windows/macOS/Linux)
Real-time voice-to-text service with system-wide text insertion capabilities.

**Status**: ✅ Production Ready
- [Desktop Documentation](desktop/README.md)
- [Installation Guide](docs/desktop/user_guide/README.md)

### Android (Coming Soon)
System-wide voice keyboard (IME) bringing desktop-quality filtering to mobile.

**Status**: 🚧 In Development
- [Android Port Plan](ANDROID_PORT_PLAN.md)
- [Quick Start Guide](QUICKSTART.md)

## 🚀 Quick Start

### Desktop

```bash
# Clone repository
git clone https://github.com/yourusername/STT
cd STT/desktop

# Install dependencies
python -m venv .venv
source .venv/bin/activate  # or .venv\Scripts\activate on Windows
pip install -e ".[dev]"

# Run
python -m voice_input_service
```

**Keyboard Shortcuts**:
- `Alt+R` - Start/Stop recording
- `Alt+I` - Toggle insertion mode
- `Alt+V` - Paste text
- `Alt+S` - Save transcript
- `Alt+C` - Clear text

### Android

> 🚧 Android version is under active development. See [Android Port Plan](ANDROID_PORT_PLAN.md) for progress.

## 📚 Documentation

### For Users
- [Desktop User Guide](docs/desktop/user_guide/README.md) - Installation and usage
- [Configuration Guide](docs/desktop/user_guide/README.md#configuration) - Customizing settings
- [Troubleshooting](docs/desktop/user_guide/README.md#troubleshooting) - Common issues

### For Developers
- [Architecture Overview](docs/desktop/architecture/README.md) - System design
- [Core Algorithms](docs/core/README.md) - Shared logic documentation
- [API Documentation](docs/desktop/api/README.md) - Technical details
- [Development Guide](docs/desktop/development/README.md) - Contributing

### Android Development
- [Android Port Plan](ANDROID_PORT_PLAN.md) - Complete execution roadmap
- [Quick Start](QUICKSTART.md) - Jump in fast
- [Component Mapping](ANDROID_PORT_PLAN.md#-core-component-porting-guide) - Python to Kotlin

## 🏗️ Architecture

### Core Components (Shared Logic)

The heart of our quality is in these platform-agnostic algorithms:

1. **Text Processor** ([docs](docs/core/text-processing.md))
   - 40+ hallucination patterns
   - Intelligent overlap detection
   - Smart text appending with capitalization

2. **Voice Activity Detection** ([docs](docs/core/vad-algorithm.md))
   - Silero VAD with configurable threshold
   - Frame-based processing (30ms)
   - Device optimization (CPU/GPU/NNAPI)

3. **Audio Processing Pipeline** ([docs](docs/core/audio-processing.md))
   - Buffer management
   - Silence-triggered segmentation
   - Continuous and manual modes

### Platform Implementations

**Desktop** (Python)
```
Microphone → AudioRecorder → TranscriptionWorker (VAD) →
Whisper → TextProcessor → System Text Insertion
```

**Android** (Kotlin - In Progress)
```
AudioRecord → AudioProcessor (VAD) → WhisperEngine →
TextProcessor → IME Text Insertion
```

## 🎯 Features

### Text Quality
- ✅ Removes Whisper hallucinations automatically
- ✅ Filters timestamps and artifacts
- ✅ Intelligent text deduplication
- ✅ Proper capitalization and spacing
- ✅ Configurable minimum word count

### Voice Detection
- ✅ Silero VAD (neural network-based)
- ✅ Configurable sensitivity (0.0-1.0)
- ✅ Smart silence duration detection
- ✅ Automatic vs manual modes

### Transcription
- ✅ OpenAI Whisper (tiny → large models)
- ✅ whisper.cpp support (faster)
- ✅ Multi-language support
- ✅ GPU acceleration (CUDA/MPS/CPU)

### User Experience
- ✅ System-wide operation
- ✅ Direct text insertion or clipboard
- ✅ Configurable keyboard shortcuts
- ✅ Session saving with timestamps
- ✅ Real-time transcription display

## 🔧 Configuration

### Audio Settings
```python
sample_rate: 16000          # Hz
vad_threshold: 0.5          # 0.0-1.0 (higher = less sensitive)
silence_duration_sec: 2.0   # Seconds of silence to trigger processing
max_chunk_duration_sec: 15.0 # Maximum audio chunk size
```

### Transcription Settings
```python
model_name: "base"          # tiny, base, small, medium, large
language: "en"              # Language code or None for auto-detect
use_cpp: true               # Use whisper.cpp (faster)
```

Full configuration guide: [Configuration Documentation](docs/desktop/user_guide/README.md#configuration)

## 📊 Quality Comparison

| Feature | Basic Whisper | Other Apps | Voice Input Service |
|---------|---------------|------------|---------------------|
| Hallucination Filtering | ❌ | Basic | ✅ Advanced (40+ patterns) |
| Overlap Detection | ❌ | ❌ | ✅ Smart deduplication |
| VAD Quality | Basic | Varies | ✅ Silero (SOTA) |
| Text Processing | None | Basic | ✅ Intelligent appending |
| Continuous Mode | ❌ | Limited | ✅ Fully featured |

## 🤝 Contributing

We welcome contributions! This project is unique in its quality focus.

### For Desktop
See [Desktop Development Guide](docs/desktop/development/README.md)

### For Android
See [Android Port Plan](ANDROID_PORT_PLAN.md) - We're actively porting to Android!

### Areas Needing Help
- 🟢 Easy: Testing on different devices/OS
- 🟡 Medium: Additional hallucination patterns
- 🔴 Advanced: Android IME development

## 📈 Roadmap

### Completed ✅
- [x] Desktop voice input service
- [x] Advanced hallucination filtering
- [x] Silero VAD integration
- [x] whisper.cpp support
- [x] Continuous mode with smart appending
- [x] Comprehensive documentation

### In Progress 🚧
- [ ] Android port (Phase 0-1)
- [ ] Component migration to Kotlin
- [ ] Android IME implementation

### Planned 📋
- [ ] iOS version
- [ ] Web app (browser-based)
- [ ] Cloud sync for transcripts
- [ ] Custom model fine-tuning
- [ ] Multi-speaker diarization

## 🐛 Known Issues

See [GitHub Issues](https://github.com/yourusername/STT/issues) for current bugs and feature requests.

**Desktop**:
- FFmpeg required for Whisper
- Large models slow on CPU
- Windows-only keyboard shortcuts (for now)

**Android**:
- In development - not yet functional

## 📝 License

MIT License - See [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- OpenAI Whisper team for the excellent STT model
- ggerganov for whisper.cpp
- Silero team for VAD models
- All contributors and testers

## 💬 Support

- 📖 [Documentation](docs/README.md)
- 🐛 [Report Issues](https://github.com/yourusername/STT/issues)
- 💡 [Feature Requests](https://github.com/yourusername/STT/issues)
- 📧 Email: your.email@example.com

---

**Built with ❤️ for high-quality voice input**

⭐ Star this repo if you find it useful!

---

## Repository Structure

```
STT/
├── android/              # Android application (in development)
├── desktop/              # Desktop application (Python)
├── docs/                 # Documentation
│   ├── core/            # Shared algorithm docs
│   ├── desktop/         # Desktop-specific docs
│   └── android/         # Android-specific docs
├── ANDROID_PORT_PLAN.md # Detailed Android porting plan
├── QUICKSTART.md        # Quick start for contributors
└── README.md            # This file
```

For developers: Start with [QUICKSTART.md](QUICKSTART.md) to understand the project structure and begin contributing.