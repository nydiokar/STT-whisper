package com.voiceinput

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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
    private lateinit var testStreamingButton: Button
    private lateinit var liveJFKButton: Button
    private lateinit var bareWhisperBenchButton: Button

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var whisperEngine: WhisperEngine
    private lateinit var voicePipeline: VoiceInputPipeline

    private var recordingJob: Job? = null
    private var isRecording = false
    private var isInitialized = false

    companion object {
        private const val RECORD_AUDIO_PERMISSION_CODE = 1001

        // Model configuration (ONNX Runtime - always uses SMALL model)
        // NOTE: Only Whisper SMALL ONNX model is available
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
            setOnClickListener { toggleProductionRecording() }
        }
        layout.addView(recordButton)

        // Test pipeline button
        testPipelineButton = Button(this).apply {
            text = "🧠 Test VAD Pipeline (Continuous)"
            isEnabled = false
            setOnClickListener { testFullPipeline() }
        }
        layout.addView(testPipelineButton)


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

        setContentView(layout)
    }

    private fun initializeComponents() {
        lifecycleScope.launch {
            try {
                updateStatus("Initializing components...")

                // Initialize audio recorder
                audioRecorder = AudioRecorder()
                updateStatus("✅ AudioRecorder initialized")

                // Initialize Whisper engine (ONNX Runtime)
                whisperEngine = WhisperEngine(this@AudioTestActivity)

                // Initialize Whisper ONNX model
                val success = whisperEngine.initialize()
                if (success) {
                    updateStatus("✅ WhisperEngine initialized with ONNX model")
                } else {
                    updateStatus("❌ WhisperEngine failed to load ONNX model")
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
                
                // Mark initialization as complete
                isInitialized = true
                
                // Re-enable buttons now that initialization is complete
                runOnUiThread {
                    enableButtons()
                }

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
        // Only enable pipeline-dependent buttons if initialization is complete
        if (isInitialized) {
            testPipelineButton.isEnabled = true
            testStreamingButton.isEnabled = true
            liveJFKButton.isEnabled = true
            bareWhisperBenchButton.isEnabled = true
            updateStatus("✅ Ready to test - Click a button to start")
        } else {
            testPipelineButton.isEnabled = false
            testStreamingButton.isEnabled = false
            liveJFKButton.isEnabled = false
            bareWhisperBenchButton.isEnabled = false
            updateStatus("⏳ Initializing components... Please wait")
        }
    }

    private fun toggleProductionRecording() {
        if (isRecording) {
            stopProductionRecording()
        } else {
            startProductionRecording()
        }
    }

    /**
     * Production-style recording that matches how VoiceInputPipeline actually works:
     * - NO delays or warm-up periods
     * - Immediate start and collection (like desktop version)
     * - Direct audioStream() collection (like VoiceInputPipeline.feedAudioToProcessor())
     */
    private fun startProductionRecording() {
        recordingJob = lifecycleScope.launch {
            try {
                isRecording = true
                recordButton.text = "🔴 Recording... (5s)"
                updateStatus("🎤 Recording audio...")
                transcriptionText.text = "Recording..."

                // Start recording immediately (like VoiceInputPipeline line 164)
                if (!audioRecorder.start()) {
                    updateStatus("❌ Failed to start recording")
                    isRecording = false
                    recordButton.text = "🎤 Start Recording (5 seconds)"
                    return@launch
                }

                // Collect audio for 5 seconds
                val audioData = mutableListOf<ByteArray>()
                val startTime = System.currentTimeMillis()

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
                        updateStatus("✅ Transcription complete")
                    } else {
                        transcriptionText.text = "❌ Empty transcription"
                        updateStatus("⚠️ No text detected")
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    transcriptionText.text = "❌ Error: ${e.message}"
                    updateStatus("❌ Recording failed")
                }
                Log.e("AudioTest", "Recording error", e)
            } finally {
                isRecording = false
                runOnUiThread {
                    recordButton.text = "🎤 Start Recording (5 seconds)"
                }
            }
        }
    }

    private fun stopProductionRecording() {
        recordingJob?.cancel()
        audioRecorder.stop()
        isRecording = false
        recordButton.text = "🎤 Start Recording (5 seconds)"
        updateStatus("⏹️ Recording stopped")
    }

    private fun testFullPipeline() {
        if (!isInitialized) {
            updateStatus("❌ Components not yet initialized. Please wait...")
            Toast.makeText(this, "Please wait for initialization to complete", Toast.LENGTH_SHORT).show()
            return
        }
        
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

    private fun updateStatus(message: String) {
        runOnUiThread {
            statusText.text = message
        }
    }

    /**
     * Test streaming performance with automated benchmarks
     */
    private fun testStreamingPerformance() {
        if (!isInitialized) {
            updateStatus("❌ Components not yet initialized. Please wait...")
            Toast.makeText(this, "Please wait for initialization to complete", Toast.LENGTH_SHORT).show()
            return
        }
        
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
        if (!isInitialized) {
            updateStatus("❌ Components not yet initialized. Please wait...")
            Toast.makeText(this, "Please wait for initialization to complete", Toast.LENGTH_SHORT).show()
            return
        }
        
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
     * This is THE test to determine if ONNX Whisper is configured correctly.
     * Tests ONLY: Audio → WhisperEngine → Result
     * NO: VAD, Streaming, Pipeline, or any other overhead
     *
     * Expected results for SMALL ONNX model on Samsung devices with APU:
     * - Real-time factor: 0.4x - 0.5x (faster than real-time)
     * - 11s JFK audio should process in ~4-5 seconds
     *
     * If slower than 1x real-time, something is wrong with:
     * - ONNX Runtime initialization
     * - NNAPI/APU acceleration
     * - Model files
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
                    This tests ONLY ONNX Whisper (no VAD, no streaming, no pipeline)

                    Testing with JFK audio file...
                """.trimIndent()

                val benchmark = BareWhisperBenchmark(this@AudioTestActivity)
                val result = benchmark.benchmarkWithJFKAudio()

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
                                ONNX Whisper with APU is configured correctly.

                                Already integrated with:
                                - VAD processing
                                - Streaming mode
                                - Full pipeline
                                """.trimIndent()
                            } else {
                                """
                                ✅ Performance is acceptable.
                                ONNX Whisper baseline is working.

                                VAD/streaming adds ~10-20% overhead.
                                """.trimIndent()
                            }}

                            ====================================
                            Check logcat for detailed timings from ONNX Whisper
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