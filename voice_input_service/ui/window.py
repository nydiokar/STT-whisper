from __future__ import annotations
import tkinter as tk
from tkinter import ttk
import logging

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
        
        continuous_cb = ttk.Checkbutton(
            controls_frame, 
            text="Continuous Mode",
            variable=self.continuous_var
        )
        continuous_cb.pack(side=tk.LEFT, padx=5)
        
        language_label = ttk.Label(controls_frame, text="Language:")
        language_label.pack(side=tk.LEFT, padx=5)
        
        language_combo = ttk.Combobox(
            controls_frame,
            textvariable=self.language_var,
            values=['en', 'es', 'fr', 'de', 'it', 'pt', 'nl', 'pl', 'ru', 'zh', 'ja'],
            width=5
        )
        language_combo.pack(side=tk.LEFT, padx=5)
        
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
    
    def _show_settings(self) -> None:
        """Show the settings dialog."""
        # We'll pass the config in service.py when we connect this button
        if hasattr(self, 'config'):
            dialog = SettingsDialog(self.window, self.config, self.on_settings_changed)
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
            # Animate the recording text instead of showing seconds
            self.recording_animation_state = (self.recording_animation_state + 1) % len(self.recording_animation_frames)
            animation_text = self.recording_animation_frames[self.recording_animation_state]
            
            status = f"{animation_text} {'(Continuous)' if continuous else ''}"
            color = "recording"
            
            # Only log if starting to record (transition from not recording)
            if self.current_status != "recording":
                self.logger.info(f"Status: {status}")
                self.current_status = "recording"
                
            self.status_label.config(text=status)
            self.update_status_color(color)
        else:
            status = "Ready"
            color = "ready"
            
            # Only log if status changed from previous state
            if self.current_status != "ready":
                self.logger.info(f"Status: {status}")
                self.current_status = "ready"
                
            self.status_label.config(text=status)
            self.update_status_color(color)
    
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
        """Update the status text.
        
        Args:
            text: Status text to display
        """
        self.status_label.config(text=text)
        self.current_status = text
        self.logger.debug(f"Status text updated: {text}")
    
    def run(self) -> None:
        """Start the UI event loop."""
        self.window.mainloop()
    
    def __del__(self) -> None:
        """Clean up UI resources."""
        if hasattr(self, 'window'):
            self.window.destroy() 