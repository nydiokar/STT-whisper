from __future__ import annotations
import os
import logging
import sys
import traceback
from typing import Dict, Any, Optional
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

class ModelError(Exception):
    """Exception raised for model loading or initialization errors."""
    pass

class TranscriptionEngine:
    """Engine responsible for speech-to-text transcription."""

    def __init__(
        self,
        model_name: str,
        device: str = "auto",
        language: str = "en",
        use_cpp: bool = False,
        whisper_cpp_path: str = "./main",
        model_file_path: Optional[str] = None,
        cache_dir: Optional[str] = None
    ):
        """Initialize Whisper model for transcription.

        Args:
            model_name: Name of the Whisper model to load
            device: Device to use for inference
            language: Language used for transcription
            use_cpp: Whether to use whisper.cpp instead of Python Whisper
            whisper_cpp_path: Path to the whisper.cpp executable
            model_file_path: Path to the specific GGML model file (for whisper.cpp)
            cache_dir: Directory to cache models (for Python Whisper)
            
        Raises:
            ModelError: If initialization fails due to model loading or configuration errors.
        """
        self.logger = logging.getLogger(__name__)
        self.model_name = model_name
        self.device = device
        self.language = language
        self.model = None
        self.use_cpp = use_cpp
        self.whisper_cpp_path = whisper_cpp_path
        self.model_file_path = model_file_path
        self.cache_dir = cache_dir
        self.initialization_error = None
        self.loaded = False
        
        # Determine device if auto
        if self.device == "auto":
            if torch.cuda.is_available():
                self.device = "cuda"
                self.logger.info("CUDA available. Using GPU for transcription.")
            else:
                self.device = "cpu"
                self.logger.info("CUDA not available. Using CPU for transcription.")
        
        # Force use_cpp if whisper is not available
        if not WHISPER_AVAILABLE and not use_cpp:
            self.logger.warning("Python Whisper library not available. Forcing use of whisper.cpp")
            self.use_cpp = True
        
        # --- Load Model or Check whisper.cpp Setup --- 
        try:
            if self.use_cpp:
                # Verify whisper.cpp executable exists
                if not os.path.exists(self.whisper_cpp_path):
                    raise FileNotFoundError(f"Whisper.cpp executable not found at: {self.whisper_cpp_path}")
                # Verify GGML model path is provided and exists
                if not self.model_file_path or not os.path.exists(self.model_file_path):
                    raise FileNotFoundError(f"GGML model file not found or not specified: {self.model_file_path}")
                self.logger.info(f"whisper.cpp setup verified. Model: {os.path.basename(self.model_file_path)}")
                self.loaded = True
            else:
                # Check if we're using CPU and warn about large model
                if self.device == "cpu" and self.model_name == "large":
                    self.logger.warning(
                        "Using 'large' model on CPU will be very slow. Consider using 'medium' or 'small' "
                        "model for better performance on CPU."
                    )
                
                # Try loading the Python Whisper model
                if not WHISPER_AVAILABLE:
                     raise ImportError("Python Whisper library is required but not installed.")
                self._load_model()
                self.loaded = True

        except (FileNotFoundError, ImportError, ModelError, Exception) as e:
            error_msg = f"Failed to initialize transcription engine: {e}"
            self.logger.error(error_msg, exc_info=True)
            self.initialization_error = str(e)
            self.loaded = False
            raise ModelError(error_msg) from e 
        # --- End Load Model --- 

    def _load_model(self) -> None:
        """Load the Whisper model.
        
        Raises:
            ModelError: If model loading fails
        """
        if self.use_cpp:
            # Verify whisper.cpp executable exists
            if not os.path.exists(self.whisper_cpp_path):
                error_msg = f"Whisper.cpp executable not found at: {self.whisper_cpp_path}"
                self.logger.error(error_msg)
                self.initialization_error = error_msg
                raise ModelError(error_msg)
                
            # Verify we have a model file specified
            if not hasattr(self, 'model_file_path') or not self.model_file_path:
                error_msg = "No GGML model file specified for whisper.cpp"
                self.logger.error(error_msg)
                self.initialization_error = error_msg
                raise ModelError(error_msg)
                
            # Verify the model file exists
            if not os.path.exists(self.model_file_path):
                error_msg = f"GGML model file not found at: {self.model_file_path}"
                self.logger.error(error_msg)
                self.initialization_error = error_msg
                raise ModelError(error_msg)
                
            # All checks passed, whisper.cpp is ready
            return
            
        if self.model is not None:
            return  # Model already loaded
            
        if not WHISPER_AVAILABLE:
            error_msg = (
                "Failed to load whisper model: Python Whisper is not installed. "
                "Please install with 'pip install openai-whisper' or use whisper.cpp."
            )
            self.logger.error(error_msg)
            self.initialization_error = error_msg
            raise ModelError(error_msg)
            
        self.logger.info(f"Loading model '{self.model_name}' on device '{self.device}'...")
        try:
            # Force float32 on CPU to avoid warnings and potential issues
            compute_type = "float32" if self.device == "cpu" else "float16"
            
            self.model = whisper.load_model(
                self.model_name,
                device=self.device,
            )
            self.logger.info(f"Successfully loaded model '{self.model_name}'")
            self.initialization_error = None  # Clear any previous errors
            self.loaded = True # Set loaded flag on success
        except Exception as e:
            error_msg = f"Failed to load model '{self.model_name}': {e}"
            self.logger.error(error_msg)
            self.initialization_error = error_msg
            
            # Add traceback for debugging
            exc_type, exc_value, exc_traceback = sys.exc_info()
            if exc_traceback:
                tb_lines = traceback.format_exception(exc_type, exc_value, exc_traceback)
                self.logger.debug("".join(tb_lines))
                
            raise ModelError(error_msg) from e

    def transcribe(self, audio: bytes, prompt: str = "") -> Dict[str, Any]:
        """Transcribe audio to text.

        Args:
            audio: Raw audio bytes to transcribe
            prompt: Prompt to guide transcription

        Returns:
            Transcription result
            
        Raises:
            ModelError: If transcription fails due to model issues
        """
        # --- Handle Empty Input --- 
        if not audio:
            self.logger.debug("Received empty audio data, returning empty result.")
            return {"text": ""}
        # --- End Empty Input --- 
            
        try:
            # Log length of audio for debugging
            audio_data_np = np.frombuffer(audio, dtype=np.int16)
            audio_duration = len(audio_data_np) / 16000  # Whisper uses 16kHz
            self.logger.debug(f"Processing {audio_duration:.1f}s of audio")
            
            if self.use_cpp:
                # Use whisper.cpp implementation
                self.logger.debug("Using whisper.cpp for transcription")
                
                # Make sure we have a valid model file
                if not hasattr(self, 'model_file_path') or not self.model_file_path:
                    error_msg = "No GGML model file specified for whisper.cpp. Update your configuration."
                    self.logger.error(error_msg)
                    raise ModelError(error_msg)
                
                # Verify the model file and whisper.cpp executable exist
                if not os.path.exists(self.model_file_path):
                    error_msg = f"GGML model file not found: {self.model_file_path}"
                    self.logger.error(error_msg)
                    raise ModelError(error_msg)
                
                if not os.path.exists(self.whisper_cpp_path):
                    error_msg = f"whisper.cpp executable not found: {self.whisper_cpp_path}"
                    self.logger.error(error_msg)
                    raise ModelError(error_msg)
                    
                # Log which model we're using
                model_name = os.path.basename(self.model_file_path)
                self.logger.info(f"Transcribing with model: {model_name}")
                    
                # Call whisper.cpp
                text = whisper_cpp_transcribe(
                    audio_data=audio,
                    model_path=self.model_file_path,
                    main_path=self.whisper_cpp_path,
                    language=self.language
                )
                
                # Check if the response indicates an error
                if text and text.startswith("Error:"):
                    self.logger.error(f"Whisper.cpp transcription error: {text}")
                    raise ModelError(f"Transcription failed: {text}")
                
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
                # Add task based on translate flag
                if hasattr(self, 'translate') and self.translate:
                    options["task"] = "translate"
                else:
                    options["task"] = "transcribe"

                # Convert bytes to float32 array that Whisper expects
                audio_data = audio_data_np.astype(np.float32) / 32768.0

                return self.model.transcribe(audio_data, **options)
                
        except ModelError:
            # Re-raise ModelError without wrapping
            raise
        except Exception as e:
            # Capture and log the full exception traceback
            exc_type, exc_value, exc_traceback = sys.exc_info()
            if exc_traceback:
                tb_lines = traceback.format_exception(exc_type, exc_value, exc_traceback)
                self.logger.error("Transcription error: " + "".join(tb_lines))
            else:
                self.logger.error(f"Transcription error: {e}")
                
            # Wrap in ModelError for consistent error handling
            raise ModelError(f"Transcription failed: {e}") from e

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
            model_path = getattr(self, 'model_file_path', None)
            model_exists = model_path and os.path.exists(model_path)
            
            return {
                "name": self.model_name,
                "device": "cpp",
                "loaded": model_exists,
                "type": "whisper.cpp",
                "path": model_path,
                "error": self.initialization_error
            }
            
        if not self.model:
            return {
                "status": "not_loaded", 
                "name": self.model_name,
                "error": self.initialization_error
            }
            
        info = {
            "name": self.model_name,
            "device": self.device,
            "loaded": bool(self.model),
            "type": "whisper-python",
            "error": self.initialization_error
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
            
        Raises:
            ModelError: If the model file doesn't exist
        """
        if not os.path.exists(model_file_path):
            error_msg = f"Model file not found: {model_file_path}"
            self.logger.error(error_msg)
            self.initialization_error = error_msg
            raise ModelError(error_msg)
            
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

    def test_model(self) -> Dict[str, Any]:
        """Test if the model can be loaded and is ready for transcription.
        
        Returns:
            Dictionary with test results including success status and error message if any
        """
        result = {
            "success": False,
            "error": None,
            "model_name": self.model_name,
            "type": "whisper.cpp" if self.use_cpp else "whisper-python"
        }
        
        try:
            if self.use_cpp:
                # For whisper.cpp, check if the model file and executable exist
                if not hasattr(self, 'model_file_path') or not self.model_file_path:
                    raise ModelError("No GGML model file specified for whisper.cpp")
                
                if not os.path.exists(self.model_file_path):
                    raise ModelError(f"GGML model file not found: {self.model_file_path}")
                
                if not os.path.exists(self.whisper_cpp_path):
                    raise ModelError(f"whisper.cpp executable not found: {self.whisper_cpp_path}")
                
                result["success"] = True
                result["model_path"] = self.model_file_path
                result["whisper_cpp_path"] = self.whisper_cpp_path
            else:
                # For Python Whisper, try loading the model
                self._load_model()
                result["success"] = True
                result["device"] = self.device
        except Exception as e:
            result["success"] = False
            result["error"] = str(e)
            self.logger.error(f"Model test failed: {e}")
            
        return result