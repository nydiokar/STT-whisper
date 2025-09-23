# Voice Input Service

A system-wide voice-to-text service that uses OpenAI's Whisper model for accurate speech-to-text conversion. Works as a drop-in replacement for Windows built-in speech-to-text but with improved accuracy using Whisper.

## Features

- Real-time speech-to-text conversion using Whisper
- System-wide text input capability (Windows only)
- Works with any application that accepts text input
- Smart silence detection and hallucination filtering
- Two flexible input modes:
  - **Non-continuous mode**: Manual control with text accumulation
  - **Continuous mode**: Automatic pause detection and text appending
- Direct text insertion or clipboard-based input
- Multi-language support
- Efficient audio processing with silence detection
- Advanced hallucination filtering for cleaner output

## Documentation

Detailed documentation is available in the `docs` directory:

- [Documentation Index](docs/index.md) - Start here for an overview
- [Architecture](docs/architecture/README.md) - System design and components
- [User Guide](docs/user_guide/README.md) - Installation and usage instructions
- [Development Guide](docs/development/README.md) - For contributors and developers
- [API Documentation](docs/api/README.md) - Technical API details

## How It Works

1. Capture audio using your default microphone
2. Process speech using OpenAI's Whisper model
3. Filter out silence and hallucinations
4. Insert text directly or copy to clipboard based on mode
5. All processing is done locally on your machine

## Prerequisites

1. Install FFmpeg (Required for Whisper):
   - Download from: https://www.gyan.dev/ffmpeg/builds/
   - Extract and add ffmpeg.exe to your system PATH
   - Or copy ffmpeg.exe to C:\Windows\System32
   - Verify by running `ffmpeg -version` in Command Prompt

2. Check your microphone:
   - Make sure it's set as your Windows default input device
   - Test it in Windows Sound settings
   - Close any applications that might be using it

## Quick Start

1. Create a virtual environment:
```bash
python -m venv .venv
.venv\Scripts\activate  # On Windows
```

2. Install dependencies:
```bash
pip install -e ".[dev]"
```

3. Start the service:
```bash
python -m voice_input_service
```

4. Available keyboard shortcuts:
   - `Alt+R`: Start/stop recording
   - `Alt+I`: Toggle direct text insertion mode
   - `Alt+V`: Paste text at cursor position
   - `Alt+S`: Save transcript
   - `Alt+C`: Clear current text

## Tips for Best Results

1. Use a good quality microphone
2. Speak clearly and at a normal pace
3. Minimize background noise
4. Keep the microphone at a consistent distance
5. Consider using a larger Whisper model if accuracy is critical

## Input Modes

### Non-Continuous Mode (Default)
- Manual control over recording start/stop
- Text accumulates until manually cleared
- Perfect for precise dictation
- Use Alt+C to clear when needed

### Continuous Mode
- Automatically detects speech pauses
- Appends new text to existing content
- Great for long-form dictation
- Natural pause detection

### Text Insertion Options
- Direct insertion mode (Alt+I to toggle)
- Clipboard-based insertion (default)
- Manual paste with Alt+V

## Contributing

Contributions are welcome! Please see the [Development Guide](docs/development/README.md) for information on how to contribute to this project.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Configuration

Basic settings in `config.py`:
- `model_size`: Whisper model to use ("tiny", "base", "small", "medium", "large")
- Audio settings optimized for Whisper compatibility
- Configurable silence detection thresholds
- Customizable keyboard shortcuts

## Requirements

- Python 3.8+
- Microphone
- OpenAI Whisper model (downloaded automatically)
- Windows OS (for system-wide keyboard shortcuts)

## Troubleshooting

Common Issues:

1. "Unanticipated host error" (-9999):
   This usually means Windows is blocking microphone access. Fix it by:
   
   a. Check Windows Microphone Privacy Settings:
      - Press Windows key + I
      - Go to Privacy & Security > Microphone
      - Turn ON "Microphone access"
      - Turn ON "Let apps access your microphone"
      - Find your terminal app (e.g., Command Prompt, PowerShell) in the list and enable it
   
   b. Check Windows Sound Settings:
      - Right-click speaker icon in taskbar
      - Click "Open Sound settings"
      - Under Input, select your microphone
      - Click "Test" to verify it works
      
   c. If still not working:
      - Close all other apps that might use the microphone
      - Run the service from a new terminal with admin privileges
      - Try restarting Windows audio service (Run services.msc)
      - Reinstall your microphone drivers

2. Poor transcription quality:
   - Use a better microphone
   - Speak clearly and at a normal pace
   - Reduce background noise
   - Try a larger Whisper model (e.g., "medium" or "large")
   - Check if you're too far from the microphone

3. FFmpeg errors:
   - Ensure FFmpeg is properly installed
   - Try copying ffmpeg.exe directly to System32
   - Check if antivirus is blocking FFmpeg

4. Text insertion issues:
   - Try toggling between direct insertion and clipboard modes
   - Use Alt+V to manually paste if automatic insertion fails
   - Check application permissions for text input

## Speech-to-Text Application

This is a speech-to-text application that uses OpenAI's Whisper model for transcription. It supports both the Python Whisper model and the faster C++ implementation (whisper.cpp).

### Features

- Real-time speech-to-text transcription
- Support for multiple transcription engines (Python Whisper or whisper.cpp)
- Multiple model size options (tiny, base, small, medium, large)
- Configurable hotkeys for recording control
- Easy-to-use UI with configurable settings

### Installation

#### Prerequisites

- Python 3.8 or higher
- Git (for installing whisper.cpp)
- C++ build environment (for building whisper.cpp)
  - On Windows: Visual Studio or MinGW
  - On Linux/macOS: GCC/Clang and Make

#### Windows Quick Setup

1. Run the automatic setup script:
   ```batch
   setup_windows.bat
   ```

2. This will:
   - Clone the whisper.cpp repository to your home directory
   - Build the whisper.cpp executable
   - Download the necessary model files
   - Configure the application to use whisper.cpp

#### Manual Setup

1. Install Python dependencies:
   ```bash
   pip install -r requirements.txt
   ```

2. Set up whisper.cpp:
   ```bash
   python scripts/install_whisper_cpp.py
   ```

3. Or run the fix script if you already have whisper.cpp installed:
   ```bash
   python scripts/fix_whisper_cpp.py
   ```

### Using whisper.cpp

The application can use either the Python Whisper model or whisper.cpp for transcription. To use whisper.cpp:

1. Make sure it's properly installed and built using one of the scripts above
2. In the application settings, ensure "Use whisper.cpp" is enabled
3. Select the appropriate GGML model file

#### Troubleshooting

If you encounter issues with the whisper.cpp integration:

1. Run the diagnostic script:
   ```bash
   python scripts/fix_whisper_cpp.py
   ```
   This will help locate your models and executables and update the configuration.

2. Check if the whisper.cpp executable can be found:
   ```bash
   python scripts/use_whisper_cpp.py
   ```
   This will test running whisper.cpp directly.

3. Common issues:
   - Executable not found: Make sure the whisper.cpp executable is built and the path is correct in settings
   - Model not found: Ensure the GGML model files are downloaded and the path is correct
   - Path issues: Use absolute paths in the configuration to avoid path resolution problems

### Configuration

The application configuration is stored in:
```
~/.voice_input_service/config.json
```

Key whisper.cpp settings:
```json
{
  "transcription": {
    "use_cpp": true,
    "whisper_cpp_path": "/path/to/whisper.cpp/main",
    "ggml_model_path": "/path/to/models/ggml-base.en.bin"
  }
}
```

## Whisper.cpp Setup (Windows)

1. **Install Visual Studio**:
   - Download Visual Studio 2022 Community from: https://visualstudio.microsoft.com/vs/community/
   - During installation, select "Desktop development with C++"

2. **Open Developer Command Prompt**:
   - Press Start
   - Search for "Developer Command Prompt for VS 2022"
   - Right-click and "Run as administrator"

3. **Get whisper.cpp**:
   ```cmd
   cd /d %USERPROFILE%
   git clone https://github.com/ggerganov/whisper.cpp
   cd whisper.cpp
   ```

4. **Build the library** (in the same Developer Command Prompt):
   ```cmd
   mkdir build
   cd build
   cmake .. -DBUILD_SHARED_LIBS=ON
   cmake --build . --config Release
   ```

5. **Download Model**:
   ```cmd
   mkdir models
   cd models
   curl -L https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin --output ggml-base.en.bin
   ```

After building, you'll find:
- The DLL at `build/bin/Release/whisper.dll`
- Header files in the `whisper.cpp/include` directory

Important Notes:
- Make sure you use the "Developer Command Prompt for VS 2022", NOT regular PowerShell or Command Prompt
- We build with `BUILD_SHARED_LIBS=ON` to create a DLL that can be used by our Python application
- The library and model files need to be accessible to our Python application
- You'll need to ensure your Python bindings can find and load the DLL

## Whisper.cpp Integration

This application supports using whisper.cpp as a faster alternative to the Python Whisper implementation.

### Setup Instructions

Follow these steps to set up whisper.cpp on Windows:

1. **View Setup Guide**: Run `scripts/setup_guide.bat` for detailed instructions on how to download, build, and set up whisper.cpp.

2. **Configure**: After setting up whisper.cpp, run `scripts/setup_easy.bat` to update your configuration with the correct paths.

That's it! Once configured, the application will automatically use whisper.cpp for transcription.

### Performance Benefits

Using whisper.cpp provides significant performance improvements:
- Up to 4x faster transcription speed
- Much lower memory usage
- Better real-time performance
