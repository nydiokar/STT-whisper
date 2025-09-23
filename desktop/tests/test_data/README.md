# Test Data

This directory contains test data files used in the test suite.

## Structure

- `audio/`: Sample audio files for testing
  - `silence.wav`: 1 second of silence
  - `speech.wav`: Sample speech recording
  - `noise.wav`: Background noise sample

- `transcripts/`: Sample transcription results
  - `simple.json`: Basic transcription result
  - `complex.json`: Multi-segment transcription with timestamps

## Usage

Use these files in tests by accessing them through the `test_data_dir` fixture:

```python
def test_with_sample_audio(test_data_dir):
    audio_file = test_data_dir / "audio" / "speech.wav"
    # Use the file in your test
```

## Adding New Test Data

1. Add your test file to the appropriate subdirectory
2. Update this README with the file description
3. Add a test that uses the new test data
4. If the file is generated, add the generation script to `generate_test_data.py` 