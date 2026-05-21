package com.adyapan.leaddialer

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.animation.AnimationUtils
import android.view.animation.ScaleAnimation
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.LinearLayout
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SplashActivity : AppCompatActivity() {

    private var routeReady = false
    private var splashEnded = false

    private var isAdminUser = false
    private var isLoggedIn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔥 Root Detection
        if (isRooted()) {

            Toast.makeText(
                this,
                "Security policy prevents app usage on rooted devices",
                Toast.LENGTH_LONG
            ).show()

            finish()
            return
        }

        setContentView(R.layout.activity_splash)

        // ✨ Entrance Animations
        playEntranceAnimations()

        lifecycleScope.launch {

            // 🔥 Device Validation
            validateActiveDevice()

            // 🔥 Admin Check
            checkAdminStatus()
        }

        Handler(Looper.getMainLooper()).postDelayed({

            splashEnded = true
            tryNavigate()

        }, 1800)
    }

    // ✨ Smooth Entrance Animations
    private fun playEntranceAnimations() {
        val mascot = findViewById<ImageView>(R.id.splashMascot)
        val glow = findViewById<ImageView>(R.id.glowBehind)
        val brandGroup = findViewById<LinearLayout>(R.id.brandGroup)
        val bottomGroup = findViewById<LinearLayout>(R.id.bottomGroup)
        val dot1 = findViewById<View>(R.id.dot1)
        val dot2 = findViewById<View>(R.id.dot2)
        val dot3 = findViewById<View>(R.id.dot3)

        val fadeInUpBottom = AnimationUtils.loadAnimation(this, R.anim.fade_in_up).also {
            it.startOffset = 600
        }

        // Glow fade in — warm pulse behind mascot
        glow.alpha = 0f
        glow.animate().alpha(0.85f).setDuration(900).setStartDelay(100).start()

        // Mascot: fade + scale in smoothly
        mascot.alpha = 0f
        mascot.scaleX = 0.85f
        mascot.scaleY = 0.85f
        mascot.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(700)
            .setStartDelay(80)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.1f))
            .start()

        // Bottom group fade up
        bottomGroup.startAnimation(fadeInUpBottom)

        // Staggered dot pulse
        animateDotPulse(dot1, 0)
        animateDotPulse(dot2, 250)
        animateDotPulse(dot3, 500)
    }

    private fun animateDotPulse(dot: View, delayMs: Long) {
        val pulse = AlphaAnimation(0.2f, 1.0f).apply {
            duration = 600
            startOffset = delayMs
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        }
        dot.startAnimation(pulse)
    }

    // 🔥 Root Detection
    private fun isRooted(): Boolean {

        return listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/app/Superuser.apk",
            "/system/app/Magisk.apk"
        ).any { File(it).exists() }
    }

    // 🔥 One Device Login Validation
    private suspend fun validateActiveDevice() {

        val user = FirebaseAuth.getInstance().currentUser ?: return

        val currentDeviceId = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        )

        try {

            val firestore = FirebaseFirestore.getInstance()

            val doc = firestore
                .collection("users")
                .document(user.uid)
                .get()
                .await()

            val savedDeviceId = doc.getString("activeDeviceId")

            // 🔥 First Login
            if (savedDeviceId.isNullOrEmpty()) {

                firestore.collection("users")
                    .document(user.uid)
                    .set(
                        mapOf(
                            "activeDeviceId" to currentDeviceId
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .await()

                return
            }

            // 🔥 Another Device Login
            if (savedDeviceId != currentDeviceId) {

                FirebaseAuth.getInstance().signOut()

                Toast.makeText(
                    this,
                    "Your account is active on another device",
                    Toast.LENGTH_LONG
                ).show()

                startActivity(
                    Intent(this, LoginPage::class.java)
                )

                finish()
            }

        } catch (e: Exception) {

            e.printStackTrace()
        }
    }

    // 🔥 Admin Check
    private suspend fun checkAdminStatus() {

        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            isLoggedIn = false
            routeReady = true
            withContext(Dispatchers.Main) { tryNavigate() }
            return
        }

        isLoggedIn = true

        try {
            val firestore = FirebaseFirestore.getInstance()

            // ✅ 5-second timeout so splash never hangs forever when offline.
            // If timeout or error → default to non-admin so employee can still log in.
            val doc = withTimeoutOrNull(5_000L) {
                firestore
                    .collection("admins")
                    .document(user.uid)
                    .get()          // default: cache-first → no offline hang
                    .await()
            }

            isAdminUser = doc?.exists() ?: false
            FirestoreSource.setAdminStatus(isAdminUser)

        } catch (e: Exception) {
            isAdminUser = false
        }

        routeReady = true
        withContext(Dispatchers.Main) { tryNavigate() }
    }

    @Synchronized
    private fun tryNavigate() {

        if (!splashEnded || !routeReady) return

        val pref = getSharedPreferences("app", MODE_PRIVATE)

        when {

            // 🔥 FIRST TIME
            pref.getBoolean("firstTime", true) -> {

                startActivity(
                    Intent(this, OnboardingActivity::class.java)
                )
            }

            // 🔥 NOT LOGGED IN
            !isLoggedIn -> {

                startActivity(
                    Intent(this, LoginPage::class.java)
                )
            }

            // 🔥 ADMIN
            isAdminUser -> {

                startActivity(
                    Intent(this, AdminPanelActivity::class.java)
                )
            }

            // 🔥 NORMAL USER
            else -> {

                startActivity(
                    Intent(this, MainActivity::class.java)
                )
            }
        }

        finish()
    }
}