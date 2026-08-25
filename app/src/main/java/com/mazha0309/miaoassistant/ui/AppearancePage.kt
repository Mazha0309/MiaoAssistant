package com.mazha0309.miaoassistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mazha0309.miaoassistant.R
import com.mazha0309.miaoassistant.config.AppConfig
import com.mazha0309.miaoassistant.config.ThemeMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun AppearancePage(
    config: AppConfig,
    glassSupported: Boolean,
    onConfigChange: (AppConfig) -> Unit,
    onBack: () -> Unit,
) {
    MiaoPage(
        title = R.string.settings_appearance,
        bottomInnerPadding = 0.dp,
        blurEnabled = config.blurEnabled,
        navigationIcon = { BackNavigationButton(onBack) },
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                OverlayDropdownPreference(
                    items = listOf(
                        stringResource(R.string.theme_system),
                        stringResource(R.string.theme_light),
                        stringResource(R.string.theme_dark),
                    ),
                    selectedIndex = ThemeMode.entries.indexOf(config.themeMode),
                    title = stringResource(R.string.theme_mode),
                    summary = stringResource(R.string.theme_mode_summary),
                    startAction = { PreferenceIcon(Icons.Rounded.ColorLens) },
                    onSelectedIndexChange = { index ->
                        onConfigChange(config.copy(themeMode = ThemeMode.entries[index]))
                    },
                )
                SwitchPreference(
                    checked = config.useMonet,
                    onCheckedChange = { onConfigChange(config.copy(useMonet = it)) },
                    title = stringResource(R.string.monet_color),
                    summary = stringResource(R.string.monet_color_summary),
                    startAction = { PreferenceIcon(Icons.Rounded.Wallpaper) },
                )
            }

            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    checked = config.blurEnabled && glassSupported,
                    onCheckedChange = { onConfigChange(config.copy(blurEnabled = it)) },
                    title = stringResource(R.string.app_bar_blur),
                    summary = stringResource(
                        if (glassSupported) R.string.app_bar_blur_summary else R.string.blur_unsupported_summary,
                    ),
                    startAction = { PreferenceIcon(Icons.Rounded.BlurOn) },
                    enabled = glassSupported,
                )
                SwitchPreference(
                    checked = config.floatingBottomBar,
                    onCheckedChange = { onConfigChange(config.copy(floatingBottomBar = it)) },
                    title = stringResource(R.string.floating_bottom_bar),
                    summary = stringResource(R.string.floating_bottom_bar_summary),
                    startAction = { PreferenceIcon(Icons.Rounded.SpaceBar) },
                )
                AnimatedVisibility(visible = config.floatingBottomBar) {
                    SwitchPreference(
                        checked = config.liquidGlassEnabled && glassSupported,
                        onCheckedChange = { onConfigChange(config.copy(liquidGlassEnabled = it)) },
                        title = stringResource(R.string.liquid_glass),
                        summary = stringResource(
                            if (glassSupported) R.string.liquid_glass_summary else R.string.blur_unsupported_summary,
                        ),
                        startAction = { PreferenceIcon(Icons.Rounded.BlurOn) },
                        enabled = glassSupported,
                    )
                }
            }
        }
    }
}
