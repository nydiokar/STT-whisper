package com.voiceinput.onnx

import kotlin.math.exp
import kotlin.math.ln

/**
 * Utility functions for ONNX inference operations
 * Adapted from RTranslator's Utils.java
 */
object OnnxUtils {

    /**
     * Get the index of the largest value in a Float array
     */
    fun getIndexOfLargest(array: FloatArray): Int {
        if (array.isEmpty()) return -1

        var largestIndex = 0
        var largest = -Float.MAX_VALUE

        for (i in array.indices) {
            if (array[i] > largest) {
                largestIndex = i
                largest = array[i]
            }
        }

        return largestIndex
    }

    /**
     * Get the index of the largest value in a Double array
     */
    fun getIndexOfLargest(array: DoubleArray): Int {
        if (array.isEmpty()) return -1

        var largestIndex = 0
        var largest = -Double.MAX_VALUE

        for (i in array.indices) {
            if (array[i] > largest) {
                largestIndex = i
                largest = array[i]
            }
        }

        return largestIndex
    }

    /**
     * Calculate softmax probability for a given input
     */
    fun softmax(input: Float, neuronValues: FloatArray): Double {
        var total = 0.0
        for (value in neuronValues) {
            total += exp(value.toDouble())
        }
        return exp(input.toDouble()) / total
    }

    /**
     * Calculate log-sum-exp (numerically stable)
     */
    fun logSumExp(neuronValues: FloatArray): Double {
        var total = 0.0
        for (value in neuronValues) {
            total += exp(value.toDouble())
        }
        return ln(total)
    }

    /**
     * Fast log-sum-exp with numerical stability
     * Subtracts max value to prevent overflow
     */
    fun logSumExpFast(neuronValues: FloatArray): Double {
        var max = Double.NEGATIVE_INFINITY
        for (v in neuronValues) {
            if (v > max) {
                max = v.toDouble()
            }
        }

        val threshold = 20.0 // Skip correction if values are much lower
        var sum = 0.0

        for (v in neuronValues) {
            val diff = v - max
            if (diff > -threshold) {
                sum += exp(diff)
            }
        }

        return max + ln(sum)
    }
}
