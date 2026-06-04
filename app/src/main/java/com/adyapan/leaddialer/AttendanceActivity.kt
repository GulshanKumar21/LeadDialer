package com.adyapan.leaddialer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class AttendanceActivity : AppCompatActivity() {

    private lateinit var btn: Button
    private lateinit var btnViewHistory: Button
    private lateinit var status: TextView
    private lateinit var subStatus: TextView

    // Calendar views
    private lateinit var tvCalMonth: TextView
    private lateinit var btnCalPrev: ImageButton
    private lateinit var btnCalNext: ImageButton
    private lateinit var llDayHeaders: LinearLayout
    private lateinit var llCalendarGrid: LinearLayout

    private var lat = 0.0
    private var lng = 0.0

    private val officeLat = 17.405616940380334
    private val officeLng = 78.4052024209847
    private val allowedRadius = 100

    private var isCheckedIn = false

    // Calendar state
    private var calendarMonth = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    private var userDesignation = ""

    // Map of "yyyy-MM-dd" → attendance status
    private val attendanceMap = mutableMapOf<String, String>() // status: Present/Late/Absent

    private val deviceId: String by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
            if (bitmap != null) {
                val base64 = encodeBitmapToBase64(bitmap)
                if (isCheckedIn) saveCheckOut(base64) else saveCheckIn(base64)
            } else {
                Toast.makeText(this, "Selfie capture cancelled", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        btn             = findViewById(R.id.btnAttendance)
        btnViewHistory  = findViewById(R.id.btnViewHistory)
        status          = findViewById(R.id.txtStatus)
        subStatus       = findViewById(R.id.txtSubStatus)

        tvCalMonth      = findViewById(R.id.tvCalMonth)
        btnCalPrev      = findViewById(R.id.btnCalPrev)
        btnCalNext      = findViewById(R.id.btnCalNext)
        llDayHeaders    = findViewById(R.id.llDayHeaders)
        llCalendarGrid  = findViewById(R.id.llCalendarGrid)

        btn.isEnabled = false

        btnViewHistory.setOnClickListener {
            startActivity(android.content.Intent(this, EmployeeAttendanceActivity::class.java))
        }

        // Load user designation & attendance records
        loadUserData()

        // Calendar nav
        btnCalPrev.setOnClickListener {
            calendarMonth.add(Calendar.MONTH, -1)
            renderCalendar()
        }
        btnCalNext.setOnClickListener {
            val now = Calendar.getInstance()
            if (calendarMonth.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                calendarMonth.get(Calendar.MONTH) == now.get(Calendar.MONTH)) return@setOnClickListener
            calendarMonth.add(Calendar.MONTH, 1)
            renderCalendar()
        }

        buildDayHeaders()
        renderCalendar()
        checkPermission()
    }

    // ── Load designation + attendance history from Firestore ─────────────
    private fun loadUserData() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Designation
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                userDesignation = doc.getString("designation") ?: ""
                renderCalendar()
            }

        // All attendance dates for this user
        FirebaseFirestore.getInstance()
            .collection("attendance").document(uid).collection("dates").get()
            .addOnSuccessListener { snap ->
                attendanceMap.clear()
                for (doc in snap.documents) {
                    val dateKey = doc.id // "yyyy-MM-dd"
                    val st = doc.getString("status") ?: "Present"
                    attendanceMap[dateKey] = st
                }
                renderCalendar()
            }
    }

    // ── Role-based weekly off ─────────────────────────────────────────────
    private fun isWeeklyOff(cal: Calendar): Boolean {
        val d = userDesignation.lowercase()
        return if (d.contains("community developer"))
            cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY
        else
            cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
    }

    // ── Day header row (Sun Mon … Sat) ───────────────────────────────────
    private fun buildDayHeaders() {
        val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        llDayHeaders.removeAllViews()
        for (d in days) {
            val tv = TextView(this).apply {
                text = d
                gravity = Gravity.CENTER
                textSize = 11f
                setTextColor(Color.parseColor("#9CA3AF"))
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            llDayHeaders.addView(tv)
        }
    }

    // ── Render calendar grid ──────────────────────────────────────────────
    private fun renderCalendar() {
        val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val keyFmt   = SimpleDateFormat("yyyy-MM-dd",  Locale.getDefault())
        val todayKey = keyFmt.format(Date())
        val now      = Calendar.getInstance()

        tvCalMonth.text = monthFmt.format(calendarMonth.time)

        // Disable Next if already on current month
        btnCalNext.alpha = if (
            calendarMonth.get(Calendar.YEAR)  == now.get(Calendar.YEAR) &&
            calendarMonth.get(Calendar.MONTH) == now.get(Calendar.MONTH)
        ) 0.3f else 1.0f

        val firstDay   = calendarMonth.clone() as Calendar
        firstDay.set(Calendar.DAY_OF_MONTH, 1)
        val startDow   = firstDay.get(Calendar.DAY_OF_WEEK) - 1  // 0=Sun
        val daysInMonth = calendarMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        val totalCells  = startDow + daysInMonth
        val rows        = Math.ceil(totalCells / 7.0).toInt()

        llCalendarGrid.removeAllViews()

        for (row in 0 until rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            for (col in 0 until 7) {
                val cellIndex = row * 7 + col
                val dayNum    = cellIndex - startDow + 1

                if (dayNum < 1 || dayNum > daysInMonth) {
                    // Empty cell
                    rowLayout.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, dpToPx(38), 1f)
                    })
                    continue
                }

                val dateCal = (calendarMonth.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, dayNum)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
                }
                val dateKey  = keyFmt.format(dateCal.time)
                val isToday  = dateKey == todayKey
                val isFuture = dateCal.after(now)
                val isOff    = isWeeklyOff(dateCal)
                val recStatus = attendanceMap[dateKey]

                // ── Cell background & text color ───────────────────────
                val bgColor: Int
                val txtColor: Int
                when {
                    isOff && !isFuture -> { bgColor = Color.parseColor("#F3F4F6"); txtColor = Color.parseColor("#9CA3AF") }
                    isFuture           -> { bgColor = Color.TRANSPARENT;            txtColor = Color.parseColor("#D1D5DB") }
                    recStatus == "Present" -> { bgColor = Color.parseColor("#DCFCE7"); txtColor = Color.parseColor("#22C55E") }
                    recStatus == "Late"    -> { bgColor = Color.parseColor("#FEF3C7"); txtColor = Color.parseColor("#F59E0B") }
                    recStatus != null      -> { bgColor = Color.parseColor("#FEE2E2"); txtColor = Color.parseColor("#EF4444") }
                    else -> { // Past, no record = absent
                        bgColor  = Color.parseColor("#FEF2F2"); txtColor = Color.parseColor("#FCA5A5")
                    }
                }

                val tv = TextView(this).apply {
                    text = "$dayNum"
                    gravity = Gravity.CENTER
                    textSize = 12f
                    setTextColor(if (isToday) Color.parseColor("#4285F4") else txtColor)
                    typeface = if (isToday) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    setBackgroundColor(bgColor)
                    setPadding(4, 8, 4, 8)
                    layoutParams = LinearLayout.LayoutParams(0, dpToPx(38), 1f).apply {
                        setMargins(2, 2, 2, 2)
                    }
                    // Blue border for today
                    if (isToday) {
                        background = androidx.core.content.res.ResourcesCompat.getDrawable(
                            resources, R.drawable.bg_calendar_today, theme
                        )
                    }
                    // Click: show bottom sheet for that date
                    if (!isFuture) {
                        isClickable = true
                        isFocusable = true
                        setOnClickListener { showDayDetail(dateCal, dateKey, isOff, recStatus) }
                    }
                }
                rowLayout.addView(tv)
            }
            llCalendarGrid.addView(rowLayout)
        }
    }

    // ── Bottom sheet: date detail ─────────────────────────────────────────
    private fun showDayDetail(
        cal: Calendar,
        dateKey: String,
        isOff: Boolean,
        recStatus: String?
    ) {
        val sheet = BottomSheetDialog(this)
        val view  = layoutInflater.inflate(R.layout.dialog_day_attendance_detail, null)
        sheet.setContentView(view)

        val displayFmt = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
        view.findViewById<TextView>(R.id.tvDayDetailDate).text = "📅 ${displayFmt.format(cal.time)}"

        val tvStatus    = view.findViewById<TextView>(R.id.tvDayDetailStatus)
        val tvCheckIn   = view.findViewById<TextView>(R.id.tvDayDetailCheckIn)
        val tvCheckOut  = view.findViewById<TextView>(R.id.tvDayDetailCheckOut)
        val tvWeeklyOff = view.findViewById<TextView>(R.id.tvDayDetailWeeklyOff)

        if (isOff) {
            tvWeeklyOff.visibility = View.VISIBLE
            tvWeeklyOff.text = if (userDesignation.lowercase().contains("community developer"))
                "🗓 Weekly Off (Monday)" else "🗓 Weekly Off (Sunday)"
            tvStatus.visibility   = View.GONE
            tvCheckIn.visibility  = View.GONE
            tvCheckOut.visibility = View.GONE
        } else {
            tvWeeklyOff.visibility = View.GONE
            // Fetch Firestore doc for this date
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseFirestore.getInstance()
                .collection("attendance").document(uid)
                .collection("dates").document(dateKey).get()
                .addOnSuccessListener { doc ->
                    if (!doc.exists()) {
                        tvStatus.text  = "❌ No attendance record found"
                        tvCheckIn.text = ""
                        tvCheckOut.text = ""
                    } else {
                        val st  = doc.getString("status") ?: "Present"
                        val ci  = doc.getString("checkIn") ?: "N/A"
                        val co  = doc.getString("checkOut") ?: "Not checked out"
                        val stColor = when (st) {
                            "Present" -> "#22C55E"
                            "Late"    -> "#F59E0B"
                            else      -> "#EF4444"
                        }
                        tvStatus.text  = "Status: $st"
                        tvStatus.setTextColor(Color.parseColor(stColor))
                        tvCheckIn.text  = "⏰ Check-In:  $ci"
                        tvCheckOut.text = "🚪 Check-Out: $co"
                    }
                }
        }

        sheet.show()
    }

    // ── Permission ────────────────────────────────────────────────────────
    private fun checkPermission() {
        val locGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val camGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!locGranted || !camGranted) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Permissions Required")
                .setMessage("Adyapan CRM needs access to your Location to verify your physical presence at the office when clocking in, and access to your Camera to capture a mandatory check-in selfie for enterprise verification.\n\nPlease grant these permissions on the next screen.")
                .setCancelable(false)
                .setPositiveButton("I Understand") { _, _ ->
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CAMERA), 101
                    )
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                    Toast.makeText(this, "Permissions are required to mark attendance.", Toast.LENGTH_LONG).show()
                }
                .show()
        } else {
            getLocation()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            getLocation()
        } else {
            Toast.makeText(this, "Location and Camera permissions required", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Location ──────────────────────────────────────────────────────────
    private fun getLocation() {
        val fused = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        status.text    = "Getting your location... 📡"
        subStatus.text = "Please wait, fetching fresh GPS coordinates."
        btn.isEnabled  = false

        // ── Use getCurrentLocation for a FRESH reading (not stale lastLocation) ──
        val cancellationToken = com.google.android.gms.tasks.CancellationTokenSource()
        fused.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            cancellationToken.token
        ).addOnSuccessListener { location ->
            if (location != null) {
                handleLocationResult(location)
            } else {
                // getCurrentLocation returned null → fallback to lastLocation
                fused.lastLocation.addOnSuccessListener { fallback ->
                    if (fallback != null) {
                        handleLocationResult(fallback)
                    } else {
                        status.text    = "Location not found ❌"
                        subStatus.text = "GPS signal unavailable. Please go outdoors, turn on GPS and try again."
                    }
                }.addOnFailureListener {
                    status.text    = "Location error ❌"
                    subStatus.text = "Failed to get location. Please enable GPS and try again."
                }
            }
        }.addOnFailureListener {
            // Permission error or timeout → fallback to lastLocation
            fused.lastLocation.addOnSuccessListener { fallback ->
                if (fallback != null) {
                    handleLocationResult(fallback)
                } else {
                    status.text    = "Location error ❌"
                    subStatus.text = "Failed to get location. Please enable GPS and try again."
                }
            }
        }
    }

    private fun handleLocationResult(location: android.location.Location) {
        if (location.isFromMockProvider) {
            status.text    = "Mock Location Detected ❌"
            subStatus.text = "Fake GPS is not allowed for attendance."
            return
        }
        lat = location.latitude
        lng = location.longitude
        val result = FloatArray(1)
        Location.distanceBetween(lat, lng, officeLat, officeLng, result)
        val distanceMeters = result[0].toInt()
        if (distanceMeters <= allowedRadius) {
            status.text    = "Inside Office ✅"
            subStatus.text = "You are within ${distanceMeters}m of the office."
            btn.isEnabled  = true
            checkToday()
        } else {
            status.text    = "Outside Office ❌"
            subStatus.text = "You are ${distanceMeters}m away. Move inside the office to mark attendance."
            btn.isEnabled  = false
        }
    }

    // ── Check today's doc + device lock ───────────────────────────────────
    private fun checkToday() {
        val user   = FirebaseAuth.getInstance().currentUser ?: return
        val userId = user.uid
        val date   = getDate()

        FirebaseFirestore.getInstance()
            .collection("daily_device_locks").document(date)
            .collection("devices").document(deviceId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists() && doc.getString("userId") != userId) {
                    status.text    = "Device Locked ⚠️"
                    subStatus.text = "This phone has already been used to mark attendance for another employee today."
                    btn.visibility = View.GONE
                    return@addOnSuccessListener
                }
                FirebaseFirestore.getInstance()
                    .collection("attendance").document(userId)
                    .collection("dates").document(date).get()
                    .addOnSuccessListener { attDoc ->
                        if (attDoc.exists()) {
                            isCheckedIn = true
                            btn.text    = "Check-Out"
                        } else {
                            btn.text = "Check-In"
                        }
                        setupSelfieCapture()
                    }
            }
    }

    private fun setupSelfieCapture() {
        btn.setOnClickListener { takePictureLauncher.launch(null) }
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
    }

    // ── Check-In ──────────────────────────────────────────────────────────
    private fun saveCheckIn(selfieBase64: String) {
        val user   = FirebaseAuth.getInstance().currentUser ?: return
        val userId = user.uid
        val date   = getDate()
        val time   = getTime()
        val email  = user.email ?: ""
        val empName = if (email.contains("@")) email.substringBefore("@").replaceFirstChar { it.uppercase() } else "Employee"

        val lockData = hashMapOf("userId" to userId, "timestamp" to System.currentTimeMillis())
        val attData  = hashMapOf(
            "userId" to userId, "employeeName" to empName, "date" to date,
            "checkIn" to time, "checkOut" to null, "status" to getStatus(),
            "lat" to lat, "lng" to lng, "deviceId" to deviceId, "checkInSelfie" to selfieBase64
        )

        val db    = FirebaseFirestore.getInstance()
        val batch = db.batch()
        batch.set(db.collection("daily_device_locks").document(date).collection("devices").document(deviceId), lockData)
        batch.set(db.collection("attendance").document(userId).collection("dates").document(date), attData)
        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Check-In Successful ✅", Toast.LENGTH_SHORT).show()
                btn.text    = "Check-Out"
                isCheckedIn = true
                // Refresh calendar
                attendanceMap[date] = getStatus()
                renderCalendar()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save attendance", Toast.LENGTH_SHORT).show()
            }
    }

    // ── Check-Out ─────────────────────────────────────────────────────────
    private fun saveCheckOut(selfieBase64: String) {
        val user   = FirebaseAuth.getInstance().currentUser ?: return
        val userId = user.uid
        val date   = getDate()
        val time   = getTime()
        val cal    = Calendar.getInstance()
        val isEarlyLeave = cal.get(Calendar.HOUR_OF_DAY) < 20

        FirebaseFirestore.getInstance()
            .collection("attendance").document(userId)
            .collection("dates").document(date)
            .update(mapOf("checkOut" to time, "checkOutSelfie" to selfieBase64,
                "earlyLeave" to isEarlyLeave, "earlyLeaveTime" to if (isEarlyLeave) time else null))
            .addOnSuccessListener {
                val msg = if (isEarlyLeave) "Check-Out ⚠️ Early Leave at $time" else "Check-Out Successful ✅"
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                btn.isEnabled = false
                btn.text      = "Attendance Completed"
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save check-out", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getStatus(): String {
        val cal = Calendar.getInstance()
        val h   = cal.get(Calendar.HOUR_OF_DAY)
        val m   = cal.get(Calendar.MINUTE)
        return if (h > 11 || (h == 11 && m > 15)) "Late" else "Present"
    }

    private fun getDate() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    private fun getTime() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}