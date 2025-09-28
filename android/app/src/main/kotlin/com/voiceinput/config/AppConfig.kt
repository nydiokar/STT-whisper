package com.voiceinput.config

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

/**
 * Audio recording configuration
 * Port of desktop/voice_input_service/config.py AudioConfig
 */
data class AudioConfig(
    val sampleRate: Int = 16000,
    val chunkSize: Int = 2048,
    val channels: Int = 1, // 1=mono, 2=stereo
    val minAudioLengthSec: Float = 1.5f,
    val minProcessInterval: Float = 0.5f,

    // Voice Activity Detection settings
    val vadMode: String = "silero",
    val vadThreshold: Float = 0.5f,  // Much more sensitive for testing, 0.0-1.0, higher = less sensitive
    val silenceDurationSec: Float = 2.0f,
    val maxChunkDurationSec: Float = 15.0f
) {
    init {
        require(sampleRate in VALID_SAMPLE_RATES) {
            "Sample rate must be one of $VALID_SAMPLE_RATES, got $sampleRate"
        }
        require(channels in 1..2) {
            "Channels must be 1 (mono) or 2 (stereo), got $channels"
        }
        require(vadThreshold in 0.0f..1.0f) {
            "VAD threshold must be between 0.0 and 1.0, got $vadThreshold"
        }
    }

    companion object {
        val VALID_SAMPLE_RATES = listOf(8000, 16000, 22050, 44100, 48000)
    }
}

/**
 * Transcription configuration
 * Port of desktop/voice_input_service/config.py TranscriptionConfig
 */
data class TranscriptionConfig(
    val modelName: String = "base",
    val language: String = "en",
    val translate: Boolean = false,
    val minChunkSizeBytes: Int = 32000, // ~1 sec @ 16kHz

    // Whisper model paths (Android-specific)
    val modelPath: String? = null // Path to GGML model in app storage
) {
    init {
        val baseModel = modelName.split(".")[0]
        require(baseModel in VALID_MODELS) {
            "Model name must be one of $VALID_MODELS (or .en variant), got $modelName"
        }
    }

    companion object {
        val VALID_MODELS = listOf("tiny", "base", "small", "medium", "large")
    }
}

/**
 * Main application configuration
 * Port of desktop/voice_input_service/config.py Config
 */
data class AppConfig(
    val audio: AudioConfig = AudioConfig(),
    val transcription: TranscriptionConfig = TranscriptionConfig(),
    val debug: Boolean = false,
    val logLevel: String = "INFO"
) {
    init {
        require(logLevel.uppercase() in VALID_LOG_LEVELS) {
            "Log level must be one of $VALID_LOG_LEVELS, got $logLevel"
        }
    }

    companion object {
        val VALID_LOG_LEVELS = listOf("DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL")
        const val PREFS_KEY_CONFIG = "app_config_json"  // Made public

        fun default(): AppConfig = AppConfig()
    }
}

/**
 * Configuration repository using SharedPreferences
 * Handles loading/saving configuration
 */
class ConfigRepository(private val context: Context) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Save configuration to SharedPreferences
     */
    fun save(config: AppConfig) {
        val json = gson.toJson(config)
        prefs.edit().putString(AppConfig.PREFS_KEY_CONFIG, json).apply()
    }

    /**
     * Load configuration from SharedPreferences
     * Returns default config if not found
     */
    fun load(): AppConfig {
        val json = prefs.getString(AppConfig.PREFS_KEY_CONFIG, null)
        return if (json != null) {
            try {
                gson.fromJson(json, AppConfig::class.java)
            } catch (e: Exception) {
                // If parsing fails, return default
                AppConfig()
            }
        } else {
            AppConfig()
        }
    }

    /**
     * Save configuration to JSON file (for export/backup)
     */
    fun saveToFile(config: AppConfig, file: File) {
        val json = gson.toJson(config)
        file.writeText(json)
    }

    /**
     * Load configuration from JSON file (for import/restore)
     */
    fun loadFromFile(file: File): AppConfig {
        val json = file.readText()
        return gson.fromJson(json, AppConfig::class.java)
    }

    /**
     * Update audio configuration
     */
    fun updateAudio(config: AppConfig, audioConfig: AudioConfig): AppConfig {
        val updated = config.copy(audio = audioConfig)
        save(updated)
        return updated
    }

    /**
     * Update transcription configuration
     */
    fun updateTranscription(config: AppConfig, transcriptionConfig: TranscriptionConfig): AppConfig {
        val updated = config.copy(transcription = transcriptionConfig)
        save(updated)
        return updated
    }

    /**
     * Update VAD threshold
     */
    fun updateVadThreshold(config: AppConfig, threshold: Float): AppConfig {
        require(threshold in 0.0f..1.0f) { "VAD threshold must be between 0.0 and 1.0" }
        val updatedAudio = config.audio.copy(vadThreshold = threshold)
        return updateAudio(config, updatedAudio)
    }

    /**
     * Update silence duration
     */
    fun updateSilenceDuration(config: AppConfig, durationSec: Float): AppConfig {
        val updatedAudio = config.audio.copy(silenceDurationSec = durationSec)
        return updateAudio(config, updatedAudio)
    }

    /**
     * Update model name
     */
    fun updateModel(config: AppConfig, modelName: String): AppConfig {
        val updatedTranscription = config.transcription.copy(modelName = modelName)
        return updateTranscription(config, updatedTranscription)
    }

    /**
     * Update language
     */
    fun updateLanguage(config: AppConfig, language: String): AppConfig {
        val updatedTranscription = config.transcription.copy(language = language)
        return updateTranscription(config, updatedTranscription)
    }

    /**
     * Reset to default configuration
     */
    fun reset(): AppConfig {
        val defaultConfig = AppConfig()
        save(defaultConfig)
        return defaultConfig
    }

    /**
     * Get data directory for app files (models, transcripts, etc.)
     */
    fun getDataDir(): File {
        val dataDir = File(context.filesDir, "voice_input_data")
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }
        return dataDir
    }

    /**
     * Get models directory
     */
    fun getModelsDir(): File {
        val modelsDir = File(getDataDir(), "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        return modelsDir
    }

    /**
     * Get transcripts directory
     */
    fun getTranscriptsDir(): File {
        val transcriptsDir = File(getDataDir(), "transcripts")
        if (!transcriptsDir.exists()) {
            transcriptsDir.mkdirs()
        }
        return transcriptsDir
    }
}