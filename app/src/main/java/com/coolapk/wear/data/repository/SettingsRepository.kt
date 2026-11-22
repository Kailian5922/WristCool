package com.coolapk.wear.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ThemePreset(val key: String, val label: String, val seedArgb: Long) {
    COOLAPK_GREEN("green", "酷安绿", 0xFF0DAE62),
    TECH_BLUE("blue", "极客蓝", 0xFF3B82F6),
    TWILIGHT_VIOLET("violet", "暮光紫", 0xFF8B5CF6),
    AMBER_ORANGE("amber", "琥珀橙", 0xFFF59E0B);

    companion object {
        fun fromKey(key: String?): ThemePreset =
            entries.firstOrNull { it.key == key } ?: COOLAPK_GREEN
    }
}

data class WatchSettings(
    val useDynamicColor: Boolean = false,
    val themePreset: ThemePreset = ThemePreset.COOLAPK_GREEN,
    val noImageMode: Boolean = false,
    val hapticEnabled: Boolean = true,
    val recordHistory: Boolean = true,
    val defaultFeedTab: String = "HEADLINE",
    val defaultCommentSort: String = ""
)

/**
 * 手表端偏好设置仓储（主题色、省流无图模式、表圈触觉反馈、浏览记录、默认频道与排序）。
 */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val legacyThemePrefs = context.applicationContext.getSharedPreferences(LEGACY_THEME_PREFS, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<WatchSettings> = _settings.asStateFlow()

    private fun loadSettings(): WatchSettings {
        val dynamicColor = if (prefs.contains(KEY_DYNAMIC_COLOR)) {
            prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
        } else {
            legacyThemePrefs.getBoolean(LEGACY_KEY_USE_MONET, false)
        }
        return WatchSettings(
            useDynamicColor = dynamicColor,
            themePreset = ThemePreset.fromKey(prefs.getString(KEY_THEME_PRESET, ThemePreset.COOLAPK_GREEN.key)),
            noImageMode = prefs.getBoolean(KEY_NO_IMAGE_MODE, false),
            hapticEnabled = prefs.getBoolean(KEY_HAPTIC_ENABLED, true),
            recordHistory = prefs.getBoolean(KEY_RECORD_HISTORY, true),
            defaultFeedTab = prefs.getString(KEY_DEFAULT_FEED_TAB, "HEADLINE") ?: "HEADLINE",
            defaultCommentSort = prefs.getString(KEY_DEFAULT_COMMENT_SORT, "") ?: ""
        )
    }

    fun setUseDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        legacyThemePrefs.edit().putBoolean(LEGACY_KEY_USE_MONET, enabled).apply()
        _settings.update { it.copy(useDynamicColor = enabled) }
    }

    fun setThemePreset(preset: ThemePreset) {
        prefs.edit().putString(KEY_THEME_PRESET, preset.key).apply()
        _settings.update { it.copy(themePreset = preset) }
    }

    fun setNoImageMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NO_IMAGE_MODE, enabled).apply()
        _settings.update { it.copy(noImageMode = enabled) }
    }

    fun setHapticEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply()
        _settings.update { it.copy(hapticEnabled = enabled) }
    }

    fun setRecordHistory(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RECORD_HISTORY, enabled).apply()
        _settings.update { it.copy(recordHistory = enabled) }
    }

    fun setDefaultFeedTab(tabName: String) {
        prefs.edit().putString(KEY_DEFAULT_FEED_TAB, tabName).apply()
        _settings.update { it.copy(defaultFeedTab = tabName) }
    }

    fun setDefaultCommentSort(sortKey: String) {
        prefs.edit().putString(KEY_DEFAULT_COMMENT_SORT, sortKey).apply()
        _settings.update { it.copy(defaultCommentSort = sortKey) }
    }

    companion object {
        private const val PREFS_NAME = "coolapk_wear_settings"
        private const val LEGACY_THEME_PREFS = "coolapk_wear_theme_prefs"
        private const val LEGACY_KEY_USE_MONET = "use_monet_theme"

        private const val KEY_DYNAMIC_COLOR = "use_dynamic_color"
        private const val KEY_THEME_PRESET = "theme_preset"
        private const val KEY_NO_IMAGE_MODE = "no_image_mode"
        private const val KEY_HAPTIC_ENABLED = "haptic_enabled"
        private const val KEY_RECORD_HISTORY = "record_history"
        private const val KEY_DEFAULT_FEED_TAB = "default_feed_tab"
        private const val KEY_DEFAULT_COMMENT_SORT = "default_comment_sort"
    }
}
