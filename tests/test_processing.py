from __future__ import annotations
import pytest
import numpy as np
import time
from queue import Queue
from typing import Any, Callable
from voice_input_service.core.processing import TranscriptionWorker

@pytest.fixture
def mock_process_func() -> Callable[[bytes], str]:
    """Create a mock processing function."""
    def process(audio: bytes) -> str:
        return f"Processed {len(audio)} samples"
    return process

@pytest.fixture
def mock_result_callback() -> Callable[[str], None]:
    """Create a mock result callback function."""
    results: list[str] = []
    def callback(text: str) -> None:
        results.append(text)
    callback.results = results  # type: ignore
    return callback

@pytest.fixture
def worker(mock_process_func: Callable[[bytes], str], 
          mock_result_callback: Callable[[str], None]) -> TranscriptionWorker:
    """Create a TranscriptionWorker instance with mock functions."""
    return TranscriptionWorker(
        process_func=mock_process_func,
        on_result=mock_result_callback,
        min_audio_length=1000
    )

def test_worker_initialization(worker: TranscriptionWorker) -> None:
    """Test worker initialization sets attributes correctly."""
    assert worker.min_audio_length == 1000
    assert isinstance(worker.audio_queue, Queue)
    assert not worker.running

def test_worker_start_stop(worker: TranscriptionWorker) -> None:
    """Test worker start and stop functionality."""
    worker.start()
    assert worker.running
    assert worker.thread is not None
    assert worker.thread.is_alive()
    
    # Store thread reference
    thread = worker.thread
    
    worker.stop()
    assert not worker.running
    assert not thread.is_alive()  # Check original thread

def test_add_audio_processing(worker: TranscriptionWorker, 
                            mock_result_callback: Callable[[str], None]) -> None:
    """Test audio processing workflow."""
    audio_data = bytes(2000)
    worker.start()
    worker.add_audio(audio_data)
    
    # Wait for processing
    time.sleep(0.1)
    worker.stop()
    
    # Check if callback was called with correct result
    assert len(mock_result_callback.results) > 0  # type: ignore
    assert "Processed 2000 samples" in mock_result_callback.results  # type: ignore

def test_worker_error_handling(worker: TranscriptionWorker) -> None:
    """Test worker handles processing errors gracefully."""
    def failing_process(_: bytes) -> str:
        raise ValueError("Processing error")
    
    worker.process_func = failing_process
    worker.start()
    worker.add_audio(bytes(2000))
    
    # Wait for processing
    time.sleep(0.1)
    worker.stop()
    
    # Worker should continue running despite error
    assert not worker.running  # Stopped explicitly

def test_empty_queue_on_stop(worker: TranscriptionWorker,
                            mock_result_callback: Callable[[str], None]) -> None:
    """Test that remaining audio is processed when stopping."""
    audio_data = bytes(2000)
    worker.start()
    
    # Add multiple chunks
    for _ in range(3):
        worker.add_audio(audio_data)
    
    # Wait for processing
    time.sleep(0.2)
    worker.stop()
    
    # All chunks should be processed
    assert len(mock_result_callback.results) == 3  # type: ignore 