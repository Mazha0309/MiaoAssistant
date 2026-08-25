package com.mazha0309.miaoassistant.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazha0309.miaoassistant.ui.liquid.FloatingBottomBar as KsuFloatingBottomBar
import com.mazha0309.miaoassistant.ui.liquid.FloatingBottomBarItem as KsuFloatingBottomBarItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class BottomBarItem(
    val label: String,
    val icon: ImageVector,
)

@Composable
internal fun MiaoBottomBar(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    floating: Boolean,
    liquidGlass: Boolean,
    backdrop: LayerBackdrop?,
    onSelected: (Int) -> Unit,
) {
    if (!floating) {
        NavigationBar {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    selected = selectedIndex == index,
                    onClick = { onSelected(index) },
                    icon = item.icon,
                    label = item.label,
                )
            }
        }
        return
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = navBarPadding + 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (liquidGlass && backdrop != null) {
            // KernelSU's component observes this provider with snapshotFlow. Keep the
            // provider stable while updating the State it reads, otherwise its lens
            // remains on the index captured during the first composition.
            val selectedIndexState = rememberUpdatedState(selectedIndex)
            val onSelectedState = rememberUpdatedState(onSelected)
            val selectedIndexProvider = remember { { selectedIndexState.value } }
            val glassSelectionHandler = remember {
                { index: Int ->
                    if (index != selectedIndexState.value) {
                        onSelectedState.value(index)
                    }
                }
            }
            KsuFloatingBottomBar(
                selectedIndex = selectedIndexProvider,
                onSelected = glassSelectionHandler,
                backdrop = backdrop,
                tabsCount = items.size,
                isBlurEnabled = true,
            ) {
                items.forEachIndexed { index, item ->
                    KsuFloatingBottomBarItem(
                        onClick = { glassSelectionHandler(index) },
                        modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                        )
                        Text(
                            text = item.label,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Visible,
                        )
                    }
                }
            }
        } else {
            FloatingBarSurface(
                items = items,
                selectedIndex = selectedIndex,
                onSelected = onSelected,
            )
        }
    }
}

@Composable
private fun FloatingBarSurface(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val containerColor = colors.surfaceContainer

    BoxWithConstraints(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .fillMaxWidth()
            .height(64.dp)
            .dropShadow(
                shape = CircleShape,
                shadow = Shadow(
                    radius = 10.dp,
                    color = Color.Black,
                    alpha = if (dark) 0.22f else 0.10f,
                ),
            )
            .background(containerColor, CircleShape)
            .border(
                width = 1.dp,
                color = colors.dividerLine.copy(alpha = 0.45f),
                shape = CircleShape,
            )
            .clip(CircleShape),
    ) {
        val contentWidth = maxWidth - 8.dp
        val itemWidth = contentWidth / items.size
        val targetOffset = 4.dp + itemWidth * selectedIndex
        val indicatorOffset = animateDpAsState(
            targetValue = targetOffset,
            animationSpec = spring(stiffness = 360f, dampingRatio = 0.82f),
            label = "floatingBarIndicator",
        )

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = indicatorOffset.value.roundToPx(),
                        y = 4.dp.roundToPx(),
                    )
                }
                .width(itemWidth)
                .height(56.dp)
                .background(
                    color = colors.primary.copy(alpha = 0.14f),
                    shape = CircleShape,
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val tint = animateColorAsState(
                    targetValue = if (selected) colors.primary else colors.onSurface.copy(alpha = 0.58f),
                    label = "floatingBarTint",
                )
                val scale = animateFloatAsState(
                    targetValue = if (selected) 1.06f else 1f,
                    animationSpec = spring(stiffness = 500f, dampingRatio = 0.78f),
                    label = "floatingBarScale",
                )
                val interactionSource = remember { MutableInteractionSource() }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            onClick = { onSelected(index) },
                            role = Role.Tab,
                            interactionSource = interactionSource,
                            indication = null,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp * scale.value),
                        tint = tint.value,
                    )
                    Text(
                        text = item.label,
                        color = tint.value,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
