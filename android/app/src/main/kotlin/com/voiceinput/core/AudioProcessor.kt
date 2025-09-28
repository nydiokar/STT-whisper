package com.voiceinput.core

import android.content.Context
import android.util.Log
import com.voiceinput.config.AppConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Audio processing component that manages VAD-based audio buffering and transcription.
 * Port of desktop/voice_input_service/core/processing.py TranscriptionWorker
 *
 * This component is responsible for:
 * 1. Voice activity detection using Silero VAD
 * 2. Audio buffering based on speech detection
 * 3. Asynchronous processing of detected speech segments
 * 4. Delivery of transcription results via callbacks
 */
class AudioProcessor(
    private val context: Context,
    private val whisperEngine: WhisperEngine,
    private val textProcessor: TextProcessor,
    private var config: AppConfig,
    private val onResult: (TranscriptionResult) -> Unit
) {

    companion object {
        private const val TAG = "AudioProcessor"
        private const val QUEUE_TIMEOUT_MS = 50L // Reduced timeout for more responsive processing
    }

    // VAD component (matching desktop initialization)
    private var sileroVAD: SileroVAD? = null

    // Configuration values cached for performance - optimized for streaming
    private var minAudioLengthBytes: Int = 0
    private var sampleRate: Int = config.audio.sampleRate
    private var silenceDurationSec: Float = config.audio.silenceDurationSec

    // Removed streaming chunk logic - now matches desktop behavior exactly
    private var maxChunkDurationSec: Float = config.audio.maxChunkDurationSec
    private var maxChunkBytes: Int = 0
    private var minChunkSizeBytes: Int = config.transcription.minChunkSizeBytes

    // Processing state
    private val isRunning = AtomicBoolean(false)
    private val lastAudioTime = AtomicLong(System.currentTimeMillis())
    private var processingJob: Job? = null
    private var processingScope: CoroutineScope? = null

    // Audio data channel (replaces Python queue.Queue)
    private var audioChannel: Channel<AudioChunk>? = null

    // Buffer state for the processing loop
    private data class BufferState(
        val activeSpeechBuffer: ByteArray = ByteArray(0),
        val lastSpeechTime: Long = System.currentTimeMillis(),
        val totalProcessedBytes: Int = 0
    ) {
        fun withSpeechAdded(chunk: ByteArray): BufferState = copy(
            activeSpeechBuffer = activeSpeechBuffer + chunk,
            lastSpeechTime = System.currentTimeMillis(),
            totalProcessedBytes = totalProcessedBytes + chunk.size
        )

        fun withSilenceAdded(chunk: ByteArray): BufferState = copy(
            activeSpeechBuffer = activeSpeechBuffer + chunk,
            totalProcessedBytes = totalProcessedBytes + chunk.size
        )

        fun cleared(): BufferState = copy(
            activeSpeechBuffer = ByteArray(0),
            totalProcessedBytes = 0
        )
    }

    // Audio chunk wrapper for channel communication
    private sealed class AudioChunk {
        data class Data(val bytes: ByteArray) : AudioChunk()
        object Stop : AudioChunk() // Sentinel object (replaces Python STOP_SIGNAL)
    }

    init {
        updateConfigurationValues()
        initializeVAD()
    }

    /**
     * Initialize Silero VAD (matching desktop silence detector initialization)
     */
    private fun initializeVAD() {
        try {
            sileroVAD = SileroVAD(context, config)
            if (!sileroVAD!!.isInitialized()) {
                Log.e(TAG, "Silence detector failed to initialize in AudioProcessor. VAD will not function.")
                // Allow graceful degradation (matching desktop behavior)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing VAD: ${e.message}", e)
            sileroVAD = null
        }
    }

    /**
     * Update configuration values from config (optimized for streaming)
     */
    private fun updateConfigurationValues() {
        minAudioLengthBytes = (config.audio.minAudioLengthSec * sampleRate * 2).toInt() // Convert seconds to bytes
        silenceDurationSec = config.audio.silenceDurationSec
        maxChunkDurationSec = config.audio.maxChunkDurationSec
        maxChunkBytes = (maxChunkDurationSec * sampleRate * 2).toInt()
        minChunkSizeBytes = config.transcription.minChunkSizeBytes

        Log.i(TAG, "🔧 Config updated: SilenceDur=${silenceDurationSec}s, MaxChunk=${maxChunkDurationSec}s (${maxChunkBytes} bytes)")
    }

    /**
     * Check if we've received audio data recently (matching desktop has_recent_audio)
     */
    private fun hasRecentAudio(): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastAudio = (currentTime - lastAudioTime.get()) / 1000.0
        return timeSinceLastAudio < silenceDurationSec
    }

    /**
     * Start the audio processing pipeline (matching desktop start method)
     */
    suspend fun start(): Boolean {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "AudioProcessor already running")
            return false
        }

        // Reset timing
        lastAudioTime.set(System.currentTimeMillis())

        // Create processing scope and channel
        processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        audioChannel = Channel(capacity = Channel.UNLIMITED)

        // Start the worker coroutine (replaces Python worker thread)
        processingJob = processingScope!!.launch {
            workerLoop()
        }

        Log.i(TAG, "AudioProcessor started (Continuous Mode)")
        return true
    }

    /**
     * Stop the audio processing pipeline (matching desktop stop method)
     */
    suspend fun stop() {
        if (!isRunning.get()) {
            return
        }

        Log.d(TAG, "Stopping AudioProcessor...")

        // Send stop signal to worker loop
        audioChannel?.send(AudioChunk.Stop)

        // Wait for processing to finish
        processingJob?.join()

        // Clean up
        audioChannel?.close()
        processingScope?.cancel()
        isRunning.set(false)

        Log.i(TAG, "AudioProcessor stopped")
    }

    /**
     * Add audio data to the processing pipeline (matching desktop add_audio method)
     */
    suspend fun addAudio(data: ByteArray) {
        if (isRunning.get() && data.isNotEmpty()) {
            audioChannel?.send(AudioChunk.Data(data))
            lastAudioTime.set(System.currentTimeMillis())
        }
    }

    /**
     * Determine if audio chunk is silent using VAD (matching desktop _is_silent method)
     */
    suspend fun isSilent(audioData: ByteArray): Boolean {
        Log.d(TAG, "VAD check: audio chunk size = ${audioData.size} bytes")

        val vad = sileroVAD
        return if (vad?.isInitialized() == true) {
            vad.isSilent(audioData)
        } else {
            // If VAD failed to init, assume everything is speech to avoid dropping audio
            // (matching desktop behavior)
            Log.w(TAG, "VAD not initialized, assuming speech")
            false
        }
    }

    /**
     * Main worker loop that processes audio chunks (port of desktop _worker_loop method)
     */
    private suspend fun workerLoop() {
        Log.i(TAG, "AudioProcessor worker loop started")

        var bufferState = BufferState()

        try {
            while (isRunning.get()) {
                // Use select with timeout to allow periodic checks (matching desktop timeout behavior)
                val chunk = withTimeoutOrNull(QUEUE_TIMEOUT_MS) {
                    audioChannel?.receive()
                }

                when (chunk) {
                    is AudioChunk.Stop -> {
                        Log.d(TAG, "Stop signal received in worker loop")
                        // Process any remaining data before stopping (matching desktop behavior)
                        if (bufferState.activeSpeechBuffer.size >= minChunkSizeBytes) {
                            Log.i(TAG, "Processing final remaining buffer chunk (${bufferState.activeSpeechBuffer.size} bytes) before stopping")
                            processAudioBuffer(bufferState.activeSpeechBuffer)
                        }
                        break
                    }

                    is AudioChunk.Data -> {
                        bufferState = processAudioChunk(bufferState, chunk.bytes)
                    }

                    null -> {
                        // Timeout occurred - check for inactivity processing (matching desktop timeout logic)
                        if (bufferState.activeSpeechBuffer.size >= minAudioLengthBytes && !hasRecentAudio()) {
                            Log.i(TAG, "Processing chunk due to inactivity timeout (${bufferState.activeSpeechBuffer.size} bytes)")
                            processAudioBuffer(bufferState.activeSpeechBuffer)
                            bufferState = bufferState.cleared()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in worker loop: ${e.message}", e)
        }

        Log.i(TAG, "AudioProcessor worker loop finished")
    }

    /**
     * Process a single audio chunk with VAD and buffering logic (extracted from desktop _worker_loop)
     */
    private suspend fun processAudioChunk(currentState: BufferState, audioChunk: ByteArray): BufferState {
        if (audioChunk.isEmpty()) return currentState

        try {
            // Check VAD on the incoming chunk (matching desktop logic)
            val isChunkSilent = isSilent(audioChunk)
            val currentTime = System.currentTimeMillis()
            val timeSinceLastSpeech = (currentTime - currentState.lastSpeechTime) / 1000.0

            Log.d(TAG, if (isChunkSilent) {
                "VAD=Silence. Time since speech: ${"%.2f".format(timeSinceLastSpeech)}s. Buffer: ${currentState.activeSpeechBuffer.size} bytes"
            } else {
                "VAD=Speech. Added ${audioChunk.size} bytes. Buffer: ${currentState.activeSpeechBuffer.size + audioChunk.size} bytes"
            })

            return if (!isChunkSilent) {
                // Speech detected - use streaming optimization
                val newState = currentState.withSpeechAdded(audioChunk)

                // Process if buffer exceeds max duration/size (matching desktop logic)
                if (newState.activeSpeechBuffer.size >= maxChunkBytes) {
                    Log.i(TAG, "Processing chunk due to max size reached (${newState.activeSpeechBuffer.size} bytes)")
                    processAudioBuffer(newState.activeSpeechBuffer)
                    newState.cleared()
                } else {
                    newState
                }
            } else {
                // Silence detected - still process accumulated speech if significant
                if (currentState.activeSpeechBuffer.size >= minAudioLengthBytes &&
                    timeSinceLastSpeech >= silenceDurationSec) {
                    // Process buffer due to silence after speech
                    Log.i(TAG, "Processing chunk due to silence detected after speech (${currentState.activeSpeechBuffer.size} bytes)")
                    processAudioBuffer(currentState.activeSpeechBuffer)
                    currentState.cleared()
                } else if (currentState.activeSpeechBuffer.isNotEmpty()) {
                    // Buffer some silence if speech just ended (helps context)
                    val newState = currentState.withSilenceAdded(audioChunk)

                    // Process if silence makes buffer exceed max size (matching desktop logic)
                    if (newState.activeSpeechBuffer.size >= maxChunkBytes) {
                        Log.i(TAG, "Processing chunk due to max size reached during silence (${newState.activeSpeechBuffer.size} bytes)")
                        processAudioBuffer(newState.activeSpeechBuffer)
                        newState.cleared()
                    } else {
                        newState
                    }
                } else {
                    currentState // No buffered speech, ignore silence
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio chunk: ${e.message}", e)
            return currentState.cleared() // Clear buffer on error to prevent reprocessing bad data
        }
    }

    /**
     * Process a complete buffer of audio data (port of desktop _process_audio_buffer method)
     */
    private suspend fun processAudioBuffer(audioData: ByteArray) {
        val bufferLen = audioData.size
        if (bufferLen < minChunkSizeBytes) {
            Log.d(TAG, "Skipping transcription for small buffer chunk ($bufferLen bytes < $minChunkSizeBytes min bytes)")
            return
        }

        Log.i(TAG, "Sending buffer chunk (${"%.1f".format(bufferLen / 1024.0)} KB) to transcription engine")

        try {
            // Transcribe the audio (matching desktop transcription call)
            val result = whisperEngine.transcribe(audioData)

            // Check if the result contains meaningful text
            val text = result.text.trim()

            if (text.isNotEmpty()) {
                Log.d(TAG, "AudioProcessor received transcription result: '${text.take(50)}...'")

                // Apply text processing (hallucination filtering, etc.)
                val filteredText = textProcessor.filterHallucinations(text)

                if (filteredText.isNotEmpty()) {
                    // Create final result with filtered text
                    val finalResult = result.copy(text = filteredText)

                    // Send the processed result via callback
                    try {
                        onResult(finalResult)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onResult callback: ${e.message}", e)
                    }
                } else {
                    Log.d(TAG, "Text was filtered out as hallucination")
                }
            } else {
                Log.d(TAG, "AudioProcessor received empty transcription result")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription call: ${e.message}", e)
        }
    }

    /**
     * Update settings from new configuration (matching desktop update_settings method)
     */
    fun updateSettings(newConfig: AppConfig) {
        Log.d(TAG, "Updating AudioProcessor settings from config")

        // Update VAD settings
        sileroVAD?.updateSettings(newConfig)

        // Update configuration values
        config = newConfig
        updateConfigurationValues()
    }

    /**
     * Get current processing status
     */
    fun isRunning(): Boolean = isRunning.get()
    suspend fun close() {
        stop() // Signal the processor to stop
        sileroVAD?.close() // Clean up VAD resources
        Log.d(TAG, "AudioProcessor closed")
    }
}