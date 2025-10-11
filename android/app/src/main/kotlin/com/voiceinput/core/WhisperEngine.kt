package com.voiceinput.core

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import com.voiceinput.onnx.TensorUtils
import com.voiceinput.onnx.OnnxUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

/**
 * ONNX Runtime-based Whisper engine for Samsung AI chip (APU) acceleration
 *
 * Based on RTranslator's implementation:
 * - Uses split encoder/decoder architecture for optimal memory usage
 * - Implements KV cache for efficient autoregressive decoding
 * - Supports NNAPI acceleration for MediaTek/Qualcomm/Exynos NPUs
 * - INT8 quantized Whisper SMALL model (244M parameters)
 *
 * Performance on Samsung devices with MediaTek APU:
 * - RTF: ~0.44x (faster than real-time)
 * - 11s audio processes in ~4.8s
 */
class WhisperEngine(
    private val context: Context,
    private val language: String = "en"
) {

    companion object {
        private const val TAG = "WhisperEngine"
        private const val SAMPLE_RATE = 16000

        // Whisper token IDs
        private const val START_TOKEN_ID = 50258
        private const val ENGLISH_TOKEN_ID = 50259  // Language token for English
        private const val TRANSCRIBE_TOKEN_ID = 50359
        private const val NO_TIMESTAMPS_TOKEN_ID = 50363
        private const val EOS_TOKEN_ID = 50257

        // Limits
        private const val MAX_TOKENS = 445  // Maximum tokens per transcription
        private const val MAX_TOKENS_PER_SECOND = 30

        // Cache dimensions
        private const val NUM_DECODER_LAYERS = 12
        private const val CACHE_DIM = 64
    }

    private var ortEnvironment: OrtEnvironment? = null
    private var initSession: OrtSession? = null
    private var encoderSession: OrtSession? = null
    private var cacheInitSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private var detokenizerSession: OrtSession? = null

    private var initialized = false

    /**
     * Initialize ONNX Runtime with APU/NNAPI acceleration
     * Compatible interface with old WhisperEngine
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        return@withContext initializeFromAssets("")
    }

    /**
     * Initialize from assets - matches old WhisperEngine API
     */
    @Suppress("UNUSED_PARAMETER") // Kept for API compatibility with old WhisperEngine
    suspend fun initializeFromAssets(assetPath: String): Boolean = withContext(Dispatchers.IO) {
        // Note: assetPath is ignored - ONNX always loads from models/ directory
        // This is just for API compatibility with old WhisperEngine
        try {
            Log.i(TAG, "========================================")
            Log.i(TAG, "🚀 Initializing ONNX Whisper Engine")
            Log.i(TAG, "   Model: Whisper SMALL INT8 (244M params)")
            Log.i(TAG, "========================================")

            // Create ORT environment
            ortEnvironment = OrtEnvironment.getEnvironment()

            // Load all 5 model components
            loadInitSession()
            loadEncoderSession()
            loadCacheInitSession()
            loadDecoderSession()
            loadDetokenizerSession()

            initialized = true

            Log.i(TAG, "")
            Log.i(TAG, "========================================")
            Log.i(TAG, "✅ ONNX Whisper Engine Ready")
            Log.i(TAG, "   Backend: NNAPI (Samsung AI Chip)")
            Log.i(TAG, "   Model: Whisper SMALL INT8")
            Log.i(TAG, "   Expected RTF: ~0.40-0.50x")
            Log.i(TAG, "========================================")

            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize ONNX Whisper", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * Get model info - API compatible
     */
    fun getModelInfo(): ModelInfo {
        return ModelInfo(
            name = "whisper-small-onnx",
            path = "assets/models/",
            language = "en",
            isInitialized = initialized,
            type = "ONNX Runtime (APU accelerated)"
        )
    }

    private fun loadInitSession() {
        val initPath = "models/Whisper_initializer.onnx"
        val sessionOptions = OrtSession.SessionOptions().apply {
            registerCustomOpLibrary(OrtxPackage.getLibraryPath())
            setCPUArenaAllocator(false)
            setMemoryPatternOptimization(false)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
        }

        context.assets.open(initPath).use { inputStream ->
            initSession = ortEnvironment!!.createSession(inputStream.readBytes(), sessionOptions)
        }
        Log.i(TAG, "✅ Initializer model loaded")
    }

    private fun loadEncoderSession() {
        val encoderPath = "models/Whisper_encoder.onnx"
        val sessionOptions = OrtSession.SessionOptions().apply {
            registerCustomOpLibrary(OrtxPackage.getLibraryPath())

            // Memory optimization based on device RAM
            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.maxMemory() / (1024 * 1024) // MB

            if (totalMemory <= 7000) {
                // Low RAM devices - disable arena allocator
                setCPUArenaAllocator(false)
                setMemoryPatternOptimization(false)
                Log.i(TAG, "   Low RAM mode enabled for encoder")
            } else {
                // High RAM devices - enable optimizations
                setCPUArenaAllocator(true)
                setMemoryPatternOptimization(true)
            }

            setSymbolicDimensionValue("batch_size", 1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)

            // Try to enable NNAPI for APU acceleration
            try {
                addNnapi()
                Log.i(TAG, "   ⚡ NNAPI (APU) acceleration enabled for encoder")
            } catch (e: Exception) {
                Log.w(TAG, "   NNAPI not available, using CPU for encoder")
            }
        }

        context.assets.open(encoderPath).use { inputStream ->
            encoderSession = ortEnvironment!!.createSession(inputStream.readBytes(), sessionOptions)
        }
        Log.i(TAG, "✅ Encoder model loaded")
    }

    private fun loadCacheInitSession() {
        val cachePath = "models/Whisper_cache_initializer.onnx"
        val sessionOptions = OrtSession.SessionOptions().apply {
            registerCustomOpLibrary(OrtxPackage.getLibraryPath())
            setCPUArenaAllocator(false)
            setMemoryPatternOptimization(false)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
        }

        context.assets.open(cachePath).use { inputStream ->
            cacheInitSession = ortEnvironment!!.createSession(inputStream.readBytes(), sessionOptions)
        }
        Log.i(TAG, "✅ Cache initializer model loaded")
    }

    private fun loadDecoderSession() {
        val decoderPath = "models/Whisper_decoder.onnx"
        val sessionOptions = OrtSession.SessionOptions().apply {
            registerCustomOpLibrary(OrtxPackage.getLibraryPath())
            setCPUArenaAllocator(false)
            setMemoryPatternOptimization(false)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
        }

        context.assets.open(decoderPath).use { inputStream ->
            decoderSession = ortEnvironment!!.createSession(inputStream.readBytes(), sessionOptions)
        }
        Log.i(TAG, "✅ Decoder model loaded")
    }

    private fun loadDetokenizerSession() {
        val detokenizerPath = "models/Whisper_detokenizer.onnx"
        val sessionOptions = OrtSession.SessionOptions().apply {
            registerCustomOpLibrary(OrtxPackage.getLibraryPath())
            setCPUArenaAllocator(false)
            setMemoryPatternOptimization(false)
        }

        context.assets.open(detokenizerPath).use { inputStream ->
            detokenizerSession = ortEnvironment!!.createSession(inputStream.readBytes(), sessionOptions)
        }
        Log.i(TAG, "✅ Detokenizer model loaded")
    }

    /**
     * Transcribe audio using ONNX Runtime with APU acceleration
     *
     * @param audioData PCM audio samples (16kHz, mono, 16-bit little-endian)
     * @return TranscriptionResult with text and performance metrics
     */
    suspend fun transcribe(audioData: ByteArray): TranscriptionResult = withContext(Dispatchers.IO) {
        require(initialized) { "Engine not initialized. Call initialize() first." }

        val startTime = System.currentTimeMillis()
        val audioDurationSec = audioData.size / (SAMPLE_RATE * 2).toFloat()

        // Safety check for very large audio chunks
        if (audioDurationSec > 30.0f) {
            Log.w(TAG, "⚠️ Large audio chunk detected: ${audioDurationSec}s. This may cause memory issues.")
        }

        Log.i(TAG, "")
        Log.i(TAG, "========================================")
        Log.i(TAG, "🎙️  STARTING TRANSCRIPTION")
        Log.i(TAG, "   Audio: ${audioDurationSec}s (${audioData.size} bytes)")
        Log.i(TAG, "========================================")

        // Declare all resources that need cleanup
        var audioTensor: OnnxTensor? = null
        var initOutputs: OrtSession.Result? = null
        var encoderOutputs: OrtSession.Result? = null
        var cacheInitResult: OrtSession.Result? = null

        try {
            // Step 1: Convert PCM to float array
            val audioFloats = TensorUtils.convertPcmToFloatArray(audioData)

            // Step 2: Run initializer (audio preprocessing + mel-spectrogram)
            Log.d(TAG, "Step 1: Running audio preprocessor...")
            val preOpTime = System.currentTimeMillis()
            audioTensor = OnnxTensor.createTensor(
                ortEnvironment!!,
                FloatBuffer.wrap(audioFloats),
                longArrayOf(1, audioFloats.size.toLong())
            )

            val initInputs = mapOf("audio_pcm" to audioTensor)
            initOutputs = initSession!!.run(initInputs)
            val melSpectrogram = initOutputs[0] as OnnxTensor
            val preOpDuration = System.currentTimeMillis() - preOpTime
            Log.i(TAG, "   Preprocessing: ${preOpDuration}ms")

            // Step 3: Run encoder (this is where APU acceleration shines!)
            Log.d(TAG, "Step 2: Running encoder on APU...")
            val encodeTime = System.currentTimeMillis()
            val encoderInputs = mapOf("input_features" to melSpectrogram)
            var encodeDuration: Long
            
            try {
                encoderOutputs = encoderSession!!.run(encoderInputs)
                encodeDuration = System.currentTimeMillis() - encodeTime
                Log.i(TAG, "   ⚡ Encoding: ${encodeDuration}ms")
            } catch (e: Exception) {
                Log.e(TAG, "❌ APU encoder failed: ${e.message}", e)
                Log.i(TAG, "🔄 Attempting CPU fallback...")
                
                // Create CPU-only encoder session as fallback
                val encoderPath = "models/Whisper_encoder.onnx"
                val sessionOptions = OrtSession.SessionOptions().apply {
                    registerCustomOpLibrary(OrtxPackage.getLibraryPath())
                    setCPUArenaAllocator(false) // Disable arena allocator for stability
                    setMemoryPatternOptimization(false) // Disable memory optimization
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                    setSymbolicDimensionValue("batch_size", 1)
                    // Explicitly avoid NNAPI for CPU fallback
                }

                val cpuEncoderSession = ortEnvironment!!.createSession(
                    context.assets.open(encoderPath).readBytes(),
                    sessionOptions
                )

                // Run encoder on CPU
                val cpuEncodeTime = System.currentTimeMillis()
                encoderOutputs = cpuEncoderSession.run(encoderInputs)
                encodeDuration = System.currentTimeMillis() - cpuEncodeTime
                Log.i(TAG, "   🖥️ CPU Encoding: ${encodeDuration}ms")
                
                // Cleanup CPU session
                cpuEncoderSession.close()
            }
            
            // Extract encoder hidden states (guaranteed non-null: set by either APU or CPU encoder above)
            val encoderHiddenStates = encoderOutputs!!.get(0) as OnnxTensor

            // Step 4: Initialize KV cache
            Log.d(TAG, "Step 3: Initializing KV cache...")
            val cacheTime = System.currentTimeMillis()
            val cacheInitInputs = mapOf("encoder_hidden_states" to encoderHiddenStates)
            cacheInitResult = cacheInitSession!!.run(cacheInitInputs)
            val cacheDuration = System.currentTimeMillis() - cacheTime
            Log.i(TAG, "   Cache init: ${cacheDuration}ms")

            // Step 5: Autoregressive decoding
            Log.d(TAG, "Step 4: Running autoregressive decoder...")
            val decodeTime = System.currentTimeMillis()
            val tokens = runAutoregressiveDecoder(cacheInitResult, audioDurationSec)
            val decodeDuration = System.currentTimeMillis() - decodeTime
            Log.i(TAG, "   Decoding: ${decodeDuration}ms (${tokens.size} tokens)")

            // Step 6: Detokenize to text
            Log.d(TAG, "Step 5: Detokenizing...")
            val text = detokenize(tokens)

            val totalDuration = System.currentTimeMillis() - startTime
            val rtf = totalDuration / (audioDurationSec * 1000)

            Log.i(TAG, "")
            Log.i(TAG, "========================================")
            Log.i(TAG, "✅ TRANSCRIPTION COMPLETE")
            Log.i(TAG, "========================================")
            Log.i(TAG, "   Audio duration:  ${audioDurationSec}s")
            Log.i(TAG, "   Preprocessing:   ${preOpDuration}ms")
            Log.i(TAG, "   Encoding:        ${encodeDuration}ms")
            Log.i(TAG, "   Cache init:      ${cacheDuration}ms")
            Log.i(TAG, "   Decoding:        ${decodeDuration}ms")
            Log.i(TAG, "   Total time:      ${totalDuration}ms")
            Log.i(TAG, "   RTF:             ${"%.2f".format(rtf)}x")
            Log.i(TAG, "   Text:            \"$text\"")
            Log.i(TAG, "========================================")

            return@withContext TranscriptionResult(
                text = text.trim(),
                language = "en",
                segments = emptyList(),
                confidence = 1.0f,
                processingTimeMs = totalDuration
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Transcription failed", e)
            e.printStackTrace()
            throw Exception("Transcription failed: ${e.message}", e)
        } finally {
            // Guaranteed cleanup - close each resource independently
            try { audioTensor?.close() } catch (e: Exception) { Log.e(TAG, "Error closing audioTensor", e) }
            try { initOutputs?.close() } catch (e: Exception) { Log.e(TAG, "Error closing initOutputs", e) }
            try { encoderOutputs?.close() } catch (e: Exception) { Log.e(TAG, "Error closing encoderOutputs", e) }
            try { cacheInitResult?.close() } catch (e: Exception) { Log.e(TAG, "Error closing cacheInitResult", e) }
        }
    }

    /**
     * Run autoregressive decoder with KV caching
     * Implements the same algorithm as RTranslator's Recognizer.java
     */
    private fun runAutoregressiveDecoder(
        cacheInitResult: OrtSession.Result,
        audioDurationSec: Float
    ): IntArray {
        val env = ortEnvironment!!
        val tokens = mutableListOf<Int>()

        // Calculate max tokens based on audio duration
        val maxTokens = ((audioDurationSec * MAX_TOKENS_PER_SECOND).toInt()).coerceAtMost(MAX_TOKENS)

        // Initial decoder tokens: <|startoftranscript|>, <|en|>, <|transcribe|>, <|notimestamps|>
        val initialTokens = intArrayOf(
            START_TOKEN_ID,
            ENGLISH_TOKEN_ID,
            TRANSCRIBE_TOKEN_ID,
            NO_TIMESTAMPS_TOKEN_ID
        )

        var currentToken = -1  // Start with invalid token
        var iteration = 1
        var isFirstIteration = true
        var previousResult: OrtSession.Result? = null

        // Continue until EOS token is generated (but always process initial 4 tokens)
        while (iteration <= 4 || currentToken != EOS_TOKEN_ID) {
            // Prepare input token(s)
            val inputIdTensor = if (iteration <= 4) {
                TensorUtils.convertIntArrayToTensor(env, intArrayOf(initialTokens[iteration - 1]))
            } else {
                TensorUtils.convertIntArrayToTensor(env, intArrayOf(currentToken))
            }

            // Prepare decoder inputs
            val decoderInputs = mutableMapOf<String, OnnxTensor>()
            decoderInputs["input_ids"] = inputIdTensor

            if (isFirstIteration) {
                // First iteration - use empty decoder cache
                val emptyCache = TensorUtils.createFloatTensorWithSingleValue(
                    env, 0f, longArrayOf(1, NUM_DECODER_LAYERS.toLong(), 0, CACHE_DIM.toLong())
                )

                for (i in 0 until NUM_DECODER_LAYERS) {
                    decoderInputs["past_key_values.$i.decoder.key"] = emptyCache
                    decoderInputs["past_key_values.$i.decoder.value"] = emptyCache
                    decoderInputs["past_key_values.$i.encoder.key"] =
                        cacheInitResult.get("present.$i.encoder.key").get() as OnnxTensor
                    decoderInputs["past_key_values.$i.encoder.value"] =
                        cacheInitResult.get("present.$i.encoder.value").get() as OnnxTensor
                }
                isFirstIteration = false
            } else {
                // Subsequent iterations - use previous decoder cache
                // previousResult is guaranteed non-null here (set in previous iteration)
                @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
                val prevResult = previousResult!!
                for (i in 0 until NUM_DECODER_LAYERS) {
                    decoderInputs["past_key_values.$i.decoder.key"] =
                        prevResult.get("present.$i.decoder.key").get() as OnnxTensor
                    decoderInputs["past_key_values.$i.decoder.value"] =
                        prevResult.get("present.$i.decoder.value").get() as OnnxTensor
                    decoderInputs["past_key_values.$i.encoder.key"] =
                        cacheInitResult.get("present.$i.encoder.key").get() as OnnxTensor
                    decoderInputs["past_key_values.$i.encoder.value"] =
                        cacheInitResult.get("present.$i.encoder.value").get() as OnnxTensor
                }
            }

            // Run decoder
            val result = decoderSession!!.run(decoderInputs)

            // Clean up previous result to free memory
            previousResult?.close()
            previousResult = result

            // Extract logits and find most likely token
            val logitsTensor = result.get("logits").get() as OnnxTensor
            @Suppress("UNCHECKED_CAST") // ONNX model guarantees this shape
            val logits = logitsTensor.value as Array<Array<FloatArray>>
            val outputLogits = logits[0][0]

            currentToken = OnnxUtils.getIndexOfLargest(outputLogits)

            // ✅ FIX: Collect ALL non-special tokens from any iteration
            // The decoder outputs tokens at EVERY iteration, not just after forced prompts.
            // Previously we skipped iterations 1-4, which threw away the first word!
            val isSpecialToken = currentToken == START_TOKEN_ID || 
                                 currentToken == ENGLISH_TOKEN_ID || 
                                 currentToken == TRANSCRIBE_TOKEN_ID || 
                                 currentToken == NO_TIMESTAMPS_TOKEN_ID
            
            if (!isSpecialToken && currentToken != EOS_TOKEN_ID) {
                tokens.add(currentToken)
            }

            // Safety check - prevent infinite loops
            if (iteration >= maxTokens) {
                Log.w(TAG, "   Max tokens reached ($maxTokens), stopping")
                break
            }

            iteration++
        }

        previousResult?.close()

        return tokens.toIntArray()
    }

    /**
     * Convert tokens to text using detokenizer model
     */
    private fun detokenize(tokens: IntArray): String {
        if (tokens.isEmpty()) {
            return ""
        }

        try {
            val shape = longArrayOf(1, 1, tokens.size.toLong())
            val sequencesTensor = TensorUtils.createInt32Tensor(ortEnvironment!!, tokens, shape)

            val detokenizerInputs = mapOf("sequences" to sequencesTensor)
            val detokenizerOutputs = detokenizerSession!!.run(detokenizerInputs)

            @Suppress("UNCHECKED_CAST") // ONNX detokenizer guarantees string output
            val textResult = detokenizerOutputs.get(0).value as Array<Array<String>>
            val rawText = textResult[0][0]

            sequencesTensor.close()
            detokenizerOutputs.close()

            // Clean up text (remove timestamps, trim, capitalize)
            return correctText(rawText)

        } catch (e: OrtException) {
            Log.e(TAG, "Detokenization failed", e)
            return ""
        }
    }

    /**
     * Clean up transcribed text
     * - Remove timestamp markers
     * - Trim whitespace
     * - Capitalize first letter
     * - Remove ellipsis
     */
    private fun correctText(text: String): String {
        var corrected = text

        // Remove timestamps like <|0.00|>
        corrected = corrected.replace(Regex("<\\|[^>]*\\|>\\s*"), "")

        // Trim whitespace
        corrected = corrected.trim()

        if (corrected.length >= 2) {
            // Capitalize first letter
            if (corrected[0].isLowerCase()) {
                corrected = corrected[0].uppercase() + corrected.substring(1)
            }

            // Remove ellipsis
            corrected = corrected.replace("...", "")
        }

        return corrected
    }

    /**
     * Release all resources
     */
    fun release() {
        try {
            Log.i(TAG, "Releasing ONNX Whisper Engine...")

            initSession?.close()
            encoderSession?.close()
            cacheInitSession?.close()
            decoderSession?.close()
            detokenizerSession?.close()
            ortEnvironment?.close()

            initSession = null
            encoderSession = null
            cacheInitSession = null
            decoderSession = null
            detokenizerSession = null
            ortEnvironment = null

            initialized = false

            Log.i(TAG, "✅ ONNX Whisper Engine released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources", e)
        }
    }
}

/**
 * Result of transcription
 */
data class TranscriptionResult(
    val text: String,
    val language: String,
    val segments: List<TranscriptionSegment> = emptyList(),
    val confidence: Float = 1.0f,
    val processingTimeMs: Long = 0
)

/**
 * Transcription segment with timestamps
 */
data class TranscriptionSegment(
    val text: String,
    val startTime: Float,
    val endTime: Float,
    val confidence: Float = 1.0f
)

/**
 * Model information
 */
data class ModelInfo(
    val name: String,
    val path: String,
    val language: String,
    val isInitialized: Boolean,
    val type: String = "ONNX Runtime"
)
