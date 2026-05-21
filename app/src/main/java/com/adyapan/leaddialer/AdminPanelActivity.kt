package com.adyapan.leaddialer

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminPanelActivity : AppCompatActivity() {

    private val viewModel: AdminViewModel by viewModels()

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) handleAdminUpload(uri)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }


        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = android.graphics.Color.parseColor("#FF6A00")
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        setContentView(R.layout.activity_admin_panel)

        findViewById<android.widget.LinearLayout>(R.id.btnAdminLogoutWrapper).setOnClickListener {
            // RTDB listeners pehle remove karo — permission denied prevent
            viewModel.clearAllListeners()

            // Show sync progress before logout to prevent data loss
            val progressDialog = AlertDialog.Builder(this)
                .setTitle("⏳ Syncing Data")
                .setMessage("Uploading pending records to cloud before logout...")
                .setCancelable(false)
                .create()
            progressDialog.show()

            LoginPage.logout(this) {
                progressDialog.dismiss()
                startActivity(Intent(this, LoginPage::class.java))
                finishAffinity()
            }
        }

        // ── FAB: upload leads (only on Employees tab) ─────────────────
        val fab = findViewById<FloatingActionButton>(R.id.fabAdminUpload)
        fab.setOnClickListener { filePickerLauncher.launch("*/*") }

        // ── Bottom Navigation setup ────────────────────────────────────
        val bottomNav = findViewById<BottomNavigationView>(R.id.adminBottomNav)

        // Initial fragment
        if (savedInstanceState == null) {
            loadFragment(AdminEmployeesFragment())
            fab.show()
            bottomNav.selectedItemId = R.id.adminNavEmployees
        }

        // Real-time unread badge on Messages item
        FirebaseFirestore.getInstance()
            .collection("adminReplies")
            .whereEqualTo("read", false)
            .addSnapshotListener { snap, _ ->
                val unread = snap?.size() ?: 0
                val msgItem = bottomNav.menu.findItem(R.id.adminNavMessages)
                msgItem?.title = if (unread > 0) "Messages ($unread)" else "Messages"
            }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.adminNavEmployees -> { loadFragment(AdminEmployeesFragment()); fab.show() }
                R.id.adminNavLeaves    -> { loadFragment(AdminLeavesFragment());    fab.hide() }
                R.id.adminNavAttendance-> { loadFragment(AdminAttendanceFragment());fab.hide() }
                R.id.adminNavMessages  -> { loadFragment(AdminInboxFragment());     fab.hide() }
                R.id.adminNavSales     -> { loadFragment(AdminSalesFragment());     fab.hide() }
            }
            true
        }

        viewModel.loadAllEmployeeData()

        // ── Thought of the Day button (TextView pill in header) ────────
        findViewById<TextView>(R.id.btnThoughtOfDay).setOnClickListener {
            showThoughtOfDayDialog()
        }
    }

    private fun loadFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.adminFragmentContainer, fragment)
            .commit()
    }

    private fun handleAdminUpload(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val leads = ExcelUtils.parseLeads(this@AdminPanelActivity, uri)
            withContext(Dispatchers.Main) {
                if (leads.isEmpty()) {
                    Toast.makeText(this@AdminPanelActivity, "No valid leads found in Excel", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                val currentEmployees = viewModel.employees.value
                if (!currentEmployees.isNullOrEmpty()) {
                    showEmployeeSelectionDialog(leads, currentEmployees)
                } else {
                    Toast.makeText(this@AdminPanelActivity, "Loading employees, please wait...", Toast.LENGTH_SHORT).show()
                    viewModel.employees.observe(this@AdminPanelActivity) { summaries ->
                        if (!summaries.isNullOrEmpty()) {
                            viewModel.employees.removeObservers(this@AdminPanelActivity)
                            showEmployeeSelectionDialog(leads, summaries)
                        }
                    }
                }
            }
        }
    }

    private fun showEmployeeSelectionDialog(leads: List<Lead>, allSummaries: List<EmployeeSummary>) {
        val dialog = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_select_employee, null)
        dialog.setContentView(view)

        val listView  = view.findViewById<ListView>(R.id.lvEmployees)
        val tvTitle   = view.findViewById<TextView>(R.id.tvTitle)
        val etSearch  = view.findViewById<android.widget.EditText>(R.id.etSearchEmployee)

        tvTitle.text = "Assign ${leads.size} leads to:"

        val currentSummaries = mutableListOf<EmployeeSummary>().apply { addAll(allSummaries) }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            currentSummaries.map { it.employeeName }.toMutableList()
        )
        listView.adapter = adapter

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase()
                currentSummaries.clear()
                currentSummaries.addAll(allSummaries.filter { it.employeeName.lowercase().contains(query) })
                adapter.clear()
                adapter.addAll(currentSummaries.map { it.employeeName })
                adapter.notifyDataSetChanged()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedEmp = currentSummaries[position]
            assignLeadsToEmployee(selectedEmp.userId, leads)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun assignLeadsToEmployee(userId: String, leads: List<Lead>) {
        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage("Uploading ${leads.size} leads...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch {
            try {
                val empName = withContext(Dispatchers.IO) {
                    FirestoreSource.getEmployeeDisplayName(userId)
                }

                val taggedLeads = leads.map { it.copy(calledBy = "") }

                val success = withContext(Dispatchers.IO) {
                    FirestoreSource.saveLeadsBatch(taggedLeads, targetUserId = userId)
                }

                progressDialog.dismiss()

                if (success) {
                    Toast.makeText(this@AdminPanelActivity, "✅ ${leads.size} leads assigned to $empName", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@AdminPanelActivity, "❌ Upload failed. Check internet connection.", Toast.LENGTH_LONG).show()
                }
                viewModel.loadAllEmployeeData()

            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(this@AdminPanelActivity, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Thought of the Day Editor ─────────────────────────────────────────────
    private fun showThoughtOfDayDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_thought_of_day, null)
        val etThought  = dialogView.findViewById<EditText>(R.id.etThought)
        val etAuthor   = dialogView.findViewById<EditText>(R.id.etThoughtAuthor)
        val tvCurrent  = dialogView.findViewById<TextView>(R.id.tvCurrentThought)

        // Load current thought
        FirebaseFirestore.getInstance()
            .collection("settings")
            .document("thoughtOfTheDay")
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val current = doc.getString("text") ?: ""
                    val author  = doc.getString("author") ?: ""
                    tvCurrent.text = "Current: \"$current\" — $author"
                    tvCurrent.visibility = android.view.View.VISIBLE
                    etThought.setText(current)
                    etAuthor.setText(author)
                }
            }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
            .also { dialog ->
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                dialogView.findViewById<Button>(R.id.btnPublishThought).setOnClickListener {
                    val text   = etThought.text.toString().trim()
                    val author = etAuthor.text.toString().trim().ifBlank { "Adyapan Team" }

                    if (text.isBlank()) {
                        etThought.error = "Please enter a thought"
                        return@setOnClickListener
                    }

                    FirebaseFirestore.getInstance()
                        .collection("settings")
                        .document("thoughtOfTheDay")
                        .set(mapOf("text" to text, "author" to author))
                        .addOnSuccessListener {
                            Toast.makeText(this, "✅ Thought published to all employees!", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "❌ Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                dialogView.findViewById<Button>(R.id.btnCancelThought).setOnClickListener {
                    dialog.dismiss()
                }
                dialog.show()
            }
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }
}