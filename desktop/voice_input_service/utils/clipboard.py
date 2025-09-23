"""Clipboard handling utilities."""
import logging
import pyperclip
from typing import Optional

logger = logging.getLogger("VoiceService.Clipboard")

def copy_to_clipboard(text: str) -> bool:
    """Copy text to system clipboard.
    
    Args:
        text: Text to copy to clipboard
        
    Returns:
        True if successful, False otherwise
    """
    if not text:
        logger.warning("Attempted to copy empty text to clipboard")
        return False
        
    try:
        pyperclip.copy(text)
        logger.info(f"Copied {len(text)} characters to clipboard")
        return True
    except Exception as e:
        logger.error(f"Failed to copy to clipboard: {e}")
        return False 