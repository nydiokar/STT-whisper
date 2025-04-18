from __future__ import annotations
import os
import logging
from logging.handlers import RotatingFileHandler

def setup_logging(log_dir: str | None = None) -> logging.Logger:
    """Set up logging configuration.
    
    Args:
        log_dir: Optional custom directory for log files
        
    Returns:
        logging.Logger: Configured logger instance
    """
    logger = logging.getLogger("VoiceService")
    logger.setLevel(logging.DEBUG)
    
    # Remove any existing handlers
    for handler in logger.handlers[:]:
        handler.close()
        logger.removeHandler(handler)
    
    # Set up log directory
    if log_dir is None:
        log_dir = os.path.join(os.path.expanduser("~"), "Documents", "Transcripts", "logs")
    os.makedirs(log_dir, exist_ok=True)
    
    # File handler
    log_file = os.path.join(log_dir, "voice_service.log")
    file_handler = RotatingFileHandler(
        log_file,
        maxBytes=5*1024*1024,  # 5MB
        backupCount=5,
        encoding='utf-8',
        delay=False  # Open the file immediately
    )
    file_handler.setLevel(logging.DEBUG)
    
    # Console handler
    console_handler = logging.StreamHandler()
    console_handler.setLevel(logging.INFO)
    
    # Formatter
    formatter = logging.Formatter(
        '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    )
    file_handler.setFormatter(formatter)
    console_handler.setFormatter(formatter)
    
    logger.addHandler(file_handler)
    logger.addHandler(console_handler)
    
    # Write an initial message to ensure file is created
    logger.debug("Logging initialized")
    
    # Force a flush to ensure messages are written
    for handler in logger.handlers:
        handler.flush()
    
    return logger 