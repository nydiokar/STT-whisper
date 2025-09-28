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

    // Memory management coordination
    private val memoryManager = MemoryManager(context)

    // State
    private var isRunning = false
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Accumulated text (using desktop approach - immediate streaming)
    private var accumulatedText = ""

    // Callbacks
    private var onTranscriptionUpdate: ((String) -> Unit)? = null
    private var onError: ((Exception) -> Unit)? = null

    // Performance metrics
    private var transcriptionCount = 0
    private var totalProcessingTime = 0L

    init {
        setupMemoryManagement()

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
     * Set up coordinated memory management across all pipeline components
     */
    private fun setupMemoryManagement() {
        memoryManager.setMemoryWarningCallback {
            Log.w(TAG, "🟡 Pipeline memory warning - optimizing resources")
            // Could implement intelligent resource management here
            scope.launch {
                if (!isRunning) {
                    // If pipeline not running, suggest cleanup
                    Log.i(TAG, "Pipeline idle during memory warning - ready for cleanup")
                }
            }
        }

        memoryManager.setMemoryCriticalCallback {
            Log.e(TAG, "🔴 Pipeline memory critical - emergency measures")
            scope.launch {
                if (isRunning) {
                    Log.w(TAG, "Critical memory during active pipeline - considering pause")
                    // Could implement emergency pause/resume here
                }
                memoryManager.requestGarbageCollection("Pipeline critical memory")
            }
        }

        memoryManager.setLowMemoryCallback {
            Log.e(TAG, "🚨 System low memory - pipeline emergency response")
            scope.launch {
                // Emergency response - could pause pipeline if critical
                Log.w(TAG, "Low memory: Pipeline monitoring resource usage")
                memoryManager.logMemoryStatusImmediate("Pipeline emergency check")
            }
        }
    }


    /**
     * Handle transcription result from AudioProcessor with immediate streaming (desktop approach)
     */
    private fun handleTranscriptionResult(result: TranscriptionResult) {
        val filtered = result.text.trim()

        if (filtered.isNotEmpty()) {
            // Update performance metrics
            transcriptionCount++
            totalProcessingTime += result.processingTimeMs

            // Memory monitoring for high-frequency operations
            memoryManager.logMemoryStatus("Transcription result #$transcriptionCount")

            // Use desktop approach: immediate text appending with overlap detection
            accumulatedText = textProcessor.appendText(accumulatedText, filtered)

            // Log streaming progress
            Log.i(TAG, "🚀 Streaming chunk #$transcriptionCount: '$filtered' (${result.processingTimeMs}ms)")
            Log.d(TAG, "📝 Updated text: '${accumulatedText.takeLast(50)}...' (${accumulatedText.length} chars)")

            // Notify result callback on main thread with immediate update
            scope.launch(Dispatchers.Main) {
                val streamingResult = result.copy(text = accumulatedText)
                onResult?.invoke(streamingResult)
                onTranscriptionUpdate?.invoke(accumulatedText)
            }

            // Memory check after text accumulation
            if (memoryManager.isMemoryWarning() && transcriptionCount % 5 == 0) {
                Log.w(TAG, "Memory warning after ${transcriptionCount} transcriptions - avg ${totalProcessingTime/transcriptionCount}ms")
            }
        }
    }

    /**
     * Start listening and transcribing with comprehensive memory monitoring
     */
    suspend fun startListening() {
        if (isRunning) {
            Log.w(TAG, "Pipeline already running")
            return
        }

        memoryManager.logMemoryStatusImmediate("Before pipeline start")

        // Reset performance metrics
        transcriptionCount = 0
        totalProcessingTime = 0L

        // Memory check before starting resource-intensive operations
        if (memoryManager.isMemoryCritical()) {
            Log.w(TAG, "Critical memory before starting pipeline - requesting cleanup")
            memoryManager.requestGarbageCollection("Pre-pipeline start")
            kotlinx.coroutines.delay(200)
        }

        // Start recording
        if (!audioRecorder.start()) {
            val error = IllegalStateException("Failed to start audio recording")
            Log.e(TAG, "Failed to start recording", error)
            memoryManager.logMemoryStatusImmediate("After recording start FAILED")
            return
        }

        memoryManager.logMemoryStatus("After audio recorder start")

        // Start AudioProcessor with VAD
        if (!audioProcessor.start()) {
            audioRecorder.stop()
            val error = IllegalStateException("Failed to start audio processor")
            Log.e(TAG, "Failed to start audio processor", error)
            memoryManager.logMemoryStatusImmediate("After processor start FAILED")
            return
        }

        memoryManager.logMemoryStatus("After audio processor start")

        isRunning = true
        accumulatedText = ""

        // Start feeding audio from recorder to processor
        job = scope.launch {
            try {
                feedAudioToProcessor()
            } catch (e: Exception) {
                Log.e(TAG, "Pipeline error", e)
                memoryManager.logMemoryStatusImmediate("Pipeline error occurred")
                stopListening()
            }
        }

        memoryManager.logMemoryStatusImmediate("Pipeline fully started")
        Log.i(TAG, "Pipeline started with VAD and memory monitoring")
    }

    /**
     * Stop listening and transcribing with memory cleanup
     * @return Final accumulated text
     */
    suspend fun stopListening(): String {
        if (!isRunning) {
            return accumulatedText
        }

        memoryManager.logMemoryStatusImmediate("Before pipeline stop")

        isRunning = false
        job?.cancelAndJoin()

        // Stop AudioProcessor (which will process any remaining buffered audio)
        audioProcessor.stop()
        memoryManager.logMemoryStatus("After audio processor stop")

        // Stop recording
        audioRecorder.stop()
        memoryManager.logMemoryStatus("After audio recorder stop")

        // Text is already accumulated, no additional processing needed
        val finalText = accumulatedText

        // Performance summary
        val avgProcessingTime = if (transcriptionCount > 0) totalProcessingTime / transcriptionCount else 0L
        Log.i(TAG, "🏁 Pipeline stopped. Performance: ${transcriptionCount} transcriptions, avg ${avgProcessingTime}ms")
        Log.i(TAG, "📝 Final text: ${finalText.length} chars")

        // Cleanup and memory optimization after stop
        memoryManager.requestGarbageCollection("Pipeline stop cleanup")
        memoryManager.logMemoryStatusImmediate("After pipeline stop cleanup")

        return finalText
    }

    /**
     * Feed audio stream from recorder to processor (replaces processAudioStream)
     */
    private suspend fun feedAudioToProcessor() {
        audioRecorder.audioStream()
            .buffer(capacity = 5) // Reduced buffer for lower latency
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
     * Set callback for transcription updates
     */
    fun setTranscriptionUpdateCallback(callback: (String) -> Unit) {
        onTranscriptionUpdate = callback
    }

    /**
     * Update pipeline settings
     */
    fun updateSettings(newConfig: AppConfig) {
        audioProcessor.updateSettings(newConfig)
        Log.d(TAG, "Pipeline settings updated")
    }

    /**
     * Get comprehensive pipeline status including memory and performance metrics
     */
    fun getStatus(): PipelineStatus {
        val memoryStatus = memoryManager.getMemoryStatus()
        val avgProcessingTime = if (transcriptionCount > 0) totalProcessingTime / transcriptionCount else 0L

        return PipelineStatus(
            isRunning = isRunning,
            isRecording = audioRecorder.isCurrentlyRecording(),
            accumulatedTextLength = accumulatedText.length,
            modelInfo = whisperEngine.getModelInfo(),
            isProcessorRunning = audioProcessor.isRunning(),
            transcriptionCount = transcriptionCount,
            averageProcessingTimeMs = avgProcessingTime,
            memoryStatus = memoryStatus
        )
    }

    /**
     * Test VAD with synthetic audio (for testing purposes)
     */
    suspend fun testVAD(audioData: ByteArray): Boolean {
        return audioProcessor.isSilent(audioData)
    }

    /**
     * Release all resources with comprehensive cleanup
     */
    suspend fun release() {
        memoryManager.logMemoryStatusImmediate("Before pipeline release")

        stopListening() // Ensure everything is stopped
        audioProcessor.close() // Clean up VAD resources
        scope.cancel()
        audioRecorder.release()
        whisperEngine.release()

        // Final cleanup and memory management
        memoryManager.release()
        memoryManager.requestGarbageCollection("Final pipeline cleanup")

        memoryManager.logMemoryStatusImmediate("After pipeline release")
        Log.i(TAG, "Pipeline resources released with full cleanup")
    }
}

/**
 * Comprehensive pipeline status with memory and performance metrics
 */
data class PipelineStatus(
    val isRunning: Boolean,
    val isRecording: Boolean,
    val accumulatedTextLength: Int,
    val modelInfo: ModelInfo,
    val isProcessorRunning: Boolean = false,
    val transcriptionCount: Int = 0,
    val averageProcessingTimeMs: Long = 0L,
    val memoryStatus: MemoryStatus? = null
)