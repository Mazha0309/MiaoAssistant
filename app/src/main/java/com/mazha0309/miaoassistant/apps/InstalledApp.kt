package com.mazha0309.miaoassistant.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import java.util.Locale

data class InstalledApp(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap?,
)

object InstalledAppsRepository {
    fun load(context: Context): List<InstalledApp> {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        }
        val collator = Collator.getInstance(Locale.getDefault())
        return resolveInfos
            .asSequence()
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == context.packageName) return@mapNotNull null
                InstalledApp(
                    label = resolveInfo.loadLabel(packageManager).toString().ifBlank { packageName },
                    packageName = packageName,
                    icon = runCatching {
                        resolveInfo.loadIcon(packageManager).toBitmap(width = 96, height = 96).asImageBitmap()
                    }.getOrNull(),
                )
            }
            .distinctBy(InstalledApp::packageName)
            .sortedWith { first, second -> collator.compare(first.label, second.label) }
            .toList()
    }
}
