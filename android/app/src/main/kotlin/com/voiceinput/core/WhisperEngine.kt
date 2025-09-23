package com.voiceinput.core

import android.content.Context
import android.util.Log
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
            val audioFloat = AudioUtils.bytesToFloat(audioData)
            val fullText = whisperContext!!.transcribeData(audioFloat, printTimestamp = false)

            Log.i(TAG, "Transcription complete: text length: ${fullText.length}")

            TranscriptionResult(
                text = fullText.trim(),
                language = language,
                segments = emptyList()
            )

        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            throw TranscriptionException("Failed to transcribe audio", e)
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
}

data class TranscriptionResult(
    val text: String,
    val language: String,
    val segments: List<Segment>
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