from __future__ import annotations
import queue
import pyaudio
import logging
from dataclasses import dataclass
from typing import Any, Optional, Callable, Dict, Tuple
from pydantic import BaseModel, ConfigDict

class AudioConfig(BaseModel):
    """Configuration for audio processing."""
    model_config = ConfigDict(frozen=True)  # Makes config immutable
    
    sample_rate: int = 44100  # Changed to standard microphone rate
    chunk_size: int = 4096    # Larger chunk size for better quality
    channels: int = 1
    format: int = pyaudio.paInt16

class AudioProcessor:
    """Handles audio recording and processing."""
    
    def __init__(self, config: AudioConfig) -> None:
        """Initialize audio processor with the given configuration."""
        self.logger = logging.getLogger("VoiceService.Audio")
        self.config = config
        self.recording = False
        self.audio_queue: queue.Queue[bytes] = queue.Queue()
        self.callback_counter = 0  # Track callback count for logging
        
        # Create PyAudio instance
        self.audio = pyaudio.PyAudio()
        self.stream: Optional[Any] = None  # PyAudio.Stream type is not exposed in typings
        
        # Get default device info
        try:
            device_info = self.audio.get_default_input_device_info()
            self.device_rate = int(device_info.get('defaultSampleRate', 44100))
            self.logger.info(f"Default microphone sample rate: {self.device_rate}Hz")
        except Exception as e:
            self.logger.warning(f"Could not get device info: {e}")
            self.device_rate = 44100
            
        self.logger.debug(f"Audio processor initialized with {config}")
    
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
    
    def _audio_callback(self, in_data: bytes, frame_count: int, time_info: Dict[str, Any], status: int) -> Tuple[bytes, int]:
        """Process audio data from the microphone."""
        if self.recording:
            # Only log occasionally, this is called very frequently
            if self.callback_counter == 0 or self.callback_counter % 1000 == 0:
                self.logger.info(f"Audio streaming active, received {len(in_data)} bytes")
            self.callback_counter += 1
            
            self.audio_queue.put(in_data)
        
        return (in_data, pyaudio.paContinue)
    
    def get_audio_data(self) -> bytes:
        """Get audio data from the queue."""
        try:
            return self.audio_queue.get_nowait()
        except queue.Empty:
            return b""
    
    def __del__(self) -> None:
        """Clean up audio resources."""
        self.logger.debug("Cleaning up AudioProcessor resources")
        self.stop_stream()
        if hasattr(self, 'audio'):
            self.audio.terminate() 