package com.adyapan.leaddialer

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
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

    private var listener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_inbox, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv          = view.findViewById<RecyclerView>(R.id.rvMessages)
        val layoutEmpty = view.findViewById<View>(R.id.layoutEmpty)
        val tvUnread: TextView? = view.findViewById(R.id.tvUnreadBadge)

        rv.layoutManager = LinearLayoutManager(requireContext()).apply {
            reverseLayout = true
            stackFromEnd  = true
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        listener = FirebaseFirestore.getInstance()
            .collection("messages")
            .document(uid)
            .collection("inbox")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener

                val messages = snap.documents.mapNotNull { doc ->
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

                val unreadCount = messages.count { !it.read }

                if (messages.isEmpty()) {
                    layoutEmpty.visibility = View.VISIBLE
                    rv.visibility          = View.GONE
                } else {
                    layoutEmpty.visibility = View.GONE
                    rv.visibility          = View.VISIBLE
                }

                tvUnread?.visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
                if (unreadCount > 0) tvUnread?.text = unreadCount.toString()

                rv.adapter = MessageAdapter(
                    list = messages,
                    onRead = { msg ->
                        if (!msg.read) {
                            FirebaseFirestore.getInstance()
                                .collection("messages").document(uid)
                                .collection("inbox").document(msg.id)
                                .update("read", true)
                        }
                    },
                    onReply = { msg ->
                        // Mark read + open thread
                        if (!msg.read) {
                            FirebaseFirestore.getInstance()
                                .collection("messages").document(uid)
                                .collection("inbox").document(msg.id)
                                .update("read", true)
                        }
                        openThreadDialog(uid, msg)
                    }
                )
            }
    }

    // ── Thread / Chat Dialog ─────────────────────────────────────────────────
    private fun openThreadDialog(uid: String, msg: InboxMessage) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext()).apply {
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

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
    }
}

// ── MessageAdapter (inbox list) ───────────────────────────────────────────────
class MessageAdapter(
    private val list   : List<InboxMessage>,
    private val onRead : (InboxMessage) -> Unit,
    private val onReply: (InboxMessage) -> Unit
) : RecyclerView.Adapter<MessageAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvFrom      : TextView      = view.findViewById(R.id.tvMsgFrom)
        val tvTime      : TextView      = view.findViewById(R.id.tvMsgTime)
        val tvText      : TextView      = view.findViewById(R.id.tvMsgText)
        val dotUnread   : View          = view.findViewById(R.id.viewUnreadDot)
        val tvReplyCnt  : TextView      = view.findViewById(R.id.tvReplyCount)
        val btnReply    : MaterialButton= view.findViewById(R.id.btnReply)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false))

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = list[position]
        holder.tvFrom.text = "${msg.from}"
        holder.tvText.text = if (msg.text.isNotEmpty()) msg.text
                             else if (msg.fileUrl.isNotEmpty()) "[${msg.fileType.uppercase()} Attachment]"
                             else ""
        holder.tvTime.text = formatTime(msg.timestamp)
        holder.dotUnread.visibility = if (msg.read) View.GONE else View.VISIBLE
        holder.tvReplyCnt.text = if (msg.replyCount == 0) "No replies yet"
                                 else "${msg.replyCount} ${if (msg.replyCount == 1) "reply" else "replies"}"

        holder.itemView.setBackgroundColor(
            if (msg.read) android.graphics.Color.WHITE
            else android.graphics.Color.parseColor("#FFF8F4")
        )

        holder.itemView.setOnClickListener { onRead(msg) }
        holder.btnReply.setOnClickListener { onReply(msg) }
    }

    private fun formatTime(ts: Long): String {
        if (ts == 0L) return ""
        val diff = System.currentTimeMillis() - ts
        return when {
            diff < 60_000      -> "Just now"
            diff < 3_600_000   -> "${diff / 60_000}m ago"
            diff < 86_400_000  -> "${diff / 3_600_000}h ago"
            else               -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(ts))
        }
    }
}

// ── ChatBubbleAdapter (thread dialog) ────────────────────────────────────────
class ChatBubbleAdapter(
    private val list: List<ChatEntry>
) : RecyclerView.Adapter<ChatBubbleAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
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
            holder.tvReplyTime.text = formatTime(entry.timestamp)
        } else {
            holder.layoutReply.visibility = View.GONE
            holder.layoutAdmin.visibility = View.VISIBLE
            holder.tvBubbleFrom.text = entry.from
            holder.tvBubbleText.text = entry.text
            holder.tvBubbleTime.text = formatTime(entry.timestamp)
        }
    }

    private fun formatTime(ts: Long): String {
        if (ts == 0L) return ""
        val diff = System.currentTimeMillis() - ts
        return when {
            diff < 60_000     -> "Just now"
            diff < 3_600_000  -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            else              -> SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(ts))
        }
    }
}
