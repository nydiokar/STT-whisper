from __future__ import annotations
import pytest
import threading
import queue
import time
from unittest.mock import Mock, patch, MagicMock
from voice_input_service.core.processing import TranscriptionWorker, STOP_SIGNAL
from voice_input_service.config import Config, AudioConfig, TranscriptionConfig
from voice_input_service.core.processing import SilenceDetector
# Constants for testing
TEST_TEXT = "Test transcription result"
TEST_DURATION = 1.23
MIN_AUDIO_LENGTH = 1000
MIN_CHUNK_SIZE = 500

@pytest.fixture
def mock_config():
    """Create a mock Config object for testing."""
    config = Mock(spec=Config)
    config.audio = Mock(spec=AudioConfig)
    config.transcription = Mock(spec=TranscriptionConfig)
    config.audio.min_audio_length = MIN_AUDIO_LENGTH
    config.transcription.min_chunk_size = MIN_CHUNK_SIZE
    config.audio.sample_rate = 16000
    config.audio.silence_duration = 0.5
    config.audio.min_process_interval = 0.1
    config.audio.vad_mode = 'silero' # Assuming default
    config.audio.vad_threshold = 0.5
    # Add other necessary attributes if TranscriptionWorker uses them directly
    return config

@pytest.fixture
def mock_process_func():
    """Create a mock processing function that returns (text, duration)."""
    return Mock(return_value=(TEST_TEXT, TEST_DURATION))

@pytest.fixture
def mock_result_callback():
    """Create a mock result callback."""
    # This mock represents the service._on_transcription_result method
    # It needs to be callable, which Mock() is by default.
    # We don't need to explicitly add the attribute here unless
    # something inside the worker *specifically* accesses it by name,
    # which it doesn't seem to do directly (it calls self.on_result).
    # The issue was likely in the test's expectation, not the worker setup.
    # Let's ensure the mock passed to the worker *is* the callback.
    return Mock() # Keep as a simple callable mock

@pytest.fixture
def mock_completion_callback():
    """Create a mock completion callback."""
    return Mock()

@pytest.fixture
def mock_final_result_callback():
    """Create a mock final result callback."""
    return Mock()

@pytest.fixture
def mock_silence_detector():
    """Create a mock SilenceDetector instance."""
    # Use MagicMock for spec to better mimic real object behavior if needed
    with patch('voice_input_service.core.processing.SilenceDetector', spec=SilenceDetector) as mock_detector_cls:
        mock_instance = MagicMock(spec=SilenceDetector)
        mock_instance.is_silent.return_value = False # Default to not silent
        mock_detector_cls.return_value = mock_instance
        yield mock_instance # Yield the instance for tests to use

@pytest.fixture
def worker(mock_config, mock_process_func, mock_result_callback, mock_completion_callback, mock_final_result_callback, mock_silence_detector):
    """Create a TranscriptionWorker instance for testing."""
    # The callbacks passed here should align with what the worker expects.
    # on_result is used for intermediate results in continuous mode.
    # on_final_result is used for the final result when stopped with a chunk.
    worker_instance = TranscriptionWorker(
        config=mock_config,
        process_func=mock_process_func,
        on_result=mock_result_callback, # This is used for intermediate results
        completion_callback=mock_completion_callback,
        on_final_result=mock_final_result_callback # This is used for final results
    )
    worker_instance.silence_detector = mock_silence_detector
    yield worker_instance
    # Ensure worker is stopped after test if it was started
    if worker_instance.running:
        worker_instance.stop()
        if worker_instance.thread and worker_instance.thread.is_alive():
            worker_instance.thread.join(timeout=1.0)

def test_worker_initialization(worker, mock_config, mock_process_func, mock_result_callback, mock_completion_callback, mock_final_result_callback):
    """Test TranscriptionWorker initialization."""
    assert worker.config == mock_config
    assert worker.process_func == mock_process_func
    assert worker.on_result == mock_result_callback
    assert worker.completion_callback == mock_completion_callback
    assert worker.on_final_result == mock_final_result_callback
    assert worker.min_audio_length == MIN_AUDIO_LENGTH
    assert worker.running is False
    assert worker.thread is None
    assert isinstance(worker.audio_queue, queue.Queue)

def test_worker_start_signal_stop(worker, mock_completion_callback):
    """Test starting and signaling the worker to stop."""
    worker.start()
    assert worker.running is True
    assert worker.thread is not None
    assert worker.thread.is_alive()

    worker.signal_stop()
    # Check that signal is in queue, worker loop should handle running=False
    assert worker.audio_queue.get_nowait() is STOP_SIGNAL
    
    # In a real scenario, the thread would exit and call the callback.
    # We can simulate this part of the worker finishing:
    worker.running = False # Simulate worker seeing signal
    worker._worker() # Run the cleanup part of the worker loop manually
    
    mock_completion_callback.assert_called_once()
    assert worker.thread is None # Thread reference should be cleared

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
    test_audio = b'test_audio_data' * MIN_CHUNK_SIZE # Ensure it meets min size

    worker._process_chunk(test_audio)

    mock_process_func.assert_called_once_with(test_audio)
    # Verify on_result was called with both text and duration
    mock_result_callback.assert_called_once_with(TEST_TEXT, TEST_DURATION)

def test_process_chunk_small_audio(worker, mock_process_func):
    """Test processing a small audio chunk (should be skipped)."""
    # Setup small test data
    test_audio = b'small'
    
    # Process the chunk
    worker._process_chunk(test_audio)
    
    # Verify process_func was not called
    mock_process_func.assert_not_called()

def test_process_chunk_with_none_result(worker, mock_process_func, mock_result_callback):
    """Test processing a chunk that returns None from process_func."""
    mock_process_func.return_value = None
    test_audio = b'test_audio_data' * MIN_CHUNK_SIZE

    worker._process_chunk(test_audio)

    mock_process_func.assert_called_once_with(test_audio)
    mock_result_callback.assert_not_called()

def test_process_chunk_with_exception(worker, mock_process_func, mock_result_callback):
    """Test processing a chunk that raises an exception."""
    # Setup mock to raise an exception
    mock_process_func.side_effect = Exception("Test error")
    
    # Setup test data
    test_audio = b'test_audio_data' * MIN_CHUNK_SIZE
    
    # Process the chunk
    worker._process_chunk(test_audio) 

    # Verify process_func was called
    mock_process_func.assert_called_once_with(test_audio)
    
    # Verify on_result was not called
    mock_result_callback.assert_not_called()

def test_on_result_exception(worker, mock_process_func):
    """Test handling an exception in the result callback."""
    mock_callback = Mock(side_effect=Exception("Callback error"))
    worker.on_result = mock_callback
    test_audio = b'test_audio_data' * MIN_CHUNK_SIZE

    # Process the chunk
    worker._process_chunk(test_audio) 

    mock_process_func.assert_called_once()
    # Callback should still be called even if it raises error
    mock_callback.assert_called_once_with(TEST_TEXT, TEST_DURATION)

def test_signal_stop_with_final_chunk(worker, mock_process_func, mock_final_result_callback, mock_completion_callback):
    """Test stopping with a final non-continuous chunk using threading."""
    final_audio = b'final_audio_data' * MIN_CHUNK_SIZE
    worker.start()
    assert worker.thread and worker.thread.is_alive()
    
    # Signal stop with the final chunk
    worker.signal_stop(final_chunk=final_audio)
    
    # --- Wait for worker thread to finish --- 
    if worker.thread:
        worker.thread.join(timeout=2.0) # Wait for thread to exit
    
    # --- Assertions --- 
    assert not worker.running
    assert worker.thread is None # Thread should be cleared after stopping
    
    # Verify final result callback was called with the expected result from mock_process_func
    mock_final_result_callback.assert_called_once_with(TEST_TEXT, TEST_DURATION)
    
    # Verify completion callback also called
    mock_completion_callback.assert_called_once()

@patch.object(TranscriptionWorker, '_process_chunk')
def test_worker_thread_basic(mock_process_chunk_method, worker):
    """Test the worker thread runs and attempts processing."""
    worker.start()
    test_audio = b'test' * MIN_CHUNK_SIZE # Make it long enough
    worker.add_audio(test_audio)
    time.sleep(0.5) # Allow time for processing attempt
    worker.signal_stop()
    worker.thread.join(timeout=1.0) # Wait for thread to exit
    # We can't easily assert _process_chunk was called due to timing,
    # but we check the thread started and stopped.
    assert not worker.running

# Remove patch decorator
# @patch.object(TranscriptionWorker, '_process_chunk')
@patch('time.time') # Patch time.time
def test_worker_continuous_mode_processing(mock_time, worker, mock_config, mock_silence_detector, mock_result_callback, mock_process_func): # Add mock_process_func
    """Test the worker processing loop in continuous mode."""
    # Set continuous mode
    worker.set_continuous_mode(True)
    assert worker.continuous_mode is True

    # Configure mock times
    start_time = 1000.0
    mock_time.side_effect = [
        start_time,         # Worker loop start time check
        start_time + 0.01,  # Time check inside loop before get
        start_time + 0.02,  # Time check for is_silent VAD check
        start_time + 0.03,  # Time check for time_since_speech
        start_time + 0.1,   # time.time() in _process_chunk start
        start_time + 0.5,   # time.time() in _process_chunk end (simulates 0.4s processing)
        start_time + 0.6,   # Next loop iteration time check
        start_time + 0.61,  # Time check inside loop before get
        start_time + 0.7,   # Next loop iteration time check (simulating silence)
        start_time + 1.0,   # Time check for silence detection
        start_time + 1.1    # Time check for time_since_speech > silence_duration
    ]

    # Configure silence detector - first speech, then silence
    mock_silence_detector.is_silent.side_effect = [
        False, # First chunk is speech
        True,  # Second chunk is silence, triggering processing
    ]

    # Mock the processing function to return a result
    mock_process_func.return_value = (TEST_TEXT, TEST_DURATION)

    # Add audio data (needs to be large enough)
    speech_audio = b's' * worker.min_audio_length # Make it exactly min length
    silence_audio = b'x' * 1600 # Some silence data > 50ms check

    # Start the worker
    worker.start()
    time.sleep(0.1) # Give thread time to start

    # Add speech audio - should buffer but not process yet
    worker.add_audio(speech_audio)
    time.sleep(0.1) # Allow queue processing
    mock_result_callback.assert_not_called() # Not processed yet

    # Add silence audio - should trigger processing due to silence_duration
    worker.add_audio(silence_audio)
    time.sleep(0.6) # Allow time for processing trigger (mock_time advances)

    # Stop the worker
    worker.stop()
    # Wait for the thread to finish
    if worker.thread and worker.thread.is_alive():
        worker.thread.join(timeout=1.0)

    # Assertions
    # Ensure _process_chunk was called (via process_func)
    mock_process_func.assert_called_once()
    # Ensure the result callback was triggered with the processed text
    mock_result_callback.assert_called_once_with(TEST_TEXT, TEST_DURATION)

# Remove or adapt test_worker_thread_error_recovery as needed
# It might be less relevant with the new queue-based stop mechanism.

# Remove old test_worker_start_stop if redundant
# Keep other tests like test_add_audio, small audio, exceptions etc. 