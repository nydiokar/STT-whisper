package com.voiceinput.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.min

/**
 * Simple audio visualizer showing waveform bars
 * Like the equalizer effect when speaking
 */
class AudioVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint().apply {
        color = Color.parseColor("#FFEB3B")  // Bright yellow/gold
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val baselinePaint = Paint().apply {
        color = Color.parseColor("#FFEB3B")  // Same color but for baseline
        strokeWidth = 2f
        isAntiAlias = true
        alpha = 128  // 50% opacity for subtle baseline
    }

    private val barCount = 40  // Number of bars
    private val amplitudes = FloatArray(barCount) { 0.05f }  // Start with small baseline
    private var currentIndex = 0
    private var updateCounter = 0  // For slower updates

    // Sensitivity: 0.0 (low) to 1.0 (high) - controls how sensitive visualizer is to audio
    var sensitivity: Float = 0.5f

    init {
        setBackgroundColor(Color.TRANSPARENT)
    }

    /**
     * Update visualizer with audio data
     * @param audioData PCM audio bytes (16-bit)
     */
    fun updateAudioData(audioData: ByteArray) {
        if (audioData.isEmpty()) return

        // Update every 2nd call for slower, lazier animation
        updateCounter++
        if (updateCounter % 2 != 0) return

        // Calculate RMS (Root Mean Square) amplitude
        var sum = 0.0
        var i = 0
        while (i < audioData.size - 1) {
            // Convert bytes to 16-bit PCM sample
            val sample = ((audioData[i + 1].toInt() shl 8) or (audioData[i].toInt() and 0xFF)).toShort()
            sum += (sample * sample).toDouble()
            i += 2
        }

        val rms = kotlin.math.sqrt(sum / (audioData.size / 2))

        // Normalize to 0-1 range with adjustable sensitivity
        // Low sensitivity (0.0): divide by 32768 (only loud sounds show)
        // High sensitivity (1.0): divide by 8192 (even quiet sounds show)
        val divisor = 32768f - (sensitivity * 24576f)  // Interpolate between 32768 and 8192
        var normalized = min(rms / divisor, 1.0).toFloat()

        // Apply minimum baseline so bars are always slightly visible
        normalized = kotlin.math.max(normalized, 0.05f)

        // Add to circular buffer
        amplitudes[currentIndex] = normalized
        currentIndex = (currentIndex + 1) % barCount

        // Trigger redraw
        postInvalidate()
    }

    /**
     * Clear visualizer
     */
    fun clear() {
        amplitudes.fill(0.05f)  // Reset to baseline, not 0
        updateCounter = 0
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0) return

        val barWidth = width.toFloat() / barCount
        val centerY = height / 2f
        val maxBarHeight = height / 2f * 1.5f  // Increase max height by 50% for more expression

        // Draw baseline first (thin horizontal line)
        canvas.drawLine(0f, centerY, width.toFloat(), centerY, baselinePaint)

        // Draw bars from oldest to newest
        for (i in 0 until barCount) {
            val index = (currentIndex + i) % barCount
            val amplitude = amplitudes[index]

            val barHeight = amplitude * maxBarHeight
            val x = i * barWidth + barWidth / 2

            // Always draw bars (baseline ensures minimum visibility)
            canvas.drawLine(x, centerY - barHeight, x, centerY + barHeight, barPaint)
        }
    }
}
