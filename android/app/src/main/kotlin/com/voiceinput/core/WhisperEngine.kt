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

    suspend fun initialize(modelFilePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            release()

            val modelFile = File(modelFilePath)
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file not found: $modelFilePath")
                return@withContext false
            }

            modelPath = modelFilePath
            whisperContext = WhisperContext.createContextFromFile(modelFilePath)

            isInitialized = true
            Log.i(TAG, "Whisper initialized with model: $modelName at $modelFilePath")

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Whisper", e)
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
            // Convert ByteArray to FloatArray (PCM 16-bit to float32)
            Log.d(TAG, "Converting ${audioData.size} bytes to float array...")
            val floatArray = convertBytesToFloats(audioData)
            Log.d(TAG, "Float array size: ${floatArray.size} samples")

            // Call the actual Whisper API with timeout
            Log.d(TAG, "Starting Whisper transcription...")
            val startTime = System.currentTimeMillis()
            val transcriptionText = withTimeout(300_000) { // extend timeout to 5 minutes for stability during testing
                whisperContext!!.transcribeData(floatArray, printTimestamp = false)
            }
            val elapsedMs = System.currentTimeMillis() - startTime

            Log.i(TAG, "Transcription complete in ${elapsedMs}ms: '$transcriptionText'")

            return@withContext TranscriptionResult(
                text = transcriptionText.trim(),
                language = language,
                segments = emptyList(),
                confidence = 1.0f,
                processingTimeMs = 0
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
            whisperContext?.release()
            whisperContext = null
            isInitialized = false
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