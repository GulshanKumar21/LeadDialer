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

data class AttendanceHistoryItem(
    val dateKey: String,
    val date: String,
    val checkInTime: String?,
    val checkOutTime: String?,
    val status: String?,
    val earlyLeave: Boolean = false,
    val checkInSelfieBase64: String?,
    val checkOutSelfieBase64: String?,
    val isWeeklyOff: Boolean = false,
    val isFuture: Boolean = false,
    val holidayName: String? = null
)

class AttendanceHistoryAdapter : ListAdapter<AttendanceHistoryItem, AttendanceHistoryAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate   : TextView = view.findViewById(R.id.tvHistoryDate)
        val tvStatus : TextView = view.findViewById(R.id.tvHistoryStatus)
        val tvTime   : TextView = view.findViewById(R.id.tvHistoryTime)
        val btnSelfie: ImageView = view.findViewById(R.id.btnHistorySelfie)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_attendance_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        holder.tvDate.text = item.date

        when {
            item.holidayName != null -> {
                holder.tvStatus.text = "🎉 ${item.holidayName}"
                holder.tvStatus.setTextColor(Color.parseColor("#EA580C"))
                holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFEDD5"))
                holder.tvTime.text = "Holiday"
                holder.btnSelfie.visibility = View.GONE
            }
            item.isWeeklyOff -> {
                holder.tvStatus.text = "Weekly Off"
                holder.tvStatus.setTextColor(Color.parseColor("#9CA3AF"))
                holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F3F4F6"))
                holder.tvTime.text = "—"
                holder.btnSelfie.visibility = View.GONE
            }
            item.isFuture -> {
                holder.tvStatus.text = "—"
                holder.tvStatus.setTextColor(Color.parseColor("#D1D5DB"))
                holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                holder.tvTime.text = "Upcoming"
                holder.btnSelfie.visibility = View.GONE
            }
            item.status == null -> {
                holder.tvStatus.text = "Absent"
                holder.tvStatus.setTextColor(Color.parseColor("#EF4444"))
                holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
                holder.tvTime.text = "Not marked"
                holder.btnSelfie.visibility = View.GONE
            }
            else -> {
                holder.btnSelfie.visibility = View.VISIBLE
                val outStr = item.checkOutTime ?: "Not Checked Out"
                holder.tvTime.text = "In: ${item.checkInTime ?: "—"}  |  Out: $outStr"

                when {
                    item.status.equals("LOP", ignoreCase = true) -> {
                        holder.tvStatus.text = "⚠️ LOP"
                        holder.tvStatus.setTextColor(Color.parseColor("#EF4444"))
                        holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
                    }
                    item.status.equals("Absent", ignoreCase = true) -> {
                        holder.tvStatus.text = "❌ Absent"
                        holder.tvStatus.setTextColor(Color.parseColor("#EF4444"))
                        holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
                    }
                    item.status.equals("Half Day", ignoreCase = true) -> {
                        holder.tvStatus.text = "🔵 Half Day"
                        holder.tvStatus.setTextColor(Color.parseColor("#0284C7"))
                        holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E0F2FE"))
                    }
                    item.status.equals("Late", ignoreCase = true) && item.earlyLeave -> {
                        holder.tvStatus.text = "🔴 Late + Early Leave"
                        holder.tvStatus.setTextColor(Color.parseColor("#DC2626"))
                        holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
                    }
                    item.status.equals("Late", ignoreCase = true) -> {
                        holder.tvStatus.text = "🔴 Late"
                        holder.tvStatus.setTextColor(Color.parseColor("#DC2626"))
                        holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
                    }
                    item.earlyLeave -> {
                        holder.tvStatus.text = "⚠️ Early Leave"
                        holder.tvStatus.setTextColor(Color.parseColor("#D97706"))
                        holder.tvStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
                    }
                    else -> {
                        holder.tvStatus.text = "🟢 Present"
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
        }
    }

    private fun showSelfieDialog(context: android.content.Context, item: AttendanceHistoryItem) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_view_selfie, null)
        val imgIn = dialogView.findViewById<ImageView>(R.id.imgCheckIn)
        val imgOut = dialogView.findViewById<ImageView>(R.id.imgCheckOut)
        val tvIn = dialogView.findViewById<TextView>(R.id.tvCheckInTitle)
        val tvOut = dialogView.findViewById<TextView>(R.id.tvCheckOutTitle)

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
            .setTitle("Selfies on ${item.date}")
            .setView(dialogView)
            .setPositiveButton("Close") { d, _ -> d.dismiss() }
            .show()
    }

    object DiffCallback : DiffUtil.ItemCallback<AttendanceHistoryItem>() {
        override fun areItemsTheSame(oldItem: AttendanceHistoryItem, newItem: AttendanceHistoryItem): Boolean =
            oldItem.date == newItem.date

        override fun areContentsTheSame(oldItem: AttendanceHistoryItem, newItem: AttendanceHistoryItem): Boolean =
            oldItem == newItem
    }
}
