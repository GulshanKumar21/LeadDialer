package com.adyapan.leaddialer

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private lateinit var viewModel: LeadViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val factory = LeadViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(requireActivity(), factory)[LeadViewModel::class.java]

        val btnSync          = view.findViewById<Button>(R.id.btnSyncSheets)
        val btnForceFullSync = view.findViewById<Button>(R.id.btnForceFullSync)
        val btnClearLeads    = view.findViewById<Button>(R.id.btnClearAllLeads)
        val btnLogout        = view.findViewById<Button>(R.id.btnLogout)
        val tvTotalLeads     = view.findViewById<TextView>(R.id.tvSettingsTotalLeads)
        val tvSyncStatus     = view.findViewById<TextView>(R.id.tvSyncStatus)
        val switchDark       = view.findViewById<SwitchMaterial>(R.id.switchDarkMode)

        // ── Dark Mode Toggle ───────────────────────────────────────────────────
        switchDark.isChecked = ThemeManager.isDarkMode(requireContext())
        switchDark.setOnCheckedChangeListener { _, isChecked ->
            ThemeManager.setDarkMode(requireContext(), isChecked)
            // Activity recreates automatically when theme changes
        }

        // ── Change Password ────────────────────────────────────────────────────
        val btnChangePassword = view.findViewById<Button>(R.id.btnChangePassword)
        btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        viewModel.totalLeads.observe(viewLifecycleOwner) { count ->
            tvTotalLeads.text = "Total Leads in Database: $count"
        }

        // ── Google Sheets Sync ─────────────────────────────────────────────────
        btnSync.setOnClickListener {
            val leads = viewModel.allLeads.value
            if (leads.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "No leads to sync", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnSync.isEnabled = false
            btnSync.text      = "Syncing..."
            tvSyncStatus.text = "Syncing ${leads.size} leads to Google Sheets..."
            tvSyncStatus.visibility = View.VISIBLE

            lifecycleScope.launch {
                val success = SheetsSync.syncAllLeads(requireContext(), leads)
                btnSync.isEnabled = true
                btnSync.text      = "Sync to Google Sheets"
                if (success) {
                    tvSyncStatus.text = "Synced successfully! Check Google Sheets."
                    Toast.makeText(requireContext(), "Synced to Google Sheets!", Toast.LENGTH_SHORT).show()
                } else {
                    tvSyncStatus.text = "Sync failed. Check internet connection."
                    Toast.makeText(requireContext(), "Sync failed. Check internet.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // ── Force Full Sync — Restore ALL call history to Sheet ───────────────
        btnForceFullSync.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Restore All Calls to Sheet?")
                .setMessage("Yeh saare call records dobara Google Sheet mein bhejega. Agar sheet mein data delete hua ho to wapas aa jaayega. Continue?")
                .setPositiveButton("Yes, Restore") { _, _ ->
                    btnForceFullSync.isEnabled = false
                    btnForceFullSync.text      = "Uploading..."
                    tvSyncStatus.text          = "Fetching all records..."
                    tvSyncStatus.visibility    = View.VISIBLE

                    lifecycleScope.launch {
                        try {
                            val db      = AppDatabase.getInstance(requireContext())
                            val allRecs = db.callRecordDao().getAllOnce()

                            if (allRecs.isEmpty()) {
                                tvSyncStatus.text = "ℹ No call records found in local database."
                                btnForceFullSync.isEnabled = true
                                btnForceFullSync.text      = "Restore All Calls to Sheet"
                                return@launch
                            }

                            tvSyncStatus.text = "Sending ${allRecs.size} records in batches..."

                            // Send in batches of 150 to avoid timeout
                            val batchSize = 150
                            var success   = true
                            var sent      = 0
                            allRecs.chunked(batchSize).forEachIndexed { idx, batch ->
                                tvSyncStatus.text = "Batch ${idx + 1}/${(allRecs.size + batchSize - 1) / batchSize} (${sent}/${allRecs.size} sent)..."
                                val ok = SheetsSync.syncCallRecords(requireContext(), batch)
                                if (!ok) success = false
                                sent += batch.size
                            }

                            btnForceFullSync.isEnabled = true
                            btnForceFullSync.text      = "Restore All Calls to Sheet"

                            if (success) {
                                tvSyncStatus.text = "${allRecs.size} records restored to Google Sheet!"
                                Toast.makeText(requireContext(), "All call records restored!", Toast.LENGTH_LONG).show()
                            } else {
                                tvSyncStatus.text = "Some batches failed. Try again."
                                Toast.makeText(requireContext(), "Partial sync. Check internet.", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            btnForceFullSync.isEnabled = true
                            btnForceFullSync.text      = "Restore All Calls to Sheet"
                            tvSyncStatus.text          = "Error: ${e.message}"
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnClearLeads.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear All Leads")
                .setMessage("This will permanently delete ALL leads. Are you sure?")
                .setPositiveButton("Yes, Delete All") { _, _ ->
                    viewModel.deleteAll()
                    tvSyncStatus.visibility = View.GONE
                    Toast.makeText(requireContext(), "All leads deleted", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // ── Logout ─────────────────────────────────────────────────────────────
        btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout") { _, _ ->
                    // Show syncing progress before logout to prevent data loss
                    val progressDialog = AlertDialog.Builder(requireContext())
                        .setTitle("Syncing Data")
                        .setMessage("Uploading pending call records to cloud before logout...")
                        .setCancelable(false)
                        .create()
                    progressDialog.show()

                    LoginPage.logout(requireContext()) {
                        // onSyncDone — called on Main thread after sync completes
                        progressDialog.dismiss()
                        val intent = Intent(requireContext(), LoginPage::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        requireActivity().finish()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // ── Request Account Deletion ───────────────────────────────────────────
        val btnRequestDeletion = view.findViewById<Button>(R.id.btnRequestDeletion)
        btnRequestDeletion.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Request Account Deletion")
                .setMessage("Are you sure you want to submit an account deletion request? This will compose an email to support@adyapan.com requesting the permanent removal of your account and all associated data.")
                .setPositiveButton("Submit Request") { _, _ ->
                    val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: "N/A"
                    val emailSubject = "Adyapan CRM - Account Deletion Request"
                    val emailBody = """
                        Dear Support Team,

                        I am writing to request the permanent deletion of my Adyapan CRM account and all associated personal data from your systems.

                        Account Details:
                        - Registered Email Address: $currentUserEmail

                        Please let me know once the deletion has been successfully processed.

                        Regards,
                        $currentUserEmail
                    """.trimIndent()

                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("support@adyapan.com"))
                        putExtra(Intent.EXTRA_SUBJECT, emailSubject)
                        putExtra(Intent.EXTRA_TEXT, emailBody)
                    }

                    try {
                        startActivity(Intent.createChooser(intent, "Send email..."))
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "No email application found", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // ── SIM Selection (dual-SIM only) ──────────────────────────────────────
        setupSimSelection(view)
    }

    // ── Change Password Dialog ─────────────────────────────────────────────────

    private fun showChangePasswordDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_change_password, null)
        dialog.setContentView(view)

        val etCurrent = view.findViewById<EditText>(R.id.etCurrentPassword)
        val etNew     = view.findViewById<EditText>(R.id.etNewPassword)
        val etConfirm = view.findViewById<EditText>(R.id.etConfirmPassword)
        val btnSubmit = view.findViewById<Button>(R.id.btnConfirmChangePassword)

        btnSubmit.setOnClickListener {
            val currentPass = etCurrent.text.toString().trim()
            val newPass     = etNew.text.toString().trim()
            val confirmPass = etConfirm.text.toString().trim()

            when {
                currentPass.isEmpty() -> {
                    etCurrent.error = "Enter current password"
                    etCurrent.requestFocus()
                    return@setOnClickListener
                }
                newPass.length < 8 -> {
                    etNew.error = "Minimum 8 characters"
                    etNew.requestFocus()
                    return@setOnClickListener
                }
                newPass != confirmPass -> {
                    etConfirm.error = "Passwords don't match"
                    etConfirm.requestFocus()
                    return@setOnClickListener
                }
            }

            btnSubmit.isEnabled = false
            btnSubmit.text = "Updating..."

            val user = FirebaseAuth.getInstance().currentUser
            val email = user?.email

            if (user == null || email == null) {
                Toast.makeText(requireContext(), "Not logged in", Toast.LENGTH_SHORT).show()
                btnSubmit.isEnabled = true
                btnSubmit.text = "Update Password"
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    // Step 1: Re-authenticate with current password
                    val credential = EmailAuthProvider.getCredential(email, currentPass)
                    withContext(Dispatchers.IO) {
                        user.reauthenticate(credential).await()
                    }

                    // Step 2: Update password in Firebase
                    withContext(Dispatchers.IO) {
                        user.updatePassword(newPass).await()
                    }

                    // Step 3: Sync new password to CRM (Supabase) backend
                    val synced = withContext(Dispatchers.IO) {
                        CrmApi.syncPasswordToCrm(newPass)
                    }

                    if (synced) {
                        Toast.makeText(
                            requireContext(),
                            "Password updated for both app and CRM website!",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "App password updated. CRM sync failed — will retry on next login.",
                            Toast.LENGTH_LONG
                        ).show()
                        // Save for retry on next app start
                        val prefs = LoginPage.getEncryptedPrefs(requireContext())
                        prefs.edit().putString("pending_password_sync", newPass).apply()
                    }

                    dialog.dismiss()
                } catch (e: Exception) {
                    val msg = when {
                        e.message?.contains("INVALID_LOGIN_CREDENTIALS") == true ||
                        e.message?.contains("wrong-password") == true ||
                        e.message?.contains("invalid-credential") == true ->
                            "Current password is incorrect"
                        e.message?.contains("requires-recent-login") == true ->
                            "Session expired. Please logout and login again."
                        else -> "Failed: ${e.message}"
                    }
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    btnSubmit.isEnabled = true
                    btnSubmit.text = "Update Password"
                }
            }
        }

        dialog.show()
    }

    // ── SIM picker UI ──────────────────────────────────────────────────────────

    private fun setupSimSelection(view: View) {
        val cardSimSelection    = view.findViewById<CardView>(R.id.cardSimSelection)
        val tvCallSettingsHeader = view.findViewById<TextView>(R.id.tvCallSettingsHeader)
        val tvCurrentSim        = view.findViewById<TextView>(R.id.tvCurrentSimSelection)
        val llSimButtons        = view.findViewById<LinearLayout>(R.id.llSimButtons)

        val accounts = SimManager.getAccounts(requireContext())

        // Only show this section on dual-SIM (2+ accounts)
        if (accounts.size < 2) return

        cardSimSelection.visibility    = View.VISIBLE
        tvCallSettingsHeader.visibility = View.VISIBLE

        // Update subtitle with currently saved selection
        tvCurrentSim.text = SimManager.getSelectedSummary(requireContext())

        // Build one button per SIM + "Ask Every Time"
        llSimButtons.removeAllViews()

        // "Ask Every Time" option (index = -1)
        addSimButton(
            container    = llSimButtons,
            label        = "Ask Every Time",
            sublabel     = "System will show SIM chooser on each call",
            isSelected   = SimManager.getSelectedIndex(requireContext()) == SimManager.INDEX_SYSTEM_DEFAULT,
            onClick      = {
                SimManager.saveSelectedIndex(requireContext(), SimManager.INDEX_SYSTEM_DEFAULT)
                tvCurrentSim.text = "Ask Every Time"
                refreshSimButtons(llSimButtons, accounts, tvCurrentSim, SimManager.INDEX_SYSTEM_DEFAULT)
                Toast.makeText(requireContext(), "SIM: Ask Every Time", Toast.LENGTH_SHORT).show()
            }
        )

        // One button for each SIM
        accounts.forEachIndexed { idx, handle ->
            val label    = SimManager.getLabel(requireContext(), handle, "SIM ${idx + 1}")
            val simLabel = "SIM ${idx + 1}  —  $label"
            addSimButton(
                container  = llSimButtons,
                label      = "$simLabel",
                sublabel   = "All calls will go through this SIM automatically",
                isSelected = SimManager.getSelectedIndex(requireContext()) == idx,
                onClick    = {
                    SimManager.saveSelectedIndex(requireContext(), idx)
                    tvCurrentSim.text = simLabel
                    refreshSimButtons(llSimButtons, accounts, tvCurrentSim, idx)
                    Toast.makeText(requireContext(), "Calls will use $simLabel", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    /**
     * Rebuilds SIM buttons after selection changes, so the highlighted state updates.
     */
    private fun refreshSimButtons(
        container   : LinearLayout,
        accounts    : List<android.telecom.PhoneAccountHandle>,
        tvCurrentSim: TextView,
        selectedIdx : Int
    ) {
        container.removeAllViews()

        addSimButton(
            container  = container,
            label      = "Ask Every Time",
            sublabel   = "System will show SIM chooser on each call",
            isSelected = selectedIdx == SimManager.INDEX_SYSTEM_DEFAULT,
            onClick    = {
                SimManager.saveSelectedIndex(requireContext(), SimManager.INDEX_SYSTEM_DEFAULT)
                tvCurrentSim.text = "Ask Every Time"
                refreshSimButtons(container, accounts, tvCurrentSim, SimManager.INDEX_SYSTEM_DEFAULT)
                Toast.makeText(requireContext(), "SIM: Ask Every Time", Toast.LENGTH_SHORT).show()
            }
        )

        accounts.forEachIndexed { idx, handle ->
            val label    = SimManager.getLabel(requireContext(), handle, "SIM ${idx + 1}")
            val simLabel = "SIM ${idx + 1}  —  $label"
            addSimButton(
                container  = container,
                label      = "$simLabel",
                sublabel   = "All calls will go through this SIM automatically",
                isSelected = selectedIdx == idx,
                onClick    = {
                    SimManager.saveSelectedIndex(requireContext(), idx)
                    tvCurrentSim.text = simLabel
                    refreshSimButtons(container, accounts, tvCurrentSim, idx)
                    Toast.makeText(requireContext(), "Calls will use $simLabel", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    /**
     * Programmatically creates a SIM choice row and appends it to [container].
     * Selected state is shown with a blue highlight border and bold text.
     */
    private fun addSimButton(
        container : LinearLayout,
        label     : String,
        sublabel  : String,
        isSelected: Boolean,
        onClick   : () -> Unit
    ) {
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density

        // Outer row container
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                (14 * density).toInt(), (12 * density).toInt(),
                (14 * density).toInt(), (12 * density).toInt()
            )
            val margin = (0 * density).toInt()
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, (6 * density).toInt(), 0, 0) }
            layoutParams = lp

            // Highlight selected option
            background = if (isSelected)
                ContextCompat.getDrawable(ctx, R.drawable.bg_neu_btn_blue)
            else
                ContextCompat.getDrawable(ctx, R.drawable.bg_search_dark)

            isClickable = true
            isFocusable = true
        }

        // Text column
        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvLabel = TextView(ctx).apply {
            text = label
            textSize = 14f
            setTypeface(typeface, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(if (isSelected) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt())
            setPadding(0, 0, 0, (2 * density).toInt())
        }

        val tvSub = TextView(ctx).apply {
            text = sublabel
            textSize = 11f
            setTextColor(if (isSelected) 0xCCFFFFFF.toInt() else 0xFF9CA3AF.toInt())
        }

        textCol.addView(tvLabel)
        textCol.addView(tvSub)

        // Checkmark indicator
        val tvCheck = TextView(ctx).apply {
            text = if (isSelected) "✓" else ""
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding((8 * density).toInt(), 0, 0, 0)
        }

        row.addView(textCol)
        row.addView(tvCheck)
        row.setOnClickListener { onClick() }

        container.addView(row)
    }
}