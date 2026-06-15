package com.adyapan.leaddialer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles both:
 *  1. Outgoing call state tracking (OFFHOOK → IDLE = real call ended → show popup)
 *  2. Incoming call lead lookup (show notification if caller is a known lead)
 *
 * Dual-SIM safe strategy:
 *  - KEY_PENDING (written by CallManager.onCallInitiated) is the AUTHORITATIVE flag
 *    that a call was launched from THIS app.
 *  - Some OEM dual-SIM phones (Xiaomi, Samsung, Realme) fire RINGING briefly BEFORE
 *    OFFHOOK even for outgoing calls (SIM picker causes this). We cannot rely on
 *    `wasRinging` alone to distinguish incoming vs outgoing.
 *  - Rule: if KEY_PENDING == true at OFFHOOK time → it is OUR outgoing call, period.
 *  - wasRinging is only used to suppress popup for pure incoming calls where
 *    KEY_PENDING was never set.
 */
class IncomingCallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "IncomingCallReceiver"

        // Static — survive across short-lived BroadcastReceiver instances
        @Volatile private var lastState  = TelephonyManager.CALL_STATE_IDLE
        @Volatile private var wasOffhook = false
        @Volatile private var wasRinging = false  // true = ringing was seen (possibly SIM picker)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr       = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

        val currentState = when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            else                                 -> TelephonyManager.CALL_STATE_IDLE
        }

        val prefs = context.getSharedPreferences(CallManager.PREF_NAME, Context.MODE_PRIVATE)
        val isPending = prefs.getBoolean(CallManager.KEY_PENDING, false)

        Log.d(TAG, "State: $stateStr | last=$lastState | wasOffhook=$wasOffhook | wasRinging=$wasRinging | isPending=$isPending")

        when (currentState) {

            TelephonyManager.CALL_STATE_RINGING -> {
                wasRinging = true
                val isIncomingCall = prefs.getBoolean("is_incoming_call", false)

                // Show lead notification ONLY if this is a true incoming call (no outgoing pending call)
                if (!isPending || (isPending && isIncomingCall)) {
                    val savedPhone = prefs.getString("call_phone", "")
                    
                    if (incomingNumber.isNotBlank() && savedPhone != incomingNumber) {
                        // Trigger async database check. If it is a lead, SharedPreferences will be updated inside.
                        handleIncomingCall(context, incomingNumber)
                    }
                } else {
                    Log.d(TAG, "RINGING ignored for lead lookup — this is our outgoing call")
                }
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                wasOffhook = true

                // KEY DUAL-SIM FIX:
                // If KEY_PENDING is true → this OFFHOOK is for OUR outgoing call,
                // regardless of whether wasRinging was briefly set by the SIM picker.
                if (isPending) {
                    val now = System.currentTimeMillis()
                    prefs.edit()
                        .putBoolean(CallManager.KEY_CONNECTED, true)
                        .putLong(CallManager.KEY_OFFHOOK_TIME, now)   // ← record real connect time
                        .apply()
                    Log.d(TAG, "OFFHOOK (our outgoing call, isPending=true) — KEY_CONNECTED=true, KEY_OFFHOOK_TIME=$now")
                } else if (!wasRinging) {
                    // No KEY_PENDING and no RINGING → shouldn't happen, but handle defensively
                    Log.d(TAG, "OFFHOOK with no pending call and no ringing — ignoring")
                } else {
                    // Pure incoming call was answered — don't touch our outgoing call flags
                    Log.d(TAG, "OFFHOOK (incoming answered, no KEY_PENDING) — ignoring for outgoing tracking")
                }
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                if (wasOffhook) {
                    val connected = prefs.getBoolean(CallManager.KEY_CONNECTED, false)

                    if (connected) {
                        // Our outgoing call ended — write actual end time
                        val actualEnd = System.currentTimeMillis()
                        prefs.edit()
                            .putLong(CallManager.KEY_ACTUAL_END, actualEnd)
                            .putBoolean(CallManager.KEY_CONNECTED, false)
                            .apply()
                        Log.d(TAG, "Outgoing call ended → KEY_ACTUAL_END written, launching popup")

                        // ✅ Directly launch CallPopupActivity so popup ALWAYS appears
                        // regardless of whether MainActivity is in foreground or not
                        launchCallPopup(context, prefs, actualEnd)

                    } else {
                        // Incoming call ended or edge case — cleanup only
                        Log.d(TAG, "IDLE after OFFHOOK but KEY_CONNECTED=false — incoming call ended, no popup")
                    }
                } else {
                    // IDLE without OFFHOOK = call rejected / SIM picker cancelled / missed call
                    Log.d(TAG, "IDLE without OFFHOOK — call never connected (SIM cancel / reject / missed)")
                    
                    val isIncoming = prefs.getBoolean("is_incoming_call", false)
                    if (isIncoming) {
                        // Missed incoming call - clean up state so it doesn't block future calls
                        prefs.edit()
                            .putBoolean(CallManager.KEY_PENDING, false)
                            .putBoolean("is_incoming_call", false)
                            .apply()
                        CallManager.callActive = false
                        Log.d(TAG, "Cleared pending state for missed incoming call")
                    } else {
                        // KEY_PENDING intentionally left so next real call attempt still works.
                        // No KEY_ACTUAL_END written → no popup. ✅
                    }
                }

                // Reset state flags on IDLE
                wasOffhook = false
                wasRinging = false
            }
        }

        lastState = currentState
    }

    // ── Incoming call: look up lead and show notification ─────────────────────

    private fun handleIncomingCall(context: Context, rawNumber: String) {
        val cleanNumber = rawNumber.filter { it.isDigit() }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db   = AppDatabase.getInstance(context)
                var lead = db.leadDao().getAllOnce().firstOrNull { l ->
                    val stored = l.phone.filter { it.isDigit() }
                    stored.takeLast(10) == cleanNumber.takeLast(10)
                }
                
                // Fallback: If not in leads, check Call History (for manual dials)
                if (lead == null) {
                    val record = db.callRecordDao().getAllOnce().lastOrNull { r ->
                        val stored = r.phone.filter { it.isDigit() }
                        stored.takeLast(10) == cleanNumber.takeLast(10)
                    }
                    if (record != null) {
                        val finalName = if (record.name.isNotBlank() && record.name != "Unknown Number") record.name else "Unknown (Called Before)"
                        lead = Lead(name = finalName, phone = record.phone, status = record.status)
                    }
                }

                if (lead != null) {
                    Log.d(TAG, "Lead found for incoming: ${lead.name}")
                    
                    // Save to SharedPreferences so the app knows a lead call is active and can show the popup when it ends
                    val prefs = context.getSharedPreferences(CallManager.PREF_NAME, Context.MODE_PRIVATE)
                    prefs.edit()
                        .putBoolean(CallManager.KEY_PENDING, true)
                        .putBoolean("is_incoming_call", true)
                        .putBoolean(CallManager.KEY_CONNECTED, false)
                        .putLong("call_start_time", System.currentTimeMillis())
                        .putString("call_phone", rawNumber)
                        .putString("call_name", lead.name)
                        .apply()
                        
                    CallManager.callActive = true

                    // Show a Toast over the incoming call screen
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "📞 ADYAPAN LEAD: ${lead.name}\n" +
                            "Status: ${lead.status.ifBlank { "Pending" }}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        
                        // Repeat toast so it stays on screen longer
                        kotlinx.coroutines.delay(2500)
                        android.widget.Toast.makeText(
                            context,
                            "📞 ADYAPAN LEAD: ${lead.name}\n" +
                            "Status: ${lead.status.ifBlank { "Pending" }}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    IncomingCallNotificationHelper.show(context, lead)
                } else {
                    Log.d(TAG, "No lead for incoming number: $rawNumber")
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleIncomingCall error: ${e.message}")
            }
        }
    }

    /**
     * Dual-SIM safety check: if the OFFHOOK→IDLE was less than 2s, it was a SIM picker
     * false trigger — reset timing keys and keep KEY_PENDING armed for the real call.
     * Otherwise, build a CallRecord and launch CallPopupActivity immediately.
     */
    private fun launchCallPopup(context: Context, prefs: android.content.SharedPreferences, actualEnd: Long) {
        val offhookTime = prefs.getLong(CallManager.KEY_OFFHOOK_TIME, 0L)
        val startTime   = prefs.getLong("call_start_time", 0L)
        val phone       = prefs.getString("call_phone", "") ?: ""
        val name        = prefs.getString("call_name", "") ?: ""

        val connectedMs = if (offhookTime > 0L) actualEnd - offhookTime
                          else actualEnd - startTime

        val isIncoming  = prefs.getBoolean("is_incoming_call", false)

        // Dual-SIM false trigger: SIM picker OFFHOOK→IDLE < 2s — ignore this one (for OUTGOING only)
        if (!isIncoming && connectedMs < 2000L) {
            Log.d(TAG, "Connected duration ${connectedMs}ms < 2s — dual-SIM false trigger, resetting timings only")
            prefs.edit()
                .putLong(CallManager.KEY_ACTUAL_END, 0L)
                .putLong(CallManager.KEY_OFFHOOK_TIME, 0L)
                .putBoolean(CallManager.KEY_CONNECTED, false)
                .apply()
            // wasOffhook / wasRinging will be reset by caller
            return
        }

        // Real call ended — compute duration and build record
        val duration   = (connectedMs / 1000).coerceAtLeast(0L)
        val calledAt   = if (startTime > 0L) startTime else actualEnd - connectedMs

        // Format time for display: "Called at HH:mm AM/PM"
        val timeFmt    = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val calledAtStr = timeFmt.format(Date(calledAt))

        Log.d(TAG, "Real call ended: name=$name phone=$phone duration=${duration}s calledAt=$calledAtStr")

        // DO NOT clear state here. Android 10+ might block startActivity from background.
        // We leave the state in SharedPreferences.
        // If CallPopupActivity successfully opens, it will clear the state in onCreate.
        // If it gets blocked, MainActivity.onResume will see the state and show its own popup.

        val record = CallRecord(
            phone     = phone,
            name      = name,
            duration  = duration,
            calledAt  = calledAt,
            status    = "Pending"
        )

        // Launch CallPopupActivity — always shows, even when app is in background
        val intent = Intent(context, CallPopupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_record", record)
            putExtra("called_at_str", calledAtStr)   // human-readable time string
        }
        context.startActivity(intent)
    }
}
