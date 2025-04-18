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
    sample_rate: int = 16000
    chunk_size: int = 1024
    channels: int = 1
    format: int = pyaudio.paInt16
    processing_chunk_size: int = 16000  # Process every 1 second of audio

class TranscriptionService:
    """Handles speech-to-text transcription."""
    
    def __init__(self, config: TranscriptionConfig) -> None:
        self.logger = logging.getLogger("VoiceService.Transcription")
        self.config = config
        self.logger.info("Loading Whisper model...")
        self.whisper_model: whisper.Whisper = whisper.load_model(config.model_name)
        self.logger.info(f"Whisper model '{config.model_name}' loaded successfully")
        self.last_activity: float = time.time()
        self.pause_detected: bool = False
        self.current_segment: str = ""
    
    def process_audio(self, audio_data: bytes, context: str = "", language: str = "en") -> Optional[str]:
        """Process audio data and return transcribed text."""
        try:
            with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as temp_file:
                with wave.open(temp_file.name, 'wb') as wf:
                    wf.setnchannels(self.config.channels)
                    wf.setsampwidth(pyaudio.get_sample_size(self.config.format))
                    wf.setframerate(self.config.sample_rate)
                    wf.writeframes(audio_data)
                
                # Use more aggressive settings for real-time transcription
                result = self.whisper_model.transcribe(
                    temp_file.name,
                    language=language,
                    initial_prompt=context[-100:] if context else None,
                    task='transcribe',
                    best_of=1,  # Speed up processing
                    beam_size=1,  # Speed up processing
                    temperature=0.0,  # More deterministic output
                    condition_on_previous_text=True,
                    word_timestamps=True  # Enable word-level timestamps
                )
                
                # Clean up the temporary file
                try:
                    os.unlink(temp_file.name)
                except Exception:
                    pass
                
                text = result["text"].strip()
                # Only process if we have actual words (not just dots or spaces)
                if text and not all(c in '. ' for c in text):
                    self.last_activity = time.time()
                    self.current_segment = text
                    return text
                else:
                    # Always set pause_detected for empty or dot-only text
                    self.pause_detected = True
                    if time.time() - self.last_activity > 1.0:
                        self.logger.debug("Long pause detected in speech")
                    return None
                
        except Exception as e:
            self.logger.error(f"Error processing audio: {e}", exc_info=True)
            return None 