package com.voiceinput

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create layout programmatically (consistent with other activities)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64) // 16dp converted to pixels
        }
        
        val textView = TextView(this).apply {
            text = "Voice Input App"
            textSize = 24f
        }
        
        layout.addView(textView)
        setContentView(layout)
    }
}