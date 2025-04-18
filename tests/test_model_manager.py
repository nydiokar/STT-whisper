import pytest
from unittest.mock import Mock, patch, create_autospec, ANY
import tkinter as tk
from voice_input_service.core.model_manager import ModelManager
from voice_input_service.core.config import Config

@pytest.fixture
def mock_ui_root():
    return Mock(spec=tk.Tk)

@pytest.fixture
def mock_config():
    config = Mock(spec=Config)
    config.transcription = Mock()
    config.transcription.model_name = "large"
    config.transcription.device = "cpu"
    config.transcription.language = "en"
    return config

@pytest.fixture
def model_manager(mock_ui_root, mock_config):
    return ModelManager(mock_ui_root, mock_config)

def test_initialize_transcription_engine_missing_model(model_manager, monkeypatch):
    """Test that missing model triggers model selection dialog."""
    # Mock TranscriptionEngine to raise "not available locally" error
    mock_engine = Mock()
    def mock_load_model(*args, **kwargs):
        raise RuntimeError("large is not available locally")
    mock_engine._load_model = mock_load_model
    
    # Mock TranscriptionEngine creation
    with patch("voice_input_service.core.model_manager.TranscriptionEngine") as mock_engine_cls:
        mock_engine_cls.return_value = mock_engine
        
        # Mock ModelSelectionDialog to simulate selecting tiny model
        def mock_dialog_init(parent, model_name, available_models, on_download, on_select, cache_dir=None):
            # Simulate user selecting tiny model by calling on_select
            on_select("tiny")
            return Mock()
            
        with patch("voice_input_service.core.model_manager.ModelSelectionDialog", autospec=True) as mock_dialog_cls:
            mock_dialog_cls.side_effect = mock_dialog_init
            
            # Call the method
            result = model_manager.initialize_transcription_engine()
            
            # Verify dialog was created with correct parameters
            mock_dialog_cls.assert_called_once_with(
                parent=model_manager.ui_root,
                model_name="large",
                available_models=ANY,
                on_download=ANY,
                on_select=ANY,
                cache_dir=model_manager.cache_dir
            )

def test_initialize_transcription_engine_user_cancels(model_manager, monkeypatch):
    """Test that canceling model selection exits gracefully."""
    # Mock TranscriptionEngine to raise "not available locally" error
    mock_engine = Mock()
    def mock_load_model(*args, **kwargs):
        raise RuntimeError("large is not available locally")
    mock_engine._load_model = mock_load_model
    
    # Mock TranscriptionEngine creation
    with patch("voice_input_service.core.model_manager.TranscriptionEngine") as mock_engine_cls:
        mock_engine_cls.return_value = mock_engine
        
        # Mock ModelSelectionDialog that simulates cancellation
        def mock_dialog_init(parent, model_name, available_models, on_download, on_select, cache_dir=None):
            # Simulate user canceling by destroying parent
            parent.destroy()
            return Mock()
            
        with patch("voice_input_service.core.model_manager.ModelSelectionDialog", autospec=True) as mock_dialog_cls:
            mock_dialog_cls.side_effect = mock_dialog_init
            
            # Call the method
            result = model_manager.initialize_transcription_engine()
            
            # Verify result is None
            assert result is None
            
            # Verify dialog was created with correct parameters
            mock_dialog_cls.assert_called_once_with(
                parent=model_manager.ui_root,
                model_name="large",
                available_models=ANY,
                on_download=ANY,
                on_select=ANY,
                cache_dir=model_manager.cache_dir
            )

def test_initialize_transcription_engine_download_model(model_manager, monkeypatch):
    """Test that selecting a model to download works."""
    # Mock TranscriptionEngine to raise "not available locally" error
    mock_engine = Mock()
    def mock_load_model(*args, **kwargs):
        raise RuntimeError("large is not available locally")
    mock_engine._load_model = mock_load_model
    
    # Mock TranscriptionEngine creation
    with patch("voice_input_service.core.model_manager.TranscriptionEngine") as mock_engine_cls:
        mock_engine_cls.return_value = mock_engine
        
        # Mock ModelSelectionDialog
        def mock_dialog_init(parent, model_name, available_models, on_download, on_select, cache_dir=None):
            # Simulate user choosing to download tiny model
            on_download("tiny")
            return Mock()
            
        with patch("voice_input_service.core.model_manager.ModelSelectionDialog", autospec=True) as mock_dialog_cls:
            mock_dialog_cls.side_effect = mock_dialog_init
            
            # Mock messagebox.askyesno to simulate user confirming download
            with patch("voice_input_service.core.model_manager.messagebox.askyesno") as mock_confirm:
                mock_confirm.return_value = True
                
                # Call the method
                result = model_manager.initialize_transcription_engine()
                
                # Verify dialog was created with correct parameters
                mock_dialog_cls.assert_called_once_with(
                    parent=model_manager.ui_root,
                    model_name="large",
                    available_models=ANY,
                    on_download=ANY,
                    on_select=ANY,
                    cache_dir=model_manager.cache_dir
                )
                
                # Verify download confirmation was shown
                mock_confirm.assert_called_once() 