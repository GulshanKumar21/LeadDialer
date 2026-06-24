package com.adyapan.leaddialer

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class AdminEmployeeInboxActivity : AppCompatActivity() {

    private lateinit var tvEmpName       : TextView
    private lateinit var tvAvatar        : TextView
    private lateinit var btnBack         : ImageButton
    private lateinit var tvTotal         : TextView
    private lateinit var tvUnread        : TextView
    private lateinit var rvReplies       : RecyclerView
    private lateinit var layoutEmpty     : View
    private lateinit var progressBar     : ProgressBar

    private var uid  = ""
    private var name = ""

    private var listener: ListenerRegistration? = null
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_employee_inbox)

        uid  = intent.getStringExtra("uid") ?: ""
        name = intent.getStringExtra("name") ?: "Employee"

        tvEmpName   = findViewById(R.id.tvAdminInboxEmpName)
        tvAvatar    = findViewById(R.id.tvAdminInboxEmpAvatar)
        btnBack     = findViewById(R.id.btnInboxBack)
        tvTotal     = findViewById(R.id.tvTotalReplies)
        tvUnread    = findViewById(R.id.tvUnreadReplies)
        rvReplies   = findViewById(R.id.rvAdminEmployeeReplies)
        layoutEmpty = findViewById(R.id.layoutAdminInboxEmpty)
        progressBar = findViewById(R.id.progressAdminEmployeeInbox)

        tvEmpName.text = name
        tvAvatar.text  = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        btnBack.setOnClickListener { finish() }

        rvReplies.layoutManager = LinearLayoutManager(this)

        progressBar.visibility = View.VISIBLE
        listener = db.collection("adminReplies")
            .whereEqualTo("employeeUid", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap: com.google.firebase.firestore.QuerySnapshot?, error: com.google.firebase.firestore.FirebaseFirestoreException? ->
                progressBar.visibility = View.GONE
                if (error != null || snap == null) return@addSnapshotListener

                val replies = snap.documents.mapNotNull { doc: com.google.firebase.firestore.DocumentSnapshot ->
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

                if (replies.isEmpty()) {
                    layoutEmpty.visibility = View.VISIBLE
                    rvReplies.visibility   = View.GONE
                } else {
                    layoutEmpty.visibility = View.GONE
                    rvReplies.visibility   = View.VISIBLE
                }

                rvReplies.adapter = AdminReplyAdapter(
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
        val dialog = Dialog(this)
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

        tvTitle.text    = "${item.employeeName}"
        tvSubtitle.text = "Conversation thread · Admin view"

        val lm = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvThread.layoutManager = lm

        // Admin's original message as first bubble
        val adminName = FirebaseAuth.getInstance().currentUser?.email
            ?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "Admin"

        val adminEntry = ChatEntry(
            id        = item.msgId,
            from      = adminName,
            text      = item.originalMsg,
            timestamp = 0L,
            isReply   = false
        )

        var threadListener: ListenerRegistration? = null

        // Listen to replies sub-collection under employee's inbox
        threadListener = db.collection("messages").document(item.employeeUid)
            .collection("inbox").document(item.msgId)
            .collection("replies")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snap: com.google.firebase.firestore.QuerySnapshot?, _ ->
                val replies = snap?.documents?.mapNotNull { doc: com.google.firebase.firestore.DocumentSnapshot ->
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
                        val combined = (listOf(adminEntry) + replies).sortedBy { it.timestamp }
                        rvThread.adapter = ChatBubbleAdapter(combined)
                        rvThread.scrollToPosition(combined.size - 1)
                    }
            }

        // Admin sends a reply back to employee
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
                "isReply"   to false
            )

            // Write to employee's inbox as a new message
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
                        this,
                        "Reply sent to ${item.employeeName}",
                        Toast.LENGTH_SHORT
                    ).show()

                    // FCM Token notification push
                    db.collection("users")
                        .document(item.employeeUid)
                        .get()
                        .addOnSuccessListener { document ->
                            val token = document.getString("fcmToken")
                            if (!token.isNullOrEmpty()) {
                                GasNotificationSender.sendNotification(
                                    this,
                                    token,
                                    "New Message",
                                    text
                                )
                            }
                        }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        this,
                        "${e.message}",
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

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
    }
}

// ── AdminReplyItem model ──────────────────────────────────────────────────────
data class AdminReplyItem(
    val id           : String,
    val employeeUid  : String,
    val employeeName : String,
    val msgId        : String,
    val originalMsg  : String,
    val replyText    : String,
    val timestamp    : Long,
    val read         : Boolean
)

// ── AdminReplyAdapter recycler adapter ─────────────────────────────────────────
class AdminReplyAdapter(
    private val list: List<AdminReplyItem>,
    private val onViewThread: (AdminReplyItem) -> Unit
) : RecyclerView.Adapter<AdminReplyAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvAvatar      : TextView = v.findViewById(R.id.tvReplyAvatar)
        val tvName        : TextView = v.findViewById(R.id.tvReplyEmployeeName)
        val tvTime        : TextView = v.findViewById(R.id.tvReplyTime)
        val viewUnreadDot : View     = v.findViewById(R.id.viewAdminUnreadDot)
        val tvOriginal    : TextView = v.findViewById(R.id.tvReplyOriginalMsg)
        val tvReply       : TextView = v.findViewById(R.id.tvReplyText)
        val btnViewThread : View     = v.findViewById(R.id.btnViewThread)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_reply, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = list[position]
        holder.tvAvatar.text = item.employeeName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.tvName.text = item.employeeName
        
        // Format time
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy hh:mm a", java.util.Locale.getDefault())
        holder.tvTime.text = if (item.timestamp > 0L) sdf.format(java.util.Date(item.timestamp)) else "N/A"
        
        holder.viewUnreadDot.visibility = if (item.read) View.GONE else View.VISIBLE
        holder.tvOriginal.text = item.originalMsg
        holder.tvReply.text = item.replyText

        val action = View.OnClickListener { onViewThread(item) }
        holder.btnViewThread.setOnClickListener(action)
        holder.itemView.setOnClickListener(action)
    }
}
