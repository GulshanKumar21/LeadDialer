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

class DialerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                DialerScreen(
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

        etName.setText("")
        etName.hint = "Student Name"
        tvPhone.text = "📱 $number"

        val saveDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        saveDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<Button>(R.id.btnSaveLeadConfirm).setOnClickListener {
            val name    = etName.text.toString().trim()
            val college = etCollege.text.toString().trim()
            val city    = etCity.text.toString().trim()

            if (name.isBlank()) {
                etName.error = "Name required"
                return@setOnClickListener
            }

            val leadViewModel = ViewModelProvider(this, LeadViewModelFactory(application))[LeadViewModel::class.java]
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

        dialogView.findViewById<Button>(R.id.btnCancelSaveLead).setOnClickListener {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialerScreen(
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
            .background(Color(0xFFF5F6F8))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row (Back Button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        tint = Color(0xFF1E293B),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Dialed Number TextField Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = numberText,
                    onValueChange = {
                        val filteredText = it.text.filter { char ->
                            char.isDigit() || char == '*' || char == '#' || char == '+' || char == ' ' || char == '-'
                        }
                        if (filteredText.length <= 15) {
                            numberText = it.copy(text = filteredText)
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        textAlign = TextAlign.Center,
                        fontSize = 36.sp,
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A)
                    ),
                    cursorBrush = SolidColor(Color(0xFFFF6A00)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                keyboardController?.hide()
                            }
                        },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                        }
                    )
                )

                if (numberText.text.isEmpty()) {
                    Text(
                        text = "Enter number",
                        fontSize = 32.sp,
                        fontFamily = NunitoFamily,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Dial Pad Layout
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("*", "0", "#")
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                keys.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { digit ->
                            DialKeyButton(
                                digit = digit,
                                onClick = {
                                    vibrateFeedback()
                                    insertDigit(digit)
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
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    // Save Button
                    DialActionButton(
                        iconRes = R.drawable.ic_edit, // R.drawable.ic_save representation
                        bgColor = Color(0xFFE2E8F0),
                        iconTint = Color(0xFF475569),
                        onClick = {
                            vibrateFeedback()
                            if (numberText.text.isNotEmpty()) {
                                onSaveClick(numberText.text)
                            } else {
                                Toast.makeText(context, "Enter number first", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    // Call Button (Vibrant Orange Gradient)
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFFFF6A00), Color(0xFFFF8F3C))
                                )
                            )
                            .clickable {
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
                            painter = painterResource(id = R.drawable.ic_phone),
                            contentDescription = "Call",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Backspace / Clear Button
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2E8F0))
                            .combinedClickable(
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
                        Text(
                            text = "⌫",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
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
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = tween(100)
    )

    Box(
        modifier = Modifier
            .size(72.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(interactionSource = interactionSource, indication = null) {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            fontSize = 28.sp,
            fontFamily = NunitoFamily,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E293B)
        )
    }
}

@Composable
fun DialActionButton(
    iconRes: Int,
    bgColor: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = tween(100)
    )

    Box(
        modifier = Modifier
            .size(56.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(interactionSource = interactionSource, indication = null) {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = "Action",
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
    }
}