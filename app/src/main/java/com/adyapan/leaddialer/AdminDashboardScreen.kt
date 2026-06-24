package com.adyapan.leaddialer

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: AdminViewModel,
    onUploadLeadsClick: () -> Unit,
    onPublishThoughtClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val employees by viewModel.employees.observeAsState(emptyList())
    val todayAttendance by viewModel.todayAttendance.observeAsState(emptyMap())
    val leaveRequests by FirestoreSource.leaveRequestsFlow().collectAsState(initial = emptyList())
    val tlList by viewModel.tlList.observeAsState(emptyList())

    val totalEmployees = employees.size
    val presentCount = employees.count {
        val status = todayAttendance[it.userId]
        status == "Present" || status == "Late"
    }
    val attendanceRate = if (totalEmployees > 0) (presentCount * 100 / totalEmployees) else 0
    val totalConnected = employees.sumOf { it.connected }
    val pendingLeavesCount = remember(leaveRequests) { leaveRequests.count { it.status == "Pending" } }

    val totalSalesToday = employees.sumOf { it.salesDone }
    val totalTargetToday = employees.sumOf { it.adminTarget }
    val totalExpectedToday = employees.sumOf { it.expectedSales }

    val pendingLeaves = remember(leaveRequests) {
        leaveRequests.filter { it.status == "Pending" }.sortedByDescending { it.appliedAt }.take(3)
    }

    val topPerformers = remember(employees) {
        employees.filter { it.salesDone > 0 || it.connected > 0 }
            .sortedWith(compareByDescending<EmployeeSummary> { it.salesDone }.thenByDescending { it.connected })
            .take(3)
    }

    var showManageTls by remember { mutableStateOf(false) }
    var showBroadcastDialog by remember { mutableStateOf(false) }

    val bgGradient = remember {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFF8FAFC), Color(0xFFEDF2F7))
        )
    }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> "Good Morning, Admin \uD83D\uDC51"
        hour < 17 -> "Good Afternoon, Admin \uD83C\uDF24"
        hour < 21 -> "Good Evening, Admin \uD83C\uDF07"
        else -> "Good Night, Admin \uD83C\uDF0C"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = greeting,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = "Here is your workforce activity overview today.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.loadAllEmployeeData() },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFFFF6A00))
                    }
                }
            }

            // Target progress card
            item {
                TargetProgressCard(totalSalesToday, totalTargetToday, totalExpectedToday)
            }

            // Stats grid
            item {
                StatsGrid(
                    totalEmployees = totalEmployees,
                    attendanceRate = attendanceRate,
                    totalConnected = totalConnected,
                    pendingLeaves = pendingLeavesCount
                )
            }

            // Quick Actions
            item {
                QuickActionsCard(
                    onPublishThoughtClick = onPublishThoughtClick,
                    onUploadLeadsClick = onUploadLeadsClick,
                    onBroadcastClick = { showBroadcastDialog = true },
                    onManageTlsClick = { showManageTls = true }
                )
            }

            // Leaderboard
            if (topPerformers.isNotEmpty()) {
                item {
                    TopPerformersCard(topPerformers)
                }
            }

            // Pending leaves
            item {
                PendingLeavesCard(
                    pendingLeaves = pendingLeaves,
                    onApprove = { leave ->
                        scope.launch {
                            val ok = FirestoreSource.updateLeaveStatus(leave.id, "Approved")
                            if (ok) Toast.makeText(context, "Leave approved", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onReject = { leave ->
                        scope.launch {
                            val ok = FirestoreSource.updateLeaveStatus(leave.id, "Rejected")
                            if (ok) Toast.makeText(context, "Leave rejected", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    if (showManageTls) {
        ManageTlsDialog(
            viewModel = viewModel,
            tlList = tlList,
            employees = employees,
            onDismiss = { showManageTls = false }
        )
    }

    if (showBroadcastDialog) {
        BroadcastDialog(
            onDismiss = { showBroadcastDialog = false }
        )
    }
}

@Composable
fun TargetProgressCard(sales: Int, target: Int, expected: Int) {
    val targetProgress = if (target > 0) (sales.toFloat() / target).coerceIn(0f, 1f) else 0f
    val expectedProgress = if (expected > 0) (sales.toFloat() / expected).coerceIn(0f, 1f) else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "\uD83C\uDFAF", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Today's Sales Achievements",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Sales Done: $sales",
                    color = Color(0xFFFF6A00),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(text = "Expected vs Target", color = Color(0xFF94A3B8), fontSize = 11.sp)
            }

            // Target
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Admin Target Progress ($sales/$target)", color = Color.White, fontSize = 11.sp)
                    Text(text = "${(targetProgress * 100).toInt()}%", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = targetProgress,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF10B981),
                    trackColor = Color(0x33FFFFFF)
                )
            }

            // Expected
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Employee Expected Goal ($sales/$expected)", color = Color.White, fontSize = 11.sp)
                    Text(text = "${(expectedProgress * 100).toInt()}%", color = Color(0xFF3B82F6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = expectedProgress,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF3B82F6),
                    trackColor = Color(0x33FFFFFF)
                )
            }
        }
    }
}

@Composable
fun StatsGrid(totalEmployees: Int, attendanceRate: Int, totalConnected: Int, pendingLeaves: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                StatCard(title = "Workforce", value = totalEmployees.toString(), subtitle = "Active employees \uD83D\uDC65", color = Color(0xFF3B82F6), icon = Icons.Default.People)
            }
            Box(modifier = Modifier.weight(1f)) {
                StatCard(title = "Attendance", value = "$attendanceRate%", subtitle = "Present today \uD83D\uDCCA", color = Color(0xFF10B981), icon = Icons.Default.CheckCircle)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                StatCard(title = "Total Connected", value = totalConnected.toString(), subtitle = "Connected leads \uD83D\uDCDE", color = Color(0xFFFF6A00), icon = Icons.Default.PhoneInTalk)
            }
            Box(modifier = Modifier.weight(1f)) {
                StatCard(title = "Pending Leaves", value = pendingLeaves.toString(), subtitle = pendingLeaves.let { if (it > 0) "Awaiting response ⌛" else "All approved! ✅" }, color = Color(0xFF8B5CF6), icon = Icons.Default.WatchLater)
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, subtitle: String, color: Color, icon: ImageVector) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.shadow(2.dp, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxWidth().height(80.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                }
            }
            Column {
                Text(
                    text = value,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.W800,
                    color = Color(0xFF1E293B)
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
fun QuickActionsCard(
    onPublishThoughtClick: () -> Unit,
    onUploadLeadsClick: () -> Unit,
    onBroadcastClick: () -> Unit,
    onManageTlsClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.shadow(2.dp, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Quick Action Tools 🛠️",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ActionTile(icon = Icons.Default.Lightbulb, label = "Publish\nThought", color = Color(0xFFFFB020), onClick = onPublishThoughtClick)
                }
                Box(modifier = Modifier.weight(1f)) {
                    ActionTile(icon = Icons.Default.FileUpload, label = "Upload\nLeads", color = Color(0xFF3B82F6), onClick = onUploadLeadsClick)
                }
                Box(modifier = Modifier.weight(1f)) {
                    ActionTile(icon = Icons.Default.Campaign, label = "Send\nBroadcast", color = Color(0xFFEF4444), onClick = onBroadcastClick)
                }
                Box(modifier = Modifier.weight(1f)) {
                    ActionTile(icon = Icons.Default.SupervisorAccount, label = "Manage\nTLs", color = Color(0xFF8B5CF6), onClick = onManageTlsClick)
                }
            }
        }
    }
}

@Composable
fun ActionTile(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = color.copy(alpha = 0.9f),
                lineHeight = 12.sp
            )
        }
    }
}

@Composable
fun TopPerformersCard(performers: List<EmployeeSummary>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.shadow(2.dp, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "\uD83C\uDFC6", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Top Performers Today",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
            }

            performers.forEachIndexed { index, emp ->
                val medal = when (index) {
                    0 -> "\uD83E\uDD47"
                    1 -> "\uD83E\uDD48"
                    else -> "\uD83E\uDD49"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = medal, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = emp.employeeName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                            Text(text = "TL: ${emp.tlName.ifBlank { "None" }}", fontSize = 10.sp, color = Color(0xFF64748B))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = emp.salesDone.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            Text(text = "Sales", fontSize = 9.sp, color = Color(0xFF94A3B8))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = emp.connected.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6A00))
                            Text(text = "Calls", fontSize = 9.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }
                if (index < performers.size - 1) {
                    Divider(color = Color(0xFFF1F5F9))
                }
            }
        }
    }
}

@Composable
fun PendingLeavesCard(
    pendingLeaves: List<LeaveRequest>,
    onApprove: (LeaveRequest) -> Unit,
    onReject: (LeaveRequest) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.shadow(2.dp, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "⏰", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pending Leaves",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFEF3C7))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${pendingLeaves.size} pending",
                        color = Color(0xFFD97706),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (pendingLeaves.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "No leave requests pending.", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            } else {
                pendingLeaves.forEachIndexed { index, leave ->
                    val initial = leave.employeeName.take(2).uppercase()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFE6D5)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initial,
                                        color = Color(0xFFFF6A00),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(text = leave.employeeName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                                    Text(text = "${leave.leaveType} · ${leave.fromDate} to ${leave.toDate}", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                            }
                        }
                        Text(
                            text = "Reason: \"${leave.reason}\"",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(start = 46.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 46.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onReject(leave) },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp),
                                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                            ) {
                                Text(text = "Reject", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { onApprove(leave) },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.White)
                            ) {
                                Text(text = "Approve", fontSize = 11.sp)
                            }
                        }
                    }
                    if (index < pendingLeaves.size - 1) {
                        Divider(color = Color(0xFFF1F5F9))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTlsDialog(
    viewModel: AdminViewModel,
    tlList: List<TeamLeaderManager.TeamLeader>,
    employees: List<EmployeeSummary>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedEmployeeId by remember { mutableStateOf<String?>(null) }
    var sheetUrl by remember { mutableStateOf("") }
    var editingTlId by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    var expandedEmployeeDropdown by remember { mutableStateOf(false) }

    var showAssignMembers by remember { mutableStateOf<TeamLeaderManager.TeamLeader?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).shadow(8.dp, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "\uD83D\uDC65 Manage Team Leaders", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Divider(color = Color(0xFFE2E8F0))

                // Form
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (editingTlId != null) "Update Team Leader" else "Add New Team Leader",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6A00)
                    )

                    // Employee Dropdown selection
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { if (editingTlId == null) expandedEmployeeDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1E293B)),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                        ) {
                            val selectedName = employees.find { it.userId == selectedEmployeeId }?.employeeName ?: "Select Employee"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = selectedName, fontSize = 13.sp)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(
                            expanded = expandedEmployeeDropdown,
                            onDismissRequest = { expandedEmployeeDropdown = false }
                        ) {
                            employees.forEach { emp ->
                                DropdownMenuItem(
                                    text = { Text(emp.employeeName) },
                                    onClick = {
                                        selectedEmployeeId = emp.userId
                                        expandedEmployeeDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Sheets URL input
                    OutlinedTextField(
                        value = sheetUrl,
                        onValueChange = { sheetUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Google Sheets URL", fontSize = 11.sp) },
                        placeholder = { Text("https://script.google.com/...", fontSize = 11.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )

                    // Submit Button
                    Button(
                        onClick = {
                            if (selectedEmployeeId == null) {
                                Toast.makeText(context, "Select employee", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (sheetUrl.isBlank()) {
                                Toast.makeText(context, "URL is required", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            saving = true
                            scope.launch {
                                val emp = employees.first { it.userId == selectedEmployeeId }
                                val ok = if (editingTlId != null) {
                                    TeamLeaderManager.updateTeamLeader(editingTlId!!, emp.employeeName, sheetUrl, emp.userId)
                                } else {
                                    TeamLeaderManager.addTeamLeader(emp.employeeName, sheetUrl, emp.userId) != null
                                }
                                saving = false
                                if (ok) {
                                    Toast.makeText(context, "TL Saved", Toast.LENGTH_SHORT).show()
                                    editingTlId = null
                                    selectedEmployeeId = null
                                    sheetUrl = ""
                                    viewModel.loadAllEmployeeData()
                                } else {
                                    Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6A00))
                    ) {
                        if (saving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(text = if (editingTlId != null) "Update Team Leader" else "Save Team Leader", fontSize = 13.sp)
                        }
                    }

                    if (editingTlId != null) {
                        TextButton(
                            onClick = {
                                editingTlId = null
                                selectedEmployeeId = null
                                sheetUrl = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Cancel Edit", color = Color(0xFF64748B), fontSize = 12.sp)
                        }
                    }
                }

                Text(text = "Active Team Leaders", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))

                // Scrollable list of active TLs
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tlList) { tl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                                .border(0.5.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = tl.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                Text(text = tl.sheetUrl, fontSize = 10.sp, color = Color(0xFF64748B), maxLines = 1)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { showAssignMembers = tl },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.GroupAdd, contentDescription = "Assign Members", tint = Color(0xFFFF6A00), modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = {
                                        editingTlId = tl.id
                                        selectedEmployeeId = tl.userId
                                        sheetUrl = tl.sheetUrl
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF3B82F6), modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val ok = TeamLeaderManager.deleteTeamLeader(tl.id)
                                            if (ok) {
                                                Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                                                viewModel.loadAllEmployeeData()
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAssignMembers != null) {
        BulkAssignDialog(
            tl = showAssignMembers!!,
            employees = employees,
            onDismiss = { showAssignMembers = null }
        )
    }
}

@Composable
fun BulkAssignDialog(
    tl: TeamLeaderManager.TeamLeader,
    employees: List<EmployeeSummary>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    val checkedIds = remember { mutableStateMapOf<String, Boolean>() }
    val currentTlIds = remember { mutableStateMapOf<String, String>() }
    val tlNameMap = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(Unit) {
        // Fetch TL names
        val tls = TeamLeaderManager.fetchAllTeamLeaders()
        tls.forEach { t -> tlNameMap[t.id] = t.name }

        // Fetch each employee's TL assignment in parallel
        employees.forEach { emp ->
            val curTl = TeamLeaderManager.getTlIdForUser(emp.userId) ?: ""
            currentTlIds[emp.userId] = curTl
            checkedIds[emp.userId] = curTl == tl.id
        }
        loading = false
    }

    var searchQuery by remember { mutableStateOf("") }
    val filteredEmployees = remember(employees, searchQuery) {
        if (searchQuery.isBlank()) employees
        else employees.filter { it.employeeName.lowercase().contains(searchQuery.lowercase()) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f).shadow(6.dp, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "${tl.name} — Members", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                Text(text = "Checked = assigned to this TL, Unchecked = not in this TL", fontSize = 10.sp, color = Color(0xFF64748B))

                Divider(color = Color(0xFFE2E8F0))

                if (loading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFFF6A00))
                    }
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search employees...", fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )

                    Text(
                        text = "${checkedIds.values.count { it }} selected",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6A00)
                    )

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredEmployees) { emp ->
                            val isChecked = checkedIds[emp.userId] ?: false
                            val curTlId = currentTlIds[emp.userId] ?: ""
                            val subtitle = if (curTlId.isNotEmpty()) "Assigned to: ${tlNameMap[curTlId] ?: "Another TL"}" else "Unassigned"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { checkedIds[emp.userId] = !isChecked }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(text = emp.employeeName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                                    Text(text = subtitle, fontSize = 10.sp, color = if (curTlId == tl.id) Color(0xFF10B981) else Color(0xFF94A3B8))
                                }
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checkedIds[emp.userId] = it ?: false },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFF6A00))
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !saving) {
                        Text(text = "Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            saving = true
                            scope.launch {
                                var success = 0
                                var fail = 0
                                employees.forEach { emp ->
                                    val isCheckedNow = checkedIds[emp.userId] ?: false
                                    val originallyChecked = currentTlIds[emp.userId] == tl.id

                                    if (isCheckedNow && !originallyChecked) {
                                        val ok = TeamLeaderManager.assignTlToUser(emp.userId, tl.id)
                                        if (ok) {
                                            success++
                                            SheetsSync.adminSyncEmployeeToTl(context, emp.userId, emp.employeeName, tl.id)
                                        } else {
                                            fail++
                                        }
                                    } else if (!isCheckedNow && originallyChecked) {
                                        val curTl = TeamLeaderManager.getTlIdForUser(emp.userId)
                                        if (curTl == tl.id) {
                                            val ok = TeamLeaderManager.unassignTlFromUser(emp.userId)
                                            if (ok) success++ else fail++
                                        }
                                    }
                                }
                                saving = false
                                Toast.makeText(context, "Updated! Success: $success, Failed: $fail", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        },
                        enabled = !saving,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6A00))
                    ) {
                        if (saving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(text = "Save")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "📢 Compose Broadcast", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    placeholder = { Text("Write your announcement here...", fontSize = 12.sp) },
                    shape = RoundedCornerShape(8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, enabled = !sending) {
                        Text(text = "Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (text.isBlank()) {
                                Toast.makeText(context, "Announcement cannot be empty", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            sending = true
                            val data = mapOf(
                                "text" to text.trim(),
                                "timestamp" to System.currentTimeMillis()
                            )
                            FirebaseFirestore.getInstance()
                                .collection("announcements")
                                .add(data)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Broadcast sent!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                .addOnFailureListener {
                                    sending = false
                                    Toast.makeText(context, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                        },
                        enabled = !sending,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        if (sending) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(text = "Broadcast")
                        }
                    }
                }
            }
        }
    }
}
