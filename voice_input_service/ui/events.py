from __future__ import annotations
import keyboard
import pyperclip
import logging
from typing import Protocol, Callable

class EventHandler(Protocol):
    """Protocol for event handlers."""
    def start_recording(self) -> bool: ...
    def stop_recording(self) -> str: ...
    def save_transcript(self) -> None: ...
    def clear_transcript(self) -> None: ...

class KeyboardEventManager:
    """Manages keyboard event handling."""
    
    def __init__(self, handler: EventHandler) -> None:
        self.logger = logging.getLogger("VoiceService.Events")
        self.handler = handler
        self.recording = False
        self.continuous_mode = False
        self.hotkeys = []
        
    def setup_hotkeys(self) -> None:
        """Setup hotkeys for controlling the service."""
        self.logger.debug("Setting up keyboard hotkeys")

        # Clear any existing hotkeys first to prevent issues
        self.clear_hotkeys()
        
        # Define hotkeys for different actions - store references to remove later
        # Use add_hotkey with explicit callback wrappers to ensure proper removal
        self.hotkeys = [
            keyboard.add_hotkey('alt+r', lambda: self._toggle_recording(), suppress=True),
            keyboard.add_hotkey('alt+s', lambda: self._save_transcript(), suppress=True),
            keyboard.add_hotkey('alt+c', lambda: self._clear_transcript(), suppress=True),
        ]
        
        self.logger.info("Keyboard hotkeys configured")
    
    def clear_hotkeys(self) -> None:
        """Clear all registered hotkeys to prevent conflicts."""
        try:
            # First remove our specific hotkeys if we have any
            for hotkey in self.hotkeys:
                keyboard.remove_hotkey(hotkey)
            self.hotkeys = []
            
            # As a safety measure, unhook any remaining hotkeys
            keyboard.unhook_all()
            self.logger.debug("All keyboard hotkeys cleared")
        except Exception as e:
            self.logger.error(f"Error clearing hotkeys: {e}")
    
    def _toggle_recording(self) -> None:
        """Handle recording toggle hotkey."""
        self.logger.debug("Recording hotkey pressed")
        if not self.recording:
            self.logger.info("Starting recording")
            if self.handler.start_recording():
                self.recording = True
                self.logger.info("Recording started successfully")
        else:
            self.logger.info("Stopping recording")
            text = self.handler.stop_recording()
            self.recording = False
            if text:
                word_count = len(text.split())
                self.logger.info(f"Transcription complete: {word_count} words")
                if not self.continuous_mode:
                    pyperclip.copy(text)
                    self.logger.info("Text copied to clipboard")
            else:
                self.logger.warning("No speech detected")
    
    def _save_transcript(self) -> None:
        """Handle save request hotkey."""
        self.logger.debug("Save hotkey pressed")
        self.logger.info("Save transcript requested")
        self.handler.save_transcript()
    
    def _clear_transcript(self) -> None:
        """Handle clear request hotkey."""
        self.logger.debug("Clear hotkey pressed")
        self.logger.info("Clear transcript requested")
        self.handler.clear_transcript()
    
    # Alias methods to match test expectations
    _on_toggle_recording_hotkey = _toggle_recording
    _on_save_hotkey = _save_transcript
    _on_clear_hotkey = _clear_transcript
    
    def __del__(self) -> None:
        """Clean up event handlers."""
        self.clear_hotkeys()  # Use our safer method 