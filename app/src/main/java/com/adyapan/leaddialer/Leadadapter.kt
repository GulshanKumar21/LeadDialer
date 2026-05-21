package com.adyapan.leaddialer

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class LeadAdapter(
    private val onCallClick      : (Lead) -> Unit,
    private val onSalesToggle    : (Lead, Boolean) -> Unit = { _, _ -> }
) : ListAdapter<Lead, LeadAdapter.LeadViewHolder>(DIFF) {

    private var fullList = listOf<Lead>()

    override fun submitList(list: List<Lead>?) {
        val sorted = list?.sortedByDescending { it.calledAt } ?: emptyList()
        fullList = sorted
        super.submitList(sorted)
    }

    fun filter(query: String) {
        val filtered = if (query.isEmpty()) fullList
        else fullList.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.phone.contains(query, ignoreCase = true) ||
            it.collegeName.contains(query, ignoreCase = true) ||
            it.collegeCity.contains(query, ignoreCase = true)
        }
        super.submitList(filtered)
    }

    inner class LeadViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAvatar      : TextView = view.findViewById(R.id.tvAvatar)
        val tvName        : TextView = view.findViewById(R.id.tvName)
        val tvPhone       : TextView = view.findViewById(R.id.tvPhone)
        val tvStatus      : TextView = view.findViewById(R.id.tvStatus)
        val tvCollegeName : TextView = view.findViewById(R.id.tvCollegeName)
        val tvCollegeCity : TextView = view.findViewById(R.id.tvCollegeCity)
        val tvHotBadge    : TextView = view.findViewById(R.id.tvHotBadge)
        val btnCall       : View     = view.findViewById(R.id.btnCall)
        val salesRow      : View     = view.findViewById(R.id.salesRow)
        val btnMarkSale   : TextView = view.findViewById(R.id.btnMarkSale)
        val btnMarkNotDone: TextView = view.findViewById(R.id.btnMarkNotDone)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeadViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lead, parent, false)
        return LeadViewHolder(view)
    }

    override fun onBindViewHolder(holder: LeadViewHolder, position: Int) {
        val lead = getItem(position)

        holder.tvName.text   = lead.name
        holder.tvPhone.text  = lead.phone
        holder.tvStatus.text = lead.status

        // Avatar — first letter of name
        holder.tvAvatar.text = lead.name
            .trim()
            .firstOrNull()
            ?.uppercaseChar()
            ?.toString()
            ?: "?"

        // College name — show only if available
        if (lead.collegeName.isNotBlank()) {
            holder.tvCollegeName.text = "🎓 ${lead.collegeName}"
            holder.tvCollegeName.isVisible = true
        } else {
            holder.tvCollegeName.isVisible = false
        }

        // College city — show only if available
        if (lead.collegeCity.isNotBlank()) {
            holder.tvCollegeCity.text = "📍 ${lead.collegeCity}"
            holder.tvCollegeCity.isVisible = true
        } else {
            holder.tvCollegeCity.isVisible = false
        }

        holder.tvHotBadge.visibility = View.GONE  // hot lead removed

        val statusColor = when {
            lead.status == "Wrong Number"                    -> 0xFF8B0000.toInt() // dark red
            lead.status == "Interested"                      -> 0xFF1565C0.toInt() // blue
            lead.status == "Busy"                            -> 0xFFE65100.toInt() // orange
            lead.status == "Not Connected"                   -> 0xFF757575.toInt() // grey
            lead.status == "Not Interested"                  -> 0xFFC62828.toInt() // red
            lead.status.startsWith("Not Interested:")        -> 0xFFC62828.toInt() // red (custom reason)
            else                                             -> 0xFFFF6A00.toInt() // orange (Pending etc.)
        }
        holder.tvStatus.setTextColor(statusColor)

        // ── Sales row — show for Hot Lead OR Interested ─────────────────
        val showSales = lead.status == "Interested"
        holder.salesRow.isVisible = showSales

        if (showSales) {
            if (lead.salesDone) {
                // Sale already recorded — Mark Sale dimmed, Not Done (Revert) bright
                holder.btnMarkSale.text  = "✅  Sale Recorded"
                holder.btnMarkSale.setTextColor(Color.parseColor("#16A34A"))
                holder.btnMarkSale.setBackgroundResource(R.drawable.bg_btn_sale)
                holder.btnMarkSale.alpha = 0.55f

                holder.btnMarkNotDone.text = "✕  Revert"
                holder.btnMarkNotDone.setTextColor(Color.parseColor("#DC2626"))
                holder.btnMarkNotDone.setBackgroundResource(R.drawable.bg_btn_not_done_active)
                holder.btnMarkNotDone.alpha = 1f
            } else {
                // Sale not yet done — Mark Sale bright, Not Done dimmed
                holder.btnMarkSale.text  = "💰  Mark as Sale"
                holder.btnMarkSale.setTextColor(Color.WHITE)
                holder.btnMarkSale.setBackgroundResource(R.drawable.bg_btn_sale)
                holder.btnMarkSale.alpha = 1f

                holder.btnMarkNotDone.text = "✕  Not Done"
                holder.btnMarkNotDone.setTextColor(Color.parseColor("#9CA3AF"))
                holder.btnMarkNotDone.setBackgroundResource(R.drawable.bg_btn_not_done)
                holder.btnMarkNotDone.alpha = 0.5f
            }

            holder.btnMarkSale.setOnClickListener    { onSalesToggle(lead, true)  }
            holder.btnMarkNotDone.setOnClickListener { onSalesToggle(lead, false) }
        }

        holder.btnCall.setOnClickListener { onCallClick(lead) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Lead>() {
            override fun areItemsTheSame(a: Lead, b: Lead)    = a.id == b.id
            override fun areContentsTheSame(a: Lead, b: Lead) = a == b
        }
    }
}