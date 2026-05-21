package com.adyapan.leaddialer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class SalesEmployeeAdapter(
    private val onClick: (EmployeeSummary) -> Unit
) : ListAdapter<EmployeeSummary, SalesEmployeeAdapter.VH>(DIFF) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val avatar : TextView = view.findViewById(R.id.tvSalesAvatar)
        val name   : TextView = view.findViewById(R.id.tvSalesEmpName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sales_employee, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val emp = getItem(position)
        val name = emp.employeeName.replaceFirstChar { it.uppercase() }
        holder.name.text   = name
        holder.avatar.text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.itemView.setOnClickListener { onClick(emp) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<EmployeeSummary>() {
            override fun areItemsTheSame(a: EmployeeSummary, b: EmployeeSummary) = a.userId == b.userId
            override fun areContentsTheSame(a: EmployeeSummary, b: EmployeeSummary) = a == b
        }
    }
}
