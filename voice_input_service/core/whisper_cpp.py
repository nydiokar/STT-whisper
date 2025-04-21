from __future__ import annotations
import os
import subprocess
import tempfile
import logging
from typing import Optional
import numpy as np
import time
import platform
from pathlib import Path

logger = logging.getLogger("VoiceService.Transcription.WhisperCPP")

class TempWavFile:
    """Context manager for temporary WAV files with automatic cleanup."""
    
    def __init__(self, audio_data: bytes, suffix: str = ".wav"):
        self.audio_data = audio_data
        self.suffix = suffix
        self.file_path = None
        
    def __enter__(self) -> str:
        """Create temporary file and write audio data.
        
        Returns:
            Path to the temporary file
        """
        fd, self.file_path = tempfile.mkstemp(suffix=self.suffix)
        os.close(fd)  # Close the file descriptor immediately
        
        # Write audio data to WAV file
        _write_wav_file(self.file_path, self.audio_data)
        
        return self.file_path
        
    def __exit__(self, exc_type, exc_val, exc_tb) -> None:
        """Clean up the temporary file."""
        self._cleanup()
        
    def _cleanup(self) -> None:
        """Clean up temporary files."""
        if self.file_path and os.path.exists(self.file_path):
            try:
                os.unlink(self.file_path)
                logger.debug(f"Deleted temporary file: {self.file_path}")
            except Exception as e:
                logger.warning(f"Failed to delete temporary file {self.file_path}: {e}")
        
        # Also clean up potential output file
        output_path = f"{self.file_path}.txt" if self.file_path else None
        if output_path and os.path.exists(output_path):
            try:
                os.unlink(output_path)
                logger.debug(f"Deleted output file: {output_path}")
            except Exception as e:
                logger.warning(f"Failed to delete output file {output_path}: {e}")

def transcribe(audio_data: bytes, 
               model_path: str,
               main_path: str,
               language: str = "en") -> str:
    """
    Transcribe audio using whisper.cpp as a subprocess.
    
    Args:
        audio_data: Raw audio bytes (16-bit PCM)
        model_path: Path to the whisper.cpp model file (.bin)
        main_path: Path to the whisper.cpp executable
        language: Language code for transcription
    
    Returns:
        Transcribed text
    """
    start_time = time.time()
    
    # Resolve paths to absolute paths
    project_root = Path(__file__).resolve().parent.parent.parent
    model_path = os.path.abspath(model_path)
    main_path = os.path.abspath(main_path)
    
    # Add .exe extension if on Windows and not already present
    if platform.system() == "Windows" and not main_path.lower().endswith('.exe'):
        main_path += '.exe'
    
    # Log paths for debugging
    logger.debug(f"Current directory: {os.getcwd()}")
    logger.debug(f"Model path: {model_path}")
    logger.debug(f"Whisper.cpp path: {main_path}")
    
    # Validate files exist
    if not os.path.exists(model_path):
        error_msg = f"Model file not found: {model_path}"
        logger.error(error_msg)
        return f"Error: {error_msg}"
        
    if not os.path.exists(main_path):
        error_msg = f"Whisper.cpp executable not found: {main_path}"
        logger.error(error_msg)
        return f"Error: {error_msg}"
    
    # Process audio using a context manager for cleanup
    with TempWavFile(audio_data) as temp_audio_path:
        # Create output file path
        output_path = f"{temp_audio_path}.txt"
        
        # Prepare command for whisper.cpp
        cmd = [
            main_path,
            "-m", model_path,
            "-f", temp_audio_path,
            "-l", language,
            "-of", output_path
        ]
        
        # Run whisper.cpp process
        logger.debug(f"Running command: {' '.join(cmd)}")
        
        try:
            logger.info(f"Starting whisper.cpp with model: {os.path.basename(model_path)}")
            
            process = subprocess.run(
                cmd,
                check=True,
                capture_output=True,
                text=True
            )
            
            elapsed_time = time.time() - start_time
            logger.info(f"whisper.cpp transcription completed in {elapsed_time:.2f} seconds")
            
            # Read the output file
            if os.path.exists(output_path):
                with open(output_path, "r", encoding="utf-8") as f:
                    result = f.read()
                    logger.debug(f"Transcription result: {result}")
                    return result
            else:
                logger.error(f"Output file not created: {output_path}")
                if process.stdout:
                    return process.stdout
                else:
                    return "Error: No output produced"
                    
        except subprocess.CalledProcessError as e:
            logger.error(f"Command failed with exit code {e.returncode}")
            logger.error(f"Command output: {e.stdout}")
            logger.error(f"Command stderr: {e.stderr}")
            return f"Error: Command failed: {e.stderr}"
        except Exception as e:
            logger.error(f"Error running whisper.cpp: {e}")
            return f"Error: {str(e)}"

def _write_wav_file(file_path: str, audio_data: bytes) -> None:
    """
    Write audio data to a WAV file
    
    Args:
        file_path: Path to save the WAV file
        audio_data: Raw audio bytes (16-bit PCM)
    """
    import wave
    
    # Parameters for the WAV file
    channels = 1
    sample_width = 2  # 16-bit
    framerate = 16000  # 16 kHz
    
    # Create WAV file
    with wave.open(file_path, 'wb') as wav_file:
        wav_file.setnchannels(channels)
        wav_file.setsampwidth(sample_width)
        wav_file.setframerate(framerate)
        wav_file.writeframes(audio_data)
        
    logger.debug(f"Audio data written to WAV file: {file_path}")