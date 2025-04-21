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
    
    # Save audio to a temporary WAV file
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as temp_audio:
        temp_audio_path = temp_audio.name
    
        # Write audio data to WAV file
        _write_wav_file(temp_audio_path, audio_data)
    
    try:
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
            
            logger.debug(f"Command output: {process.stdout}")
            if process.stderr:
                logger.warning(f"Command stderr: {process.stderr}")
                
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
    finally:
        # Clean up temporary files
        for path in [temp_audio_path, output_path]:
            if os.path.exists(path):
                try:
                    os.unlink(path)
                except Exception as e:
                    logger.warning(f"Failed to delete temporary file {path}: {e}")

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