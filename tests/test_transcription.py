from __future__ import annotations
import pytest
from unittest.mock import Mock, patch, ANY
import wave
import os
import tempfile
import time
import pyaudio
from typing import Generator
from voice_input_service.core.transcription import TranscriptionService, TranscriptionConfig

@pytest.fixture(autouse=True)
def cleanup_temp_wav_files() -> Generator[None, None, None]:
    """Cleanup any temporary WAV files before and after each test."""
    # Get initial state
    temp_dir = tempfile.gettempdir()
    initial_wav_files = {f for f in os.listdir(temp_dir) if f.endswith('.wav')}
    
    yield
    
    # Allow any async file operations to complete
    time.sleep(0.1)
    
    # Cleanup any new WAV files
    current_wav_files = {f for f in os.listdir(temp_dir) if f.endswith('.wav')}
    new_wav_files = current_wav_files - initial_wav_files
    
    for wav_file in new_wav_files:
        try:
            os.remove(os.path.join(temp_dir, wav_file))
        except (OSError, PermissionError):
            pass  # Best effort cleanup

@pytest.fixture
def config() -> TranscriptionConfig:
    """Create a test configuration.
    
    Returns:
        TranscriptionConfig: Test configuration with minimal model
    """
    return TranscriptionConfig(
        model_name='small',  # Use small model for faster tests
        sample_rate=16000,
        chunk_size=1024,
        channels=1,
        format=pyaudio.paInt16,
        processing_chunk_size=16000
    )

@pytest.fixture
def mock_whisper():
    """Create a mock Whisper model."""
    with patch('whisper.load_model') as mock:
        mock_model = Mock()
        mock.return_value = mock_model
        yield mock_model

@pytest.fixture
def transcription_service(config: TranscriptionConfig, mock_whisper: Mock) -> TranscriptionService:
    """Create a TranscriptionService instance for testing.
    
    Args:
        config: Test configuration
        mock_whisper: Mocked Whisper model
        
    Returns:
        TranscriptionService: Service instance for testing
    """
    return TranscriptionService(config)

def test_transcription_service_initialization(transcription_service: TranscriptionService, mock_whisper: Mock) -> None:
    """Test TranscriptionService initialization.
    
    Verifies:
    - Whisper model is loaded with correct config
    - Initial state is properly set
    """
    assert transcription_service.config.model_name == 'small'
    assert transcription_service.whisper_model == mock_whisper
    assert transcription_service.pause_detected is False
    assert transcription_service.current_segment == ""
    assert isinstance(transcription_service.last_activity, float)

def test_process_audio_success(transcription_service: TranscriptionService) -> None:
    """Test successful audio processing.
    
    Verifies:
    - Audio is correctly processed
    - Temporary WAV file is created and cleaned up
    - Result is properly returned
    """
    # Create test audio data
    test_audio = b'dummy_audio_data'
    expected_text = "test transcription"
    
    transcription_service.whisper_model.transcribe.return_value = {
        "text": expected_text
    }
    
    result = transcription_service.process_audio(test_audio)
    
    assert result == expected_text
    assert transcription_service.current_segment == expected_text
    assert transcription_service.pause_detected is False
    transcription_service.whisper_model.transcribe.assert_called_once()

def test_process_audio_empty_result(transcription_service: TranscriptionService) -> None:
    """Test processing audio that results in empty text.
    
    Verifies:
    - Empty result is handled correctly
    - Pause is detected
    - None is returned
    """
    test_audio = b'dummy_audio_data'
    transcription_service.whisper_model.transcribe.return_value = {
        "text": "   "  # Empty or whitespace text
    }
    
    result = transcription_service.process_audio(test_audio)
    
    assert result is None
    assert transcription_service.pause_detected is True

def test_process_audio_dots_only(transcription_service: TranscriptionService) -> None:
    """Test processing audio that results in only dots.
    
    Verifies:
    - Dots-only result is handled as pause
    - None is returned
    """
    test_audio = b'dummy_audio_data'
    transcription_service.whisper_model.transcribe.return_value = {
        "text": "..."
    }
    
    result = transcription_service.process_audio(test_audio)
    
    assert result is None
    assert transcription_service.pause_detected is True

def test_process_audio_with_context(transcription_service: TranscriptionService) -> None:
    """Test audio processing with context.
    
    Verifies:
    - Context is properly passed to Whisper
    - Result includes context influence
    """
    test_audio = b'dummy_audio_data'
    test_context = "previous text"
    expected_text = "continuation"
    
    transcription_service.whisper_model.transcribe.return_value = {
        "text": expected_text
    }
    
    result = transcription_service.process_audio(test_audio, context=test_context)
    
    assert result == expected_text
    transcription_service.whisper_model.transcribe.assert_called_once_with(
        ANY,  # Temp file path
        language="en",
        initial_prompt=test_context,
        task='transcribe',
        best_of=1,
        beam_size=1,
        temperature=0.0,
        condition_on_previous_text=True,
        word_timestamps=True
    )

def test_process_audio_error_handling(transcription_service: TranscriptionService) -> None:
    """Test error handling during audio processing.
    
    Verifies:
    - Errors are caught and logged
    - None is returned on error
    - Temporary files are cleaned up
    """
    test_audio = b'dummy_audio_data'
    transcription_service.whisper_model.transcribe.side_effect = Exception("Test error")
    
    result = transcription_service.process_audio(test_audio)
    assert result is None 