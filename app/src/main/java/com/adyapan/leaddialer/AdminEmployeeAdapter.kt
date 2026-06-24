package com.adyapan.leaddialer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class AdminEmployeeAdapter(
    private val showEditButton   : Boolean = true,
    private val onClick          : (EmployeeSummary) -> Unit,
    private val onEditClick      : (EmployeeSummary) -> Unit,
    private val onMessageClick   : (EmployeeSummary) -> Unit = {},
    private val onSetTargetClick : (EmployeeSummary) -> Unit = {},
    private val onTlAssign       : (emp: EmployeeSummary, tl: TeamLeaderManager.TeamLeader?) -> Unit = { _, _ -> }
) : ListAdapter<EmployeeSummary, AdminEmployeeAdapter.VH>(DIFF) {

    // TL list — updated by fragment via setTlList()
    private var tlList: List<TeamLeaderManager.TeamLeader> = emptyList()

    /** Call this from the fragment whenever the TL list changes */
    fun setTlList(list: List<TeamLeaderManager.TeamLeader>) {
        tlList = list
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val avatar        : TextView = view.findViewById(R.id.tvEmployeeAvatar)
        val name          : TextView = view.findViewById(R.id.tvEmployeeName)
        val email         : TextView = view.findViewById(R.id.tvEmployeeEmail)
        val tlBadge       : TextView = view.findViewById(R.id.tvTlBadge)
        val leads         : TextView = view.findViewById(R.id.tvLeadCount)
        val connected     : TextView = view.findViewById(R.id.tvConnectedCount)
        val interested    : TextView = view.findViewById(R.id.tvInterestedCount)
        val sales         : TextView = view.findViewById(R.id.tvSalesCount)
        val expected      : TextView = view.findViewById(R.id.tvExpectedSales)
        val adminTarget   : TextView = view.findViewById(R.id.tvAdminTarget)
        val rowAdminTarget: View     = view.findViewById(R.id.rowAdminTarget)
        val spinnerTl     : Spinner  = view.findViewById(R.id.spinnerTlAssign)
        val btnEdit       : View     = view.findViewById(R.id.btnEditProfile)
        val btnMessage    : View     = view.findViewById(R.id.btnSendMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_employee, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val emp = getItem(position)

        val initial = emp.employeeName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.avatar.text     = initial
        holder.name.text       = emp.employeeName.replaceFirstChar { it.uppercase() }
        holder.email.text      = emp.userId.take(20)
        holder.leads.text      = emp.totalLeads.toString()
        holder.connected.text  = emp.connected.toString()
        holder.interested.text = emp.interested.toString()
        holder.sales.text      = emp.salesDone.toString()
        holder.expected.text   = emp.expectedSales.toString()

        val card = holder.itemView as androidx.cardview.widget.CardView

        // TL badge (blue pill = assigned, orange pill = no TL)
        if (emp.tlName.isNotBlank()) {
            holder.tlBadge.visibility       = View.GONE
            card.setCardBackgroundColor(0xFFF0FDF4.toInt()) // very light green
        } else {
            holder.tlBadge.text             = "No TL assigned"
            holder.tlBadge.setBackgroundColor(0xFFFF6A00.toInt()) // orange
            holder.tlBadge.visibility       = View.VISIBLE
            card.setCardBackgroundColor(0xFFFFFFFF.toInt()) // white
        }

        // ── TL Assignment Spinner ──────────────────────────────────────────────
        // Items: "-- No TL --" + each TL name
        val spinnerItems = mutableListOf("-- No TL --")
        spinnerItems.addAll(tlList.map { it.name })

        val spinAdapter = ArrayAdapter(
            holder.itemView.context,
            android.R.layout.simple_spinner_item,
            spinnerItems
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Suppress listener while setting selection to avoid false triggers
        holder.spinnerTl.onItemSelectedListener = null
        holder.spinnerTl.adapter = spinAdapter

        // Pre-select current TL (or "No TL" if none)
        val currentIdx = if (emp.tlName.isBlank()) 0
        else tlList.indexOfFirst { it.name == emp.tlName }.let { if (it >= 0) it + 1 else 0 }
        holder.spinnerTl.setSelection(currentIdx, false)

        // Now attach the real listener
        holder.spinnerTl.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val selectedTl = if (pos == 0) null else tlList.getOrNull(pos - 1)
                // Only fire if selection actually changed
                val newName = selectedTl?.name ?: ""
                if (newName != emp.tlName) {
                    onTlAssign(emp, selectedTl)
                }
            }
        }

        // Admin target — shown separately (set by admin)
        if (emp.adminTarget > 0) {
            holder.adminTarget.text       = "Admin Target: ${emp.adminTarget}"
            holder.adminTarget.visibility = View.VISIBLE
        } else {
            holder.adminTarget.text       = "Set Target"
            holder.adminTarget.visibility = View.VISIBLE
        }

        holder.itemView.setOnClickListener { onClick(emp) }

        // Long press = Set Target
        holder.itemView.setOnLongClickListener {
            onSetTargetClick(emp)
            true
        }
        holder.rowAdminTarget.setOnClickListener { onSetTargetClick(emp) }

        if (showEditButton) {
            holder.btnEdit.visibility = View.VISIBLE
            holder.btnEdit.setOnClickListener { onEditClick(emp) }
        } else {
            holder.btnEdit.visibility = View.GONE
        }

        //  Message button
        holder.btnMessage.setOnClickListener { onMessageClick(emp) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<EmployeeSummary>() {
            override fun areItemsTheSame(a: EmployeeSummary, b: EmployeeSummary) =
                a.userId == b.userId
            override fun areContentsTheSame(a: EmployeeSummary, b: EmployeeSummary) =
                a == b
        }
    }
}
