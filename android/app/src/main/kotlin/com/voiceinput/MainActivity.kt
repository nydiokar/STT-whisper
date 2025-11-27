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
    private lateinit var favoritesToggle: TextView
    private lateinit var sortSpinner: Spinner
    private lateinit var adapter: NotesAdapter
    private var allNotes: List<Note> = emptyList()
    private var currentQuery: String = ""
    private var showFavoritesOnly: Boolean = false
    private var sortOption: SortOption = SortOption.NEWEST

    private enum class SortOption {
        NEWEST,
        OLDEST,
        LENGTH_DESC,
        LENGTH_ASC,
        FAVORITES_FIRST
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = NotesRepository(this)
        supportActionBar?.hide()

        // Main container with cosmos gradient
        val container = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.cosmos_gradient)
        }

        // Top bar (floating controls)
        val topBar = createTopBar()

        // Search + filter row
        val searchBar = createSearchBar()

        // RecyclerView for notes
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = dpToPx(64 + 56) // floating controls offset
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
            onEdit = { note, newText -> handleNoteEdit(note, newText) },
            onFavoriteToggle = { note -> toggleFavorite(note) },
            onEditTags = { note -> editTags(note) }
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
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(64)
            ).apply {
                gravity = Gravity.TOP
                topMargin = dpToPx(16)
            }

            val iconRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dpToPx(16)
                    marginEnd = dpToPx(16)
                }
            }

            val title = TextView(this@MainActivity).apply {
                text = "Voice Input"
                textSize = 18f
                setTextColor(Color.parseColor("#e0e0e0"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            favoritesToggle = TextView(this@MainActivity).apply {
                text = "☆"
                textSize = 20f
                setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                setOnClickListener {
                    showFavoritesOnly = !showFavoritesOnly
                    updateFilterUI()
                    applyFilter()
                }
            }

            val manualNoteButton = createTopIcon("✍️") { showManualNoteDialog() }
            val exportButton = createTopIcon("📤") { exportNotes() }
            val settingsButton = createTopIcon("⚙️") {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }

            iconRow.addView(title)
            iconRow.addView(favoritesToggle)
            iconRow.addView(manualNoteButton)
            iconRow.addView(exportButton)
            iconRow.addView(settingsButton)
            addView(iconRow)
        }
    }

    private fun createTopIcon(symbol: String, action: () -> Unit): TextView {
        return TextView(this).apply {
            text = symbol
            textSize = 22f
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setOnClickListener { action() }
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
                topMargin = dpToPx(72)
                marginStart = dpToPx(16)
                marginEnd = dpToPx(16)
            }

            val searchContainer = LinearLayout(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f)
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1f1f35"))
                    cornerRadius = dpToPx(28).toFloat()
                }
                setPadding(dpToPx(16), 0, dpToPx(16), 0)
            }

            val searchIcon = TextView(this@MainActivity).apply {
                text = "🔍"
                textSize = 18f
                setPadding(0, 0, dpToPx(8), 0)
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
                setOnClickListener { searchInput.text?.clear() }
            }

            val sortContainer = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dpToPx(12)
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1f1f35"))
                    cornerRadius = dpToPx(28).toFloat()
                }
                setPadding(dpToPx(12), 0, dpToPx(12), 0)
            }

            val sortLabel = TextView(this@MainActivity).apply {
                text = "Sort"
                textSize = 14f
                setTextColor(Color.parseColor("#e0e0e0"))
                setPadding(0, 0, dpToPx(8), 0)
            }

            sortSpinner = Spinner(this@MainActivity, Spinner.MODE_DROPDOWN).apply {
                adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_item,
                    listOf("Newest", "Oldest", "Longest", "Shortest", "Favs first")
                ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                setSelection(0)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        sortOption = when (position) {
                            0 -> SortOption.NEWEST
                            1 -> SortOption.OLDEST
                            2 -> SortOption.LENGTH_DESC
                            3 -> SortOption.LENGTH_ASC
                            else -> SortOption.FAVORITES_FIRST
                        }
                        applyFilter()
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }

            searchContainer.addView(searchIcon)
            searchContainer.addView(searchInput)
            searchContainer.addView(clearButton)

            sortContainer.addView(sortLabel)
            sortContainer.addView(sortSpinner)

            addView(searchContainer)
            addView(sortContainer)
        }
    }

    private fun createFilterBar(): LinearLayout {
        // Filter content already merged into search bar row; keep placeholder for layout parity
        return LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(0, 0)
            visibility = View.GONE
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
                topMargin = dpToPx(56 + 64 + 48)
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
        allNotes = repository.getAllNotes().map { ensureDefaults(it) }
        applyFilter()
    }

    private fun applyFilter() {
        updateFilterUI()
        val query = currentQuery.trim()
        val filtered = if (query.isBlank()) {
            allNotes
        } else {
            allNotes.filter {
                it.text.contains(query, ignoreCase = true) ||
                    it.tags.orEmpty().any { tag -> tag.contains(query, ignoreCase = true) }
            }
        }

        val favoriteFiltered = if (showFavoritesOnly) {
            filtered.filter { it.isFavorite }
        } else {
            filtered
        }

        val sorted = when (sortOption) {
            SortOption.NEWEST -> favoriteFiltered.sortedByDescending { it.createdAt }
            SortOption.OLDEST -> favoriteFiltered.sortedBy { it.createdAt }
            SortOption.LENGTH_DESC -> favoriteFiltered.sortedByDescending { it.charCount }
            SortOption.LENGTH_ASC -> favoriteFiltered.sortedBy { it.charCount }
            SortOption.FAVORITES_FIRST -> favoriteFiltered.sortedWith(
                compareByDescending<Note> { it.isFavorite }
                    .thenByDescending { it.createdAt }
            )
        }

        if (sorted.isEmpty()) {
            if (allNotes.isEmpty() && currentQuery.isBlank()) {
                updateEmptyState("No voice notes yet", "Tap + to record your first note")
            } else if (allNotes.isNotEmpty() && (currentQuery.isNotBlank() || showFavoritesOnly)) {
                updateEmptyState("No matches", "Try a different filter")
            } else {
                updateEmptyState("No voice notes yet", "Tap + to record your first note")
            }
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            adapter.submitNotes(emptyList())
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            adapter.submitNotes(sorted)
        }
    }

    private fun updateFilterUI() {
        if (!::favoritesToggle.isInitialized) return
        if (showFavoritesOnly) {
            favoritesToggle.text = "★ Favorites"
            favoritesToggle.setTextColor(Color.parseColor("#FFD54F"))
            (favoritesToggle.background as? GradientDrawable)?.setColor(Color.parseColor("#3a2f1f"))
        } else {
            favoritesToggle.text = "☆ Favorites"
            favoritesToggle.setTextColor(Color.parseColor("#e0e0e0"))
            (favoritesToggle.background as? GradientDrawable)?.setColor(Color.parseColor("#2b2b45"))
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

        val options = arrayOf("Text (.txt)", "Spreadsheet (.csv)")
        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
            .setTitle("Export notes")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> shareExport(buildExportText(allNotes), "text/plain", "txt")
                    1 -> shareExport(buildExportCsv(allNotes), "text/csv", "csv")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleFavorite(note: Note) {
        val updated = note.copy(isFavorite = !note.isFavorite)
        repository.updateNote(updated)
        loadNotes()
        Toast.makeText(this, if (updated.isFavorite) "Added to favorites" else "Removed from favorites", Toast.LENGTH_SHORT).show()
    }

    private fun editTags(note: Note) {
        val input = EditText(this).apply {
            hint = "comma,separated,tags"
            setHintTextColor(Color.parseColor("#777799"))
            setTextColor(Color.parseColor("#e0e0e0"))
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            setText(note.tags.orEmpty().joinToString(", "))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1f1f35"))
                cornerRadius = dpToPx(8).toFloat()
            }
        }

        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
            .setTitle("Edit tags")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val raw = input.text?.toString() ?: ""
                val tags = raw.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val updated = note.copy(tags = tags)
                repository.updateNote(updated)
                loadNotes()
                Toast.makeText(this, "Tags updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildExportText(notes: List<Note>): String {
        val headerFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        return notes.joinToString(separator = "\n\n") { note ->
            val header = headerFormat.format(Date(note.createdAt))
            buildString {
                append(header)
                val tags = note.tags.orEmpty()
                if (tags.isNotEmpty()) {
                    append(" | Tags: ")
                    append(tags.joinToString(", "))
                }
                if (note.isFavorite) {
                    append(" | ★ Favorite")
                }
                append("\n")
                append(note.text)
            }
        }
    }

    private fun buildExportCsv(notes: List<Note>): String {
        val header = listOf("id", "created_at", "updated_at", "source", "duration_sec", "favorite", "tags", "text")
        val rows = notes.map { note ->
            listOf(
                note.id,
                note.createdAt.toString(),
                note.updatedAt.toString(),
                note.source,
                note.durationSec?.toString() ?: "",
                if (note.isFavorite) "true" else "false",
                note.tags.orEmpty().joinToString(";"),
                note.text
            )
        }
        return (listOf(header) + rows).joinToString("\n") { row ->
            row.joinToString(",") { value ->
                val escaped = value.replace("\"", "\"\"")
                "\"$escaped\""
            }
        }
    }

    private fun shareExport(content: String, mimeType: String, extension: String) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            val fileName = "voice-notes-$timestamp.$extension"
            val file = File(cacheDir, fileName).apply { writeText(content) }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_SUBJECT, "Voice notes export")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Export notes"))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export notes", Toast.LENGTH_SHORT).show()
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
            adapter.updateNote(ensureDefaults(it))
        }
        Toast.makeText(this, "Note updated", Toast.LENGTH_SHORT).show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun ensureDefaults(note: Note): Note {
        return note.copy(tags = note.tags ?: emptyList())
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
    private val onEdit: (Note, String) -> Unit,
    private val onFavoriteToggle: (Note) -> Unit,
    private val onEditTags: (Note) -> Unit
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
            val metaBits = mutableListOf<String>()
            metaBits.add("${note.charCount} chars")
            note.durationSec?.let { metaBits.add("$it sec") }
            val tagsText = note.tags.orEmpty()
            if (tagsText.isNotEmpty()) {
                metaBits.add("Tags: ${tagsText.joinToString(", ")}")
            }
            if (note.isFavorite) {
                metaBits.add("★ Favorite")
            }
            holder.metadataText.text = metaBits.joinToString(" • ")
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
            val favoriteButton = createActionButton(holder.view.context, if (note.isFavorite) "★ Unfavorite" else "☆ Favorite") {
                onFavoriteToggle(note)
            }
            holder.actionsRow.addView(favoriteButton)

            val tagButton = createActionButton(holder.view.context, "🏷 Tags") {
                onEditTags(note)
            }
            holder.actionsRow.addView(tagButton)

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
