from __future__ import annotations
import os
import logging
from logging.handlers import RotatingFileHandler
import sys
from datetime import datetime
from typing import Optional

def setup_logging(log_level: int = logging.INFO) -> logging.Logger:
    """Setup application logging.
    
    Args:
        log_level: The logging level to use
        
    Returns:
        Root logger
    """
    # Create logger
    logger = logging.getLogger("VoiceService")
    logger.setLevel(log_level)
    
    # Create console handler
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(log_level)
    
    # Create formatter and add it to handlers
    formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
    console_handler.setFormatter(formatter)
    
    # Add handlers to logger
    logger.addHandler(console_handler)
    
    # Set specific component log levels
    logging.getLogger("VoiceService.Events").setLevel(logging.INFO)
    logging.getLogger("VoiceService.Worker").setLevel(logging.INFO)
    logging.getLogger("VoiceService.Audio").setLevel(logging.INFO)
    logging.getLogger("VoiceService.Transcription").setLevel(logging.INFO)
    logging.getLogger("VoiceService.UI").setLevel(logging.INFO)
    
    logger.debug("Logging initialized")
    return logger 