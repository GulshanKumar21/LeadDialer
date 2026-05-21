package com.adyapan.leaddialer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch


class AdminLeadsFragment : Fragment() {

    private lateinit var viewModel: AdminViewModel

    companion object {
        private const val ARG_NAME       = "emp_name"
        private const val ARG_USER_ID    = "emp_uid"
        private const val ARG_TOTAL      = "emp_total"
        private const val ARG_CONNECTED  = "emp_connected"
        private const val ARG_INTERESTED = "emp_interested"
        private const val ARG_PENDING    = "emp_pending"
        private const val ARG_SALES_DONE = "emp_sales"
        private const val ARG_EXPECTED   = "emp_expected"

        fun newInstance(emp: EmployeeSummary): AdminLeadsFragment {
            val fragment = AdminLeadsFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_NAME,       emp.employeeName)
                putString(ARG_USER_ID,    emp.userId)
                putInt(ARG_TOTAL,         emp.totalLeads)
                putInt(ARG_CONNECTED,     emp.connected)
                putInt(ARG_INTERESTED,    emp.interested)
                putInt(ARG_PENDING,       emp.pending)
                putInt(ARG_SALES_DONE,    emp.salesDone)
                putInt(ARG_EXPECTED,      emp.expectedSales)
            }
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_admin_leads, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        viewModel = ViewModelProvider(requireActivity())[AdminViewModel::class.java]

        val name          = arguments?.getString(ARG_NAME)      ?: "Employee"
        val userId        = arguments?.getString(ARG_USER_ID)   ?: ""
        val total         = arguments?.getInt(ARG_TOTAL)        ?: 0
        val connected     = arguments?.getInt(ARG_CONNECTED)    ?: 0
        val interested    = arguments?.getInt(ARG_INTERESTED)   ?: 0
        val pending       = arguments?.getInt(ARG_PENDING)      ?: 0
        val salesDone     = arguments?.getInt(ARG_SALES_DONE)   ?: 0
        val expectedSales = arguments?.getInt(ARG_EXPECTED)     ?: 0

        view.findViewById<TextView>(R.id.tvAdminLeadEmployeeAvatar).text =
            name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        view.findViewById<TextView>(R.id.tvAdminLeadEmployeeName).text =
            name.replaceFirstChar { it.uppercase() }
        view.findViewById<TextView>(R.id.tvAdminLeadCount).text       = "$total leads"
        view.findViewById<TextView>(R.id.tvAdminLeadConnected).text   = "✅ $connected Connected"
        view.findViewById<TextView>(R.id.tvAdminLeadInterested).text  = "⭐ $interested Interested"
        view.findViewById<TextView>(R.id.tvAdminLeadPending).text     = "⏳ $pending Pending"
        view.findViewById<TextView>(R.id.tvAdminSalesDone)?.text      = "💰 Sales Done: $salesDone"
        view.findViewById<TextView>(R.id.tvAdminExpectedSales)?.text  = "🎯 Target: $expectedSales"

        val progressBar = view.findViewById<ProgressBar>(R.id.progressAdminLeads)

        val adapter = AdminLeadAdapter(
            onSalesDoneToggle = { lead ->
                lifecycleScope.launch {
                    val newVal = !lead.salesDone
                    val fid = lead.firestoreId ?: return@launch
                    val ok = FirestoreSource.updateSalesDone(fid, newVal)
                    if (ok) {
                        val msg = if (newVal) "✅ Sales Done marked!" else "↩️ Sales Done removed"
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "❌ Failed to update", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        val rv = view.findViewById<RecyclerView>(R.id.rvAdminLeads)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        viewModel.leadsLoading.observe(viewLifecycleOwner) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.employeeLeads.observe(viewLifecycleOwner) { leads ->
            adapter.submitList(leads)
        }


        if (userId.isNotBlank()) {
            viewModel.loadEmployeeLeads(userId)
        }


        val btnCallLogs = view.findViewById<android.widget.Button>(R.id.btnAdminViewCallLogs)
        btnCallLogs.setOnClickListener {
            val fragment = AdminCallHistoryFragment.newInstance(userId, name)
            parentFragmentManager.beginTransaction()
                .replace(R.id.adminFragmentContainer, fragment)
                .addToBackStack(null)
                .commit()
        }
    }
}
