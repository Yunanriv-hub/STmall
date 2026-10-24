package com.fktm.app.settings

import android.content.Context

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

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = ThemeMode.fromStored(prefs.getString(KEY_THEME_MODE, null))
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.storedValue).apply()

    fun enabledHomeModules(): Set<String> {
        val saved = prefs.getStringSet(KEY_ENABLED_HOME_MODULES, null)
        if (saved == null) {
            prefs.edit().putBoolean(KEY_ADDED_ILIFE_NFC, true).apply()
            return DEFAULT_HOME_MODULES
        }
        if (prefs.getBoolean(KEY_ADDED_ILIFE_NFC, false)) return saved.toSet()

        // Existing installations keep their choices while gaining the two new modules once.
        val migrated = saved.toMutableSet().apply {
            add(MODULE_ILIFE)
            add(MODULE_NFC)
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
            .apply()
    }

    fun setEnabledHomeModules(keys: Set<String>) {
        prefs.edit()
            .putStringSet(KEY_ENABLED_HOME_MODULES, keys.toSet())
            .putBoolean(KEY_ADDED_ILIFE_NFC, true)
            .apply()
    }

    fun resetHomeModules() {
        prefs.edit()
            .putStringSet(KEY_ENABLED_HOME_MODULES, DEFAULT_HOME_MODULES)
            .putBoolean(KEY_ADDED_ILIFE_NFC, true)
            .apply()
    }

    companion object {
        const val MODULE_SMART_DORM = "smart_dorm"
        const val MODULE_SCANNER = "scanner"
        const val MODULE_QUICK_UNLOCK = "quick_unlock"
        const val MODULE_ILIFE = "ilife"
        const val MODULE_NFC = "nfc"

        val DEFAULT_HOME_MODULES: Set<String> = setOf(
            MODULE_SMART_DORM,
            MODULE_SCANNER,
            MODULE_QUICK_UNLOCK,
            MODULE_ILIFE,
            MODULE_NFC,
        )

        private const val PREFS = "campus_lite_appearance"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ENABLED_HOME_MODULES = "enabled_home_modules"
        private const val KEY_ADDED_ILIFE_NFC = "added_ilife_nfc"
    }
}
