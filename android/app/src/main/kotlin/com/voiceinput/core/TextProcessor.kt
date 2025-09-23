package com.voiceinput.core

/**
 * Handles text processing operations such as filtering and formatting.
 *
 * This is a direct port of the Python TextProcessor from desktop/voice_input_service/utils/text_processor.py
 * The goal is to maintain identical behavior across platforms.
 *
 * @param minWords Minimum number of words to consider a valid utterance (default: 2)
 */
class TextProcessor(private val minWords: Int = 2) {

    companion object {
        // Maximum overlap length to prevent excessive searching
        private const val MAX_OVERLAP = 30

        // Regular expression for matching timestamp patterns from whisper.cpp
        // Pattern: [00:00:00.000 --> 00:00:05.000]
        private val TIMESTAMP_PATTERN = Regex("""\[\d{2}:\d{2}:\d{2}\.\d{3}\s+-->\s+\d{2}:\d{2}:\d{2}\.\d{3}\]\s*""")
    }

    // Hallucination patterns - common Whisper artifacts
    private val hallucinationPatterns = listOf(
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
        "hi everyone"
    )

    /**
     * Remove whisper.cpp timestamp markers from text.
     *
     * @param text Text potentially containing timestamp markers
     * @return Text with timestamp markers removed
     */
    fun removeTimestamps(text: String): String {
        if (text.isEmpty()) return ""

        // Remove timestamp markers using regex
        var cleanText = TIMESTAMP_PATTERN.replace(text, "")

        // Replace multiple spaces with single space and trim
        cleanText = cleanText.replace(Regex("""\s+"""), " ").trim()

        return cleanText
    }

    /**
     * Filter out common hallucinated phrases from Whisper.
     *
     * @param text Text to filter
     * @return Filtered text
     */
    fun filterHallucinations(text: String): String {
        if (text.isEmpty()) return ""

        // First remove any timestamp markers
        var processedText = removeTimestamps(text)
        if (processedText.isEmpty()) return ""

        // Convert to lowercase for comparison
        val textLower = processedText.lowercase().trim()

        // Check for exact matches of common hallucinations
        if (textLower in hallucinationPatterns) {
            return "" // Exact match returns empty
        }

        // Check if the text starts or ends with any of these patterns
        var modified = false

        for (pattern in hallucinationPatterns) {
            val patternLen = pattern.length

            // Check startswith
            if (textLower.startsWith(pattern)) {
                // If it's an exact match (or only differs by surrounding spaces/punct), return empty
                if (processedText.trim(" .!?,").length == patternLen) {
                    return ""
                }
                // If it starts with pattern + space/punct
                if (processedText.length > patternLen && processedText[patternLen] in " .!?,") {
                    processedText = processedText.substring(patternLen).trimStart(" .!?,")
                    modified = true
                    if (processedText.isEmpty()) return ""
                }
            }

            // Check endswith
            if (textLower.endsWith(pattern)) {
                // If it's an exact match (or only differs by surrounding spaces/punct), return empty
                if (processedText.trim(" .!?,").length == patternLen) {
                    return ""
                }
                // If it ends with space/punct + pattern
                if (processedText.length > patternLen && processedText[processedText.length - patternLen - 1] in " .!?,") {
                    // Find where pattern starts
                    val patternStartIndex = textLower.lastIndexOf(pattern)
                    if (patternStartIndex > 0) {
                        // Check character before pattern start
                        if (processedText[patternStartIndex - 1] in " .!?,") {
                            // Slice up to the character before the pattern starts
                            processedText = processedText.substring(0, patternStartIndex)
                            // Trim only the space/punct immediately before the pattern
                            processedText = processedText.trimEnd(' ', '.', '!', '?', ',')
                            modified = true
                            if (processedText.isEmpty()) return ""
                        }
                    }
                }
            }
        }

        // Re-trim after potential modifications
        if (modified) {
            processedText = processedText.trim()
        }

        // Check long transcripts for hallucinations at start/end
        val words = processedText.split(Regex("""\s+"""))
        if (words.size > 50) {
            // Look for hallucinations at start or end of long texts
            val firstFew = words.take(3).joinToString(" ").lowercase()
            val lastFew = words.takeLast(3).joinToString(" ").lowercase()

            for (pattern in hallucinationPatterns) {
                if (pattern in firstFew) {
                    // Remove first sentence if it has hallucination
                    val sentenceEndIndex = processedText.indexOfAny(charArrayOf('.', '!', '?'))
                    if (sentenceEndIndex != -1) {
                        processedText = processedText.substring(sentenceEndIndex + 1).trim()
                    }
                }

                if (pattern in lastFew) {
                    // Remove last sentence if it has hallucination
                    val sentenceEndIndex = processedText.lastIndexOfAny(charArrayOf('.', '!', '?'))
                    if (sentenceEndIndex > 0) {
                        processedText = processedText.substring(0, sentenceEndIndex + 1).trim()
                    }
                }
            }
        }

        return processedText
    }

    /**
     * Check if a transcription result is a valid utterance.
     *
     * @param text Text to check
     * @return True if valid, False otherwise
     */
    fun isValidUtterance(text: String): Boolean {
        // First remove timestamps
        val cleanText = removeTimestamps(text)

        if (cleanText.isEmpty()) return false

        // Check word count
        val wordCount = cleanText.split(Regex("""\s+""")).size
        return wordCount >= minWords
    }

    /**
     * Format a transcript for display or saving.
     *
     * @param text Text to format
     * @return Formatted text
     */
    fun formatTranscript(text: String): String {
        if (text.isEmpty()) return ""

        // Remove timestamps
        var formattedText = removeTimestamps(text)
        if (formattedText.isEmpty()) return ""

        // Ensure proper spacing after punctuation
        for (punct in listOf('.', '!', '?')) {
            formattedText = formattedText.replace("$punct ", "$punct ")
            formattedText = formattedText.replace("$punct", "$punct ")
        }

        // Remove double spaces
        while ("  " in formattedText) {
            formattedText = formattedText.replace("  ", " ")
        }

        // Capitalize sentences
        val sentences = mutableListOf<String>()
        var current = ""

        for (char in formattedText) {
            current += char
            if (char in ".!?") {
                sentences.add(current.trim())
                current = ""
            }
        }

        if (current.isNotEmpty()) {
            sentences.add(current.trim())
        }

        // Capitalize first letter of each sentence
        val capitalizedSentences = sentences.map { sentence ->
            if (sentence.isNotEmpty()) {
                sentence.first().uppercase() + sentence.substring(1)
            } else {
                sentence
            }
        }

        return capitalizedSentences.joinToString(" ").trim()
    }

    /**
     * Append new text to accumulated text intelligently.
     *
     * Handles potential overlaps, ensures proper spacing and capitalization.
     *
     * @param accumulated Existing accumulated text
     * @param newText New text to append
     * @return Updated accumulated text
     */
    fun appendText(accumulated: String, newText: String): String {
        if (newText.isEmpty()) return accumulated

        // Clean the new text - just strip it
        val formattedNewText = newText.trim()

        if (formattedNewText.isEmpty()) return accumulated

        if (accumulated.isEmpty()) {
            // If accumulated is empty, return formatted new text
            return formatTranscript(formattedNewText)
        }

        // Simple overlap check (Whisper often repeats segments)
        val accumulatedLower = accumulated.lowercase().trimEnd()
        val newLower = formattedNewText.lowercase()
        var bestOverlap = 0

        // Start check from a reasonably small overlap to avoid false positives
        for (overlap in minOf(accumulatedLower.length, newLower.length, MAX_OVERLAP) downTo 3) {
            if (accumulatedLower.endsWith(newLower.take(overlap))) {
                bestOverlap = overlap
                break
            }
        }

        if (bestOverlap > 0) {
            // Append only the non-overlapping part
            val nonOverlappingPart = formattedNewText.substring(bestOverlap)
            val nonOverlappingPartStripped = nonOverlappingPart.trim()

            // Check if non-overlapping is just punctuation
            if (nonOverlappingPartStripped.isNotEmpty() &&
                nonOverlappingPartStripped.length <= 2 &&
                nonOverlappingPartStripped.all { it in ".!?," }) {
                // Check if accumulated already ends with this punctuation
                if (accumulated.trimEnd().endsWith(nonOverlappingPartStripped)) {
                    return accumulated.trimEnd()
                }
                // Handle specific case like adding '.' when ends with '..'
                if (nonOverlappingPartStripped == "." && accumulated.trimEnd().endsWith("..")) {
                    return accumulated.trimEnd()
                }
            }

            if (nonOverlappingPartStripped.isEmpty()) {
                return accumulated.trimEnd()
            }

            // If there's a real non-overlapping part to add
            var result = accumulated.trimEnd()
            val lastCharAcc = result.lastOrNull() ?: ' '
            val firstCharNew = nonOverlappingPartStripped.firstOrNull() ?: ' '

            // Add space unless previous ends in space/newline or new part starts with punctuation
            val separator = if (lastCharAcc in "\\n " || firstCharNew in ".,'?!") "" else " "

            // Capitalize if previous ended a sentence
            val appendPart = if (lastCharAcc in ".!?") {
                // Find first letter to capitalize
                val firstLetterIndex = nonOverlappingPartStripped.indexOfFirst { it.isLetter() }
                if (firstLetterIndex != -1) {
                    nonOverlappingPartStripped.substring(0, firstLetterIndex) +
                    nonOverlappingPartStripped[firstLetterIndex].uppercase() +
                    nonOverlappingPartStripped.substring(firstLetterIndex + 1)
                } else {
                    nonOverlappingPartStripped
                }
            } else {
                nonOverlappingPartStripped
            }

            return "$result$separator$appendPart"
        } else {
            // No significant overlap found, append with formatting
            var result = accumulated.trimEnd()
            val lastCharAcc = result.lastOrNull() ?: ' '
            val firstCharNew = formattedNewText.firstOrNull() ?: ' '

            // Add space unless previous ends in space/newline or new part starts with punctuation
            val separator = if (lastCharAcc in "\\n " || firstCharNew in ".,'?!") "" else " "

            // Capitalize if accumulated is empty or ends with sentence-ending punctuation
            val appendPart = if (result.isEmpty() || lastCharAcc in ".!?") {
                // Find first letter to capitalize
                val firstLetterIndex = formattedNewText.indexOfFirst { it.isLetter() }
                if (firstLetterIndex != -1) {
                    formattedNewText.substring(0, firstLetterIndex) +
                    formattedNewText[firstLetterIndex].uppercase() +
                    formattedNewText.substring(firstLetterIndex + 1)
                } else {
                    formattedNewText
                }
            } else {
                formattedNewText
            }

            return "$result$separator$appendPart"
        }
    }
}