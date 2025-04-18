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
│   │   ├── audio.py          # Audio handling
│   │   ├── transcription.py  # Transcription service
│   │   └── processing.py     # Worker thread
│   ├── ui/                   # User interface
│   │   ├── window.py         # Main UI window
│   │   └── events.py         # Event handling
│   └── utils/                # Utilities
│       ├── file_ops.py       # File operations
│       └── logging.py        # Logging setup
├── tests/                    # Test suite
│   ├── conftest.py           # Test fixtures
│   ├── test_audio.py         # Audio tests
│   ├── test_transcription.py # Transcription tests
│   ├── test_service.py       # Service tests
│   └── ...
├── docs/                     # Documentation
├── pyproject.toml            # Project configuration
└── README.md                 # Project overview
```

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