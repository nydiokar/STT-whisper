# Architecture Overview

The Voice Input Service follows a modular architecture with clear separation of concerns. This document outlines the system's primary components and how they interact.

## System Architecture

![System Architecture Diagram (placeholder)]()

### Core Components

1. **VoiceInputService** (`service.py`) - The main orchestrator that coordinates all components:
   - Manages recording state and lifecycle
   - Processes audio input and updates transcription
   - Handles UI updates through direct method calls
   - Implements speech detection through TranscriptionWorker
   - Filters hallucinations from transcription
   - Manages text accumulation modes
   - Implements Component and Closeable interfaces for proper resource management

2. **AudioRecorder** (`core/audio.py`) - Handles audio input from the microphone:
   - Configures and manages audio streams
   - Captures raw audio data
   - Provides audio analysis capabilities
   - Optimizes chunk processing
   - Includes proper resource cleanup

3. **TranscriptionEngine** (`core/transcription.py`) - Converts audio to text:
   - Uses Whisper AI models for transcription
   - Implements advanced hallucination filtering
   - Supports multiple languages
   - Optimizes processing for silence

4. **TranscriptionWorker** (`core/processing.py`) - Background processing thread:
   - Processes audio chunks asynchronously 
   - Maintains a queue for audio data
   - Implements smart buffering
   - Provides results via callbacks
   - Uses SilenceDetector for voice activity detection
   - Implements Component interface for standardized lifecycle

### User Interface Components

1. **TranscriptionUI** (`ui/window.py`) - Tkinter-based user interface:
   - Displays transcription text
   - Shows recording status and word count
   - Provides controls for language and continuous mode
   - Supports mode switching
   - Displays insertion mode status
   - Implements VAD settings UI components
   - Uses thread-safe animation with proper cleanup

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

2. **SilenceDetector** (`utils/silence_detection.py`) - Voice activity detection utility:
   - Provides multiple detection methods (basic, webrtc, silero)
   - Abstracts complexity of different VAD implementations
   - Handles fallback strategies when certain modules aren't available
   - Includes proper error handling for all detection methods
   - Allows dynamic configuration updates

3. **Clipboard Utilities** (`utils/clipboard.py`):
   - Provides centralized clipboard operations
   - Handles error conditions gracefully
   - Includes proper logging for clipboard operations

4. **Lifecycle Management** (`utils/lifecycle.py`):
   - Defines Closeable interface for resource cleanup
   - Defines Component interface for standardized start/stop lifecycle
   - Provides context manager support for resource management
   - Ensures consistent cleanup patterns across components

5. **Logging** (`utils/logging.py`) - Configures application logging:
   - Sets up file and console logging
   - Manages log rotation
   - Provides structured logging format
   - Tracks performance metrics

6. **Config** (`config.py`) - Central configuration:
   - Audio settings (sample rate, format)
   - Model specifications
   - VAD parameters (mode, aggressiveness, threshold)
   - Keyboard shortcuts
   - Language settings

## Data Flow

### Audio Capture and Processing

1. User initiates recording (keyboard shortcut or UI)
2. AudioRecorder starts capturing audio
3. Audio chunks are sent to TranscriptionWorker
4. SilenceDetector analyzes chunks for speech
5. Silent chunks are filtered out based on detection mode
6. Valid audio is buffered until silence is detected
7. TranscriptionEngine transcribes speech segments
8. Results flow back to VoiceInputService
9. UI is updated with filtered text

### Text Handling Flow

1. TranscriptionService produces text
2. VoiceInputService applies hallucination filtering
3. Based on mode:
   - Direct Insertion: Text is typed via keyboard simulation
   - Clipboard Mode: Text is copied with clipboard utilities
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
1. Automatic pause detection via SilenceDetector
2. Smart text appending
3. Proper sentence spacing
4. Continuous recording until stopped

## Implementation Details

### Component Interaction

- **Observer Pattern**: Components notify others of events through callbacks
- **Dependency Injection**: Service references are passed to components that need them
- **Command Pattern**: UI actions are encapsulated in command objects
- **Strategy Pattern**: Different silence detection strategies

### Threading Model

- UI runs on the main thread
- Audio processing runs on background threads
- Worker queues ensure thread safety
- Thread synchronization via queue and event objects
- Thread-safe animation using proper timer cancellation

### Error Handling

- Graceful degradation on audio device errors
- Fallback mechanisms for VAD methods
- Proper error handling in clipboard operations
- Comprehensive logging for troubleshooting

## Configuration

The system is configurable through the nested Config classes:

### AudioConfig
- Sample rates and formats
- Chunk sizes
- VAD mode, aggressiveness, and threshold settings
- Device selection

### TranscriptionConfig
- Model selection
- Language settings
- Processing thresholds
- Hallucination filters

### UIConfig
- Window settings
- Animation intervals
- Display preferences

### HotkeyConfig
- Keyboard shortcut mappings
- Modifier key combinations
- Input mode controls

## Resource Management

1. **Component Interface**
   - Standardized start() and stop() methods
   - Consistent lifecycle management
   - Restart capability

2. **Closeable Interface**
   - Explicit close() methods for cleanup
   - Context manager support
   - Deterministic resource release

3. **UI Animation**
   - Thread-safe animation using tkinter's after() timers
   - Proper cancellation of animation timers
   - Prevention of memory leaks

## Performance Optimizations

1. **Voice Activity Detection**
   - Multiple VAD strategies (basic, webrtc, silero)
   - Configurable thresholds and aggressiveness
   - Skip processing of silent chunks

2. **Text Processing**
   - Smart buffering of audio chunks
   - Efficient hallucination filtering
   - Optimized text accumulation

3. **UI Updates**
   - Thread-safe UI updates
   - Efficient text rendering
   - Background processing

## Future Architecture Considerations

Potential architectural improvements:

1. **Event Handling Enhancement**
   - Expand the EventHandler Protocol for more specialized event types
   - Consider typed events with payload data structures 
   - Add optional callback registration patterns for extensibility

2. **Advanced Audio Processing**
   - Better noise reduction
   - Enhanced VAD algorithm selection
   - Multiple audio source support

3. **UI Improvements**
   - More visual feedback
   - Progress indicators
   - Custom themes

4. **Extensibility**
   - Plugin system for transcription backends
   - Custom filter modules
   - Alternative UI frameworks 