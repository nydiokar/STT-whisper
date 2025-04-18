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
            # Avoid duplicate "Hi" transcriptions
            if text.strip().lower() == "hi" and "hi" in self.accumulated_text.lower():
                self.logger.debug("Filtered duplicate 'hi' transcription")
                return
            
            # Add appropriate spacing between sentences
            if self.accumulated_text and not self.accumulated_text.endswith(('.', '!', '?', ':', ';')):
                separator = " "
            else:
                separator = " "
            
            self.accumulated_text += separator + text
            self.accumulated_text = self.accumulated_text.strip()
            
            # Clean up the text - remove multiple spaces, fix capitalization
            self.accumulated_text = ' '.join(self.accumulated_text.split())
            
            # Update UI with visual indication of new text
            self.ui.update_text(self.accumulated_text, highlight_new=text)
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
            self.worker.add_audio(audio_data)
    
    def _update_status(self) -> None:
        """Update the UI status."""
        if self.recording:
            elapsed = time.time() - self.recording_start_time
            self.ui.update_status(True, elapsed, self.continuous_mode)
            
            # Process audio data from the audio processor queue
            try:
                audio_data = self.audio_processor.get_audio_data()
                if audio_data:
                    self.worker.add_audio(audio_data)
            except Exception as e:
                self.logger.error(f"Error processing audio data: {e}")
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
        
        # Visual indication that recording has started
        self.ui.update_status_color("recording")
        
        # Configure audio processor and connect it to the worker
        self.audio_processor.recording = True
        
        # Set up a periodic callback to process audio
        def periodic_audio_processing():
            if self.recording:
                audio_data = self.audio_processor.get_audio_data()
                if audio_data:
                    self.logger.debug(f"Periodic callback got {len(audio_data)} bytes")
                    self.worker.add_audio(audio_data)
                # Schedule next call
                self.ui.window.after(50, periodic_audio_processing)  # More frequent updates
                
        # Schedule first call
        self.ui.window.after(50, periodic_audio_processing)
        
        # Start audio stream
        if not self.audio_processor.start_stream():
            self.logger.error("Failed to start audio stream")
            self.recording = False
            self.ui.update_status_color("error")
            return False
        
        # Start transcription worker
        self.worker.start()
        
        # Reset pause detection
        self.transcription_service.pause_detected = False
        
        # Make sure the audio processor's queue is being monitored
        self.logger.debug("Ensuring audio data flows to worker")
        
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
        
        # Visual indication that recording has stopped
        self.ui.update_status_color("ready")
        
        # Ensure keyboard hooks are working properly by refreshing them
        if hasattr(self, 'event_manager'):
            self.event_manager.clear_hotkeys()
            self.event_manager.setup_hotkeys()
            self.logger.debug("Keyboard hotkeys refreshed")
        
        # Clear the transcription service cache to prevent duplicates on next start
        if hasattr(self.transcription_service, '_transcript_cache'):
            self.transcription_service._transcript_cache = set()
            self.transcription_service._last_transcript = ""
        
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
        
        # Clear transcription service cache
        if hasattr(self.transcription_service, '_transcript_cache'):
            self.transcription_service._transcript_cache = set()
            self.transcription_service._last_transcript = ""
        
        self.logger.info("Transcript cleared")
    
    def run(self) -> None:
        """Run the service."""
        self.logger.info("Starting main service loop")
        
        # Setup UI update timer
        def update_ui() -> None:
            # Only log status changes, not periodic updates
            old_status = self.ui.status_label.cget("text") if hasattr(self.ui, "status_label") else ""
            
            self._update_status()
            
            # Schedule next update - less frequent updates (500ms instead of 100ms)
            if hasattr(self, 'ui') and hasattr(self.ui, 'window') and self.ui.window.winfo_exists():
                self.ui.window.after(500, update_ui)
        
        # Start UI updates
        self.ui.window.after(500, update_ui)
        
        try:
            # Start UI main loop
            self.ui.run()
        finally:
            # Make sure we clean up properly when closing
            self.logger.info("Main loop ended, cleaning up resources")
            self._cleanup()
    
    def _cleanup(self) -> None:
        """Clean up all resources properly."""
        try:
            # Stop recording if active
            if self.recording:
                self.stop_recording()
            
            # Clean up keyboard hooks explicitly
            if hasattr(self, 'event_manager'):
                self.event_manager.clear_hotkeys()
                self.logger.debug("Keyboard hotkeys cleared")
            
            # Clean up audio resources
            if hasattr(self, 'audio_processor'):
                self.audio_processor.stop_stream()
            
            # Stop worker if running
            if hasattr(self, 'worker'):
                self.worker.stop()
                
            self.logger.info("All resources cleaned up")
        except Exception as e:
            self.logger.error(f"Error during cleanup: {e}", exc_info=True)
    
    def __del__(self) -> None:
        """Clean up service resources."""
        self.logger.info("Shutting down Voice Input Service")
        self._cleanup()
