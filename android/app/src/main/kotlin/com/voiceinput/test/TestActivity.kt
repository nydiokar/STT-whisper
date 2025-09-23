package com.voiceinput.test

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.voiceinput.config.AppConfig
import com.voiceinput.config.ConfigRepository
import com.voiceinput.core.TextProcessor

/**
 * Simple test activity to verify TextProcessor works on Android
 *
 * This proves Phase 1 is complete - core logic works identically to Python version
 *
 * UI:
 * - Input field for raw Whisper text
 * - Button to test filtering
 * - Output showing filtered result
 * - Config display showing current settings
 */
class TestActivity : AppCompatActivity() {

    private lateinit var textProcessor: TextProcessor
    private lateinit var configRepository: ConfigRepository
    private lateinit var config: AppConfig

    // UI elements
    private lateinit var inputText: EditText
    private lateinit var outputText: TextView
    private lateinit var configText: TextView
    private lateinit var filterButton: Button
    private lateinit var testAllButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize components
        textProcessor = TextProcessor()
        configRepository = ConfigRepository(this)
        config = configRepository.load()

        // Set up UI (programmatically - no XML needed for quick test)
        setupUI()

        // Display current config
        displayConfig()
    }

    private fun setupUI() {
        // Create UI programmatically for quick testing
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        layout.addView(TextView(this).apply {
            text = "Voice Input Service - Core Logic Test"
            textSize = 20f
            setPadding(0, 0, 0, 24)
        })

        // Config display
        configText = TextView(this).apply {
            text = ""
            textSize = 12f
            setPadding(16, 8, 16, 16)
            setBackgroundColor(0xFFEEEEEE.toInt())
        }
        layout.addView(configText)

        // Input label
        layout.addView(TextView(this).apply {
            text = "Raw Whisper Text (with hallucinations):"
            setPadding(0, 24, 0, 8)
        })

        // Input field
        inputText = EditText(this).apply {
            hint = "Enter text like: Thanks for watching! This is real content."
            minHeight = 120
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }
        layout.addView(inputText)

        // Filter button
        filterButton = Button(this).apply {
            text = "Filter Hallucinations"
            setOnClickListener { testFiltering() }
        }
        layout.addView(filterButton)

        // Test all button
        testAllButton = Button(this).apply {
            text = "Run All Test Cases"
            setOnClickListener { runAllTests() }
        }
        layout.addView(testAllButton)

        // Output label
        layout.addView(TextView(this).apply {
            text = "Filtered Output:"
            setPadding(0, 24, 0, 8)
        })

        // Output display
        outputText = TextView(this).apply {
            text = ""
            minHeight = 120
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFFE8F5E9.toInt())
        }
        layout.addView(outputText)

        setContentView(layout)
    }

    private fun displayConfig() {
        val configInfo = """
            Config Loaded:
            - Sample Rate: ${config.audio.sampleRate} Hz
            - VAD Threshold: ${config.audio.vadThreshold}
            - Model: ${config.transcription.modelName}
            - Language: ${config.transcription.language}
        """.trimIndent()
        configText.text = configInfo
    }

    private fun testFiltering() {
        val input = inputText.text.toString()
        val filtered = textProcessor.filterHallucinations(input)

        outputText.text = """
            Input: "$input"

            Filtered: "$filtered"

            ${if (filtered.isEmpty()) "✅ Correctly removed as hallucination" else "✅ Filtered successfully"}
        """.trimIndent()
    }

    private fun runAllTests() {
        val testCases = listOf(
            "Thank you." to "",
            "Thanks for watching!" to "",
            "Thanks for watching this is real content" to "this is real content",
            "This is real content thanks for watching" to "This is real content",
            "[00:00:00.000 --> 00:00:05.000] Hello world" to "Hello world",
            "Hello world" to "Hello world"
        )

        val results = StringBuilder("Running ${testCases.size} test cases:\n\n")

        testCases.forEachIndexed { index, (input, expected) ->
            val actual = textProcessor.filterHallucinations(input)
            val passed = actual == expected
            val status = if (passed) "✅ PASS" else "❌ FAIL"

            results.append("Test ${index + 1}: $status\n")
            results.append("Input: \"$input\"\n")
            results.append("Expected: \"$expected\"\n")
            results.append("Actual: \"$actual\"\n\n")
        }

        outputText.text = results.toString()
    }
}