package com.whispercpp.whisper

// Deprecated: keep file to avoid breaking imports in examples; no longer used.
object WhisperCpuConfig {
    val preferredThreadCount: Int
        get() = Runtime.getRuntime().availableProcessors().coerceAtLeast(4)
}