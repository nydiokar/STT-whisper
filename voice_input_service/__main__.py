import pyaudio
import sys
from .service import VoiceInputService

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

def main() -> None:
    """Main entry point for the voice input service."""
    if not check_microphone():
        print("\nMicrophone check failed. Please fix the issues and try again.")
        sys.exit(1)
        
    service = VoiceInputService()
    try:
        service.run()
    except KeyboardInterrupt:
        print("\nService stopped by user")
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    main()
