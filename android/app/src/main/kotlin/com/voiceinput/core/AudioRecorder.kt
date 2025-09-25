package com.voiceinput.core

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/**
 * Handles audio recording for voice input
 * Port of desktop/voice_input_service/core/audio.py AudioRecorder
 *
 * @param sampleRate Audio sample rate in Hz (16000 for Whisper)
 * @param chunkSize Number of bytes per buffer chunk
 * @param channels Number of audio channels (1=mono)
 */
class AudioRecorder(
    private val sampleRate: Int = 16000,
    private val chunkSize: Int = 2048,
    private val channels: Int = 1
) {

    companion object {
        private const val TAG = "AudioRecorder"

        // Audio format constants
        private const val AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val audioData = mutableListOf<ByteArray>()
    private val lock = Any()

    /**
     * Get minimum buffer size for AudioRecord
     */
    private fun getMinBufferSize(): Int {
        val channelConfig = if (channels == 1) {
            AudioFormat.CHANNEL_IN_MONO
        } else {
            AudioFormat.CHANNEL_IN_STEREO
        }

        val minSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, ENCODING)

        // Use larger of minimum size or our chunk size
        return maxOf(minSize, chunkSize * 2)
    }

    /**
     * Initialize AudioRecord
     * @return true if initialization successful
     */
    fun initialize(): Boolean {
        return try {
            val channelConfig = if (channels == 1) {
                AudioFormat.CHANNEL_IN_MONO
            } else {
                AudioFormat.CHANNEL_IN_STEREO
            }

            val bufferSize = getMinBufferSize()

            audioRecord = AudioRecord(
                AUDIO_SOURCE,
                sampleRate,
                channelConfig,
                ENCODING,
                bufferSize
            )

            val state = audioRecord?.state
            if (state == AudioRecord.STATE_INITIALIZED) {
                Log.i(TAG, "AudioRecord initialized: rate=$sampleRate, channels=$channels, bufferSize=$bufferSize")
                true
            } else {
                Log.e(TAG, "AudioRecord initialization failed, state=$state")
                audioRecord?.release()
                audioRecord = null
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioRecord", e)
            false
        }
    }

    /**
     * Start recording audio
     * @return true if started successfully
     */
    fun start(): Boolean {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return false
        }

        if (audioRecord == null && !initialize()) {
            return false
        }

        return try {
            synchronized(lock) {
                audioData.clear()
            }

            audioRecord?.startRecording()
            isRecording = true
            Log.i(TAG, "Recording started")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            isRecording = false
            false
        }
    }

    /**
     * Stop recording audio
     * @return Recorded audio data as ByteArray
     */
    fun stop(): ByteArray {
        if (!isRecording) {
            return synchronized(lock) {
                concatenateAudioData()
            }
        }

        try {
            audioRecord?.stop()
            isRecording = false
            Log.i(TAG, "Recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }

        return synchronized(lock) {
            concatenateAudioData()
        }
    }

    /**
     * Get audio stream as Flow for real-time processing
     * Emits audio chunks as they're recorded
     */
    fun audioStream(): Flow<ByteArray> = flow {
        if (!isRecording) {
            Log.w(TAG, "Not recording, cannot stream audio")
            return@flow
        }

        val buffer = ByteArray(chunkSize)
        val record = audioRecord ?: return@flow

        while (coroutineContext.isActive && isRecording) {
            val bytesRead = withContext(Dispatchers.IO) {
                record.read(buffer, 0, buffer.size)
            }

            if (bytesRead > 0) {
                val chunk = buffer.copyOf(bytesRead)

                // Store for later retrieval
                synchronized(lock) {
                    audioData.add(chunk)
                }

                // Emit for real-time processing
                emit(chunk)
            } else if (bytesRead < 0) {
                Log.e(TAG, "AudioRecord read error: $bytesRead")
                break
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Read a single chunk of audio (blocking)
     * @return ByteArray of audio data, or empty array if error
     */
    suspend fun readChunk(): ByteArray = withContext(Dispatchers.IO) {
        if (!isRecording || audioRecord == null) {
            return@withContext ByteArray(0)
        }

        val buffer = ByteArray(chunkSize)
        val bytesRead = audioRecord!!.read(buffer, 0, buffer.size)

        if (bytesRead > 0) {
            val chunk = buffer.copyOf(bytesRead)
            synchronized(lock) {
                audioData.add(chunk)
            }
            chunk
        } else {
            ByteArray(0)
        }
    }

    /**
     * Get all recorded audio data
     */
    fun getAudioData(): ByteArray = synchronized(lock) {
        concatenateAudioData()
    }

    /**
     * Clear recorded audio buffer
     */
    fun clearBuffer() = synchronized(lock) {
        audioData.clear()
    }

    /**
     * Release resources
     */
    fun release() {
        stop()
        audioRecord?.release()
        audioRecord = null
        Log.i(TAG, "AudioRecord released")
    }

    /**
     * Check if currently recording
     */
    fun isCurrentlyRecording(): Boolean = isRecording

    /**
     * Get audio info
     */
    fun getAudioInfo(): AudioInfo {
        return AudioInfo(
            sampleRate = sampleRate,
            channels = channels,
            encoding = ENCODING,
            bufferSize = getMinBufferSize(),
            isRecording = isRecording
        )
    }

    /**
     * Concatenate all audio chunks into single ByteArray
     */
    private fun concatenateAudioData(): ByteArray {
        if (audioData.isEmpty()) {
            return ByteArray(0)
        }

        val totalSize = audioData.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0

        for (chunk in audioData) {
            chunk.copyInto(result, offset)
            offset += chunk.size
        }

        return result
    }

    /**
     * Audio information data class
     */
    data class AudioInfo(
        val sampleRate: Int,
        val channels: Int,
        val encoding: Int,
        val bufferSize: Int,
        val isRecording: Boolean
    )
}

/**
 * Audio utility functions
 */
object AudioUtils {

    /**
     * Convert ByteArray (PCM 16-bit) to FloatArray normalized to [-1.0, 1.0]
     * This is needed for Whisper and VAD models
     */
    fun bytesToFloat(audioBytes: ByteArray): FloatArray {
        val shorts = ByteBuffer.wrap(audioBytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()

        val floats = FloatArray(shorts.capacity())
        for (i in 0 until shorts.capacity()) {
            floats[i] = shorts.get(i) / 32768.0f
        }
        return floats
    }

    /**
     * Convert FloatArray to ByteArray (PCM 16-bit)
     */
    fun floatToBytes(audioFloats: FloatArray): ByteArray {
        val bytes = ByteArray(audioFloats.size * 2)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        for (float in audioFloats) {
            val sample = (float * 32768.0f).toInt().coerceIn(-32768, 32767).toShort()
            buffer.putShort(sample)
        }

        return bytes
    }

    /**
     * Calculate RMS (Root Mean Square) of audio for volume detection
     */
    fun calculateRMS(audioBytes: ByteArray): Float {
        if (audioBytes.isEmpty()) return 0f

        val floats = bytesToFloat(audioBytes)
        var sum = 0.0
        for (sample in floats) {
            sum += sample * sample
        }
        return Math.sqrt(sum / floats.size).toFloat()
    }

    /**
     * Check if audio is silent based on RMS threshold
     */
    fun isSilent(audioBytes: ByteArray, threshold: Float = 0.01f): Boolean {
        return calculateRMS(audioBytes) < threshold
    }
}