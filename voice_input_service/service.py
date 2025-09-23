from __future__ import annotations
import threading
import time
from typing import Optional, Dict, Any, List, Literal, Tuple
import logging
import tkinter as tk
from tkinter import messagebox
from datetime import datetime # Import datetime for formatting
import os # Import os for path manipulation
from pathlib import Path # Added Path

# Import core components
from voice_input_service.core.audio import AudioRecorder
from voice_input_service.core.transcription import TranscriptionEngine, ModelError, TranscriptionResult
from voice_input_service.core.processing import TranscriptionWorker # Restored worker
from voice_input_service.utils.file_ops import TranscriptManager # Use updated manager
from voice_input_service.ui.events import KeyboardEventManager, EventHandler
from voice_input_service.config import Config
from voice_input_service.utils.clipboard import copy_to_clipboard
from voice_input_service.utils.lifecycle import Component, Closeable
from voice_input_service.utils.text_processor import TextProcessor

# Type alias for mode
OperatingMode = Literal["session", "continuous"]

class VoiceInputService(EventHandler, Closeable):
    """Main service for voice transcription with session and continuous modes."""
    
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
            on_data_callback=None # Set dynamically
        )
        
        # Initialize utility classes - each with a clear responsibility
        self.transcript_manager = TranscriptManager(base_dir=self.config.data_dir / "data")
        self.text_processor = TextProcessor(min_words=2)
        
        # State variables
        self.current_mode: OperatingMode = "session" # Default mode
        self.recording = False
        self.session_start_time: Optional[float] = None
        self.model_error_reported = False
        self.accumulated_audio_data = bytearray() # For session mode
        # Store for intermediate results in continuous mode (replace ChunkMetadataManager)
        self.continuous_segments: List[Dict[str, Any]] = [] 
        self.last_continuous_text = "" # Track last successful continuous text for UI
        
        # Thread synchronization
        self.state_lock = threading.RLock()
        
        # Test the transcription model before starting
        self._verify_transcription_model()
        
        # --- Initialize Worker ---
        # Worker handles VAD, buffering, and calls transcriber for intermediate results.
        self.worker: Optional[TranscriptionWorker] = None
        try:
             # Initialize TranscriptionWorker with ONLY the required arguments
             self.worker = TranscriptionWorker(
                 transcriber=self.transcriber,
                 on_result=self._on_continuous_result,
                 config=self.config
             )
             self.logger.info("TranscriptionWorker initialized.")
        except Exception as e:
             self.logger.error(f"Failed to initialize TranscriptionWorker: {e}", exc_info=True)
             # App can continue, but continuous mode might fail
        # --- End Worker Init ---
        
        # Set up processing worker, passing initial mode
        self.worker = TranscriptionWorker(
            process_func=self._process_audio_chunk,
            on_result=self._on_transcription_result,
            config=self.config,
            initial_continuous_mode=self.current_mode == "continuous", # Pass initial state
        )
        
        # Set up UI events
        self._setup_ui_events()
        
        # Set up keyboard event manager
        self.event_manager = KeyboardEventManager(self)
        self.event_manager.setup_hotkeys()
        
        # Set config in UI and register for settings changes
        self.ui.set_config(self.config)
        
        # --- Sync UI Checkbox with Initial Config State ---
        if hasattr(self.ui, 'continuous_var'):
            # Determine initial mode from config if available, else default
            # This depends on if config loading happens before service init
            # Assuming config is loaded, read initial state:
            self.current_mode = "continuous" if self.config.transcription.get('continuous_mode', False) else "session"
            self.ui.continuous_var.set(self.current_mode == "continuous")
            self.logger.debug(f"Initial UI checkbox state set to: {self.current_mode == 'continuous'} based on config")
        else:
             # Fallback if UI doesn't have the var yet
             self.current_mode = "session"
             self.logger.debug("UI continuous_var not found, defaulting mode to session")
        # --- End Sync ---
        
        self.logger.info(f"Initializing VoiceInputService instance, id(self): {id(self)}")
        
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
            self.ui.continuous_var.set(self.current_mode == "continuous") # Set checkbox back
            return
            
        with self.state_lock:
            if self.current_mode == "continuous" == enabled:
                return # No change
                
            self.current_mode = "continuous" if enabled else "session"
            # Update keyboard manager if needed
            if hasattr(self, 'event_manager'): 
                self.event_manager.continuous_mode = self.current_mode == "continuous"
            
            # Update the config object
            self.config.transcription.continuous_mode = self.current_mode == "continuous"
            self.config.save()
            
            # Update the worker's mode state and VAD settings
            if hasattr(self, 'worker'):
                self.worker.set_continuous_mode(self.current_mode == "continuous") # Inform the worker
                self.worker.update_vad_settings()
                
            self.logger.info(f"Continuous mode {'enabled' if self.current_mode == 'continuous' else 'disabled'}")
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
        """Handle incoming audio data by passing it to the worker."""
        if self.recording and self.worker:
            self.worker.add_audio(data)

    def _process_audio_chunk(self, audio_data: bytes) -> Optional[Tuple[str, float]]:
        """Process an audio chunk and return transcription result and duration."""
        min_chunk_size = self.config.transcription.min_chunk_size
        if not audio_data or len(audio_data) < min_chunk_size:
            return None

        # Calculate duration based on sample rate and assume 16-bit (2 bytes/sample)
        bytes_per_sample = 2
        duration = len(audio_data) / (self.config.audio.sample_rate * bytes_per_sample)
        
        self.logger.info(f"Starting transcription for audio chunk ({duration:.2f}s)...") # Log duration
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
            
            # Return text and duration
            return text, duration
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
        """Start recording based on the current mode."""
        if self.recording:
            self.logger.warning("Already recording.")
            return False
        if self.model_error_reported:
             self.logger.error("Cannot start recording due to model error.")
             self.ui.update_status_text("Fix model error in settings first!")
             return False

        with self.state_lock:
            self.recording = True
            self.session_start_time = time.time()
            self.accumulated_audio_data.clear() # Clear session buffer
            self.continuous_segments.clear() # Clear continuous results
            self.last_continuous_text = "" # Clear UI text tracker
            
            # Worker always receives audio, its internal logic handles mode differences
            if self.worker and self.worker.start():
                 self.recorder.on_data_callback = self.worker.add_audio
                 self.logger.info(f"Starting recording (Mode: {self.current_mode}). Worker started.")
            else:
                 self.logger.error("Failed to start TranscriptionWorker.")
                 self.recording = False
                 self.session_start_time = None
                 self.ui.update_status_text("Error: Worker failed")
                 return False

            # Start the actual audio recorder
            if not self.recorder.start():
                self.logger.error("Audio recorder failed to start.")
                if self.worker:
                    self.worker.stop() # Stop worker if recorder failed
                self.recording = False
                self.session_start_time = None
                self.ui.update_status_text("Error: Audio input failed")
                return False
                
            # Update UI
            self.ui.update_status(True) # Show recording active
            self.ui.update_text("") # Clear text area
            self.ui.update_status_text(f"Recording ({self.current_mode})...") 
            
            return True

    def stop_recording(self) -> None:
        """Stop recording and start background thread for final processing."""
        if not self.recording:
            self.logger.debug("Stop recording called but not recording.")
            return

        with self.state_lock:
            self.logger.info(f"Stopping recording (Mode: {self.current_mode}). Starting final processing...")
            self.recording = False # Prevent more callbacks
            
            # Stop the audio source - THIS NOW BLOCKS UNTIL RECORDER THREAD IS DONE
            # It returns the full audio buffer captured during the session.
            full_audio_data = self.recorder.stop() 
            if not full_audio_data:
                 self.logger.warning("No audio data captured by recorder, skipping final processing.")
                 # Update UI directly since there's nothing to process
                 self.ui.update_status(False) 
                 self.ui.update_status_text("Ready (No audio captured)")
                 self.ui.update_status_color("ready") # Explicitly set ready color
                 return
            
            # Signal worker to stop processing its queue and finish
            if self.worker:
                self.worker.stop() 
                 
            # --- UI Update: Processing --- Immediately update UI
            self.ui.update_status(False) 
            self.ui.update_status_text("Processing...")
            # --- End UI Update ---

            # --- Start Background Thread for Final Transcription/Saving --- 
            mode_at_stop = self.current_mode # Capture mode state
            processing_thread = threading.Thread(
                target=self._finalize_session_processing,
                args=(full_audio_data, mode_at_stop),
                daemon=True # Allow app to exit even if this thread hangs
            )
            processing_thread.start()
            self.logger.debug(f"Started background thread for final processing (Mode: {mode_at_stop}).")
            # --- End Background Thread --- 

        # Return immediately, freeing the UI thread
        self.logger.debug("stop_recording method finished, final processing running in background.")

    # --- NEW: Method runs in background thread --- 
    def _finalize_session_processing(self, full_audio_data: bytes, mode: OperatingMode) -> None:
        """Performs final transcription/segment processing and saving in a background thread."""
        self.logger.info(f"Background thread: Starting final processing for mode '{mode}'. Audio size: {len(full_audio_data)} bytes")
        saved_paths: Optional[Dict] = None
        final_text: str = ""
        error_message: Optional[str] = None
        
        try:
            if not self.session_start_time:
                 raise ValueError("Session start time not set.")
                 
            session_dt = datetime.fromtimestamp(self.session_start_time)
            base_path = self.transcript_manager._get_session_base_path(session_dt)
            target_wav_path = str(base_path.with_suffix(".wav"))
            
            # --- Prepare Session Data --- 
            session_end_time = time.time()
            session_duration = session_end_time - self.session_start_time
            
            session_data = {
                "session_id": base_path.name,
                "date": session_dt.strftime("%Y-%m-%d"),
                "start_time_str": session_dt.strftime("%H:%M:%S"),
                "start_timestamp_unix": self.session_start_time,
                "end_timestamp_unix": session_end_time,
                "total_duration_sec": round(session_duration, 2),
                "mode": mode,
                "language": self.config.transcription.language, # Start with config language
                "model_info": self.transcriber.get_model_info(),
                "full_text": "", # Will be populated below
                "segments": [], # Will be populated below
                "json_path": None, # Will be added by save_session
                "wav_path": None,
            }
            # --- End Prepare Session Data --- 

            if mode == "session":
                self.logger.info("Background thread: Transcribing full audio for session mode...")
                # Transcribe FULL audio (Engine saves WAV if path provided)
                transcription_result: TranscriptionResult = self.transcriber.transcribe(
                    audio=full_audio_data, 
                    target_wav_path=target_wav_path 
                )
                final_text = transcription_result.get("text", "")
                session_data["full_text"] = final_text
                session_data["segments"] = transcription_result.get("segments", [])
                session_data["language"] = transcription_result.get("language", session_data["language"])
                self.logger.info("Background thread: Session mode transcription complete.")
                
            elif mode == "continuous":
                self.logger.info("Background thread: Using collected segments for continuous mode saving.")
                # Use collected segments, save the full WAV separately
                with self.state_lock: # Access segments safely
                     session_data["segments"] = list(self.continuous_segments) # Take a copy
                # Reconstruct full text from segments
                final_text = " ".join([seg.get('text', '') for seg in session_data["segments"]]).strip()
                session_data["full_text"] = final_text
                # Language was determined by chunks, might be less accurate than full pass
                # Keep language from config or maybe last chunk? For now, use config.
                
                # Save the WAV file separately since transcribe wasn't called with path
                try:
                     self.transcript_manager.save_wav(target_wav_path, full_audio_data)
                     self.logger.info(f"Background thread: Continuous mode WAV saved to {target_wav_path}")
                except Exception as wav_e:
                     self.logger.error(f"Background thread: Failed to save continuous mode WAV file: {wav_e}", exc_info=True)
                     # Proceed to save JSON anyway?

            # --- Save Session JSON --- 
            saved_paths = self.transcript_manager.save_session(session_data)
            self.logger.info("Background thread: Session data saved.")

        except (ModelError, IOError, ValueError) as e:
            self.logger.error(f"Background thread: Error during final processing: {e}", exc_info=True)
            error_message = f"Error processing/saving: {e}"
        except Exception as e:
            self.logger.error(f"Background thread: Unexpected error during final processing: {e}", exc_info=True)
            error_message = f"Unexpected Error: {e}"
        finally:
            # --- Schedule UI Update on Main Thread --- 
            # final_text and saved_paths are captured from the try block
            # If error occurred, final_text might be empty, saved_paths None
            if error_message:
                 # Pass error message instead of saved_paths
                 self.ui.window.after(0, lambda: self._update_ui_post_save(final_text, None, error_message))
            else:
                 self.ui.window.after(0, lambda: self._update_ui_post_save(final_text, saved_paths))
            self.logger.debug("Background thread: Scheduled final UI update.")
            # --- End Schedule UI Update --- 

    # --- NEW: Method runs in UI thread via root.after --- 
    def _update_ui_post_save(self, final_text: str, saved_paths: Optional[Dict], error_message: Optional[str] = None) -> None:
        """Updates the UI after background processing is complete."""
        self.logger.info(f"UI thread: Updating UI post-processing. Error: {error_message}")
        with self.state_lock:
             # Check if final text differs from incrementally built text
             # Strip both for comparison to avoid issues with trailing spaces
             if final_text.strip() != self.last_continuous_text.strip():
                 self.logger.debug("Final text differs from incremental, updating UI text.")
                 self.ui.update_text(final_text)
             else:
                 self.logger.debug("Final text matches incremental, skipping redundant UI text update.")
             # Always update the tracker to the definitive final text
             self.last_continuous_text = final_text
             self.ui.update_word_count(len(final_text.split()))

             # Update status message
             if error_message:
                 self.ui.update_status_text(error_message)
                 self.ui.update_status_color("error")
             elif saved_paths:
                 json_basename = os.path.basename(saved_paths.get('json', 'N/A'))
                 self.ui.update_status_text(f"Saved: {json_basename}")
                 self.ui.update_status_color("ready")
                 # Optionally handle clipboard copy after successful save
                 # self._handle_clipboard_copy()
             else:
                 self.ui.update_status_text("Error saving session files!")
                 self.ui.update_status_color("error")
             
             # Reset session start time (safe to do here after all processing)
             self.session_start_time = None
             # start_recording handles clearing buffers/segments for the *next* session.

    def _on_continuous_result(self, result: TranscriptionResult) -> None:
        """Handle INTERMEDIATE transcription result from the worker in Continuous Mode."""
        with self.state_lock:
            if not self.recording or self.current_mode != "continuous":
                self.logger.debug("Ignoring continuous result (not recording or not continuous mode).")
                return
        
        new_text_chunk = result.get("text", "").strip()
        if not new_text_chunk or new_text_chunk == ".":
            return # Ignore empty or noise results
            
        self.logger.debug(f"Received continuous transcription chunk: '{new_text_chunk[:50]}...'" )
        
        # --- Store Processed Segments --- 
        if not self.session_start_time:
             self.logger.error("Cannot process segments, session_start_time is not set.")
             return
             
        processed_segments = []
        for segment in result.get("segments", []):
             try:
                 abs_start_time = self.session_start_time + float(segment['start'])
                 abs_end_time = self.session_start_time + float(segment['end'])
                 processed_segments.append({
                     "text": segment.get('text', '').strip(),
                     "start_time_unix": abs_start_time,
                     "end_time_unix": abs_end_time,
                     "start_str": datetime.fromtimestamp(abs_start_time).strftime("%H:%M:%S.%f")[:-3],
                     "end_str": datetime.fromtimestamp(abs_end_time).strftime("%H:%M:%S.%f")[:-3],
                 })
             except (KeyError, ValueError, TypeError) as e:
                 self.logger.warning(f"Skipping segment due to missing/invalid data: {segment}. Error: {e}")
                 
        with self.state_lock:
            self.continuous_segments.extend(processed_segments)
            self.logger.debug(f"Added {len(processed_segments)} segments to continuous_segments list.")
        # --- End Store Segments --- 

        # --- Update UI Incrementally --- 
        with self.state_lock: 
            # Use text processor to handle appending and capitalization
            new_full_text = self.text_processor.append_text(self.last_continuous_text, new_text_chunk)
            self.last_continuous_text = new_full_text
            
            # Update UI
            self.ui.update_text(self.last_continuous_text) 
            self.ui.update_word_count(len(self.last_continuous_text.split()))
            
        # --- VAD/Silence checks are handled by the Worker --- 

    def _on_transcription_result(self, text: str, duration: float) -> None:
        """Handle transcription result FROM THE WORKER during continuous mode."""
        # --- Check if stopping --- 
        # Immediately return if stop_recording has already set self.recording to False
        # This prevents processing results that arrive after the stop sequence began.
        with self.state_lock:
            if not self.recording:
                self.logger.debug("Ignoring transcription result received after stop signal.")
                return
        # --- End Check --- 

        # If not in continuous mode, this callback should ideally not be triggered by the worker,
        # but we add a check just in case.
        if self.current_mode != "continuous":
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

        # --- Store Metadata (Simplified for now) ---
        # We are temporarily saving the *full* session at the end, 
        # so detailed chunk metadata isn't strictly needed for the final JSON yet.
        # But we need to update the UI. Let's just append the text for now.
        # TODO: Store segments properly when continuous mode saving is implemented.
        # segment_metadata = { "text": cleaned_chunk, "timestamp": time.time() }
        # self.continuous_segments.append(segment_metadata)
        # --- End Metadata Storage ---

        # --- Update UI Incrementally --- 
        with self.state_lock: 
            # Append new chunk to the last known text for UI update
            # Use text processor to handle potential overlaps or spacing
            new_full_text = self.text_processor.append_text(self.last_continuous_text, text)
            self.last_continuous_text = new_full_text
            
            # Update UI via queue or direct call if safe
            # For simplicity, assuming direct UI update might be okay for text
            self.ui.update_text(self.last_continuous_text) 
            self.ui.update_word_count(len(self.last_continuous_text.split()))
            
        # --- Check for auto-stop (natural pause) ---
        # Use worker's check for recent audio
        if self.worker and not self.worker.has_recent_audio():
             self.logger.info("Natural pause detected in continuous mode, stopping recording.")
             # Need to run stop_recording on the main thread if it accesses UI directly
             # Or make stop_recording fully thread-safe / use queue for UI parts
             # For now, assuming stop_recording can be called (but beware UI access)
             self.stop_recording() 
             # TODO: Ensure thread safety if calling stop_recording from here
        
    def _handle_clipboard_copy(self) -> None:
        """Handle copying text to clipboard and updating UI."""
        text_to_copy = self.last_continuous_text # Use the final displayed text
        if text_to_copy:
            if copy_to_clipboard(text_to_copy):
                self.logger.info("Transcript copied to clipboard")
                self.ui.update_status_text("Copied to clipboard!")
            else:
                self.logger.warning("Failed to copy transcript to clipboard")
                self.ui.update_status_text("Copy to clipboard failed")
        else:
             self.logger.info("No text to copy to clipboard")

    def save_transcript(self) -> None: # TODO: Maybe remove this? Automatic saving is primary.
        """Explicitly save the current transcript (if applicable)."""
        # This might be redundant now, as saving happens on stop.
        messagebox.showinfo("Save", "Transcript is automatically saved when recording stops.")

    def clear_transcript(self) -> None:
        """Handle clear transcript request from UI."""
        with self.state_lock:
            self.last_continuous_text = "" # Clear displayed text tracker
            # We don't clear saved files, just the UI state
            
        # Update UI to reflect cleared state
        self.ui.update_text("")
        self.ui.update_word_count(0)
        self.logger.info("Transcript cleared from UI")
    
    def run(self) -> None:
        """Run the service UI main loop."""
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
        self.logger.info("Cleaning up Voice Input Service resources...")
        # Check if state_lock was initialized before using it
        if hasattr(self, 'state_lock'):
            with self.state_lock:
                if self.recording:
                     self.stop_recording() # Ensure recording is stopped cleanly
 
                if self.worker:
                     self.worker.close()
                     
                # --- Recorder cleanup handled by stop_recording / __del__ --- 
                # if hasattr(self, 'recorder') and self.recorder:
                #     self.recorder.close() # REMOVED - No close method
                 
                # Close other components if they implement Closeable
                if isinstance(self.transcriber, Closeable):
                     self.transcriber.close()
                     
                if self.event_manager:
                     if hasattr(self.event_manager, 'close'):
                         self.event_manager.close()
                     elif hasattr(self.event_manager, 'stop_listening'):
                         self.event_manager.stop_listening()
        else:
            # Log if lock is missing, indicating partial initialization
            if hasattr(self, 'logger'): # Check if logger exists too
                self.logger.warning("Cleanup called on partially initialized service (state_lock missing).")
            # Attempt cleanup of other resources even if lock is missing
            try:
                if hasattr(self, 'worker') and self.worker:
                    self.worker.close()
                # --- Recorder cleanup handled by stop_recording / __del__ --- 
                # if hasattr(self, 'recorder') and self.recorder:
                #    self.recorder.close() # REMOVED - No close method
                if hasattr(self, 'transcriber') and isinstance(self.transcriber, Closeable):
                    self.transcriber.close()
                if hasattr(self, 'event_manager') and self.event_manager:
                     if hasattr(self.event_manager, 'close'):
                         self.event_manager.close()
                     elif hasattr(self.event_manager, 'stop_listening'):
                         self.event_manager.stop_listening()
            except Exception as e:
                # Log potential errors during this best-effort cleanup
                if hasattr(self, 'logger'):
                    self.logger.error(f"Error during fallback cleanup: {e}", exc_info=False)

        # --- Logger might not exist if init failed early --- 
        # self.logger.info("Cleanup complete.")
        # Log only if logger was initialized
        if hasattr(self, 'logger'):
            self.logger.info("Cleanup attempt finished.")

    def close(self) -> None:
        """Public method to clean up resources."""
        self._cleanup()
        
    def __del__(self) -> None:
        """Ensure cleanup on object deletion."""
        # Note: Relying on __del__ can be problematic. Explicit close() is preferred.
        self._cleanup()

    def _on_settings_changed(self) -> None:
        """Handle changes to application settings."""
        self.logger.info("Settings updated - applying changes")
        
        # Check if we need to reinitialize the transcription engine (major changes)
        # Simplified check: Assume engine needs re-init for now on any setting change
        # TODO: Implement more granular checks (model change, cpp path change etc.)
        needs_engine_restart = True 
        
        if needs_engine_restart:
             self.logger.warning("Settings change requires transcription engine restart. (Not fully implemented)")
             # Ideally, we'd re-initialize self.transcriber here
             # Re-verify model after re-init
             # self.transcriber = TranscriptionEngine(...) 
             # self._verify_transcription_model()
             self.ui.update_status_text("Settings changed (Restart app?)") # Placeholder message
        else:
            # For minor changes like VAD threshold, update components directly
             if self.worker:
                  self.worker.update_settings() # Worker reads config for VAD threshold etc.
             self.logger.info("Applied non-critical settings changes.")
             self.ui.update_status_text("Settings updated.")
