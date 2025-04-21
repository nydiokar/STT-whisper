from __future__ import annotations
import os
import logging
from logging.handlers import RotatingFileHandler
import sys
from datetime import datetime
from pathlib import Path
from typing import Optional
import platform

class ColoredFormatter(logging.Formatter):
    """Custom formatter to add colors to logging levels"""
    
    # Windows terminals don't support ANSI color codes by default
    # We'll check for Windows and ensure colorama is initialized if it's available
    if platform.system() == 'Windows':
        try:
            import colorama
            colorama.init()
            COLORS_ENABLED = True
        except ImportError:
            COLORS_ENABLED = False
    else:
        COLORS_ENABLED = True
    
    # ANSI color codes
    COLORS = {
        'DEBUG': '\033[94m',  # Blue
        'INFO': '\033[92m',   # Green
        'WARNING': '\033[93m', # Yellow
        'ERROR': '\033[91m',  # Red
        'CRITICAL': '\033[91m\033[1m',  # Bold Red
        'RESET': '\033[0m',   # Reset to default
    }
    
    def format(self, record):
        log_fmt = '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
        formatter = logging.Formatter(log_fmt)
        
        if self.COLORS_ENABLED:
            levelname = record.levelname
            if levelname in self.COLORS:
                colored_levelname = f"{self.COLORS[levelname]}{levelname}{self.COLORS['RESET']}"
                record.levelname = colored_levelname
        
        return formatter.format(record)

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
    
    # Create console handler with colored output
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(log_level)
    colored_formatter = ColoredFormatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
    console_handler.setFormatter(colored_formatter)
    
    # Add console handler to logger
    logger.addHandler(console_handler)
    
    # Setup file handler if log_dir is provided or use default
    if log_dir is None:
        log_dir = Path.home() / ".voice_input_service" / "logs"
    
    # Ensure directory exists
    os.makedirs(log_dir, exist_ok=True)
    
    # Create file handler (no colors in file)
    log_file = os.path.join(log_dir, "voice_service.log")
    file_handler = RotatingFileHandler(
        log_file,
        maxBytes=5*1024*1024,  # 5MB
        backupCount=5,
        encoding='utf-8'
    )
    file_handler.setLevel(log_level)
    standard_formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
    file_handler.setFormatter(standard_formatter)
    
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