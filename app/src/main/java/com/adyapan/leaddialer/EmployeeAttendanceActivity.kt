package com.adyapan.leaddialer

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class EmployeeAttendanceActivity : AppCompatActivity() {

    private lateinit var rvHistory: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var tvNoHistory: TextView
    private lateinit var tvTotalDaysWorked: TextView
    private lateinit var btnMonthYear: Button
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

        rvHistory = findViewById(R.id.rvAttendanceHistory)
        progress = findViewById(R.id.progressHistory)
        tvNoHistory = findViewById(R.id.tvNoHistory)
        tvTotalDaysWorked = findViewById(R.id.tvTotalDaysWorked)
        btnMonthYear = findViewById(R.id.btnMonthYear)

        adapter = AttendanceHistoryAdapter()
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = adapter

        updateMonthButtonText()
        fetchHistoryForMonth()

        btnMonthYear.setOnClickListener {
            // Simple date picker, we care about Month/Year
            val dpd = DatePickerDialog(this, { _, year, month, _ ->
                selectedCalendar.set(Calendar.YEAR, year)
                selectedCalendar.set(Calendar.MONTH, month)
                updateMonthButtonText()
                fetchHistoryForMonth()
            }, selectedCalendar.get(Calendar.YEAR), selectedCalendar.get(Calendar.MONTH), selectedCalendar.get(Calendar.DAY_OF_MONTH))
            
            dpd.show()
        }
    }

    private fun updateMonthButtonText() {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        btnMonthYear.text = sdf.format(selectedCalendar.time)
    }

    private fun fetchHistoryForMonth() {
        progress.visibility = View.VISIBLE
        tvNoHistory.visibility = View.GONE
        rvHistory.visibility = View.GONE
        tvTotalDaysWorked.text = "0"

        val uid = targetUid ?: return
        val year  = selectedCalendar.get(Calendar.YEAR)
        val month = selectedCalendar.get(Calendar.MONTH) + 1 // 1-12

        // Build all "yyyy-MM-dd" dates for this month (AttendanceActivity stores as yyyy-MM-dd)
        val cal    = Calendar.getInstance()
        cal.set(year, month - 1, 1)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val monthPrefix = String.format("%04d-%02d-", year, month)

        // AttendanceActivity saves to: attendance/{uid}/dates/{yyyy-MM-dd}
        FirebaseFirestore.getInstance()
            .collection("attendance")
            .document(uid)
            .collection("dates")
            .get()
            .addOnSuccessListener { querySnapshot ->
                progress.visibility = View.GONE
                val items = mutableListOf<AttendanceHistoryItem>()

                for (doc in querySnapshot.documents) {
                    val docDate = doc.id // "yyyy-MM-dd"
                    // Filter to selected month only
                    if (!docDate.startsWith(monthPrefix)) continue

                    // Format for display: "yyyy-MM-dd" → "dd/MM/yyyy"
                    val parts       = docDate.split("-")
                    val displayDate = if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else docDate

                    val checkIn     = doc.getString("checkIn")          ?: "--"
                    val checkOut    = doc.getString("checkOut")
                    val status      = doc.getString("status")           ?: "Present"
                    val earlyLeave  = doc.getBoolean("earlyLeave")      ?: false
                    val inSelfie    = doc.getString("checkInSelfie")
                    val outSelfie   = doc.getString("checkOutSelfie")

                    items.add(AttendanceHistoryItem(
                        date                = displayDate,
                        checkInTime         = checkIn,
                        checkOutTime        = checkOut,
                        status              = status,
                        earlyLeave          = earlyLeave,
                        checkInSelfieBase64 = inSelfie,
                        checkOutSelfieBase64= outSelfie
                    ))
                }

                if (items.isEmpty()) {
                    tvNoHistory.visibility = View.VISIBLE
                    tvNoHistory.text = "No records for this month."
                } else {
                    items.sortByDescending { it.date }
                    adapter.submitList(items)
                    rvHistory.visibility = View.VISIBLE
                    tvTotalDaysWorked.text = items.size.toString()
                }
            }
            .addOnFailureListener { e ->
                progress.visibility = View.GONE
                tvNoHistory.visibility = View.VISIBLE
                tvNoHistory.text = "Error fetching records."
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
