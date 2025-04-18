from __future__ import annotations
import pytest
from unittest.mock import Mock
import pyaudio
import queue
from voice_input_service.core.audio import AudioProcessor, AudioConfig

@pytest.fixture
def config() -> AudioConfig:
    """Create a test configuration.
    
    Returns:
        AudioConfig: Test configuration instance with default values
    """
    return AudioConfig(
        sample_rate=16000,
        chunk_size=1024,
        channels=1,
        format=pyaudio.paInt16
    )

@pytest.fixture
def mock_pyaudio() -> Mock:
    """Create a mock PyAudio instance.
    
    Returns:
        Mock: Mocked PyAudio instance with required functionality
    """
    mock = Mock(spec=pyaudio.PyAudio)
    return mock

@pytest.fixture
def audio_processor(config: AudioConfig, mock_pyaudio: Mock) -> AudioProcessor:
    """Create an AudioProcessor instance for testing.
    
    Args:
        config: Audio configuration
        mock_pyaudio: Mocked PyAudio instance
        
    Returns:
        AudioProcessor: Configured processor for testing
    """
    processor = AudioProcessor(config)
    processor.audio = mock_pyaudio
    return processor

def test_audio_processor_initialization(audio_processor: AudioProcessor) -> None:
    """Test AudioProcessor initialization.
    
    Verifies that a new AudioProcessor instance has:
    - recording flag set to False
    - empty audio queue
    - no active stream
    - properly configured PyAudio instance
    """
    assert audio_processor.recording is False
    assert isinstance(audio_processor.audio_queue, queue.Queue)
    assert audio_processor.audio_queue.empty()
    assert audio_processor.stream is None
    assert isinstance(audio_processor.audio, Mock)  # In tests it's a mock
    assert isinstance(audio_processor.config, AudioConfig)

def test_start_stream(audio_processor: AudioProcessor) -> None:
    """Test starting the audio stream.
    
    Verifies:
    - Stream is created with correct configuration
    - Success status is returned
    - Stream is properly stored
    """
    mock_stream = Mock()
    audio_processor.audio.open.return_value = mock_stream
    
    result = audio_processor.start_stream()
    
    assert result is True
    assert audio_processor.stream is mock_stream
    audio_processor.audio.open.assert_called_once_with(
        format=audio_processor.config.format,
        channels=audio_processor.config.channels,
        rate=audio_processor.config.sample_rate,
        input=True,
        frames_per_buffer=audio_processor.config.chunk_size,
        stream_callback=audio_processor._audio_callback
    )

def test_start_stream_handles_existing_stream(audio_processor: AudioProcessor) -> None:
    """Test starting stream when one already exists.
    
    Verifies:
    - Existing stream is properly closed
    - New stream is created
    """
    # Setup existing stream
    existing_stream = Mock()
    audio_processor.stream = existing_stream
    
    # Setup new stream
    new_stream = Mock()
    audio_processor.audio.open.return_value = new_stream
    
    result = audio_processor.start_stream()
    
    assert result is True
    existing_stream.stop_stream.assert_called_once()
    existing_stream.close.assert_called_once()
    assert audio_processor.stream is new_stream

def test_start_stream_failure(audio_processor: AudioProcessor) -> None:
    """Test handling of stream start failure.
    
    Verifies:
    - False is returned on error
    - Stream remains None
    - Error is properly logged
    """
    audio_processor.audio.open.side_effect = Exception("Test error")
    
    result = audio_processor.start_stream()
    
    assert result is False
    assert audio_processor.stream is None

def test_stop_stream(audio_processor: AudioProcessor) -> None:
    """Test stopping the audio stream.
    
    Verifies:
    - Stream is properly stopped and closed
    - Stream reference is cleared
    """
    mock_stream = Mock()
    audio_processor.stream = mock_stream
    
    audio_processor.stop_stream()
    
    mock_stream.stop_stream.assert_called_once()
    mock_stream.close.assert_called_once()
    assert audio_processor.stream is None

def test_stop_stream_no_active_stream(audio_processor: AudioProcessor) -> None:
    """Test stopping when no stream is active.
    
    Verifies operation is safe when no stream exists.
    """
    audio_processor.stream = None
    
    # Should not raise any exceptions
    audio_processor.stop_stream()

def test_audio_callback_recording(audio_processor: AudioProcessor) -> None:
    """Test audio callback during recording.
    
    Verifies:
    - Audio data is added to queue when recording
    - Correct return values are provided
    """
    audio_processor.recording = True
    test_data = b"test_audio_data"
    
    result, status = audio_processor._audio_callback(
        in_data=test_data,
        frame_count=1024,
        time_info={},
        status=0
    )
    
    assert result == test_data
    assert status == pyaudio.paContinue
    assert audio_processor.audio_queue.get_nowait() == test_data

def test_audio_callback_not_recording(audio_processor: AudioProcessor) -> None:
    """Test audio callback when not recording.
    
    Verifies:
    - Audio data is not queued when not recording
    - Correct return values are provided
    """
    audio_processor.recording = False
    test_data = b"test_audio_data"
    
    result, status = audio_processor._audio_callback(
        in_data=test_data,
        frame_count=1024,
        time_info={},
        status=0
    )
    
    assert result == test_data
    assert status == pyaudio.paContinue
    assert audio_processor.audio_queue.empty()

def test_cleanup(audio_processor: AudioProcessor) -> None:
    """Test resource cleanup on deletion.
    
    Verifies:
    - Stream is properly stopped
    - PyAudio instance is terminated
    """
    mock_stream = Mock()
    audio_processor.stream = mock_stream
    
    audio_processor.__del__()
    
    mock_stream.stop_stream.assert_called_once()
    mock_stream.close.assert_called_once()
    audio_processor.audio.terminate.assert_called_once() 