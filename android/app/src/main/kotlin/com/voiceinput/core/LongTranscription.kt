package com.voiceinput.core

import android.util.Log
import kotlin.math.min

object LongTranscription {
    private const val TAG = "LongTranscription"

    suspend fun transcribeInChunks(
        whisperEngine: WhisperEngine,
        textProcessor: TextProcessor,
        audioData: ByteArray,
        sampleRate: Int,
        maxChunkDurationSec: Float,
        overlapDurationSec: Float = 0f,
        onProgress: ((Int, Int) -> Unit)? = null
    ): String {
        if (audioData.isEmpty()) return ""

        val bytesPerSecond = sampleRate * 2
        val chunkBytes = (maxChunkDurationSec * bytesPerSecond).toInt().coerceAtLeast(1)
        val rawOverlapBytes = (overlapDurationSec * bytesPerSecond).toInt().coerceAtLeast(0)
        val overlapBytes = rawOverlapBytes.coerceAtMost(chunkBytes / 2)

        val totalBytes = audioData.size
        val totalChunks = ((totalBytes + chunkBytes - 1) / chunkBytes).coerceAtLeast(1)

        var accumulated = ""
        var start = 0
        var chunkIndex = 0

        while (start < totalBytes) {
            val end = min(start + chunkBytes, totalBytes)
            val chunk = audioData.copyOfRange(start, end)
            chunkIndex++
            onProgress?.invoke(chunkIndex, totalChunks)

            val result = whisperEngine.transcribe(chunk)
            val processed = textProcessor.process(result.text)
            if (processed.isNotEmpty()) {
                accumulated = textProcessor.appendText(accumulated, processed)
            }

            if (end == totalBytes) break

            val nextStart = end - overlapBytes
            start = if (nextStart > start) nextStart else end
        }

        Log.i(TAG, "Transcribed $chunkIndex chunk(s) into ${accumulated.length} chars")
        return accumulated
    }
}
