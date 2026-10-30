package com.coolapk.wear.presentation.theme

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

val CoolapkGreen = Color(0xFF0DAE62)
val CoolapkGreenLight = Color(0xFF40C782)
val CoolapkGreenDark = Color(0xFF088247)
val WearBackground = Color(0xFF000000)
val WearSurface = Color(0xFF1C1C1E)
val WearSurfaceVariant = Color(0xFF2C2C2E)
val WearOnSurface = Color(0xFFFFFFFF)
val WearOnSurfaceSecondary = Color(0xFF9E9EA2)
val WearError = Color(0xFFFF453A)

val LocalAppPrimaryColor = compositionLocalOf { CoolapkGreen }

object ThemeManager {
    private const val TAG = "ThemeManager"
    private const val PREFS_NAME = "coolapk_wear_theme_prefs"
    private const val KEY_USE_MONET = "use_monet_theme"

    private val _useMonet = MutableStateFlow(false)
    val useMonet: StateFlow<Boolean> = _useMonet.asStateFlow()

    fun init(context: Context) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _useMonet.value = sp.getBoolean(KEY_USE_MONET, false)
    }

    fun toggleMonet(context: Context) {
        val newValue = !_useMonet.value
        _useMonet.value = newValue
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_USE_MONET, newValue).apply()
        Log.d(TAG, "Toggled Monet theme: $newValue")
    }

    fun getActiveColor(context: Context, useMonet: Boolean): Color {
        if (!useMonet) return CoolapkGreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 1. 尝试从系统 Secure Settings 获取三星/Pixel 的 Monet 调色板 Hex
            try {
                val overlayJson = Settings.Secure.getString(context.contentResolver, "theme_customization_overlay_packages")
                if (!overlayJson.isNullOrBlank()) {
                    val paletteRegex = """"android\.theme\.customization\.system_palette":"([0-9a-fA-F]{6})"""".toRegex()
                    val match = paletteRegex.find(overlayJson)
                    if (match != null) {
                        val hex = match.groupValues[1]
                        val parsed = Color(android.graphics.Color.parseColor("#$hex"))
                        Log.d(TAG, "Monet color from system_palette: #$hex")
                        return parsed
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading theme_customization_overlay_packages", e)
            }

            // 2. 尝试从 Android 12+ framework resources 获取 accent 色 (优先 system_accent1_200 适合暗色手表屏幕)
            try {
                val color200 = ContextCompat.getColor(context, android.R.color.system_accent1_200)
                if (color200 != 0 && color200 != -1) {
                    Log.d(TAG, "Monet color from system_accent1_200: $color200")
                    return Color(color200)
                }
            } catch (_: Exception) {}

            try {
                val color500 = ContextCompat.getColor(context, android.R.color.system_accent1_500)
                if (color500 != 0 && color500 != -1) {
                    Log.d(TAG, "Monet color from system_accent1_500: $color500")
                    return Color(color500)
                }
            } catch (_: Exception) {}
        }
        return CoolapkGreen
    }
}

@Composable
fun CoolapkWearTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useMonet by ThemeManager.useMonet.collectAsState()
    val primaryColor = ThemeManager.getActiveColor(context, useMonet)

    val colors = Colors(
        primary = primaryColor,
        primaryVariant = primaryColor.copy(alpha = 0.8f),
        secondary = primaryColor.copy(alpha = 0.7f),
        secondaryVariant = primaryColor.copy(alpha = 0.5f),
        background = WearBackground,
        surface = WearSurface,
        error = WearError,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = WearOnSurface,
        onSurface = WearOnSurface,
        onError = Color.White
    )

    CompositionLocalProvider(LocalAppPrimaryColor provides primaryColor) {
        MaterialTheme(
            colors = colors,
            content = content
        )
    }
}
