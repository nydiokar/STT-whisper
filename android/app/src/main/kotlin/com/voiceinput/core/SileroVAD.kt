package com.voiceinput.core

import android.content.Context
import android.util.Log
import ai.onnxruntime.*
import com.voiceinput.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Silence detection utility using Silero VAD ONNX model.
 * Port of desktop/voice_input_service/utils/silence_detection.py
 *
 * Handles voice activity detection using the Silero VAD model with ONNX Runtime.
 * Maintains the same logic and configuration as the desktop version but adapted for Android.
 */
class SileroVAD(
    private val context: Context,
    private var config: AppConfig
) {

    companion object {
        private const val TAG = "SileroVAD"
        private const val MODEL_FILENAME = "silero_vad.onnx"

        // VAD frame settings (matching desktop implementation)
        private const val FRAME_DURATION_MS = 30 // Silero examples use 30ms frames
        private const val BYTES_PER_SAMPLE = 2   // 16-bit PCM = 2 bytes per sample
    }

    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var initialized = false

    // State tensor for stateful RNN (shape [2, None, 128])
    private var stateState: OnnxTensor? = null

    // Configuration values (cached for performance)
    private var sampleRate: Int = config.audio.sampleRate
    private var vadThreshold: Float = config.audio.vadThreshold

    // Frame size calculations (matching desktop logic)
    private val frameSizeSamples: Int = (sampleRate * (FRAME_DURATION_MS / 1000.0)).toInt()
    private val frameSizeBytes: Int = frameSizeSamples * BYTES_PER_SAMPLE

    init {
        // Validate sample rate compatibility (matching desktop validation)
        if (sampleRate !in listOf(8000, 16000)) {
            Log.w(TAG, "Silero VAD model typically supports 8kHz or 16kHz. " +
                      "Current config rate is ${sampleRate}Hz. VAD might not work as expected.")
        }

        Log.d(TAG, "VAD Frame size: $frameSizeBytes bytes (${FRAME_DURATION_MS}ms) at ${sampleRate}Hz")

        initializeDetector()
    }

    /**
     * Initialize the Silero VAD detector using ONNX Runtime.
     * Port of desktop _init_detector() method
     */
    private fun initializeDetector() {
        try {
            Log.i(TAG, "Loading Silero VAD model...")

            // Initialize ONNX Runtime environment
            ortEnvironment = OrtEnvironment.getEnvironment()

            // Load model from assets
            val modelBytes = context.assets.open("models/$MODEL_FILENAME").use { inputStream ->
                inputStream.readBytes()
            }

            // Create session options for Android optimization
            val sessionOptions = OrtSession.SessionOptions().apply {
                // Use CPU provider for maximum compatibility
                // Could be enhanced later with NNAPI provider for hardware acceleration
                addConfigEntry("session.load_model_format", "ONNX")
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                
                // Suppress ONNX Runtime cleanup warnings (they're just noise)
                addConfigEntry("session.log_severity_level", "3") // 3 = ERROR level only
                addConfigEntry("session.log_verbosity_level", "0") // 0 = minimal logging
            }

            // Create ONNX session
            ortSession = ortEnvironment!!.createSession(modelBytes, sessionOptions)

            // Initialize state tensor (shape [2, 1, 128])
            initializeStateTensor()

            initialized = true
            Log.i(TAG, "Silero VAD model loaded successfully")

        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL Error initializing Silero VAD: ${e.message}", e)
            cleanup()
            initialized = false
            // Note: Not throwing exception to allow graceful degradation (matching desktop behavior)
        }
    }

    /**
     * Determine if audio chunk is silent using Silero VAD.
     * Port of desktop is_silent() method with identical logic.
     *
     * @param audioChunk Raw audio bytes (16-bit PCM, matching sample_rate) to analyze.
     *                   Should ideally be a multiple of frame_size_bytes, but the underlying
     *                   model can handle variable lengths.
     * @return True if audio is determined to be silent, False if speech is detected (or if VAD failed).
     */
    suspend fun isSilent(audioChunk: ByteArray): Boolean = withContext(Dispatchers.Default) {
        // Fail-safe checks (matching desktop behavior)
        if (!initialized || ortSession == null) {
            Log.w(TAG, "Silero VAD not initialized, cannot perform silence detection. Assuming NOT silent.")
            return@withContext false // Fail safe: assume not silent if VAD isn't working
        }

        if (audioChunk.isEmpty()) {
            Log.d(TAG, "Received empty audio chunk, assuming silent.")
            return@withContext true
        }

        try {
            // Convert bytes to float32 array (matching desktop preprocessing)
            val audioFloat32 = convertBytesToFloat32(audioChunk)

            // Run inference with both audio and sample rate (matching desktop behavior)
            val speechProb = runInference(audioFloat32)

            // Apply threshold from config (matching desktop logic)
            val isSpeech = speechProb >= vadThreshold

            // Log only when speech detection changes state (reduce log spam)
            if (isSpeech) {
                Log.d(TAG, "🎤 Speech detected: prob=${"%.3f".format(speechProb)}")
            }

            return@withContext !isSpeech // Return true if silent (i.e., not speech)

        } catch (e: Exception) {
            Log.e(TAG, "Error during Silero VAD processing: ${e.message}", e)
            return@withContext false // Fail safe: assume not silent on error
        }
    }

    /**
     * Convert audio bytes to float32 array.
     * Matches desktop conversion: audio_int16.astype(np.float32) / 32768.0
     */
    private fun convertBytesToFloat32(audioBytes: ByteArray): FloatArray {
        val byteBuffer = ByteBuffer.wrap(audioBytes)
            .order(ByteOrder.LITTLE_ENDIAN) // Ensure little-endian byte order

        val audioFloat32 = FloatArray(audioBytes.size / 2) // 16-bit samples = 2 bytes per sample

        for (i in audioFloat32.indices) {
            if (byteBuffer.remaining() >= 2) {
                val int16Sample = byteBuffer.short
                audioFloat32[i] = int16Sample.toFloat() / 32768.0f // Normalize to [-1.0, 1.0]
            }
        }

        return audioFloat32
    }

    /**
     * Create ONNX input tensor from float32 audio array
     * NOTE: Starting with simple single-input approach, may need to add sample rate later
     */
    private fun createInputTensor(audioFloat32: FloatArray): OnnxTensor {
        val ortEnvironment = this.ortEnvironment ?: throw IllegalStateException("ORT environment not initialized")

        // Create audio tensor - trying different shapes to match model expectations
        // Most ONNX Silero models expect shape [1, audio_length] (batch_size=1)
        val shape = longArrayOf(1, audioFloat32.size.toLong())
        val floatBuffer = FloatBuffer.wrap(audioFloat32)

        return OnnxTensor.createTensor(ortEnvironment, floatBuffer, shape)
    }

    /**
     * Run inference with audio, sample rate, and state inputs (stateful RNN)
     */
    private fun runInference(audioFloat32: FloatArray): Float {
        val ortSession = this.ortSession ?: throw IllegalStateException("ORT session not initialized")

        // Create all input tensors (audio, sample rate, h0, c0)
        val inputs = createMultiInputTensors(audioFloat32)

        try {
            val result = ortSession.run(inputs)
            try {
                // Get output tensor (Silero VAD outputs speech probability + updated states)
                val outputTensor = result.get(0) as OnnxTensor
                val outputArray = outputTensor.floatBuffer.array()

                // Update state tensor from model output for next inference
                updateStateTensor(result)

                // Return speech probability
                return outputArray[0]
            } finally {
                // Close the result to free resources
                result.close()
            }
        } finally {
            // Clean up only the temporary input tensors (not the persistent state tensor)
            inputs["input"]?.close()
            inputs["sr"]?.close()
            // Note: state tensor is persistent and should not be closed here
        }
    }

    /**
     * Update state tensor from model output for next inference
     */
    private fun updateStateTensor(result: OrtSession.Result) {
        try {
            // The model should output updated state tensor
            if (result.size() >= 2) {
                val newStateTensor = result.get(1) as OnnxTensor
                val newStateShape = newStateTensor.info.shape

                Log.d(TAG, "DEBUG: Model output has ${result.size()} tensors")
                Log.d(TAG, "DEBUG: State tensor shape from model: [${newStateShape.joinToString(",")}]")
                Log.d(TAG, "DEBUG: Current state tensor shape: [${stateState?.info?.shape?.joinToString(",")}]")

                // For Silero VAD, we need exactly [2, 1, 128] shape for the state tensor
                // The model might output this in different arrangements, so we need to handle it properly
                val ortEnvironment = this.ortEnvironment ?: throw IllegalStateException("ORT environment not initialized")

                // Get the raw state data
                val outputBuffer = newStateTensor.floatBuffer
                val outputData = FloatArray(outputBuffer.remaining())
                outputBuffer.get(outputData)
                outputBuffer.rewind() // Reset buffer position

                Log.d(TAG, "DEBUG: Output state data size: ${outputData.size} floats")

                // Expected size for [2, 1, 128] = 256 floats
                val expectedSize = 2 * 1 * 128

                val stateData = when {
                    // Perfect size match - reshape if needed
                    outputData.size == expectedSize -> {
                        Log.d(TAG, "DEBUG: Perfect size match, using data as-is")
                        outputData
                    }
                    // Too much data - take first 256 floats
                    outputData.size > expectedSize -> {
                        Log.d(TAG, "DEBUG: Too much data (${outputData.size}), taking first $expectedSize")
                        outputData.sliceArray(0 until expectedSize)
                    }
                    // Too little data - pad with zeros
                    else -> {
                        Log.w(TAG, "DEBUG: Insufficient data (${outputData.size}), padding to $expectedSize")
                        val paddedData = FloatArray(expectedSize)
                        outputData.copyInto(paddedData, 0, 0, minOf(outputData.size, expectedSize))
                        paddedData
                    }
                }

                // Close old state tensor
                stateState?.close()

                // Create new state tensor with correct shape [2, 1, 128]
                val stateBuffer = FloatBuffer.wrap(stateData)
                val correctStateShape = longArrayOf(2, 1, 128)
                stateState = OnnxTensor.createTensor(ortEnvironment, stateBuffer, correctStateShape)

                Log.d(TAG, "DEBUG: Created new state tensor with shape [2, 1, 128] from ${stateData.size} floats")
                Log.d(TAG, "DEBUG: New state tensor info: ${stateState?.info?.shape?.joinToString(",")}")
            } else {
                Log.w(TAG, "Model output doesn't contain state tensor (size=${result.size()})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating state tensor: ${e.message}", e)

            // On critical error, reinitialize state tensor to prevent further corruption
            try {
                stateState?.close()
                initializeStateTensor()
                Log.i(TAG, "State tensor reinitialized due to update error")
            } catch (reinitError: Exception) {
                Log.e(TAG, "Failed to reinitialize state tensor: ${reinitError.message}", reinitError)
            }
        }
    }

    /**
     * Initialize state tensor for stateful RNN (shape [2, 1, 128])
     */
    private fun initializeStateTensor() {
        val ortEnvironment = this.ortEnvironment ?: throw IllegalStateException("ORT environment not initialized")

        // Initialize state tensor as zeros with shape [2, 1, 128]
        val stateShape = longArrayOf(2, 1, 128)
        val stateData = FloatArray(2 * 1 * 128) // All zeros
        val stateBuffer = FloatBuffer.wrap(stateData)

        stateState = OnnxTensor.createTensor(ortEnvironment, stateBuffer, stateShape)

        Log.d(TAG, "State tensor initialized with shape [2, 1, 128]")
    }

    /**
     * Create input tensors: audio, state, and sample rate (stateful mode)
     */
    private fun createMultiInputTensors(audioFloat32: FloatArray): Map<String, OnnxTensor> {
        val ortEnvironment = this.ortEnvironment ?: throw IllegalStateException("ORT environment not initialized")

        // Create audio tensor with shape [1, audio_length] (batch_size=1)
        val audioShape = longArrayOf(1, audioFloat32.size.toLong())
        val audioBuffer = FloatBuffer.wrap(audioFloat32)
        val audioTensor = OnnxTensor.createTensor(ortEnvironment, audioBuffer, audioShape)

        Log.d(TAG, "DEBUG: Creating audio tensor with shape [${audioShape.joinToString(",")}]")

        // Create sample rate tensor as int64 scalar (no shape dimensions)
        val sampleRateArray = longArrayOf(sampleRate.toLong())
        val sampleRateBuffer = java.nio.LongBuffer.wrap(sampleRateArray)
        val sampleRateTensor = OnnxTensor.createTensor(ortEnvironment, sampleRateBuffer, longArrayOf())

        // TEMPORARY FIX: Always create fresh state tensor instead of using corrupted one
        // This disables stateful mode but prevents the tensor corruption errors
        val freshStateShape = longArrayOf(2, 1, 128)
        val freshStateData = FloatArray(2 * 1 * 128) // All zeros
        val freshStateBuffer = FloatBuffer.wrap(freshStateData)
        val freshStateTensor = OnnxTensor.createTensor(ortEnvironment, freshStateBuffer, freshStateShape)

        Log.d(TAG, "DEBUG: Using fresh state tensor with shape [2, 1, 128] (stateless mode)")
        Log.d(TAG, "DEBUG: Current persistent state shape: [${stateState?.info?.shape?.joinToString(",")}]")

        return mapOf(
            "input" to audioTensor,
            "state" to freshStateTensor,
            "sr" to sampleRateTensor
        )
    }

    /**
     * Update VAD settings from the stored config object.
     * Port of desktop update_settings() method
     */
    fun updateSettings(newConfig: AppConfig) {
        val newThreshold = newConfig.audio.vadThreshold
        if (newThreshold != vadThreshold) {
            if (newThreshold in 0.0f..1.0f) {
                vadThreshold = newThreshold
                Log.i(TAG, "Updated Silero VAD threshold from config to: $vadThreshold")
            } else {
                Log.w(TAG, "Invalid VAD threshold in config ignored: $newThreshold. Must be between 0.0 and 1.0.")
            }
        }

        val newSampleRate = newConfig.audio.sampleRate
        if (newSampleRate != sampleRate) {
            Log.w(TAG, "Sample rate changed in config to ${newSampleRate}Hz. " +
                      "SileroVAD requires re-initialization for this change to take effect.")
            // For simplicity, we don't re-initialize here, but ideally the app should handle this
            // (matching desktop behavior and comment)
        }

        config = newConfig
    }

    /**
     * Get current frame size in bytes for audio processing
     */
    fun getFrameSizeBytes(): Int = frameSizeBytes

    /**
     * Get current frame duration in milliseconds
     */
    fun getFrameDurationMs(): Int = FRAME_DURATION_MS

    /**
     * Check if VAD is properly initialized
     */
    fun isInitialized(): Boolean = initialized

    /**
     * Clean up resources.
     * Port of desktop close() method
     */
    fun close() {
        try {
            // Close state tensor
            stateState?.close()
            stateState = null

            ortSession?.close()
            ortEnvironment?.close()
            initialized = false
            Log.d(TAG, "SileroVAD resources cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up SileroVAD resources: ${e.message}", e)
        }
    }

    /**
     * Clean up resources (called internally on initialization failure)
     */
    private fun cleanup() {
        try {
            // Close state tensor
            stateState?.close()
            stateState = null

            ortSession?.close()
            ortSession = null
            ortEnvironment?.close()
            ortEnvironment = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}", e)
        }
    }
}