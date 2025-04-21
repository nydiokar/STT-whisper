from __future__ import annotations
import os
import logging
from typing import Dict, Any
import numpy as np


# Try to import whisper packages, but don't fail if they're not available
try:
    import whisper
    import torch
    from whisper.tokenizer import LANGUAGES, TO_LANGUAGE_CODE
    WHISPER_AVAILABLE = True
except ImportError:
    WHISPER_AVAILABLE = False
    # Create empty dictionaries for languages if whisper is not available
    LANGUAGES = {}
    TO_LANGUAGE_CODE = {}

# Import our whisper.cpp implementation
from voice_input_service.core.whisper_cpp import transcribe as whisper_cpp_transcribe


class TranscriptionEngine:
    """Engine responsible for speech-to-text transcription."""

    def __init__(
        self,
        model_name: str,
        device: str = "auto",
        language: str = "en",
        use_cpp: bool = False,
        whisper_cpp_path: str = "./main",
    ):
        """Initialize Whisper model for transcription.

        Args:
            model_name: Name of the Whisper model to load
            device: Device to use for inference
            language: Language used for transcription
            use_cpp: Whether to use whisper.cpp instead of Python Whisper
            whisper_cpp_path: Path to the whisper.cpp executable
        """
        self.logger = logging.getLogger(__name__)
        self.model_name = model_name
        self.device = device
        self.language = language
        self.model = None
        self.use_cpp = use_cpp
        self.whisper_cpp_path = whisper_cpp_path
        
        # Force use_cpp if whisper is not available
        if not WHISPER_AVAILABLE and not use_cpp:
            self.logger.warning("Python Whisper is not available. Forcing use of whisper.cpp")
            self.use_cpp = True
        
        # Don't initialize whisper.cpp model (will be loaded on-demand)
        if not self.use_cpp:
            # Check if we're using CPU and warn about large model
            if self.device == "cpu" and self.model_name == "large":
                self.logger.warning(
                    "Using 'large' model on CPU will be very slow. Consider using 'medium' or 'small' "
                    "model for better performance on CPU."
                )
            
            # Don't load model yet - just set up parameters
            self.logger.debug(f"Transcription engine initialized with model '{model_name}' (not loaded yet)")
        else:
            self.logger.debug(f"Using whisper.cpp with model '{model_name}'")

    def _load_model(self) -> None:
        """Load the Whisper model.
        
        Raises:
            RuntimeError: If model loading fails
        """
        if self.use_cpp:
            # No need to load model for whisper.cpp - it's handled by the subprocess
            return
            
        if self.model is not None:
            return  # Model already loaded
            
        if not WHISPER_AVAILABLE:
            raise RuntimeError(
                "Failed to load whisper model: Python Whisper is not installed. "
                "Please install with 'pip install openai-whisper' or use whisper.cpp."
            )
            
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
        # Log length of audio for debugging
        audio_data_np = np.frombuffer(audio, dtype=np.int16)
        audio_duration = len(audio_data_np) / 16000  # Whisper uses 16kHz
        self.logger.debug(f"Processing {audio_duration:.1f}s of audio")
        
        if self.use_cpp:
            # Use whisper.cpp implementation
            self.logger.debug("Using whisper.cpp for transcription")
            
            # Get the model file path from config if specified
            model_file = getattr(self, 'model_file_path', None)
            
            # If no model file is specified, log error and return
            if not model_file:
                error_msg = "No GGML model file specified for whisper.cpp. Update your configuration."
                self.logger.error(error_msg)
                return {"text": error_msg}
                
            # Log which model we're using
            model_name = os.path.basename(model_file)
            self.logger.info(f"Transcribing with model: {model_name}")
                
            # Call whisper.cpp
            text = whisper_cpp_transcribe(
                audio_data=audio,
                model_path=model_file,
                main_path=self.whisper_cpp_path,
                language=self.language
            )
            
            # Return in a format similar to the Whisper Python output
            return {"text": text.strip()}
        else:
            # Use Python Whisper implementation
            # Ensure model is loaded before transcribing
            if self.model is None:
                self._load_model()
                
            self.logger.info(f"Transcribing with Python Whisper model: {self.model_name}")
            
            options = dict(language=self.language)
            if prompt:
                options["initial_prompt"] = prompt

            # Convert bytes to float32 array that Whisper expects
            audio_data = audio_data_np.astype(np.float32) / 32768.0

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
        if self.use_cpp:
            return {
                "name": self.model_name,
                "device": "cpp",
                "loaded": True,
                "type": "whisper.cpp"
            }
            
        if not self.model:
            return {"status": "not_loaded"}
            
        info = {
            "name": self.model_name,
            "device": self.device,
            "loaded": bool(self.model),
            "type": "whisper-python"
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

    def set_model_file(self, model_file_path: str) -> None:
        """Set a specific model file path to use.
        
        Args:
            model_file_path: Path to the model file
        """
        self.model_file_path = model_file_path
        # Add clear message about which model file is being used
        model_filename = os.path.basename(model_file_path)
        model_size_mb = os.path.getsize(model_file_path) / (1024 * 1024)
        self.logger.info(f"********************************************")
        self.logger.info(f"USING GGML MODEL: {model_filename} ({model_size_mb:.1f} MB)")
        self.logger.info(f"********************************************")
        
        # Try to extract model name from filename
        base_name = os.path.basename(model_file_path)
        if base_name.startswith("ggml-") and base_name.endswith(".bin"):
            # Extract the model name from ggml-{name}.bin or ggml-{name}-{variant}.bin
            parts = base_name[5:-4].split("-")  # Remove "ggml-" and ".bin"
            if parts:
                self.model_name = parts[0].split(".")[0]  # Get base part before any dot
                self.logger.debug(f"Extracted model name: {self.model_name}")
                
                # Detect language-specific model
                if "." in parts[0]:
                    lang = parts[0].split(".")[1]
                    if lang in LANGUAGES or lang in TO_LANGUAGE_CODE:
                        self.language = lang
                        self.logger.debug(f"Detected language from model: {lang}")