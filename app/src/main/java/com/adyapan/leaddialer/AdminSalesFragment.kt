package com.adyapan.leaddialer

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
    val isLoading by viewModel.isLoading.observeAsState(false)

    var selectedEmployee by remember { mutableStateOf<EmployeeSummary?>(null) }

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

            // Sales list
            if (employees.isEmpty() && !isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No sales data available", color = Color.Gray, fontSize = 15.sp, fontFamily = NunitoFamily)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(employees, key = { it.userId }) { emp ->
                        SalesEmployeeCard(emp = emp, onClick = { selectedEmployee = emp })
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
            SalesDetailDialog(emp = emp, onDismiss = { selectedEmployee = null })
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
fun SalesDetailDialog(
    emp: EmployeeSummary,
    onDismiss: () -> Unit
) {
    val diff = emp.salesDone - emp.expectedSales
    val isAchieved = diff >= 0
    val name = emp.employeeName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "👤 $name",
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    text = if (isAchieved) "+$diff ✅ Target Achieved!" else "$diff ⚠️ Below Target",
                    color = if (isAchieved) Color(0xFF1B7A34) else Color(0xFFD32F2F),
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = NunitoFamily,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
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
