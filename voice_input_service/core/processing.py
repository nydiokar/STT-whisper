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
                 min_audio_length: int = 16000) -> None:
        """Initialize the worker.
        
        Args:
            process_func: Function that processes audio data and returns text
            on_result: Callback for when text is transcribed
            min_audio_length: Minimum number of audio samples to process
        """
        self.logger = logging.getLogger("VoiceService.Worker")
        self.process_func = process_func
        self.on_result = on_result
        self.min_audio_length = min_audio_length
        self.running = False
        self.thread: threading.Thread | None = None
        self.audio_queue: queue.Queue[bytes] = queue.Queue()
    
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
        
        while self.running:
            try:
                # Get audio data with a short timeout
                try:
                    chunk = self.audio_queue.get(timeout=0.1)
                    accumulated_data += chunk
                except queue.Empty:
                    continue
                
                # Process when we have enough data
                if len(accumulated_data) >= self.min_audio_length * 2:
                    text = self.process_func(accumulated_data)
                    if text:
                        self.on_result(text)
                    
                    # Keep a small overlap for context
                    accumulated_data = accumulated_data[-self.min_audio_length:]
                    
            except Exception as e:
                self.logger.error(f"Error in transcription worker: {e}", exc_info=True)
                continue
        
        # Process any remaining audio
        if accumulated_data and len(accumulated_data) > self.min_audio_length:
            try:
                text = self.process_func(accumulated_data)
                if text:
                    self.on_result(text)
            except Exception as e:
                self.logger.error(f"Error processing final audio: {e}", exc_info=True) 