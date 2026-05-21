package com.adyapan.leaddialer

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * ThemeManager — handles Dark / Light mode toggle.
 *
 * Usage:
 *   ThemeManager.applyTheme(context)          // call on app start
 *   ThemeManager.setDarkMode(context, true)   // enable dark mode
 *   ThemeManager.isDarkMode(context)          // check current
 *   ThemeManager.toggle(context)              // flip current mode
 */
object ThemeManager {

    private const val PREF_NAME  = "theme_prefs"
    private const val KEY_DARK   = "dark_mode"

    /** Apply saved theme on app startup — call in Application or MainActivity.onCreate() */
    fun applyTheme(context: Context) {
        val mode = if (isDarkMode(context))
            AppCompatDelegate.MODE_NIGHT_YES
        else
            AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    /** Enable or disable dark mode and persist the preference */
    fun setDarkMode(context: Context, enable: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK, enable)
            .apply()

        val mode = if (enable)
            AppCompatDelegate.MODE_NIGHT_YES
        else
            AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    /** Toggle between dark and light */
    fun toggle(context: Context) = setDarkMode(context, !isDarkMode(context))

    /** Returns true if dark mode is currently saved */
    fun isDarkMode(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK, false)
}
