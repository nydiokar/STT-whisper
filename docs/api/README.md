# API Documentation

This document provides API-level documentation for the Voice Input Service, detailing the interfaces, classes, and methods that developers may need to use or extend.

## Core Classes

### VoiceInputService

The main service class that orchestrates all components.

```python
class VoiceInputService(EventHandler):
    def __init__(self) -> None:
        # Initialize service components
        
    def start_recording(self) -> bool:
        """Start audio recording.
        
        Returns:
            bool: True if recording started successfully, False otherwise
        """
        
    def stop_recording(self) -> str:
        """Stop audio recording and return the transcription.
        
        Returns:
            str: The accumulated transcription text
        """
        
    def save_transcript(self) -> None:
        """Save the current transcript to a file."""
        
    def clear_transcript(self) -> None:
        """Clear the current transcript."""
        
    def run(self) -> None:
        """Run the service main loop."""
```

### AudioProcessor

Handles audio input capture and streaming.

```python
class AudioProcessor:
    def __init__(self, config: AudioConfig) -> None:
        """Initialize audio processor.
        
        Args:
            config: Audio configuration settings
        """
        
    def start_stream(self) -> bool:
        """Start the audio stream.
        
        Returns:
            bool: True if stream started successfully, False otherwise
        """
        
    def stop_stream(self) -> None:
        """Stop the audio stream."""
        
    def cleanup(self) -> None:
        """Clean up audio resources."""
```

### TranscriptionService

Handles audio-to-text conversion using Whisper.

```python
class TranscriptionService:
    def __init__(self, config: TranscriptionConfig) -> None:
        """Initialize transcription service.
        
        Args:
            config: Transcription configuration settings
        """
        
    def process_audio(self, audio_data: bytes, context: str = "", language: str = "en") -> str:
        """Process audio data and return transcription.
        
        Args:
            audio_data: Raw audio data to transcribe
            context: Optional context from previous transcriptions
            language: Language code for transcription
            
        Returns:
            str: Transcribed text
        """
```

### TranscriptionWorker

Background worker for processing audio asynchronously.

```python
class TranscriptionWorker:
    def __init__(
        self, 
        process_func: Callable[[bytes], str], 
        on_result: Callable[[str], None],
        min_audio_length: int = 16000
    ) -> None:
        """Initialize worker thread.
        
        Args:
            process_func: Function to process audio data
            on_result: Callback for transcription results
            min_audio_length: Minimum audio chunk size to process
        """
        
    def start(self) -> None:
        """Start the worker thread."""
        
    def stop(self) -> None:
        """Stop the worker thread."""
        
    def process(self, audio_data: bytes) -> None:
        """Add audio data to the processing queue.
        
        Args:
            audio_data: Raw audio data to process
        """
```

### TranscriptionUI

Manages the user interface.

```python
class TranscriptionUI:
    def __init__(self) -> None:
        """Initialize the UI components."""
        
    def update_status(self, is_recording: bool, elapsed: float = 0, continuous: bool = False) -> None:
        """Update the status display.
        
        Args:
            is_recording: Whether recording is active
            elapsed: Elapsed recording time in seconds
            continuous: Whether continuous mode is active
        """
        
    def update_word_count(self, count: int) -> None:
        """Update the word count display.
        
        Args:
            count: Current word count
        """
        
    def update_text(self, text: str) -> None:
        """Update the text display.
        
        Args:
            text: Text to display
        """
        
    def run(self) -> None:
        """Start the UI event loop."""
```

### KeyboardEventManager

Manages keyboard shortcuts and event handling.

```python
class KeyboardEventManager:
    def __init__(self, handler: EventHandler) -> None:
        """Initialize keyboard event manager.
        
        Args:
            handler: Event handler instance
        """
        
    def setup_hotkeys(self) -> None:
        """Setup keyboard event handlers."""
```

## Configuration Classes

### Config

Central configuration for the application.

```python
class Config:
    model_size: str = "small"
    sample_rate: int = 16000
    chunk_size: int = 1024
    channels: int = 1
    format: int = pyaudio.paInt16
    min_audio_length: int = 16000
    keep_context: bool = True
    hotkey: str = "alt+r"
```

### AudioConfig

Configuration for audio processing.

```python
class AudioConfig:
    def __init__(
        self,
        sample_rate: int = 16000,
        chunk_size: int = 1024,
        channels: int = 1,
        format: int = pyaudio.paInt16
    ) -> None:
        """Initialize audio configuration.
        
        Args:
            sample_rate: Audio sample rate
            chunk_size: Audio chunk size
            channels: Number of audio channels
            format: PyAudio format
        """
```

### TranscriptionConfig

Configuration for transcription processing.

```python
class TranscriptionConfig:
    def __init__(
        self,
        model_name: str = "small",
        sample_rate: int = 16000,
        chunk_size: int = 1024,
        channels: int = 1,
        format: int = pyaudio.paInt16,
        processing_chunk_size: int = 16000,
        language: str = "en"
    ) -> None:
        """Initialize transcription configuration.
        
        Args:
            model_name: Whisper model name (tiny, base, small, medium, large)
            sample_rate: Audio sample rate
            chunk_size: Audio chunk size
            channels: Number of audio channels
            format: PyAudio format
            processing_chunk_size: Minimum audio size for processing
            language: Default language for transcription
        """
```

## Utility Classes

### TranscriptManager

Manages transcript file operations.

```python
class TranscriptManager:
    def __init__(self, output_dir: Optional[str] = None) -> None:
        """Initialize transcript manager.
        
        Args:
            output_dir: Directory to save transcripts
        """
        
    def save_transcript(self, text: str) -> Optional[str]:
        """Save transcript to file.
        
        Args:
            text: Text to save
            
        Returns:
            Optional[str]: Path to saved file or None if failed
        """
        
    def get_transcript_files(self) -> list[str]:
        """Get list of saved transcript files.
        
        Returns:
            list[str]: List of file paths
        """
```

## Protocols

### EventHandler

Protocol for event handling.

```python
class EventHandler(Protocol):
    """Protocol for event handlers."""
    def start_recording(self) -> bool: ...
    def stop_recording(self) -> str: ...
    def save_transcript(self) -> None: ...
    def clear_transcript(self) -> None: ...
```

## Functions

### setup_logging

Set up logging configuration.

```python
def setup_logging(log_dir: Optional[str] = None) -> logging.Logger:
    """Set up logging configuration.
    
    Args:
        log_dir: Optional custom directory for log files
        
    Returns:
        logging.Logger: Configured logger instance
    """
```

## Usage Examples

### Basic Service Usage

```python
from voice_input_service.service import VoiceInputService

# Create and run the service
service = VoiceInputService()
service.run()
```

### Custom Audio Configuration

```python
from voice_input_service.service import VoiceInputService
from voice_input_service.core.audio import AudioConfig
import pyaudio

# Create custom audio config
audio_config = AudioConfig(
    sample_rate=44100,
    chunk_size=2048,
    channels=1,
    format=pyaudio.paInt16
)

# Inject into service
service = VoiceInputService()
service.audio_config = audio_config
service.run()
```

### Custom Transcription Configuration

```python
from voice_input_service.service import VoiceInputService
from voice_input_service.core.transcription import TranscriptionConfig

# Create custom transcription config
transcription_config = TranscriptionConfig(
    model_name="medium",  # Higher quality model
    language="fr"         # French language
)

# Inject into service
service = VoiceInputService()
service.transcription_config = transcription_config
service.run()
``` 