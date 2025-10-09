package com.voiceinput

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.voiceinput.config.ConfigRepository
import com.voiceinput.core.AudioRecorder
import com.voiceinput.core.WhisperEngine
import com.voiceinput.core.VoiceInputPipeline
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * Phase 2 Test Activity: Audio Recording + Whisper Transcription
 *
 * Tests:
 * - AudioRecorder can capture audio from microphone
 * - WhisperEngine can load model and transcribe audio
 * - VoiceInputPipeline can orchestrate the full flow
 *
 * This proves Phase 2 is complete: Record voice → see text output
 */
class AudioTestActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var transcriptionText: TextView
    private lateinit var recordButton: Button
    private lateinit var testPipelineButton: Button
    private lateinit var testVADButton: Button
    private lateinit var testStreamingButton: Button
    private lateinit var liveJFKButton: Button
    private lateinit var bareWhisperBenchButton: Button

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var whisperEngine: WhisperEngine
    private lateinit var voicePipeline: VoiceInputPipeline

    private var recordingJob: Job? = null
    private var isRecording = false

    companion object {
        private const val RECORD_AUDIO_PERMISSION_CODE = 1001

        // ⚡ CENTRAL MODEL CONFIGURATION - Change this to switch models for ALL tests
        private const val WHISPER_MODEL = "tiny"  // Options: "tiny", "base"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
        initializeComponents()
        checkPermissions()
    }

    private fun setupUI() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        layout.addView(TextView(this).apply {
            text = "Voice Input Pipeline Test"
            textSize = 20f
            setPadding(0, 0, 0, 24)
        })

        // Status display
        statusText = TextView(this).apply {
            text = "Initializing..."
            textSize = 14f
            setPadding(16, 8, 16, 16)
            setBackgroundColor(0xFFEEEEEE.toInt())
        }
        layout.addView(statusText)

        // Record button
        recordButton = Button(this).apply {
            text = "🎤 Start Recording (5 seconds)"
            isEnabled = false
            setOnClickListener { toggleRecording() }
        }
        layout.addView(recordButton)

        // Test pipeline button
        testPipelineButton = Button(this).apply {
            text = "🧠 Test VAD Pipeline (Continuous)"
            isEnabled = false
            setOnClickListener { testFullPipeline() }
        }
        layout.addView(testPipelineButton)

        // Test VAD button (simplified)
        testVADButton = Button(this).apply {
            text = "🔬 VAD System Test"
            isEnabled = false
            setOnClickListener { testVADWithSyntheticAudio() }
        }
        layout.addView(testVADButton)

        // Streaming performance test button
        testStreamingButton = Button(this).apply {
            text = "⚡ Test Streaming Performance"
            isEnabled = false
            setOnClickListener { testStreamingPerformance() }
        }
        layout.addView(testStreamingButton)

        // Live JFK Demo button
        liveJFKButton = Button(this).apply {
            text = "🎬 Live JFK Demo"
            isEnabled = false
            setOnClickListener { testLiveJFKDemo() }
        }
        layout.addView(liveJFKButton)

        // BARE WHISPER BENCHMARK button (DEFINITIVE TEST)
        bareWhisperBenchButton = Button(this).apply {
            text = "🔬 BARE WHISPER BENCHMARK (Definitive)"
            isEnabled = false
            setOnClickListener { runBareWhisperBenchmark() }
            setBackgroundColor(0xFF4CAF50.toInt()) // Green to highlight it
        }
        layout.addView(bareWhisperBenchButton)

        // Transcription output
        layout.addView(TextView(this).apply {
            text = "Transcription Output:"
            setPadding(0, 24, 0, 8)
        })

        // Create scrollable transcription output
        val scrollView = android.widget.ScrollView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                400 // Fixed height so it doesn't take whole screen
            )
        }

        transcriptionText = TextView(this).apply {
            text = ""
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFFE8F5E9.toInt())
            textSize = 12f // Smaller text to fit more
        }

        scrollView.addView(transcriptionText)
        layout.addView(scrollView)

        // Instructions
        layout.addView(TextView(this).apply {
            text = """
                Instructions:
                1. Ensure microphone permissions are granted
                2. Manual test: Click 'Start Recording' for 5-second test
                3. VAD test: Click 'Test VAD Pipeline' for continuous detection
                4. Synthetic test: Click 'Test VAD with Synthetic Audio' (no speaking required)
                5. Performance test: Click 'Test Streaming Performance' for comprehensive analysis
                6. Live Demo: Click 'Live JFK Demo' to see real-time file transcription
                7. VAD will automatically detect speech and process it
                8. Speak clearly and watch for automatic transcription

                If VAD detects speech and transcribes correctly, Phase 3 is complete!
            """.trimIndent()
            textSize = 12f
            setPadding(0, 24, 0, 0)
            setTextColor(0xFF666666.toInt())
        })

        setContentView(layout)
    }

    private fun initializeComponents() {
        lifecycleScope.launch {
            try {
                updateStatus("Initializing components...")

                // Initialize audio recorder
                audioRecorder = AudioRecorder()
                updateStatus("✅ AudioRecorder initialized")

                // Initialize Whisper engine
                whisperEngine = WhisperEngine(this@AudioTestActivity, modelName = WHISPER_MODEL)

                // Initialize Whisper from assets - use centralized model config
                val modelPath = if (WHISPER_MODEL == "tiny") {
                    "models/ggml-tiny-q5_1.bin"
                } else {
                    "models/ggml-$WHISPER_MODEL.en-q5_1.bin"
                }
                val success = whisperEngine.initializeFromAssets(modelPath)
                if (success) {
                    updateStatus("✅ WhisperEngine initialized with model from assets")
                } else {
                    updateStatus("❌ WhisperEngine failed to load model from assets")
                }

                // Initialize pipeline with VAD (Phase 3)
                val config = ConfigRepository(this@AudioTestActivity).load()
                voicePipeline = VoiceInputPipeline(
                    context = this@AudioTestActivity,
                    audioRecorder = audioRecorder,
                    whisperEngine = whisperEngine,
                    config = config,
                    onResult = { result ->
                        runOnUiThread {
                            val currentTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                            val newText = """
                                [$currentTime] VAD Detected Speech:
                                "${result.text}"
                                Confidence: ${result.confidence}
                                Processing Time: ${result.processingTimeMs}ms

                                ${transcriptionText.text}
                            """.trimIndent()
                            transcriptionText.text = newText
                        }
                    }
                )
                updateStatus("✅ VoiceInputPipeline with VAD initialized")

            } catch (e: Exception) {
                updateStatus("❌ Initialization failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }


    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
        } else {
            enableButtons()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableButtons()
            } else {
                updateStatus("❌ Microphone permission required")
                Toast.makeText(this, "Microphone permission is required for testing", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enableButtons() {
        recordButton.isEnabled = true
        testPipelineButton.isEnabled = true
        testVADButton.isEnabled = true
        testStreamingButton.isEnabled = true
        liveJFKButton.isEnabled = true
        bareWhisperBenchButton.isEnabled = true
        updateStatus("✅ Ready to test - Click a button to start")
    }

    private fun toggleRecording() {
        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        recordingJob = lifecycleScope.launch {
            try {
                isRecording = true
                recordButton.text = "🔴 Recording... (${5} seconds)"
                updateStatus("🎤 Recording audio...")
                transcriptionText.text = "Recording in progress..."

                // Collect audio for 5 seconds
                val audioData = mutableListOf<ByteArray>()
                val startTime = System.currentTimeMillis()

                audioRecorder.start()

                audioRecorder.audioStream().collect { chunk ->
                    audioData.add(chunk)
                    val elapsed = System.currentTimeMillis() - startTime

                    val remaining = (5000 - elapsed) / 1000
                    if (remaining > 0) {
                        runOnUiThread {
                            recordButton.text = "🔴 Recording... (${remaining}s)"
                        }
                    }

                    if (elapsed >= 5000) {
                        audioRecorder.stop()
                        return@collect
                    }
                }

                // Process collected audio
                updateStatus("🎵 Processing audio...")

                // Combine all chunks
                val totalSize = audioData.sumOf { it.size }
                val combinedAudio = ByteArray(totalSize)
                var offset = 0
                audioData.forEach { chunk ->
                    chunk.copyInto(combinedAudio, offset)
                    offset += chunk.size
                }

                // Transcribe
                updateStatus("🧠 Transcribing with Whisper...")
                val result = whisperEngine.transcribe(combinedAudio)

                runOnUiThread {
                    if (result.text.isNotEmpty()) {
                        transcriptionText.text = """
                            ✅ Transcription successful!

                            Text: "${result.text}"
                            Confidence: ${result.confidence}
                            Processing Time: ${result.processingTimeMs}ms
                        """.trimIndent()
                        updateStatus("✅ Recording and transcription complete!")
                    } else {
                        transcriptionText.text = "❌ No transcription result (model may be missing or audio too quiet)"
                        updateStatus("⚠️ Transcription returned empty result")
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    transcriptionText.text = "❌ Error: ${e.message}"
                    updateStatus("❌ Recording failed: ${e.message}")
                }
                e.printStackTrace()
            } finally {
                isRecording = false
                runOnUiThread {
                    recordButton.text = "🎤 Start Recording (5 seconds)"
                }
            }
        }
    }

    private fun stopRecording() {
        recordingJob?.cancel()
        audioRecorder.stop()
        isRecording = false
        recordButton.text = "🎤 Start Recording (5 seconds)"
        updateStatus("⏹️ Recording stopped")
    }

    private fun testFullPipeline() {
        lifecycleScope.launch {
            try {
                updateStatus("🧠 Testing VAD Pipeline...")
                transcriptionText.text = "Starting VAD Pipeline...\n\nNow speak clearly. The system will:\n1. Detect when you start speaking (VAD)\n2. Buffer your speech\n3. Detect when you stop speaking\n4. Automatically transcribe the segment\n\nResults will appear below as they're processed..."

                // Disable button during test
                testPipelineButton.isEnabled = false
                testPipelineButton.text = "🟡 VAD Pipeline Running..."

                // Start the voice pipeline with VAD
                voicePipeline.startListening()

                updateStatus("✅ VAD Pipeline running - Speak now and watch for automatic detection!")

                // Let it run for longer to test continuous VAD
                kotlinx.coroutines.delay(15000) // 15 seconds

                // Stop the pipeline
                val finalText = voicePipeline.stopListening()

                updateStatus("✅ VAD Pipeline test complete")
                testPipelineButton.text = "🧠 Test VAD Pipeline (Continuous)"
                testPipelineButton.isEnabled = true

                // Show final accumulated text
                if (finalText.isNotEmpty()) {
                    runOnUiThread {
                        transcriptionText.text = """
                            ${transcriptionText.text}

                            ===== FINAL ACCUMULATED TEXT =====
                            "$finalText"
                        """.trimIndent()
                    }
                }

            } catch (e: Exception) {
                transcriptionText.text = "❌ VAD Pipeline test failed: ${e.message}"
                updateStatus("❌ VAD Pipeline test failed: ${e.message}")
                testPipelineButton.text = "🧠 Test VAD Pipeline (Continuous)"
                testPipelineButton.isEnabled = true
                e.printStackTrace()
            }
        }
    }

    private fun testVADWithSyntheticAudio() {
        lifecycleScope.launch {
            try {
                updateStatus("🔬 Testing VAD with synthetic audio...")
                transcriptionText.text = "Generating synthetic audio test patterns...\n\nThis will test VAD without requiring you to speak."

                // Disable button during test
                testVADButton.isEnabled = false
                testVADButton.text = "🟡 Testing VAD..."

                // Test 1: Silence (should return true from isSilent)
                updateStatus("Testing silence detection...")
                val silenceAudio = generateSilenceAudio(1.0f) // 1 second of silence
                
                // Process silence in 512-sample chunks (32ms each)
                val chunkSize = 1024 // 512 samples * 2 bytes per sample = 1024 bytes
                var silentCount = 0
                var totalChunks = 0
                for (i in silenceAudio.indices step chunkSize) {
                    val endIndex = minOf(i + chunkSize, silenceAudio.size)
                    val chunk = silenceAudio.sliceArray(i until endIndex)
                    val chunkIsSilent = voicePipeline.testVAD(chunk)
                    if (chunkIsSilent) silentCount++
                    totalChunks++
                    if (totalChunks >= 10) break // Test first 10 chunks
                }
                val isSilentResult = silentCount > totalChunks / 2 // Majority should be silent

                // Get current VAD threshold for diagnostic info
                val config = ConfigRepository(this@AudioTestActivity).load()
                val vadThreshold = config.audio.vadThreshold

                var testResults = "=== VAD SYSTEM TEST ===\n\n"
                testResults += "🔇 Silence Detection: ${if (isSilentResult) "✅ PASS" else "❌ FAIL"} (${silentCount}/${totalChunks} chunks)\n"

                // Test 2: Real speech using JFK audio sample
                updateStatus("Testing real speech detection...")
                val realSpeechAudio = loadJFKAudio()
                var realSpeechResult = false

                if (realSpeechAudio != null) {
                    // Process JFK audio in chunks
                    var realSpeechCount = 0
                    var realTotalChunks = 0
                    var realSpeechProbs = mutableListOf<String>()

                    for (i in realSpeechAudio.indices step chunkSize) {
                        val endIndex = minOf(i + chunkSize, realSpeechAudio.size)
                        val chunk = realSpeechAudio.sliceArray(i until endIndex)
                        val chunkIsSilent = voicePipeline.testVAD(chunk)
                        if (!chunkIsSilent) realSpeechCount++
                        realTotalChunks++

                        // Log first few probabilities for debugging
                        if (realTotalChunks <= 5) {
                            realSpeechProbs.add("Chunk $realTotalChunks: ${if (chunkIsSilent) "Silent" else "Speech"}")
                        }

                        if (realTotalChunks >= 20) break // Test first 20 chunks of real speech
                    }
                    realSpeechResult = realSpeechCount > realTotalChunks / 3 // At least 1/3 should be speech

                    testResults += "🎤 Real Speech Test (JFK): ${if (realSpeechResult) "✅ PASS" else "❌ FAIL"} (${realSpeechCount}/${realTotalChunks} chunks)\n\n"
                } else {
                    testResults += "❌ Could not load JFK audio sample from assets\n\n"
                }

                // Skip mixed test for cleaner output

                testResults += "=== FINAL RESULT ===\n"
                val silenceWorking = isSilentResult
                val realSpeechWorking = if (realSpeechAudio != null) realSpeechResult else false

                if (silenceWorking && realSpeechWorking) {
                    testResults += "🎉 VAD SYSTEM: FULLY OPERATIONAL!\n"
                    testResults += "✅ Ready for Phase 4 Integration\n"
                    testResults += "Threshold: $vadThreshold (optimal for production)"
                } else if (silenceWorking && !realSpeechWorking && realSpeechAudio != null) {
                    testResults += "⚠️ VAD Threshold may need adjustment\n"
                    testResults += "✅ Silence detection working\n"
                    testResults += "❌ Speech sensitivity low\n"
                    testResults += "Try threshold: 0.3 for more sensitivity"
                } else if (silenceWorking && realSpeechAudio == null) {
                    testResults += "✅ Partial Test Complete\n"
                    testResults += "Use 'Test VAD Pipeline' for full validation"
                } else {
                    testResults += "❌ VAD System Issues\n"
                    testResults += "Check model initialization and configuration"
                }

                runOnUiThread {
                    transcriptionText.text = testResults
                }

                updateStatus("✅ VAD synthetic audio test complete!")
                testVADButton.text = "🔬 Test VAD with Synthetic Audio"
                testVADButton.isEnabled = true

            } catch (e: Exception) {
                runOnUiThread {
                    transcriptionText.text = "❌ VAD synthetic test failed: ${e.message}\n\nStack trace:\n${e.stackTraceToString()}"
                    updateStatus("❌ VAD synthetic test failed: ${e.message}")
                }
                testVADButton.text = "🔬 Test VAD with Synthetic Audio"
                testVADButton.isEnabled = true
                e.printStackTrace()
            }
        }
    }

    /**
     * Generate silence audio (all zeros) with VAD-friendly frame size
     */
    private fun generateSilenceAudio(durationSeconds: Float): ByteArray {
        val sampleRate = 16000
        val bytesPerSample = 2

        // Calculate total samples needed for the duration
        val totalSamples = (durationSeconds * sampleRate).toInt()
        val totalBytes = totalSamples * bytesPerSample

        return ByteArray(totalBytes) // All zeros = silence
    }

    /**
     * Generate synthetic speech audio optimized for Silero VAD detection
     */
    private fun generateSyntheticSpeechAudio(durationSeconds: Float): ByteArray {
        val sampleRate = 16000
        val bytesPerSample = 2

        // Calculate total samples needed for the duration
        val totalSamples = (durationSeconds * sampleRate).toInt()
        val totalBytes = totalSamples * bytesPerSample

        val audioBytes = ByteArray(totalBytes)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate

            // Generate more realistic speech-like audio that Silero VAD will recognize
            // Based on typical human speech characteristics

            // Fundamental frequency (pitch) with natural variation
            val f0 = 120.0 + 30.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 0.8 * t) // 90-150 Hz pitch variation

            // Formant frequencies (typical for vowel sounds)
            val formant1 = 600.0 + 200.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 0.3 * t)  // F1: 400-800 Hz
            val formant2 = 1200.0 + 400.0 * kotlin.math.cos(2.0 * kotlin.math.PI * 0.4 * t) // F2: 800-1600 Hz
            val formant3 = 2400.0 + 300.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 0.2 * t) // F3: 2100-2700 Hz

            // Generate harmonic series (more realistic than pure sine waves)
            var harmonic = 0.0
            for (h in 1..8) { // First 8 harmonics
                val freq = f0 * h
                val amplitude = 1.0 / (h * h) // Natural harmonic decay
                harmonic += amplitude * kotlin.math.sin(2.0 * kotlin.math.PI * freq * t)
            }

            // Formant filtering (simplified resonance)
            val formantWave1 = 0.8 * kotlin.math.sin(2.0 * kotlin.math.PI * formant1 * t)
            val formantWave2 = 0.6 * kotlin.math.sin(2.0 * kotlin.math.PI * formant2 * t)
            val formantWave3 = 0.4 * kotlin.math.sin(2.0 * kotlin.math.PI * formant3 * t)

            // Add broadband noise (consonant-like energy)
            val broadbandNoise = generateGaussianNoise() * 0.3

            // Speech-like envelope with pauses (more realistic)
            val speechPattern = kotlin.math.max(0.0, kotlin.math.sin(2.0 * kotlin.math.PI * 1.5 * t))
            val envelope = 0.2 + 0.8 * speechPattern * speechPattern // Non-linear envelope

            // Combine all components
            val speechSignal = (harmonic * 0.4 + formantWave1 + formantWave2 + formantWave3 + broadbandNoise) * envelope

            // Higher amplitude and add spectral richness
            val sample = (speechSignal * 25000).coerceIn(-32768.0, 32767.0).toInt().toShort()

            // Convert to little-endian bytes
            val byteIndex = i * 2
            audioBytes[byteIndex] = (sample.toInt() and 0xFF).toByte()
            audioBytes[byteIndex + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        return audioBytes
    }

    /**
     * Generate mixed audio pattern: silence → speech → silence
     */
    private fun generateMixedAudio(): ByteArray {
        val silence1 = generateSilenceAudio(1.0f)
        val speech = generateSyntheticSpeechAudio(2.0f)
        val silence2 = generateSilenceAudio(1.0f)

        return silence1 + speech + silence2
    }

    /**
     * Load JFK audio sample from assets
     */
    private fun loadJFKAudio(): ByteArray? {
        return try {
            assets.open("jfk.wav").use { inputStream ->
                // Skip WAV header (44 bytes) to get raw PCM data
                val wavData = inputStream.readBytes()
                if (wavData.size > 44) {
                    wavData.sliceArray(44 until wavData.size) // Skip WAV header, return PCM data
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioTest", "Failed to load JFK audio: ${e.message}", e)
            null
        }
    }

    /**
     * Generate Gaussian noise using Box-Muller transform
     */
    private fun generateGaussianNoise(): Double {
        val u1 = kotlin.random.Random.nextDouble()
        val u2 = kotlin.random.Random.nextDouble()
        return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * kotlin.math.PI * u2)
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            statusText.text = message
        }
    }

    /**
     * Test streaming performance with automated benchmarks
     */
    private fun testStreamingPerformance() {
        lifecycleScope.launch {
            try {
                updateStatus("⚡ Running streaming performance test...")
                testStreamingButton.isEnabled = false

                val streamingTest = StreamingPerformanceTest(this@AudioTestActivity)
                val results = streamingTest.runStreamingTest()

                // Display results
                val output = buildString {
                    appendLine("🎯 === STREAMING PERFORMANCE TEST RESULTS ===")
                    appendLine()

                    appendLine("📊 Overall Performance: ${results.getOverallPerformance()}")
                    appendLine("⏱️  Total Test Time: ${results.totalTestDurationMs}ms")
                    appendLine()

                    // JFK Audio Test Results
                    results.jfkAudioTest?.let { jfk ->
                        appendLine("🎵 JFK Audio Test:")
                        appendLine("  Audio: ${jfk.audioDurationSec}s (${jfk.audioSizeBytes} bytes)")
                        appendLine("  Processing: ${jfk.totalProcessingTime}ms (${String.format("%.2f", jfk.getPerformanceRatio())}x real-time)")
                        appendLine("  Streaming: ${jfk.chunkCount} chunks")
                        appendLine("  First chunk: ${jfk.firstChunkDelayMs ?: "N/A"}ms delay")
                        appendLine("  Text: ${jfk.finalTextLength} chars - \"${jfk.finalText.take(50)}...\"")
                        appendLine("  Status: ${if (jfk.isRealTime()) "✅ Real-time" else "🟡 Slower than real-time"}")
                        if (jfk.error != null) {
                            appendLine("  Error: ${jfk.error}")
                        }
                        appendLine()
                    } ?: run {
                        appendLine("🎵 JFK Audio Test: Skipped (file not available)")
                        appendLine()
                    }

                    // Synthetic Audio Test Results
                    val synthetic = results.syntheticAudioTest
                    appendLine("🔊 Synthetic Audio Test:")
                    appendLine("  Audio: ${synthetic.audioDurationSec}s (${synthetic.audioSizeBytes} bytes)")
                    appendLine("  Processing: ${synthetic.totalProcessingTime}ms (${String.format("%.2f", synthetic.getPerformanceRatio())}x real-time)")
                    appendLine("  Streaming: ${synthetic.chunkCount} chunks")
                    appendLine("  First chunk: ${synthetic.firstChunkDelayMs ?: "N/A"}ms delay")
                    appendLine("  Avg chunk time: ${synthetic.getAverageChunkTime()}ms")
                    appendLine("  Text: ${synthetic.finalTextLength} chars - \"${synthetic.finalText.take(50)}...\"")
                    appendLine("  Status: ${if (synthetic.isRealTime()) "✅ Real-time" else "🟡 Slower than real-time"}")
                    if (synthetic.error != null) {
                        appendLine("  Error: ${synthetic.error}")
                    }
                    appendLine()

                    // Performance Analysis
                    appendLine("📈 Performance Analysis:")
                    val avgRatio = listOfNotNull(results.jfkAudioTest, results.syntheticAudioTest)
                        .map { it.getPerformanceRatio() }.average()

                    when {
                        avgRatio <= 1.0f -> appendLine("  🟢 EXCELLENT: Real-time transcription achieved!")
                        avgRatio <= 2.0f -> appendLine("  🟡 GOOD: Near real-time performance")
                        avgRatio <= 5.0f -> appendLine("  🟠 FAIR: Usable but slow")
                        else -> appendLine("  🔴 POOR: Too slow for real-time use")
                    }

                    appendLine("  Average performance ratio: ${String.format("%.2f", avgRatio)}x real-time")

                    val totalChunks = listOfNotNull(results.jfkAudioTest, results.syntheticAudioTest)
                        .sumOf { it.chunkCount }

                    if (totalChunks > 1) {
                        appendLine("  ✅ Streaming confirmed: ${totalChunks} total chunks processed")
                    } else {
                        appendLine("  ❌ No streaming detected - processing in single chunks")
                    }
                }

                transcriptionText.text = output
                updateStatus("✅ Streaming performance test completed - See results below")

            } catch (e: Exception) {
                transcriptionText.text = "❌ Streaming test failed: ${e.message}\n\nError details: ${e.stackTraceToString()}"
                updateStatus("❌ Streaming test failed: ${e.message}")
            } finally {
                testStreamingButton.isEnabled = true
            }
        }
    }

    /**
     * Test Live JFK Demo - Stream JFK audio through pipeline with real-time transcription
     */
    private fun testLiveJFKDemo() {
        lifecycleScope.launch {
            try {
                updateStatus("🎬 Starting Live JFK Demo...")
                liveJFKButton.isEnabled = false
                transcriptionText.text = "🎬 Loading JFK audio file...\n\n"

                // Load JFK audio from assets
                val jfkAudioData = loadJFKAudioFromAssets()
                if (jfkAudioData == null) {
                    transcriptionText.text = "❌ JFK audio file not found in assets\n\nPlease ensure 'jfk.wav' is in the assets folder"
                    updateStatus("❌ JFK audio file not found")
                    return@launch
                }

                val audioDurationSec = jfkAudioData.size / (16000 * 2).toFloat()
                transcriptionText.text = "🎬 JFK Audio Loaded: ${audioDurationSec}s\n\nStarting real-time transcription...\n\n"

                // Clear any previous text
                voicePipeline.clearText()

                    // Feed JFK audio through the pipeline at maximum speed
                    voicePipeline.feedFileAudio(
                        audioData = jfkAudioData,
                        chunkSizeBytes = 8000, // 0.25 seconds at 16kHz (mobile-optimized)
                        delayMs = 0L // PERFORMANCE: No delay for maximum speed
                    )

                // Get final accumulated text
                val finalText = voicePipeline.getText()
                
                transcriptionText.text = """
                    🎬 Live JFK Demo Complete!
                    
                    📊 Results:
                    • Audio Duration: ${audioDurationSec}s
                    • Final Text Length: ${finalText.length} characters
                    • Processing: Real-time streaming through VAD pipeline
                    
                    📝 Transcribed Text:
                    "$finalText"
                    
                    ✅ This demonstrates the pipeline's ability to process file audio
                    with the same real-time streaming capabilities as microphone input!
                """.trimIndent()

                updateStatus("✅ Live JFK Demo completed - See transcription below")

            } catch (e: Exception) {
                transcriptionText.text = "❌ Live JFK Demo failed: ${e.message}\n\nError details: ${e.stackTraceToString()}"
                updateStatus("❌ Live JFK Demo failed: ${e.message}")
            } finally {
                liveJFKButton.isEnabled = true
            }
        }
    }

    /**
     * Load JFK audio file from assets
     */
    private fun loadJFKAudioFromAssets(): ByteArray? {
        return try {
            val inputStream = assets.open("jfk.wav")
            val audioData = inputStream.readBytes()
            inputStream.close()
            audioData
        } catch (e: Exception) {
            android.util.Log.e("AudioTestActivity", "Failed to load JFK audio: ${e.message}")
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recordingJob?.cancel()
        if (isRecording) {
            audioRecorder.stop()
        }
        if (::voicePipeline.isInitialized) {
            lifecycleScope.launch {
                voicePipeline.stopListening()
            }
        }
        if (::whisperEngine.isInitialized) {
            lifecycleScope.launch {
                whisperEngine.release()
            }
        }
    }

    /**
     * 🔬 DEFINITIVE BARE WHISPER BENCHMARK
     *
     * This is THE test to determine if whisper.cpp is configured correctly.
     * Tests ONLY: Audio → WhisperEngine → Result
     * NO: VAD, Streaming, Pipeline, or any other overhead
     *
     * Expected results for base model on modern phone (6-8 cores):
     * - Real-time factor: 0.5x - 1.0x (faster than or equal to real-time)
     * - 11s JFK audio should process in ~5-11 seconds
     *
     * If slower than 2x real-time, something is wrong with:
     * - Thread count
     * - JNI parameters
     * - Model file
     * - Device performance
     */
    private fun runBareWhisperBenchmark() {
        lifecycleScope.launch {
            try {
                updateStatus("🔬 Running BARE WHISPER benchmark...")
                bareWhisperBenchButton.isEnabled = false
                transcriptionText.text = """
                    🔬 BARE WHISPER BENCHMARK
                    ========================

                    Running definitive performance test...
                    This tests ONLY whisper.cpp (no VAD, no streaming, no pipeline)

                    Testing with JFK audio file...
                """.trimIndent()

                val benchmark = BareWhisperBenchmark(this@AudioTestActivity)
                val result = benchmark.benchmarkWithJFKAudio(WHISPER_MODEL)

                runOnUiThread {
                    if (result.success) {
                        val verdict = when {
                            result.realTimeFactor < 0.5f -> "🚀 EXCELLENT - Much faster than real-time!"
                            result.realTimeFactor < 1.0f -> "⚡ GOOD - Faster than real-time"
                            result.realTimeFactor < 1.5f -> "✅ ACCEPTABLE - Near real-time"
                            result.realTimeFactor < 2.0f -> "⚠️ SLOW - Slower than real-time"
                            else -> "🐌 VERY SLOW - Performance issue detected"
                        }

                        transcriptionText.text = """
                            🔬 BARE WHISPER BENCHMARK RESULTS
                            ====================================

                            Model: ${result.modelName}
                            Audio Length: ${String.format("%.1f", result.audioLengthSec)}s
                            Processing Time: ${result.transcriptionTimeMs}ms (${String.format("%.1f", result.transcriptionTimeMs / 1000.0)}s)

                            ⚡ REAL-TIME FACTOR: ${String.format("%.2f", result.realTimeFactor)}x

                            VERDICT: $verdict

                            Threads Used: ${result.threadCount}
                            CPU Cores Available: ${Runtime.getRuntime().availableProcessors()}

                            Transcription:
                            "${result.transcribedText}"

                            ====================================

                            ANALYSIS:
                            ${if (result.realTimeFactor > 1.5f) {
                                """
                                ⚠️ Performance is slower than expected!

                                Possible causes:
                                1. Check logcat for actual thread count used
                                2. Verify model file is quantized (q5_1)
                                3. Check device CPU performance
                                4. Ensure JNI optimizations are compiled
                                """.trimIndent()
                            } else if (result.realTimeFactor < 1.0f) {
                                """
                                ✅ Excellent performance!
                                whisper.cpp is configured correctly.

                                Now you can safely add:
                                - VAD processing
                                - Streaming mode
                                - Full pipeline
                                """.trimIndent()
                            } else {
                                """
                                ✅ Performance is acceptable.
                                whisper.cpp baseline is working.

                                Adding VAD/streaming will add ~10-20% overhead.
                                """.trimIndent()
                            }}

                            ====================================
                            Check logcat for detailed timings from whisper.cpp
                        """.trimIndent()
                        updateStatus("✅ Benchmark complete - See results above")
                    } else {
                        transcriptionText.text = """
                            ❌ BENCHMARK FAILED

                            Error: ${result.error}

                            Possible causes:
                            - Model file not found in assets/models/
                            - JNI compilation issue
                            - Memory constraints
                        """.trimIndent()
                        updateStatus("❌ Benchmark failed - See error above")
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    transcriptionText.text = "❌ Benchmark error: ${e.message}\n\n${e.stackTraceToString()}"
                    updateStatus("❌ Benchmark error")
                }
            } finally {
                runOnUiThread {
                    bareWhisperBenchButton.isEnabled = true
                }
            }
        }
    }
}