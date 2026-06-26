package com.adyapan.leaddialer

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private lateinit var viewModel: LeadViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val factory = LeadViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), factory)[LeadViewModel::class.java]

        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    DashboardScreen(viewModel = viewModel, onCardClick = { status, title ->
                        (activity as? MainActivity)?.loadFragmentWithBack(
                            FilteredLeadsFragment.newInstance(status),
                            title
                        )
                    })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = "Dashboard"
        viewModel.refreshStats()
        viewModel.loadTargets()
        // Milky-white status bar for light dashboard
        requireActivity().window.statusBarColor = android.graphics.Color.parseColor("#F8F8F5")
        androidx.core.view.WindowInsetsControllerCompat(
            requireActivity().window, requireActivity().window.decorView
        ).isAppearanceLightStatusBars = true
    }

    override fun onPause() {
        super.onPause()
        // Restore milky-white status bar for other screens
        requireActivity().window.statusBarColor = android.graphics.Color.parseColor("#F8F8F5")
        androidx.core.view.WindowInsetsControllerCompat(
            requireActivity().window, requireActivity().window.decorView
        ).isAppearanceLightStatusBars = true
    }
}

// Custom Font Family setup
private val PoppinsFamily = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DashboardScreen(
    viewModel: LeadViewModel,
    onCardClick: (status: String, title: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Announcements states
    var showAnnouncementsDialog by remember { mutableStateOf(false) }
    val announcementsList = remember { mutableStateListOf<Map<String, Any>>() }

    // Fetch announcements from Firestore
    DisposableEffect(Unit) {
        val listener = FirebaseFirestore.getInstance()
            .collection("announcements")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    announcementsList.clear()
                    for (doc in snap.documents) {
                        announcementsList.add(mapOf(
                            "id" to doc.id,
                            "text" to (doc.getString("text") ?: ""),
                            "createdAt" to (doc.getLong("createdAt") ?: 0L)
                        ))
                    }
                }
            }
        onDispose {
            listener.remove()
        }
    }

    // ── LiveData Observers ──
    val totalLeads by viewModel.totalLeads.observeAsState(0)
    val totalPending by viewModel.totalPending.observeAsState(0)
    val totalConnected by viewModel.totalConnected.observeAsState(0)
    val totalCalled by viewModel.totalCalled.observeAsState(0)
    val allLeads by viewModel.allLeads.observeAsState(emptyList())

    // Calculated stats
    val busyCount = allLeads.count { it.status == "Busy" }
    val salesCount = allLeads.count { it.salesDone }

    // Greeting setup
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 0..11 -> "Good Morning,"
            in 12..16 -> "Good Afternoon,"
            in 17..20 -> "Good Evening,"
            else -> "Good Night,"
        }
    }

    val uid = FirebaseAuth.getInstance().currentUser?.uid

    val displayName = remember(uid) {
        val email = FirebaseAuth.getInstance().currentUser?.email ?: "user@gmail.com"
        val namePart = email.substringBefore("@")
        val cleanName = namePart.replace(Regex("[^a-zA-Z]"), "").lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        if (cleanName.isNotEmpty()) cleanName else "User"
    }

    val initials = remember(displayName) {
        displayName.take(1).uppercase()
    }

    // Expected Sales & Admin Target (observed from ViewModel)
    val todayDateStr = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()) }
    val expectedSales by viewModel.expectedSales.observeAsState(0)
    val adminTarget by viewModel.adminTarget.observeAsState("Not Set")

    // Thought of the Day
    var thoughtText by remember { mutableStateOf("Believe you can and you're halfway there.") }
    var thoughtAuthor by remember { mutableStateOf("Theodore Roosevelt") }

    // Fetch data from Firestore/RTDB
    DisposableEffect(uid) {
        if (uid != null) {
            // Firestore Thought of the Day Listener
            val totdListener = FirebaseFirestore.getInstance()
                .collection("settings")
                .document("thoughtOfTheDay")
                .addSnapshotListener { snap, _ ->
                    if (snap != null && snap.exists()) {
                        snap.getString("text")?.let { thoughtText = it }
                        snap.getString("author")?.let { thoughtAuthor = it }
                    }
                }

            onDispose {
                totdListener?.remove()
            }
        } else {
            onDispose {}
        }
    }

    // Scroll state
    val scrollState = rememberScrollState()
    val bgGradientColor = Color(0xFFF2F2F7)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {

            // ── HERO HEADER ─────────────────────────────────────────
            Card(
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    // Top row: Hamburger + Avatar + Bell
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Card(
                            shape = CircleShape,
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        (context as? MainActivity)?.findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout)
                                            ?.openDrawer(androidx.core.view.GravityCompat.START)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, Color(0xFFFF6A00).copy(alpha = 0.55f), CircleShape)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color(0xFFFF6A00), Color(0xFFFF8C00))
                                    )
                                )
                                .clickable {
                                    (context as? MainActivity)?.loadFragment(ProfileFragment(), "Profile")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontFamily = PoppinsFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Card(
                            shape = CircleShape,
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        showAnnouncementsDialog = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Notifications",
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(20.dp)
                                )
                                if (announcementsList.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .align(Alignment.TopEnd)
                                            .offset(x = (-4).dp, y = 4.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFF6A00))
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Greeting
                    Text(
                        text = greeting,
                        color = Color(0xFFFF7A00),
                        fontSize = 13.sp,
                        fontFamily = PoppinsFamily,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Name
                    Text(
                        text = "$displayName 👋",
                        color = Color.Black,
                        fontSize = 26.sp,
                        fontFamily = PoppinsFamily,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 4 mini stats row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MiniStat(value = totalLeads.toString(), label = "Total", modifier = Modifier.weight(1f))
                        MiniStat(value = totalCalled.toString(), label = "Called", modifier = Modifier.weight(1f))
                        MiniStat(value = busyCount.toString(), label = "Busy", modifier = Modifier.weight(1f))
                        MiniStat(value = salesCount.toString(), label = "Sales", modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFFEFEFEF), Color(0xFFDFDFDF))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "INDIA'S LARGEST\nSTUDENT COMMUNITY",
                            textAlign = TextAlign.Center,
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color.Black.copy(alpha = 0.5f),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = PoppinsFamily,
                                letterSpacing = 1.2.sp,
                                lineHeight = 28.sp
                            )
                        )
                    }
                }
            }

            // ── TODAY'S PROGRESS ────────────────────────────────────────
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .cleanCardEffect(isClickable = false)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    val progressPercent = if (totalLeads > 0) (totalCalled * 100 / totalLeads) else 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = "Progress",
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Today's Progress",
                                    color = Color(0xFFFF7A00),
                                    fontSize = 12.sp,
                                    fontFamily = PoppinsFamily,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.3.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "$totalCalled / $totalLeads leads called",
                                color = Color.Black,
                                fontSize = 15.sp,
                                fontFamily = PoppinsFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "$progressPercent%",
                            color = Color(0xFFFF5100),
                            fontSize = 28.sp,
                            fontFamily = PoppinsFamily,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val progressVal = if (totalLeads > 0) (totalCalled.toFloat() / totalLeads.toFloat()) else 0f
                    val animatedProgress by animateFloatAsState(
                        targetValue = progressVal,
                        animationSpec = tween(durationMillis = 900, easing = DecelerateInterpolatorEasing)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF2F2F7))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFF5100))
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    var showEditExpectedSalesDialog by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Expected Sales
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFECFDF5))
                                .border(1.dp, Color(0xFFD1FAE5), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Expected Sales",
                                    color = Color(0xFF047857),
                                    fontSize = 10.sp,
                                    fontFamily = PoppinsFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = expectedSales.toString(),
                                        color = Color(0xFF065F46),
                                        fontSize = 20.sp,
                                        fontFamily = PoppinsFamily,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFA7F3D0))
                                            .clickable {
                                                showEditExpectedSalesDialog = true
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit",
                                            tint = Color(0xFF065F46),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Admin Target
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFEFF6FF))
                                .border(1.dp, Color(0xFFDBEAFE), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Admin Target",
                                    color = Color(0xFF1D4ED8),
                                    fontSize = 10.sp,
                                    fontFamily = PoppinsFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = adminTarget,
                                    color = Color(0xFF1E40AF),
                                    fontSize = 20.sp,
                                    fontFamily = PoppinsFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (showEditExpectedSalesDialog) {
                        var tempExpectedSales by remember { mutableStateOf(expectedSales.toString()) }
                        AlertDialog(
                            onDismissRequest = { showEditExpectedSalesDialog = false },
                            title = {
                                Text(
                                    text = "Expected Sales",
                                    fontFamily = PoppinsFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2C3E50)
                                )
                            },
                            text = {
                                OutlinedTextField(
                                    value = tempExpectedSales,
                                    onValueChange = { tempExpectedSales = it },
                                    label = { Text("Enter Expected Sales", fontFamily = PoppinsFamily) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFFFF6A00),
                                        focusedLabelColor = Color(0xFFFF6A00),
                                        cursorColor = Color(0xFFFF6A00)
                                    )
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val newVal = tempExpectedSales.toIntOrNull() ?: 0
                                        viewModel.updateExpectedSales(newVal)
                                        showEditExpectedSalesDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6A00))
                                ) {
                                    Text("Save", fontFamily = PoppinsFamily, color = Color.White)
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showEditExpectedSalesDialog = false }
                                ) {
                                    Text("Cancel", fontFamily = PoppinsFamily, color = Color.Gray)
                                }
                            },
                            containerColor = Color.White,
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }

            // ── OVERVIEW SECTION ─────────────────────────────────────
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Overview",
                    color = Color(0xFF0F172A),
                    fontSize = 18.sp,
                    fontFamily = PoppinsFamily,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF34C759).copy(alpha = 0.1f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF34C759))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "LIVE",
                            color = Color(0xFF34C759),
                            fontSize = 10.sp,
                            fontFamily = PoppinsFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // ── OVERVIEW CARDS GRID ──────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OverviewCard(
                        title = "Total Leads",
                        value = totalLeads,
                        color = Color(0xFFFF5100),
                        iconRes = R.drawable.ic_group,
                        modifier = Modifier.weight(1f),
                        onClick = { onCardClick("Total Leads", "Total Leads") }
                    )
                    OverviewCard(
                        title = "Connected",
                        value = totalConnected,
                        color = Color(0xFF5856D6),
                        iconRes = R.drawable.ic_phone,
                        modifier = Modifier.weight(1f),
                        onClick = { onCardClick("Connected", "Connected") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OverviewCard(
                        title = "Busy Leads",
                        value = busyCount,
                        color = Color(0xFFFF9500),
                        emoji = "📵",
                        modifier = Modifier.weight(1f),
                        onClick = { onCardClick("Busy", "Busy") }
                    )
                    OverviewCard(
                        title = "Pending Leads",
                        value = totalPending,
                        color = Color(0xFF5856D6),
                        iconRes = R.drawable.ic_clock,
                        modifier = Modifier.weight(1f),
                        onClick = { onCardClick("Pending", "Pending") }
                    )
                }

                // Full Width Sales Done Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFEDEEF5).copy(alpha = 0.5f))
                        .clickable { onCardClick("SalesDone", "Sales Done") }
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "✅", fontSize = 26.sp)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Sales Done",
                                color = Color(0xFF6C6C70),
                                fontSize = 12.sp,
                                fontFamily = PoppinsFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            AnimatedCounter(
                                targetValue = salesCount,
                                color = Color(0xFF1C1C1E),
                                fontSize = 34.sp
                            )
                        }

                        Text(
                            text = "Leads Converted",
                            color = Color(0xFF6C6C70),
                            fontSize = 12.sp,
                            fontFamily = PoppinsFamily,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ── THOUGHT OF THE DAY ──────────────────────────────────
            if (thoughtText.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFFF5100))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Thought of the Day",
                        color = Color.Black,
                        fontSize = 18.sp,
                        fontFamily = PoppinsFamily,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.3).sp
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
                        .cleanCardEffect(isClickable = false)
                        .border(1.dp, Color(0xFFFF7A00).copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(text = "💡", fontSize = 28.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "\"$thoughtText\"",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontFamily = PoppinsFamily,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 21.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "— $thoughtAuthor",
                            color = Color(0xFFFF7A00),
                            fontSize = 12.sp,
                            fontFamily = PoppinsFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showAnnouncementsDialog) {
        AlertDialog(
            onDismissRequest = { showAnnouncementsDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("", fontSize = 24.sp)
                    Text(
                        text = "Announcements",
                        fontFamily = PoppinsFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF111827)
                    )
                }
            },
            text = {
                if (announcementsList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No announcements published yet.",
                            fontFamily = PoppinsFamily,
                            color = Color(0xFF6B7280),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        items(announcementsList) { item ->
                            val text = item["text"] as? String ?: ""
                            val createdAt = item["createdAt"] as? Long ?: 0L
                            val dateStr = if (createdAt > 0L) sdf.format(Date(createdAt)) else ""
                            
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = text,
                                        fontFamily = PoppinsFamily,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1F2937),
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (dateStr.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = dateStr,
                                            fontFamily = PoppinsFamily,
                                            fontSize = 11.sp,
                                            color = Color(0xFF9CA3AF),
                                            fontWeight = FontWeight.Normal,
                                            modifier = Modifier.align(Alignment.End)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAnnouncementsDialog = false }
                ) {
                    Text(
                        text = "Close",
                        fontFamily = PoppinsFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6A00)
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color.White
        )
    }
}

@Composable
fun MiniStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF4F2FC))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                color = Color(0xFF332D6A),
                fontSize = 22.sp,
                fontFamily = PoppinsFamily,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = Color(0xFF6C6C70),
                fontSize = 11.sp,
                fontFamily = PoppinsFamily,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun OverviewCard(
    title: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    emoji: String? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .cleanCardEffect(isClickable = true, onClick = onClick)
            .height(130.dp)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = title,
                        tint = color,
                        modifier = Modifier.size(22.dp)
                    )
                } else if (emoji != null) {
                    Text(text = emoji, fontSize = 22.sp)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            AnimatedCounter(
                targetValue = value,
                color = Color(0xFF1C1C1E),
                fontSize = 34.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                color = Color(0xFF6C6C70),
                fontSize = 13.sp,
                fontFamily = PoppinsFamily,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun AnimatedCounter(
    targetValue: Int,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit
) {
    val animatedValue by animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = 1200, easing = DecelerateInterpolatorEasing)
    )
    Text(
        text = animatedValue.toString(),
        color = color,
        fontSize = fontSize,
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Bold
    )
}

// Hero Image Overlay Mini Stat — frosted glass on dark background
@Composable
fun HeroMiniStat(title: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                color = Color.White,
                fontSize = 20.sp,
                fontFamily = PoppinsFamily,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.80f),
                fontSize = 10.sp,
                fontFamily = PoppinsFamily,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// Glassmorphic 3D Card Tactile Translation Click Effect (kept for Hero section)
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
        targetValue = if (isPressed) 4.dp else 16.dp,
        animationSpec = tween(100)
    )

    this
        .offset(y = translationY)
        .shadow(
            elevation = shadowElevation,
            shape = RoundedCornerShape(28.dp),
            clip = false
        )
        .background(
            color = Color.White,
            shape = RoundedCornerShape(28.dp)
        )
        .border(
            width = 1.dp,
            color = Color(0xFFF1F5F9),
            shape = RoundedCornerShape(28.dp)
        )
        .then(
            if (isClickable && onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )
}

// iOS-style Clean White Card Effect — smooth shadow + scale on press
private fun Modifier.cleanCardEffect(
    isClickable: Boolean = true,
    onClick: (() -> Unit)? = null
): Modifier = this.composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed = if (isClickable) interactionSource.collectIsPressedAsState().value else false
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 6.dp,
        animationSpec = tween(150)
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .shadow(
            elevation = elevation,
            shape = RoundedCornerShape(16.dp),
            ambientColor = Color(0x1A000000),
            spotColor = Color(0x1A000000)
        )
        .clip(RoundedCornerShape(16.dp))
        .background(Color.White)
        .then(
            if (isClickable && onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            } else Modifier
        )
}

// Touch effect helper using Compose states
fun Modifier.shadowWithTouchEffect(): Modifier = this.composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1.0f,
        animationSpec = tween(100)
    )
    this
        .scale(scale)
        .shadow(
            elevation = if (isPressed) 1.dp else 4.dp,
            shape = RoundedCornerShape(16.dp),
            clip = false
        )
}

// Decelerate Interpolator easing matching Android system
val DecelerateInterpolatorEasing = Easing { fraction ->
    val t = 1.0f - fraction
    1.0f - t * t
}

// Helper to scale banner back and forth
fun twistTween(duration: Int): TweenSpec<Float> = tween(
    durationMillis = duration,
    easing = FastOutSlowInEasing
)