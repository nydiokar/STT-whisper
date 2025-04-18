from __future__ import annotations
import os
import logging
from logging.handlers import RotatingFileHandler
import sys
from datetime import datetime
from pathlib import Path
from typing import Optional

def setup_logging(log_dir: Optional[str] = None, log_level: int = logging.DEBUG) -> logging.Logger:
    """Setup application logging.
    
    Args:
        log_dir: Directory to store log files (None for default)
        log_level: The logging level to use
        
    Returns:
        Root logger
    """
    # Create logger
    logger = logging.getLogger("VoiceService")
    logger.setLevel(log_level)
    
    # Clear existing handlers to avoid duplicates
    for handler in logger.handlers[:]:
        logger.removeHandler(handler)
    
    # Create console handler
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(log_level)
    
    # Create formatter and add it to handlers
    formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
    console_handler.setFormatter(formatter)
    
    # Add console handler to logger
    logger.addHandler(console_handler)
    
    # Setup file handler if log_dir is provided or use default
    if log_dir is None:
        log_dir = Path.home() / ".voice_input_service" / "logs"
    
    # Ensure directory exists
    os.makedirs(log_dir, exist_ok=True)
    
    # Create file handler
    log_file = os.path.join(log_dir, "voice_service.log")
    file_handler = RotatingFileHandler(
        log_file,
        maxBytes=5*1024*1024,  # 5MB
        backupCount=5
    )
    file_handler.setLevel(log_level)
    file_handler.setFormatter(formatter)
    
    # Add file handler to logger
    logger.addHandler(file_handler)
    
    # Set specific component log levels
    logging.getLogger("VoiceService.Events").setLevel(logging.INFO)
    logging.getLogger("VoiceService.Worker").setLevel(logging.INFO)
    logging.getLogger("VoiceService.Audio").setLevel(logging.INFO)
    logging.getLogger("VoiceService.Transcription").setLevel(logging.INFO)
    logging.getLogger("VoiceService.UI").setLevel(logging.INFO)
    
    logger.debug("Logging initialized")
    return logger 