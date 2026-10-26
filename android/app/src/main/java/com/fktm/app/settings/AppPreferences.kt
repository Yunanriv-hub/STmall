package com.fktm.app.settings

import android.content.Context
import com.water.widget.GlassPreferences

enum class ThemeMode(val storedValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStored(value: String?): ThemeMode =
            entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}

/**
 * Non-sensitive appearance and home-layout preferences.
 */
class AppPreferences(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = ThemeMode.fromStored(prefs.getString(KEY_THEME_MODE, null))
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.storedValue).apply()

    var liquidGlassEnabled: Boolean
        get() = GlassPreferences.isEnabled(appContext)
        set(value) = GlassPreferences.setEnabled(appContext, value)

    var nfcAutoStartWater: Boolean
        get() = prefs.getBoolean(KEY_NFC_AUTO_START_WATER, false)
        set(value) = prefs.edit().putBoolean(KEY_NFC_AUTO_START_WATER, value).apply()

    fun enabledHomeModules(): Set<String> {
        val saved = prefs.getStringSet(KEY_ENABLED_HOME_MODULES, null)
        if (saved == null) {
            prefs.edit().putBoolean(KEY_ADDED_ILIFE_NFC, true).apply()
            return DEFAULT_HOME_MODULES
        }
        val sanitized = saved - LEGACY_HOME_MODULES
        val withQuickWater = if (prefs.getBoolean(KEY_ADDED_QUICK_WATER, false)) {
            sanitized
        } else {
            sanitized + MODULE_QUICK_WATER
        }
        if (sanitized != saved || withQuickWater != saved) {
            prefs.edit()
                .putStringSet(KEY_ENABLED_HOME_MODULES, withQuickWater)
                .putBoolean(KEY_ADDED_ILIFE_NFC, true)
                .putBoolean(KEY_ADDED_QUICK_WATER, true)
                .apply()
            return withQuickWater
        }
        if (prefs.getBoolean(KEY_ADDED_ILIFE_NFC, false)) return sanitized

        // Existing installations keep their choices while gaining the two new modules once.
        val migrated = sanitized.toMutableSet().apply {
            add(MODULE_ILIFE)
        }.toSet()
        prefs.edit()
            .putStringSet(KEY_ENABLED_HOME_MODULES, migrated)
            .putBoolean(KEY_ADDED_ILIFE_NFC, true)
            .apply()
        return migrated
    }

    fun setHomeModuleEnabled(key: String, enabled: Boolean) {
        val next = enabledHomeModules().toMutableSet()
        if (enabled) next += key else next -= key
        prefs.edit()
            .putStringSet(KEY_ENABLED_HOME_MODULES, next)
            .putBoolean(KEY_ADDED_ILIFE_NFC, true)
            .putBoolean(KEY_ADDED_QUICK_WATER, true)
            .apply()
    }

    fun setEnabledHomeModules(keys: Set<String>) {
        prefs.edit()
            .putStringSet(KEY_ENABLED_HOME_MODULES, keys.toSet())
            .putBoolean(KEY_ADDED_ILIFE_NFC, true)
            .putBoolean(KEY_ADDED_QUICK_WATER, true)
            .apply()
    }

    fun resetHomeModules() {
        prefs.edit()
            .putStringSet(KEY_ENABLED_HOME_MODULES, DEFAULT_HOME_MODULES)
            .putBoolean(KEY_ADDED_ILIFE_NFC, true)
            .putBoolean(KEY_ADDED_QUICK_WATER, true)
            .apply()
    }

    companion object {
        const val MODULE_SMART_DORM = "smart_dorm"
        const val MODULE_QUICK_UNLOCK = "quick_unlock"
        const val MODULE_ILIFE = "ilife"
        const val MODULE_QUICK_WATER = "quick_water"

        private const val LEGACY_MODULE_SCANNER = "scanner"
        private const val LEGACY_MODULE_NFC = "nfc"
        private val LEGACY_HOME_MODULES = setOf(LEGACY_MODULE_SCANNER, LEGACY_MODULE_NFC)

        val DEFAULT_HOME_MODULES: Set<String> = setOf(
            MODULE_SMART_DORM,
            MODULE_QUICK_UNLOCK,
            MODULE_ILIFE,
            MODULE_QUICK_WATER,
        )

        private const val PREFS = "campus_lite_appearance"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ENABLED_HOME_MODULES = "enabled_home_modules"
        private const val KEY_ADDED_ILIFE_NFC = "added_ilife_nfc"
        private const val KEY_NFC_AUTO_START_WATER = "nfc_auto_start_water"
        private const val KEY_ADDED_QUICK_WATER = "added_quick_water"
    }
}
