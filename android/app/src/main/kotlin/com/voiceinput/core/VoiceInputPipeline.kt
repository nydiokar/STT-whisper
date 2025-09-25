package com.voiceinput.core

import android.content.Context
import android.util.Log
import com.voiceinput.config.AppConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Complete voice input pipeline connecting all components
 * Mirrors desktop/voice_input_service/service.py VoiceInputService
 *
 * Pipeline flow:
 * AudioRecorder → WhisperEngine → TextProcessor → Output
 */
class VoiceInputPipeline(
    private val audioRecorder: AudioRecorder,
    private val whisperEngine: WhisperEngine,
    private val config: AppConfig,
    private val onResult: ((TranscriptionResult) -> Unit)? = null
) {

    companion object {
        private const val TAG = "VoiceInputPipeline"
    }

    private val textProcessor = TextProcessor()

    // State
    private var isRunning = false
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Accumulated text
    private var accumulatedText = ""

    // Callbacks
    private var onTranscriptionUpdate: ((String) -> Unit)? = null
    private var onError: ((Exception) -> Unit)? = null


    /**
     * Start listening and transcribing
     */
    fun startListening() {
        if (isRunning) {
            Log.w(TAG, "Pipeline already running")
            return
        }

        // Use constructor callback

        // Start recording
        if (!audioRecorder.start()) {
            val error = IllegalStateException("Failed to start audio recording")
            Log.e(TAG, "Failed to start recording", error)
            return
        }

        isRunning = true
        accumulatedText = ""

        // Start processing pipeline
        job = scope.launch {
            try {
                processAudioStream()
            } catch (e: Exception) {
                Log.e(TAG, "Pipeline error", e)
                stopListening()
            }
        }

        Log.i(TAG, "Pipeline started")
    }

    /**
     * Stop listening and transcribing
     * @return Final accumulated text
     */
    suspend fun stopListening(): String {
        if (!isRunning) {
            return accumulatedText
        }

        isRunning = false
        job?.cancelAndJoin()

        // Stop recording and get final audio
        val finalAudio = audioRecorder.stop()

        // Transcribe final chunk if has audio
        if (finalAudio.isNotEmpty()) {
            try {
                val result = whisperEngine.transcribe(finalAudio)
                val filtered = textProcessor.filterHallucinations(result.text)

                if (filtered.isNotEmpty()) {
                    accumulatedText = textProcessor.appendText(accumulatedText, filtered)
                    // Use result callback if provided
                    onResult?.invoke(TranscriptionResult(filtered, "en", emptyList()))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error transcribing final audio", e)
            }
        }

        Log.i(TAG, "Pipeline stopped. Final text: ${accumulatedText.length} chars")
        return accumulatedText
    }

    /**
     * Process audio stream with transcription
     */
    private suspend fun processAudioStream() {
        audioRecorder.audioStream()
            .buffer(capacity = 10) // Buffer up to 10 chunks
            .collect { audioChunk ->
                try {
                    processAudioChunk(audioChunk)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing audio chunk", e)
                }
            }
    }

    /**
     * Process a single audio chunk
     */
    private suspend fun processAudioChunk(audioChunk: ByteArray) {
        // Check if chunk has enough audio (avoid transcribing silence)
        if (audioChunk.size < config.transcription.minChunkSizeBytes) {
            return
        }

        // Optional: Check if silent (basic RMS check)
        if (AudioUtils.isSilent(audioChunk, threshold = 0.01f)) {
            Log.d(TAG, "Skipping silent chunk")
            return
        }

        Log.d(TAG, "Processing audio chunk: ${audioChunk.size} bytes")

        // Transcribe
        val result = whisperEngine.transcribe(audioChunk)

        // Filter hallucinations
        val filtered = textProcessor.filterHallucinations(result.text)

        if (filtered.isNotEmpty()) {
            // Append to accumulated text with overlap detection
            accumulatedText = textProcessor.appendText(accumulatedText, filtered)

            // Notify result callback
            withContext(Dispatchers.Main) {
                onResult?.invoke(result)
            }

            Log.d(TAG, "Transcription update: '$filtered'")
        }
    }

    /**
     * Clear accumulated text
     */
    fun clearText() {
        accumulatedText = ""
    }

    /**
     * Get current accumulated text
     */
    fun getText(): String = accumulatedText

    /**
     * Get pipeline status
     */
    fun getStatus(): PipelineStatus {
        return PipelineStatus(
            isRunning = isRunning,
            isRecording = audioRecorder.isCurrentlyRecording(),
            accumulatedTextLength = accumulatedText.length,
            modelInfo = whisperEngine.getModelInfo()
        )
    }

    /**
     * Release all resources
     */
    suspend fun release() {
        scope.cancel()
        audioRecorder.release()
        whisperEngine.release()
        Log.i(TAG, "Pipeline resources released")
    }
}

/**
 * Pipeline status
 */
data class PipelineStatus(
    val isRunning: Boolean,
    val isRecording: Boolean,
    val accumulatedTextLength: Int,
    val modelInfo: ModelInfo
)