package com.voiceinput

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.voiceinput.data.Note
import com.voiceinput.data.NotesRepository
import java.text.SimpleDateFormat
import java.util.*
import java.io.File

/**
 * Main activity showing history of all voice notes
 *
 * Styling matches IME cosmos theme:
 * - Background: cosmos_gradient (#0f0c29 → #302b63 → #24243e)
 * - Cards: #1a1a2e with subtle glow
 * - Text: #e0e0e0 (light gray)
 * - Accent: #4CAF50 (green)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var repository: NotesRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: LinearLayout
    private lateinit var emptyMessage: TextView
    private lateinit var emptyHint: TextView
    private lateinit var searchInput: EditText
    private lateinit var adapter: NotesAdapter
    private var allNotes: List<Note> = emptyList()
    private var currentQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = NotesRepository(this)
        supportActionBar?.hide()

        // Main container with cosmos gradient
        val container = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.cosmos_gradient)
        }

        // Top bar
        val topBar = createTopBar()

        // Search bar
        val searchBar = createSearchBar()

        // RecyclerView for notes
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = dpToPx(56 + 64) // Top bar + search bar
            }
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(88))
            clipToPadding = false
        }

        // Empty state view
        emptyView = createEmptyView()

        // FAB for new note - green circle with mic icon
        val fab = createFab()

        container.addView(recyclerView)
        container.addView(emptyView)
        container.addView(topBar)
        container.addView(searchBar)
        container.addView(fab)

        setContentView(container)

        adapter = NotesAdapter(
            onDelete = { note -> confirmDelete(note) },
            onShare = { text -> shareNote(text) },
            onEdit = { note, newText -> handleNoteEdit(note, newText) }
        )
        recyclerView.adapter = adapter

        loadNotes()
    }

    override fun onResume() {
        super.onResume()
        loadNotes()
    }

    private fun createTopBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1a1a2e")) // Slightly lighter than bg
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(56)
            ).apply {
                gravity = Gravity.TOP
            }

            // App title
            val title = TextView(this@MainActivity).apply {
                text = "Voice Input"
                textSize = 18f
                setTextColor(Color.parseColor("#e0e0e0")) // Light gray
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val manualNoteButton = TextView(this@MainActivity).apply {
                text = "✍️"
                textSize = 22f
                setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                setOnClickListener { showManualNoteDialog() }
            }

            val exportButton = TextView(this@MainActivity).apply {
                text = "📤"
                textSize = 22f
                setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                setOnClickListener { exportNotes() }
            }

            val settingsButton = TextView(this@MainActivity).apply {
                text = "⚙️"
                textSize = 22f
                setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                }
            }

            addView(title)
            addView(manualNoteButton)
            addView(exportButton)
            addView(settingsButton)
        }
    }

    private fun createSearchBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(56)
            ).apply {
                topMargin = dpToPx(56)
                marginStart = dpToPx(16)
                marginEnd = dpToPx(16)
            }

            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#1f1f35"))
                cornerRadius = dpToPx(28).toFloat()
            }

            val searchIcon = TextView(this@MainActivity).apply {
                text = "🔍"
                textSize = 18f
                setPadding(dpToPx(16), 0, dpToPx(8), 0)
            }

            searchInput = EditText(this@MainActivity).apply {
                hint = "Search notes"
                setHintTextColor(Color.parseColor("#777799"))
                setTextColor(Color.parseColor("#e0e0e0"))
                background = null
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        currentQuery = s?.toString() ?: ""
                        applyFilter()
                    }
                })
            }

            val clearButton = TextView(this@MainActivity).apply {
                text = "✕"
                textSize = 16f
                alpha = 0.6f
                setPadding(dpToPx(12), 0, dpToPx(16), 0)
                setOnClickListener {
                    searchInput.text?.clear()
                }
            }

            addView(searchIcon)
            addView(searchInput)
            addView(clearButton)
        }
    }

    private fun createEmptyView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = dpToPx(56 + 64)
            }

            val icon = TextView(this@MainActivity).apply {
                text = "🎤"
                textSize = 64f
                gravity = Gravity.CENTER
            }

            val title = TextView(this@MainActivity).apply {
                text = "Voice Notes"
                textSize = 22f
                setTextColor(Color.parseColor("#e0e0e0"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(24), 0, dpToPx(8))
            }

            emptyMessage = TextView(this@MainActivity).apply {
                text = "No voice notes yet"
                textSize = 16f
                setTextColor(Color.parseColor("#a0a0a0"))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dpToPx(8))
            }

            emptyHint = TextView(this@MainActivity).apply {
                text = "Tap + to record your first note"
                textSize = 14f
                setTextColor(Color.parseColor("#808080"))
                gravity = Gravity.CENTER
            }

            addView(icon)
            addView(title)
            addView(emptyMessage)
            addView(emptyHint)
        }
    }

    private fun createFab(): FrameLayout {
        return FrameLayout(this).apply {
            val size = dpToPx(56)
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                rightMargin = dpToPx(24)
                bottomMargin = dpToPx(24)
            }

            // Green circle (matching IME ready state)
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#4CAF50")) // Green accent
            }

            // Mic icon
            val micIcon = TextView(this@MainActivity).apply {
                text = "🎤"
                textSize = 28f
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            addView(micIcon)

            setOnClickListener {
                startActivity(Intent(this@MainActivity, RecorderActivity::class.java))
            }
        }
    }

    private fun loadNotes() {
        allNotes = repository.getAllNotes()
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = if (currentQuery.isBlank()) {
            allNotes
        } else {
            allNotes.filter { it.text.contains(currentQuery, ignoreCase = true) }
        }

        if (filtered.isEmpty()) {
            if (allNotes.isEmpty() && currentQuery.isBlank()) {
                updateEmptyState("No voice notes yet", "Tap + to record your first note")
            } else if (allNotes.isNotEmpty() && currentQuery.isNotBlank()) {
                updateEmptyState("No matches", "Try a different search")
            } else {
                updateEmptyState("No voice notes yet", "Tap + to record your first note")
            }
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            adapter.submitNotes(emptyList())
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            adapter.submitNotes(filtered)
        }
    }

    private fun updateEmptyState(message: String, hint: String) {
        emptyMessage.text = message
        emptyHint.text = hint
    }

    private fun confirmDelete(note: Note) {
        // Show delete confirmation dialog with cosmos theme
        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
            .setTitle("Delete Note")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                repository.deleteNote(note.id)
                loadNotes()
                Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareNote(text: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Voice note")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(shareIntent, "Share note"))
    }

    private fun showManualNoteDialog() {
        val input = EditText(this).apply {
            hint = "Type your note"
            setHintTextColor(Color.parseColor("#777799"))
            setTextColor(Color.parseColor("#e0e0e0"))
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            minLines = 3
            gravity = Gravity.TOP
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#1f1f35"))
                cornerRadius = dpToPx(8).toFloat()
            }
        }

        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
            .setTitle("New text note")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val text = input.text?.toString()?.trim() ?: ""
                if (text.isEmpty()) {
                    Toast.makeText(this, "Note cannot be empty", Toast.LENGTH_SHORT).show()
                } else {
                    val note = Note(text = text, source = "manual")
                    repository.saveNote(note)
                    loadNotes()
                    Toast.makeText(this, "Note added", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportNotes() {
        if (allNotes.isEmpty()) {
            Toast.makeText(this, "No notes to export", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val exportText = buildExportText(allNotes)
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            val fileName = "voice-notes-$timestamp.txt"
            val file = File(cacheDir, fileName)
            file.writeText(exportText)

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Voice notes export")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Export notes"))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export notes", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildExportText(notes: List<Note>): String {
        val headerFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        return notes.joinToString(separator = "\n\n") { note ->
            val header = headerFormat.format(Date(note.createdAt))
            "$header\n${note.text}"
        }
    }

    private fun handleNoteEdit(note: Note, newText: String) {
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) {
            Toast.makeText(this, "Note cannot be empty", Toast.LENGTH_SHORT).show()
            adapter.refreshNote(note)
            return
        }

        if (trimmed == note.text) {
            adapter.refreshNote(note)
            return
        }

        val updated = note.copy(text = trimmed, charCount = trimmed.length)
        repository.updateNote(updated)
        repository.getNoteById(updated.id)?.let {
            adapter.updateNote(it)
        }
        Toast.makeText(this, "Note updated", Toast.LENGTH_SHORT).show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}

/**
 * RecyclerView adapter for displaying notes
 *
 * Cards styled to match IME:
 * - Background: #1a1a2e
 * - Subtle glow effect
 * - Proper spacing
 */
class NotesAdapter(
    private val onDelete: (Note) -> Unit,
    private val onShare: (String) -> Unit,
    private val onEdit: (Note, String) -> Unit
) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
    private val expandedNoteIds = mutableSetOf<String>()
    private val editingNoteIds = mutableSetOf<String>()
    private val notes = mutableListOf<Note>()

    inner class NoteViewHolder(val view: LinearLayout) : RecyclerView.ViewHolder(view) {
        val previewText: TextView = view.findViewById(1)
        val timestampText: TextView = view.findViewById(2)
        val fullText: EditText = view.findViewById(3)
        val metadataText: TextView = view.findViewById(4)
        val actionsRow: LinearLayout = view.findViewById(5)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val container = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            // Card background matching IME
            setBackgroundColor(Color.parseColor("#1a1a2e"))

            // Add subtle glow effect
            elevation = parent.context.resources.displayMetrics.density * 4

            // Rounded corners
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dpToPx(8).toFloat())
                }
            }

            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(12) // Space between cards
            }
        }

        // Preview text (first 60 chars)
        val preview = TextView(parent.context).apply {
            id = 1
            textSize = 15f
            setTextColor(Color.parseColor("#e0e0e0")) // Light gray
            maxLines = 2
            setLineSpacing(dpToPx(2).toFloat(), 1f)
        }

        // Timestamp
        val timestamp = TextView(parent.context).apply {
            id = 2
            textSize = 12f
            setTextColor(Color.parseColor("#a0a0a0")) // Dimmer gray
            setPadding(0, dpToPx(6), 0, 0)
        }

        // Full text (hidden by default)
        val fullText = EditText(parent.context).apply {
            id = 3
            textSize = 15f
            setTextColor(Color.parseColor("#e0e0e0"))
            visibility = View.GONE
            setPadding(0, dpToPx(8), 0, 0)
            setLineSpacing(dpToPx(2).toFloat(), 1f)
            isFocusable = false
            isFocusableInTouchMode = false
            isCursorVisible = false
            background = null
        }

        // Metadata (char count + duration) - hidden by default
        val metadata = TextView(parent.context).apply {
            id = 4
            textSize = 11f
            setTextColor(Color.parseColor("#808080"))
            visibility = View.GONE
            setPadding(0, dpToPx(6), 0, 0)
        }

        // Actions row (hidden by default)
        val actionsRow = LinearLayout(parent.context).apply {
            id = 5
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            visibility = View.GONE
            setPadding(0, dpToPx(12), 0, 0)
        }

        container.addView(preview)
        container.addView(timestamp)
        container.addView(fullText)
        container.addView(metadata)
        container.addView(actionsRow)

        return NoteViewHolder(container)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = notes[position]
        val isExpanded = expandedNoteIds.contains(note.id)
        val isEditing = editingNoteIds.contains(note.id)
        holder.fullText.setText(note.text)

        // Show preview or full text based on expansion state
        if (isExpanded) {
            holder.previewText.visibility = View.GONE
            holder.fullText.visibility = View.VISIBLE
            holder.metadataText.visibility = View.VISIBLE
            holder.metadataText.text = "${note.charCount} chars" +
                if (note.durationSec != null) " • ${note.durationSec} sec" else ""
            holder.actionsRow.visibility = View.VISIBLE
            holder.fullText.isFocusable = isEditing
            holder.fullText.isFocusableInTouchMode = isEditing
            holder.fullText.isCursorVisible = isEditing
            holder.fullText.isEnabled = true
            if (!isEditing) {
                holder.fullText.clearFocus()
            } else {
                holder.fullText.post {
                    if (editingNoteIds.contains(note.id)) {
                        if (!holder.fullText.hasFocus()) {
                            holder.fullText.requestFocus()
                        }
                        holder.fullText.setSelection(holder.fullText.text?.length ?: 0)
                        showKeyboard(holder.fullText)
                    }
                }
            }

            holder.actionsRow.removeAllViews()
            val editButton = createActionButton(holder.view.context, if (isEditing) "✅ Done" else "✏️ Edit") {}
            holder.actionsRow.addView(editButton)
            holder.fullText.tag = editButton
            editButton.setOnClickListener {
                if (editingNoteIds.contains(note.id)) {
                    completeInlineEdit(holder, note, editButton)
                } else {
                    beginInlineEdit(holder, note, editButton)
                }
            }
            val shareButton = createActionButton(holder.view.context, "📤 Share") {
                val currentText = holder.fullText.text?.toString() ?: note.text
                if (editingNoteIds.contains(note.id)) {
                    completeInlineEdit(holder, note, holder.fullText.tag as? TextView)
                }
                onShare(currentText)
            }
            holder.actionsRow.addView(shareButton)
            val deleteButton = createActionButton(holder.view.context, "🗑️ Delete") {
                onDelete(note)
            }
            holder.actionsRow.addView(deleteButton)
        } else {
            holder.previewText.visibility = View.VISIBLE
            holder.fullText.visibility = View.GONE
            holder.metadataText.visibility = View.GONE
            holder.actionsRow.visibility = View.GONE
            holder.fullText.tag = null

            val preview = if (note.text.length > 60) {
                note.text.substring(0, 60) + "..."
            } else {
                note.text
            }
            holder.previewText.text = preview
        }

        holder.timestampText.text = dateFormat.format(Date(note.createdAt))

        holder.fullText.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            val button = v.tag as? TextView
            if (!hasFocus && editingNoteIds.contains(note.id)) {
                completeInlineEdit(holder, note, button)
            }
        }

        // Toggle expansion on click
        holder.view.setOnClickListener {
            if (expandedNoteIds.contains(note.id)) {
                expandedNoteIds.remove(note.id)
                val button = holder.fullText.tag as? TextView
                if (editingNoteIds.contains(note.id)) {
                    completeInlineEdit(holder, note, button)
                } else {
                    holder.fullText.clearFocus()
                }
            } else {
                expandedNoteIds.add(note.id)
            }
            notifyItemChanged(position)
        }
    }

    override fun getItemCount() = notes.size

    fun submitNotes(newNotes: List<Note>) {
        notes.clear()
        notes.addAll(newNotes)
        val currentIds = notes.map { it.id }.toSet()
        expandedNoteIds.retainAll(currentIds)
        editingNoteIds.retainAll(currentIds)
        notifyDataSetChanged()
    }

    fun updateNote(updated: Note) {
        val index = notes.indexOfFirst { it.id == updated.id }
        if (index != -1) {
            notes[index] = updated
            notifyItemChanged(index)
        }
    }

    fun refreshNote(note: Note) {
        val index = notes.indexOfFirst { it.id == note.id }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }

    private fun createActionButton(context: android.content.Context, text: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.parseColor("#4CAF50")) // Green accent
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            setOnClickListener { onClick() }

            // Button background
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#2a2a3e")) // Slightly lighter than card
                cornerRadius = dpToPx(4).toFloat()
            }

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dpToPx(8)
            }
        }
    }

    private fun beginInlineEdit(holder: NoteViewHolder, note: Note, editButton: TextView) {
        if (!editingNoteIds.add(note.id)) return
        editButton.text = "✅ Done"
        holder.fullText.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            isCursorVisible = true
            requestFocus()
            post {
                setSelection(text?.length ?: 0)
                showKeyboard(this)
            }
        }
    }

    private fun completeInlineEdit(holder: NoteViewHolder, note: Note, editButton: TextView?) {
        if (!editingNoteIds.contains(note.id)) return
        editingNoteIds.remove(note.id)
        editButton?.text = "✏️ Edit"
        holder.fullText.isCursorVisible = false
        hideKeyboard(holder.fullText)
        holder.fullText.clearFocus()
        val updatedText = holder.fullText.text?.toString() ?: ""
        onEdit(note, updatedText)
    }

    private fun showKeyboard(view: View) {
        val imm = view.context.getSystemService(InputMethodManager::class.java)
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard(view: View) {
        val imm = view.context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
    }
}
