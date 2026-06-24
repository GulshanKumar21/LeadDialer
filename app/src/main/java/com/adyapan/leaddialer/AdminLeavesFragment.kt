package com.adyapan.leaddialer

import android.content.Intent
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
import android.app.DatePickerDialog
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Date

class AdminLeavesFragment : Fragment() {

    private lateinit var viewModel: AdminViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(requireActivity())[AdminViewModel::class.java]

        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    AdminLeavesScreen(
                        viewModel = viewModel,
                        onItemClick = { emp ->
                            startActivity(
                                Intent(requireContext(), AdminEmployeeLeavesActivity::class.java)
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

private val NunitoFamily = FontFamily(
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold)
)

@Composable
fun AdminSimpleListScreen(
    title: String,
    viewModel: AdminViewModel,
    onItemClick: (EmployeeSummary) -> Unit
) {
    val employees by viewModel.employees.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)

    var searchQuery by remember { mutableStateOf("") }
    val filteredEmployees = remember(employees, searchQuery) {
        if (searchQuery.isBlank()) employees
        else employees.filter { it.employeeName.contains(searchQuery.trim(), ignoreCase = true) }
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
            // Screen Title
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = NunitoFamily,
                color = Color(0xFF1E293B),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search Employee...", fontFamily = NunitoFamily, fontSize = 15.sp) },
                leadingIcon = { Text("", modifier = Modifier.padding(start = 8.dp)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6A00),
                    cursorColor = Color(0xFFFF6A00)
                )
            )

            // Employee List
            if (filteredEmployees.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "No employees found." else "No employee named \"$searchQuery\".",
                        color = Color.Gray,
                        fontSize = 15.sp,
                        fontFamily = NunitoFamily
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredEmployees, key = { it.userId }) { emp ->
                        SimpleEmployeeCard(emp = emp, onClick = { onItemClick(emp) })
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
fun AdminLeavesScreen(
    viewModel: AdminViewModel,
    onItemClick: (EmployeeSummary) -> Unit
) {
    val employees by viewModel.employees.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val leaveRequests by FirestoreSource.leaveRequestsFlow().collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    val filteredEmployees = remember(employees, searchQuery) {
        if (searchQuery.isBlank()) employees
        else employees.filter { it.employeeName.contains(searchQuery.trim(), ignoreCase = true) }
    }

    var selectedDate by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
    }

    // Active leaves calculation covering selectedDate (yyyy-MM-dd)
    val activeLeavesOnSelectedDate = remember(leaveRequests, selectedDate) {
        val sdfInput = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val sdfCompare = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val targetDate = try {
            sdfCompare.parse(selectedDate)
        } catch (e: Exception) {
            null
        }

        if (targetDate == null) {
            emptyList()
        } else {
            leaveRequests.filter { req ->
                try {
                    val from = parseDateRobustly(req.fromDate)
                    val to = parseDateRobustly(req.toDate)
                    if (from != null && to != null) {
                        val cal = Calendar.getInstance()
                        cal.time = from
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        val fromTime = cal.timeInMillis

                        cal.time = to
                        cal.set(Calendar.HOUR_OF_DAY, 23)
                        cal.set(Calendar.MINUTE, 59)
                        cal.set(Calendar.SECOND, 59)
                        cal.set(Calendar.MILLISECOND, 999)
                        val toTime = cal.timeInMillis

                        targetDate.time in fromTime..toTime
                    } else false
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    // All-time totals (not date-filtered)
    val totalLeavesCount = leaveRequests.size
    val approvedLeavesCount = leaveRequests.count { it.status == "Approved" }
    val pendingLeavesCount = leaveRequests.count { it.status == "Pending" }

    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val dateParts = selectedDate.split("-")
    if (dateParts.size == 3) {
        calendar.set(Calendar.YEAR, dateParts[0].toInt())
        calendar.set(Calendar.MONTH, dateParts[1].toInt() - 1)
        calendar.set(Calendar.DAY_OF_MONTH, dateParts[2].toInt())
    }

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

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
            // Header Row with Date Picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Leaves Management",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = NunitoFamily,
                    color = Color(0xFF1E293B)
                )

                // Date Selector Button
                val sdfDisplay = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                val sdfKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val displayDate = try {
                    val date = sdfKey.parse(selectedDate)
                    if (date != null) sdfDisplay.format(date) else selectedDate
                } catch (e: Exception) {
                    selectedDate
                }

                Box(
                    modifier = Modifier
                        .background(Color.White, shape = RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFFF6A00), shape = RoundedCornerShape(8.dp))
                        .clickable { datePickerDialog.show() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$displayDate",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = NunitoFamily,
                        color = Color(0xFFFF6A00)
                    )
                }
            }

            // Stats Cards Row: Total, Approved, Pending
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Total Leaves Card (Purple)
                AttendanceStatCard(
                    title = "Total Leaves",
                    count = totalLeavesCount,
                    textColor = Color(0xFF8B5CF6),
                    gradientColors = listOf(Color(0x1A8B5CF6), Color(0x0A8B5CF6)),
                    borderColor = Color(0x408B5CF6),
                    modifier = Modifier.weight(1f)
                )

                // Approved Leaves Card (Green)
                AttendanceStatCard(
                    title = "Approved",
                    count = approvedLeavesCount,
                    textColor = Color(0xFF10B981),
                    gradientColors = listOf(Color(0x1A10B981), Color(0x0A10B981)),
                    borderColor = Color(0x4010B981),
                    modifier = Modifier.weight(1f)
                )

                // Pending Leaves Card (Orange)
                AttendanceStatCard(
                    title = "Pending",
                    count = pendingLeavesCount,
                    textColor = Color(0xFFEA580C),
                    gradientColors = listOf(Color(0x1AEA580C), Color(0x0AEA580C)),
                    borderColor = Color(0x40EA580C),
                    modifier = Modifier.weight(1f)
                )
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search Employee...", fontFamily = NunitoFamily, fontSize = 15.sp) },
                leadingIcon = { Text("", modifier = Modifier.padding(start = 8.dp)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6A00),
                    cursorColor = Color(0xFFFF6A00)
                )
            )

            // Employee List
            if (filteredEmployees.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "No employees found." else "No employee named \"$searchQuery\".",
                        color = Color.Gray,
                        fontSize = 15.sp,
                        fontFamily = NunitoFamily
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredEmployees, key = { it.userId }) { emp ->
                        val hasPending = remember(leaveRequests, emp.userId) {
                            leaveRequests.any { it.uid == emp.userId && it.status == "Pending" }
                        }
                        SimpleEmployeeCard(
                            emp = emp,
                            hasPending = hasPending,
                            onClick = { onItemClick(emp) }
                        )
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
fun SimpleEmployeeCard(
    emp: EmployeeSummary,
    hasPending: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassy3dCardEffect(isClickable = true, onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar Initials
            val initial = emp.employeeName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasPending) {
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFFEF4444), Color(0xFFFCA5A5))
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFFFF6A00), Color(0xFFFF9E59))
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = NunitoFamily
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Employee Name & Pending Request text
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = emp.employeeName,
                    fontSize = 18.sp,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                if (hasPending) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Pending Leave Request",
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Right arrow indicator
            Text(
                text = "›",
                fontSize = 26.sp,
                color = if (hasPending) Color(0xFFEF4444) else Color(0xFF94A3B8),
                modifier = Modifier.padding(end = 4.dp)
            )
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
            color = Color(0xE6FFFFFF), // Translucent Milky White Glass
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

private fun parseDateRobustly(dateStr: String): Date? {
    if (dateStr.isBlank()) return null
    val formats = listOf(
        "dd/MM/yyyy",
        "dd-MM-yyyy",
        "yyyy-MM-dd",
        "yyyy/MM/dd",
        "d/M/yyyy",
        "d-M-yyyy"
    )
    for (format in formats) {
        try {
            val sdf = SimpleDateFormat(format, Locale.getDefault())
            sdf.isLenient = false
            return sdf.parse(dateStr)
        } catch (e: Exception) {
            // try next
        }
    }
    return null
}

