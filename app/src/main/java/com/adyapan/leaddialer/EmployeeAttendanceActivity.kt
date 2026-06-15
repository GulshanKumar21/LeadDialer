package com.adyapan.leaddialer

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class EmployeeAttendanceActivity : AppCompatActivity() {

    private lateinit var rvHistory: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var tvNoHistory: TextView
    private lateinit var tvTotalDaysWorked: TextView
    private lateinit var btnMonthYear: Button
    private lateinit var btnPrevMonth: ImageButton
    private lateinit var btnNextMonth: ImageButton
    private lateinit var tvPresent: TextView
    private lateinit var tvLate: TextView
    private lateinit var tvAbsent: TextView
    private lateinit var adapter: AttendanceHistoryAdapter

    private var targetUid: String? = null
    private var selectedCalendar: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_employee_attendance)

        targetUid = intent.getStringExtra("uid") ?: FirebaseAuth.getInstance().currentUser?.uid

        if (targetUid == null) {
            Toast.makeText(this, "User ID missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        rvHistory           = findViewById(R.id.rvAttendanceHistory)
        progress            = findViewById(R.id.progressHistory)
        tvNoHistory         = findViewById(R.id.tvNoHistory)
        tvTotalDaysWorked   = findViewById(R.id.tvTotalDaysWorked)
        btnMonthYear        = findViewById(R.id.btnMonthYear)
        btnPrevMonth        = findViewById(R.id.btnPrevMonth)
        btnNextMonth        = findViewById(R.id.btnNextMonth)
        tvPresent           = findViewById(R.id.tvPresentCount)
        tvLate              = findViewById(R.id.tvLateCount)
        tvAbsent            = findViewById(R.id.tvAbsentCount)

        adapter = AttendanceHistoryAdapter()
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = adapter

        updateMonthButtonText()
        updateNextButtonState()
        fetchHistoryForMonth()

        // ◀ Previous month
        btnPrevMonth.setOnClickListener {
            selectedCalendar.add(Calendar.MONTH, -1)
            updateMonthButtonText()
            updateNextButtonState()
            fetchHistoryForMonth()
        }

        // ▶ Next month (max = current month)
        btnNextMonth.setOnClickListener {
            val now = Calendar.getInstance()
            if (selectedCalendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                selectedCalendar.get(Calendar.MONTH) == now.get(Calendar.MONTH)) return@setOnClickListener
            selectedCalendar.add(Calendar.MONTH, 1)
            updateMonthButtonText()
            updateNextButtonState()
            fetchHistoryForMonth()
        }

        // Month label click → same as next (or open picker)
        btnMonthYear.setOnClickListener {
            // Cycle to current month quickly
            selectedCalendar = Calendar.getInstance()
            updateMonthButtonText()
            updateNextButtonState()
            fetchHistoryForMonth()
        }
    }

    private fun updateMonthButtonText() {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        btnMonthYear.text = sdf.format(selectedCalendar.time)
    }

    private fun updateNextButtonState() {
        val now = Calendar.getInstance()
        val isCurrentMonth = selectedCalendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                selectedCalendar.get(Calendar.MONTH) == now.get(Calendar.MONTH)
        btnNextMonth.alpha = if (isCurrentMonth) 0.3f else 1.0f
        btnNextMonth.isEnabled = !isCurrentMonth
    }

    private fun fetchHistoryForMonth() {
        progress.visibility = View.VISIBLE
        tvNoHistory.visibility = View.GONE
        rvHistory.visibility = View.GONE
        tvTotalDaysWorked.text = "0"
        tvPresent.text = "0"
        tvLate.text    = "0"
        tvAbsent.text  = "0"

        val uid   = targetUid ?: return
        val year  = selectedCalendar.get(Calendar.YEAR)
        val month = selectedCalendar.get(Calendar.MONTH) + 1 // 1-12

        val cal        = Calendar.getInstance()
        cal.set(year, month - 1, 1)
        val maxDay     = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val monthPrefix = String.format("%04d-%02d-", year, month)

        lifecycleScope.launch {
            try {
                val holidayMap = FirestoreSource.fetchHolidaysOnce()

                // Calculate total working days in this month (excluding Tuesdays - weekly off, and Holidays)
                var totalWorkingDays = 0
                val keyFmtTemp = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                for (day in 1..maxDay) {
                    cal.set(year, month - 1, day)
                    val dateKey = keyFmtTemp.format(cal.time)
                    val isWeeklyOff = cal.get(Calendar.DAY_OF_WEEK) == Calendar.TUESDAY
                    val isHoliday = holidayMap.containsKey(dateKey)
                    if (!isWeeklyOff && !isHoliday) {
                        // Don't count future days
                        val now = Calendar.getInstance()
                        if (!cal.after(now)) totalWorkingDays++
                    }
                }

                val querySnapshot = FirebaseFirestore.getInstance()
                    .collection("attendance")
                    .document(uid)
                    .collection("dates")
                    .get()
                    .await()

                progress.visibility = View.GONE

                // Build record map
                val recordMap = mutableMapOf<String, Map<String, Any?>>()
                for (doc in querySnapshot.documents) {
                    if (doc.id.startsWith(monthPrefix)) {
                        recordMap[doc.id] = mapOf(
                            "checkIn"        to doc.getString("checkIn"),
                            "checkOut"       to doc.getString("checkOut"),
                            "status"         to doc.getString("status"),
                            "checkInSelfie"  to doc.getString("checkInSelfie"),
                            "checkOutSelfie" to doc.getString("checkOutSelfie"),
                            "earlyLeave"     to (doc.getBoolean("earlyLeave") ?: false)
                        )
                    }
                }

                // Build full month day list
                val items = mutableListOf<AttendanceHistoryItem>()
                val calendar = Calendar.getInstance()
                val now = Calendar.getInstance()
                val daysInMonth = Calendar.getInstance().apply {
                    set(year, month - 1, 1)
                }.getActualMaximum(Calendar.DAY_OF_MONTH)

                var workedCount  = 0
                var presentCount = 0
                var lateCount    = 0
                var halfDayCount = 0

                val dayFmt = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
                val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                for (day in 1..daysInMonth) {
                    calendar.set(year, month - 1, day, 0, 0, 0)
                    val dateKey     = keyFmt.format(calendar.time)
                    val displayDate = dayFmt.format(calendar.time)
                    val isWeeklyOff = calendar.get(Calendar.DAY_OF_WEEK) == Calendar.TUESDAY
                    val isHoliday   = holidayMap.containsKey(dateKey)
                    val isFuture    = calendar.after(now)

                    val rec    = recordMap[dateKey]
                    val status = rec?.get("status") as? String

                    if (rec != null) {
                        workedCount++
                        when {
                            status?.equals("Late", ignoreCase = true) == true -> lateCount++
                            status?.equals("Half Day", ignoreCase = true) == true -> halfDayCount++
                            else -> presentCount++
                        }
                    }

                    items.add(AttendanceHistoryItem(
                        dateKey              = dateKey,
                        date                 = displayDate,
                        checkInTime          = rec?.get("checkIn")        as? String,
                        checkOutTime         = rec?.get("checkOut")       as? String,
                        status               = status,
                        earlyLeave           = rec?.get("earlyLeave")     as? Boolean ?: false,
                        checkInSelfieBase64  = rec?.get("checkInSelfie")  as? String,
                        checkOutSelfieBase64 = rec?.get("checkOutSelfie") as? String,
                        isWeeklyOff          = isWeeklyOff,
                        isFuture             = isFuture,
                        holidayName          = if (rec == null && isHoliday && !isWeeklyOff) holidayMap[dateKey] else null
                    ))
                }

                val absentCount = (totalWorkingDays - workedCount).coerceAtLeast(0)

                // Update summary metrics
                tvTotalDaysWorked.text = workedCount.toString()
                tvPresent.text = presentCount.toString()
                tvLate.text    = if (halfDayCount > 0) "$lateCount (${halfDayCount}HD)" else lateCount.toString()
                tvAbsent.text  = absentCount.toString()

                if (items.isEmpty()) {
                    tvNoHistory.visibility = View.VISIBLE
                    tvNoHistory.text = "No attendance records for this month."
                } else {
                    items.sortByDescending { it.dateKey }
                    adapter.submitList(items)
                    rvHistory.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                progress.visibility = View.GONE
                tvNoHistory.visibility = View.VISIBLE
                tvNoHistory.text = "Error fetching records."
                Toast.makeText(this@EmployeeAttendanceActivity, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
