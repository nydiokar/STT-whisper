"""Silence detection utility using Silero VAD."""
from __future__ import annotations
import numpy as np
from typing import Optional
import logging
import platform # Added platform import

# Import the Config class (adjust path if necessary)
from voice_input_service.config import Config 

# Conditional imports for Silero VAD
try:
    import torch
    # Check for MPS availability on Mac
    _IS_MAC = platform.system() == 'Darwin'
    _IS_ARM = platform.machine() == 'arm64' or platform.machine() == 'aarch64'
    # Check device based on availability
    if _IS_MAC and _IS_ARM and hasattr(torch.backends, 'mps') and torch.backends.mps.is_available():
         _DEVICE = "mps"
    elif torch.cuda.is_available():
         _DEVICE = "cuda"
    else:
         _DEVICE = "cpu"
    TORCH_AVAILABLE = True
except ImportError:
    TORCH_AVAILABLE = False
    _DEVICE = "cpu" # Default to CPU if torch isn't installed

# Global variable for Silero model and utils to load only once
SILERO_MODEL = None
SILERO_UTILS = None

logger = logging.getLogger("VoiceService.SilenceDetection")

class SilenceDetector:
    """Handles silence detection using the Silero VAD model."""
    
    def __init__(self, config: Config):
        """Initialize the Silero VAD detector using settings from Config.
        
        Args:
            config (Config): The application configuration object.
        """
        self.logger = logger
        self.config = config # Store config
        self.sample_rate = config.audio.sample_rate 
        self.vad_threshold = config.audio.vad_threshold
        
        self.silero_model = None
        self.silero_utils = None
        self._initialized = False

        if not TORCH_AVAILABLE:
            self.logger.critical("PyTorch is not available. Silero VAD cannot be initialized.")
            return
            
        # Ensure sample rate is supported by default Silero model (8k or 16k)
        if self.sample_rate not in [8000, 16000]:
             self.logger.warning(f"Silero VAD model used typically supports 8kHz or 16kHz. Current config rate is {self.sample_rate}Hz. VAD might not work as expected.")
             # Optionally, could force a supported rate or load a different model variant

        self._init_detector()

        # Calculate frame size (assuming 16-bit PCM) - common frame sizes for VAD
        self.frame_duration_ms = 30 # Silero examples often use 30ms
        self.frame_size_samples = int(self.sample_rate * (self.frame_duration_ms / 1000.0))
        # Bytes per sample (16-bit = 2 bytes)
        self.bytes_per_sample = 2
        self.frame_size_bytes = self.frame_size_samples * self.bytes_per_sample
        self.logger.debug(f"VAD Frame size: {self.frame_size_bytes} bytes ({self.frame_duration_ms}ms) at {self.sample_rate}Hz")
            
    def _init_detector(self) -> None:
        """Initialize the Silero VAD detector."""
        global SILERO_MODEL, SILERO_UTILS
        if self.silero_model:
             return # Already initialized

        try:
            if SILERO_MODEL is None:
                self.logger.info(f"Loading Silero VAD model (device: {_DEVICE})...")
                # torch.set_num_threads(1) # Recommended for Silero VAD
                torch.set_grad_enabled(False)
                
                # Choose model based on sample rate if needed (e.g., silero_vad_micro_8k)
                # Using default 'silero_vad' which supports 16k and possibly 8k
                model_name = 'silero_vad'
                # Add logic here if specific 8k model needed based on self.sample_rate

                model, utils = torch.hub.load(
                    repo_or_dir='snakers4/silero-vad',
                    model=model_name, 
                    force_reload=False, # Use cached model if available
                    onnx=False # Set to True if using ONNX runtime
                )
                # Move model to the determined device
                model.to(_DEVICE)
                
                SILERO_MODEL = model
                SILERO_UTILS = utils
                self.logger.info("Silero VAD model loaded successfully")
            
            self.silero_model = SILERO_MODEL
            self.silero_utils = SILERO_UTILS
            self._initialized = True
        except Exception as e:
            self.logger.critical(f"CRITICAL Error initializing Silero VAD: {e}", exc_info=True)
            self.silero_model = None
            self.silero_utils = None
            self._initialized = False
            # Optionally raise the exception to halt startup if VAD is critical
            # raise RuntimeError(f"Failed to initialize Silero VAD: {e}") from e
        
    def is_silent(self, audio_chunk: bytes) -> bool:
        """Determine if audio chunk is silent using Silero VAD.
        
        Args:
            audio_chunk: Raw audio bytes (16-bit PCM, matching sample_rate) to analyze.
                         Should ideally be a multiple of frame_size_bytes, but the underlying
                         model can handle variable lengths.
            
        Returns:
            True if audio is determined to be silent, False if speech is detected (or if VAD failed).
        """
        if not self._initialized or not self.silero_model or not self.silero_utils:
            self.logger.warning("Silero VAD not initialized, cannot perform silence detection. Assuming NOT silent.")
            return False # Fail safe: assume not silent if VAD isn't working
            
        if not audio_chunk:
            self.logger.debug("Received empty audio chunk, assuming silent.")
            return True

        # Silero expects Float32 Tensor
        try:
            audio_int16 = np.frombuffer(audio_chunk, dtype=np.int16)
            audio_float32 = audio_int16.astype(np.float32) / 32768.0
            audio_tensor = torch.from_numpy(audio_float32).to(_DEVICE)
            
            # Use the VAD model directly - it returns speech probability for the chunk
            speech_prob = self.silero_model(audio_tensor, self.sample_rate).item()
            
            # Use the threshold from the config
            is_speech = speech_prob >= self.vad_threshold 
            
            # Log sparingly, maybe only on transition or high probability?
            # self.logger.debug(f"VAD Result: Speech Prob={speech_prob:.3f}, Threshold={self.vad_threshold}, IsSpeech={is_speech}")
            
            return not is_speech # Return True if silent (i.e., not speech)
            
        except Exception as e:
            self.logger.error(f"Error during Silero VAD processing: {e}", exc_info=True)
            return False # Fail safe: assume not silent on error

    def update_settings(self) -> None:
        """Update VAD settings from the stored config object."""
        new_threshold = self.config.audio.vad_threshold
        if new_threshold != self.vad_threshold:
            if 0.0 <= new_threshold <= 1.0:
                self.vad_threshold = new_threshold
                self.logger.info(f"Updated Silero VAD threshold from config to: {self.vad_threshold}")
            else:
                 self.logger.warning(f"Invalid VAD threshold in config ignored: {new_threshold}. Must be between 0.0 and 1.0.")
        
        new_sample_rate = self.config.audio.sample_rate
        if new_sample_rate != self.sample_rate:
            self.logger.warning(f"Sample rate changed in config to {new_sample_rate}Hz. SilenceDetector requires re-initialization for this change to take effect.")
            # For simplicity, we don't re-initialize here, but ideally the app should handle this.
            # self.sample_rate = new_sample_rate 
            # # Re-calculate frame sizes etc. if needed
            # # Potentially re-load model if rate change requires it

    def close(self) -> None:
        """Clean up resources (optional, model is global)."""
        # Global model is kept, but could unload here if needed
        self.logger.debug("SilenceDetector closed (no explicit cleanup needed for global model).")
        pass 