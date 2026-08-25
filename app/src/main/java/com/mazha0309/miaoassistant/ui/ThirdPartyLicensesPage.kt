package com.mazha0309.miaoassistant.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mazha0309.miaoassistant.R
import com.mazha0309.miaoassistant.config.AppConfig
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card

private data class LicenseEntry(
    val name: String,
    val license: String,
    val source: String,
)

@Composable
internal fun ThirdPartyLicensesPage(
    config: AppConfig,
    onBack: () -> Unit,
) {
    val entries = listOf(
        LicenseEntry("AndroidX / Jetpack Compose", "Apache License 2.0", "android.googlesource.com/platform/frameworks/support"),
        LicenseEntry("MiuiX", "Apache License 2.0", "github.com/compose-miuix-ui/miuix"),
        LicenseEntry("KernelSU Manager UI", "GNU GPL v3", "github.com/tiann/KernelSU"),
        LicenseEntry("AndroidLiquidGlass", "Apache License 2.0", "github.com/Kyant0/AndroidLiquidGlass"),
        LicenseEntry("Material Design Icons", "Apache License 2.0", "fonts.google.com/icons"),
    )

    MiaoPage(
        title = R.string.third_party_licenses,
        bottomInnerPadding = 0.dp,
        blurEnabled = config.blurEnabled,
        navigationIcon = { BackNavigationButton(onBack) },
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                entries.forEach { entry ->
                    BasicComponent(
                        title = entry.name,
                        summary = "${entry.license}\n${entry.source}",
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = stringResource(R.string.project_license),
                    summary = stringResource(R.string.project_license_summary),
                )
            }
        }
    }
}
