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

    var ujingAutoRefresh: Boolean
        get() = prefs.getBoolean(KEY_UJING_AUTO_REFRESH, false)
        set(value) = prefs.edit().putBoolean(KEY_UJING_AUTO_REFRESH, value).apply()

    var ujingFreeOnly: Boolean
        get() = prefs.getBoolean(KEY_UJING_FREE_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_UJING_FREE_ONLY, value).apply()

    var ujingSortMode: String
        get() = prefs.getString(KEY_UJING_SORT_MODE, "added").orEmpty()
        set(value) = prefs.edit().putString(KEY_UJING_SORT_MODE, value).apply()

    var backgroundImageUri: String?
        get() = prefs.getString(KEY_BACKGROUND_IMAGE_URI, null)
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_BACKGROUND_IMAGE_URI)
            else putString(KEY_BACKGROUND_IMAGE_URI, value)
        }.apply()

    /** 「允许后台运行」引导是否已经弹过（每个安装只弹一次）。 */
    var batteryPromptShown: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_PROMPT_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_BATTERY_PROMPT_SHOWN, value).apply()

    fun enabledHomeModules(): Set<String> {
        val saved = prefs.getStringSet(KEY_ENABLED_HOME_MODULES, null)
        if (saved == null) {
            prefs.edit()
                .putBoolean(KEY_ADDED_ILIFE_NFC, true)
                .putBoolean(KEY_ADDED_QUICK_WATER, true)
                .putBoolean(KEY_ADDED_UJING, true)
                .putBoolean(KEY_ADDED_UJING_SCAN, true)
                .putBoolean(KEY_ADDED_PANGGUAI, true)
                .putBoolean(KEY_ADDED_PANGGUAI_SCAN, true)
                .putBoolean(KEY_ADDED_PERFECT_CAMPUS, true)
                .apply()
            return DEFAULT_HOME_MODULES
        }
        val sanitized = saved - LEGACY_HOME_MODULES
        val migrated = sanitized.toMutableSet()
        var changed = sanitized != saved
        fun addOnce(flag: String, module: String) {
            if (!prefs.getBoolean(flag, false)) {
                migrated += module
                prefs.edit().putBoolean(flag, true).apply()
                changed = true
            }
        }
        addOnce(KEY_ADDED_ILIFE_NFC, MODULE_ILIFE)
        addOnce(KEY_ADDED_QUICK_WATER, MODULE_QUICK_WATER)
        addOnce(KEY_ADDED_UJING, MODULE_UJING)
        addOnce(KEY_ADDED_UJING_SCAN, MODULE_UJING_SCAN)
        addOnce(KEY_ADDED_PANGGUAI, MODULE_PANGGUAI)
        addOnce(KEY_ADDED_PANGGUAI_SCAN, MODULE_PANGGUAI_SCAN)
        addOnce(KEY_ADDED_PERFECT_CAMPUS, MODULE_PERFECT_CAMPUS)
        if (changed) {
            prefs.edit().putStringSet(KEY_ENABLED_HOME_MODULES, migrated).apply()
        }
        return migrated
    }

    fun setHomeModuleEnabled(key: String, enabled: Boolean) {
        val next = enabledHomeModules().toMutableSet()
        if (enabled) next += key else next -= key
        prefs.edit()
            .putStringSet(KEY_ENABLED_HOME_MODULES, next)
            .putBoolean(KEY_ADDED_ILIFE_NFC, true)
            .putBoolean(KEY_ADDED_QUICK_WATER, true)
            .putBoolean(KEY_ADDED_UJING, true)
            .putBoolean(KEY_ADDED_UJING_SCAN, true)
            .putBoolean(KEY_ADDED_PANGGUAI, true)
            .putBoolean(KEY_ADDED_PANGGUAI_SCAN, true)
            .putBoolean(KEY_ADDED_PERFECT_CAMPUS, true)
            .apply()
    }

    fun setEnabledHomeModules(keys: Set<String>) {
        prefs.edit()
            .putStringSet(KEY_ENABLED_HOME_MODULES, keys.toSet())
            .putBoolean(KEY_ADDED_ILIFE_NFC, true)
            .putBoolean(KEY_ADDED_QUICK_WATER, true)
            .putBoolean(KEY_ADDED_UJING, true)
            .putBoolean(KEY_ADDED_UJING_SCAN, true)
            .putBoolean(KEY_ADDED_PANGGUAI, true)
            .putBoolean(KEY_ADDED_PANGGUAI_SCAN, true)
            .putBoolean(KEY_ADDED_PERFECT_CAMPUS, true)
            .apply()
    }

    fun resetHomeModules() {
        prefs.edit()
            .putStringSet(KEY_ENABLED_HOME_MODULES, DEFAULT_HOME_MODULES)
            .putBoolean(KEY_ADDED_ILIFE_NFC, true)
            .putBoolean(KEY_ADDED_QUICK_WATER, true)
            .putBoolean(KEY_ADDED_UJING, true)
            .putBoolean(KEY_ADDED_UJING_SCAN, true)
            .putBoolean(KEY_ADDED_PANGGUAI, true)
            .putBoolean(KEY_ADDED_PANGGUAI_SCAN, true)
            .putBoolean(KEY_ADDED_PERFECT_CAMPUS, true)
            .apply()
    }

    companion object {
        const val MODULE_SMART_DORM = "smart_dorm"
        const val MODULE_QUICK_UNLOCK = "quick_unlock"
        const val MODULE_ILIFE = "ilife"
        const val MODULE_QUICK_WATER = "quick_water"
        const val MODULE_UJING = "ujing"
        const val MODULE_UJING_SCAN = "ujing_scan"
        const val MODULE_PANGGUAI = "pangguai"
        const val MODULE_PANGGUAI_SCAN = "pangguai_scan"
        const val MODULE_PERFECT_CAMPUS = "perfect_campus"

        private const val LEGACY_MODULE_SCANNER = "scanner"
        private const val LEGACY_MODULE_NFC = "nfc"
        private val LEGACY_HOME_MODULES = setOf(LEGACY_MODULE_SCANNER, LEGACY_MODULE_NFC)

        val DEFAULT_HOME_MODULES: Set<String> = setOf(
            MODULE_SMART_DORM,
            MODULE_QUICK_UNLOCK,
            MODULE_ILIFE,
            MODULE_QUICK_WATER,
            MODULE_UJING,
            MODULE_UJING_SCAN,
            MODULE_PANGGUAI,
            MODULE_PANGGUAI_SCAN,
            MODULE_PERFECT_CAMPUS,
        )

        private const val PREFS = "campus_lite_appearance"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ENABLED_HOME_MODULES = "enabled_home_modules"
        private const val KEY_ADDED_ILIFE_NFC = "added_ilife_nfc"
        private const val KEY_NFC_AUTO_START_WATER = "nfc_auto_start_water"
        private const val KEY_ADDED_QUICK_WATER = "added_quick_water"
        private const val KEY_ADDED_UJING = "added_ujing"
        private const val KEY_ADDED_UJING_SCAN = "added_ujing_scan"
        private const val KEY_ADDED_PANGGUAI = "added_pangguai"
        private const val KEY_ADDED_PANGGUAI_SCAN = "added_pangguai_scan"
        private const val KEY_ADDED_PERFECT_CAMPUS = "added_perfect_campus"
        private const val KEY_UJING_AUTO_REFRESH = "ujing_auto_refresh"
        private const val KEY_UJING_FREE_ONLY = "ujing_free_only"
        private const val KEY_UJING_SORT_MODE = "ujing_sort_mode"
        private const val KEY_BACKGROUND_IMAGE_URI = "background_image_uri"
        private const val KEY_BATTERY_PROMPT_SHOWN = "battery_prompt_shown"
    }
}
