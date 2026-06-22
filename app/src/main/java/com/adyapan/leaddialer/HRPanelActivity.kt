package com.adyapan.leaddialer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class HRScreen(val title: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Default.Home),
    Employees("Employees", Icons.Default.People),
    Recruitment("Recruitment", Icons.Default.Work),
    Attendance("Attendance", Icons.Default.DateRange),
    Leaves("Leaves", Icons.Default.AccessTime),
    Sales("Sales", Icons.Default.TrendingUp),
    Documents("Documents", Icons.Default.Description),
    Performance("Performance", Icons.Default.Star),
    Announcements("Announcements", Icons.Default.Notifications),
    Settings("Settings", Icons.Default.Settings)
}

class HRPanelActivity : AppCompatActivity() {

    private val viewModel: AdminViewModel by viewModels()
    private val selectedScreenState = mutableStateOf(HRScreen.Dashboard)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hr_panel)

        // Status bar styling
        window.statusBarColor = android.graphics.Color.parseColor("#FFFFFF")
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true

        val composeView = findViewById<ComposeView>(R.id.composeView)
        composeView.setContent {
            HRPortalTheme {
                HRPortalMainLayout(
                    viewModel = viewModel,
                    selectedScreen = selectedScreenState.value,
                    onScreenSelected = { selectedScreenState.value = it },
                    onFragmentRequested = { fragment ->
                        composeView.visibility = View.GONE
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.adminFragmentContainer, fragment)
                            .commit()
                    },
                    onRestoreComposeRequested = {
                        // Clear active fragment
                        val activeFrag = supportFragmentManager.findFragmentById(R.id.adminFragmentContainer)
                        if (activeFrag != null) {
                            supportFragmentManager.beginTransaction().remove(activeFrag).commit()
                        }
                        composeView.visibility = View.VISIBLE
                    },
                    onLogoutRequested = {
                        performLogout()
                    }
                )
            }
        }

        viewModel.setHRPortal(true)
        viewModel.loadAllEmployeeData()
    }

    private fun performLogout() {
        viewModel.clearAllListeners()
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("⏳ Syncing Data")
            .setMessage("Cleaning up and logging out...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        LoginPage.logout(this) {
            progressDialog.dismiss()
            startActivity(Intent(this, LoginPage::class.java))
            finishAffinity()
        }
    }

    override fun onBackPressed() {
        val activeFrag = supportFragmentManager.findFragmentById(R.id.adminFragmentContainer)
        if (activeFrag != null) {
            // Restore compose view
            val composeView = findViewById<ComposeView>(R.id.composeView)
            supportFragmentManager.beginTransaction().remove(activeFrag).commit()
            composeView.visibility = View.VISIBLE
            // Reset selected screen to Dashboard so it's not blank
            selectedScreenState.value = HRScreen.Dashboard
        } else if (selectedScreenState.value != HRScreen.Dashboard) {
            // Return to main Dashboard screen
            selectedScreenState.value = HRScreen.Dashboard
        } else {
            super.onBackPressed()
        }
    }
}

// ── CUSTOM THEME ──
@Composable
fun HRPortalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFFFF6A00),
            secondary = Color(0xFFFF8533),
            background = Color(0xFFFAF9F6),
            surface = Color(0xFFFFFFFF)
        ),
        content = content
    )
}

// ── MAIN PORTAL LAYOUT ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HRPortalMainLayout(
    viewModel: AdminViewModel,
    selectedScreen: HRScreen,
    onScreenSelected: (HRScreen) -> Unit,
    onFragmentRequested: (Fragment) -> Unit,
    onRestoreComposeRequested: () -> Unit,
    onLogoutRequested: () -> Unit
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.White,
                modifier = Modifier.width(280.dp)
            ) {
                // Header
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFE6D5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("ady.", color = Color(0xFFFF6A00), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("ADYAPAN", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2C3E50))
                        Text("HR PORTAL", fontSize = 12.sp, color = Color(0xFF7F8C8D), fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Color(0xFFF0F3F4))

                // Navigation Items
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    items(HRScreen.values()) { screen ->
                        val isSelected = selectedScreen == screen
                        NavigationDrawerItem(
                            label = { Text(screen.title, fontWeight = FontWeight.SemiBold) },
                            selected = isSelected,
                            onClick = {
                                onScreenSelected(screen)
                                // Close drawer
                                coroutineScope.launch { drawerState.close() }
                                
                                // Route to fragment or Compose View
                                if (screen == HRScreen.Employees) {
                                    onFragmentRequested(AdminEmployeesFragment())
                                } else {
                                    onRestoreComposeRequested()
                                }
                            },
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Color(0xFFFFE6D5),
                                selectedIconColor = Color(0xFFFF6A00),
                                selectedTextColor = Color(0xFFFF6A00),
                                unselectedIconColor = Color(0xFF7F8C8D),
                                unselectedTextColor = Color(0xFF2C3E50)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }

                // Logout Button at bottom
                HorizontalDivider(color = Color(0xFFF0F3F4))
                NavigationDrawerItem(
                    label = { Text("Logout", fontWeight = FontWeight.SemiBold) },
                    selected = false,
                    onClick = onLogoutRequested,
                    icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout") },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedIconColor = Color(0xFFC0392B),
                        unselectedTextColor = Color(0xFFC0392B)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            selectedScreen.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF2C3E50)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color(0xFF2C3E50))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Determine screen layout
                when (selectedScreen) {
                    HRScreen.Dashboard -> DashboardScreen(viewModel)
                    HRScreen.Employees -> {
                        // Rendered via Fragment overlay (visibility of ComposeView is set to GONE)
                    }
                    HRScreen.Recruitment -> RecruitmentScreen()
                    HRScreen.Attendance -> {
                        AdminAttendanceScreen(
                            viewModel = viewModel,
                            onItemClick = { emp ->
                                val intent = Intent(context, AdminEmployeeAttendanceActivity::class.java)
                                    .putExtra("uid", emp.userId)
                                    .putExtra("name", emp.employeeName)
                                context.startActivity(intent)
                            }
                        )
                    }
                    HRScreen.Leaves -> {
                        AdminLeavesScreen(
                            viewModel = viewModel,
                            onItemClick = { emp ->
                                val intent = Intent(context, AdminEmployeeLeavesActivity::class.java)
                                    .putExtra("uid", emp.userId)
                                    .putExtra("name", emp.employeeName)
                                context.startActivity(intent)
                            }
                        )
                    }
                    HRScreen.Sales -> AdminSalesScreen(viewModel)
                    HRScreen.Documents -> DocumentsScreen()
                    HRScreen.Performance -> PerformanceScreen(viewModel)
                    HRScreen.Announcements -> AnnouncementsScreen()
                    HRScreen.Settings -> HRSettingsScreen()
                }
            }
        }
    }
}

// ── 1. DASHBOARD SCREEN ──
@Composable
fun DashboardScreen(viewModel: AdminViewModel) {
    val context = LocalContext.current
    val employees by viewModel.employees.observeAsState(emptyList())
    val userProfiles by viewModel.userProfiles.observeAsState(emptyList())
    val todayAttendance by viewModel.todayAttendance.observeAsState(emptyMap())
    val leaveRequests by FirestoreSource.leaveRequestsFlow().collectAsState(initial = emptyList())
    val pendingLeavesCount = remember(leaveRequests) { leaveRequests.count { it.status == "Pending" } }

    val totalEmployees = employees.size
    val presentCount = employees.count {
        val status = todayAttendance[it.userId]
        status == "Present" || status == "Late"
    }
    val leaveCount = employees.count { todayAttendance[it.userId] == "Leave" || todayAttendance[it.userId] == "Sick Leave" }
    val absentCount = totalEmployees - presentCount - leaveCount

    // Joined this month calculation
    val joinedThisMonthCount = remember(userProfiles) {
        val today = Calendar.getInstance()
        val currentMonth = today.get(Calendar.MONTH)
        val currentYear = today.get(Calendar.YEAR)
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        userProfiles.count { profile ->
            val dojStr = profile.dateOfJoining
            if (dojStr.isNullOrBlank()) false
            else {
                try {
                    val date = sdf.parse(dojStr)
                    if (date != null) {
                        val dojCal = Calendar.getInstance().apply { time = date }
                        dojCal.get(Calendar.MONTH) == currentMonth && dojCal.get(Calendar.YEAR) == currentYear
                    } else false
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    // Attendance Rate calculation
    val attendanceRate = if (totalEmployees > 0) (presentCount * 100 / totalEmployees) else 0

    // Upcoming Birthdays (Next 7 days) calculation
    val upcomingBirthdaysCount = remember(userProfiles) {
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        userProfiles.count { profile ->
            val dobStr = profile.dob
            if (dobStr.isNullOrBlank()) false
            else {
                try {
                    val date = sdf.parse(dobStr)
                    if (date != null) {
                        val birthCal = Calendar.getInstance().apply { time = date }
                        val birthThisYear = Calendar.getInstance().apply {
                            set(Calendar.MONTH, birthCal.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, birthCal.get(Calendar.DAY_OF_MONTH))
                            set(Calendar.YEAR, today.get(Calendar.YEAR))
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        if (birthThisYear.before(today)) {
                            birthThisYear.add(Calendar.YEAR, 1)
                        }
                        val diffMillis = birthThisYear.timeInMillis - today.timeInMillis
                        val diffDays = diffMillis / (24 * 60 * 60 * 1000)
                        diffDays in 0..7
                    } else false
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome and Refresh Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "HR Dashboard",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                    Text(
                        text = "Live workforce, attendance, leave and recruitment overview.",
                        fontSize = 12.sp,
                        color = Color(0xFF7F8C8D)
                    )
                }
                IconButton(
                    onClick = { viewModel.loadAllEmployeeData() },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF3F4F6))
                        .border(BorderStroke(1.dp, Color(0xFFE5E7EB)), RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFF111827)
                    )
                }
            }
        }

        // Attendance Quick Mark banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE6D5)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(context, AttendanceActivity::class.java)
                        context.startActivity(intent)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Clock",
                        tint = Color(0xFFFF6A00),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Mark Attendance",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF6A00)
                        )
                        Text(
                            text = "Clock-In or Clock-Out for today",
                            fontSize = 12.sp,
                            color = Color(0xFF7F8C8D)
                        )
                    }
                }
            }
        }

        // Stats Cards: 3 rows of 2 cards each (total 6 cards)
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardCard(
                    title = "Total Employees",
                    count = totalEmployees.toString(),
                    subtitle = "Live employee records",
                    icon = Icons.Default.People,
                    color = Color(0xFF5856D6),
                    modifier = Modifier.weight(1f)
                )
                DashboardCard(
                    title = "Active Employees",
                    count = totalEmployees.toString(),
                    subtitle = if (joinedThisMonthCount > 0) "$joinedThisMonthCount joined this month" else "No new joiners this month",
                    icon = Icons.Default.PersonAdd,
                    color = Color(0xFF22C55E),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardCard(
                    title = "Open Positions",
                    count = "0",
                    subtitle = "Currently accepting candidates",
                    icon = Icons.Default.Work,
                    color = Color(0xFFFF9500),
                    modifier = Modifier.weight(1f)
                )
                DashboardCard(
                    title = "Pending Leaves",
                    count = pendingLeavesCount.toString(),
                    subtitle = "Awaiting HR review",
                    icon = Icons.Default.WatchLater,
                    color = Color(0xFF9B51E0),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardCard(
                    title = "Attendance Rate",
                    count = "$attendanceRate%",
                    subtitle = "Current month records",
                    icon = Icons.Default.DateRange,
                    color = Color(0xFF007AFF),
                    modifier = Modifier.weight(1f)
                )
                DashboardCard(
                    title = "Upcoming Birthdays",
                    count = upcomingBirthdaysCount.toString(),
                    subtitle = "Next seven days",
                    icon = Icons.Default.Cake,
                    color = Color(0xFFE91E63),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Top Performers and Pending Leave Requests Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .shadow(4.dp, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎗️", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Top Performers",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2C3E50)
                                )
                            }
                            Text(
                                text = "View reviews",
                                fontSize = 11.sp,
                                color = Color(0xFFFF6A00),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {}
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No performance reviews have been recorded.",
                                fontSize = 12.sp,
                                color = Color(0xFF95A5A6),
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .shadow(4.dp, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⏰", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Pending Leaves",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2C3E50)
                                )
                            }
                            Text(
                                text = "View all",
                                fontSize = 11.sp,
                                color = Color(0xFFFF6A00),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    viewModel.employees.value?.firstOrNull()?.let { emp ->
                                        val intent = Intent(context, AdminEmployeeLeavesActivity::class.java)
                                            .putExtra("uid", emp.userId)
                                            .putExtra("name", emp.employeeName)
                                        context.startActivity(intent)
                                    }
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (pendingLeavesCount > 0) "$pendingLeavesCount leaves pending review" else "No leave requests are awaiting review.",
                                fontSize = 12.sp,
                                color = Color(0xFF95A5A6),
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun FunnelRow(label: String, count: Int, ratio: Float, color: Color) {

    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, fontSize = 13.sp, color = Color(0xFF7F8C8D))
            Text(count.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { ratio },
            color = color,
            trackColor = Color(0xFFF2F2F7),
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
        )
    }
}

@Composable
fun DashboardCard(
    title: String,
    count: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.shadow(4.dp, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    fontSize = 11.sp,
                    color = Color(0xFF7F8C8D),
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = count,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF2C3E50)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color(0xFF95A5A6),
                fontWeight = FontWeight.Normal
            )
        }
    }
}

// ── 3. RECRUITMENT SCREEN (HR ONLY - Job creation) ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecruitmentScreen() {
    val isAdmin = FirestoreSource.isAdmin
    val db = FirebaseFirestore.getInstance()

    // State
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var expandedJobId by remember { mutableStateOf<String?>(null) }

    val jobOpenings = remember { mutableStateListOf<Map<String, Any>>() }
    val applications = remember { mutableStateListOf<Map<String, Any>>() }

    // Load job openings
    LaunchedEffect(Unit) {
        db.collection("jobOpenings")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    jobOpenings.clear()
                    for (doc in snap.documents) {
                        val data = doc.data?.toMutableMap() ?: continue
                        data["id"] = doc.id
                        jobOpenings.add(data)
                    }
                }
            }
        db.collection("jobApplications")
            .orderBy("appliedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    applications.clear()
                    for (doc in snap.documents) {
                        val data = doc.data?.toMutableMap() ?: continue
                        data["id"] = doc.id
                        applications.add(data)
                    }
                }
            }
    }

    val tabs = listOf("📋 Job Openings", "👤 Applications")

    Scaffold(
        floatingActionButton = {
            if (!isAdmin) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = Color(0xFFFF6A00),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Job Opening")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFFAF9F6))
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = Color(0xFFFF6A00)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) Color(0xFFFF6A00) else Color(0xFF7F8C8D)
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> JobOpeningsTab(jobOpenings, applications, expandedJobId) { id -> expandedJobId = if (expandedJobId == id) null else id }
                1 -> ApplicationsTab(applications)
            }
        }
    }

    if (showCreateDialog) {
        CreateJobDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { jobData ->
                db.collection("jobOpenings").add(jobData)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun JobOpeningsTab(
    jobs: List<Map<String, Any>>,
    applications: List<Map<String, Any>>,
    expandedJobId: String?,
    onToggleExpand: (String) -> Unit
) {
    val context = LocalContext.current
    if (jobs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📋", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text("No job openings yet", color = Color(0xFF7F8C8D), fontSize = 16.sp)
                Text("Tap + to create the first opening", color = Color(0xFFBDC3C7), fontSize = 13.sp)
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(jobs) { job ->
            val jobId = job["id"]?.toString() ?: return@items
            val title = job["title"]?.toString() ?: ""
            val dept = job["department"]?.toString() ?: ""
            val location = job["location"]?.toString() ?: ""
            val status = job["status"]?.toString() ?: "Open"
            val vacancies = job["vacancies"]?.toString() ?: "1"
            val employmentType = job["employmentType"]?.toString() ?: ""
            val closingDate = job["closingDate"]?.toString() ?: ""
            val description = job["description"]?.toString() ?: ""
            val requirements = job["requirements"]?.toString() ?: ""
            val applyLink = job["applyLink"]?.toString() ?: ""
            val linkedinUrl = job["linkedinUrl"]?.toString() ?: ""
            val naukriUrl = job["naukriUrl"]?.toString() ?: ""
            val indeedUrl = job["indeedUrl"]?.toString() ?: ""
            @Suppress("UNCHECKED_CAST")
            val customUrls = job["customUrls"] as? List<Map<String, String>> ?: emptyList()

            val applicantCount = applications.count { it["jobId"]?.toString() == jobId }
            val isExpanded = expandedJobId == jobId
            var showApplyDialog by remember { mutableStateOf(false) }

            val statusColor = when (status.lowercase()) {
                "open" -> Color(0xFF10B981)
                "closed" -> Color(0xFFEF4444)
                "on hold" -> Color(0xFFF59E0B)
                else -> Color(0xFF6B7280)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, RoundedCornerShape(14.dp))
                    .clickable { onToggleExpand(jobId) }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFFE6D5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("💼", fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            Text("$dept · $location", fontSize = 12.sp, color = Color(0xFF7F8C8D))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(statusColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(status, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Stats row
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("👥", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("$applicantCount applied", fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🪑", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("$vacancies vacancies", fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⏰", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(employmentType, fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                    }

                    // Posting Links
                    val links = buildList {
                        if (linkedinUrl.isNotBlank()) add("LinkedIn" to linkedinUrl)
                        if (naukriUrl.isNotBlank()) add("Naukri" to naukriUrl)
                        if (indeedUrl.isNotBlank()) add("Indeed" to indeedUrl)
                        customUrls.forEach { cu ->
                            val lbl = cu["label"] ?: "Link"
                            val url = cu["url"] ?: ""
                            if (url.isNotBlank()) add(lbl to url)
                        }
                    }

                    if (links.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Post on:", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            links.forEach { (label, url) ->
                                val chipColor = when (label.lowercase()) {
                                    "linkedin" -> Color(0xFF0A66C2)
                                    "naukri" -> Color(0xFF1B4FBB)
                                    "indeed" -> Color(0xFF2557A7)
                                    else -> Color(0xFF6B7280)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(chipColor)
                                        .clickable {
                                            try {
                                                if (label.lowercase() == "linkedin") {
                                                    // Build a job post text with all details
                                                    val postText = buildString {
                                                        append("🚀 We're Hiring!\n\n")
                                                        append("📋 Position: $title\n")
                                                        if (dept.isNotBlank()) append("🏢 Department: $dept\n")
                                                        if (location.isNotBlank()) append("📍 Location: $location\n")
                                                        if (vacancies.isNotBlank()) append("💺 Vacancies: $vacancies\n")
                                                        if (employmentType.isNotBlank()) append("⏰ Type: $employmentType\n")
                                                        if (description.isNotBlank()) append("\n📝 ${description.take(300)}\n")
                                                        if (requirements.isNotBlank()) append("\n✅ Requirements: ${requirements.take(200)}\n")
                                                        if (applyLink.isNotBlank()) {
                                                            append("\n📩 Apply Now: $applyLink\n")
                                                        } else {
                                                            append("\n📩 Interested? DM us or send your CV!\n")
                                                        }
                                                        append("\n#Hiring #Jobs #Recruitment #${title.replace(" ", "")} #${dept.replace(" ", "")}")
                                                    }

                                                    // Try LinkedIn app via ACTION_SEND (opens post composer)
                                                    val linkedInShareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, postText)
                                                        setPackage("com.linkedin.android")
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    try {
                                                        context.startActivity(linkedInShareIntent)
                                                    } catch (e2: Exception) {
                                                        // LinkedIn app not installed — open browser share page
                                                        var finalUrl = url.trim()
                                                        if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
                                                            finalUrl = "https://$finalUrl"
                                                        }
                                                        val encodedText = android.net.Uri.encode(postText)
                                                        val browserShare = "https://www.linkedin.com/sharing/share-offsite/?url=${android.net.Uri.encode(finalUrl)}"
                                                        val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(browserShare)).apply {
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        try {
                                                            context.startActivity(browserIntent)
                                                        } catch (e3: Exception) {
                                                            android.widget.Toast.makeText(context, "LinkedIn not found. Install LinkedIn app.", android.widget.Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                } else {
                                                    // Non-LinkedIn: open URL directly
                                                    var finalUrl = url.trim()
                                                    if (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://")) {
                                                        finalUrl = "https://$finalUrl"
                                                    }
                                                    val uri = android.net.Uri.parse(finalUrl)
                                                    val openIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    try {
                                                        context.startActivity(openIntent)
                                                    } catch (e2: Exception) {
                                                        val chromeIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            setPackage("com.android.chrome")
                                                        }
                                                        try {
                                                            context.startActivity(chromeIntent)
                                                        } catch (e3: Exception) {
                                                            android.widget.Toast.makeText(context, "Cannot open link", android.widget.Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("🔗 $label", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    // Expanded details
                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        Spacer(modifier = Modifier.height(12.dp))
                        if (closingDate.isNotBlank()) {
                            Text("📅 Closing: $closingDate", fontSize = 12.sp, color = Color(0xFF64748B))
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        if (description.isNotBlank()) {
                            Text("Description", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(description, fontSize = 12.sp, color = Color(0xFF475569))
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        if (requirements.isNotBlank()) {
                            Text("Requirements", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(requirements, fontSize = 12.sp, color = Color(0xFF475569))
                        }
                        // Apply Now button — opens in-app form
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showApplyDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6A00)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📩 Apply Now", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        if (showApplyDialog) {
                            ApplyJobDialog(
                                jobId = jobId,
                                jobTitle = title,
                                onDismiss = { showApplyDialog = false }
                            )
                        }
                    }

                    // Expand indicator
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text(
                            if (isExpanded) "▲ Collapse" else "▼ View Details",
                            fontSize = 11.sp, color = Color(0xFFFF6A00), fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun ApplyJobDialog(jobId: String, jobTitle: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📩", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Apply for Position", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E293B))
                        Text(jobTitle, fontSize = 12.sp, color = Color(0xFFFF6A00), fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Name
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Full Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6A00))
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Email
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("Email Address *") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6A00))
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Phone
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Phone Number *") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6A00))
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Experience
                OutlinedTextField(
                    value = experience, onValueChange = { experience = it },
                    label = { Text("Years of Experience") },
                    placeholder = { Text("e.g. 2 years") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6A00))
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Cover message
                OutlinedTextField(
                    value = message, onValueChange = { message = it },
                    label = { Text("Why should we hire you?") },
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    maxLines = 4,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6A00))
                )
                Spacer(modifier = Modifier.height(20.dp))

                // Buttons
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF7F8C8D))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isBlank() || email.isBlank() || phone.isBlank()) {
                                android.widget.Toast.makeText(context, "Please fill Name, Email & Phone", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSubmitting = true
                            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            val data = mapOf(
                                "jobId" to jobId,
                                "jobTitle" to jobTitle,
                                "name" to name.trim(),
                                "email" to email.trim(),
                                "phone" to phone.trim(),
                                "experience" to experience.trim(),
                                "message" to message.trim(),
                                "status" to "Screening",
                                "appliedAt" to System.currentTimeMillis()
                            )
                            db.collection("jobApplications").add(data)
                                .addOnSuccessListener {
                                    isSubmitting = false
                                    android.widget.Toast.makeText(context, "✅ Application submitted successfully!", android.widget.Toast.LENGTH_LONG).show()
                                    onDismiss()
                                }
                                .addOnFailureListener { e ->
                                    isSubmitting = false
                                    android.widget.Toast.makeText(context, "Failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                        },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6A00)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isSubmitting) {
                            Text("Submitting...", fontWeight = FontWeight.Bold)
                        } else {
                            Text("Submit Application", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ApplicationsTab(applications: List<Map<String, Any>>) {
    if (applications.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📨", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text("No applications yet", color = Color(0xFF7F8C8D), fontSize = 16.sp)
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(applications) { app ->
            val name = app["name"]?.toString() ?: ""
            val jobTitle = app["jobTitle"]?.toString() ?: ""
            val email = app["email"]?.toString() ?: ""
            val phone = app["phone"]?.toString() ?: ""
            val experience = app["experience"]?.toString() ?: ""
            val status = app["status"]?.toString() ?: "Screening"
            val appliedAt = app["appliedAt"]?.toString()?.toLongOrNull() ?: 0L
            val dateStr = if (appliedAt > 0) {
                SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(appliedAt))
            } else ""

            val statusColor = when (status.lowercase()) {
                "selected" -> Color(0xFF10B981)
                "rejected" -> Color(0xFFEF4444)
                "interview" -> Color(0xFF3B82F6)
                else -> Color(0xFFF59E0B)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(12.dp))
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFFFE6D5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(name.take(1).uppercase(), color = Color(0xFFFF6A00), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                        Text("For: $jobTitle", fontSize = 11.sp, color = Color(0xFF64748B))
                        if (email.isNotBlank()) Text(email, fontSize = 11.sp, color = Color(0xFF94A3B8))
                        if (phone.isNotBlank()) Text("📞 $phone", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        if (experience.isNotBlank()) Text("Exp: $experience", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        if (dateStr.isNotBlank()) Text("Applied: $dateStr", fontSize = 10.sp, color = Color(0xFFBDC3C7))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(statusColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(status, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateJobDialog(
    onDismiss: () -> Unit,
    onSave: (Map<String, Any>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var employmentType by remember { mutableStateOf("Full Time") }
    var vacancies by remember { mutableStateOf("1") }
    var status by remember { mutableStateOf("Open") }
    var closingDate by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var requirements by remember { mutableStateOf("") }
    var applyLink by remember { mutableStateOf("") }
    var linkedinUrl by remember { mutableStateOf("") }
    var naukriUrl by remember { mutableStateOf("") }
    var indeedUrl by remember { mutableStateOf("") }
    var customUrls by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var newCustomLabel by remember { mutableStateOf("") }
    var newCustomUrl by remember { mutableStateOf("") }
    var showCustomLinkRow by remember { mutableStateOf(false) }

    val employmentTypes = listOf("Full Time", "Part Time", "Contract", "Internship", "Freelance")
    val statuses = listOf("Open", "Closed", "On Hold")
    var expandedType by remember { mutableStateOf(false) }
    var expandedStatus by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💼", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Job Opening", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E293B))
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Section: Basic Info
                Text("Basic Info", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6A00))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Job Title *") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6A00))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = department, onValueChange = { department = it },
                        label = { Text("Department") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6A00))
                    )
                    OutlinedTextField(
                        value = location, onValueChange = { location = it },
                        label = { Text("Location") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6A00))
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Employment Type dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        ExposedDropdownMenuBox(expanded = expandedType, onExpandedChange = { expandedType = it }) {
                            OutlinedTextField(
                                value = employmentType, onValueChange = {},
                                readOnly = true,
                                label = { Text("Type") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6A00))
                            )
                            ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                                employmentTypes.forEach { t ->
                                    DropdownMenuItem(text = { Text(t) }, onClick = { employmentType = t; expandedType = false })
                                }
                            }
                        }
                    }
                    // Status dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        ExposedDropdownMenuBox(expanded = expandedStatus, onExpandedChange = { expandedStatus = it }) {
                            OutlinedTextField(
                                value = status, onValueChange = {},
                                readOnly = true,
                                label = { Text("Status") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStatus) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6A00))
                            )
                            ExposedDropdownMenu(expanded = expandedStatus, onDismissRequest = { expandedStatus = false }) {
                                statuses.forEach { s ->
                                    DropdownMenuItem(text = { Text(s) }, onClick = { status = s; expandedStatus = false })
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = vacancies, onValueChange = { vacancies = it },
                        label = { Text("Vacancies") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6A00))
                    )
                    OutlinedTextField(
                        value = closingDate, onValueChange = { closingDate = it },
                        label = { Text("Closing Date") },
                        placeholder = { Text("dd-MM-yyyy") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6A00))
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Job Description") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6A00))
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = requirements, onValueChange = { requirements = it },
                    label = { Text("Requirements / Skills") },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6A00))
                )

                // Apply Link
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📩", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apply Link", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6A00))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Candidates will use this link to apply (Google Form, Typeform, your website, etc.)",
                    fontSize = 10.sp, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = applyLink, onValueChange = { applyLink = it },
                    label = { Text("Apply Link / Google Form URL *") },
                    leadingIcon = { Text("📩", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp)) },
                    placeholder = { Text("https://forms.gle/...", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6A00))
                )

                // Section: Posting Links
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔗", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Posting Links", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6A00))
                }
                Spacer(modifier = Modifier.height(8.dp))

                // LinkedIn
                OutlinedTextField(
                    value = linkedinUrl, onValueChange = { linkedinUrl = it },
                    label = { Text("LinkedIn Job URL") },
                    leadingIcon = { Text("in", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0A66C2), modifier = Modifier.padding(start = 4.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF0A66C2))
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = naukriUrl, onValueChange = { naukriUrl = it },
                    label = { Text("Naukri.com URL") },
                    leadingIcon = { Text("N", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B4FBB), modifier = Modifier.padding(start = 4.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF1B4FBB))
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = indeedUrl, onValueChange = { indeedUrl = it },
                    label = { Text("Indeed URL") },
                    leadingIcon = { Text("in", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2557A7), modifier = Modifier.padding(start = 4.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF2557A7))
                )

                // Custom Links
                Spacer(modifier = Modifier.height(8.dp))
                customUrls.forEachIndexed { idx, (label, url) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🔗 $label", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF6B7280))
                            Text(url, fontSize = 11.sp, color = Color(0xFF94A3B8))
                        }
                        IconButton(onClick = { customUrls = customUrls.toMutableList().also { it.removeAt(idx) } }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                if (showCustomLinkRow) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = newCustomLabel, onValueChange = { newCustomLabel = it },
                            label = { Text("Label") }, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6A00))
                        )
                        OutlinedTextField(
                            value = newCustomUrl, onValueChange = { newCustomUrl = it },
                            label = { Text("URL") }, modifier = Modifier.weight(2f),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6A00))
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { showCustomLinkRow = false; newCustomLabel = ""; newCustomUrl = "" }) {
                            Text("Cancel", color = Color(0xFF7F8C8D))
                        }
                        TextButton(onClick = {
                            if (newCustomLabel.isNotBlank() && newCustomUrl.isNotBlank()) {
                                customUrls = customUrls + (newCustomLabel to newCustomUrl)
                                newCustomLabel = ""; newCustomUrl = ""; showCustomLinkRow = false
                            }
                        }) {
                            Text("Add", color = Color(0xFFFF6A00))
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = { showCustomLinkRow = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFFFF6A00), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Custom Link", color = Color(0xFFFF6A00), fontSize = 12.sp)
                    }
                }

                // Action Buttons
                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF7F8C8D))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                val urlList = customUrls.map { mapOf("label" to it.first, "url" to it.second) }
                                onSave(mapOf(
                                    "title" to title,
                                    "department" to department,
                                    "location" to location,
                                    "employmentType" to employmentType,
                                    "vacancies" to (vacancies.toIntOrNull() ?: 1),
                                    "status" to status,
                                    "closingDate" to closingDate,
                                    "description" to description,
                                    "requirements" to requirements,
                                    "applyLink" to applyLink,
                                    "linkedinUrl" to linkedinUrl,
                                    "naukriUrl" to naukriUrl,
                                    "indeedUrl" to indeedUrl,
                                    "customUrls" to urlList,
                                    "createdAt" to System.currentTimeMillis()
                                ))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6A00)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Opening", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── ADMIN RECRUITMENT SCREEN (Stats-only, no job creation) ──
@Composable
fun AdminRecruitmentScreen() {
    val db = FirebaseFirestore.getInstance()
    val jobOpenings = remember { mutableStateListOf<Map<String, Any>>() }
    val applications = remember { mutableStateListOf<Map<String, Any>>() }

    LaunchedEffect(Unit) {
        db.collection("jobOpenings")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    jobOpenings.clear()
                    for (doc in snap.documents) {
                        val data = doc.data?.toMutableMap() ?: continue
                        data["id"] = doc.id
                        jobOpenings.add(data)
                    }
                }
            }
        db.collection("jobApplications")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    applications.clear()
                    for (doc in snap.documents) {
                        val data = doc.data?.toMutableMap() ?: continue
                        data["id"] = doc.id
                        applications.add(data)
                    }
                }
            }
    }

    val totalOpenings = jobOpenings.size
    val openJobs = jobOpenings.count { it["status"]?.toString()?.lowercase() == "open" }
    val totalApplications = applications.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF9F6))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Stats Header
        item {
            Text("Recruitment Overview", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RecruitmentStatCard("Total Openings", totalOpenings, "💼", Color(0xFF8B5CF6), modifier = Modifier.weight(1f))
                RecruitmentStatCard("Open Positions", openJobs, "🟢", Color(0xFF10B981), modifier = Modifier.weight(1f))
                RecruitmentStatCard("Applications", totalApplications, "📨", Color(0xFFFF6A00), modifier = Modifier.weight(1f))
            }
        }

        // Job Openings list header
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Job Openings", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
        }

        if (jobOpenings.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No job openings created yet.\nAsk HR to create openings.", color = Color(0xFF94A3B8), fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        } else {
            items(jobOpenings) { job ->
                val jobId = job["id"]?.toString() ?: ""
                val title = job["title"]?.toString() ?: ""
                val dept = job["department"]?.toString() ?: ""
                val location = job["location"]?.toString() ?: ""
                val status = job["status"]?.toString() ?: "Open"
                val vacancies = job["vacancies"]?.toString() ?: "1"
                val applicantCount = applications.count { it["jobId"]?.toString() == jobId }

                val statusColor = when (status.lowercase()) {
                    "open" -> Color(0xFF10B981)
                    "closed" -> Color(0xFFEF4444)
                    "on hold" -> Color(0xFFF59E0B)
                    else -> Color(0xFF6B7280)
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(12.dp))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFE6D5)),
                            contentAlignment = Alignment.Center
                        ) { Text("💼", fontSize = 18.sp) }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            Text("$dept · $location", fontSize = 11.sp, color = Color(0xFF7F8C8D))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                                Text("🪑 $vacancies vacancies", fontSize = 11.sp, color = Color(0xFF64748B))
                                Text("👥 $applicantCount applied", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(statusColor.copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(status, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
fun RecruitmentStatCard(title: String, count: Int, emoji: String, accentColor: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.shadow(3.dp, RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 22.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(count.toString(), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = accentColor)
            Text(title, fontSize = 10.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        }
    }
}



// ── 7. DOCUMENTS SCREEN ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen() {
    val context = LocalContext.current
    val documentsList = remember { mutableStateListOf<Map<String, Any>>() }
    val onboardingProfiles = remember { mutableStateListOf<Map<String, Any>>() }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Corporate Files", "Employee Uploads", "Onboarding Profiles")
    var selectedDocForView by remember { mutableStateOf<Map<String, Any>?>(null) }
    var selectedProfileForView by remember { mutableStateOf<Map<String, Any>?>(null) }

    val coroutineScope = rememberCoroutineScope()
    var showUploadDialog by remember { mutableStateOf(false) }
    var uploadTitle by remember { mutableStateOf("") }
    var uploadCategory by remember { mutableStateOf("Corporate Guidelines") }
    var isUploading by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isUploading = true
            val storageRef = FirebaseStorage.getInstance().reference
                .child("documents/${System.currentTimeMillis()}_${uploadTitle.replace(" ", "_")}.pdf")
            
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()

                    if (bytes != null) {
                        storageRef.putBytes(bytes).await()
                        val downloadUrl = storageRef.downloadUrl.await().toString()

                        val newDoc = hashMapOf(
                            "title" to uploadTitle,
                            "category" to uploadCategory,
                            "fileData" to downloadUrl,
                            "uploadedBy" to if (selectedTab == 1) "HR" else "",
                            "employeeName" to if (selectedTab == 1) "HR Manager" else ""
                        )

                        FirebaseFirestore.getInstance().collection("documents").add(newDoc).await()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Uploaded successfully ✅", Toast.LENGTH_SHORT).show()
                            showUploadDialog = false
                            uploadTitle = ""
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isUploading = false
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance().collection("documents")
            .addSnapshotListener { snap, err ->
                if (snap != null) {
                    documentsList.clear()
                    if (snap.isEmpty) {
                        val mock = listOf(
                            mapOf("title" to "Employee Handbook.pdf", "category" to "Corporate Guidelines"),
                            mapOf("title" to "Offer Letter Template.docx", "category" to "Recruitment Files"),
                            mapOf("title" to "NDA Agreement.pdf", "category" to "Legal Templates")
                        )
                        mock.forEach { doc ->
                            FirebaseFirestore.getInstance().collection("documents").add(doc)
                        }
                    } else {
                        for (doc in snap.documents) {
                            documentsList.add(mapOf(
                                "id" to doc.id,
                                "title" to (doc.getString("title") ?: ""),
                                "category" to (doc.getString("category") ?: ""),
                                "uploadedBy" to (doc.getString("uploadedBy") ?: ""),
                                "employeeName" to (doc.getString("employeeName") ?: ""),
                                "fileData" to (doc.getString("fileData") ?: "")
                            ))
                        }
                    }
                }
            }

        FirebaseFirestore.getInstance().collection("users")
            .addSnapshotListener { snap, err ->
                if (snap != null) {
                    onboardingProfiles.clear()
                    for (doc in snap.documents) {
                        val data = doc.data ?: continue
                        val profile = data.toMutableMap()
                        profile["uid"] = doc.id
                        onboardingProfiles.add(profile)
                    }
                }
            }
    }

    val filteredDocs = remember(documentsList, selectedTab) {
        if (selectedTab == 0) {
            documentsList.filter { !it.containsKey("uploadedBy") || it["uploadedBy"]?.toString().isNullOrBlank() }
        } else {
            documentsList.filter { it.containsKey("uploadedBy") && !it["uploadedBy"]?.toString().isNullOrBlank() }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 0 || selectedTab == 1) {
                FloatingActionButton(
                    onClick = { showUploadDialog = true },
                    containerColor = Color(0xFFFF6A00)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Upload Document", tint = Color.White)
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color(0xFFFAF9F6))) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = Color(0xFFFF6A00)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                }
            }

            if (selectedTab == 2) {
                // Onboarding Profiles List
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(onboardingProfiles) { profile ->
                        val name = profile["name"]?.toString() ?: profile["email"]?.toString()?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "Employee"
                        val email = profile["email"]?.toString() ?: ""
                        val empId = profile["employeeId"]?.toString() ?: "N/A"
                        val docsMap = profile["documents"] as? Map<*, *>
                        val docsCount = docsMap?.size ?: 0
                        
                        val fieldsToCheck = listOf(
                            "name", "phone", "alternatePhone", "fatherName", "motherName", "permanentAddress", "currentAddress", "gender", "dob", "bloodGroup", 
                            "department", "designation", "reportingManager", "teamLeader", "teamName", "employeeType", "dateOfJoining", "workLocation", "linkedinProfile", 
                            "accountNumber", "accountHolderName", "ifscCode", "branch", "bankName", "universityName", "yearOfPassing"
                        )
                        val fieldsFilled = fieldsToCheck.count { (profile[it]?.toString() ?: "").isNotBlank() }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(2.dp, RoundedCornerShape(12.dp))
                                .clickable { selectedProfileForView = profile }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFE6D5)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(name.take(1), color = Color(0xFFFF6A00), fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                                    Text("ID: $empId • $email", fontSize = 12.sp, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Details: $fieldsFilled/26",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (fieldsFilled == 26) Color(0xFF22C55E) else Color(0xFFFF9500),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (fieldsFilled == 26) Color(0xFF22C55E).copy(alpha = 0.12f) else Color(0xFFFF9500).copy(alpha = 0.12f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                        Text(
                                            text = "Docs: $docsCount/10",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (docsCount == 10) Color(0xFF22C55E) else Color(0xFFFF3B30),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (docsCount == 10) Color(0xFF22C55E).copy(alpha = 0.12f) else Color(0xFFFF3B30).copy(alpha = 0.12f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = "View Details", tint = Color.Gray)
                            }
                        }
                    }
                }
            } else {
                // Corporate / Employee Uploads List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredDocs) { doc ->
                        val title = doc["title"]?.toString() ?: ""
                        val category = doc["category"]?.toString() ?: ""
                        val empName = doc["employeeName"]?.toString() ?: ""
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(2.dp, RoundedCornerShape(12.dp))
                                .clickable { selectedDocForView = doc }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = "Doc",
                                    tint = Color(0xFFFF6A00),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                                    Text(
                                        if (empName.isNotBlank()) "By: $empName" else category,
                                        fontSize = 12.sp,
                                        color = Color(0xFF7F8C8D)
                                    )
                                }
                                IconButton(onClick = { selectedDocForView = doc }) {
                                    Icon(Icons.Default.Info, contentDescription = "View", tint = Color(0xFF7F8C8D))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUploadDialog) {
        Dialog(onDismissRequest = { if (!isUploading) showUploadDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Upload Document", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))

                    OutlinedTextField(
                        value = uploadTitle,
                        onValueChange = { uploadTitle = it },
                        label = { Text("Document Title *") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = uploadCategory,
                        onValueChange = { uploadCategory = it },
                        label = { Text("Category (e.g. Legal, Policy)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally), color = Color(0xFFFF6A00))
                        Text("Uploading to cloud storage...", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showUploadDialog = false }) {
                                Text("Cancel", color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (uploadTitle.isBlank()) {
                                        Toast.makeText(context, "Please enter a title", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    filePickerLauncher.launch("application/pdf")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6A00))
                            ) {
                                Text("Choose PDF")
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedDocForView != null) {
        val doc = selectedDocForView!!
        val title = doc["title"]?.toString() ?: ""
        val empName = doc["employeeName"]?.toString() ?: ""
        val category = doc["category"]?.toString() ?: ""
        val fileUrl = doc["fileData"]?.toString() ?: ""
        val isRealFile = fileUrl.startsWith("http")

        Dialog(onDismissRequest = { selectedDocForView = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Document Details", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("File Name: $title", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Category: $category", fontSize = 14.sp)
                        if (empName.isNotBlank()) {
                            Text("Uploaded By: $empName", fontSize = 14.sp)
                        }
                        Text(
                            text = if (isRealFile) "Status: Safe Cloud Storage (Firebase)" else "Status: Metadata Only",
                            fontSize = 13.sp,
                            color = Color(0xFF22C55E),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (isRealFile) {
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(fileUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Unable to open file", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5856D6))
                            ) {
                                Text("Open File 🌐")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Button(
                            onClick = { selectedDocForView = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6A00))
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }

    if (selectedProfileForView != null) {
        val profile = selectedProfileForView!!
        val name = profile["name"]?.toString() ?: "Employee"
        val email = profile["email"]?.toString() ?: ""
        val empId = profile["employeeId"]?.toString() ?: "N/A"
        
        var dialogTab by remember { mutableIntStateOf(0) }
        val dialogTabs = listOf("Personal", "Job", "Bank & Edu", "Docs")
        
        Dialog(onDismissRequest = { selectedProfileForView = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(vertical = 16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text(
                        text = name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C3E50)
                    )
                    Text(
                        text = "ID: $empId • $email",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    TabRow(
                        selectedTabIndex = dialogTab,
                        containerColor = Color(0xFFF2F2F7),
                        contentColor = Color(0xFFFF6A00),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).height(36.dp)
                    ) {
                        dialogTabs.forEachIndexed { index, title ->
                            Tab(
                                selected = dialogTab == index,
                                onClick = { dialogTab = index },
                                text = { Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Box(modifier = Modifier.weight(1f)) {
                        when (dialogTab) {
                            0 -> { // Personal
                                val fields = listOf(
                                    "Mobile Number" to (profile["phone"]?.toString() ?: "N/A"),
                                    "Alt Mobile" to (profile["alternatePhone"]?.toString() ?: "N/A"),
                                    "Company Email" to (profile["companyEmail"]?.toString() ?: "N/A"),
                                    "Father Name" to (profile["fatherName"]?.toString() ?: "N/A"),
                                    "Mother Name" to (profile["motherName"]?.toString() ?: "N/A"),
                                    "Gender" to (profile["gender"]?.toString() ?: "N/A"),
                                    "DOB" to (profile["dob"]?.toString() ?: "N/A"),
                                    "Blood Group" to (profile["bloodGroup"]?.toString() ?: "N/A"),
                                    "Permanent Addr" to (profile["permanentAddress"]?.toString() ?: "N/A"),
                                    "Current Addr" to (profile["currentAddress"]?.toString() ?: "N/A")
                                )
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(fields) { (label, value) ->
                                        Column {
                                            Text(label, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                            Text(value, fontSize = 13.sp, color = Color(0xFF2C3E50), fontWeight = FontWeight.Bold)
                                            HorizontalDivider(color = Color(0xFFF2F2F7), modifier = Modifier.padding(top = 4.dp))
                                        }
                                    }
                                }
                            }
                            1 -> { // Job
                                val fields = listOf(
                                    "Department" to (profile["department"]?.toString() ?: "N/A"),
                                    "Designation" to (profile["designation"]?.toString() ?: "N/A"),
                                    "Reporting Manager" to (profile["reportingManager"]?.toString() ?: "N/A"),
                                    "Team Leader" to (profile["teamLeader"]?.toString() ?: "N/A"),
                                    "Team Name" to (profile["teamName"]?.toString() ?: "N/A"),
                                    "Employee Type" to (profile["employeeType"]?.toString() ?: "N/A"),
                                    "Joining Date" to (profile["dateOfJoining"]?.toString() ?: "N/A"),
                                    "Work Location" to (profile["workLocation"]?.toString() ?: "N/A"),
                                    "LinkedIn Link" to (profile["linkedinProfile"]?.toString() ?: "N/A")
                                )
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(fields) { (label, value) ->
                                        Column {
                                            Text(label, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                            Text(value, fontSize = 13.sp, color = Color(0xFF2C3E50), fontWeight = FontWeight.Bold)
                                            HorizontalDivider(color = Color(0xFFF2F2F7), modifier = Modifier.padding(top = 4.dp))
                                        }
                                    }
                                }
                            }
                            2 -> { // Bank & Edu
                                val fields = listOf(
                                    "Bank Name" to (profile["bankName"]?.toString() ?: "N/A"),
                                    "Account Number" to (profile["accountNumber"]?.toString() ?: "N/A"),
                                    "Holder Name" to (profile["accountHolderName"]?.toString() ?: "N/A"),
                                    "IFSC Code" to (profile["ifscCode"]?.toString() ?: "N/A"),
                                    "Branch" to (profile["branch"]?.toString() ?: "N/A"),
                                    "University" to (profile["universityName"]?.toString() ?: "N/A"),
                                    "Passing Year" to (profile["yearOfPassing"]?.toString() ?: "N/A"),
                                    "Prev Experience" to (profile["previousExperience"]?.toString() ?: "N/A"),
                                    "Prev Company" to (profile["previousCompanyName"]?.toString() ?: "N/A"),
                                    "Prev Designation" to (profile["previousDesignation"]?.toString() ?: "N/A")
                                )
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(fields) { (label, value) ->
                                        Column {
                                            Text(label, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                            Text(value, fontSize = 13.sp, color = Color(0xFF2C3E50), fontWeight = FontWeight.Bold)
                                            HorizontalDivider(color = Color(0xFFF2F2F7), modifier = Modifier.padding(top = 4.dp))
                                        }
                                    }
                                }
                            }
                            3 -> { // Docs
                                val docsMap = profile["documents"] as? Map<*, *>
                                val docTypesList = listOf(
                                    "class10" to "10th Class Certificate",
                                    "class12" to "12th Class Certificate",
                                    "lastSemester" to "Last Semester Mark Sheet",
                                    "passportPhoto" to "Passport Size Photo",
                                    "aadhar" to "Aadhar Card",
                                    "pan" to "PAN Card",
                                    "drivingLicense" to "Driving License",
                                    "passport" to "Passport",
                                    "resume" to "Resume",
                                    "offerLetter" to "Offer Letter"
                                )
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(docTypesList) { (docKey, docName) ->
                                        val url = docsMap?.get(docKey)?.toString()
                                        val isUploaded = !url.isNullOrBlank()
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFF8F9FA))
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isUploaded) Icons.Default.CheckCircle else Icons.Default.Description,
                                                contentDescription = null,
                                                tint = if (isUploaded) Color(0xFF22C55E) else Color.Gray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = docName,
                                                fontSize = 12.sp,
                                                color = Color(0xFF2C3E50),
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isUploaded) {
                                                Button(
                                                    onClick = {
                                                        try {
                                                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Cannot open browser", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6A00)),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text("View", fontSize = 10.sp, color = Color.White)
                                                }
                                            } else {
                                                Text("Pending", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { selectedProfileForView = null },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)
                    ) {
                        Text("Close Profile", color = Color.Black)
                    }
                }
            }
        }
    }
}

// ── 8. PERFORMANCE SCREEN (Sales-based, auto-calculated) ──
@Composable
fun PerformanceScreen(viewModel: AdminViewModel) {
    val context = LocalContext.current
    val employees by viewModel.employees.observeAsState(emptyList())
    val performanceNotes = remember { mutableStateListOf<Map<String, Any>>() }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var selectedEmpForFeedback by remember { mutableStateOf<EmployeeSummary?>(null) }

    // Load HR/Admin sent feedback notes
    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance().collection("performance")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    performanceNotes.clear()
                    for (doc in snap.documents) {
                        performanceNotes.add(mapOf(
                            "id" to doc.id,
                            "userId" to (doc.getString("userId") ?: ""),
                            "employeeName" to (doc.getString("employeeName") ?: ""),
                            "feedback" to (doc.getString("feedback") ?: ""),
                            "timestamp" to (doc.getLong("timestamp") ?: 0L)
                        ))
                    }
                }
            }
    }

    // Sorted by performance % desc
    val sortedEmployees = remember(employees) {
        employees.sortedByDescending { emp ->
            if (emp.expectedSales > 0) emp.salesDone.toDouble() / emp.expectedSales else 0.0
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showFeedbackDialog = true },
                containerColor = Color(0xFFFF6A00)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send Feedback", tint = Color.White)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFFAF9F6))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Sales Performance", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E293B))
                Text("Auto-calculated from sales data", fontSize = 12.sp, color = Color(0xFF94A3B8))
            }

            // Summary stats
            item {
                val totalAchieved = employees.sumOf { it.salesDone }
                val totalTarget = employees.sumOf { it.expectedSales }
                val overallPct = if (totalTarget > 0) (totalAchieved * 100 / totalTarget) else 0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PerfStatCard("Team Target", totalTarget.toString(), "🎯", Color(0xFF8B5CF6), Modifier.weight(1f))
                    PerfStatCard("Achieved", totalAchieved.toString(), "✅", Color(0xFF10B981), Modifier.weight(1f))
                    PerfStatCard("Attainment", "$overallPct%", "📊", Color(0xFFFF6A00), Modifier.weight(1f))
                }
            }

            if (sortedEmployees.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("No employees found", color = Color(0xFF94A3B8))
                    }
                }
            } else {
                items(sortedEmployees) { emp ->
                    val achieved = emp.salesDone
                    val target = emp.expectedSales
                    val pct = if (target > 0) (achieved * 100.0 / target).coerceAtMost(100.0) else 0.0
                    val isOnTarget = achieved >= target

                    // Derive star rating from %
                    val stars = when {
                        pct >= 95 -> 5
                        pct >= 80 -> 4
                        pct >= 60 -> 3
                        pct >= 40 -> 2
                        else -> 1
                    }

                    val badgeColor = when {
                        pct >= 95 -> Color(0xFF10B981)
                        pct >= 70 -> Color(0xFF3B82F6)
                        pct >= 40 -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444)
                    }
                    val badgeLabel = when {
                        pct >= 95 -> "🏆 Top Performer"
                        pct >= 70 -> "🔵 On Track"
                        pct >= 40 -> "⚠️ Needs Boost"
                        else -> "🔴 Below Target"
                    }

                    // Feedback for this employee
                    val note = performanceNotes.firstOrNull { it["userId"]?.toString() == emp.userId }
                    val feedbackText = note?.get("feedback")?.toString() ?: ""
                    val noteDate = (note?.get("timestamp") as? Long)?.let {
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it))
                    } ?: ""

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFE6D5)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emp.employeeName.take(1).uppercase(), color = Color(0xFFFF6A00), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(emp.employeeName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                    Text("⭐".repeat(stars) + " (${"%.0f".format(pct)}%)", fontSize = 12.sp, color = Color(0xFFFF6A00))
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(badgeColor.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(badgeLabel, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            // Progress bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Achieved: $achieved", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text("Target: $target", fontSize = 12.sp, color = Color(0xFF64748B))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                                color = badgeColor,
                                trackColor = Color(0xFFF1F5F9),
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            )
                            if (feedbackText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF8F9FA))
                                        .padding(8.dp)
                                ) {
                                    Text("💬 ", fontSize = 12.sp)
                                    Column {
                                        Text(feedbackText, fontSize = 12.sp, color = Color(0xFF475569))
                                        if (noteDate.isNotBlank()) Text(noteDate, fontSize = 10.sp, color = Color(0xFFBDC3C7))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showFeedbackDialog) {
        SendFeedbackDialog(
            employees = employees.toList(),
            onDismiss = { showFeedbackDialog = false },
            onSend = { emp, feedbackMsg ->
                val doc = hashMapOf(
                    "userId" to emp.userId,
                    "employeeName" to emp.employeeName,
                    "feedback" to feedbackMsg,
                    "timestamp" to System.currentTimeMillis()
                )
                FirebaseFirestore.getInstance().collection("performance")
                    .whereEqualTo("userId", emp.userId)
                    .get()
                    .addOnSuccessListener { snap ->
                        if (snap.isEmpty) {
                            FirebaseFirestore.getInstance().collection("performance").add(doc)
                        } else {
                            snap.documents[0].reference.set(doc)
                        }
                        Toast.makeText(context, "Feedback sent to ${emp.employeeName}", Toast.LENGTH_SHORT).show()
                        showFeedbackDialog = false
                    }
            }
        )
    }
}

@Composable
fun PerfStatCard(title: String, value: String, emoji: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.shadow(2.dp, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(title, fontSize = 10.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendFeedbackDialog(
    employees: List<EmployeeSummary>,
    onDismiss: () -> Unit,
    onSend: (EmployeeSummary, String) -> Unit
) {
    var selectedEmp by remember { mutableStateOf<EmployeeSummary?>(null) }
    var feedbackText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("💬 Send Feedback", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E293B))
                // Employee dropdown
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedEmp?.employeeName ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Employee") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6A00))
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        employees.forEach { emp ->
                            DropdownMenuItem(
                                text = { Text(emp.employeeName) },
                                onClick = { selectedEmp = emp; expanded = false }
                            )
                        }
                    }
                }
                // Show auto-metrics for selected employee
                selectedEmp?.let { emp ->
                    val pct = if (emp.expectedSales > 0) (emp.salesDone * 100.0 / emp.expectedSales).coerceAtMost(100.0) else 0.0
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${emp.salesDone}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF10B981))
                                Text("Achieved", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                            Text("/", fontSize = 18.sp, color = Color(0xFF94A3B8))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${emp.expectedSales}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6B7280))
                                Text("Target", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${"%.0f".format(pct)}%", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF6A00))
                                Text("Attainment", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = feedbackText,
                    onValueChange = { feedbackText = it },
                    label = { Text("Feedback Note") },
                    placeholder = { Text("e.g. Great performance this month!") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF6A00)),
                    maxLines = 3
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF7F8C8D)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val emp = selectedEmp
                            if (emp != null && feedbackText.isNotBlank()) onSend(emp, feedbackText)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6A00)),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Send", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ── 9. ANNOUNCEMENTS SCREEN ──
@Composable
fun AnnouncementsScreen() {
    val context = LocalContext.current
    var announcementText by remember { mutableStateOf("") }
    val announcementsList = remember { mutableStateListOf<Map<String, Any>>() }

    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance().collection("announcements")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (snap != null) {
                    announcementsList.clear()
                    if (snap.isEmpty) {
                        val mock = listOf(
                            mapOf("text" to "Adyapan annual sports meet scheduled on June 28th!", "createdAt" to System.currentTimeMillis() - 500000),
                            mapOf("text" to "Maintenance downtime on Saturday midnight for 2 hours.", "createdAt" to System.currentTimeMillis() - 1000000)
                        )
                        mock.forEach { doc ->
                            FirebaseFirestore.getInstance().collection("announcements").add(doc)
                        }
                    } else {
                        for (doc in snap.documents) {
                            announcementsList.add(mapOf(
                                "id" to doc.id,
                                "text" to (doc.getString("text") ?: ""),
                                "createdAt" to (doc.getLong("createdAt") ?: 0L)
                            ))
                        }
                    }
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Publish Announcement", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = announcementText,
            onValueChange = { announcementText = it },
            placeholder = { Text("Write news or circular message...") },
            modifier = Modifier.fillMaxWidth().height(100.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                if (announcementText.isNotBlank()) {
                    val textToSend = announcementText
                    FirebaseFirestore.getInstance()
                        .collection("announcements")
                        .document(UUID.randomUUID().toString())
                        .set(mapOf("text" to textToSend, "createdAt" to System.currentTimeMillis()))
                    
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .get()
                        .addOnSuccessListener { snap ->
                            for (doc in snap.documents) {
                                val token = doc.getString("fcmToken")
                                if (!token.isNullOrEmpty()) {
                                    GasNotificationSender.sendNotification(
                                        context,
                                        token,
                                        "📢 New Announcement",
                                        textToSend
                                    )
                                }
                            }
                        }
                    announcementText = ""
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6A00)),
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Publish")
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Recent Announcements", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
        Spacer(modifier = Modifier.height(10.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(announcementsList) { item ->
                val msg = item["text"]?.toString() ?: ""
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(12.dp))
                ) {
                    Text(msg, fontSize = 14.sp, color = Color(0xFF2C3E50), modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

// ── 10. SETTINGS SCREEN ──
@Composable
fun HRSettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Portal Configuration", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("HR Portal v1.0.0", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF2C3E50))
                Text("Role: Human Resources Manager", fontSize = 12.sp, color = Color(0xFF7F8C8D))
            }
        }
    }
}


