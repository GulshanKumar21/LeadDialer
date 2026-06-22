package com.adyapan.leaddialer

import android.app.TimePickerDialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

// ── Data class for each day row ───────────────────────────────────────────────
data class DayAttendanceRow(
    val dateKey          : String,   // "yyyy-MM-dd"
    val displayDate      : String,   // "Mon, 03 Jun"
    val checkIn          : String?,
    val checkOut         : String?,
    val status           : String?,  // "Present" / "Late" / null (absent)
    val isWeeklyOff      : Boolean,
    val isFuture         : Boolean,
    val checkInSelfie    : String?,  // Base64 string
    val checkOutSelfie   : String?,  // Base64 string
    val isHoliday        : Boolean = false,
    val holidayName      : String? = null
)

// ── Admin Employee Attendance Activity ────────────────────────────────────────
class AdminEmployeeAttendanceActivity : AppCompatActivity() {

    private lateinit var tvEmpName    : TextView
    private lateinit var tvAvatar     : TextView
    private lateinit var btnPrevMonth : ImageButton
    private lateinit var btnNextMonth : ImageButton
    private lateinit var btnMonthLabel: Button
    private lateinit var tvWorked     : TextView
    private lateinit var tvPresent    : TextView
    private lateinit var tvLate       : TextView
    private lateinit var tvAbsent     : TextView
    private lateinit var rvDays       : RecyclerView
    private lateinit var progressBar  : ProgressBar

    private var uid  = ""
    private var name = ""
    private var selectedCal: Calendar = Calendar.getInstance()

    private val db       = FirebaseFirestore.getInstance()
    private val dayFmt   = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
    private val keyFmt   = SimpleDateFormat("yyyy-MM-dd",  Locale.getDefault())
    private val monthFmt = SimpleDateFormat("MMMM yyyy",   Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_employee_attendance)

        uid  = intent.getStringExtra("uid")  ?: ""
        name = intent.getStringExtra("name") ?: "Employee"

        tvEmpName     = findViewById(R.id.tvAdminEmpName)
        tvAvatar      = findViewById(R.id.tvAdminEmpAvatar)
        btnPrevMonth  = findViewById(R.id.btnAdminPrevMonth)
        btnNextMonth  = findViewById(R.id.btnAdminNextMonth)
        btnMonthLabel = findViewById(R.id.btnAdminMonthLabel)
        tvWorked      = findViewById(R.id.tvAdminWorked)
        tvPresent     = findViewById(R.id.tvAdminPresent)
        tvLate        = findViewById(R.id.tvAdminLate)
        tvAbsent      = findViewById(R.id.tvAdminAbsent)
        rvDays        = findViewById(R.id.rvAdminDays)
        progressBar   = findViewById(R.id.progressAdminAttendance)

        tvEmpName.text = name
        tvAvatar.text  = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        rvDays.layoutManager = LinearLayoutManager(this)

        updateMonthLabel()
        updateNextState()
        loadMonth()

        btnPrevMonth.setOnClickListener {
            selectedCal.add(Calendar.MONTH, -1)
            updateMonthLabel(); updateNextState(); loadMonth()
        }
        btnNextMonth.setOnClickListener {
            val now = Calendar.getInstance()
            if (selectedCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                selectedCal.get(Calendar.MONTH) == now.get(Calendar.MONTH)) return@setOnClickListener
            selectedCal.add(Calendar.MONTH, 1)
            updateMonthLabel(); updateNextState(); loadMonth()
        }
        btnMonthLabel.setOnClickListener {
            selectedCal = Calendar.getInstance()
            updateMonthLabel(); updateNextState(); loadMonth()
        }
    }

    private fun updateMonthLabel() { btnMonthLabel.text = monthFmt.format(selectedCal.time) }

    private fun updateNextState() {
        val now = Calendar.getInstance()
        val isCurrent = selectedCal.get(Calendar.YEAR)  == now.get(Calendar.YEAR) &&
                        selectedCal.get(Calendar.MONTH) == now.get(Calendar.MONTH)
        btnNextMonth.alpha     = if (isCurrent) 0.3f else 1.0f
        btnNextMonth.isEnabled = !isCurrent
    }

    private fun loadMonth() {
        progressBar.visibility = View.VISIBLE
        rvDays.visibility      = View.GONE
        tvWorked.text = "–"; tvPresent.text = "–"; tvLate.text = "–"; tvAbsent.text = "–"

        val year   = selectedCal.get(Calendar.YEAR)
        val month  = selectedCal.get(Calendar.MONTH)  // 0-based
        val prefix = String.format("%04d-%02d-", year, month + 1)

        lifecycleScope.launch {
            try {
                val holidayMap = FirestoreSource.fetchHolidaysOnce()
                val snap = db.collection("attendance").document(uid).collection("dates").get().await()

                // ── Build record map with selfie fields ──────────────────────
                val recordMap = mutableMapOf<String, Map<String, Any?>>()
                for (doc in snap.documents) {
                    if (doc.id.startsWith(prefix)) {
                        recordMap[doc.id] = mapOf(
                            "checkIn"        to doc.getString("checkIn"),
                            "checkOut"       to doc.getString("checkOut"),
                            "status"         to doc.getString("status"),
                            "checkInSelfie"  to doc.getString("checkInSelfie"),
                            "checkOutSelfie" to doc.getString("checkOutSelfie")
                        )
                    }
                }

                // ── Build full day list ───────────────────────────────────────
                val rows = mutableListOf<DayAttendanceRow>()
                val cal  = Calendar.getInstance()
                val now  = Calendar.getInstance()
                val daysInMonth = Calendar.getInstance().apply {
                    set(year, month, 1)
                }.getActualMaximum(Calendar.DAY_OF_MONTH)

                var workedCount  = 0
                var presentCount = 0
                var lateCount    = 0
                var halfDayCount = 0
                var lopCount     = 0
                var workingDays  = 0

                for (day in 1..daysInMonth) {
                    cal.set(year, month, day, 0, 0, 0)
                    val dateKey     = keyFmt.format(cal.time)
                    val displayDate = dayFmt.format(cal.time)
                    val isWeeklyOff = cal.get(Calendar.DAY_OF_WEEK) == Calendar.TUESDAY
                    val isHoliday   = holidayMap.containsKey(dateKey)
                    val isFuture    = cal.after(now)

                    if (!isWeeklyOff && !isFuture && !isHoliday) workingDays++

                    val rec    = recordMap[dateKey]
                    val status = rec?.get("status") as? String

                    if (rec != null && status?.equals("Absent", ignoreCase = true) != true && status?.equals("LOP", ignoreCase = true) != true) workedCount++
                    when {
                        status?.equals("Present", ignoreCase = true) == true -> presentCount++
                        status?.equals("Late",    ignoreCase = true) == true -> lateCount++
                        status?.equals("Half Day", ignoreCase = true) == true -> halfDayCount++
                        status?.equals("LOP",      ignoreCase = true) == true -> lopCount++
                    }

                    rows.add(DayAttendanceRow(
                        dateKey        = dateKey,
                        displayDate    = displayDate,
                        checkIn        = rec?.get("checkIn")        as? String,
                        checkOut       = rec?.get("checkOut")       as? String,
                        status         = status,
                        isWeeklyOff    = isWeeklyOff,
                        isFuture       = isFuture,
                        checkInSelfie  = rec?.get("checkInSelfie")  as? String,
                        checkOutSelfie = rec?.get("checkOutSelfie") as? String,
                        isHoliday      = isHoliday,
                        holidayName    = if (rec == null && isHoliday && !isWeeklyOff) holidayMap[dateKey] else null
                    ))
                }

                val absentCount = (workingDays - workedCount).coerceAtLeast(0)
                tvWorked.text  = workedCount.toString()
                tvPresent.text = presentCount.toString()
                tvLate.text    = if (halfDayCount > 0) "$lateCount (${halfDayCount}HD)" else lateCount.toString()
                tvAbsent.text  = if (lopCount > 0) "$absentCount (${lopCount}LOP)" else absentCount.toString()

                progressBar.visibility = View.GONE
                rvDays.visibility      = View.VISIBLE

                val adapter = DayRowAdapter(
                    rows         = rows,
                    onPhotoClick = { row -> showSelfieDialog(row) },
                    onItemClick  = { row -> showEditAttendanceDialog(row) }
                )
                rvDays.adapter = adapter
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@AdminEmployeeAttendanceActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditAttendanceDialog(row: DayAttendanceRow) {
        if (!FirestoreSource.isAdmin) {
            Toast.makeText(this, "Only Admin can edit attendance or mark LOP", Toast.LENGTH_SHORT).show()
            return
        }
        if (row.isFuture) {
            Toast.makeText(this, "Cannot modify future attendance", Toast.LENGTH_SHORT).show()
            return
        }

        val statuses = arrayOf("Present", "Late", "Half Day", "Absent", "LOP", "Remove Record")
        AlertDialog.Builder(this)
            .setTitle("✏️ Edit Status: ${row.displayDate}")
            .setItems(statuses) { dialog, which ->
                val selectedOption = statuses[which]
                if (selectedOption == "Remove Record") {
                    db.collection("attendance").document(uid)
                        .collection("dates").document(row.dateKey)
                        .delete()
                        .addOnSuccessListener {
                            Toast.makeText(this, "Record deleted ✅", Toast.LENGTH_SHORT).show()
                            loadMonth()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    val data = hashMapOf<String, Any?>(
                        "userId"       to uid,
                        "employeeName" to name,
                        "date"         to row.dateKey,
                        "status"       to selectedOption,
                        "checkIn"      to row.checkIn,
                        "checkOut"     to row.checkOut,
                        "checkInSelfie"  to row.checkInSelfie,
                        "checkOutSelfie" to row.checkOutSelfie,
                        "manuallySet"  to true
                    )
                    db.collection("attendance").document(uid)
                        .collection("dates").document(row.dateKey)
                        .set(data)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Attendance updated to $selectedOption ✅", Toast.LENGTH_SHORT).show()
                            loadMonth()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Selfie Dialog ─────────────────────────────────────────────────────────
    private fun showSelfieDialog(row: DayAttendanceRow) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view_selfie, null)
        val imgIn  = dialogView.findViewById<ImageView>(R.id.imgCheckIn)
        val imgOut = dialogView.findViewById<ImageView>(R.id.imgCheckOut)
        val tvIn   = dialogView.findViewById<TextView>(R.id.tvCheckInTitle)
        val tvOut  = dialogView.findViewById<TextView>(R.id.tvCheckOutTitle)

        fun decodeAndShow(base64: String?, imgView: ImageView, label: TextView) {
            if (!base64.isNullOrBlank()) {
                try {
                    val bytes  = Base64.decode(base64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    imgView.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    imgView.visibility = View.GONE
                    label.visibility   = View.GONE
                }
            } else {
                imgView.visibility = View.GONE
                label.visibility   = View.GONE
            }
        }

        decodeAndShow(row.checkInSelfie,  imgIn,  tvIn)
        decodeAndShow(row.checkOutSelfie, imgOut, tvOut)

        AlertDialog.Builder(this)
            .setTitle("📸 ${name} — ${row.displayDate}")
            .setView(dialogView)
            .setPositiveButton("Close") { d, _ -> d.dismiss() }
            .show()
    }

    // Manual attendance dialog removed — admin view only
    private fun showManualDialog_REMOVED(row: DayAttendanceRow) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manual_attendance, null)

        val tvDate     = dialogView.findViewById<TextView>(R.id.tvManualDate)
        val spinStatus = dialogView.findViewById<Spinner>(R.id.spinManualStatus)
        val tvCheckIn  = dialogView.findViewById<TextView>(R.id.tvManualCheckIn)
        val tvCheckOut = dialogView.findViewById<TextView>(R.id.tvManualCheckOut)
        val btnSetIn   = dialogView.findViewById<Button>(R.id.btnSetCheckIn)
        val btnSetOut  = dialogView.findViewById<Button>(R.id.btnSetCheckOut)
        val btnSave    = dialogView.findViewById<Button>(R.id.btnManualSave)
        val btnDelete  = dialogView.findViewById<Button>(R.id.btnManualDelete)

        tvDate.text = row.displayDate

        val statuses = arrayOf("Present", "Late", "Absent")
        spinStatus.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, statuses)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        var checkInTime  = row.checkIn  ?: ""
        var checkOutTime = row.checkOut ?: ""
        tvCheckIn.text  = if (checkInTime.isNotBlank())  "Check-In:  $checkInTime"  else "Check-In:  Not set"
        tvCheckOut.text = if (checkOutTime.isNotBlank()) "Check-Out: $checkOutTime" else "Check-Out: Not set"

        val existingStatus = row.status ?: "Present"
        spinStatus.setSelection(statuses.indexOf(existingStatus).coerceAtLeast(0))

        btnSetIn.setOnClickListener {
            val h = checkInTime.take(2).toIntOrNull() ?: 9
            val m = if (checkInTime.length >= 5) checkInTime.substring(3, 5).toIntOrNull() ?: 0 else 0
            TimePickerDialog(this, { _, hour, minute ->
                checkInTime = String.format("%02d:%02d:00", hour, minute)
                tvCheckIn.text = "Check-In:  $checkInTime"
            }, h, m, true).show()
        }
        btnSetOut.setOnClickListener {
            val h = checkOutTime.take(2).toIntOrNull() ?: 19
            val m = if (checkOutTime.length >= 5) checkOutTime.substring(3, 5).toIntOrNull() ?: 0 else 0
            TimePickerDialog(this, { _, hour, minute ->
                checkOutTime = String.format("%02d:%02d:00", hour, minute)
                tvCheckOut.text = "Check-Out: $checkOutTime"
            }, h, m, true).show()
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnSave.setOnClickListener {
            val selectedStatus = spinStatus.selectedItem.toString()
            btnSave.isEnabled = false
            btnSave.text = "Saving..."

            if (selectedStatus == "Absent") {
                db.collection("attendance").document(uid)
                    .collection("dates").document(row.dateKey)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Marked Absent ✅", Toast.LENGTH_SHORT).show()
                        dialog.dismiss(); loadMonth()
                    }
                    .addOnFailureListener {
                        btnSave.isEnabled = true; btnSave.text = "Save"
                        Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                val data = hashMapOf<String, Any?>(
                    "userId"       to uid,
                    "employeeName" to name,
                    "date"         to row.dateKey,
                    "status"       to selectedStatus,
                    "checkIn"      to checkInTime.ifBlank { null },
                    "checkOut"     to checkOutTime.ifBlank { null },
                    "manuallySet"  to true
                )
                db.collection("attendance").document(uid)
                    .collection("dates").document(row.dateKey)
                    .set(data)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Saved ✅", Toast.LENGTH_SHORT).show()
                        dialog.dismiss(); loadMonth()
                    }
                    .addOnFailureListener {
                        btnSave.isEnabled = true; btnSave.text = "Save"
                        Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Record?")
                .setMessage("${row.displayDate} की attendance delete होगी। Sure?")
                .setPositiveButton("Delete") { _, _ ->
                    db.collection("attendance").document(uid)
                        .collection("dates").document(row.dateKey)
                        .delete()
                        .addOnSuccessListener {
                            Toast.makeText(this, "Deleted ✅", Toast.LENGTH_SHORT).show()
                            dialog.dismiss(); loadMonth()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        dialog.show()
    }
}

// ── RecyclerView Adapter for day rows ─────────────────────────────────────────
class DayRowAdapter(
    private val rows        : List<DayAttendanceRow>,
    private val onPhotoClick: (DayAttendanceRow) -> Unit,
    private val onItemClick : (DayAttendanceRow) -> Unit
) : RecyclerView.Adapter<DayRowAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvDate   : TextView  = v.findViewById(R.id.tvDayDate)
        val tvStatus : TextView  = v.findViewById(R.id.tvDayStatus)
        val tvTime   : TextView  = v.findViewById(R.id.tvDayTime)
        val ivPhoto  : ImageView = v.findViewById(R.id.ivDayPhoto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_attendance_day, parent, false)
        return VH(v)
    }

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = rows[position]
        holder.tvDate.text = row.displayDate

        holder.itemView.setOnClickListener {
            onItemClick(row)
        }

        when {
            row.holidayName != null -> {
                holder.tvStatus.text = "🎉 ${row.holidayName}"
                holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#EA580C"))
                holder.tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#FFEDD5"))
                holder.tvTime.text = "Holiday"
                holder.ivPhoto.visibility = View.GONE
            }
            row.isWeeklyOff -> {
                holder.tvStatus.text = "Weekly Off"
                holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#9CA3AF"))
                holder.tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#F3F4F6"))
                holder.tvTime.text = "—"
                holder.ivPhoto.visibility = View.GONE
            }
            row.isFuture -> {
                holder.tvStatus.text = "—"
                holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#D1D5DB"))
                holder.tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.TRANSPARENT)
                holder.tvTime.text = "Upcoming"
                holder.ivPhoto.visibility = View.GONE
            }
            row.status == null || row.status.equals("Absent", ignoreCase = true) -> {
                holder.tvStatus.text = "Absent"
                holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#EF4444"))
                holder.tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#FEE2E2"))
                holder.tvTime.text = "Not marked"
                holder.ivPhoto.visibility = View.GONE
            }
            row.status.equals("LOP", ignoreCase = true) -> {
                holder.tvStatus.text = "⚠️ LOP"
                holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#EF4444"))
                holder.tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#FEE2E2"))
                holder.tvTime.text = "Loss of Pay"
                holder.ivPhoto.visibility = View.GONE
            }
            row.status.equals("Half Day", ignoreCase = true) -> {
                holder.tvStatus.text = "🔵 Half Day"
                holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#0284C7"))
                holder.tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#E0F2FE"))
                holder.tvTime.text = "In: ${row.checkIn ?: "—"}  Out: ${row.checkOut ?: "—"}"
                val hasSelfie = !row.checkInSelfie.isNullOrBlank() || !row.checkOutSelfie.isNullOrBlank()
                holder.ivPhoto.visibility = View.VISIBLE
                holder.ivPhoto.alpha      = if (hasSelfie) 1.0f else 0.25f
            }
            row.status.equals("Late", ignoreCase = true) -> {
                holder.tvStatus.text = "🔴 Late"
                holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                holder.tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#FEE2E2"))
                holder.tvTime.text = "In: ${row.checkIn ?: "—"}  Out: ${row.checkOut ?: "—"}"
                val hasSelfie = !row.checkInSelfie.isNullOrBlank() || !row.checkOutSelfie.isNullOrBlank()
                holder.ivPhoto.visibility = View.VISIBLE
                holder.ivPhoto.alpha      = if (hasSelfie) 1.0f else 0.25f
            }
            else -> {
                holder.tvStatus.text = "🟢 Present"
                holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#10B981"))
                holder.tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#D1FAE5"))
                holder.tvTime.text = "In: ${row.checkIn ?: "—"}  Out: ${row.checkOut ?: "—"}"
                val hasSelfie = !row.checkInSelfie.isNullOrBlank() || !row.checkOutSelfie.isNullOrBlank()
                holder.ivPhoto.visibility = View.VISIBLE
                holder.ivPhoto.alpha      = if (hasSelfie) 1.0f else 0.25f
            }
        }
        holder.ivPhoto.setOnClickListener {
            if (!row.checkInSelfie.isNullOrBlank() || !row.checkOutSelfie.isNullOrBlank()) {
                onPhotoClick(row)
            } else {
                Toast.makeText(holder.itemView.context, "No selfie available", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
