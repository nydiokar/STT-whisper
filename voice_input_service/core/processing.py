from __future__ import annotations
import threading
import queue
import logging
from typing import Callable, Optional
import time
import numpy as np

class TranscriptionWorker:
    """Manages threaded audio processing and transcription."""
    
    def __init__(self, 
                 process_func: Callable[[bytes], Optional[str]],
                 on_result: Callable[[str], None],
                 min_audio_length: int = 32000) -> None:
        """Initialize the worker.
        
        Args:
            process_func: Function that processes audio data and returns text
            on_result: Callback for when text is transcribed
            min_audio_length: Minimum number of samples to process
        """
        self.logger = logging.getLogger("VoiceService.Worker")
        self.process_func = process_func
        self.on_result = on_result
        self.min_audio_length = min_audio_length
        self.running = False
        self.thread: Optional[threading.Thread] = None
        self.audio_queue: queue.Queue[bytes] = queue.Queue()
        
        # Controls for better response
        self.last_audio_time = time.time()
        self.silence_threshold = 400  # RMS threshold for silence detection
        self.silence_duration = 0.5  # Wait for 0.5s of silence before processing
        self.min_process_interval = 1.0  # Minimum time between processing chunks
        self.last_process_time = 0
        
        # Buffer controls
        self.min_chunk_size = 32000  # Minimum chunk size (2 seconds at 16kHz)
        self.max_chunk_size = 480000  # Maximum chunk size (30 seconds at 16kHz)
    
    def has_recent_audio(self) -> bool:
        """Check if we've received audio data recently."""
        return time.time() - self.last_audio_time < self.silence_duration
        
    def start(self) -> None:
        """Start the worker thread."""
        if self.running:
            self.logger.warning("Worker already running")
            return
            
        self.running = True
        self.last_audio_time = time.time()  # Reset timer
        self.thread = threading.Thread(target=self._worker)
        self.thread.daemon = True
        self.thread.start()
        self.logger.info("Transcription worker started")
    
    def stop(self) -> None:
        """Stop the worker thread."""
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
        self.audio_queue.queue.clear()  # Clear any pending audio
        self.logger.info("Transcription worker stopped")
    
    def add_audio(self, data: bytes) -> None:
        """Add audio data to the processing queue."""
        if self.running:
            self.audio_queue.put(data)
            self.last_audio_time = time.time()
    
    def _is_silent(self, audio_data: bytes) -> bool:
        """Check if audio chunk is silent."""
        try:
            audio_array = np.frombuffer(audio_data, dtype=np.int16)
            rms = np.sqrt(np.mean(np.square(audio_array.astype(np.float32))))
            return rms < self.silence_threshold
        except Exception:
            return False
    
    def _worker(self) -> None:
        """Worker thread that processes audio chunks."""
        accumulated_data = bytearray()
        last_active_time = time.time()
        processing_in_progress = False
        error_count = 0
        last_chunk_hash = None  # Track last processed chunk to prevent duplicates
        
        while self.running:
            try:
                current_time = time.time()
                
                # Get audio data with timeout
                try:
                    chunk = self.audio_queue.get(timeout=0.1)
                    accumulated_data.extend(chunk)
                    
                    # Check if this chunk is not silent
                    if not self._is_silent(chunk):
                        last_active_time = current_time
                    
                    # Only log significant changes in buffer size
                    if len(accumulated_data) % (32000 * 2) == 0:  # Log every ~2 seconds
                        data_duration = len(accumulated_data) / 32000
                        self.logger.debug(f"Buffer: {len(accumulated_data)/1024:.1f}KB ({data_duration:.1f}s)")
                    
                except queue.Empty:
                    # Check if we should process based on:
                    # 1. Enough silence has passed
                    # 2. Minimum chunk size reached
                    # 3. Enough time since last processing
                    silence_duration = current_time - last_active_time
                    time_since_last_process = current_time - self.last_process_time
                    
                    should_process = (
                        not processing_in_progress and 
                        accumulated_data and
                        len(accumulated_data) >= self.min_chunk_size and
                        time_since_last_process >= self.min_process_interval and
                        (silence_duration >= self.silence_duration or 
                         len(accumulated_data) >= self.max_chunk_size)
                    )
                    
                    if should_process:
                        processing_in_progress = True
                        data_duration = len(accumulated_data) / 32000
                        self.logger.info(f"Processing {data_duration:.1f}s of audio")
                        
                        try:
                            # Check if this chunk is different from the last one
                            current_chunk = bytes(accumulated_data)
                            current_hash = hash(current_chunk[:1000])  # Hash first 1000 bytes as sample
                            
                            if current_hash != last_chunk_hash:
                                self._process_chunk(current_chunk)
                                last_chunk_hash = current_hash
                            else:
                                self.logger.debug("Skipping duplicate audio chunk")
                                
                            self.last_process_time = current_time
                            error_count = 0  # Reset error count on success
                        except Exception as e:
                            self.logger.error(f"Error processing chunk: {e}")
                            error_count += 1
                            if error_count >= 3:
                                self.logger.critical("Too many consecutive errors, stopping worker")
                                self.running = False
                                break
                        finally:
                            accumulated_data = bytearray()
                            processing_in_progress = False
                    continue
                
                # Process if buffer is too large
                if (not processing_in_progress and 
                    len(accumulated_data) >= self.max_chunk_size):
                    processing_in_progress = True
                    data_duration = len(accumulated_data) / 32000
                    self.logger.info(f"Processing {data_duration:.1f}s of audio (buffer full)")
                    
                    try:
                        current_chunk = bytes(accumulated_data)
                        current_hash = hash(current_chunk[:1000])
                        
                        if current_hash != last_chunk_hash:
                            self._process_chunk(current_chunk)
                            last_chunk_hash = current_hash
                        else:
                            self.logger.debug("Skipping duplicate audio chunk")
                            
                        self.last_process_time = current_time
                        error_count = 0
                    except Exception as e:
                        self.logger.error(f"Error processing chunk: {e}")
                        error_count += 1
                        if error_count >= 3:
                            self.logger.critical("Too many consecutive errors, stopping worker")
                            self.running = False
                            break
                    finally:
                        accumulated_data = bytearray()
                        processing_in_progress = False
                    
            except Exception as e:
                self.logger.error(f"Error in worker thread: {e}", exc_info=True)
                error_count += 1
                processing_in_progress = False
                
                if error_count >= 3:
                    self.logger.critical("Too many consecutive errors, stopping worker")
                    self.running = False
                    break
                
                time.sleep(0.1)
                continue
        
        # Process any remaining audio before stopping
        if accumulated_data and len(accumulated_data) >= self.min_chunk_size:
            try:
                current_chunk = bytes(accumulated_data)
                current_hash = hash(current_chunk[:1000])
                
                if current_hash != last_chunk_hash:
                    data_duration = len(accumulated_data) / 32000
                    self.logger.info(f"Processing final {data_duration:.1f}s of audio")
                    self._process_chunk(current_chunk)
            except Exception as e:
                self.logger.error(f"Error processing final audio: {e}")
    
    def _process_chunk(self, audio_data: bytes) -> None:
        """Process a chunk of audio data."""
        if not audio_data or len(audio_data) < self.min_chunk_size:
            self.logger.debug(f"Skipping small audio chunk ({len(audio_data)} bytes)")
            return
            
        # Ensure chunk size is within limits
        if len(audio_data) > self.max_chunk_size:
            self.logger.warning(f"Truncating large audio chunk from {len(audio_data)} to {self.max_chunk_size} bytes")
            audio_data = audio_data[:self.max_chunk_size]
        
        try:
            text = self.process_func(audio_data)
            if text:
                text = text.strip()
                if text:  # Only send non-empty results
                    self.logger.debug(f"Transcription result: {text}")
                    self.on_result(text)
            else:
                self.logger.debug("No text from transcription")
        except Exception as e:
            self.logger.error(f"Error processing audio chunk: {e}", exc_info=True)
            raise  # Re-raise to let worker handle it 