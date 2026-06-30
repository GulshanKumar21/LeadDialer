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
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

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
                        read         = doc.getBoolean("read")        ?: false,
                        fileUrl      = doc.getString("fileUrl")      ?: "",
                        fileType     = doc.getString("fileType")     ?: ""
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
        val composeView = androidx.compose.ui.platform.ComposeView(this).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                HRPortalTheme {
                    ChatThreadScreen(
                        isAdminView = true,
                        employeeUid = item.employeeUid,
                        msgId = item.msgId,
                        employeeName = item.employeeName,
                        onDismiss = { dialog.dismiss() }
                    )
                }
            }
        }
        // Fix ViewTreeLifecycleOwner not found crash in custom Dialog
        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeViewModelStoreOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)

        dialog.setContentView(composeView)

        dialog.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
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
    val read         : Boolean,
    val fileUrl      : String = "",
    val fileType     : String = ""
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
        holder.tvReply.text = if (item.replyText.isNotEmpty()) item.replyText
                              else if (item.fileUrl.isNotEmpty()) "[${item.fileType.uppercase()} Attachment]"
                              else ""

        val action = View.OnClickListener { onViewThread(item) }
        holder.btnViewThread.setOnClickListener(action)
        holder.itemView.setOnClickListener(action)
    }
}
