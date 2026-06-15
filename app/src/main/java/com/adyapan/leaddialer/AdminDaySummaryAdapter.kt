package com.adyapan.leaddialer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class AdminDaySummaryAdapter : ListAdapter<AdminViewModel.DaySummary, AdminDaySummaryAdapter.VH>(DIFF) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate     : TextView = view.findViewById(R.id.tvDayDate)
        val tvDials    : TextView = view.findViewById(R.id.tvDayDials)
        val tvConnected: TextView = view.findViewById(R.id.tvDayConnected)
        val tvSales    : TextView = view.findViewById(R.id.tvDaySales)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_day_summary, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.tvDate.text      = item.dateDisplay
        holder.tvDials.text     = item.totalCalled.toString()
        holder.tvConnected.text = item.connected.toString()
        holder.tvSales.text     = item.sales.toString()
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AdminViewModel.DaySummary>() {
            override fun areItemsTheSame(a: AdminViewModel.DaySummary, b: AdminViewModel.DaySummary) = a.dateKey == b.dateKey
            override fun areContentsTheSame(a: AdminViewModel.DaySummary, b: AdminViewModel.DaySummary) = a == b
        }
    }
}
