package com.adyapan.leaddialer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.composed
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import android.app.DatePickerDialog
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Date

class AdminAttendanceFragment : Fragment() {

    private lateinit var viewModel: AdminViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(requireActivity())[AdminViewModel::class.java]

        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    AdminAttendanceScreen(
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

private val NunitoFamily = FontFamily(
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold)
)

@Composable
fun AdminAttendanceScreen(
    viewModel: AdminViewModel,
    onItemClick: (EmployeeSummary) -> Unit
) {
    val employees by viewModel.employees.observeAsState(emptyList())
    val todayAttendance by viewModel.todayAttendance.observeAsState(emptyMap())
    val isLoading by viewModel.isLoading.observeAsState(false)

    var searchQuery by remember { mutableStateOf("") }
    val filteredEmployees = remember(employees, searchQuery) {
        if (searchQuery.isBlank()) employees
        else employees.filter { it.employeeName.contains(searchQuery.trim(), ignoreCase = true) }
    }

    var selectedDate by remember { mutableStateOf(viewModel.selectedDateStr) }

    LaunchedEffect(selectedDate, employees) {
        if (employees.isNotEmpty()) {
            viewModel.loadTodayAttendance(selectedDate, employees.map { it.userId })
        }
    }

    // Attendance stats calculations (based on filtered list)
    val presentCount = filteredEmployees.count {
        todayAttendance[it.userId] == "Present"
    }
    val lateCount = filteredEmployees.count {
        todayAttendance[it.userId] == "Late"
    }
    val halfDayCount = filteredEmployees.count {
        todayAttendance[it.userId] == "Half Day"
    }
    val absentCount = filteredEmployees.count {
        val status = todayAttendance[it.userId]
        status == "Absent" || status == null
    }

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
        // Decorative background blobs
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
            // Screen Title Row with Date Picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Attendance Inspection",
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
                        text = "📅 $displayDate",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = NunitoFamily,
                        color = Color(0xFFFF6A00)
                    )
                }
            }

            // ── 4 Glassmorphic Stats Cards Row ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Present Card (Green)
                    AttendanceStatCard(
                        title = "Present",
                        count = presentCount,
                        textColor = Color(0xFF10B981),
                        gradientColors = listOf(Color(0x1A10B981), Color(0x0A10B981)),
                        borderColor = Color(0x4010B981),
                        modifier = Modifier.weight(1f)
                    )

                    // Late Card (Orange)
                    AttendanceStatCard(
                        title = "Late",
                        count = lateCount,
                        textColor = Color(0xFFEA580C),
                        gradientColors = listOf(Color(0x1AEA580C), Color(0x0AEA580C)),
                        borderColor = Color(0x40EA580C),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Half Day Card (Blue)
                    AttendanceStatCard(
                        title = "Half Day",
                        count = halfDayCount,
                        textColor = Color(0xFF0284C7),
                        gradientColors = listOf(Color(0x1A0284C7), Color(0x0A0284C7)),
                        borderColor = Color(0x400284C7),
                        modifier = Modifier.weight(1f)
                    )

                    // Absent Card (Red)
                    AttendanceStatCard(
                        title = "Absent",
                        count = absentCount,
                        textColor = Color(0xFFEF4444),
                        gradientColors = listOf(Color(0x1AEF4444), Color(0x0AEF4444)),
                        borderColor = Color(0x40EF4444),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search Employee...", fontFamily = NunitoFamily, fontSize = 15.sp) },
                leadingIcon = { Text("🔍", modifier = Modifier.padding(start = 8.dp)) },
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
                        val status = todayAttendance[emp.userId] ?: "Absent"
                        AttendanceEmployeeCard(
                            emp = emp,
                            status = status,
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
fun AttendanceStatCard(
    title: String,
    count: Int,
    textColor: Color,
    gradientColors: List<Color>,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(4.dp, shape = RoundedCornerShape(12.dp))
            .background(
                brush = Brush.verticalGradient(gradientColors),
                shape = RoundedCornerShape(12.dp)
            )
            .border(1.dp, borderColor, shape = RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = count.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = NunitoFamily,
                color = textColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = NunitoFamily,
                color = Color(0xFF64748B)
            )
        }
    }
}

@Composable
fun AttendanceEmployeeCard(
    emp: EmployeeSummary,
    status: String,
    onClick: () -> Unit
) {
    val isAbsent = status == "Absent"

    // Glassmorphic 3D Card effect customization for Absent state
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .attendanceGlassyCard(isAbsent = isAbsent, onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar Circle
            val initial = emp.employeeName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            
            // Avatar Background Brush - Red gradient for Absent, Orange gradient for others
            val avatarBrush = if (isAbsent) {
                Brush.verticalGradient(colors = listOf(Color(0xFFEF4444), Color(0xFFF87171)))
            } else {
                Brush.verticalGradient(colors = listOf(Color(0xFFFF6A00), Color(0xFFFF9E59)))
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(avatarBrush),
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

            // Employee Name and TL Name
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = emp.employeeName,
                    fontSize = 18.sp,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                if (emp.tlName.isNotBlank()) {
                    Text(
                        text = "TL: ${emp.tlName}",
                        fontSize = 13.sp,
                        fontFamily = NunitoFamily,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // Status Pill Badge
            AttendanceStatusBadge(status = status)

            Spacer(modifier = Modifier.width(10.dp))

            // Right arrow indicator
            Text(
                text = "›",
                fontSize = 26.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    }
}

@Composable
fun AttendanceStatusBadge(status: String) {
    val (text, textColor, bgColor) = when (status) {
        "Present" -> Triple("Present", Color(0xFF10B981), Color(0xFFD1FAE5))
        "Late" -> Triple("Late", Color(0xFFEA580C), Color(0xFFFFEDD5))
        "Half Day" -> Triple("Half Day", Color(0xFF0284C7), Color(0xFFE0F2FE))
        else -> Triple("Absent", Color(0xFFEF4444), Color(0xFFFEE2E2))
    }

    Box(
        modifier = Modifier
            .background(bgColor, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = NunitoFamily,
            color = textColor
        )
    }
}

private fun Modifier.attendanceGlassyCard(
    isAbsent: Boolean,
    onClick: () -> Unit
): Modifier = this.composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed = interactionSource.collectIsPressedAsState().value

    val translationY by animateDpAsState(
        targetValue = if (isPressed) 4.dp else 0.dp,
        animationSpec = tween(100)
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 8.dp,
        animationSpec = tween(100)
    )

    // Red tint for absent, translucent milky white for present
    val backgroundColor = if (isAbsent) Color(0xFFFFECEC) else Color(0xE6FFFFFF)
    val borderColor = if (isAbsent) Color(0xFFFECACA) else Color(0x30FFFFFF)

    this
        .offset(y = translationY)
        .shadow(
            elevation = shadowElevation,
            shape = RoundedCornerShape(14.dp),
            clip = false
        )
        .background(
            color = backgroundColor,
            shape = RoundedCornerShape(14.dp)
        )
        .border(
            width = 1.dp,
            brush = if (isAbsent) Brush.verticalGradient(listOf(borderColor, borderColor)) else Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFFFFF),        // top highlight
                    Color(0x30FFFFFF)         // bottom fade
                )
            ),
            shape = RoundedCornerShape(14.dp)
        )
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}
