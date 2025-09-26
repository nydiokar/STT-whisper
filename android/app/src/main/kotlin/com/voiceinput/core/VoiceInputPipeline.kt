package com.voiceinput.core

import android.content.Context
import android.util.Log
import com.voiceinput.config.AppConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Complete voice input pipeline connecting all components with VAD
 * Mirrors desktop/voice_input_service/service.py VoiceInputService
 *
 * Pipeline flow (Phase 3 with VAD):
 * AudioRecorder → AudioProcessor (with SileroVAD) → WhisperEngine → TextProcessor → Output
 */
class VoiceInputPipeline(
    private val context: Context,
    private val audioRecorder: AudioRecorder,
    private val whisperEngine: WhisperEngine,
    private val config: AppConfig,
    private val onResult: ((TranscriptionResult) -> Unit)? = null
) {

    companion object {
        private const val TAG = "VoiceInputPipeline"
    }

    private val textProcessor = TextProcessor()
    private val audioProcessor: AudioProcessor

    // State
    private var isRunning = false
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Accumulated text
    private var accumulatedText = ""

    // Callbacks
    private var onTranscriptionUpdate: ((String) -> Unit)? = null
    private var onError: ((Exception) -> Unit)? = null

    init {
        // Initialize AudioProcessor with VAD (Phase 3 integration)
        audioProcessor = AudioProcessor(
            context = context,
            whisperEngine = whisperEngine,
            textProcessor = textProcessor,
            config = config,
            onResult = { result ->
                handleTranscriptionResult(result)
            }
        )
    }


    /**
     * Handle transcription result from AudioProcessor (replaces processAudioChunk)
     */
    private fun handleTranscriptionResult(result: TranscriptionResult) {
        val filtered = result.text

        if (filtered.isNotEmpty()) {
            // Append to accumulated text with overlap detection
            accumulatedText = textProcessor.appendText(accumulatedText, filtered)

            // Notify result callback on main thread
            scope.launch(Dispatchers.Main) {
                onResult?.invoke(result)
            }

            Log.d(TAG, "Transcription update: '$filtered'")
        }
    }

    /**
     * Start listening and transcribing (Phase 3 with VAD)
     */
    suspend fun startListening() {
        if (isRunning) {
            Log.w(TAG, "Pipeline already running")
            return
        }

        // Start recording
        if (!audioRecorder.start()) {
            val error = IllegalStateException("Failed to start audio recording")
            Log.e(TAG, "Failed to start recording", error)
            return
        }

        // Start AudioProcessor with VAD
        if (!audioProcessor.start()) {
            audioRecorder.stop()
            val error = IllegalStateException("Failed to start audio processor")
            Log.e(TAG, "Failed to start audio processor", error)
            return
        }

        isRunning = true
        accumulatedText = ""

        // Start feeding audio from recorder to processor
        job = scope.launch {
            try {
                feedAudioToProcessor()
            } catch (e: Exception) {
                Log.e(TAG, "Pipeline error", e)
                stopListening()
            }
        }

        Log.i(TAG, "Pipeline started with VAD")
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

        // Stop AudioProcessor (which will process any remaining buffered audio)
        audioProcessor.stop()

        // Stop recording
        audioRecorder.stop()

        Log.i(TAG, "Pipeline stopped. Final text: ${accumulatedText.length} chars")
        return accumulatedText
    }

    /**
     * Feed audio stream from recorder to processor (replaces processAudioStream)
     */
    private suspend fun feedAudioToProcessor() {
        audioRecorder.audioStream()
            .buffer(capacity = 10) // Buffer up to 10 chunks
            .collect { audioChunk ->
                try {
                    // Feed audio chunk to processor (which handles VAD and transcription)
                    audioProcessor.addAudio(audioChunk)
                } catch (e: Exception) {
                    Log.e(TAG, "Error feeding audio to processor", e)
                }
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
     * Update pipeline settings
     */
    fun updateSettings(newConfig: AppConfig) {
        audioProcessor.updateSettings(newConfig)
        Log.d(TAG, "Pipeline settings updated")
    }

    /**
     * Get pipeline status
     */
    fun getStatus(): PipelineStatus {
        return PipelineStatus(
            isRunning = isRunning,
            isRecording = audioRecorder.isCurrentlyRecording(),
            accumulatedTextLength = accumulatedText.length,
            modelInfo = whisperEngine.getModelInfo(),
            isProcessorRunning = audioProcessor.isRunning()
        )
    }

    /**
     * Test VAD with synthetic audio (for testing purposes)
     */
    suspend fun testVAD(audioData: ByteArray): Boolean {
        return audioProcessor.isSilent(audioData)
    }

    /**
     * Release all resources
     */
    suspend fun release() {
        stopListening() // Ensure everything is stopped
        audioProcessor.close() // Clean up VAD resources
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
    val modelInfo: ModelInfo,
    val isProcessorRunning: Boolean = false
)