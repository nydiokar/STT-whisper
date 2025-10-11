package com.voiceinput

import android.content.Context
import android.util.Log
import com.voiceinput.core.WhisperEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.sin

/**
 * DEFINITIVE BENCHMARK TEST
 *
 * Tests ONLY whisper.cpp performance with ZERO overhead:
 * - No VAD
 * - No streaming
 * - No pipeline
 * - No audio processing
 *
 * Just: Raw audio → WhisperEngine → Result
 *
 * This gives us the TRUE performance baseline for whisper.cpp on this device.
 */
class BareWhisperBenchmark(private val context: Context) {

    companion object {
        private const val TAG = "BareWhisperBench"
        private const val SAMPLE_RATE = 16000
    }

    data class BenchmarkResult(
        val modelName: String,
        val audioLengthSec: Float,
        val threadCount: Int,
        val transcriptionTimeMs: Long,
        val realTimeFactor: Float,  // <1.0 = faster than real-time
        val transcribedText: String,
        val success: Boolean,
        val error: String? = null
    )

    /**
     * Run definitive benchmark with pre-recorded JFK audio
     */
    suspend fun benchmarkWithJFKAudio(): BenchmarkResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "========================================")
        Log.i(TAG, "🔬 BARE WHISPER BENCHMARK - JFK Audio")
        Log.i(TAG, "   Model: Whisper SMALL INT8")
        Log.i(TAG, "========================================")

        val whisperEngine = WhisperEngine(context)

        try {
            // Initialize ONNX model
            Log.i(TAG, "📦 Initializing Whisper SMALL model...")
            val modelLoaded = whisperEngine.initialize()
            if (!modelLoaded) {
                return@withContext BenchmarkResult(
                    modelName = "small",
                    audioLengthSec = 0f,
                    threadCount = 0,
                    transcriptionTimeMs = 0,
                    realTimeFactor = 0f,
                    transcribedText = "",
                    success = false,
                    error = "Failed to initialize model"
                )
            }

            // Load JFK audio
            Log.i(TAG, "🎵 Loading JFK audio file...")
            val audioData = loadJFKAudio()
            if (audioData == null) {
                return@withContext BenchmarkResult(
                    modelName = "small",
                    audioLengthSec = 0f,
                    threadCount = 0,
                    transcriptionTimeMs = 0,
                    realTimeFactor = 0f,
                    transcribedText = "",
                    success = false,
                    error = "JFK audio not found in assets"
                )
            }

            val audioLengthSec = audioData.size / (SAMPLE_RATE * 2).toFloat()
            val threadCount = Runtime.getRuntime().availableProcessors()

            Log.i(TAG, "")
            Log.i(TAG, "📊 BENCHMARK PARAMETERS:")
            Log.i(TAG, "   Model: Whisper SMALL (ONNX INT8)")
            Log.i(TAG, "   Audio: ${audioLengthSec}s (${audioData.size} bytes)")
            Log.i(TAG, "   Threads: $threadCount (${Runtime.getRuntime().availableProcessors()} cores available)")
            Log.i(TAG, "   Test: Direct WhisperEngine.transcribe() with APU acceleration")
            Log.i(TAG, "")

            // THE BENCHMARK: Pure whisper.cpp call
            Log.i(TAG, "⚡ Starting transcription...")
            val startTime = System.currentTimeMillis()

            val result = whisperEngine.transcribe(audioData)

            val elapsedMs = System.currentTimeMillis() - startTime
            val realTimeFactor = elapsedMs / (audioLengthSec * 1000)

            Log.i(TAG, "")
            Log.i(TAG, "========================================")
            Log.i(TAG, "✅ BENCHMARK RESULTS:")
            Log.i(TAG, "========================================")
            Log.i(TAG, "Audio Length:      ${audioLengthSec}s")
            Log.i(TAG, "Processing Time:   ${elapsedMs}ms (${elapsedMs / 1000.0}s)")
            Log.i(TAG, "Real-Time Factor:  ${String.format("%.2f", realTimeFactor)}x")
            Log.i(TAG, "Speed:             ${if (realTimeFactor < 1.0) "⚡ FASTER than real-time" else "🐌 SLOWER than real-time"}")
            Log.i(TAG, "Transcription:     \"${result.text}\"")
            Log.i(TAG, "========================================")

            // Cleanup
            whisperEngine.release()

            return@withContext BenchmarkResult(
                modelName = "small",
                audioLengthSec = audioLengthSec,
                threadCount = threadCount,
                transcriptionTimeMs = elapsedMs,
                realTimeFactor = realTimeFactor,
                transcribedText = result.text,
                success = true
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Benchmark failed", e)
            whisperEngine.release()
            return@withContext BenchmarkResult(
                modelName = "small",
                audioLengthSec = 0f,
                threadCount = 0,
                transcriptionTimeMs = 0,
                realTimeFactor = 0f,
                transcribedText = "",
                success = false,
                error = e.message
            )
        }
    }

    /**
     * Run benchmark with synthetic audio (for testing without JFK file)
     */
    suspend fun benchmarkWithSyntheticAudio(
        durationSec: Int = 5
    ): BenchmarkResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "========================================")
        Log.i(TAG, "🔬 BARE WHISPER BENCHMARK - Synthetic Audio")
        Log.i(TAG, "   Model: Whisper SMALL INT8")
        Log.i(TAG, "========================================")

        val whisperEngine = WhisperEngine(context)

        try {
            // Initialize ONNX model
            Log.i(TAG, "📦 Initializing Whisper SMALL model...")
            val modelLoaded = whisperEngine.initialize()
            if (!modelLoaded) {
                return@withContext BenchmarkResult(
                    modelName = "small",
                    audioLengthSec = 0f,
                    threadCount = 0,
                    transcriptionTimeMs = 0,
                    realTimeFactor = 0f,
                    transcribedText = "",
                    success = false,
                    error = "Failed to initialize model"
                )
            }

            // Generate synthetic audio
            Log.i(TAG, "🎵 Generating ${durationSec}s synthetic audio...")
            val audioData = generateSyntheticAudio(durationSec)
            val audioLengthSec = audioData.size / (SAMPLE_RATE * 2).toFloat()
            val threadCount = Runtime.getRuntime().availableProcessors()

            Log.i(TAG, "")
            Log.i(TAG, "📊 BENCHMARK PARAMETERS:")
            Log.i(TAG, "   Model: Whisper SMALL (ONNX INT8)")
            Log.i(TAG, "   Audio: ${audioLengthSec}s synthetic (${audioData.size} bytes)")
            Log.i(TAG, "   Threads: $threadCount")
            Log.i(TAG, "")

            // THE BENCHMARK
            Log.i(TAG, "⚡ Starting transcription...")
            val startTime = System.currentTimeMillis()

            val result = whisperEngine.transcribe(audioData)

            val elapsedMs = System.currentTimeMillis() - startTime
            val realTimeFactor = elapsedMs / (audioLengthSec * 1000)

            Log.i(TAG, "")
            Log.i(TAG, "========================================")
            Log.i(TAG, "✅ BENCHMARK RESULTS:")
            Log.i(TAG, "========================================")
            Log.i(TAG, "Audio Length:      ${audioLengthSec}s")
            Log.i(TAG, "Processing Time:   ${elapsedMs}ms")
            Log.i(TAG, "Real-Time Factor:  ${String.format("%.2f", realTimeFactor)}x")
            Log.i(TAG, "Transcription:     \"${result.text}\"")
            Log.i(TAG, "========================================")

            whisperEngine.release()

            return@withContext BenchmarkResult(
                modelName = "small",
                audioLengthSec = audioLengthSec,
                threadCount = threadCount,
                transcriptionTimeMs = elapsedMs,
                realTimeFactor = realTimeFactor,
                transcribedText = result.text,
                success = true
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Benchmark failed", e)
            whisperEngine.release()
            return@withContext BenchmarkResult(
                modelName = "small",
                audioLengthSec = 0f,
                threadCount = 0,
                transcriptionTimeMs = 0,
                realTimeFactor = 0f,
                transcribedText = "",
                success = false,
                error = e.message
            )
        }
    }

    /**
     * Load JFK audio from assets
     */
    private fun loadJFKAudio(): ByteArray? {
        return try {
            val inputStream: InputStream = context.assets.open("jfk.wav")
            // Skip WAV header (44 bytes standard)
            inputStream.skip(44)
            val audioData = inputStream.readBytes()
            inputStream.close()
            Log.i(TAG, "✅ Loaded JFK audio: ${audioData.size} bytes")
            audioData
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load JFK audio", e)
            null
        }
    }

    /**
     * Generate synthetic audio for benchmarking
     */
    private fun generateSyntheticAudio(durationSec: Int): ByteArray {
        val numSamples = SAMPLE_RATE * durationSec
        val audioData = ByteArray(numSamples * 2) // 16-bit = 2 bytes per sample

        for (i in 0 until numSamples) {
            // Generate a mix of frequencies (simulate speech-like audio)
            val t = i.toFloat() / SAMPLE_RATE
            val sample = (
                sin(2.0 * Math.PI * 200.0 * t) * 0.3 +  // Low frequency
                sin(2.0 * Math.PI * 800.0 * t) * 0.2 +  // Mid frequency
                sin(2.0 * Math.PI * 1500.0 * t) * 0.1   // High frequency
            ).toFloat()

            val scaledSample = (sample * 10000).toInt().coerceIn(-32768, 32767).toShort()

            // Convert to little-endian bytes
            audioData[i * 2] = (scaledSample.toInt() and 0xFF).toByte()
            audioData[i * 2 + 1] = ((scaledSample.toInt() shr 8) and 0xFF).toByte()
        }

        return audioData
    }
}
