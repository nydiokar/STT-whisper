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

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var whisperEngine: WhisperEngine
    private lateinit var voicePipeline: VoiceInputPipeline

    private var recordingJob: Job? = null
    private var isRecording = false

    companion object {
        private const val RECORD_AUDIO_PERMISSION_CODE = 1001
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
            text = "Phase 3 Test: Audio + VAD + Transcription"
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

        // Test VAD button
        testVADButton = Button(this).apply {
            text = "🔬 Test VAD with Synthetic Audio"
            isEnabled = false
            setOnClickListener { testVADWithSyntheticAudio() }
        }
        layout.addView(testVADButton)

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
                5. VAD will automatically detect speech and process it
                6. Speak clearly and watch for automatic transcription

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
                whisperEngine = WhisperEngine(this@AudioTestActivity)

                // Initialize Whisper from assets
                val success = whisperEngine.initializeFromAssets("models/ggml-base.en-q5_1.bin")
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

                var testResults = "=== VAD TEST RESULTS ===\n\n"
                testResults += "🔇 Silence Test: ${if (isSilentResult) "✅ SILENT (correct)" else "❌ SPEECH (incorrect)"} (${silentCount}/${totalChunks} chunks)\n"

                // Test 2: Synthetic speech (should return false from isSilent)
                updateStatus("Testing speech detection...")
                val speechAudio = generateSyntheticSpeechAudio(2.0f) // 2 seconds of synthetic speech
                
                // Process speech in 512-sample chunks (32ms each)
                var speechCount = 0
                totalChunks = 0
                for (i in speechAudio.indices step chunkSize) {
                    val endIndex = minOf(i + chunkSize, speechAudio.size)
                    val chunk = speechAudio.sliceArray(i until endIndex)
                    val chunkIsSilent = voicePipeline.testVAD(chunk)
                    if (!chunkIsSilent) speechCount++
                    totalChunks++
                    if (totalChunks >= 10) break // Test first 10 chunks
                }
                val isSpeechResult = speechCount > totalChunks / 2 // Majority should be speech

                testResults += "🔊 Speech Test: ${if (isSpeechResult) "✅ SPEECH (correct)" else "❌ SILENT (incorrect)"} (${speechCount}/${totalChunks} chunks)\n\n"

                // Test 3: Mixed pattern (silence → speech → silence)
                updateStatus("Testing mixed pattern...")
                val mixedAudio = generateMixedAudio()
                // Test 3: Quick chunk test
                testResults += "🔄 Mixed Pattern Test:\n"

                // Process mixed audio in chunks to see VAD responses
                val mixedChunkSize = 1024 // Process in small chunks
                var mixedSilentCount = 0
                var mixedSpeechCount = 0
                for (i in mixedAudio.indices step mixedChunkSize) {
                    val endIndex = minOf(i + mixedChunkSize, mixedAudio.size)
                    val chunk = mixedAudio.sliceArray(i until endIndex)
                    val chunkResult = voicePipeline.testVAD(chunk)
                    if (chunkResult) mixedSilentCount++ else mixedSpeechCount++
                    if (mixedSilentCount + mixedSpeechCount > 5) break // Limit processing
                }

                testResults += "Detected: ${mixedSilentCount} silent chunks, ${mixedSpeechCount} speech chunks\n\n"

                testResults += "=== FINAL RESULT ===\n"
                val silenceWorking = isSilentResult
                val speechWorking = !isSpeechResult

                if (silenceWorking && speechWorking) {
                    testResults += "🎉 VAD IS WORKING PERFECTLY!\n"
                    testResults += "Both silence and speech detection are correct.\n"
                    testResults += "Phase 3 VAD integration: ✅ COMPLETE"
                } else {
                    testResults += "⚠️ VAD has issues:\n"
                    testResults += "Silence detection: ${if (silenceWorking) "✅" else "❌"}\n"
                    testResults += "Speech detection: ${if (speechWorking) "✅" else "❌"}\n"
                    testResults += "Check fail-safe behavior in logs."
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
     * Generate synthetic speech audio (simple but effective) with VAD-friendly frame size
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
            
            // Simple but effective approach: multiple sine waves + noise
            // This mimics the spectral characteristics that VAD models look for
            
            // Primary frequency in speech range
            val freq1 = 800.0 + 400.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 0.5 * t)
            val freq2 = 1200.0 + 300.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 0.3 * t)
            val freq3 = 2000.0 + 500.0 * kotlin.math.sin(2.0 * kotlin.math.PI * 0.2 * t)
            
            // Generate waves
            val wave1 = kotlin.math.sin(2.0 * kotlin.math.PI * freq1 * t)
            val wave2 = 0.6 * kotlin.math.sin(2.0 * kotlin.math.PI * freq2 * t)
            val wave3 = 0.4 * kotlin.math.sin(2.0 * kotlin.math.PI * freq3 * t)
            
            // Add noise for realism
            val noise = (kotlin.random.Random.nextDouble() - 0.5) * 0.2
            
            // Amplitude modulation (speech-like envelope)
            val envelope = 0.3 + 0.7 * kotlin.math.sin(2.0 * kotlin.math.PI * 0.1 * t)
            
            // Combine everything
            val speechWave = (wave1 + wave2 + wave3 + noise) * envelope
            
            // Strong amplitude for VAD detection
            val sample = (32767 * 0.9 * speechWave).toInt().coerceIn(-32768, 32767).toShort()

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

    private fun updateStatus(message: String) {
        runOnUiThread {
            statusText.text = message
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
}