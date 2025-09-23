# API Documentation

This document provides API-level documentation for the Voice Input Service, detailing the interfaces, classes, and methods that developers may need to use or extend.

## Core Classes

### VoiceInputService

The main service class that orchestrates all components.

```python
class VoiceInputService(EventHandler, Closeable):
    def __init__(self, config: Config, ui: TranscriptionUI, transcriber: TranscriptionEngine) -> None:
        """Initialize service components."""
        
    def start_recording(self) -> bool:
        """Start audio recording.
        
        Returns:
            bool: True if recording started successfully
        """
        
    def stop_recording(self) -> str:
        """Stop recording and return transcription.
        
        Returns:
            str: The accumulated transcription text
        """
        
    def _process_audio_chunk(self, audio_data: bytes) -> Optional[str]:
        """Process audio chunk with silence detection and filtering.
        
        Args:
            audio_data: Raw audio data to process
            
        Returns:
            Optional[str]: Filtered transcription text or None
        """
        
    def _filter_hallucinations(self, text: str) -> str:
        """Filter out common hallucinated phrases.
        
        Args:
            text: Text to filter
            
        Returns:
            str: Filtered text
        """
        
    def _on_transcription_result(self, text: str) -> None:
        """Handle transcription result with mode-specific behavior."""
        
    def close(self) -> None:
        """Clean up all resources properly."""
```

### TranscriptionEngine

Handles audio-to-text conversion using Whisper.

```python
class TranscriptionEngine:
    def __init__(self, model_name: str = "base", language: str = "en") -> None:
        """Initialize the transcription engine.
        
        Args:
            model_name: Whisper model name
            language: Default language code
        """
        
    def transcribe(self, audio: Union[bytes, np.ndarray]) -> dict[str, Any]:
        """Transcribe audio data.
        
        Args:
            audio: Audio data to transcribe
            
        Returns:
            dict: Transcription result with text and metadata
        """
        
    def set_language(self, language: str) -> None:
        """Set transcription language.
        
        Args:
            language: ISO language code
        """
```

### KeyboardEventManager

Manages keyboard shortcuts and text insertion.

```python
class KeyboardEventManager:
    def __init__(self, handler: EventHandler) -> None:
        """Initialize keyboard event manager.
        
        Args:
            handler: Event handler instance
        """
        
    def setup_hotkeys(self) -> None:
        """Setup keyboard shortcuts."""
        
    def _toggle_insert_mode(self) -> None:
        """Toggle between direct insertion and clipboard modes."""
        
    def _insert_or_copy_text(self, text: str) -> None:
        """Insert text directly or copy to clipboard based on mode.
        
        Args:
            text: Text to insert/copy
        """
        
    def _paste_text(self) -> None:
        """Paste text at current cursor position."""
```

### AudioProcessor

Handles audio capture with silence detection.

```python
class AudioProcessor:
    def __init__(
        self,
        sample_rate: int,
        chunk_size: int,
        silence_threshold: float = 500.0,
        on_data: Callable[[bytes], None] = None
    ) -> None:
        """Initialize audio processor.
        
        Args:
            sample_rate: Audio sample rate
            chunk_size: Processing chunk size
            silence_threshold: RMS threshold for silence
            on_data: Callback for audio data
        """
        
    def process_chunk(self, data: bytes) -> tuple[bool, float]:
        """Process audio chunk and detect silence.
        
        Args:
            data: Raw audio data
            
        Returns:
            tuple: (is_silent, rms_value)
        """
```

### TranscriptionWorker

Handles audio processing in a background thread with VAD support.

```python
class TranscriptionWorker(Component):
    def __init__(
        self,
        process_func: Callable[[bytes], Optional[str]],
        on_result: Callable[[str], None],
        min_audio_length: int = 32000,
        vad_mode: Literal["basic", "webrtc", "silero"] = "silero",
        vad_aggressiveness: int = 3,
        vad_threshold: float = 0.5,
        silence_duration: float = 0.5,
        sample_rate: int = 16000
    ) -> None:
        """Initialize the worker with VAD settings."""
        
    def start(self) -> bool:
        """Start the worker thread.
        
        Returns:
            bool: True if started successfully
        """
        
    def stop(self) -> None:
        """Stop the worker thread."""
        
    def close(self) -> None:
        """Clean up resources."""
        
    def add_audio(self, data: bytes) -> None:
        """Add audio data to the processing queue.
        
        Args:
            data: Audio data to process
        """
        
    def has_recent_audio(self) -> bool:
        """Check if audio was received recently.
        
        Returns:
            bool: True if audio was received within silence_duration
        """
        
    def update_vad_settings(
        self,
        mode: Optional[str] = None,
        aggressiveness: Optional[int] = None,
        threshold: Optional[float] = None
    ) -> None:
        """Update VAD settings.
        
        Args:
            mode: VAD mode (basic, webrtc, silero)
            aggressiveness: WebRTC aggressiveness (0-3)
            threshold: Silero threshold (0.0-1.0)
        """
```

## Utility Classes

### SilenceDetector

Voice activity detection utility with multiple detection methods.

```python
class SilenceDetector:
    def __init__(
        self, 
        mode: Literal["basic", "webrtc", "silero"] = "basic",
        sample_rate: int = 16000,
        aggressiveness: int = 3,
        threshold: float = 0.5,
        silence_rms_threshold: int = 400
    ) -> None:
        """Initialize the silence detector.
        
        Args:
            mode: Detection mode to use
            sample_rate: Audio sample rate in Hz
            aggressiveness: WebRTC aggressiveness (0-3)
            threshold: Silero threshold (0.0-1.0)
            silence_rms_threshold: RMS threshold for basic detection
        """
        
    def is_silent(self, audio_data: bytes) -> bool:
        """Determine if audio is silent using configured detector.
        
        Args:
            audio_data: Raw audio bytes to analyze
            
        Returns:
            bool: True if audio is silent, False if speech detected
        """
        
    def update_settings(
        self, 
        mode: Optional[str] = None,
        aggressiveness: Optional[int] = None,
        threshold: Optional[float] = None,
        silence_threshold: Optional[int] = None
    ) -> None:
        """Update detector settings.
        
        Args:
            mode: New detection mode
            aggressiveness: New WebRTC aggressiveness
            threshold: New Silero threshold
            silence_threshold: New RMS threshold for basic detection
        """
```

### Clipboard Utilities

```python
def copy_to_clipboard(text: str) -> bool:
    """Copy text to system clipboard.
    
    Args:
        text: Text to copy to clipboard
        
    Returns:
        bool: True if successful, False otherwise
    """
```

### Lifecycle Interfaces

```python
class Closeable(ABC):
    """Interface for objects that need explicit cleanup."""
    
    @abstractmethod
    def close(self) -> None:
        """Clean up resources."""
        
    def __enter__(self):
        """Context manager entry."""
        return self
        
    def __exit__(self, exc_type, exc_val, exc_tb):
        """Context manager exit."""
        self.close()

class Component(Closeable):
    """Base interface for components with standardized lifecycle."""
    
    @abstractmethod
    def start(self) -> bool:
        """Start the component.
        
        Returns:
            bool: True if started successfully, False otherwise
        """
        
    @abstractmethod
    def stop(self) -> None:
        """Stop the component."""
        
    def restart(self) -> bool:
        """Restart the component.
        
        Returns:
            bool: True if restarted successfully, False otherwise
        """
```

### Event Handling

The application uses a protocol-based event handling system that enables clear communication between components:

```python
class EventHandler(Protocol):
    """Protocol for event handlers."""
    def start_recording(self) -> bool: ...
    def stop_recording(self) -> str: ...
    def save_transcript(self) -> None: ...
    def clear_transcript(self) -> None: ...
```

The service implements this protocol and the UI components register callbacks:

```python
# In service initialization
def _setup_ui_events(self) -> None:
    """Setup UI event callbacks."""
    # Register callbacks with the UI
    self.ui.set_continuous_mode_handler(self._toggle_continuous_mode)
    self.ui.set_language_handler(self._change_language)

# In UI implementation
def set_continuous_mode_handler(self, handler: Callable[[bool], None]) -> None:
    """Register handler for continuous mode changes."""
    self._continuous_mode_handler = handler
```

This approach provides:
1. Type-safe event handling with clear interfaces
2. Direct method calls for better performance
3. Simple dependency management through handler registration

## Configuration Classes

### Config

Central configuration with nested components.

```python
class Config(BaseModel):
    """Application configuration."""
    audio: AudioConfig
    transcription: TranscriptionConfig
    ui: UIConfig
    hotkeys: HotkeyConfig
    
    def save(self) -> None:
        """Save configuration to file."""
        
    @classmethod
    def load(cls) -> Config:
        """Load configuration from file."""
```

### AudioConfig

Audio processing configuration.

```python
class AudioConfig(BaseModel):
    """Audio configuration settings."""
    sample_rate: int = Field(16000, description="Audio sample rate")
    chunk_size: int = Field(1024, description="Audio chunk size")
    channels: int = Field(1, description="Number of audio channels")
    vad_mode: str = Field("silero", description="Voice activity detection mode")
    vad_aggressiveness: int = Field(3, description="WebRTC VAD aggressiveness")
    vad_threshold: float = Field(0.5, description="Silero VAD threshold")
    device_index: Optional[int] = Field(None, description="Audio device index")
```

### TranscriptionConfig

Transcription settings.

```python
class TranscriptionConfig(BaseModel):
    """Transcription configuration settings."""
    model_name: str = Field("base", description="Whisper model name")
    language: str = Field("en", description="Default language code")
    min_chunk_size: int = Field(32000, description="Minimum audio chunk size")
    hallucination_patterns: list[str] = Field(default_factory=list)
```

### UIConfig

User interface configuration.

```python
class UIConfig(BaseModel):
    """UI configuration settings."""
    window_title: str = Field("Voice Input Service")
    update_interval: float = Field(0.25, description="UI update interval in seconds")
    status_colors: dict[str, str] = Field(default_factory=dict)
```

### HotkeyConfig

Keyboard shortcut configuration.

```python
class HotkeyConfig(BaseModel):
    """Hotkey configuration settings."""
    toggle_recording: str = Field("alt+r", description="Toggle recording")
    toggle_insert_mode: str = Field("alt+i", description="Toggle insert mode")
    paste_text: str = Field("alt+v", description="Paste text")
    save_transcript: str = Field("alt+s", description="Save transcript")
    clear_text: str = Field("alt+c", description="Clear text")
```

## Usage Examples

### Basic Usage

```python
from voice_input_service.service import VoiceInputService
from voice_input_service.core.config import Config

# Load configuration
config = Config.load()

# Create and run service
service = VoiceInputService(config)
service.run()
```

### Custom Audio Configuration

```python
from voice_input_service.core.config import AudioConfig

# Create custom audio config
audio_config = AudioConfig(
    sample_rate=44100,
    chunk_size=2048,
    silence_threshold=750.0
)

# Update main config
config.audio = audio_config
```

### Custom Transcription Settings

```python
from voice_input_service.core.config import TranscriptionConfig

# Create custom transcription config
transcription_config = TranscriptionConfig(
    model_name="medium",
    language="fr",
    min_chunk_size=48000
)

# Update main config
config.transcription = transcription_config
```

### Custom Hotkeys

```python
from voice_input_service.core.config import HotkeyConfig

# Create custom hotkey config
hotkey_config = HotkeyConfig(
    toggle_recording="ctrl+shift+r",
    toggle_insert_mode="ctrl+shift+i"
)

# Update main config
config.hotkeys = hotkey_config
``` 