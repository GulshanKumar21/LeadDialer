package com.adyapan.leaddialer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider

class ReportsFragment : Fragment() {

    private lateinit var viewModel: LeadViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_reports, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val factory = LeadViewModelFactory(requireActivity().application)
        viewModel   = ViewModelProvider(requireActivity(), factory)[LeadViewModel::class.java]

        val tvTotalCalled   = view.findViewById<TextView>(R.id.rptTotalCalled)
        val tvConnected     = view.findViewById<TextView>(R.id.rptConnected)
        val tvNotConnected  = view.findViewById<TextView>(R.id.rptNotConnected)
        val tvBusy          = view.findViewById<TextView>(R.id.rptBusy)
        val tvInterested    = view.findViewById<TextView>(R.id.rptInterested)
        val tvNotInterested = view.findViewById<TextView>(R.id.rptNotInterested)
        val tvPending       = view.findViewById<TextView>(R.id.rptPending)

        val cardTotalCalled   = view.findViewById<CardView>(R.id.cardTotalCalled)
        val cardConnected     = view.findViewById<CardView>(R.id.cardConnected)
        val cardNotConnected  = view.findViewById<CardView>(R.id.cardNotConnected)
        val cardBusy          = view.findViewById<CardView>(R.id.cardBusy)
        val cardInterested    = view.findViewById<CardView>(R.id.cardInterested)
        val cardNotInterested = view.findViewById<CardView>(R.id.cardNotInterested)
        val cardPending       = view.findViewById<CardView>(R.id.cardPending)

        viewModel.allLeads.observe(viewLifecycleOwner) { leads ->
            tvTotalCalled.text   = leads.count { it.calledAt > 0                          }.toString()
            tvConnected.text     = leads.count { it.status == "Wrong Number"              }.toString()
            tvNotConnected.text  = leads.count { it.status == "Not Connected"             }.toString()
            tvBusy.text          = leads.count { it.status == "Busy"                      }.toString()
            tvInterested.text    = leads.count { it.status == "Interested"                }.toString()
            tvNotInterested.text = leads.count { it.status.startsWith("Not Interested")   }.toString()
            tvPending.text       = leads.count { it.status == "Pending"                   }.toString()
        }

        cardTotalCalled.setOnClickListener   { openFiltered("All Called")     }
        cardConnected.setOnClickListener     { openFiltered("Wrong Number")   }
        cardNotConnected.setOnClickListener  { openFiltered("Not Connected")  }
        cardBusy.setOnClickListener          { openFiltered("Busy")           }
        cardInterested.setOnClickListener    { openFiltered("Interested")     }
        cardNotInterested.setOnClickListener { openFiltered("Not Interested") }
        cardPending.setOnClickListener       { openFiltered("Pending")        }
    }

    private fun openFiltered(status: String) {
        val fragment = FilteredLeadsFragment.newInstance(status)
        val title = when (status) {
            "All Called"     -> "📊 All Called"
            "Wrong Number"   -> "🔢 Wrong Number"
            "Not Connected"  -> "❌ Not Connected"
            "Busy"           -> "📵 Busy"
            "Interested"     -> "⭐ Interested"
            "Not Interested" -> "👎 Not Interested"
            "Pending"        -> "🕐 Pending"
            else             -> status
        }
        // loadFragmentWithBack → back press returns to Reports, not Dashboard
        (requireActivity() as? MainActivity)?.loadFragmentWithBack(fragment, title)
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as? MainActivity)?.supportActionBar?.title = "Reports"
    }
}