package com.adyapan.leaddialer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog

class AdminSalesFragment : Fragment() {

    private lateinit var viewModel: AdminViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_admin_sales, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(requireActivity())[AdminViewModel::class.java]

        val rv = view.findViewById<RecyclerView>(R.id.rvSalesEmployees)
        rv.layoutManager = LinearLayoutManager(requireContext())

        val adapter = SalesEmployeeAdapter { emp ->
            showSalesDetailSheet(emp)
        }
        rv.adapter = adapter

        viewModel.employees.observe(viewLifecycleOwner) { summaries ->
            adapter.submitList(summaries)
        }
    }

    private fun showSalesDetailSheet(emp: EmployeeSummary) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottomsheet_sales_detail, null)
        dialog.setContentView(view)

        val name = emp.employeeName.replaceFirstChar { it.uppercase() }
        view.findViewById<TextView>(R.id.tvSalesDetailName).text = "👤 $name"
        view.findViewById<TextView>(R.id.tvSalesDetailDone).text = emp.salesDone.toString()
        view.findViewById<TextView>(R.id.tvSalesDetailExpected).text = emp.expectedSales.toString()

        val diff = emp.salesDone - emp.expectedSales
        val tvDiff = view.findViewById<TextView>(R.id.tvSalesDetailDiff)
        when {
            diff >= 0 -> {
                tvDiff.text = "+$diff ✅ Target Achieved!"
                tvDiff.setTextColor(0xFF1B7A34.toInt())
            }
            else -> {
                tvDiff.text = "$diff ⚠️ Below Target"
                tvDiff.setTextColor(0xFFE53935.toInt())
            }
        }

        dialog.show()
    }
}
