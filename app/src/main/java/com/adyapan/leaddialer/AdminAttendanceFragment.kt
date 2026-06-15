package com.adyapan.leaddialer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider

class AdminAttendanceFragment : Fragment() {

    private lateinit var viewModel: AdminViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(requireActivity())[AdminViewModel::class.java]

        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    AdminSimpleListScreen(
                        title = "Attendance Inspection",
                        viewModel = viewModel,
                        onItemClick = { emp ->
                            startActivity(
                                Intent(requireContext(), AdminEmployeeAttendanceActivity::class.java)
                                    .putExtra("uid",  emp.userId)
                                    .putExtra("name", emp.employeeName)
                            )
                        }
                    )
                }
            }
        }
    }
}
