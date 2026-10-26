package com.water.widget

import android.content.Context

/** Shared appearance switch used by the host app and the embedded Life798 module. */
object GlassPreferences {
    private const val PREFS = "campus_lite_appearance"
    private const val KEY_LIQUID_GLASS = "liquid_glass_enabled"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_LIQUID_GLASS, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LIQUID_GLASS, enabled)
            .apply()
    }
}
