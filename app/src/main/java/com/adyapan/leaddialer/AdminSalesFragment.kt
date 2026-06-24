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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import java.util.Locale
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
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

class AdminSalesFragment : Fragment() {

    private lateinit var viewModel: AdminViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(requireActivity())[AdminViewModel::class.java]

        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    AdminSalesScreen(viewModel = viewModel)
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
fun AdminSalesScreen(viewModel: AdminViewModel) {
    val employees by viewModel.employees.observeAsState(emptyList())
    val allSales by viewModel.allSales.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)

    var selectedEmployee by remember { mutableStateOf<EmployeeSummary?>(null) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Summaries, 1 = All Sales
    
    var searchQuery by remember { mutableStateOf("") }
    var salesSearchQuery by remember { mutableStateOf("") }

    val filteredEmployees = remember(employees, searchQuery) {
        employees.filter {
            it.employeeName.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredSales = remember(allSales, salesSearchQuery) {
        allSales.filter {
            it.name.contains(salesSearchQuery, ignoreCase = true) ||
            it.employeeName.contains(salesSearchQuery, ignoreCase = true) ||
            it.phone.contains(salesSearchQuery, ignoreCase = true)
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

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
            // Title
            Text(
                text = "Sales Performance",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = NunitoFamily,
                color = Color(0xFF1E293B),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Custom Tab Selector Chips (milk-glass styling)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                // Tab 1: Summaries
                val summariesColor = if (selectedTab == 0) Color(0xFFFF6A00) else Color(0xFFE2E8F0)
                val summariesTextColor = if (selectedTab == 0) Color.White else Color(0xFF475569)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(summariesColor)
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Overview By Employee",
                        color = summariesTextColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = NunitoFamily,
                        fontSize = 14.sp
                    )
                }

                // Tab 2: All Sales
                val salesColor = if (selectedTab == 1) Color(0xFFFF6A00) else Color(0xFFE2E8F0)
                val salesTextColor = if (selectedTab == 1) Color.White else Color(0xFF475569)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(salesColor)
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "All Sales (${allSales.size})",
                        color = salesTextColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = NunitoFamily,
                        fontSize = 14.sp
                    )
                }
            }

            if (selectedTab == 0) {
                // Search Bar for employees
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search employees by name...", fontFamily = NunitoFamily, color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFFFF6A00)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF6A00),
                        unfocusedBorderColor = Color.LightGray,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .shadow(1.dp, RoundedCornerShape(12.dp))
                )

                // Summaries List
                if (filteredEmployees.isEmpty() && !isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (searchQuery.isEmpty()) "No sales data available" else "No matching employees found",
                            color = Color.Gray,
                            fontSize = 15.sp,
                            fontFamily = NunitoFamily
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredEmployees, key = { it.userId }) { emp ->
                            SalesEmployeeCard(emp = emp, onClick = { selectedEmployee = emp })
                        }
                    }
                }
            } else {
                // All Sales View - Search + Export Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    OutlinedTextField(
                        value = salesSearchQuery,
                        onValueChange = { salesSearchQuery = it },
                        placeholder = { Text("Search customer or employee...", fontFamily = NunitoFamily, color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFFFF6A00)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF6A00),
                            unfocusedBorderColor = Color.LightGray,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        modifier = Modifier.weight(1f).shadow(1.dp, RoundedCornerShape(12.dp))
                    )

                    Button(
                        onClick = {
                            if (filteredSales.isEmpty()) {
                                Toast.makeText(context, "No sales records to export", Toast.LENGTH_SHORT).show()
                            } else {
                                scope.launch {
                                    val msg = SalesExcelWriter.export(context, filteredSales)
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D3748)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("Export", fontFamily = NunitoFamily, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // Detailed Sales List
                if (filteredSales.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (salesSearchQuery.isEmpty()) "No sales records found" else "No matching sales found",
                            color = Color.Gray,
                            fontSize = 15.sp,
                            fontFamily = NunitoFamily
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredSales, key = { it.firestoreId }) { sale ->
                            SaleRecordCard(sale = sale)
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

        // Custom details dialog
        selectedEmployee?.let { emp ->
            SalesDetailDialog(emp = emp, viewModel = viewModel, onDismiss = { selectedEmployee = null })
        }
    }
}

@Composable
fun SalesEmployeeCard(
    emp: EmployeeSummary,
    onClick: () -> Unit
) {
    val diff = emp.salesDone - emp.expectedSales
    val badgeColor = if (diff >= 0) Color(0xFFE2FBE7) else Color(0xFFFFEBEA)
    val badgeTextColor = if (diff >= 0) Color(0xFF1E7E34) else Color(0xFFD32F2F)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassy3dCardEffect(isClickable = true, onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            val initial = emp.employeeName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            Box(
                modifier = Modifier
                    .size(48.dp)
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = NunitoFamily
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Employee name and details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = emp.employeeName,
                    fontSize = 18.sp,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Achieved: ${emp.salesDone}  |  Target: ${emp.expectedSales}",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    fontFamily = NunitoFamily
                )
            }

            // Quick Status Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (diff >= 0) "+$diff" else "$diff",
                    color = badgeTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = NunitoFamily
                )
            }
        }
    }
}

@Composable
fun SaleRecordCard(sale: SaleRecord) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val dateStr = if (sale.calledAt > 0) dateFormat.format(Date(sale.calledAt)) else "—"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassy3dCardEffect(isClickable = false)
            .border(1.dp, Color(0xFFD4EDDA), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = sale.name,
                    fontSize = 18.sp,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE2FBE7))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Sales Done",
                        color = Color(0xFF1E7E34),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = NunitoFamily
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Phone: ${sale.phone}",
                fontSize = 14.sp,
                color = Color(0xFF475569),
                fontFamily = NunitoFamily
            )

            if (sale.collegeName.isNotBlank()) {
                Text(
                    text = "College: ${sale.collegeName} (${sale.collegeCity})",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    fontFamily = NunitoFamily
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Sold By",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontFamily = NunitoFamily
                    )
                    Text(
                        text = sale.employeeName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6A00),
                        fontFamily = NunitoFamily
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Date",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontFamily = NunitoFamily
                    )
                    Text(
                        text = dateStr,
                        fontSize = 13.sp,
                        color = Color(0xFF475569),
                        fontFamily = NunitoFamily
                    )
                }
            }

            if (sale.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Notes: ${sale.notes}",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        fontFamily = NunitoFamily
                    )
                }
            }
        }
    }
}

@Composable
fun SalesDetailDialog(
    emp: EmployeeSummary,
    viewModel: AdminViewModel,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val employeeLeads by viewModel.employeeLeads.observeAsState(emptyList())
    val isLoadingLeads by viewModel.leadsLoading.observeAsState(false)

    LaunchedEffect(emp.userId) {
        viewModel.loadEmployeeLeads(emp.userId)
    }

    val completedSales = remember(employeeLeads) {
        employeeLeads.filter { it.salesDone }
    }

    val diff = emp.salesDone - emp.expectedSales
    val isAchieved = diff >= 0
    val name = emp.employeeName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "$name",
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Sales Achieved Today: ", modifier = Modifier.weight(1f), fontFamily = NunitoFamily, fontSize = 15.sp)
                    Text(emp.salesDone.toString(), fontWeight = FontWeight.Bold, color = Color(0xFF1B7A34), fontFamily = NunitoFamily, fontSize = 15.sp)
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Target Set Today: ", modifier = Modifier.weight(1f), fontFamily = NunitoFamily, fontSize = 15.sp)
                    Text(emp.expectedSales.toString(), fontWeight = FontWeight.Bold, color = Color(0xFF475569), fontFamily = NunitoFamily, fontSize = 15.sp)
                }
                HorizontalDivider(color = Color(0xFFE2E8F0))
                Text(
                    text = if (isAchieved) "+$diff Target Achieved!" else "$diff Below Target",
                    color = if (isAchieved) Color(0xFF1B7A34) else Color(0xFFD32F2F),
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = NunitoFamily,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                HorizontalDivider(color = Color(0xFFE2E8F0))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Completed Sales (${completedSales.size})",
                        fontWeight = FontWeight.Bold,
                        fontFamily = NunitoFamily,
                        fontSize = 15.sp,
                        color = Color(0xFF1E293B)
                    )

                    if (completedSales.isNotEmpty()) {
                        Text(
                            text = "Export",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF6A00),
                            fontFamily = NunitoFamily,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable {
                                    scope.launch {
                                        val salesRecords = completedSales.map { lead ->
                                            SaleRecord(
                                                firestoreId = lead.firestoreId ?: "",
                                                name = lead.name,
                                                phone = lead.phone,
                                                status = lead.status,
                                                notes = lead.notes,
                                                calledAt = lead.calledAt,
                                                duration = lead.duration,
                                                collegeName = lead.collegeName,
                                                collegeCity = lead.collegeCity,
                                                calledBy = lead.calledBy,
                                                employeeName = emp.employeeName,
                                                salesDone = lead.salesDone
                                            )
                                        }
                                        val msg = SalesExcelWriter.export(context, salesRecords)
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                }
                                .padding(4.dp)
                        )
                    }
                }

                if (isLoadingLeads) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFFF6A00), modifier = Modifier.size(24.dp))
                    }
                } else if (completedSales.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("No completed sales listed.", color = Color.Gray, fontSize = 13.sp, fontFamily = NunitoFamily)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(completedSales) { sale ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text(sale.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B), fontFamily = NunitoFamily)
                                    Text("Phone: ${sale.phone}", fontSize = 12.sp, color = Color(0xFF475569), fontFamily = NunitoFamily)
                                    if (sale.notes.isNotBlank()) {
                                        Text("Notes: ${sale.notes}", fontSize = 12.sp, color = Color.Gray, fontFamily = NunitoFamily)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6A00))
            ) {
                Text("Close", fontFamily = NunitoFamily, fontSize = 15.sp)
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
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
