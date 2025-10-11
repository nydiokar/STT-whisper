package com.voiceinput

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.voiceinput.config.ConfigRepository

/**
 * Settings Activity for Voice Input IME
 * 
 * This activity is launched from the IME settings in the system keyboard settings.
 * It allows users to configure the voice input behavior and access help.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set action bar title
        supportActionBar?.title = "Voice Input Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Create UI
        val rootLayout = createSettingsLayout()
        setContentView(rootLayout)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun createSettingsLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.white))
            val padding = dpToPx(16)
            setPadding(padding, padding, padding, padding)

            // Header
            addView(TextView(this@SettingsActivity).apply {
                text = "🎙️ Voice Input"
                textSize = 24f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.black))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, dpToPx(24))
                }
            })

            // Help Button Card
            addView(createCard(
                "📖 Help & Guide",
                "Learn how to use the voice keyboard, best practices, limits, and troubleshooting tips."
            ) {
                startActivity(Intent(this@SettingsActivity, HelpActivity::class.java))
            })

            // Current Settings Info
            val config = ConfigRepository(this@SettingsActivity).load()
            addView(createInfoCard(
                "⚙️ Current Settings",
                """
                Model: Whisper Small INT8 (161 MB)
                Language: ${config.transcription.language}
                Max Duration: 60 seconds
                Sample Rate: ${config.audio.sampleRate} Hz
                VAD Threshold: ${config.audio.vadThreshold}
                Acceleration: APU/NNAPI + CPU fallback
                """.trimIndent()
            ))

            // Coming Soon
            addView(createInfoCard(
                "🚧 Coming Soon",
                """
                • Model selection (Tiny/Base/Small/Medium)
                • Multi-language support
                • Adjustable recording duration
                • VAD sensitivity settings
                • Custom prompts for accuracy
                • Streaming transcription
                """.trimIndent()
            ))

            // Spacer
            addView(android.view.View(this@SettingsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            })

            // Footer
            addView(TextView(this@SettingsActivity).apply {
                text = "For developers: adb logcat -s VoiceInputIME"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.darker_gray))
            })
        }
    }

    private fun createCard(title: String, description: String, onClick: () -> Unit): CardView {
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dpToPx(16))
            }
            radius = dpToPx(8).toFloat()
            cardElevation = dpToPx(4).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.white))
            isClickable = true
            isFocusable = true
            foreground = ContextCompat.getDrawable(this@SettingsActivity, android.R.attr.selectableItemBackground)
            setOnClickListener { onClick() }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dpToPx(16)
            setPadding(padding, padding, padding, padding)
        }

        content.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.black))
        })

        content.addView(TextView(this).apply {
            text = description
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.darker_gray))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8)
            }
        })

        card.addView(content)
        return card
    }

    private fun createInfoCard(title: String, content: String): CardView {
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dpToPx(16))
            }
            radius = dpToPx(8).toFloat()
            cardElevation = dpToPx(2).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(this@SettingsActivity, 0xFFF5F5F5.toInt()))
        }

        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dpToPx(16)
            setPadding(padding, padding, padding, padding)
        }

        cardContent.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.black))
        })

        cardContent.addView(TextView(this).apply {
            text = content
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.darker_gray))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(8)
            }
        })

        card.addView(cardContent)
        return card
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }
}
