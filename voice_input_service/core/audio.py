from __future__ import annotations
import queue
import pyaudio
import logging
import wave
import os
import tempfile
import numpy as np
import threading
import time
from typing import Any, Optional, Callable, Dict, Tuple

class AudioRecorder:
    """Handles audio recording and processing."""
    
    def __init__(
        self,
        sample_rate: int = 44100,
        chunk_size: int = 4096,
        channels: int = 1,
        format_type: int = pyaudio.paInt16,
        device_index: Optional[int] = None,
        on_data_callback: Optional[Callable[[bytes], None]] = None
    ) -> None:
        """Initialize the audio recorder.
        
        Args:
            sample_rate: Audio sample rate in Hz
            chunk_size: Number of frames per buffer
            channels: Number of audio channels
            format_type: PyAudio format type
            device_index: Index of the audio input device to use
            on_data_callback: Callback function for processing audio data
        """
        self.logger = logging.getLogger("voice_input_service.core.audio")
        
        # Audio parameters
        self.sample_rate = sample_rate
        self.chunk_size = chunk_size
        self.channels = channels
        self.format_type = format_type
        self.device_index = device_index
        self.on_data_callback = on_data_callback
        
        # Initialize PyAudio
        self.py_audio = pyaudio.PyAudio()
        
        # Recording state
        self.is_recording = False
        self.stream: Optional[pyaudio.Stream] = None
        self.audio_data = bytearray()
        self.lock = threading.Lock()
        
    def __del__(self) -> None:
        """Clean up resources when the object is destroyed."""
        self.stop()
        self.py_audio.terminate()
        
    def get_input_devices(self) -> Dict[int, str]:
        """Get all available input devices.
        
        Returns:
            Dictionary mapping device indices to device names
        """
        devices = {}
        for i in range(self.py_audio.get_device_count()):
            device_info = self.py_audio.get_device_info_by_index(i)
            if device_info['maxInputChannels'] > 0:
                devices[i] = device_info['name']
        return devices
        
    def start(self) -> bool:
        """Start audio recording.
        
        Returns:
            True if recording started successfully, False otherwise
        """
        if self.is_recording:
            self.logger.warning("Already recording")
            return False
            
        try:
            self.audio_data = bytearray()
            
            # Start the audio stream
            self.stream = self.py_audio.open(
                rate=self.sample_rate,
                channels=self.channels,
                format=self.format_type,
                input=True,
                input_device_index=self.device_index,
                frames_per_buffer=self.chunk_size,
                stream_callback=self._audio_callback
            )
            
            self.is_recording = True
            self.logger.info(f"Recording started (sample_rate={self.sample_rate}, chunk_size={self.chunk_size})")
            return True
            
        except Exception as e:
            self.logger.error(f"Failed to start recording: {e}")
            self.is_recording = False
            return False
            
    def stop(self) -> bytes:
        """Stop audio recording.
        
        Returns:
            Recorded audio data as bytes
        """
        if not self.is_recording:
            return bytes(self.audio_data)
            
        if self.stream:
            self.stream.stop_stream()
            self.stream.close()
            self.stream = None
            
        self.is_recording = False
        self.logger.info("Recording stopped")
        
        with self.lock:
            result = bytes(self.audio_data)
        
        return result
        
    def _audio_callback(
        self, 
        in_data: bytes, 
        frame_count: int, 
        time_info: Dict[str, float], 
        status: int
    ) -> tuple[bytes, int]:
        """Process audio data from the stream.
        
        Args:
            in_data: Audio data from PyAudio
            frame_count: Number of frames
            time_info: Time information from PyAudio
            status: Status flag
            
        Returns:
            Tuple of (in_data, flag) where flag indicates if more data is expected
        """
        if status:
            self.logger.warning(f"Audio callback status: {status}")
            
        # Append data to our buffer
        with self.lock:
            self.audio_data.extend(in_data)
            
        # Call the data callback if provided
        if self.on_data_callback:
            try:
                self.on_data_callback(in_data)
            except Exception as e:
                self.logger.error(f"Error in audio data callback: {e}")
        
        return in_data, pyaudio.paContinue
        
    def get_audio_data(self) -> bytes:
        """Get a copy of the current audio data.
        
        Returns:
            Copy of the recorded audio data
        """
        with self.lock:
            return bytes(self.audio_data)
            
    def is_silent(self, audio_data: bytes, threshold: int = 400) -> bool:
        """Check if an audio chunk is silent.
        
        Args:
            audio_data: Audio data to check
            threshold: RMS threshold for silence detection
            
        Returns:
            True if the audio is silent, False otherwise
        """
        # Convert audio bytes to numpy array
        audio_array = np.frombuffer(audio_data, dtype=np.int16)
        
        # Calculate root mean square (RMS) to determine volume
        rms = np.sqrt(np.mean(np.square(audio_array.astype(np.float32))))
        
        return rms < threshold
    
    def save_to_wav(self, filepath: str) -> bool:
        """Save the current buffer to a WAV file.
        
        Args:
            filepath: Path to save the WAV file
            
        Returns:
            True if the file was saved successfully
        """
        try:
            with wave.open(filepath, 'wb') as wf:
                wf.setnchannels(self.channels)
                wf.setsampwidth(pyaudio.get_sample_size(self.format_type))
                wf.setframerate(self.sample_rate)
                wf.writeframes(self.audio_data)
            self.logger.info(f"Saved recording to {filepath}")
            return True
        except Exception as e:
            self.logger.error(f"Failed to save recording: {e}")
            return False
    
    def save_to_temp_wav(self) -> Optional[str]:
        """Save the current buffer to a temporary WAV file.
        
        Returns:
            Path to the temporary file or None if saving failed
        """
        try:
            fd, filepath = tempfile.mkstemp(suffix='.wav')
            os.close(fd)
            
            if self.save_to_wav(filepath):
                return filepath
            return None
        except Exception as e:
            self.logger.error(f"Failed to create temp file: {e}")
            return None