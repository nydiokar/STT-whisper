# Voice Input Service

A system-wide voice-to-text service that uses OpenAI's Whisper model for accurate speech-to-text conversion. Works as a drop-in replacement for Windows built-in speech-to-text but with improved accuracy using Whisper.

## Features

- Real-time speech-to-text conversion using Whisper
- System-wide text input capability (Windows only)
- Works with any application that accepts text input
- Uses Windows+G shortcut
- Higher accuracy than Windows speech recognition

## How It Works

1. Capture audio using your default microphone
2. Process speech using OpenAI's Whisper model
3. Automatically paste the transcribed text
4. All processing is done locally on your machine

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

## Installation

1. Create a virtual environment:
```bash
python -m venv .venv
.venv\Scripts\activate  # On Windows
```

2. Install dependencies:
```bash
pip install -e ".[dev]"
```

3. Install Whisper and download the model:
```bash
# First, install Whisper
pip install openai-whisper

# Then download the model (this may take a few minutes)
python -c "import whisper; whisper.load_model('base')"
```

Note: The first time you run the service, Whisper will automatically download the specified model if it hasn't been downloaded yet. Models are stored in:
- Windows: %USERPROFILE%\.cache\whisper
- The "base" model is around 142MB

## Usage

1. Start the service:
```bash
python -m voice_input_service
```

2. In any text input field:
   - Press Windows+G to start recording (different from Windows+H to avoid conflicts with built-in STT)
   - Speak into your microphone
   - Press Windows+G again or Esc to stop recording
   - The transcribed text will be automatically inserted at your cursor position

Note: This service uses Windows+G to avoid conflicts with the Windows built-in speech-to-text (Windows+H).
You can use either service independently without interference.

## Configuration

Basic settings in `config.py`:
- `model_size`: Whisper model to use ("tiny", "base", "small", "medium", "large")
- `hotkey`: Shortcut to activate (default: "windows+g")

Audio settings are fixed for optimal Whisper compatibility:
- Sample rate: 16kHz
- Channels: Mono
- Format: 16-bit PCM

## Requirements

- Python 3.8+
- Microphone
- OpenAI Whisper model (downloaded automatically)
- Windows OS (for system-wide keyboard shortcuts)

## Tips for Best Results

1. Use a good quality microphone
2. Speak clearly and at a normal pace
3. Minimize background noise
4. Keep the microphone at a consistent distance
5. Consider using a larger Whisper model if accuracy is critical

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

3. Poor transcription quality:
   - Use a better microphone
   - Speak clearly and at a normal pace
   - Reduce background noise
   - Try a larger Whisper model (e.g., "medium" or "large")

4. FFmpeg errors:
   - Ensure FFmpeg is properly installed
   - Try copying ffmpeg.exe directly to System32
   - Check if antivirus is blocking FFmpeg
