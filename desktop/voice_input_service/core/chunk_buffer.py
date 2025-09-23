from __future__ import annotations
import time
import uuid
import logging
from typing import Optional, List, Dict, Any
from datetime import datetime
import numpy as np

class AudioChunk:
    """Represents a single chunk of audio with associated metadata."""
    
    def __init__(
        self,
        audio_data: bytes,
        sample_rate: int = 16000,
        start_time: Optional[float] = None,
        chunk_id: Optional[str] = None
    ) -> None:
        """Initialize an audio chunk.
        
        Args:
            audio_data: Raw audio bytes
            sample_rate: Sample rate of the audio
            start_time: Start time of the audio chunk (as UNIX timestamp)
            chunk_id: Unique identifier for the chunk
        """
        self.audio_data = audio_data
        self.sample_rate = sample_rate
        self.start_time = start_time if start_time is not None else time.time()
        self.chunk_id = chunk_id if chunk_id is not None else self._generate_chunk_id()
        
        # Calculate duration based on audio length and sample rate
        self.duration = len(audio_data) / 2 / sample_rate  # 2 bytes per sample (int16)
        
    @property
    def timestamp(self) -> str:
        """Get formatted timestamp string for the chunk."""
        dt = datetime.fromtimestamp(self.start_time)
        return dt.strftime("%H:%M:%S")
        
    @property
    def formatted_date(self) -> str:
        """Get formatted date string for the chunk."""
        dt = datetime.fromtimestamp(self.start_time)
        return dt.strftime("%Y-%m-%d")
    
    def _generate_chunk_id(self) -> str:
        """Generate a unique ID for this chunk."""
        dt = datetime.fromtimestamp(self.start_time)
        date_str = dt.strftime("%Y-%m-%d")
        unique_id = str(uuid.uuid4())[:8]
        return f"chunk_{date_str}_{unique_id}"
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert the chunk to a dictionary representation."""
        return {
            "audio_data": self.audio_data,
            "sample_rate": self.sample_rate,
            "start_time": self.start_time,
            "timestamp": self.timestamp,
            "duration": self.duration,
            "chunk_id": self.chunk_id,
            "date": self.formatted_date
        }
    
    def to_json_compatible(self) -> Dict[str, Any]:
        """Convert the chunk to a JSON-compatible dictionary."""
        return {
            "text": None,  # Placeholder for transcription
            "start_time": self.timestamp,
            "duration": self.duration,
            "chunk_id": self.chunk_id,
            "date": self.formatted_date
        }

class AudioChunkBuffer:
    """Manages a buffer of audio chunks."""
    
    def __init__(self, max_chunks: int = 100):
        """Initialize the buffer.
        
        Args:
            max_chunks: Maximum number of chunks to keep in the buffer
        """
        self.logger = logging.getLogger("voice_input_service.core.chunk_buffer")
        self.buffer: List[AudioChunk] = []
        self.max_chunks = max_chunks
    
    def add_chunk(self, audio_data: bytes, sample_rate: int = 16000) -> AudioChunk:
        """Add a new audio chunk to the buffer.
        
        Args:
            audio_data: Raw audio bytes
            sample_rate: Sample rate of the audio
            
        Returns:
            The newly created AudioChunk
        """
        chunk = AudioChunk(audio_data, sample_rate)
        self.buffer.append(chunk)
        
        # Trim buffer if it exceeds max size
        if len(self.buffer) > self.max_chunks:
            self.buffer.pop(0)
            
        self.logger.debug(f"Added chunk {chunk.chunk_id}, duration: {chunk.duration:.2f}s")
        return chunk
    
    def get_chunk(self, chunk_id: str) -> Optional[AudioChunk]:
        """Get a specific chunk by ID.
        
        Args:
            chunk_id: ID of the chunk to retrieve
            
        Returns:
            The requested AudioChunk or None if not found
        """
        for chunk in self.buffer:
            if chunk.chunk_id == chunk_id:
                return chunk
        return None
    
    def get_latest_chunk(self) -> Optional[AudioChunk]:
        """Get the most recent chunk in the buffer.
        
        Returns:
            The most recent AudioChunk or None if buffer is empty
        """
        if not self.buffer:
            return None
        return self.buffer[-1]
    
    def get_chunks_by_date(self, date_str: str) -> List[AudioChunk]:
        """Get all chunks for a specific date.
        
        Args:
            date_str: Date string in YYYY-MM-DD format
            
        Returns:
            List of AudioChunk objects for the specified date
        """
        return [chunk for chunk in self.buffer if chunk.formatted_date == date_str]
    
    def clear(self) -> None:
        """Clear all chunks from the buffer."""
        self.buffer.clear()
        self.logger.debug("Chunk buffer cleared")
    
    def __len__(self) -> int:
        """Get the number of chunks in the buffer."""
        return len(self.buffer)

class ChunkMetadataManager:
    """Manages metadata for audio chunks after transcription."""
    
    def __init__(self):
        """Initialize the metadata manager."""
        self.logger = logging.getLogger("voice_input_service.core.chunk_metadata")
        self.metadata: Dict[str, Dict[str, Any]] = {}
    
    def add_transcription(
        self, 
        chunk_id: str, 
        text: str, 
        timestamp: str, 
        duration: float, 
        date: str
    ) -> Dict[str, Any]:
        """Add transcription result to a chunk's metadata.
        
        Args:
            chunk_id: ID of the chunk to update
            text: Transcribed text
            timestamp: Formatted timestamp string
            duration: Duration of the audio in seconds
            date: Date string in YYYY-MM-DD format
            
        Returns:
            Complete metadata for the chunk
        """
        metadata = {
            "text": text,
            "start_time": timestamp,
            "duration": duration,
            "chunk_id": chunk_id,
            "date": date
        }
        
        self.metadata[chunk_id] = metadata
        self.logger.debug(f"Added transcription for chunk {chunk_id}")
        
        return metadata
    
    def get_metadata(self, chunk_id: str) -> Optional[Dict[str, Any]]:
        """Get metadata for a specific chunk.
        
        Args:
            chunk_id: ID of the chunk
            
        Returns:
            Metadata dictionary or None if not found
        """
        return self.metadata.get(chunk_id)
    
    def get_all_metadata(self) -> Dict[str, Dict[str, Any]]:
        """Get all metadata.
        
        Returns:
            Dictionary of all chunk metadata
        """
        return self.metadata
    
    def get_metadata_by_date(self, date: str) -> List[Dict[str, Any]]:
        """Get all metadata for a specific date.
        
        Args:
            date: Date string in YYYY-MM-DD format
            
        Returns:
            List of metadata dictionaries for the specified date
        """
        return [meta for meta in self.metadata.values() if meta.get("date") == date] 