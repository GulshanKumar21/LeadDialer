package com.adyapan.leaddialer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.io.File
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
    private val checkInTimeMap = mutableMapOf<String, String>() // check-in time for status checking
    private lateinit var btnRefreshLocation: TextView

    private val deviceId: String by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    // File URI for camera photo (TakePicture is reliable on all Android versions)
    private var photoUri: Uri? = null

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = photoUri
                if (uri != null) {
                    try {
                        val base64 = encodeUriToBase64(uri)
                        if (isCheckedIn) saveCheckOut(base64) else saveCheckIn(base64)
                    } catch (e: Exception) {
                        btn.isEnabled = true
                        Toast.makeText(this, "Photo processing failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    btn.isEnabled = true
                    Toast.makeText(this, "Photo URI missing, please try again", Toast.LENGTH_SHORT).show()
                }
            } else {
                // User cancelled camera
                btn.isEnabled = true
                Toast.makeText(this, "Photo cancelled. Please take a selfie to mark attendance.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        btn             = findViewById(R.id.btnAttendance)
        btnViewHistory  = findViewById(R.id.btnViewHistory)
        status          = findViewById(R.id.txtStatus)
        subStatus       = findViewById(R.id.txtSubStatus)
        btnRefreshLocation = findViewById(R.id.btnRefreshLocation)

        tvCalMonth      = findViewById(R.id.tvCalMonth)
        btnCalPrev      = findViewById(R.id.btnCalPrev)
        btnCalNext      = findViewById(R.id.btnCalNext)
        llDayHeaders    = findViewById(R.id.llDayHeaders)
        llCalendarGrid  = findViewById(R.id.llCalendarGrid)

        btn.isEnabled = false

        btnRefreshLocation.setOnClickListener {
            getLocation()
        }

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
                checkInTimeMap.clear()
                for (doc in snap.documents) {
                    val dateKey = doc.id // "yyyy-MM-dd"
                    val st = doc.getString("status") ?: "Present"
                    attendanceMap[dateKey] = st
                    val ci = doc.getString("checkIn") ?: ""
                    checkInTimeMap[dateKey] = ci
                }
                renderCalendar()
                performRetentionCleanup(uid)
            }
    }

    // ── Role-based weekly off ─────────────────────────────────────────────
    private fun isWeeklyOff(cal: Calendar): Boolean {
        return cal.get(Calendar.DAY_OF_WEEK) == Calendar.TUESDAY
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
                    recStatus == "Half Day" -> { bgColor = Color.parseColor("#E0F2FE"); txtColor = Color.parseColor("#0284C7") }
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
            tvWeeklyOff.text = "🗓 Weekly Off (Tuesday)"
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
                            "Half Day" -> "#0284C7"
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        status.text    = "Fetching location... ⏳"
        subStatus.text = "Please wait a moment."
        btn.isEnabled  = false

        // 1. Try to get a fresh location
        val cts = com.google.android.gms.tasks.CancellationTokenSource()
        fused.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    handleLocationResult(loc)
                } else {
                    // 2. Fallback to last known if null
                    fused.lastLocation.addOnSuccessListener { fallback ->
                        if (fallback != null) handleLocationResult(fallback)
                        else {
                            status.text    = "Location error ❌"
                            subStatus.text = "Enable GPS and try again."
                            btn.isEnabled  = true
                        }
                    }
                }
            }
    }

    private fun handleLocationResult(location: android.location.Location) {
        if (location.isFromMockProvider) {
            status.text    = "Mock Location Detected ❌"
            subStatus.text = "Fake GPS is not allowed."
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
                            val checkOutTime = attDoc.getString("checkOut")
                            if (!checkOutTime.isNullOrEmpty()) {
                                isCheckedIn   = false
                                btn.text      = "Attendance Completed"
                                btn.isEnabled = false
                            } else {
                                isCheckedIn   = true
                                btn.text      = "Check-Out"
                            }
                        } else {
                            isCheckedIn   = false
                            btn.text      = "Check-In"
                        }
                        setupSelfieCapture()
                    }
            }
    }

    private fun setupSelfieCapture() {
        btn.setOnClickListener {
            // Disable button immediately to prevent double-tap / double-save
            btn.isEnabled = false
            // Create a temp file in cache for the photo
            try {
                val photoFile = File.createTempFile("selfie_${System.currentTimeMillis()}", ".jpg", cacheDir)
                photoUri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.provider",
                    photoFile
                )
                val intent = android.content.Intent(this, CustomCameraActivity::class.java).apply {
                    putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoUri)
                }
                takePictureLauncher.launch(intent)
            } catch (e: Exception) {
                btn.isEnabled = true
                Toast.makeText(this, "Cannot open camera: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Encode image file URI to Base64 (compressed, ~320px)
    private fun encodeUriToBase64(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot read photo file")
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        // Fix rotation using EXIF
        val rotatedBitmap = try {
            val exifStream = contentResolver.openInputStream(uri)
            val exif = ExifInterface(exifStream!!)
            exifStream.close()
            val rotation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90  -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
            } else originalBitmap
        } catch (e: Exception) { originalBitmap }

        return encodeBitmapToBase64(rotatedBitmap)
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val maxDimension = 320
        val width = bitmap.width
        val height = bitmap.height
        
        val scaledBitmap = if (width > maxDimension || height > maxDimension) {
            val ratio = width.toFloat() / height.toFloat()
            val newWidth: Int
            val newHeight: Int
            if (width > height) {
                newWidth = maxDimension
                newHeight = (maxDimension / ratio).toInt()
            } else {
                newHeight = maxDimension
                newWidth = (maxDimension * ratio).toInt()
            }
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }

        val baos = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 45, baos)
        val byteArray = baos.toByteArray()
        
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }
        
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun performRetentionCleanup(uid: String) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -60)
        val thresholdDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)

        val db = FirebaseFirestore.getInstance()
        db.collection("attendance").document(uid).collection("dates")
            .whereLessThan(com.google.firebase.firestore.FieldPath.documentId(), thresholdDateStr)
            .get()
            .addOnSuccessListener { snap ->
                val batch = db.batch()
                var hasUpdates = false
                for (doc in snap.documents) {
                    val hasCheckInSelfie = doc.contains("checkInSelfie") && doc.get("checkInSelfie") != null
                    val hasCheckOutSelfie = doc.contains("checkOutSelfie") && doc.get("checkOutSelfie") != null
                    if (hasCheckInSelfie || hasCheckOutSelfie) {
                        val updates = hashMapOf<String, Any?>()
                        if (hasCheckInSelfie) {
                            updates["checkInSelfie"] = com.google.firebase.firestore.FieldValue.delete()
                        }
                        if (hasCheckOutSelfie) {
                            updates["checkOutSelfie"] = com.google.firebase.firestore.FieldValue.delete()
                        }
                        batch.update(doc.reference, updates)
                        hasUpdates = true
                    }
                }
                if (hasUpdates) {
                    batch.commit()
                }
            }
    }

    // ── Check-In ──────────────────────────────────────────────────────────
    private fun saveCheckIn(selfieBase64: String) {
        val user   = FirebaseAuth.getInstance().currentUser ?: return
        val userId = user.uid
        val date   = getDate()
        val time   = getTime()
        val statusVal = determineStatusForTime(time)
        val email  = user.email ?: ""
        val empName = if (email.contains("@")) email.substringBefore("@").replaceFirstChar { it.uppercase() } else "Employee"

        val lockData = hashMapOf("userId" to userId, "timestamp" to System.currentTimeMillis())
        val attData  = hashMapOf(
            "userId" to userId, "employeeName" to empName, "date" to date,
            "checkIn" to time, "checkOut" to null, "status" to statusVal,
            "lat" to lat, "lng" to lng, "deviceId" to deviceId, "checkInSelfie" to selfieBase64
        )

        val db    = FirebaseFirestore.getInstance()
        val batch = db.batch()
        batch.set(db.collection("daily_device_locks").document(date).collection("devices").document(deviceId), lockData)
        batch.set(db.collection("attendance").document(userId).collection("dates").document(date), attData)
        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Check-In Successful ✅", Toast.LENGTH_SHORT).show()
                btn.text      = "Check-Out"
                btn.isEnabled = true   // re-enable for checkout
                isCheckedIn   = true
                // Refresh calendar
                attendanceMap[date] = statusVal
                checkInTimeMap[date] = time
                renderCalendar()
            }
            .addOnFailureListener {
                btn.isEnabled = true   // re-enable so user can retry
                Toast.makeText(this, "Failed to save attendance", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getSecondsFromTimeString(timeStr: String): Int {
        if (timeStr.isBlank() || timeStr == "N/A" || timeStr == "--") return 0
        try {
            val parts = timeStr.split(":")
            if (parts.size >= 2) {
                val h = parts[0].toInt()
                val m = parts[1].toInt()
                val s = if (parts.size > 2) parts[2].toInt() else 0
                return h * 3600 + m * 60 + s
            }
        } catch (e: Exception) {
            // ignore
        }
        return 0
    }

    // ── Check-Out ─────────────────────────────────────────────────────────
    private fun saveCheckOut(selfieBase64: String) {
        val user   = FirebaseAuth.getInstance().currentUser ?: return
        val userId = user.uid
        val date   = getDate()
        val time   = getTime()
        val cal    = Calendar.getInstance()
        val isEarlyLeave = cal.get(Calendar.HOUR_OF_DAY) < 20

        val checkInTimeStr = checkInTimeMap[date] ?: "11:00:00"
        val checkInSeconds = getSecondsFromTimeString(checkInTimeStr)
        val checkOutSeconds = getSecondsFromTimeString(time)
        var workedSeconds = checkOutSeconds - checkInSeconds
        if (workedSeconds < 0) {
            workedSeconds += 24 * 3600
        }

        var newStatus = attendanceMap[date] ?: "Present"
        if (isEarlyLeave) {
            if (workedSeconds < 9000) { // 2.5 hours = 9000 seconds
                newStatus = "Absent"
            } else {
                newStatus = "Half Day"
            }
        } else {
            if (workedSeconds < 9000) {
                newStatus = "Absent"
            }
        }

        val updates = mutableMapOf<String, Any?>(
            "checkOut" to time,
            "checkOutSelfie" to selfieBase64,
            "earlyLeave" to isEarlyLeave,
            "earlyLeaveTime" to if (isEarlyLeave) time else null,
            "status" to newStatus
        )

        FirebaseFirestore.getInstance()
            .collection("attendance").document(userId)
            .collection("dates").document(date)
            .update(updates)
            .addOnSuccessListener {
                attendanceMap[date] = newStatus
                renderCalendar()
                val msg = if (isEarlyLeave) {
                    if (newStatus == "Absent") "Check-Out ❌ Absent (Worked < 2.5h)" else "Check-Out ⚠️ Early Leave (Half Day)"
                } else {
                    if (newStatus == "Absent") "Check-Out ❌ Absent (Worked < 2.5h)" else "Check-Out Successful ✅"
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                btn.isEnabled = false
                btn.text      = "Attendance Completed"
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save check-out", Toast.LENGTH_SHORT).show()
            }
    }

    private fun isLateCheckInTime(timeStr: String): Boolean {
        if (timeStr.isBlank() || timeStr == "N/A" || timeStr == "--") return false
        try {
            val parts = timeStr.split(":")
            if (parts.size >= 2) {
                val h = parts[0].toInt()
                val m = parts[1].toInt()
                val s = if (parts.size > 2) parts[2].toInt() else 0
                val totalSeconds = h * 3600 + m * 60 + s
                // 11:05:01 to 11:10:00 (Grace period)
                // 11:05:00 is 39900 seconds
                // 11:10:00 is 40200 seconds
                return totalSeconds in 39901..40200
            }
        } catch (e: Exception) {
            // ignore
        }
        return false
    }

    private fun getLateCountForCurrentMonth(): Int {
        val monthPrefix = SimpleDateFormat("yyyy-MM-", Locale.getDefault()).format(Date())
        var count = 0
        for ((dateKey, timeStr) in checkInTimeMap) {
            if (dateKey.startsWith(monthPrefix)) {
                if (isLateCheckInTime(timeStr)) {
                    count++
                }
            }
        }
        return count
    }

    private fun determineStatusForTime(timeStr: String): String {
        try {
            val parts = timeStr.split(":")
            if (parts.size >= 2) {
                val h = parts[0].toInt()
                val m = parts[1].toInt()
                val s = if (parts.size > 2) parts[2].toInt() else 0
                val totalSeconds = h * 3600 + m * 60 + s
                
                if (totalSeconds <= 39900) { // 11:05:00 or earlier
                    return "Present"
                } else if (totalSeconds <= 40200) { // 11:05:01 to 11:10:00
                    val priorLateCount = getLateCountForCurrentMonth()
                    return if (priorLateCount >= 3) {
                        "Half Day"
                    } else {
                        "Late"
                    }
                } else { // 11:10:01 or later
                    return "Half Day"
                }
            }
        } catch (e: Exception) {
            // Fallback if parsing fails
        }
        return "Present"
    }

    private fun getStatus(): String {
        return determineStatusForTime(getTime())
    }

    private fun getDate() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    private fun getTime() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}