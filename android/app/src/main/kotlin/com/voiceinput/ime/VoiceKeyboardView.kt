package com.voiceinput.ime

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.voiceinput.R
import com.voiceinput.config.InputMode
import com.voiceinput.config.PreferencesManager
import android.widget.FrameLayout

/**
 * Custom keyboard view for voice input IME
 *
 * Design principles:
 * - Material Design 3 aesthetics
 * - Clear visual feedback for all states
 * - Accessible (large touch targets, clear labels)
 * - Minimal but functional
 *
 * States:
 * - Ready: Default state, ready to record
 * - Recording: Active recording, pulsing animation
 * - Processing: Transcribing audio
 * - Error: Show error message
 * - Success: Brief feedback after insertion
 *
 * Settings:
 * - Inline drawer (slide-up) for quick settings
 * - No app switching for common preferences
 */
class VoiceKeyboardView(
    context: Context,
    private val preferencesManager: PreferencesManager
) : FrameLayout(context) {

    // Callbacks to IME
    var onMicrophonePressed: (() -> Unit)? = null
    var onMicrophoneReleased: (() -> Unit)? = null
    var onMicrophoneClicked: (() -> Unit)? = null  // For tap mode
    var onCancelPressed: (() -> Unit)? = null
    var onModeToggled: ((Boolean) -> Unit)? = null  // Callback for mode change (tapMode)
    var onHapticChanged: ((Boolean) -> Unit)? = null
    var onSensitivityChanged: ((Float) -> Unit)? = null
    var onOpenAppSettings: (() -> Unit)? = null
    var onSpacePressed: (() -> Unit)? = null
    var onBackspacePressed: (() -> Unit)? = null

    // UI Components
    private val mainKeyboardLayout: LinearLayout  // Main keyboard content
    private var settingsDrawer: SettingsDrawerView? = null  // Settings panel
    private val statusText: TextView
    private val microphoneButton: Button
    private val audioVisualizer: AudioVisualizerView  // Audio waveform visualizer
    private val cancelIcon: TextView
    private val tapModeButton: Button
    private val holdModeButton: Button
    private val settingsButton: Button
    private val previewText: TextView
    private val instructionText: TextView

    private val readyGradientColors = intArrayOf(Color.parseColor("#5B86E5"), Color.parseColor("#36D1DC"))
    private val recordingGradientColors = intArrayOf(Color.parseColor("#FDC830"), Color.parseColor("#F37335"))
    private val processingGradientColors = intArrayOf(Color.parseColor("#8E2DE2"), Color.parseColor("#4A00E0"))
    private val recordingAccentColor = Color.parseColor("#FFE082")
    private val processingAccentColor = Color.parseColor("#B39DDB")

    // Mode
    private var isTapMode: Boolean = true  // Will be loaded from preferences
    private var isCurrentlyRecording: Boolean = false  // Track recording state for tap mode

    // Haptic feedback
    private val vibrator: Vibrator? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        context.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    // State
    private var currentState: KeyboardState = KeyboardState.READY

    enum class KeyboardState {
        READY,
        RECORDING,
        PROCESSING,
        ERROR,
        SUCCESS
    }

    init {
        // Main keyboard layout (vertical linear layout)
        mainKeyboardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // Use cosmos gradient background instead of white
            setBackgroundResource(R.drawable.cosmos_gradient)
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(16))
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }

        // Status/Preview area at top
        previewText = TextView(context).apply {
            text = ""
            textSize = 14f
            setTextColor(Color.parseColor("#B0B0B0"))  // Lighter for dark background
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                dpToPx(40)
            ).apply {
                bottomMargin = dpToPx(8)
            }
            visibility = View.GONE  // Hidden by default
        }
        mainKeyboardLayout.addView(previewText)

        // Status text (for recording time and status)
        statusText = TextView(context).apply {
            text = "Initializing..."
            textSize = 14f
            setTextColor(Color.parseColor("#FFFFFF"))  // White for dark background
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(8)
            }
        }
        mainKeyboardLayout.addView(statusText)

        // Main recording area - horizontal layout with button + visualizer
        val recordingArea = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                dpToPx(80)
            ).apply {
                bottomMargin = dpToPx(12)
            }
        }

        // Microphone button (main action) - calmer comet-themed control
        microphoneButton = Button(context).apply {
            text = "☄"
            textSize = 28f
            setTextColor(Color.WHITE)
            isAllCaps = false
            setPadding(0, 0, 0, 0)
            isEnabled = false  // Disabled until initialized
            layoutParams = LayoutParams(
                dpToPx(60),
                dpToPx(60)
            ).apply {
                marginEnd = dpToPx(12)
            }

            // Touch listener for press/release with haptic feedback
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (isEnabled && !isTapMode) {
                            performHapticFeedback()
                            onMicrophonePressed?.invoke()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isEnabled) {
                            performHapticFeedback()
                            if (isTapMode) {
                                onMicrophoneClicked?.invoke()
                            } else {
                                onMicrophoneReleased?.invoke()
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
        }
        recordingArea.addView(microphoneButton)
        applyMicrophoneVisualState(MicrophoneVisualState.READY)

        // Audio visualizer (waveform display during recording)
        audioVisualizer = AudioVisualizerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,  // Use weight
                dpToPx(60),
                1f  // weight parameter
            )
            visibility = View.GONE  // Hidden by default
            // Set initial sensitivity from preferences
            sensitivity = preferencesManager.visualizerSensitivity
        }
        recordingArea.addView(audioVisualizer)

        cancelIcon = TextView(context).apply {
            text = "✕"
            textSize = 18f
            setTextColor(Color.parseColor("#D0D0D0"))
            gravity = Gravity.CENTER
            alpha = 0.7f
            contentDescription = "Cancel recording"
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(36),
                dpToPx(36)
            ).apply {
                marginStart = dpToPx(8)
            }
            setOnClickListener {
                onCancelPressed?.invoke()
            }
        }
        recordingArea.addView(cancelIcon)

        mainKeyboardLayout.addView(recordingArea)

        // Instruction text - mode selection
        instructionText = TextView(context).apply {
            text = "MODE:"
            textSize = 11f
            setTextColor(Color.parseColor("#B0B0B0"))
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(6)
            }
        }
        mainKeyboardLayout.addView(instructionText)

        // Row 1: Main action buttons (always visible)
        val actionButtonsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Tap mode button
        tapModeButton = Button(context).apply {
            text = "TAP"
            textSize = 12f
            setBackgroundColor(Color.parseColor("#4CAF50"))  // Green when active
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(70),
                dpToPx(36)
            ).apply {
                marginEnd = dpToPx(6)
            }
            setOnClickListener {
                if (!isTapMode) {
                    isTapMode = true
                    onModeToggled?.invoke(true)
                    updateModeButtons()
                }
            }
        }
        actionButtonsLayout.addView(tapModeButton)

        // Hold mode button
        holdModeButton = Button(context).apply {
            text = "HOLD"
            textSize = 12f
            setBackgroundColor(Color.parseColor("#616161"))  // Gray when inactive
            setTextColor(Color.parseColor("#AAAAAA"))  // Dimmed text
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(70),
                dpToPx(36)
            ).apply {
                marginEnd = dpToPx(6)
            }
            setOnClickListener {
                if (isTapMode) {
                    isTapMode = false
                    onModeToggled?.invoke(false)
                    updateModeButtons()
                }
            }
        }
        actionButtonsLayout.addView(holdModeButton)

        // Spacer to push following buttons to the right
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(36), 1f)
        }
        actionButtonsLayout.addView(spacer)

        // Space button
        val spaceButton = Button(context).apply {
            text = "Space"
            textSize = 12f
            setBackgroundColor(Color.parseColor("#616161"))  // Gray
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(70),
                dpToPx(36)
            ).apply {
                marginEnd = dpToPx(6)
            }
            setOnClickListener {
                onSpacePressed?.invoke()
                performHapticFeedback()
            }
        }
        actionButtonsLayout.addView(spaceButton)

        // Backspace button
        val backspaceButton = Button(context).apply {
            text = "⌫"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#616161"))  // Gray
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(50),
                dpToPx(36)
            ).apply {
                marginEnd = dpToPx(6)
            }
            setOnClickListener {
                onBackspacePressed?.invoke()
                performHapticFeedback()
            }
        }
        actionButtonsLayout.addView(backspaceButton)

        // Settings button
        settingsButton = Button(context).apply {
            text = "⚙️"
            textSize = 14f
            setBackgroundColor(Color.parseColor("#757575"))  // Gray
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(40),
                dpToPx(36)
            )
            setOnClickListener {
                settingsDrawer?.toggle()
            }
        }
        actionButtonsLayout.addView(settingsButton)

        mainKeyboardLayout.addView(actionButtonsLayout)

        // Add main keyboard layout to FrameLayout
        addView(mainKeyboardLayout)

        // Create settings drawer (initially hidden, overlays on top)
        settingsDrawer = SettingsDrawerView(context, preferencesManager).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM  // Slide up from bottom
            }

            // Callbacks
            onClosed = {
                // Settings drawer closed, no action needed
            }
            onModeChanged = { mode ->
                // Update keyboard mode when changed in settings
                val tapMode = (mode == InputMode.TAP)
                setInputMode(tapMode)
                onModeToggled?.invoke(tapMode)
            }
            onHapticChanged = { enabled ->
                // Notify IME of haptic change
                this@VoiceKeyboardView.onHapticChanged?.invoke(enabled)
            }
            onSensitivityChanged = { sensitivity ->
                // Update visualizer sensitivity
                audioVisualizer.sensitivity = sensitivity
                // Notify IME of sensitivity change
                this@VoiceKeyboardView.onSensitivityChanged?.invoke(sensitivity)
            }
            onOpenAppSettings = {
                // Notify IME to open app settings
                this@VoiceKeyboardView.onOpenAppSettings?.invoke()
            }
        }
        addView(settingsDrawer)

        // Load saved preferences and apply
        loadAndApplyPreferences()
    }

    /**
     * Load saved preferences and apply to UI
     */
    private fun loadAndApplyPreferences() {
        val mode = preferencesManager.defaultMode
        isTapMode = (mode == InputMode.TAP)
        updateModeButtons()

        // Apply saved visualizer sensitivity
        audioVisualizer.sensitivity = preferencesManager.visualizerSensitivity
    }

    private enum class MicrophoneVisualState {
        READY,
        RECORDING,
        PROCESSING
    }

    private fun createCometDrawable(state: MicrophoneVisualState): GradientDrawable {
        val colors = when (state) {
            MicrophoneVisualState.READY -> readyGradientColors
            MicrophoneVisualState.RECORDING -> recordingGradientColors
            MicrophoneVisualState.PROCESSING -> processingGradientColors
        }

        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            shape = GradientDrawable.OVAL
        }
    }

    private fun applyMicrophoneVisualState(state: MicrophoneVisualState) {
        microphoneButton.background = createCometDrawable(state)
        val iconColor = when (state) {
            MicrophoneVisualState.READY -> Color.WHITE
            MicrophoneVisualState.RECORDING -> Color.parseColor("#FFFAF0")
            MicrophoneVisualState.PROCESSING -> Color.parseColor("#EDE7F6")
        }
        microphoneButton.setTextColor(iconColor)
        val iconAlpha = when (state) {
            MicrophoneVisualState.READY -> 1f
            MicrophoneVisualState.RECORDING -> 1f
            MicrophoneVisualState.PROCESSING -> 0.85f
        }
        microphoneButton.alpha = iconAlpha
        if (microphoneButton.text.isNullOrEmpty()) {
            microphoneButton.text = "☄"
        }
    }

    // ============================================================================
    // State Management
    // ============================================================================

    fun showReadyState() {
        currentState = KeyboardState.READY
        isCurrentlyRecording = false
        post {
            statusText.text = "Ready to speak"
            statusText.setTextColor(Color.parseColor("#FFFFFF"))
            applyMicrophoneVisualState(MicrophoneVisualState.READY)
            microphoneButton.isEnabled = true

            // Add gentle pulse animation to attract attention
            microphoneButton.clearAnimation()
            val pulseAnimation = android.view.animation.ScaleAnimation(
                1f, 1.1f,  // Scale from 100% to 110%
                1f, 1.1f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
            ).apply {
                duration = 1000  // 1 second per pulse
                repeatCount = android.view.animation.Animation.INFINITE
                repeatMode = android.view.animation.Animation.REVERSE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }
            microphoneButton.startAnimation(pulseAnimation)

            cancelIcon.visibility = View.GONE
            previewText.visibility = View.GONE
            audioVisualizer.visibility = View.GONE
            audioVisualizer.clear()
        }
    }

    fun showRecordingState() {
        currentState = KeyboardState.RECORDING
        isCurrentlyRecording = true
        post {
            statusText.text = "Recording..."
            statusText.setTextColor(recordingAccentColor)
            applyMicrophoneVisualState(MicrophoneVisualState.RECORDING)
            microphoneButton.clearAnimation()  // No animation during recording
            cancelIcon.visibility = View.VISIBLE
            previewText.visibility = View.GONE

            // Show audio visualizer
            audioVisualizer.visibility = View.VISIBLE
            audioVisualizer.clear()
        }
    }

    fun showProcessingState() {
        currentState = KeyboardState.PROCESSING
        isCurrentlyRecording = false
        post {
            statusText.text = "Processing..."
            statusText.setTextColor(processingAccentColor)
            applyMicrophoneVisualState(MicrophoneVisualState.PROCESSING)
            microphoneButton.isEnabled = false
            cancelIcon.visibility = View.VISIBLE
            audioVisualizer.visibility = View.GONE

            // Pulse the orange button itself
            val pulseAnimation = AnimationUtils.loadAnimation(context, R.anim.button_pulse_animation)
            microphoneButton.startAnimation(pulseAnimation)
        }
    }

    fun showError(message: String) {
        currentState = KeyboardState.ERROR
        isCurrentlyRecording = false
        post {
            statusText.text = "❌ Error: $message"
            statusText.setTextColor(Color.parseColor("#F44336"))
            statusText.textSize = 13f  // Slightly smaller for longer messages
            applyMicrophoneVisualState(MicrophoneVisualState.READY)
            microphoneButton.isEnabled = true
            microphoneButton.clearAnimation()
            cancelIcon.visibility = View.GONE
            previewText.visibility = View.GONE
            audioVisualizer.visibility = View.GONE

            // Keep error visible longer (3 seconds)
            postDelayed({
                statusText.textSize = 14f  // Reset size
                showReadyState()
            }, 3000)
        }
    }

    fun showSuccess(message: String) {
        currentState = KeyboardState.SUCCESS
        isCurrentlyRecording = false
        post {
            statusText.text = "✅ Text inserted successfully!"
            statusText.setTextColor(Color.parseColor("#4CAF50"))
            applyMicrophoneVisualState(MicrophoneVisualState.READY)
            microphoneButton.clearAnimation()
            previewText.visibility = View.GONE
            audioVisualizer.visibility = View.GONE

            // Keep success visible longer (2.5 seconds so user can see it)
            postDelayed({ showReadyState() }, 2500)
        }
    }

    fun showStatus(message: String) {
        post {
            statusText.text = message
            when (currentState) {
                KeyboardState.RECORDING -> statusText.setTextColor(recordingAccentColor)
                KeyboardState.PROCESSING -> statusText.setTextColor(processingAccentColor)
                KeyboardState.READY -> statusText.setTextColor(Color.parseColor("#FFFFFF"))
                KeyboardState.SUCCESS -> statusText.setTextColor(Color.parseColor("#4CAF50"))
                KeyboardState.ERROR -> statusText.setTextColor(Color.parseColor("#F44336"))
            }
        }
    }

    fun showWarning(message: String) {
        post {
            statusText.text = "⚠️ $message"
            statusText.setTextColor(Color.parseColor("#FF9800"))
        }
    }

    // ============================================================================
    // Configuration
    // ============================================================================

    fun setMicrophoneEnabled(enabled: Boolean) {
        post {
            microphoneButton.isEnabled = enabled
            if (enabled) {
                statusText.text = "Ready to speak"
            }
        }
    }

    fun setInputMode(tapMode: Boolean) {
        isTapMode = tapMode
        post {
            updateModeButtons()
        }
    }

    private fun updateModeButtons() {
        if (isTapMode) {
            // Tap mode active - soft blue quartz
            tapModeButton.setBackgroundColor(Color.parseColor("#6BA3D1"))  // Soft blue
            tapModeButton.setTextColor(Color.WHITE)
            holdModeButton.setBackgroundColor(Color.parseColor("#616161"))  // Gray
            holdModeButton.setTextColor(Color.parseColor("#AAAAAA"))  // Dimmed
        } else {
            // Hold mode active - soft blue quartz
            tapModeButton.setBackgroundColor(Color.parseColor("#616161"))  // Gray
            tapModeButton.setTextColor(Color.parseColor("#AAAAAA"))  // Dimmed
            holdModeButton.setBackgroundColor(Color.parseColor("#6BA3D1"))  // Soft blue
            holdModeButton.setTextColor(Color.WHITE)
        }
    }

    fun updateAudioLevel(audioData: ByteArray) {
        post {
            audioVisualizer.updateAudioData(audioData)
        }
    }

    fun isRecording(): Boolean = isCurrentlyRecording

    fun setInputTypeInfo(_isPassword: Boolean, _isSingleLine: Boolean) {
        // Could adjust UI based on text field type
        // For now, just log
    }

    fun showPreview(text: String) {
        post {
            previewText.text = text
            previewText.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    // ============================================================================
    // Utilities
    // ============================================================================

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    /**
     * Perform haptic feedback for button press (if enabled)
     */
    private fun performHapticFeedback() {
        // Check if haptic feedback is enabled
        if (!preferencesManager.hapticEnabled) return

        try {
            vibrator?.let {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(50)
                }
            }
        } catch (e: Exception) {
            // Ignore vibration errors (some devices don't support it)
        }
    }
}
