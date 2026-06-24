package com.adyapan.leaddialer

import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.content.res.ColorStateList
import androidx.appcompat.app.AlertDialog

data class AdminAttendanceItem(
    val employeeName: String,
    val checkInTime: String,
    val checkOutTime: String?,
    val status: String,
    val earlyLeave: Boolean = false,
    val checkInSelfieBase64: String?,
    val checkOutSelfieBase64: String?
)

class AdminAttendanceAdapter : ListAdapter<AdminAttendanceItem, AdminAttendanceAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName   : TextView = view.findViewById(R.id.tvEmpName)
        val tvStatus : TextView = view.findViewById(R.id.tvStatus)
        val tvTime   : TextView = view.findViewById(R.id.tvTime)
        val btnSelfie: ImageView = view.findViewById(R.id.btnViewSelfie)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_attendance, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        holder.tvName.text = item.employeeName

        val outStr = item.checkOutTime ?: "Not Checked Out"
        holder.tvTime.text = "Check-In: ${item.checkInTime} | Check-Out: $outStr"

        // Status badge: Late / Early Leave / Present
        when {
            item.status.equals("Absent", ignoreCase = true) -> {
                holder.tvStatus.text = "Absent"
                holder.tvStatus.setTextColor(Color.parseColor("#EF4444"))
                holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
            }
            item.status.equals("Half Day", ignoreCase = true) -> {
                holder.tvStatus.text = "Half Day"
                holder.tvStatus.setTextColor(Color.parseColor("#0284C7"))
                holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E0F2FE"))
            }
            item.status.equals("Late", ignoreCase = true) && item.earlyLeave -> {
                holder.tvStatus.text = "Late + Early Leave"
                holder.tvStatus.setTextColor(Color.parseColor("#DC2626"))
                holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
            }
            item.status.equals("Late", ignoreCase = true) -> {
                holder.tvStatus.text = "Late"
                holder.tvStatus.setTextColor(Color.parseColor("#DC2626"))
                holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
            }
            item.earlyLeave -> {
                holder.tvStatus.text = "Early Leave"
                holder.tvStatus.setTextColor(Color.parseColor("#D97706"))
                holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
            }
            else -> {
                holder.tvStatus.text = "Present"
                holder.tvStatus.setTextColor(Color.parseColor("#10B981"))
                holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D1FAE5"))
            }
        }

        if (item.checkInSelfieBase64.isNullOrBlank() && item.checkOutSelfieBase64.isNullOrBlank()) {
            holder.btnSelfie.alpha = 0.3f
            holder.btnSelfie.setOnClickListener {
                // No selfie
            }
        } else {
            holder.btnSelfie.alpha = 1.0f
            holder.btnSelfie.setOnClickListener {
                showSelfieDialog(it.context, item)
            }
        }
    }

    private fun showSelfieDialog(context: android.content.Context, item: AdminAttendanceItem) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_view_selfie, null)
        val imgIn = dialogView.findViewById<ImageView>(R.id.imgCheckIn)
        val imgOut = dialogView.findViewById<ImageView>(R.id.imgCheckOut)
        val tvIn = dialogView.findViewById<TextView>(R.id.tvCheckInTitle)
        val tvOut = dialogView.findViewById<TextView>(R.id.tvCheckOutTitle)

        // Decode check-in
        if (!item.checkInSelfieBase64.isNullOrBlank()) {
            try {
                val decodedString = Base64.decode(item.checkInSelfieBase64, Base64.DEFAULT)
                val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                imgIn.setImageBitmap(decodedByte)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            imgIn.visibility = View.GONE
            tvIn.visibility = View.GONE
        }

        // Decode check-out
        if (!item.checkOutSelfieBase64.isNullOrBlank()) {
            try {
                val decodedString = Base64.decode(item.checkOutSelfieBase64, Base64.DEFAULT)
                val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                imgOut.setImageBitmap(decodedByte)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            imgOut.visibility = View.GONE
            tvOut.visibility = View.GONE
        }

        AlertDialog.Builder(context)
            .setTitle("${item.employeeName}'s Selfies")
            .setView(dialogView)
            .setPositiveButton("Close") { d, _ -> d.dismiss() }
            .show()
    }

    object DiffCallback : DiffUtil.ItemCallback<AdminAttendanceItem>() {
        override fun areItemsTheSame(oldItem: AdminAttendanceItem, newItem: AdminAttendanceItem): Boolean =
            oldItem.employeeName == newItem.employeeName && oldItem.checkInTime == newItem.checkInTime

        override fun areContentsTheSame(oldItem: AdminAttendanceItem, newItem: AdminAttendanceItem): Boolean =
            oldItem == newItem
    }
}
