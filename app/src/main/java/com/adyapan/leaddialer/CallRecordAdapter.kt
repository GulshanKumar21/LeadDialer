package com.adyapan.leaddialer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class CallRecordAdapter :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items =
        mutableListOf<CallHistoryItem>()

    companion object {

        private const val TYPE_HEADER = 0

        private const val TYPE_CALL = 1

        private const val TYPE_GAP = 2
    }

    // SUBMIT LIST

    fun submitList(
        list: List<CallHistoryItem>
    ) {

        items.clear()

        items.addAll(list)

        notifyDataSetChanged()
    }

    // VIEW TYPES

    override fun getItemViewType(
        position: Int
    ): Int {

        return when (items[position]) {

            is CallHistoryItem.Header ->
                TYPE_HEADER

            is CallHistoryItem.CallItem ->
                TYPE_CALL

            is CallHistoryItem.GapItem ->
                TYPE_GAP
        }
    }

    // CREATE VIEW

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {

        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater
                    .from(parent.context)
                    .inflate(
                        R.layout.item_call_header,
                        parent,
                        false
                    )
                HeaderViewHolder(view)
            }

            TYPE_GAP -> {
                val view = LayoutInflater
                    .from(parent.context)
                    .inflate(
                        R.layout.item_gap_separator,
                        parent,
                        false
                    )
                GapViewHolder(view)
            }

            else -> {
                val view = LayoutInflater
                    .from(parent.context)
                    .inflate(
                        R.layout.item_call_record,
                        parent,
                        false
                    )
                CallViewHolder(view)
            }
        }
    }

    // BIND VIEW

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {

        when (val item = items[position]) {

            is CallHistoryItem.Header -> {

                (holder as HeaderViewHolder)
                    .bind(item)
            }

            is CallHistoryItem.CallItem -> {

                (holder as CallViewHolder)
                    .bind(item.record)
            }

            is CallHistoryItem.GapItem -> {

                (holder as GapViewHolder)
                    .bind(item.gapSeconds)
            }
        }
    }

    override fun getItemCount(): Int {

        return items.size
    }

    // ── HEADER VIEW HOLDER ──────────────────────────────────────────────

    inner class HeaderViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view) {

        private val tvHeader =
            view.findViewById<TextView>(
                R.id.tvHeader
            )

        fun bind(
            item: CallHistoryItem.Header
        ) {

            tvHeader.text = item.title
        }
    }

    // ── GAP VIEW HOLDER ─────────────────────────────────────────────────

    inner class GapViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view) {

        private val tvGap =
            view.findViewById<TextView>(
                R.id.tvGapDuration
            )

        fun bind(gapSeconds: Long) {
            tvGap.text = "Gap: ${formatGap(gapSeconds)}"
        }

        private fun formatGap(seconds: Long): String = when {
            seconds < 60  -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else           -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }

    // ── CALL VIEW HOLDER ─────────────────────────────────────────────────

    inner class CallViewHolder(
        view: View
    ) : RecyclerView.ViewHolder(view) {

        private val tvAvatar =
            view.findViewById<TextView>(
                R.id.tvAvatar
            )

        private val tvName =
            view.findViewById<TextView>(
                R.id.tvName
            )

        private val tvPhone =
            view.findViewById<TextView>(
                R.id.tvPhone
            )

        private val tvStatus =
            view.findViewById<TextView>(
                R.id.tvStatus
            )

        private val tvDuration =
            view.findViewById<TextView>(
                R.id.tvDuration
            )

        private val tvTime =
            view.findViewById<TextView>(
                R.id.tvTime
            )

        private val timeFormat =
            SimpleDateFormat(
                "hh:mm a",
                Locale.getDefault()
            )

        fun bind(record: CallRecord) {

            tvAvatar.text =
                record.name
                    .trim()
                    .firstOrNull()
                    ?.uppercaseChar()
                    ?.toString()
                    ?: "?"

            tvName.text = record.name

            tvPhone.text = record.phone

            tvDuration.text =
                "⏱ ${
                    CallManager.formatDuration(
                        record.duration
                    )
                }"

            tvTime.text =
                if (record.calledAt > 0)
                    timeFormat.format(
                        Date(record.calledAt)
                    )
                else
                    "—"

            tvStatus.text = record.status

            tvStatus.setTextColor(
                when {
                    record.status == "Wrong Number"                   -> 0xFF8B0000.toInt()
                    record.status == "Interested"                     -> 0xFF1565C0.toInt()
                    record.status == "Busy"                           -> 0xFFE65100.toInt()
                    record.status == "Not Connected"                  -> 0xFF757575.toInt()
                    record.status.startsWith("Not Interested")        -> 0xFFC62828.toInt()
                    else                                              -> 0xFF9E9E9E.toInt()
                }
            )
        }
    }
}