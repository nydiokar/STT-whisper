from __future__ import annotations
import threading
import queue
import logging
from typing import Callable
import time

class TranscriptionWorker:
    """Manages threaded audio processing and transcription."""
    
    def __init__(self, 
                 process_func: Callable[[bytes], str | None],
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
        self.thread: threading.Thread | None = None
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
        accumulated_data = b""
        last_process_time = time.time()
        last_audio_time = time.time()
        min_process_interval = 1.5  # Slower processing to avoid excessive logs
        processing_in_progress = False
        
        while self.running:
            try:
                current_time = time.time()
                silence_detected = (current_time - last_audio_time) > self.silence_duration
                buffer_full = len(accumulated_data) >= self.min_audio_length
                time_to_process = current_time - last_process_time >= min_process_interval
                
                # Get audio data with timeout
                try:
                    chunk = self.audio_queue.get(timeout=0.2)
                    accumulated_data += chunk
                    last_audio_time = current_time
                    
                    # Log only when buffer is full
                    if buffer_full and time_to_process:
                        data_duration = len(accumulated_data) / 32000
                        self.logger.info(f"Processing {data_duration:.1f}s of audio")
                        
                except queue.Empty:
                    # No new data
                    if not processing_in_progress and accumulated_data and len(accumulated_data) > 8000 and (silence_detected or time_to_process):
                        processing_in_progress = True
                        self._process_chunk(bytes(accumulated_data))
                        accumulated_data = b""  # Reset accumulated data
                        last_process_time = current_time
                        processing_in_progress = False
                    continue
                
                # Process when buffer is full
                if not processing_in_progress and buffer_full and time_to_process:
                    processing_in_progress = True
                    self._process_chunk(bytes(accumulated_data))
                    accumulated_data = b""  # Reset accumulated data
                    last_process_time = current_time
                    processing_in_progress = False
                    
            except Exception as e:
                self.logger.error(f"Error in transcription worker: {e}")
                processing_in_progress = False
                time.sleep(0.2)  # Pause on error
                continue
        
        # Process any remaining audio before stopping
        if accumulated_data and len(accumulated_data) > 8000:
            try:
                self._process_chunk(bytes(accumulated_data))
            except Exception as e:
                self.logger.error(f"Error processing final audio: {e}")
    
    def _process_chunk(self, audio_data: bytes) -> None:
        """Process a chunk of audio data in a separate thread."""
        try:
            # Add a check to prevent processing of very small audio chunks
            if not audio_data or len(audio_data) < 2000:
                self.logger.debug(f"Skipping processing of small audio chunk ({len(audio_data)} bytes)")
                return
            
            # Process with error catching
            try:    
                text = self.process_func(audio_data)
                if text:
                    self.logger.debug(f"Transcription result: {text}")
                    self.on_result(text)
                else:
                    self.logger.debug("No text from transcription")
            except Exception as e:
                self.logger.error(f"Error in transcription function: {e}", exc_info=True)
                # Continue processing - don't let one error stop everything
        except Exception as e:
            self.logger.error(f"Error processing audio chunk: {e}", exc_info=True) 