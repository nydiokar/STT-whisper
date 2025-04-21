"""Silence detection utility using only Silero VAD."""
import numpy as np
from typing import Optional
import logging

# Import the Config class
from voice_input_service.config import Config

# Conditional imports for Silero VAD
try:
    import torch
    TORCH_AVAILABLE = True
except ImportError:
    TORCH_AVAILABLE = False

# Global variable for Silero model and utils
SILERO_MODEL = None
SILERO_UTILS = None

logger = logging.getLogger("VoiceService.SilenceDetection")

class SilenceDetector:
    """Handles silence detection using the Silero VAD model."""
    
    def __init__(self, config: Config):
        """Initialize the Silero VAD detector."""
        self.logger = logger
        self.config = config
        self.sample_rate = config.audio.sample_rate
        self.vad_threshold = config.audio.vad_threshold
        
        self.silero_model = None
        self.silero_utils = None
        self._initialized = False

        if not TORCH_AVAILABLE:
            self.logger.critical("PyTorch is not available. Silero VAD cannot be initialized. Please install torch and torchaudio.")
            # Or raise an error: raise ImportError("PyTorch not found for Silero VAD")
            return
            
        self._init_detector()

        # Calculate frame size (assuming 16-bit PCM)
        self.frame_duration_ms = 30 
        self.frame_size = int(self.sample_rate * (self.frame_duration_ms / 1000.0) * 2)
        self.logger.debug(f"VAD Frame size: {self.frame_size} bytes ({self.frame_duration_ms}ms)")
            
    def _init_detector(self) -> None:
        """Initialize the Silero VAD detector."""
        global SILERO_MODEL, SILERO_UTILS
        if self.silero_model:
             return # Already initialized

        try:
            if SILERO_MODEL is None:
                self.logger.info("Loading Silero VAD model...")
                torch.set_grad_enabled(False)
                model, utils = torch.hub.load(
                    repo_or_dir='snakers4/silero-vad',
                    model='silero_vad',
                    force_reload=False, # Use cached model if available
                    onnx=False
                )
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
            # Optionally raise the exception to halt startup
            # raise RuntimeError(f"Failed to initialize Silero VAD: {e}") from e
        
    def is_silent(self, audio_data: bytes) -> bool:
        """Determine if audio is silent using Silero VAD.
        
        Args:
            audio_data: Raw audio bytes to analyze
            
        Returns:
            True if audio is silent, False if speech detected (or if VAD failed)
        """
        if not self._initialized or not self.silero_model or not self.silero_utils:
            self.logger.warning("Silero VAD not initialized, cannot perform silence detection. Assuming NOT silent.")
            return False # Fail safe: assume not silent if VAD isn't working
            
        try:
            # Check if audio data length is sufficient for processing
            # Silero VAD expects specific chunk sizes, but get_speech_timestamps handles variable lengths
            if len(audio_data) < self.frame_size: # Need at least one frame? Check Silero docs/usage
                 self.logger.debug(f"Audio data length ({len(audio_data)}) too short for VAD frame size ({self.frame_size}), assuming not silent.")
                 return False # Treat very short chunks as non-silent to avoid clipping
                 
            # Convert bytes to numpy array -> float tensor
            audio_array = np.frombuffer(audio_data, dtype=np.int16)
            audio_float32 = audio_array.astype(np.float32) / 32768.0
            audio_tensor = torch.from_numpy(audio_float32) # Convert to tensor
            
            # Get speech timestamps
            get_speech_timestamps = self.silero_utils[0]
            
            speech_timestamps = get_speech_timestamps(
                audio_tensor, # Use tensor input
                self.silero_model,
                threshold=self.vad_threshold,
                sampling_rate=self.sample_rate
            )
            
            # If we have any speech timestamps, the audio contains speech
            is_empty = len(speech_timestamps) == 0
            # Only log when speech IS detected to reduce noise
            if not is_empty:
                self.logger.debug(f"Silero VAD result: Speech DETECTED, Threshold={self.vad_threshold}, Length={len(audio_data)}")
            return is_empty
            
        except Exception as e:
            self.logger.error(f"Error during Silero VAD processing: {e}", exc_info=True)
            return False # Fail safe: assume not silent on error

    def update_settings(self, mode: Optional[str] = None, threshold: Optional[float] = None) -> None:
        """Update VAD settings.
        
        Args:
            mode: New VAD mode (currently ignored as only Silero is implemented)
            threshold: New Silero threshold (0.0-1.0)
        """
        changed = False
        # Mode change is ignored for now
        # if mode is not None and mode != self.mode:
        #     self.mode = mode
        #     changed = True
        #     self.logger.info(f"Updated VAD mode to: {self.mode}")

        if threshold is not None and threshold != self.vad_threshold:
            self.vad_threshold = threshold
            # Store in config object as well if needed for persistence via SettingsDialog
            if self.config:
                 self.config.audio.vad_threshold = threshold
            changed = True
            self.logger.info(f"Updated Silero VAD threshold to: {self.vad_threshold}")

        # No need to re-initialize the model for threshold changes
        # return changed # Can return bool if caller needs to know 