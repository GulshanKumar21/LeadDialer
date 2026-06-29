package com.adyapan.leaddialer

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

data class InboxMessage(
    val id        : String  = "",
    val from      : String  = "Admin",
    val text      : String  = "",
    val timestamp : Long    = 0L,
    val read      : Boolean = false,
    val replyCount: Int     = 0,
    val fileUrl   : String  = "",
    val fileName  : String  = "",
    val fileType  : String  = ""
)

data class ChatEntry(
    val id        : String  = "",
    val from      : String  = "",
    val text      : String  = "",
    val timestamp : Long    = 0L,
    val isReply   : Boolean = false,   // true = employee reply, false = admin msg
    val fileUrl   : String  = "",
    val fileName  : String  = "",
    val fileType  : String  = ""
)

// ─────────────────────────────────────────────────────────────────────────────
class InboxFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setContent {
            MaterialTheme {
                WaInboxScreen(
                    onOpenThread = { uid, msg ->
                        openThreadDialog(uid, msg)
                    }
                )
            }
        }
    }

    // ── Thread / Chat Dialog ─────────────────────────────────────────────────
    private fun openThreadDialog(uid: String, msg: InboxMessage) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val composeView = ComposeView(requireContext()).apply {
            setContent {
                HRPortalTheme {
                    ChatThreadScreen(
                        isAdminView = false,
                        employeeUid = uid,
                        msgId = msg.id,
                        employeeName = msg.from,
                        onDismiss = { dialog.dismiss() }
                    )
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.show()
    }
}

// ── WhatsApp-style inbox conversation list (Compose) ─────────────────────────
@Composable
fun WaInboxScreen(onOpenThread: (uid: String, msg: InboxMessage) -> Unit) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var messages by remember { mutableStateOf<List<InboxMessage>>(emptyList()) }

    // Realtime listener
    DisposableEffect(uid) {
        var reg: ListenerRegistration? = null
        reg = FirebaseFirestore.getInstance()
            .collection("messages")
            .document(uid)
            .collection("inbox")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                messages = snap.documents.mapNotNull { doc ->
                    InboxMessage(
                        id         = doc.id,
                        from       = doc.getString("from")      ?: "Admin",
                        text       = doc.getString("text")      ?: "",
                        timestamp  = doc.getLong("timestamp")   ?: 0L,
                        read       = doc.getBoolean("read")     ?: false,
                        replyCount = (doc.getLong("replyCount") ?: 0L).toInt(),
                        fileUrl    = doc.getString("fileUrl")   ?: "",
                        fileName   = doc.getString("fileName")  ?: "",
                        fileType   = doc.getString("fileType")  ?: ""
                    )
                }
            }
        onDispose { reg?.remove() }
    }

    // WhatsApp colours
    val waGreen      = Color(0xFF075E54)
    val waGreenLight = Color(0xFF25D366)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Surface(color = waGreen, shadowElevation = 3.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Messages",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                val unreadCount = messages.count { !it.read }
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .background(waGreenLight, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "$unreadCount",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ── Conversation list / empty state ────────────────────────────────
        if (messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(waGreen.copy(alpha = 0.08f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, tint = waGreen, modifier = Modifier.size(36.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No messages yet", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color(0xFF2C2C2C))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Admin messages will appear here", color = Color.Gray, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(messages) { msg ->
                    WaConversationTile(
                        msg = msg,
                        uid = uid,
                        waGreen = waGreen,
                        waGreenLight = waGreenLight,
                        onClick = { onOpenThread(uid, msg) }
                    )
                    Divider(thickness = 0.5.dp, color = Color(0xFFE0E0E0), modifier = Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

// ── Single conversation tile (like WhatsApp chat row) ────────────────────────
@Composable
fun WaConversationTile(
    msg: InboxMessage,
    uid: String,
    waGreen: Color,
    waGreenLight: Color,
    onClick: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val initials = msg.from.take(1).uppercase()

    // Avatar colour based on name
    val avatarColors = listOf(
        Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350),
        Color(0xFFAB47BC), Color(0xFF42A5F5), Color(0xFFFF7043)
    )
    val avatarColor = if (msg.from.isNotEmpty()) avatarColors[msg.from[0].code % avatarColors.size] else avatarColors[0]

    val timeStr = remember(msg.timestamp) { formatWaTime(msg.timestamp) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!msg.read) {
                    db.collection("messages").document(uid)
                        .collection("inbox").document(msg.id)
                        .update("read", true)
                }
                onClick()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(avatarColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        msg.from,
                        fontWeight = if (!msg.read) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = Color(0xFF1A1A1A),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        timeStr,
                        fontSize = 11.sp,
                        color = if (!msg.read) waGreenLight else Color.Gray,
                        fontWeight = if (!msg.read) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            msg.text.isNotEmpty() -> msg.text
                            msg.fileUrl.isNotEmpty() -> "📎 ${msg.fileType.uppercase()} Attachment"
                            else -> ""
                        },
                        fontSize = 13.sp,
                        color = if (!msg.read) Color(0xFF1A1A1A) else Color.Gray,
                        fontWeight = if (!msg.read) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!msg.read) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(waGreenLight, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("1", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (msg.replyCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${msg.replyCount} ${if (msg.replyCount == 1) "reply" else "replies"}",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

private fun formatWaTime(ts: Long): String {
    if (ts == 0L) return ""
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000      -> "now"
        diff < 3_600_000   -> "${diff / 60_000}m"
        diff < 86_400_000  -> SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(ts))
        diff < 604_800_000 -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(ts))
        else               -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ts))
    }
}

// ── Legacy adapters kept for any XML layouts still referencing them ───────────
class MessageAdapter(
    private val list   : List<InboxMessage>,
    private val onRead : (InboxMessage) -> Unit,
    private val onReply: (InboxMessage) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<MessageAdapter.VH>() {

    inner class VH(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val tvFrom     : TextView = view.findViewById(R.id.tvMsgFrom)
        val tvTime     : TextView = view.findViewById(R.id.tvMsgTime)
        val tvText     : TextView = view.findViewById(R.id.tvMsgText)
        val dotUnread  : View     = view.findViewById(R.id.viewUnreadDot)
        val tvReplyCnt : TextView = view.findViewById(R.id.tvReplyCount)
        val btnReply   : com.google.android.material.button.MaterialButton = view.findViewById(R.id.btnReply)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false))

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = list[position]
        holder.tvFrom.text = msg.from
        holder.tvText.text = if (msg.text.isNotEmpty()) msg.text
                             else if (msg.fileUrl.isNotEmpty()) "[${msg.fileType.uppercase()} Attachment]"
                             else ""
        holder.tvTime.text = formatWaTime(msg.timestamp)
        holder.dotUnread.visibility = if (msg.read) View.GONE else View.VISIBLE
        holder.tvReplyCnt.text = if (msg.replyCount == 0) "No replies yet"
                                 else "${msg.replyCount} ${if (msg.replyCount == 1) "reply" else "replies"}"
        holder.itemView.setOnClickListener { onRead(msg) }
        holder.btnReply.setOnClickListener { onReply(msg) }
    }
}

class ChatBubbleAdapter(
    private val list: List<ChatEntry>
) : androidx.recyclerview.widget.RecyclerView.Adapter<ChatBubbleAdapter.VH>() {

    inner class VH(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val layoutAdmin  : ViewGroup = view.findViewById(R.id.layoutAdminBubble)
        val layoutReply  : ViewGroup = view.findViewById(R.id.layoutReplyBubble)
        val tvBubbleFrom : TextView  = view.findViewById(R.id.tvBubbleFrom)
        val tvBubbleText : TextView  = view.findViewById(R.id.tvBubbleText)
        val tvBubbleTime : TextView  = view.findViewById(R.id.tvBubbleTime)
        val tvReplyFrom  : TextView  = view.findViewById(R.id.tvReplyFrom)
        val tvReplyText  : TextView  = view.findViewById(R.id.tvReplyText)
        val tvReplyTime  : TextView  = view.findViewById(R.id.tvReplyTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_chat_bubble, parent, false))

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = list[position]
        if (entry.isReply) {
            holder.layoutAdmin.visibility = View.GONE
            holder.layoutReply.visibility = View.VISIBLE
            holder.tvReplyFrom.text = entry.from
            holder.tvReplyText.text = entry.text
            holder.tvReplyTime.text = formatWaTime(entry.timestamp)
        } else {
            holder.layoutReply.visibility = View.GONE
            holder.layoutAdmin.visibility = View.VISIBLE
            holder.tvBubbleFrom.text = entry.from
            holder.tvBubbleText.text = entry.text
            holder.tvBubbleTime.text = formatWaTime(entry.timestamp)
        }
    }
}
