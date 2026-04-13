package com.voiceinput.core

/**
 * Handles text processing operations such as filtering and formatting.
 *
 * This is a direct port of the Python TextProcessor from desktop/voice_input_service/utils/text_processor.py
 * The goal is to maintain identical behavior across platforms.
 *
 * Processing Pipeline:
 * 1. filterHallucinations() - Remove Whisper artifacts
 * 2. formatStructuredData() - Convert spoken patterns to symbols (emails, URLs, etc.)
 * 3. applyPunctuation() - Future: voice commands for punctuation
 * 4. fixCapitalization() - Future: smart capitalization
 *
 * @param minWords Minimum number of words to consider a valid utterance (default: 2)
 * @param enableSmartFormatting Enable email/URL/phone formatting (default: true)
 */
class TextProcessor(
    private val minWords: Int = 2,
    private var enableSmartFormatting: Boolean = true
) {

    companion object {
        // Maximum overlap length to prevent excessive searching
        private const val MAX_OVERLAP = 80

        // Regular expression for matching timestamp patterns from whisper.cpp
        // Pattern: [00:00:00.000 --> 00:00:05.000]
        private val TIMESTAMP_PATTERN = Regex("""\[\d{2}:\d{2}:\d{2}\.\d{3}\s+-->\s+\d{2}:\d{2}:\d{2}\.\d{3}\]\s*""")
    }

    // Custom vocabulary corrections (personalized)
    // Maps common misrecognitions to correct terms
    private val customVocabulary = mapOf(
        // Personal identifiers (GitHub username)
        "nitiocard" to "Nydiokar",
        "nydiokar" to "Nydiokar",  // Already correct but ensure capitalization
        "need yocar" to "Nydiokar",
        "need your car" to "Nydiokar",
        "netiocard" to "Nydiokar",
        "knitiocard" to "Nydiokar",

        // Add more as you discover them
        // "some wrong phrase" to "correct phrase"
    )

    // Hallucination patterns - common Whisper artifacts
    private val hallucinationPatterns = listOf(
        // YouTube/Video patterns
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

        // Silence/blank audio patterns
        "[blank_audio]",
        "[blank audio]",
        "[silence]",

        // Music/sound effect patterns
        "(upbeat music)",
        "(music)",
        "(music playing)",
        "(background music)",
        "(applause)",
        "(laughter)",
        "(audience chattering)",
        "(audience laughing)",
        "(audience applauding)",
        "[music]",
        "[applause]",
        "[laughter]"
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
                if (processedText.trim(' ', '.', '!', '?', ',').length == patternLen) {
                    return ""
                }
                // If it starts with pattern + space/punct
                if (processedText.length > patternLen && processedText[patternLen] in " .!?,") {
                    processedText = processedText.substring(patternLen).trimStart(' ', '.', '!', '?', ',')
                    modified = true
                    if (processedText.isEmpty()) return ""
                }
            }

            // Check endswith
            if (textLower.endsWith(pattern)) {
                // If it's an exact match (or only differs by surrounding spaces/punct), return empty
                if (processedText.trim(' ', '.', '!', '?', ',').length == patternLen) {
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

        // Special case: If it contains @ or common TLDs, it's likely structured data (email/URL)
        // and should be considered valid even if it's "one word"
        if (cleanText.contains("@") ||
            cleanText.contains(".com") ||
            cleanText.contains(".org") ||
            cleanText.contains(".net")) {
            return true
        }

        // Check word count for regular text
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

        // Normalize only boundary artifacts; avoid reformatting the whole chunk aggressively.
        val formattedNewText = normalizeChunkBoundaryText(newText.trim())

        if (formattedNewText.isEmpty()) return accumulated

        if (accumulated.isEmpty()) {
            // If accumulated is empty, return formatted new text
            return formatTranscript(formattedNewText)
        }

        val normalizedAccumulated = normalizeChunkBoundaryText(accumulated.trimEnd())
        val overlapWords = findWordOverlap(normalizedAccumulated, formattedNewText)
        val newChunkWithoutOverlap = dropLeadingWords(formattedNewText, overlapWords)
        if (newChunkWithoutOverlap.isEmpty()) {
            return normalizedAccumulated
        }

        var result = normalizedAccumulated.trimEnd()
        val appendPart = adaptChunkLeadingCase(result, newChunkWithoutOverlap)
        val separator = determineChunkSeparator(result, appendPart)
        return normalizeChunkBoundaryText("$result$separator$appendPart")
    }

    private fun determineChunkSeparator(accumulated: String, newText: String): String {
        val lastCharAcc = accumulated.lastOrNull() ?: return ""
        val firstCharNew = newText.firstOrNull() ?: return ""

        if (lastCharAcc.isWhitespace() || firstCharNew.isWhitespace()) return ""
        if (firstCharNew in ".,?!:;)]}") return ""
        if (lastCharAcc in "([{\"'") return ""

        return if (lastCharAcc.isLetterOrDigit() && firstCharNew.isLetterOrDigit()) " " else " "
    }

    private fun adaptChunkLeadingCase(accumulated: String, newText: String): String {
        if (newText.isEmpty()) return newText

        val previousChar = accumulated.trimEnd().lastOrNull() ?: return newText
        if (previousChar in ".!?") {
            return newText
        }

        val firstWord = newText.takeWhile { !it.isWhitespace() }
        if (firstWord.length < 2) {
            return newText
        }

        val shouldLowercaseStarter =
            firstWord.first().isUpperCase() &&
            firstWord.getOrNull(1)?.isLowerCase() == true &&
            firstWord.lowercase() in boundaryContinuationWords

        return if (shouldLowercaseStarter) {
            firstWord.replaceFirstChar { it.lowercase() } + newText.substring(firstWord.length)
        } else {
            newText
        }
    }

    private fun findWordOverlap(accumulated: String, newText: String): Int {
        val accumulatedWords = tokenizeForBoundaryMatch(accumulated)
        val newWords = tokenizeForBoundaryMatch(newText)
        val maxWords = minOf(accumulatedWords.size, newWords.size, 12)

        for (overlap in maxWords downTo 2) {
            if (accumulatedWords.takeLast(overlap) == newWords.take(overlap)) {
                return overlap
            }
        }
        return 0
    }

    private fun dropLeadingWords(text: String, wordsToDrop: Int): String {
        if (wordsToDrop <= 0) return text.trim()

        var index = 0
        var dropped = 0
        while (index < text.length && dropped < wordsToDrop) {
            while (index < text.length && text[index].isWhitespace()) {
                index++
            }
            while (index < text.length && !text[index].isWhitespace()) {
                index++
            }
            dropped++
        }
        return text.substring(index).trimStart()
    }

    private fun tokenizeForBoundaryMatch(text: String): List<String> {
        return text
            .lowercase()
            .replace(Regex("""[^\p{L}\p{N}'\s-]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.isNotEmpty() }
            .takeLast(MAX_OVERLAP)
    }

    private fun normalizeChunkBoundaryText(text: String): String {
        if (text.isEmpty()) return text

        return text
            .replace(Regex("""(?<=[a-z])(?=[A-Z])"""), " ")
            .replace(Regex("""(?<=[.,!?])(?=[A-Za-z])"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Format structured data like emails, URLs, and phone numbers.
     * Converts spoken patterns to their symbolic forms.
     *
     * Examples:
     * - "john at gmail dot com" → "john@gmail.com"
     * - "google dot com" → "google.com"
     * - "www dot example dot org" → "www.example.org"
     *
     * @param text Text to format
     * @return Text with structured data formatted
     */
    fun formatStructuredData(text: String): String {
        if (!enableSmartFormatting || text.isEmpty()) return text

        var result = text

        // Apply email formatting
        result = formatEmails(result)

        // Apply URL formatting
        result = formatURLs(result)

        // Future: phone numbers, addresses, etc.

        return result
    }

    /**
     * Format email addresses from spoken form.
     * Pattern: "word at word dot word" → "word@word.word"
     */
    private fun formatEmails(text: String): String {
        var result = text

        // Pattern: Look for sequences like "word at word dot com/org/net"
        // Example: "contact me at john dot smith at gmail dot com"
        val emailPattern = Regex(
            """(\b[\w]+(?:\s+dot\s+[\w]+)*\s+at\s+[\w]+(?:\s+dot\s+[\w]+)+)""",
            RegexOption.IGNORE_CASE
        )

        result = emailPattern.replace(result) { match ->
            var email = match.value
                .replace(Regex("""\s+at\s+""", RegexOption.IGNORE_CASE), "@")
                .replace(Regex("""\s+dot\s+""", RegexOption.IGNORE_CASE), ".")
                .replace(Regex("""\s+"""), "")  // Remove all remaining spaces
            email
        }

        // Also handle simple patterns without context
        // "john at gmail dot com" even if not in a sentence
        result = result
            .replace(Regex("""\bat\b""", RegexOption.IGNORE_CASE)) { "@" }
            .replace(Regex("""\bdot\b""", RegexOption.IGNORE_CASE)) { "." }

        // Clean up multiple spaces that may have been created
        result = result.replace(Regex("""\s+"""), " ").trim()

        return result
    }

    /**
     * Format URLs from spoken form.
     * Pattern: "www dot example dot com" → "www.example.com"
     */
    private fun formatURLs(text: String): String {
        var result = text

        // Pattern 1: URLs starting with www
        // "www dot google dot com"
        val wwwPattern = Regex(
            """\bw+\s+w+\s+w+\s+dot\s+[\w]+(?:\s+dot\s+[\w]+)+""",
            RegexOption.IGNORE_CASE
        )

        result = wwwPattern.replace(result) { match ->
            match.value
                .replace(Regex("""\bw+\s+w+\s+w+\b""", RegexOption.IGNORE_CASE), "www")
                .replace(Regex("""\s+dot\s+""", RegexOption.IGNORE_CASE), ".")
                .replace(Regex("""\s+"""), "")
        }

        // Pattern 2: Simple domain names
        // "google dot com", "github dot io"
        val domainPattern = Regex(
            """\b[\w]+\s+dot\s+(?:com|org|net|edu|gov|io|co|uk|de|fr|jp|cn)\b""",
            RegexOption.IGNORE_CASE
        )

        result = domainPattern.replace(result) { match ->
            match.value
                .replace(Regex("""\s+dot\s+""", RegexOption.IGNORE_CASE), ".")
                .replace(Regex("""\s+"""), "")
        }

        // Pattern 3: URLs with protocol
        // "https colon slash slash google dot com"
        val protocolPattern = Regex(
            """\b(https?|ftp)\s+colon\s+slash\s+slash\s+[\w]+(?:\s+dot\s+[\w]+)+""",
            RegexOption.IGNORE_CASE
        )

        result = protocolPattern.replace(result) { match ->
            match.value
                .replace(Regex("""\s+colon\s+""", RegexOption.IGNORE_CASE), ":")
                .replace(Regex("""\s+slash\s+""", RegexOption.IGNORE_CASE), "/")
                .replace(Regex("""\s+dot\s+""", RegexOption.IGNORE_CASE), ".")
                .replace(Regex("""\s+"""), "")
        }

        // Clean up
        result = result.replace(Regex("""\s+"""), " ").trim()

        return result
    }

    /**
     * Apply custom vocabulary corrections.
     * Replaces commonly misrecognized phrases with correct ones.
     *
     * Examples:
     * - "nitiocard" → "Nydiokar"
     * - "need yocar" → "Nydiokar"
     */
    private fun applyCustomVocabulary(text: String): String {
        var result = text

        // Apply case-insensitive replacements
        for ((wrong, correct) in customVocabulary) {
            // Replace whole word matches (with word boundaries)
            val pattern = Regex("\\b$wrong\\b", RegexOption.IGNORE_CASE)
            result = pattern.replace(result, correct)
        }

        return result
    }

    /**
     * Process text through the full pipeline.
     * Convenience method that applies all processing stages.
     *
     * @param rawText Raw transcription from Whisper
     * @return Fully processed text ready for output
     */
    fun process(rawText: String): String {
        if (rawText.isEmpty()) return ""

        // Stage 1: Remove hallucinations
        var text = filterHallucinations(rawText)
        if (text.isEmpty()) return ""

        // Stage 2: Apply custom vocabulary (personal corrections)
        text = applyCustomVocabulary(text)

        // Stage 3: Format structured data (emails, URLs)
        text = formatStructuredData(text)

        // Stage 4: Future - Apply punctuation commands
        // text = applyPunctuation(text)

        // Stage 5: Future - Fix capitalization
        // text = fixCapitalization(text)

        return text
    }

    /**
     * Process accumulated text at the end of a recording.
     * Intended to run after per-chunk filtering has already happened.
     */
    fun processFinal(rawText: String): String {
        if (rawText.isEmpty()) return ""

        // Remove timestamps and trim once at the end
        var text = normalizeChunkBoundaryText(removeTimestamps(rawText).trim())
        if (text.isEmpty()) return ""

        // Apply custom vocabulary (personal corrections)
        text = applyCustomVocabulary(text)

        // Format structured data (emails, URLs)
        text = formatStructuredData(text)

        return text
    }

    private val boundaryContinuationWords = setOf(
        "a", "an", "and", "are", "as", "at", "because", "but", "for", "if",
        "in", "is", "it", "maybe", "of", "on", "or", "so", "that", "the",
        "then", "there", "these", "this", "those", "to", "we", "when", "while",
        "with", "you"
    )

    /**
     * Toggle smart formatting on/off at runtime.
     */
    fun setSmartFormattingEnabled(enabled: Boolean) {
        enableSmartFormatting = enabled
    }
}
