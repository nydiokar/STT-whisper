package com.voiceinput

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.voiceinput.config.AppConfig
import com.voiceinput.config.ConfigRepository
import com.voiceinput.core.*
import kotlinx.coroutines.*
import java.io.InputStream
import kotlin.math.sin

/**
 * Automated test for streaming transcription performance
 * Tests real-time streaming behavior without requiring manual speech input
 */
class StreamingPerformanceTest(private val context: Context) {

    companion object {
        private const val TAG = "StreamingPerfTest"
        private const val TEST_DURATION_SEC = 15 // 15 second test
        private const val SAMPLE_RATE = 16000
        private const val CHANNELS = 1
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private val testMetrics = StreamingTestMetrics()
    private var whisperEngine: WhisperEngine? = null
    private var voicePipeline: VoiceInputPipeline? = null

    /**
     * Run comprehensive streaming performance test
     */
    suspend fun runStreamingTest(): StreamingTestResults = withContext(Dispatchers.Default) {
        Log.i(TAG, "🚀 Starting automated streaming performance test...")

        try {
            setupTestEnvironment()

            val testStartTime = System.currentTimeMillis()

            // Test 1: Load JFK audio file if available
            val jfkResults = testWithJFKAudio()

            // Test 2: Generate synthetic speech-like audio
            val syntheticResults = testWithSyntheticAudio()

            val totalTestTime = System.currentTimeMillis() - testStartTime

            Log.i(TAG, "✅ Streaming performance test completed in ${totalTestTime}ms")

            return@withContext StreamingTestResults(
                jfkAudioTest = jfkResults,
                syntheticAudioTest = syntheticResults,
                totalTestDurationMs = totalTestTime,
                testMetrics = testMetrics.copy()
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Streaming test failed", e)
            throw e
        } finally {
            cleanup()
        }
    }

    /**
     * Set up test environment with whisper engine and pipeline
     */
    private suspend fun setupTestEnvironment() {
        Log.i(TAG, "Setting up test environment...")

        // Initialize WhisperEngine (ONNX Runtime)
        whisperEngine = WhisperEngine(context)
        val initSuccess = whisperEngine!!.initialize()

        if (!initSuccess) {
            throw IllegalStateException("Failed to initialize Whisper engine for test")
        }

        // Get app config
        val configRepository = ConfigRepository(context)
        val config = configRepository.load()

        // Initialize audio recorder (for pipeline setup)
        val audioRecorder = AudioRecorder()

        // Set up pipeline with test callbacks
        voicePipeline = VoiceInputPipeline(
            context = context,
            audioRecorder = audioRecorder,
            whisperEngine = whisperEngine!!,
            config = config,
            onResult = { result ->
                handleStreamingResult(result)
            }
        )

        // Set up streaming callback
        voicePipeline!!.setTranscriptionUpdateCallback { text ->
            handleStreamingUpdate(text)
        }

        Log.i(TAG, "✅ Test environment ready")
    }

    /**
     * Test with JFK audio file if available
     */
    private suspend fun testWithJFKAudio(): AudioTestResult? {
        return try {
            Log.i(TAG, "🎯 Testing with JFK audio file...")

            val audioData = loadJFKAudio()
            if (audioData != null) {
                testStreamingWithAudio("JFK Audio", audioData)
            } else {
                Log.w(TAG, "JFK audio not available, skipping")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "JFK audio test failed", e)
            null
        }
    }

    /**
     * Test with synthetic speech-like audio
     */
    private suspend fun testWithSyntheticAudio(): AudioTestResult {
        Log.i(TAG, "🎯 Testing with synthetic speech audio...")

        val syntheticAudio = generateSpeechLikeAudio(TEST_DURATION_SEC)
        return testStreamingWithAudio("Synthetic Speech", syntheticAudio)
    }

    /**
     * Core streaming test with provided audio data
     * This now uses the FULL PIPELINE with JFK audio instead of microphone
     */
    private suspend fun testStreamingWithAudio(testName: String, audioData: ByteArray): AudioTestResult {
        val testResult = AudioTestResult(testName)
        testResult.startTime = System.currentTimeMillis()
        testResult.audioSizeBytes = audioData.size
        testResult.audioDurationSec = audioData.size / (SAMPLE_RATE * 2).toFloat()

        Log.i(TAG, "📊 Starting $testName test - ${testResult.audioDurationSec}s audio (${audioData.size} bytes)")

        try {
            // Reset metrics for this test
            testMetrics.reset()

            // Start the regular pipeline
            voicePipeline!!.startListening()

            // Feed JFK audio through the pipeline's AudioProcessor directly
            feedAudioThroughPipeline(audioData)

            // Stop pipeline and get final text
            val finalText = voicePipeline!!.stopListening()
            testResult.finalText = finalText
            testResult.finalTextLength = finalText.length

            testResult.endTime = System.currentTimeMillis()
            testResult.totalProcessingTime = testResult.endTime - testResult.startTime

            // Copy current metrics
            testResult.streamingChunks = testMetrics.streamingChunks.toList()
            testResult.chunkCount = testMetrics.chunkCount
            testResult.firstChunkDelayMs = testMetrics.firstChunkTime?.let { it - testResult.startTime }

            // Calculate comprehensive performance metrics
            logComprehensiveResults(testResult)

        } catch (e: Exception) {
            Log.e(TAG, "Error in streaming test: $testName", e)
            testResult.error = e.message
        }

        return testResult
    }

    /**
     * Feed audio data through the pipeline's AudioProcessor
     * This uses the full pipeline flow: AudioProcessor → VAD → Whisper → TextProcessor
     */
    private suspend fun feedAudioThroughPipeline(audioData: ByteArray) {
        val chunkSize = 1024 // Match AudioRecorder chunk size to avoid VAD tensor corruption
        val chunks = audioData.toList().chunked(chunkSize)

        Log.i(TAG, "📤 Feeding ${chunks.size} audio chunks through full pipeline...")
        Log.i(TAG, "📊 Using 1024-byte chunks to match microphone (AudioRecorder) chunk size")

        for ((index, chunk) in chunks.withIndex()) {
            val chunkBytes = chunk.toByteArray()

            // Simulate real-time by adding small delay
            delay(100) // 100ms delay to simulate real-time audio

            // Feed chunk directly to AudioProcessor (which handles VAD and transcription)
            // This goes through the full pipeline: AudioProcessor → VAD → Whisper → TextProcessor
            voicePipeline?.let { pipeline ->
                // Get the AudioProcessor and feed audio directly
                val audioProcessor = pipeline.getAudioProcessor()
                audioProcessor.addAudio(chunkBytes)
            }

            if (index % 10 == 0) {
                Log.d(TAG, "Fed chunk ${index + 1}/${chunks.size} through pipeline")
            }
        }

        Log.i(TAG, "✅ Finished feeding audio chunks through pipeline")
    }

    /**
     * Feed audio data in realistic chunks to simulate real-time streaming
     * This method is now DEPRECATED - we use the full pipeline instead
     */
    private suspend fun feedAudioInChunks(audioData: ByteArray, testResult: AudioTestResult) {
        val chunkSize = SAMPLE_RATE * 2 / 10 // 100ms chunks (3200 bytes)
        val chunks = audioData.toList().chunked(chunkSize)

        val expectedChunks = (audioData.size / chunkSize.toFloat()).toInt()
        Log.i(TAG, "📤 Feeding ${chunks.size} audio chunks (${chunkSize} bytes each) through VAD...")
        Log.i(TAG, "📊 Audio duration: ${testResult.audioDurationSec}s, Expected chunks: $expectedChunks")

        for ((index, chunk) in chunks.withIndex()) {
            val chunkBytes = chunk.toByteArray()

            // Simulate real-time by adding small delay
            delay(100) // 100ms delay to simulate real-time audio

            // Feed chunk through VAD using the same method as VAD SYSTEM TEST
            val isSilent = voicePipeline?.testVAD(chunkBytes) ?: true
            
            if (!isSilent) {
                Log.d(TAG, "🎤 Speech detected in chunk ${index + 1}")
                // Process through Whisper for transcription
                if (chunkBytes.size >= 1600) { // Minimum chunk size
                    val result = whisperEngine!!.transcribe(chunkBytes)
                    handleStreamingResult(result)
                }
            } else {
                Log.d(TAG, "VAD=Silence in chunk ${index + 1}")
            }

            if (index % 10 == 0) {
                Log.d(TAG, "Processed chunk ${index + 1}/${chunks.size} through VAD")
            }
        }

        Log.i(TAG, "✅ Finished feeding audio chunks through VAD")
    }

    /**
     * Handle streaming transcription results
     */
    private fun handleStreamingResult(result: TranscriptionResult) {
        val currentTime = System.currentTimeMillis()

        if (testMetrics.firstChunkTime == null) {
            testMetrics.firstChunkTime = currentTime
            Log.i(TAG, "🎯 First transcription chunk received!")
        }

        testMetrics.chunkCount++
        testMetrics.totalTranscriptionTime += result.processingTimeMs

        val chunkMetric = StreamingChunkMetric(
            chunkNumber = testMetrics.chunkCount,
            timestamp = currentTime,
            text = result.text,
            processingTimeMs = result.processingTimeMs,
            textLength = result.text.length
        )

        testMetrics.streamingChunks.add(chunkMetric)

        Log.i(TAG, "📝 Chunk #${testMetrics.chunkCount}: '${result.text.take(30)}...' (${result.processingTimeMs}ms)")
    }

    /**
     * Handle streaming text updates
     */
    private fun handleStreamingUpdate(text: String) {
        testMetrics.lastStreamingUpdate = System.currentTimeMillis()
        testMetrics.currentStreamingText = text

        Log.d(TAG, "🔄 Streaming update: ${text.length} chars")
    }

    /**
     * Generate realistic speech-like audio for testing
     */
    private fun generateSpeechLikeAudio(durationSec: Int): ByteArray {
        val sampleCount = SAMPLE_RATE * durationSec
        val audioData = ByteArray(sampleCount * 2) // 16-bit = 2 bytes per sample

        Log.i(TAG, "🎵 Generating ${durationSec}s of synthetic speech audio...")

        var index = 0
        for (i in 0 until sampleCount) {
            // Create speech-like audio with multiple frequencies and pauses
            val time = i.toFloat() / SAMPLE_RATE

            // Add speech segments with pauses
            val amplitude = if (time % 3.0f < 2.0f) { // 2s speech, 1s pause pattern
                // Simulate speech with fundamental frequency around 100-300Hz
                val fundamental = 150.0f + 50.0f * sin(time * 0.5f) // Varying pitch
                val harmonics = sin(2 * Math.PI * fundamental * time).toFloat() * 0.7f +
                               sin(2 * Math.PI * fundamental * 2 * time).toFloat() * 0.3f +
                               sin(2 * Math.PI * fundamental * 3 * time).toFloat() * 0.1f

                // Add noise to simulate consonants
                val noise = (Math.random() - 0.5).toFloat() * 0.1f

                (harmonics + noise) * 0.3f // Moderate volume
            } else {
                0.0f // Silence period
            }

            // Convert to 16-bit PCM
            val sample = (amplitude * 32767).toInt().coerceIn(-32768, 32767).toShort()

            audioData[index++] = (sample.toInt() and 0xFF).toByte()
            audioData[index++] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        Log.i(TAG, "✅ Generated ${audioData.size} bytes of test audio")
        return audioData
    }

    /**
     * Try to load JFK audio from assets for consistent testing
     */
    private fun loadJFKAudio(): ByteArray? {
        return try {
            Log.i(TAG, "🔍 Attempting to load JFK audio from assets...")
            // Try to load from assets if available
            val inputStream: InputStream = context.assets.open("jfk.wav")
            val audioData = inputStream.readBytes()
            inputStream.close()

            Log.i(TAG, "✅ Loaded JFK audio: ${audioData.size} bytes")
            audioData
        } catch (e: Exception) {
            Log.w(TAG, "❌ JFK audio not found in assets: ${e.message}")
            null
        }
    }

    /**
     * Log comprehensive performance results with UX insights
     */
    private fun logComprehensiveResults(result: AudioTestResult) {
        val performanceRatio = result.totalProcessingTime / (result.audioDurationSec * 1000f)
        val avgChunkTime = if (result.chunkCount > 0) testMetrics.totalTranscriptionTime / result.chunkCount else 0L
        val chunksPerSecond = if (result.totalProcessingTime > 0) (result.chunkCount * 1000f) / result.totalProcessingTime else 0f

        Log.i(TAG, """
            🎯 === STREAMING PERFORMANCE ANALYSIS ===

            📊 AUDIO PROCESSING:
            • Audio Duration: ${String.format("%.1f", result.audioDurationSec)}s (${result.audioSizeBytes} bytes)
            • Total Processing Time: ${result.totalProcessingTime}ms
            • Performance Ratio: ${String.format("%.2f", performanceRatio)}x real-time
            • ${if (performanceRatio <= 1.0f) "✅ REAL-TIME CAPABLE" else "⚠️ SLOWER THAN REAL-TIME"}

            🚀 STREAMING METRICS:
            • Total Chunks: ${result.chunkCount}
            • Chunks/Second: ${String.format("%.1f", chunksPerSecond)}
            • First Chunk Delay: ${result.firstChunkDelayMs ?: "N/A"}ms
            • Average Chunk Time: ${avgChunkTime}ms
            • ${if (result.chunkCount > 10) "✅ CONTINUOUS STREAMING" else "⚠️ LIMITED STREAMING"}

            📝 OUTPUT QUALITY:
            • Final Text Length: ${result.finalTextLength} characters
            • Text Preview: "${result.finalText.take(100)}${if (result.finalText.length > 100) "..." else ""}"
            • ${if (result.finalTextLength > 0) "✅ TRANSCRIPTION SUCCESS" else "❌ NO OUTPUT"}

            🎯 UX ASSESSMENT:
            ${when {
                performanceRatio <= 0.5f -> "🟢 EXCELLENT: Ultra-fast, very responsive"
                performanceRatio <= 1.0f -> "🟢 GOOD: Real-time, responsive experience"
                performanceRatio <= 2.0f -> "🟡 ACCEPTABLE: Slight delay, still usable"
                performanceRatio <= 5.0f -> "🟠 POOR: Noticeable lag, frustrating"
                else -> "🔴 UNUSABLE: Too slow for real-time use"
            }}

            ${if (result.firstChunkDelayMs != null && result.firstChunkDelayMs!! < 500) "⚡ FAST STARTUP" else "🐌 SLOW STARTUP"}
            ${if (result.chunkCount > 0 && avgChunkTime < 100) "⚡ SMOOTH STREAMING" else "🔄 CHOPPY STREAMING"}

            === END ANALYSIS ===
        """.trimIndent())
    }

    /**
     * Log detailed test results
     */
    private fun logTestResults(result: AudioTestResult) {
        Log.i(TAG, """
            📊 === ${result.testName} Results ===
            🎵 Audio: ${result.audioDurationSec}s (${result.audioSizeBytes} bytes)
            ⏱️  Total Time: ${result.totalProcessingTime}ms
            📈 Performance Ratio: ${String.format("%.2f", result.totalProcessingTime / (result.audioDurationSec * 1000f))}x real-time
            🔄 Streaming Chunks: ${result.chunkCount}
            🎯 First Chunk Delay: ${result.firstChunkDelayMs ?: "N/A"}ms
            📝 Final Text: ${result.finalTextLength} chars - "${result.finalText.take(50)}..."
            ⚡ Avg Chunk Time: ${if (result.chunkCount > 0) testMetrics.totalTranscriptionTime / result.chunkCount else 0}ms
            ${if (result.error != null) "❌ Error: ${result.error}" else "✅ Success"}
        """.trimIndent())

        // Log streaming timeline
        if (result.streamingChunks.isNotEmpty()) {
            Log.i(TAG, "🕐 Streaming Timeline:")
            for (chunk in result.streamingChunks.take(5)) { // Show first 5 chunks
                val relativeTime = chunk.timestamp - result.startTime
                Log.i(TAG, "  [+${relativeTime}ms] Chunk ${chunk.chunkNumber}: '${chunk.text.take(20)}...' (${chunk.processingTimeMs}ms)")
            }
            if (result.streamingChunks.size > 5) {
                Log.i(TAG, "  ... and ${result.streamingChunks.size - 5} more chunks")
            }
        }
    }

    /**
     * Clean up test resources
     */
    private suspend fun cleanup() {
        voicePipeline?.release()
        whisperEngine?.release()
        Log.i(TAG, "🧹 Test cleanup completed")
    }
}

/**
 * Test metrics tracking
 */
data class StreamingTestMetrics(
    var chunkCount: Int = 0,
    var firstChunkTime: Long? = null,
    var lastStreamingUpdate: Long? = null,
    var currentStreamingText: String = "",
    var totalTranscriptionTime: Long = 0,
    val streamingChunks: MutableList<StreamingChunkMetric> = mutableListOf()
) {
    fun reset() {
        chunkCount = 0
        firstChunkTime = null
        lastStreamingUpdate = null
        currentStreamingText = ""
        totalTranscriptionTime = 0
        streamingChunks.clear()
    }

    fun copy(): StreamingTestMetrics {
        return StreamingTestMetrics(
            chunkCount = chunkCount,
            firstChunkTime = firstChunkTime,
            lastStreamingUpdate = lastStreamingUpdate,
            currentStreamingText = currentStreamingText,
            totalTranscriptionTime = totalTranscriptionTime,
            streamingChunks = streamingChunks.toMutableList()
        )
    }
}

/**
 * Individual streaming chunk metrics
 */
data class StreamingChunkMetric(
    val chunkNumber: Int,
    val timestamp: Long,
    val text: String,
    val processingTimeMs: Long,
    val textLength: Int
)

/**
 * Results for a single audio test
 */
data class AudioTestResult(
    val testName: String,
    var startTime: Long = 0,
    var endTime: Long = 0,
    var totalProcessingTime: Long = 0,
    var audioSizeBytes: Int = 0,
    var audioDurationSec: Float = 0f,
    var chunkCount: Int = 0,
    var firstChunkDelayMs: Long? = null,
    var finalText: String = "",
    var finalTextLength: Int = 0,
    var streamingChunks: List<StreamingChunkMetric> = emptyList(),
    var error: String? = null
) {
    fun getPerformanceRatio(): Float = totalProcessingTime / (audioDurationSec * 1000f)
    fun isRealTime(): Boolean = getPerformanceRatio() <= 1.0f
    fun getAverageChunkTime(): Long = if (chunkCount > 0) streamingChunks.sumOf { it.processingTimeMs } / chunkCount else 0
}

/**
 * Complete test results
 */
data class StreamingTestResults(
    val jfkAudioTest: AudioTestResult?,
    val syntheticAudioTest: AudioTestResult,
    val totalTestDurationMs: Long,
    val testMetrics: StreamingTestMetrics
) {
    fun getOverallPerformance(): String {
        val results = listOfNotNull(jfkAudioTest, syntheticAudioTest)
        val avgRatio = results.map { it.getPerformanceRatio() }.average()
        val isStreaming = results.all { it.chunkCount > 1 }

        return when {
            avgRatio <= 1.0f && isStreaming -> "✅ EXCELLENT - Real-time streaming"
            avgRatio <= 2.0f && isStreaming -> "🟡 GOOD - Near real-time streaming"
            avgRatio <= 5.0f && isStreaming -> "🟠 FAIR - Slow but streaming"
            isStreaming -> "🔴 POOR - Very slow streaming"
            else -> "❌ FAILED - No streaming detected"
        }
    }
}