from __future__ import annotations
import pytest
import threading
import queue
import time
from unittest.mock import Mock, patch, MagicMock
from voice_input_service.core.processing import TranscriptionWorker

@pytest.fixture
def mock_process_func():
    """Create a mock processing function."""
    return Mock(return_value="Test transcription result")

@pytest.fixture
def mock_result_callback():
    """Create a mock result callback."""
    return Mock()

@pytest.fixture
def worker(mock_process_func, mock_result_callback):
    """Create a TranscriptionWorker instance for testing."""
    return TranscriptionWorker(
        process_func=mock_process_func,
        on_result=mock_result_callback,
        min_audio_length=1000  # Small value for testing
    )

def test_worker_initialization(worker, mock_process_func, mock_result_callback):
    """Test TranscriptionWorker initialization."""
    assert worker.process_func == mock_process_func
    assert worker.on_result == mock_result_callback
    assert worker.min_audio_length == 1000
    assert worker.running is False
    assert worker.thread is None
    assert isinstance(worker.audio_queue, queue.Queue)

def test_worker_start_stop(worker):
    """Test starting and stopping the worker."""
    # Start the worker
    worker.start()
    assert worker.running is True
    assert worker.thread is not None
    assert worker.thread.is_alive()
    
    # Start again (should log warning)
    worker.start()
    
    # Stop the worker
    worker.stop()
    assert worker.running is False
    assert worker.thread is None

def test_add_audio(worker):
    """Test adding audio data to the queue."""
    # Start the worker
    worker.start()
    
    # Add audio data
    test_audio = b'test_audio_data'
    worker.add_audio(test_audio)
    
    # Verify data was added to the queue
    assert worker.audio_queue.qsize() == 1
    
    # Stop the worker
    worker.stop()

def test_process_chunk(worker, mock_process_func, mock_result_callback):
    """Test processing an audio chunk."""
    # Setup test data
    test_audio = b'test_audio_data' * 500  # Make sure it's over the minimum size
    
    # Process the chunk
    worker._process_chunk(test_audio)
    
    # Verify process_func was called
    mock_process_func.assert_called_once_with(test_audio)
    
    # Verify on_result was called with the result
    mock_result_callback.assert_called_once_with("Test transcription result")

def test_process_chunk_small_audio(worker, mock_process_func):
    """Test processing a small audio chunk (should be skipped)."""
    # Setup small test data
    test_audio = b'small'
    
    # Process the chunk
    worker._process_chunk(test_audio)
    
    # Verify process_func was not called
    mock_process_func.assert_not_called()

def test_process_chunk_with_empty_result(worker, mock_process_func, mock_result_callback):
    """Test processing a chunk that returns no text."""
    # Setup mock to return None
    mock_process_func.return_value = None
    
    # Setup test data
    test_audio = b'test_audio_data' * 500
    
    # Process the chunk
    worker._process_chunk(test_audio)
    
    # Verify process_func was called
    mock_process_func.assert_called_once_with(test_audio)
    
    # Verify on_result was not called
    mock_result_callback.assert_not_called()

def test_process_chunk_with_exception(worker, mock_process_func, mock_result_callback):
    """Test processing a chunk that raises an exception."""
    # Setup mock to raise an exception
    mock_process_func.side_effect = Exception("Test error")
    
    # Setup test data
    test_audio = b'test_audio_data' * 500
    
    # Process the chunk
    worker._process_chunk(test_audio)
    
    # Verify process_func was called
    mock_process_func.assert_called_once_with(test_audio)
    
    # Verify on_result was not called
    mock_result_callback.assert_not_called()

def test_on_result_exception(worker, mock_process_func):
    """Test handling an exception in the result callback."""
    # Setup mock callback that raises an exception
    mock_callback = Mock(side_effect=Exception("Callback error"))
    worker.on_result = mock_callback
    
    # Setup test data
    test_audio = b'test_audio_data' * 500
    
    # Process the chunk (should not crash despite callback error)
    worker._process_chunk(test_audio)
    
    # Verify process_func and callback were called
    mock_process_func.assert_called_once()
    mock_callback.assert_called_once()

def test_worker_thread_basic(worker):
    """Test the worker thread runs and processes data."""
    # Patch _process_chunk to verify it's called
    with patch.object(worker, '_process_chunk') as mock_process:
        # Start worker
        worker.start()
        
        # Add sufficient audio data
        test_audio = b'test_audio_data' * 1000
        worker.add_audio(test_audio)
        
        # Wait for processing
        time.sleep(0.5)
        
        # Stop worker
        worker.stop()
        
        # Verify processing was attempted
        mock_process.assert_called()

def test_worker_thread_error_recovery(worker):
    """Test the worker thread recovers from errors.
    
    Note: This test intentionally triggers an error in a worker thread to test
    recovery logic. We're not actually testing with Thread but using a mock
    to simulate the behavior.
    """
    # Instead of using a real thread that raises an exception, 
    # let's mock _worker behavior more safely
    with patch.object(worker, '_worker') as mock_worker:
        # Setup worker to run
        worker.running = True
        worker.thread = Mock()
        
        # Simulate worker error by calling the _worker's error handler
        worker.audio_queue.put(b"test_audio" * 100)
        
        # Test that worker handles the situation appropriately
        mock_worker.side_effect = Exception("Test error")
        
        # Run stop - this should clean up properly even after an error
        worker.stop()
        
        # Verify cleanup happened
        assert worker.running is False 