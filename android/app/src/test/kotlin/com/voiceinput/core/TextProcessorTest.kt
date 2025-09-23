package com.voiceinput.core

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for TextProcessor
 *
 * These tests mirror the Python tests in desktop/tests/test_text_processor.py
 * to ensure identical behavior across platforms.
 */
class TextProcessorTest {

    private lateinit var textProcessor: TextProcessor

    @Before
    fun setUp() {
        textProcessor = TextProcessor()
    }

    // --- Test removeTimestamps ---

    @Test
    fun `removeTimestamps should remove whisper timestamp markers`() {
        val input = "[00:00:00.000 --> 00:00:05.000] Hello world"
        val expected = "Hello world"
        assertEquals(expected, textProcessor.removeTimestamps(input))
    }

    @Test
    fun `removeTimestamps should remove multiple timestamps`() {
        val input = "[00:00:00.000 --> 00:00:05.000] Hello [00:00:05.000 --> 00:00:10.000] world"
        val expected = "Hello world"
        assertEquals(expected, textProcessor.removeTimestamps(input))
    }

    @Test
    fun `removeTimestamps should handle text without timestamps`() {
        val input = "Hello world"
        assertEquals(input, textProcessor.removeTimestamps(input))
    }

    @Test
    fun `removeTimestamps should return empty string for empty input`() {
        assertEquals("", textProcessor.removeTimestamps(""))
    }

    // --- Test filterHallucinations ---

    @Test
    fun `filterHallucinations should remove exact match hallucinations`() {
        val testCases = mapOf(
            "Thank you." to "",
            "Thanks for watching!" to "",
            "thanks" to "",
            "goodbye" to "",
            "hello everyone" to ""
        )

        testCases.forEach { (input, expected) ->
            assertEquals("Failed for input: $input", expected, textProcessor.filterHallucinations(input))
        }
    }

    @Test
    fun `filterHallucinations should remove prefix hallucinations`() {
        val input = "thanks for watching this is real content"
        val expected = "this is real content"
        assertEquals(expected, textProcessor.filterHallucinations(input))
    }

    @Test
    fun `filterHallucinations should remove suffix hallucinations`() {
        val input = "this is real content thanks for watching"
        val expected = "this is real content"
        assertEquals(expected, textProcessor.filterHallucinations(input))
    }

    @Test
    fun `filterHallucinations should keep non-hallucination text`() {
        val input = "This is legitimate transcription content"
        assertEquals(input, textProcessor.filterHallucinations(input))
    }

    @Test
    fun `filterHallucinations should handle empty string`() {
        assertEquals("", textProcessor.filterHallucinations(""))
    }

    @Test
    fun `filterHallucinations should remove timestamps AND hallucinations`() {
        val input = "[00:00:00.000 --> 00:00:05.000] Thank you."
        assertEquals("", textProcessor.filterHallucinations(input))
    }

    @Test
    fun `filterHallucinations should handle mixed content`() {
        val input = "[00:00:00.000 --> 00:00:05.000] Real content here. Thanks for watching"
        val expected = "Real content here."
        assertEquals(expected, textProcessor.filterHallucinations(input))
    }

    // --- Test isValidUtterance ---

    @Test
    fun `isValidUtterance should return true for valid text`() {
        assertTrue(textProcessor.isValidUtterance("Hello world"))
        assertTrue(textProcessor.isValidUtterance("This is a longer sentence"))
    }

    @Test
    fun `isValidUtterance should return false for single word`() {
        assertFalse(textProcessor.isValidUtterance("Hello"))
    }

    @Test
    fun `isValidUtterance should return false for empty string`() {
        assertFalse(textProcessor.isValidUtterance(""))
    }

    @Test
    fun `isValidUtterance should ignore timestamps`() {
        val input = "[00:00:00.000 --> 00:00:05.000] Hello world"
        assertTrue(textProcessor.isValidUtterance(input))
    }

    // --- Test formatTranscript ---

    @Test
    fun `formatTranscript should capitalize sentences`() {
        val input = "hello world. how are you"
        val expected = "hello world. How are you"
        assertEquals(expected, textProcessor.formatTranscript(input))
    }

    @Test
    fun `formatTranscript should remove double spaces`() {
        val input = "Hello  world"
        val expected = "Hello world"
        assertEquals(expected, textProcessor.formatTranscript(input))
    }

    @Test
    fun `formatTranscript should ensure proper spacing after punctuation`() {
        val input = "Hello.World"
        val expected = "Hello. World"
        assertEquals(expected, textProcessor.formatTranscript(input))
    }

    // --- Test appendText ---

    @Test
    fun `appendText should append to empty accumulated text`() {
        val accumulated = ""
        val newText = "hello world"
        val result = textProcessor.appendText(accumulated, newText)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `appendText should detect and remove overlaps`() {
        val accumulated = "this is the first part"
        val newText = "first part. the second part"
        val result = textProcessor.appendText(accumulated, newText)
        // Should not duplicate "first part"
        assertFalse(result.contains("first part. the second part"))
        assertTrue(result.contains("second part"))
    }

    @Test
    fun `appendText should add proper spacing`() {
        val accumulated = "Hello"
        val newText = "world"
        val result = textProcessor.appendText(accumulated, newText)
        assertEquals("Hello world", result)
    }

    @Test
    fun `appendText should capitalize after sentence ending`() {
        val accumulated = "Hello world."
        val newText = "this is new"
        val result = textProcessor.appendText(accumulated, newText)
        assertTrue(result.endsWith("This is new") || result.contains(". T"))
    }

    @Test
    fun `appendText should return accumulated if new text is empty`() {
        val accumulated = "Hello world"
        val result = textProcessor.appendText(accumulated, "")
        assertEquals(accumulated, result)
    }

    @Test
    fun `appendText should not add space before punctuation`() {
        val accumulated = "Hello world"
        val newText = "."
        val result = textProcessor.appendText(accumulated, newText)
        assertEquals("Hello world.", result)
    }
}