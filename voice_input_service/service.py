from __future__ import annotations
import threading
import time
from typing import Optional
import pyperclip
import logging
import tkinter as tk
from tkinter import messagebox
import numpy as np

# Import core components
from voice_input_service.core.audio import AudioRecorder
from voice_input_service.core.transcription import TranscriptionEngine
from voice_input_service.core.processing import TranscriptionWorker
from voice_input_service.utils.file_ops import TranscriptManager
from voice_input_service.ui.window import TranscriptionUI
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
        
        self.ui.set_config(self.config)
        self.ui.on_settings_changed = self._on_settings_changed
        
        self.logger.info("Voice Input Service ready")
    
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
            # Convert bytes to numpy array for audio analysis
            audio_array = np.frombuffer(audio_data, dtype=np.int16)
            
            # Calculate RMS value to detect silence
            rms = np.sqrt(np.mean(np.square(audio_array, dtype=np.float64)))
            
            # Skip if the audio is too quiet (likely silence)
            # Typical silence threshold for 16-bit audio is around 500-1000
            silence_threshold = 500
            if rms < silence_threshold:
                self.logger.debug(f"Skipping silent audio chunk (RMS: {rms:.2f})")
                return None
            
            # Transcribe the audio data
            result = self.transcriber.transcribe(audio=audio_data)
            
            # Get the text and apply more aggressive filtering
            text = result.get("text", "").strip()
            
            # Skip very short results as they're often hallucinations
            if len(text.split()) <= 2:
                self.logger.debug(f"Skipping very short result: '{text}'")
                return None
                
            return text
            
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
        # Common hallucinated phrases and their variations
        hallucination_patterns = [
            "thanks for watching",
            "thank you for watching",
            "don't forget to subscribe",
            "like and subscribe",
            "see you in the next video",
            "thanks for listening",
            "thank you.",
            "thank you",
            "subscribe to",
            "click the",
            "check out",
            "in this video",
            "in today's video",
            "please",  # Added common single-word hallucinations
            "thanks",
            "goodbye",
            "bye bye",
            "see you",
            "welcome",
            "hello everyone",
            "hi everyone",
        ]
        
        # Convert to lower case for comparison
        text_lower = text.lower().strip()
        
        # 1. Check for exact matches of common hallucinations
        if text_lower in hallucination_patterns:
            self.logger.debug(f"Filtered exact hallucination match: {text}")
            return ""
            
        # 2. Check if the text starts or ends with any of these patterns
        for pattern in hallucination_patterns:
            if text_lower.startswith(pattern) or text_lower.endswith(pattern):
                self.logger.debug(f"Filtered prefix/suffix hallucination: {text}")
                return ""
                
        # 3. Check if the text is mostly composed of hallucinated content
        for pattern in hallucination_patterns:
            if pattern in text_lower:
                # If the text is mostly the hallucinated pattern (allowing for some extra words)
                if len(text.split()) <= len(pattern.split()) + 2:
                    self.logger.debug(f"Filtered partial hallucination: {text}")
                    return ""
        
        # 4. Additional heuristics for short responses
        if len(text.split()) <= 3:  # For very short responses
            if any(word in text_lower for word in ["thanks", "thank", "please", "subscribe", "hello", "hi", "hey"]):
                self.logger.debug(f"Filtered short hallucination: {text}")
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
            # In non-continuous mode, append with proper spacing
            if self.accumulated_text:
                if self.accumulated_text.endswith(('.', '!', '?', ':', ';')):
                    self.accumulated_text += " " + text
                else:
                    self.accumulated_text += ". " + text
            else:
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
        self.logger.info("Starting main application loop")
        
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
            self.logger.info("Main application loop ended, cleaning up resources")
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

    def _on_settings_changed(self) -> None:
        """Handle changes to settings."""
        self.logger.info("Settings updated - applying changes")
        
        # Check if we need to reinitialize the transcription engine
        use_cpp = self.config.transcription.use_cpp
        whisper_cpp_path = self.config.transcription.whisper_cpp_path
        ggml_model_path = self.config.transcription.ggml_model_path
        
        # If engine type changed or whisper.cpp path changed while using cpp
        current_is_cpp = getattr(self.transcriber, "use_cpp", False)
        
        # Identify what changed to give a specific message
        if current_is_cpp != use_cpp:
            change_type = "engine type"
            needs_restart = True
        elif use_cpp and self.transcriber.whisper_cpp_path != whisper_cpp_path:
            change_type = "whisper.cpp path"
            needs_restart = True
        elif use_cpp and hasattr(self.transcriber, 'model_file_path') and self.transcriber.model_file_path != ggml_model_path:
            change_type = "GGML model"
            needs_restart = True
        else:
            change_type = "settings"
            needs_restart = False
            
        if needs_restart:
            # Show restart needed message in UI
            restart_message = f"{change_type.capitalize()} changed - application restart required for changes to take effect"
            self.logger.info(restart_message)
            
            # Show a proper dialog message box
            messagebox.showinfo(
                "Restart Required",
                f"The {change_type} has been changed. This change requires restarting the application to take effect.\n\n"
                "Please close and restart the application for the changes to be applied."
            )
            
            # Update UI status as well
            self.ui.update_status_text(restart_message)
            
            # Flash the status frame to indicate the change
            self.ui.update_status_color("error")
            self.ui.window.after(2000, lambda: self.ui.update_status_color("ready"))
            
            return
            
        # Handle minor settings changes that don't require restart
        self.logger.info(f"Applied {change_type} changes without restart")
        self.ui.update_status_text(f"{change_type.capitalize()} updated")
        self.ui.update_status_color("ready")
