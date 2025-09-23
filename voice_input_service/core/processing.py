from __future__ import annotations
import threading
import queue
import logging
from typing import Callable, Optional, Literal, Dict, Any, Union, Tuple
import time
import numpy as np

from voice_input_service.utils.lifecycle import Component
from voice_input_service.utils.silence_detection import SilenceDetector
from voice_input_service.config import Config
from voice_input_service.core.transcription import TranscriptionEngine, TranscriptionResult

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

# Define a sentinel object for the stop signal
STOP_SIGNAL = object()

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
        transcriber: TranscriptionEngine,
        on_result: Callable[[TranscriptionResult], None],
        config: Config,
    ) -> None:
        """Initialize the worker.
        
        Args:
            transcriber: The TranscriptionEngine instance.
            on_result: Callback for when an intermediate text chunk is transcribed.
            config: Application configuration.
        """
        self.logger = logging.getLogger("VoiceService.Worker")
        self.transcriber = transcriber
        self.on_result = on_result
        self.config = config
        
        # Extract relevant settings from config
        self.min_audio_length_bytes = int(config.audio.min_audio_length_sec * config.audio.sample_rate * 2) # Convert seconds to bytes
        self.sample_rate = config.audio.sample_rate
        self.silence_duration_sec = config.audio.silence_duration_sec
        self.max_chunk_duration_sec = config.audio.max_chunk_duration_sec
        self.max_chunk_bytes = int(self.max_chunk_duration_sec * self.sample_rate * 2)
        self.min_chunk_size_bytes = config.transcription.min_chunk_size_bytes # Min bytes for transcription call
        
        # State initialization
        self.running = False
        self.thread: Optional[threading.Thread] = None
        self.audio_queue: queue.Queue[bytes | object] = queue.Queue()
        self.last_audio_time = time.time()
        
        # VAD setup
        self.silence_detector = SilenceDetector(config=self.config)
        if not self.silence_detector._initialized:
            self.logger.error("Silence detector failed to initialize in Worker. VAD will not function.")
            # Decide if this is fatal? For now, worker can run but VAD won't work.
        
        # Thread synchronization
        self.buffer_lock = threading.RLock() # Lock for buffer access
    
    def has_recent_audio(self) -> bool:
        """Check if we've received audio data recently."""
        # Check if current time is within silence_duration_sec of the last audio time
        with self.buffer_lock: # Ensure thread-safe access to last_audio_time
            return time.time() - self.last_audio_time < self.silence_duration_sec
    
    def start(self) -> bool:
        """Start the worker thread."""
        with self.buffer_lock:
            if self.running:
                self.logger.warning("Worker already running")
                return False
            self.running = True
            self.last_audio_time = time.time() # Reset timer
            
        # Clear any old data in the queue
        while not self.audio_queue.empty():
            try:
                self.audio_queue.get_nowait()
                self.audio_queue.task_done()
            except queue.Empty:
                break
            
        self.thread = threading.Thread(target=self._worker_loop)
        self.thread.daemon = True
        self.thread.start()
        self.logger.info(f"Transcription worker started (Continuous Mode)")
        return True
    
    def stop(self) -> None:
        """Stop the worker thread asynchronously by putting STOP_SIGNAL in the queue."""
        if not self.running:
            return
        self.logger.debug("Putting stop signal into worker queue.")
        self.audio_queue.put(STOP_SIGNAL)
        # Don't set self.running = False here, let the worker loop handle it
        # Don't join the thread here, let it finish processing the queue up to the signal

    def add_audio(self, data: bytes) -> None:
        """Add audio data to the processing queue."""
        if self.running:
            self.audio_queue.put(data)
            with self.buffer_lock: # Update last audio time safely
                self.last_audio_time = time.time()
    
    def _is_silent(self, audio_data: bytes) -> bool:
        """Determine if audio chunk is silent using the detector."""
        if not self.silence_detector._initialized:
            # If VAD failed to init, assume everything is speech to avoid dropping audio
            return False
        return self.silence_detector.is_silent(audio_data)
    
    def _worker_loop(self) -> None:
        """Main worker thread loop that processes audio chunks."""
        self.logger.info("Worker thread entering loop.")
        
        active_speech_buffer = bytearray()
        last_speech_time = time.time()
        total_processed_bytes = 0 # Track bytes processed within the current potential chunk

        while True: # Loop until STOP_SIGNAL is received
            try:
                # Get audio data or signal from queue, wait if necessary
                item = self.audio_queue.get(timeout=0.1) # Timeout allows periodic checks

                if item is STOP_SIGNAL:
                    self.logger.debug("Stop signal received in worker queue.")
                    # Process any remaining data in the buffer before exiting
                    if len(active_speech_buffer) >= self.min_chunk_size_bytes:
                        self.logger.info(f"Processing final remaining buffer chunk ({len(active_speech_buffer)} bytes) before stopping worker.")
                        self._process_audio_buffer(bytes(active_speech_buffer))
                    break # Exit the while loop
                
                # --- Regular Audio Chunk Handling --- 
                audio_chunk: bytes = item
                chunk_len = len(audio_chunk)
                if chunk_len == 0: continue
                
                # Check VAD on the incoming chunk
                is_chunk_silent = self._is_silent(audio_chunk)
                
                with self.buffer_lock: # Protect buffer and related state
                    time_since_last_speech = time.time() - last_speech_time
                    buffer_len = len(active_speech_buffer)
                    
                    # --- Logic for Buffering and Processing --- 
                    if not is_chunk_silent:
                        # Speech detected
                        active_speech_buffer.extend(audio_chunk)
                        last_speech_time = time.time() # Update last speech time
                        total_processed_bytes += chunk_len
                        self.logger.debug(f"VAD=Speech. Added {chunk_len} bytes. Buffer: {len(active_speech_buffer)} bytes.")
                        
                        # Process if buffer exceeds max duration/size
                        if len(active_speech_buffer) >= self.max_chunk_bytes:
                            self.logger.info(f"Processing chunk due to max size reached ({len(active_speech_buffer)} bytes).")
                            self._process_audio_buffer(bytes(active_speech_buffer))
                            active_speech_buffer.clear()
                            total_processed_bytes = 0
                    else:
                        # Silence detected
                        self.logger.debug(f"VAD=Silence. Time since speech: {time_since_last_speech:.2f}s. Buffer: {buffer_len} bytes.")
                        # If we have a buffer with speech and enough silence has passed, process it
                        if buffer_len >= self.min_audio_length_bytes and time_since_last_speech >= self.silence_duration_sec:
                            self.logger.info(f"Processing chunk due to silence detected after speech ({buffer_len} bytes).")
                            self._process_audio_buffer(bytes(active_speech_buffer))
                            active_speech_buffer.clear()
                            total_processed_bytes = 0
                        elif buffer_len > 0:
                            # Still buffer some silence if speech just ended, helps context
                            # Limit how much silence we buffer? Maybe add a config for this.
                            active_speech_buffer.extend(audio_chunk)
                            total_processed_bytes += chunk_len
                            # Process if silence makes buffer exceed max size
                            if len(active_speech_buffer) >= self.max_chunk_bytes:
                                self.logger.info(f"Processing chunk due to max size reached during silence ({len(active_speech_buffer)} bytes).")
                                self._process_audio_buffer(bytes(active_speech_buffer))
                                active_speech_buffer.clear()
                                total_processed_bytes = 0
                    # --- End Buffering Logic --- 

                self.audio_queue.task_done()
                
            except queue.Empty:
                # Timeout occurred, check if we should process buffer due to inactivity
                with self.buffer_lock:
                    if self.running and len(active_speech_buffer) >= self.min_audio_length_bytes and not self.has_recent_audio():
                        self.logger.info(f"Processing chunk due to inactivity timeout ({len(active_speech_buffer)} bytes).")
                        self._process_audio_buffer(bytes(active_speech_buffer))
                        active_speech_buffer.clear()
                        total_processed_bytes = 0
                continue # Continue loop after timeout check
                
            except Exception as e:
                self.logger.error(f"Error in worker thread loop: {e}", exc_info=True)
                # Decide whether to break or continue on error
                # For now, log and continue, but clear buffer to prevent reprocessing bad data
                with self.buffer_lock:
                    active_speech_buffer.clear()
                    total_processed_bytes = 0

        # --- Worker Loop Finished --- 
        self.logger.info("Worker thread loop finished.")
        self.running = False
        self.thread = None
    
    def _process_audio_buffer(self, audio_data: bytes) -> None:
        """Process a complete buffer of audio data (likely containing speech)."""
        buffer_len = len(audio_data)
        if buffer_len < self.min_chunk_size_bytes:
            self.logger.debug(f"Skipping transcription for small buffer chunk ({buffer_len} bytes < {self.min_chunk_size_bytes} min bytes)")
            return
        
        self.logger.info(f"Sending buffer chunk ({buffer_len / 1024:.1f} KB) to transcription engine.")
        
        try:
            # Transcribe the audio - DO NOT provide a save path for intermediate chunks
            result: TranscriptionResult = self.transcriber.transcribe(
                audio=audio_data, 
                target_wav_path=None # Explicitly None
            )
            
            # Check if the result actually contains meaningful text
            text = result.get("text", "").strip()
            
            if text:
                self.logger.debug(f"Worker received transcription result: '{text[:50]}...'')")
                # Send the transcribed text back via the callback
                try:
                    self.on_result(result)
                except Exception as cb_err:
                    self.logger.error(f"Error in worker on_result callback: {cb_err}")
            else:
                self.logger.debug("Worker received empty transcription result.")
                
        except Exception as e:
            # Log errors from transcription engine
            self.logger.error(f"Error during transcription call in worker: {e}", exc_info=True)
            # Optionally, notify main thread of error?
    
    def update_settings(self) -> None:
        """Update worker settings from config (e.g., VAD threshold)."""
        self.logger.debug("Updating worker settings from config.")
        if self.silence_detector._initialized:
            self.silence_detector.update_settings() # Reads from config passed to detector
        
        # Update durations/sizes read from config
        with self.buffer_lock: # Ensure thread safety if these are read in loop
            self.min_audio_length_bytes = int(self.config.audio.min_audio_length_sec * self.sample_rate * 2)
            self.silence_duration_sec = self.config.audio.silence_duration_sec
            self.max_chunk_duration_sec = self.config.audio.max_chunk_duration_sec 
            self.max_chunk_bytes = int(self.max_chunk_duration_sec * self.sample_rate * 2)
            self.min_chunk_size_bytes = self.config.transcription.min_chunk_size_bytes
            self.logger.info(f"Worker settings updated: SilenceDur={self.silence_duration_sec}s, MaxChunk={self.max_chunk_duration_sec}s")
    
    def close(self) -> None:
        """Clean up resources."""
        self.stop() # Signal the worker to stop
        # Wait for thread to finish? Optional, depends on desired shutdown behavior.
        # if self.thread and self.thread.is_alive():
        #    self.logger.debug("Waiting for worker thread to join...")
        #    self.thread.join(timeout=2.0) # Wait max 2 seconds
        #    if self.thread.is_alive():
        #         self.logger.warning("Worker thread did not join cleanly.")
        self.logger.debug("TranscriptionWorker closed.")
    
    def __del__(self) -> None:
        """Ensure resources are cleaned up."""
        self.close() 