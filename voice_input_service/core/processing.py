from __future__ import annotations
import threading
import queue
import logging
from typing import Callable, Optional
import time

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
        self.last_audio_time = 0
        self.last_process_time = 0
        self.silence_duration = 0.3  # Time in seconds to wait after silence to process
        self.max_buffer_time = 10.0   # Maximum time to buffer audio before forcing processing
    
    def start(self) -> None:
        """Start the worker thread."""
        if self.running:
            self.logger.warning("Worker already running")
            return
            
        self.running = True
        self.thread = threading.Thread(target=self._worker)
        self.thread.daemon = True  # Don't prevent program exit
        self.thread.start()
        self.logger.info("Transcription worker started")
    
    def stop(self) -> None:
        """Stop the worker thread."""
        if not self.running:
            return
            
        self.logger.debug("Stopping transcription worker")
        self.running = False
        if self.thread and self.thread.is_alive():
            self.thread.join()
        self.thread = None
        self.logger.info("Transcription worker stopped")
    
    def add_audio(self, data: bytes) -> None:
        """Add audio data to the processing queue."""
        if self.running:
            self.audio_queue.put(data)
    
    def _worker(self) -> None:
        """Worker thread that processes audio chunks."""
        accumulated_data = bytearray()
        last_process_time = time.time()
        last_audio_time = time.time()
        min_process_interval = 1.5
        processing_in_progress = False
        error_count = 0  # Track consecutive errors
        
        while self.running:
            try:
                current_time = time.time()
                silence_detected = (current_time - last_audio_time) > self.silence_duration
                buffer_full = len(accumulated_data) >= self.min_audio_length
                time_to_process = current_time - last_process_time >= min_process_interval
                
                # Get audio data with timeout
                try:
                    chunk = self.audio_queue.get(timeout=0.2)
                    accumulated_data.extend(chunk)
                    last_audio_time = current_time
                    error_count = 0  # Reset error count on successful processing
                    
                    if buffer_full and time_to_process:
                        data_duration = len(accumulated_data) / 32000
                        self.logger.info(f"Processing {data_duration:.1f}s of audio")
                        
                except queue.Empty:
                    if not processing_in_progress and accumulated_data and len(accumulated_data) > 8000 and (silence_detected or time_to_process):
                        processing_in_progress = True
                        self._process_chunk(bytes(accumulated_data))
                        accumulated_data = bytearray()
                        last_process_time = current_time
                        processing_in_progress = False
                    continue
                
                if not processing_in_progress and buffer_full and time_to_process:
                    processing_in_progress = True
                    self._process_chunk(bytes(accumulated_data))
                    accumulated_data = bytearray()
                    last_process_time = current_time
                    processing_in_progress = False
                    
            except Exception as e:
                self.logger.error(f"Critical error in worker thread: {e}", exc_info=True)
                error_count += 1
                processing_in_progress = False
                
                # If we get too many consecutive errors, stop the worker
                if error_count >= 5:
                    self.logger.critical("Too many consecutive errors, stopping worker")
                    self.running = False
                    break
                    
                time.sleep(0.5)  # Pause on error to prevent rapid loops
                continue
        
        # Process any remaining audio before stopping
        if accumulated_data and len(accumulated_data) > 8000:
            try:
                self._process_chunk(bytes(accumulated_data))
            except Exception as e:
                self.logger.error(f"Error processing final audio: {e}", exc_info=True)
    
    def _process_chunk(self, audio_data: bytes) -> None:
        """Process a chunk of audio data in a separate thread."""
        if not audio_data or len(audio_data) < 2000:
            self.logger.debug(f"Skipping processing of small audio chunk ({len(audio_data)} bytes)")
            return
        
        try:
            # Process with error catching
            text = self.process_func(audio_data)
            if text:
                self.logger.debug(f"Transcription result: {text}")
                try:
                    self.on_result(text)
                except Exception as e:
                    self.logger.error(f"Error in result callback: {e}", exc_info=True)
                    # Don't re-raise as this is a callback error, not a processing error
            else:
                self.logger.debug("No text from transcription")
        except Exception as e:
            self.logger.error(f"Error processing audio chunk: {e}", exc_info=True)
            # Sleep briefly to prevent rapid error loops
            time.sleep(0.5) 