package com.adyapan.leaddialer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AdminAttendanceFragment : Fragment() {

    private lateinit var viewModel: AdminViewModel
    private lateinit var adapter: AdminEmployeeAdapter
    private lateinit var rvAttendance: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var tvNoAttendance: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_admin_attendance, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(requireActivity())[AdminViewModel::class.java]

        rvAttendance = view.findViewById(R.id.rvAdminAttendance)
        progress = view.findViewById(R.id.progressAttendance)
        tvNoAttendance = view.findViewById(R.id.tvNoAttendance)

        adapter = AdminEmployeeAdapter(
            showEditButton = false,
            onClick = { emp ->
                val intent = Intent(requireContext(), EmployeeAttendanceActivity::class.java)
                intent.putExtra("uid", emp.userId)
                startActivity(intent)
            },
            onEditClick = { }
        )
        
        rvAttendance.layoutManager = LinearLayoutManager(requireContext())
        rvAttendance.adapter = adapter

        fetchEmployees()
    }

    private fun fetchEmployees() {
        progress.visibility = View.VISIBLE
        tvNoAttendance.visibility = View.GONE
        rvAttendance.visibility = View.GONE

        viewModel.employees.observe(viewLifecycleOwner) { summaries ->
            progress.visibility = View.GONE
            if (summaries.isNullOrEmpty()) {
                tvNoAttendance.visibility = View.VISIBLE
            } else {
                rvAttendance.visibility = View.VISIBLE
                adapter.submitList(summaries)
            }
        }
    }
}
