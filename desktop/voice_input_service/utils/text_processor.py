from __future__ import annotations
import logging
import re
from typing import List, Optional

# Define a maximum overlap length to prevent excessive searching
MAX_OVERLAP = 30

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
            "thanks for watching!",
            "subscribe to",
            "click the",
            "check out",
            "in this video",
            "in today's video",
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
        
        # Remove any leading/trailing whitespace AFTER removing timestamps
        # And replace multiple spaces that might result from substitution with a single space
        clean_text = re.sub(r'\s+', ' ', clean_text).strip() # Replace multiple spaces and strip
        
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
            return "" # Exact match still returns empty

        # Check if the text starts or ends with any of these patterns
        original_text = text # Keep original for slicing
        modified = False
        for pattern in self.hallucination_patterns:
            pattern_len = len(pattern)
            # Check startswith
            if text_lower.startswith(pattern):
                # If it's an exact match (or only differs by surrounding spaces/punct), return empty
                if len(text.strip(" .!?,")) == pattern_len:
                     self.logger.debug(f"Filtered near-exact prefix match: {pattern}")
                     return ""
                # If it starts with pattern + space/punct
                if len(text) > pattern_len and text[pattern_len] in " .!?,":
                    text = text[pattern_len:].lstrip(" .!?,")
                    text_lower = text.lower().strip() # Update lower text
                    self.logger.debug(f"Filtered prefix hallucination: {pattern}")
                    modified = True
                    if not text: return ""

            # Check endswith
            if text_lower.endswith(pattern):
                 # If it's an exact match (or only differs by surrounding spaces/punct), return empty
                 if len(text.strip(" .!?,")) == pattern_len:
                     self.logger.debug(f"Filtered near-exact suffix match: {pattern}")
                     return ""
                 # If it ends with space/punct + pattern
                 if len(text) > pattern_len and text[-(pattern_len + 1)] in " .!?,":
                    # Find where pattern starts, being careful with case for index
                    pattern_start_index = text_lower.rfind(pattern)
                    if pattern_start_index > 0:
                        # Check character before pattern start
                        if text[pattern_start_index - 1] in " .!?,":
                             # Slice up to the character *before* the pattern starts
                             text = text[:pattern_start_index]
                             # Rstrip only the space/punct immediately before the pattern
                             text = text.rstrip(" .!?,")
                             text_lower = text.lower().strip() # Update lower text
                             self.logger.debug(f"Filtered suffix hallucination: {pattern}")
                             modified = True
                             if not text: return "" # Return empty if nothing left

        # Re-strip after potential modifications
        if modified:
             text = text.strip()

        # Keep the long transcript check for now, but it might be redundant
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
        """Append new text to accumulated text intelligently.

        Handles potential overlaps, ensures proper spacing and capitalization.

        Args:
            accumulated: Existing accumulated text
            new_text: New text to append

        Returns:
            Updated accumulated text
        """
        if not new_text:
            return accumulated

        # Clean the new text first - just strip it, assume filtering/formatting happens elsewhere
        formatted_new_text = new_text.strip()

        if not formatted_new_text:
            return accumulated

        if not accumulated:
            # If accumulated is empty, just return the formatted new text (capitalized by format_transcript)
            return self.format_transcript(formatted_new_text) # Ensure first sentence is capitalized

        # Simple Overlap Check (Whisper often repeats segments)
        accumulated_lower = accumulated.lower().rstrip() # Compare without trailing space
        new_lower = formatted_new_text.lower()
        best_overlap = 0
        # Start check from a reasonably small overlap to avoid false positives
        for overlap in range(min(len(accumulated_lower), len(new_lower), MAX_OVERLAP), 2, -1): # Require overlap > 2
            if accumulated_lower.endswith(new_lower[:overlap]):
                best_overlap = overlap
                self.logger.debug(f"Overlap found: {best_overlap} chars ('{accumulated[-best_overlap:]}' vs '{formatted_new_text[:best_overlap]}')")
                break

        if best_overlap > 0:
            # Append only the non-overlapping part
            non_overlapping_part = formatted_new_text[best_overlap:]
            non_overlapping_part_stripped = non_overlapping_part.strip()

            # Check if non-overlapping is just punctuation and if it matches the end of accumulated
            if non_overlapping_part_stripped and len(non_overlapping_part_stripped) <= 2 and all(c in '.!?,' for c in non_overlapping_part_stripped): # Check for 1 or 2 punct chars
                 # Check if accumulated already ends with this punctuation
                 if accumulated.rstrip().endswith(non_overlapping_part_stripped):
                     self.logger.debug(f"Ignoring redundant punctuation append: '{non_overlapping_part_stripped}'")
                     return accumulated.rstrip() # Return original stripped
                 # Handle specific case like adding '.' when ends with '..'
                 if non_overlapping_part_stripped == '.' and accumulated.rstrip().endswith('..'):
                     self.logger.debug(f"Ignoring redundant period append after ellipsis.")
                     return accumulated.rstrip()

            if not non_overlapping_part_stripped:
                self.logger.debug(f"New text fully overlapped or only spaces: '{new_text}'")
                return accumulated.rstrip() # Return original, potentially stripped of trailing space

            # If there's a real non-overlapping part to add
            accumulated = accumulated.rstrip() # Ensure no trailing space on original
            last_char_acc = accumulated[-1] if accumulated else ''
            first_char_new = non_overlapping_part_stripped[0] if non_overlapping_part_stripped else ''

            # Add space unless previous ends in space/newline or new part starts with punctuation
            separator = ' '
            if not accumulated or last_char_acc in ('\\n', ' ') or first_char_new in ('.', ',', '?', '!'):
                separator = ''

            # Capitalize the non-overlapping part if previous ended a sentence
            if last_char_acc in '.!?':
                 # Find first letter to capitalize, skipping leading non-alpha
                 first_letter_index = -1
                 for i, char in enumerate(non_overlapping_part_stripped):
                     if char.isalpha():
                         first_letter_index = i
                         break
                 if first_letter_index != -1:
                     append_part = non_overlapping_part_stripped[:first_letter_index] + \
                                   non_overlapping_part_stripped[first_letter_index].upper() + \
                                   non_overlapping_part_stripped[first_letter_index+1:]
                 else:
                     append_part = non_overlapping_part_stripped # No letters to capitalize
            else:
                 append_part = non_overlapping_part_stripped

            self.logger.debug(f"Appending non-overlapping part: '{append_part}'")
            return f"{accumulated}{separator}{append_part}"
        else:
            # No significant overlap found, append with formatting
            accumulated = accumulated.rstrip()
            last_char_acc = accumulated[-1] if accumulated else ''
            first_char_new = formatted_new_text[0] if formatted_new_text else ''

            # Add space unless previous ends in space/newline or new part starts with punctuation
            separator = ' '
            if not accumulated or last_char_acc in ('\\n', ' ') or first_char_new in ('.', ',', '?', '!'):
                separator = ''

            # Capitalize if accumulated text is empty or ends with sentence-ending punctuation.
            if not accumulated or last_char_acc in '.!?':
                # Find first letter to capitalize
                 first_letter_index = -1
                 for i, char in enumerate(formatted_new_text):
                     if char.isalpha():
                         first_letter_index = i
                         break
                 if first_letter_index != -1:
                     append_part = formatted_new_text[:first_letter_index] + \
                                   formatted_new_text[first_letter_index].upper() + \
                                   formatted_new_text[first_letter_index+1:]
                 else:
                     append_part = formatted_new_text # No letters to capitalize
            else:
                append_part = formatted_new_text

            self.logger.debug(f"Appending with formatting: '{append_part}'")
            return f"{accumulated}{separator}{append_part}"