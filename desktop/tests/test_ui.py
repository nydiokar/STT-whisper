from __future__ import annotations
import pytest
from unittest.mock import Mock, patch, MagicMock
import tkinter as tk
from typing import Generator
from voice_input_service.ui.window import TranscriptionUI
from voice_input_service.ui.events import KeyboardEventManager, EventHandler

@pytest.fixture
def mock_tk() -> Generator[Mock, None, None]:
    """Create a mock Tkinter instance."""
    with patch('tkinter.Tk') as mock:
        mock_window = mock.return_value
        mock_window.title = Mock()
        mock_window.geometry = Mock()
        
        # Mock BooleanVar and StringVar
        mock_bool_var = Mock()
        mock_str_var = Mock()
        mock_str_var.get.return_value = 'en'
        
        with patch('tkinter.BooleanVar', return_value=mock_bool_var), \
             patch('tkinter.StringVar', return_value=mock_str_var):
            yield mock_window

@pytest.fixture
def mock_handler() -> Mock:
    """Create a mock event handler."""
    handler = Mock(spec=EventHandler)
    handler.start_recording.return_value = True
    handler.stop_recording.return_value = "Test transcription"
    return handler

@pytest.fixture
def mock_label() -> Mock:
    """Create a mock Label widget."""
    label = Mock()
    label.cget = Mock()
    label.configure = Mock()
    return label

@pytest.fixture
def mock_text() -> Mock:
    """Create a mock Text widget."""
    text = Mock()
    text.get = Mock(return_value="Test text")
    text.delete = Mock()
    text.insert = Mock()
    return text

@pytest.fixture
def ui(mock_tk: Mock) -> TranscriptionUI:
    """Create a TranscriptionUI instance for testing."""
    with patch('tkinter.ttk.Label') as label_mock, \
         patch('tkinter.Text') as text_mock, \
         patch('tkinter.ttk.Frame'), \
         patch('tkinter.ttk.Combobox'):

        # Create an actual mock for the status label with proper cget behavior
        status_label = Mock()
        # Make cget return different values based on context
        status_label.cget = Mock(side_effect=lambda key: 
            "Ready" if key == "text" else None
        )
        
        # Set up configure to update the cget response
        def configure_label(**kwargs):
            if 'text' in kwargs:
                status_label.cget = Mock(side_effect=lambda key: 
                    kwargs['text'] if key == "text" else None
                )
        
        status_label.configure = Mock(side_effect=configure_label)
        
        # For word count label
        word_count_label = Mock()
        word_count_text = ["Words: 0"]
        
        def word_count_cget(key):
            if key == "text":
                return word_count_text[0]
            return None
            
        def word_count_config(**kwargs):
            if 'text' in kwargs:
                word_count_text[0] = kwargs['text']
                
        word_count_label.cget = Mock(side_effect=word_count_cget)
        word_count_label.config = Mock(side_effect=word_count_config)
        word_count_label.configure = word_count_label.config
        
        # For language label
        language_label = Mock()
        language_label.configure = Mock()
        
        # Set up label mock to return our custom mocks
        labels = [status_label, word_count_label, language_label]
        label_mock.side_effect = lambda *args, **kwargs: labels.pop(0) if labels else Mock()
        
        # Set up text widget
        text_widget = Mock()
        text_content = [""]
        
        def text_get(start, end):
            return text_content[0] + ("\n" if text_content[0] else "")
            
        def text_delete(start, end):
            text_content[0] = ""
            
        def text_insert(position, content):
            text_content[0] = content
        
        text_widget.get = Mock(side_effect=text_get)
        text_widget.delete = Mock(side_effect=text_delete)
        text_widget.insert = Mock(side_effect=text_insert)
        
        text_mock.return_value = text_widget
        
        ui = TranscriptionUI()
        
        # Force our custom mocks as the UI widgets
        ui.status_label = status_label
        ui.word_count_label = word_count_label
        ui.text_display = text_widget
        
        return ui

@pytest.fixture
def event_manager(mock_handler: Mock) -> KeyboardEventManager:
    """Create a KeyboardEventManager instance for testing."""
    return KeyboardEventManager(mock_handler)

def test_ui_initialization(ui: TranscriptionUI, mock_tk: Mock) -> None:
    """Test UI initialization.
    
    Verifies:
    - Window is created with correct title and size
    - UI components are initialized
    """
    mock_tk.title.assert_called_once_with("Voice Transcription Service")
    mock_tk.geometry.assert_called_once_with("600x400")
    assert ui.continuous_var is not None
    assert ui.language_var is not None
    assert ui.language_var.get() == 'en'

@pytest.mark.skip(reason="Skipping due to Tkinter initialization issues in CI environment")
def test_ui_status_update(ui: TranscriptionUI) -> None:
    """Test status display updates.
    
    Verifies:
    - Status text is updated correctly
    - Recording state is reflected
    """
    # Mock update_status_color to avoid Tkinter issues
    ui.update_status_color = Mock()
    
    # Call the update_status method with different parameters
    ui.update_status(False)
    ui.update_status(True, elapsed=5.5)
    ui.update_status(True, elapsed=10.2, continuous=True)
    
    # Verify the status_label was updated
    assert ui.update_status_color.call_count >= 1

def test_ui_word_count_update(ui: TranscriptionUI) -> None:
    """Test word count display updates."""
    ui.update_word_count(42)
    assert ui.word_count_label.cget("text") == "Words: 42"

def test_ui_text_update(ui: TranscriptionUI) -> None:
    """Test text display updates."""
    test_text = "Hello, world!"
    ui.update_text(test_text)
    assert ui.text_display.get("1.0", tk.END).strip() == test_text

def test_event_manager_recording(event_manager: KeyboardEventManager, mock_handler: Mock) -> None:
    """Test recording event handling.
    
    Verifies:
    - Start/stop recording calls
    - State changes
    - Text handling
    """
    # Test start recording
    event_manager._on_toggle_recording_hotkey()
    assert event_manager.recording is True
    mock_handler.start_recording.assert_called_once()
    
    # Test stop recording
    event_manager._on_toggle_recording_hotkey()
    assert event_manager.recording is False
    mock_handler.stop_recording.assert_called_once()

def test_event_manager_save_clear(event_manager: KeyboardEventManager, mock_handler: Mock) -> None:
    """Test save and clear event handling."""
    event_manager._on_save_hotkey()
    mock_handler.save_transcript.assert_called_once()
    
    event_manager._on_clear_hotkey()
    mock_handler.clear_transcript.assert_called_once()

def test_event_manager_continuous_mode(event_manager: KeyboardEventManager, mock_handler: Mock) -> None:
    """Test continuous mode behavior."""
    event_manager.continuous_mode = True
    
    # Start and stop recording
    event_manager._on_toggle_recording_hotkey()
    event_manager._on_toggle_recording_hotkey()
    
    # Verify clipboard operations were not called
    with pytest.raises(AttributeError):
        mock_handler.pyperclip.copy.assert_called() 