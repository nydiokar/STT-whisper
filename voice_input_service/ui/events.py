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
        
    def setup_hotkeys(self) -> None:
        """Setup keyboard event handlers."""
        self.logger.debug("Setting up keyboard event handlers")
        keyboard.add_hotkey('alt+r', self._on_toggle_recording_hotkey, suppress=True)
        keyboard.add_hotkey('alt+s', self._on_save_hotkey, suppress=True)
        keyboard.add_hotkey('alt+c', self._on_clear_hotkey, suppress=True)
        self.logger.debug("Event handlers setup complete")
    
    def _on_toggle_recording_hotkey(self) -> None:
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
                    keyboard.press_and_release('ctrl+v')
            else:
                self.logger.warning("No speech detected")
    
    def _on_save_hotkey(self) -> None:
        """Handle save request hotkey."""
        self.logger.debug("Save hotkey pressed")
        self.logger.info("Save transcript requested")
        self.handler.save_transcript()
    
    def _on_clear_hotkey(self) -> None:
        """Handle clear request hotkey."""
        self.logger.debug("Clear hotkey pressed")
        self.logger.info("Clear transcript requested")
        self.handler.clear_transcript()
    
    def __del__(self) -> None:
        """Clean up event handlers."""
        keyboard.unhook_all() 