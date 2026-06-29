package com.adyapan.leaddialer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.composed
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.*

class AdminLeadsFragment : Fragment() {

    private lateinit var viewModel: AdminViewModel

    companion object {
        private const val ARG_NAME       = "emp_name"
        private const val ARG_USER_ID    = "emp_uid"
        private const val ARG_TOTAL      = "emp_total"
        private const val ARG_CONNECTED  = "emp_connected"
        private const val ARG_INTERESTED = "emp_interested"
        private const val ARG_PENDING    = "emp_pending"
        private const val ARG_SALES_DONE = "emp_sales"
        private const val ARG_EXPECTED   = "emp_expected"

        fun newInstance(emp: EmployeeSummary): AdminLeadsFragment {
            val fragment = AdminLeadsFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_NAME,       emp.employeeName)
                putString(ARG_USER_ID,    emp.userId)
                putInt(ARG_TOTAL,         emp.totalLeads)
                putInt(ARG_CONNECTED,     emp.connected)
                putInt(ARG_INTERESTED,    emp.interested)
                putInt(ARG_PENDING,       emp.pending)
                putInt(ARG_SALES_DONE,    emp.salesDone)
                putInt(ARG_EXPECTED,      emp.expectedSales)
            }
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(requireActivity())[AdminViewModel::class.java]

        val name      = arguments?.getString(ARG_NAME)      ?: "Employee"
        val userId    = arguments?.getString(ARG_USER_ID)   ?: ""
        val total     = arguments?.getInt(ARG_TOTAL)        ?: 0
        val connected = arguments?.getInt(ARG_CONNECTED)    ?: 0
        val interested = arguments?.getInt(ARG_INTERESTED)  ?: 0
        val pending   = arguments?.getInt(ARG_PENDING)      ?: 0
        val salesDone = arguments?.getInt(ARG_SALES_DONE)   ?: 0
        val expected  = arguments?.getInt(ARG_EXPECTED)     ?: 0

        if (userId.isNotBlank()) {
            viewModel.loadEmployeeLeads(userId)
        }

        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    AdminLeadsScreen(
                        name = name,
                        userId = userId,
                        total = total,
                        connected = connected,
                        interested = interested,
                        pending = pending,
                        salesDone = salesDone,
                        expected = expected,
                        viewModel = viewModel,
                        onViewLogsClick = {
                            val fragment = AdminCallHistoryFragment.newInstance(userId, name)
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.adminFragmentContainer, fragment)
                                .addToBackStack(null)
                                .commit()
                        },
                        onSalesToggle = { lead ->
                            lifecycleScope.launch {
                                val newVal = !lead.salesDone
                                val fid = lead.firestoreId ?: return@launch
                                // Optimistic UI update
                                viewModel.updateLeadSalesDoneLocally(fid, newVal)
                                val ok = FirestoreSource.updateSalesDone(fid, newVal, userId)
                                if (ok) {
                                    val msg = if (newVal) "Sales Done marked!" else "↩ Sales Done removed"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                } else {
                                    // Revert on failure
                                    viewModel.updateLeadSalesDoneLocally(fid, lead.salesDone)
                                    Toast.makeText(context, "Failed to update", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAdminUpload)?.hide()
    }
}

private val NunitoFamily = FontFamily(
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold)
)

@Composable
fun AdminLeadsScreen(
    name: String,
    userId: String,
    total: Int,
    connected: Int,
    interested: Int,
    pending: Int,
    salesDone: Int,
    expected: Int,
    viewModel: AdminViewModel,
    onViewLogsClick: () -> Unit,
    onSalesToggle: (Lead) -> Unit
) {
    val leads by viewModel.employeeLeads.observeAsState(emptyList())
    val isLoading by viewModel.leadsLoading.observeAsState(false)

    val listState = rememberLazyListState()

    // Detect if we scrolled near the bottom of the list
    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItemsNumber = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
            // Trigger when user is within 5 items of the end
            lastVisibleItemIndex > 0 && lastVisibleItemIndex >= totalItemsNumber - 5
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !isLoading && leads.size >= viewModel.currentEmployeeLeadsLimit) {
            viewModel.loadMoreEmployeeLeads(userId)
        }
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .glassy3dCardEffect(isClickable = false)
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Avatar
                        val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color(0xFFFF6A00), Color(0xFFFF9E59))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial,
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = NunitoFamily
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = NunitoFamily,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "$total leads assigned",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                fontFamily = NunitoFamily
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0x2064748B))
                    Spacer(modifier = Modifier.height(10.dp))

                    // 5-Column Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        StatBadge(title = "Connected", value = connected.toString(), bgColor = Color(0xFFF0FDFA), textColor = Color(0xFF0D9488), modifier = Modifier.weight(1f))
                        StatBadge(title = "Interested", value = interested.toString(), bgColor = Color(0xFFEFF6FF), textColor = Color(0xFF2563EB), modifier = Modifier.weight(1f))
                        StatBadge(title = "Pending", value = pending.toString(), bgColor = Color(0xFFFFFBEB), textColor = Color(0xFFD97706), modifier = Modifier.weight(1f))
                        StatBadge(title = "Sales", value = salesDone.toString(), bgColor = Color(0xFFF0FDF4), textColor = Color(0xFF16A34A), modifier = Modifier.weight(1f))
                        StatBadge(title = "Target", value = expected.toString(), bgColor = Color(0xFFFAF5FF), textColor = Color(0xFF9333EA), modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Button: View Call Logs
                    Button(
                        onClick = onViewLogsClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6A00)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View Call Logs & Timings", fontFamily = NunitoFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            // Leads List Title
            Text(
                text = "Assigned Leads Details",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = NunitoFamily,
                color = Color(0xFF334155),
                modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
            )

            // Leads list
            if (leads.isEmpty() && !isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No leads records available", color = Color.Gray, fontSize = 15.sp, fontFamily = NunitoFamily)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(leads) { lead ->
                        LeadItemCard(lead = lead, onSalesToggle = onSalesToggle)
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
fun LeadItemCard(
    lead: Lead,
    onSalesToggle: (Lead) -> Unit
) {
    // Resolve status color
    val statusColor = when (lead.status) {
        "Connected" -> Color(0xFF0D9488)
        "Interested" -> Color(0xFF2563EB)
        "Wrong Number" -> Color(0xFFDC2626)
        "Busy" -> Color(0xFF9333EA)
        "Not Interested" -> Color(0xFF64748B)
        "Pending" -> Color(0xFFD97706)
        else -> Color(0xFF475569)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassy3dCardEffect(isClickable = false)
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lead.name,
                    fontSize = 17.sp,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = lead.phone,
                    fontSize = 13.sp,
                    color = Color.Gray,
                    fontFamily = NunitoFamily
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = lead.status,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = NunitoFamily
                    )
                }
            }

            // Sales Done Switch/Checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onSalesToggle(lead) }
            ) {
                Text(
                    text = "Sales Done",
                    fontSize = 13.sp,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF475569),
                    modifier = Modifier.padding(end = 8.dp)
                )
                Checkbox(
                    checked = lead.salesDone,
                    onCheckedChange = { onSalesToggle(lead) },
                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF16A34A))
                )
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
            shape = RoundedCornerShape(14.dp),
            clip = false
        )
        .background(
            color = Color(0xE6FFFFFF), // Translucent Milky White Glass (90% Opacity)
            shape = RoundedCornerShape(14.dp)
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFFFFF),        // top highlight
                    Color(0x30FFFFFF)         // bottom fade
                )
            ),
            shape = RoundedCornerShape(14.dp)
        )
        .then(
            if (isClickable && onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null, // Disable ripple for clean 3D physical movement
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )
}

@Composable
fun StatBadge(
    title: String,
    value: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                fontSize = 16.sp,
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Text(
                text = title,
                fontSize = 10.sp,
                fontFamily = NunitoFamily,
                color = Color(0xFF888888),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

