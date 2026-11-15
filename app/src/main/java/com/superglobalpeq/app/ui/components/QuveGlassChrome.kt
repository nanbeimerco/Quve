package com.superglobalpeq.app.ui.components

import androidx.compose.ui.res.stringResource
import com.superglobalpeq.app.R

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superglobalpeq.app.service.ShizukuStatus
import com.superglobalpeq.app.ui.haptics.Haptic
import com.superglobalpeq.app.ui.haptics.rememberHaptics
import com.superglobalpeq.app.ui.icons.LucideIcons

internal val GESTURE_BAR_FLOOR: Dp = 15.dp

internal val floatingBarInsets: WindowInsets
    @Composable get() = WindowInsets.navigationBars.union(WindowInsets(bottom = GESTURE_BAR_FLOOR))

internal val PILL_INSET: Dp = 6.dp
internal val TAB_VERTICAL_PADDING: Dp = 9.dp
internal val TAB_ICON_LABEL_GAP: Dp = 2.dp
internal val PAGE_GUTTER: Dp = 16.dp

internal val GlassSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 320f)

val LocalTopBarGlassBackdrop = staticCompositionLocalOf { false }

val TopBarGlassButtonSize: Dp = 42.dp

@Composable
internal fun topBarGlass(shape: CornerBasedShape): Modifier {
    val glassEnabled = LocalLiquidGlassEnabled.current && isGlassSupported()
    return if (LocalTopBarGlassBackdrop.current && glassEnabled) {
        Modifier.liquidGlass(shape = shape, role = LocalTopBarGlassRole.current)
    } else {
        Modifier.lightweightLiquidGlass(
            shape = shape,
            fallbackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
fun TopBarGlassButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = if (LocalLiquidGlassEnabled.current && isGlassSupported()) {
        glassContentColor()
    } else {
        MaterialTheme.colorScheme.onSurface
    },
) {
    val glass = topBarGlass(CircleShape)
    Box(
        modifier = modifier
            .size(TopBarGlassButtonSize)
            .then(glass)
            .clip(CircleShape)
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Floating top-right Liquid Glass capsules without any top band/bar.
 * Stays transparent outside the capsules so page content scrolls cleanly underneath.
 */
@Composable
fun QuveFloatingTopControls(
    shizukuStatus: ShizukuStatus,
    serviceBound: Boolean,
    onShizukuClick: () -> Unit,
    onOpenAppearance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val glassEnabled = LocalLiquidGlassEnabled.current && isGlassSupported()
    val capsuleShape = remember { RoundedCornerShape(percent = 50) }
    val fg = if (glassEnabled) glassContentColor() else MaterialTheme.colorScheme.onSurface

    val (dotColor, label, statusIcon) = when (shizukuStatus) {
        ShizukuStatus.READY -> {
            if (serviceBound) {
                Triple(Color(0xFF34D399), stringResource(R.string.shizuku_uid_2000), LucideIcons.ShieldCheck)
            } else {
                Triple(MaterialTheme.colorScheme.primary, stringResource(R.string.shizuku_authorized), LucideIcons.ShieldCheck)
            }
        }
        ShizukuStatus.PERMISSION_REQUIRED -> Triple(Color(0xFFF59E0B), stringResource(R.string.shizuku_permission_needed), LucideIcons.ShieldAlert)
        ShizukuStatus.NOT_RUNNING -> Triple(MaterialTheme.colorScheme.error, stringResource(R.string.shizuku_stopped), LucideIcons.ShieldAlert)
    }
    val animatedDot by animateColorAsState(dotColor, label = "shizukuDot")

    androidx.compose.runtime.CompositionLocalProvider(
        LocalTopBarGlassBackdrop provides glassEnabled,
    ) {
        Row(
            modifier = modifier
                .padding(top = statusBarSafeInset() + 6.dp, end = PAGE_GUTTER),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .height(TopBarGlassButtonSize)
                    .then(topBarGlass(capsuleShape))
                    .clip(capsuleShape)
                    .clickable {
                        haptics.play(Haptic.Select)
                        onShizukuClick()
                    }
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(animatedDot),
                )
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = if (shizukuStatus == ShizukuStatus.READY && serviceBound) animatedDot else fg,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = label,
                    color = fg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            TopBarGlassButton(
                onClick = {
                    haptics.play(Haptic.Select)
                    onOpenAppearance()
                },
                icon = LucideIcons.Palette,
                contentDescription = stringResource(R.string.cd_appearance_settings),
            )
        }
    }
}

/**
 * Scrollable brand header placed at the top of the page (like Tinque's TabLogoHeader).
 * Scrolls smoothly under [TopFadeScrim] without overlapping the top-right capsules.
 */
@Composable
fun QuveScrollableHeader(
    presetName: String,
    isPresetModified: Boolean = false,
    channelSwap: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = statusBarSafeInset() + 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TopBarGlassButtonSize)
                .padding(end = 172.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuveGlyphMark(
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = "Quve",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val subtitlePresetName = if (isPresetModified) {
                stringResource(R.string.preset_name_edited_format, presetName)
            } else {
                presetName
            }
            Text(
                text = stringResource(R.string.header_subtitle_format, subtitlePresetName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (channelSwap) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.badge_lr_swap),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        softWrap = false,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
