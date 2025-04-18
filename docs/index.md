# Voice Input Service

A system-wide voice-to-text application that provides real-time transcription using OpenAI's Whisper models.

![Voice Input Service](assets/screenshot.png)

## Overview

Voice Input Service is designed to make speech-to-text transcription accessible and convenient. It allows users to:

- Transcribe speech in real-time with high accuracy
- Automatically detect pauses in speech for continuous transcription
- Save transcripts with timestamps
- Support multiple languages
- Operate via keyboard shortcuts for hands-free use

## Features

- **Real-time Transcription**: Low-latency conversion of speech to text
- **Multiple Languages**: Support for English, Spanish, French, German, Italian, and more
- **Continuous Mode**: Automatically save and restart on detecting pauses in speech
- **Clipboard Integration**: Automatically copy transcriptions to clipboard
- **File Management**: Save transcriptions with timestamps
- **Keyboard Shortcuts**: Control the application hands-free
- **Configurable Models**: Choose from different Whisper model sizes for quality/speed tradeoff

## Documentation

The documentation is organized into the following sections:

- [Architecture](architecture/README.md): System design and component interaction
- [User Guide](user_guide/README.md): Installation, configuration, and usage instructions
- [Development Guide](development/README.md): Setting up the development environment and contributing
- [API Documentation](api/README.md): Technical documentation for developers

## Quick Start

To get started with Voice Input Service:

1. Ensure your microphone is connected and functioning
2. Launch the application:
   ```powershell
   python -m voice_input_service
   ```
3. Use Alt+R to start/stop recording
4. Use Alt+S to save the transcript
5. Use Alt+C to clear the text

## System Requirements

- Windows 10 or later
- Python 3.9 or later
- Working microphone
- 2GB RAM (8GB recommended for larger models)

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Contributing

Contributions are welcome! See the [Development Guide](development/README.md) for details on how to contribute to this project. 