from __future__ import annotations
import pytest
from unittest.mock import Mock, patch
import pyaudio
import sys
from voice_input_service.__main__ import check_microphone, main

@pytest.fixture
def mock_pyaudio():
    with patch('pyaudio.PyAudio', autospec=True) as mock:
        mock_instance = Mock()
        mock.return_value = mock_instance
        yield mock_instance

def test_check_microphone_success(mock_pyaudio, capsys):
    """Test successful microphone check."""
    # Setup mock
    mock_pyaudio.get_default_input_device_info.return_value = {'name': 'Test Mic'}
    mock_stream = Mock()
    mock_pyaudio.open.return_value = mock_stream
    
    result = check_microphone()
    captured = capsys.readouterr()
    
    assert result is True
    assert "Found microphone: Test Mic" in captured.out
    mock_pyaudio.get_default_input_device_info.assert_called_once()
    mock_pyaudio.open.assert_called_once()
    mock_stream.start_stream.assert_called_once()
    mock_stream.read.assert_called_once_with(1024)
    mock_stream.stop_stream.assert_called_once()
    mock_stream.close.assert_called_once()
    mock_pyaudio.terminate.assert_called_once()

def test_check_microphone_no_device(mock_pyaudio, capsys):
    """Test microphone check with no device found."""
    mock_pyaudio.get_default_input_device_info.side_effect = Exception("No device")
    
    result = check_microphone()
    captured = capsys.readouterr()
    
    assert result is False
    assert "Error accessing microphone:" in captured.out
    assert "Make sure your microphone is connected" in captured.out
    mock_pyaudio.terminate.assert_called_once()

def test_check_microphone_stream_error(mock_pyaudio, capsys):
    """Test microphone check with stream error."""
    mock_pyaudio.get_default_input_device_info.return_value = {'name': 'Test Mic'}
    mock_pyaudio.open.side_effect = Exception("Stream error")
    
    result = check_microphone()
    captured = capsys.readouterr()
    
    assert result is False
    assert "Error accessing microphone:" in captured.out
    mock_pyaudio.terminate.assert_called_once()

def test_check_microphone_pyaudio_error(capsys):
    """Test microphone check with PyAudio initialization error."""
    with patch('pyaudio.PyAudio', side_effect=Exception("PyAudio error")):
        result = check_microphone()
        captured = capsys.readouterr()
        
        assert result is False
        assert "Failed to initialize audio system" in captured.out

def test_main_microphone_check_failed(capsys):
    """Test main function when microphone check fails."""
    with patch('voice_input_service.__main__.check_microphone', return_value=False):
        with pytest.raises(SystemExit) as exc_info:
            main()
        
        assert exc_info.value.code == 1
        captured = capsys.readouterr()
        assert "Microphone check failed" in captured.out

def test_main_success():
    """Test successful main function execution."""
    with patch('voice_input_service.__main__.check_microphone', return_value=True):
        with patch('voice_input_service.__main__.VoiceInputService') as mock_service:
            main()
            
            mock_service.assert_called_once()
            mock_service.return_value.run.assert_called_once()

def test_main_keyboard_interrupt(capsys):
    """Test main function with keyboard interrupt."""
    with patch('voice_input_service.__main__.check_microphone', return_value=True):
        with patch('voice_input_service.__main__.VoiceInputService') as mock_service:
            mock_service.return_value.run.side_effect = KeyboardInterrupt()
            
            main()
            
            captured = capsys.readouterr()
            assert "Service stopped by user" in captured.out

def test_main_error(capsys):
    """Test main function with error."""
    with patch('voice_input_service.__main__.check_microphone', return_value=True):
        with patch('voice_input_service.__main__.VoiceInputService') as mock_service:
            mock_service.return_value.run.side_effect = Exception("Test error")
            
            main()
            
            captured = capsys.readouterr()
            assert "Error: Test error" in captured.out 