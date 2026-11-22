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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import androidx.wear.compose.material3.dynamicColorScheme
import com.coolapk.wear.data.repository.SettingsRepository
import com.coolapk.wear.data.repository.ThemePreset
import com.coolapk.wear.data.repository.WatchSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// 经典酷安绿与 OLED 暗色表面基准色（保留兼容性导出）
val CoolapkGreen = Color(0xFF0DAE62)
val CoolapkGreenLight = Color(0xFF4ADE80)
val CoolapkGreenDark = Color(0xFF065F36)
val WearBackground = Color(0xFF000000)
val WearSurface = Color(0xFF171C19)
val WearSurfaceVariant = Color(0xFF242B27)
val WearOnSurface = Color(0xFFF2F5F3)
val WearOnSurfaceSecondary = Color(0xFFA2ACA6)
val WearError = Color(0xFFFF6B6B)

val LocalAppPrimaryColor = compositionLocalOf { CoolapkGreen }
val LocalAppOnPrimaryColor = compositionLocalOf { Color(0xFF00381C) }
val LocalWatchSettings = compositionLocalOf { WatchSettings() }

/**
 * Wear OS Material 3 Expressive 主题管理器。
 * 1. 优先对接 SettingsRepository，并向下兼容旧版 use_monet_theme 配置；
 * 2. 支持 Wear OS 原生 dynamicColorScheme（表盘动态取色）+ 三星/Pixel system_palette 回退；
 * 3. 提供 4 套专为 OLED 圆形表盘调优的 Material 3 Expressive 色阶方案。
 */
object ThemeManager {
    private const val TAG = "ThemeManager"
    private const val LEGACY_PREFS_NAME = "coolapk_wear_theme_prefs"
    private const val LEGACY_KEY_USE_MONET = "use_monet_theme"
    private val PALETTE_REGEX = Regex(""""android\.theme\.customization\.system_palette":"([0-9a-fA-F]{6})"""")

    @Volatile
    private var settingsRepository: SettingsRepository? = null

    private val _useMonet = MutableStateFlow(false)
    val useMonet: StateFlow<Boolean> = _useMonet.asStateFlow()

    private val _preset = MutableStateFlow(ThemePreset.COOLAPK_GREEN)
    val preset: StateFlow<ThemePreset> = _preset.asStateFlow()

    fun init(context: Context, repository: SettingsRepository? = null) {
        if (repository != null) {
            settingsRepository = repository
            _useMonet.value = repository.settings.value.useDynamicColor
            _preset.value = repository.settings.value.themePreset
        } else {
            val sp = context.applicationContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            _useMonet.value = sp.getBoolean(LEGACY_KEY_USE_MONET, false)
        }
    }

    fun setUseMonet(context: Context, enabled: Boolean) {
        _useMonet.value = enabled
        settingsRepository?.setUseDynamicColor(enabled) ?: run {
            val sp = context.applicationContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            sp.edit().putBoolean(LEGACY_KEY_USE_MONET, enabled).apply()
        }
    }

    fun toggleMonet(context: Context): Boolean {
        val next = !_useMonet.value
        setUseMonet(context, next)
        Log.d(TAG, "Toggled dynamic color: $next")
        return next
    }

    fun setPreset(preset: ThemePreset) {
        _preset.value = preset
        settingsRepository?.setThemePreset(preset)
    }

    fun getLegacyMonetSeed(context: Context): Color? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        try {
            val overlayJson = Settings.Secure.getString(
                context.contentResolver,
                "theme_customization_overlay_packages"
            )
            if (!overlayJson.isNullOrBlank()) {
                val match = PALETTE_REGEX.find(overlayJson)
                if (match != null) {
                    return Color(android.graphics.Color.parseColor("#${match.groupValues[1]}"))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading theme_customization_overlay_packages", e)
        }
        try {
            val color200 = ContextCompat.getColor(context, android.R.color.system_accent1_200)
            if (color200 != 0 && color200 != -1) return Color(color200)
        } catch (_: Exception) {
        }
        try {
            val color500 = ContextCompat.getColor(context, android.R.color.system_accent1_500)
            if (color500 != 0 && color500 != -1) return Color(color500)
        } catch (_: Exception) {
        }
        return null
    }

    fun computeOnPrimaryColor(primary: Color): Color {
        return if (primary.luminance() > 0.42f) Color(0xFF00210E) else Color.White
    }

    fun buildExpressiveColorScheme(preset: ThemePreset): ColorScheme = when (preset) {
        ThemePreset.COOLAPK_GREEN -> ColorScheme(
            primary = Color(0xFF5CE09B),
            primaryDim = Color(0xFF28B970),
            primaryContainer = Color(0xFF0A4D2A),
            onPrimary = Color(0xFF00391B),
            onPrimaryContainer = Color(0xFFBEFFD8),
            secondary = Color(0xFFB4CCB9),
            secondaryDim = Color(0xFF8CA693),
            secondaryContainer = Color(0xFF223529),
            onSecondary = Color(0xFF1F3527),
            onSecondaryContainer = Color(0xFFD0E8D5),
            tertiary = Color(0xFF8FD3E8),
            tertiaryDim = Color(0xFF5FAAC2),
            tertiaryContainer = Color(0xFF163D49),
            onTertiary = Color(0xFF003543),
            onTertiaryContainer = Color(0xFFC2EEFD),
            surfaceContainerLow = Color(0xFF121614),
            surfaceContainer = Color(0xFF1A201D),
            surfaceContainerHigh = Color(0xFF242C28),
            onSurface = Color(0xFFF1F5F2),
            onSurfaceVariant = Color(0xFFABB7AF),
            outline = Color(0xFF6B7870),
            outlineVariant = Color(0xFF39443E),
            background = Color.Black,
            onBackground = Color(0xFFF1F5F2),
            error = Color(0xFFFF8A80),
            errorDim = Color(0xFFE5534B),
            errorContainer = Color(0xFF5C1916),
            onError = Color(0xFF3E0002),
            onErrorContainer = Color(0xFFFFDAD6)
        )
        ThemePreset.TECH_BLUE -> ColorScheme(
            primary = Color(0xFF82B8FF),
            primaryDim = Color(0xFF4C91F0),
            primaryContainer = Color(0xFF0F3B75),
            onPrimary = Color(0xFF002D63),
            onPrimaryContainer = Color(0xFFD6E6FF),
            secondary = Color(0xFFB6C6DF),
            secondaryDim = Color(0xFF8D9FBB),
            secondaryContainer = Color(0xFF233044),
            onSecondary = Color(0xFF203047),
            onSecondaryContainer = Color(0xFFD5E3FA),
            tertiary = Color(0xFF7CE2D6),
            tertiaryDim = Color(0xFF4CB8AC),
            tertiaryContainer = Color(0xFF14423D),
            onTertiary = Color(0xFF003731),
            onTertiaryContainer = Color(0xFFB8F5ED),
            surfaceContainerLow = Color(0xFF11151B),
            surfaceContainer = Color(0xFF191F28),
            surfaceContainerHigh = Color(0xFF232B36),
            onSurface = Color(0xFFF0F4FA),
            onSurfaceVariant = Color(0xFFAAB4C4),
            outline = Color(0xFF6B778A),
            outlineVariant = Color(0xFF374252),
            background = Color.Black,
            onBackground = Color(0xFFF0F4FA),
            error = Color(0xFFFF8A80),
            errorDim = Color(0xFFE5534B),
            errorContainer = Color(0xFF5C1916),
            onError = Color(0xFF3E0002),
            onErrorContainer = Color(0xFFFFDAD6)
        )
        ThemePreset.TWILIGHT_VIOLET -> ColorScheme(
            primary = Color(0xFFCBB2FF),
            primaryDim = Color(0xFF9F7AEA),
            primaryContainer = Color(0xFF3B2273),
            onPrimary = Color(0xFF2C0E63),
            onPrimaryContainer = Color(0xFFE9DDFF),
            secondary = Color(0xFFC9BFD9),
            secondaryDim = Color(0xFF9E94B0),
            secondaryContainer = Color(0xFF2F283D),
            onSecondary = Color(0xFF2E273D),
            onSecondaryContainer = Color(0xFFE5DCF5),
            tertiary = Color(0xFFF2B0D0),
            tertiaryDim = Color(0xFFC77FA3),
            tertiaryContainer = Color(0xFF4F2239),
            onTertiary = Color(0xFF49122F),
            onTertiaryContainer = Color(0xFFFFD8EA),
            surfaceContainerLow = Color(0xFF15121B),
            surfaceContainer = Color(0xFF1E1A26),
            surfaceContainerHigh = Color(0xFF282333),
            onSurface = Color(0xFFF4F1FA),
            onSurfaceVariant = Color(0xFFB4ACBF),
            outline = Color(0xFF766D85),
            outlineVariant = Color(0xFF413A4D),
            background = Color.Black,
            onBackground = Color(0xFFF4F1FA),
            error = Color(0xFFFF8A80),
            errorDim = Color(0xFFE5534B),
            errorContainer = Color(0xFF5C1916),
            onError = Color(0xFF3E0002),
            onErrorContainer = Color(0xFFFFDAD6)
        )
        ThemePreset.AMBER_ORANGE -> ColorScheme(
            primary = Color(0xFFFFC164),
            primaryDim = Color(0xFFEB9826),
            primaryContainer = Color(0xFF5B3B05),
            onPrimary = Color(0xFF422800),
            onPrimaryContainer = Color(0xFFFFE0B2),
            secondary = Color(0xFFDCC3A3),
            secondaryDim = Color(0xFFB0987A),
            secondaryContainer = Color(0xFF3A2E20),
            onSecondary = Color(0xFF392B1B),
            onSecondaryContainer = Color(0xFFF8DFC0),
            tertiary = Color(0xFFA8D68D),
            tertiaryDim = Color(0xFF7CAB62),
            tertiaryContainer = Color(0xFF253E19),
            onTertiary = Color(0xFF18380B),
            onTertiaryContainer = Color(0xFFC9F2B0),
            surfaceContainerLow = Color(0xFF181410),
            surfaceContainer = Color(0xFF211C16),
            surfaceContainerHigh = Color(0xFF2C251D),
            onSurface = Color(0xFFF7F3EE),
            onSurfaceVariant = Color(0xFFBEB3A5),
            outline = Color(0xFF807568),
            outlineVariant = Color(0xFF473F36),
            background = Color.Black,
            onBackground = Color(0xFFF7F3EE),
            error = Color(0xFFFF8A80),
            errorDim = Color(0xFFE5534B),
            errorContainer = Color(0xFF5C1916),
            onError = Color(0xFF3E0002),
            onErrorContainer = Color(0xFFFFDAD6)
        )
    }

    private fun buildFromSeed(seed: Color): ColorScheme {
        val primary = if (seed.luminance() < 0.25f) {
            Color(
                red = (seed.red + 0.35f).coerceAtMost(1f),
                green = (seed.green + 0.35f).coerceAtMost(1f),
                blue = (seed.blue + 0.35f).coerceAtMost(1f),
                alpha = 1f
            )
        } else {
            seed
        }
        val onPrimary = computeOnPrimaryColor(primary)
        val primaryContainer = Color(
            red = (primary.red * 0.28f).coerceIn(0.05f, 0.4f),
            green = (primary.green * 0.28f).coerceIn(0.05f, 0.4f),
            blue = (primary.blue * 0.28f).coerceIn(0.05f, 0.4f),
            alpha = 1f
        )
        return buildExpressiveColorScheme(ThemePreset.COOLAPK_GREEN).copy(
            primary = primary,
            primaryDim = primary.copy(alpha = 0.82f),
            primaryContainer = primaryContainer,
            onPrimary = onPrimary,
            onPrimaryContainer = Color(0xFFF4F8F6)
        )
    }

    fun resolveColorScheme(
        context: Context,
        useDynamicColor: Boolean,
        preset: ThemePreset
    ): ColorScheme {
        if (useDynamicColor) {
            dynamicColorScheme(context)?.let { nativeDynamic ->
                return nativeDynamic.copy(background = Color.Black)
            }
            getLegacyMonetSeed(context)?.let { seed ->
                return buildFromSeed(seed)
            }
        }
        return buildExpressiveColorScheme(preset)
    }
}

@Composable
fun CoolapkWearTheme(
    settingsRepository: SettingsRepository? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val settings by if (settingsRepository != null) {
        settingsRepository.settings.collectAsState()
    } else {
        val useMonet by ThemeManager.useMonet.collectAsState()
        val preset by ThemeManager.preset.collectAsState()
        remember(useMonet, preset) {
            MutableStateFlow(WatchSettings(useDynamicColor = useMonet, themePreset = preset))
        }.collectAsState()
    }

    val colorScheme = remember(context, settings.useDynamicColor, settings.themePreset, configuration.uiMode) {
        ThemeManager.resolveColorScheme(
            context = context,
            useDynamicColor = settings.useDynamicColor,
            preset = settings.themePreset
        )
    }

    CompositionLocalProvider(
        LocalWatchSettings provides settings,
        LocalAppPrimaryColor provides colorScheme.primary,
        LocalAppOnPrimaryColor provides colorScheme.onPrimary
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    }
}
