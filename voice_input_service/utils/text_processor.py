from __future__ import annotations
import logging
import re
from typing import List, Optional

class TextProcessor:
    """Handles text processing operations such as filtering and formatting."""
    
    def __init__(self, min_words: int = 2) -> None:
        """Initialize the text processor.
        
        Args:
            min_words: Minimum number of words to consider a valid utterance
        """
        self.logger = logging.getLogger("voice_input_service.utils.text_processor")
        self.min_words = min_words
        self.hallucination_patterns = [
            "thanks for watching",
            "thank you for watching",
            "don't forget to subscribe",
            "like and subscribe",
            "see you in the next video",
            "thanks for listening",
            "thank you.",
            "thank you",
            "subscribe to",
            "click the",
            "check out",
            "in this video",
            "in today's video",
            "please",
            "thanks",
            "goodbye",
            "bye bye",
            "see you",
            "welcome",
            "hello everyone",
            "hi everyone",
        ]
        
        # Regular expression for matching timestamp patterns from whisper.cpp
        self.timestamp_pattern = re.compile(r'\[\d{2}:\d{2}:\d{2}\.\d{3}\s+-->\s+\d{2}:\d{2}:\d{2}\.\d{3}\]\s*')
    
    def remove_timestamps(self, text: str) -> str:
        """Remove whisper.cpp timestamp markers from text.
        
        Args:
            text: Text potentially containing timestamp markers
            
        Returns:
            Text with timestamp markers removed
        """
        if not text:
            return ""
            
        # Remove timestamp markers using regex
        clean_text = self.timestamp_pattern.sub('', text)
        
        # Remove any leading/trailing whitespace
        clean_text = clean_text.strip()
        
        if text != clean_text:
            self.logger.debug(f"Removed timestamps from text")
            
        return clean_text
    
    def filter_hallucinations(self, text: str) -> str:
        """Filter out common hallucinated phrases from Whisper.
        
        Args:
            text: Text to filter
            
        Returns:
            Filtered text
        """
        if not text:
            return ""
        
        # First remove any timestamp markers
        text = self.remove_timestamps(text)
        if not text:
            return ""
            
        # Convert to lower case for comparison
        text_lower = text.lower().strip()
        
        # Check for exact matches of common hallucinations
        if text_lower in self.hallucination_patterns:
            self.logger.debug(f"Filtered exact hallucination match: {text}")
            return ""
            
        # Check if the text starts or ends with any of these patterns
        for pattern in self.hallucination_patterns:
            if text_lower.startswith(pattern) or text_lower.endswith(pattern):
                self.logger.debug(f"Filtered prefix/suffix hallucination: {text}")
                return ""
        
        # Check for long transcripts that may contain multiple sentences
        words = text.split()
        if len(words) > 50:
            # Look for hallucinations at start or end of long texts
            # This helps avoid removing content in the middle of legitimate transcription
            first_few = " ".join(words[:3]).lower()
            last_few = " ".join(words[-3:]).lower()
            
            for pattern in self.hallucination_patterns:
                if pattern in first_few:
                    # Remove first sentence if it has hallucination
                    for i, char in enumerate(text):
                        if char in ".!?":
                            text = text[i+1:].strip()
                            self.logger.debug(f"Trimmed hallucination from start of long text")
                            break
                
                if pattern in last_few:
                    # Remove last sentence if it has hallucination
                    for i in range(len(text)-1, 0, -1):
                        if text[i] in ".!?":
                            text = text[:i+1].strip()
                            self.logger.debug(f"Trimmed hallucination from end of long text")
                            break
        
        return text
    
    def is_valid_utterance(self, text: str) -> bool:
        """Check if a transcription result is a valid utterance.
        
        Args:
            text: Text to check
            
        Returns:
            True if valid, False otherwise
        """
        # First remove timestamps
        text = self.remove_timestamps(text)
        
        if not text:
            return False
            
        # Check word count
        word_count = len(text.split())
        if word_count < self.min_words:
            self.logger.debug(f"Utterance too short: {word_count} words")
            return False
            
        return True
    
    def format_transcript(self, text: str) -> str:
        """Format a transcript for display or saving.
        
        Args:
            text: Text to format
            
        Returns:
            Formatted text
        """
        if not text:
            return ""
        
        # Remove timestamps
        text = self.remove_timestamps(text)
        if not text:
            return ""
            
        # Basic formatting:
        # 1. Ensure proper spacing after punctuation
        for punct in ".!?":
            text = text.replace(f"{punct} ", f"{punct} ")
            text = text.replace(f"{punct}", f"{punct} ")
        
        # 2. Remove double spaces
        while "  " in text:
            text = text.replace("  ", " ")
            
        # 3. Capitalize sentences
        sentences = []
        current = ""
        
        for char in text:
            current += char
            if char in ".!?":
                sentences.append(current.strip())
                current = ""
                
        if current:
            sentences.append(current.strip())
            
        # Capitalize first letter of each sentence
        for i in range(len(sentences)):
            if sentences[i]:
                sentences[i] = sentences[i][0].upper() + sentences[i][1:]
                
        return " ".join(sentences).strip()
    
    def append_text(self, accumulated: str, new_text: str) -> str:
        """Append new text to accumulated text with proper formatting.
        
        Args:
            accumulated: Existing accumulated text
            new_text: New text to append
            
        Returns:
            Updated accumulated text
        """
        if not new_text:
            return accumulated
        
        # First remove timestamps from the new text
        new_text = self.remove_timestamps(new_text)
        if not new_text:
            return accumulated
            
        if not accumulated:
            return new_text
            
        # Ensure there's proper spacing and punctuation between texts
        last_char = accumulated[-1] if accumulated else ""
        
        if last_char in ".!?":
            # We already have sentence-ending punctuation
            return f"{accumulated} {new_text}"
        else:
            # Add a period if needed
            return f"{accumulated}. {new_text}" 