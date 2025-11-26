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
import com.voiceinput.core.AudioRecorder
import com.voiceinput.core.WhisperEngine
import com.voiceinput.data.Note
import com.voiceinput.data.NotesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import android.util.Log
import android.graphics.drawable.GradientDrawable
import com.voiceinput.ime.AudioVisualizerView
import java.util.Locale
import kotlin.math.max

/**
 * Full-screen recording activity for creating voice notes
 */
class RecorderActivity : AppCompatActivity() {

    private lateinit var repository: NotesRepository
    private var audioRecorder: AudioRecorder? = null
    private var whisperEngine: WhisperEngine? = null

    private lateinit var statusText: TextView
    private lateinit var timerText: TextView
    private lateinit var transcriptionText: TextView
    private lateinit var recordButton: FrameLayout
    private lateinit var recordIcon: TextView
    private lateinit var audioVisualizer: AudioVisualizerView
    private lateinit var cancelRecordingButton: TextView
    private lateinit var bottomBar: LinearLayout
    private lateinit var saveButton: TextView

    private val readyGradientColors = intArrayOf(Color.parseColor("#5B86E5"), Color.parseColor("#36D1DC"))
    private val recordingGradientColors = intArrayOf(Color.parseColor("#FDC830"), Color.parseColor("#F37335"))
    private val processingGradientColors = intArrayOf(Color.parseColor("#8E2DE2"), Color.parseColor("#4A00E0"))
    private val recordingAccentColor = Color.parseColor("#FFE082")
    private val processingAccentColor = Color.parseColor("#B39DDB")

    private var isRecording = false
    private var currentTranscription = ""
    private var recordingStartTime: Long = 0
    private var recordingJob: Job? = null
    private var timerJob: Job? = null

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
            // Use cosmos gradient matching IME and MainActivity
            setBackgroundResource(R.drawable.cosmos_gradient)
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

            // Status + timer row
            val statusRow = LinearLayout(this@RecorderActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            statusText = TextView(this@RecorderActivity).apply {
                text = "Tap to record"
                textSize = 18f
                setTextColor(Color.parseColor("#a0a0a0"))
                gravity = Gravity.CENTER
            }

            timerText = TextView(this@RecorderActivity).apply {
                text = "00:00"
                textSize = 16f
                setTextColor(Color.parseColor("#a0a0a0"))
                setPadding(dpToPx(12), 0, 0, 0)
                gravity = Gravity.CENTER
                visibility = View.GONE
            }

            statusRow.addView(statusText)
            statusRow.addView(timerText)

            // Record button (large circle)
            recordButton = createRecordButton()

            // Visualizer + cancel alignment
            val visualizerRow = LinearLayout(this@RecorderActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(16)
                }
            }

            audioVisualizer = AudioVisualizerView(this@RecorderActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, dpToPx(56), 1f)
                alpha = 0.9f
            }

            cancelRecordingButton = TextView(this@RecorderActivity).apply {
                text = "✕"
                textSize = 20f
                setTextColor(Color.parseColor("#e0e0e0"))
                gravity = Gravity.CENTER
                contentDescription = "Cancel recording"
                layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)).apply {
                    leftMargin = dpToPx(12)
                }
                setOnClickListener {
                    handleCancelTapped()
                }
            }

            visualizerRow.addView(audioVisualizer)
            visualizerRow.addView(cancelRecordingButton)

            // Transcription text
            transcriptionText = TextView(this@RecorderActivity).apply {
                text = ""
                textSize = 16f
                setTextColor(Color.parseColor("#e0e0e0")) // Match MainActivity light gray
                gravity = Gravity.CENTER
                setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24))
                visibility = View.GONE
            }

            addView(statusRow)
            addView(recordButton)
            addView(visualizerRow)
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
            setBackgroundColor(Color.parseColor("#1a1a2e")) // Match MainActivity
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

            background = createCometDrawable(RecordingVisualState.READY)

            // Comet icon to keep the cosmic theme fun but calm
            recordIcon = TextView(this@RecorderActivity).apply {
                text = "☄"
                textSize = 44f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            addView(recordIcon)

            setOnClickListener {
                toggleRecording()
            }
        }
    }

    private enum class RecordingVisualState {
        READY,
        RECORDING,
        PROCESSING
    }

    private fun createCometDrawable(state: RecordingVisualState): GradientDrawable {
        val colors = when (state) {
            RecordingVisualState.READY -> readyGradientColors
            RecordingVisualState.RECORDING -> recordingGradientColors
            RecordingVisualState.PROCESSING -> processingGradientColors
        }

        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            shape = GradientDrawable.OVAL
        }
    }

    private fun updateRecordButtonAppearance(state: RecordingVisualState) {
        recordButton.background = createCometDrawable(state)
        val iconAlpha = when (state) {
            RecordingVisualState.READY -> 1f
            RecordingVisualState.RECORDING -> 1f
            RecordingVisualState.PROCESSING -> 0.85f
        }
        val iconColor = when (state) {
            RecordingVisualState.READY -> Color.WHITE
            RecordingVisualState.RECORDING -> Color.parseColor("#FFFAF0")
            RecordingVisualState.PROCESSING -> Color.parseColor("#EDE7F6")
        }
        recordIcon.alpha = iconAlpha
        recordIcon.setTextColor(iconColor)
    }

    private fun createBottomBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
            visibility = View.GONE

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

    private fun handleCancelTapped() {
        discardRecording()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && isRecording) {
                val elapsedMs = System.currentTimeMillis() - recordingStartTime
                val formatted = formatDuration(elapsedMs)
                timerText.text = formatted
                delay(1000)
            }
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = max(0L, durationMs / 1000L)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun startRecording() {
        if (audioRecorder == null || whisperEngine == null) {
            Toast.makeText(this, "Speech recognition not ready", Toast.LENGTH_SHORT).show()
            return
        }

        isRecording = true
        recordingStartTime = System.currentTimeMillis()

        statusText.text = "Recording"
        statusText.setTextColor(recordingAccentColor)
        timerText.apply {
            visibility = View.VISIBLE
            setTextColor(recordingAccentColor)
            text = "00:00"
        }
        startTimer()

        // Calmer comet-style appearance while recording
        updateRecordButtonAppearance(RecordingVisualState.RECORDING)

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
                    withContext(Dispatchers.Main) {
                        audioVisualizer.updateAudioData(chunk)
                    }
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
        timerJob?.cancel()
        timerJob = null

        scope.launch(Dispatchers.IO) {
            try {
                val recordedAudio = audioRecorder?.stop() ?: ByteArray(0)

                runOnUiThread {
                    statusText.text = "Processing..."
                    statusText.setTextColor(processingAccentColor)
                    updateRecordButtonAppearance(RecordingVisualState.PROCESSING)
                    audioVisualizer.clear()
                    timerText.setTextColor(processingAccentColor)
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
                            statusText.setTextColor(Color.parseColor("#e0e0e0"))
                            bottomBar.visibility = View.VISIBLE
                            timerText.setTextColor(Color.parseColor("#e0e0e0"))
                        } else {
                            statusText.text = "No speech detected"
                            statusText.setTextColor(Color.parseColor("#e0e0e0"))
                            timerText.visibility = View.GONE
                        }

                        updateRecordButtonAppearance(RecordingVisualState.READY)
                    }
                } else {
                    runOnUiThread {
                        statusText.text = "No audio recorded"
                        statusText.setTextColor(Color.parseColor("#e0e0e0"))
                        updateRecordButtonAppearance(RecordingVisualState.READY)
                        timerText.visibility = View.GONE
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
        recordingJob?.cancel()
        recordingJob = null
        timerJob?.cancel()
        timerJob = null

        if (isRecording) {
            scope.launch(Dispatchers.IO) {
                try {
                    audioRecorder?.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error cancelling recording", e)
                }
                withContext(Dispatchers.Main) {
                    resetRecording()
                    finish()
                }
            }
        } else {
            resetRecording()
            finish()
        }
    }

    private fun resetRecording() {
        isRecording = false
        currentTranscription = ""
        transcriptionText.text = ""
        transcriptionText.visibility = View.GONE
        statusText.text = "Tap to record"
        statusText.setTextColor(Color.GRAY)
        bottomBar.visibility = View.GONE
        audioVisualizer.clear()
        recordingStartTime = 0L
        timerJob?.cancel()
        timerJob = null
        timerText.apply {
            visibility = View.GONE
            text = "00:00"
            setTextColor(Color.parseColor("#a0a0a0"))
        }

        updateRecordButtonAppearance(RecordingVisualState.READY)
    }

    override fun onDestroy() {
        super.onDestroy()
        recordingJob?.cancel()
        if (isRecording) {
            audioRecorder?.stop()
        }
        audioRecorder?.release()
        whisperEngine?.release()
        timerJob?.cancel()
        scope.cancel()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
