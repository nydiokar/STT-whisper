"""Silence detection utilities."""
import numpy as np
from typing import Literal, Union, Optional, Any
import logging

# Import the Config class
from voice_input_service.config import Config

# Conditional imports
try:
    import webrtcvad
    WEBRTC_AVAILABLE = True
except ImportError:
    WEBRTC_AVAILABLE = False

try:
    import torch
    TORCH_AVAILABLE = True
except ImportError:
    TORCH_AVAILABLE = False

# Global variable for Silero model
SILERO_MODEL = None
SILERO_UTILS = None

logger = logging.getLogger("VoiceService.SilenceDetection")

class SilenceDetector:
    """Handles various methods of silence detection."""
    
    def __init__(
        self, 
        config: Optional[Config] = None,
        mode: Optional[Literal["basic", "webrtc", "silero"]] = None,
        sample_rate: Optional[int] = None,
        aggressiveness: Optional[int] = None,
        threshold: Optional[float] = None,
        silence_rms_threshold: Optional[int] = None
    ) -> None:
        """Initialize the silence detector.
        
        Args:
            config: Application configuration (preferred source)
            mode: Detection mode to use (fallback if config not provided)
            sample_rate: Audio sample rate in Hz (fallback)
            aggressiveness: WebRTC aggressiveness (0-3) (fallback)
            threshold: Silero threshold (0.0-1.0) (fallback)
            silence_rms_threshold: RMS threshold for basic detection (fallback)
        """
        # Use config if provided, otherwise use directly provided parameters
        if config is not None:
            self.mode = self._validate_mode(config.audio.vad_mode)
            self.sample_rate = config.audio.sample_rate
            self.aggressiveness = config.audio.vad_aggressiveness
            self.threshold = config.audio.vad_threshold
            self.silence_rms_threshold = int(config.audio.silence_threshold * 10000)  # Convert to appropriate scale
            self.config = config  # Store for later updates
        else:
            # Fallback to directly provided parameters
            self.mode = self._validate_mode(mode or "basic")
            self.sample_rate = sample_rate or 16000
            self.aggressiveness = min(3, max(0, aggressiveness or 3))
            self.threshold = threshold or 0.5
            self.silence_rms_threshold = silence_rms_threshold or 400
            self.config = None
        
        # Initialize the appropriate detector
        self.detector = self._init_detector()
        
    def _validate_mode(self, mode: str) -> str:
        """Validate and adjust mode based on available modules."""
        if mode == "webrtc" and not WEBRTC_AVAILABLE:
            logger.warning("WebRTC VAD not available. Install with: pip install webrtcvad. Falling back to basic.")
            return "basic"
        elif mode == "silero" and not TORCH_AVAILABLE:
            logger.warning("Silero VAD requires PyTorch. Install with: pip install torch. Falling back to basic.")
            return "basic"
        return mode
    
    def _init_detector(self) -> Optional[Union[Any, object]]:
        """Initialize the detector based on selected mode."""
        if self.mode == "webrtc":
            try:
                vad = webrtcvad.Vad()
                vad.set_mode(self.aggressiveness)
                logger.info(f"Initialized WebRTC VAD with aggressiveness {self.aggressiveness}")
                return vad
            except Exception as e:
                logger.error(f"Error initializing WebRTC VAD: {e}")
                self.mode = "basic"
                return None
        
        elif self.mode == "silero":
            try:
                global SILERO_MODEL, SILERO_UTILS
                if SILERO_MODEL is None:
                    logger.info("Loading Silero VAD model...")
                    torch.set_grad_enabled(False)
                    
                    # Download and load the model
                    model, utils = torch.hub.load(
                        repo_or_dir='snakers4/silero-vad',
                        model='silero_vad',
                        force_reload=False,
                        onnx=False
                    )
                    
                    SILERO_MODEL = model
                    SILERO_UTILS = utils
                    logger.info("Silero VAD model loaded successfully")
                
                return SILERO_MODEL
            except Exception as e:
                logger.error(f"Error initializing Silero VAD: {e}")
                self.mode = "basic"
                return None
        
        return None  # Basic mode doesn't need a detector object
    
    def is_silent(self, audio_data: bytes) -> bool:
        """Determine if audio is silent using configured detector.
        
        Args:
            audio_data: Raw audio bytes to analyze
            
        Returns:
            True if audio is silent, False if speech detected
        """
        if self.mode == "basic":
            return self._is_silent_basic(audio_data)
        elif self.mode == "webrtc":
            return self._is_silent_webrtc(audio_data)
        elif self.mode == "silero":
            return self._is_silent_silero(audio_data)
        
        # Fallback to basic if mode is invalid
        return self._is_silent_basic(audio_data)
    
    def _is_silent_basic(self, audio_data: bytes) -> bool:
        """Use basic RMS-based silence detection.
        
        Args:
            audio_data: Raw audio bytes
            
        Returns:
            True if silent, False otherwise
        """
        # Convert bytes to numpy array
        try:
            # Convert to 16-bit int array
            audio_array = np.frombuffer(audio_data, dtype=np.int16)
            
            # Calculate RMS
            rms = np.sqrt(np.mean(np.square(audio_array.astype(np.float32))))
            
            # Check against threshold
            return rms < self.silence_rms_threshold
        except Exception as e:
            logger.error(f"Error in basic silence detection: {e}")
            return True  # Default to silent on error
    
    def _is_silent_webrtc(self, audio_data: bytes) -> bool:
        """Use WebRTC for silence detection.
        
        Args:
            audio_data: Raw audio bytes
            
        Returns:
            True if silent, False otherwise
        """
        if not self.detector:
            return self._is_silent_basic(audio_data)
            
        try:
            # WebRTC requires specific frame sizes (10, 20, or 30ms)
            frame_duration_ms = 30
            samples_per_frame = int(self.sample_rate * frame_duration_ms / 1000)
            bytes_per_frame = samples_per_frame * 2  # 16-bit = 2 bytes per sample
            
            # Process frames
            offset = 0
            speech_frames = 0
            silent_frames = 0
            
            while offset + bytes_per_frame <= len(audio_data):
                frame = audio_data[offset:offset + bytes_per_frame]
                offset += bytes_per_frame
                
                # Check for speech in this frame
                is_speech = self.detector.is_speech(frame, self.sample_rate)
                if is_speech:
                    speech_frames += 1
                else:
                    silent_frames += 1
            
            # Consider silent if fewer than 25% of frames contain speech
            if speech_frames + silent_frames == 0:
                return True
                
            speech_ratio = speech_frames / (speech_frames + silent_frames)
            return speech_ratio < 0.25
            
        except Exception as e:
            logger.error(f"Error in WebRTC silence detection: {e}")
            return self._is_silent_basic(audio_data)
    
    def _is_silent_silero(self, audio_data: bytes) -> bool:
        """Use Silero VAD for silence detection.
        
        Args:
            audio_data: Raw audio bytes
            
        Returns:
            True if silent, False otherwise
        """
        if not self.detector or not SILERO_UTILS:
            return self._is_silent_basic(audio_data)
            
        try:
            # Convert bytes to numpy array
            audio_array = np.frombuffer(audio_data, dtype=np.int16)
            audio_float = audio_array.astype(np.float32) / 32768.0  # Normalize to [-1, 1]
            
            # Get speech timestamps
            get_speech_timestamps = SILERO_UTILS[0]
            
            speech_timestamps = get_speech_timestamps(
                audio_float, 
                self.detector,
                threshold=self.threshold,
                sampling_rate=self.sample_rate
            )
            
            # If we have any speech timestamps, the audio contains speech
            return len(speech_timestamps) == 0
            
        except Exception as e:
            logger.error(f"Error in Silero silence detection: {e}")
            return self._is_silent_basic(audio_data)
    
    def update_from_config(self, config: Config) -> bool:
        """Update detector settings from Config object.
        
        Args:
            config: Application configuration
            
        Returns:
            True if settings were changed, False otherwise
        """
        changed = False
        
        # Get new values from config
        new_mode = self._validate_mode(config.audio.vad_mode)
        new_aggressiveness = config.audio.vad_aggressiveness
        new_threshold = config.audio.vad_threshold
        new_silence_rms = int(config.audio.silence_threshold * 10000)  # Convert to appropriate scale
        
        # Check if any settings changed
        if new_mode != self.mode:
            self.mode = new_mode
            changed = True
            
        if new_aggressiveness != self.aggressiveness:
            self.aggressiveness = new_aggressiveness
            changed = True
            
        if new_threshold != self.threshold:
            self.threshold = new_threshold
            changed = True
            
        if new_silence_rms != self.silence_rms_threshold:
            self.silence_rms_threshold = new_silence_rms
            changed = True
        
        # Store the config
        self.config = config
            
        # Reinitialize detector if settings changed
        if changed:
            self.detector = self._init_detector()
            logger.info(f"Updated silence detector settings: mode={self.mode}, "
                     f"aggressiveness={self.aggressiveness}, threshold={self.threshold}")
            
        return changed
            
    def update_settings(
        self, 
        mode: Optional[str] = None,
        aggressiveness: Optional[int] = None,
        threshold: Optional[float] = None,
        silence_threshold: Optional[int] = None
    ) -> None:
        """Update detector settings directly (legacy method).
        
        Args:
            mode: New detection mode
            aggressiveness: New WebRTC aggressiveness
            threshold: New Silero threshold
            silence_threshold: New RMS threshold for basic detection
        """
        # Use the config-based update if available, otherwise update directly
        if self.config is not None:
            # Update the config
            if mode is not None:
                self.config.audio.vad_mode = mode
            if aggressiveness is not None:
                self.config.audio.vad_aggressiveness = aggressiveness
            if threshold is not None:
                self.config.audio.vad_threshold = threshold
            if silence_threshold is not None:
                self.config.audio.silence_threshold = silence_threshold / 10000  # Convert to config scale
                
            # Apply the updates
            self.update_from_config(self.config)
            return
            
        # Direct update (legacy path)
        changed = False
        
        if mode is not None and mode != self.mode:
            self.mode = self._validate_mode(mode)
            changed = True
            
        if aggressiveness is not None and aggressiveness != self.aggressiveness:
            self.aggressiveness = min(3, max(0, aggressiveness))
            changed = True
            
        if threshold is not None and threshold != self.threshold:
            self.threshold = threshold
            changed = True
            
        if silence_threshold is not None and silence_threshold != self.silence_rms_threshold:
            self.silence_rms_threshold = silence_threshold
            changed = True
            
        # Reinitialize detector if settings changed
        if changed:
            self.detector = self._init_detector() 