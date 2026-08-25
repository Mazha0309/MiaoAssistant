package com.mazha0309.miaoassistant.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.graphics.drawable.toDrawable
import com.mazha0309.miaoassistant.config.ThemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

@Composable
fun MiaoAssistantTheme(
    themeMode: ThemeMode,
    useMonet: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val darkTheme = themeMode == ThemeMode.DARK || (themeMode == ThemeMode.SYSTEM && systemDark)
    val controller = remember(themeMode, useMonet, darkTheme) {
        ThemeController(
            colorSchemeMode = when {
                useMonet && themeMode == ThemeMode.SYSTEM -> ColorSchemeMode.MonetSystem
                useMonet && themeMode == ThemeMode.LIGHT -> ColorSchemeMode.MonetLight
                useMonet && themeMode == ThemeMode.DARK -> ColorSchemeMode.MonetDark
                themeMode == ThemeMode.SYSTEM -> ColorSchemeMode.System
                themeMode == ThemeMode.LIGHT -> ColorSchemeMode.Light
                else -> ColorSchemeMode.Dark
            },
            colorSpec = ThemeColorSpec.Spec2025,
            paletteStyle = ThemePaletteStyle.TonalSpot,
            isDark = darkTheme,
        )
    }

    MiuixTheme(controller = controller) {
        val surfaceColor = MiuixTheme.colorScheme.surface
        LaunchedEffect(darkTheme, surfaceColor) {
            val window = (context as? Activity)?.window ?: return@LaunchedEffect
            // App-selected dark mode can differ from the system resource qualifier. Keep the
            // actual window/transition background in sync so page and task animations never flash light.
            window.setBackgroundDrawable(surfaceColor.toArgb().toDrawable())
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
        content()
    }
}
