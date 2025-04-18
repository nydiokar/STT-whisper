# Architecture Overview

The Voice Input Service follows a modular architecture with clear separation of concerns. This document outlines the system's primary components and how they interact.

## System Architecture

![System Architecture Diagram (placeholder)]()

### Core Components

1. **VoiceInputService** (`service.py`) - The main orchestrator that coordinates all components:
   - Manages recording state and lifecycle
   - Processes audio input and updates transcription
   - Handles UI updates and system events
   - Implements smart silence detection
   - Filters hallucinations from transcription
   - Manages text accumulation modes

2. **AudioProcessor** (`core/audio.py`) - Handles audio input from the microphone:
   - Configures and manages audio streams
   - Captures raw audio data
   - Implements real-time silence detection
   - Provides RMS-based audio analysis
   - Optimizes chunk processing

3. **TranscriptionService** (`core/transcription.py`) - Converts audio to text:
   - Uses Whisper AI models for transcription
   - Implements advanced hallucination filtering
   - Supports multiple languages
   - Optimizes processing for silence

4. **TranscriptionWorker** (`core/processing.py`) - Background processing thread:
   - Processes audio chunks asynchronously 
   - Maintains a queue for audio data
   - Implements smart buffering
   - Provides results via callbacks

### User Interface Components

1. **TranscriptionUI** (`ui/window.py`) - Tkinter-based user interface:
   - Displays transcription text
   - Shows recording status and word count
   - Provides controls for language and continuous mode
   - Supports mode switching
   - Displays insertion mode status

2. **KeyboardEventManager** (`ui/events.py`) - Manages keyboard shortcuts:
   - Handles recording toggle (Alt+R)
   - Manages save (Alt+S) and clear (Alt+C) operations
   - Controls text insertion mode (Alt+I)
   - Provides manual paste option (Alt+V)
   - Supports continuous mode

### Utility Components

1. **TranscriptManager** (`utils/file_ops.py`) - Handles file operations:
   - Saves transcripts with timestamps
   - Manages transcript directory
   - Lists existing transcripts
   - Provides read/write operations

2. **Logging** (`utils/logging.py`) - Configures application logging:
   - Sets up file and console logging
   - Manages log rotation
   - Provides structured logging format
   - Tracks performance metrics

3. **Config** (`core/config.py`) - Central configuration:
   - Audio settings (sample rate, format)
   - Model specifications
   - Silence detection parameters
   - Keyboard shortcuts
   - Language settings

## Data Flow

### Audio Capture and Processing

1. User initiates recording (keyboard shortcut or UI)
2. AudioProcessor starts capturing with silence detection
3. Audio chunks are analyzed for RMS levels
4. Silent chunks are filtered out
5. Valid audio is sent to TranscriptionWorker
6. Worker processes chunks and manages buffering
7. TranscriptionService applies hallucination filtering
8. Results flow back to VoiceInputService
9. UI is updated with filtered text

### Text Handling Flow

1. TranscriptionService produces text
2. VoiceInputService applies hallucination filtering
3. Based on mode:
   - Direct Insertion: Text is typed via keyboard simulation
   - Clipboard Mode: Text is copied to clipboard
4. Text accumulation based on mode:
   - Continuous: Appends with proper spacing
   - Non-continuous: Accumulates until cleared

### Mode Management

#### Non-Continuous Mode
1. User controls recording manually
2. Text accumulates until explicitly cleared
3. Manual control over text insertion
4. Clear operation (Alt+C) resets text

#### Continuous Mode
1. Automatic pause detection
2. Smart text appending
3. Proper sentence spacing
4. Continuous recording until stopped

## Implementation Details

### Component Interaction

- **Observer Pattern**: Components notify others of events through callbacks
- **Dependency Injection**: Service references are passed to components that need them
- **Command Pattern**: UI actions are encapsulated in command objects
- **Strategy Pattern**: Different text insertion modes

### Threading Model

- UI runs on the main thread
- Audio processing runs on background threads
- Worker queues ensure thread safety
- Thread synchronization via queue and event objects
- Separate thread for keyboard simulation

### Error Handling

- Graceful degradation on audio device errors
- Retry mechanisms for transcription failures
- Fallback mechanisms for text insertion
- Comprehensive logging for troubleshooting

## Configuration

The system is configurable through the nested Config classes:

### AudioConfig
- Sample rates and formats
- Chunk sizes
- Silence detection thresholds
- Device selection

### TranscriptionConfig
- Model selection
- Language settings
- Processing thresholds
- Hallucination filters

### UIConfig
- Window settings
- Status update intervals
- Display preferences

### HotkeyConfig
- Keyboard shortcut mappings
- Modifier key combinations
- Input mode controls

## Performance Optimizations

1. **Silence Detection**
   - RMS-based audio analysis
   - Configurable thresholds
   - Skip processing of silent chunks

2. **Text Processing**
   - Smart buffering of audio chunks
   - Efficient hallucination filtering
   - Optimized text accumulation

3. **UI Updates**
   - Throttled status updates
   - Efficient text rendering
   - Background processing

## Future Architecture Considerations

Potential architectural improvements:

1. **Enhanced Text Processing**
   - More sophisticated hallucination detection
   - Context-aware filtering
   - Custom filter rules

2. **Advanced Audio Processing**
   - Better noise reduction
   - Dynamic silence detection
   - Multiple audio source support

3. **UI Improvements**
   - More visual feedback
   - Progress indicators
   - Custom themes

4. **Extensibility**
   - Plugin system for transcription backends
   - Custom filter modules
   - Alternative UI frameworks 