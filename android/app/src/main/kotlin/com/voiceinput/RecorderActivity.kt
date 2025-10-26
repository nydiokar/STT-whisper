package com.voiceinput

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.voiceinput.config.AppConfig
import com.voiceinput.core.AudioRecorder
import com.voiceinput.core.WhisperEngine
import com.voiceinput.data.Note
import com.voiceinput.data.NotesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import android.util.Log

/**
 * Full-screen recording activity for creating voice notes
 */
class RecorderActivity : AppCompatActivity() {

    private lateinit var repository: NotesRepository
    private var audioRecorder: AudioRecorder? = null
    private var whisperEngine: WhisperEngine? = null

    private lateinit var statusText: TextView
    private lateinit var transcriptionText: TextView
    private lateinit var recordButton: FrameLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var saveButton: TextView
    private lateinit var discardButton: TextView

    private var isRecording = false
    private var currentTranscription = ""
    private var recordingStartTime: Long = 0
    private var recordingJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val TAG = "RecorderActivity"
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = NotesRepository(this)

        val container = createUI()
        setContentView(container)

        // Check microphone permission
        checkPermission()

        // Initialize components
        initializeComponents()
    }

    private fun initializeComponents() {
        scope.launch {
            try {
                Log.i(TAG, "Initializing components...")
                audioRecorder = AudioRecorder()
                whisperEngine = WhisperEngine(this@RecorderActivity)
                val initSuccess = whisperEngine?.initialize() ?: false

                if (initSuccess) {
                    Log.i(TAG, "Components initialized successfully")
                } else {
                    Log.e(TAG, "Failed to initialize Whisper engine")
                    runOnUiThread {
                        Toast.makeText(this@RecorderActivity, "Failed to initialize speech recognition", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing components", e)
                runOnUiThread {
                    Toast.makeText(this@RecorderActivity, "Initialization error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun createUI(): FrameLayout {
        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0F0F1A"))
        }

        // Top bar with back button
        val topBar = createTopBar()

        // Center content
        val centerContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = dpToPx(56)
                bottomMargin = dpToPx(80)
            }

            // Status text
            statusText = TextView(this@RecorderActivity).apply {
                text = "Tap to record"
                textSize = 18f
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
            }

            // Record button (large circle)
            recordButton = createRecordButton()

            // Transcription text
            transcriptionText = TextView(this@RecorderActivity).apply {
                text = ""
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))
                visibility = View.GONE
            }

            addView(statusText)
            addView(recordButton)
            addView(transcriptionText)
        }

        // Bottom action buttons
        bottomBar = createBottomBar()

        container.addView(centerContent)
        container.addView(topBar)
        container.addView(bottomBar)

        return container
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

            // Back button
            val backButton = TextView(this@RecorderActivity).apply {
                text = "←"
                textSize = 24f
                setTextColor(Color.WHITE)
                setPadding(dpToPx(8), dpToPx(8), dpToPx(16), dpToPx(8))
                setOnClickListener {
                    finish()
                }
            }

            // Title
            val title = TextView(this@RecorderActivity).apply {
                text = "New Voice Note"
                textSize = 20f
                setTextColor(Color.WHITE)
            }

            addView(backButton)
            addView(title)
        }
    }

    private fun createRecordButton(): FrameLayout {
        return FrameLayout(this).apply {
            val size = dpToPx(120)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                topMargin = dpToPx(32)
                bottomMargin = dpToPx(32)
            }

            // Outer circle
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#6BA3D1"))
            }

            // Mic icon
            val micIcon = TextView(this@RecorderActivity).apply {
                text = "🎤"
                textSize = 48f
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            addView(micIcon)

            setOnClickListener {
                toggleRecording()
            }
        }
    }

    private fun createBottomBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
            visibility = View.GONE

            // Discard button
            discardButton = TextView(this@RecorderActivity).apply {
                text = "Discard"
                textSize = 16f
                setTextColor(Color.parseColor("#FF6B6B"))
                setPadding(dpToPx(24), dpToPx(12), dpToPx(24), dpToPx(12))
                setOnClickListener {
                    discardRecording()
                }
            }

            // Spacer
            val spacer = View(this@RecorderActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            }

            // Save button
            saveButton = TextView(this@RecorderActivity).apply {
                text = "Save"
                textSize = 16f
                setTextColor(Color.parseColor("#6BA3D1"))
                setPadding(dpToPx(24), dpToPx(12), dpToPx(24), dpToPx(12))
                setOnClickListener {
                    saveRecording()
                }
            }

            addView(discardButton)
            addView(spacer)
            addView(saveButton)
        }
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Microphone permission required", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun toggleRecording() {
        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        if (audioRecorder == null || whisperEngine == null) {
            Toast.makeText(this, "Speech recognition not ready", Toast.LENGTH_SHORT).show()
            return
        }

        isRecording = true
        recordingStartTime = System.currentTimeMillis()

        statusText.text = "Recording..."
        statusText.setTextColor(Color.parseColor("#FF6B6B"))

        // Change button to red square
        recordButton.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#FF6B6B"))
            cornerRadius = dpToPx(16).toFloat()
        }

        val recorder = audioRecorder ?: return
        val started = recorder.start()

        if (!started) {
            Toast.makeText(this, "Failed to start recording", Toast.LENGTH_SHORT).show()
            resetRecording()
            return
        }

        // Collect audio chunks in background
        recordingJob = scope.launch {
            try {
                recorder.audioStream().collect { chunk ->
                    // Audio is being collected by AudioRecorder
                    // We just need to keep the stream alive
                    Log.d(TAG, "Collected audio chunk: ${chunk.size} bytes")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Recording error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RecorderActivity, "Recording error: ${e.message}", Toast.LENGTH_SHORT).show()
                    resetRecording()
                }
            }
        }
    }

    private fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        scope.launch(Dispatchers.IO) {
            try {
                val recordedAudio = audioRecorder?.stop() ?: ByteArray(0)

                runOnUiThread {
                    statusText.text = "Processing..."
                    statusText.setTextColor(Color.GRAY)
                }

                // Transcribe the audio
                if (recordedAudio.isNotEmpty()) {
                    val result = whisperEngine?.transcribe(recordedAudio)

                    runOnUiThread {
                        if (result != null && result.text.isNotEmpty()) {
                            currentTranscription = result.text
                            transcriptionText.text = result.text
                            transcriptionText.visibility = View.VISIBLE
                            statusText.text = "Recording complete"
                            bottomBar.visibility = View.VISIBLE
                        } else {
                            statusText.text = "No speech detected"
                        }

                        // Reset button appearance
                        recordButton.background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(Color.parseColor("#6BA3D1"))
                        }
                    }
                } else {
                    runOnUiThread {
                        statusText.text = "No audio recorded"
                        recordButton.background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(Color.parseColor("#6BA3D1"))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Stop error", e)
                runOnUiThread {
                    Toast.makeText(this@RecorderActivity, "Processing error: ${e.message}", Toast.LENGTH_SHORT).show()
                    resetRecording()
                }
            }
        }
    }

    private fun saveRecording() {
        if (currentTranscription.isEmpty()) {
            Toast.makeText(this, "No transcription to save", Toast.LENGTH_SHORT).show()
            return
        }

        val durationSec = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
        val note = Note(
            text = currentTranscription,
            source = "stt",
            durationSec = durationSec
        )

        repository.saveNote(note)
        Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun discardRecording() {
        resetRecording()
        finish()
    }

    private fun resetRecording() {
        isRecording = false
        currentTranscription = ""
        transcriptionText.text = ""
        transcriptionText.visibility = View.GONE
        statusText.text = "Tap to record"
        statusText.setTextColor(Color.GRAY)
        bottomBar.visibility = View.GONE

        recordButton.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(Color.parseColor("#6BA3D1"))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recordingJob?.cancel()
        if (isRecording) {
            audioRecorder?.stop()
        }
        audioRecorder?.release()
        whisperEngine?.release()
        scope.cancel()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
