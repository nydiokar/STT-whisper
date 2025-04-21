from __future__ import annotations
import threading
import time
from typing import Optional
import logging
import numpy as np
import tkinter as tk
from tkinter import messagebox

# Import core components
from voice_input_service.core.audio import AudioRecorder
from voice_input_service.core.transcription import TranscriptionEngine, ModelError
from voice_input_service.core.processing import TranscriptionWorker
from voice_input_service.utils.file_ops import TranscriptManager
from voice_input_service.ui.events import KeyboardEventManager, EventHandler
from voice_input_service.config import Config
from voice_input_service.utils.clipboard import copy_to_clipboard
from voice_input_service.utils.lifecycle import Component, Closeable
from voice_input_service.utils.text_processor import TextProcessor

class VoiceInputService(EventHandler, Closeable):
    """Main service for voice transcription."""
    
    def __init__(
        self, 
        config: Config, 
        ui, 
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
        
        # Initialize utility classes - each with a clear responsibility
        self.transcript_manager = TranscriptManager()
        self.text_processor = TextProcessor(min_words=2)
        
        # State variables
        self.recording = False
        # Read initial continuous mode state from config
        self.continuous_mode = config.transcription.continuous_mode 
        self.model_error_reported = False
        
        # Thread synchronization
        self.state_lock = threading.RLock()
        self.stop_timer = None
        
        # Test the transcription model before starting
        self._verify_transcription_model()
        
        # Set up processing worker, passing initial mode
        self.worker = TranscriptionWorker(
            process_func=self._process_audio_chunk,
            on_result=self._on_transcription_result,
            config=self.config,
            initial_continuous_mode=self.continuous_mode # Pass initial state
        )
        
        # Set up UI events
        self._setup_ui_events()
        
        # Set up keyboard event manager
        self.event_manager = KeyboardEventManager(self)
        self.event_manager.setup_hotkeys()
        
        # Set config in UI and register for settings changes
        self.ui.set_config(self.config)
        self.ui.on_settings_changed = self._on_settings_changed
        
        self.logger.info(f"Initializing VoiceInputService instance, id(self): {id(self)}")
        self.accumulated_text = ""
        
        self.logger.info("Voice Input Service ready")
    
    def _verify_transcription_model(self) -> bool:
        """Test the transcription model to ensure it's ready to use.
        
        Returns:
            True if model verification succeeded, False otherwise
        """
        try:
            # Run a test to see if the model is available and properly configured
            test_result = self.transcriber.test_model()
            
            if not test_result["success"]:
                error_message = test_result.get("error", "Unknown model error")
                self.logger.error(f"Transcription model test failed: {error_message}")
                
                # Notify the user through the UI
                if hasattr(self.ui, 'show_model_error'):
                    self.ui.show_model_error(error_message)
                else:
                    # Fallback if custom error dialog isn't available
                    self.ui.update_status_text(f"Model error: {error_message}")
                    
                self.model_error_reported = True
                return False
                
            # Model verification successful
            self.model_error_reported = False
            return True
            
        except Exception as e:
            error_message = f"Error testing transcription model: {e}"
            self.logger.error(error_message)
            
            # Notify through UI if possible
            if hasattr(self.ui, 'show_model_error'):
                self.ui.show_model_error(str(e))
            else:
                self.ui.update_status_text(f"Model error: {e}")
                
            self.model_error_reported = True
            return False
    
    def _setup_ui_events(self) -> None:
        """Setup UI event callbacks."""
        # Tell UI about our callbacks for continuous mode and language
        self.ui.set_continuous_mode_handler(self._toggle_continuous_mode)
        self.ui.set_language_handler(self._change_language)
    
    def _toggle_continuous_mode(self, enabled: bool) -> None:
        """Toggle continuous mode."""
        # Prevent mode change while recording
        if self.recording:
            self.logger.warning("Cannot change continuous mode while recording is active.")
            # Optionally, revert the checkbox state in the UI if possible
            # self.ui.continuous_var.set(not enabled) # Revert UI state
            messagebox.showwarning("Recording Active", "Cannot change Continuous Mode while recording is in progress.")
            self.ui.continuous_var.set(self.continuous_mode) # Set checkbox back
            return
            
        with self.state_lock:
            if self.continuous_mode == enabled:
                return # No change
                
            self.continuous_mode = enabled
            # Update keyboard manager if needed
            if hasattr(self, 'event_manager'): 
                self.event_manager.continuous_mode = self.continuous_mode
            
            # Update the config object
            self.config.transcription.continuous_mode = enabled
            
            # Update the worker's mode state and VAD settings
            if hasattr(self, 'worker'):
                self.worker.set_continuous_mode(enabled) # Inform the worker
                self.worker.update_vad_settings()
                
            self.logger.info(f"Continuous mode {'enabled' if self.continuous_mode else 'disabled'}")
            # Consider saving config here if desired, or rely on SettingsDialog save
            # self.config.save()
    
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
            # Notify UI of error
            self.ui.show_language_error(str(e))
    
    def _on_audio_data(self, data: bytes) -> None:
        """Handle incoming audio data."""
        # Using a lock isn't necessary here as we're only checking
        # self.recording which is a boolean (atomic read in Python)
        if self.recording:
            # Add data to processing worker
            self.worker.add_audio(data)
    
    def _process_audio_chunk(self, audio_data: bytes) -> Optional[str]:
        """Process an audio chunk and return transcription result."""
        min_chunk_size = self.config.transcription.min_chunk_size
        if not audio_data or len(audio_data) < min_chunk_size:
            return None

        chunk_duration = len(audio_data) / (self.config.audio.sample_rate * 2) # Approx duration
        self.logger.info(f"Starting transcription for audio chunk ({chunk_duration:.1f}s)...") # Log start
        start_time = time.time()
        
        try:
            # Transcribe the audio data
            result = self.transcriber.transcribe(audio=audio_data)
            
            end_time = time.time()
            self.logger.info(f"Transcription finished in {end_time - start_time:.2f} seconds.") # Log end
            
            # Get the text and apply more aggressive filtering
            text = result.get("text", "").strip()
            
            # Skip very short results as they're often hallucinations
            if not self.text_processor.is_valid_utterance(text):
                self.logger.debug(f"Skipping invalid utterance: '{text}'")
                return None
                
            # Reset model error flag if transcription succeeded
            self.model_error_reported = False
            
            return text
        except ModelError as e:
            # Handle model errors specifically
            if not self.model_error_reported:
                self.logger.error(f"Model error during transcription: {e}")
                
                # Show model error in UI if possible
                if hasattr(self.ui, 'show_model_error'):
                    self.ui.show_model_error(str(e))
                else:
                    self.ui.update_status_text(f"Transcription error: {e}")
                
                # Set flag to avoid spamming the user with the same error
                self.model_error_reported = True
                
                # Stop recording if it's in progress
                if self.recording:
                    self.stop_recording()
            
            return None    
        except Exception as e:
            self.logger.error(f"Error transcribing audio: {e}")
            return None
    
    def start_recording(self) -> bool:
        """Start audio recording."""
        with self.state_lock:
            if self.recording:
                self.logger.warning("Already recording")
                return False
            
            # Don't start if there's a known model error
            if self.model_error_reported:
                self.logger.warning("Cannot start recording due to model error")
                self.ui.update_status_text("Cannot start recording - model error")
                return False
                
            self.logger.info("Starting recording")
            
            # --- Always clear previous text for a clean start --- 
            self.logger.debug("Clearing previous accumulated text for new recording session.")
            self.accumulated_text = ""
            # Also clear the UI display immediately
            self.ui.update_text("") 
            self.ui.update_word_count(0)
            # ---------------------------------------------------

            # Cancel any pending stop timer
            if self.stop_timer:
                self.stop_timer.cancel()
                self.stop_timer = None
            
            # Reset state
            self.recording = True
            self.recording_start_time = time.time()
            
            # Update UI status
            self.ui.update_status(True)
            self.ui.update_status_color("recording")
            
            # Start worker if not running
            if not self.worker.running:
                self.worker.start()
            
            # Start audio recorder
            if not self.recorder.start():
                self.logger.error("Failed to start audio recorder")
                self.recording = False
                self.ui.update_status(False)
                self.ui.update_status_color("error")
                return False
            
            return True
    
    def stop_recording(self, cancel_timer: bool = True) -> str:
        """Stop audio recording, process final chunk, update state/UI, and return text."""
        final_text_to_return = ""
        with self.state_lock:
            if not self.recording: return "" 
            self.logger.info(f"Stopping recording... Instance: {id(self)}")
            
            # Cancel any pending stop timer
            if cancel_timer and self.stop_timer:
                self.stop_timer.cancel()
                self.stop_timer = None
            
            # Stop recording flag first
            self.recording = False
            
            # Stop audio recorder and get final data
            audio_data = self.recorder.stop()
            
            # Show processing status
            self.ui.update_status(False)
            self.ui.update_status_color("processing")

            # --- Conditional Final Chunk Processing ---
            if not self.continuous_mode:
                # == Non-Continuous Mode: Process the entire recording once ==
                self.logger.info("Non-continuous mode: Processing entire recording.")
                final_chunk_text: Optional[str] = None
                # Ensure accumulated text is clear before setting from final chunk
                self.accumulated_text = "" 
                if len(audio_data) > self.config.transcription.min_chunk_size: # Use configured min size
                    self.logger.info(f"Starting final audio chunk processing ({len(audio_data)/1024:.1f} KB)...")
                    start_time = time.time()
                    try:
                        # Process the final chunk directly
                        final_chunk_text = self._process_audio_chunk(audio_data)
                    finally:
                        end_time = time.time()
                        self.logger.info(f"Final audio chunk processing finished in {end_time - start_time:.2f} seconds.")

                    if final_chunk_text:
                        self.logger.debug("Setting accumulated text from final chunk.")
                        # This IS the final text for non-continuous mode
                        self.accumulated_text = final_chunk_text
                    else:
                        self.logger.warning("Final audio chunk processing yielded no text.")
                else:
                    self.logger.info("Skipping final audio chunk processing (too short).")
            else:
                # == Continuous Mode: Use incrementally built text ==
                self.logger.info("Continuous mode: Skipping final chunk processing, using incremental results.")
                # self.accumulated_text already holds the text from _on_transcription_result
            # --- End Conditional Processing ---

            # Stop the worker thread (important after deciding final text)
            self.worker.stop()

            # --- Update UI with the FINAL complete text ---
            self.logger.debug(f"Updating UI with final text: '{self.accumulated_text}'")
            self.ui.update_text(self.accumulated_text)
            self.ui.update_word_count(len(self.accumulated_text.split()))

            # Update UI to ready state
            self.ui.update_status_color("ready")
            
            final_text_to_return = self.accumulated_text
            # --- End of state_lock block ---
            
        self.logger.info("Recording stopped successfully.")
        return final_text_to_return
    
    def _on_transcription_result(self, text: str) -> None:
        """Handle transcription result FROM THE WORKER during continuous mode."""
        # If not in continuous mode, this callback should ideally not be triggered by the worker,
        # but we add a check just in case.
        if not self.continuous_mode:
            self.logger.debug(f"Ignoring intermediate transcription result in non-continuous mode: {text}")
            return
            
        if not text:
            return
            
        self.logger.debug(f"Received intermediate transcription chunk (continuous): {text}")
        
        # Clean up the text
        text = text.strip()
        if text == "." or not text: return
        text = self.text_processor.remove_timestamps(text)
        if not text: return
        text = self.text_processor.filter_hallucinations(text)
        if not text: return
            
        # --- Update UI Incrementally (Continuous Mode) --- 
        # Append the new text chunk to the UI display
        # NOTE: This requires the UI method to handle appending or be called appropriately.
        # Assuming ui.update_text replaces content for now.
        # A potential improvement: ui.append_text(text)
        with self.state_lock: # Lock needed if we read accumulated_text
            # Build the text to display (potentially including previous chunks for this session)
            # For simplicity, let's keep track of UI text separately or just append
            # Let's try just appending the new fragment for now
            # We need a way to get current text from UI or store it
            # current_ui_text = self.ui.get_text() # Hypothetical UI method
            # self.ui.update_text(current_ui_text + " " + text)
            
            # Alternative: Update accumulated text HERE for continuous mode only?
            # This makes stop_recording simpler for continuous.
            if self.accumulated_text:
                 self.accumulated_text = self.text_processor.append_text(self.accumulated_text, text)
            else:
                 self.accumulated_text = text
            self.ui.update_text(self.accumulated_text) # Update UI with growing text
            self.ui.update_word_count(len(self.accumulated_text.split()))
        # -----------------------------------------------
        
        # Check pause detection ONLY if continuous mode (already checked above, but safe)
        if self.continuous_mode:
            self.logger.debug("Checking for natural pause in continuous mode (intermediate chunk)...")
            if not self.worker.has_recent_audio():
                self.logger.debug("Natural pause detected, scheduling delayed stop...")
                self._delayed_stop() 
            else:
                # Cancel timer if speech resumes quickly
                with self.state_lock:
                    if self.stop_timer:
                        self.stop_timer.cancel()
                        self.stop_timer = None

    def _delayed_stop(self) -> None:
        """Handle delayed stop logic in a thread-safe way."""
        with self.state_lock:
            # Clear the timer reference
            self.stop_timer = None
            
            # Only stop if we're still recording and no recent audio
            if self.recording and not self.worker.has_recent_audio():
                self.logger.debug("No further speech detected, stopping recording")
                # Call stop_recording with cancel_timer=False since we're already in the timer callback
                result = self.stop_recording(cancel_timer=False)
                
                # Copy to clipboard in non-continuous mode
                if result:
                    self._handle_clipboard_copy()
    
    def _handle_clipboard_copy(self) -> None:
        """Handle copying text to clipboard and updating UI."""
        with self.state_lock:
            text_to_copy = self.accumulated_text
            
        if not text_to_copy:
            self.logger.warning("No transcript to copy to clipboard")
            self.ui.update_status_text("No text to copy")
            return
            
        # Use clipboard utility directly
        if copy_to_clipboard(text_to_copy):
            self.ui.update_status_text("Copied to clipboard")
    
    # EventHandler interface methods - these handle events from the UI
    def save_transcript(self) -> None:
        """Handle save transcript request from UI."""
        with self.state_lock:
            text_to_save = self.accumulated_text
            
        if not text_to_save:
            self.logger.warning("No transcript to save")
            self.ui.update_status_text("No transcript to save")
            return
            
        # Delegate to transcript manager
        file_path = self.transcript_manager.save_transcript(text_to_save)
        
        # Update UI based on result
        if file_path:
            self.logger.info(f"Transcript saved to: {file_path}")
            self.ui.update_status_text(f"Saved to: {file_path}")
        else:
            self.logger.error("Failed to save transcript")
            self.ui.update_status_text("Error saving transcript")
    
    def clear_transcript(self) -> None:
        """Handle clear transcript request from UI."""
        with self.state_lock:
            self.accumulated_text = ""
            
        # Update UI to reflect cleared state
        self.ui.update_text("")
        self.ui.update_word_count(0)
        self.logger.info("Transcript cleared")
    
    def run(self) -> None:
        """Run the service."""
        self.logger.info("Starting main application loop")
        
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
            # Cancel any pending timers
            with self.state_lock:
                if self.stop_timer:
                    self.stop_timer.cancel()
                    self.stop_timer = None
            
            # Stop recording if active
            if self.recording:
                self.stop_recording()
            
            # Close components in reverse order of dependency
            components = []
            
            if hasattr(self, 'worker'):
                components.append(('worker', self.worker))
            
            if hasattr(self, 'recorder'):
                components.append(('recorder', self.recorder))
                
            if hasattr(self, 'event_manager'):
                components.append(('event_manager', self.event_manager))
            
            # Close each component properly
            for name, component in components:
                try:
                    if hasattr(component, 'close'):
                        component.close()
                    elif hasattr(component, 'stop'):
                        component.stop()
                    
                    self.logger.debug(f"Closed component: {name}")
                except Exception as e:
                    self.logger.error(f"Error closing {name}: {e}")
            
            self.logger.info("All resources cleaned up")
        except Exception as e:
            self.logger.error(f"Error during cleanup: {e}")
    
    def close(self) -> None:
        """Public method to clean up resources."""
        self._cleanup()
    
    def __del__(self) -> None:
        """Clean up service resources."""
        self.logger.info("Shutting down Voice Input Service")
        self.close()

    def _on_settings_changed(self) -> None:
        """Handle changes to settings."""
        self.logger.info("Settings updated - applying changes")
        
        # Check if we need to reinitialize the transcription engine
        use_cpp = self.config.transcription.use_cpp
        whisper_cpp_path = self.config.transcription.whisper_cpp_path
        ggml_model_path = self.config.transcription.ggml_model_path
        
        # If engine type changed or whisper.cpp path changed while using cpp
        current_is_cpp = getattr(self.transcriber, "use_cpp", False)
        
        # Flag for engine-related changes that need restart
        needs_restart = False
        change_type = ""
        
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
            # For VAD and other settings that don't need full restart
            change_type = "settings"
            
            # Reset model error flag and verify the model with new settings
            self.model_error_reported = False
            model_verified = self._verify_transcription_model()
            
            # Update worker with new settings even if model verification failed
            # This ensures we at least try to apply non-critical changes
            worker_was_running = self.worker.running
            if worker_was_running:
                self.worker.stop()
            
            # Update worker with new settings from config
            self.worker.update_vad_settings()
            
            # Restart worker if it was running and model is verified
            if worker_was_running and model_verified:
                self.worker.start()
                
            self.logger.info(f"Updated configuration settings")
            
        if needs_restart:
            # Show restart needed message in UI
            restart_message = f"{change_type.capitalize()} changed - application restart required for changes to take effect"
            self.logger.info(restart_message)
            
            # Notify UI of restart requirement
            self.ui.show_restart_required(change_type)
            
            return
            
        # Handle minor settings changes that don't require restart
        self.logger.info(f"Applied {change_type} changes without restart")
        
        if not self.model_error_reported:
            self.ui.update_status_text(f"{change_type.capitalize()} updated")
