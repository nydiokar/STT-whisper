from __future__ import annotations
import os
import subprocess
import tempfile
import logging
from typing import Optional, Dict, List, Any
import numpy as np
import time
import platform
from pathlib import Path
import json # Added for parsing JSON output
import wave # Added for saving WAV

logger = logging.getLogger("VoiceService.Transcription.WhisperCPP")

# Removed TempWavFile context manager as we save permanently now

def save_wav_file(file_path: str, audio_data: bytes) -> None:
    """
    Write audio data to a WAV file.
    
    Args:
        file_path: Path to save the WAV file.
        audio_data: Raw audio bytes (16-bit PCM, 16kHz mono assumed).
    """
    # Parameters for the WAV file (assuming Whisper standard)
    channels = 1
    sample_width = 2  # 16-bit
    framerate = 16000  # 16 kHz
    
    try:
        with wave.open(file_path, 'wb') as wav_file:
            wav_file.setnchannels(channels)
            wav_file.setsampwidth(sample_width)
            wav_file.setframerate(framerate)
            wav_file.writeframes(audio_data)
        logger.debug(f"Audio data written to WAV file: {file_path}")
    except Exception as e:
        logger.error(f"Failed to write WAV file {file_path}: {e}")
        raise # Re-raise the exception

def transcribe(
    audio_data: bytes, 
    model_path: str, 
    main_path: str, 
    target_wav_path: Optional[str] = None, # Made optional
    language: str = "en"
) -> Dict[str, Any]:
    """
    Transcribes using whisper.cpp (JSON output). Optionally saves audio first if target_wav_path is provided.
    
    Args:
        audio_data: Raw audio bytes (16-bit PCM, 16kHz mono).
        model_path: Path to the whisper.cpp model file (.bin).
        main_path: Path to the whisper.cpp executable.
        target_wav_path: Optional full path to save the session audio WAV file.
        language: Language code for transcription.
    
    Returns:
        Dictionary containing transcription results or {"error": "..."}.
        
    Raises:
        IOError: If saving the WAV file fails (when path is provided).
    """
    start_time = time.time()
    input_file_path = None # Path to the audio file whisper.cpp will read
    temp_file_created = False

    # --- Handle Audio Input: Save Permanently or Use Temp File --- 
    if target_wav_path:
        # Save permanently if path is provided
        try:
            save_wav_file(target_wav_path, audio_data)
            logger.info(f"Session audio saved to: {target_wav_path}")
            input_file_path = target_wav_path
        except Exception as e:
            raise IOError(f"Failed to save WAV file before transcription: {e}") from e
    else:
        # Transcribing intermediate chunk, use a temporary WAV file
        try:
            fd, temp_path = tempfile.mkstemp(suffix='.wav')
            os.close(fd)
            save_wav_file(temp_path, audio_data)
            input_file_path = temp_path
            temp_file_created = True
            logger.debug(f"Using temporary WAV file for transcription: {input_file_path}")
        except Exception as e:
            logger.error(f"Failed to create/write temporary WAV file: {e}")
            return {"error": "Failed to create temporary audio file"}
    # --- End Audio Input Handling --- 

    # Resolve executable and model paths
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
        # Clean up temp file if created
        if temp_file_created and input_file_path and os.path.exists(input_file_path):
             try: os.remove(input_file_path) 
             except OSError: pass
        return {"error": error_msg}
        
    if not os.path.exists(main_path):
        error_msg = f"Whisper.cpp executable not found: {main_path}"
        logger.error(error_msg)
        # Clean up temp file if created
        if temp_file_created and input_file_path and os.path.exists(input_file_path):
             try: os.remove(input_file_path) 
             except OSError: pass
        return {"error": error_msg}

    # --- Run Transcription --- 
    cmd = [
        main_path,
        "-m", model_path,
        "-f", input_file_path, # Use the saved permanent or temporary WAV
        "-l", language,
        "-oj" # Output JSON format (to file)
    ]
    
    logger.debug(f"Running command: {' '.join(cmd)}")
    result_data: Dict[str, Any] = {}
    output_json_path = input_file_path + ".json" # Expected output path

    try:
        logger.info(f"Starting whisper.cpp with model: {os.path.basename(model_path)}")
        
        process = subprocess.run(
            cmd,
            check=True,
            capture_output=True, 
            text=True,
            cwd=os.path.dirname(input_file_path) # Run in the output directory
        )
        
        elapsed_time = time.time() - start_time
        logger.info(f"whisper.cpp transcription completed in {elapsed_time:.2f} seconds")

        # --- Parse JSON Output --- 
        if os.path.exists(output_json_path):
            try:
                with open(output_json_path, "r", encoding="utf-8") as f:
                    result_data = json.load(f)
                logger.debug(f"Successfully loaded transcription JSON from: {output_json_path}")
            except json.JSONDecodeError as e_json:
                logger.error(f"Failed to parse whisper.cpp JSON output from {output_json_path}: {e_json}")
                result_data = {"error": f"Failed to parse JSON output: {e_json}"}
            except OSError as e_os:
                 logger.error(f"Error reading whisper.cpp JSON output file {output_json_path}: {e_os}")
                 result_data = {"error": f"Error accessing JSON output file: {e_os}"}
        else:
            logger.error(f"whisper.cpp did not produce expected JSON output file: {output_json_path}")
            stderr_output = process.stderr.strip()
            if stderr_output:
                 logger.error(f"whisper.cpp stderr: {stderr_output}")
                 result_data = {"error": f"No JSON output. Stderr: {stderr_output}"}
            else:
                 result_data = {"error": "No JSON output and no stderr from whisper.cpp."}

    except subprocess.CalledProcessError as e:
        logger.error(f"whisper.cpp command failed with exit code {e.returncode}")
        logger.error(f"Command stdout: {e.stdout}")
        logger.error(f"Command stderr: {e.stderr}")
        result_data = {"error": f"Command failed: {e.stderr or e.stdout}"}
    except Exception as e:
        logger.error(f"Error running whisper.cpp: {e}", exc_info=True) # Added exc_info
        result_data = {"error": f"Unexpected error: {str(e)}"}
    finally:
        # Clean up whisper.cpp output JSON if it exists
        if os.path.exists(output_json_path):
           try: 
               os.remove(output_json_path)
               logger.debug(f"Cleaned up whisper.cpp output JSON: {output_json_path}")
           except OSError: pass
           
        # Clean up temp input WAV file if we created one
        if temp_file_created and input_file_path and os.path.exists(input_file_path):
            try: 
                os.remove(input_file_path)
                logger.debug(f"Cleaned up temporary input WAV: {input_file_path}")
            except OSError: pass
           
    return result_data

# Removed _write_wav_file as it's replaced by save_wav_file