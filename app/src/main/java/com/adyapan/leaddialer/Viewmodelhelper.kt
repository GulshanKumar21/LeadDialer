package com.adyapan.leaddialer


import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider


fun Fragment.getLeadViewModel(): LeadViewModel {
    val factory = LeadViewModelFactory(requireActivity().application)
    return ViewModelProvider(requireActivity(), factory)[LeadViewModel::class.java]
}

fun FragmentActivity.getLeadViewModel(): LeadViewModel {
    val factory = LeadViewModelFactory(application)
    return ViewModelProvider(this, factory)[LeadViewModel::class.java]
}