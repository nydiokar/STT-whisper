# User Guide

This guide provides information on how to install, configure, and use the Voice Input Service for speech-to-text transcription.

## Installation

### Prerequisites

- Windows 10 or later
- Python 3.9 or later
- A working microphone
- At least 2GB of RAM (8GB recommended for larger models)

### Install Steps

1. Clone the repository or download the source code:
   ```powershell
   git clone https://github.com/nydiokar/STT-whisper.git
   cd STT
   ```

2. Create and activate a virtual environment:
   ```powershell
   python -m venv .venv
   .\.venv\Scripts\activate
   ```

3. Install the required dependencies:
   ```powershell
   pip install -e ".[dev]"
   ```

4. Verify the installation:
   ```powershell
   python -m voice_input_service --version
   ```

## Getting Started

### First Run

1. Ensure your microphone is connected and functioning
2. Launch the application:
   ```powershell
   python -m voice_input_service
   ```
3. The application will perform a microphone check and launch the UI
4. Use Alt+R to start/stop recording

### User Interface

![UI Overview (placeholder)]()

The main window consists of:

- **Status bar**: Shows recording status, elapsed time, and word count
- **Option controls**: Language selection and continuous mode toggle
- **Text area**: Displays the transcribed text
- **Help section**: Lists available keyboard shortcuts

## Features

### Recording Modes

#### Non-Continuous Mode (Default)
1. Press `Alt+R` to start recording
2. Speak clearly into your microphone
3. Press `Alt+R` again to stop recording
4. Text accumulates until you clear it
5. Use `Alt+C` to clear when needed

Key benefits:
- Complete control over recording
- Text persists until manually cleared
- Perfect for precise dictation
- No automatic interruptions

#### Continuous Mode
1. Check the "Continuous Mode" checkbox
2. Press `Alt+R` to start recording
3. Speak naturally with pauses
4. The application will:
   - Detect natural pauses
   - Append new text to existing content
   - Keep recording until manually stopped

Key benefits:
- Natural speech flow
- Automatic text accumulation
- Great for long-form dictation
- Smart pause detection

### Text Insertion Options

#### Direct Insertion Mode
1. Press `Alt+I` to toggle direct insertion
2. Text will be typed directly into your active window
3. Great for applications that don't support clipboard
4. Falls back to clipboard if insertion fails

#### Clipboard Mode (Default)
1. Text is automatically copied to clipboard
2. Paste manually where needed
3. Use `Alt+V` for quick paste at cursor
4. More reliable across all applications

### Language Selection

1. Select your preferred language from the dropdown menu
2. The transcription will use the selected language model
3. Available languages:
   - English (en)
   - Spanish (es)
   - French (fr)
   - German (de)
   - Italian (it)
   - And more...

### Smart Features

#### Silence Detection
- Automatically skips silent audio
- Reduces processing time
- Prevents false transcriptions
- Improves overall accuracy

#### Hallucination Filtering
- Removes common false detections
- Filters out YouTube-style phrases
- Handles short meaningless segments
- Improves transcription quality

### Saving Transcripts

Automatically saved transcripts can be found in:
`C:\Users\<username>\Documents\Voice Transcripts\`

You can also manually save the current transcript:
1. Press `Alt+S` to save the current transcript
2. The file will be saved with a timestamp in the name

### Clearing Text

1. Press `Alt+C` to clear the current transcription
2. This does not delete saved transcripts
3. In non-continuous mode, text persists until cleared
4. In continuous mode, clearing resets the accumulation

## Keyboard Shortcuts

- `Alt+R`: Start/stop recording
- `Alt+I`: Toggle direct text insertion mode
- `Alt+V`: Paste text at cursor position
- `Alt+S`: Save transcript
- `Alt+C`: Clear current text

## Troubleshooting

### Microphone Issues

If you encounter microphone problems:

1. Check Windows microphone permissions:
   - Open Windows Settings > Privacy > Microphone
   - Ensure "Allow apps to access your microphone" is turned on

2. Verify default microphone:
   - Right-click the sound icon in system tray
   - Select "Open Sound settings"
   - Choose your desired microphone as the default input device

3. Test your microphone:
   - In Windows search, type "Sound settings"
   - Click on Input > Test your microphone

### Transcription Quality

To improve transcription quality:

1. Use a good quality microphone
2. Reduce background noise
3. Speak clearly and at a moderate pace
4. Try adjusting your distance from the microphone
5. Consider using a larger model (in configuration)
6. Check if silence detection is too aggressive

### Text Insertion Issues

If text insertion isn't working:

1. Try toggling between insertion modes (Alt+I)
2. Use manual paste (Alt+V) if automatic fails
3. Check application permissions
4. Verify clipboard access
5. Try running the app as administrator

### Application Not Responding

If the application freezes:

1. Close the application using Task Manager
2. Check the logs at: `C:\Users\<username>\Documents\Voice Transcripts\logs\`
3. Ensure you have enough system resources
4. Try using a smaller model if performance is an issue

## Advanced Configuration

### Model Selection

Edit `config.py` to change the transcription model:
- "tiny" - Fastest, lowest quality
- "base" - Good balance of speed and accuracy
- "small" - Better quality, slower processing
- "medium" - High quality, requires more resources
- "large" - Best quality, requires significant resources

### Audio Settings

Advanced audio settings can be modified in `config.py`:
- Sample rate
- Chunk size
- Silence detection threshold
- Processing chunk size

## Getting Help

If you encounter issues:

1. Check the log files for detailed error information
2. Review the troubleshooting guide above
3. File an issue on the project's GitHub repository 