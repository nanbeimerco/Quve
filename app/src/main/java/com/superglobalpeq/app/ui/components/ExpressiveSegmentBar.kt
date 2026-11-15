package com.superglobalpeq.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.superglobalpeq.app.ui.haptics.Haptic
import com.superglobalpeq.app.ui.haptics.rememberHaptics
import com.superglobalpeq.app.ui.icons.LucideIcons
import com.superglobalpeq.app.ui.theme.LocalQuveExpressiveColorScheme

/**
 * Material 3 Expressive switch synced with [LocalQuveExpressiveColorScheme] so
 * toggles inside monochrome Liquid Glass overlays share the exact same accent
 * colors as [ExpressiveSegmentBar].
 */
@Composable
fun QuveExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = LocalQuveExpressiveColorScheme.current ?: MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    Switch(
        checked = checked,
        onCheckedChange = { next ->
            haptics.play(Haptic.Select)
            onCheckedChange(next)
        },
        modifier = modifier,
        thumbContent = if (checked) {
            {
                Icon(
                    imageVector = LucideIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        } else {
            null
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = scheme.onPrimary,
            checkedTrackColor = scheme.primary,
            checkedBorderColor = Color.Transparent,
            checkedIconColor = scheme.primary,
            uncheckedThumbColor = scheme.onSurfaceVariant,
            uncheckedTrackColor = scheme.surfaceContainerHighest,
            uncheckedBorderColor = scheme.outline.copy(alpha = 0.65f),
        ),
    )
}

/**
 * Material 3 Expressive connected toggle button group used for non-floating
 * segmented selectors (Common/L/R channel target, theme mode, glass material,
 * and tab-pill motion style).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveSegmentBar(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) return
    val haptics = rememberHaptics()
    val scheme = LocalQuveExpressiveColorScheme.current ?: MaterialTheme.colorScheme
    val colors = ToggleButtonDefaults.colors(
        containerColor = scheme.surfaceContainerHighest,
        contentColor = scheme.onSurfaceVariant,
        checkedContainerColor = scheme.primary,
        checkedContentColor = scheme.onPrimary,
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val shapes = when {
                labels.size == 1 -> ToggleButtonDefaults.shapesFor(42.dp)
                index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                index == labels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(
                checked = selected,
                onCheckedChange = {
                    if (!selected) {
                        haptics.play(Haptic.Select)
                        onSelect(index)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shapes = shapes,
                colors = colors,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
