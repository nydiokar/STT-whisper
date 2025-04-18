# Architecture Overview

The Voice Input Service follows a modular architecture with clear separation of concerns. This document outlines the system's primary components and how they interact.

## System Architecture

![System Architecture Diagram (placeholder)]()

### Core Components

1. **VoiceInputService** (`service.py`) - The main orchestrator that coordinates all components:
   - Manages the recording state and lifecycle
   - Processes audio input and updates transcription
   - Handles UI updates and system events
   - Implements the EventHandler protocol for keyboard shortcuts

2. **AudioProcessor** (`core/audio.py`) - Handles audio input from the microphone:
   - Configures and manages audio streams
   - Captures raw audio data
   - Provides callbacks for real-time processing

3. **TranscriptionService** (`core/transcription.py`) - Converts audio to text:
   - Uses Whisper AI models for transcription
   - Detects pauses and maintains context
   - Supports multiple languages

4. **TranscriptionWorker** (`core/processing.py`) - Background processing thread:
   - Processes audio chunks asynchronously 
   - Maintains a queue for audio data
   - Provides results via callbacks

### User Interface Components

1. **TranscriptionUI** (`ui/window.py`) - Tkinter-based user interface:
   - Displays transcription text
   - Shows recording status and word count
   - Provides controls for language and continuous mode

2. **KeyboardEventManager** (`ui/events.py`) - Manages keyboard shortcuts:
   - Handles recording toggle (Alt+R)
   - Manages save (Alt+S) and clear (Alt+C) operations
   - Supports continuous mode

### Utility Components

1. **TranscriptManager** (`utils/file_ops.py`) - Handles file operations:
   - Saves transcripts with timestamps
   - Manages transcript directory
   - Lists existing transcripts

2. **Logging** (`utils/logging.py`) - Configures application logging:
   - Sets up file and console logging
   - Manages log rotation
   - Provides structured logging format

3. **Config** (`config.py`) - Central configuration:
   - Audio settings (sample rate, format)
   - Model specifications
   - Feature toggles

## Data Flow

### Audio Capture and Processing

1. User initiates recording (keyboard shortcut or UI)
2. `VoiceInputService` starts `AudioProcessor` stream
3. `AudioProcessor` captures audio and sends to `TranscriptionWorker` via callback
4. `TranscriptionWorker` queues audio chunks and processes when sufficient data is available
5. `TranscriptionService` transcribes audio using Whisper model
6. Results flow back to `VoiceInputService` via callbacks
7. `VoiceInputService` updates UI with transcribed text

### Continuous Mode Flow

1. `VoiceInputService` monitors for pauses in speech
2. When a pause is detected, `VoiceInputService`:
   - Copies text to clipboard
   - Saves transcript
   - Clears current text
   - Automatically restarts recording

## Implementation Details

### Component Interaction

- **Observer Pattern**: Components notify others of events through callbacks
- **Dependency Injection**: Service references are passed to components that need them
- **Command Pattern**: UI actions are encapsulated in command objects

### Threading Model

- UI runs on the main thread
- Audio processing runs on background threads
- Worker queues ensure thread safety
- Thread synchronization via queue and event objects

### Error Handling

- Graceful degradation on audio device errors
- Retry mechanisms for transcription failures
- Comprehensive logging for troubleshooting

## Configuration

The system is configurable through the `Config` class, which provides:

- Audio sampling rates and formats
- Transcription model selection
- Feature toggles (e.g., continuous mode)
- Hotkey mappings

## Future Architecture Enhancements

Potential architectural improvements:

- Plugin system for alternative transcription backends
- Client-server architecture for distributed processing
- Web interface option
- Support for more audio input/output devices 