package com.adyapan.leaddialer

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class CallHistoryFragment : Fragment() {

    private lateinit var callViewModel: CallViewModel
    private lateinit var callAdapter: CallRecordAdapter

    // Summary card views
    private var tvSummaryTotalCalls: TextView? = null
    private var tvSummaryTalkTime: TextView? = null
    private var tvSummaryGapTime: TextView? = null
    private var tvSummaryAvg: TextView? = null
    private var tvCountdown: TextView? = null

    private var countDownTimer: CountDownTimer? = null

    // Work-day constants  (11:15 AM → 8:00 PM)
    private val WORK_START_HOUR   = 11
    private val WORK_START_MINUTE = 15  // Gap counting starts here
    private val WORK_END_HOUR     = 20   // 8 PM
    private val WORK_END_MINUTE   = 0

    // Live gap-time ticker — updates every minute even when no call is made
    private var liveGapTimer: CountDownTimer? = null

    private var currentSummaryDate: Long = System.currentTimeMillis()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(
            R.layout.fragment_call_history,
            container,
            false
        )
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val callFactory = CallViewModelFactory(requireActivity().application)
        callViewModel = ViewModelProvider(requireActivity(), callFactory)[CallViewModel::class.java]

        // Summary views
        tvSummaryTotalCalls = view.findViewById(R.id.tvSummaryTotalCalls)
        tvSummaryTalkTime   = view.findViewById(R.id.tvSummaryTalkTime)
        tvSummaryGapTime    = view.findViewById(R.id.tvSummaryGapTime)
        tvSummaryAvg        = view.findViewById(R.id.tvSummaryAvg)
        tvCountdown         = view.findViewById(R.id.tvCountdown)

        val rvHistory  = view.findViewById<RecyclerView>(R.id.rvCallHistory)
        val tvEmpty    = view.findViewById<TextView>(R.id.tvEmptyHistory)
        val fabExport  = view.findViewById<FloatingActionButton>(R.id.fabExportCalls)
        
        val btnPrevDay = view.findViewById<TextView>(R.id.btnPrevDay)
        val btnNextDay = view.findViewById<TextView>(R.id.btnNextDay)

        callAdapter = CallRecordAdapter()
        rvHistory.layoutManager = LinearLayoutManager(requireContext())
        rvHistory.adapter = callAdapter

        // Date navigation listeners
        btnPrevDay?.setOnClickListener {
            currentSummaryDate -= 24 * 60 * 60 * 1000L
            updateSummaryCard(callViewModel.allRecords.value ?: emptyList())
        }
        btnNextDay?.setOnClickListener {
            currentSummaryDate += 24 * 60 * 60 * 1000L
            updateSummaryCard(callViewModel.allRecords.value ?: emptyList())
        }

        // ── Observe all records → build grouped list with gaps ───────────
        callViewModel.allRecords.observe(viewLifecycleOwner) { records ->

            val sorted = records.sortedByDescending { it.calledAt }

            // Build the display list (header + calls + gap items)
            val displayList = buildDisplayList(sorted)
            callAdapter.submitList(displayList)

            tvEmpty.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE

            // Update summary card using records
            updateSummaryCard(records)
        }

        // ── Start 8 PM countdown ─────────────────────────────────────────
        startCountdown()

        // ── Live gap-time ticker (updates every minute) ───────────────────
        startLiveGapTicker(callViewModel.allRecords.value ?: emptyList())

        // Re-start ticker whenever records change (new call saved)
        callViewModel.allRecords.observe(viewLifecycleOwner) { records ->
            startLiveGapTicker(records)
        }

        // ── Export FAB ───────────────────────────────────────────────────
        fabExport.setOnClickListener {
            val records = callViewModel.allRecords.value ?: emptyList()

            if (records.isEmpty()) {
                Toast.makeText(requireContext(), "No call records to download", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val options = arrayOf("Download Today's Calls", "Download Yesterday's Calls", "Download All Calls")

            AlertDialog.Builder(requireContext())
                .setTitle("Download Call History")
                .setItems(options) { _, which ->
                    val filtered = when (which) {
                        0    -> records.filter { getDayHeader(it.calledAt) == "Today" }
                        1    -> records.filter { getDayHeader(it.calledAt) == "Yesterday" }
                        else -> records
                    }
                    lifecycleScope.launch {
                        val statusMessage = withContext(Dispatchers.IO) {
                            CallExcelWriter.export(requireContext(), filtered)
                        }
                        Toast.makeText(
                            requireContext(),
                            statusMessage,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .show()
        }
    }

    // ── Build display list: headers + calls + gap separators ─────────────

    /**
     * Builds a flat list of [CallHistoryItem] that includes:
     * - Day headers (Today / Yesterday / date)
     * - Call items (newest first within each day)
     * - Gap rows between consecutive calls on the **same day**
     *
     * Strategy:
     * 1. Group records by day-header string.
     * 2. For each day's group, sort ascending by calledAt so we can measure gaps.
     * 3. For each pair of consecutive calls, if the gap > 60 s, inject a GapItem.
     * 4. Reverse the day-sorted list back to descending for display (newest first).
     */
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
                    val newerStart = desc[i].calledAt
                    val olderEnd = desc[i + 1].calledAt + (desc[i + 1].duration * 1000L)

                    // Get work start and end for the day of this call
                    val workStart = Calendar.getInstance().apply {
                        timeInMillis = newerStart
                        set(Calendar.HOUR_OF_DAY, 11)
                        set(Calendar.MINUTE, 15)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis

                    val workEnd = Calendar.getInstance().apply {
                        timeInMillis = newerStart
                        set(Calendar.HOUR_OF_DAY, 20)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis

                    // Only show gap pill if both calls ended/started within working hours (11:15 AM to 8:00 PM)
                    if (newerStart in workStart..workEnd && olderEnd in workStart..workEnd) {
                        val gapMs = newerStart - olderEnd
                        val gapSec = gapMs / 1000L
                        if (gapSec >= 1) {
                            result.add(CallHistoryItem.GapItem(gapSec))
                        }
                    }
                }
            }
        }
        return result
    }

    // ── Summary card ─────────────────────────────────────────────────────

    private fun updateSummaryCard(allRecords: List<CallRecord>) {
        // Filter records for the selected date within work window
        val dateRecords = getWindowRecordsForDate(allRecords, currentSummaryDate)

        val totalCalls = dateRecords.size
        val talkSec    = dateRecords.sumOf { it.duration }

        // ── Gap time = work_start → first_call  +  gaps between calls ──────
        val asc    = dateRecords.sortedBy { it.calledAt }
        var gapSec = computeTotalGap(asc, currentSummaryDate)

        val avgSec = if (totalCalls > 0) talkSec / totalCalls else 0L

        tvSummaryTotalCalls?.text = totalCalls.toString()
        tvSummaryTalkTime?.text   = formatDurationShort(talkSec)
        tvSummaryGapTime?.text    = formatDurationShort(gapSec)
        tvSummaryAvg?.text        = "⌀ Avg/call: ${CallManager.formatDuration(avgSec)}"

        view?.findViewById<TextView>(R.id.tvSummaryTitle)?.text = "${getDayHeader(currentSummaryDate)}"
    }

    /**
     * Computes total gap time:
     *  1. From work start (11:15 AM) → first call of the day
     *  2. Between end of each call → start of next call
     *  3. From last call end → current time (live, if today)
     *
     * Capped at 8:00 PM. Deducts 1 hr 30 mins (5400 seconds) for lunch break.
     */
    private fun computeTotalGap(ascRecords: List<CallRecord>, dateMs: Long): Long {
        val isToday = getDayHeader(dateMs) == "Today"

        // Work start time for the selected date
        val cal = Calendar.getInstance().apply { timeInMillis = dateMs }
        val workStartMs = Calendar.getInstance().apply {
            set(Calendar.YEAR, cal.get(Calendar.YEAR))
            set(Calendar.DAY_OF_YEAR, cal.get(Calendar.DAY_OF_YEAR))
            set(Calendar.HOUR_OF_DAY, WORK_START_HOUR)
            set(Calendar.MINUTE, WORK_START_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val workEndMs = Calendar.getInstance().apply {
            set(Calendar.YEAR, cal.get(Calendar.YEAR))
            set(Calendar.DAY_OF_YEAR, cal.get(Calendar.DAY_OF_YEAR))
            set(Calendar.HOUR_OF_DAY, WORK_END_HOUR)
            set(Calendar.MINUTE, WORK_END_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val nowMs = System.currentTimeMillis()

        // If work hasn't started yet → no gap
        if (nowMs < workStartMs && isToday) return 0L

        // Only calculate gap for calls made during working hours (11:15 AM to 8:00 PM)
        val workingHoursRecords = ascRecords.filter { it.calledAt in workStartMs..workEndMs }

        val endRef = if (isToday) minOf(nowMs, workEndMs) else workEndMs
        val deduction = getBreakDeductionSeconds(endRef, dateMs)

        if (workingHoursRecords.isEmpty()) {
            // No calls at all — gap = work_start to now (or work_end for past dates)
            val gap = (endRef - workStartMs) / 1000L
            return maxOf(0L, gap - deduction)
        }

        var totalGapSec = 0L

        // 1. Gap: work start → first call
        val firstCallStart = workingHoursRecords.first().calledAt
        if (firstCallStart > workStartMs) {
            totalGapSec += (firstCallStart - workStartMs) / 1000L
        }

        // 2. Gaps between consecutive calls
        for (i in 1 until workingHoursRecords.size) {
            val prevEnd   = workingHoursRecords[i - 1].calledAt + workingHoursRecords[i - 1].duration * 1000L
            val nextStart = workingHoursRecords[i].calledAt
            val gap = (nextStart - prevEnd) / 1000L
            if (gap > 0) totalGapSec += gap
        }

        // 3. Gap: last call end → now (live, only for today)
        if (isToday) {
            val lastCallEnd = workingHoursRecords.last().calledAt + workingHoursRecords.last().duration * 1000L
            val capTime = minOf(nowMs, workEndMs)
            if (capTime > lastCallEnd) {
                totalGapSec += (capTime - lastCallEnd) / 1000L
            }
        } else {
            val lastCallEnd = workingHoursRecords.last().calledAt + workingHoursRecords.last().duration * 1000L
            if (workEndMs > lastCallEnd) {
                totalGapSec += (workEndMs - lastCallEnd) / 1000L
            }
        }

        return maxOf(0L, totalGapSec - deduction)
    }

    private fun getBreakDeductionSeconds(referenceTimeMs: Long, dateMs: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMs }
        
        val lunchStart = Calendar.getInstance().apply {
            set(Calendar.YEAR, cal.get(Calendar.YEAR))
            set(Calendar.DAY_OF_YEAR, cal.get(Calendar.DAY_OF_YEAR))
            set(Calendar.HOUR_OF_DAY, 13)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val lunchEnd = Calendar.getInstance().apply {
            set(Calendar.YEAR, cal.get(Calendar.YEAR))
            set(Calendar.DAY_OF_YEAR, cal.get(Calendar.DAY_OF_YEAR))
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val breakStart = Calendar.getInstance().apply {
            set(Calendar.YEAR, cal.get(Calendar.YEAR))
            set(Calendar.DAY_OF_YEAR, cal.get(Calendar.DAY_OF_YEAR))
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val breakEnd = Calendar.getInstance().apply {
            set(Calendar.YEAR, cal.get(Calendar.YEAR))
            set(Calendar.DAY_OF_YEAR, cal.get(Calendar.DAY_OF_YEAR))
            set(Calendar.HOUR_OF_DAY, 17)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        var deductionMs = 0L

        // 1. Lunch break (max 60 minutes = 3600 seconds)
        if (referenceTimeMs > lunchStart) {
            val elapsedLunch = minOf(referenceTimeMs, lunchEnd) - lunchStart
            deductionMs += elapsedLunch
        }

        // 2. Short break (max 30 minutes = 1800 seconds)
        if (referenceTimeMs > breakStart) {
            val elapsedBreak = minOf(referenceTimeMs, breakEnd) - breakStart
            deductionMs += elapsedBreak
        }

        return deductionMs / 1000L
    }

    /** Live ticker: updates gap time every minute so it grows even between calls */
    private fun startLiveGapTicker(currentRecords: List<CallRecord>) {
        liveGapTimer?.cancel()

        // Only run live ticker for today
        val isToday = getDayHeader(System.currentTimeMillis()) == "Today"
        if (!isToday) return

        val now = System.currentTimeMillis()
        val eod = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, WORK_END_HOUR)
            set(Calendar.MINUTE, WORK_END_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val msLeft = eod - now
        if (msLeft <= 0) return

        liveGapTimer = object : CountDownTimer(msLeft, 60_000L) {
            override fun onTick(millisUntilFinished: Long) {
                // Recompute gap with latest records every minute
                updateSummaryCard(currentRecords)
            }
            override fun onFinish() {
                updateSummaryCard(currentRecords)
            }
        }.start()
    }

    /** Records from a specific day — all records from midnight (gap calc handles window) */
    private fun getWindowRecordsForDate(all: List<CallRecord>, dateMs: Long): List<CallRecord> {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMs }

        val dayStart = Calendar.getInstance().apply {
            set(Calendar.YEAR, cal.get(Calendar.YEAR))
            set(Calendar.DAY_OF_YEAR, cal.get(Calendar.DAY_OF_YEAR))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val dayEnd = dayStart + 24 * 60 * 60 * 1000L
        // Include all calls from that day — work window handled in computeTotalGap
        return all.filter { it.calledAt in dayStart until dayEnd }
    }

    // ── 8 PM countdown ───────────────────────────────────────────────────

    private fun startCountdown() {
        val now = System.currentTimeMillis()

        val eod = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, WORK_END_HOUR)
            set(Calendar.MINUTE, WORK_END_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val msLeft = eod - now

        if (msLeft <= 0) {
            tvCountdown?.text = "Work day ended at 8:00 PM"
            return
        }

        countDownTimer?.cancel()

        countDownTimer = object : CountDownTimer(msLeft, 60_000L) { // tick every minute

            override fun onTick(millisUntilFinished: Long) {
                val h = millisUntilFinished / 3_600_000L
                val m = (millisUntilFinished % 3_600_000L) / 60_000L
                tvCountdown?.text = when {
                    h > 0 -> "${h}h ${m}m left to 8PM"
                    else  -> "${m}m left to 8PM"
                }
            }

            override fun onFinish() {
                tvCountdown?.text = "Work day ended at 8:00 PM"
                // Refresh summary one final time
                callViewModel.allRecords.value?.let { updateSummaryCard(it) }
            }

        }.start()
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Short formatted duration e.g. "1h 23m" or "45m 10s". */
    private fun formatDurationShort(totalSec: Long): String = when {
        totalSec < 60     -> "${totalSec}s"
        totalSec < 3600   -> "${totalSec / 60}m ${totalSec % 60}s"
        else              -> "${totalSec / 3600}h ${(totalSec % 3600) / 60}m"
    }

    private fun getDayHeader(time: Long): String {
        val today     = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val callDate  = Calendar.getInstance().apply { timeInMillis = time }

        return when {
            isSameDay(callDate, today)     -> "Today"
            isSameDay(callDate, yesterday) -> "Yesterday"
            else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(time))
        }
    }

    private fun isSameDay(c1: Calendar, c2: Calendar): Boolean =
        c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        liveGapTimer?.cancel()
    }
}