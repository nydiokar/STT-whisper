from __future__ import annotations
import os
import wave
import tempfile
import time
import logging
from typing import Optional
import whisper
from pydantic import BaseModel, ConfigDict
import pyaudio

class TranscriptionConfig(BaseModel):
    """Configuration for transcription service."""
    model_config = ConfigDict(frozen=True)  # Makes config immutable
    
    model_name: str = 'medium'
    sample_rate: int = 16000  # Whisper expects 16kHz
    chunk_size: int = 4096
    channels: int = 1
    format: int = pyaudio.paInt16
    processing_chunk_size: int = 32000  # Process every 2 seconds of audio

class TranscriptionService:
    """Handles speech-to-text transcription."""
    
    def __init__(self, config: TranscriptionConfig) -> None:
        self.logger = logging.getLogger("VoiceService.Transcription")
        self.config = config
        self.logger.info("Loading Whisper model...")
        self.whisper_model = whisper.load_model(config.model_name)
        self.logger.info(f"Whisper model '{config.model_name}' loaded successfully")
        self.last_activity: float = time.time()
        self.pause_detected: bool = False
        self.current_segment: str = ""
        
        # Used to prevent duplicate transcriptions
        self._last_transcript = ""
        self._transcript_cache = set()
    
    def process_audio(self, audio_data: bytes, context: str = "", language: str = "en") -> Optional[str]:
        """Process audio data and return transcribed text."""
        if not audio_data or len(audio_data) < 2000:  # Skip very small chunks
            return None
            
        # Create a temporary WAV file
        temp_filename = None
        try:
            with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as temp_file:
                temp_filename = temp_file.name
            
            # Create WAV file with proper headers
            with wave.open(temp_filename, 'wb') as wf:
                wf.setnchannels(self.config.channels)
                wf.setsampwidth(pyaudio.get_sample_size(self.config.format))
                wf.setframerate(self.config.sample_rate)
                wf.writeframes(audio_data)
                
            # Use basic options for transcription
            try:
                result = self.whisper_model.transcribe(temp_filename, language=language)
                text = result["text"].strip()
                
                # Check for duplicates
                if text in self._transcript_cache or text == self._last_transcript:
                    return None
                    
                # Only return actual content
                if text and not all(c in '. ' for c in text):
                    self.last_activity = time.time()
                    self._last_transcript = text
                    self._transcript_cache.add(text)
                    if len(self._transcript_cache) > 5:
                        self._transcript_cache = set(list(self._transcript_cache)[-5:])
                    self.logger.info(f"Transcribed: '{text}'")
                    return text
                else:
                    self.pause_detected = True
                    return None
                    
            except Exception as e:
                self.logger.error(f"Error in transcription: {str(e).split('\n')[0]}")
                return None
        
        except Exception as e:
            self.logger.error(f"Error processing audio: {str(e).split('\n')[0]}")
            return None
            
        finally:
            # Clean up temp file
            if temp_filename and os.path.exists(temp_filename):
                try:
                    os.unlink(temp_filename)
                except:
                    pass 