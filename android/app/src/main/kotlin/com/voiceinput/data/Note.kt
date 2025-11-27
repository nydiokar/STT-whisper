package com.voiceinput.data

import java.util.UUID

/**
 * Represents a voice note transcription
 */
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val source: String = "stt", // "stt" or "manual"
    val charCount: Int = text.length,
    val durationSec: Int? = null, // Only for STT notes
    val isFavorite: Boolean = false,
    val tags: List<String>? = emptyList()
)
