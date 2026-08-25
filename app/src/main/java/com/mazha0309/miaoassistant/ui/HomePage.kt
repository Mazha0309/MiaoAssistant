package com.mazha0309.miaoassistant.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazha0309.miaoassistant.R
import com.mazha0309.miaoassistant.config.AppConfig
import com.mazha0309.miaoassistant.config.InputWriteMode
import com.mazha0309.miaoassistant.config.ProcessingMode
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
internal fun HomePage(
    config: AppConfig,
    serviceEnabled: Boolean,
    bottomInnerPadding: Dp,
    onConfigChange: (AppConfig) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    MiaoPage(
        title = R.string.app_name,
        bottomInnerPadding = bottomInnerPadding,
        blurEnabled = config.blurEnabled,
    ) {
        item {
            ServiceHeroCard(
                serviceEnabled = serviceEnabled,
                inputWriteMode = config.inputWriteMode,
                onClick = onOpenAccessibilitySettings,
            )
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    checked = config.enabled,
                    onCheckedChange = { onConfigChange(config.copy(enabled = it)) },
                    title = stringResource(R.string.master_switch),
                    summary = stringResource(R.string.master_switch_summary),
                    startAction = { PreferenceIcon(Icons.Rounded.PowerSettingsNew) },
                )
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = stringResource(R.string.processing_mode),
                    summary = stringResource(
                        if (config.processingMode == ProcessingMode.PUNCTUATION) {
                            R.string.mode_punctuation
                        } else {
                            R.string.mode_realtime
                        },
                    ),
                    startAction = { PreferenceIcon(Icons.Rounded.AutoFixHigh) },
                )
                BasicComponent(
                    title = stringResource(R.string.replacement_rules),
                    summary = if (config.rules.isEmpty()) {
                        stringResource(R.string.replacement_rules_empty)
                    } else {
                        stringResource(R.string.replacement_rules_count, config.rules.size)
                    },
                    startAction = { PreferenceIcon(Icons.Rounded.SwapHoriz) },
                )
                BasicComponent(
                    title = stringResource(R.string.keep_alive),
                    summary = stringResource(
                        if (config.keepAliveEnabled) R.string.state_enabled else R.string.state_disabled,
                    ),
                    startAction = { PreferenceIcon(Icons.Rounded.NotificationsActive) },
                )
            }
        }
    }
}

@Composable
private fun ServiceHeroCard(
    serviceEnabled: Boolean,
    inputWriteMode: InputWriteMode,
    onClick: () -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val privilegedWaiting = !serviceEnabled && inputWriteMode != InputWriteMode.ACCESSIBILITY
    val containerColor = when {
        serviceEnabled -> if (isDark) Color(0xFF1A3825) else Color(0xFFDFFAE4)
        privilegedWaiting -> if (isDark) Color(0xFF443713) else Color(0xFFFFF1C2)
        else -> colorScheme.errorContainer
    }
    val contentColor = if (serviceEnabled || privilegedWaiting) colorScheme.onSurface else colorScheme.onErrorContainer
    val accent = when {
        serviceEnabled -> Color(0xFF36D167)
        privilegedWaiting -> Color(0xFFFFB020)
        else -> colorScheme.error
    }
    val modeName = stringResource(
        when (inputWriteMode) {
            InputWriteMode.ACCESSIBILITY -> R.string.input_write_accessibility
            InputWriteMode.SHIZUKU -> R.string.input_write_shizuku
            InputWriteMode.ROOT -> R.string.input_write_root
        },
    )
    val title = when {
        serviceEnabled -> stringResource(R.string.service_running)
        privilegedWaiting -> stringResource(R.string.service_privileged_waiting_title)
        else -> stringResource(R.string.service_stopped)
    }
    val summary = when {
        serviceEnabled && inputWriteMode != InputWriteMode.ACCESSIBILITY ->
            stringResource(R.string.service_running_privileged_summary, modeName)

        serviceEnabled -> stringResource(R.string.service_running_summary)
        privilegedWaiting -> stringResource(R.string.service_privileged_waiting_summary, modeName)
        else -> stringResource(R.string.service_stopped_summary)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(156.dp),
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = containerColor,
            contentColor = contentColor,
        ),
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = true,
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = when {
                    serviceEnabled -> Icons.Rounded.CheckCircleOutline
                    privilegedWaiting -> Icons.Rounded.WarningAmber
                    else -> Icons.Rounded.ErrorOutline
                },
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 22.dp, y = 28.dp)
                    .size(116.dp),
                tint = accent.copy(alpha = 0.78f),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = summary,
                    modifier = Modifier.fillMaxWidth(0.76f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = contentColor.copy(alpha = 0.78f),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.tap_to_manage_service),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                )
            }
        }
    }
}
