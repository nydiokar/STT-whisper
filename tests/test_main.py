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
    # Patch check_microphone to return True
    with patch('voice_input_service.__main__.check_microphone', return_value=True),\
         patch('voice_input_service.__main__.initialize_app') as mock_init_app,\
         patch('voice_input_service.__main__.VoiceInputService') as mock_voice_service:

        # Mock initialize_app return values
        mock_config = Mock()
        mock_ui = Mock()
        mock_model_manager = Mock()
        mock_transcriber = Mock() # Mock the transcriber engine
        mock_model_manager.initialize_transcription_engine.return_value = mock_transcriber
        mock_init_app.return_value = (mock_config, mock_ui, mock_model_manager)

        # Mock the service instance
        mock_service_instance = Mock()
        mock_voice_service.return_value = mock_service_instance

        # Call main
        main()

        # Assertions
        mock_init_app.assert_called_once()
        mock_model_manager.initialize_transcription_engine.assert_called_once()
        mock_voice_service.assert_called_once_with(mock_config, mock_ui, mock_transcriber)
        mock_service_instance.run.assert_called_once()

def test_main_keyboard_interrupt(capsys):
    """Test main function with keyboard interrupt."""
    with patch('voice_input_service.__main__.check_microphone', return_value=True),\
         patch('voice_input_service.__main__.initialize_app') as mock_init_app,\
         patch('voice_input_service.__main__.VoiceInputService') as mock_voice_service:

        # Mock initialize_app return values
        mock_config = Mock()
        mock_ui = Mock()
        mock_model_manager = Mock()
        mock_transcriber = Mock()
        mock_model_manager.initialize_transcription_engine.return_value = mock_transcriber
        mock_init_app.return_value = (mock_config, mock_ui, mock_model_manager)

        # Mock the service instance to raise KeyboardInterrupt
        mock_service_instance = Mock()
        mock_service_instance.run.side_effect = KeyboardInterrupt()
        mock_voice_service.return_value = mock_service_instance

        # Call main
        main()

        # Assertions
        captured = capsys.readouterr()
        assert "Service stopped by user" in captured.out
        mock_init_app.assert_called_once()
        mock_model_manager.initialize_transcription_engine.assert_called_once()
        mock_voice_service.assert_called_once_with(mock_config, mock_ui, mock_transcriber)
        mock_service_instance.run.assert_called_once()

def test_main_error(capsys):
    """Test main function with error during service run."""
    with patch('voice_input_service.__main__.check_microphone', return_value=True),\
         patch('voice_input_service.__main__.initialize_app') as mock_init_app,\
         patch('voice_input_service.__main__.VoiceInputService') as mock_voice_service:

        # Mock initialize_app return values
        mock_config = Mock()
        mock_ui = Mock()
        mock_model_manager = Mock()
        mock_transcriber = Mock()
        mock_model_manager.initialize_transcription_engine.return_value = mock_transcriber
        mock_init_app.return_value = (mock_config, mock_ui, mock_model_manager)

        # Mock the service instance to raise an error
        mock_service_instance = Mock()
        mock_service_instance.run.side_effect = Exception("Test error")
        mock_voice_service.return_value = mock_service_instance

        # Expect SystemExit when main encounters an error
        with pytest.raises(SystemExit) as exc_info:
            main()

        # Assertions
        assert exc_info.value.code == 1
        captured = capsys.readouterr()
        assert "Error: Test error" in captured.out
        mock_init_app.assert_called_once()
        mock_model_manager.initialize_transcription_engine.assert_called_once()
        mock_voice_service.assert_called_once_with(mock_config, mock_ui, mock_transcriber)
        mock_service_instance.run.assert_called_once()

def test_main_engine_init_fails(capsys):
    """Test main function when transcription engine fails to initialize."""
    with patch('voice_input_service.__main__.check_microphone', return_value=True),\
         patch('voice_input_service.__main__.initialize_app') as mock_init_app,\
         patch('voice_input_service.__main__.VoiceInputService') as mock_voice_service: # Patch service anyway

        # Mock initialize_app return values
        mock_config = Mock()
        mock_ui = Mock()
        mock_model_manager = Mock()
        # Make engine initialization return None
        mock_model_manager.initialize_transcription_engine.return_value = None 
        mock_init_app.return_value = (mock_config, mock_ui, mock_model_manager)

        # Expect SystemExit
        with pytest.raises(SystemExit) as exc_info:
            main()
            
        # Assertions
        assert exc_info.value.code == 1
        captured = capsys.readouterr()
        assert "No transcription model was selected" in captured.out
        mock_init_app.assert_called_once()
        mock_model_manager.initialize_transcription_engine.assert_called_once()
        mock_voice_service.assert_not_called() # Service should not be created 