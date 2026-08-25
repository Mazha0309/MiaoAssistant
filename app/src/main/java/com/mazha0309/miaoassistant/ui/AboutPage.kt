package com.mazha0309.miaoassistant.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazha0309.miaoassistant.BuildConfig
import com.mazha0309.miaoassistant.R
import com.mazha0309.miaoassistant.config.AppConfig
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AboutPage(
    config: AppConfig,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    MiaoPage(
        title = R.string.about,
        bottomInnerPadding = 0.dp,
        blurEnabled = config.blurEnabled,
        navigationIcon = { BackNavigationButton(onBack) },
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(18.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 20.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.version_summary, BuildConfig.VERSION_NAME),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.about_description),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
            }

            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.third_party_licenses),
                    summary = stringResource(R.string.third_party_licenses_summary),
                    startAction = { PreferenceIcon(Icons.Rounded.Policy) },
                    onClick = onOpenLicenses,
                )
            }
        }
    }
}
