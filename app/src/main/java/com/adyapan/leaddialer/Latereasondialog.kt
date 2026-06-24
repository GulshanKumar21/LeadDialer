package com.adyapan.leaddialer

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast

object LateReasonDialog {

    private const val TAG           = "LateReasonDialog"
    private const val MANAGER_PHONE = "917880591408"

    private val REASONS = listOf(
        "Health issues",
        "Internet or power issue",
        "Family emergency",
        "Transportation issue",
        "Meeting or training",
        "Occupied with other work",
        "Other reason"
    )

    fun show(
        context        : Context,
        currentTime    : String,
        onReasonSubmit : (reason: String) -> Unit
    ) {
        val dialogView    = LayoutInflater.from(context)
            .inflate(R.layout.dialog_late_reason, null)

        val tvTime        = dialogView.findViewById<TextView>(R.id.tvLateTime)
        val radioGroup    = dialogView.findViewById<RadioGroup>(R.id.radioGroupReasons)
        val etOtherReason = dialogView.findViewById<EditText>(R.id.etOtherReason)

        tvTime.text = "Your punch-in time: $currentTime\n(After 11:30 AM)"

        REASONS.forEachIndexed { index, reason ->
            val rb = RadioButton(context).apply {
                id       = index + 1
                text     = reason
                setPadding(8, 12, 8, 12)
                setTextColor(context.getColor(android.R.color.black))
                textSize = 14f
            }
            radioGroup.addView(rb)
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            etOtherReason.visibility =
                if (checkedId == REASONS.size) View.VISIBLE else View.GONE
        }

        val reasonDialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        reasonDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.btnSubmitReason).setOnClickListener {
            val selectedId = radioGroup.checkedRadioButtonId

            if (selectedId == -1) {
                Toast.makeText(context, "Select a reason", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val reason = if (selectedId == REASONS.size) {
                val other = etOtherReason.text.toString().trim()
                if (other.isEmpty()) {
                    Toast.makeText(context, "Write your reason", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                other
            } else {
                REASONS[selectedId - 1]
            }

            reasonDialog.dismiss()

            showManagerConfirmDialog(
                context        = context,
                currentTime    = currentTime,
                reason         = reason,
                onDone         = { onReasonSubmit(reason) }
            )
        }

        reasonDialog.show()
    }

    private fun showManagerConfirmDialog(
        context     : Context,
        currentTime : String,
        reason      : String,
        onDone      : () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle("Notify Manager?")
            .setMessage(
                "Do you want to inform the manager about your late attendance via WhatsApp?\n\n" +
                        "Time: $currentTime\n" +
                        "Reason: $reason"
            )
            .setCancelable(false)

            .setPositiveButton("Yes, send it") { _, _ ->
                notifyManagerOnWhatsApp(
                    context      = context,
                    employeeName = getEmployeeName(context),
                    punchInTime  = currentTime,
                    reason       = reason,
                    onSent       = { onDone() }  // WhatsApp open hone ke baad call
                )
            }
            .setNegativeButton("No, Skip") { _, _ ->
                onDone()
            }
            .show()
    }

    private fun notifyManagerOnWhatsApp(
        context      : Context,
        employeeName : String,
        punchInTime  : String,
        reason       : String,
        onSent       : () -> Unit
    ) {
        val date = java.text.SimpleDateFormat(
            "dd/MM/yyyy", java.util.Locale.getDefault()
        ).format(java.util.Date())

        val message = """Late Attendance Report

 Employee: $employeeName
 Punch-in Time: $punchInTime
 Date: $date

 Wajah: $reason

— Adyapan CRM System""".trimIndent()

        try {
            val encoded = java.net.URLEncoder.encode(message, "UTF-8")
            val intent  = Intent(Intent.ACTION_VIEW).apply {
                data  = Uri.parse("whatsapp://send?phone=$MANAGER_PHONE&text=$encoded")
            }
            val chooser = Intent.createChooser(intent, "Select WhatsApp Application").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "WhatsApp error: ${e.message}")
        }

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            onSent()
        }, 1500)
    }

    private fun getEmployeeName(context: Context): String {
        var name = SheetsSync.getEmployeeNameStatic(context)
        if (name == "Unknown" || name.isEmpty()) {
            name = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@") ?: "Unknown"
        }
        return name
    }
}