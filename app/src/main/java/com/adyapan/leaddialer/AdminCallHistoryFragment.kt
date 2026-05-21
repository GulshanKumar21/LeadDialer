package com.adyapan.leaddialer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AdminCallHistoryFragment : Fragment() {

    private lateinit var callAdapter: CallRecordAdapter
    private lateinit var progress: ProgressBar

    // Summary card views
    private var tvSummaryTotalCalls: TextView? = null
    private var tvSummaryTalkTime: TextView? = null
    private var tvSummaryGapTime: TextView? = null
    private var tvSummaryAvg: TextView? = null

    // Work-day constants  (11:30 AM → 8:00 PM)
    private val WORK_START_HOUR   = 11
    private val WORK_START_MINUTE = 30
    private val WORK_END_HOUR     = 20   // 8 PM
    private val WORK_END_MINUTE   = 0

    companion object {
        private const val ARG_USER_ID = "emp_uid"
        private const val ARG_NAME = "emp_name"

        fun newInstance(userId: String, name: String): AdminCallHistoryFragment {
            val fragment = AdminCallHistoryFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_USER_ID, userId)
                putString(ARG_NAME, name)
            }
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_call_history, container, false)

    private var cachedRecords: List<CallRecord> = emptyList()
    private var currentSummaryDate: Long = System.currentTimeMillis()

    // Real-time RTDB listener — stored for cleanup in onDestroyView
    private var rtdbListener: ValueEventListener? = null
    private var rtdbRef: com.google.firebase.database.DatabaseReference? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val name = arguments?.getString(ARG_NAME) ?: "Employee"
        val userId = arguments?.getString(ARG_USER_ID) ?: return

        // Hide UI elements not needed for admin
        view.findViewById<TextView>(R.id.tvCountdown)?.visibility = View.GONE
        view.findViewById<FloatingActionButton>(R.id.fabExportCalls)?.visibility = View.GONE

        // Add title to summary card initially
        view.findViewById<TextView>(R.id.tvSummaryTitle)?.text = "📊 Today"

        tvSummaryTotalCalls = view.findViewById(R.id.tvSummaryTotalCalls)
        tvSummaryTalkTime   = view.findViewById(R.id.tvSummaryTalkTime)
        tvSummaryGapTime    = view.findViewById(R.id.tvSummaryGapTime)
        tvSummaryAvg        = view.findViewById(R.id.tvSummaryAvg)
        
        val rvHistory = view.findViewById<RecyclerView>(R.id.rvCallHistory)
        val tvEmpty   = view.findViewById<TextView>(R.id.tvEmptyHistory)
        val btnPrevDay = view.findViewById<TextView>(R.id.btnPrevDay)
        val btnNextDay = view.findViewById<TextView>(R.id.btnNextDay)
        
        callAdapter = CallRecordAdapter()
        rvHistory.layoutManager = LinearLayoutManager(requireContext())
        rvHistory.adapter = callAdapter

        tvEmpty.text = "Loading call logs..."
        tvEmpty.visibility = View.VISIBLE

        btnPrevDay?.setOnClickListener {
            currentSummaryDate -= 24 * 60 * 60 * 1000L
            updateSummaryCard(cachedRecords)
        }
        btnNextDay?.setOnClickListener {
            currentSummaryDate += 24 * 60 * 60 * 1000L
            updateSummaryCard(cachedRecords)
        }

        lifecycleScope.launch {
            val ref = FirebaseDatabase.getInstance()
                .getReference("callRecords").child(userId)
            rtdbRef = ref

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val records = snapshot.children.mapNotNull { callNode ->
                        val map = callNode.value as? Map<*, *> ?: return@mapNotNull null
                        runCatching {
                            CallRecord(
                                id       = 0,
                                name     = map["name"]?.toString()     ?: "",
                                phone    = map["phone"]?.toString()    ?: "",
                                status   = map["status"]?.toString()   ?: "Not Connected",
                                duration = (map["duration"] as? Number)?.toLong() ?: 0L,
                                calledAt = (map["calledAt"] as? Number)?.toLong() ?: 0L
                            )
                        }.getOrNull()
                    }

                    cachedRecords = records
                    val sorted = records.sortedByDescending { it.calledAt }
                    val displayList = buildDisplayList(sorted)
                    callAdapter.submitList(displayList)

                    tvEmpty?.text = "No call history found"
                    tvEmpty?.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE

                    updateSummaryCard(records)
                }

                override fun onCancelled(error: DatabaseError) {
                    tvEmpty?.text = "Error loading logs: ${error.message}"
                }
            }

            rtdbListener = listener
            ref.addValueEventListener(listener)  // Real-time — auto-updates jab bhi call aaye
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Memory leak prevent — listener remove karo
        rtdbListener?.let { rtdbRef?.removeEventListener(it) }
        rtdbListener = null
        rtdbRef = null
    }

    private fun buildDisplayList(sortedDesc: List<CallRecord>): List<CallHistoryItem> {
        val result = mutableListOf<CallHistoryItem>()
        val grouped = linkedMapOf<String, MutableList<CallRecord>>()
        for (record in sortedDesc) {
            val header = getDayHeader(record.calledAt)
            grouped.getOrPut(header) { mutableListOf() }.add(record)
        }

        for ((header, dayRecords) in grouped) {
            result.add(CallHistoryItem.Header(header))
            val asc  = dayRecords.sortedBy { it.calledAt }
            val desc = asc.reversed()

            for (i in desc.indices) {
                result.add(CallHistoryItem.CallItem(desc[i]))
                if (i + 1 < desc.size) {
                    // Gap = time between end of newer call and start of older call
                    // Use max(duration*1000, 30s) so calls with 0 duration still show gap
                    val effectiveDuration = maxOf(desc[i].duration * 1000L, 0L)
                    val newerEnd   = desc[i].calledAt + effectiveDuration
                    val olderStart = desc[i + 1].calledAt
                    val gapMs = newerEnd - olderStart
                    // Show gap even if small — min 1s gap considered
                    val gapSec = gapMs / 1000L
                    if (gapSec >= 1) {
                        result.add(CallHistoryItem.GapItem(gapSec))
                    }
                }
            }
        }
        return result
    }

    private fun updateSummaryCard(allRecords: List<CallRecord>) {
        val dateRecords = getWindowRecordsForDate(allRecords, currentSummaryDate)
        val totalCalls  = dateRecords.size
        val talkSec     = dateRecords.sumOf { it.duration }

        val asc         = dateRecords.sortedBy { it.calledAt }
        var gapSec      = 0L
        for (i in 1 until asc.size) {
            val prevEnd  = asc[i - 1].calledAt + asc[i - 1].duration * 1000L
            val nextStart = asc[i].calledAt
            val gap = (nextStart - prevEnd) / 1000L
            if (gap > 0) gapSec += gap
        }

        val avgSec = if (totalCalls > 0) talkSec / totalCalls else 0L

        tvSummaryTotalCalls?.text = totalCalls.toString()
        tvSummaryTalkTime?.text   = formatDurationShort(talkSec)
        tvSummaryGapTime?.text    = formatDurationShort(gapSec)
        tvSummaryAvg?.text        = "⌀ Avg/call: ${CallManager.formatDuration(avgSec)}"

        // Update Title Date
        view?.findViewById<TextView>(R.id.tvSummaryTitle)?.text = "📊 ${getDayHeader(currentSummaryDate)}"
    }

    private fun getWindowRecordsForDate(all: List<CallRecord>, dateMs: Long): List<CallRecord> {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMs }

        val windowStart = Calendar.getInstance().apply {
            set(Calendar.YEAR, cal.get(Calendar.YEAR))
            set(Calendar.DAY_OF_YEAR, cal.get(Calendar.DAY_OF_YEAR))
            set(Calendar.HOUR_OF_DAY, WORK_START_HOUR)
            set(Calendar.MINUTE, WORK_START_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val windowEnd = Calendar.getInstance().apply {
            set(Calendar.YEAR, cal.get(Calendar.YEAR))
            set(Calendar.DAY_OF_YEAR, cal.get(Calendar.DAY_OF_YEAR))
            set(Calendar.HOUR_OF_DAY, WORK_END_HOUR)
            set(Calendar.MINUTE, WORK_END_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val dayStart = Calendar.getInstance().apply {
            set(Calendar.YEAR, cal.get(Calendar.YEAR))
            set(Calendar.DAY_OF_YEAR, cal.get(Calendar.DAY_OF_YEAR))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val dayEnd = dayStart + 24 * 60 * 60 * 1000L

        return all.filter { it.calledAt in dayStart until dayEnd }
            .filter { it.calledAt >= windowStart }
    }

    private fun getDayHeader(timestamp: Long): String {
        val cal = Calendar.getInstance()
        val todayStart = cal.apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yesterdayStart = todayStart - (24 * 60 * 60 * 1000L)

        return when {
            timestamp >= todayStart -> "📞 Today"
            timestamp in yesterdayStart until todayStart -> "🕒 Yesterday"
            else -> {
                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                "📅 ${sdf.format(Date(timestamp))}"
            }
        }
    }

    private fun formatDurationShort(totalSeconds: Long): String {
        if (totalSeconds <= 0) return "0m"
        val hours = totalSeconds / 3600
        val mins = (totalSeconds % 3600) / 60
        return buildString {
            if (hours > 0) append("${hours}h ")
            append("${mins}m")
        }
    }
}
