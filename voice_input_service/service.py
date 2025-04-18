from __future__ import annotations
import threading
import time
import os
from typing import Optional
import pyperclip
import logging

# Import core components
from voice_input_service.core.audio import AudioRecorder
from voice_input_service.core.transcription import TranscriptionEngine
from voice_input_service.core.processing import TranscriptionWorker
from voice_input_service.utils.file_ops import TranscriptManager
from voice_input_service.utils.logging import setup_logging
from voice_input_service.ui.window import TranscriptionUI
from voice_input_service.ui.events import KeyboardEventManager, EventHandler
from voice_input_service.core.config import Config

class VoiceInputService(EventHandler):
    """Main service for voice transcription."""
    
    def __init__(self) -> None:
        # Setup logging
        self.logger = setup_logging()
        self.logger.info("Starting Voice Input Service")
        
        # Load configuration
        self.config = Config.load()
        
        # Initialize core components
        self.recorder = AudioRecorder(
            sample_rate=self.config.audio.sample_rate,
            channels=self.config.audio.channels,
            chunk_size=self.config.audio.chunk_size,
            device_index=self.config.audio.device_index,
            on_data_callback=self._on_audio_data
        )
        
        self.transcriber = TranscriptionEngine(
            model_name=self.config.transcription.model_name,
            device=self.config.transcription.device,
            compute_type=self.config.transcription.compute_type,
            language=self.config.transcription.language
        )
        
        self.transcript_manager = TranscriptManager()
        
        # Initialize UI
        self.ui = TranscriptionUI()
        
        # State variables
        self.recording = False
        self.recording_start_time = 0.0
        self.accumulated_text = ""
        self.continuous_mode = False
        self.processing_thread: Optional[threading.Thread] = None
        self.audio_buffer = bytearray()
        self.last_ui_update_time = 0
        
        # Set up processing worker
        self.worker = TranscriptionWorker(
            process_func=self._process_audio_chunk,
            on_result=self._on_transcription_result,
            min_audio_length=self.config.audio.min_audio_length
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
    
    def _toggle_continuous_mode(self) -> None:
        """Toggle continuous mode."""
        self.continuous_mode = self.ui.continuous_var.get()
        self.event_manager.continuous_mode = self.continuous_mode
        self.logger.info(f"Continuous mode {'enabled' if self.continuous_mode else 'disabled'}")
    
    def _change_language(self, language: str) -> None:
        """Change the transcription language.
        
        Args:
            language: ISO 639-1 language code
        """
        if not language:
            self.logger.warning("Empty language code provided")
            return

        # Update transcriber language
        try:
            self.transcriber.set_language(language)
            self.logger.info(f"Language changed to {language}")
            
            # Update config to persist the change
            self.config.transcription.language = language
            self.config.save()
        except ValueError as e:
            self.logger.error(f"Invalid language code: {language}")
            # Revert UI to current language
            self.ui.language_var.set(self.config.transcription.language)
    
    def _on_audio_data(self, data: bytes) -> None:
        """Handle incoming audio data."""
        if self.recording:
            # Add data to processing worker
            self.worker.add_audio(data)
            self.audio_buffer.extend(data)
    
    def _process_audio_chunk(self, audio_data: bytes) -> Optional[str]:
        """Process an audio chunk and return transcription result."""
        if not audio_data or len(audio_data) < 4000:  # Minimum audio length
            return None
        
        try:
            # Get current language setting
            language = self.ui.language_var.get()
            
            # Transcribe the audio data
            result = self.transcriber.transcribe(
                audio_data=audio_data,
                language=language
            )
            
            # Return the transcribed text
            return result.get("text", "").strip()
        except Exception as e:
            self.logger.error(f"Error transcribing audio: {e}")
            return None
    
    def start_recording(self) -> bool:
        """Start audio recording."""
        if self.recording:
            self.logger.warning("Already recording")
            return False
        
        self.logger.info("Starting recording")
        
        # Reset state
        self.recording = True
        self.recording_start_time = time.time()
        self.audio_buffer = bytearray()
        
        # Visual indication of recording
        self.ui.update_status_color("recording")
        
        # Start worker if not running
        if not self.worker.running:
            self.worker.start()
        
        # Start audio recorder
        if not self.recorder.start():
            self.logger.error("Failed to start audio recorder")
            self.recording = False
            self.ui.update_status_color("error")
            return False
        
        # Update UI
        self.ui.update_status(True)
        
        return True
    
    def stop_recording(self) -> str:
        """Stop audio recording and return the transcription."""
        if not self.recording:
            self.logger.warning("Not recording")
            return ""
        
        self.logger.info("Stopping recording")
        
        # Stop recording
        audio_data = self.recorder.stop()
        
        # Update state
        self.recording = False
        
        # Visual indication that recording has stopped
        self.ui.update_status_color("processing")
        self.ui.update_status(False)
        
        # Process any remaining audio buffer
        if len(self.audio_buffer) > 8000:  # At least ~0.1s of audio
            self.logger.info(f"Processing final audio chunk ({len(self.audio_buffer)/1024:.1f} KB)")
            self._process_audio_chunk(bytes(self.audio_buffer))
        else:
            self.logger.warning("No audio data to transcribe")
            self.ui.update_status_color("ready")
        
        # Stop the worker
        self.worker.stop()
        
        return self.accumulated_text
    
    def _on_transcription_result(self, text: str) -> None:
        """Handle transcription result."""
        if not text:
            return
            
        # Skip duplicate "hi" transcriptions which happen often
        if text.lower() == "hi" and "hi" in self.accumulated_text.lower():
            self.logger.debug("Skipping duplicate 'hi' transcription")
            return
            
        # Add appropriate spacing between sentences
        if self.accumulated_text:
            if self.accumulated_text.endswith(('.', '!', '?', ':', ';')):
                separator = " "
            else:
                separator = " "
        else:
            separator = ""
        
        # Add text with appropriate separator
        new_text = text.strip()
        self.accumulated_text += separator + new_text
        self.accumulated_text = self.accumulated_text.strip()
        
        # Clean up the text - remove multiple spaces, fix capitalization
        self.accumulated_text = ' '.join(self.accumulated_text.split())
        
        # Update UI
        self.ui.update_text(self.accumulated_text, highlight_new=new_text)
        word_count = len(self.accumulated_text.split())
        self.ui.update_word_count(word_count)
        
        # Detect pause and stop recording if needed
        if not self.continuous_mode and word_count > 5:
            # After getting reasonable content, check for natural pause
            elapsed = time.time() - self.recording_start_time
            if elapsed > 2.0:  # At least 2 seconds of recording
                self.logger.debug("Natural pause detected after transcription, stopping recording")
                self.stop_recording()
                
                # Copy to clipboard for convenience
                self._copy_to_clipboard()
        
        # Handle continuous mode
        if self.continuous_mode and word_count > 10:
            # Copy to clipboard and save
            self._copy_to_clipboard()
            self._save_transcript()
            self._clear_transcript()
            
            # Start a new recording
            self.start_recording()
    
    def _copy_to_clipboard(self) -> None:
        """Copy the current transcript to clipboard."""
        if self.accumulated_text:
            pyperclip.copy(self.accumulated_text)
            self.logger.info("Transcript copied to clipboard")
    
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
            if hasattr(self, 'ui') and hasattr(self.ui, 'window') and self.ui.window.winfo_exists():
                current_time = time.time()
                update_interval = 0.5  # Update UI every 500ms
                
                # Only update status if recording or enough time has passed
                if self.recording or (current_time - self.last_ui_update_time) >= update_interval:
                    # Update status
                    if self.recording:
                        elapsed = time.time() - self.recording_start_time
                        self.ui.update_status(True, elapsed, self.continuous_mode)
                    else:
                        # Only update status if not already set to "Ready"
                        self.ui.update_status(False)
                    
                    self.last_ui_update_time = current_time
                
                # Schedule next update (more frequent for smoother animation)
                self.ui.window.after(250, update_ui)
        
        # Start UI updates
        self.ui.window.after(250, update_ui)
        
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
            
            # Stop the worker
            if hasattr(self, 'worker'):
                self.worker.stop()
            
            # Clean up keyboard hooks explicitly
            if hasattr(self, 'event_manager'):
                self.event_manager.clear_hotkeys()
                self.logger.debug("Keyboard hotkeys cleared")
            
            self.logger.info("All resources cleaned up")
        except Exception as e:
            self.logger.error(f"Error during cleanup: {e}")
    
    def __del__(self) -> None:
        """Clean up service resources."""
        self.logger.info("Shutting down Voice Input Service")
        self._cleanup()
