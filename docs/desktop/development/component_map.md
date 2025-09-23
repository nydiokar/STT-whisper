# Voice Input Service Component Map

This document maps all components and methods within the codebase to provide a clear overview of responsibilities and interactions.

## 1. Service Layer

### `VoiceInputService` (service.py)

Service methods (coordination and orchestration):
- `__init__()` - Initializes the main service and its components
- `run()` - Runs the service main loop
- `close()` - Public method to clean up resources

UI event handling:
- `_setup_ui_events()` - Sets up UI event callbacks
- `_toggle_continuous_mode()` - Toggles continuous transcription mode
- `_change_language()` - Changes the transcription language
- `save_transcript()` - Handles UI save transcript requests and delegates to TranscriptManager
- `clear_transcript()` - Handles UI clear transcript requests and updates UI

Audio processing:
- `_on_audio_data()` - Handles incoming audio data
- `_process_audio_chunk()` - Processes an audio chunk and returns transcription
- `start_recording()` - Starts audio recording
- `stop_recording()` - Stops audio recording and returns transcription
- `_on_transcription_result()` - Handles transcription results

State and resource management:
- `_verify_transcription_model()` - Tests transcription model availability
- `_cleanup()` - Cleans up resources
- `__del__()` - Destructor that calls close()
- `_on_settings_changed()` - Handles changes to settings
- `_handle_clipboard_copy()` - Handles text copying and UI updates
- `_delayed_stop()` - Handles delayed stop logic

## 2. Core Components

### `AudioRecorder` (core/audio.py)

Low-level audio capture:
- `__init__()` - Initializes the audio recorder
- `start()` - Starts audio recording
- `stop()` - Stops audio recording
- `_audio_callback()` - Processes audio data from the stream

Audio utilities:
- `get_input_devices()` - Gets all available input devices
- `_resample()` - Resamples audio data to a different sample rate
- `get_audio_data()` - Gets a copy of current audio data
- `is_silent()` - Checks if an audio chunk is silent
- `save_to_wav()` - Saves the current buffer to a WAV file
- `save_to_temp_wav()` - Saves the current buffer to a temporary WAV file
- `__del__()` - Cleans up resources when object is destroyed

### `TranscriptionWorker` (core/processing.py)

Worker thread management:
- `__init__()` - Initializes the worker with VAD settings
- `start()` - Starts the worker thread
- `stop()` - Stops the worker thread
- `close()` - Cleans up resources

Audio processing:
- `add_audio()` - Adds audio data to the processing queue
- `_is_silent()` - Determines if audio chunk is silent using SilenceDetector
- `_worker()` - Worker thread that processes audio chunks
- `_process_chunk()` - Processes a chunk of audio data
- `has_recent_audio()` - Checks if audio was received recently

Configuration:
- `_validate_vad_mode()` - Validates and potentially adjusts VAD mode
- `update_vad_settings()` - Updates Voice Activity Detection settings
- `__del__()` - Destructor that calls close()

### `TranscriptionEngine` (core/transcription.py)

Core transcription:
- `__init__()` - Initializes the transcription engine
- `load_model()` - Loads the Whisper model
- `transcribe()` - Transcribes audio data
- `_transcribe_whisper()` - Transcribes with Python Whisper
- `_transcribe_whisper_cpp()` - Transcribes with whisper.cpp

Configuration:
- `set_language()` - Sets the language for transcription
- `_validate_model_size()` - Validates the model size
- `_ensure_ffmpeg()` - Ensures FFmpeg is available
- `_get_model_path()` - Gets the path to Whisper model

### `ModelManager` (core/model_manager.py)

Model operations:
- `__init__()` - Initializes the model manager
- `check_whisper_model()` - Checks if the Whisper model exists
- `download_whisper_model()` - Downloads a Whisper model
- `check_whisper_cpp()` - Checks if whisper.cpp is available
- `setup_whisper_cpp()` - Sets up whisper.cpp
- `download_ggml_model()` - Downloads a GGML model
- `check_ggml_model()` - Checks if a GGML model exists
- `select_ggml_model()` - Selects a GGML model
- `find_ggml_models()` - Finds all available GGML models

### `WhisperCppWrapper` (core/whisper_cpp.py)

External interface:
- `__init__()` - Initializes the whisper.cpp wrapper
- `transcribe()` - Transcribes audio using whisper.cpp

Implementation details:
- `_write_wav_file()` - Writes audio data to a WAV file
- `_run_whisper_cpp()` - Runs the whisper.cpp subprocess
- `_parse_output()` - Parses whisper.cpp output

### `ChunkBuffer` (core/chunk_buffer.py)

Buffer management:
- `__init__()` - Initializes the audio chunk buffer
- `add_chunk()` - Adds an audio chunk to the buffer
- `get_chunks()` - Gets chunks from the buffer
- `clear()` - Clears the buffer

## 3. UI Components

### `TranscriptionUI` (ui/window.py)

UI initialization:
- `__init__()` - Initializes the UI
- `_setup_ui()` - Sets up the UI components
- `run()` - Starts the UI event loop

UI event callbacks:
- `_on_continuous_changed()` - Handles continuous mode changes
- `_on_language_changed()` - Handles language changes
- `set_continuous_mode_handler()` - Sets handler for continuous mode changes
- `set_language_handler()` - Sets handler for language changes
- `_setup_vad_settings()` - Sets up VAD settings in the UI
- `_connect_vad_settings_callbacks()` - Connects VAD settings callbacks
- `_show_settings()` - Shows the settings dialog
- `set_config()` - Sets the configuration

UI state management:
- `_start_recording_animation()` - Starts recording animation
- `_stop_recording_animation()` - Stops recording animation
- `update_status_color()` - Updates status frame color
- `update_status()` - Updates the status display
- `update_word_count()` - Updates word count display
- `update_text()` - Updates text display with highlighting
- `update_status_text()` - Updates status text
- `show_restart_required()` - Shows restart required message
- `show_language_error()` - Shows language error message
- `__del__()` - Cleans up UI resources

### `KeyboardEventManager` (ui/events.py)

Keyboard event management:
- `__init__()` - Initializes the event manager
- `setup_hotkeys()` - Sets up keyboard hotkeys
- `clear_hotkeys()` - Clears all registered hotkeys
- `__del__()` - Cleans up event handlers

Event routing (connecting keyboard shortcuts to service methods):
- `_toggle_recording()` - Routes recording toggle hotkey to service
- `_save_transcript()` - Routes save hotkey to service's save_transcript method
- `_clear_transcript()` - Routes clear hotkey to service's clear_transcript method

Text handling:
- `_toggle_insert_mode()` - Toggles direct text insertion mode
- `_paste_text()` - Pastes text at cursor position
- `_insert_or_copy_text()` - Inserts text or copies to clipboard

### Dialog Classes (ui/dialogs.py)

#### `ModelSelectionDialog`
- Presents UI for model selection
- Routes user choices to the appropriate handlers

#### `DownloadProgressDialog`
- Shows download progress for models
- Provides feedback for long-running operations

#### `SettingsDialog`
- Presents UI for application settings
- Routes settings changes to the configuration system

## 4. Utility Components

### `TranscriptManager` (utils/file_ops.py)

File operations (actual implementation):
- `__init__()` - Initializes the transcript manager
- `save_transcript()` - Saves transcript text to file
- `read_transcript()` - Reads transcript from file
- `get_transcript_files()` - Gets list of available transcript files

Directory management:
- `get_transcript_path()` - Generates path for new transcript file
- `get_transcript_dir()` - Gets transcript directory
- `ensure_dir_exists()` - Ensures directory exists

### `TextProcessor` (utils/text_processor.py)

Text processing:
- `__init__()` - Initializes the text processor
- `filter_hallucinations()` - Filters out hallucinated phrases
- `is_valid_utterance()` - Validates if text is a meaningful utterance
- `format_transcript()` - Formats transcript text
- `append_text()` - Properly appends text with formatting

### `SilenceDetector` (utils/silence_detection.py)

Silence detection:
- `__init__()` - Initializes silence detector
- `is_silent()` - Determines if audio is silent
- `update_settings()` - Updates detector settings

Implementation details:
- `_validate_mode()` - Validates/adjusts mode based on available modules
- `_init_detector()` - Initializes the detector based on selected mode
- `_is_silent_basic()` - Basic RMS-based silence detection
- `_is_silent_webrtc()` - WebRTC silence detection
- `_is_silent_silero()` - Silero VAD silence detection

### Clipboard Utilities (utils/clipboard.py)

Core clipboard operations:
- `copy_to_clipboard()` - Copies text to system clipboard with error handling

### Lifecycle Management (utils/lifecycle.py)

#### `Closeable` Interface
- `close()` - Abstract method for cleaning up resources
- `__enter__()` - Context manager entry
- `__exit__()` - Context manager exit

#### `Component` Interface
- `start()` - Abstract method for starting a component
- `stop()` - Abstract method for stopping a component
- `close()` - Method for cleaning up resources
- `restart()` - Method for restarting a component

### Logging Utilities (utils/logging.py)

- `setup_logging()` - Sets up logging
- `get_log_file_path()` - Gets log file path
- `log_system_info()` - Logs system information

## 5. Configuration

### `Config` (config.py)

Configuration management:
- `__init__()` - Initializes configuration
- `load()` - Loads configuration from file
- `save()` - Saves configuration to file
- `get()` - Gets a configuration value
- `set()` - Sets a configuration value
- `validate()` - Validates configuration

## Component Interactions and Responsibility Boundaries

### Service Layer (Coordinator)
- Coordinates all other components
- Handles state management and lifecycle
- Routes UI events to appropriate actions
- Provides feedback to the UI
- Doesn't implement core functionality, but delegates to utilities

### UI Components (Presentation)
- Handle user interface presentation
- Capture user interactions
- Forward events to service layer
- Display application state

### Core Components (Domain Logic)
- Implement domain-specific functionality
- Remain unaware of UI or service layer
- Operate on data independently
- Maintain their own state

### Utility Components (Reusable Functions)
- Provide reusable functionality
- Have no dependency on service or UI
- Perform single, well-defined operations
- Remain stateless where possible

### Configuration (Settings)
- Centrally manages application settings
- Provides validation and persistence

This architecture ensures proper separation of concerns:
- The **Service Layer** manages state and coordinates components
- The **UI Layer** handles user interaction and display
- The **Core Components** implement domain-specific logic
- The **Utility Components** provide reusable functionality
- **Similar method names across layers** represent different aspects of the same operation (routing, coordination, implementation) 