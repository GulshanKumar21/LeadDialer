package com.adyapan.leaddialer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallPopupActivity : AppCompatActivity() {

    private lateinit var leadViewModel: LeadViewModel
    private lateinit var callViewModel: CallViewModel
    private lateinit var attendanceViewModel: AttendanceViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        leadViewModel = ViewModelProvider(this, LeadViewModelFactory(application))[LeadViewModel::class.java]
        callViewModel = ViewModelProvider(this, CallViewModelFactory(application))[CallViewModel::class.java]
        attendanceViewModel = ViewModelProvider(this, AttendanceViewModelFactory(application))[AttendanceViewModel::class.java]

        val record = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("call_record", CallRecord::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("call_record")
        }

        val calledAtStr = intent.getStringExtra("called_at_str")
            ?: SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(record?.calledAt ?: System.currentTimeMillis()))


        getSharedPreferences(CallManager.PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(CallManager.KEY_PENDING, false)
            .putLong(CallManager.KEY_ACTUAL_END, 0L)
            .putLong(CallManager.KEY_OFFHOOK_TIME, 0L)
            .apply()
        CallManager.callActive = false

        if (record != null) {
            showCallStatusDialog(record, calledAtStr)
        } else {
            finish()
        }
    }

    private fun showCallStatusDialog(record: CallRecord, calledAtStr: String) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_call_status, null)
        dialog.setContentView(view)

        view.findViewById<TextView>(R.id.tvTitle).text = "📞 Call Summary"
        view.findViewById<TextView>(R.id.tvDetails).text =
            "${record.name.ifBlank { record.phone }} • ${CallManager.formatDuration(record.duration)}"
        view.findViewById<TextView>(R.id.tvCallTime).text = "🕐 Called at: $calledAtStr"

        val lead = CallManager.currentLead
        val isManual = lead == null || lead.name == "Manual Dial"

        val layoutSave = view.findViewById<android.view.View>(R.id.layoutSaveLead)
        val btnSave    = view.findViewById<Button>(R.id.btnSaveAsLead)
        if (isManual) {
            layoutSave.visibility = android.view.View.VISIBLE
            btnSave.setOnClickListener {
                dialog.dismiss()
                showSaveLeadDialog(record, calledAtStr)
            }
        }


        val brochureStatuses  = setOf("Interested")
        val messageOnlyStatuses = setOf("Busy", "Not Connected")

        fun saveStatus(selected: String) {
            if (lead != null) {
                if (lead.id != 0) {
                    leadViewModel.updateStatus(lead, selected)
                } else {
                    leadViewModel.insert(
                        lead.copy(
                            status   = selected,
                            calledAt = System.currentTimeMillis()
                        )
                    )
                }
            }

            callViewModel.saveRecord(record.copy(status = selected))

            CalledNumbersCache.clear(this)
            dialog.dismiss()

            Toast.makeText(this, "✅ $selected", Toast.LENGTH_SHORT).show()

            if (selected in brochureStatuses) {
                showWhatsAppDialog(record, status = selected, showBrochure = true)
            } else if (selected in messageOnlyStatuses) {
                showWhatsAppDialog(record, status = selected, showBrochure = false)
            } else {
                finish()
            }
        }

        view.findViewById<Button>(R.id.btnConnected).setOnClickListener    { saveStatus("Wrong Number") }
        view.findViewById<Button>(R.id.btnNotConnected).setOnClickListener {
            // Show WhatsApp dialog AFTER saving
            saveStatus("Not Connected")
        }
        view.findViewById<Button>(R.id.btnBusy).setOnClickListener {
            // Show WhatsApp dialog AFTER saving
            saveStatus("Busy")
        }
        view.findViewById<Button>(R.id.btnInterested).setOnClickListener   { saveStatus("Interested")   }
        view.findViewById<Button>(R.id.btnNotInterested).setOnClickListener {
            // Show reason dialog first, then save
            dialog.dismiss()
            showNotInterestedReasonDialog(record)
        }
        view.findViewById<Button>(R.id.btnCustom).setOnClickListener {
            // Show custom note dialog
            dialog.dismiss()
            showCustomStatusDialog(record)
        }
        view.findViewById<Button>(R.id.btnSkip).setOnClickListener {
            dialog.dismiss()
            finish()
        }

        dialog.setOnDismissListener { /* handled per-button */ }
        dialog.show()
    }

    // ── Save as Lead dialog ───────────────────────────────────────────────────
    private fun showSaveLeadDialog(record: CallRecord, calledAtStr: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_save_lead, null)

        val etName    = dialogView.findViewById<EditText>(R.id.etLeadName)
        val etCollege = dialogView.findViewById<EditText>(R.id.etLeadCollege)
        val etCity    = dialogView.findViewById<EditText>(R.id.etLeadCity)
        val tvPhone   = dialogView.findViewById<TextView>(R.id.tvLeadPhone)

        tvPhone.text = "📱 ${record.phone}"

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

            val newLead = Lead(
                name        = name,
                phone       = record.phone,
                status      = "Connected",
                collegeName = college,
                collegeCity = city,
                calledAt    = System.currentTimeMillis()
            )
            leadViewModel.insert(newLead)

            CallManager.currentLead = null
            callViewModel.saveRecord(record.copy(name = name, status = "Connected"))

            saveDialog.dismiss()
            Toast.makeText(this, "✅ Lead saved: $name", Toast.LENGTH_SHORT).show()
            finish()
        }

        dialogView.findViewById<Button>(R.id.btnCancelSaveLead).setOnClickListener {
            saveDialog.dismiss()
            CallManager.currentLead = null
            finish()
        }

        saveDialog.show()
    }



    private fun showWhatsAppDialog(record: CallRecord, status: String, showBrochure: Boolean = true) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_whatsapp_message, null)

        val etName         = dialogView.findViewById<EditText>(R.id.etWaStudentName)
        val etWaPhone      = dialogView.findViewById<EditText>(R.id.etWaPhoneNumber)
        val etCourseSearch = dialogView.findViewById<EditText>(R.id.etCourseSearch)
        val spinnerCourse  = dialogView.findViewById<Spinner>(R.id.spinnerCourse)
        val spinnerLang    = dialogView.findViewById<Spinner>(R.id.spinnerLanguage)
        val btnGenerate    = dialogView.findViewById<Button>(R.id.btnGenerateMsg)
        val tvGenerating   = dialogView.findViewById<TextView>(R.id.tvGenerating)
        val etMessage      = dialogView.findViewById<EditText>(R.id.etMessagePreview)
        val btnSend        = dialogView.findViewById<Button>(R.id.btnSendWa)
        val btnBrochure    = dialogView.findViewById<Button>(R.id.btnSendBrochure)
        val btnSendMsgOnly = dialogView.findViewById<Button>(R.id.btnSendMsgOnly)
        val btnCancel      = dialogView.findViewById<Button>(R.id.btnCancelWa)
        val btnCopy        = dialogView.findViewById<Button>(R.id.btnCopyMsg)

        etName.setText(record.name)
        etWaPhone.setText(record.phone)

        btnCopy.setOnClickListener {
            val msg = etMessage.text.toString()
            if (msg.isBlank()) {
                Toast.makeText(this, "Nothing to copy", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("WhatsApp Message", msg)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied to clipboard! 📋", Toast.LENGTH_SHORT).show()
        }

        data class Brochure(val title: String, val link: String)
        val allBrochures = mutableListOf<Brochure>()
        try {
            val json = assets.open("brochures.json").bufferedReader().readText()
            val arr  = org.json.JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                allBrochures.add(Brochure(obj.getString("title"), obj.getString("link")))
            }
        } catch (e: Exception) { android.util.Log.e("WA", "brochures load error: ${e.message}") }

        var filteredBrochures = allBrochures.toMutableList()

        fun updateCourseSpinner(list: List<Brochure>) {
            spinnerCourse.adapter = ArrayAdapter(this,
                android.R.layout.simple_spinner_item, list.map { it.title }
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }
        updateCourseSpinner(filteredBrochures)

        etCourseSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                val q = s.toString().lowercase()
                filteredBrochures = allBrochures.filter { it.title.lowercase().contains(q) }.toMutableList()
                updateCourseSpinner(filteredBrochures)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        spinnerLang.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            WhatsAppMessageGenerator.LANGUAGES.map { "${it.first} — ${it.second}" }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val waDialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(true).create()
        waDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        waDialog.setOnDismissListener { finish() }


        if (!showBrochure) {
            dialogView.findViewById<android.view.View>(R.id.etCourseSearch)?.visibility  = android.view.View.GONE
            dialogView.findViewById<android.view.View>(R.id.spinnerCourse)?.visibility   = android.view.View.GONE
            dialogView.findViewById<android.view.View>(R.id.btnSendBrochure)?.visibility = android.view.View.GONE
            dialogView.findViewById<android.view.View>(R.id.btnSendWa)?.visibility       = android.view.View.GONE
            dialogView.findViewById<android.view.View>(R.id.tvDownloadStatus)?.visibility = android.view.View.GONE
        }

        // ── Generate: AI message + brochure link ─────────────────────────────
        btnGenerate.setOnClickListener {
            val course = filteredBrochures.getOrNull(spinnerCourse.selectedItemPosition)
            val lang   = WhatsAppMessageGenerator.LANGUAGES[spinnerLang.selectedItemPosition].first
            btnGenerate.isEnabled = false
            btnGenerate.text      = "Generating..."
            tvGenerating.visibility = android.view.View.VISIBLE
            etMessage.setText("")

            val currentName = etName.text.toString().trim()
            lifecycleScope.launch {
                val aiMsg = WhatsAppMessageGenerator.generateMessage(
                    context = this@CallPopupActivity, studentName = currentName, language = lang, callStatus = status
                )
                etMessage.setText(aiMsg)
                tvGenerating.visibility = android.view.View.GONE
                btnGenerate.isEnabled = true
                btnGenerate.text      = "✨ Regenerate"
            }
        }

        val tvDownloadStatus = dialogView.findViewById<TextView>(R.id.tvDownloadStatus)

        btnSend.setOnClickListener {
            val msg     = etMessage.text.toString().trim()
            val waPhone = etWaPhone.text.toString().trim()
            val course  = filteredBrochures.getOrNull(spinnerCourse.selectedItemPosition)
            
            if (waPhone.isEmpty()) {
                Toast.makeText(this, "Please enter WhatsApp number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (msg.isEmpty()) {
                Toast.makeText(this, "Please generate the message first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (course == null) {
                Toast.makeText(this, "Please select a course", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnSend.isEnabled     = false
            btnBrochure.isEnabled = false
            tvDownloadStatus.visibility = android.view.View.VISIBLE
            tvDownloadStatus.text = "⬇️ Downloading PDF brochure..."

            lifecycleScope.launch {
                val error = BrochureSharer.downloadAndShare(
                    context     = this@CallPopupActivity,
                    phone       = waPhone,
                    courseTitle = course.title,
                    driveLink   = course.link,
                    message     = msg
                )
                if (error != null) {
                    tvDownloadStatus.text = "❌ $error"
                    btnSend.isEnabled     = true
                    btnBrochure.isEnabled = true
                } else {
                    waDialog.dismiss()
                    finish()
                }
            }
        }


        btnSendMsgOnly.setOnClickListener {
            val msg     = etMessage.text.toString().trim()
            val waPhone = etWaPhone.text.toString().trim()
            if (waPhone.isEmpty()) {
                Toast.makeText(this, "Please enter WhatsApp number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (msg.isEmpty()) {
                Toast.makeText(this, "Please generate the message first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendWhatsApp(waPhone, msg)
            waDialog.dismiss()
            finish()
        }


        btnBrochure.setOnClickListener {
            val waPhone = etWaPhone.text.toString().trim()
            if (waPhone.isEmpty()) {
                Toast.makeText(this, "Please enter WhatsApp number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val course = filteredBrochures.getOrNull(spinnerCourse.selectedItemPosition)
            if (course == null) {
                Toast.makeText(this, "Please select a course first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val simpleMsg = "Namaste ${record.name} ji! 🙏\n\n" +
                "*${course.title}* ka brochure dekhen.\n— Adyapan Team"
            btnSend.isEnabled     = false
            btnBrochure.isEnabled = false
            tvDownloadStatus.visibility = android.view.View.VISIBLE
            tvDownloadStatus.text = "⬇️ Downloading PDF brochure..."

            lifecycleScope.launch {
                val error = BrochureSharer.downloadAndShare(
                    context     = this@CallPopupActivity,
                    phone       = waPhone,
                    courseTitle = course.title,
                    driveLink   = course.link,
                    message     = simpleMsg
                )
                if (error != null) {
                    tvDownloadStatus.text = "❌ $error"
                    btnSend.isEnabled     = true
                    btnBrochure.isEnabled = true
                } else {
                    waDialog.dismiss()
                    finish()
                }
            }
        }

        btnCancel.setOnClickListener { waDialog.dismiss(); finish() }
        waDialog.show()
    }
    private fun sendWhatsApp(phone: String, message: String) {
        val clean     = phone.filter { it.isDigit() }
        val fullPhone = if (clean.startsWith("91") && clean.length == 12) clean else "91$clean"
        try {
            val encoded = java.net.URLEncoder.encode(message, "UTF-8")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("whatsapp://send?phone=$fullPhone&text=$encoded")
            }
            val chooser = Intent.createChooser(intent, "Select WhatsApp Application")
            startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp is not opening", Toast.LENGTH_SHORT).show()
        }
    }


    private fun showNotInterestedReasonDialog(record: CallRecord) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_not_interested_reason, null)

        val etReason   = dialogView.findViewById<EditText>(R.id.etNotInterestedReason)
        val tvCount    = dialogView.findViewById<android.widget.TextView>(R.id.tvWordCount)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmNotInterested)
        val btnCancel  = dialogView.findViewById<Button>(R.id.btnCancelNotInterested)

        val niDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        niDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)


        etReason.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                val words = s.toString().trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
                tvCount.text = "$words / 15 words"
                tvCount.setTextColor(
                    if (words in 10..15) android.graphics.Color.parseColor("#16A34A")
                    else android.graphics.Color.parseColor("#9CA3AF")
                )
            }
        })

        btnConfirm.setOnClickListener {
            val reason = etReason.text.toString().trim()
            val words  = reason.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
            if (reason.isBlank()) {
                etReason.error = "Please write the reason"
                return@setOnClickListener
            }
            if (words > 15) {
                etReason.error = "Please keep it under 15 words"
                return@setOnClickListener
            }


            val statusClean = "Not Interested"
            val lead = CallManager.currentLead
            if (lead != null) {
                if (lead.id != 0) leadViewModel.updateStatusAndNotes(lead, statusClean, reason)
                else leadViewModel.insert(lead.copy(status = statusClean, notes = reason, calledAt = System.currentTimeMillis()))
            }
            callViewModel.saveRecord(record.copy(status = statusClean))
            CalledNumbersCache.clear(this)

            niDialog.dismiss()
            Toast.makeText(this, "✅ Not Interested saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnCancel.setOnClickListener { niDialog.dismiss(); finish() }
        niDialog.show()
    }

    private fun showCustomStatusDialog(record: CallRecord) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_custom_status, null)

        val etCustom   = dialogView.findViewById<EditText>(R.id.etCustomStatus)
        val tvCount    = dialogView.findViewById<android.widget.TextView>(R.id.tvCustomWordCount)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmCustom)
        val btnCancel  = dialogView.findViewById<Button>(R.id.btnCancelCustom)

        val customDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        customDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Live word count
        etCustom.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                val words = s.toString().trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
                tvCount.text = "$words / 15 words"
                tvCount.setTextColor(
                    if (words in 10..15) android.graphics.Color.parseColor("#16A34A")
                    else android.graphics.Color.parseColor("#9CA3AF")
                )
            }
        })

        btnConfirm.setOnClickListener {
            val note  = etCustom.text.toString().trim()
            val words = note.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
            if (note.isBlank()) {
                etCustom.error = "Please write something"
                return@setOnClickListener
            }
            if (words > 15) {
                etCustom.error = "Please keep it under 15 words"
                return@setOnClickListener
            }

            val statusClean = "Custom"
            val lead = CallManager.currentLead
            if (lead != null) {
                if (lead.id != 0) leadViewModel.updateStatusAndNotes(lead, statusClean, note)
                else leadViewModel.insert(lead.copy(status = statusClean, notes = note, calledAt = System.currentTimeMillis()))
            }
            callViewModel.saveRecord(record.copy(status = statusClean, note = note))
            CalledNumbersCache.clear(this)

            customDialog.dismiss()
            Toast.makeText(this, "✅ Status saved", Toast.LENGTH_SHORT).show()

            finish()
        }

        btnCancel.setOnClickListener { customDialog.dismiss(); finish() }
        customDialog.show()
    }

    override fun finish() {
        val lead = CallManager.currentLead
        if (lead != null && lead.name == "Manual Dial" && !isFinishing) {
            showSaveLeadEndDialog(lead)
        } else {
            super.finish()
        }
    }

    private fun showSaveLeadEndDialog(lead: Lead) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_save_lead, null)

        val etName    = dialogView.findViewById<EditText>(R.id.etLeadName)
        val etCollege = dialogView.findViewById<EditText>(R.id.etLeadCollege)
        val etCity    = dialogView.findViewById<EditText>(R.id.etLeadCity)
        val tvPhone   = dialogView.findViewById<TextView>(R.id.tvLeadPhone)

        etName.setText("")
        etName.hint = "Student Name"
        tvPhone.text = "📱 ${lead.phone}"

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

            lifecycleScope.launch(Dispatchers.IO) {
                val existing = AppDatabase.getInstance(applicationContext).leadDao().getByPhone(lead.phone)
                if (existing != null) {
                    val updated = existing.copy(
                        name        = name,
                        collegeName = college,
                        collegeCity = city
                    )
                    leadViewModel.updateLeadDirectly(updated)
                } else {
                    val newLead = Lead(
                        name        = name,
                        phone       = lead.phone,
                        status      = lead.status,
                        collegeName = college,
                        collegeCity = city,
                        calledAt    = lead.calledAt
                    )
                    leadViewModel.insert(newLead)
                }

                // Also update CallRecord if any
                val records = callViewModel.allRecords.value ?: emptyList()
                val lastRecord = records.find { it.phone == lead.phone }
                if (lastRecord != null) {
                    callViewModel.saveRecord(lastRecord.copy(name = name))
                }

                withContext(Dispatchers.Main) {
                    saveDialog.dismiss()
                    Toast.makeText(this@CallPopupActivity, "✅ Lead saved: $name", Toast.LENGTH_SHORT).show()
                    CallManager.currentLead = null
                    super@CallPopupActivity.finish()
                }
            }
        }

        dialogView.findViewById<Button>(R.id.btnCancelSaveLead).setOnClickListener {
            saveDialog.dismiss()
            CallManager.currentLead = null
            super@CallPopupActivity.finish()
        }

        saveDialog.show()
    }
}