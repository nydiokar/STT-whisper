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
