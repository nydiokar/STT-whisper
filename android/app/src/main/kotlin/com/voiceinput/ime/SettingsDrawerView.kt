package com.voiceinput.ime

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.voiceinput.config.InputMode
import com.voiceinput.config.PreferencesManager

/**
 * Slide-up settings drawer for voice keyboard IME
 *
 * Design: Inline quick settings without leaving keyboard context
 * See: docs/android/SETTINGS_UX_DESIGN.md
 *
 * Essential settings:
 * 1. Default Mode (TAP/HOLD) - Radio buttons
 * 2. Haptic Feedback (ON/OFF) - Switch
 * 3. Visualizer Sensitivity (slider) - SeekBar
 *
 * Features:
 * - Slides up from bottom with smooth animation (200ms)
 * - Saves immediately on change (no Apply button)
 * - Matches cosmos theme from main keyboard
 * - Close button returns to keyboard
 */
class SettingsDrawerView(
    context: Context,
    private val preferencesManager: PreferencesManager
) : LinearLayout(context) {

    // Callbacks
    var onClosed: (() -> Unit)? = null
    var onModeChanged: ((InputMode) -> Unit)? = null
    var onHapticChanged: ((Boolean) -> Unit)? = null
    var onSensitivityChanged: ((Float) -> Unit)? = null

    // UI Components
    private val headerLayout: LinearLayout
    private val modeRadioGroup: RadioGroup
    private val tapRadioButton: RadioButton
    private val holdRadioButton: RadioButton
    private val hapticSwitch: Switch
    private val sensitivitySeekBar: SeekBar
    private val sensitivityLabel: TextView
    private val closeButton: Button

    init {
        orientation = VERTICAL
        // Match cosmos gradient background from main keyboard
        setBackgroundColor(Color.parseColor("#1a1a2e"))  // Slightly lighter than main bg
        setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(16))
        elevation = dpToPx(8).toFloat()  // Elevated above keyboard
        visibility = View.GONE  // Hidden by default

        // Header row (title + close button)
        headerLayout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(12)
            }
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleText = TextView(context).apply {
            text = "⚙️ Settings"
            textSize = 16f
            setTextColor(Color.WHITE)
            layoutParams = LayoutParams(
                0,  // Use weight
                LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
            }
        }
        headerLayout.addView(titleText)

        closeButton = Button(context).apply {
            text = "✕"
            textSize = 18f
            setBackgroundColor(Color.parseColor("#424242"))
            setTextColor(Color.WHITE)
            layoutParams = LayoutParams(
                dpToPx(36),
                dpToPx(36)
            )
            setOnClickListener {
                hide()
            }
        }
        headerLayout.addView(closeButton)
        addView(headerLayout)

        // Separator
        addView(View(context).apply {
            setBackgroundColor(Color.parseColor("#404040"))
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                dpToPx(1)
            ).apply {
                bottomMargin = dpToPx(12)
            }
        })

        // Setting 1: Default Mode
        addView(TextView(context).apply {
            text = "Default Input Mode"
            textSize = 13f
            setTextColor(Color.parseColor("#B0B0B0"))
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(6)
            }
        })

        modeRadioGroup = RadioGroup(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(16)
            }
            setOnCheckedChangeListener { _, checkedId ->
                val newMode = when (checkedId) {
                    tapRadioButton.id -> InputMode.TAP
                    holdRadioButton.id -> InputMode.HOLD
                    else -> InputMode.TAP
                }
                preferencesManager.defaultMode = newMode
                onModeChanged?.invoke(newMode)
            }
        }

        tapRadioButton = RadioButton(context).apply {
            id = View.generateViewId()
            text = "TAP"
            textSize = 13f
            setTextColor(Color.WHITE)
            layoutParams = LayoutParams(
                0,  // Use weight
                LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
                marginEnd = dpToPx(8)
            }
        }
        modeRadioGroup.addView(tapRadioButton)

        holdRadioButton = RadioButton(context).apply {
            id = View.generateViewId()
            text = "HOLD"
            textSize = 13f
            setTextColor(Color.WHITE)
            layoutParams = LayoutParams(
                0,  // Use weight
                LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
            }
        }
        modeRadioGroup.addView(holdRadioButton)
        addView(modeRadioGroup)

        // Setting 2: Haptic Feedback
        val hapticLayout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(16)
            }
            gravity = Gravity.CENTER_VERTICAL
        }

        val hapticLabel = TextView(context).apply {
            text = "Haptic Feedback"
            textSize = 13f
            setTextColor(Color.parseColor("#B0B0B0"))
            layoutParams = LayoutParams(
                0,  // Use weight
                LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
            }
        }
        hapticLayout.addView(hapticLabel)

        hapticSwitch = Switch(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            )
            setOnCheckedChangeListener { _, isChecked ->
                preferencesManager.hapticEnabled = isChecked
                onHapticChanged?.invoke(isChecked)
            }
        }
        hapticLayout.addView(hapticSwitch)
        addView(hapticLayout)

        // Setting 3: Visualizer Sensitivity
        addView(TextView(context).apply {
            text = "Visualizer Sensitivity"
            textSize = 13f
            setTextColor(Color.parseColor("#B0B0B0"))
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(4)
            }
        })

        sensitivityLabel = TextView(context).apply {
            text = "Medium"  // Default
            textSize = 11f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(4)
            }
        }
        addView(sensitivityLabel)

        sensitivitySeekBar = SeekBar(context).apply {
            max = 100  // 0-100, will convert to 0.0-1.0
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(8)
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val sensitivity = progress / 100f
                    updateSensitivityLabel(sensitivity)
                    if (fromUser) {
                        preferencesManager.visualizerSensitivity = sensitivity
                        onSensitivityChanged?.invoke(sensitivity)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        addView(sensitivitySeekBar)

        // Load saved preferences
        loadPreferences()
    }

    /**
     * Load current preferences and update UI
     */
    private fun loadPreferences() {
        // Mode
        val mode = preferencesManager.defaultMode
        when (mode) {
            InputMode.TAP -> tapRadioButton.isChecked = true
            InputMode.HOLD -> holdRadioButton.isChecked = true
        }

        // Haptic
        hapticSwitch.isChecked = preferencesManager.hapticEnabled

        // Sensitivity
        val sensitivity = preferencesManager.visualizerSensitivity
        sensitivitySeekBar.progress = (sensitivity * 100).toInt()
        updateSensitivityLabel(sensitivity)
    }

    /**
     * Update sensitivity label text based on value
     */
    private fun updateSensitivityLabel(sensitivity: Float) {
        val label = when {
            sensitivity < 0.3f -> "Low"
            sensitivity < 0.7f -> "Medium"
            else -> "High"
        }
        sensitivityLabel.text = "$label (${(sensitivity * 100).toInt()}%)"
    }

    /**
     * Show settings drawer with slide-up animation
     */
    fun show() {
        if (visibility == View.VISIBLE) return

        visibility = View.VISIBLE
        alpha = 0f
        translationY = height.toFloat()

        animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    /**
     * Hide settings drawer with slide-down animation
     */
    fun hide() {
        if (visibility == View.GONE) return

        animate()
            .alpha(0f)
            .translationY(height.toFloat())
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                visibility = View.GONE
                onClosed?.invoke()
            }
            .start()
    }

    /**
     * Toggle visibility
     */
    fun toggle() {
        if (visibility == View.VISIBLE) {
            hide()
        } else {
            show()
        }
    }

    /**
     * Check if drawer is visible
     */
    fun isVisible(): Boolean = visibility == View.VISIBLE

    /**
     * Update mode selection (called when mode buttons are pressed)
     */
    fun setMode(mode: InputMode) {
        // Don't trigger callback when programmatically setting
        val previousListener = modeRadioGroup.getOnCheckedChangeListener()
        modeRadioGroup.setOnCheckedChangeListener(null)

        when (mode) {
            InputMode.TAP -> tapRadioButton.isChecked = true
            InputMode.HOLD -> holdRadioButton.isChecked = true
        }

        modeRadioGroup.setOnCheckedChangeListener(previousListener)
    }

    // Utility function
    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
