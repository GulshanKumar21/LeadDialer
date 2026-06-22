package com.adyapan.leaddialer

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class AdminEmployeeLeavesActivity : AppCompatActivity() {

    private lateinit var tvEmpName       : TextView
    private lateinit var tvAvatar        : TextView
    private lateinit var btnBack         : ImageButton
    private lateinit var btnAll          : TextView
    private lateinit var btnPending      : TextView
    private lateinit var btnApproved     : TextView
    private lateinit var btnRejected     : TextView
    private lateinit var tvCount         : TextView
    private lateinit var rvLeaves        : RecyclerView
    private lateinit var tvNoLeaves      : TextView
    private lateinit var progressBar     : ProgressBar

    private var uid  = ""
    private var name = ""

    private var allLeaves    = listOf<LeaveRequest>()
    private var currentFilter = "All"

    private lateinit var adapter: LeaveRequestAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_employee_leaves)

        uid  = intent.getStringExtra("uid") ?: ""
        name = intent.getStringExtra("name") ?: "Employee"

        tvEmpName    = findViewById(R.id.tvAdminLeavesEmpName)
        tvAvatar     = findViewById(R.id.tvAdminLeavesEmpAvatar)
        btnBack      = findViewById(R.id.btnLeavesBack)
        btnAll       = findViewById(R.id.btnFilterAll)
        btnPending   = findViewById(R.id.btnFilterPending)
        btnApproved  = findViewById(R.id.btnFilterApproved)
        btnRejected  = findViewById(R.id.btnFilterRejected)
        tvCount      = findViewById(R.id.tvLeaveCount)
        rvLeaves     = findViewById(R.id.rvAdminEmployeeLeaves)
        tvNoLeaves   = findViewById(R.id.tvNoAdminEmployeeLeaves)
        progressBar  = findViewById(R.id.progressAdminEmployeeLeaves)

        tvEmpName.text = name
        tvAvatar.text  = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        btnBack.setOnClickListener { finish() }

        adapter = LeaveRequestAdapter(
            isAdminMode = true,
            onApprove   = { req -> updateStatus(req, "Approved") },
            onReject    = { req -> updateStatus(req, "Rejected") }
        )
        rvLeaves.layoutManager = LinearLayoutManager(this)
        rvLeaves.adapter = adapter

        btnAll.setOnClickListener      { setFilter("All") }
        btnPending.setOnClickListener  { setFilter("Pending") }
        btnApproved.setOnClickListener { setFilter("Approved") }
        btnRejected.setOnClickListener { setFilter("Rejected") }

        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            FirestoreSource.leaveRequestsFlow().collect { leaves ->
                progressBar.visibility = View.GONE
                // Filter leaves only for this specific employee UID
                allLeaves = leaves.filter { it.uid == uid }
                applyFilter()
            }
        }
    }

    private fun setFilter(filter: String) {
        currentFilter = filter
        val allBtns = listOf(btnAll, btnPending, btnApproved, btnRejected)
        allBtns.forEach { btn ->
            btn.setBackgroundResource(R.drawable.bg_filter_chip_inactive)
            btn.setTextColor(android.graphics.Color.parseColor("#64748B"))
        }
        val activeBtn = when (filter) {
            "Pending"  -> btnPending
            "Approved" -> btnApproved
            "Rejected" -> btnRejected
            else       -> btnAll
        }
        activeBtn.setBackgroundResource(R.drawable.bg_filter_chip_active)
        activeBtn.setTextColor(android.graphics.Color.WHITE)
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = if (currentFilter == "All") allLeaves
                       else allLeaves.filter { it.status == currentFilter }

        adapter.submitList(filtered)

        val label = when (currentFilter) {
            "All"      -> "${allLeaves.size} total requests"
            "Pending"  -> "${filtered.size} pending"
            "Approved" -> "${filtered.size} approved"
            "Rejected" -> "${filtered.size} rejected"
            else       -> "${filtered.size} requests"
        }
        tvCount.text = label

        if (filtered.isEmpty()) {
            rvLeaves.visibility   = View.GONE
            tvNoLeaves.visibility = View.VISIBLE
        } else {
            rvLeaves.visibility   = View.VISIBLE
            tvNoLeaves.visibility = View.GONE
        }
    }

    private fun updateStatus(req: LeaveRequest, newStatus: String) {
        lifecycleScope.launch {
            val ok = FirestoreSource.updateLeaveStatus(req.id, newStatus)
            if (ok) {
                val emoji = if (newStatus == "Approved") "✅" else "❌"
                Toast.makeText(
                    this@AdminEmployeeLeavesActivity,
                    "$emoji Leave ${newStatus.lowercase()} for ${req.employeeName}",
                    Toast.LENGTH_SHORT
                ).show()

                if (req.employeeEmail.isNotBlank()) {
                    SheetsSync.sendLeaveStatusEmail(
                        empEmail  = req.employeeEmail,
                        empName   = req.employeeName,
                        leaveType = req.leaveType,
                        fromDate  = req.fromDate,
                        toDate    = req.toDate,
                        newStatus = newStatus
                    )
                }
            } else {
                Toast.makeText(this@AdminEmployeeLeavesActivity, "❌ Failed to update. Check internet.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
