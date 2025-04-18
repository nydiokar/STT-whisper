from __future__ import annotations
import tkinter as tk
from tkinter import ttk
import logging

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
        
        self._setup_ui()
        self.logger.info("UI initialized")
        
    def _setup_ui(self) -> None:
        """Setup the UI components."""
        self.logger.debug("Setting up UI components")
        # Status frame
        status_frame = ttk.LabelFrame(self.window, text="Status", padding="5")
        status_frame.pack(fill=tk.X, padx=5, pady=5)
        
        self.status_label: ttk.Label = ttk.Label(status_frame, text="Ready")
        self.status_label.pack(side=tk.LEFT, padx=5)
        
        self.word_count_label = ttk.Label(status_frame, text="Words: 0")
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
        
        # Text display
        text_frame = ttk.LabelFrame(self.window, text="Transcription", padding="5")
        text_frame.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)
        
        self.text_display = tk.Text(text_frame, wrap=tk.WORD, height=10)
        self.text_display.pack(fill=tk.BOTH, expand=True)
        
        # Buttons frame
        buttons_frame = ttk.Frame(self.window, padding="5")
        buttons_frame.pack(fill=tk.X, padx=5, pady=5)
        
        save_button = ttk.Button(buttons_frame, text="Save Transcript")
        save_button.pack(side=tk.LEFT, padx=5)
        
        clear_button = ttk.Button(buttons_frame, text="Clear")
        clear_button.pack(side=tk.LEFT, padx=5)
    
    def update_status(self, is_recording: bool, elapsed: float = 0, continuous: bool = False) -> None:
        """Update the status display."""
        status = "Ready"
        if is_recording:
            status = f"Recording {'(Continuous) ' if continuous else ''}- {int(elapsed)}s"
        self.status_label.config(text=status)
        self.logger.debug(f"Status updated: {status}")
    
    def update_word_count(self, count: int) -> None:
        """Update the word count display."""
        self.word_count_label.config(text=f"Words: {count}")
        self.logger.debug(f"Word count updated: {count}")
    
    def update_text(self, text: str) -> None:
        """Update the text display."""
        self.text_display.delete('1.0', tk.END)
        self.text_display.insert('1.0', text)
        self.logger.debug(f"Text display updated ({len(text)} chars)")
    
    def run(self) -> None:
        """Start the UI event loop."""
        self.window.mainloop()
    
    def __del__(self) -> None:
        """Clean up UI resources."""
        if hasattr(self, 'window'):
            self.window.destroy() 