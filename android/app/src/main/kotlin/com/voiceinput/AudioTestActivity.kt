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
            text = "Phase 2 Test: Audio + Transcription"
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
            text = "🔗 Test Full Pipeline"
            isEnabled = false
            setOnClickListener { testFullPipeline() }
        }
        layout.addView(testPipelineButton)

        // Transcription output
        layout.addView(TextView(this).apply {
            text = "Transcription Output:"
            setPadding(0, 24, 0, 8)
        })

        transcriptionText = TextView(this).apply {
            text = ""
            minHeight = 120
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFFE8F5E9.toInt())
        }
        layout.addView(transcriptionText)

        // Instructions
        layout.addView(TextView(this).apply {
            text = """
                Instructions:
                1. Ensure microphone permissions are granted
                2. Click 'Start Recording' and speak clearly
                3. Recording will stop after 5 seconds
                4. Transcription will appear below

                If this works, Phase 2 is complete!
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

                // Initialize pipeline
                val config = ConfigRepository(this@AudioTestActivity).load()
                voicePipeline = VoiceInputPipeline(
                    audioRecorder = audioRecorder,
                    whisperEngine = whisperEngine,
                    config = config,
                    onResult = { result ->
                        runOnUiThread {
                            transcriptionText.text = """
                                Raw Result: "${result.text}"
                                Confidence: ${result.confidence}
                                Processing Time: ${result.processingTimeMs}ms
                            """.trimIndent()
                        }
                    }
                )
                updateStatus("✅ VoiceInputPipeline initialized")

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
                updateStatus("🔗 Testing full VoiceInputPipeline...")
                transcriptionText.text = "Testing full pipeline...\n(This should start recording automatically and process via the pipeline)"

                // Start the voice pipeline
                voicePipeline.startListening()

                // Let it run for a few seconds
                kotlinx.coroutines.delay(7000)

                // Stop the pipeline
                voicePipeline.stopListening()

                updateStatus("✅ Pipeline test complete")

            } catch (e: Exception) {
                transcriptionText.text = "❌ Pipeline test failed: ${e.message}"
                updateStatus("❌ Pipeline test failed: ${e.message}")
                e.printStackTrace()
            }
        }
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