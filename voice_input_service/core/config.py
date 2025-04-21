from __future__ import annotations
import os
from pathlib import Path
from typing import Optional, Union

import pyaudio
from pydantic import BaseModel, Field, field_validator

class AudioConfig(BaseModel):
    """Audio recording configuration."""
    sample_rate: int = Field(16000, description="Audio sample rate in Hz")
    chunk_size: int = Field(2048, description="Size of audio chunks to process")
    channels: int = Field(1, description="Number of audio channels (1=mono, 2=stereo)")
    format_type: int = Field(pyaudio.paInt16, description="Audio format type")
    device_index: Optional[int] = Field(None, description="Input device index, None for default")
    silence_threshold: float = Field(0.04, description="RMS threshold for silence detection")
    min_silence_length: float = Field(2.5, description="Minimum silence length in seconds")
    min_audio_length: int = Field(32000, description="Minimum audio length in samples before processing")
    min_process_interval: float = Field(0.5, description="Minimum interval between processing audio chunks")
    
    @field_validator('sample_rate')
    @classmethod
    def validate_sample_rate(cls, v: int) -> int:
        valid_rates = [8000, 16000, 22050, 44100, 48000]
        if v not in valid_rates:
            raise ValueError(f"Sample rate must be one of {valid_rates}, got {v}")
        return v
    
    @field_validator('channels')
    @classmethod
    def validate_channels(cls, v: int) -> int:
        if v not in [1, 2]:
            raise ValueError(f"Channels must be 1 (mono) or 2 (stereo), got {v}")
        return v

class TranscriptionConfig(BaseModel):
    """Transcription configuration."""
    model_name: str = Field("base", description="Whisper model name (tiny, base, small, medium, large)")
    device: Optional[str] = Field(None, description="Device to run model on (cpu, cuda)")
    compute_type: str = Field("float32", description="Computation type (float16, float32, int8)")
    language: Optional[str] = Field("en", description="Language code for transcription")
    translate: bool = Field(False, description="Whether to translate to English")
    cache_dir: Optional[str] = Field(None, description="Directory to cache models")
    min_chunk_size: int = Field(32000, description="Minimum audio chunk size to process (in bytes)")
    
    # whisper.cpp specific options
    use_cpp: bool = Field(True, description="Whether to use whisper.cpp instead of Python Whisper")
    whisper_cpp_path: str = Field("C:\\Users\\Cicada38\\Projects\\whisper.cpp\\build\\bin\\Release\\whisper-cli.exe", description="Path to the whisper.cpp executable")
    ggml_model_path: Optional[str] = Field(None, description="Path to specific GGML model file")
    
    @field_validator('model_name')
    @classmethod
    def validate_model_name(cls, v: str) -> str:
        valid_models = ["tiny", "base", "small", "medium", "large"]
        if v.lower() not in valid_models:
            raise ValueError(f"Model name must be one of {valid_models}, got {v}")
        return v.lower()
    
    @field_validator('compute_type')
    @classmethod
    def validate_compute_type(cls, v: str) -> str:
        valid_types = ["float16", "float32", "int8"]
        if v not in valid_types:
            raise ValueError(f"Compute type must be one of {valid_types}, got {v}")
        return v

class UIConfig(BaseModel):
    """User interface configuration."""
    window_title: str = Field("Voice Input Service", description="Window title")
    window_width: int = Field(600, description="Window width")
    window_height: int = Field(400, description="Window height")
    font_family: str = Field("Segoe UI", description="Font family")
    font_size: int = Field(11, description="Base font size")
    highlight_new_text: bool = Field(True, description="Whether to highlight new text")
    highlight_color: str = Field("#e6f2ff", description="Color to highlight new text")
    
    @field_validator('window_width', 'window_height')
    @classmethod
    def validate_dimensions(cls, v: int) -> int:
        if v < 200:
            raise ValueError("Window dimensions must be at least 200px")
        return v

class HotkeyConfig(BaseModel):
    """Hotkey configuration."""
    start_recording: str = Field("ctrl+alt+r", description="Hotkey to start recording")
    stop_recording: str = Field("ctrl+alt+s", description="Hotkey to stop recording")
    toggle_recording: str = Field("ctrl+alt+t", description="Hotkey to toggle recording")
    abort_recording: str = Field("ctrl+alt+a", description="Hotkey to abort recording")
    copy_text: str = Field("ctrl+alt+c", description="Hotkey to copy text")
    clear_text: str = Field("ctrl+alt+d", description="Hotkey to clear text")

class Config(BaseModel):
    """Configuration for voice input service."""
    audio: AudioConfig = Field(default_factory=AudioConfig)
    transcription: TranscriptionConfig = Field(default_factory=TranscriptionConfig)
    ui: UIConfig = Field(default_factory=UIConfig)
    hotkeys: HotkeyConfig = Field(default_factory=HotkeyConfig)
    debug: bool = Field(False, description="Enable debug mode")
    log_level: str = Field("INFO", description="Logging level")
    data_dir: Path = Field(default_factory=lambda: Path.home() / ".voice_input_service")
    
    @field_validator('log_level')
    @classmethod
    def validate_log_level(cls, v: str) -> str:
        valid_levels = ["DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"]
        if v.upper() not in valid_levels:
            raise ValueError(f"Log level must be one of {valid_levels}, got {v}")
        return v.upper()
    
    @field_validator('data_dir')
    @classmethod
    def validate_data_dir(cls, v: Path) -> Path:
        v.mkdir(parents=True, exist_ok=True)
        return v
    
    def save(self, config_path: Optional[Union[str, Path]] = None) -> None:
        """Save configuration to a file.
        
        Args:
            config_path: Path to save configuration to
        """
        if config_path is None:
            config_path = self.data_dir / "config.json"
        else:
            config_path = Path(config_path)
            
        config_path.parent.mkdir(parents=True, exist_ok=True)
        with open(config_path, "w") as f:
            f.write(self.model_dump_json(indent=2))
    
    @classmethod
    def load(cls, config_path: Optional[Union[str, Path]] = None) -> Config:
        """Load configuration from a file.
        
        Args:
            config_path: Path to load configuration from
            
        Returns:
            Loaded configuration
        """
        if config_path is None:
            default_path = Path.home() / ".voice_input_service" / "config.json"
            if default_path.exists():
                config_path = default_path
            else:
                return cls()
        else:
            config_path = Path(config_path)
            
        if config_path.exists():
            with open(config_path, "r") as f:
                return cls.model_validate_json(f.read())
        
        return cls()

# Create a default configuration instance
default_config = Config() 