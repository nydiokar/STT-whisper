from __future__ import annotations
from pydantic import BaseModel, ConfigDict
from typing import Literal
import pyaudio

class Config(BaseModel):
    """Configuration settings for the voice input service."""
    
    model_config = ConfigDict(frozen=True)
    
    # Whisper model settings
    model_size: Literal["tiny", "base", "small", "medium", "large"] = "medium"
    language: str = "en"  # ISO 639-1 language code
    keep_context: bool = True  # Whether to maintain context between chunks
    
    # Audio settings are fixed for Whisper compatibility
    sample_rate: int = 44100  # Higher sample rate for better quality
    chunk_size: int = 4096    # Larger chunk size
    channels: int = 1
    format: int = pyaudio.paInt16
    min_audio_length: int = 32000  # Minimum number of samples to process

    @property
    def model_name(self) -> str:
        """Alias for model_size to maintain compatibility."""
        return self.model_size

# Default configuration
config = Config()
