package com.adyapan.leaddialer

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AdminEmployeeLeavesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra("name").orEmpty().ifBlank { "Employee" }
        setContentView(TextView(this).apply {
            text = "$name leaves"
            textSize = 20f
            setPadding(32, 48, 32, 32)
        })
    }
}
