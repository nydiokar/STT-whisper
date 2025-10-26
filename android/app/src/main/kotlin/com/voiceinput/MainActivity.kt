package com.voiceinput

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.voiceinput.data.Note
import com.voiceinput.data.NotesRepository
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main activity showing history of all voice notes
 */
class MainActivity : AppCompatActivity() {

    private lateinit var repository: NotesRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: LinearLayout
    private lateinit var adapter: NotesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = NotesRepository(this)

        // Main container
        val container = FrameLayout(this)

        // Top bar
        val topBar = createTopBar()

        // RecyclerView for notes
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = dpToPx(56) // Below top bar
            }
        }

        // Empty state view
        emptyView = createEmptyView()

        // FAB for new note
        val fab = createFab()

        container.addView(recyclerView)
        container.addView(emptyView)
        container.addView(topBar)
        container.addView(fab)

        setContentView(container)

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
            setBackgroundColor(Color.parseColor("#1E1E2E"))
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(56)
            ).apply {
                gravity = Gravity.TOP
            }

            // Title
            val title = TextView(this@MainActivity).apply {
                text = "Voice Notes"
                textSize = 20f
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            // Settings button
            val settingsButton = TextView(this@MainActivity).apply {
                text = "⚙️"
                textSize = 24f
                setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                }
            }

            addView(title)
            addView(settingsButton)
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
                topMargin = dpToPx(56) // Below top bar
            }

            val icon = TextView(this@MainActivity).apply {
                text = "🎤"
                textSize = 64f
                gravity = Gravity.CENTER
            }

            val message = TextView(this@MainActivity).apply {
                text = "No voice notes yet"
                textSize = 18f
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(16), 0, dpToPx(8))
            }

            val hint = TextView(this@MainActivity).apply {
                text = "Tap + to record your first note"
                textSize = 14f
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
            }

            addView(icon)
            addView(message)
            addView(hint)
        }
    }

    private fun createFab(): FrameLayout {
        return FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#6BA3D1"))
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(56),
                dpToPx(56)
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                rightMargin = dpToPx(24)
                bottomMargin = dpToPx(24)
            }

            // Rounded corners
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#6BA3D1"))
            }

            val plusIcon = TextView(this@MainActivity).apply {
                text = "+"
                textSize = 32f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            addView(plusIcon)

            setOnClickListener {
                startActivity(Intent(this@MainActivity, RecorderActivity::class.java))
            }
        }
    }

    private fun loadNotes() {
        val notes = repository.getAllNotes()

        if (notes.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE

            adapter = NotesAdapter(notes) { note ->
                handleNoteAction(note)
            }
            recyclerView.adapter = adapter
        }
    }

    private fun handleNoteAction(note: Note) {
        // Show delete confirmation dialog
        android.app.AlertDialog.Builder(this)
            .setTitle("Delete Note")
            .setMessage("Are you sure you want to delete this note?")
            .setPositiveButton("Delete") { _, _ ->
                repository.deleteNote(note.id)
                loadNotes()
                Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}

/**
 * RecyclerView adapter for displaying notes
 */
class NotesAdapter(
    private val notes: List<Note>,
    private val onNoteClick: (Note) -> Unit
) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
    private val expandedPositions = mutableSetOf<Int>()

    inner class NoteViewHolder(val view: LinearLayout) : RecyclerView.ViewHolder(view) {
        val previewText: TextView = view.findViewById(1)
        val timestampText: TextView = view.findViewById(2)
        val fullText: TextView = view.findViewById(3)
        val actionsRow: LinearLayout = view.findViewById(4)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val container = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E2E"))
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(8)
            }
        }

        // Preview text (first 60 chars)
        val preview = TextView(parent.context).apply {
            id = 1
            textSize = 16f
            setTextColor(Color.WHITE)
            maxLines = 2
        }

        // Timestamp
        val timestamp = TextView(parent.context).apply {
            id = 2
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, dpToPx(4), 0, 0)
        }

        // Full text (hidden by default)
        val fullText = TextView(parent.context).apply {
            id = 3
            textSize = 16f
            setTextColor(Color.WHITE)
            visibility = View.GONE
            setPadding(0, dpToPx(8), 0, 0)
        }

        // Actions row (hidden by default)
        val actionsRow = LinearLayout(parent.context).apply {
            id = 4
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            visibility = View.GONE
            setPadding(0, dpToPx(8), 0, 0)
        }

        container.addView(preview)
        container.addView(timestamp)
        container.addView(fullText)
        container.addView(actionsRow)

        return NoteViewHolder(container)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = notes[position]
        val isExpanded = expandedPositions.contains(position)

        // Show preview or full text based on expansion state
        if (isExpanded) {
            holder.previewText.visibility = View.GONE
            holder.fullText.visibility = View.VISIBLE
            holder.fullText.text = note.text
            holder.actionsRow.visibility = View.VISIBLE

            // Add action buttons if not already added
            if (holder.actionsRow.childCount == 0) {
                holder.actionsRow.addView(createActionButton(holder.view.context, "📋 Copy") {
                    copyToClipboard(holder.view.context, note.text)
                })
                holder.actionsRow.addView(createActionButton(holder.view.context, "🗑️ Delete") {
                    onNoteClick(note)
                })
            }
        } else {
            holder.previewText.visibility = View.VISIBLE
            holder.fullText.visibility = View.GONE
            holder.actionsRow.visibility = View.GONE

            val preview = if (note.text.length > 60) {
                note.text.substring(0, 60) + "..."
            } else {
                note.text
            }
            holder.previewText.text = preview
        }

        holder.timestampText.text = dateFormat.format(Date(note.createdAt))

        // Toggle expansion on click
        holder.view.setOnClickListener {
            if (expandedPositions.contains(position)) {
                expandedPositions.remove(position)
            } else {
                expandedPositions.add(position)
            }
            notifyItemChanged(position)
        }
    }

    override fun getItemCount() = notes.size

    private fun createActionButton(context: android.content.Context, text: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(Color.parseColor("#6BA3D1"))
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            setOnClickListener { onClick() }
        }
    }

    private fun copyToClipboard(context: android.content.Context, text: String) {
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        val clip = android.content.ClipData.newPlainText("Note", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
    }
}
