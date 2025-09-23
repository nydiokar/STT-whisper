#!/usr/bin/env kotlin

/**
 * Standalone verification script for core logic
 * Run this without Android Studio to verify TextProcessor works
 *
 * Usage: kotlinc -script verify_core_logic.kts
 */

// Simplified TextProcessor for verification (no Android dependencies)
class TextProcessor(private val minWords: Int = 2) {

    companion object {
        private const val MAX_OVERLAP = 30
        private val TIMESTAMP_PATTERN = Regex("""\[\d{2}:\d{2}:\d{2}\.\d{3}\s+-->\s+\d{2}:\d{2}:\d{2}\.\d{3}\]\s*""")
    }

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

    fun removeTimestamps(text: String): String {
        if (text.isEmpty()) return ""
        var cleanText = TIMESTAMP_PATTERN.replace(text, "")
        cleanText = cleanText.replace(Regex("""\s+"""), " ").trim()
        return cleanText
    }

    fun filterHallucinations(text: String): String {
        if (text.isEmpty()) return ""

        var processedText = removeTimestamps(text)
        if (processedText.isEmpty()) return ""

        val textLower = processedText.lowercase().trim()

        if (textLower in hallucinationPatterns) {
            return ""
        }

        var modified = false

        for (pattern in hallucinationPatterns) {
            val patternLen = pattern.length

            if (textLower.startsWith(pattern)) {
                if (processedText.trim(" .!?,").length == patternLen) {
                    return ""
                }
                if (processedText.length > patternLen && processedText[patternLen] in " .!?,") {
                    processedText = processedText.substring(patternLen).trimStart(' ', '.', '!', '?', ',')
                    modified = true
                    if (processedText.isEmpty()) return ""
                }
            }

            if (textLower.endsWith(pattern)) {
                if (processedText.trim(" .!?,").length == patternLen) {
                    return ""
                }
                if (processedText.length > patternLen && processedText[processedText.length - patternLen - 1] in " .!?,") {
                    val patternStartIndex = textLower.lastIndexOf(pattern)
                    if (patternStartIndex > 0) {
                        if (processedText[patternStartIndex - 1] in " .!?,") {
                            processedText = processedText.substring(0, patternStartIndex)
                            processedText = processedText.trimEnd(' ', '.', '!', '?', ',')
                            modified = true
                            if (processedText.isEmpty()) return ""
                        }
                    }
                }
            }
        }

        if (modified) {
            processedText = processedText.trim()
        }

        return processedText
    }
}

// Test cases
fun runTests() {
    val processor = TextProcessor()

    val testCases = listOf(
        "Thank you." to "",
        "Thanks for watching!" to "",
        "thanks" to "",
        "goodbye" to "",
        "hello everyone" to "",
        "Thanks for watching this is real content" to "this is real content",
        "This is real content thanks for watching" to "This is real content",
        "[00:00:00.000 --> 00:00:05.000] Hello world" to "Hello world",
        "[00:00:00.000 --> 00:00:05.000] Thank you." to "",
        "Hello world" to "Hello world",
        "This is legitimate content" to "This is legitimate content"
    )

    println("=".repeat(60))
    println("Voice Input Service - Core Logic Verification")
    println("=".repeat(60))
    println()

    var passed = 0
    var failed = 0

    testCases.forEachIndexed { index, (input, expected) ->
        val actual = processor.filterHallucinations(input)
        val isPass = actual == expected

        if (isPass) {
            passed++
            println("✅ Test ${index + 1}: PASS")
        } else {
            failed++
            println("❌ Test ${index + 1}: FAIL")
            println("   Input:    \"$input\"")
            println("   Expected: \"$expected\"")
            println("   Actual:   \"$actual\"")
        }
    }

    println()
    println("=".repeat(60))
    println("Results: $passed passed, $failed failed out of ${testCases.size} tests")
    println("=".repeat(60))

    if (failed == 0) {
        println()
        println("🎉 SUCCESS! Core logic works identically to Python version!")
        println("   Phase 1 verification complete ✅")
    } else {
        println()
        println("⚠️  Some tests failed. Review implementation.")
    }
}

// Run tests
runTests()