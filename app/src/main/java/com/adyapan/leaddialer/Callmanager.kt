package com.adyapan.leaddialer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log

object CallManager {

    private const val TAG = "CallManager"

    // Public — shared with IncomingCallReceiver so both write to the same prefs file
    const val PREF_NAME      = "call_manager_prefs"
    const val KEY_PENDING    = "call_pending"
    const val KEY_CONNECTED  = "call_connected"
    const val KEY_ACTUAL_END = "call_actual_end_time"
    const val KEY_OFFHOOK_TIME = "call_offhook_time"   // when OFFHOOK fired (real connect time)

    private const val KEY_START = "call_start_time"
    private const val KEY_PHONE = "call_phone"
    private const val KEY_NAME  = "call_name"

    // ── Global state — accessible from any Activity / Fragment ─────────
    /** The lead for which a call was most recently initiated. */
    @Volatile var currentLead: Lead? = null

    /** True once startCall() is triggered; cleared after popup is shown. */
    @Volatile var callActive: Boolean = false

    /**
     * Saves call metadata to SharedPrefs so the state survives process changes.
     * Called by both [onCallInitiated] and [placeCall].
     */
    private fun saveCallPrefs(context: Context, lead: Lead) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_START,       System.currentTimeMillis())
            .putLong(KEY_ACTUAL_END,  0L)
            .putLong(KEY_OFFHOOK_TIME, 0L)
            .putBoolean(KEY_CONNECTED, false)
            .putString(KEY_PHONE,     lead.phone)
            .putString(KEY_NAME,      lead.name)
            .putBoolean(KEY_PENDING,  true)
            .putBoolean("is_incoming_call", false)
            .apply()
    }

    /**
     * Legacy: saves call metadata and returns an ACTION_CALL intent.
     * Use [placeCall] for dual-SIM-aware calling instead.
     */
    fun onCallInitiated(context: Context, lead: Lead): Intent {
        Log.d(TAG, "onCallInitiated: ${lead.name} | ${lead.phone}")
        saveCallPrefs(context, lead)
        return Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${lead.phone}")
        }
    }

    /**
     * Places an outgoing call, automatically using the SIM selected in app Settings.
     *
     * - If the user has selected a specific SIM: calls [TelecomManager.placeCall] directly,
     *   which bypasses the system SIM-chooser popup entirely.
     * - If the user chose "Ask Every Time" (default): falls back to ACTION_CALL so Android
     *   can show its normal SIM picker.
     *
     * @param context        Any valid context (applicationContext is fine).
     * @param lead           The lead being called.
     * @param activityStarter Lambda that calls [startActivity] on the fallback intent.
     *                        Only invoked when TelecomManager path is NOT used.
     */
    fun placeCall(context: Context, lead: Lead, activityStarter: (Intent) -> Unit) {
        Log.d(TAG, "placeCall: ${lead.name} | ${lead.phone}")
        saveCallPrefs(context, lead)

        val selectedHandle = SimManager.getSelectedHandle(context)

        if (selectedHandle != null) {
            // ── SIM selected by user: bypass system SIM picker ────────────────
            try {
                val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                val extras = Bundle().apply {
                    putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, selectedHandle)
                }
                tm.placeCall(Uri.parse("tel:${lead.phone}"), extras)
                Log.d(TAG, "TelecomManager.placeCall() called on SIM: ${SimManager.getLabel(context, selectedHandle)}")
                return   // done — no startActivity needed
            } catch (e: Exception) {
                Log.e(TAG, "TelecomManager.placeCall failed (${e.message}), falling back to ACTION_CALL")
                // Fall through to intent fallback below
            }
        }

        // ── Fallback: standard ACTION_CALL intent ─────────────────────────────
        // Includes the handle as a hint; some OEMs honour it and skip the picker.
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:${lead.phone}")
            if (selectedHandle != null) {
                putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, selectedHandle)
            }
        }
        activityStarter(intent)
        Log.d(TAG, "ACTION_CALL started (system SIM picker may appear)")
    }

    /**
     * Call this from [LeadsFragment.onResume].
     *
     * Returns a [CallRecord] ONLY when:
     *  1. A call was initiated ([KEY_PENDING] = true), AND
     *  2. The phone state receiver detected OFFHOOK → IDLE ([KEY_ACTUAL_END] > 0).
     *
     * This prevents the dialog from appearing when:
     *  - The dialer first opens (app pauses but no OFFHOOK yet)
     *  - A SIM-picker dialog appears mid-flow
     *  - The call was never answered (rejected/missed)
     */
    fun checkOnResume(context: Context): CallRecord? {
        val prefs     = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val isPending = prefs.getBoolean(KEY_PENDING, false)

        Log.d(TAG, "checkOnResume: isPending=$isPending")
        if (!isPending) return null

        val startTime   = prefs.getLong(KEY_START,      0L)
        val actualEnd   = prefs.getLong(KEY_ACTUAL_END,  0L)
        val phone       = prefs.getString(KEY_PHONE,    "") ?: ""
        val name        = prefs.getString(KEY_NAME,     "") ?: ""

        Log.d(TAG, "startTime=$startTime | actualEnd=$actualEnd | phone=$phone")

        // Guard: basic data must be present
        if (startTime == 0L || phone.isBlank()) {
            prefs.edit().putBoolean(KEY_PENDING, false).apply()
            return null
        }

        // KEY guard: only show dialog if receiver confirmed OFFHOOK→IDLE
        var finalEnd = actualEnd
        var callLogDuration: Long? = null

        if (finalEnd == 0L) {
            Log.d(TAG, "actualEnd not set yet — performing call log fallback check")
            // Try to find the call log entry for this phone number from the last 2 minutes
            val resolver = context.contentResolver
            try {
                val cursor = resolver.query(
                    android.provider.CallLog.Calls.CONTENT_URI,
                    arrayOf(android.provider.CallLog.Calls.DATE, android.provider.CallLog.Calls.DURATION),
                    "${android.provider.CallLog.Calls.NUMBER} = ? AND ${android.provider.CallLog.Calls.TYPE} = ?",
                    arrayOf(phone, android.provider.CallLog.Calls.OUTGOING_TYPE.toString()),
                    "${android.provider.CallLog.Calls.DATE} DESC LIMIT 1"
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val dateIndex = it.getColumnIndex(android.provider.CallLog.Calls.DATE)
                        val durationIndex = it.getColumnIndex(android.provider.CallLog.Calls.DURATION)
                        if (dateIndex >= 0 && durationIndex >= 0) {
                            val timestamp = it.getLong(dateIndex)
                            val dur = it.getLong(durationIndex)
                            // If the call log timestamp is after our start time - 30 seconds
                            if (timestamp > startTime - 30000L && System.currentTimeMillis() - timestamp < 120000L) {
                                finalEnd = timestamp + (dur * 1000)
                                callLogDuration = dur
                                Log.d(TAG, "Fallback call log match found: duration=$dur, timestamp=$timestamp")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Call log fallback check failed: ${e.message}")
            }
        }

        if (finalEnd == 0L) {
            Log.d(TAG, "actualEnd not set yet and fallback failed — call not ended, skipping dialog")
            return null
        }

        val duration = callLogDuration ?: run {
            val offhookTime = prefs.getLong(KEY_OFFHOOK_TIME, 0L)
            val connectedMs = if (offhookTime > 0L) finalEnd - offhookTime
                              else finalEnd - startTime
            (connectedMs / 1000).toLong().coerceAtLeast(0L)
        }

        if (duration < 2L) {
            Log.d(TAG, "Duration ${duration}s < 2s — ignoring short call / picker cancel")
            if (actualEnd > 0L) {
                // Reset timing keys only if it was a real receiver trigger (to allow real call later)
                prefs.edit()
                    .putLong(KEY_ACTUAL_END,   0L)
                    .putLong(KEY_OFFHOOK_TIME, 0L)
                    .putBoolean(KEY_CONNECTED, false)
                    .apply()
            } else {
                // Clear state if it was a fallback match that was too short
                prefs.edit()
                    .putBoolean(KEY_PENDING,   false)
                    .putLong(KEY_ACTUAL_END,   0L)
                    .putLong(KEY_OFFHOOK_TIME, 0L)
                    .apply()
            }
            return null
        }

        // All good — compute duration and clear state
        Log.d(TAG, "Call confirmed ended — duration=${duration}s")

        prefs.edit()
            .putBoolean(KEY_PENDING,   false)
            .putLong(KEY_ACTUAL_END,   0L)
            .putLong(KEY_OFFHOOK_TIME, 0L)
            .apply()

        return CallRecord(
            name     = name,
            phone    = phone,
            duration = duration,
            calledAt = startTime,
            status   = "Connected"
        )
    }

    /** Human-readable duration string. */
    fun formatDuration(seconds: Long): String = when {
        seconds <= 0 -> "0s"
        seconds < 60 -> "${seconds}s"
        else         -> "${seconds / 60}m ${seconds % 60}s"
    }
}