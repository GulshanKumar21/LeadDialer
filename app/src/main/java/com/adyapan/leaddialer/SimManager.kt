package com.adyapan.leaddialer

import android.content.Context
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log

/**
 * Manages SIM selection for outgoing calls.
 *
 * On dual-SIM devices, Android normally shows a "Choose SIM" picker every time.
 * By storing the user's preferred SIM here and passing its [PhoneAccountHandle]
 * to [TelecomManager.placeCall], we bypass that picker entirely.
 *
 * Permission required: READ_PHONE_STATE (already declared & requested in MainActivity).
 */
object SimManager {

    private const val TAG = "SimManager"

    const val PREF_NAME          = "sim_prefs"
    const val KEY_SELECTED_INDEX = "selected_sim_index"

    // -1 means "let the system decide" (shows system SIM picker on dual-SIM)
    const val INDEX_SYSTEM_DEFAULT = -1

    // ── SIM account list ──────────────────────────────────────────────────────

    /**
     * Returns all call-capable phone accounts (one entry per active SIM slot).
     * Returns an empty list on single-SIM devices or if an error occurs.
     */
    fun getAccounts(context: Context): List<PhoneAccountHandle> {
        return try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.callCapablePhoneAccounts ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "getAccounts error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Human-readable display label for a SIM account
     * (e.g., "Airtel", "Jio", "SIM 1"). Falls back to "SIM N" if unavailable.
     */
    fun getLabel(context: Context, handle: PhoneAccountHandle, fallback: String = "SIM"): String {
        return try {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.getPhoneAccount(handle)?.label?.toString()?.takeIf { it.isNotBlank() } ?: fallback
        } catch (e: Exception) {
            Log.e(TAG, "getLabel error: ${e.message}")
            fallback
        }
    }

    /**
     * Returns a list of display names like ["SIM 1 — Airtel", "SIM 2 — Jio"].
     * Always prepends an "Ask Every Time" option at index 0 in the UI list.
     */
    fun getDisplayNames(context: Context): List<String> {
        return getAccounts(context).mapIndexed { i, handle ->
            val label = getLabel(context, handle, "SIM ${i + 1}")
            "SIM ${i + 1}  —  $label"
        }
    }

    // ── Preference storage ────────────────────────────────────────────────────

    fun saveSelectedIndex(context: Context, index: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SELECTED_INDEX, index).apply()
        Log.d(TAG, "Saved selected SIM index: $index")
    }

    fun getSelectedIndex(context: Context): Int =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_SELECTED_INDEX, INDEX_SYSTEM_DEFAULT)

    /**
     * Returns the [PhoneAccountHandle] for the user-selected SIM,
     * or `null` if the user chose "Ask Every Time" or no SIM is selected.
     */
    fun getSelectedHandle(context: Context): PhoneAccountHandle? {
        val idx = getSelectedIndex(context)
        if (idx == INDEX_SYSTEM_DEFAULT) return null
        return getAccounts(context).getOrNull(idx)
    }

    /** Returns a human-readable summary of the current selection, e.g. "SIM 2 — Jio". */
    fun getSelectedSummary(context: Context): String {
        val idx = getSelectedIndex(context)
        if (idx == INDEX_SYSTEM_DEFAULT) return "Ask Every Time"
        val handle = getAccounts(context).getOrNull(idx) ?: return "Ask Every Time"
        return "SIM ${idx + 1}  —  ${getLabel(context, handle, "SIM ${idx + 1}")}"
    }
}
