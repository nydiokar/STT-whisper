from __future__ import annotations
import threading
import time
import os
from typing import Optional
import pyperclip
import logging
import whisper
import tkinter as tk
from tkinter import filedialog, messagebox
import numpy as np

# Import core components
from voice_input_service.core.audio import AudioRecorder
from voice_input_service.core.transcription import TranscriptionEngine
from voice_input_service.core.processing import TranscriptionWorker
from voice_input_service.utils.file_ops import TranscriptManager
from voice_input_service.utils.logging import setup_logging
from voice_input_service.ui.window import TranscriptionUI
from voice_input_service.ui.dialogs import ModelSelectionDialog, DownloadProgressDialog
from voice_input_service.ui.events import KeyboardEventManager, EventHandler
from voice_input_service.core.config import Config

class VoiceInputService(EventHandler):
    """Main service for voice transcription."""
    
    def __init__(
        self, 
        config: Config, 
        ui: TranscriptionUI, 
        transcriber: TranscriptionEngine
    ) -> None:
        """Initialize the voice input service.
        
        Args:
            config: Application configuration
            ui: User interface component
            transcriber: Transcription engine
        """
        # Get logger (should be already set up)
        self.logger = logging.getLogger("VoiceService")
        self.logger.info("Initializing Voice Input Service")
        
        # Store initialized components
        self.config = config
        self.ui = ui
        self.transcriber = transcriber
        
        # Initialize additional components
        self.recorder = AudioRecorder(
            sample_rate=self.config.audio.sample_rate,
            channels=self.config.audio.channels,
            chunk_size=self.config.audio.chunk_size,
            device_index=self.config.audio.device_index,
            on_data_callback=self._on_audio_data
        )
        
        self.transcript_manager = TranscriptManager()
        
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
        # Use a larger minimum chunk size for better accuracy
        min_chunk_size = self.config.transcription.min_chunk_size  # Default 32000 bytes (about 1 second)
        
        if not audio_data or len(audio_data) < min_chunk_size:
            return None
        
        try:
            # Transcribe the audio data directly
            result = self.transcriber.transcribe(audio=audio_data)
            
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
        
        # Clear accumulated text in non-continuous mode
        if not self.continuous_mode:
            self.accumulated_text = ""
            self.ui.update_text("")
            self.ui.update_word_count(0)
        
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
        
        # Stop recording flag first
        self.recording = False
        
        # Stop audio recorder and get final data
        audio_data = self.recorder.stop()
        
        # Visual indication that recording has stopped
        self.ui.update_status_color("processing")
        self.ui.update_status(False)
        
        # Process any remaining audio buffer if significant
        if len(audio_data) > 8000:  # At least ~0.1s of audio
            self.logger.info(f"Processing final audio chunk ({len(audio_data)/1024:.1f} KB)")
            self._process_audio_chunk(audio_data)
        
        # Stop the worker after processing final chunk
        self.worker.stop()
        
        # Reset state
        self.audio_buffer = bytearray()
        
        # Update UI to ready state
        self.ui.update_status_color("ready")
        
        return self.accumulated_text
    
    def _filter_hallucinations(self, text: str) -> str:
        """Filter out common hallucinated phrases from Whisper."""
        # Common YouTube-style endings that Whisper tends to hallucinate
        hallucination_patterns = [
            "thanks for watching",
            "thank you for watching",
            "don't forget to subscribe",
            "like and subscribe",
            "see you in the next video",
            "thanks for listening",
        ]
        
        # Convert to lower case for comparison
        text_lower = text.lower()
        
        # Check if the text is mostly or entirely composed of hallucinated content
        for pattern in hallucination_patterns:
            if pattern in text_lower:
                # If the text is mostly the hallucinated pattern (allowing for some extra words)
                if len(text.split()) <= len(pattern.split()) + 2:
                    self.logger.debug(f"Filtered hallucinated phrase: {text}")
                    return ""
        
        return text

    def _on_transcription_result(self, text: str) -> None:
        """Handle transcription result."""
        if not text:
            return
            
        self.logger.debug(f"Received transcription: {text}")
        
        # Clean up the text and filter hallucinations
        text = text.strip()
        if text == "." or not text:  # Skip empty or just punctuation
            return
            
        # Filter out hallucinated phrases
        text = self._filter_hallucinations(text)
        if not text:  # Skip if filtered out
            return
            
        # Update accumulated text
        if self.continuous_mode:
            if self.accumulated_text:
                # Add appropriate spacing between sentences
                if self.accumulated_text.endswith(('.', '!', '?', ':', ';')):
                    self.accumulated_text += " " + text
                else:
                    self.accumulated_text += ". " + text
            else:
                self.accumulated_text = text
        else:
            # In non-continuous mode, keep the current transcription
            self.accumulated_text = text
        
        # Update UI with accumulated text
        self.ui.update_text(self.accumulated_text)
        word_count = len(self.accumulated_text.split())
        self.ui.update_word_count(word_count)
        
        # Natural pause detection only in non-continuous mode
        if self.recording and not self.continuous_mode:
            self.logger.debug("Natural pause detected after transcription, stopping in 1.5 seconds unless more speech is detected")
            
            def delayed_stop():
                time.sleep(1.5)  # Wait for potential continued speech
                if self.recording and not self.worker.has_recent_audio():
                    self.logger.debug("No further speech detected, stopping recording")
                    self.stop_recording()
                    # Copy to clipboard in non-continuous mode
                    if self.accumulated_text:
                        self._copy_to_clipboard()
            
            # Start delayed stop thread
            stop_thread = threading.Thread(target=delayed_stop)
            stop_thread.daemon = True
            stop_thread.start()
    
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
