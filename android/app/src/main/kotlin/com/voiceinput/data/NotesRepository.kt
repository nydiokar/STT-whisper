package com.voiceinput.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Simple repository for storing notes using SharedPreferences
 *
 * For MVP, we use SharedPreferences with JSON serialization.
 * Can be replaced with Room database later if needed.
 */
class NotesRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val gson = Gson()

    /**
     * Save a new note
     */
    fun saveNote(note: Note) {
        val notes = getAllNotes().toMutableList()
        notes.add(0, note) // Add to beginning (newest first)
        saveNotes(notes)
    }

    /**
     * Get all notes (sorted by creation date, newest first)
     */
    fun getAllNotes(): List<Note> {
        val json = prefs.getString(KEY_NOTES, null) ?: return emptyList()
        val type = object : TypeToken<List<Note>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    /**
     * Get a note by ID
     */
    fun getNoteById(id: String): Note? {
        return getAllNotes().find { it.id == id }
    }

    /**
     * Update an existing note
     */
    fun updateNote(note: Note) {
        val notes = getAllNotes().toMutableList()
        val index = notes.indexOfFirst { it.id == note.id }
        if (index != -1) {
            notes[index] = note.copy(updatedAt = System.currentTimeMillis())
            saveNotes(notes)
        }
    }

    /**
     * Delete a note
     */
    fun deleteNote(id: String) {
        val notes = getAllNotes().toMutableList()
        notes.removeIf { it.id == id }
        saveNotes(notes)
    }

    /**
     * Search notes by text
     */
    fun searchNotes(query: String): List<Note> {
        if (query.isBlank()) return getAllNotes()
        return getAllNotes().filter {
            it.text.contains(query, ignoreCase = true)
        }
    }

    /**
     * Clear all notes
     */
    fun clearAll() {
        prefs.edit().remove(KEY_NOTES).apply()
    }

    private fun saveNotes(notes: List<Note>) {
        val json = gson.toJson(notes)
        prefs.edit().putString(KEY_NOTES, json).apply()
    }

    companion object {
        private const val PREFS_NAME = "voice_notes"
        private const val KEY_NOTES = "notes_list"
    }
}
