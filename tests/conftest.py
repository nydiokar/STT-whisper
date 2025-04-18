from __future__ import annotations
import pytest
import logging
import tempfile
import os
from pathlib import Path
from typing import Generator, Any
from unittest.mock import Mock, patch

# Disable logging during tests
@pytest.fixture(autouse=True)
def disable_logging():
    logging.disable(logging.CRITICAL)
    yield
    logging.disable(logging.NOTSET)

@pytest.fixture
def temp_dir() -> Generator[Path, Any, None]:
    """Create a temporary directory for test files."""
    with tempfile.TemporaryDirectory() as tmp_dir:
        yield Path(tmp_dir)

@pytest.fixture
def test_data_dir() -> Path:
    """Return path to test data directory."""
    return Path(__file__).parent / "test_data"

@pytest.fixture(autouse=True)
def mock_environment(monkeypatch: pytest.MonkeyPatch):
    """Mock environment variables and system settings."""
    monkeypatch.setenv("PYTHONPATH", str(Path(__file__).parent.parent))
    monkeypatch.setenv("TEST_MODE", "true")

@pytest.fixture
def mock_audio_device():
    """Mock audio device availability."""
    with patch('pyaudio.PyAudio') as mock:
        mock_stream = Mock()
        mock.return_value.open.return_value = mock_stream
        mock.return_value.get_default_input_device_info.return_value = {
            "name": "Test Microphone",
            "index": 0,
            "maxInputChannels": 2,
            "defaultSampleRate": 44100
        }
        yield mock

# Test configurations
@pytest.fixture
def test_wav_file(temp_dir: Path) -> Path:
    """Create a test WAV file."""
    import wave
    import struct
    
    wav_path = temp_dir / "test.wav"
    sample_rate = 16000
    duration = 1.0  # seconds
    
    with wave.open(str(wav_path), 'wb') as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(sample_rate)
        
        # Generate a simple sine wave
        for i in range(int(duration * sample_rate)):
            value = int(32767.0 * 0.5)  # Half amplitude
            packed_value = struct.pack('h', value)
            wav.writeframes(packed_value)
    
    return wav_path

@pytest.fixture
def sample_transcription_result() -> dict:
    """Return a sample transcription result."""
    return {
        "text": "This is a test transcription",
        "segments": [
            {
                "id": 0,
                "seek": 0,
                "start": 0.0,
                "end": 1.0,
                "text": "This is a test transcription",
                "tokens": [1, 2, 3, 4, 5],
                "temperature": 0.0,
                "avg_logprob": -0.5,
                "compression_ratio": 1.5,
                "no_speech_prob": 0.1
            }
        ],
        "language": "en"
    } 