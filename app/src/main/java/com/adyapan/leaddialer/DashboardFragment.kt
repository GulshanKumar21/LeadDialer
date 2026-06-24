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

    val bgGradient = remember {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFF8FAFC), Color(0xFFEDF2F7))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {

            // ── 1. HEADER SECTION (Hamburger + Avatar + Greeting + Bell) ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hamburger — opens drawer
                    Card(
                        shape = CircleShape,
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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

                    // Avatar circle — click to open Profile
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFFFF6A00).copy(alpha = 0.55f), CircleShape)
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
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = PoppinsFamily
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Greeting + Name
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = greeting,
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            fontFamily = PoppinsFamily,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = displayName,
                            color = Color(0xFF0F172A),
                            fontSize = 20.sp,
                            fontFamily = PoppinsFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Bell notification button
                    Card(
                        shape = CircleShape,
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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

                Spacer(modifier = Modifier.height(16.dp))

                // Stats row: 4 Mini Stats side by side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniGlassStat(title = "Total",  value = totalLeads.toString(),     modifier = Modifier.weight(1f))
                    MiniGlassStat(title = "Called", value = totalCalled.toString(),    modifier = Modifier.weight(1f))
                    MiniGlassStat(title = "Busy",   value = busyCount.toString(),      modifier = Modifier.weight(1f))
                    MiniGlassStat(title = "Sales",  value = salesCount.toString(),     modifier = Modifier.weight(1f))
                }
            }

            // ── 2. STUDENT COMMUNITY BANNER (Brought below greeting & stats) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(140.dp)
                    .shadow(6.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.studentbanner),
                    contentDescription = "India's Largest Student Community Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // ── 3. Today's Progress Card (iOS Clean Widget style) ──
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .cleanCardEffect(isClickable = false)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp)
                ) {
                    // Title + Percentage row
                    val progressPercent = if (totalLeads > 0) (totalCalled * 100 / totalLeads) else 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Today's Progress",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp,
                                fontFamily = PoppinsFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$totalCalled of $totalLeads leads called",
                                color = Color(0xFF0F172A),
                                fontSize = 16.sp,
                                fontFamily = PoppinsFamily,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFFF4EE))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$progressPercent%",
                                color = Color(0xFFFF6A00),
                                fontSize = 22.sp,
                                fontFamily = PoppinsFamily,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Progress Bar
                    val progressVal = if (totalLeads > 0) (totalCalled.toFloat() / totalLeads.toFloat()) else 0f
                    val animatedProgress by animateFloatAsState(
                        targetValue = progressVal,
                        animationSpec = tween(durationMillis = 900, easing = DecelerateInterpolatorEasing)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color(0xFFFF6A00), Color(0xFFFFAA55))
                                    )
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Expected Sales + Admin Target side by side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Expected Sales chip
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFECFDF5))
                                .border(1.dp, Color(0xFFD1FAE5), RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
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
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFA7F3D0))
                                            .clickable {
                                                val input = EditText(context).apply {
                                                    inputType = InputType.TYPE_CLASS_NUMBER
                                                    if (expectedSales > 0) setText(expectedSales.toString())
                                                }
                                                AlertDialog.Builder(context)
                                                    .setTitle("Expected Sales")
                                                    .setMessage("Enter your expected sales for today:")
                                                    .setView(input)
                                                    .setPositiveButton("Save") { _, _ ->
                                                        val newVal = input.text.toString().toIntOrNull() ?: 0
                                                        viewModel.updateExpectedSales(newVal)
                                                    }
                                                    .setNegativeButton("Cancel", null)
                                                    .show()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_edit),
                                            contentDescription = "Edit",
                                            tint = Color(0xFF065F46),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Admin Target chip
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFEFF6FF))
                                .border(1.dp, Color(0xFFDBEAFE), RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
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
                }
            }

            // ── 4. Overview Header ──
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
                        .background(Color(0xFFFF6A00).copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF6A00))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE STATS",
                            color = Color(0xFFFF6A00),
                            fontSize = 10.sp,
                            fontFamily = PoppinsFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ── 5. Overview Cards Grid ──
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
                        iconRes = R.drawable.ic_group,
                        modifier = Modifier.weight(1f),
                        onClick = { onCardClick("Total Leads", "Total Leads") }
                    )
                    OverviewCard(
                        title = "Connected",
                        value = totalConnected,
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
                        emoji = "📵",
                        modifier = Modifier.weight(1f),
                        onClick = { onCardClick("Busy", "Busy") }
                    )
                    OverviewCard(
                        title = "Pending Leads",
                        value = totalPending,
                        iconRes = R.drawable.ic_clock,
                        modifier = Modifier.weight(1f),
                        onClick = { onCardClick("Pending", "Pending") }
                    )
                }

                // Full Width Sales Done Card (Premium Emerald Box)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cleanCardEffect(isClickable = true, onClick = { onCardClick("SalesDone", "Sales Done") })
                        .border(1.dp, Color(0xFFD1FAE5), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFD1FAE5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Sales Done",
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sales Done (Total Converted)",
                                color = Color(0xFF475569),
                                fontSize = 11.sp,
                                fontFamily = PoppinsFamily,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            AnimatedCounter(
                                targetValue = salesCount,
                                color = Color(0xFF065F46),
                                fontSize = 22.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Converted",
                                color = Color(0xFF059669),
                                fontSize = 10.sp,
                                fontFamily = PoppinsFamily,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "View List →",
                                color = Color(0xFF059669),
                                fontSize = 11.sp,
                                fontFamily = PoppinsFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ── 6. Thought of the Day ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Thought of the Day",
                    color = Color(0xFF0F172A),
                    fontSize = 18.sp,
                    fontFamily = PoppinsFamily,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "💡",
                    fontSize = 18.sp
                )
            }

            // ── 7. Thought of the Day Card ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
                    .cleanCardEffect(isClickable = false)
                    .border(1.dp, Color(0xFFFFEDD5), RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFF7ED))
            ) {
                // Orange left accent strip
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .background(Color(0xFFFF6A00))
                        .align(Alignment.CenterStart)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 18.dp, top = 20.dp, bottom = 20.dp)
                ) {
                    Text(
                        text = "\u201C",
                        color = Color(0xFFFF6A00).copy(alpha = 0.20f),
                        fontSize = 56.sp,
                        fontFamily = PoppinsFamily,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 32.sp,
                        modifier = Modifier.offset(y = 8.dp)
                    )
                    Text(
                        text = thoughtText,
                        color = Color(0xFF334155),
                        fontSize = 15.sp,
                        fontFamily = PoppinsFamily,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 24.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF6A00))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = thoughtAuthor,
                            color = Color(0xFFFF6A00),
                            fontSize = 13.sp,
                            fontFamily = PoppinsFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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
fun MiniGlassStat(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                color = Color(0xFFFF6A00),
                fontSize = 18.sp,
                fontFamily = PoppinsFamily,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                fontFamily = PoppinsFamily,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun OverviewCard(
    title: String,
    value: Int,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    emoji: String? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .cleanCardEffect(isClickable = true, onClick = onClick)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Icon box — top left
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFF4EE)),
                contentAlignment = Alignment.Center
            ) {
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = title,
                        tint = Color(0xFFFF6A00),
                        modifier = Modifier.size(22.dp)
                    )
                } else if (emoji != null) {
                    Text(text = emoji, fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Number — large
            AnimatedCounter(
                targetValue = value,
                color = Color(0xFF111827),
                fontSize = 30.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                color = Color(0xFF6B7280),
                fontSize = 12.sp,
                fontFamily = PoppinsFamily,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Tap indicator
            Text(
                text = "View →",
                color = Color(0xFFFF6A00),
                fontSize = 11.sp,
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