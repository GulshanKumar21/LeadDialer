package com.adyapan.leaddialer

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import android.content.Intent
import android.text.SpannableString
import android.text.style.StyleSpan
import android.text.Spannable
import android.graphics.Typeface
import androidx.appcompat.app.AlertDialog
import kotlin.math.abs

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager  : ViewPager2
    private lateinit var btnNext    : Button
    private lateinit var btnPrev    : Button
    private lateinit var btnSkip    : TextView
    private lateinit var dotsLayout : LinearLayout
    private lateinit var dots       : Array<ImageView?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if already seen onboarding
        val pref = getSharedPreferences("app", MODE_PRIVATE)
        if (!pref.getBoolean("firstTime", true)) {
            startActivity(Intent(this, LoginPage::class.java))
            finish()
            return
        }

        // Full-screen immersive
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        setContentView(R.layout.activity_onboarding)

        viewPager   = findViewById(R.id.viewPager)
        btnNext     = findViewById(R.id.btnNext)
        btnPrev     = findViewById(R.id.btnPrev)
        btnSkip     = findViewById(R.id.btnSkip)
        dotsLayout  = findViewById(R.id.dotsLayout)

        // ── 5 onboarding pages ─────────────────────────────────────────────
        val list = listOf(
            OnboardingItem(
                image       = R.drawable.onboard_1_leads1,
                title       = "Smart Lead Management",
                description = "Organize all your student leads in one place. Assign, track, and never miss a follow-up — all from your phone.",
                accentColor = "#FF6A00"
            ),
            OnboardingItem(
                image       = R.drawable.onboard_2_calling2,
                title       = "One-Tap Power Dialing",
                description = "Call any lead with a single tap. Get automatic call logging, status updates, and WhatsApp follow-ups instantly.",
                accentColor = "#0EA5E9"
            ),
            OnboardingItem(
                image       = R.drawable.onboard_3_attandance2,
                title       = "Selfie Attendance System",
                description = "Mark attendance with a selfie — GPS verified, device locked. On time or late, every record is accurate.",
                accentColor = "#8B5CF6"
            ),
            OnboardingItem(
                image       = R.drawable.onboard_4_report1,
                title       = "Live Reports & Insights",
                description = "Watch your team's performance in real time. Track calls, leads, conversions, and monthly goals with beautiful charts.",
                accentColor = "#10B981"
            ),
            OnboardingItem(
                image       = R.drawable.onboarding_5_welcome1,
                title       = "Welcome to Adyapan CRM!",
                description = "India's smart CRM for education teams. Built to help you reach more students, close more admissions, every day.",
                accentColor = "#F59E0B"
            )
        )

        viewPager.adapter = OnboardingAdapter(list)
        createDots(list.size)
        updateDots(0)

        btnPrev.visibility = View.INVISIBLE

        // ── Cinematic page transition ──────────────────────────────────────
        viewPager.setPageTransformer { page, position ->
            val absPos = abs(position)
            page.alpha = 1f - (absPos * 0.4f)
            page.scaleX = 1f - (0.08f * absPos)
            page.scaleY = 1f - (0.08f * absPos)
            page.translationX = -position * page.width * 0.15f
        }

        // ── Button listeners ──────────────────────────────────────────────
        btnSkip.setOnClickListener { goToLogin(pref) }

        btnNext.setOnClickListener {
            if (viewPager.currentItem < list.size - 1) {
                viewPager.currentItem += 1
            } else {
                goToLogin(pref)
            }
        }

        btnPrev.setOnClickListener {
            if (viewPager.currentItem > 0) {
                viewPager.currentItem -= 1
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateDots(position)
                btnPrev.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
                btnNext.text = if (position == list.size - 1) "Get Started" else "Next →"
            }
        })
    }

    private fun goToLogin(pref: android.content.SharedPreferences) {
        // Google Play MANDATORY: Show Prominent Disclosure before sensitive permission request
        showProminentDisclosure {
            pref.edit().putBoolean("firstTime", false).apply()
            startActivity(Intent(this, LoginPage::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }

    /**
     * Google Play Prominent Disclosure — MANDATORY before requesting:
     * READ_CALL_LOG, READ_PHONE_STATE, ACCESS_FINE_LOCATION, CAMERA
     */
    private fun showProminentDisclosure(onAccept: () -> Unit) {
        val msg = "Adyapan CRM requires certain device permissions to function as an enterprise tool:\n\n" +
            "Call Log & Phone State — To automatically track calls made to CRM leads and support dual-SIM routing. Only calls to registered leads are logged.\n\n" +
            "Location — To verify your physical presence when clocking in for attendance. No background tracking.\n\n" +
            "Camera — To capture a selfie for enterprise attendance verification.\n\n" +
            "Notifications — To receive real-time CRM alerts and lead updates.\n\n" +
            "This data is used exclusively for internal enterprise operations and is never sold or shared with third parties. See Settings > Privacy Policy for full details."

        AlertDialog.Builder(this)
            .setTitle("Data & Permissions Notice")
            .setMessage(msg)
            .setCancelable(false)
            .setPositiveButton("I Understand & Accept") { _, _ -> onAccept() }
            .setNegativeButton("Exit App") { _, _ -> finishAffinity() }
            .show()
    }

    private fun updateDots(position: Int) {
        for (i in dots.indices) {
            dots[i]?.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    if (i == position) R.drawable.active_dot else R.drawable.inactive_dot
                )
            )
        }
    }

    private fun createDots(size: Int) {
        dots = arrayOfNulls(size)
        dotsLayout.removeAllViews()

        for (i in 0 until size) {
            dots[i] = ImageView(this)
            dots[i]?.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.inactive_dot))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(10, 0, 10, 0)
            dots[i]?.layoutParams = params
            dotsLayout.addView(dots[i])
        }
    }
}