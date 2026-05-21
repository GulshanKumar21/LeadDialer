package com.adyapan.leaddialer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class AdminLeavesFragment : Fragment() {

    private lateinit var rvLeaves     : RecyclerView
    private lateinit var tvNoLeaves   : TextView
    private lateinit var tvCount      : TextView
    private lateinit var btnAll       : TextView
    private lateinit var btnPending   : TextView
    private lateinit var btnApproved  : TextView
    private lateinit var btnRejected  : TextView

    private var allLeaves    = listOf<LeaveRequest>()
    private var currentFilter = "All"

    private lateinit var adapter: LeaveRequestAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_admin_leaves, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvLeaves   = view.findViewById(R.id.rvAdminLeaves)
        tvNoLeaves = view.findViewById(R.id.tvNoAdminLeaves)
        tvCount    = view.findViewById(R.id.tvLeaveCount)
        btnAll      = view.findViewById(R.id.btnFilterAll)
        btnPending  = view.findViewById(R.id.btnFilterPending)
        btnApproved = view.findViewById(R.id.btnFilterApproved)
        btnRejected = view.findViewById(R.id.btnFilterRejected)

        adapter = LeaveRequestAdapter(
            isAdminMode = true,
            onApprove   = { req -> updateStatus(req, "Approved") },
            onReject    = { req -> updateStatus(req, "Rejected")  }
        )
        rvLeaves.layoutManager = LinearLayoutManager(requireContext())
        rvLeaves.adapter = adapter


        btnAll.setOnClickListener      { setFilter("All")      }
        btnPending.setOnClickListener  { setFilter("Pending")  }
        btnApproved.setOnClickListener { setFilter("Approved") }
        btnRejected.setOnClickListener { setFilter("Rejected") }

        lifecycleScope.launch {
            FirestoreSource.leaveRequestsFlow().collect { leaves ->
                allLeaves = leaves
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
                    requireContext(),
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
                Toast.makeText(requireContext(), "❌ Failed to update. Check internet.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
