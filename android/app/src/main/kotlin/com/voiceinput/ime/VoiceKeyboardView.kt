package com.voiceinput.ime

import android.content.Context
import android.graphics.Color
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.voiceinput.R

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
 */
class VoiceKeyboardView(context: Context) : LinearLayout(context) {

    // Callbacks to IME
    var onMicrophonePressed: (() -> Unit)? = null
    var onMicrophoneReleased: (() -> Unit)? = null
    var onCancelPressed: (() -> Unit)? = null
    var onSettingsPressed: (() -> Unit)? = null

    // UI Components
    private val statusText: TextView
    private val microphoneButton: Button
    private val cancelButton: Button
    private val settingsButton: Button
    private val previewText: TextView

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
        orientation = VERTICAL
        setBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
        setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(16))

        // Status/Preview area at top
        previewText = TextView(context).apply {
            text = ""
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                dpToPx(40)
            ).apply {
                bottomMargin = dpToPx(8)
            }
            visibility = View.GONE  // Hidden by default
        }
        addView(previewText)

        // Status text
        statusText = TextView(context).apply {
            text = "Initializing..."
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(12)
            }
        }
        addView(statusText)

        // Microphone button (main action)
        microphoneButton = Button(context).apply {
            // Use vector drawable instead of emoji
            setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_microphone, 0, 0)
            compoundDrawablePadding = 0
            text = ""  // No text, just icon
            setBackgroundColor(Color.parseColor("#4CAF50"))  // Green
            setTextColor(Color.WHITE)
            isEnabled = false  // Disabled until initialized
            layoutParams = LayoutParams(
                dpToPx(80),
                dpToPx(80)
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dpToPx(16)
            }

            // Touch listener for press/release with haptic feedback
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (isEnabled) {
                            performHapticFeedback()  // ✅ Haptic feedback
                            onMicrophonePressed?.invoke()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isEnabled) {
                            performHapticFeedback()  // ✅ Haptic feedback
                            onMicrophoneReleased?.invoke()
                        }
                        true
                    }
                    else -> false
                }
            }
        }
        addView(microphoneButton)

        // Instruction text
        val instructionText = TextView(context).apply {
            text = "Press and hold to speak"
            textSize = 12f
            setTextColor(Color.parseColor("#999999"))
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(16)
            }
        }
        addView(instructionText)

        // Bottom action buttons
        val actionButtonsLayout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_HORIZONTAL
        }

        cancelButton = Button(context).apply {
            text = "Cancel"
            textSize = 14f
            setBackgroundColor(Color.parseColor("#FF5252"))  // Red
            setTextColor(Color.WHITE)
            visibility = View.GONE  // Hidden when not recording
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                dpToPx(40)
            ).apply {
                marginEnd = dpToPx(16)
            }
            setOnClickListener {
                onCancelPressed?.invoke()
            }
        }
        actionButtonsLayout.addView(cancelButton)

        settingsButton = Button(context).apply {
            text = "⚙️ Settings"
            textSize = 14f
            setBackgroundColor(Color.parseColor("#757575"))  // Gray
            setTextColor(Color.WHITE)
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                dpToPx(40)
            )
            setOnClickListener {
                onSettingsPressed?.invoke()
            }
        }
        actionButtonsLayout.addView(settingsButton)

        addView(actionButtonsLayout)
    }

    // ============================================================================
    // State Management
    // ============================================================================

    fun showReadyState() {
        currentState = KeyboardState.READY
        post {
            statusText.text = "Ready to speak"
            statusText.setTextColor(Color.parseColor("#333333"))
            microphoneButton.setBackgroundColor(Color.parseColor("#4CAF50"))
            microphoneButton.isEnabled = true
            cancelButton.visibility = View.GONE
            previewText.visibility = View.GONE
        }
    }

    fun showRecordingState() {
        currentState = KeyboardState.RECORDING
        post {
            statusText.text = "🔴 Recording... speak now"
            statusText.setTextColor(Color.parseColor("#F44336"))  // Red
            microphoneButton.setBackgroundColor(Color.parseColor("#F44336"))
            cancelButton.visibility = View.VISIBLE
            previewText.visibility = View.GONE
        }
    }

    fun showProcessingState() {
        currentState = KeyboardState.PROCESSING
        post {
            statusText.text = "⏳ Processing..."
            statusText.setTextColor(Color.parseColor("#FF9800"))  // Orange
            microphoneButton.setBackgroundColor(Color.parseColor("#FF9800"))
            microphoneButton.isEnabled = false
            // ✅ FIX: Keep cancel button visible during processing
            cancelButton.visibility = View.VISIBLE
        }
    }

    fun showError(message: String) {
        currentState = KeyboardState.ERROR
        post {
            statusText.text = "❌ $message"
            statusText.setTextColor(Color.parseColor("#F44336"))
            microphoneButton.setBackgroundColor(Color.parseColor("#4CAF50"))
            microphoneButton.isEnabled = true
            cancelButton.visibility = View.GONE
            previewText.visibility = View.GONE

            // Auto-return to ready state after 3 seconds
            postDelayed({ showReadyState() }, 3000)
        }
    }

    fun showSuccess(message: String) {
        currentState = KeyboardState.SUCCESS
        post {
            statusText.text = "✅ $message"
            statusText.setTextColor(Color.parseColor("#4CAF50"))
            previewText.visibility = View.GONE

            // Auto-return to ready state after 2 seconds
            postDelayed({ showReadyState() }, 2000)
        }
    }

    fun showStatus(message: String) {
        post {
            statusText.text = message
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

    fun setInputTypeInfo(isPassword: Boolean, isSingleLine: Boolean) {
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
     * Perform haptic feedback for button press
     */
    private fun performHapticFeedback() {
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

