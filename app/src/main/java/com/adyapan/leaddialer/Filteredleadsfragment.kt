package com.adyapan.leaddialer

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FilteredLeadsFragment : Fragment() {

    private lateinit var viewModel  : LeadViewModel
    private lateinit var adapter    : LeadAdapter
    private var currentLead         : Lead? = null
    private var callStarted         = false
    private var filterStatus        = ""

    companion object {
        private const val ARG_STATUS = "status"

        fun newInstance(status: String): FilteredLeadsFragment {
            val fragment = FilteredLeadsFragment()
            val args     = Bundle()
            args.putString(ARG_STATUS, status)
            fragment.arguments = args
            return fragment
        }
    }

    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) currentLead?.let { makeCall(it) }
        else Toast.makeText(requireContext(), "Call permission denied", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_filteredleadsfragment, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        filterStatus = arguments?.getString(ARG_STATUS) ?: ""

        val factory = LeadViewModelFactory(requireActivity().application)
        viewModel   = ViewModelProvider(requireActivity(), factory)[LeadViewModel::class.java]

        val tvTitle: TextView? = view.findViewById(R.id.tvFilteredTitle)
        val tvEmpty   = view.findViewById<TextView>(R.id.tvFilteredEmpty)
        val rvLeads   = view.findViewById<RecyclerView>(R.id.rvFilteredLeads)

        tvTitle?.text = when (filterStatus) {
            "All Called"     -> "All Called Leads"
            "Wrong Number"   -> "Wrong Number"
            "Not Connected"  -> "Not Connected"
            "Busy"           -> "Busy"
            "Interested"     -> "Interested"
            "Not Interested" -> "Not Interested"
            "Pending"        -> "Pending"
            "HotLead"        -> "Hot Leads"
            "SalesDone"      -> "Sales Done"
            "Connected"      -> "Connected"
            "Total Leads"    -> "Total Leads"
            else             -> filterStatus
        }

        adapter = LeadAdapter(
            onCallClick = { lead ->
                currentLead = lead
                initiateCall(lead)
            }
        )
        rvLeads.layoutManager = LinearLayoutManager(requireContext())
        rvLeads.adapter       = adapter

        viewModel.allLeads.observe(viewLifecycleOwner) { leads ->
            val filtered = when (filterStatus) {
                "All Called"  -> leads.filter { it.calledAt > 0 }.sortedByDescending { it.calledAt }
                "HotLead"     -> leads.filter { it.status == "Interested" }.sortedByDescending { it.calledAt }
                "SalesDone"   -> leads.filter { it.salesDone }.sortedByDescending { it.calledAt }
                "Total Leads" -> leads.toList()
                "Pending"     -> leads.filter { it.status == "Pending" || it.status.isBlank() || it.status.equals("New", ignoreCase = true) }
                "Connected"   -> leads.filter { it.status == "Interested" || it.status == "Connected" }.sortedByDescending { it.calledAt }
                else          -> leads.filter { it.status == filterStatus }
            }
            adapter.submitList(filtered)
            tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun initiateCall(lead: Lead) {
        currentLead = lead
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            makeCall(lead)
        } else {
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun makeCall(lead: Lead) {
        callStarted = true
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${lead.phone}")
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        if (callStarted) {
            callStarted = false
            Handler(Looper.getMainLooper()).postDelayed({
                currentLead?.let { showStatusPopup(it) }
            }, 1500)
        }
        val title = when (filterStatus) {
            "All Called"     -> "All Called Leads"
            "Wrong Number"   -> "Wrong Number"
            "Not Connected"  -> "Not Connected"
            "Busy"           -> "Busy"
            "Interested"     -> "Interested"
            "Not Interested" -> "Not Interested"
            "Pending"        -> "Pending"
            "HotLead"        -> "Hot Leads"
            "SalesDone"      -> "Sales Done"
            "Connected"      -> "Connected"
            "Total Leads"    -> "Total Leads"
            else             -> filterStatus
        }
        (requireActivity() as? MainActivity)?.supportActionBar?.title = title
    }

    private fun showStatusPopup(lead: Lead) {
        if (!isAdded) return

        val statuses = arrayOf(
            "Wrong Number",
            "Not Connected",
            "Busy",
            "Interested",
            "Not Interested"
        )
        val statusValues = arrayOf(
            "Wrong Number", "Not Connected", "Busy", "Interested", "Not Interested"
        )

        AlertDialog.Builder(requireContext())
            .setTitle("Call Status — ${lead.name}")
            .setItems(statuses) { _, which ->
                viewModel.updateStatus(lead, statusValues[which])
                Toast.makeText(
                    requireContext(),
                    "Status updated: ${statusValues[which]}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Skip", null)
            .show()
    }
}