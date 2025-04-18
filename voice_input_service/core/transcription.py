from __future__ import annotations
import os
import logging
from typing import Optional, List, Dict, Any, Tuple
import numpy as np
import whisper
import tempfile
import threading
import time
import wave

import torch
from whisper.tokenizer import LANGUAGES, TO_LANGUAGE_CODE

class TranscriptionEngine:
    """Engine responsible for speech-to-text transcription."""

    def __init__(
        self,
        model_name: str,
        device: str = "auto",
        language: str = "en",
    ):
        """Initialize Whisper model for transcription.

        Args:
            model_name: Name of the Whisper model to load
            device: Device to use for inference
            language: Language used for transcription
        """
        self.logger = logging.getLogger(__name__)
        self.model_name = model_name
        self.device = device
        self.language = language
        self.model = None
        
        # Check if we're using CPU and warn about large model
        if self.device == "cpu" and self.model_name == "large":
            self.logger.warning(
                "Using 'large' model on CPU will be very slow. Consider using 'medium' or 'small' "
                "model for better performance on CPU."
            )
        
        # Don't load model yet - just set up parameters
        self.logger.debug(f"Transcription engine initialized with model '{model_name}' (not loaded yet)")

    def _load_model(self) -> None:
        """Load the Whisper model.
        
        Raises:
            RuntimeError: If model loading fails
        """
        if self.model is not None:
            return  # Model already loaded
            
        self.logger.info(f"Loading model '{self.model_name}' on device '{self.device}'...")
        try:
            # Force float32 on CPU to avoid warnings and potential issues
            compute_type = "float32" if self.device == "cpu" else "float16"
            
            self.model = whisper.load_model(
                self.model_name,
                device=self.device,
            )
            self.logger.info(f"Successfully loaded model '{self.model_name}'")
        except Exception as e:
            self.logger.error(f"Failed to load model '{self.model_name}': {e}")
            raise RuntimeError(f"Failed to load model: {e}") from e

    def transcribe(self, audio: bytes, prompt: str = "") -> Dict[str, Any]:
        """Transcribe audio to text.

        Args:
            audio: Raw audio bytes to transcribe
            prompt: Prompt to guide transcription

        Returns:
            Transcription result
        """
        # Ensure model is loaded before transcribing
        if self.model is None:
            self._load_model()
            
        options = dict(language=self.language)
        if prompt:
            options["initial_prompt"] = prompt

        # Convert bytes to float32 array that Whisper expects
        audio_data = np.frombuffer(audio, dtype=np.int16).astype(np.float32) / 32768.0
        
        # Log length of audio for debugging
        audio_duration = len(audio_data) / 16000  # Whisper uses 16kHz
        self.logger.debug(f"Processing {audio_duration:.1f}s of audio")

        return self.model.transcribe(audio_data, **options)

    def get_available_languages(self) -> Dict[str, str]:
        """Get available languages for transcription.
        
        Returns:
            Dictionary mapping language codes to names
        """
        return {code: name for code, name in LANGUAGES.items()}
        
    def get_model_info(self) -> Dict[str, Any]:
        """Get information about the loaded model.
        
        Returns:
            Dictionary with model information
        """
        if not self.model:
            return {"status": "not_loaded"}
            
        info = {
            "name": self.model_name,
            "device": self.device,
            "loaded": bool(self.model)
        }
        
        if hasattr(self.model, "dims"):
            info["dimensions"] = self.model.dims
            
        return info

    def set_language(self, language: str) -> None:
        """Set the transcription language.
        
        Args:
            language: ISO 639-1 language code
            
        Raises:
            ValueError: If the language code is invalid
        """
        # Normalize language code
        language = language.lower().strip()
        
        # Check if language is directly supported
        if language in LANGUAGES:
            self.language = language
            return
        
        # Check if it's a valid code that needs mapping
        if language in TO_LANGUAGE_CODE:
            self.language = TO_LANGUAGE_CODE[language]
            return
        
        raise ValueError(f"Unsupported language code: {language}")