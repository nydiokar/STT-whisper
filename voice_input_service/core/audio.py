from __future__ import annotations
import queue
import pyaudio
import logging
from dataclasses import dataclass
from typing import Optional, Callable
from pydantic import BaseModel, ConfigDict

class AudioConfig(BaseModel):
    """Configuration for audio processing."""
    model_config = ConfigDict(frozen=True)  # Makes config immutable
    
    sample_rate: int = 16000
    chunk_size: int = 1024
    channels: int = 1
    format: int = pyaudio.paInt16

class AudioProcessor:
    """Handles audio recording and processing."""
    
    def __init__(self, config: AudioConfig) -> None:
        self.logger = logging.getLogger("VoiceService.Audio")
        self.config = config
        self.recording: bool = False
        self.audio_queue: queue.Queue[bytes] = queue.Queue()
        self.audio: pyaudio.PyAudio = pyaudio.PyAudio()
        self.stream: Optional[pyaudio.Stream] = None
        self.logger.info("Audio processor initialized")
    
    def start_stream(self) -> bool:
        """Start the audio stream."""
        try:
            if self.stream:
                self.logger.debug("Stopping existing stream")
                self.stream.stop_stream()
                self.stream.close()
            
            self.logger.debug("Opening new audio stream")
            self.stream = self.audio.open(
                format=self.config.format,
                channels=self.config.channels,
                rate=self.config.sample_rate,
                input=True,
                frames_per_buffer=self.config.chunk_size,
                stream_callback=self._audio_callback
            )
            self.logger.info("Audio stream started successfully")
            return True
        except Exception as e:
            self.logger.error(f"Failed to start audio stream: {e}", exc_info=True)
            return False
    
    def stop_stream(self) -> None:
        """Stop the audio stream."""
        if self.stream:
            self.logger.debug("Stopping audio stream")
            self.stream.stop_stream()
            self.stream.close()
            self.stream = None
            self.logger.info("Audio stream stopped")
    
    def _audio_callback(self, in_data: bytes, frame_count: int, 
                       time_info: dict, status: int) -> tuple[bytes, int]:
        """Handle incoming audio data."""
        if status:
            self.logger.warning(f"Audio callback status: {status}")
        if self.recording:
            self.audio_queue.put(in_data)
        return (in_data, pyaudio.paContinue)
    
    def __del__(self) -> None:
        """Clean up audio resources."""
        self.logger.debug("Cleaning up AudioProcessor resources")
        self.stop_stream()
        if hasattr(self, 'audio'):
            self.audio.terminate() 