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

// ── Data model for an admin-visible employee reply ────────────────────────────
data class AdminReplyItem(
    val id           : String = "",
    val employeeUid  : String = "",
    val employeeName : String = "",
    val msgId        : String = "",
    val originalMsg  : String = "",
    val replyText    : String = "",
    val timestamp    : Long   = 0L,
    val read         : Boolean = false
)

class AdminInboxFragment : Fragment() {

    private var listener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_admin_inbox, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv          = view.findViewById<RecyclerView>(R.id.rvAdminReplies)
        val layoutEmpty = view.findViewById<View>(R.id.layoutAdminInboxEmpty)
        val tvBadge     = view.findViewById<TextView>(R.id.tvAdminUnreadBadge)
        val tvTotal     = view.findViewById<TextView>(R.id.tvTotalReplies)
        val tvUnread    = view.findViewById<TextView>(R.id.tvUnreadReplies)

        rv.layoutManager = LinearLayoutManager(requireContext())

        val db = FirebaseFirestore.getInstance()

        listener = db.collection("adminReplies")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener

                val replies = snap.documents.mapNotNull { doc ->
                    AdminReplyItem(
                        id           = doc.id,
                        employeeUid  = doc.getString("employeeUid")  ?: "",
                        employeeName = doc.getString("employeeName") ?: "Employee",
                        msgId        = doc.getString("msgId")        ?: "",
                        originalMsg  = doc.getString("originalMsg")  ?: "",
                        replyText    = doc.getString("replyText")    ?: "",
                        timestamp    = doc.getLong("timestamp")      ?: 0L,
                        read         = doc.getBoolean("read")        ?: false
                    )
                }

                val unreadCount = replies.count { !it.read }

                // Update stats
                tvTotal.text  = replies.size.toString()
                tvUnread.text = unreadCount.toString()

                // Badge on tab
                tvBadge.visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
                if (unreadCount > 0) tvBadge.text = unreadCount.toString()

                if (replies.isEmpty()) {
                    layoutEmpty.visibility = View.VISIBLE
                    rv.visibility          = View.GONE
                } else {
                    layoutEmpty.visibility = View.GONE
                    rv.visibility          = View.VISIBLE
                }

                rv.adapter = AdminReplyAdapter(
                    list = replies,
                    onViewThread = { item ->
                        // Mark as read in Firestore
                        if (!item.read) {
                            db.collection("adminReplies").document(item.id)
                                .update("read", true)
                        }
                        // Open full thread dialog
                        openAdminThreadDialog(item, db)
                    }
                )
            }
    }

    // ── Full conversation thread dialog ───────────────────────────────────────
    private fun openAdminThreadDialog(item: AdminReplyItem, db: FirebaseFirestore) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_message_thread)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.white)

        val tvTitle    = dialog.findViewById<TextView>(R.id.tvThreadTitle)
        val tvSubtitle = dialog.findViewById<TextView>(R.id.tvThreadSubtitle)
        val btnClose   = dialog.findViewById<TextView>(R.id.btnCloseThread)
        val rvThread   = dialog.findViewById<RecyclerView>(R.id.rvThread)
        val etReply    = dialog.findViewById<TextInputEditText>(R.id.etReply)
        val btnSend    = dialog.findViewById<android.widget.Button>(R.id.btnSendReply)

        tvTitle.text    = "💬 ${item.employeeName}"
        tvSubtitle.text = "Conversation thread · Admin view"

        val lm = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        rvThread.layoutManager = lm

        // Admin's original message as first bubble
        val adminName = FirebaseAuth.getInstance().currentUser?.email
            ?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "Admin"

        val adminEntry = ChatEntry(
            id        = item.msgId,
            from      = adminName,
            text      = item.originalMsg,
            timestamp = 0L,   // Will be overwritten from Firestore if needed
            isReply   = false
        )

        // Fetch original message timestamp from Firestore for correct ordering
        var threadListener: ListenerRegistration? = null

        // Listen to replies sub-collection under employee's inbox
        threadListener = db.collection("messages").document(item.employeeUid)
            .collection("inbox").document(item.msgId)
            .collection("replies")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                val replies = snap?.documents?.mapNotNull { doc ->
                    ChatEntry(
                        id        = doc.id,
                        from      = doc.getString("from") ?: item.employeeName,
                        text      = doc.getString("text") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        isReply   = doc.getBoolean("isReply") ?: true
                    )
                } ?: emptyList()

                // Fetch admin's original message timestamp
                db.collection("messages").document(item.employeeUid)
                    .collection("inbox").document(item.msgId)
                    .get()
                    .addOnSuccessListener { doc ->
                        val adminTs = doc.getLong("timestamp") ?: 0L
                        val adminWithTs = adminEntry.copy(timestamp = adminTs)
                        val combined = (listOf(adminWithTs) + replies).sortedBy { it.timestamp }
                        rvThread.adapter = ChatBubbleAdapter(combined)
                        rvThread.scrollToPosition(combined.size - 1)
                    }
                    .addOnFailureListener {
                        // Fallback without correct timestamp
                        val combined = (listOf(adminEntry) + replies).sortedBy { it.timestamp }
                        rvThread.adapter = ChatBubbleAdapter(combined)
                        rvThread.scrollToPosition(combined.size - 1)
                    }
            }

        // Admin sends a reply back to employee (new message in employee inbox)
        btnSend.setOnClickListener {
            val text = etReply.text?.toString()?.trim() ?: ""
            if (text.isBlank()) {
                etReply.error = "Reply cannot be empty"
                return@setOnClickListener
            }

            val replyData = hashMapOf(
                "from"      to adminName,
                "text"      to text,
                "timestamp" to System.currentTimeMillis(),
                "isReply"   to false   // Admin's message — shown on left
            )

            // Write to employee's inbox as a new message so they see it
            val newMsg = hashMapOf(
                "from"       to adminName,
                "text"       to text,
                "timestamp"  to System.currentTimeMillis(),
                "read"       to false,
                "replyCount" to 0
            )

            db.collection("messages")
                .document(item.employeeUid)
                .collection("inbox")
                .add(newMsg)

                .addOnSuccessListener {

                    etReply.setText("")

                    Toast.makeText(
                        requireContext(),
                        "✅ Reply sent to ${item.employeeName}",
                        Toast.LENGTH_SHORT
                    ).show()

                    // ─────────────────────────────
                    // GET EMPLOYEE FCM TOKEN
                    // ─────────────────────────────

                    db.collection("users")
                        .document(item.employeeUid)
                        .get()

                        .addOnSuccessListener { document ->

                            val token =
                                document.getString("fcmToken")

                            if (!token.isNullOrEmpty()) {

                                // ─────────────────────
                                // SEND NOTIFICATION
                                // ─────────────────────

                                GasNotificationSender
                                    .sendNotification(

                                        requireContext(),

                                        token,

                                        "📩 New Message",

                                        text
                                    )
                            }
                        }
                }

                .addOnFailureListener { e ->

                    Toast.makeText(
                        requireContext(),
                        "❌ ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }

        btnClose.setOnClickListener {
            threadListener?.remove()
            dialog.dismiss()
        }
        dialog.setOnDismissListener { threadListener?.remove() }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
    }
}

// ── Adapter for admin reply list ──────────────────────────────────────────────
class AdminReplyAdapter(
    private val list        : List<AdminReplyItem>,
    private val onViewThread: (AdminReplyItem) -> Unit
) : RecyclerView.Adapter<AdminReplyAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvAvatar      : TextView      = view.findViewById(R.id.tvReplyAvatar)
        val tvName        : TextView      = view.findViewById(R.id.tvReplyEmployeeName)
        val tvTime        : TextView      = view.findViewById(R.id.tvReplyTime)
        val tvOriginal    : TextView      = view.findViewById(R.id.tvReplyOriginalMsg)
        val tvReply       : TextView      = view.findViewById(R.id.tvReplyText)
        val dotUnread     : View          = view.findViewById(R.id.viewAdminUnreadDot)
        val btnViewThread : MaterialButton= view.findViewById(R.id.btnViewThread)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_admin_reply, parent, false))

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]

        // Avatar: first letter of employee name
        val initial = item.employeeName.firstOrNull()?.uppercaseChar()?.toString() ?: "E"
        holder.tvAvatar.text   = initial
        holder.tvName.text     = item.employeeName
        holder.tvTime.text     = formatTime(item.timestamp)
        holder.tvOriginal.text = "↩ ${item.originalMsg}"
        holder.tvReply.text    = item.replyText
        holder.dotUnread.visibility = if (item.read) View.GONE else View.VISIBLE

        // Slightly highlight unread
        holder.itemView.alpha = if (item.read) 0.85f else 1.0f

        holder.btnViewThread.setOnClickListener { onViewThread(item) }
        holder.itemView.setOnClickListener { onViewThread(item) }
    }

    private fun formatTime(ts: Long): String {
        if (ts == 0L) return ""
        val diff = System.currentTimeMillis() - ts
        return when {
            diff < 60_000      -> "Just now"
            diff < 3_600_000   -> "${diff / 60_000}m ago"
            diff < 86_400_000  -> "${diff / 3_600_000}h ago"
            else               -> SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(ts))
        }
    }
}
