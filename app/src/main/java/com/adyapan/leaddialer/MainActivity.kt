package com.adyapan.leaddialer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import android.widget.Button
import android.widget.TextView
import android.widget.ImageButton
import android.widget.Toast
import android.content.Context
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
class MainActivity : AppCompatActivity(),
    NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout        : DrawerLayout
    private lateinit var navView             : NavigationView
    private lateinit var toolbar             : Toolbar
    private lateinit var bottomNav           : BottomNavigationView
    private lateinit var islandNav           : IslandNavBar
    private lateinit var fabGlobalDial       : com.google.android.material.floatingactionbutton.FloatingActionButton
    private var dialGlowAnimator             : android.animation.AnimatorSet? = null


    private lateinit var leadViewModel       : LeadViewModel
    private lateinit var callViewModel       : CallViewModel
    private lateinit var attendanceViewModel : AttendanceViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val phoneStateOk = results[Manifest.permission.READ_PHONE_STATE] ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
        val callLogOk = results[Manifest.permission.READ_CALL_LOG] ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED)
        if (phoneStateOk && callLogOk) {
            CallMonitorService.start(this)
            android.util.Log.d("MainActivity", "CallMonitorService started after permissions granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force status bar color to milky-white (prevents fitsSystemWindows DrawerLayout from drawing orange status bar background)
        window.statusBarColor = android.graphics.Color.parseColor("#F8F8F5")

        setContentView(R.layout.activity_main)

        // ── Init activity-scoped ViewModels ────────────────────────
        leadViewModel       = ViewModelProvider(this, LeadViewModelFactory(application))[LeadViewModel::class.java]
        callViewModel       = ViewModelProvider(this, CallViewModelFactory(application))[CallViewModel::class.java]
        attendanceViewModel = ViewModelProvider(this, AttendanceViewModelFactory(application))[AttendanceViewModel::class.java]

        // ── Reload data from Firebase after logout (Room DB was cleared) ──────
        // "needs_login_sync" flag is set to true in logout() and cleared here.
        // This runs only once per login session — not on every app open.
        val prefs = LoginPage.getEncryptedPrefs(this)
        if (prefs.getBoolean("needs_login_sync", false)) {
            leadViewModel.syncFromFirebaseOnce()
            callViewModel.syncCallRecordsFromFirebase()
            prefs.edit().putBoolean("needs_login_sync", false).apply()
            android.util.Log.d("MainActivity", "Post-login sync triggered")
        }


        // Prominent Disclosure before requesting sensitive permissions
        val permsNeeded = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permsNeeded += Manifest.permission.POST_NOTIFICATIONS
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permsNeeded += Manifest.permission.READ_PHONE_STATE
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            permsNeeded += Manifest.permission.READ_CALL_LOG
        }

        if (permsNeeded.isNotEmpty()) {
            if (permsNeeded.contains(Manifest.permission.READ_CALL_LOG) || permsNeeded.contains(Manifest.permission.READ_PHONE_STATE)) {
                // Show Prominent Disclosure for Call Logs and Phone State
                MaterialAlertDialogBuilder(this)
                    .setTitle("Permission Required")
                    .setMessage("Adyapan is an Enterprise CRM app. We need access to your Call Logs and Phone State to automatically track incoming and outgoing calls with leads, update their connection status, and log call durations for reporting. This data is strictly used for your internal CRM productivity and is not shared with unauthorized third parties.\n\nPlease grant these permissions on the next screen to enable automatic call tracking.")
                    .setCancelable(false)
                    .setPositiveButton("I Understand") { _, _ ->
                        requestPermissionLauncher.launch(permsNeeded.toTypedArray())
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            } else {
                requestPermissionLauncher.launch(permsNeeded.toTypedArray())
            }
        } else {
            // Already granted on start
            CallMonitorService.start(this)
            android.util.Log.d("MainActivity", "CallMonitorService started on onCreate (permissions already granted)")
        }

        // Check for SYSTEM_ALERT_WINDOW (Display over other apps)
        if (!android.provider.Settings.canDrawOverlays(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Display Over Other Apps")
                .setMessage("To show the quick-action call popup after a call ends, please enable 'Display over other apps' for Adyapan in the settings.")
                .setPositiveButton("Go to Settings") { _, _ ->
                    val intent = Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        toolbar      = findViewById(R.id.toolbar)
        drawerLayout = findViewById(R.id.drawerLayout)
        navView      = findViewById(R.id.navView)
        bottomNav    = findViewById(R.id.bottomNav)
        islandNav    = findViewById(R.id.islandNavBar)
        fabGlobalDial = findViewById(R.id.fabGlobalDial)

        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navView.setNavigationItemSelectedListener(this)

        val tvPrivacyPolicy = findViewById<TextView>(R.id.tvDrawerPrivacyPolicy)
        tvPrivacyPolicy?.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://sites.google.com/view/adyapan-crm-policy/home"))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "No browser found to open link", Toast.LENGTH_SHORT).show()
            }
        }

        FirebaseApp.initializeApp(this)

        val appCheck = FirebaseAppCheck.getInstance()
        appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
        
        val btnProfile = findViewById<ImageButton>(R.id.btnProfile)
        btnProfile.setOnClickListener {
            loadFragment(ProfileFragment(), "Profile")
        }

        if (savedInstanceState == null) {
            if (intent?.getBooleanExtra("open_leads", false) == true) {
                loadFragment(LeadsFragment(), "Leads")
                islandNav.setSelectedItemId(R.id.nav_lead)
            } else {
                loadFragment(DashboardFragment(), "Dashboard")
            }
            navView.setCheckedItem(R.id.navDashboard)
            islandNav.setSelectedItemId(R.id.nav_home)
        }

        // Start center button glow animation
        islandNav.post { islandNav.startCenterGlow() }

        // ── Draggable Global Manual Dial FAB ────────────────────────────────
        val dialContainer = findViewById<android.widget.FrameLayout>(R.id.globalDialBtnContainer)
        var dX = 0f
        var dY = 0f
        var isDragging = false

        dialContainer.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    isDragging = false
                    view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(100).start()
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val newX = event.rawX + dX
                    val newY = event.rawY + dY
                    // Only count as dragging if moved significantly
                    if (kotlin.math.abs(view.x - newX) > 10 || kotlin.math.abs(view.y - newY) > 10) {
                        isDragging = true
                    }
                    view.x = newX
                    view.y = newY
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    if (!isDragging) {
                        // Handle as a click
                        startActivity(Intent(this, DialerActivity::class.java))
                    }
                    true
                }
                else -> false
            }
        }
        
        // Pulsing glow on dial button
        startDialFabGlow()

        // ── Synced Action Bar title, BottomNav and NavDrawer with backstack changes ──
        supportFragmentManager.addOnBackStackChangedListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            android.util.Log.d("MainActivity", "BackStackChanged: currentFragment=$currentFragment")
            val container = findViewById<android.widget.FrameLayout>(R.id.fragmentContainer)
            val params = container.layoutParams as android.widget.RelativeLayout.LayoutParams
            if (currentFragment is DashboardFragment) {
                supportActionBar?.hide()
                toolbar.visibility = android.view.View.GONE
                params.topMargin = 0
            } else {
                supportActionBar?.show()
                toolbar.visibility = android.view.View.VISIBLE
                params.topMargin = toolbar.layoutParams.height
            }
            container.layoutParams = params
            when (currentFragment) {
                is DashboardFragment -> {
                    supportActionBar?.title = "Dashboard"
                    islandNav.setSelectedItemId(R.id.nav_home)
                    navView.setCheckedItem(R.id.navDashboard)
                }
                is LeadsFragment -> {
                    supportActionBar?.title = "Leads"
                    islandNav.setSelectedItemId(R.id.nav_lead)
                    navView.setCheckedItem(R.id.navLeads)
                }
                is CallHistoryFragment -> {
                    supportActionBar?.title = "Call History"
                    islandNav.setSelectedItemId(R.id.nav_center)
                    navView.setCheckedItem(R.id.navCallHistory)
                }
                is ReportsFragment -> {
                    supportActionBar?.title = "Reports"
                    navView.setCheckedItem(R.id.navReports)
                }
                is ProfileFragment -> {
                    supportActionBar?.title = "Profile"
                }
                is SettingsFragment -> {
                    supportActionBar?.title = "Settings"
                }
                is InboxFragment -> {
                    supportActionBar?.title = "💬 Inbox"
                }
                is CalendarFragment -> {
                    supportActionBar?.title = "Calendar"
                    islandNav.setSelectedItemId(R.id.nav_calendar)
                }
                is FilteredLeadsFragment -> {
                    val filterStatus = currentFragment.arguments?.getString("status") ?: ""
                    val title = when (filterStatus) {
                        "All Called"     -> "📊 All Called Leads"
                        "Wrong Number"   -> "🔢 Wrong Number"
                        "Not Connected"  -> "❌ Not Connected"
                        "Busy"           -> "📵 Busy"
                        "Interested"     -> "⭐ Interested"
                        "Not Interested" -> "👎 Not Interested"
                        "Pending"        -> "🕐 Pending"
                        "HotLead"        -> "🔥 Hot Leads"
                        "SalesDone"      -> "💰 Sales Done"
                        "Connected"      -> "📞 Connected"
                        "Total Leads"    -> "👥 Total Leads"
                        else             -> filterStatus
                    }
                    supportActionBar?.title = title
                }
            }
        }

        // ── IslandNavBar listener ────────────────────────────────────────
        islandNav.listener = object : IslandNavBar.OnNavItemSelectedListener {
            override fun onNavItemSelected(itemId: Int) {
                when (itemId) {
                    R.id.nav_home -> {
                        loadFragment(DashboardFragment(), "Dashboard")
                        navView.setCheckedItem(R.id.navDashboard)
                    }
                    R.id.nav_lead -> {
                        loadFragment(LeadsFragment(), "Leads")
                        navView.setCheckedItem(R.id.navLeads)
                    }
                    R.id.nav_center -> {
                        loadFragment(CallHistoryFragment(), "Call History")
                        navView.setCheckedItem(R.id.navCallHistory)
                    }
                    R.id.nav_calendar -> {
                        loadFragment(CalendarFragment(), "Calendar")
                    }
                    R.id.nav_attendance -> {
                        startActivity(Intent(this@MainActivity, AttendanceActivity::class.java))
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        drawerLayout.isDrawerOpen(GravityCompat.START) -> {
                            drawerLayout.closeDrawer(GravityCompat.START)
                        }
                        supportFragmentManager.backStackEntryCount > 0 -> {
                            // Reports → FilteredLeads → back = back to Reports
                            supportFragmentManager.popBackStack()
                        }
                        supportFragmentManager
                            .findFragmentById(R.id.fragmentContainer) is DashboardFragment -> {
                            finish()
                        }
                        else -> {
                            loadFragment(DashboardFragment(), "Dashboard")
                            navView.setCheckedItem(R.id.navDashboard)
                            islandNav.setSelectedItemId(R.id.nav_home)
                        }
                    }
                }
            })
    }

    // ── App background mein jaye → Firebase sync ───────────────────────
    // Jab employee app minimize kare ya phone band kare → sab data RTDB mein push
    // Admin ko fresh data milta hai bina real-time Firebase reads ke
    override fun onStop() {
        super.onStop()
        val isAdmin = FirestoreSource.isAdmin
        if (!isAdmin) {
            // Sirf employee ke liye sync — admin ka data RTDB se aata hai directly
            Log.d("MainActivity", "onStop: triggering background sync to Firebase")
            callViewModel.forceSyncNow()
        }
    }

    // ── Global call-popup handler ─────────────────────────────────────────────────
    override fun onResume() {
        super.onResume()
        window.statusBarColor = android.graphics.Color.parseColor("#F8F8F5")
        checkAndShowCallPopup()
        syncBottomNavSelection()
    }

    private fun syncBottomNavSelection() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        when (currentFragment) {
            is DashboardFragment -> {
                islandNav.setSelectedItemId(R.id.nav_home)
                navView.setCheckedItem(R.id.navDashboard)
            }
            is LeadsFragment -> {
                islandNav.setSelectedItemId(R.id.nav_lead)
                navView.setCheckedItem(R.id.navLeads)
            }
            is CallHistoryFragment -> {
                islandNav.setSelectedItemId(R.id.nav_center)
                navView.setCheckedItem(R.id.navCallHistory)
            }
            is CalendarFragment -> {
                islandNav.setSelectedItemId(R.id.nav_calendar)
            }
        }
    }

    private fun checkAndShowCallPopup() {
        // Only show here if callActive is still true AND receiver hasn't already handled it
        // (receiver sets callActive=false and clears KEY_PENDING before launching popup)
        if (!CallManager.callActive) return

        val record = CallManager.checkOnResume(this)
        Log.d("MainActivity", "checkCallPopup (fallback): record=$record")

        if (record != null) {
            CallManager.callActive = false
            val calledAtStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(record.calledAt))
            showCallStatusDialog(record, calledAtStr)
            CalledNumbersCache.clear(this)
        }
    }

    private fun showCallStatusDialog(record: CallRecord, calledAtStr: String) {
        val dialog = BottomSheetDialog(this)
        val view   = layoutInflater.inflate(R.layout.bottom_sheet_call_status, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.tvTitle).text   = "📞 Call Summary"
        view.findViewById<TextView>(R.id.tvDetails).text =
            "${record.name} • ${CallManager.formatDuration(record.duration)}"
        view.findViewById<TextView>(R.id.tvCallTime).text = "🕐 Called at: $calledAtStr"

        val lead = CallManager.currentLead

        fun saveStatus(selected: String) {
            if (lead != null) {
                if (lead.id != 0) leadViewModel.updateStatus(lead, selected)
                else leadViewModel.insert(lead.copy(status = selected, calledAt = System.currentTimeMillis()))
            }
            callViewModel.saveRecord(record.copy(status = selected))

            val appCtx = applicationContext
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val allLeads = withContext(Dispatchers.Main) { leadViewModel.allLeads.value ?: emptyList() }
                    SheetsSync.syncAllLeads(appCtx, allLeads)
                    val allRecords = AppDatabase.getInstance(appCtx).callRecordDao().getAllOnce()
                    SheetsSync.syncCallRecords(appCtx, allRecords)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Sync error: ${e.message}")
                }
            }

            dialog.dismiss()
            Toast.makeText(this, "✅ $selected", Toast.LENGTH_SHORT).show()

            // WhatsApp follow-up for Interested, Busy, Not Connected
            if (selected == "Interested" || selected == "Busy" || selected == "Not Connected") {
                val frag = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                if (frag is LeadsFragment) {
                    frag.showWhatsAppDialog(record, selected)
                } else {
                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.fragmentContainer, LeadsFragment())
                        .commitNow()
                    val newFrag = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                    if (newFrag is LeadsFragment) {
                        newFrag.showWhatsAppDialog(record, selected)
                    }
                }
            }
        }

        view.findViewById<Button>(R.id.btnConnected).setOnClickListener     { saveStatus("Wrong Number")  }
        view.findViewById<Button>(R.id.btnNotConnected).setOnClickListener  { saveStatus("Not Connected")  }
        view.findViewById<Button>(R.id.btnBusy).setOnClickListener          { saveStatus("Busy")           }
        view.findViewById<Button>(R.id.btnInterested).setOnClickListener    { saveStatus("Interested")     }
        view.findViewById<Button>(R.id.btnNotInterested).setOnClickListener { saveStatus("Not Interested") }
        view.findViewById<Button>(R.id.btnSkip).setOnClickListener          { dialog.dismiss()             }

        dialog.show()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {

            R.id.navDashboard -> {
                loadFragment(DashboardFragment(), "Dashboard")
                islandNav.setSelectedItemId(R.id.nav_home)
            }

            R.id.navLeads -> {
                loadFragment(LeadsFragment(), "Leads")
                islandNav.setSelectedItemId(R.id.nav_lead)
            }

            R.id.navCallHistory -> {
                loadFragment(CallHistoryFragment(), "Call History")
                islandNav.setSelectedItemId(R.id.nav_center)
            }

            R.id.navReports -> {
                // Clear backstack when switching to Reports from nav drawer
                supportFragmentManager.popBackStack(null,
                    androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                loadFragment(ReportsFragment(), "Reports")
            }

            R.id.navInbox -> {
                loadFragment(InboxFragment(), "💬 Inbox")
            }

            R.id.navSettings -> {
                loadFragment(SettingsFragment(), "Settings")
            }
        }

        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    internal fun loadFragment(fragment: Fragment, title: String) {
        val container = findViewById<android.widget.FrameLayout>(R.id.fragmentContainer)
        val params = container.layoutParams as android.widget.RelativeLayout.LayoutParams
        if (fragment is DashboardFragment) {
            supportActionBar?.hide()
            toolbar.visibility = android.view.View.GONE
            params.topMargin = 0
        } else {
            supportActionBar?.show()
            toolbar.visibility = android.view.View.VISIBLE
            params.topMargin = toolbar.layoutParams.height
        }
        container.layoutParams = params
        supportActionBar?.title = title
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    /** Load fragment and add to backstack (use for drill-down navigation like Reports → FilteredLeads) */
    internal fun loadFragmentWithBack(fragment: Fragment, title: String) {
        val container = findViewById<android.widget.FrameLayout>(R.id.fragmentContainer)
        val params = container.layoutParams as android.widget.RelativeLayout.LayoutParams
        if (fragment is DashboardFragment) {
            supportActionBar?.hide()
            toolbar.visibility = android.view.View.GONE
            params.topMargin = 0
        } else {
            supportActionBar?.show()
            toolbar.visibility = android.view.View.VISIBLE
            params.topMargin = toolbar.layoutParams.height
        }
        container.layoutParams = params
        supportActionBar?.title = title
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun loadFragmentPublic(fragment: Fragment, title: String) = loadFragment(fragment, title)

    fun navigateTo(navItemId: Int) {
        islandNav.setSelectedItemId(navItemId)
    }

    private fun startDialFabGlow() {
        val glowView = findViewById<android.view.View>(R.id.dialBtnGlow) ?: return

        val scaleX = android.animation.ObjectAnimator.ofFloat(glowView, "scaleX", 1f, 1.5f, 1f)
        val scaleY = android.animation.ObjectAnimator.ofFloat(glowView, "scaleY", 1f, 1.5f, 1f)
        val alpha  = android.animation.ObjectAnimator.ofFloat(glowView, "alpha", 0.5f, 0f, 0.5f)

        scaleX.duration = 1800
        scaleY.duration = 1800
        alpha.duration  = 1800

        scaleX.repeatCount = android.animation.ValueAnimator.INFINITE
        scaleY.repeatCount = android.animation.ValueAnimator.INFINITE
        alpha.repeatCount  = android.animation.ValueAnimator.INFINITE

        val set = android.animation.AnimatorSet()
        set.playTogether(scaleX, scaleY, alpha)
        set.start()
        dialGlowAnimator = set
    }


}