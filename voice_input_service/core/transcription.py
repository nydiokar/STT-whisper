from __future__ import annotations
import os
import logging
from typing import Optional, List, Dict, Any, Tuple
import whisper
import tempfile
import threading
import time
import wave

import torch
from whisper.tokenizer import LANGUAGES, TO_LANGUAGE_CODE

class TranscriptionEngine:
    """Engine for performing speech-to-text transcription using whisper."""
    
    def __init__(
        self,
        model_name: str = "base",
        device: Optional[str] = None,
        compute_type: str = "float16",
        language: Optional[str] = None,
        translate: bool = False,
        verbose: bool = False,
        cache_dir: Optional[str] = None
    ) -> None:
        """Initialize the transcription engine.
        
        Args:
            model_name: Whisper model to use (tiny, base, small, medium, large)
            device: Device to run the model on (cuda, cpu)
            compute_type: Computation type (float16, float32, int8)
            language: Language code for transcription
            translate: Whether to translate to English
            verbose: Whether to log detailed information
            cache_dir: Directory to cache models
        """
        self.logger = logging.getLogger("voice_input_service.core.transcription")
        
        # Configuration
        self.model_name = model_name.lower()
        self.language = language
        self.translate = translate
        self.verbose = verbose
        self.device = device or ("cuda" if torch.cuda.is_available() else "cpu")
        self.compute_type = compute_type
        
        # Loading state
        self.model: Optional[whisper.Whisper] = None
        self.loaded = False
        self.loading_lock = threading.Lock()
        
        # Cache directory
        if cache_dir:
            os.makedirs(cache_dir, exist_ok=True)
            
        # Attempt to load the model
        self._load_model(cache_dir)
        
    def _load_model(self, cache_dir: Optional[str] = None) -> None:
        """Load the whisper model.
        
        Args:
            cache_dir: Directory to cache the model
            
        Raises:
            RuntimeError: If model loading fails
        """
        if self.loaded:
            return
            
        with self.loading_lock:
            if self.loaded:  # Double check in case of race condition
                return
                
            try:
                start_time = time.time()
                self.logger.info(f"Loading whisper model '{self.model_name}' on {self.device}...")
                
                # Verify CUDA availability if requested
                if self.device == "cuda" and not torch.cuda.is_available():
                    raise RuntimeError("CUDA requested but not available. Falling back to CPU.")
                
                self.model = whisper.load_model(
                    self.model_name,
                    device=self.device,
                    download_root=cache_dir,
                    in_memory=True
                )
                
                # Verify model loaded correctly
                if not self.model:
                    raise RuntimeError("Model loading returned None")
                    
                self.loaded = True
                load_time = time.time() - start_time
                self.logger.info(f"Model loaded in {load_time:.2f} seconds")
                
                # Log model info
                if hasattr(self.model, 'dims'):
                    self.logger.info(f"Model dimensions: {self.model.dims}")
                if self.verbose:
                    num_params = sum(p.numel() for p in self.model.parameters())
                    self.logger.info(f"Model parameters: {num_params:,}")
                    
            except Exception as e:
                self.loaded = False
                error_msg = f"Failed to load whisper model: {str(e)}"
                self.logger.error(error_msg, exc_info=True)
                raise RuntimeError(error_msg) from e
                
    def transcribe(
        self, 
        audio_data: bytes,
        sample_rate: int = 44100,
        language: Optional[str] = None,
        task: Optional[str] = None,
        initial_prompt: Optional[str] = None
    ) -> Dict[str, Any]:
        """Transcribe audio data.
        
        Args:
            audio_data: Raw audio data bytes
            sample_rate: Sample rate of the audio data
            language: Override language for transcription
            task: Task to perform (transcribe or translate)
            initial_prompt: Initial prompt for the transcription
            
        Returns:
            Dictionary with transcription results
            
        Raises:
            RuntimeError: If transcription fails or model is not loaded
        """
        if not self.loaded or self.model is None:
            self.logger.warning("Model not loaded, attempting to load...")
            try:
                self._load_model()
            except Exception as e:
                error_msg = "Failed to load model for transcription"
                self.logger.error(error_msg, exc_info=True)
                raise RuntimeError(error_msg) from e
            
            if not self.loaded:
                raise RuntimeError("Model failed to load")
        
        temp_file = None
        try:
            # Create temp file in a way that ensures cleanup
            temp_file = tempfile.NamedTemporaryFile(suffix=".wav", delete=False)
            
            # Write audio to temp WAV file
            with wave.open(temp_file.name, "wb") as wf:
                wf.setnchannels(1)  # Mono
                wf.setsampwidth(2)  # 16-bit
                wf.setframerate(sample_rate)
                wf.writeframes(audio_data)
            
            # Prepare transcription options
            language_code = language or self.language
            options = {
                "language": language_code,
                "task": task or ("translate" if self.translate else "transcribe"),
                "verbose": self.verbose
            }
            
            if initial_prompt:
                options["initial_prompt"] = initial_prompt
                
            if language_code:
                if language_code in LANGUAGES:
                    options["language"] = language_code
                else:
                    valid_code = TO_LANGUAGE_CODE.get(language_code)
                    if valid_code:
                        options["language"] = valid_code
                    else:
                        self.logger.warning(f"Unsupported language code: {language_code}")
            
            # Perform transcription
            start_time = time.time()
            self.logger.info(f"Starting transcription of {len(audio_data)/1024:.2f} KB audio")
            
            try:
                result = self.model.transcribe(
                    temp_file.name,
                    fp16=(self.compute_type == "float16"),
                    **options
                )
            except Exception as e:
                error_msg = "Transcription failed"
                self.logger.error(f"{error_msg}: {e}", exc_info=True)
                raise RuntimeError(error_msg) from e
            
            transcription_time = time.time() - start_time
            word_count = len(result.get("text", "").split())
            
            self.logger.info(
                f"Transcription completed in {transcription_time:.2f}s, "
                f"{word_count} words, {word_count/transcription_time:.1f} words/sec"
            )
            
            return result
            
        except Exception as e:
            error_msg = "Unexpected error during transcription"
            self.logger.error(f"{error_msg}: {e}", exc_info=True)
            raise RuntimeError(error_msg) from e
            
        finally:
            # Clean up temp file
            if temp_file:
                try:
                    os.unlink(temp_file.name)
                except Exception as e:
                    self.logger.warning(f"Failed to remove temp file: {e}")
    
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
        if not self.loaded or self.model is None:
            return {"status": "not_loaded"}
            
        info = {
            "name": self.model_name,
            "device": self.device,
            "compute_type": self.compute_type,
            "loaded": self.loaded
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