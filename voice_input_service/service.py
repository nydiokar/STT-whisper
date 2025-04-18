from __future__ import annotations
import threading
import time
from typing import Optional, Callable
import queue
import pyperclip
import logging

# Import our well-tested core components
from voice_input_service.core.audio import AudioProcessor, AudioConfig
from voice_input_service.core.transcription import TranscriptionService, TranscriptionConfig
from voice_input_service.core.processing import TranscriptionWorker
from voice_input_service.utils.file_ops import TranscriptManager
from voice_input_service.utils.logging import setup_logging
from voice_input_service.config import Config
from voice_input_service.ui.window import TranscriptionUI
from voice_input_service.ui.events import KeyboardEventManager, EventHandler

class VoiceInputService(EventHandler):
    """Main service for voice transcription."""
    
    def __init__(self) -> None:
        # Setup logging first
        self.logger = setup_logging()
        self.logger.info("Starting Voice Input Service")
        
        # Configuration
        self.config = Config()
        
        # Initialize components
        self.audio_config = AudioConfig(
            sample_rate=self.config.sample_rate,
            chunk_size=self.config.chunk_size,
            channels=self.config.channels,
            format=self.config.format
        )
        
        self.audio_processor = AudioProcessor(self.audio_config)
        self.transcription_config = TranscriptionConfig(
            model_name=self.config.model_size,
            sample_rate=self.config.sample_rate,
            chunk_size=self.config.chunk_size,
            channels=self.config.channels,
            format=self.config.format,
            processing_chunk_size=self.config.min_audio_length
        )
        
        self.transcription_service = TranscriptionService(self.transcription_config)
        self.transcript_manager = TranscriptManager()
        self.ui = TranscriptionUI()
        
        # State variables
        self.recording = False
        self.recording_start_time = 0.0
        self.accumulated_text = ""
        self.continuous_mode = False
        
        # Setup worker for background transcription
        self.worker = TranscriptionWorker(
            process_func=self._process_audio,
            on_result=self._on_transcription_result,
            min_audio_length=self.config.min_audio_length
        )
        
        # Set up UI events
        self._setup_ui_events()
        
        # Set up keyboard event manager
        self.event_manager = KeyboardEventManager(self)
        self.event_manager.setup_hotkeys()
        
        self.logger.info("Voice Input Service initialized")
    
    def _setup_ui_events(self) -> None:
        """Setup UI event callbacks."""
        # Trace variable changes for continuous mode
        self.ui.continuous_var.trace_add("write", lambda *args: self._toggle_continuous_mode())
        
        # Trace variable changes for language
        self.ui.language_var.trace_add("write", lambda *args: self._change_language(self.ui.language_var.get()))
        
        self.logger.debug("UI events configured")
    
    def _toggle_continuous_mode(self) -> None:
        """Toggle continuous mode."""
        self.continuous_mode = self.ui.continuous_var.get()
        self.event_manager.continuous_mode = self.continuous_mode
        self.logger.info(f"Continuous mode {'enabled' if self.continuous_mode else 'disabled'}")
    
    def _change_language(self, language: str) -> None:
        """Change the transcription language."""
        self.transcription_service.config.language = language
        self.logger.info(f"Language changed to {language}")
    
    def _process_audio(self, audio_data: bytes) -> str:
        """Process audio data through transcription service."""
        language = self.ui.language_var.get()
        return self.transcription_service.process_audio(
            audio_data, 
            context=self.accumulated_text[-100:] if self.config.keep_context else "",
            language=language
        ) or ""
    
    def _on_transcription_result(self, text: str) -> None:
        """Handle transcription result."""
        if text:
            self.accumulated_text += " " + text
            self.accumulated_text = self.accumulated_text.strip()
            
            # Update UI
            self.ui.update_text(self.accumulated_text)
            word_count = len(self.accumulated_text.split())
            self.ui.update_word_count(word_count)
            
            # Check for pause (handled by transcription service)
            if self.transcription_service.pause_detected and self.continuous_mode:
                self.logger.info("Pause detected, stopping continuous recording")
                self.stop_recording()
                
                # Copy text and save if in continuous mode
                if self.continuous_mode:
                    self._copy_to_clipboard()
                    self._save_transcript()
                    self._clear_transcript()
                    
                    # Restart recording for continuous mode
                    self.start_recording()
    
    def _audio_callback(self, audio_data: bytes) -> None:
        """Process incoming audio data."""
        if self.recording:
            self.worker.process(audio_data)
    
    def _update_status(self) -> None:
        """Update the UI status."""
        if self.recording:
            elapsed = time.time() - self.recording_start_time
            self.ui.update_status(True, elapsed, self.continuous_mode)
        else:
            self.ui.update_status(False)
    
    def _copy_to_clipboard(self) -> None:
        """Copy the current transcript to clipboard."""
        if self.accumulated_text:
            pyperclip.copy(self.accumulated_text)
            self.logger.info("Transcript copied to clipboard")
    
    def start_recording(self) -> bool:
        """Start audio recording."""
        if self.recording:
            self.logger.warning("Already recording")
            return False
        
        self.logger.info("Starting recording")
        self.recording = True
        self.recording_start_time = time.time()
        
        # Configure audio processor
        self.audio_processor.recording = True
        
        # Start audio stream
        if not self.audio_processor.start_stream():
            self.logger.error("Failed to start audio stream")
            self.recording = False
            return False
        
        # Start transcription worker
        self.worker.start()
        
        # Reset pause detection
        self.transcription_service.pause_detected = False
        
        return True
    
    def stop_recording(self) -> str:
        """Stop audio recording and return the transcription."""
        if not self.recording:
            self.logger.warning("Not recording")
            return ""
        
        self.logger.info("Stopping recording")
        
        # Stop audio capturing
        self.audio_processor.recording = False
        self.audio_processor.stop_stream()
        
        # Stop transcription worker
        self.worker.stop()
        
        # Update state
        self.recording = False
        
        return self.accumulated_text
    
    def save_transcript(self) -> None:
        """Save the current transcript to a file."""
        self._save_transcript()
    
    def _save_transcript(self) -> None:
        """Save the current transcript to a file."""
        if not self.accumulated_text:
            self.logger.warning("No transcript to save")
            return
            
        file_path = self.transcript_manager.save_transcript(self.accumulated_text)
        if file_path:
            self.logger.info(f"Transcript saved to: {file_path}")
        else:
            self.logger.error("Failed to save transcript")
    
    def clear_transcript(self) -> None:
        """Clear the current transcript."""
        self._clear_transcript()
    
    def _clear_transcript(self) -> None:
        """Clear the current transcript."""
        self.accumulated_text = ""
        self.ui.update_text("")
        self.ui.update_word_count(0)
        self.logger.info("Transcript cleared")
    
    def run(self) -> None:
        """Run the service."""
        self.logger.info("Starting main service loop")
        
        # Setup UI update timer
        def update_ui() -> None:
            self._update_status()
            self.ui.window.after(100, update_ui)
        
        # Start UI updates
        self.ui.window.after(100, update_ui)
        
        # Start UI main loop
        self.ui.run()
    
    def __del__(self) -> None:
        """Clean up service resources."""
        self.logger.info("Shutting down Voice Input Service")
        if hasattr(self, 'recording') and self.recording:
            self.stop_recording()
        
        if hasattr(self, 'worker'):
            self.worker.stop()
