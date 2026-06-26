package com.adyapan.leaddialer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.util.Log
import androidx.compose.runtime.livedata.observeAsState
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.BorderStroke

class DialerActivity : AppCompatActivity() {

    private lateinit var attendanceViewModel: AttendanceViewModel
    private lateinit var leadViewModel: LeadViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        attendanceViewModel = ViewModelProvider(this, AttendanceViewModelFactory(application))[AttendanceViewModel::class.java]
        leadViewModel = ViewModelProvider(this, LeadViewModelFactory(application))[LeadViewModel::class.java]

        setContent {
            MaterialTheme {
                val leads by leadViewModel.allLeads.observeAsState(initial = emptyList())
                DialerScreen(
                    leads = leads,
                    onBackClick = { finish() },
                    onCallClick = { number -> makeCall(number) },
                    onSaveClick = { number -> saveContact(number) },
                    vibrateFeedback = { vibrate() }
                )
            }
        }
    }

   
    @Suppress("DEPRECATION")
    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            val effect = VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE)
            vm.defaultVibrator.vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(18)
        }
    }

    // Save lead in-app dialog
    private fun saveContact(number: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_save_lead, null)

        val etName    = dialogView.findViewById<EditText>(R.id.etLeadName)
        val etCollege = dialogView.findViewById<EditText>(R.id.etLeadCollege)
        val etCity    = dialogView.findViewById<EditText>(R.id.etLeadCity)
        val tvPhone   = dialogView.findViewById<TextView>(R.id.tvLeadPhone)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnSaveLeadConfirm)
        val btnCancel  = dialogView.findViewById<Button>(R.id.btnCancelSaveLead)

        btnConfirm.text = "✅ Save"
        btnCancel.text = "❌ Cancel"
        etName.setText("")
        tvPhone.text = "$number"

        val saveDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        saveDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnConfirm.setOnClickListener {
            val name    = etName.text.toString().trim()
            val college = etCollege.text.toString().trim()
            val city    = etCity.text.toString().trim()

            if (name.isBlank()) {
                etName.error = "Name required"
                return@setOnClickListener
            }

            val newLead = Lead(
                name        = name,
                phone       = number,
                status      = "Pending",
                collegeName = college,
                collegeCity = city,
                calledAt    = 0L
            )
            leadViewModel.insert(newLead)

            saveDialog.dismiss()
            Toast.makeText(this, "✅ Lead saved: $name", Toast.LENGTH_SHORT).show()
        }

        btnCancel.setOnClickListener {
            saveDialog.dismiss()
        }

        saveDialog.show()
    }

    // Call launcher
    private fun makeCall(number: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 101)
            return
        }

        // Show checking progress
        Toast.makeText(this, "🔍 Checking...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                SheetsSync.checkIfAlreadyCalled(number)
            }

            if (result.called) {
                showAlreadyCalledWarning(number, result)
            } else {
                handleFirstCallAttendance(number)
            }
        }
    }

    private fun showAlreadyCalledWarning(number: String, result: CallCheckResult) {
        val callerName = result.calledBy.ifBlank { "Someone" }

        AlertDialog.Builder(this)
            .setTitle("⚠️ Call Already Made!")
            .setMessage(
                "📞 Phone: $number\n\n" +
                "👤 Called By: $callerName\n" +
                "📊 Status: ${result.status.ifBlank { "Pending" }}\n\n" +
                "Do you still want to make the call?"
            )
            .setPositiveButton("📞 Call Anyway") { _, _ ->
                handleFirstCallAttendance(number)
            }
            .setNegativeButton("❌ Cancel", null)
            .show()
    }

    private fun handleFirstCallAttendance(number: String) {
        lifecycleScope.launch {
            val alreadyPunchedIn = attendanceViewModel.isTodayPunchedIn()

            if (alreadyPunchedIn) {
                proceedWithCall(number)
            } else {
                val isLate = attendanceViewModel.isCurrentlyLate()
                val time   = attendanceViewModel.getCurrentTime()

                if (isLate) {
                    LateReasonDialog.show(
                        context        = this@DialerActivity,
                        currentTime    = time,
                        onReasonSubmit = { reason ->
                            attendanceViewModel.punchIn(this@DialerActivity, reason) { record ->
                                Log.d("DialerActivity", "Punched in LATE: ${record.punchInTime}")
                                proceedWithCall(number)
                            }
                        }
                    )
                } else {
                    attendanceViewModel.punchIn(this@DialerActivity) { record ->
                        Log.d("DialerActivity", "Punched in on time: ${record.punchInTime}")
                        Toast.makeText(
                            this@DialerActivity,
                            "✅ Punch-in: ${record.punchInTime}",
                            Toast.LENGTH_SHORT
                        ).show()
                        proceedWithCall(number)
                    }
                }
            }
        }
    }

    private fun proceedWithCall(number: String) {
        // Register with CallManager
        val manualLead = Lead(
            id = 0,
            name = "Manual Dial",
            phone = number,
            status = "Pending"
        )
        CallManager.currentLead = manualLead
        CallManager.callActive = true

        CallManager.placeCall(this, manualLead) { intent ->
            startActivity(intent)
        }
    }
}

private val NunitoFamily = FontFamily(
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold)
)

fun formatNumber(n: String): String {
    if (n.length <= 5) return n
    if (n.length <= 10) {
        return "${n.substring(0, 5)} ${n.substring(5)}"
    }
    return n
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerScreen(
    leads: List<Lead>,
    onBackClick: () -> Unit,
    onCallClick: (String) -> Unit,
    onSaveClick: (String) -> Unit,
    vibrateFeedback: () -> Unit
) {
    val context = LocalContext.current
    var numberText by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current

    fun insertDigit(digit: String) {
        if (numberText.text.length >= 15) return
        val selection = numberText.selection
        val text = numberText.text
        val newText = StringBuilder(text)
            .replace(selection.start, selection.end, digit)
            .toString()
        val newCursorOffset = selection.start + digit.length
        numberText = TextFieldValue(
            text = newText,
            selection = TextRange(newCursorOffset)
        )
    }

    fun deleteDigit() {
        val selection = numberText.selection
        val text = numberText.text
        if (selection.length > 0) {
            val newText = StringBuilder(text)
                .replace(selection.start, selection.end, "")
                .toString()
            numberText = TextFieldValue(
                text = newText,
                selection = TextRange(selection.start)
            )
        } else if (selection.start > 0) {
            val newText = StringBuilder(text)
                .deleteCharAt(selection.start - 1)
                .toString()
            numberText = TextFieldValue(
                text = newText,
                selection = TextRange(selection.start - 1)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F7))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row (Centered Title like Flutter AppBar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                // Back Button (Left aligned)
                Card(
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onBackClick() }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Centered Title "Dialer"
                Text(
                    text = "Dialer",
                    fontSize = 18.sp,
                    fontFamily = NunitoFamily,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Dialed Number Display Row (Matches Flutter: Clear | Number | Paste)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Clear (X) button
                if (numberText.text.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            vibrateFeedback()
                            numberText = TextFieldValue("")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                // Center Number text
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val scale by animateFloatAsState(
                        targetValue = if (numberText.text.isEmpty()) 1f else 1.06f,
                        animationSpec = tween(100)
                    )
                    Text(
                        text = if (numberText.text.isEmpty()) "Enter number" else formatNumber(numberText.text),
                        fontSize = if (numberText.text.isEmpty()) 18.sp else if (numberText.text.length > 10) 24.sp else 30.sp,
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 2.sp,
                        color = if (numberText.text.isEmpty()) Color(0xFF94A3B8) else Color(0xFF0F172A),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.scale(scale)
                    )
                }

                // Right Paste button
                IconButton(
                    onClick = {
                        vibrateFeedback()
                        val clipText = clipboardManager.getText()?.text ?: ""
                        val clean = clipText.filter { it.isDigit() || it == '*' || it == '#' || it == '+' }
                        if (clean.isNotEmpty()) {
                            numberText = TextFieldValue(clean, selection = TextRange(clean.length))
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste",
                        tint = Color(0xFFFF7A00),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            val query = numberText.text.filter { it.isDigit() }
            val matchedLead = remember(query, leads) {
                if (query.isEmpty()) null
                else leads.find { lead ->
                    lead.phone.filter { it.isDigit() }.contains(query)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (matchedLead != null) {
                    LeadMatchCard(matchedLead = matchedLead)
                }
            }

            // Dial Pad Layout
            val keys = listOf(
                listOf("1" to "", "2" to "A B C", "3" to "D E F"),
                listOf("4" to "G H I", "5" to "J K L", "6" to "M N O"),
                listOf("7" to "P Q R S", "8" to "T U V", "9" to "W X Y Z"),
                listOf("*" to "", "0" to "+", "#" to "")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                keys.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { pair ->
                            DialKeyButton(
                                digit = pair.first,
                                letters = pair.second,
                                onClick = {
                                    vibrateFeedback()
                                    insertDigit(pair.first)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action buttons: Save, Call, Backspace (Delete)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    // Save Button
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF7A00).copy(alpha = 0.1f))
                            .clickable {
                                vibrateFeedback()
                                if (numberText.text.isNotEmpty()) {
                                    onSaveClick(numberText.text)
                                } else {
                                    Toast.makeText(context, "Enter number first", Toast.LENGTH_SHORT).show()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Save Contact",
                            tint = Color(0xFFFF7A00),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Call Button (Vibrant Green Gradient)
                    val callInteractionSource = remember { MutableInteractionSource() }
                    val callPressed by callInteractionSource.collectIsPressedAsState()
                    val callScale by animateFloatAsState(
                        targetValue = if (callPressed) 0.90f else 1f,
                        animationSpec = tween(100)
                    )

                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .scale(callScale)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF34C759), Color(0xFF059669))
                                )
                            )
                            .clickable(interactionSource = callInteractionSource, indication = null) {
                                vibrateFeedback()
                                if (numberText.text.isNotEmpty()) {
                                    onCallClick(numberText.text)
                                } else {
                                    Toast.makeText(context, "Enter number", Toast.LENGTH_SHORT).show()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Backspace / Clear Button
                    val backspaceInteractionSource = remember { MutableInteractionSource() }
                    val backspacePressed by backspaceInteractionSource.collectIsPressedAsState()
                    val backspaceScale by animateFloatAsState(
                        targetValue = if (backspacePressed) 0.90f else 1f,
                        animationSpec = tween(100)
                    )

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .scale(backspaceScale)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444).copy(alpha = 0.1f))
                            .combinedClickable(
                                interactionSource = backspaceInteractionSource,
                                indication = null,
                                onClick = {
                                    vibrateFeedback()
                                    deleteDigit()
                                },
                                onLongClick = {
                                    vibrateFeedback()
                                    numberText = TextFieldValue("")
                                    Toast.makeText(context, "Cleared", Toast.LENGTH_SHORT).show()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Backspace,
                            contentDescription = "Delete",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DialKeyButton(
    digit: String,
    letters: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(100)
    )

    Card(
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(0.5.dp, Color(0xFFC6C6C8)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .size(74.dp)
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null) {
                onClick()
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = digit,
                fontSize = 26.sp,
                fontFamily = NunitoFamily,
                fontWeight = FontWeight.Light,
                color = Color(0xFF0F172A)
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}

@Composable
fun LeadMatchCard(matchedLead: Lead) {
    val isCalled = matchedLead.calledAt > 0L || matchedLead.status != "Pending"
    val statusText = if (isCalled) matchedLead.status else "Not Called Yet"
    
    val badgeBg = when (matchedLead.status) {
        "Interested" -> Color(0xFFDCFCE7)
        "Not Connected", "Wrong Number" -> Color(0xFFFEE2E2)
        "Busy" -> Color(0xFFFEF3C7)
        "Pending" -> Color(0xFFF1F5F9)
        else -> Color(0xFFF1F5F9)
    }
    val badgeText = when (matchedLead.status) {
        "Interested" -> Color(0xFF16A34A)
        "Not Connected", "Wrong Number" -> Color(0xFFDC2626)
        "Busy" -> Color(0xFFD97706)
        "Pending" -> Color(0xFF475569)
        else -> Color(0xFF475569)
    }
    val leftBorderColor = badgeText

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Status colored left bar
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(leftBorderColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = matchedLead.name,
                        fontSize = 18.sp,
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1E293B)
                    )

                    // Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(badgeBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = statusText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = badgeText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "📞 ${matchedLead.phone}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF475569)
                )

                if (matchedLead.collegeName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "🏫 ${matchedLead.collegeName}${if (matchedLead.collegeCity.isNotBlank()) ", ${matchedLead.collegeCity}" else ""}",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                }

                if (matchedLead.calledAt > 0L) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFE2E8F0))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⏳ Last call: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(matchedLead.calledAt))}",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium
                    )
                    if (matchedLead.calledBy.isNotBlank()) {
                        Text(
                            text = "👤 Called by: ${matchedLead.calledBy}",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}