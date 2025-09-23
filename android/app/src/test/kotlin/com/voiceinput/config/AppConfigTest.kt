package com.voiceinput.config

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for AppConfig
 */
class AppConfigTest {

    @Test
    fun `AudioConfig should have valid defaults`() {
        val config = AudioConfig()
        assertEquals(16000, config.sampleRate)
        assertEquals(2048, config.chunkSize)
        assertEquals(1, config.channels)
        assertEquals(0.5f, config.vadThreshold)
        assertEquals(2.0f, config.silenceDurationSec)
    }

    @Test
    fun `AudioConfig should validate sample rate`() {
        // Valid sample rates should work
        AudioConfig(sampleRate = 16000)
        AudioConfig(sampleRate = 8000)
        AudioConfig(sampleRate = 44100)

        // Invalid sample rate should throw
        assertThrows(IllegalArgumentException::class.java) {
            AudioConfig(sampleRate = 12000)
        }
    }

    @Test
    fun `AudioConfig should validate channels`() {
        // Valid channels
        AudioConfig(channels = 1)
        AudioConfig(channels = 2)

        // Invalid channels should throw
        assertThrows(IllegalArgumentException::class.java) {
            AudioConfig(channels = 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AudioConfig(channels = 0)
        }
    }

    @Test
    fun `AudioConfig should validate VAD threshold`() {
        // Valid thresholds
        AudioConfig(vadThreshold = 0.0f)
        AudioConfig(vadThreshold = 0.5f)
        AudioConfig(vadThreshold = 1.0f)

        // Invalid thresholds should throw
        assertThrows(IllegalArgumentException::class.java) {
            AudioConfig(vadThreshold = -0.1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AudioConfig(vadThreshold = 1.1f)
        }
    }

    @Test
    fun `TranscriptionConfig should have valid defaults`() {
        val config = TranscriptionConfig()
        assertEquals("base", config.modelName)
        assertEquals("en", config.language)
        assertEquals(false, config.translate)
        assertEquals(32000, config.minChunkSizeBytes)
    }

    @Test
    fun `TranscriptionConfig should validate model name`() {
        // Valid models
        TranscriptionConfig(modelName = "tiny")
        TranscriptionConfig(modelName = "base")
        TranscriptionConfig(modelName = "small")
        TranscriptionConfig(modelName = "medium")
        TranscriptionConfig(modelName = "large")
        TranscriptionConfig(modelName = "base.en") // .en variant

        // Invalid model should throw
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptionConfig(modelName = "invalid")
        }
    }

    @Test
    fun `AppConfig should have valid defaults`() {
        val config = AppConfig()
        assertNotNull(config.audio)
        assertNotNull(config.transcription)
        assertEquals(false, config.debug)
        assertEquals("INFO", config.logLevel)
    }

    @Test
    fun `AppConfig should validate log level`() {
        // Valid log levels
        AppConfig(logLevel = "DEBUG")
        AppConfig(logLevel = "INFO")
        AppConfig(logLevel = "WARNING")
        AppConfig(logLevel = "ERROR")
        AppConfig(logLevel = "CRITICAL")
        AppConfig(logLevel = "info") // Should accept lowercase

        // Invalid log level should throw
        assertThrows(IllegalArgumentException::class.java) {
            AppConfig(logLevel = "INVALID")
        }
    }

    @Test
    fun `AppConfig should allow nested configuration updates`() {
        val config = AppConfig()

        // Update audio config
        val newAudio = config.audio.copy(vadThreshold = 0.7f)
        val updatedConfig = config.copy(audio = newAudio)

        assertEquals(0.7f, updatedConfig.audio.vadThreshold)
        assertEquals(0.5f, config.audio.vadThreshold) // Original unchanged
    }

    @Test
    fun `AudioConfig valid sample rates list should be correct`() {
        val validRates = AudioConfig.VALID_SAMPLE_RATES
        assertTrue(validRates.contains(8000))
        assertTrue(validRates.contains(16000))
        assertTrue(validRates.contains(22050))
        assertTrue(validRates.contains(44100))
        assertTrue(validRates.contains(48000))
    }

    @Test
    fun `TranscriptionConfig valid models list should be correct`() {
        val validModels = TranscriptionConfig.VALID_MODELS
        assertTrue(validModels.contains("tiny"))
        assertTrue(validModels.contains("base"))
        assertTrue(validModels.contains("small"))
        assertTrue(validModels.contains("medium"))
        assertTrue(validModels.contains("large"))
    }
}