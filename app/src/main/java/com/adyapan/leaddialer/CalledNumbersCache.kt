package com.adyapan.leaddialer

import android.content.Context
import org.json.JSONObject

object CalledNumbersCache {

    private const val PREF         = "called_cache"
    private const val KEY_DATA     = "cached_data"
    private const val KEY_TIME     = "cache_time"
    private const val CACHE_EXPIRY = 5 * 60 * 1000L  // 5 minutes

    fun save(context: Context, results: Map<String, CallCheckResult>) {
        val json = JSONObject()
        results.forEach { (phone, result) ->
            json.put(phone, JSONObject().apply {
                put("called",   result.called)
                put("calledBy", result.calledBy)
                put("status",   result.status)
            })
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DATA, json.toString())
            .putLong(KEY_TIME, System.currentTimeMillis())
            .apply()
    }

    fun check(context: Context, phone: String): CallCheckResult? {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        val cacheTime = prefs.getLong(KEY_TIME, 0L)
        if (System.currentTimeMillis() - cacheTime > CACHE_EXPIRY) {
            return null
        }

        val dataStr = prefs.getString(KEY_DATA, null) ?: return null

        return try {
            val json      = JSONObject(dataStr)
            val cleanPhone = phone.filter { it.isDigit() }

            if (json.has(cleanPhone)) {
                val obj = json.getJSONObject(cleanPhone)
                CallCheckResult(
                    called   = obj.optBoolean("called", false),
                    calledBy = obj.optString("calledBy", ""),
                    status   = obj.optString("status", "")
                )
            } else {
                CallCheckResult(false, "", "")
            }
        } catch (e: Exception) {
            null
        }
    }

    // ── Cache clear karo ──────────────────────────────────────────────────
    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    // ── Cache kitna purana hai ────────────────────────────────────────────
    fun getAge(context: Context): Long {
        val cacheTime = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getLong(KEY_TIME, 0L)
        return System.currentTimeMillis() - cacheTime
    }
}