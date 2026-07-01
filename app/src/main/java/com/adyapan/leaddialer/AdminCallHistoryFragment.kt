package com.adyapan.leaddialer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.composed
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.fragment.app.Fragment
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AdminCallHistoryFragment : Fragment() {

    private val WORK_START_HOUR   = 11
    private val WORK_START_MINUTE = 15
    private val WORK_END_HOUR     = 20
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

    private var rtdbListener: ValueEventListener? = null
    private var rtdbRef: com.google.firebase.database.DatabaseReference? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val name = arguments?.getString(ARG_NAME) ?: "Employee"
        val userId = arguments?.getString(ARG_USER_ID) ?: ""

        val cachedRecordsState = mutableStateListOf<CallRecord>()
        val isLoadingState = mutableStateOf(true)

        if (userId.isNotBlank()) {
            val ref = FirebaseDatabase.getInstance().getReference("callRecords").child(userId)
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
                                calledAt = (map["calledAt"] as? Number)?.toLong() ?: 0L,
                                note     = map["note"]?.toString()     ?: ""  // Custom employee note
                            )
                        }.getOrNull()
                    }

                    cachedRecordsState.clear()
                    cachedRecordsState.addAll(records)
                    isLoadingState.value = false
                }

                override fun onCancelled(error: DatabaseError) {
                    isLoadingState.value = false
                }
            }

            rtdbListener = listener
            ref.addValueEventListener(listener)
        }

        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    AdminCallHistoryScreen(
                        name = name,
                        cachedRecords = cachedRecordsState,
                        isLoading = isLoadingState.value,
                        computeGapSec = { asc, date -> computeTotalGap(asc, date) },
                        getWindowRecords = { all, date -> getWindowRecordsForDate(all, date) },
                        getDayHeaderStr = { ts -> getDayHeader(ts) },
                        formatDurationShortStr = { sec -> formatDurationShort(sec) },
                        buildDisplayListItems = { sorted -> buildDisplayList(sorted) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAdminUpload)?.hide()
    }

    override fun onDestroyView() {
        super.onDestroyView()
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

    private fun computeTotalGap(ascRecords: List<CallRecord>, dateMs: Long): Long {
        val isToday = getDayHeader(dateMs) == "Today"

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

        if (nowMs < workStartMs && isToday) return 0L

        // Filter calls to only calculate gap for those made during working hours (11:15 AM to 8:00 PM)
        val workingHoursRecords = ascRecords.filter { it.calledAt in workStartMs..workEndMs }

        val endRef = if (isToday) minOf(nowMs, workEndMs) else workEndMs
        val deduction = getBreakDeductionSeconds(endRef, dateMs)

        if (workingHoursRecords.isEmpty()) {
            val gap = (endRef - workStartMs) / 1000L
            return maxOf(0L, gap - deduction)
        }

        var totalGapSec = 0L

        val firstCallStart = workingHoursRecords.first().calledAt
        if (firstCallStart > workStartMs) {
            totalGapSec += (firstCallStart - workStartMs) / 1000L
        }

        for (i in 1 until workingHoursRecords.size) {
            val prevEnd   = workingHoursRecords[i - 1].calledAt + workingHoursRecords[i - 1].duration * 1000L
            val nextStart = workingHoursRecords[i].calledAt
            val gap = (nextStart - prevEnd) / 1000L
            if (gap > 0) totalGapSec += gap
        }

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

        // Fetch all calls made during the full day (no windowStart filter)
        return all.filter { it.calledAt in dayStart until dayEnd }
    }

    private fun getDayHeader(timestamp: Long): String {
        val cal = Calendar.getInstance()
        val todayStart = cal.apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yesterdayStart = todayStart - (24 * 60 * 60 * 1000L)

        return when {
            timestamp >= todayStart -> "Today"
            timestamp in yesterdayStart until todayStart -> "Yesterday"
            else -> {
                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }

    private fun formatDurationShort(totalSeconds: Long): String {
        if (totalSeconds <= 0) return "0m"
        val hours = totalSeconds / 3600
        val mins  = (totalSeconds % 3600) / 60
        val secs  = totalSeconds % 60
        return buildString {
            if (hours > 0) append("${hours}h ")
            if (mins > 0 || hours > 0) append("${mins}m ")
            append("${secs}s")
        }.trim()
    }
}

private val NunitoFamily = FontFamily(
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold)
)

@Composable
fun AdminCallHistoryScreen(
    name: String,
    cachedRecords: List<CallRecord>,
    isLoading: Boolean,
    computeGapSec: (List<CallRecord>, Long) -> Long,
    getWindowRecords: (List<CallRecord>, Long) -> List<CallRecord>,
    getDayHeaderStr: (Long) -> String,
    formatDurationShortStr: (Long) -> String,
    buildDisplayListItems: (List<CallRecord>) -> List<CallHistoryItem>
) {
    var currentSummaryDate by remember { mutableStateOf(System.currentTimeMillis()) }

    // Derived states for performance & reactivity
    val displayList = remember(cachedRecords, currentSummaryDate) {
        val dateRecords = getWindowRecords(cachedRecords, currentSummaryDate)
        val sorted = dateRecords.sortedByDescending { it.calledAt }
        buildDisplayListItems(sorted)
    }

    val (totalCalls, talkTime, gapTime, avgDuration, dateHeader) = remember(cachedRecords, currentSummaryDate) {
        val dateRecords = getWindowRecords(cachedRecords, currentSummaryDate)
        val total = dateRecords.size
        val talkSec = dateRecords.sumOf { it.duration }
        val asc = dateRecords.sortedBy { it.calledAt }
        val gap = computeGapSec(asc, currentSummaryDate)
        val avg = if (total > 0) talkSec / total else 0L

        val avgStr = "⌀ Avg/call: " + CallManager.formatDuration(avg)
        val talkStr = formatDurationShortStr(talkSec)
        val gapStr = formatDurationShortStr(gap)
        val titleStr = getDayHeaderStr(currentSummaryDate)

        listOf(total.toString(), talkStr, gapStr, avgStr, titleStr)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF9F6))
    ) {
        // Decorative background gradient blobs
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x20FF6A00), Color(0x00FF6A00)),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.15f),
                    radius = size.width * 0.7f
                ),
                radius = size.width * 0.7f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.15f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x158B5CF6), Color(0x008B5CF6)),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.6f),
                    radius = size.width * 0.6f
                ),
                radius = size.width * 0.6f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.6f)
            )
        }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Header Title
            Text(
                text = "$name's History",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = NunitoFamily,
                color = Color(0xFF1E293B),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Date Selector & Summary Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .glassy3dCardEffect(isClickable = false)
                    .padding(16.dp)
            ) {
                Column {
                    // Date Selector navigation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "◀ Prev",
                            color = Color(0xFFFF6A00),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = NunitoFamily,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { currentSummaryDate -= 24 * 60 * 60 * 1000L }
                                .padding(6.dp)
                        )
                        Text(
                            text = "$dateHeader",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = NunitoFamily,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Next ▶",
                            color = Color(0xFFFF6A00),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = NunitoFamily,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { currentSummaryDate += 24 * 60 * 60 * 1000L }
                                .padding(6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0x2064748B))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Stats display row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryStatBox(title = "Calls", value = totalCalls, modifier = Modifier.weight(1f))
                        SummaryStatBox(title = "Talk Time", value = talkTime, modifier = Modifier.weight(1f))
                        SummaryStatBox(title = "Gap Time", value = gapTime, modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = avgDuration,
                        fontSize = 13.sp,
                        color = Color.Gray,
                        fontFamily = NunitoFamily,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Call Logs Title
            Text(
                text = "Call Records List",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = NunitoFamily,
                color = Color(0xFF334155),
                modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
            )

            // Scrollable Logs List
            if (displayList.isEmpty() && !isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No call logs found", color = Color.Gray, fontSize = 15.sp, fontFamily = NunitoFamily)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayList) { item ->
                        when (item) {
                            is CallHistoryItem.Header -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = item.title,
                                        fontSize = 14.sp,
                                        fontFamily = NunitoFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray
                                    )
                                }
                            }
                            is CallHistoryItem.CallItem -> {
                                HistoryCallLogCard(record = item.record)
                            }
                            is CallHistoryItem.GapItem -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Gap: ${CallManager.formatDuration(item.gapSeconds)}",
                                        fontSize = 12.sp,
                                        fontFamily = NunitoFamily,
                                        color = Color(0xFFD97706),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFFFEF3C7))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0x33000000)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFFF6A00))
            }
        }
    }
}

@Composable
fun SummaryStatBox(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF1F5F9))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = NunitoFamily,
                color = Color(0xFF0F172A)
            )
            Text(
                text = title,
                fontSize = 11.sp,
                color = Color.Gray,
                fontFamily = NunitoFamily
            )
        }
    }
}

@Composable
fun HistoryCallLogCard(record: CallRecord) {
    val durationText = CallManager.formatDuration(record.duration)
    val callTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(record.calledAt))

    val statusColor = when (record.status) {
        "Connected"      -> Color(0xFF0D9488)
        "Interested"     -> Color(0xFF2563EB)
        "Wrong Number"   -> Color(0xFFDC2626)
        "Busy"           -> Color(0xFF9333EA)
        "Not Interested" -> Color(0xFF64748B)
        "Pending"        -> Color(0xFFD97706)
        "Custom"         -> Color(0xFFD97706)
        else             -> Color(0xFF475569)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassy3dCardEffect(isClickable = false)
            .padding(12.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.name,
                        fontSize = 16.sp,
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${record.phone}  •  $durationText",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontFamily = NunitoFamily
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = callTime,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontFamily = NunitoFamily
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusColor.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = record.status,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = NunitoFamily
                        )
                    }
                }
            }

            // ── Custom note section ─────────────────────────────────────────
            // Show the employee's custom message below the call card
            if (record.status == "Custom" && record.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFEF9C3))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "",
                            fontSize = 13.sp
                        )
                        Text(
                            text = record.note,
                            fontSize = 13.sp,
                            fontFamily = NunitoFamily,
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// Glassmorphic 3D Card Tactile Click Effect
private fun Modifier.glassy3dCardEffect(
    isClickable: Boolean = true,
    onClick: (() -> Unit)? = null
): Modifier = this.composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed = if (isClickable) interactionSource.collectIsPressedAsState().value else false

    val translationY by animateDpAsState(
        targetValue = if (isPressed) 4.dp else 0.dp,
        animationSpec = tween(100)
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 8.dp,
        animationSpec = tween(100)
    )

    this
        .offset(y = translationY)
        .shadow(
            elevation = shadowElevation,
            shape = RoundedCornerShape(16.dp),
            clip = false
        )
        .background(
            color = Color(0xE6FFFFFF), // Translucent Milky White Glass (90% Opacity)
            shape = RoundedCornerShape(16.dp)
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFFFFF),        // top highlight
                    Color(0x30FFFFFF)         // bottom fade
                )
            ),
            shape = RoundedCornerShape(16.dp)
        )
        .then(
            if (isClickable && onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null, // Disable default native ripple for visual clean 3D physical movement
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )
}
