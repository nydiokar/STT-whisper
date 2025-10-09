package com.voiceinput.core

import android.content.Context
import android.util.Log
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WhisperEngine(
    private val context: Context,
    private val modelName: String = "base",
    private val language: String = "en"
) {

    companion object {
        private const val TAG = "WhisperEngine"
    }

    private var whisperContext: WhisperContext? = null
    private var isInitialized = false
    private var modelPath: String? = null

    // Memory management integration
    private val memoryManager = MemoryManager(context)

    init {
        setupMemoryCallbacks()
    }

    /**
     * Set up memory pressure response callbacks
     */
    private fun setupMemoryCallbacks() {
        memoryManager.setMemoryWarningCallback {
            Log.w(TAG, "⚠️ Memory warning - consider releasing resources")
            // Could implement model offloading here if needed
        }

        memoryManager.setMemoryCriticalCallback {
            Log.e(TAG, "🚨 Critical memory pressure - forcing cleanup")
            // Force garbage collection and consider emergency cleanup
            memoryManager.requestGarbageCollection("Critical memory in WhisperEngine")
        }

        memoryManager.setLowMemoryCallback {
            Log.e(TAG, "🚨 System low memory - emergency response")
            // Could implement emergency model unloading
            if (isInitialized) {
                Log.w(TAG, "Low memory: considering model release to free resources")
            }
        }
    }

    suspend fun initialize(modelFilePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            memoryManager.logMemoryStatusImmediate("Before model initialization")
            release()

            val modelFile = File(modelFilePath)
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file not found: $modelFilePath")
                return@withContext false
            }

            // Check memory before loading heavy model
            if (memoryManager.isMemoryCritical()) {
                Log.w(TAG, "Critical memory detected before model loading - requesting GC")
                memoryManager.requestGarbageCollection("Pre-model loading")
                kotlinx.coroutines.delay(200) // Give GC time to work
            }

            modelPath = modelFilePath
            whisperContext = WhisperContext.createContextFromFile(modelFilePath)

            isInitialized = true
            memoryManager.logMemoryStatusImmediate("After model initialization")
            Log.i(TAG, "Whisper initialized with model: $modelName at $modelFilePath")

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Whisper", e)
            memoryManager.logMemoryStatusImmediate("After model initialization FAILED")
            isInitialized = false
            false
        }
    }

    suspend fun initializeFromAssets(assetPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            release()

            whisperContext = WhisperContext.createContextFromAsset(
                context.assets,
                assetPath
            )

            isInitialized = true
            Log.i(TAG, "Whisper initialized from assets: $assetPath")

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Whisper from assets", e)
            isInitialized = false
            false
        }
    }

    suspend fun transcribe(audioData: ByteArray): TranscriptionResult = withContext(Dispatchers.IO) {
        if (!isInitialized || whisperContext == null) {
            throw IllegalStateException("Whisper not initialized. Call initialize() first.")
        }

        if (audioData.isEmpty()) {
            return@withContext TranscriptionResult("", language, emptyList())
        }

        val audioDurationSec = audioData.size / (16000 * 2).toFloat()
        Log.i(TAG, "Transcribing ${audioDurationSec}s of audio")

        try {
            // PERFORMANCE: Reduced memory management overhead in hot path
            // Only check memory if it's critical, don't log status frequently
            if (memoryManager.isMemoryCritical()) {
                Log.w(TAG, "Critical memory before transcription - requesting GC")
                memoryManager.requestGarbageCollection("Pre-transcription")
                kotlinx.coroutines.delay(100)
            }

            // Convert ByteArray to FloatArray (PCM 16-bit to float32)
            val floatArray = convertBytesToFloats(audioData)

            // Call the actual Whisper API with timeout
            Log.d(TAG, "Starting Whisper transcription...")
            val startTime = System.currentTimeMillis()

            // Timeout for transcription - increased for current performance issues
            val transcriptionText = withTimeout(300_000) { // 5 minute timeout until performance is optimized
                whisperContext!!.transcribeData(floatArray, printTimestamp = false)
            }
            val elapsedMs = System.currentTimeMillis() - startTime

            // PERFORMANCE: Removed memory logging from hot path
            Log.i(TAG, "Transcription complete in ${elapsedMs}ms: '$transcriptionText'")

            return@withContext TranscriptionResult(
                text = transcriptionText.trim(),
                language = language,
                segments = emptyList(),
                confidence = 1.0f,
                processingTimeMs = elapsedMs
            )

        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed: ${e.message}", e)
            e.printStackTrace()
            throw TranscriptionException("Failed to transcribe audio: ${e.message}", e)
        }
    }

    fun getModelInfo(): ModelInfo {
        return ModelInfo(
            name = modelName,
            path = modelPath,
            language = language,
            isInitialized = isInitialized,
            type = "whisper.cpp (official)"
        )
    }

    suspend fun release() = withContext(Dispatchers.IO) {
        try {
            memoryManager.logMemoryStatusImmediate("Before Whisper release")

            whisperContext?.release()
            whisperContext = null
            isInitialized = false

            // Clean up memory manager
            memoryManager.release()

            // Request GC after releasing large resources
            memoryManager.requestGarbageCollection("Whisper engine release")

            memoryManager.logMemoryStatusImmediate("After Whisper release")
            Log.i(TAG, "Whisper engine released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Whisper resources", e)
        }
    }

    /**
     * Convert PCM 16-bit audio bytes to float32 array for Whisper
     * Whisper expects audio samples normalized to [-1.0, 1.0]
     */
    private fun convertBytesToFloats(audioData: ByteArray): FloatArray {
        val floatArray = FloatArray(audioData.size / 2)
        val byteBuffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)

        for (i in floatArray.indices) {
            // Convert 16-bit PCM to float [-1.0, 1.0]
            val sample = byteBuffer.short.toFloat() / 32768.0f
            floatArray[i] = sample
        }

        return floatArray
    }
}

data class TranscriptionResult(
    val text: String,
    val language: String,
    val segments: List<Segment>,
    val confidence: Float = 1.0f,
    val processingTimeMs: Long = 0
)

data class Segment(
    val id: Int,
    val start: Float,
    val end: Float,
    val text: String
)

data class ModelInfo(
    val name: String,
    val path: String?,
    val language: String,
    val isInitialized: Boolean,
    val type: String
)

class TranscriptionException(message: String, cause: Throwable? = null) : Exception(message, cause)