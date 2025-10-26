package com.voiceinput.config

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple preferences manager for IME user-facing settings
 *
 * This is separate from AppConfig which handles technical audio/transcription settings.
 * PreferencesManager focuses on user preferences for the keyboard UI:
 * - Input mode (TAP vs HOLD)
 * - Haptic feedback
 * - Visualizer sensitivity
 *
 * Design: Inline settings drawer (slide-up from keyboard)
 * See: docs/android/SETTINGS_UX_DESIGN.md
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Default input mode for voice recording
     */
    var defaultMode: InputMode
        get() {
            val modeName = prefs.getString(KEY_DEFAULT_MODE, InputMode.TAP.name) ?: InputMode.TAP.name
            return try {
                InputMode.valueOf(modeName)
            } catch (e: IllegalArgumentException) {
                InputMode.TAP // Fallback to TAP if invalid
            }
        }
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_MODE, value.name).apply()
        }

    /**
     * Whether haptic feedback (vibration) is enabled
     * Default: true (ON)
     */
    var hapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, value).apply()
        }

    /**
     * Audio visualizer sensitivity level
     * Range: 0.0 (low) to 1.0 (high)
     * Default: 0.5 (medium)
     *
     * This controls how responsive the visualizer is to audio input.
     * - 0.0-0.3: Low sensitivity (only loud sounds show)
     * - 0.4-0.6: Medium sensitivity (balanced)
     * - 0.7-1.0: High sensitivity (even quiet sounds show)
     */
    var visualizerSensitivity: Float
        get() = prefs.getFloat(KEY_VISUALIZER_SENSITIVITY, 0.5f)
        set(value) {
            val clamped = value.coerceIn(0.0f, 1.0f)
            prefs.edit().putFloat(KEY_VISUALIZER_SENSITIVITY, clamped).apply()
        }

    /**
     * Reset all preferences to defaults
     */
    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "voice_ime_prefs"

        private const val KEY_DEFAULT_MODE = "default_mode"
        private const val KEY_HAPTIC_ENABLED = "haptic_enabled"
        private const val KEY_VISUALIZER_SENSITIVITY = "visualizer_sensitivity"
    }
}

/**
 * Input mode for voice recording
 */
enum class InputMode {
    /**
     * TAP mode: Click mic to start, click again to stop
     * Best for: Longer utterances, multi-sentence input
     */
    TAP,

    /**
     * HOLD mode: Press and hold mic, release to process
     * Best for: Quick single phrases, one-handed use
     */
    HOLD
}
