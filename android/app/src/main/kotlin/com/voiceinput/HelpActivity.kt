package com.voiceinput

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat

/**
 * Help & Guide Activity
 * 
 * Displays user-friendly information about:
 * - How to use the Voice Input IME
 * - Recording limits and best practices
 * - Setup instructions
 * - Troubleshooting tips
 */
class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set action bar title
        supportActionBar?.title = "Voice Input Guide"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Build UI programmatically for simplicity
        val rootLayout = createHelpLayout()
        setContentView(rootLayout)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun createHelpLayout(): View {
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(ContextCompat.getColor(this@HelpActivity, android.R.color.white))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val padding = dpToPx(16)
            setPadding(padding, padding, padding, padding)
        }

        // Header
        container.addView(createHeaderText("🎙️ Voice Input Keyboard"))
        container.addView(createBodyText("Dictate text hands-free using on-device AI. Private, secure, and works offline."))
        container.addView(createSpacer(24))

        // Quick Start
        container.addView(createCard(
            "🚀 Quick Start",
            """
            1. Tap any text field to show the keyboard
            2. Tap the microphone button to start
            3. Speak clearly into your device
            4. Tap again to stop and transcribe
            5. Your text appears instantly!
            """.trimIndent()
        ))

        // Recording Limits
        container.addView(createCard(
            "⏱️ Recording Limits",
            """
            • Maximum Duration: 7 minutes
            • Warning: Last 30 seconds (timer turns red)
            • Optimal Length: 30-90 seconds
            • Processing Time: scales with length (chunked)
            
            💡 Tip: Long recordings are chunked automatically; shorter ones process faster.
            """.trimIndent()
        ))

        // Best Practices
        container.addView(createCard(
            "✅ Best Practices",
            """
            • Speak at a natural, moderate pace
            • Use in quiet environments
            • Hold device 6-12 inches from mouth
            • Break long dictation into segments
            • Wait for "Ready" before recording again
            
            ⚠️ Background noise can affect accuracy
            """.trimIndent()
        ))

        // Visual Indicators
        container.addView(createCard(
            "🎨 What the Icons Mean",
            """
            ✅ Green Mic = Ready to record
            🔴 Red Mic = Currently recording
            ⏳ Hourglass = Transcribing audio
            ❌ Red Status = Error (tap to retry)
            
            Timer shows elapsed time during recording
            Last 30 seconds show countdown warning
            """.trimIndent()
        ))

        // Setup Instructions
        container.addView(createCard(
            "⚙️ Setup Instructions",
            """
            First Time Setup:
            
            1. Go to Settings → System → Languages & Input
            2. Tap "On-screen keyboard"
            3. Tap "Manage keyboards"
            4. Enable "Voice Input"
            5. Tap any text field
            6. Tap keyboard switcher icon (⌨️)
            7. Select "Voice Input"
            
            The first launch may take 5-10 seconds to load AI models.
            """.trimIndent()
        ))

        // Troubleshooting
        container.addView(createCard(
            "🔧 Troubleshooting",
            """
            "Initializing..." appears too long?
            → Wait 5-10 seconds (first-time model loading)
            
            "Failed to initialize audio recorder"?
            → Grant microphone permission in Settings
            → Close other apps using microphone
            
            "No speech detected"?
            → Speak louder or reduce background noise
            → Ensure recording is at least 1 second
            
            "Max duration reached"?
            → Record in shorter segments if you hit the limit
            
            Text doesn't appear?
            → Text field may have closed - tap field again
            """.trimIndent()
        ))

        // Privacy
        container.addView(createCard(
            "🔐 Privacy & Security",
            """
            100% On-Device Processing
            → All transcription happens locally
            → No internet required
            → No data sent to servers
            → Your voice never leaves your device
            
            Models: Whisper Small (161 MB)
            Acceleration: APU/NNAPI + CPU fallback
            """.trimIndent()
        ))

        // Performance Tips
        container.addView(createCard(
            "⚡ Performance Tips",
            """
            For faster transcription:
            • Shorter recordings process faster
            • Use newer devices with APU/NPU
            • Close background apps
            • Charge device during heavy use
            
            Expected processing times:
            • 10 seconds → ~2 seconds
            • 20 seconds → ~3-4 seconds
            • 30 seconds → ~5 seconds
            • 60 seconds → ~10 seconds
            """.trimIndent()
        ))

        // Footer
        container.addView(createSpacer(16))
        container.addView(createBodyText("Need more help? Check device logs with 'adb logcat -s VoiceInputIME'", 
            android.graphics.Typeface.ITALIC))

        scrollView.addView(container)
        return scrollView
    }

    private fun createCard(title: String, content: String): CardView {
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dpToPx(16))
            }
            radius = dpToPx(8).toFloat()
            cardElevation = dpToPx(4).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(this@HelpActivity, android.R.color.white))
        }

        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dpToPx(16)
            setPadding(padding, padding, padding, padding)
        }

        // Title
        cardContent.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@HelpActivity, android.R.color.black))
        })

        cardContent.addView(createSpacer(8))

        // Content
        cardContent.addView(TextView(this).apply {
            text = content
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@HelpActivity, android.R.color.darker_gray))
            // lineHeight requires API 28, use setLineSpacing for compatibility
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                lineHeight = (textSize * 1.5).toInt()
            } else {
                setLineSpacing(textSize * 0.5f, 1.0f)  // additive spacing for API < 28
            }
        })

        card.addView(cardContent)
        return card
    }

    private fun createHeaderText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@HelpActivity, android.R.color.black))
        }
    }

    private fun createBodyText(text: String, style: Int = android.graphics.Typeface.NORMAL): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            setTypeface(null, style)
            setTextColor(ContextCompat.getColor(this@HelpActivity, android.R.color.darker_gray))
        }
    }

    private fun createSpacer(heightDp: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(heightDp)
            )
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }
}

