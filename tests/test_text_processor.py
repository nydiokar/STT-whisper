from __future__ import annotations
import pytest
from voice_input_service.utils.text_processor import TextProcessor

@pytest.fixture
def text_processor() -> TextProcessor:
    """Fixture for TextProcessor with default settings."""
    return TextProcessor()

@pytest.fixture
def text_processor_min_words_5() -> TextProcessor:
    """Fixture for TextProcessor with min_words=5."""
    return TextProcessor(min_words=5)

# --- Test remove_timestamps --- 

def test_remove_timestamps_basic(text_processor: TextProcessor):
    text = "[00:00:00.000 --> 00:00:05.123] Hello world."
    expected = "Hello world."
    assert text_processor.remove_timestamps(text) == expected

def test_remove_timestamps_multiple(text_processor: TextProcessor):
    text = "[00:00:00.000 --> 00:00:05.000] Hello [00:00:05.500 --> 00:00:07.000] world."
    expected = "Hello world."
    assert text_processor.remove_timestamps(text) == expected

def test_remove_timestamps_no_timestamps(text_processor: TextProcessor):
    text = "This has no timestamps."
    assert text_processor.remove_timestamps(text) == text

def test_remove_timestamps_empty(text_processor: TextProcessor):
    assert text_processor.remove_timestamps("") == ""

# --- Test filter_hallucinations --- 

def test_filter_hallucinations_exact_match(text_processor: TextProcessor):
    # Using default filter phrases
    text = "Thanks for watching!"
    assert text_processor.filter_hallucinations(text) == ""

def test_filter_hallucinations_with_leading_trailing_space(text_processor: TextProcessor):
    text = "  Thank you.  "
    assert text_processor.filter_hallucinations(text) == ""
    
def test_filter_hallucinations_case_insensitive(text_processor: TextProcessor):
    text = "thanks for watching"
    assert text_processor.filter_hallucinations(text) == ""
    
def test_filter_hallucinations_partial_not_filtered(text_processor: TextProcessor):
    text = "Give thanks for watching the show."
    assert text_processor.filter_hallucinations(text) == text
    
def test_filter_hallucinations_not_present(text_processor: TextProcessor):
    text = "This is a normal sentence."
    assert text_processor.filter_hallucinations(text) == text
    
def test_filter_hallucinations_empty(text_processor: TextProcessor):
    assert text_processor.filter_hallucinations("") == ""

# --- Test append_text --- 

def test_append_simple(text_processor: TextProcessor):
    existing = "Hello."
    new = "World."
    expected = "Hello. World."
    assert text_processor.append_text(existing, new) == expected

def test_append_no_existing_period(text_processor: TextProcessor):
    existing = "Hello"
    new = "World."
    expected = "Hello. World."
    assert text_processor.append_text(existing, new) == expected
    
def test_append_new_no_period(text_processor: TextProcessor):
    existing = "Hello."
    new = "World"
    expected = "Hello. World"
    assert text_processor.append_text(existing, new) == expected
    
def test_append_new_starts_lowercase(text_processor: TextProcessor):
    existing = "Hello."
    new = "world."
    expected = "Hello. world."
    assert text_processor.append_text(existing, new) == expected
    
def test_append_empty_existing(text_processor: TextProcessor):
    existing = ""
    new = "Hello world."
    expected = "Hello world."
    assert text_processor.append_text(existing, new) == expected
    
def test_append_empty_new(text_processor: TextProcessor):
    existing = "Hello world."
    new = ""
    expected = "Hello world."
    assert text_processor.append_text(existing, new) == expected

def test_append_duplicate(text_processor: TextProcessor):
    existing = "This is a test."
    new = "This is a test."
    expected = "This is a test. This is a test."
    assert text_processor.append_text(existing, new) == expected

def test_append_partial_duplicate_different_case(text_processor: TextProcessor):
    existing = "Test sentence."
    new = "test sentence addition."
    expected = "Test sentence. test sentence addition."
    assert text_processor.append_text(existing, new) == expected

def test_append_avoids_double_period(text_processor: TextProcessor):
    existing = "Sentence one."
    new = ". Sentence two."
    expected = "Sentence one. . Sentence two."
    assert text_processor.append_text(existing, new) == expected
    
# --- Tests targeting format_transcript (previously clean_text) --- 

def test_format_transcript_removes_timestamps(text_processor: TextProcessor):
    text = "[00:00:01.000 --> 00:00:02.500] Clean this."
    expected = "Clean this."
    assert text_processor.format_transcript(text) == expected
    
def test_format_transcript_capitalizes_sentences(text_processor: TextProcessor):
    text = "needs capitalization. also this one? yes! okay."
    expected = "Needs capitalization. Also this one? Yes! Okay."
    assert text_processor.format_transcript(text) == expected

def test_format_transcript_keeps_existing_punct(text_processor: TextProcessor):
    text = "Has punctuation! also this? yes."
    expected = "Has punctuation! Also this? Yes."
    assert text_processor.format_transcript(text) == expected

# --- Test is_valid_utterance --- 

def test_is_valid_utterance_true(text_processor: TextProcessor):
    assert text_processor.is_valid_utterance("Hello there friend") is True # 3 words >= 2

def test_is_valid_utterance_false_short(text_processor: TextProcessor):
    assert text_processor.is_valid_utterance("Hi") is False # 1 word < 2

def test_is_valid_utterance_false_short_custom(text_processor_min_words_5: TextProcessor):
    assert text_processor_min_words_5.is_valid_utterance("This has four words") is False # 4 words < 5
    
def test_is_valid_utterance_true_custom(text_processor_min_words_5: TextProcessor):
    assert text_processor_min_words_5.is_valid_utterance("This has exactly five words now") is True # 5 words >= 5
    
def test_is_valid_utterance_removes_timestamps(text_processor: TextProcessor):
    assert text_processor.is_valid_utterance("[00:00:01.000 --> 00:00:02.000] One") is False # Only "One" word after timestamp removal
    assert text_processor.is_valid_utterance("[00:00:01.000 --> 00:00:02.000] Two words") is True 

def test_is_valid_utterance_empty(text_processor: TextProcessor):
    assert text_processor.is_valid_utterance("") is False 