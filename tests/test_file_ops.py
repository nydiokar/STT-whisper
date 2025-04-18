from __future__ import annotations
import os
import pytest
from pathlib import Path
from datetime import datetime
from voice_input_service.utils.file_ops import TranscriptManager

@pytest.fixture
def temp_transcript_dir(tmp_path: Path) -> Path:
    """Create a temporary directory for transcript files."""
    transcript_dir = tmp_path / "transcripts"
    transcript_dir.mkdir()
    return transcript_dir

@pytest.fixture
def transcript_manager(temp_transcript_dir: Path) -> TranscriptManager:
    """Create a TranscriptManager instance with a temporary directory."""
    return TranscriptManager(str(temp_transcript_dir))

def test_init_creates_directory(tmp_path: Path) -> None:
    """Test that initializing creates the output directory if it doesn't exist."""
    output_dir = tmp_path / "new_transcripts"
    TranscriptManager(str(output_dir))
    assert output_dir.exists()
    assert output_dir.is_dir()

def test_save_transcript(transcript_manager: TranscriptManager) -> None:
    """Test saving a transcript creates a file with correct content."""
    text = "Test transcript content"
    file_path = transcript_manager.save_transcript(text)
    
    assert os.path.exists(file_path)
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    assert content == text

def test_save_empty_transcript(transcript_manager: TranscriptManager) -> None:
    """Test saving an empty transcript returns None and logs warning."""
    file_path = transcript_manager.save_transcript("")
    assert file_path is None

def test_get_transcript_files(transcript_manager: TranscriptManager) -> None:
    """Test retrieving transcript files returns sorted list."""
    # Create test files with known timestamps
    base_time = datetime.now().timestamp()
    files = []
    for i in range(3):
        path = Path(transcript_manager.output_dir) / f"transcript_{i}.txt"
        path.write_text(f"Test {i}")
        os.utime(path, (base_time + i, base_time + i))
        files.append(str(path))
    
    result = transcript_manager.get_transcript_files()
    assert len(result) == 3
    assert result == sorted(files) 