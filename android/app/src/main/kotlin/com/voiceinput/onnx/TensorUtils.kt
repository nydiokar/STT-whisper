package com.voiceinput.onnx

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer

/**
 * Utility functions for creating and manipulating ONNX tensors
 * Adapted from RTranslator's TensorUtils.java
 */
object TensorUtils {

    /**
     * Create shape array for tensors
     */
    fun tensorShape(vararg dims: Long): LongArray {
        return dims
    }

    /**
     * Create Int64 (Long) tensor from Int array
     */
    fun convertIntArrayToTensor(env: OrtEnvironment, intArray: IntArray): OnnxTensor {
        val longArray = intArray.map { it.toLong() }.toLongArray()
        val shape = longArrayOf(1, intArray.size.toLong())
        val buffer = LongBuffer.wrap(longArray)
        return OnnxTensor.createTensor(env, buffer, shape)
    }

    /**
     * Create Int64 tensor with custom shape
     */
    fun convertIntArrayToTensor(env: OrtEnvironment, intArray: IntArray, shape: LongArray): OnnxTensor {
        val longArray = intArray.map { it.toLong() }.toLongArray()
        val buffer = LongBuffer.wrap(longArray)
        return OnnxTensor.createTensor(env, buffer, shape)
    }

    /**
     * Create Int32 tensor
     */
    fun createInt32Tensor(env: OrtEnvironment, data: IntArray, shape: LongArray): OnnxTensor {
        return OnnxTensor.createTensor(env, IntBuffer.wrap(data), shape)
    }

    /**
     * Create Float tensor from 1D array
     */
    fun createFloatTensor(env: OrtEnvironment, data: FloatArray, shape: LongArray): OnnxTensor {
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)
    }

    /**
     * Create Float tensor with a single value repeated
     * Optimized for zero values using direct ByteBuffer
     */
    fun createFloatTensorWithSingleValue(env: OrtEnvironment, value: Float, shape: LongArray): OnnxTensor {
        val flatLength = shape.fold(1L) { acc, dim -> acc * dim }.toInt()

        val buffer: FloatBuffer = if (value != 0f) {
            val array = FloatArray(flatLength) { value }
            FloatBuffer.wrap(array)
        } else {
            // Optimized path for zeros - uses direct buffer to avoid copying
            ByteBuffer.allocateDirect(flatLength * 4).asFloatBuffer()
        }

        return OnnxTensor.createTensor(env, buffer, shape)
    }

    /**
     * Create Int64 tensor with a single value repeated
     * Optimized for zero values using direct ByteBuffer
     */
    fun createInt64TensorWithSingleValue(env: OrtEnvironment, value: Long, shape: LongArray): OnnxTensor {
        val flatLength = shape.fold(1L) { acc, dim -> acc * dim }.toInt()

        val buffer: LongBuffer = if (value != 0L) {
            val array = LongArray(flatLength) { value }
            LongBuffer.wrap(array)
        } else {
            // Optimized path for zeros - 2 orders of magnitude faster (500ms -> 4ms)
            ByteBuffer.allocateDirect(flatLength * 8).asLongBuffer()
        }

        return OnnxTensor.createTensor(env, buffer, shape)
    }

    /**
     * Create optimized Float tensor using direct ByteBuffer
     * Reduces RAM consumption and execution time
     */
    fun createFloatTensorOptimized(
        env: OrtEnvironment,
        data: FloatArray,
        shape: LongArray
    ): OnnxTensor {
        val buffer = ByteBuffer.allocateDirect(data.size * 4).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            asFloatBuffer().put(data)
            position(0)
        }

        return OnnxTensor.createTensor(env, buffer, shape, OnnxJavaType.FLOAT)
    }

    /**
     * Convert audio PCM bytes to Float array
     * Assumes 16-bit PCM (2 bytes per sample), little-endian
     */
    fun convertPcmToFloatArray(audioData: ByteArray): FloatArray {
        val audioSamples = audioData.size / 2
        val floatArray = FloatArray(audioSamples)
        val byteBuffer = ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN)

        // Convert PCM samples to normalized floats [-1.0, 1.0]
        for (i in 0 until audioSamples) {
            val sample = byteBuffer.short.toFloat() / 32768.0f
            floatArray[i] = sample
        }

        return floatArray
    }
}
