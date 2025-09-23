from __future__ import annotations
import os
import logging
import sys
import traceback
from typing import Dict, Any, Optional, List
import numpy as np
from datetime import datetime
import uuid
import platform # Import platform

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
    # Define torch stubs if not available to avoid errors in type hints/checks
    class torch:
        class cuda:
            @staticmethod
            def is_available() -> bool: 
                return False

# Import our whisper.cpp implementation
from voice_input_service.core.whisper_cpp import transcribe as whisper_cpp_transcribe, save_wav_file
# Removed direct import of TranscriptManager

class ModelError(Exception):
    """Exception raised for model loading or initialization errors."""
    pass

class TranscriptionResult(Dict):
    """Standardized transcription result structure."""
    text: str
    language: str
    segments: List[Dict[str, Any]]

class TranscriptionEngine:
    """Engine responsible for speech-to-text transcription."""

    def __init__(
        self,
        model_name: str,
        device: str = "auto",
        language: str = "en",
        use_cpp: bool = False,
        cache_dir: Optional[str] = None,
        config: Optional[Dict[str, Any]] = None # Pass config for cpp paths
    ):
        """Initialize Whisper model for transcription."""
        self.logger = logging.getLogger(__name__)
        self.model_name = model_name
        self.device = device
        self.language = language
        self.model = None
        self.use_cpp = use_cpp
        self.cache_dir = cache_dir
        self.initialization_error = None
        self.loaded = False
        self.config = config
        self.whisper_cpp_path = None # Store paths after verification
        self.model_file_path = None
        
        # --- Resolve Device --- 
        if self.device == "auto":
             # Add platform check for MPS
             _IS_MAC = platform.system() == 'Darwin'
             _IS_ARM = platform.machine() == 'arm64' or platform.machine() == 'aarch64'
             if _IS_MAC and _IS_ARM and WHISPER_AVAILABLE and hasattr(torch.backends, 'mps') and torch.backends.mps.is_available():
                  self.device = "mps"
                  self.logger.info("MPS available. Using Apple Silicon GPU for transcription.")
             elif WHISPER_AVAILABLE and torch.cuda.is_available():
                self.device = "cuda"
                self.logger.info("CUDA available. Using GPU for transcription.")
             else:
                self.device = "cpu"
                self.logger.info("CUDA/MPS not available or PyTorch missing. Using CPU for transcription.")
        # --- End Resolve Device --- 
        
        # Force use_cpp if whisper is not available
        if not WHISPER_AVAILABLE and not use_cpp:
            self.logger.warning("Python Whisper library not available. Forcing use of whisper.cpp")
            self.use_cpp = True
        
        # --- Initial Check/Load --- 
        try:
            if self.use_cpp:
                # Verify whisper.cpp executable and model path are available in config
                if not self.config:
                     raise ValueError("Config object must be provided when using whisper.cpp")
                cpp_path = self.config.transcription.whisper_cpp_path
                ggml_path = self.config.transcription.ggml_model_path

                if not cpp_path or not os.path.exists(cpp_path):
                    raise FileNotFoundError(f"Whisper.cpp executable not found or not specified in config: {cpp_path}")
                if not ggml_path or not os.path.exists(ggml_path):
                    # Allow ggml_model_path to be None initially, model manager might select one
                    self.logger.warning(f"Initial GGML model file path not found or not specified: {ggml_path}. ModelManager may select one.")
                    # raise FileNotFoundError(f"GGML model file not found or not specified in config: {ggml_path}")
                
                # Store verified paths
                self.whisper_cpp_path = cpp_path
                self.model_file_path = ggml_path # Can be None initially
                
                self.logger.info(f"whisper.cpp setup verified. Executable: {self.whisper_cpp_path}, Initial Model: {self.model_file_path}")
                self.loaded = True # Mark as loaded if paths are valid (even if model is None initially)
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
                self._load_model() # This sets self.loaded = True on success

        except (FileNotFoundError, ImportError, ValueError, ModelError, Exception) as e:
            error_msg = f"Failed to initialize transcription engine: {e}"
            self.logger.error(error_msg, exc_info=True)
            self.initialization_error = str(e)
            self.loaded = False
            # Don't raise here, let checks happen before transcription
        # --- End Initial Check/Load --- 

    def _load_model(self) -> None:
        """Load the Python Whisper model.
        
        Raises:
            ModelError: If model loading fails
        """
        if self.use_cpp:
             self.logger.warning("_load_model called unnecessarily for whisper.cpp setup.")
             return
            
        if self.model is not None:
            return  # Model already loaded
            
        if not WHISPER_AVAILABLE:
            error_msg = (
                "Failed to load whisper model: Python Whisper is not installed. "
                "Please install with 'pip install openai-whisper'."
            )
            self.logger.error(error_msg)
            self.initialization_error = error_msg
            self.loaded = False
            raise ModelError(error_msg)
            
        self.logger.info(f"Loading model '{self.model_name}' on device '{self.device}'...")
        try:
            self.model = whisper.load_model(
                self.model_name,
                device=self.device,
                download_root=self.cache_dir # Use specified cache dir if provided
            )
            self.logger.info(f"Successfully loaded model '{self.model_name}'")
            self.initialization_error = None
            self.loaded = True
        except Exception as e:
            error_msg = f"Failed to load model '{self.model_name}': {e}"
            self.logger.error(error_msg, exc_info=True)
            self.initialization_error = error_msg
            self.loaded = False 
            raise ModelError(error_msg) from e

    def transcribe(self, audio: bytes, target_wav_path: Optional[str] = None, prompt: str = "") -> TranscriptionResult:
        """Transcribe audio to text. Optionally saves the WAV file if target_wav_path is provided.

        Args:
            audio: Raw audio bytes to transcribe (16kHz, 16-bit mono).
            target_wav_path: Optional full path where the WAV file should be saved.
            prompt: Prompt to guide transcription.

        Returns:
            TranscriptionResult dictionary containing segments, text, language.
            
        Raises:
            ModelError: If transcription prerequisites fail or transcription itself fails.
            IOError: If saving the WAV file fails (when path is provided).
        """
        # --- Pre-transcription Checks --- 
        if not self.loaded:
             error_msg = f"Transcription engine not loaded or initialization failed. Error: {self.initialization_error}"
             self.logger.error(error_msg)
             raise ModelError(error_msg)
             
        if self.use_cpp and (not self.whisper_cpp_path or not self.model_file_path):
             error_msg = "whisper.cpp executable or model path not configured correctly."
             self.logger.error(error_msg)
             raise ModelError(error_msg)
             
        if not audio:
            self.logger.debug("Received empty audio data, returning empty result.")
            return TranscriptionResult({"text": "", "language": self.language, "segments": []})
        # --- End Checks --- 
            
        try:
            # Log length of audio for debugging
            audio_data_np = np.frombuffer(audio, dtype=np.int16)
            audio_duration = len(audio_data_np) / 16000  # Whisper uses 16kHz
            self.logger.info(f"Processing {audio_duration:.1f}s of audio")
            
            if self.use_cpp:
                # --- whisper.cpp Path --- 
                self.logger.info(f"Using whisper.cpp for transcription. Model: {self.model_file_path}")
                
                # Call whisper.cpp wrapper - it handles WAV saving internally if path is given
                raw_result = whisper_cpp_transcribe(
                    audio_data=audio,
                    model_path=self.model_file_path,
                    main_path=self.whisper_cpp_path,
                    target_wav_path=target_wav_path, # Pass the optional path
                    language=self.language
                )
                
                # Check for errors returned by the wrapper
                if "error" in raw_result:
                    error_msg = f"Whisper.cpp transcription failed: {raw_result['error']}"
                    self.logger.error(error_msg)
                    raise ModelError(error_msg)
                    
                # --- Standardize whisper.cpp JSON output --- 
                # Adapted based on testing whisper.cpp -oj output structure
                segments = []
                full_text = ""
                
                cpp_segments = raw_result.get('transcription', []) 
                if isinstance(cpp_segments, list):
                    for i, seg_data in enumerate(cpp_segments):
                        # Extract text and timestamps (stored as lists within 'timestamps')
                        text = seg_data.get('text', '').strip()
                        timestamps = seg_data.get('timestamps', {})
                        start_ms = timestamps.get('from')
                        end_ms = timestamps.get('to')
                        
                        if start_ms is not None and end_ms is not None:
                            segments.append({
                                "id": i,
                                "seek": 0,
                                "start": start_ms / 1000.0,
                                "end": end_ms / 1000.0,
                                "text": text,
                                "tokens": [], 
                                "temperature": 0.0,
                                "avg_logprob": 0.0,
                                "compression_ratio": 0.0,
                                "no_speech_prob": 0.0
                            })
                        else:
                            self.logger.warning(f"Skipping segment due to missing timestamp data: {seg_data}")
                else:
                    self.logger.warning(f"Unexpected whisper.cpp transcription segment format: {cpp_segments}")
                
                # Reconstruct full text
                if segments:
                    full_text = " ".join([s['text'] for s in segments])
                
                # Get language (whisper.cpp JSON might not include it, fall back)
                detected_language = raw_result.get("params", {}).get("language", self.language)
                
                return TranscriptionResult({
                    "text": full_text,
                    "language": detected_language,
                    "segments": segments
                })
                # --- End Standardization --- 
            else:
                # --- Python Whisper Path --- 
                if self.model is None:
                    self._load_model()
                    
                self.logger.info(f"Transcribing with Python Whisper model: {self.model_name}")
                
                options = dict(language=self.language, word_timestamps=False) # Get segment timestamps
                if prompt:
                    options["initial_prompt"] = prompt
                # Add task based on translate flag (assuming config might have this)
                if self.config and self.config.get('transcription', {}).get('translate', False):
                    options["task"] = "translate"
                else:
                    options["task"] = "transcribe"

                audio_float32 = audio_data_np.astype(np.float32) / 32768.0

                # Perform transcription
                result: Dict[str, Any] = self.model.transcribe(audio_float32, **options)
                
                # --- Save WAV file (Only if path provided) --- 
                if target_wav_path:
                    try:
                        save_wav_file(target_wav_path, audio) # Save the original bytes
                        self.logger.info(f"Session audio saved to: {target_wav_path}")
                    except Exception as e:
                        # Log error but don't necessarily fail transcription if WAV save fails
                        self.logger.error(f"Failed to save WAV file after Python Whisper transcription: {e}")
                        # Raise specific error? Or just log?
                        # raise IOError(f"Failed to save WAV file: {e}") from e
                # --- End Save WAV --- 
                
                # Return standardized result
                if "text" not in result: result["text"] = ""
                if "language" not in result: result["language"] = self.language
                if "segments" not in result: result["segments"] = []
                return TranscriptionResult(result)
                
        except ModelError as e:
             self.logger.error(f"ModelError during transcription: {e}")
             raise
        except IOError as e:
             self.logger.error(f"IOError during transcription (likely WAV save): {e}")
             raise
        except Exception as e:
            exc_type, exc_value, exc_traceback = sys.exc_info()
            tb_detail = "".join(traceback.format_exception(exc_type, exc_value, exc_traceback))
            self.logger.error(f"Unexpected transcription error: {e}\n{tb_detail}")
            raise ModelError(f"Unexpected transcription failed: {e}") from e

    def get_available_languages(self) -> Dict[str, str]:
        """Get available languages for transcription."""
        if WHISPER_AVAILABLE:
            return {code: name for code, name in LANGUAGES.items()}
        else:
            self.logger.warning("Whisper library not installed, cannot provide language list.")
            return {}
        
    def get_model_info(self) -> Dict[str, Any]:
        """Get information about the loaded model."""
        info = {
            "name": self.model_name,
            "device": self.device,
            "loaded": self.loaded,
            "error": self.initialization_error,
            "using_cpp": self.use_cpp
        }
            
        if self.use_cpp:
            info["type"] = "whisper.cpp"
            info["path"] = self.model_file_path
        elif WHISPER_AVAILABLE and self.model:
            info["type"] = "whisper-python"
            if hasattr(self.model, "dims"):
                info["dimensions"] = self.model.dims
        else:
             info["type"] = "unknown (python whisper not loaded)"
            
        return info
        
    def set_language(self, language: str) -> None:
        """Set the transcription language."""
        if WHISPER_AVAILABLE:
            if language not in LANGUAGES and language not in TO_LANGUAGE_CODE:
                self.logger.warning(f"Invalid language code: {language}")
                return
        self.language = language
        self.logger.info(f"Transcription language set to: {self.language}")

    def test_model(self) -> Dict[str, Any]:
        """Test if the currently configured model can be loaded or used."""
        result: Dict[str, Any] = {"success": False, "error": None}
        
        try:
            if self.use_cpp:
                # Re-check paths from stored attributes if init succeeded
                if not self.whisper_cpp_path or not os.path.exists(self.whisper_cpp_path):
                    raise ModelError(f"whisper.cpp executable not found: {self.whisper_cpp_path}")
                if not self.model_file_path or not os.path.exists(self.model_file_path):
                    raise ModelError(f"GGML model file not found: {self.model_file_path}")
                
                result["success"] = True
                result["model_path"] = self.model_file_path
                result["whisper_cpp_path"] = self.whisper_cpp_path
                self.loaded = True 
                self.initialization_error = None
            else:
                if not WHISPER_AVAILABLE:
                    raise ModelError("Python Whisper library is not installed.")
                self._load_model()
                result["success"] = True
                result["device"] = self.device
        except Exception as e:
            result["success"] = False
            result["error"] = str(e)
            self.logger.error(f"Model test failed: {e}")
            self.loaded = False 
            self.initialization_error = str(e)
            
        return result