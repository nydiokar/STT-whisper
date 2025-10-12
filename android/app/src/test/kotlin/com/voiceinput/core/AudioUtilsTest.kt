package com.voiceinput.core

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Unit tests for AudioUtils
 */
class AudioUtilsTest {

    @Test
    fun `bytesToFloat should convert PCM 16-bit to normalized floats`() {
        // Create test audio: max positive, zero, max negative
        val buffer = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(32767)  // Max positive
        buffer.putShort(0)      // Zero
        buffer.putShort(-32768) // Max negative

        val bytes = buffer.array()
        val floats = AudioUtils.bytesToFloat(bytes)

        assertEquals(3, floats.size)
        assertEquals(1.0f, floats[0], 0.001f)    // 32767/32768 ≈ 1.0
        assertEquals(0.0f, floats[1], 0.001f)    // 0
        assertEquals(-1.0f, floats[2], 0.001f)   // -32768/32768 = -1.0
    }

    @Test
    fun `floatToBytes should convert normalized floats to PCM 16-bit`() {
        val floats = floatArrayOf(1.0f, 0.0f, -1.0f)
        val bytes = AudioUtils.floatToBytes(floats)

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(32767.toShort(), buffer.getShort(0))
        assertEquals(0.toShort(), buffer.getShort(2))
        assertEquals((-32768).toShort(), buffer.getShort(4))
    }

    @Test
    fun `bytesToFloat and floatToBytes should be inverse operations`() {
        // Create original bytes
        val originalBuffer = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
        originalBuffer.putShort(1000)
        originalBuffer.putShort(-1000)
        originalBuffer.putShort(15000)
        originalBuffer.putShort(-15000)
        originalBuffer.putShort(0)

        val original = originalBuffer.array()

        // Convert to float and back
        val floats = AudioUtils.bytesToFloat(original)
        val recovered = AudioUtils.floatToBytes(floats)

        assertArrayEquals(original, recovered)
    }

    @Test
    fun `calculateRMS should return zero for silent audio`() {
        val silentAudio = ByteArray(1000) { 0 }
        val rms = AudioUtils.calculateRMS(silentAudio)
        assertEquals(0.0f, rms, 0.001f)
    }

    @Test
    fun `calculateRMS should return non-zero for audio with signal`() {
        // Create audio with some signal
        val buffer = ByteBuffer.allocate(100).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 50) {
            buffer.putShort((Math.sin(i * 0.1) * 10000).toInt().toShort())
        }

        val audio = buffer.array()
        val rms = AudioUtils.calculateRMS(audio)

        assertTrue("RMS should be > 0 for signal", rms > 0.0f)
    }

    @Test
    fun `isSilent should detect silent audio`() {
        val silentAudio = ByteArray(1000) { 0 }
        assertTrue(AudioUtils.isSilent(silentAudio))
    }

    @Test
    fun `isSilent should detect non-silent audio`() {
        // Create audio with loud signal
        val buffer = ByteBuffer.allocate(100).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 50) {
            buffer.putShort(10000)
        }

        val audio = buffer.array()
        assertFalse(AudioUtils.isSilent(audio))
    }

    @Test
    fun `isSilent should respect custom threshold`() {
        // Create quiet but not silent audio
        val buffer = ByteBuffer.allocate(100).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 50) {
            buffer.putShort(100) // Very quiet
        }

        val audio = buffer.array()

        // Should be silent with high threshold
        assertTrue(AudioUtils.isSilent(audio, threshold = 0.1f))

        // Should NOT be silent with very low threshold
        assertFalse(AudioUtils.isSilent(audio, threshold = 0.0001f))
    }

    @Test
    fun `bytesToFloat should handle empty array`() {
        val floats = AudioUtils.bytesToFloat(ByteArray(0))
        assertEquals(0, floats.size)
    }

    @Test
    fun `calculateRMS should handle empty array`() {
        val rms = AudioUtils.calculateRMS(ByteArray(0))
        assertEquals(0.0f, rms, 0.001f)
    }
}