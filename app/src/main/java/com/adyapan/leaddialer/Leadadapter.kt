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
    private val onSalesToggle    : (Lead, Boolean) -> Unit = { _, _ -> },
    private val onItemClick      : (Lead) -> Unit = {}
) : ListAdapter<Lead, LeadAdapter.LeadViewHolder>(DIFF) {

    private var fullList = listOf<Lead>()
    private var currentFilterStatus = "All"
    private var currentSearchQuery = ""

    override fun submitList(list: List<Lead>?) {
        val sorted = list?.sortedByDescending { it.calledAt } ?: emptyList()
        fullList = sorted
        applyFilter()
    }

    fun filter(query: String) {
        currentSearchQuery = query
        applyFilter()
    }

    fun filterByStatus(status: String) {
        currentFilterStatus = status
        applyFilter()
    }

    private fun applyFilter() {
        var list = fullList

        // Filter by Status
        if (currentFilterStatus != "All") {
            list = when (currentFilterStatus) {
                "Connected" -> list.filter { it.status == "Connected" || it.status == "Interested" }
                "Pending" -> list.filter { it.status == "Pending" || it.status.isBlank() }
                "Interested" -> list.filter { it.status == "Interested" }
                "Busy" -> list.filter { it.status == "Busy" }
                "Sales" -> list.filter { it.salesDone }
                else -> list.filter { it.status == currentFilterStatus }
            }
        }

        // Filter by Search Query
        if (currentSearchQuery.isNotEmpty()) {
            list = list.filter {
                it.name.contains(currentSearchQuery, ignoreCase = true) ||
                it.phone.contains(currentSearchQuery, ignoreCase = true) ||
                it.collegeName.contains(currentSearchQuery, ignoreCase = true) ||
                it.collegeCity.contains(currentSearchQuery, ignoreCase = true)
            }
        }

        super.submitList(list)
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
        val viewStatusStrip : View   = view.findViewById(R.id.viewStatusStrip)
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

        // Premium dynamic pastel colors for avatar based on name hash
        val colors = listOf(
            0xFF3B82F6.toInt(), // Blue
            0xFF10B981.toInt(), // Emerald
            0xFF8B5CF6.toInt(), // Violet
            0xFFF59E0B.toInt(), // Amber
            0xFFEC4899.toInt(), // Pink
            0xFFEF4444.toInt(), // Red
            0xFF06B6D4.toInt()  // Cyan
        )
        val hash = lead.name.hashCode()
        val avatarBgColor = colors[kotlin.math.abs(hash) % colors.size]
        holder.tvAvatar.background?.setTint(avatarBgColor)

        // College name — show only if available
        if (lead.collegeName.isNotBlank()) {
            holder.tvCollegeName.text = "${lead.collegeName}"
            holder.tvCollegeName.isVisible = true
        } else {
            holder.tvCollegeName.isVisible = false
        }

        // College city — show only if available
        if (lead.collegeCity.isNotBlank()) {
            holder.tvCollegeCity.text = "${lead.collegeCity}"
            holder.tvCollegeCity.isVisible = true
        } else {
            holder.tvCollegeCity.isVisible = false
        }

        holder.tvHotBadge.visibility = View.GONE  // hot lead removed

        val statusColor = when {
            lead.status == "Wrong Number"                    -> 0xFFDC2626.toInt() // red
            lead.status == "Interested"                      -> 0xFF16A34A.toInt() // green
            lead.status == "Busy"                            -> 0xFFD97706.toInt() // amber
            lead.status == "Not Connected"                   -> 0xFF4B5563.toInt() // grey
            lead.status == "Not Interested"                  -> 0xFFB91C1C.toInt() // dark red
            lead.status.startsWith("Not Interested:")        -> 0xFFB91C1C.toInt() // dark red (custom reason)
            else                                             -> 0xFFFF6A00.toInt() // orange (Pending etc.)
        }

        val statusBgColor = when {
            lead.status == "Wrong Number"                    -> 0xFFFEE2E2.toInt() // light red
            lead.status == "Interested"                      -> 0xFFDCFCE7.toInt() // light green
            lead.status == "Busy"                            -> 0xFFFEF3C7.toInt() // light amber
            lead.status == "Not Connected"                   -> 0xFFF3F4F6.toInt() // light grey
            lead.status == "Not Interested"                  -> 0xFFFEE2E2.toInt() // light red
            lead.status.startsWith("Not Interested:")        -> 0xFFFEE2E2.toInt() // light red
            else                                             -> 0xFFFFE6D5.toInt() // light orange
        }

        holder.tvStatus.setTextColor(statusColor)
        holder.tvStatus.background?.setTint(statusBgColor)
        holder.viewStatusStrip.setBackgroundColor(statusColor)

        // ── Sales row — show for Hot Lead OR Interested ─────────────────
        val showSales = lead.status == "Interested"
        holder.salesRow.isVisible = showSales

        if (showSales) {
            if (lead.salesDone) {
                // Sale already recorded — Mark Sale dimmed, Not Done (Revert) bright
                holder.btnMarkSale.text  = "Sale Recorded"
                holder.btnMarkSale.setTextColor(Color.parseColor("#16A34A"))
                holder.btnMarkSale.setBackgroundResource(R.drawable.bg_btn_sale)
                holder.btnMarkSale.alpha = 0.55f

                holder.btnMarkNotDone.text = "✕  Revert"
                holder.btnMarkNotDone.setTextColor(Color.parseColor("#DC2626"))
                holder.btnMarkNotDone.setBackgroundResource(R.drawable.bg_btn_not_done_active)
                holder.btnMarkNotDone.alpha = 1f
            } else {
                // Sale not yet done — Mark Sale bright, Not Done dimmed
                holder.btnMarkSale.text  = "Mark as Sale"
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
        holder.itemView.setOnClickListener { onItemClick(lead) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Lead>() {
            override fun areItemsTheSame(a: Lead, b: Lead)    = a.id == b.id
            override fun areContentsTheSame(a: Lead, b: Lead) = a == b
        }
    }
}