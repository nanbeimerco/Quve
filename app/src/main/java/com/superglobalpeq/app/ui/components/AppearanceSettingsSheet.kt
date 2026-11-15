package com.superglobalpeq.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.superglobalpeq.app.R
import com.superglobalpeq.app.data.DEFAULT_GLASS_CLARITY
import com.superglobalpeq.app.data.DEFAULT_GLASS_REFRACTION
import com.superglobalpeq.app.data.DEFAULT_GLASS_SHADE
import com.superglobalpeq.app.data.DEFAULT_LIQUID_GLASS_BLUR_DP
import com.superglobalpeq.app.data.DEFAULT_LIQUID_GLASS_OPACITY
import com.superglobalpeq.app.data.GlassRenderStyle
import com.superglobalpeq.app.data.PillMotionStyle
import com.superglobalpeq.app.data.QuveLanguage
import com.superglobalpeq.app.data.QuveSettings
import com.superglobalpeq.app.data.QuveThemeMode
import com.superglobalpeq.app.data.QuveThemePreset
import com.superglobalpeq.app.service.PeqEngineManager
import com.superglobalpeq.app.ui.icons.LucideIcons
import com.superglobalpeq.app.ui.theme.LocalQuveDarkTheme
import com.superglobalpeq.app.ui.theme.LocalQuveExpressiveColorScheme
import com.superglobalpeq.app.ui.theme.swatchPrimary
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceSettingsSheet(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val appLanguage by QuveSettings.appLanguage.collectAsState()
    val themeMode by QuveSettings.themeMode.collectAsState()
    val themePreset by QuveSettings.themePreset.collectAsState()
    val amoledBlack by QuveSettings.amoledBlack.collectAsState()
    val glassRenderStyle by QuveSettings.glassRenderStyle.collectAsState()
    val glassClarity by QuveSettings.glassClarity.collectAsState()
    val glassShade by QuveSettings.glassShade.collectAsState()
    val glassRefraction by QuveSettings.glassRefraction.collectAsState()
    val blurDp by QuveSettings.liquidGlassBlurDp.collectAsState()
    val opacity by QuveSettings.liquidGlassOpacity.collectAsState()
    val pillMotionStyle by QuveSettings.pillMotionStyle.collectAsState()
    val lockBarExpanded by QuveSettings.lockBottomBarExpanded.collectAsState()
    val hideNavLabels by QuveSettings.hideNavigationBarLabels.collectAsState()
    val bottomBarBounce by QuveSettings.bottomBarBounce.collectAsState()
    val reduceBlur by QuveSettings.reduceDynamicBlur.collectAsState()
    val reduceAnim by QuveSettings.reduceAnimation.collectAsState()

    val isDark = LocalQuveDarkTheme.current
    val expressiveColors = LocalQuveExpressiveColorScheme.current ?: MaterialTheme.colorScheme
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.82f).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Sheet Header ───────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuveAppIconTile(
                size = 42.dp,
                backgroundColor = if (isDark) Color.Black else Color.White,
                glyphColor = Color(0xFFFF5935),
                shape = CircleShape
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.appearance_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.appearance_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = { QuveSettings.resetLiquidGlassParams() },
                colors = ButtonDefaults.textButtonColors(contentColor = expressiveColors.primary),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(LucideIcons.RotateCcw, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.action_reset_all), maxLines = 1, softWrap = false)
            }
        }

        // ── Language Selector ──────────────────────────────────────────────
        GlassSettingCard {
            Text(
                text = stringResource(R.string.lang_section_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            val languages = QuveLanguage.entries
            ExpressiveSegmentBar(
                labels = languages.map { it.displayLabel() },
                selectedIndex = languages.indexOf(appLanguage).coerceAtLeast(0),
                onSelect = { idx ->
                    val chosen = languages[idx]
                    QuveSettings.setAppLanguage(context, chosen)
                    PeqEngineManager.onAppLocaleChanged()
                }
            )
            Text(
                text = stringResource(R.string.lang_section_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // ── Theme Mode & AMOLED Pure Black ─────────────────────────────────
        GlassSettingCard {
            Text(
                text = stringResource(R.string.theme_section_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            val modes = QuveThemeMode.entries
            ExpressiveSegmentBar(
                labels = modes.map { it.displayLabel() },
                selectedIndex = modes.indexOf(themeMode),
                onSelect = { idx -> QuveSettings.setThemeMode(modes[idx]) }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { QuveSettings.setAmoledBlack(!amoledBlack) }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.amoled_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.amoled_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                QuveExpressiveSwitch(
                    checked = amoledBlack,
                    onCheckedChange = { QuveSettings.setAmoledBlack(it) }
                )
            }
        }

        // ── 14 Material 3 Expressive Palettes ──────────────────────────────
        GlassSettingCard {
            Text(
                text = stringResource(R.string.palette_quve_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            val quvePresets = QuveThemePreset.entries.filter { !it.isRootlessPalette }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quvePresets.forEach { preset ->
                    PalettePresetChip(
                        preset = preset,
                        selected = preset == themePreset,
                        isDark = isDark,
                        onClick = { QuveSettings.setThemePreset(preset) }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.palette_rootless_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            val rootlessPresets = QuveThemePreset.entries.filter { it.isRootlessPalette }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rootlessPresets.forEach { preset ->
                    PalettePresetChip(
                        preset = preset,
                        selected = preset == themePreset,
                        isDark = isDark,
                        onClick = { QuveSettings.setThemePreset(preset) }
                    )
                }
            }
        }

        // ── Liquid Glass Optical Controls ──────────────────────────────────
        GlassSettingCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.glass_material_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = glassRenderStyle.displayLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = expressiveColors.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }
            val styles = GlassRenderStyle.entries
            ExpressiveSegmentBar(
                labels = styles.map { it.displayLabel() },
                selectedIndex = styles.indexOf(glassRenderStyle),
                onSelect = { idx -> QuveSettings.setGlassRenderStyle(styles[idx]) }
            )

            if (glassRenderStyle == GlassRenderStyle.CLEAR) {
                GlassSliderRow(
                    label = stringResource(R.string.glass_clarity_label),
                    valueText = "${(glassClarity * 100f).roundToInt()}%",
                    value = glassClarity,
                    range = 0f..1f,
                    showReset = glassClarity != DEFAULT_GLASS_CLARITY,
                    onReset = { QuveSettings.setGlassClarity(DEFAULT_GLASS_CLARITY) },
                    onValueChange = QuveSettings::setGlassClarity
                )
                GlassSliderRow(
                    label = stringResource(R.string.glass_shade_label),
                    valueText = "${(glassShade * 100f).roundToInt()}%",
                    value = glassShade,
                    range = 0f..1f,
                    showReset = glassShade != DEFAULT_GLASS_SHADE,
                    onReset = { QuveSettings.setGlassShade(DEFAULT_GLASS_SHADE) },
                    onValueChange = QuveSettings::setGlassShade
                )
            } else {
                GlassSliderRow(
                    label = stringResource(R.string.glass_blur_label),
                    valueText = "${blurDp.roundToInt()} dp",
                    value = blurDp,
                    range = 0f..32f,
                    showReset = blurDp != DEFAULT_LIQUID_GLASS_BLUR_DP,
                    onReset = { QuveSettings.setLiquidGlassBlurDp(DEFAULT_LIQUID_GLASS_BLUR_DP) },
                    onValueChange = QuveSettings::setLiquidGlassBlurDp
                )
                GlassSliderRow(
                    label = stringResource(R.string.glass_frosted_opacity_label),
                    valueText = "${(opacity * 100f).roundToInt()}%",
                    value = opacity,
                    range = 0.05f..0.70f,
                    showReset = opacity != DEFAULT_LIQUID_GLASS_OPACITY,
                    onReset = { QuveSettings.setLiquidGlassOpacity(DEFAULT_LIQUID_GLASS_OPACITY) },
                    onValueChange = QuveSettings::setLiquidGlassOpacity
                )
            }

            GlassSliderRow(
                label = stringResource(R.string.glass_refraction_label),
                valueText = String.format(Locale.US, "%.2fx", glassRefraction),
                value = glassRefraction,
                range = 0f..1.5f,
                showReset = glassRefraction != DEFAULT_GLASS_REFRACTION,
                onReset = { QuveSettings.setGlassRefraction(DEFAULT_GLASS_REFRACTION) },
                onValueChange = QuveSettings::setGlassRefraction
            )
        }

        // ── Tab Pill & Floating Dock Motion ────────────────────────────────
        GlassSettingCard {
            Text(
                text = stringResource(R.string.dock_section_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            val motions = PillMotionStyle.entries
            ExpressiveSegmentBar(
                labels = motions.map { it.displayLabel() },
                selectedIndex = motions.indexOf(pillMotionStyle),
                onSelect = { idx -> QuveSettings.setPillMotionStyle(motions[idx]) }
            )

            GlassSwitchRow(
                title = stringResource(R.string.switch_lock_dock_title),
                subtitle = stringResource(R.string.switch_lock_dock_subtitle),
                checked = lockBarExpanded,
                onCheckedChange = QuveSettings::setLockBottomBarExpanded
            )
            if (!lockBarExpanded) {
                GlassSwitchRow(
                    title = stringResource(R.string.switch_dock_bounce_title),
                    subtitle = stringResource(R.string.switch_dock_bounce_subtitle),
                    checked = bottomBarBounce,
                    onCheckedChange = QuveSettings::setBottomBarBounce
                )
            }
            GlassSwitchRow(
                title = stringResource(R.string.switch_hide_labels_title),
                subtitle = stringResource(R.string.switch_hide_labels_subtitle),
                checked = hideNavLabels,
                onCheckedChange = QuveSettings::setHideNavigationBarLabels
            )
            GlassSwitchRow(
                title = stringResource(R.string.switch_reduce_blur_title),
                subtitle = stringResource(R.string.switch_reduce_blur_subtitle),
                checked = reduceBlur,
                onCheckedChange = QuveSettings::setReduceDynamicBlur
            )
            GlassSwitchRow(
                title = stringResource(R.string.switch_reduce_motion_title),
                subtitle = stringResource(R.string.switch_reduce_motion_subtitle),
                checked = reduceAnim,
                onCheckedChange = QuveSettings::setReduceAnimation
            )
        }

        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = expressiveColors.primary,
                contentColor = expressiveColors.onPrimary,
            )
        ) {
            Text(
                text = stringResource(R.string.action_done),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun GlassSettingCard(
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        content()
    }
}

@Composable
private fun PalettePresetChip(
    preset: QuveThemePreset,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val swatch = preset.swatchPrimary(isDark)
    val bg = if (selected) {
        swatch.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    }
    val borderColor = if (selected) swatch else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(swatch, swatch.copy(alpha = 0.65f)))
                )
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
        )
        Text(
            text = preset.displayLabel(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GlassSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    showReset: Boolean,
    onReset: () -> Unit,
    onValueChange: (Float) -> Unit,
) {
    val expressiveColors = LocalQuveExpressiveColorScheme.current ?: MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            if (showReset) {
                Text(
                    text = stringResource(R.string.action_reset),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = expressiveColors.primary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onReset)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = expressiveColors.primary,
                maxLines = 1,
                softWrap = false
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = expressiveColors.primary,
                activeTrackColor = expressiveColors.primary,
                inactiveTrackColor = expressiveColors.surfaceContainerHighest,
                activeTickColor = expressiveColors.onPrimary.copy(alpha = 0.6f),
                inactiveTickColor = expressiveColors.onSurfaceVariant.copy(alpha = 0.6f),
            )
        )
    }
}

@Composable
private fun GlassSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        QuveExpressiveSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
