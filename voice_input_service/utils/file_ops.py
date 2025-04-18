from __future__ import annotations
import os
from datetime import datetime
import logging
from pathlib import Path

class TranscriptManager:
    """Manages transcript file operations."""
    
    def __init__(self, output_dir: str | None = None) -> None:
        self.logger = logging.getLogger("VoiceService.Files")
        self.output_dir = output_dir or os.path.join(
            os.path.expanduser("~"), 
            "Documents", 
            "Transcripts"
        )
        os.makedirs(self.output_dir, exist_ok=True)
        self.logger.info(f"Transcript directory: {self.output_dir}")
    
    def save_transcript(self, text: str) -> str | None:
        """Save transcript and return the file path.
        
        Args:
            text: The transcript text to save
            
        Returns:
            str | None: Path to the saved transcript file, None if empty
        """
        if not text.strip():
            self.logger.warning("Attempted to save empty transcript")
            return None
            
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = os.path.join(self.output_dir, f"transcript_{timestamp}.txt")
        
        try:
            with open(filename, 'w', encoding='utf-8') as f:
                f.write(text)
            self.logger.info(f"Transcript saved to: {filename}")
            return filename
        except Exception as e:
            self.logger.error(f"Failed to save transcript: {e}", exc_info=True)
            return None
    
    def get_transcript_files(self) -> list[str]:
        """Get list of all transcript files.
        
        Returns:
            list[str]: List of transcript file paths
        """
        try:
            return sorted([
                os.path.join(self.output_dir, f)
                for f in os.listdir(self.output_dir)
                if f.startswith("transcript_") and f.endswith(".txt")
            ])
        except Exception as e:
            self.logger.error(f"Failed to list transcripts: {e}", exc_info=True)
            return [] 