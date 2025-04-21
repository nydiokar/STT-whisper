from __future__ import annotations
import tkinter as tk
from tkinter import ttk, messagebox
import logging
import time
from typing import Callable, Dict, Any, Optional

from voice_input_service.ui.dialogs import SettingsDialog

class TranscriptionUI:
    """Handles the user interface for transcription."""
    
    def __init__(self) -> None:
        self.logger = logging.getLogger("VoiceService.UI")
        self.window: tk.Tk = tk.Tk()
        self.window.title("Voice Transcription Service")
        self.window.geometry("600x400")
        
        # UI state
        self.continuous_var: tk.BooleanVar = tk.BooleanVar()
        self.language_var: tk.StringVar = tk.StringVar(value='en')
        self.recording_animation_state = 0
        self.recording_animation_frames = ["Recording", "Recording.", "Recording..", "Recording..."]
        
        # Colors for status indication
        self.status_colors = {
            "ready": "#f0f0f0",     # Light gray
            "recording": "#ffcccc",  # Light red
            "processing": "#ccffcc", # Light green
            "error": "#ffaaaa"       # Darker red
        }
        
        # Track current status to avoid duplicate logs
        self.current_status = ""
        
        # Callback for settings changes
        self.on_settings_changed = None
        
        # Callback handlers
        self.continuous_mode_handler = None
        self.language_handler = None
        
        # Setup animation timer
        self.animation_after_id = None
        
        self._setup_ui()
        self.logger.info("UI initialized")
        
    def _setup_ui(self) -> None:
        """Setup the UI components."""
        self.logger.debug("Setting up UI components")
        # Status frame
        self.status_frame = ttk.LabelFrame(self.window, text="Status", padding="5")
        self.status_frame.pack(fill=tk.X, padx=5, pady=5)
        
        self.status_label: ttk.Label = ttk.Label(self.status_frame, text="Ready")
        self.status_label.pack(side=tk.LEFT, padx=5)
        
        self.word_count_label = ttk.Label(self.status_frame, text="Words: 0")
        self.word_count_label.pack(side=tk.RIGHT, padx=5)
        
        # Controls frame
        controls_frame = ttk.Frame(self.window, padding="5")
        controls_frame.pack(fill=tk.X, padx=5, pady=5)
        
        # Connect continuous mode checkbox to callback
        continuous_cb = ttk.Checkbutton(
            controls_frame, 
            text="Continuous Mode",
            variable=self.continuous_var,
            command=self._on_continuous_changed
        )
        continuous_cb.pack(side=tk.LEFT, padx=5)
        
        language_label = ttk.Label(controls_frame, text="Language:")
        language_label.pack(side=tk.LEFT, padx=5)
        
        # Connect language combobox to callback
        language_combo = ttk.Combobox(
            controls_frame,
            textvariable=self.language_var,
            values=['en', 'es', 'fr', 'de', 'it', 'pt', 'nl', 'pl', 'ru', 'zh', 'ja'],
            width=5
        )
        language_combo.pack(side=tk.LEFT, padx=5)
        language_combo.bind("<<ComboboxSelected>>", self._on_language_changed)
        
        # Settings button
        settings_button = ttk.Button(controls_frame, text="Settings", command=self._show_settings)
        settings_button.pack(side=tk.RIGHT, padx=5)
        
        # Text display with improved styling
        text_frame = ttk.LabelFrame(self.window, text="Transcription", padding="5")
        text_frame.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)
        
        # Add scrollbar
        scrollbar = ttk.Scrollbar(text_frame)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        
        self.text_display = tk.Text(text_frame, wrap=tk.WORD, height=10, 
                                   yscrollcommand=scrollbar.set)
        self.text_display.pack(fill=tk.BOTH, expand=True)
        scrollbar.config(command=self.text_display.yview)
        
        # Configure text display for highlighting
        self.text_display.tag_configure("highlight", background="#e0f0ff")
        
        # Buttons frame
        buttons_frame = ttk.Frame(self.window, padding="5")
        buttons_frame.pack(fill=tk.X, padx=5, pady=5)
        
        save_button = ttk.Button(buttons_frame, text="Save Transcript")
        save_button.pack(side=tk.LEFT, padx=5)
        
        clear_button = ttk.Button(buttons_frame, text="Clear")
        clear_button.pack(side=tk.LEFT, padx=5)
        
        # Keyboard shortcuts frame
        shortcut_frame = ttk.LabelFrame(self.window, text="Keyboard Shortcuts", padding="5")
        shortcut_frame.pack(fill=tk.X, padx=5, pady=5)
        
        shortcuts_text = "Alt+R: Start/Stop Recording | Alt+S: Save | Alt+C: Clear"
        shortcut_label = ttk.Label(shortcut_frame, text=shortcuts_text, justify=tk.CENTER)
        shortcut_label.pack(fill=tk.X)
    
    def _start_recording_animation(self) -> None:
        """Start the recording animation."""
        if self.animation_after_id:
            self.window.after_cancel(self.animation_after_id)
            
        def update_animation():
            self.recording_animation_state = (self.recording_animation_state + 1) % len(self.recording_animation_frames)
            animation_text = self.recording_animation_frames[self.recording_animation_state]
            
            status = f"{animation_text} {'(Continuous)' if self.continuous_var.get() else ''}"
            self.status_label.config(text=status)
            
            # Schedule next update
            self.animation_after_id = self.window.after(500, update_animation)
            
        update_animation()
    
    def _stop_recording_animation(self) -> None:
        """Stop the recording animation."""
        if self.animation_after_id:
            self.window.after_cancel(self.animation_after_id)
            self.animation_after_id = None
            
    def _on_continuous_changed(self) -> None:
        """Handle continuous mode checkbox changes."""
        if self.continuous_mode_handler:
            self.continuous_mode_handler(self.continuous_var.get())
    
    def _on_language_changed(self, event=None) -> None:
        """Handle language combobox changes."""
        if self.language_handler:
            self.language_handler(self.language_var.get())
    
    def set_continuous_mode_handler(self, handler: Callable[[bool], None]) -> None:
        """Set handler for continuous mode changes.
        
        Args:
            handler: Function to call when continuous mode changes
        """
        self.continuous_mode_handler = handler
    
    def set_language_handler(self, handler: Callable[[str], None]) -> None:
        """Set handler for language changes.
        
        Args:
            handler: Function to call when language changes
        """
        self.language_handler = handler
    
    def show_restart_required(self, change_type: str) -> None:
        """Show restart required message.
        
        Args:
            change_type: Type of change that requires restart
        """
        message = f"{change_type.capitalize()} changed - application restart required for changes to take effect"
        self.update_status_text(message)
        self.update_status_color("error")
        
        # Show a proper dialog message box
        messagebox.showinfo(
            "Restart Required",
            f"The {change_type} has been changed. This change requires restarting the application to take effect.\n\n"
            "Please close and restart the application for the changes to be applied."
        )
        
        # Reset status after a delay
        self.window.after(2000, lambda: self.update_status_color("ready"))
    
    def show_language_error(self, error_message: str) -> None:
        """Show language error message.
        
        Args:
            error_message: Error message to show
        """
        self.update_status_text(f"Language error: {error_message}")
        self.update_status_color("error")
        
        # Reset language to current config value
        if hasattr(self, 'config'):
            self.language_var.set(self.config.transcription.language)
    
    def _setup_vad_settings(self) -> None:
        """Set up VAD settings in the UI.
        
        This should be called when creating the settings dialog.
        """
        if not hasattr(self, 'config') or not hasattr(self, 'settings_window'):
            self.logger.error("Cannot set up VAD settings: config or settings window not set")
            return
            
        settings = self.settings_window
        
        # Create VAD frame
        settings.vad_frame = tk.LabelFrame(settings.root, text="Voice Activity Detection")
        settings.vad_frame.pack(fill="x", padx=10, pady=5)
        
        # VAD mode
        settings.vad_mode_var = tk.StringVar(value=self.config.get("audio.vad_mode", "silero"))
        vad_mode_frame = tk.Frame(settings.vad_frame)
        vad_mode_frame.pack(fill="x", padx=5, pady=5)
        
        tk.Label(vad_mode_frame, text="VAD Mode:").pack(side="left")
        vad_modes = ["basic", "webrtc", "silero"]
        settings.vad_mode_menu = tk.OptionMenu(
            vad_mode_frame, 
            settings.vad_mode_var, 
            *vad_modes
        )
        settings.vad_mode_menu.pack(side="right")
        
        # VAD aggressiveness (for WebRTC)
        settings.vad_aggressiveness_var = tk.IntVar(value=self.config.get("audio.vad_aggressiveness", 3))
        vad_agg_frame = tk.Frame(settings.vad_frame)
        vad_agg_frame.pack(fill="x", padx=5, pady=5)
        
        tk.Label(vad_agg_frame, text="WebRTC Aggressiveness:").pack(side="left")
        settings.vad_agg_scale = tk.Scale(
            vad_agg_frame,
            from_=0,
            to=3,
            orient="horizontal",
            variable=settings.vad_aggressiveness_var
        )
        settings.vad_agg_scale.pack(side="right", fill="x", expand=True)
        
        # VAD threshold (for Silero)
        settings.vad_threshold_var = tk.DoubleVar(value=self.config.get("audio.vad_threshold", 0.5))
        vad_threshold_frame = tk.Frame(settings.vad_frame)
        vad_threshold_frame.pack(fill="x", padx=5, pady=5)
        
        tk.Label(vad_threshold_frame, text="Silero Threshold:").pack(side="left")
        settings.vad_threshold_scale = tk.Scale(
            vad_threshold_frame,
            from_=0.1,
            to=0.9,
            resolution=0.1,
            orient="horizontal",
            variable=settings.vad_threshold_var
        )
        settings.vad_threshold_scale.pack(side="right", fill="x", expand=True)
        
        # Connect settings changes to callback
        self._connect_vad_settings_callbacks(settings)
        
    def _connect_vad_settings_callbacks(self, settings) -> None:
        """Connect VAD settings callbacks."""
        def on_vad_change(*args):
            # Update config
            self.config.set("audio.vad_mode", settings.vad_mode_var.get())
            self.config.set("audio.vad_aggressiveness", settings.vad_aggressiveness_var.get())
            self.config.set("audio.vad_threshold", settings.vad_threshold_var.get())
            self.config.save()
            
            # Notify settings changed
            if self.on_settings_changed:
                self.on_settings_changed()
        
        # Attach change handlers
        settings.vad_mode_var.trace_add("write", on_vad_change)
        settings.vad_aggressiveness_var.trace_add("write", on_vad_change)
        settings.vad_threshold_var.trace_add("write", on_vad_change)
    
    def _show_settings(self) -> None:
        """Show the settings dialog."""
        # We'll pass the config in service.py when we connect this button
        if hasattr(self, 'config'):
            self.settings_window = SettingsDialog(self.window, self.config, self.on_settings_changed)
            # Add VAD settings to the dialog
            self._setup_vad_settings()
        else:
            self.logger.error("Cannot show settings: config not set")
            
    def set_config(self, config) -> None:
        """Set the configuration for this UI.
        
        Args:
            config: Application configuration
        """
        self.config = config
    
    def update_status_color(self, status: str) -> None:
        """Update the status frame color to indicate the current status."""
        if not hasattr(self, 'status_frame') or not self.status_frame.winfo_exists():
            return  # UI already destroyed or not fully initialized
            
        color = self.status_colors.get(status, self.status_colors["ready"])
        
        try:
            self.status_frame.configure(style=f"{status}.TLabelframe")
            
            # Create a style for the frame if it doesn't exist
            style = ttk.Style()
            style.configure(f"{status}.TLabelframe", background=color)
            style.configure(f"{status}.TLabelframe.Label", background=color)
            
            self.window.update_idletasks()
        except tk.TclError:
            # Widget might have been destroyed while we were processing
            self.logger.debug("Could not update status color - widget may be destroyed")
    
    def update_status(self, is_recording: bool, elapsed: float = 0, continuous: bool = False) -> None:
        """Update the status display."""
        if not hasattr(self, 'status_label') or not self.status_label.winfo_exists():
            return  # UI already destroyed or not fully initialized
            
        if is_recording:
            # Start animation for recording status
            self._start_recording_animation()
            
            # Only log if starting to record (transition from not recording)
            if self.current_status != "recording":
                self.logger.info("Status: Recording")
                self.current_status = "recording"
        else:
            # Stop animation
            self._stop_recording_animation()
            
            # Only log if status changed from previous state
            if self.current_status != "ready":
                self.logger.info("Status: Ready")
                self.current_status = "ready"
                
            self.status_label.config(text="Ready")
    
    def update_word_count(self, count: int) -> None:
        """Update the word count display."""
        if not hasattr(self, 'word_count_label') or not self.word_count_label.winfo_exists():
            return  # UI already destroyed or not fully initialized
            
        current_count = self.word_count_label.cget("text").split(": ")[1]
        if current_count != str(count):
            self.word_count_label.config(text=f"Words: {count}")
            self.logger.info(f"Word count: {count}")
        else:
            self.word_count_label.config(text=f"Words: {count}")
    
    def update_text(self, text: str, highlight_new: str = "") -> None:
        """Update the text display with optional highlighting for new text."""
        if not hasattr(self, 'text_display') or not self.text_display.winfo_exists():
            return  # UI already destroyed or not fully initialized
            
        # Get current text content to check for changes
        try:
            current_text = self.text_display.get('1.0', tk.END).strip()
            
            # Only proceed with full update if text has changed
            if current_text != text.strip():
                self.text_display.delete('1.0', tk.END)
                self.text_display.insert('1.0', text)
                
                # If there's new text to highlight, find and highlight it
                if highlight_new and highlight_new in text:
                    start_idx = text.rfind(highlight_new)
                    if start_idx >= 0:
                        # Calculate the text positions
                        start_pos = f"1.{start_idx}"
                        end_pos = f"1.{start_idx + len(highlight_new)}"
                        
                        # Apply the highlight
                        self.text_display.tag_add("highlight", start_pos, end_pos)
                        
                        # Scroll to show the highlighted text
                        self.text_display.see(end_pos)
                
                if text:  # Only log when there's actual text
                    self.logger.info(f"Text updated: {len(text)} chars")
        except tk.TclError:
            # Widget might have been destroyed while we were processing
            pass
    
    def update_status_text(self, text: str) -> None:
        """Update the status text."""
        if not hasattr(self, 'status_label') or not self.status_label.winfo_exists():
            return
            
        self.status_label.config(text=text)
        self.current_status = text
        self.logger.debug(f"Status text updated: {text}")
    
    def run(self) -> None:
        """Start the UI event loop."""
        self.window.mainloop()
    
    def __del__(self) -> None:
        """Clean up UI resources."""
        self._stop_recording_animation()
        if hasattr(self, 'window'):
            self.window.destroy() 