package com.adyapan.leaddialer

import android.app.Activity
import android.os.Bundle

class CustomCameraActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        finish()
    }
}
