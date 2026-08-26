package com.mazha0309.miaoassistant.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mazha0309.miaoassistant.R
import com.mazha0309.miaoassistant.config.AppConfig
import com.mazha0309.miaoassistant.config.AppScopeMode
import com.mazha0309.miaoassistant.config.InputWriteMode
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun ScopePage(
    config: AppConfig,
    serviceEnabled: Boolean,
    batteryOptimizationIgnored: Boolean,
    keepAliveRunning: Boolean,
    rootKeepAliveBusy: Boolean,
    bottomInnerPadding: Dp,
    onInputWriteModeChange: (InputWriteMode) -> Unit,
    onKeepAliveChange: (Boolean) -> Unit,
    onRootKeepAliveChange: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAutoStartSettings: () -> Unit,
    onOpenPowerPolicySettings: () -> Unit,
    onOpenApplicationDetails: () -> Unit,
    onOpenAppScope: () -> Unit,
) {
    var showBackgroundRunDialog by rememberSaveable { mutableStateOf(false) }
    var showRootKeepAliveConfirm by rememberSaveable { mutableStateOf(false) }

    MiaoPage(
        title = R.string.scope_page_title,
        bottomInnerPadding = bottomInnerPadding,
        blurEnabled = config.blurEnabled,
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.open_accessibility_settings),
                    summary = when {
                        serviceEnabled -> stringResource(R.string.service_running)
                        config.inputWriteMode != InputWriteMode.ACCESSIBILITY -> stringResource(
                            R.string.service_privileged_waiting_short,
                            stringResource(
                                if (config.inputWriteMode == InputWriteMode.SHIZUKU) {
                                    R.string.input_write_shizuku
                                } else {
                                    R.string.input_write_root
                                },
                            ),
                        )

                        else -> stringResource(R.string.service_stopped)
                    },
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
                OverlayDropdownPreference(
                    items = listOf(
                        stringResource(R.string.input_write_accessibility),
                        stringResource(R.string.input_write_shizuku),
                        stringResource(R.string.input_write_root),
                    ),
                    selectedIndex = InputWriteMode.entries.indexOf(config.inputWriteMode),
                    title = stringResource(R.string.input_write_mode),
                    summary = stringResource(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            R.string.input_write_mode_modern_summary
                        } else {
                            R.string.input_write_mode_legacy_summary
                        },
                    ),
                    startAction = { PreferenceIcon(Icons.Rounded.Terminal) },
                    onSelectedIndexChange = { index ->
                        onInputWriteModeChange(InputWriteMode.entries[index])
                    },
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
                    summary = stringResource(
                        when {
                            !config.keepAliveEnabled -> R.string.keep_alive_summary
                            keepAliveRunning -> R.string.keep_alive_running
                            else -> R.string.keep_alive_waiting
                        },
                    ),
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
                ArrowPreference(
                    title = stringResource(R.string.background_run_settings),
                    summary = stringResource(R.string.background_run_settings_summary),
                    startAction = { PreferenceIcon(Icons.Rounded.Settings) },
                    onClick = { showBackgroundRunDialog = true },
                )
                SwitchPreference(
                    checked = config.rootKeepAliveEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            showRootKeepAliveConfirm = true
                        } else {
                            onRootKeepAliveChange(false)
                        }
                    },
                    title = stringResource(R.string.root_keep_alive),
                    summary = stringResource(
                        when {
                            rootKeepAliveBusy -> R.string.root_keep_alive_working
                            !config.keepAliveEnabled -> R.string.root_keep_alive_requires_normal
                            else -> R.string.root_keep_alive_summary
                        },
                    ),
                    enabled = config.keepAliveEnabled && !rootKeepAliveBusy,
                    startAction = { PreferenceIcon(Icons.Rounded.Terminal) },
                )
            }
        }
    }

    BackgroundRunDialog(
        show = showBackgroundRunDialog,
        onDismiss = { showBackgroundRunDialog = false },
        onOpenAutoStart = {
            onOpenAutoStartSettings()
        },
        onOpenPowerPolicy = {
            onOpenPowerPolicySettings()
        },
        onOpenApplicationDetails = {
            onOpenApplicationDetails()
        },
    )
    RootKeepAliveConfirmDialog(
        show = showRootKeepAliveConfirm,
        onDismiss = { showRootKeepAliveConfirm = false },
        onConfirm = {
            showRootKeepAliveConfirm = false
            onRootKeepAliveChange(true)
        },
    )
}

@Composable
private fun BackgroundRunDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onOpenAutoStart: () -> Unit,
    onOpenPowerPolicy: () -> Unit,
    onOpenApplicationDetails: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.background_run_dialog_title),
        summary = stringResource(R.string.background_run_dialog_summary),
        onDismissRequest = onDismiss,
        insideMargin = DpSize(24.dp, 24.dp),
    ) {
        Column {
            val itemPadding = PaddingValues(horizontal = 6.dp, vertical = 14.dp)
            ArrowPreference(
                title = stringResource(R.string.autostart_settings),
                summary = stringResource(R.string.autostart_settings_summary),
                insideMargin = itemPadding,
                onClick = onOpenAutoStart,
            )
            ArrowPreference(
                title = stringResource(R.string.power_policy_settings),
                summary = stringResource(R.string.power_policy_settings_summary),
                insideMargin = itemPadding,
                onClick = onOpenPowerPolicy,
            )
            ArrowPreference(
                title = stringResource(R.string.application_details),
                summary = stringResource(R.string.application_details_summary),
                insideMargin = itemPadding,
                onClick = onOpenApplicationDetails,
            )
            BasicComponent(
                title = stringResource(R.string.recent_task_lock),
                summary = stringResource(R.string.recent_task_lock_hint),
                insideMargin = itemPadding,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(
                text = stringResource(R.string.done),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun RootKeepAliveConfirmDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.root_keep_alive_confirm_title),
        summary = stringResource(R.string.root_keep_alive_confirm_summary),
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(20.dp))
            TextButton(
                text = stringResource(R.string.enable),
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}
