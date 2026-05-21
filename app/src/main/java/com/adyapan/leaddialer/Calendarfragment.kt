package com.adyapan.leaddialer

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CalendarView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CalendarFragment : Fragment() {

    private lateinit var attendanceViewModel : AttendanceViewModel
    private lateinit var callViewModel       : CallViewModel
    private lateinit var leadViewModel       : LeadViewModel

    private val dateFormat  = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val displayFmt  = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_calendarfragment, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val attFactory = AttendanceViewModelFactory(requireActivity().application)
        attendanceViewModel = ViewModelProvider(requireActivity(), attFactory)[AttendanceViewModel::class.java]

        val callFactory = CallViewModelFactory(requireActivity().application)
        callViewModel   = ViewModelProvider(requireActivity(), callFactory)[CallViewModel::class.java]

        val leadFactory = LeadViewModelFactory(requireActivity().application)
        leadViewModel   = ViewModelProvider(requireActivity(), leadFactory)[LeadViewModel::class.java]

        val calendarView = view.findViewById<CalendarView>(R.id.calendarView)
        val detailPanel  = view.findViewById<LinearLayout>(R.id.detailPanel)
        val tvDate       = view.findViewById<TextView>(R.id.tvSelectedDate)
        val tvPunchIn    = view.findViewById<TextView>(R.id.tvPunchIn)
        val tvLate       = view.findViewById<TextView>(R.id.tvLateStatus)
        val tvLateReason = view.findViewById<TextView>(R.id.tvLateReason)
        val tvWeeklyOff  = view.findViewById<TextView>(R.id.tvWeeklyOff)
        val tvCallCount  = view.findViewById<TextView>(R.id.tvCallCount)
        val tvCallList   = view.findViewById<TextView>(R.id.tvCallList)

        val tvTodayCalls = view.findViewById<TextView>(R.id.tvTodayCalls)
        val tvTodayIn    = view.findViewById<TextView>(R.id.tvTodayPunchIn)
        val tvTotalDays  = view.findViewById<TextView>(R.id.tvTotalDays)

        var userDesignation = ""
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance()
                .collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    userDesignation = doc.getString("designation") ?: ""
                }
        }

        fun isWeeklyOff(cal: Calendar): Boolean {
            val d = userDesignation.lowercase()
            return if (d.contains("community developer"))
                cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY
            else
                cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        }

        attendanceViewModel.allAttendance.observe(viewLifecycleOwner) { records ->
            val today    = dateFormat.format(Calendar.getInstance().time)
            val todayRec = records.find { it.date == today }
            tvTodayIn.text =
                if (todayRec != null) "Today's Punch-In: ${todayRec.punchInTime}"
                else "No Punch-In Today"
            tvTotalDays.text = "Total Days: ${records.size}"
        }

        callViewModel.allRecords.observe(viewLifecycleOwner) { records ->
            val today      = dateFormat.format(Calendar.getInstance().time)
            val todayCount = records.count { r ->
                val cal = Calendar.getInstance().apply { timeInMillis = r.calledAt }
                dateFormat.format(cal.time) == today
            }
            tvTodayCalls.text = "Today's Calls: $todayCount"
        }

        fun showDetails(selected: Calendar) {
            val selectedMs   = selected.timeInMillis
            val displayDate  = displayFmt.format(selected.time)
            val selectedDate = dateFormat.format(selected.time)

            detailPanel.visibility = View.VISIBLE
            tvDate.text = "📅 $displayDate"

            tvWeeklyOff.visibility  = View.GONE
            tvPunchIn.visibility    = View.VISIBLE
            tvLate.visibility       = View.VISIBLE
            tvLateReason.visibility = View.GONE

            if (isWeeklyOff(selected)) {
                val offLabel = if (userDesignation.lowercase().contains("community developer"))
                    "🗓 Weekly Off (Monday)" else "🗓 Weekly Off (Sunday)"
                tvWeeklyOff.text       = offLabel
                tvWeeklyOff.visibility = View.VISIBLE
                tvPunchIn.text  = ""
                tvLate.text     = ""
            } else {
                val attRecord = attendanceViewModel.allAttendance.value
                    ?.find { it.date == selectedDate }

                if (attRecord != null) {
                    tvPunchIn.text = "⏰ Punch-in: ${attRecord.punchInTime}"
                    tvPunchIn.setTextColor(Color.parseColor("#2E7D32"))
                    if (attRecord.isLate) {
                        tvLate.text = "🔴 Arrived Late"
                        tvLate.setTextColor(Color.parseColor("#C62828"))
                        tvLateReason.text       = "Reason: ${attRecord.lateReason}"
                        tvLateReason.visibility = View.VISIBLE
                    } else {
                        tvLate.text = "🟢 Arrived On Time"
                        tvLate.setTextColor(Color.parseColor("#2E7D32"))
                    }
                } else {
                    tvPunchIn.text = "❌ No Record Found for This Day"
                    tvPunchIn.setTextColor(Color.parseColor("#757575"))
                    tvLate.text = ""
                }
            }

            val selCal = Calendar.getInstance().apply { timeInMillis = selectedMs }
            val dayCalls = (callViewModel.allRecords.value ?: emptyList()).filter { r ->
                val rCal = Calendar.getInstance().apply { timeInMillis = r.calledAt }
                rCal.get(Calendar.YEAR)        == selCal.get(Calendar.YEAR) &&
                rCal.get(Calendar.DAY_OF_YEAR) == selCal.get(Calendar.DAY_OF_YEAR)
            }
            tvCallCount.text = "📞 Total calls: ${dayCalls.size}"
            if (dayCalls.isNotEmpty()) {
                tvCallList.text = dayCalls.sortedByDescending { it.calledAt }
                    .joinToString("\n") { record ->
                        val time = SimpleDateFormat("hh:mm a", Locale.getDefault())
                            .format(java.util.Date(record.calledAt))
                        val icon = when (record.status) {
                            "Connected"      -> "✅"
                            "Not Connected"  -> "❌"
                            "Busy"           -> "📵"
                            "Interested"     -> "⭐"
                            "Not Interested" -> "👎"
                            else             -> "🕐"
                        }
                        "$icon ${record.name}  •  ${CallManager.formatDuration(record.duration)}  •  $time"
                    }
                tvCallList.visibility = View.VISIBLE
            } else {
                tvCallList.text       = "No Calls Made on This Day"
                tvCallList.visibility = View.VISIBLE
            }
        }

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selected = Calendar.getInstance().apply {
                set(year, month, dayOfMonth)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
            }
            showDetails(selected)
        }

        calendarView.post { calendarView.date = Calendar.getInstance().timeInMillis }
    }
}