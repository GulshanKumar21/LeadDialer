package com.adyapan.leaddialer

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class LeaveRequestAdapter(
    private val isAdminMode: Boolean = false,
    private val onApprove: ((LeaveRequest) -> Unit)? = null,
    private val onReject:  ((LeaveRequest) -> Unit)? = null
) : ListAdapter<LeaveRequest, LeaveRequestAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<LeaveRequest>() {
            override fun areItemsTheSame(a: LeaveRequest, b: LeaveRequest) = a.id == b.id
            override fun areContentsTheSame(a: LeaveRequest, b: LeaveRequest) = a == b
        }
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val statusBar        : View     = itemView.findViewById(R.id.viewStatusBar)
        val tvEmployeeName   : TextView = itemView.findViewById(R.id.tvEmployeeName)
        val tvType           : TextView = itemView.findViewById(R.id.tvLeaveType)
        val tvDates          : TextView = itemView.findViewById(R.id.tvLeaveDates)
        val tvReason         : TextView = itemView.findViewById(R.id.tvLeaveReason)
        val tvStatus         : TextView = itemView.findViewById(R.id.tvLeaveStatus)
        val layoutActions    : View     = itemView.findViewById(R.id.layoutAdminActions)
        val btnApprove       : Button   = itemView.findViewById(R.id.btnApproveLeave)
        val btnReject        : Button   = itemView.findViewById(R.id.btnRejectLeave)
        val layoutAttachment : View     = itemView.findViewById(R.id.layoutAttachment)
        val tvAttachmentLink : TextView = itemView.findViewById(R.id.tvAttachmentLink)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leave_request, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val req = getItem(position)

        // Employee name (admin-only)
        if (isAdminMode) {
            holder.tvEmployeeName.visibility = View.VISIBLE
            holder.tvEmployeeName.text = "${req.employeeName}"
        } else {
            holder.tvEmployeeName.visibility = View.GONE
        }

        holder.tvType.text   = req.leaveType
        holder.tvDates.text  = "${req.fromDate}  →  ${req.toDate}"
        holder.tvReason.text = req.reason

        if (!req.documentUrl.isNullOrBlank()) {
            holder.layoutAttachment.visibility = View.VISIBLE
            val dispName = if (!req.documentName.isNullOrBlank()) req.documentName else "View Attachment"
            holder.tvAttachmentLink.text = "$dispName"
            holder.layoutAttachment.setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(req.documentUrl))
                    holder.itemView.context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(holder.itemView.context, "No app to open this link", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            holder.layoutAttachment.visibility = View.GONE
        }

        // Status badge colour
        when (req.status) {
            "Approved" -> {
                holder.tvStatus.text = "Approved"
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_approved)
                holder.statusBar.setBackgroundColor(Color.parseColor("#27AE60"))
            }
            "Rejected" -> {
                holder.tvStatus.text = "Rejected"
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_rejected)
                holder.statusBar.setBackgroundColor(Color.parseColor("#E74C3C"))
            }
            else -> {
                holder.tvStatus.text = "Pending"
                holder.tvStatus.setBackgroundResource(R.drawable.bg_badge_pending)
                holder.statusBar.setBackgroundColor(Color.parseColor("#FFA500"))
            }
        }

        // Admin action buttons — only show when Pending and in admin mode
        if (isAdminMode && req.status == "Pending") {
            holder.layoutActions.visibility = View.VISIBLE
            holder.btnApprove.setOnClickListener { onApprove?.invoke(req) }
            holder.btnReject.setOnClickListener  { onReject?.invoke(req)  }
        } else {
            holder.layoutActions.visibility = View.GONE
        }
    }
}
