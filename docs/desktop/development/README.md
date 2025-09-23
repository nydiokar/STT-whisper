# Development Guide

This guide provides information for developers who want to contribute to or modify the Voice Input Service.

## Development Environment Setup

### Prerequisites

- Windows 10 or later
- Python 3.9 or later
- Git
- Visual Studio Code (recommended) or another IDE
- Basic knowledge of Python and tkinter

### Setup Steps

1. Clone the repository:
   ```powershell
   git clone https://github.com/nydiokar/STT-whisper.git
   cd STT
   ```

2. Create and activate a virtual environment:
   ```powershell
   python -m venv .venv
   .\.venv\Scripts\activate
   ```

3. Install the development dependencies:
   ```powershell
   pip install -e ".[dev]"
   ```

4. Configure pre-commit hooks:
   ```powershell
   pre-commit install
   ```

## Project Structure

```
STT/
├── voice_input_service/      # Main package
│   ├── __main__.py           # Entry point
│   ├── config.py             # Central configuration
│   ├── service.py            # Main service
│   ├── core/                 # Core functionality
│   │   ├── audio.py          # Audio recording and processing
│   │   ├── transcription.py  # Speech-to-text conversion
│   │   ├── processing.py     # Worker thread with VAD
│   │   ├── model_manager.py  # AI model management
│   │   ├── whisper_cpp.py    # Whisper.cpp integration
│   │   └── chunk_buffer.py   # Audio chunk management
│   ├── ui/                   # User interface
│   │   ├── window.py         # Main UI window
│   │   ├── dialogs.py        # UI dialog components
│   │   └── events.py         # Event handling
│   └── utils/                # Utilities
│       ├── file_ops.py       # Transcript file operations
│       ├── logging.py        # Logging configuration
│       ├── silence_detection.py # Voice activity detection
│       ├── clipboard.py      # Clipboard operations
│       └── lifecycle.py      # Component lifecycle interfaces
├── tests/                    # Test suite
│   ├── conftest.py           # Test fixtures
│   ├── test_audio.py         # Audio tests
│   ├── test_transcription.py # Transcription tests
│   ├── test_service.py       # Service tests
│   ├── test_processing.py    # Worker thread tests
│   ├── test_ui.py            # UI tests
│   ├── test_file_ops.py      # File operations tests
│   ├── test_utils.py         # Utility tests
│   ├── test_model_manager.py # Model manager tests
│   ├── test_config.py        # Configuration tests
│   ├── test_main.py          # Entry point tests
│   └── test_data/            # Test data files
├── docs/                     # Documentation
│   ├── architecture/         # Architecture documentation
│   ├── api/                  # API documentation
│   ├── user_guide/           # User documentation
│   └── development/          # Development documentation
├── examples/                 # Example applications
├── pyproject.toml            # Project configuration
└── README.md                 # Project overview
```

## Architecture Overview

The Voice Input Service follows a modular architecture with clear separation of concerns:

### Core Layer

1. **VoiceInputService** (`service.py`) - Central orchestrator:
   - Manages recording state and application lifecycle
   - Processes audio and updates transcription
   - Handles UI updates and event processing
   - Coordinates all core components

2. **Audio System** (`core/audio.py`):
   - Configures and manages audio input devices
   - Captures raw audio data via PyAudio
   - Provides audio stream callbacks
   - Supports device selection and configuration

3. **Transcription Engine** (`core/transcription.py`):
   - Interfaces with Whisper AI models for speech-to-text
   - Supports multiple languages and model sizes
   - Implements hallucination filtering
   - Handles both Python Whisper and whisper.cpp backends

4. **Worker Thread** (`core/processing.py`):
   - Processes audio chunks asynchronously 
   - Implements Voice Activity Detection (VAD)
   - Maintains audio queue and buffering
   - Provides callback-based results handling

5. **Model Management** (`core/model_manager.py`):
   - Handles model downloading and verification
   - Manages whisper.cpp integration
   - Provides model selection capabilities
   - Checks for available models and versions

6. **Chunk Buffer** (`core/chunk_buffer.py`):
   - Manages audio chunks and processing state
   - Provides smart audio buffering
   - Handles timing and segmentation of audio data

### UI Layer

1. **Main Window** (`ui/window.py`):
   - Provides the primary user interface
   - Displays transcription text and status
   - Manages language selection and mode controls
   - Handles VAD settings UI

2. **Dialog Components** (`ui/dialogs.py`):
   - Implements model selection dialogs
   - Provides settings management
   - Displays progress indicators for downloads
   - Manages configuration UI

3. **Event Handling** (`ui/events.py`):
   - Defines the EventHandler Protocol
   - Manages keyboard shortcuts
   - Handles clipboard operations
   - Controls UI-initiated actions

### Utility Layer

1. **File Operations** (`utils/file_ops.py`):
   - Manages transcript saving and loading
   - Handles file system operations
   - Provides timestamp-based filenames
   - Lists transcript history

2. **Silence Detection** (`utils/silence_detection.py`):
   - Implements multiple VAD methods:
     - Basic RMS-based detection
     - WebRTC VAD for better accuracy
     - Silero VAD for best quality
   - Provides unified interface for all detection methods
   - Handles fallbacks between detection methods

3. **Lifecycle Management** (`utils/lifecycle.py`):
   - Defines Component and Closeable interfaces
   - Standardizes start/stop/close operations
   - Ensures proper resource cleanup
   - Provides context manager support

4. **Logging** (`utils/logging.py`):
   - Configures application-wide logging
   - Handles log rotation and formatting
   - Logs performance metrics
   - Provides debugging support

5. **Clipboard** (`utils/clipboard.py`):
   - Handles clipboard operations
   - Provides error handling for clipboard actions

### Configuration

The **Config** system (`config.py`):
- Provides a centralized configuration with nested components
- Uses Pydantic for validation and typing
- Includes settings for:
  - Audio (sample rate, chunk size, VAD parameters)
  - Transcription (model, language)
  - UI (colors, update intervals)
  - Hotkeys (keyboard shortcuts)

### Communication Patterns

1. **Event Handling**:
   - The UI registers handlers with the service
   - The service implements the EventHandler protocol
   - UI components trigger callbacks on user actions
   - The service updates UI through direct method calls

2. **Threading Model**:
   - UI runs on the main thread
   - Audio processing runs on background threads
   - Worker queues ensure thread safety
   - Animation uses tkinter timers for thread safety

3. **Component Lifecycle**:
   - Components implement standardized interfaces
   - Proper resource acquisition and release
   - Consistent start/stop/close methods
   - Context manager support for resource management

## Development Workflow

### Making Changes

1. Create a new branch for your feature or fix:
   ```powershell
   git checkout -b feature/your-feature-name
   ```

2. Make your changes following these principles:
   - Follow type hints and functional programming principles
   - Use minimal code changes when possible
   - Add appropriate tests
   - Document any new functionality

3. Run tests to ensure your changes don't break existing functionality:
   ```powershell
   pytest
   ```

4. Run the linter to ensure code quality:
   ```powershell
   ruff check .
   mypy .
   ```

5. Format your code:
   ```powershell
   black .
   isort .
   ```

### Testing

The project uses pytest for testing:

```powershell
# Run all tests
pytest

# Run specific test file
pytest tests/test_audio.py

# Run with coverage report
pytest --cov=voice_input_service

# Run only unit tests
pytest -m unit
```

#### Test Categories

- **Unit Tests**: Test individual components in isolation
- **Integration Tests**: Test component interactions
- **Functional Tests**: Test full features from a user perspective

#### Test Fixtures

The project uses fixtures (in `conftest.py`) to provide common test dependencies:

- `mock_pyaudio`: Mocks the PyAudio dependency
- `temp_transcript_dir`: Creates a temporary directory for transcript testing
- `mock_whisper`: Prevents actual model loading during tests

### Continuous Integration

The project uses GitHub Actions to run tests on each push and pull request:

- Code formatting checks
- Linting
- Type checking
- Test suite execution
- Coverage reporting

## Design Principles

When making changes, adhere to these principles:

1. **Type Safety**: Use strict type hints for all functions and variables
2. **Functional Programming**: Prefer pure functions over mutable state
3. **Separation of Concerns**: Keep components focused on single responsibilities
4. **Testability**: Design for easy testing with dependency injection
5. **Error Handling**: Gracefully handle exceptions with appropriate logging

## Code Style

The project follows these style guidelines:

- Use [Black](https://black.readthedocs.io/) for code formatting
- Follow [PEP 8](https://www.python.org/dev/peps/pep-0008/) naming conventions
- Use [isort](https://pycqa.github.io/isort/) for import sorting
- Follow [mypy](https://mypy.readthedocs.io/) for type hints

## Documentation

Update documentation when making changes:

1. Update docstrings for modified functions
2. Update README.md if user-facing functionality changes
3. Update this development guide if development workflows change
4. Add architecture documentation for significant structural changes

## Releasing

To create a new release:

1. Update the version in `pyproject.toml`
2. Update the changelog
3. Create a new tag:
   ```powershell
   git tag v0.1.0
   git push origin v0.1.0
   ```

## Troubleshooting Development Issues

### Common Issues

#### PyAudio Installation Problems

If you have issues installing PyAudio:
1. Download the appropriate wheel from https://www.lfd.uci.edu/~gohlke/pythonlibs/#pyaudio
2. Install it manually:
   ```powershell
   pip install PyAudio‑0.2.11‑cp39‑cp39‑win_amd64.whl
   ```

#### Test Failures

If tests fail:
1. Check that you have all dependencies installed
2. Verify your Python version (3.9+ required)
3. Look for specific error messages in the test output
4. Check if there are OS-specific issues

#### Debug Logging

Enable debug logging for development:
```powershell
$env:LOG_LEVEL = "DEBUG"
python -m voice_input_service
```

## Contributing Guidelines

1. Fork the repository
2. Create a feature branch
3. Make your changes following the development workflow
4. Add tests for your changes
5. Ensure all tests pass
6. Submit a pull request
7. Respond to review comments 