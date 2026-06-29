package com.adyapan.leaddialer

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LeadsFragment : Fragment() {

    private val TAG = "LeadsFragment"
    private var isFabOpen = false
    private lateinit var leadViewModel       : LeadViewModel
    private lateinit var callViewModel       : CallViewModel
    private lateinit var attendanceViewModel : AttendanceViewModel
    private lateinit var adapter             : LeadAdapter
    private var currentLead                  : Lead? = null
    private var callInProgress               = false

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        val uri = result.data?.data
        if (uri != null) parseExcel(uri)
        else Toast.makeText(requireContext(), "No file selected", Toast.LENGTH_SHORT).show()
    }

    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) currentLead?.let { handleFirstCallAttendance(it) }
        else Toast.makeText(requireContext(), "Call permission denied", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_leads, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        val leadFactory = LeadViewModelFactory(requireActivity().application)
        leadViewModel   = ViewModelProvider(requireActivity(), leadFactory)[LeadViewModel::class.java]

        val callFactory = CallViewModelFactory(requireActivity().application)
        callViewModel   = ViewModelProvider(requireActivity(), callFactory)[CallViewModel::class.java]

        val attFactory = AttendanceViewModelFactory(requireActivity().application)
        attendanceViewModel = ViewModelProvider(requireActivity(), attFactory)[AttendanceViewModel::class.java]

        val recyclerView = view.findViewById<RecyclerView>(R.id.rvLeads)
        val etSearch     = view.findViewById<EditText>(R.id.etSearch)

        // Bind filter tabs for a premium, highly responsive UI/UX
        val tabAll         = view.findViewById<TextView>(R.id.tabAll)
        val tabConnected   = view.findViewById<TextView>(R.id.tabConnected)
        val tabPending     = view.findViewById<TextView>(R.id.tabPending)
        val tabInterested  = view.findViewById<TextView>(R.id.tabInterested)
        val tabBusy        = view.findViewById<TextView>(R.id.tabBusy)
        val tabSales       = view.findViewById<TextView>(R.id.tabSales)

        val tabs = listOf(tabAll, tabConnected, tabPending, tabInterested, tabBusy, tabSales)
        val statuses = listOf("All", "Connected", "Pending", "Interested", "Busy", "Sales")

        fun selectTab(selectedIdx: Int) {
            tabs.forEachIndexed { idx, tab ->
                if (tab != null) {
                    if (idx == selectedIdx) {
                        tab.setBackgroundResource(R.drawable.bg_tab_active_orange)
                        tab.setTextColor(0xFFFFFFFF.toInt())
                    } else {
                        tab.setBackgroundResource(R.drawable.bg_tab_inactive)
                        tab.setTextColor(0xFF374151.toInt())
                    }
                }
            }
            adapter.filterByStatus(statuses[selectedIdx])
        }

        tabs.forEachIndexed { idx, tab ->
            tab?.setOnClickListener {
                selectTab(idx)
            }
        }

        adapter = LeadAdapter(
            onCallClick = { lead ->
                currentLead = lead
                initiateCall(lead)
            },
            onSalesToggle = { lead, newValue ->
                val fid: String = if (!lead.firestoreId.isNullOrBlank()) {
                    lead.firestoreId!!
                } else {
                    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    if (uid == null || lead.phone.isBlank()) {
                        Toast.makeText(requireContext(), "Lead not synced yet — try again", Toast.LENGTH_SHORT).show()
                        return@LeadAdapter
                    }
                    val computed = "${uid}_${lead.phone.filter { it.isDigit() }}"
                    computed
                }

                // Optimistic UI Update: Local Room DB me turant update karo
                val updated = lead.copy(salesDone = newValue, firestoreId = fid)
                leadViewModel.updateLeadDirectly(updated)
                
                val msg = if (newValue) "Sale marked as Done!" else "↩ Sale marked as Not Done"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

                // Background Sync
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        FirestoreSource.updateSalesDone(fid, newValue)
                    }
                    if (!ok) {
                        Toast.makeText(requireContext(), "Saved locally, but cloud sync failed (check internet)", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onItemClick = { lead ->
                showCallHistoryDialog(lead)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter       = adapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                // dy > 0 check ensures it only triggers when scrolling down.
                // canScrollVertically(1) returns false when it reaches the bottom of the list.
                if (dy > 0 && !recyclerView.canScrollVertically(1)) {
                    leadViewModel.loadMoreLeads()
                }
            }
        })

        leadViewModel.allLeads.observe(viewLifecycleOwner) { leads ->
            adapter.submitList(leads)
            refreshCacheIfNeeded(leads)
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })



        startAutoRefresh()
    }

    private fun refreshCacheIfNeeded(leads: List<Lead>) {
        val cacheAge = CalledNumbersCache.getAge(requireContext())
        if (cacheAge > 5 * 60 * 1000L) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val phones  = leads.map { it.phone }
                    val results = SheetsSync.fetchAllCalledNumbers(requireContext(), phones)
                    CalledNumbersCache.save(requireContext(), results)
                    Log.d(TAG, "Cache refreshed — ${results.size} phones")
                } catch (e: Exception) {
                    Log.e(TAG, "Cache refresh error: ${e.message}")
                }
            }
        }
    }

    private fun showManualDialDialog() {
        val layout = android.widget.LinearLayout(requireContext())
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.setPadding(50, 50, 50, 50)

        val nameInput = EditText(requireContext())
        nameInput.hint = "Enter Student Name (Optional)"
        nameInput.inputType = android.text.InputType.TYPE_CLASS_TEXT

        val phoneInput = EditText(requireContext())
        phoneInput.hint = "Enter phone number"
        phoneInput.inputType = android.text.InputType.TYPE_CLASS_PHONE
        phoneInput.setPadding(0, 30, 0, 0)

        layout.addView(nameInput)
        layout.addView(phoneInput)

        AlertDialog.Builder(requireContext())
            .setTitle("Manual Dial")
            .setView(layout)
            .setPositiveButton("Call") { _, _ ->
                val number = phoneInput.text.toString().trim()
                var name = nameInput.text.toString().trim()
                if (name.isEmpty()) name = "Manual Dial"
                
                if (number.isNotEmpty()) {
                    val manualLead = Lead(name = name, phone = number)
                    initiateCall(manualLead)
                } else {
                    Toast.makeText(requireContext(), "Please enter a valid number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startAutoRefresh() {

        lifecycleScope.launch {

            while (true) {

                delay(15 * 60 * 1000L)

                val leads =
                    leadViewModel.allLeads.value
                        ?: continue

                try {

                    // CACHE REFRESH

                    val phones =
                        leads.map { it.phone }

                    val results =
                        withContext(Dispatchers.IO) {

                            SheetsSync.fetchAllCalledNumbers(
                                requireContext(),
                                phones
                            )
                        }

                    CalledNumbersCache.save(
                        requireContext(),
                        results
                    )

                    // AUTO GOOGLE SHEETS SYNC

                    withContext(Dispatchers.IO) {

                        SheetsSync.syncAllLeads(
                            requireContext(),
                            leads
                        )

                        val allRecords =
                            AppDatabase
                                .getInstance(requireContext())
                                .callRecordDao()
                                .getAllOnce()

                        SheetsSync.syncCallRecords(
                            requireContext(),
                            allRecords
                        )
                    }

                    Log.d(
                        TAG,
                        "Auto sync completed"
                    )

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Auto refresh error: ${e.message}"
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        //  Call popup is now handled globally in MainActivity.onResume()
        // so the popup shows regardless of which fragment the user is on.
        callInProgress = false
        leadViewModel.syncFromFirebaseOnce()
    }

    private fun initiateCall(lead: Lead) {
        currentLead = lead
        val hasPerm = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val cached = CalledNumbersCache.check(requireContext(), lead.phone)

        if (cached != null) {
            Log.d(TAG, "Cache hit for ${lead.phone}")
            if (cached.called) {
                showAlreadyCalledWarning(lead, cached, hasPerm)
            } else {
                if (hasPerm) handleFirstCallAttendance(lead)
                else callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            }
        } else {
            Log.d(TAG, "Cache miss — fetching for ${lead.phone}")
            Toast.makeText(requireContext(), "Checking...", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    SheetsSync.checkIfAlreadyCalled(lead.phone)
                }
                if (!isAdded) return@launch

                if (result.called) {
                    showAlreadyCalledWarning(lead, result, hasPerm)
                } else {
                    if (hasPerm) handleFirstCallAttendance(lead)
                    else callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                }
            }
        }
    }


    private fun proceedWithCall(lead: Lead, hasPerm: Boolean) {
        val cached = CalledNumbersCache.check(requireContext(), lead.phone)
        if (cached != null && cached.called) {
            showAlreadyCalledWarning(lead, cached, hasPerm)
        } else {
            if (hasPerm) handleFirstCallAttendance(lead)
            else callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }


    private fun showAlreadyCalledWarning(
        lead    : Lead,
        result  : CallCheckResult,
        hasPerm : Boolean
    ) {
        if (!isAdded) return

        val callerName = result.calledBy
            .ifBlank { lead.calledBy }
            .ifBlank { "Someone" }

        AlertDialog.Builder(requireContext())
            .setTitle("Call Already Made!")
            .setMessage(
                "${lead.name}\n ${lead.phone}\n\n" +
                "Called By: $callerName\n" +
                "Status: ${result.status.ifBlank { lead.status }}\n\n" +
                "Do you still want to make the call?"
            )
            .setPositiveButton("Call Anyway") { _, _ ->
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    handleFirstCallAttendance(lead)
                } else {
                    callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                }
            }
            .setNegativeButton("Skip") { _, _ ->
                val updatedLead = lead.copy(
                    status = result.status.ifBlank { lead.status },
                    notes  = "Already called by: $callerName | Status: ${result.status.ifBlank { lead.status }}"
                )
                leadViewModel.updateLeadDirectly(updatedLead)

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val allLeads = withContext(Dispatchers.Main) {
                            leadViewModel.allLeads.value ?: emptyList()
                        }
                        SheetsSync.syncAllLeads(requireContext(), allLeads)
                    } catch (e: Exception) {
                        Log.e(TAG, "Sync error: ${e.message}")
                    }
                }

                Toast.makeText(
                    requireContext(),
                    "$callerName has already made the call",
                    Toast.LENGTH_LONG
                ).show()
            }
            .show()
    }


    private fun handleFirstCallAttendance(lead: Lead) {
        lifecycleScope.launch {
            val alreadyPunchedIn = attendanceViewModel.isTodayPunchedIn()

            if (alreadyPunchedIn) {
                startCall(lead)
            } else {
                val isLate = attendanceViewModel.isCurrentlyLate()
                val time   = attendanceViewModel.getCurrentTime()

                if (isLate) {
                    withContext(Dispatchers.Main) {
                        LateReasonDialog.show(
                            context        = requireContext(),
                            currentTime    = time,
                            onReasonSubmit = { reason ->
                                attendanceViewModel.punchIn(requireContext(), reason) { record ->
                                    Log.d(TAG, "Punched in LATE: ${record.punchInTime}")
                                    startCall(lead)
                                }
                            }
                        )
                    }
                } else {
                    attendanceViewModel.punchIn(requireContext()) { record ->
                        Log.d(TAG, "Punched in on time: ${record.punchInTime}")
                        Toast.makeText(
                            requireContext(),
                            "Punch-in: ${record.punchInTime}",
                            Toast.LENGTH_SHORT
                        ).show()
                        startCall(lead)
                    }
                }
            }
        }
    }


    private fun startCall(lead: Lead) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            currentLead = lead
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            return
        }

        Log.d(TAG, "startCall: ${lead.name}")
        callInProgress = true

        // Store globally so MainActivity can show popup from any screen
        CallManager.currentLead = lead
        CallManager.callActive  = true

        //  SIM-aware call: uses the SIM selected in Settings, bypasses system SIM picker
        CallManager.placeCall(requireContext(), lead) { intent ->
            startActivity(intent)   // only reached when no SIM is selected (Ask Every Time)
        }
    }

    private fun showStatusCard(record: CallRecord) {
        if (!isAdded || !isVisible) return

        val safeContext = context ?: return   // never call requireContext() inside callbacks

        val dialog = BottomSheetDialog(safeContext)
        val view   = layoutInflater.inflate(R.layout.bottom_sheet_call_status, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.tvTitle).text   = "Call Summary"
        view.findViewById<TextView>(R.id.tvDetails).text =
            "${record.name} • ${CallManager.formatDuration(record.duration)}"

        fun saveStatus(selected: String) {
            if (!isAdded) return   // fragment may have been detached before user tapped

            val lead = currentLead
            if (lead != null) {
                if (lead.id != 0) {
                    // Normal lead already in DB — update status
                    leadViewModel.updateStatus(lead, selected)
                } else {
                    // Manual Dial lead (id=0, never inserted) — insert first, then update
                    val inserted = lead.copy(status = selected, calledAt = System.currentTimeMillis())
                    leadViewModel.insert(inserted)
                }
            }

            // Save call record (safe even if lead is null)
            callViewModel.saveRecord(record.copy(status = selected))

            // Background sync — capture application context to avoid fragment lifecycle issues
            val appContext = safeContext.applicationContext
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val allLeads = withContext(Dispatchers.Main) {
                        leadViewModel.allLeads.value ?: emptyList()
                    }
                    SheetsSync.syncAllLeads(appContext, allLeads)

                    val allRecords = AppDatabase.getInstance(appContext)
                        .callRecordDao().getAllOnce()
                    SheetsSync.syncCallRecords(appContext, allRecords)
                } catch (e: Exception) {
                    Log.e(TAG, "Sync error: ${e.message}")
                }
            }

            dialog.dismiss()

            if (isAdded) {
                Toast.makeText(safeContext, "$selected", Toast.LENGTH_SHORT).show()
                if (selected == "Interested" || selected == "Busy" || selected == "Not Connected") {
                    showWhatsAppDialog(record, selected)
                }
            }
        }

        view.findViewById<Button>(R.id.btnConnected).setOnClickListener     { saveStatus("Wrong Number")  }
        view.findViewById<Button>(R.id.btnNotConnected).setOnClickListener  { saveStatus("Not Connected")  }
        view.findViewById<Button>(R.id.btnBusy).setOnClickListener          { saveStatus("Busy")           }
        view.findViewById<Button>(R.id.btnInterested).setOnClickListener    { saveStatus("Interested")     }
        view.findViewById<Button>(R.id.btnNotInterested).setOnClickListener { saveStatus("Not Interested") }
        view.findViewById<Button>(R.id.btnSkip).setOnClickListener          { dialog.dismiss()             }

        dialog.show()
    }

    internal fun showWhatsAppDialog(record: CallRecord, status: String = "Interested") {
        if (!isAdded) return

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_whatsapp_message, null)

        val etName          = dialogView.findViewById<EditText>(R.id.etWaStudentName)
        val etCourseSearch  = dialogView.findViewById<EditText>(R.id.etCourseSearch)
        val spinnerCourse   = dialogView.findViewById<Spinner>(R.id.spinnerCourse)
        val spinnerLang     = dialogView.findViewById<Spinner>(R.id.spinnerLanguage)
        val btnGenerate     = dialogView.findViewById<Button>(R.id.btnGenerateMsg)
        val tvGenerating    = dialogView.findViewById<TextView>(R.id.tvGenerating)
        val etMessage       = dialogView.findViewById<EditText>(R.id.etMessagePreview)
        val btnSend         = dialogView.findViewById<Button>(R.id.btnSendWa)
        val btnBrochure     = dialogView.findViewById<Button>(R.id.btnSendBrochure)
        val btnSendMsgOnly  = dialogView.findViewById<Button>(R.id.btnSendMsgOnly)
        val btnCancel       = dialogView.findViewById<Button>(R.id.btnCancelWa)
        val btnCopy         = dialogView.findViewById<Button>(R.id.btnCopyMsg)

        etName.setText(record.name)

        btnCopy.setOnClickListener {
            val msg = etMessage.text.toString()
            if (msg.isBlank()) {
                Toast.makeText(requireContext(), "Nothing to copy", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("WhatsApp Message", msg)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        // ── Load brochures from assets ────────────────────────────────────────
        data class Brochure(val title: String, val link: String)
        val allBrochures: MutableList<Brochure> = mutableListOf()
        try {
            val json = requireContext().assets.open("brochures.json")
                .bufferedReader().readText()
            val arr = org.json.JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                allBrochures.add(Brochure(obj.getString("title"), obj.getString("link")))
            }
        } catch (e: Exception) {
            android.util.Log.e("WA", "brochures.json load error: ${e.message}")
        }

        var filteredBrochures = allBrochures.toMutableList()

        fun updateCourseSpinner(list: List<Brochure>) {
            val titles = list.map { it.title }
            spinnerCourse.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                titles
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }
        updateCourseSpinner(filteredBrochures)

        // ── Course search filter ──────────────────────────────────────────────
        etCourseSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                val query = s.toString().lowercase()
                filteredBrochures = allBrochures.filter {
                    it.title.lowercase().contains(query)
                }.toMutableList()
                updateCourseSpinner(filteredBrochures)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // ── Language spinner ──────────────────────────────────────────────────
        val langNames = WhatsAppMessageGenerator.LANGUAGES.map { "${it.first} — ${it.second}" }
        spinnerLang.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            langNames
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView).setCancelable(true).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Hide brochure stuff for Busy / Not Connected
        val showBrochure = status == "Interested"
        if (!showBrochure) {
            dialogView.findViewById<View>(R.id.etCourseSearch)?.visibility  = View.GONE
            dialogView.findViewById<View>(R.id.spinnerCourse)?.visibility   = View.GONE
            dialogView.findViewById<View>(R.id.btnSendBrochure)?.visibility = View.GONE
            dialogView.findViewById<View>(R.id.btnSendWa)?.visibility       = View.GONE
            dialogView.findViewById<View>(R.id.tvDownloadStatus)?.visibility = View.GONE
        }

        // ── Generate message + brochure link ─────────────────────────────────
        btnGenerate.setOnClickListener {
            val selectedCourse = filteredBrochures.getOrNull(spinnerCourse.selectedItemPosition)
            val lang = WhatsAppMessageGenerator.LANGUAGES[spinnerLang.selectedItemPosition].first

            btnGenerate.isEnabled   = false
            btnGenerate.text        = "Generating..."
            tvGenerating.visibility = View.VISIBLE
            etMessage.setText("")

            lifecycleScope.launch {
                val currentName = etName.text.toString().trim()
                val aiMessage = WhatsAppMessageGenerator.generateMessage(
                    context     = requireContext(),
                    studentName = currentName,
                    language    = lang,
                    callStatus  = status
                )
                val fullMessage = aiMessage
                etMessage.setText(fullMessage)
                tvGenerating.visibility = View.GONE
                btnGenerate.isEnabled   = true
                btnGenerate.text        = "Regenerate"
            }
        }

        val tvDownloadStatus = dialogView.findViewById<TextView>(R.id.tvDownloadStatus)

        // ── Send PDF + message together ───────────────────────────────────────
        btnSend.setOnClickListener {
            val msg = etMessage.text.toString().trim()
            val course = filteredBrochures.getOrNull(spinnerCourse.selectedItemPosition)
            if (msg.isEmpty()) {
                Toast.makeText(requireContext(), "Please click 'Generate' first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (course == null) {
                Toast.makeText(requireContext(), "Please select a course", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnSend.isEnabled       = false
            btnBrochure.isEnabled   = false
            tvDownloadStatus.visibility = View.VISIBLE
            tvDownloadStatus.text   = "Downloading PDF..."

            lifecycleScope.launch {
                val error = BrochureSharer.downloadAndShare(
                    context     = requireContext(),
                    phone       = record.phone,
                    courseTitle = course.title,
                    driveLink   = course.link,
                    message     = msg
                )
                if (error != null) {
                    tvDownloadStatus.text = "$error"
                    btnSend.isEnabled     = true
                    btnBrochure.isEnabled = true
                } else {
                    dialog.dismiss()
                }
            }
        }

        btnSendMsgOnly.setOnClickListener {
            val msg = etMessage.text.toString().trim()
            if (msg.isEmpty()) {
                Toast.makeText(requireContext(), "Please click 'Generate' first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendWhatsApp(record.phone, msg)
            dialog.dismiss()
        }

        btnBrochure.setOnClickListener {
            val course = filteredBrochures.getOrNull(spinnerCourse.selectedItemPosition)
            if (course == null) {
                Toast.makeText(requireContext(), "Please select a course first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val simpleMsg = "Hello ${record.name}! \n\n" +
                "Please find the brochure for *${course.title}* attached.\n— Adyapan Team"

            btnSend.isEnabled       = false
            btnBrochure.isEnabled   = false
            tvDownloadStatus.visibility = View.VISIBLE
            tvDownloadStatus.text   = "Downloading PDF..."

            lifecycleScope.launch {
                val error = BrochureSharer.downloadAndShare(
                    context     = requireContext(),
                    phone       = record.phone,
                    courseTitle = course.title,
                    driveLink   = course.link,
                    message     = simpleMsg
                )
                if (error != null) {
                    tvDownloadStatus.text = "$error"
                    btnSend.isEnabled     = true
                    btnBrochure.isEnabled = true
                } else {
                    dialog.dismiss()
                }
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()

    }

    private fun sendWhatsApp(phone: String, message: String) {
        try {
            val clean = phone.filter { it.isDigit() }
            val fullPhone = if (clean.startsWith("91") && clean.length == 12) clean else "91$clean"
            val encoded = java.net.URLEncoder.encode(message, "UTF-8")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("whatsapp://send?phone=$fullPhone&text=$encoded")
            }
            val chooser = Intent.createChooser(intent, "Select WhatsApp Application")
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "WhatsApp is not opening", Toast.LENGTH_SHORT).show()
        }
    }

    /** Opens an Intent chooser that explicitly accepts Excel + CSV MIME types. */
    private fun launchFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            // Allow xlsx, xls, and csv — covers all common spreadsheet exports
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES, arrayOf(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // xlsx
                    "application/vnd.ms-excel",                                          // xls
                    "text/csv",                                                           // csv
                    "text/comma-separated-values",                                        // csv alt
                    "application/csv",                                                    // csv alt
                    "text/plain"                                                           // .txt csv
                )
            )
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        filePickerLauncher.launch(intent)
    }

    private fun parseExcel(uri: Uri) {
        // Capture applicationContext BEFORE switching to IO — avoids requireContext() on background thread
        val appCtx = requireContext().applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val leads = ExcelUtils.parseLeads(appCtx, uri)
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    if (leads.isNotEmpty()) {
                        leadViewModel.insertAll(leads)
                        Toast.makeText(appCtx, "${leads.size} leads imported", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(appCtx, "No valid leads found in file", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "parseExcel error: ${e.message}")
                withContext(Dispatchers.Main) {
                    if (isAdded)
                        Toast.makeText(appCtx, "Failed to read file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showCallHistoryDialog(lead: Lead) {
        val dialog = BottomSheetDialog(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_lead_call_history, null)
        dialog.setContentView(dialogView)

        val tvAvatar = dialogView.findViewById<TextView>(R.id.tvDialogAvatar)
        val tvName = dialogView.findViewById<TextView>(R.id.tvDialogLeadName)
        val tvPhone = dialogView.findViewById<TextView>(R.id.tvDialogLeadPhone)
        val btnClose = dialogView.findViewById<View>(R.id.btnDialogClose)
        val rvHistory = dialogView.findViewById<RecyclerView>(R.id.rvDialogCallHistory)
        val layoutEmpty = dialogView.findViewById<View>(R.id.layoutDialogEmptyState)

        tvName.text = lead.name
        tvPhone.text = lead.phone
        tvAvatar.text = lead.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        
        // Avatar bg color matching LeadAdapter name hash
        val colors = listOf(
            0xFF3B82F6.toInt(), 0xFF10B981.toInt(), 0xFF8B5CF6.toInt(),
            0xFFF59E0B.toInt(), 0xFFEC4899.toInt(), 0xFFEF4444.toInt(), 0xFF06B6D4.toInt()
        )
        val hash = lead.name.hashCode()
        val avatarBgColor = colors[kotlin.math.abs(hash) % colors.size]
        tvAvatar.background?.setTint(avatarBgColor)

        btnClose.setOnClickListener { dialog.dismiss() }

        // Filter call history for this lead
        val cleanLeadPhone = lead.phone.filter { it.isDigit() }
        val allRecords = callViewModel.allRecords.value ?: emptyList()
        val filtered = allRecords.filter { rec ->
            val cleanRecPhone = rec.phone.filter { it.isDigit() }
            cleanRecPhone == cleanLeadPhone || (cleanRecPhone.endsWith(cleanLeadPhone) && cleanLeadPhone.length >= 10)
        }.sortedByDescending { it.calledAt }

        if (filtered.isEmpty()) {
            rvHistory.visibility = View.GONE
            layoutEmpty.visibility = View.VISIBLE
        } else {
            rvHistory.visibility = View.VISIBLE
            layoutEmpty.visibility = View.GONE

            rvHistory.layoutManager = LinearLayoutManager(requireContext())
            rvHistory.adapter = CallHistoryDialogAdapter(filtered)
        }

        dialog.show()
    }

    private class CallHistoryDialogAdapter(private val list: List<CallRecord>) :
        RecyclerView.Adapter<CallHistoryDialogAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val statusStrip: View = view.findViewById(R.id.viewLogStatusStrip)
            val statusBadge: TextView = view.findViewById(R.id.tvLogStatusBadge)
            val duration: TextView = view.findViewById(R.id.tvLogDuration)
            val dateTime: TextView = view.findViewById(R.id.tvLogDateTime)
            val notes: TextView = view.findViewById(R.id.tvLogNotes)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_call_history_log, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val rec = list[position]
            holder.statusBadge.text = rec.status
            
            // Format Duration
            val sec = rec.duration
            val durationStr = when {
                sec <= 0 -> "0s"
                sec >= 60 -> "${sec / 60}m ${sec % 60}s"
                else -> "${sec}s"
            }
            holder.duration.text = durationStr

            // Format Date Time
            val dateStr = if (rec.calledAt > 0L) {
                java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                    .format(java.util.Date(rec.calledAt))
            } else {
                "Unknown"
            }
            holder.dateTime.text = dateStr

            // Notes
            if (rec.note.isNotBlank()) {
                holder.notes.text = "Note: ${rec.note}"
                holder.notes.visibility = View.VISIBLE
            } else {
                holder.notes.visibility = View.GONE
            }

            // Colors
            val statusColor = when {
                rec.status == "Wrong Number"                    -> 0xFFDC2626.toInt() // red
                rec.status == "Interested"                      -> 0xFF16A34A.toInt() // green
                rec.status == "Busy"                            -> 0xFFD97706.toInt() // amber
                rec.status == "Not Connected"                   -> 0xFF4B5563.toInt() // grey
                rec.status == "Not Interested"                  -> 0xFFB91C1C.toInt() // dark red
                rec.status.startsWith("Not Interested:")        -> 0xFFB91C1C.toInt()
                else                                             -> 0xFFFF6A00.toInt() // orange
            }

            val statusBgColor = when {
                rec.status == "Wrong Number"                    -> 0xFFFEE2E2.toInt()
                rec.status == "Interested"                      -> 0xFFDCFCE7.toInt()
                rec.status == "Busy"                            -> 0xFFFEF3C7.toInt()
                rec.status == "Not Connected"                   -> 0xFFF3F4F6.toInt()
                rec.status == "Not Interested"                  -> 0xFFFEE2E2.toInt()
                rec.status.startsWith("Not Interested:")        -> 0xFFFEE2E2.toInt()
                else                                             -> 0xFFFFE6D5.toInt()
            }

            holder.statusStrip.setBackgroundColor(statusColor)
            holder.statusBadge.setTextColor(statusColor)
            holder.statusBadge.background?.setTint(statusBgColor)
        }
    }
}