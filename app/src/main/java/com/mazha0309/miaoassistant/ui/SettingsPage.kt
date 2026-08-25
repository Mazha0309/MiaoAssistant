package com.mazha0309.miaoassistant.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mazha0309.miaoassistant.BuildConfig
import com.mazha0309.miaoassistant.R
import com.mazha0309.miaoassistant.config.AppConfig
import com.mazha0309.miaoassistant.config.ThemeMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
internal fun SettingsPage(
    config: AppConfig,
    bottomInnerPadding: Dp,
    onOpenAppearance: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val themeName = stringResource(
        when (config.themeMode) {
            ThemeMode.SYSTEM -> R.string.theme_system
            ThemeMode.LIGHT -> R.string.theme_light
            ThemeMode.DARK -> R.string.theme_dark
        },
    )
    val barName = stringResource(
        if (config.floatingBottomBar) R.string.bar_floating else R.string.bar_standard,
    )

    MiaoPage(
        title = R.string.nav_settings,
        bottomInnerPadding = bottomInnerPadding,
        blurEnabled = config.blurEnabled,
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.settings_appearance),
                    summary = stringResource(R.string.settings_appearance_summary, themeName, barName),
                    startAction = { PreferenceIcon(Icons.Rounded.Palette) },
                    onClick = onOpenAppearance,
                )
            }

            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.about),
                    summary = stringResource(R.string.version_summary, BuildConfig.VERSION_NAME),
                    startAction = { PreferenceIcon(Icons.Rounded.Info) },
                    onClick = onOpenAbout,
                )
            }
        }
    }
}
