package com.voiceinput.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.voiceinput.core.AudioRecorder
import com.voiceinput.core.WhisperEngine
import com.voiceinput.core.TextProcessor
import com.voiceinput.config.ConfigRepository
import com.voiceinput.config.PreferencesManager
import com.voiceinput.config.InputMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log

/**
 * Voice Input IME (Input Method Editor)
 * 
 * A system keyboard that uses speech recognition instead of typing.
 * Implements best practices:
 * - Proper lifecycle management (LifecycleOwner)
 * - Coroutine scope management (avoid leaks)
 * - Memory-efficient resource handling
 * - Graceful error handling
 * - User-friendly feedback
 * 
 * Architecture:
 * - VoiceInputIME (this) - IME service, lifecycle, & orchestration
 * - VoiceKeyboardView - UI & user interactions
 * - AudioRecorder - Microphone capture
 * - WhisperEngine - Speech-to-text transcription
 */
class VoiceInputIME : InputMethodService(), LifecycleOwner {

    companion object {
        private const val TAG = "VoiceInputIME"
        private const val MAX_RECORDING_DURATION_MS = 60_000L  // 60 seconds max (was 30s)
        private const val MAX_BUFFER_SIZE_BYTES = 20 * 1024 * 1024  // 20 MB max (was 10MB)
        private const val WARNING_DURATION_MS = 50_000L  // Warn at 50 seconds
    }

    // Lifecycle management for coroutines
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    // Coroutine scope with proper lifecycle binding
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Voice processing components (lazy init to save memory)
    private var audioRecorder: AudioRecorder? = null
    private var whisperEngine: WhisperEngine? = null
    private val textProcessor = TextProcessor()  // For filtering hallucinations

    // Configuration
    private lateinit var preferencesManager: PreferencesManager

    // UI
    private var keyboardView: VoiceKeyboardView? = null

    // State
    private var isInitialized = false
    private var isRecording = false
    private var isTapMode = true  // Will be loaded from preferences

    // Thread-safe audio buffer with size limits
    private val audioBufferMutex = kotlinx.coroutines.sync.Mutex()
    private val audioBuffer = mutableListOf<ByteArray>()
    private var recordingJob: kotlinx.coroutines.Job? = null
    private var recordingStartTime: Long = 0
    private var timerJob: kotlinx.coroutines.Job? = null  // For duration display

    // ============================================================================
    // IME Lifecycle Methods
    // ============================================================================

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        Log.i(TAG, "IME service created")

        // Initialize preferences manager
        preferencesManager = PreferencesManager(this)

        // Load saved mode preference
        isTapMode = (preferencesManager.defaultMode == InputMode.TAP)
        Log.i(TAG, "Loaded saved mode: ${if (isTapMode) "TAP" else "HOLD"}")
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        
        Log.i(TAG, "Creating keyboard view")
        
        // ✅ FIX: Reuse existing view if available
        if (keyboardView == null) {
            keyboardView = VoiceKeyboardView(this, preferencesManager).apply {
                onMicrophonePressed = { handleMicrophonePressed() }
                onMicrophoneReleased = { handleMicrophoneReleased() }
                onMicrophoneClicked = { handleMicrophoneClicked() }
                onCancelPressed = { handleCancelPressed() }
                onModeToggled = { tapMode -> handleModeToggle(tapMode) }
                onHapticChanged = { enabled -> handleHapticChanged(enabled) }
                onSensitivityChanged = { sensitivity -> handleSensitivityChanged(sensitivity) }
                setInputMode(isTapMode)
            }
        }

        // Initialize components if not already done
        if (!isInitialized) {
            initializeComponents()
        } else {
            // Already initialized - update view state
            keyboardView?.showReadyState()
        }

        return keyboardView!!
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Log.d(TAG, "Input started - inputType: ${attribute?.inputType}, restarting: $restarting")
        
        // Update keyboard view based on text field type
        updateKeyboardForInputType(attribute)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        Log.d(TAG, "Input finished")
        
        // ✅ FIX: Cancel recording instead of stopping (don't transcribe garbage)
        if (isRecording) {
            Log.i(TAG, "Text field closed during recording - cancelling")
            cancelRecording()
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "IME service destroyed")
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        
        // Clean up resources
        cleanup()
        serviceScope.cancel()
        
        super.onDestroy()
    }

    // ============================================================================
    // Component Initialization
    // ============================================================================

    /**
     * Initialize voice processing components
     * Heavy initialization done asynchronously to avoid blocking UI
     */
    private fun initializeComponents() {
        serviceScope.launch {
            try {
                keyboardView?.showStatus("Initializing voice recognition...")
                keyboardView?.setMicrophoneEnabled(false)
                
                // ✅ FIX: Show animated loading
                var dots = 0
                val loadingJob = serviceScope.launch {
                    while (true) {
                        kotlinx.coroutines.delay(500)
                        dots = (dots + 1) % 4
                        val dotsStr = ".".repeat(dots)
                        keyboardView?.showStatus("Initializing$dotsStr")
                    }
                }
                
                val config = ConfigRepository(this@VoiceInputIME).load()
                
                // Initialize audio recorder
                audioRecorder = AudioRecorder(
                    sampleRate = config.audio.sampleRate,
                    chunkSize = config.audio.chunkSize,
                    channels = config.audio.channels
                ).apply {
                    initialize()
                }
                
                // Initialize Whisper engine (heavy - do in background)
                whisperEngine = WhisperEngine(
                    context = this@VoiceInputIME,
                    language = config.transcription.language
                ).apply {
                    initializeFromAssets("models/")
                }
                
                // Stop loading animation
                loadingJob.cancel()
                
                isInitialized = true
                keyboardView?.showStatus("Ready to speak!")
                keyboardView?.setMicrophoneEnabled(true)
                
                Log.i(TAG, "✅ Voice components initialized successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to initialize voice components", e)
                keyboardView?.showError("Initialization failed: ${e.message}")
                isInitialized = false
            }
        }
    }

    /**
     * Clean up resources to prevent memory leaks
     */
    private fun cleanup() {
        Log.d(TAG, "Cleaning up resources")
        
        try {
            audioRecorder?.stop()
            audioRecorder?.release()
            // Note: WhisperEngine doesn't have release() method yet
            // whisperEngine?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
        
        whisperEngine = null
        audioRecorder = null
        audioBuffer.clear()
        isInitialized = false
    }

    // ============================================================================
    // User Interaction Handlers
    // ============================================================================

    private fun handleMicrophonePressed() {
        if (!isInitialized) {
            keyboardView?.showError("Voice recognition not ready")
            return
        }

        if (isRecording) {
            Log.w(TAG, "Already recording, ignoring microphone press")
            return
        }

        startRecording()
    }

    private fun handleMicrophoneReleased() {
        if (isRecording) {
            stopRecording()
        }
    }

    private fun handleMicrophoneClicked() {
        // Tap mode - toggle recording on/off
        if (isRecording) {
            // Already recording, stop it
            stopRecording()
        } else {
            // Not recording, start it
            startRecording()
        }
    }

    private fun handleCancelPressed() {
        if (isRecording) {
            cancelRecording()
        }
    }

    private fun handleModeToggle(tapMode: Boolean) {
        isTapMode = tapMode
        Log.i(TAG, "Input mode changed to: ${if (isTapMode) "Tap" else "Hold"}")

        // Save preference
        val mode = if (tapMode) InputMode.TAP else InputMode.HOLD
        preferencesManager.defaultMode = mode
    }

    private fun handleHapticChanged(enabled: Boolean) {
        Log.i(TAG, "Haptic feedback ${if (enabled) "enabled" else "disabled"}")
        // Preference already saved by SettingsDrawerView
        // Could update runtime behavior here if needed
    }

    private fun handleSensitivityChanged(sensitivity: Float) {
        Log.i(TAG, "Visualizer sensitivity changed to: $sensitivity")
        // Preference already saved by SettingsDrawerView
        // Could update AudioVisualizerView settings here if needed
        // For now, sensitivity is applied on next recording
    }

    // ============================================================================
    // Recording Control
    // ============================================================================

    private fun startRecording() {
        Log.i(TAG, "Starting recording")

        // Validate audio recorder is ready
        val recorder = audioRecorder
        if (recorder == null) {
            keyboardView?.showError("Audio not initialized")
            return
        }

        // Set state and update UI immediately (before coroutine)
        isRecording = true
        recordingStartTime = System.currentTimeMillis()
        keyboardView?.showRecordingState()

        serviceScope.launch {
            try {
                // Clear previous audio safely
                audioBufferMutex.withLock {
                    audioBuffer.clear()
                }
                
                // Start audio recording
                if (!recorder.start()) {
                    throw Exception("Failed to start AudioRecorder")
                }
                
                // ✅ Start duration timer display
                timerJob = serviceScope.launch {
                    while (isRecording) {
                        val elapsed = (System.currentTimeMillis() - recordingStartTime) / 1000
                        val remaining = (MAX_RECORDING_DURATION_MS / 1000) - elapsed

                        if (elapsed >= (WARNING_DURATION_MS / 1000)) {
                            // Show warning in last 10 seconds
                            keyboardView?.showStatus("Recording • ${remaining}s left")
                        } else {
                            // Show elapsed time
                            keyboardView?.showStatus("Recording • ${elapsed}s")
                        }

                        kotlinx.coroutines.delay(1000)
                    }
                }
                
                // Collect audio chunks in background with safety checks
                recordingJob = serviceScope.launch {
                    try {
                        recorder.audioStream().collect { chunk ->
                            // Check recording limits
                            val elapsed = System.currentTimeMillis() - recordingStartTime

                            if (elapsed > MAX_RECORDING_DURATION_MS) {
                                Log.w(TAG, "Maximum recording duration reached (60s)")
                                keyboardView?.showError("Max duration reached (60s)")
                                stopRecording()
                                return@collect
                            }

                            // Thread-safe buffer access with size limit
                            audioBufferMutex.withLock {
                                if (isRecording) {
                                    val currentSize = audioBuffer.sumOf { it.size }
                                    if (currentSize + chunk.size > MAX_BUFFER_SIZE_BYTES) {
                                        Log.w(TAG, "Maximum buffer size reached (10MB)")
                                        stopRecording()
                                        return@collect
                                    }
                                    audioBuffer.add(chunk)

                                    // Update visualizer with audio data
                                    keyboardView?.updateAudioLevel(chunk)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // JobCancellationException is normal when stopping - not an error
                        if (e is kotlinx.coroutines.CancellationException) {
                            Log.d(TAG, "Audio collection cancelled (normal)")
                        } else {
                            Log.e(TAG, "Error collecting audio", e)
                            serviceScope.launch {
                                keyboardView?.showError("Recording error")
                                isRecording = false
                            }
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording", e)
                keyboardView?.showError("Can't start recording")
                isRecording = false
                audioBufferMutex.withLock {
                    audioBuffer.clear()
                }
            }
        }
    }

    private fun stopRecording() {
        serviceScope.launch {
            try {
                Log.i(TAG, "Stopping recording")
                isRecording = false
                
                keyboardView?.showProcessingState()
                
                // Stop audio recording and timer
                audioRecorder?.stop()
                recordingJob?.cancel()
                timerJob?.cancel()
                
                // Small delay to ensure last chunks are collected
                kotlinx.coroutines.delay(100)
                
                // Get recorded audio
                val audioData = combineAudioChunks()
                
                if (audioData.isEmpty()) {
                    Log.w(TAG, "No audio data collected")
                    keyboardView?.showError("No audio recorded")
                    keyboardView?.showReadyState()
                    return@launch
                }
                
                Log.i(TAG, "Transcribing ${audioData.size} bytes of audio (${audioBuffer.size} chunks)")
                
                // Transcribe
                val result = whisperEngine?.transcribe(audioData)

                if (result != null && result.text.isNotEmpty()) {
                    // Filter hallucinations and clean text
                    val filteredText = textProcessor.filterHallucinations(result.text)

                    if (filteredText.isEmpty() || !textProcessor.isValidUtterance(filteredText)) {
                        // Filtered text is empty or invalid (e.g., just "[BLANK_AUDIO]")
                        keyboardView?.showError("No speech detected")
                        Log.i(TAG, "Transcription filtered out as hallucination: ${result.text}")
                    } else {
                        insertText(filteredText)
                        keyboardView?.showSuccess("✓")
                        Log.i(TAG, "Transcription successful: $filteredText")
                    }
                } else {
                    keyboardView?.showError("No speech detected")
                    Log.w(TAG, "Transcription returned empty")
                }
                
                // Clear buffer
                audioBuffer.clear()
                keyboardView?.showReadyState()
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop recording", e)
                keyboardView?.showError("Error: ${e.message}")
                audioBuffer.clear()
                keyboardView?.showReadyState()
            }
        }
    }

    private fun cancelRecording() {
        serviceScope.launch {
            Log.i(TAG, "Cancelling recording")
            
        try {
            audioRecorder?.stop()
            recordingJob?.cancel()
            timerJob?.cancel()
            isRecording = false
            
            // Clear buffer safely
            audioBufferMutex.withLock {
                audioBuffer.clear()
            }
            
            keyboardView?.showReadyState()
            keyboardView?.showStatus("Cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling recording", e)
        }
        }
    }

    /**
     * Combine all buffered audio chunks into a single ByteArray
     * Thread-safe access to audio buffer
     */
    private suspend fun combineAudioChunks(): ByteArray {
        return audioBufferMutex.withLock {
            if (audioBuffer.isEmpty()) {
                return@withLock ByteArray(0)
            }
            
            // Calculate total size
            val totalSize = audioBuffer.sumOf { it.size }
            
            // Combine all chunks
            val combined = ByteArray(totalSize)
            var offset = 0
            
            for (chunk in audioBuffer) {
                chunk.copyInto(combined, offset)
                offset += chunk.size
            }
            
            Log.d(TAG, "Combined ${audioBuffer.size} chunks into ${combined.size} bytes")
            return@withLock combined
        }
    }

    // ============================================================================
    // Text Insertion
    // ============================================================================

    /**
     * Insert text into the current text field
     * Handles the InputConnection properly with batch edits and validation
     */
    private fun insertText(text: String) {
        if (text.isBlank()) {
            Log.w(TAG, "Attempted to insert blank text")
            return
        }

        val ic: InputConnection? = currentInputConnection
        
        if (ic == null) {
            Log.w(TAG, "No input connection available")
            keyboardView?.showError("Cannot insert text")
            return
        }

        try {
            // Validate InputConnection is still alive
            // getTextBeforeCursor with 0 length will fail if connection is stale
            val testText = ic.getTextBeforeCursor(0, 0)
            if (testText == null) {
                Log.w(TAG, "InputConnection is stale/disconnected")
                keyboardView?.showError("Connection lost")
                return
            }

            // Use batch edit for better performance
            ic.beginBatchEdit()
            
            // Insert the text at cursor position
            // newCursorPosition=1 means move cursor to end of inserted text
            ic.commitText(text, 1)
            
            ic.endBatchEdit()
            
            Log.i(TAG, "✅ Text inserted successfully: \"$text\"")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert text", e)
            keyboardView?.showError("Insert failed")
        }
    }

    // ============================================================================
    // Keyboard Adaptation
    // ============================================================================

    /**
     * Update keyboard behavior based on text field type
     * Different fields may have different requirements (e.g., password fields)
     */
    private fun updateKeyboardForInputType(editorInfo: EditorInfo?) {
        if (editorInfo == null) return

        // Check if it's a password field
        val isPassword = (editorInfo.inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0 ||
                        (editorInfo.inputType and android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) != 0

        if (isPassword) {
            Log.d(TAG, "Password field detected - voice input may not be appropriate")
            keyboardView?.showWarning("Voice input for passwords may not be secure")
        }

        // Check if it's a single line field
        val isSingleLine = (editorInfo.inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0

        keyboardView?.setInputTypeInfo(isPassword, isSingleLine)
    }
}

