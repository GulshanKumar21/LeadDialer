package com.adyapan.leaddialer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.window.Dialog
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadScreen(
    isAdminView: Boolean,
    employeeUid: String,
    msgId: String,
    employeeName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()

    var textInput by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var uploadProgressText by remember { mutableStateOf("") }

    val adminName = remember {
        val email = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "Admin"
        email.substringBefore("@").replaceFirstChar { it.uppercase() }
    }

    val senderName = if (isAdminView) adminName else {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        currentUser?.displayName?.ifBlank { null }
            ?: currentUser?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
            ?: "Employee"
    }

    // Load replies from subcollection
    var repliesList by remember { mutableStateOf<List<ChatEntry>>(emptyList()) }
    var parentMessage by remember { mutableStateOf<ChatEntry?>(null) }

    // Listen to changes in the subcollection
    DisposableEffect(employeeUid, msgId) {
        var repliesListener: ListenerRegistration? = null
        
        repliesListener = db.collection("messages").document(employeeUid)
            .collection("inbox").document(msgId)
            .collection("replies")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(25)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { doc ->
                    ChatEntry(
                        id = doc.id,
                        from = doc.getString("from") ?: "",
                        text = doc.getString("text") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        isReply = doc.getBoolean("isReply") ?: true,
                        fileUrl = doc.getString("fileUrl") ?: "",
                        fileName = doc.getString("fileName") ?: "",
                        fileType = doc.getString("fileType") ?: ""
                    )
                } ?: emptyList()
                repliesList = list.reversed()
            }

        // Also fetch the parent message once
        db.collection("messages").document(employeeUid)
            .collection("inbox").document(msgId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    parentMessage = ChatEntry(
                        id = doc.id,
                        from = doc.getString("from") ?: "Admin",
                        text = doc.getString("text") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        isReply = false, // parent is always admin
                        fileUrl = doc.getString("fileUrl") ?: "",
                        fileName = doc.getString("fileName") ?: "",
                        fileType = doc.getString("fileType") ?: ""
                    )
                }
            }

        onDispose {
            repliesListener?.remove()
        }
    }

    val combinedList = remember(parentMessage, repliesList) {
        val parent = parentMessage
        if (parent != null) {
            // Deduplicate: if parent text and timestamp are very close to one of the replies, don't show parent
            val hasDuplicate = repliesList.any { 
                it.text == parent.text && Math.abs(it.timestamp - parent.timestamp) < 5000 
            }
            if (hasDuplicate) {
                repliesList
            } else {
                (listOf(parent) + repliesList).sortedBy { it.timestamp }
            }
        } else {
            repliesList
        }
    }

    // Auto-scroll to bottom on new messages
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(combinedList.size) {
        if (combinedList.isNotEmpty()) {
            lazyListState.animateScrollToItem(combinedList.size - 1)
        }
    }

    // Pickers
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            uploading = true
            uploadProgressText = "Uploading Image..."
            uploadFile(context, scope, employeeUid, msgId, uri, "image", senderName, isAdminView) {
                uploading = false
            }
        }
    }

    val docLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            uploading = true
            uploadProgressText = "Uploading PDF Document..."
            uploadFile(context, scope, employeeUid, msgId, uri, "pdf", senderName, isAdminView) {
                uploading = false
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            uploading = true
            uploadProgressText = "Uploading Photo..."
            uploadBitmap(context, scope, employeeUid, msgId, bitmap, senderName, isAdminView) {
                uploading = false
            }
        }
    }

    var showAttachmentMenu by remember { mutableStateOf(false) }
    var selectedZoomImageUrl by remember { mutableStateOf<String?>(null) }

    // ── WhatsApp colours ────────────────────────────────────────────────────
    val waGreen      = Color(0xFF075E54)
    val waGreenLight = Color(0xFF25D366)
    val waBg         = Color(0xFFECE5DD)
    val waBubbleMe   = Color(0xFFDCF8C6)
    val waBubbleOther= Color.White

    Scaffold(
        containerColor = waBg,
        topBar = {
            Surface(
                color = waGreen,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                waGreenLight.copy(alpha = 0.35f),
                                androidx.compose.foundation.shape.CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (if (isAdminView) employeeName else "A").take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isAdminView) employeeName else "Admin Support",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (isAdminView) "Employee" else "Online",
                            color = Color(0xFFB2DFDB),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Message list ───────────────────────────────────────────────
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(combinedList) { entry ->
                    val isMyMsg = if (isAdminView) !entry.isReply else entry.isReply
                    WaChatBubble(
                        entry = entry,
                        isMyMsg = isMyMsg,
                        bubbleColorMe = waBubbleMe,
                        bubbleColorOther = waBubbleOther,
                        onImageClick = { selectedZoomImageUrl = it }
                    )
                }
            }

            // ── Upload progress bar ────────────────────────────────────────
            if (uploading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF3CD))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = waGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = uploadProgressText, fontSize = 12.sp, color = Color(0xFF856404), fontWeight = FontWeight.SemiBold)
                }
            }

            // ── WhatsApp-style input bar ───────────────────────────────────
            Surface(
                color = Color(0xFFF0F0F0),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box {
                        IconButton(onClick = { showAttachmentMenu = !showAttachmentMenu }) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Attach", tint = Color(0xFF9E9E9E), modifier = Modifier.size(26.dp))
                        }
                        if (showAttachmentMenu) {
                            DropdownMenu(
                                expanded = showAttachmentMenu,
                                onDismissRequest = { showAttachmentMenu = false }
                            ) {
                                DropdownMenuItem(text = { Text("📷 Camera") }, onClick = { showAttachmentMenu = false; cameraLauncher.launch(null) })
                                DropdownMenuItem(text = { Text("🖼️ Gallery") }, onClick = { showAttachmentMenu = false; galleryLauncher.launch("image/*") })
                                DropdownMenuItem(text = { Text("📄 PDF Document") }, onClick = { showAttachmentMenu = false; docLauncher.launch("application/pdf") })
                            }
                        }
                    }

                    // Pill text field
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color(0xFF1A1A1A),
                                fontSize = 15.sp
                            ),
                            decorationBox = { inner ->
                                if (textInput.isEmpty()) {
                                    Text("Message", color = Color(0xFF9E9E9E), fontSize = 15.sp)
                                }
                                inner()
                            },
                            maxLines = 5
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Green send circle
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(waGreenLight, androidx.compose.foundation.shape.CircleShape)
                            .clickable {
                                val text = textInput.trim()
                                if (text.isNotEmpty()) {
                                    textInput = ""
                                    scope.launch {
                                        sendMessage(context, employeeUid, msgId, text, "", "", "", senderName, isAdminView)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }

    // Zoom Image Dialog
    if (selectedZoomImageUrl != null) {
        Dialog(onDismissRequest = { selectedZoomImageUrl = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { selectedZoomImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                GlideImage(
                    url = selectedZoomImageUrl!!,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
                IconButton(
                    onClick = { selectedZoomImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

// ── WhatsApp-style Chat Bubble ────────────────────────────────────────────────
@Composable
fun WaChatBubble(
    entry: ChatEntry,
    isMyMsg: Boolean,
    bubbleColorMe: Color,
    bubbleColorOther: Color,
    onImageClick: (String) -> Unit
) {
    val context = LocalContext.current
    val bubbleColor = if (isMyMsg) bubbleColorMe else bubbleColorOther
    val sdf = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeStr = if (entry.timestamp > 0) sdf.format(Date(entry.timestamp)) else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isMyMsg) Arrangement.End else Arrangement.Start
    ) {
        // Left avatar for other person
        if (!isMyMsg) {
            Box(
                modifier = Modifier
                    .padding(end = 4.dp, top = 4.dp)
                    .size(28.dp)
                    .background(Color(0xFF5C6BC0).copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entry.from.take(1).uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5C6BC0)
                )
            }
        }

        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMyMsg) 18.dp else 4.dp,
                bottomEnd = if (isMyMsg) 4.dp else 18.dp
            ),
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!isMyMsg) {
                    Text(
                        text = entry.from,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF075E54),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                if (entry.fileUrl.isNotEmpty()) {
                    if (entry.fileType == "image") {
                        GlideImage(
                            url = entry.fileUrl,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onImageClick(entry.fileUrl) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    } else if (entry.fileType == "pdf") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                .clickable { openPdf(context, entry.fileUrl) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Description, contentDescription = "PDF", tint = Color(0xFFEF4444), modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = entry.fileName, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(text = "Tap to open PDF", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                if (entry.text.isNotEmpty()) {
                    Text(text = entry.text, fontSize = 14.sp, color = Color(0xFF1A1A1A))
                }

                // Time + double tick
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = timeStr, fontSize = 9.sp, color = Color.Gray)
                    if (isMyMsg) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            Icons.Default.DoneAll,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        if (isMyMsg) Spacer(modifier = Modifier.width(4.dp))
    }
}


@Composable
fun GlideImage(
    url: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
        },
        modifier = modifier,
        update = { imageView ->
            Glide.with(imageView.context)
                .load(url)
                .placeholder(android.R.drawable.progress_horizontal)
                .error(android.R.drawable.stat_notify_error)
                .into(imageView)
        }
    )
}

fun openPdf(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(url), "application/pdf")
        flags = Intent.FLAG_ACTIVITY_NO_HISTORY
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(browserIntent)
    }
}

suspend fun sendMessage(
    context: Context,
    employeeUid: String,
    msgId: String,
    text: String,
    fileUrl: String,
    fileName: String,
    fileType: String,
    senderName: String,
    isAdminView: Boolean
) {
    val db = FirebaseFirestore.getInstance()
    
    val payload = hashMapOf(
        "from" to senderName,
        "text" to text,
        "timestamp" to System.currentTimeMillis(),
        "isReply" to !isAdminView,
        "fileUrl" to fileUrl,
        "fileName" to fileName,
        "fileType" to fileType
    )

    try {
        // 1. Add reply to replies subcollection
        db.collection("messages").document(employeeUid)
            .collection("inbox").document(msgId)
            .collection("replies")
            .add(payload)
            .await()

        // 2. Bump replyCount
        val docRef = db.collection("messages").document(employeeUid)
            .collection("inbox").document(msgId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val replyCount = snapshot.getLong("replyCount") ?: 0L
            transaction.update(docRef, "replyCount", replyCount + 1)
        }.await()

        if (!isAdminView) {
            // Employee replying: Write summary to adminReplies
            val parentDoc = docRef.get().await()
            val parentText = parentDoc.getString("text") ?: ""
            db.collection("adminReplies")
                .add(hashMapOf(
                    "employeeUid" to employeeUid,
                    "employeeName" to senderName,
                    "msgId" to msgId,
                    "originalMsg" to parentText,
                    "replyText" to (if (fileUrl.isNotEmpty()) "[$fileType] $text".trim() else text),
                    "timestamp" to System.currentTimeMillis(),
                    "read" to false
                ))
                .await()
        } else {
            // Admin replying: Send FCM Notification via GAS (coroutine-safe)
            try {
                val document = db.collection("users")
                    .document(employeeUid)
                    .get()
                    .await()
                val token = document.getString("fcmToken")
                if (!token.isNullOrEmpty()) {
                    val body = if (fileUrl.isNotEmpty()) "[${fileType.uppercase()} Attachment] $text".trim() else text
                    try {
                        GasNotificationSender.sendNotification(
                            context.applicationContext ?: context,
                            token,
                            "New Message from Admin",
                            body
                        )
                    } catch (t: Throwable) {
                        android.util.Log.e("ChatThreadScreen", "GAS notification error: ${t.message}")
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e("ChatThreadScreen", "FCM token fetch error: ${t.message}")
            }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Error sending: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

fun uploadFile(
    context: Context,
    scope: CoroutineScope,
    employeeUid: String,
    msgId: String,
    uri: Uri,
    fileType: String,
    senderName: String,
    isAdminView: Boolean,
    onCompleted: () -> Unit
) {
    scope.launch(Dispatchers.IO) {
        try {
            val fileName = getFileName(context, uri)
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()

            if (bytes == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to read file", Toast.LENGTH_SHORT).show()
                    onCompleted()
                }
                return@launch
            }

            if (bytes.size > 10 * 1024 * 1024) { // 10MB limit
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "File size must be under 10MB", Toast.LENGTH_LONG).show()
                    onCompleted()
                }
                return@launch
            }

            val storage = FirebaseStorage.getInstance()
            val uniqueId = UUID.randomUUID().toString()
            val ref = storage.reference.child("message_attachments/$employeeUid/$msgId/$uniqueId/$fileName")
            ref.putBytes(bytes).await()
            val fileUrl = ref.downloadUrl.await().toString()

            sendMessage(context, employeeUid, msgId, "", fileUrl, fileName, fileType, senderName, isAdminView)

            withContext(Dispatchers.Main) {
                onCompleted()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                onCompleted()
            }
        }
    }
}

fun uploadBitmap(
    context: Context,
    scope: CoroutineScope,
    employeeUid: String,
    msgId: String,
    bitmap: Bitmap,
    senderName: String,
    isAdminView: Boolean,
    onCompleted: () -> Unit
) {
    scope.launch(Dispatchers.IO) {
        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            val bytes = stream.toByteArray()
            val fileName = "camera_capture_${System.currentTimeMillis()}.jpg"

            val storage = FirebaseStorage.getInstance()
            val uniqueId = UUID.randomUUID().toString()
            val ref = storage.reference.child("message_attachments/$employeeUid/$msgId/$uniqueId/$fileName")
            ref.putBytes(bytes).await()
            val fileUrl = ref.downloadUrl.await().toString()

            sendMessage(context, employeeUid, msgId, "", fileUrl, fileName, "image", senderName, isAdminView)

            withContext(Dispatchers.Main) {
                onCompleted()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Camera upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                onCompleted()
            }
        }
    }
}

fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "attachment"
}
