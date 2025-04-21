import pyaudio
import sys
import logging
from .service import VoiceInputService
from .core.config import Config
from .core.model_manager import ModelManager
from .ui.window import TranscriptionUI
from .utils.logging import setup_logging

def check_microphone() -> bool:
    """Pre-flight check of microphone access."""
    print("Checking microphone access...")
    try:
        audio = pyaudio.PyAudio()
        try:
            # Try to get default input device
            info = audio.get_default_input_device_info()
            print(f"Found microphone: {info['name']}")
            
            # Try to open a test stream
            stream = audio.open(
                format=pyaudio.paInt16,
                channels=1,
                rate=16000,
                input=True,
                frames_per_buffer=1024,
                start=False
            )
            stream.start_stream()
            stream.read(1024)  # Try one read
            stream.stop_stream()
            stream.close()
            return True
            
        except Exception as e:
            print("\nError accessing microphone:")
            print("1. Make sure your microphone is connected")
            print("2. Open Windows Settings > Privacy > Microphone")
            print("3. Enable microphone access for apps")
            print("4. Try running terminal as administrator")
            print(f"\nTechnical error: {str(e)}")
            return False
            
        finally:
            audio.terminate()
            
    except Exception as e:
        print(f"\nFailed to initialize audio system: {str(e)}")
        return False

def initialize_app() -> tuple[Config, TranscriptionUI, ModelManager]:
    """Initialize application components.
    
    Returns:
        Tuple of config, UI, and model manager
    
    Raises:
        Exception: If initialization fails
    """
    # Setup logging first
    logger = setup_logging()
    logger.info("Application starting - initializing components")
    
    # Load configuration
    config = Config.load()
    logger.info("Configuration loaded")
    
    # Initialize UI first (needed for model dialogs)
    ui = TranscriptionUI()
    logger.debug("UI components initialized")
    
    # Initialize ModelManager
    model_manager = ModelManager(ui.window, config)
    logger.debug("Model manager initialized")
    
    return config, ui, model_manager

def main() -> None:
    """Main entry point for the voice input service."""
    if not check_microphone():
        print("\nMicrophone check failed. Please fix the issues and try again.")
        sys.exit(1)
    
    try:
        # Step 1: Initialize core components
        config, ui, model_manager = initialize_app()
        
        # Step 2: Initialize transcription engine
        logger = logging.getLogger("VoiceService")
        logger.info("Loading transcription engine")
        transcriber = model_manager.initialize_transcription_engine()
        if not transcriber:
            logger.error("Transcription engine initialization failed - no model selected")
            print("\nNo transcription model was selected or model initialization failed.")
            print("Please run the application again and select a model.")
            sys.exit(1)
            
        # Step 3: Create and run service
        logger.info("Creating voice input service")
        service = VoiceInputService(config, ui, transcriber)
        service.run()
        
    except KeyboardInterrupt:
        print("\nService stopped by user")
    except Exception as e:
        logger = logging.getLogger("VoiceService")
        logger.error(f"Startup error: {e}", exc_info=True)
        print(f"\nError: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
