package com.adyapan.leaddialer

import android.content.Intent
import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
class AdminEmployeesFragment : Fragment() {

    private lateinit var viewModel: AdminViewModel
    private lateinit var adapter: AdminEmployeeAdapter

    private lateinit var rvEmployees    : RecyclerView
    private lateinit var rvDayWiseStats : RecyclerView
    private lateinit var progress       : ProgressBar
    private lateinit var tvTotalEmp     : TextView
    private lateinit var tvTotalLeads   : TextView
    private lateinit var tvTotalConn    : TextView
    private lateinit var tvTotalSales   : TextView
    private lateinit var etSearch       : EditText

    private lateinit var daySummaryAdapter: AdminDaySummaryAdapter

    private var allSummaries: List<EmployeeSummary> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_admin_employees, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(requireActivity())[AdminViewModel::class.java]

        rvEmployees  = view.findViewById(R.id.rvEmployees)
        rvDayWiseStats = view.findViewById(R.id.rvDayWiseStats)
        progress     = view.findViewById(R.id.progressAdmin)
        tvTotalEmp   = view.findViewById(R.id.tvTotalEmployees)
        tvTotalLeads = view.findViewById(R.id.tvTotalAllLeads)
        tvTotalConn  = view.findViewById(R.id.tvTotalConnected)
        tvTotalSales = view.findViewById(R.id.tvTotalSales)
        etSearch     = view.findViewById(R.id.etSearchEmployee)

        val tvManageTlsAdmin = view.findViewById<TextView>(R.id.tvManageTlsAdmin)
        tvManageTlsAdmin.setOnClickListener {
            showManageTlsDialog()
        }

        adapter = AdminEmployeeAdapter(
            onClick = { emp ->
                val fragment = AdminLeadsFragment.newInstance(emp)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.adminFragmentContainer, fragment)
                    .addToBackStack(null)
                    .commit()
            },
            onEditClick = { emp ->
                showManageProfileDialog(emp)
            },
            onMessageClick = { emp ->
                showSendMessageDialog(emp)
            },
            onSetTargetClick = { emp ->
                showSetTargetDialog(emp)
            },
            onAttendanceClick = { emp ->
                val intent = Intent(requireContext(), AdminEmployeeAttendanceActivity::class.java).apply {
                    putExtra("uid", emp.userId)
                    putExtra("name", emp.employeeName)
                }
                startActivity(intent)
            },
            onTlAssign = { emp, tl ->
                if (tl == null) {
                    viewModel.removeTlAssignment(emp.userId)
                    Toast.makeText(requireContext(), "TL removed: ${emp.employeeName}", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.saveTlAssignment(emp.userId, tl)
                    Toast.makeText(requireContext(), "${emp.employeeName} → ${tl.name}", Toast.LENGTH_SHORT).show()
                    lifecycleScope.launch {
                        Toast.makeText(requireContext(), "Syncing data to ${tl.name}...", Toast.LENGTH_SHORT).show()
                        val success = SheetsSync.adminSyncEmployeeToTl(requireContext(), emp.userId, emp.employeeName, tl.id)
                        if (success) {
                            Toast.makeText(requireContext(), "Data synced!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )

        daySummaryAdapter = AdminDaySummaryAdapter()

        rvEmployees.layoutManager = LinearLayoutManager(requireContext())
        rvEmployees.adapter = adapter

        rvDayWiseStats.layoutManager = LinearLayoutManager(requireContext())
        rvDayWiseStats.adapter = daySummaryAdapter

        // ── Tab switcher logic ────────────────────────────────────────────────
        val btnTabEmployees = view.findViewById<TextView>(R.id.btnTabEmployees)
        val btnTabDayHistory = view.findViewById<TextView>(R.id.btnTabDayHistory)

        btnTabEmployees.setOnClickListener {
            btnTabEmployees.setBackgroundResource(R.drawable.bg_active_tab)
            btnTabEmployees.setTextColor(android.graphics.Color.parseColor("#3B82F6"))

            btnTabDayHistory.setBackgroundResource(R.drawable.bg_inactive_tab)
            btnTabDayHistory.setTextColor(android.graphics.Color.parseColor("#64748B"))

            rvEmployees.visibility = View.VISIBLE
            rvDayWiseStats.visibility = View.GONE
        }

        btnTabDayHistory.setOnClickListener {
            btnTabDayHistory.setBackgroundResource(R.drawable.bg_active_tab)
            btnTabDayHistory.setTextColor(android.graphics.Color.parseColor("#3B82F6"))

            btnTabEmployees.setBackgroundResource(R.drawable.bg_inactive_tab)
            btnTabEmployees.setTextColor(android.graphics.Color.parseColor("#64748B"))

            rvEmployees.visibility = View.GONE
            rvDayWiseStats.visibility = View.VISIBLE
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim()?.lowercase() ?: ""
                val filtered = if (query.isEmpty()) allSummaries
                               else allSummaries.filter {
                                   it.employeeName.lowercase().contains(query)
                               }
                adapter.submitList(filtered)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            progress.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { err ->
            err?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }
        }

        viewModel.employees.observe(viewLifecycleOwner) { summaries ->
            allSummaries = summaries
            adapter.submitList(summaries)

            tvTotalEmp.text   = summaries.size.toString()
            tvTotalLeads.text = summaries.sumOf { it.totalLeads }.toString()
            tvTotalConn.text  = summaries.sumOf { it.connected }.toString()
            tvTotalSales.text = summaries.sumOf { it.salesDone }.toString()
        }

        viewModel.dayWiseStats.observe(viewLifecycleOwner) { stats ->
            daySummaryAdapter.submitList(stats)
        }

        // Feed TL list into adapter so each card's spinner is populated
        viewModel.tlList.observe(viewLifecycleOwner) { tls ->
            adapter.setTlList(tls)
        }
    }

    private fun showManageProfileDialog(emp: EmployeeSummary) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_manage_employee_profile, null)
        dialog.setContentView(view)

        val etEmpId       = view.findViewById<EditText>(R.id.etManageEmpId)
        val etPhone       = view.findViewById<EditText>(R.id.etManagePhone)
        val etDesignation = view.findViewById<EditText>(R.id.etManageDesignation)
        val etDepartment  = view.findViewById<EditText>(R.id.etManageDepartment)
        val etManager     = view.findViewById<EditText>(R.id.etManageManager)
        val etLocation    = view.findViewById<EditText>(R.id.etManageLocation)
        val tvDateOfJoin  = view.findViewById<TextView>(R.id.tvManageDateOfJoining)
        val tvDateOfBirth = view.findViewById<TextView>(R.id.tvManageDateOfBirth)
        val spinnerTl     = view.findViewById<Spinner>(R.id.spinnerTl)
        val tvManageTls   = view.findViewById<TextView>(R.id.tvManageTls)
        val btnSave       = view.findViewById<Button>(R.id.btnSaveProfile)

        view.findViewById<TextView>(R.id.tvManageProfileTitle).text = "Manage ${emp.employeeName}"

        var selectedDoj  = ""
        var selectedDob  = ""
        var tlList       = listOf<TeamLeaderManager.TeamLeader>()
        var selectedTlId = ""  // currently assigned TL

        // ── Date of Joining picker ──────────────────────────────────────────
        tvDateOfJoin.setOnClickListener {
            val cal = Calendar.getInstance()
            val dp = DatePickerDialog(requireContext(), { _, y, m, d ->
                cal.set(y, m, d)
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                selectedDoj = sdf.format(cal.time)
                tvDateOfJoin.text = "Date of Joining: $selectedDoj"
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            dp.show()
        }

        // ── Date of Birth picker ──────────────────────────────────────────
        tvDateOfBirth.setOnClickListener {
            val cal = Calendar.getInstance()
            val dp = DatePickerDialog(requireContext(), { _, y, m, d ->
                cal.set(y, m, d)
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                selectedDob = sdf.format(cal.time)
                tvDateOfBirth.text = "Date of Birth: $selectedDob"
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            dp.show()
        }

        // ── Load TL list + current assignment ──────────────────────────────
        lifecycleScope.launch {
            // Load profile
            val existingProfile = FirestoreSource.userProfileFlow(emp.userId).firstOrNull()
            if (existingProfile != null) {
                etEmpId.setText(existingProfile.employeeId)
                etPhone.setText(existingProfile.phone)
                etDesignation.setText(existingProfile.designation)
                etDepartment.setText(existingProfile.department)
                etManager.setText(existingProfile.reportingManager)
                etLocation.setText(existingProfile.workLocation)
                if (existingProfile.dateOfJoining.isNotEmpty()) {
                    selectedDoj = existingProfile.dateOfJoining
                    tvDateOfJoin.text = "Date of Joining: $selectedDoj"
                }
                if (existingProfile.dob.isNotEmpty()) {
                    selectedDob = existingProfile.dob
                    tvDateOfBirth.text = "Date of Birth: $selectedDob"
                }
            }

            // Load TL dropdown
            tlList = TeamLeaderManager.fetchAllTeamLeaders()
            val currentTlId = TeamLeaderManager.getTlIdForUser(emp.userId)
            selectedTlId = currentTlId ?: ""

            // Build spinner items: "-- No TL --" + TL names
            val items = mutableListOf("-- No Team Leader --")
            items.addAll(tlList.map { it.name })
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, items)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerTl.adapter = adapter

            // Select current TL
            val selIdx = tlList.indexOfFirst { it.id == selectedTlId }
            spinnerTl.setSelection(if (selIdx >= 0) selIdx + 1 else 0)
        }

        // ── Manage TLs link ────────────────────────────────────────────────
        tvManageTls.setOnClickListener {
            dialog.dismiss()
            showManageTlsDialog()
        }

        // ── Save ───────────────────────────────────────────────────────────
        btnSave.setOnClickListener {
            lifecycleScope.launch {
                // Save profile
                val existingProfile = FirestoreSource.userProfileFlow(emp.userId).firstOrNull()
                val updatedProfile = UserProfile(
                    uid              = emp.userId,
                    name             = existingProfile?.name ?: emp.employeeName,
                    email            = existingProfile?.email ?: "",
                    phone            = etPhone.text.toString().trim(),
                    employeeId       = etEmpId.text.toString().trim(),
                    designation      = etDesignation.text.toString().trim(),
                    department       = etDepartment.text.toString().trim(),
                    reportingManager = etManager.text.toString().trim(),
                    workLocation     = etLocation.text.toString().trim(),
                    dateOfJoining    = selectedDoj,
                    dob              = selectedDob
                )
                val profileOk = FirestoreSource.saveUserProfile(updatedProfile)

                // Save TL assignment
                val spinPos = spinnerTl.selectedItemPosition
                if (spinPos == 0) {
                    viewModel.removeTlAssignment(emp.userId)
                } else {
                    val chosenTl = tlList.getOrNull(spinPos - 1)
                    if (chosenTl != null) {
                        viewModel.saveTlAssignment(emp.userId, chosenTl)
                        lifecycleScope.launch {
                            SheetsSync.adminSyncEmployeeToTl(requireContext(), emp.userId, emp.employeeName, chosenTl.id)
                        }
                    }
                }

                if (profileOk) {
                    Toast.makeText(requireContext(), "Profile & TL assignment saved", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "Profile save failed, try again", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    // ── Manage Team Leaders Dialog ────────────────────────────────────────────
    private fun showManageTlsDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_manage_team_leaders, null)

        val rvTls            = dialogView.findViewById<RecyclerView>(R.id.rvTeamLeaders)
        val spinnerEmployee  = dialogView.findViewById<Spinner>(R.id.spinnerTlEmployee)
        val etUserId         = dialogView.findViewById<EditText>(R.id.etTlUserId)
        val etUrl            = dialogView.findViewById<EditText>(R.id.etTlSheetUrl)
        val btnAdd           = dialogView.findViewById<Button>(R.id.btnAddTl)
        val btnClose         = dialogView.findViewById<Button>(R.id.btnCloseTlDialog)
        val tvChangeSheet    = dialogView.findViewById<TextView>(R.id.tvChangeSheetHint)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        tvChangeSheet.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Sheet Full? Here's how to change it")
                .setMessage(
                    "1⃣ Google Apps Script mein naya Spreadsheet banao.\n\n" +
                    "2⃣ Script ko wahan deploy karo (Extensions → Apps Script → Deploy → New Deployment).\n\n" +
                    "3⃣ Naya URL copy karo.\n\n" +
                    "4⃣ Yahan us TL ka Edit button dabaao, nayi URL paste karo, aur Save karo.\n\n" +
                    "Agla sync automatically naye sheet mein jayega!"
                )
                .setPositiveButton("Got it", null)
                .show()
        }

        // ── Build employee list for spinner (fresh fetch if allSummaries empty) ──
        data class EmpItem(val name: String, val userId: String)
        val empItems = mutableListOf(EmpItem("-- Select Employee --", ""))

        var editingTlId: String? = null
        var tlAdapter: TlManageAdapter? = null

        // ── TL list adapter setup ──────────────────────────────────────────────
        tlAdapter = TlManageAdapter(
            onEdit = { tl ->
                val idx = empItems.indexOfFirst { it.userId == tl.userId }
                spinnerEmployee.setSelection(if (idx >= 0) idx else 0)
                etUrl.setText(tl.sheetUrl)
                editingTlId = tl.id
                btnAdd.text = "Update Team Leader"
            },
            onDelete = { tl ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete ${tl.name}?")
                    .setMessage("Assigned employees will NOT be auto-unassigned.")
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch {
                            TeamLeaderManager.deleteTeamLeader(tl.id)
                            tlAdapter?.let { refreshTlList(rvTls, it) }
                            editingTlId = null
                            btnAdd.text = "Save Team Leader"
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onAssign = { tl -> showBulkAssignDialog(tl) }
        )
        rvTls.layoutManager = LinearLayoutManager(requireContext())
        rvTls.adapter = tlAdapter

        // ── Load employees + TL list ───────────────────────────────────────────
        lifecycleScope.launch {
            // If allSummaries not yet loaded, fetch fresh from Firestore
            val sourceList = if (allSummaries.isNotEmpty()) {
                allSummaries.map { EmpItem(it.employeeName, it.userId) }
            } else {
                try {
                    val snap = FirebaseFirestore.getInstance().collection("users").get().await()
                    val adminSnap = FirebaseFirestore.getInstance().collection("admins").get().await()
                    val adminIds = adminSnap.documents.map { it.id }.toSet()
                    snap.documents
                        .filter { !adminIds.contains(it.id) }
                        .mapNotNull { doc ->
                            val uid  = doc.id
                            val name = doc.getString("displayName")?.takeIf { it.isNotBlank() }
                                ?: doc.getString("name")?.takeIf { it.isNotBlank() }
                                ?: doc.getString("email")?.substringBefore("@")
                                ?: return@mapNotNull null
                            EmpItem(name.replaceFirstChar { it.uppercase() }, uid)
                        }.sortedBy { it.name }
                } catch (e: Exception) {
                    android.util.Log.e("TLDialog", "Employee load failed: ${e.message}")
                    emptyList()
                }
            }

            empItems.addAll(sourceList)

            val spinnerAdapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                empItems.map { it.name }
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

            activity?.runOnUiThread {
                spinnerEmployee.adapter = spinnerAdapter
            }

            // Auto-fill hidden userId when employee is selected
            spinnerEmployee.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                    etUserId.setText(empItems.getOrNull(pos)?.userId ?: "")
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }

            refreshTlList(rvTls, tlAdapter!!)
        }

        // ── Save / Update ──────────────────────────────────────────────────────
        btnAdd.setOnClickListener {
            val selectedPos = spinnerEmployee.selectedItemPosition
            val selectedEmp = empItems.getOrNull(selectedPos)
            val url         = etUrl.text.toString().trim()
            val userId      = etUserId.text.toString().trim()
            val name        = selectedEmp?.name ?: ""

            // Fix: validate by userId (not name) to handle blank-name edge case
            if (selectedPos == 0 || userId.isBlank()) {
                Toast.makeText(requireContext(), "Pehle employee select karo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (url.isBlank()) {
                Toast.makeText(requireContext(), "Google Sheet URL daalna zaroori hai", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val editId = editingTlId
                if (editId != null) {
                    TeamLeaderManager.updateTeamLeader(editId, name, url, userId)
                    Toast.makeText(requireContext(), "TL updated", Toast.LENGTH_SHORT).show()
                    editingTlId = null
                    btnAdd.text = "Save Team Leader"
                } else {
                    // New TL
                    TeamLeaderManager.addTeamLeader(name, url, userId)
                    Toast.makeText(requireContext(), "TL added: $name", Toast.LENGTH_SHORT).show()
                }
                spinnerEmployee.setSelection(0)
                etUrl.setText("")
                etUserId.setText("")
                tlAdapter?.let { refreshTlList(rvTls, it) }
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }


    // ── Bulk Assign Dialog — assign multiple employees to ONE TL ──────────────
    private fun showBulkAssignDialog(tl: TeamLeaderManager.TeamLeader) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_assign_tl_members, null)

        val tvTitle    = dialogView.findViewById<TextView>(R.id.tvAssignTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvAssignSubtitle)
        val tvCount    = dialogView.findViewById<TextView>(R.id.tvAssignCount)
        val etSearch   = dialogView.findViewById<EditText>(R.id.etSearchAssign)
        val rvEmps     = dialogView.findViewById<RecyclerView>(R.id.rvAssignEmployees)
        val btnSave    = dialogView.findViewById<Button>(R.id.btnSaveAssign)
        val btnCancel  = dialogView.findViewById<Button>(R.id.btnCancelAssign)

        tvTitle.text    = "${tl.name} — Members"
        tvSubtitle.text = "Checked = in this TL Unchecked = not in this TL"

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        lifecycleScope.launch {
            // 1. Fetch ALL employees from the ViewModel's cached list
            val empList = allSummaries

            // 2. Fetch ALL TLs (for showing current TL name in subtitle)
            val allTls   = TeamLeaderManager.fetchAllTeamLeaders()
            val tlNameMap = allTls.associate { it.id to it.name }

            // 3. For each employee, check their current TL assignment
            //    (read from RTDB users/{uid}/teamLeaderId)
            val assignItems = empList.map { emp ->
                val currentTlId = TeamLeaderManager.getTlIdForUser(emp.userId) ?: ""
                TlAssignEmployeeAdapter.AssignItem(
                    userId      = emp.userId,
                    name        = emp.employeeName,
                    currentTlId = currentTlId
                )
            }


            val alreadyInThisTl = assignItems
                .filter { it.currentTlId == tl.id }
                .map { it.userId }
                .toSet()

            // Declared as var first to allow self-reference inside onChecked lambda
            var finalAdapter: TlAssignEmployeeAdapter? = null
            finalAdapter = TlAssignEmployeeAdapter(
                tlNameMap = tlNameMap,
                onChecked = { _, _ ->
                    tvCount.text = "${finalAdapter?.getCheckedIds()?.size ?: 0} selected"
                }
            )
            val adapter = finalAdapter!! // non-null alias for use in this coroutine block
            adapter.setChecked(alreadyInThisTl)
            tvCount.text = "${alreadyInThisTl.size} selected"

            activity?.runOnUiThread {
                rvEmps.layoutManager = LinearLayoutManager(requireContext())
                rvEmps.adapter = adapter
                adapter.submitList(assignItems)
                TlAssignEmployeeAdapter.attachSearch(adapter, assignItems, etSearch)
            }

            btnSave.setOnClickListener {
                lifecycleScope.launch {
                    val checkedNow = adapter.getCheckedIds()

                    // Users to ADD to this TL (newly checked)
                    val toAdd = checkedNow - alreadyInThisTl
                    // Users to REMOVE from this TL (unchecked)
                    val toRemove = alreadyInThisTl - checkedNow

                    var successCount = 0
                    var failCount    = 0

                    // Assign new members
                    toAdd.forEach { userId ->
                        if (TeamLeaderManager.assignTlToUser(userId, tl.id)) {
                            successCount++
                            // Sync immediately
                            val empName = empList.find { it.userId == userId }?.employeeName ?: "Employee"
                            launch { SheetsSync.adminSyncEmployeeToTl(requireContext(), userId, empName, tl.id) }
                        } else {
                            failCount++
                        }
                    }

                    // Unassign removed members
                    toRemove.forEach { userId ->
                        // Only unassign if they are actually in THIS TL
                        // (don't accidentally remove someone assigned to another TL)
                        val currentTl = TeamLeaderManager.getTlIdForUser(userId)
                        if (currentTl == tl.id) {
                            if (TeamLeaderManager.unassignTlFromUser(userId)) successCount++
                            else failCount++
                        }
                    }

                    val msg = if (failCount == 0)
                        "${successCount} changes saved! New syncs will use the new sheet."
                    else
                        "$successCount saved, $failCount failed"

                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    }
                }
            }

            btnCancel.setOnClickListener { dialog.dismiss() }
            dialog.show()
        }
    }

    private suspend fun refreshTlList(rv: RecyclerView, adapter: TlManageAdapter) {
        val list = TeamLeaderManager.fetchAllTeamLeaders()
        activity?.runOnUiThread { adapter.submitList(list) }
    }

    // ── Send Personal Message to Employee ────────────────────────────────────
    private fun showSendMessageDialog(emp: EmployeeSummary) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_send_message, null)

        val tvTo    = dialogView.findViewById<TextView>(R.id.tvMessageTo)
        val etMsg   = dialogView.findViewById<EditText>(R.id.etMessage)

        tvTo.text = "To: ${emp.employeeName.replaceFirstChar { it.uppercase() }}"

        // Quick tag chips
        val tags = mapOf(
            R.id.tag1 to "Urgent:",
            R.id.tag2 to "Task:",
            R.id.tag3 to "Good Work!",
            R.id.tag4 to "Please call now.",
            R.id.tag5 to "Please submit your report."
        )
        tags.forEach { (id, prefix) ->
            dialogView.findViewById<TextView>(id).setOnClickListener {
                val current = etMsg.text.toString()
                if (!current.startsWith(prefix)) {
                    etMsg.setText(prefix + current)
                    etMsg.setSelection(etMsg.text.length)
                }
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val adminName = FirebaseAuth.getInstance().currentUser?.email
            ?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "Admin"

        dialogView.findViewById<Button>(R.id.btnSendMsg).setOnClickListener {
            val text = etMsg.text.toString().trim()
            if (text.isBlank()) {
                etMsg.error = "Message cannot be empty"
                return@setOnClickListener
            }

            val message = hashMapOf(
                "from"        to adminName,
                "text"        to text,
                "timestamp"   to System.currentTimeMillis(),
                "read"        to false,
                "replyCount"  to 0
            )

            FirebaseFirestore.getInstance()
                .collection("messages")
                .document(emp.userId)
                .collection("inbox")
                .add(message)
                .addOnSuccessListener { docRef ->
                    Toast.makeText(requireContext(),
                        "Message sent to ${emp.employeeName}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    // Open thread so admin can follow replies
                    openAdminThreadDialog(
                        employeeUid  = emp.userId,
                        employeeName = emp.employeeName,
                        msgId        = docRef.id,
                        adminMsgText = text,
                        adminMsgTs   = message["timestamp"] as Long,
                        adminName    = adminName
                    )
                    // Send FCM Notification via GAS
                    FirebaseFirestore.getInstance().collection("users")
                        .document(emp.userId)
                        .get()
                        .addOnSuccessListener { document ->
                            val token = document.getString("fcmToken")
                            if (!token.isNullOrEmpty()) {
                                GasNotificationSender.sendNotification(
                                    requireContext(),
                                    token,
                                    "New Message",
                                    text
                                )
                            }
                        }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(),
                        "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        dialogView.findViewById<Button>(R.id.btnCancelMsg).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // ── Admin Thread View (see replies from employee) ─────────────────────────
    private fun openAdminThreadDialog(
        employeeUid : String,
        employeeName: String,
        msgId       : String,
        adminMsgText: String,
        adminMsgTs  : Long,
        adminName   : String
    ) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val composeView = androidx.compose.ui.platform.ComposeView(requireContext()).apply {
            setContent {
                HRPortalTheme {
                    ChatThreadScreen(
                        isAdminView = true,
                        employeeUid = employeeUid,
                        msgId = msgId,
                        employeeName = employeeName,
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

    // ── Admin Set Target Dialog ──────────────────────────────────────────────────
    private fun showSetTargetDialog(emp: EmployeeSummary) {
        val ctx = requireContext()
        val etTarget = EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint      = "Enter target (e.g. 10)"
            if (emp.adminTarget > 0) setText(emp.adminTarget.toString())
            setPadding(40, 24, 40, 24)
        }

        AlertDialog.Builder(ctx)
            .setTitle("Set Target for ${emp.employeeName}")
            .setMessage("Current target: ${if (emp.adminTarget > 0) emp.adminTarget else "Not set"}")
            .setView(etTarget)
            .setPositiveButton("Save") { _, _ ->
                val input = etTarget.text.toString().trim().toIntOrNull()
                if (input == null || input <= 0) {
                    Toast.makeText(ctx, "Enter a valid number", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.saveAdminTarget(emp.userId, input)
                Toast.makeText(ctx, "Target set: $input for ${emp.employeeName}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

