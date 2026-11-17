package com.superglobalpeq.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.superglobalpeq.app.R

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import com.superglobalpeq.app.data.QuveSettings
import com.superglobalpeq.app.service.AppUiState
import com.superglobalpeq.app.ui.components.floatingtabbar.BarMorph
import com.superglobalpeq.app.ui.components.floatingtabbar.FloatingTabBar
import com.superglobalpeq.app.ui.components.floatingtabbar.FloatingTabBarDefaults
import com.superglobalpeq.app.ui.components.floatingtabbar.FloatingTabBarScrollConnection
import com.superglobalpeq.app.ui.haptics.Haptic
import com.superglobalpeq.app.ui.haptics.rememberHaptics
import com.superglobalpeq.app.ui.icons.LucideIcons
import com.superglobalpeq.app.ui.theme.LocalQuveDarkTheme
import kotlin.math.roundToInt

enum class QuveTab(
    @StringRes val titleRes: Int,
    val icon: ImageVector,
) {
    PEQ(R.string.tab_peq, LucideIcons.SlidersHorizontal),
    PRESETS(R.string.tab_presets, LucideIcons.Layers),
    ROUTING(R.string.tab_routing, LucideIcons.Route),
}

@Composable
fun QuveFloatingDock(
    selectedTab: QuveTab,
    onSelectTab: (QuveTab) -> Unit,
    scrollConnection: FloatingTabBarScrollConnection,
    uiState: AppUiState,
    onToggleMaster: () -> Unit,
    onToggleChannelSwap: () -> Unit,
    onOpenQuickTarget: () -> Unit,
    onHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillShape = remember { RoundedCornerShape(percent = 50) }
    val isDark = LocalQuveDarkTheme.current
    val glassEnabled = LocalLiquidGlassEnabled.current && isGlassSupported()
    val reduceDynamicBlur by QuveSettings.reduceDynamicBlur.collectAsState()
    val useGlass = glassEnabled && !reduceDynamicBlur
    val hideLabels by QuveSettings.hideNavigationBarLabels.collectAsState()
    val borderColor = if (isDark) GLASS_EDGE_COLOR else Color.Black.copy(alpha = 0.08f)
    val haptics = rememberHaptics()

    val baseContentColor = if (useGlass) glassContentColor() else MaterialTheme.colorScheme.onSurface
    val accentColor = MaterialTheme.colorScheme.primary
    val unselectedColor = if (useGlass) {
        baseContentColor.copy(alpha = 0.68f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val indicatorColor = if (useGlass) {
        glassSelectionPillColor()
    } else {
        accentColor.copy(alpha = 0.14f)
    }

    val currentSelected by rememberUpdatedState(selectedTab)
    val currentOnSelect by rememberUpdatedState(onSelectTab)

    val tabBarModifierFactory: @Composable () -> Modifier = remember(useGlass, pillShape, borderColor) {
        if (useGlass) {
            { Modifier.liquidGlass(shape = pillShape, role = GlassRole.TabBar) }
        } else {
            {
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, pillShape)
                    .border(GLASS_EDGE_WIDTH, borderColor, pillShape)
            }
        }
    }

    val playerModifierFactory: @Composable () -> Modifier = remember(useGlass, pillShape, borderColor) {
        if (useGlass) {
            { Modifier.liquidGlass(shape = pillShape, role = GlassRole.Player) }
        } else {
            {
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, pillShape)
                    .border(GLASS_EDGE_WIDTH, borderColor, pillShape)
            }
        }
    }

    FloatingTabBar(
        selectedTabKey = selectedTab,
        scrollConnection = scrollConnection,
        modifier = modifier
            .windowInsetsPadding(floatingBarInsets)
            .padding(horizontal = PAGE_GUTTER)
            .padding(bottom = 2.dp)
            .onSizeChanged { onHeightChanged(it.height) }
            .fillMaxWidth(),
        tabBarContentModifier = tabBarModifierFactory,
        accessory = { accessoryModifier, morph ->
            QuveQuickControlBar(
                uiState = uiState,
                morph = morph,
                contentColor = baseContentColor,
                useGlass = useGlass,
                onClickBar = {
                    if (scrollConnection.isInline) {
                        scrollConnection.expand()
                    } else {
                        onOpenQuickTarget()
                    }
                },
                onToggleMaster = onToggleMaster,
                onToggleChannelSwap = onToggleChannelSwap,
                modifier = accessoryModifier.then(playerModifierFactory()),
            )
        },
        colors = FloatingTabBarDefaults.colors(
            backgroundColor = Color.Transparent,
            accessoryBackgroundColor = Color.Transparent,
            indicatorColor = indicatorColor,
            selectedContentColor = accentColor,
            artworkColor = accentColor,
            unselectedContentColor = unselectedColor,
            isArtworkTheme = true,
        ),
        elevations = FloatingTabBarDefaults.elevations(
            inlineElevation = 0.dp,
            expandedElevation = 0.dp,
        ),
        sizes = FloatingTabBarDefaults.sizes(
            tabBarContentPadding = PaddingValues(PILL_INSET),
            tabExpandedContentPadding = PaddingValues(vertical = TAB_VERTICAL_PADDING),
            inlineBarHeight = 46.dp,
        ),
        contentKey = remember(hideLabels, useGlass, accentColor, unselectedColor, indicatorColor) {
            listOf(hideLabels, useGlass, accentColor, unselectedColor, indicatorColor)
        },
        splitStandalone = false,
        pillSampleModel = true,
    ) {
        QuveTab.entries.forEach { tab ->
            tab(
                key = tab,
                icon = {
                    val isSelected = tab == currentSelected
                    val fallbackTint = if (isSelected) accentColor else unselectedColor
                    val provided = LocalContentColor.current
                    val tint = if (provided.alpha > 0f) provided else fallbackTint
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = stringResource(tab.titleRes),
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                },
                title = {
                    if (!hideLabels) {
                        val isSelected = tab == currentSelected
                        val fallbackTint = if (isSelected) accentColor else unselectedColor
                        val provided = LocalContentColor.current
                        val tint = if (provided.alpha > 0f) provided else fallbackTint
                        Text(
                            text = stringResource(tab.titleRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = tint,
                            softWrap = false,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = TAB_ICON_LABEL_GAP),
                        )
                    }
                },
                onClick = {
                    if (tab != currentSelected) haptics.play(Haptic.Select)
                    currentOnSelect(tab)
                },
                indication = null,
            )
        }
    }
}

@Composable
private fun QuveQuickControlBar(
    uiState: AppUiState,
    morph: BarMorph,
    contentColor: Color,
    useGlass: Boolean,
    onClickBar: () -> Unit,
    onToggleMaster: () -> Unit,
    onToggleChannelSwap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val pressSource = remember { MutableInteractionSource() }
    val isPressed by pressSource.collectIsPressedAsState()
    val drop = remember(morph) { { morph.accessoryDrop } }
    val narrow = remember(morph) { { (morph.accessoryWidth / 0.5f).coerceIn(0f, 1f) } }
    val buttonsGone = remember(morph) { { (morph.accessoryWidth / QUICK_BUTTON_FADE_END).coerceIn(0f, 1f) } }
    val swapTappable by remember(morph) { derivedStateOf { morph.accessoryWidth < QUICK_BUTTON_FADE_END * 0.5f } }

    val activeRunning = uiState.masterPowerOn && !uiState.pausedByDeviceRule && uiState.telemetry.isRunning
    val statusColor by animateColorAsState(
        targetValue = when {
            activeRunning -> MaterialTheme.colorScheme.primary
            uiState.masterPowerOn && uiState.pausedByDeviceRule -> Color(0xFFF59E0B)
            else -> contentColor.copy(alpha = 0.45f)
        },
        label = "quickStatusColor",
    )

    val detectedDevice = uiState.activeDevice?.displayName
    val statusSubtitle = when {
        activeRunning -> stringResource(
            R.string.dock_status_running,
            detectedDevice ?: stringResource(R.string.dock_detecting),
        )
        uiState.masterPowerOn && uiState.pausedByDeviceRule -> stringResource(
            R.string.dock_status_paused_rule,
            detectedDevice ?: "",
        )
        uiState.masterPowerOn -> stringResource(
            R.string.dock_status_starting,
            detectedDevice ?: stringResource(R.string.dock_detecting),
        )
        else -> stringResource(
            R.string.dock_status_bypassed,
            detectedDevice ?: stringResource(R.string.dock_stopped),
        )
    }

    val placements = remember(morph) { QuickControlButtonPlacement(morph) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .graphicsLayer {
                val s = if (isPressed) 0.985f else 1f
                scaleX = s
                scaleY = s
            }
            .then(modifier)
            .clickable(
                interactionSource = pressSource,
                indication = null,
                onClick = onClickBar,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .morphPadding(12.dp, 8.dp, 8.dp, 4.dp, drop),
        ) {
            // Left Quve brand glyph badge
            Box(
                modifier = Modifier
                    .morphSize(34.dp, 28.dp, drop)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = if (uiState.masterPowerOn) 0.18f else 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                QuveGlyphMark(
                    modifier = Modifier.size(18.dp),
                    tint = statusColor,
                )
            }

            Spacer(Modifier.morphSize(10.dp, 8.dp, drop, height = false))

            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val commonShownName = if (uiState.activeCommonPreset.isCommonModifiedFromBaseline) {
                        stringResource(
                            R.string.preset_name_edited_format,
                            uiState.activeCommonPreset.displayName(),
                        )
                    } else {
                        uiState.activeCommonPreset.displayName()
                    }
                    val titleLine = uiState.activeIndependentPreset?.let { indep ->
                        "$commonShownName + L/R: ${indep.name}"
                    } ?: commonShownName
                    Text(
                        text = titleLine,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (uiState.channelSwap) {
                        Box(
                            modifier = Modifier
                                .foldAway(narrow, horizontal = true)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "L⇄R",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                softWrap = false,
                                maxLines = 1,
                            )
                        }
                    }
                    if (activeRunning && uiState.telemetry.lowLatencyActive) {
                        Box(
                            modifier = Modifier
                                .foldAway(narrow, horizontal = true)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.low_latency_badge),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                softWrap = false,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Text(
                    text = statusSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (useGlass) contentColor.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.foldAway(narrow, horizontal = false),
                )
            }

            Spacer(
                Modifier.layout { _, _ ->
                    val reserved = placements.reservedWidth(this, drop()).roundToInt().coerceAtLeast(0)
                    layout(reserved, 0) {}
                }
            )
        }

        // Right-aligned anchored Swap & Power buttons (matching Tinque GlassNowPlaying layout)
        Layout(
            modifier = Modifier.matchParentSize(),
            content = {
                // Button 0: Quick Swap L⇄R (fades out when folded into narrow inline pill)
                val swapBg by animateColorAsState(
                    targetValue = if (uiState.channelSwap) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                    } else {
                        contentColor.copy(alpha = 0.10f)
                    },
                    label = "quickSwapBg",
                )
                Box(
                    modifier = Modifier
                        .graphicsLayer { alpha = 1f - buttonsGone() }
                        .clip(CircleShape)
                        .background(swapBg)
                        .clickable(enabled = swapTappable) {
                            haptics.play(Haptic.Select)
                            onToggleChannelSwap()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = LucideIcons.ArrowLeftRight,
                        contentDescription = stringResource(R.string.cd_toggle_channel_swap),
                        tint = if (uiState.channelSwap) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp),
                    )
                }

                // Button 1: 1-tap Power ON/OFF pill
                val powerBg by animateColorAsState(
                    targetValue = if (uiState.masterPowerOn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        contentColor.copy(alpha = 0.14f)
                    },
                    label = "quickPowerBg",
                )
                val powerFg by animateColorAsState(
                    targetValue = if (uiState.masterPowerOn) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        contentColor
                    },
                    label = "quickPowerFg",
                )
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            val scale = 1f + (POWER_FOLDED_SCALE - 1f) * drop()
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(powerBg)
                        .clickable {
                            haptics.play(Haptic.Select)
                            onToggleMaster()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = LucideIcons.Power,
                        contentDescription = if (uiState.masterPowerOn) "EQ OFF" else "EQ ON",
                        tint = powerFg,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        ) { measurables, constraints ->
            val slot = QUICK_BUTTON_SLOT.roundToPx()
            val placeables = measurables.map { it.measure(Constraints.fixed(slot, slot)) }
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
            val height = if (constraints.hasBoundedHeight) constraints.maxHeight else maxOf(constraints.minHeight, slot)
            layout(width, height) {
                val y = (height - slot) / 2
                val centers = placements.centersFromRight(this@Layout)
                placeables.forEachIndexed { index, placeable ->
                    val fromRight = centers.getOrNull(index) ?: return@forEachIndexed
                    placeable.place((width - fromRight - slot / 2f).roundToInt(), y)
                }
            }
        }
    }
}

private const val QUICK_BUTTON_FADE_END = 0.35f
private const val POWER_FOLDED_SCALE = 0.86f
private val QUICK_BUTTON_SLOT = 34.dp

private class QuickControlButtonPlacement(
    private val morph: BarMorph,
) {
    private val slot = QUICK_BUTTON_SLOT
    private val gap = 6.dp

    private fun Density.powerExpanded(): Float = (10.dp + slot / 2).toPx()
    private fun Density.powerFolded(): Float = (6.dp + 16.dp).toPx()
    private fun Density.power(): Float = powerExpanded() + (powerFolded() - powerExpanded()) * morph.accessoryWidth
    private fun Density.swap(): Float = (10.dp + slot + gap + slot / 2).toPx() - morph.accessoryRightInset

    fun centersFromRight(density: Density): List<Float> = with(density) {
        listOf(swap(), power())
    }

    fun reservedWidth(density: Density, drop: Float): Float = with(density) {
        val half = (slot / 2).toPx()
        val swapLeft = swap() + half
        val powerLeft = power() + half * (1f + (POWER_FOLDED_SCALE - 1f) * drop)
        val gone = (morph.accessoryWidth / QUICK_BUTTON_FADE_END).coerceIn(0f, 1f)
        val edge = swapLeft + (powerLeft - swapLeft) * gone
        val rowEndPadding = (12.dp + (8.dp - 12.dp) * drop).toPx()
        edge + 6.dp.toPx() - rowEndPadding
    }
}

private fun Modifier.morphSize(from: Dp, to: Dp, fraction: () -> Float, height: Boolean = true): Modifier =
    layout { measurable, _ ->
        val f = fraction()
        val px = (from.toPx() + (to.toPx() - from.toPx()) * f).roundToInt()
        val placeable = measurable.measure(
            if (height) Constraints.fixed(px, px) else Constraints.fixed(px, 0),
        )
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

private fun Modifier.morphPadding(fromH: Dp, toH: Dp, fromV: Dp, toV: Dp, fraction: () -> Float): Modifier =
    layout { measurable, constraints ->
        val f = fraction()
        val h = (fromH.toPx() + (toH.toPx() - fromH.toPx()) * f).roundToInt()
        val v = (fromV.toPx() + (toV.toPx() - fromV.toPx()) * f).roundToInt()
        val placeable = measurable.measure(constraints.offset(-2 * h, -2 * v))
        val width = constraints.constrainWidth(placeable.width + 2 * h)
        val height = constraints.constrainHeight(placeable.height + 2 * v)
        layout(width, height) { placeable.place(h, v) }
    }

private fun Modifier.foldAway(fraction: () -> Float, horizontal: Boolean): Modifier =
    this
        .layout { measurable, constraints ->
            val placeable = measurable.measure(
                if (horizontal) constraints.copy(minWidth = 0) else constraints.copy(minHeight = 0),
            )
            val keep = (1f - fraction()).coerceIn(0f, 1f)
            val width = if (horizontal) (placeable.width * keep).roundToInt() else placeable.width
            val height = if (horizontal) placeable.height else (placeable.height * keep).roundToInt()
            layout(width, height) { placeable.place(0, 0) }
        }
        .graphicsLayer {
            alpha = (1f - fraction()).coerceIn(0f, 1f)
            clip = true
        }
