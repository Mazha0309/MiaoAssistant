package com.mazha0309.miaoassistant.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mazha0309.miaoassistant.R
import com.mazha0309.miaoassistant.config.AppConfig
import com.mazha0309.miaoassistant.config.AppScopeMode
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun ScopePage(
    config: AppConfig,
    serviceEnabled: Boolean,
    batteryOptimizationIgnored: Boolean,
    bottomInnerPadding: Dp,
    onConfigChange: (AppConfig) -> Unit,
    onKeepAliveChange: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAppScope: () -> Unit,
) {
    MiaoPage(
        title = R.string.scope_page_title,
        bottomInnerPadding = bottomInnerPadding,
        blurEnabled = config.blurEnabled,
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.open_accessibility_settings),
                    summary = stringResource(
                        if (serviceEnabled) R.string.service_running else R.string.service_stopped,
                    ),
                    startAction = { PreferenceIcon(Icons.Rounded.AccessibilityNew) },
                    onClick = onOpenAccessibilitySettings,
                )
                ArrowPreference(
                    title = stringResource(R.string.app_scope_title),
                    summary = if (config.scopedPackages.isEmpty()) {
                        stringResource(
                            if (config.appScopeMode == AppScopeMode.EXCLUDE_SELECTED) {
                                R.string.scope_all_apps
                            } else {
                                R.string.scope_no_apps
                            },
                        )
                    } else {
                        stringResource(
                            if (config.appScopeMode == AppScopeMode.EXCLUDE_SELECTED) {
                                R.string.scope_excluded_count
                            } else {
                                R.string.scope_included_count
                            },
                            config.scopedPackages.size,
                        )
                    },
                    startAction = { PreferenceIcon(Icons.Rounded.Apps) },
                    onClick = onOpenAppScope,
                )
                BasicComponent(
                    title = stringResource(R.string.password_protection),
                    summary = stringResource(R.string.password_protection_summary),
                    startAction = { PreferenceIcon(Icons.Rounded.Lock) },
                )
            }

            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    checked = config.keepAliveEnabled,
                    onCheckedChange = onKeepAliveChange,
                    title = stringResource(R.string.keep_alive),
                    summary = stringResource(R.string.keep_alive_summary),
                    startAction = { PreferenceIcon(Icons.Rounded.NotificationsActive) },
                )
                ArrowPreference(
                    title = stringResource(R.string.battery_optimization),
                    summary = if (batteryOptimizationIgnored) {
                        stringResource(R.string.battery_optimization_ignored)
                    } else {
                        stringResource(R.string.battery_optimization_active)
                    },
                    startAction = { PreferenceIcon(Icons.Rounded.BatteryFull) },
                    onClick = onOpenBatterySettings,
                )
            }

            if (config.keepAliveEnabled) {
                Spacer(Modifier.height(12.dp))
                InfoNotice(stringResource(R.string.keep_alive_limit))
            }
        }
    }
}
