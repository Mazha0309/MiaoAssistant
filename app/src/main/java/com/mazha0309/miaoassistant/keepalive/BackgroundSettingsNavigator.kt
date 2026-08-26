package com.mazha0309.miaoassistant.keepalive

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

internal object BackgroundSettingsNavigator {
    fun openAutoStart(context: Context): Boolean = startFirstAvailable(
        context,
        Intent().setComponent(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
        ),
        Intent("miui.intent.action.OP_AUTO_START"),
        applicationDetailsIntent(context),
    )

    fun openPowerPolicy(context: Context): Boolean {
        val label = context.applicationInfo.loadLabel(context.packageManager).toString()
        val appPowerPolicy = Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST")
            .setComponent(
                ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
                ),
            )
            .putExtra("package_name", context.packageName)
            .putExtra("package_label", label)
        return startFirstAvailable(
            context,
            appPowerPolicy,
            Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST")
                .putExtra("package_name", context.packageName)
                .putExtra("package_label", label),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            applicationDetailsIntent(context),
        )
    }

    fun openApplicationDetails(context: Context): Boolean =
        startFirstAvailable(context, applicationDetailsIntent(context))

    private fun applicationDetailsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )

    private fun startFirstAvailable(context: Context, vararg intents: Intent): Boolean {
        intents.forEach { candidate ->
            val intent = Intent(candidate).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val started = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (started) return true
        }
        return false
    }
}
