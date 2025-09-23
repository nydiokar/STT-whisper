from __future__ import annotations
import pytest
import os
import tempfile
from pathlib import Path
import json
import pyaudio
from typing import Generator
from pydantic import ValidationError

from voice_input_service.config import (
    AudioConfig, 
    TranscriptionConfig, 
    UIConfig, 
    HotkeyConfig, 
    Config
)

@pytest.fixture
def temp_config_dir() -> Generator[Path, None, None]:
    """Create a temporary directory for configuration files."""
    with tempfile.TemporaryDirectory() as temp_dir:
        yield Path(temp_dir)

# AudioConfig Tests
def test_audio_config_defaults():
    """Test AudioConfig default values."""
    config = AudioConfig()
    assert config.sample_rate == 16000
    assert config.chunk_size == 2048
    assert config.channels == 1
    assert config.format_type == pyaudio.paInt16
    assert config.device_index is None
    assert config.min_audio_length_sec == 1.5
    assert config.min_process_interval == 0.5
    assert config.vad_mode == "silero"
    assert config.vad_threshold == 0.5
    assert config.silence_duration_sec == 2.0
    assert config.max_chunk_duration_sec == 15.0

@pytest.mark.parametrize(
    "value, expected",
    [
        (16000, 16000),
        (44100, 44100)
    ]
)
def test_audio_config_valid_sample_rate(value: int, expected: int):
    config = AudioConfig(sample_rate=value)
    assert config.sample_rate == expected

def test_audio_config_invalid_sample_rate():
    with pytest.raises(ValueError):
        AudioConfig(sample_rate=12345)

def test_audio_config_custom_values():
    """Test AudioConfig with custom values."""
    config = AudioConfig(
        sample_rate=44100,
        chunk_size=1024,
        channels=2,
        format_type=pyaudio.paFloat32,
        device_index=1,
        min_audio_length_sec=1.0,
        min_process_interval=1.0,
        vad_threshold=0.8,
        silence_duration_sec=2.0,
        max_chunk_duration_sec=5.0
    )
    assert config.sample_rate == 44100
    assert config.chunk_size == 1024
    assert config.channels == 2
    assert config.format_type == pyaudio.paFloat32
    assert config.device_index == 1
    assert config.min_audio_length_sec == 1.0
    assert config.min_process_interval == 1.0
    assert config.vad_threshold == 0.8
    assert config.silence_duration_sec == 2.0
    assert config.max_chunk_duration_sec == 5.0

# TranscriptionConfig Tests
def test_transcription_config_defaults():
    """Test TranscriptionConfig default values."""
    config = TranscriptionConfig()
    assert config.model_name == "base"
    assert config.device is None
    assert config.compute_type == "float32"
    assert config.language == "en"
    assert config.translate is False
    assert config.cache_dir is None
    assert config.min_chunk_size_bytes == 32000
    assert config.use_cpp is True
    assert config.whisper_cpp_path is not None
    assert config.ggml_model_path is None

def test_transcription_config_custom_values():
    """Test TranscriptionConfig with custom values."""
    config = TranscriptionConfig(
        model_name="medium",
        device="cuda",
        compute_type="float32",
        language="fr",
        translate=True,
        cache_dir="/tmp/cache",
        min_chunk_size_bytes=16000
    )
    assert config.model_name == "medium"
    assert config.device == "cuda"
    assert config.compute_type == "float32"
    assert config.language == "fr"
    assert config.translate is True
    assert config.cache_dir == "/tmp/cache"
    assert config.min_chunk_size_bytes == 16000

def test_transcription_config_invalid_model():
    """Test TranscriptionConfig with invalid model name."""
    with pytest.raises(ValidationError) as exc_info:
        TranscriptionConfig(model_name="invalid")
    error_msg = str(exc_info.value)
    assert "model_name" in error_msg
    assert "one of" in error_msg

def test_transcription_config_model_name_normalization():
    """Test model name is normalized to lowercase."""
    config = TranscriptionConfig(model_name="BASE")
    assert config.model_name == "base"

def test_transcription_config_invalid_compute_type():
    """Test TranscriptionConfig with invalid compute type."""
    with pytest.raises(ValidationError) as exc_info:
        TranscriptionConfig(compute_type="double")
    error_msg = str(exc_info.value)
    assert "compute_type" in error_msg
    assert "one of" in error_msg

# UIConfig Tests
def test_ui_config_defaults():
    """Test UIConfig default values."""
    config = UIConfig()
    assert config.window_title == "Voice Input Service"
    assert config.window_width == 600
    assert config.window_height == 400
    assert config.font_family == "Segoe UI"
    assert config.font_size == 11
    assert config.highlight_new_text is True
    assert config.highlight_color == "#e6f2ff"

def test_ui_config_custom_values():
    """Test UIConfig with custom values."""
    config = UIConfig(
        window_title="Custom App",
        window_width=800,
        window_height=600,
        font_family="Arial",
        font_size=14,
        highlight_new_text=False,
        highlight_color="#ff0000"
    )
    assert config.window_title == "Custom App"
    assert config.window_width == 800
    assert config.window_height == 600
    assert config.font_family == "Arial"
    assert config.font_size == 14
    assert config.highlight_new_text is False
    assert config.highlight_color == "#ff0000"

def test_ui_config_invalid_dimensions():
    """Test UIConfig with invalid dimensions."""
    with pytest.raises(ValidationError) as exc_info:
        UIConfig(window_width=100)
    error_msg = str(exc_info.value)
    assert "window_width" in error_msg
    assert "at least 200px" in error_msg
    
    with pytest.raises(ValidationError) as exc_info:
        UIConfig(window_height=150)
    error_msg = str(exc_info.value)
    assert "window_height" in error_msg
    assert "at least 200px" in error_msg

# Config Integration Tests
def test_config_defaults():
    """Test Config default values."""
    config = Config()
    assert isinstance(config.audio, AudioConfig)
    assert isinstance(config.transcription, TranscriptionConfig)
    assert isinstance(config.ui, UIConfig)
    assert isinstance(config.hotkeys, HotkeyConfig)
    assert config.debug is False
    assert config.log_level == "INFO"
    assert isinstance(config.data_dir, Path)

def test_config_custom_values():
    """Test Config with custom values."""
    config = Config(
        audio=AudioConfig(sample_rate=16000),
        transcription=TranscriptionConfig(model_name="small"),
        ui=UIConfig(window_width=800),
        debug=True,
        log_level="DEBUG"
    )
    assert config.audio.sample_rate == 16000
    assert config.transcription.model_name == "small"
    assert config.ui.window_width == 800
    assert config.debug is True
    assert config.log_level == "DEBUG"

def test_config_invalid_log_level():
    """Test Config with invalid log level."""
    with pytest.raises(ValidationError) as exc_info:
        Config(log_level="TRACE")
    error_msg = str(exc_info.value)
    assert "log_level" in error_msg
    assert "one of" in error_msg

def test_config_log_level_normalization():
    """Test log level is normalized to uppercase."""
    config = Config(log_level="debug")
    assert config.log_level == "DEBUG"

def test_config_save_and_load(temp_config_dir):
    """Test saving and loading configuration."""
    config_path = temp_config_dir / "config.json"
    
    # Create custom config
    original_config = Config(
        audio=AudioConfig(sample_rate=16000),
        transcription=TranscriptionConfig(model_name="small"),
        ui=UIConfig(window_width=800),
        debug=True,
        log_level="DEBUG"
    )
    
    # Save config
    original_config.save(config_path)
    
    # Verify file exists
    assert config_path.exists()
    
    # Load config
    loaded_config = Config.load(config_path)
    
    # Verify loaded config matches original
    assert loaded_config.audio.sample_rate == 16000
    assert loaded_config.transcription.model_name == "small"
    assert loaded_config.ui.window_width == 800
    assert loaded_config.debug is True
    assert loaded_config.log_level == "DEBUG"

def test_config_load_nonexistent_file(temp_config_dir):
    """Test loading config from nonexistent file returns default config."""
    config_path = temp_config_dir / "nonexistent.json"
    assert not config_path.exists()
    
    config = Config.load(config_path)
    assert isinstance(config, Config)
    assert config.audio.sample_rate == 16000
    assert config.transcription.model_name == "base"

def test_config_save_creates_directories(temp_config_dir):
    """Test saving config creates parent directories if they don't exist."""
    nested_path = temp_config_dir / "nested" / "dirs" / "config.json"
    assert not nested_path.parent.exists()
    
    config = Config()
    config.save(nested_path)
    
    assert nested_path.parent.exists()
    assert nested_path.exists() 