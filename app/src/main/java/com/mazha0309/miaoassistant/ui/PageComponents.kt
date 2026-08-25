package com.mazha0309.miaoassistant.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mazha0309.miaoassistant.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
internal fun MiaoPage(
    @StringRes title: Int,
    bottomInnerPadding: Dp,
    blurEnabled: Boolean = false,
    navigationIcon: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val blurActive = blurEnabled && isRuntimeShaderSupported()
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = if (blurActive) {
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    } else {
        null
    }
    Scaffold(
        topBar = {
            val topBarModifier = if (backdrop != null) {
                Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = RectangleShape,
                    blurRadius = 24f,
                    colors = BlurDefaults.blurColors(
                        blendColors = listOf(BlendColorEntry(surfaceColor.copy(alpha = 0.82f))),
                        saturation = 1.12f,
                    ),
                )
            } else {
                Modifier
            }
            Box(modifier = topBarModifier) {
                TopAppBar(
                    title = stringResource(title),
                    color = if (backdrop != null) surfaceColor.copy(alpha = 0f) else surfaceColor,
                    navigationIcon = navigationIcon,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
            overscrollEffect = null,
        ) {
            item { Spacer(Modifier.height(12.dp)) }
            content()
            item { Spacer(Modifier.height(bottomInnerPadding + 16.dp)) }
        }
    }
}

@Composable
internal fun BackNavigationButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = MiuixIcons.Back,
            contentDescription = stringResource(R.string.back),
            modifier = Modifier.size(24.dp),
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun PreferenceIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier
            .padding(end = 6.dp)
            .size(24.dp),
        tint = MiuixTheme.colorScheme.onBackground,
    )
}

@Composable
internal fun EditorDialog(
    show: Boolean,
    title: String,
    summary: String,
    initialValue: String,
    minLines: Int,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(show, initialValue) { mutableStateOf(initialValue) }
    OverlayDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismiss,
    ) {
        Column {
            TextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                minLines = minLines,
                maxLines = 14,
            )
            Spacer(Modifier.height(18.dp))
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
                    text = stringResource(R.string.save),
                    onClick = { onSave(value) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
