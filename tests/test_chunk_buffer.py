import numpy as np
import pytest
from pydantic import ValidationError

from voice_input_service.core.chunk_buffer import ChunkBuffer, ChunkMetadata


# Helper function to create dummy audio data
def create_dummy_audio(duration_ms: int, sample_rate: int) -> np.ndarray:
    num_samples: int = int(sample_rate * duration_ms / 1000)
    return np.random.randint(-1000, 1000, size=num_samples, dtype=np.int16)


@pytest.fixture
def sample_rate() -> int:
    return 16000


@pytest.fixture
def buffer_capacity_ms() -> int:
    return 5000  # 5 seconds


@pytest.fixture
def chunk_buffer(sample_rate: int, buffer_capacity_ms: int) -> ChunkBuffer:
    return ChunkBuffer(capacity_ms=buffer_capacity_ms, sample_rate=sample_rate)


@pytest.fixture
def populated_buffer(chunk_buffer: ChunkBuffer, sample_rate: int) -> ChunkBuffer:
    # Add 6 chunks of 500ms each
    for i in range(6):
        chunk_data: np.ndarray = create_dummy_audio(500, sample_rate)
        chunk_buffer.add_chunk(chunk_data)
    # Total duration = 3000ms
    return chunk_buffer


# === Test Initialization ===


def test_chunk_buffer_initialization(sample_rate: int, buffer_capacity_ms: int) -> None:
    buffer = ChunkBuffer(capacity_ms=buffer_capacity_ms, sample_rate=sample_rate)
    assert buffer.capacity_ms == buffer_capacity_ms
    assert buffer.sample_rate == sample_rate
    assert buffer.get_total_duration_ms() == 0
    assert not buffer.get_all_chunks()


def test_chunk_buffer_invalid_capacity() -> None:
    with pytest.raises(ValidationError):
        ChunkBuffer(capacity_ms=0, sample_rate=16000)
    with pytest.raises(ValidationError):
        ChunkBuffer(capacity_ms=-100, sample_rate=16000)


def test_chunk_buffer_invalid_sample_rate() -> None:
    with pytest.raises(ValidationError):
        ChunkBuffer(capacity_ms=1000, sample_rate=0)
    with pytest.raises(ValidationError):
        ChunkBuffer(capacity_ms=1000, sample_rate=-16000)


# === Test Adding Chunks ===


def test_add_chunk_updates_duration(chunk_buffer: ChunkBuffer, sample_rate: int) -> None:
    assert chunk_buffer.get_total_duration_ms() == 0
    chunk1: np.ndarray = create_dummy_audio(500, sample_rate)
    chunk_buffer.add_chunk(chunk1)
    assert chunk_buffer.get_total_duration_ms() == 500
    chunk2: np.ndarray = create_dummy_audio(300, sample_rate)
    chunk_buffer.add_chunk(chunk2)
    assert chunk_buffer.get_total_duration_ms() == 800


def test_add_chunk_updates_chunks_list(chunk_buffer: ChunkBuffer, sample_rate: int) -> None:
    chunk1: np.ndarray = create_dummy_audio(500, sample_rate)
    chunk_buffer.add_chunk(chunk1)
    chunks: list[ChunkMetadata] = chunk_buffer.get_all_chunks()
    assert len(chunks) == 1
    assert chunks[0].index == 0
    assert chunks[0].duration_ms == 500
    np.testing.assert_array_equal(chunks[0].audio_chunk, chunk1)

    chunk2: np.ndarray = create_dummy_audio(300, sample_rate)
    chunk_buffer.add_chunk(chunk2)
    chunks = chunk_buffer.get_all_chunks()
    assert len(chunks) == 2
    assert chunks[1].index == 1
    assert chunks[1].duration_ms == 300
    np.testing.assert_array_equal(chunks[1].audio_chunk, chunk2)


def test_add_chunk_evicts_old_chunks(chunk_buffer: ChunkBuffer, sample_rate: int) -> None:
    # Capacity is 5000ms
    # Add 11 chunks of 500ms (total 5500ms)
    initial_chunks_data: list[np.ndarray] = []
    for i in range(11):
        chunk_data: np.ndarray = create_dummy_audio(500, sample_rate)
        initial_chunks_data.append(chunk_data)
        chunk_buffer.add_chunk(chunk_data)
        # Check duration doesn't exceed capacity (allowing for slight float inaccuracies)
        assert chunk_buffer.get_total_duration_ms() <= chunk_buffer.capacity_ms + 10

    final_chunks: list[ChunkMetadata] = chunk_buffer.get_all_chunks()
    # Should keep the last 10 chunks (5000ms)
    assert len(final_chunks) == 10
    assert chunk_buffer.get_total_duration_ms() == 5000
    # First chunk added (index 0) should be evicted
    assert final_chunks[0].index == 1
    # Last chunk added should be present
    assert final_chunks[-1].index == 10
    np.testing.assert_array_equal(final_chunks[-1].audio_chunk, initial_chunks_data[-1])


def test_add_large_chunk_evicts_multiple(chunk_buffer: ChunkBuffer, sample_rate: int) -> None:
    # Add 5 chunks of 500ms (2500ms total)
    for i in range(5):
        chunk_buffer.add_chunk(create_dummy_audio(500, sample_rate))
    assert chunk_buffer.get_total_duration_ms() == 2500
    assert len(chunk_buffer.get_all_chunks()) == 5
    assert chunk_buffer.get_all_chunks()[0].index == 0

    # Add a large chunk of 4000ms. Capacity is 5000ms.
    # This should evict the first 3 chunks (1500ms) to make space.
    # New total duration = (2500 - 1500) + 4000 = 1000 + 4000 = 5000ms
    large_chunk: np.ndarray = create_dummy_audio(4000, sample_rate)
    chunk_buffer.add_chunk(large_chunk)

    final_chunks: list[ChunkMetadata] = chunk_buffer.get_all_chunks()
    assert len(final_chunks) == 3 # The 2 remaining old chunks + the new large one
    assert chunk_buffer.get_total_duration_ms() == 5000
    # Check that the first chunk now has index 3
    assert final_chunks[0].index == 3
    # Check the large chunk is the last one
    assert final_chunks[-1].index == 5
    np.testing.assert_array_equal(final_chunks[-1].audio_chunk, large_chunk)


def test_add_chunk_exceeding_capacity(chunk_buffer: ChunkBuffer, sample_rate: int) -> None:
    # Add a chunk larger than the total capacity (6000ms > 5000ms)
    large_chunk: np.ndarray = create_dummy_audio(6000, sample_rate)
    chunk_buffer.add_chunk(large_chunk)

    final_chunks: list[ChunkMetadata] = chunk_buffer.get_all_chunks()
    # Only the large chunk should remain
    assert len(final_chunks) == 1
    assert chunk_buffer.get_total_duration_ms() == 6000 # Duration reflects the actual chunk
    assert final_chunks[0].index == 0
    np.testing.assert_array_equal(final_chunks[0].audio_chunk, large_chunk)

    # Add another small chunk, the large one should be evicted
    small_chunk: np.ndarray = create_dummy_audio(100, sample_rate)
    chunk_buffer.add_chunk(small_chunk)
    final_chunks = chunk_buffer.get_all_chunks()
    assert len(final_chunks) == 1
    assert chunk_buffer.get_total_duration_ms() == 100
    assert final_chunks[0].index == 1
    np.testing.assert_array_equal(final_chunks[0].audio_chunk, small_chunk)


# === Test Getting Chunks ===


def test_get_all_chunks_empty(chunk_buffer: ChunkBuffer) -> None:
    assert not chunk_buffer.get_all_chunks()


def test_get_all_chunks_populated(populated_buffer: ChunkBuffer) -> None:
    chunks: list[ChunkMetadata] = populated_buffer.get_all_chunks()
    assert len(chunks) == 6
    assert chunks[0].index == 0
    assert chunks[-1].index == 5
    assert populated_buffer.get_total_duration_ms() == 3000


def test_get_recent_chunks_exact_match(populated_buffer: ChunkBuffer) -> None:
    # Buffer has 3000ms (6 x 500ms chunks)
    recent_chunks_meta: list[ChunkMetadata] = populated_buffer.get_recent_chunks(duration_ms=1500)
    assert len(recent_chunks_meta) == 3
    assert recent_chunks_meta[0].index == 3 # Should get chunks 3, 4, 5
    assert recent_chunks_meta[-1].index == 5


def test_get_recent_chunks_partial_match(populated_buffer: ChunkBuffer) -> None:
    # Request 1200ms
    recent_chunks_meta: list[ChunkMetadata] = populated_buffer.get_recent_chunks(duration_ms=1200)
    # Needs chunks 5 (500ms), 4 (500ms), 3 (500ms) -> total 1500ms
    assert len(recent_chunks_meta) == 3
    assert recent_chunks_meta[0].index == 3
    assert recent_chunks_meta[-1].index == 5


def test_get_recent_chunks_more_than_available(populated_buffer: ChunkBuffer) -> None:
    # Request 4000ms, but only 3000ms available
    recent_chunks_meta: list[ChunkMetadata] = populated_buffer.get_recent_chunks(duration_ms=4000)
    assert len(recent_chunks_meta) == 6 # Should return all chunks
    assert recent_chunks_meta[0].index == 0
    assert recent_chunks_meta[-1].index == 5


def test_get_recent_chunks_zero_duration(populated_buffer: ChunkBuffer) -> None:
    recent_chunks_meta: list[ChunkMetadata] = populated_buffer.get_recent_chunks(duration_ms=0)
    assert not recent_chunks_meta


def test_get_recent_chunks_negative_duration(populated_buffer: ChunkBuffer) -> None:
    # Should likely return empty or raise an error depending on implementation preference
    # Current implementation based on reading seems it would return empty
     with pytest.raises(ValueError): # Or check for empty list depending on desired behavior
        populated_buffer.get_recent_chunks(duration_ms=-500)


def test_get_recent_chunks_empty_buffer(chunk_buffer: ChunkBuffer) -> None:
    recent_chunks_meta: list[ChunkMetadata] = chunk_buffer.get_recent_chunks(duration_ms=1000)
    assert not recent_chunks_meta


def test_get_chunks_after_valid_index(populated_buffer: ChunkBuffer) -> None:
    # Get chunks after index 2 (should return 3, 4, 5)
    chunks_after: list[ChunkMetadata] = populated_buffer.get_chunks_after(index=2)
    assert len(chunks_after) == 3
    assert chunks_after[0].index == 3
    assert chunks_after[1].index == 4
    assert chunks_after[2].index == 5


def test_get_chunks_after_last_index(populated_buffer: ChunkBuffer) -> None:
    # Get chunks after index 5 (the last index)
    chunks_after: list[ChunkMetadata] = populated_buffer.get_chunks_after(index=5)
    assert not chunks_after


def test_get_chunks_after_nonexistent_index(populated_buffer: ChunkBuffer) -> None:
    # Get chunks after index 10 (doesn't exist)
    chunks_after: list[ChunkMetadata] = populated_buffer.get_chunks_after(index=10)
    assert not chunks_after


def test_get_chunks_after_negative_index(populated_buffer: ChunkBuffer) -> None:
    # Get chunks after index -1 (should return all chunks 0-5)
    chunks_after: list[ChunkMetadata] = populated_buffer.get_chunks_after(index=-1)
    assert len(chunks_after) == 6
    assert chunks_after[0].index == 0
    assert chunks_after[-1].index == 5


def test_get_chunks_after_empty_buffer(chunk_buffer: ChunkBuffer) -> None:
    chunks_after: list[ChunkMetadata] = chunk_buffer.get_chunks_after(index=0)
    assert not chunks_after


# === Test Clearing Buffer ===


def test_clear_buffer(populated_buffer: ChunkBuffer) -> None:
    assert populated_buffer.get_total_duration_ms() == 3000
    assert len(populated_buffer.get_all_chunks()) == 6

    populated_buffer.clear()

    assert populated_buffer.get_total_duration_ms() == 0
    assert not populated_buffer.get_all_chunks()
    # Check internal state reset
    assert populated_buffer._next_chunk_index == 0


def test_clear_empty_buffer(chunk_buffer: ChunkBuffer) -> None:
    assert chunk_buffer.get_total_duration_ms() == 0
    chunk_buffer.clear()
    assert chunk_buffer.get_total_duration_ms() == 0
    assert not chunk_buffer.get_all_chunks()


# === Test Combined Audio Data Retrieval ===

def test_get_combined_audio_data_recent(populated_buffer: ChunkBuffer, sample_rate: int) -> None:
    # Request 1500ms (last 3 chunks of 500ms)
    combined_audio: np.ndarray | None = populated_buffer.get_combined_audio_data(duration_ms=1500)
    assert combined_audio is not None
    expected_samples: int = int(sample_rate * 1500 / 1000)
    assert combined_audio.shape[0] == expected_samples

    all_chunks: list[ChunkMetadata] = populated_buffer.get_all_chunks()
    expected_combined = np.concatenate([c.audio_chunk for c in all_chunks[3:]])
    np.testing.assert_array_equal(combined_audio, expected_combined)


def test_get_combined_audio_data_all(populated_buffer: ChunkBuffer, sample_rate: int) -> None:
    # Request duration longer than available -> get all
    combined_audio: np.ndarray | None = populated_buffer.get_combined_audio_data(duration_ms=4000)
    assert combined_audio is not None
    expected_samples: int = int(sample_rate * 3000 / 1000) # Total duration is 3000ms
    assert combined_audio.shape[0] == expected_samples

    all_chunks: list[ChunkMetadata] = populated_buffer.get_all_chunks()
    expected_combined = np.concatenate([c.audio_chunk for c in all_chunks])
    np.testing.assert_array_equal(combined_audio, expected_combined)


def test_get_combined_audio_data_empty(chunk_buffer: ChunkBuffer) -> None:
    combined_audio: np.ndarray | None = chunk_buffer.get_combined_audio_data(duration_ms=1000)
    assert combined_audio is None


def test_get_combined_audio_data_zero_duration(populated_buffer: ChunkBuffer) -> None:
    combined_audio: np.ndarray | None = populated_buffer.get_combined_audio_data(duration_ms=0)
    assert combined_audio is None # Or check for empty array depending on desired behavior

def test_get_combined_audio_data_negative_duration(populated_buffer: ChunkBuffer) -> None:
     with pytest.raises(ValueError):
        populated_buffer.get_combined_audio_data(duration_ms=-500) 