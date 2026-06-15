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

class AdminLeavesFragment : Fragment() {

    private lateinit var viewModel: AdminViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(requireActivity())[AdminViewModel::class.java]

        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    AdminSimpleListScreen(
                        title = "Leaves Management",
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
fun SimpleEmployeeCard(
    emp: EmployeeSummary,
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
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFFFF6A00), Color(0xFFFF9E59))
                        )
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

            // Employee Name
            Text(
                text = emp.employeeName,
                fontSize = 18.sp,
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
                modifier = Modifier.weight(1f)
            )

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
