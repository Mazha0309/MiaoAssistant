package com.mazha0309.miaoassistant.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazha0309.miaoassistant.R
import com.mazha0309.miaoassistant.apps.InstalledApp
import com.mazha0309.miaoassistant.config.AppConfig
import com.mazha0309.miaoassistant.config.AppScopeMode
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppScopePage(
    config: AppConfig,
    installedApps: List<InstalledApp>?,
    onConfigChange: (AppConfig) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val allApps = remember(installedApps, config.scopedPackages) {
        val knownPackages = installedApps.orEmpty().mapTo(mutableSetOf(), InstalledApp::packageName)
        installedApps.orEmpty() + config.scopedPackages
            .asSequence()
            .filterNot(knownPackages::contains)
            .sorted()
            .map { packageName -> InstalledApp(packageName, packageName, null) }
    }
    val filteredApps = remember(allApps, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            allApps
        } else {
            allApps.filter { app ->
                app.label.contains(needle, ignoreCase = true) ||
                    app.packageName.contains(needle, ignoreCase = true)
            }
        }
    }

    MiaoPage(
        title = R.string.app_scope_title,
        bottomInnerPadding = 0.dp,
        blurEnabled = config.blurEnabled,
        navigationIcon = { BackNavigationButton(onBack) },
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                OverlayDropdownPreference(
                    items = listOf(
                        stringResource(R.string.scope_exclude),
                        stringResource(R.string.scope_include),
                    ),
                    selectedIndex = if (config.appScopeMode == AppScopeMode.EXCLUDE_SELECTED) 0 else 1,
                    title = stringResource(R.string.app_scope_mode),
                    summary = stringResource(
                        if (config.appScopeMode == AppScopeMode.EXCLUDE_SELECTED) {
                            R.string.scope_exclude_summary
                        } else {
                            R.string.scope_include_summary
                        },
                    ),
                    startAction = { PreferenceIcon(Icons.Rounded.Apps) },
                    onSelectedIndexChange = { index ->
                        onConfigChange(
                            config.copy(
                                appScopeMode = if (index == 0) {
                                    AppScopeMode.EXCLUDE_SELECTED
                                } else {
                                    AppScopeMode.INCLUDE_SELECTED
                                },
                            ),
                        )
                    },
                )
            }

            Spacer(Modifier.height(12.dp))
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.search_apps),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
        }

        when {
            installedApps == null -> item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(
                        title = stringResource(R.string.loading_apps),
                        summary = stringResource(R.string.loading_apps_summary),
                    )
                }
            }

            filteredApps.isEmpty() -> item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    BasicComponent(title = stringResource(R.string.no_apps_found))
                }
            }

            else -> item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    filteredApps.forEach { app ->
                        val checked = app.packageName in config.scopedPackages
                        CheckboxPreference(
                            title = app.label,
                            summary = app.packageName,
                            checked = checked,
                            onCheckedChange = { selected ->
                                val updatedPackages = config.scopedPackages.toMutableSet().apply {
                                    if (selected) add(app.packageName) else remove(app.packageName)
                                }
                                onConfigChange(config.copy(scopedPackages = updatedPackages))
                            },
                            startAction = { AppIcon(app) },
                            checkboxLocation = CheckboxLocation.End,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(app: InstalledApp) {
    val shape = RoundedCornerShape(10.dp)
    if (app.icon != null) {
        Image(
            bitmap = app.icon,
            contentDescription = null,
            modifier = Modifier
                .padding(end = 6.dp)
                .size(38.dp)
                .clip(shape),
        )
    } else {
        Box(
            modifier = Modifier
                .padding(end = 6.dp)
                .size(38.dp)
                .clip(shape)
                .background(MiuixTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = app.label.take(1).uppercase(),
                color = MiuixTheme.colorScheme.onPrimaryContainer,
                fontSize = 16.sp,
            )
        }
    }
}
