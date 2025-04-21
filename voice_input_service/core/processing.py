from __future__ import annotations
import threading
import queue
import logging
from typing import Callable, Optional, Literal, Dict, Any, Union
import time
import numpy as np

from voice_input_service.utils.lifecycle import Component
from voice_input_service.utils.silence_detection import SilenceDetector
from voice_input_service.config import Config

# Try to import VAD-related modules
try:
    import webrtcvad
    WEBRTC_AVAILABLE = True
except ImportError:
    WEBRTC_AVAILABLE = False

try:
    import torch
    TORCH_AVAILABLE = True
except ImportError:
    TORCH_AVAILABLE = False

# Global variable to hold the Silero VAD model
SILERO_MODEL = None
SILERO_AVAILABLE = False

class TranscriptionWorker(Component):
    """Manages threaded audio processing, VAD (Voice Activity Detection), and transcription.
    
    This is the primary component responsible for:
    1. Voice activity detection using one of several backends (basic, webrtc, silero)
    2. Audio buffering based on speech detection
    3. Asynchronous processing of detected speech segments
    4. Delivery of transcription results via callbacks
    """
    
    def __init__(
        self, 
        process_func: Callable[[bytes], Optional[str]],
        on_result: Callable[[str], None],
        config: Config,
        initial_continuous_mode: bool = False
    ) -> None:
        """Initialize the worker.
        
        Args:
            process_func: Function that processes audio data and returns text
            on_result: Callback for when text is transcribed
            config: Application configuration
            initial_continuous_mode: Initial continuous mode state
        """
        self.logger = logging.getLogger("VoiceService.Worker")
        self.process_func = process_func
        self.on_result = on_result
        self.config = config
        self.continuous_mode = initial_continuous_mode
        
        # Extract settings from config
        self.min_audio_length = config.audio.min_audio_length
        self.sample_rate = config.audio.sample_rate
        
        # State initialization
        self.running = False
        self.thread: Optional[threading.Thread] = None
        self.audio_queue: queue.Queue[bytes] = queue.Queue()
        
        # VAD configuration from config - no need to duplicate settings here
        # They'll all come from the config object
        
        # Initialize the silence detector with the config
        self.silence_detector = SilenceDetector(config=self.config)
        
        # Controls for better response
        self.last_audio_time = time.time()
        self.silence_duration = config.audio.silence_duration  # Wait for silence before processing
        self.min_process_interval = config.audio.min_process_interval  # Minimum time between processing chunks
        self.last_process_time = 0
        
        # Buffer controls
        self.min_chunk_size = config.transcription.min_chunk_size  # Minimum chunk size to process
        self.max_chunk_size = 480000  # Maximum chunk size (30 seconds at 16kHz)
        
        # Thread synchronization
        self.buffer_lock = threading.RLock()  # Reentrant lock for buffer access
    
    def _validate_vad_mode(self, mode: str) -> str:
        """Validate and potentially adjust VAD mode based on available modules."""
        if mode == "webrtc" and not WEBRTC_AVAILABLE:
            self.logger.warning("WebRTC VAD not available. Install with: pip install webrtcvad. Falling back to basic VAD.")
            return "basic"
        elif mode == "silero" and not TORCH_AVAILABLE:
            self.logger.warning("Silero VAD requires PyTorch. Install with: pip install torch. Falling back to basic VAD.")
            return "basic"
        return mode
        
    def has_recent_audio(self) -> bool:
        """Check if we've received audio data recently."""
        return time.time() - self.last_audio_time < self.silence_duration
        
    def start(self) -> bool:
        """Start the worker thread.
        
        Returns:
            True if started successfully, False otherwise
        """
        with self.buffer_lock:
            if self.running:
                self.logger.warning("Worker already running")
                return False
                
            self.running = True
            self.last_audio_time = time.time()  # Reset timer
            
        # Clear any old data in the queue
        while not self.audio_queue.empty():
            try:
                self.audio_queue.get_nowait()
                self.audio_queue.task_done()
            except queue.Empty:
                break
            
        self.thread = threading.Thread(target=self._worker)
        self.thread.daemon = True
        self.thread.start()
        self.logger.info(f"Transcription worker started (using Silero VAD)")
        return True
    
    def stop(self) -> None:
        """Stop the worker thread."""
        with self.buffer_lock:
            if not self.running:
                return
                
            self.logger.debug("Stopping transcription worker")
            self.running = False
        
        # Wait for thread to finish
        if self.thread and self.thread.is_alive():
            current_thread = threading.current_thread()
            if current_thread is not self.thread:
                self.thread.join(timeout=2.0)  # Wait up to 2 seconds
                
        self.thread = None
        
        # Clear any pending audio
        while not self.audio_queue.empty():
            try:
                self.audio_queue.get_nowait()
                self.audio_queue.task_done()
            except queue.Empty:
                break
                
        self.logger.info("Transcription worker stopped")
    
    def close(self) -> None:
        """Clean up resources."""
        self.stop()
    
    def add_audio(self, data: bytes) -> None:
        """Add audio data to the processing queue."""
        # Thread-safe operation - queue is already thread-safe
        if self.running:
            self.audio_queue.put(data)
            # Update last audio time with atomic operation
            self.last_audio_time = time.time()
    
    def _is_silent(self, audio_data: bytes) -> bool:
        """Determine if audio chunk is silent using the configured VAD mode.
        
        Args:
            audio_data: Raw audio bytes to process
            
        Returns:
            True if the audio is silent, False otherwise
        """
        # SilenceDetector is thread-safe, no lock needed
        return self.silence_detector.is_silent(audio_data)
    
    def _worker(self) -> None:
        """Worker thread that processes audio chunks."""
        self.logger.info(f"Worker thread started (Continuous Mode: {self.continuous_mode})")
        
        buffer = bytearray()
        last_speech_time = time.time()
        
        while True:
            # Check if we should exit - use local variable to avoid race conditions
            with self.buffer_lock:
                if not self.running:
                    break
            
            try:
                # Get audio from queue with timeout to prevent blocking indefinitely
                try:
                    audio_chunk = self.audio_queue.get(timeout=0.1)
                    
                    # Skip processing if chunk is too small
                    if len(audio_chunk) < 1600:  # ~50ms at 16kHz
                        self.audio_queue.task_done()
                        continue
                        
                    # Check VAD
                    is_silence = self._is_silent(audio_chunk)
                    
                    with self.buffer_lock:
                        time_since_speech = time.time() - last_speech_time
                        current_buffer_len = len(buffer)
                        # Access the worker's current mode state safely
                        is_continuous = self.continuous_mode 
                        
                        self.logger.debug(f"Worker Loop: Cont={is_continuous}, VAD={not is_silence}, BufLen={current_buffer_len}, TimeSinceSpeech={time_since_speech:.2f}s")

                        # --- Processing Logic --- 
                        process_now = False
                        reason = ""

                        if not is_silence:
                            # Speech detected: Add to buffer, update last speech time
                            buffer.extend(audio_chunk)
                            last_speech_time = time.time()
                        else:
                            # Silence detected: Extend buffer only if recent speech or non-continuous
                            # In non-continuous, we buffer silence too, up to max_chunk_size
                            if time_since_speech < self.silence_duration or not is_continuous:
                                buffer.extend(audio_chunk)
                            
                            # Check processing conditions ONLY if continuous mode
                            if is_continuous and current_buffer_len >= self.min_audio_length and \
                               time_since_speech >= self.silence_duration:
                                process_now = True
                                reason = "Silence & MinLength (Continuous)"
                        
                        # Check max buffer size condition (applies to both modes)
                        # Use updated buffer length after potential extend
                        current_buffer_len = len(buffer) # Re-check length after extend
                        if current_buffer_len > self.max_chunk_size:
                            process_now = True
                            reason = "MaxSize"
                        
                        # --- Perform Processing if needed --- 
                        if process_now:
                            self.logger.debug(f"Processing ({reason}): BufLen={current_buffer_len}, TimeSinceSpeech={time_since_speech:.2f}s")
                            audio_copy = bytes(buffer)
                            buffer.clear()
                            self._process_chunk(audio_copy)
                            # Reset last_speech_time after processing to avoid immediate re-processing if silence follows
                            last_speech_time = time.time() 

                    self.audio_queue.task_done()
                        
                except queue.Empty:
                    # Queue is empty
                    with self.buffer_lock:
                        time_since_speech = time.time() - last_speech_time
                        current_buffer_len = len(buffer)
                        is_continuous = self.continuous_mode # Access mode safely

                        self.logger.debug(f"Worker Loop: QueueEmpty, Cont={is_continuous}, BufLen={current_buffer_len}, TimeSinceSpeech={time_since_speech:.2f}s")
                        
                        # Process final buffer ONLY if continuous mode and silence conditions met
                        if is_continuous and current_buffer_len >= self.min_audio_length and \
                           time_since_speech >= self.silence_duration:
                            self.logger.debug(f"Processing (QueueEmpty & Silence & MinLength - Continuous): BufLen={current_buffer_len}, TimeSinceSpeech={time_since_speech:.2f}s")
                            audio_copy = bytes(buffer)
                            buffer.clear()
                            self._process_chunk(audio_copy)
                            last_speech_time = time.time() # Reset timer
            except Exception as e:
                self.logger.error(f"Error in worker thread: {e}")
                # Clear buffer on error
                with self.buffer_lock:
                    buffer.clear()
                
        self.logger.info("Worker thread stopped")
    
    def _process_chunk(self, audio_data: bytes) -> None:
        """Process a chunk of audio data."""
        current_time = time.time()
        audio_len = len(audio_data)
        
        # Log the size received
        self.logger.debug(f"_process_chunk received audio chunk. Size: {audio_len} bytes.")

        # Skip if too recent or too short
        if (current_time - self.last_process_time < self.min_process_interval or 
                audio_len < self.min_chunk_size):
            self.logger.debug(f"Skipping chunk processing: Too recent or too short ({audio_len} bytes < {self.min_chunk_size} min bytes or interval issue)")
            return
            
        self.last_process_time = current_time
        
        self.logger.info(f"Processing audio chunk ({audio_len/1024:.1f} KB)") # Changed level to INFO for visibility
        
        # Process the audio data 
        result = self.process_func(audio_data)
        
        # Send result if available
        if result:
            self.logger.debug(f"Got transcription result: {result}")
            self.on_result(result)
    
    def update_vad_settings(self) -> None:
        """Update Voice Activity Detection settings from config."""
        if not hasattr(self, 'config') or not self.config:
            self.logger.warning("No config available to update VAD settings")
            return
            
        # Get VAD settings from config
        vad_mode = self.config.audio.vad_mode
        vad_aggressiveness = self.config.audio.vad_aggressiveness
        vad_threshold = self.config.audio.vad_threshold
        
        # Set silence duration based on whether we're in continuous mode
        # In continuous mode, use a longer silence duration to avoid breaking speech
        self.silence_duration = 1.5  # Default duration
        
        # If config has a continuous_mode setting, use a longer silence duration
        if hasattr(self.config.transcription, 'continuous_mode') and self.config.transcription.continuous_mode:
            self.silence_duration = 2.5  # Longer silence for continuous mode
            self.logger.debug(f"Continuous mode detected - using longer silence duration: {self.silence_duration}s")
            
        # Update the detector with new settings
        if hasattr(self, 'silence_detector'):
            self.silence_detector.update_settings(
                mode=vad_mode, 
                aggressiveness=vad_aggressiveness,
                threshold=vad_threshold
            )
            
        # Log the update
        self.logger.debug(f"Updated VAD settings: mode={vad_mode}, silence_duration={self.silence_duration}s")
    
    def set_continuous_mode(self, enabled: bool) -> None:
        """Update the worker's continuous mode state."""
        with self.buffer_lock: # Use lock for thread safety
            if self.continuous_mode != enabled:
                self.continuous_mode = enabled
                self.logger.info(f"Worker continuous mode set to: {self.continuous_mode}")
                # Optional: Reset buffer or timers if needed when mode changes, but maybe not necessary here.
    
    def __del__(self) -> None:
        """Ensure resources are cleaned up."""
        self.close() 