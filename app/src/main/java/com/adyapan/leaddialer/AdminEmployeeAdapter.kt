package com.adyapan.leaddialer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class AdminEmployeeAdapter(
    private val showEditButton    : Boolean = true,
    private val onClick           : (EmployeeSummary) -> Unit,
    private val onEditClick       : (EmployeeSummary) -> Unit,
    private val onMessageClick    : (EmployeeSummary) -> Unit = {},
    private val onSetTargetClick  : (EmployeeSummary) -> Unit = {},
    private val onAttendanceClick : (EmployeeSummary) -> Unit = {},
    private val onTlAssign        : (emp: EmployeeSummary, tl: TeamLeaderManager.TeamLeader?) -> Unit = { _, _ -> }
) : ListAdapter<EmployeeSummary, AdminEmployeeAdapter.VH>(DIFF) {

    private var tlList: List<TeamLeaderManager.TeamLeader> = emptyList()

    fun setTlList(list: List<TeamLeaderManager.TeamLeader>) {
        tlList = list
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val avatar        : TextView = view.findViewById(R.id.tvEmployeeAvatar)
        val name          : TextView = view.findViewById(R.id.tvEmployeeName)
        val email         : TextView = view.findViewById(R.id.tvEmployeeEmail)
        val tlBadge       : TextView = view.findViewById(R.id.tvTlBadge)
        val successRate   : TextView = view.findViewById(R.id.tvSuccessRate)
        val leads         : TextView = view.findViewById(R.id.tvLeadCount)
        val connected     : TextView = view.findViewById(R.id.tvConnectedCount)
        val interested    : TextView = view.findViewById(R.id.tvInterestedCount)
        val sales         : TextView = view.findViewById(R.id.tvSalesCount)
        val expected      : TextView = view.findViewById(R.id.tvExpectedSales)
        val adminTarget   : TextView = view.findViewById(R.id.tvAdminTarget)
        val rowAdminTarget: View     = view.findViewById(R.id.rowAdminTarget)
        val btnEdit       : View     = view.findViewById(R.id.btnEditProfile)
        val btnMessage    : View     = view.findViewById(R.id.btnSendMessage)
        val btnViewLeads  : View     = view.findViewById(R.id.btnViewLeads)
        val btnAttendance : View     = view.findViewById(R.id.btnAttendance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_employee, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val emp = getItem(position)

        val initial = if (emp.employeeName.length >= 2) {
            emp.employeeName.substring(0, 2).uppercase()
        } else {
            emp.employeeName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        }
        holder.avatar.text     = initial
        holder.name.text       = emp.employeeName.replaceFirstChar { it.uppercase() }
        holder.email.text      = emp.userId

        holder.leads.text      = emp.totalLeads.toString()
        holder.connected.text  = emp.connected.toString()
        holder.interested.text = emp.interested.toString()
        holder.sales.text      = emp.salesDone.toString()
        holder.expected.text   = emp.expectedSales.toString()

        // Success rate percentage
        holder.successRate.text = "${emp.successRate}%"
        if (emp.successRate > 10) {
            holder.successRate.setTextColor(0xFF34C759.toInt())
        } else {
            holder.successRate.setTextColor(0xFF6C6C70.toInt())
        }

        // TL Badge
        if (emp.tlName.isNotBlank()) {
            holder.tlBadge.text = "TL: ${emp.tlName}"
            holder.tlBadge.visibility = View.VISIBLE
        } else {
            holder.tlBadge.visibility = View.GONE
        }

        // Admin Daily Target button text
        if (emp.adminTarget > 0) {
            holder.adminTarget.text = "Admin Target: ${emp.adminTarget}"
        } else {
            holder.adminTarget.text = "Set Target"
        }

        // Click actions
        holder.itemView.setOnClickListener { onClick(emp) }
        holder.btnViewLeads.setOnClickListener { onClick(emp) }
        holder.btnMessage.setOnClickListener { onMessageClick(emp) }
        holder.btnAttendance.setOnClickListener { onAttendanceClick(emp) }
        holder.rowAdminTarget.setOnClickListener { onSetTargetClick(emp) }

        if (showEditButton) {
            holder.btnEdit.visibility = View.VISIBLE
            holder.btnEdit.setOnClickListener { onEditClick(emp) }
        } else {
            holder.btnEdit.visibility = View.GONE
        }
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
