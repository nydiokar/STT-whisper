package com.voiceinput.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.voiceinput.R
import com.voiceinput.config.ConfigRepository
import com.voiceinput.core.*
import kotlinx.coroutines.*

/**
 * Background service for voice input transcription
 * Designed to support IME and standalone operation
 *
 * Architecture:
 * - Foreground service for background transcription
 * - Lifecycle-aware resource management
 * - Memory-efficient operation
 * - Proper cleanup and error handling
 * - Integration with IME and app UI
 */
class VoiceInputService : Service() {

    companion object {
        private const val TAG = "VoiceInputService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "voice_input_channel"
        private const val CHANNEL_NAME = "Voice Input Processing"

        // Service actions
        const val ACTION_START_TRANSCRIPTION = "START_TRANSCRIPTION"
        const val ACTION_STOP_TRANSCRIPTION = "STOP_TRANSCRIPTION"
        const val ACTION_TOGGLE_TRANSCRIPTION = "TOGGLE_TRANSCRIPTION"
    }

    // Service binder for local connections
    inner class VoiceInputBinder : Binder() {
        fun getService(): VoiceInputService = this@VoiceInputService
    }

    private val binder = VoiceInputBinder()
    private var serviceScope: CoroutineScope? = null

    // Core components
    private var voicePipeline: VoiceInputPipeline? = null
    private var audioRecorder: AudioRecorder? = null
    private var whisperEngine: WhisperEngine? = null
    private var memoryManager: MemoryManager? = null

    // State
    private var isTranscribing = false
    private var isInitialized = false

    // Configuration
    private lateinit var configRepository: ConfigRepository
    private var currentConfig: com.voiceinput.config.AppConfig? = null

    // Callbacks for transcription results
    private var transcriptionCallback: ((TranscriptionResult) -> Unit)? = null
    private var statusCallback: ((ServiceStatus) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "VoiceInputService created")

        serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        configRepository = ConfigRepository(this)
        currentConfig = configRepository.load()

        setupMemoryManagement()
        createNotificationChannel()
        initializeComponents()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRANSCRIPTION -> startTranscription()
            ACTION_STOP_TRANSCRIPTION -> stopTranscription()
            ACTION_TOGGLE_TRANSCRIPTION -> toggleTranscription()
        }
        return START_STICKY // Restart if killed
    }

    /**
     * Initialize core components with memory management
     */
    private fun initializeComponents() {
        serviceScope?.launch {
            try {
                Log.i(TAG, "Initializing service components...")

                // Initialize audio recorder
                audioRecorder = AudioRecorder()

                // Initialize Whisper engine (ONNX Runtime)
                whisperEngine = WhisperEngine(this@VoiceInputService)
                val initSuccess = whisperEngine!!.initialize()

                if (initSuccess) {
                    // Initialize voice pipeline
                    voicePipeline = VoiceInputPipeline(
                        context = this@VoiceInputService,
                        audioRecorder = audioRecorder!!,
                        whisperEngine = whisperEngine!!,
                        config = currentConfig!!,
                        onResult = { result ->
                            handleTranscriptionResult(result)
                        }
                    )

                    isInitialized = true
                    Log.i(TAG, "Service components initialized successfully")
                    notifyStatusChange(ServiceStatus.READY)
                } else {
                    Log.e(TAG, "Failed to initialize Whisper engine")
                    notifyStatusChange(ServiceStatus.ERROR)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing service components", e)
                notifyStatusChange(ServiceStatus.ERROR)
            }
        }
    }

    /**
     * Set up memory management with service-specific callbacks
     */
    private fun setupMemoryManagement() {
        memoryManager = MemoryManager(this).apply {
            setMemoryWarningCallback {
                Log.w(TAG, "Service memory warning - optimizing")
                if (!isTranscribing) {
                    // Suggest cleanup when idle
                    requestGarbageCollection("Service memory warning")
                }
            }

            setMemoryCriticalCallback {
                Log.e(TAG, "Service memory critical - emergency measures")
                if (isTranscribing) {
                    // Could implement emergency pause
                    Log.w(TAG, "Critical memory during transcription - monitoring")
                }
                requestGarbageCollection("Service critical memory")
            }

            setLowMemoryCallback {
                Log.e(TAG, "Service low memory - emergency response")
                // Emergency cleanup
                requestGarbageCollection("Service low memory emergency")
            }
        }
    }

    /**
     * Start transcription with foreground service
     */
    private fun startTranscription() {
        if (!isInitialized) {
            Log.w(TAG, "Cannot start transcription - service not initialized")
            return
        }

        if (isTranscribing) {
            Log.w(TAG, "Transcription already running")
            return
        }

        serviceScope?.launch {
            try {
                // Start foreground service
                startForeground(NOTIFICATION_ID, createNotification("Starting transcription..."))

                // Start voice pipeline
                voicePipeline?.startListening()
                isTranscribing = true

                Log.i(TAG, "Transcription started")
                notifyStatusChange(ServiceStatus.TRANSCRIBING)
                updateNotification("Listening for speech...")

            } catch (e: Exception) {
                Log.e(TAG, "Error starting transcription", e)
                notifyStatusChange(ServiceStatus.ERROR)
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
    }

    /**
     * Stop transcription
     */
    private fun stopTranscription() {
        if (!isTranscribing) return

        serviceScope?.launch {
            try {
                val finalText = voicePipeline?.stopListening() ?: ""
                isTranscribing = false

                Log.i(TAG, "Transcription stopped. Final text length: ${finalText.length}")
                notifyStatusChange(ServiceStatus.READY)

                stopForeground(STOP_FOREGROUND_REMOVE)

            } catch (e: Exception) {
                Log.e(TAG, "Error stopping transcription", e)
            }
        }
    }

    /**
     * Toggle transcription state
     */
    private fun toggleTranscription() {
        if (isTranscribing) {
            stopTranscription()
        } else {
            startTranscription()
        }
    }

    /**
     * Handle transcription results from pipeline
     */
    private fun handleTranscriptionResult(result: TranscriptionResult) {
        Log.d(TAG, "Transcription result: '${result.text}'")

        // Update notification with recent text
        val preview = result.text.take(30) + if (result.text.length > 30) "..." else ""
        updateNotification("Transcribed: $preview")

        // Notify listeners
        transcriptionCallback?.invoke(result)
    }

    /**
     * Notify status change to listeners
     */
    private fun notifyStatusChange(status: ServiceStatus) {
        statusCallback?.invoke(status)
    }

    /**
     * Create notification channel for Android O+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Voice input transcription notifications"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create service notification
     */
    private fun createNotification(content: String): Notification {
        val stopIntent = Intent(this, VoiceInputService::class.java).apply {
            action = ACTION_STOP_TRANSCRIPTION
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Input Active")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher) // You'll need to add a proper icon
            .setOngoing(true)
            .addAction(R.mipmap.ic_launcher, "Stop", stopPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Update existing notification
     */
    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    // Public API for binding clients

    /**
     * Set transcription result callback
     */
    fun setTranscriptionCallback(callback: (TranscriptionResult) -> Unit) {
        transcriptionCallback = callback
    }

    /**
     * Set service status callback
     */
    fun setStatusCallback(callback: (ServiceStatus) -> Unit) {
        statusCallback = callback
    }

    /**
     * Get current service status
     */
    fun getStatus(): ServiceStatus {
        return when {
            !isInitialized -> ServiceStatus.INITIALIZING
            isTranscribing -> ServiceStatus.TRANSCRIBING
            else -> ServiceStatus.READY
        }
    }

    /**
     * Get pipeline status if available
     */
    fun getPipelineStatus(): PipelineStatus? {
        return voicePipeline?.getStatus()
    }

    /**
     * Update service configuration
     */
    fun updateConfiguration(newConfig: com.voiceinput.config.AppConfig) {
        currentConfig = newConfig
        configRepository.save(newConfig)
        voicePipeline?.updateSettings(newConfig)
        Log.i(TAG, "Service configuration updated")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "VoiceInputService destroyed")

        serviceScope?.launch {
            try {
                // Stop transcription if running
                if (isTranscribing) {
                    voicePipeline?.stopListening()
                }

                // Release all resources
                voicePipeline?.release()
                memoryManager?.release()

                serviceScope?.cancel()

            } catch (e: Exception) {
                Log.e(TAG, "Error during service cleanup", e)
            }
        }
    }
}

/**
 * Service status enumeration
 */
enum class ServiceStatus {
    INITIALIZING,
    READY,
    TRANSCRIBING,
    ERROR
}