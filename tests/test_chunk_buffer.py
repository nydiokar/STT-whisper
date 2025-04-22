import numpy as np
import pytest
from pydantic import ValidationError
from unittest.mock import MagicMock
from typing import List
import time # Needed for testing date/time aspects

from voice_input_service.core.chunk_buffer import AudioChunkBuffer, AudioChunk


# Constants
SAMPLE_RATE = 16000
CHUNK_DURATION_MS = 500
CHUNK_SIZE = int(SAMPLE_RATE * CHUNK_DURATION_MS / 1000)
MAX_CHUNKS = 5  # Keep a small buffer for testing


# Helper function to create dummy audio data
def create_dummy_audio(duration_ms: int, sample_rate: int) -> np.ndarray:
    num_samples: int = int(sample_rate * duration_ms / 1000)
    return np.random.randint(-1000, 1000, size=num_samples, dtype=np.int16)


@pytest.fixture
def buffer() -> AudioChunkBuffer:
    """Fixture to create an AudioChunkBuffer instance."""
    return AudioChunkBuffer(max_chunks=MAX_CHUNKS)


def create_dummy_audio_chunk(duration_ms: int, sample_rate: int) -> bytes:
    """Creates a dummy audio chunk (silence) of specified duration."""
    num_samples = int(sample_rate * duration_ms / 1000)
    audio_data = np.zeros(num_samples, dtype=np.int16)
    return audio_data.tobytes()


# === Test Initialization ===


def test_buffer_initialization(buffer: AudioChunkBuffer):
    """Test that the buffer initializes correctly."""
    assert len(buffer) == 0
    assert buffer.max_chunks == MAX_CHUNKS


def test_add_single_chunk(buffer: AudioChunkBuffer):
    """Test adding a single chunk to the buffer."""
    audio_data: bytes = create_dummy_audio_chunk(CHUNK_DURATION_MS, SAMPLE_RATE)
    chunk: AudioChunk = buffer.add_chunk(audio_data, SAMPLE_RATE)
    
    assert len(buffer) == 1
    assert buffer.get_latest_chunk() == chunk
    assert chunk.audio_data == audio_data
    assert chunk.sample_rate == SAMPLE_RATE
    assert chunk.duration == pytest.approx(CHUNK_DURATION_MS / 1000, abs=1e-3)


def test_add_multiple_chunks(buffer: AudioChunkBuffer):
    """Test adding multiple chunks within the buffer capacity."""
    num_chunks_to_add = 3
    added_chunks: List[AudioChunk] = []
    for _ in range(num_chunks_to_add):
        audio_data: bytes = create_dummy_audio_chunk(CHUNK_DURATION_MS, SAMPLE_RATE)
        chunk: AudioChunk = buffer.add_chunk(audio_data, SAMPLE_RATE)
        added_chunks.append(chunk)
        
    assert len(buffer) == num_chunks_to_add
    assert buffer.get_latest_chunk() == added_chunks[-1]
    # Verify all added chunks are in the buffer
    assert buffer.buffer == added_chunks


def test_buffer_overflow(buffer: AudioChunkBuffer):
    """Test adding chunks beyond the buffer capacity (FIFO behavior based on count)."""
    num_chunks_to_add = MAX_CHUNKS + 2
    added_chunks: List[AudioChunk] = []
    for i in range(num_chunks_to_add):
        audio_data: bytes = create_dummy_audio_chunk(CHUNK_DURATION_MS, SAMPLE_RATE)
        chunk: AudioChunk = buffer.add_chunk(audio_data, SAMPLE_RATE)
        added_chunks.append(chunk)
        
    assert len(buffer) == MAX_CHUNKS
    # The buffer should contain the *last* MAX_CHUNKS added
    assert buffer.buffer == added_chunks[-MAX_CHUNKS:]
    # Ensure the oldest chunks were removed
    assert added_chunks[0] not in buffer.buffer
    assert added_chunks[1] not in buffer.buffer
    assert buffer.get_latest_chunk() == added_chunks[-1]


def test_get_chunk_by_id(buffer: AudioChunkBuffer):
    """Test retrieving a specific chunk by its ID."""
    audio_data1: bytes = create_dummy_audio_chunk(100, SAMPLE_RATE)
    chunk1: AudioChunk = buffer.add_chunk(audio_data1, SAMPLE_RATE)
    
    audio_data2: bytes = create_dummy_audio_chunk(200, SAMPLE_RATE)
    chunk2: AudioChunk = buffer.add_chunk(audio_data2, SAMPLE_RATE)
    
    retrieved_chunk1: AudioChunk | None = buffer.get_chunk(chunk1.chunk_id)
    retrieved_chunk2: AudioChunk | None = buffer.get_chunk(chunk2.chunk_id)
    
    assert retrieved_chunk1 == chunk1
    assert retrieved_chunk2 == chunk2
    
    non_existent_chunk: AudioChunk | None = buffer.get_chunk("non_existent_id")
    assert non_existent_chunk is None


def test_get_latest_chunk_empty_buffer(buffer: AudioChunkBuffer):
    """Test getting the latest chunk from an empty buffer."""
    assert buffer.get_latest_chunk() is None


def test_clear_buffer(buffer: AudioChunkBuffer):
    """Test clearing all chunks from the buffer."""
    buffer.add_chunk(create_dummy_audio_chunk(100, SAMPLE_RATE), SAMPLE_RATE)
    buffer.add_chunk(create_dummy_audio_chunk(200, SAMPLE_RATE), SAMPLE_RATE)
    assert len(buffer) == 2
    
    buffer.clear()
    assert len(buffer) == 0
    assert buffer.get_latest_chunk() is None
    assert not buffer.buffer # Check if the list itself is empty


def test_audio_chunk_properties():
    """Test properties of the AudioChunk class."""
    sample_rate: int = 8000
    duration_ms: int = 1000
    audio_data: bytes = create_dummy_audio_chunk(duration_ms, sample_rate)
    chunk: AudioChunk = AudioChunk(audio_data, sample_rate=sample_rate)
    
    assert chunk.sample_rate == sample_rate
    assert chunk.duration == pytest.approx(duration_ms / 1000, abs=1e-3)
    assert isinstance(chunk.chunk_id, str)
    assert len(chunk.chunk_id) > 0
    assert isinstance(chunk.timestamp, str) # Check format indirectly
    assert isinstance(chunk.formatted_date, str) # Check format indirectly


def test_get_chunks_by_date(buffer: AudioChunkBuffer):
    """Test retrieving chunks based on their date."""
    # We need to control the time to test dates reliably
    import time
    
    # Add a chunk for "today"
    chunk_today: AudioChunk = buffer.add_chunk(create_dummy_audio_chunk(100, SAMPLE_RATE), SAMPLE_RATE)
    date_today: str = chunk_today.formatted_date
    
    # Mock time to simulate adding a chunk "yesterday" (tricky without a library)
    # For simplicity, we'll assume this test runs quickly and date doesn't change.
    # A more robust test would use time mocking (e.g., freezegun).
    # Let's add another chunk for today to ensure filtering works.
    chunk_today_2: AudioChunk = buffer.add_chunk(create_dummy_audio_chunk(150, SAMPLE_RATE), SAMPLE_RATE)

    chunks_for_today: List[AudioChunk] = buffer.get_chunks_by_date(date_today)
    assert len(chunks_for_today) == 2
    assert chunk_today in chunks_for_today
    assert chunk_today_2 in chunks_for_today

    # Test for a date with no chunks
    chunks_for_future: List[AudioChunk] = buffer.get_chunks_by_date("2999-12-31")
    assert len(chunks_for_future) == 0


# Test chunk creation timestamp accuracy (optional, needs tolerance)
def test_audio_chunk_timestamp_creation(buffer: AudioChunkBuffer):
    before_creation = time.time()
    audio_data: bytes = create_dummy_audio_chunk(10, SAMPLE_RATE) 
    chunk = buffer.add_chunk(audio_data, SAMPLE_RATE)
    after_creation = time.time()
    assert before_creation <= chunk.start_time <= after_creation 