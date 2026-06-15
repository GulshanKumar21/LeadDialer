package com.adyapan.leaddialer

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ProfileFragment : Fragment() {

    private lateinit var tvName        : TextView
    private lateinit var tvDesignation : TextView
    private lateinit var tvEmail       : TextView
    private lateinit var tvInitials    : TextView
    private lateinit var tvEmpId       : TextView
    private lateinit var tvDepartment  : TextView
    private lateinit var tvManager     : TextView
    private lateinit var tvPhone       : TextView
    private lateinit var tvLocation    : TextView
    private lateinit var btnEditPersonal : ImageButton

    private lateinit var btnApplyLeave   : Button
    private lateinit var rvLeaveRequests : RecyclerView
    private lateinit var tvNoLeaves      : TextView
    private lateinit var tvProfileDoj    : TextView
    private lateinit var tvCasualLeaves  : TextView
    private lateinit var tvLeavesEarned  : TextView
    private lateinit var tvLeavesTaken   : TextView
    private lateinit var leaveAdapter    : LeaveRequestAdapter
    
    private var allLeaves = listOf<LeaveRequest>()

    private var currentProfile: UserProfile? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        tvName         = view.findViewById(R.id.tvProfileName)
        tvDesignation  = view.findViewById(R.id.tvProfileDesignation)
        tvEmail        = view.findViewById(R.id.tvProfileEmail)
        tvInitials     = view.findViewById(R.id.tvProfileInitials)
        tvEmpId        = view.findViewById(R.id.tvProfileEmpId)
        tvDepartment   = view.findViewById(R.id.tvProfileDepartment)
        tvManager      = view.findViewById(R.id.tvProfileManager)
        tvPhone        = view.findViewById(R.id.tvProfilePhone)
        tvLocation     = view.findViewById(R.id.tvProfileLocation)
        btnEditPersonal = view.findViewById(R.id.btnEditPersonal)

        btnApplyLeave   = view.findViewById(R.id.btnApplyLeave)
        rvLeaveRequests = view.findViewById(R.id.rvLeaveRequests)
        tvNoLeaves      = view.findViewById(R.id.tvNoLeaves)
        tvProfileDoj    = view.findViewById(R.id.tvProfileDoj)
        tvCasualLeaves  = view.findViewById(R.id.tvCasualLeaves)
        tvLeavesEarned  = view.findViewById(R.id.tvLeavesEarned)
        tvLeavesTaken   = view.findViewById(R.id.tvLeavesTaken)

        leaveAdapter = LeaveRequestAdapter(isAdminMode = false)
        rvLeaveRequests.layoutManager = LinearLayoutManager(requireContext())
        rvLeaveRequests.adapter = leaveAdapter

        val user = FirebaseAuth.getInstance().currentUser
        val uid  = user?.uid ?: return view

        val prefs        = LoginPage.getEncryptedPrefs(requireContext())
        val defaultEmail = user.email ?: prefs.getString(LoginPage.KEY_EMAIL, "Unknown") ?: "Unknown"
        var defaultName  = user.displayName?.takeIf { it.isNotBlank() }
            ?: defaultEmail.substringBefore("@").replaceFirstChar { it.uppercase() }

        // Fallback values
        tvEmail.text    = defaultEmail
        tvName.text     = defaultName
        tvInitials.text = defaultName.take(2).uppercase()

        lifecycleScope.launch {
            FirestoreSource.userProfileFlow(uid).collect { profile ->
                if (profile != null) {
                    currentProfile = profile
                    val displayName = profile.name.ifBlank { defaultName }
                    defaultName     = displayName

                    tvName.text        = displayName
                    tvInitials.text    = displayName.take(2).uppercase()
                    tvEmail.text       = profile.email.ifBlank { defaultEmail }
                    tvPhone.text       = profile.phone.ifBlank { "Not provided" }
                    tvDesignation.text = profile.designation.ifBlank { "Employee" }
                    tvEmpId.text       = profile.employeeId.ifBlank { "N/A" }
                    tvDepartment.text  = profile.department.ifBlank { "N/A" }
                    tvManager.text     = profile.reportingManager.ifBlank { "N/A" }
                    tvLocation.text    = profile.workLocation.ifBlank { "N/A" }
                    tvProfileDoj.text  = profile.dateOfJoining.ifBlank { "Not Set" }
                    updateCasualLeaves()
                }
            }
        }

        lifecycleScope.launch {
            FirestoreSource.leaveRequestsFlow().collect { leaves ->
                allLeaves = leaves
                leaveAdapter.submitList(leaves)
                if (leaves.isEmpty()) {
                    rvLeaveRequests.visibility = View.GONE
                    tvNoLeaves.visibility      = View.VISIBLE
                } else {
                    rvLeaveRequests.visibility = View.VISIBLE
                    tvNoLeaves.visibility      = View.GONE
                }
                updateCasualLeaves()
            }
        }

        btnEditPersonal.setOnClickListener {
            showEditPersonalDialog(uid, defaultEmail, defaultName)
        }

        btnApplyLeave.setOnClickListener {
            showApplyLeaveDialog(uid, defaultEmail, defaultName, currentProfile?.employeeId ?: "")
        }

        return view
    }
    
    private fun updateCasualLeaves() {
        val dojString = currentProfile?.dateOfJoining
        if (dojString.isNullOrBlank()) {
            tvCasualLeaves.text = "0"
            tvLeavesEarned.text = "0"
            tvLeavesTaken.text = "0"
            return
        }
        
        try {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val dojDate = sdf.parse(dojString) ?: return
            
            val calDoj = Calendar.getInstance().apply { time = dojDate }
            val calNow = Calendar.getInstance()
            
            var monthsPassed = 0
            if (calNow.after(calDoj)) {
                val yearDiff = calNow.get(Calendar.YEAR) - calDoj.get(Calendar.YEAR)
                val monthDiff = calNow.get(Calendar.MONTH) - calDoj.get(Calendar.MONTH)
                monthsPassed = yearDiff * 12 + monthDiff
            }
            
            // 1 leave credit per month starting from joining month
            val totalEarned = (monthsPassed + 1).toDouble().coerceAtLeast(0.0)
            
            // Count all non-rejected, non-LOP leaves. Half Day = 0.5, others = 1.0
            val taken = allLeaves.sumOf { req ->
                if (req.status == "Rejected" || req.leaveType.contains("LOP", ignoreCase = true)) {
                    0.0
                } else if (req.leaveType.equals("Half Day", ignoreCase = true)) {
                    0.5
                } else {
                    1.0
                }
            }
            
            val available = (totalEarned - taken).coerceAtLeast(0.0)
            
            val fmt: (Double) -> String = { d ->
                if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()
            }
            
            tvLeavesEarned.text = fmt(totalEarned)
            tvLeavesTaken.text = fmt(taken)
            tvCasualLeaves.text = fmt(available)
            
        } catch (e: Exception) {
            e.printStackTrace()
            tvCasualLeaves.text = "0"
            tvLeavesEarned.text = "0"
            tvLeavesTaken.text = "0"
        }
    }

    // ── Edit personal info dialog ─────────────────────────────────────
    private fun showEditPersonalDialog(uid: String, fallbackEmail: String, fallbackName: String) {
        val dialog = BottomSheetDialog(requireContext())
        val view   = layoutInflater.inflate(R.layout.dialog_manage_employee_profile, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.tvManageProfileTitle).text = "Edit Personal Info"

        val etName  = view.findViewById<EditText>(R.id.etManageEmpId)
        etName.hint = "Full Name"
        val etPhone  = view.findViewById<EditText>(R.id.etManageDesignation)
        etPhone.hint = "Phone Number"

        view.findViewById<View>(R.id.etManageDepartment).visibility = View.GONE
        view.findViewById<View>(R.id.etManageManager).visibility    = View.GONE
        view.findViewById<View>(R.id.etManageLocation).visibility   = View.GONE

        etName.setText(currentProfile?.name ?: fallbackName)
        etPhone.setText(currentProfile?.phone ?: "")

        view.findViewById<Button>(R.id.btnSaveProfile).setOnClickListener {
            lifecycleScope.launch {
                val updated = currentProfile?.copy(
                    name  = etName.text.toString().trim(),
                    phone = etPhone.text.toString().trim()
                ) ?: UserProfile(
                    uid   = uid,
                    name  = etName.text.toString().trim(),
                    email = fallbackEmail,
                    phone = etPhone.text.toString().trim()
                )
                val ok = FirestoreSource.saveUserProfile(updated)
                Toast.makeText(
                    requireContext(),
                    if (ok) "Profile updated" else "Failed to update",
                    Toast.LENGTH_SHORT
                ).show()
                if (ok) dialog.dismiss()
            }
        }
        dialog.show()
    }

    // ── Apply Leave dialog ────────────────────────────────────────────
    private fun showApplyLeaveDialog(uid: String, empEmail: String, empName: String, empId: String) {
        val dialog = BottomSheetDialog(requireContext())
        val view   = layoutInflater.inflate(R.layout.dialog_apply_leave, null)
        dialog.setContentView(view)

        // ── Leave type spinner ────────────────────────────────────────
        val spinnerLeave = view.findViewById<Spinner>(R.id.spinnerLeaveType)
        val leaveTypes   = arrayOf("Sick Leave", "Casual Leave", "Half Day", "Emergency Leave", "Maternity Leave")
        spinnerLeave.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, leaveTypes)

        // ── Admin email — LOCKED to 2 verified addresses ──────────────
        val spinnerAdmin  = view.findViewById<Spinner>(R.id.spinnerAdminEmail)
        val adminEmails   = arrayOf("hr@adyapan.com", "mounika@adyapan.com")
        spinnerAdmin.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, adminEmails)

        // ── Date pickers (past dates BLOCKED) ────────────────────────
        val tvFrom = view.findViewById<TextView>(R.id.tvFromDate)
        val tvTo   = view.findViewById<TextView>(R.id.tvToDate)
        val sdf    = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        val todayMs = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val fromCal     = Calendar.getInstance()
        val toCal       = Calendar.getInstance()
        var fromSelected = false
        var toSelected   = false

        tvFrom.setOnClickListener {
            val dp = DatePickerDialog(requireContext(), { _, y, m, d ->
                fromCal.set(y, m, d)
                tvFrom.text = sdf.format(fromCal.time)
                tvFrom.setTextColor(resources.getColor(android.R.color.black, null))
                fromSelected = true
                // Auto-advance To date if it's before From
                if (toCal.timeInMillis < fromCal.timeInMillis) {
                    toCal.timeInMillis = fromCal.timeInMillis
                    tvTo.text = sdf.format(toCal.time)
                    tvTo.setTextColor(resources.getColor(android.R.color.black, null))
                    toSelected = true
                }
            }, fromCal.get(Calendar.YEAR), fromCal.get(Calendar.MONTH), fromCal.get(Calendar.DAY_OF_MONTH))
            dp.datePicker.minDate = todayMs   // 🔒 No past dates
            dp.show()
        }

        tvTo.setOnClickListener {
            val dp = DatePickerDialog(requireContext(), { _, y, m, d ->
                toCal.set(y, m, d)
                tvTo.text = sdf.format(toCal.time)
                tvTo.setTextColor(resources.getColor(android.R.color.black, null))
                toSelected = true
            }, toCal.get(Calendar.YEAR), toCal.get(Calendar.MONTH), toCal.get(Calendar.DAY_OF_MONTH))
            dp.datePicker.minDate = if (fromSelected) fromCal.timeInMillis else todayMs   // 🔒
            dp.show()
        }

        val etReason = view.findViewById<EditText>(R.id.etLeaveReason)

        // ── Submit ────────────────────────────────────────────────────
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmitLeave)
        btnSubmit.setOnClickListener {
            val selectedType = spinnerLeave.selectedItem.toString()
            
            // Dynamic check of available balance
            val dojString = currentProfile?.dateOfJoining
            var available = 0.0
            if (!dojString.isNullOrBlank()) {
                try {
                    val sdfDoj = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    val dojDate = sdfDoj.parse(dojString)
                    if (dojDate != null) {
                        val calDoj = Calendar.getInstance().apply { time = dojDate }
                        val calNow = Calendar.getInstance()
                        var monthsPassed = 0
                        if (calNow.after(calDoj)) {
                            val yearDiff = calNow.get(Calendar.YEAR) - calDoj.get(Calendar.YEAR)
                            val monthDiff = calNow.get(Calendar.MONTH) - calDoj.get(Calendar.MONTH)
                            monthsPassed = yearDiff * 12 + monthDiff
                        }
                        val totalEarned = (monthsPassed + 1).toDouble().coerceAtLeast(0.0)
                        val taken = allLeaves.sumOf { req ->
                            if (req.status == "Rejected" || req.leaveType.contains("LOP", ignoreCase = true)) {
                                0.0
                            } else if (req.leaveType.equals("Half Day", ignoreCase = true)) {
                                0.5
                            } else {
                                1.0
                            }
                        }
                        available = (totalEarned - taken).coerceAtLeast(0.0)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val required = if (selectedType.equals("Half Day", ignoreCase = true)) 0.5 else 1.0
            val leaveType = if (available < required && 
                (selectedType.equals("Sick Leave", ignoreCase = true) || 
                 selectedType.equals("Casual Leave", ignoreCase = true) || 
                 selectedType.equals("Half Day", ignoreCase = true))) {
                "LOP"
            } else {
                selectedType
            }

            if (leaveType == "LOP" && !selectedType.equals("LOP", ignoreCase = true)) {
                Toast.makeText(requireContext(), "⚠️ Out of paid leave balance. Applied as LOP (Loss of Pay).", Toast.LENGTH_LONG).show()
            }

            val fromDate   = tvFrom.text.toString()
            val toDate     = tvTo.text.toString()
            val reason     = etReason.text.toString().trim()
            val adminEmail = adminEmails[spinnerAdmin.selectedItemPosition]   // always valid

            if (!fromSelected) {
                Toast.makeText(requireContext(), "Please select From date", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!toSelected) {
                Toast.makeText(requireContext(), "Please select To date", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (reason.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a reason", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSubmit.isEnabled = false
            btnSubmit.text = "Submitting..."

            val llPadding = 40
            val ll = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(llPadding, llPadding, llPadding, llPadding)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val progressBar = android.widget.ProgressBar(requireContext()).apply {
                isIndeterminate = true
                setPadding(0, 0, llPadding, 0)
            }
            val tvText = android.widget.TextView(requireContext()).apply {
                text = "Submitting Leave Request..."
                setTextColor(android.graphics.Color.BLACK)
                textSize = 18f
            }
            ll.addView(progressBar)
            ll.addView(tvText)

            val progressDialog = android.app.AlertDialog.Builder(requireContext())
                .setCancelable(false)
                .setView(ll)
                .create()
            progressDialog.show()

            val leaveRequest = LeaveRequest(
                uid           = uid,
                employeeName  = empName,
                employeeEmail = empEmail,
                leaveType     = leaveType,
                fromDate      = fromDate,
                toDate        = toDate,
                reason        = reason,
                status        = "Pending",
                appliedAt     = System.currentTimeMillis()
            )

            lifecycleScope.launch {
                val docId = FirestoreSource.saveLeaveRequest(leaveRequest)
                if (docId != null) {

                    // ━━ Try to auto-send via GAS backend first ━━━━━━━━━━━━━━━━━━━━━
                    val gasSent = SheetsSync.sendLeaveEmail(
                        adminEmail = adminEmail,
                        empName    = empName,
                        empId      = empId,
                        empEmail   = empEmail,
                        leaveType  = leaveType,
                        fromDate   = fromDate,
                        toDate     = toDate,
                        reason     = reason
                    )

                    if (gasSent) {
                        // ✅ Email sent automatically via GAS — no user action needed
                        Toast.makeText(
                            requireContext(),
                            "✅ Leave submitted! Email sent to admin automatically.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        // ⚠️ GAS failed (offline / error) — open email app as fallback
                        openLeaveEmailApp(
                            adminEmail = adminEmail,
                            empName    = empName,
                            empId      = empId,
                            empEmail   = empEmail,
                            leaveType  = leaveType,
                            fromDate   = fromDate,
                            toDate     = toDate,
                            reason     = reason
                        )
                        Toast.makeText(
                            requireContext(),
                            "✅ Leave submitted! Please press Send in the email app to notify admin.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    progressDialog.dismiss()
                    dialog.dismiss()
                } else {
                    progressDialog.dismiss()
                    btnSubmit.isEnabled = true
                    btnSubmit.text = "Submit"

                    val loggedIn = FirebaseAuth.getInstance().currentUser != null
                    Toast.makeText(
                        requireContext(),
                        if (loggedIn) "❌ Submit failed. Check internet & try again."
                        else          "❌ Session expired. Please log in again.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        dialog.show()
    }


    private fun openLeaveEmailApp(
        adminEmail : String,
        empName    : String,
        empId      : String,
        empEmail   : String,
        leaveType  : String,
        fromDate   : String,
        toDate     : String,
        reason     : String
    ) {
        val subject = "Leave Request - $empName ($leaveType)"
        val body    = """
Dear Admin,

I would like to request leave. Please find the details below:

Employee Name   : $empName
Employee ID     : $empId
Employee Email  : $empEmail
Leave Type      : $leaveType
From Date       : $fromDate
To Date         : $toDate
Reason          : $reason

Kindly approve this request at your earliest convenience.

Thank you,
$empName
        """.trimIndent()

        // ✅ Correct mailto URI construction — Uri.encode each part separately
        val mailto = "mailto:${android.net.Uri.encode(adminEmail)}" +
            "?subject=${android.net.Uri.encode(subject)}" +
            "&body=${android.net.Uri.encode(body)}"

        val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse(mailto))

        try {
            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent)
            } else {
                // Fallback: ACTION_SEND with rfc822 chooser
                val fallback = Intent(Intent.ACTION_SEND).apply {
                    type = "message/rfc822"
                    putExtra(Intent.EXTRA_EMAIL,   arrayOf(adminEmail))
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT,    body)
                }
                startActivity(Intent.createChooser(fallback, "Send via Email"))
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "❌ No email app found. Please email $adminEmail manually.", Toast.LENGTH_LONG).show()
        }
    }
}
