from __future__ import annotations
import os
from datetime import datetime
import logging
from pathlib import Path
from typing import Optional

class TranscriptManager:
    """Manages transcript file operations."""
    
    def __init__(self, base_dir: Optional[str] = None) -> None:
        """Initialize the transcript manager.
        
        Args:
            base_dir: Base directory for transcript storage. If None, use default location.
        """
        self.logger = logging.getLogger("voice_input_service.utils.file_ops")
        
        # Set up base directory for transcripts
        if base_dir:
            self.base_dir = Path(base_dir)
        else:
            self.base_dir = Path.home() / "Documents" / "Voice Transcripts"
        
        # Create directory if it doesn't exist
        os.makedirs(self.base_dir, exist_ok=True)
        self.logger.info(f"Transcript directory: {self.base_dir}")
        
        # Add output_dir alias for test compatibility
        self.output_dir = self.base_dir
    
    def get_transcript_path(self, prefix: str = "transcript") -> Path:
        """Generate a path for a new transcript file.
        
        Args:
            prefix: Prefix for the filename.
            
        Returns:
            Path object for the new transcript file.
        """
        timestamp = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
        filename = f"{prefix}_{timestamp}.txt"
        return self.base_dir / filename
    
    def save_transcript(self, text: str, prefix: str = "transcript") -> Optional[str]:
        """Save transcript text to a file.
        
        Args:
            text: Transcript text to save.
            prefix: Prefix for the filename.
            
        Returns:
            Path to the saved file as a string, or None if save failed.
        """
        if not text:
            self.logger.warning("No text to save")
            return None
        
        try:
            file_path = self.get_transcript_path(prefix)
            with open(file_path, "w", encoding="utf-8") as f:
                f.write(text)
                
            return str(file_path)
        except Exception as e:
            self.logger.error(f"Error saving transcript: {e}")
            return None
    
    def get_transcript_files(self) -> list[str]:
        """List all transcript files sorted by creation time.
        
        Returns:
            List of transcript file paths sorted by creation time.
        """
        try:
            files = [str(file) for file in self.base_dir.glob("*.txt")]
            # Sort files by creation time
            return sorted(files)
        except Exception as e:
            self.logger.error(f"Error listing transcripts: {e}")
            return []
        
    def read_transcript(self, file_path: str) -> Optional[str]:
        """Read transcript from a file.
        
        Args:
            file_path: Path to the transcript file.
            
        Returns:
            Transcript text, or None if read failed.
        """
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                return f.read()
        except Exception as e:
            self.logger.error(f"Error reading transcript: {e}")
            return None 