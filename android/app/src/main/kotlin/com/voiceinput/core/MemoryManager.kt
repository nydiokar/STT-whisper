package com.voiceinput.core

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Comprehensive memory management system for voice input pipeline
 *
 * Responsibilities:
 * 1. Monitor memory usage and pressure
 * 2. Respond to system memory callbacks
 * 3. Manage model loading/unloading
 * 4. Provide memory metrics for optimization
 * 5. Implement graceful degradation strategies
 */
class MemoryManager(
    private val context: Context
) : ComponentCallbacks2 {

    companion object {
        private const val TAG = "MemoryManager"
        private const val WARNING_THRESHOLD_MB = 200
        private const val CRITICAL_THRESHOLD_MB = 300
        private const val LOG_INTERVAL_MS = 30000L // Log every 30 seconds
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    // Memory tracking
    private val lastLogTime = AtomicLong(0)
    private var memoryPressureLevel = ComponentCallbacks2.TRIM_MEMORY_COMPLETE

    // Callbacks for memory pressure response
    private var onMemoryWarning: (() -> Unit)? = null
    private var onMemoryCritical: (() -> Unit)? = null
    private var onLowMemory: (() -> Unit)? = null

    init {
        // Register for system memory callbacks
        context.registerComponentCallbacks(this)
        Log.i(TAG, "MemoryManager initialized with system callbacks")
    }

    /**
     * Set callback for memory warning (suggest cleanup)
     */
    fun setMemoryWarningCallback(callback: () -> Unit) {
        onMemoryWarning = callback
    }

    /**
     * Set callback for memory critical (force cleanup)
     */
    fun setMemoryCriticalCallback(callback: () -> Unit) {
        onMemoryCritical = callback
    }

    /**
     * Set callback for low memory (emergency cleanup)
     */
    fun setLowMemoryCallback(callback: () -> Unit) {
        onLowMemory = callback
    }

    /**
     * Get current memory status with detailed breakdown
     */
    fun getMemoryStatus(): MemoryStatus {
        val runtime = Runtime.getRuntime()
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        val heapMax = runtime.maxMemory() / 1024 / 1024
        val heapTotal = runtime.totalMemory() / 1024 / 1024
        val systemAvailable = memInfo.availMem / 1024 / 1024
        val systemTotal = memInfo.totalMem / 1024 / 1024

        return MemoryStatus(
            heapUsedMB = heapUsed,
            heapMaxMB = heapMax,
            heapTotalMB = heapTotal,
            heapUtilization = (heapUsed.toFloat() / heapMax.toFloat()) * 100f,
            systemAvailableMB = systemAvailable,
            systemTotalMB = systemTotal,
            systemUtilization = ((systemTotal - systemAvailable).toFloat() / systemTotal.toFloat()) * 100f,
            isLowMemory = memInfo.lowMemory,
            memoryPressureLevel = memoryPressureLevel
        )
    }

    /**
     * Log memory status if enough time has passed (avoid spam)
     */
    fun logMemoryStatus(tag: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastLogTime.get() > LOG_INTERVAL_MS) {
            lastLogTime.set(currentTime)

            val status = getMemoryStatus()
            Log.i(TAG, "$tag - Memory Status:")
            Log.i(TAG, "  Heap: ${status.heapUsedMB}MB / ${status.heapMaxMB}MB (${String.format("%.1f", status.heapUtilization)}%)")
            Log.i(TAG, "  System: ${status.systemAvailableMB}MB available / ${status.systemTotalMB}MB total")

            // Check thresholds and warn
            if (status.heapUsedMB > CRITICAL_THRESHOLD_MB) {
                Log.w(TAG, "⚠️ Critical memory usage: ${status.heapUsedMB}MB")
            } else if (status.heapUsedMB > WARNING_THRESHOLD_MB) {
                Log.w(TAG, "⚠️ High memory usage: ${status.heapUsedMB}MB")
            }
        }
    }

    /**
     * Force immediate memory status log (for important events)
     */
    fun logMemoryStatusImmediate(tag: String) {
        val status = getMemoryStatus()
        Log.i(TAG, "$tag - Immediate Memory Check:")
        Log.i(TAG, "  Heap: ${status.heapUsedMB}MB / ${status.heapMaxMB}MB (${String.format("%.1f", status.heapUtilization)}%)")

        if (status.heapUsedMB > CRITICAL_THRESHOLD_MB) {
            Log.e(TAG, "🚨 CRITICAL: Memory usage ${status.heapUsedMB}MB exceeds ${CRITICAL_THRESHOLD_MB}MB threshold")
        }
    }

    /**
     * Check if memory usage is in warning range
     */
    fun isMemoryWarning(): Boolean {
        val status = getMemoryStatus()
        return status.heapUsedMB > WARNING_THRESHOLD_MB || status.isLowMemory
    }

    /**
     * Check if memory usage is critical
     */
    fun isMemoryCritical(): Boolean {
        val status = getMemoryStatus()
        return status.heapUsedMB > CRITICAL_THRESHOLD_MB || status.heapUtilization > 85f
    }

    /**
     * Trigger garbage collection and log results
     */
    fun requestGarbageCollection(reason: String) {
        val beforeStatus = getMemoryStatus()
        Log.i(TAG, "Requesting GC: $reason (before: ${beforeStatus.heapUsedMB}MB)")

        System.gc()

        // Wait a bit for GC to complete
        scope.launch {
            kotlinx.coroutines.delay(100)
            val afterStatus = getMemoryStatus()
            val freed = beforeStatus.heapUsedMB - afterStatus.heapUsedMB
            Log.i(TAG, "GC completed: freed ${freed}MB (now: ${afterStatus.heapUsedMB}MB)")
        }
    }

    // ComponentCallbacks2 implementation
    override fun onConfigurationChanged(newConfig: Configuration) {
        // No action needed for configuration changes
    }

    override fun onLowMemory() {
        Log.w(TAG, "🚨 System LOW MEMORY callback received")
        memoryPressureLevel = ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        onLowMemory?.invoke()
        requestGarbageCollection("System low memory")
    }

    override fun onTrimMemory(level: Int) {
        memoryPressureLevel = level

        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                Log.i(TAG, "🟡 Memory pressure: MODERATE")
                onMemoryWarning?.invoke()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                Log.w(TAG, "🟠 Memory pressure: LOW")
                onMemoryWarning?.invoke()
                requestGarbageCollection("Memory running low")
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.e(TAG, "🔴 Memory pressure: CRITICAL")
                onMemoryCritical?.invoke()
                requestGarbageCollection("Memory running critical")
            }
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                Log.d(TAG, "UI hidden - memory trim requested")
            }
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Log.w(TAG, "🚨 Background memory pressure: level $level")
                onLowMemory?.invoke()
                requestGarbageCollection("Background memory pressure")
            }
        }
    }

    /**
     * Clean up resources
     */
    fun release() {
        try {
            context.unregisterComponentCallbacks(this)
            Log.i(TAG, "MemoryManager released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MemoryManager: ${e.message}")
        }
    }
}

/**
 * Detailed memory status information
 */
data class MemoryStatus(
    val heapUsedMB: Long,
    val heapMaxMB: Long,
    val heapTotalMB: Long,
    val heapUtilization: Float,
    val systemAvailableMB: Long,
    val systemTotalMB: Long,
    val systemUtilization: Float,
    val isLowMemory: Boolean,
    val memoryPressureLevel: Int
)