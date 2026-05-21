package com.adyapan.leaddialer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.SetOptions

class LoginPage : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private var btnTogglePassword: ImageButton? = null
    private lateinit var btnLogin: Button
    private lateinit var tvForgotPassword: android.widget.TextView
    private lateinit var prefs: SharedPreferences

    private val auth = FirebaseAuth.getInstance()

    private var isPasswordVisible = false

    companion object {

        const val PREF_NAME = "adyapan_prefs"

        const val KEY_IS_LOGGED_IN = "is_logged_in"

        const val KEY_EMAIL = "saved_email"

        const val KEY_EMPLOYEE_NAME = "employee_name"

        fun getEncryptedPrefs(context: Context): SharedPreferences {

            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        /**
         * Sync-before-logout:
         * 1. Push ALL pending Room data (leads + call records) to Firebase RTDB.
         * 2. Then clear local Room DB — no data loss even if < 50 calls.
         * 3. Sign out and clear prefs.
         *
         * [onSyncDone] is called on the MAIN thread after sync completes,
         * so the caller can navigate to LoginPage.
         */
        fun logout(context: Context, onSyncDone: (() -> Unit)? = null) {

            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)

                    // ── Step 1: Fetch pending data from Room ──────────────────────────
                    val pendingLeads   = db.leadDao().getAllOnce()
                    val pendingCalls   = db.callRecordDao().getAllOnce()

                    // ── Step 2: Push to Firebase RTDB (primary cloud backup) ──────────
                    if (pendingLeads.isNotEmpty()) {
                        FirestoreSource.saveLeadsBatch(pendingLeads)
                        android.util.Log.d("Logout", "Synced ${pendingLeads.size} leads to RTDB before logout")
                    }
                    if (pendingCalls.isNotEmpty()) {
                        FirestoreSource.saveCallRecordsBatch(pendingCalls)
                        android.util.Log.d("Logout", "Synced ${pendingCalls.size} call records to RTDB before logout")
                    }

                    // ── Step 3: Push to Google Sheet (TL's sheet) ─────────────────────
                    if (pendingLeads.isNotEmpty()) {
                        try {
                            SheetsSync.syncAllLeads(context, pendingLeads)
                            android.util.Log.d("Logout", "Sheet sync done before logout")
                        } catch (e: Exception) {
                            android.util.Log.e("Logout", "Sheet sync failed on logout (non-critical): ${e.message}")
                            // Non-critical — RTDB already has the data
                        }
                    }

                    // ── Step 4: Now safe to clear local Room DB ───────────────────────
                    db.leadDao().deleteAll()
                    db.callRecordDao().deleteAll()
                    // attendance stays as historical record

                } catch (e: Exception) {
                    android.util.Log.e("Logout", "Pre-logout sync failed: ${e.message}")
                    // Still proceed with logout even if sync fails
                }

                // ── Sign out & clear prefs (can run on IO) ────────────────────
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    FirebaseAuth.getInstance().signOut()
                    FirestoreSource.clearAdminStatus()
                    CalledNumbersCache.clear(context)
                    getEncryptedPrefs(context)
                        .edit()
                        .putBoolean(KEY_IS_LOGGED_IN, false)
                        .putBoolean("needs_login_sync", true)  // ← reload data from Firebase on next login
                        .remove(KEY_EMAIL)
                        .remove(KEY_EMPLOYEE_NAME)
                        .apply()

                    onSyncDone?.invoke()
                }
            }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login_page)

        prefs = getEncryptedPrefs(this)

        // Auto login

        if (
            auth.currentUser != null &&
            prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        ) {

            lifecycleScope.launch {

                routeByAdminStatus()
            }

            return
        }

        // Views

        etEmail = findViewById(R.id.etEmail)

        etPassword = findViewById(R.id.etPassword)

        btnLogin = findViewById(R.id.btnLogin)

        btnTogglePassword = findViewById(R.id.btnTogglePassword)

        tvForgotPassword = findViewById(R.id.tvForgotPassword)

        val layoutPassword = findViewById<android.view.View>(R.id.layoutPassword)
        val tvPasswordLabel = findViewById<android.widget.TextView>(R.id.tvPasswordLabel)

        setupPasswordToggle()

        setupLogin()

        // ── Entrance animations — set alpha=0 in code, NOT in XML ─────────
        val tvHeroBrand    = findViewById<android.widget.TextView>(R.id.tvHeroBrand)
        val heroAccentLine = findViewById<android.view.View>(R.id.heroAccentLine)
        val tvHeroTagline  = findViewById<android.widget.TextView>(R.id.tvHeroTagline)
        val loginCard      = findViewById<androidx.cardview.widget.CardView>(R.id.loginCard)
        val tvIndiasLargest = findViewById<android.widget.TextView>(R.id.tvIndiasLargest)
        val tvStudentCommunity = findViewById<android.widget.TextView>(R.id.tvStudentCommunity)

        // Hero elements start hidden — animated in sequence
        tvHeroBrand?.alpha        = 0f
        tvHeroBrand?.translationY = 20f
        heroAccentLine?.alpha     = 0f
        tvHeroTagline?.alpha      = 0f
        // loginCard → NO animation, visible immediately
        
        // Community text starts hidden and shifted
        tvIndiasLargest?.alpha = 0f
        tvIndiasLargest?.translationX = -300f
        
        tvStudentCommunity?.alpha = 0f
        tvStudentCommunity?.translationX = 300f

        // 1) Brand name: fade in + float up (120ms delay)
        tvHeroBrand?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setDuration(600)
            ?.setStartDelay(120)
            ?.setInterpolator(android.view.animation.DecelerateInterpolator())
            ?.start()

        // 2) Accent line: expand width 0→48dp (320ms delay)
        heroAccentLine?.post {
            val targetWidth = (48 * resources.displayMetrics.density).toInt()
            val anim = android.animation.ValueAnimator.ofInt(0, targetWidth)
            anim.duration = 500
            anim.startDelay = 320
            anim.interpolator = android.view.animation.DecelerateInterpolator()
            anim.addUpdateListener { va ->
                val lp = heroAccentLine.layoutParams
                lp.width = va.animatedValue as Int
                heroAccentLine.layoutParams = lp
            }
            anim.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: android.animation.Animator) {
                    heroAccentLine.alpha = 1f
                }
            })
            anim.start()
        }

        // 3) Tagline: fade in (560ms delay)
        tvHeroTagline?.animate()
            ?.alpha(1f)
            ?.setDuration(500)
            ?.setStartDelay(560)
            ?.setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            ?.start()

        // 4) Community Text: Slide in from left and right (400ms delay)
        tvIndiasLargest?.animate()
            ?.alpha(1f)
            ?.translationX(0f)
            ?.setDuration(600)
            ?.setStartDelay(400)
            ?.setInterpolator(android.view.animation.DecelerateInterpolator())
            ?.start()

        tvStudentCommunity?.animate()
            ?.alpha(1f)
            ?.translationX(0f)
            ?.setDuration(600)
            ?.setStartDelay(400)
            ?.setInterpolator(android.view.animation.DecelerateInterpolator())
            ?.start()

        etEmail.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!s.isNullOrEmpty() && layoutPassword.visibility != android.view.View.VISIBLE) {
                    tvPasswordLabel?.visibility = android.view.View.VISIBLE
                    layoutPassword.visibility = android.view.View.VISIBLE
                    layoutPassword.alpha = 0f
                    layoutPassword.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(250)
                        .start()
                } else if (s.isNullOrEmpty()) {
                    tvPasswordLabel?.visibility = android.view.View.GONE
                    layoutPassword.visibility = android.view.View.GONE
                }
            }
        })

        tvForgotPassword.setOnClickListener {

            showForgotPasswordDialog()
        }
    }


    private fun showForgotPasswordDialog() {

        val dialog = BottomSheetDialog(this)

        val view = layoutInflater.inflate(
            R.layout.dialog_forgot_password,
            null
        )

        dialog.setContentView(view)

        val etResetEmail =
            view.findViewById<EditText>(R.id.etResetEmail)

        val btnSendReset =
            view.findViewById<Button>(R.id.btnSendResetLink)

        val currentEmail =
            etEmail.text.toString().trim()

        if (
            currentEmail.isNotEmpty() &&
            android.util.Patterns.EMAIL_ADDRESS
                .matcher(currentEmail)
                .matches()
        ) {

            etResetEmail.setText(currentEmail)
        }

        btnSendReset.setOnClickListener {

            val email =
                etResetEmail.text.toString().trim()

            if (
                email.isEmpty() ||
                !android.util.Patterns.EMAIL_ADDRESS
                    .matcher(email)
                    .matches()
            ) {

                etResetEmail.error = "Enter a valid email"

                return@setOnClickListener
            }

            btnSendReset.isEnabled = false

            btnSendReset.text = "Sending..."

            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {

                    Toast.makeText(
                        this,
                        "Reset link sent! Check your email.",
                        Toast.LENGTH_LONG
                    ).show()

                    dialog.dismiss()
                }
                .addOnFailureListener {

                    btnSendReset.isEnabled = true

                    btnSendReset.text = "Send Reset Link"

                    Toast.makeText(
                        this,
                        "Failed: ${it.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

        dialog.show()
    }

    private fun setupLogin() {

        btnLogin.setOnClickListener {

            val email =
                etEmail.text.toString().trim()

            val password =
                etPassword.text.toString().trim()

            when {

                email.isEmpty() -> {

                    etEmail.error = "Email required"

                    etEmail.requestFocus()

                    return@setOnClickListener
                }

                !android.util.Patterns.EMAIL_ADDRESS
                    .matcher(email)
                    .matches() -> {

                    etEmail.error = "Invalid email"

                    etEmail.requestFocus()

                    return@setOnClickListener
                }

                password.isEmpty() -> {

                    etPassword.error = "Password required"

                    etPassword.requestFocus()

                    return@setOnClickListener
                }

                password.length < 6 -> {

                    etPassword.error = "Minimum 6 characters"

                    etPassword.requestFocus()

                    return@setOnClickListener
                }
            }

            btnLogin.isEnabled = false

            btnLogin.text = "..."

            auth.signInWithEmailAndPassword(
                email,
                password
            )
                .addOnSuccessListener {

                    val employeeName =
                        auth.currentUser?.displayName
                            ?.takeIf { name ->
                                name.isNotBlank()
                            }
                            ?: email.substringBefore("@")


                    prefs.edit()
                        .putBoolean(KEY_IS_LOGGED_IN, true)
                        .putString(KEY_EMAIL, email)
                        .putString(
                            KEY_EMPLOYEE_NAME,
                            employeeName
                        )
                        .apply()

                    FirebaseMessaging.getInstance().token
                        .addOnSuccessListener { token ->

                            val uid =
                                auth.currentUser?.uid
                                    ?: return@addOnSuccessListener


                            FirebaseFirestore.getInstance()
                                .collection("admins")
                                .document(uid)
                                .get()

                                .addOnSuccessListener { adminDoc ->

                                    val role =

                                        if (adminDoc.exists()) {

                                            "admin"

                                        } else {

                                            "employee"
                                        }

                                    FirebaseFirestore.getInstance()
                                        .collection("users")
                                        .document(uid)

                                        .set(

                                            mapOf(

                                                "name" to employeeName,

                                                "email" to email,

                                                "fcmToken" to token,

                                                "role" to role
                                            ),

                                            SetOptions.merge()
                                        )

                                        .addOnSuccessListener {

                                            android.util.Log.d(

                                                "FCM",

                                                "Token + Role saved successfully"
                                            )
                                        }

                                        .addOnFailureListener { e ->

                                            android.util.Log.e(

                                                "FCM",

                                                "Save failed: ${e.message}"
                                            )
                                        }
                                }
                        }



                    android.util.Log.d(
                        "EMPLOYEE_DEBUG",
                        "Saved Name = $employeeName"
                    )

                    Toast.makeText(
                        this,
                        "🔥 Let’s Close More Deals Today",
                        Toast.LENGTH_SHORT
                    ).show()

                    lifecycleScope.launch {

                        routeByAdminStatus()
                    }
                }
                .addOnFailureListener { exception ->

                    btnLogin.isEnabled = true

                    btnLogin.text = "→"

                    val errorMsg = when (exception) {

                        is FirebaseAuthInvalidUserException ->
                            "User not found ❌"

                        is FirebaseAuthInvalidCredentialsException ->
                            "Wrong password ❌"

                        else ->
                            "Login failed ❌"
                    }

                    Toast.makeText(
                        this,
                        errorMsg,
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }



    private fun setupPasswordToggle() {

        btnTogglePassword?.setOnClickListener {

            isPasswordVisible = !isPasswordVisible

            etPassword.transformationMethod =

                if (isPasswordVisible) {

                    null

                } else {

                    PasswordTransformationMethod.getInstance()
                }

            etPassword.setSelection(
                etPassword.text.length
            )
        }
    }

    // Route admin or employee

    private suspend fun routeByAdminStatus() {

        val uid = auth.currentUser?.uid

        if (uid == null) {

            goToMain()

            return
        }

        return try {

            val isAdmin = withContext(Dispatchers.IO) {

                FirebaseFirestore.getInstance()
                    .collection("admins")
                    .document(uid)
                    .get()
                    .await()
                    .exists()
            }

            FirestoreSource.setAdminStatus(isAdmin)

            if (isAdmin) {

                startActivity(
                    Intent(
                        this,
                        AdminPanelActivity::class.java
                    )
                )

            } else {

                goToMain()
            }

            finish()

        } catch (e: Exception) {

            goToMain()

            finish()
        }
    }

    // Open main

    private fun goToMain() {

        startActivity(
            Intent(
                this,
                MainActivity::class.java
            )
        )

        finish()
    }

    override fun onDestroy() {

        super.onDestroy()
    }
}