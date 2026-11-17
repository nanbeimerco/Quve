package com.superglobalpeq.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superglobalpeq.app.R
import com.superglobalpeq.app.data.QuveLanguage
import com.superglobalpeq.app.data.QuveSettings
import com.superglobalpeq.app.model.AppAudioMode
import com.superglobalpeq.app.model.AppRuleItem
import com.superglobalpeq.app.model.BundleSelectionOutcome
import com.superglobalpeq.app.model.DeviceCategory
import com.superglobalpeq.app.model.DeviceIndependentRuleAction
import com.superglobalpeq.app.model.DeviceRoutingRule
import com.superglobalpeq.app.model.DeviceRuleAction
import com.superglobalpeq.app.model.DeviceSwapRuleAction
import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.model.IndependentPeqPreset
import com.superglobalpeq.app.model.OutputDeviceSnapshot
import com.superglobalpeq.app.model.SwapPosition
import com.superglobalpeq.app.model.evaluateSavedPresetSelection
import com.superglobalpeq.app.service.PeqEngineManager
import com.superglobalpeq.app.ui.icons.LucideIcons
import com.superglobalpeq.app.ui.theme.LocalQuveExpressiveColorScheme

@Composable
fun RoutingTab(
    channelSwap: Boolean,
    swapPosition: SwapPosition,
    onChannelSwapChanged: (Boolean, SwapPosition) -> Unit,
    globalLowLatency: Boolean,
    lowLatencyActive: Boolean,
    perAppLowLatencyActive: Boolean,
    onGlobalLowLatencyChanged: (Boolean) -> Unit,
    backgroundPersistenceEnabled: Boolean,
    onBackgroundPersistenceChanged: (Boolean) -> Unit,
    connectedDevices: List<OutputDeviceSnapshot>,
    activeDevice: OutputDeviceSnapshot?,
    deviceRules: List<DeviceRoutingRule>,
    presets: List<EqPreset>,
    independentPresets: List<IndependentPeqPreset>,
    onUpdateDeviceRule: (
        deviceKey: String,
        action: DeviceRuleAction,
        presetId: String?,
        independentAction: DeviceIndependentRuleAction,
        independentPresetId: String?,
        channelSwapAction: DeviceSwapRuleAction
    ) -> Unit,
    onRemoveDeviceRule: (String) -> Unit,
    appRules: List<AppRuleItem>,
    onOpenAppRulePicker: () -> Unit,
    onChangeAppMode: (String, AppAudioMode) -> Unit,
    onResetAppRules: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appLanguage by QuveSettings.appLanguage.collectAsState()
    val connectedKeys = connectedDevices.map { it.deviceKey }.toSet()
    var editingRule by remember { mutableStateOf<DeviceRoutingRule?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Channel Swap Card (decoupled from presets)
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = LucideIcons.ArrowLeftRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.routing_channel_swap_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                softWrap = false,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(
                                    if (channelSwap) R.string.routing_channel_swap_on else R.string.routing_channel_swap_off
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (channelSwap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    QuveExpressiveSwitch(
                        checked = channelSwap,
                        onCheckedChange = { onChannelSwapChanged(it, swapPosition) }
                    )
                }

                // Signal chain flow box
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val flowText = when {
                        !channelSwap ->
                            stringResource(R.string.routing_flow_off)
                        swapPosition == SwapPosition.BEFORE_INDEPENDENT_EQ ->
                            stringResource(R.string.routing_flow_before)
                        else ->
                            stringResource(R.string.routing_flow_after)
                    }
                    Text(
                        text = flowText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.routing_swap_timing_label),
                    style = MaterialTheme.typography.labelLarge
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(ExpressiveGroupDefaults.Gap)
                ) {
                    val positions = SwapPosition.entries
                    positions.forEachIndexed { idx, pos ->
                        val selected = swapPosition == pos
                        val rowColor = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .expressiveItem(idx, positions.size, rowColor)
                                .clickable { onChannelSwapChanged(channelSwap, pos) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { onChannelSwapChanged(channelSwap, pos) }
                            )
                            Spacer(Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pos.titleLabel(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = pos.detailLabel(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Output Device Auto-Switching Rules
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ExpressiveGroupTitle(text = stringResource(R.string.routing_rules_title, deviceRules.size))
            Text(
                text = stringResource(R.string.routing_rules_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(2.dp))
            ExpressiveGroup {
                deviceRules.forEachIndexed { idx, rule ->
                    val isActive = activeDevice?.deviceKey == rule.deviceKey
                    val isConnected = rule.deviceKey in connectedKeys
                    DeviceRuleRow(
                        rowIndex = idx,
                        rowCount = deviceRules.size,
                        rule = rule,
                        isActive = isActive,
                        isConnected = isConnected,
                        presets = presets,
                        independentPresets = independentPresets,
                        onOpenEditor = { editingRule = rule },
                        onRemove = if (rule.deviceKey != "speaker" && !isConnected) {
                            { onRemoveDeviceRule(rule.deviceKey) }
                        } else {
                            null
                        }
                    )
                }
            }
        }

        // Global Low-Latency Card + Per-App Audio Rules
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = LucideIcons.Zap,
                            contentDescription = null,
                            tint = if (lowLatencyActive || globalLowLatency) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.routing_low_latency_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    softWrap = false,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (lowLatencyActive) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = stringResource(R.string.low_latency_badge),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            softWrap = false,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = stringResource(
                                    if (globalLowLatency) R.string.routing_low_latency_subtitle_on
                                    else R.string.routing_low_latency_subtitle_off
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (globalLowLatency || perAppLowLatencyActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    QuveExpressiveSwitch(
                        checked = globalLowLatency,
                        onCheckedChange = onGlobalLowLatencyChanged
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val latencyFlowText = when {
                        globalLowLatency -> stringResource(R.string.routing_low_latency_flow_global_on)
                        perAppLowLatencyActive -> stringResource(R.string.routing_low_latency_flow_per_app_on)
                        else -> stringResource(R.string.routing_low_latency_flow_standard)
                    }
                    Text(
                        text = latencyFlowText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (globalLowLatency || perAppLowLatencyActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Per-App Audio Rules (Standard / Bypass / Low Latency)
        AppRoutingSection(
            appRules = appRules,
            globalLowLatency = globalLowLatency,
            onOpenPicker = onOpenAppRulePicker,
            onChangeAppMode = onChangeAppMode,
            onResetAll = onResetAppRules
        )

        // Background Keep-Alive Card
        BackgroundPersistenceCard(
            enabled = backgroundPersistenceEnabled,
            onChanged = onBackgroundPersistenceChanged
        )

        // App Language Card
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ExpressiveGroupTitle(text = stringResource(R.string.lang_section_title))
            Text(
                text = stringResource(R.string.lang_section_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(2.dp))
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val languages = QuveLanguage.entries
                    ExpressiveSegmentBar(
                        labels = languages.map { it.displayLabel() },
                        selectedIndex = languages.indexOf(appLanguage).coerceAtLeast(0),
                        onSelect = { index ->
                            val selected = languages[index]
                            QuveSettings.setAppLanguage(context, selected)
                            PeqEngineManager.onAppLocaleChanged()
                        }
                    )
                }
            }
        }
    }

    editingRule?.let { targetRule ->
        DeviceRuleEditorSheet(
            rule = targetRule,
            presets = presets,
            independentPresets = independentPresets,
            onDismiss = { editingRule = null },
            onApply = { action, presetId, indepAction, indepPresetId, swapAction ->
                onUpdateDeviceRule(
                    targetRule.deviceKey,
                    action,
                    presetId,
                    indepAction,
                    indepPresetId,
                    swapAction
                )
                editingRule = null
            }
        )
    }
}

@Composable
private fun BackgroundPersistenceCard(
    enabled: Boolean,
    onChanged: (Boolean) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = LucideIcons.ShieldCheck,
                        contentDescription = null,
                        tint = if (enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.routing_bg_persistence_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            softWrap = false,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(
                                if (enabled) R.string.routing_bg_persistence_subtitle_on
                                else R.string.routing_bg_persistence_subtitle_off
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                QuveExpressiveSwitch(
                    checked = enabled,
                    onCheckedChange = onChanged
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        if (enabled) R.string.routing_bg_persistence_desc_on
                        else R.string.routing_bg_persistence_desc_off
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun DeviceRuleRow(
    rowIndex: Int,
    rowCount: Int,
    rule: DeviceRoutingRule,
    isActive: Boolean,
    isConnected: Boolean,
    presets: List<EqPreset>,
    independentPresets: List<IndependentPeqPreset>,
    onOpenEditor: () -> Unit,
    onRemove: (() -> Unit)?
) {
    val icon = when (rule.category) {
        DeviceCategory.SPEAKER -> LucideIcons.Speaker
        DeviceCategory.BLUETOOTH -> LucideIcons.Bluetooth
        DeviceCategory.USB_DAC -> LucideIcons.Usb
        DeviceCategory.WIRED -> LucideIcons.Headphones
    }

    val currentRuleLabel = when (rule.action) {
        DeviceRuleAction.FOLLOW_MANUAL -> stringResource(R.string.rule_follow_manual_short)
        DeviceRuleAction.EQ_OFF -> stringResource(R.string.rule_eq_off_short)
        DeviceRuleAction.PRESET -> {
            val commonName = rule.presetId?.let { id -> presets.firstOrNull { it.id == id } }?.displayName()
            val indepLabel = when (rule.independentAction) {
                DeviceIndependentRuleAction.KEEP_CURRENT -> null
                DeviceIndependentRuleAction.CLEAR_NONE -> stringResource(R.string.lr_preset_none_short)
                DeviceIndependentRuleAction.APPLY_PRESET -> {
                    val found = rule.independentPresetId?.let { id ->
                        independentPresets.firstOrNull { it.id == id }
                    }
                    found?.let { "L/R: ${it.name}" }
                }
            }
            when {
                commonName != null && indepLabel != null -> "$commonName + $indepLabel"
                commonName != null -> commonName
                indepLabel != null -> "${stringResource(R.string.rule_summary_keep_common)} + $indepLabel"
                else -> stringResource(R.string.rule_preset_unselected)
            }
        }
    }

    val rowColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f)
    } else {
        ExpressiveGroupDefaults.containerColor()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveItem(rowIndex, rowCount, rowColor)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = rule.displayDeviceName(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val deviceBadges = buildList {
                    if (isActive) {
                        add(
                            PresetBadgeSpec(
                                text = stringResource(R.string.device_badge_active),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            )
                        )
                    } else if (isConnected) {
                        add(
                            PresetBadgeSpec(
                                text = stringResource(R.string.device_badge_connected),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        )
                    }
                    if (rule.action != DeviceRuleAction.EQ_OFF &&
                        rule.channelSwapAction != DeviceSwapRuleAction.FOLLOW_MANUAL
                    ) {
                        add(
                            PresetBadgeSpec(
                                text = if (rule.channelSwapAction == DeviceSwapRuleAction.FORCE_ON) {
                                    "L⇄R ON"
                                } else {
                                    "L⇄R OFF"
                                },
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        )
                    }
                }
                PresetBadgeFlowRow(badges = deviceBadges)
                Text(
                    text = rule.category.displayLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = LucideIcons.Trash2,
                        contentDescription = stringResource(R.string.cd_remove_history),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        FilledTonalButton(
            onClick = onOpenEditor,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.rule_action_prefix, currentRuleLabel),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(LucideIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceRuleEditorSheet(
    rule: DeviceRoutingRule,
    presets: List<EqPreset>,
    independentPresets: List<IndependentPeqPreset>,
    onDismiss: () -> Unit,
    onApply: (
        action: DeviceRuleAction,
        presetId: String?,
        independentAction: DeviceIndependentRuleAction,
        independentPresetId: String?,
        channelSwapAction: DeviceSwapRuleAction
    ) -> Unit
) {
    val expressiveColors = LocalQuveExpressiveColorScheme.current ?: MaterialTheme.colorScheme
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.84f).dp

    var draftAction by remember(rule.deviceKey) { mutableStateOf(rule.action) }
    var draftPresetId by remember(rule.deviceKey) { mutableStateOf(rule.presetId) }
    var draftIndepAction by remember(rule.deviceKey) { mutableStateOf(rule.independentAction) }
    var draftIndepPresetId by remember(rule.deviceKey) { mutableStateOf(rule.independentPresetId) }
    var draftSwapAction by remember(rule.deviceKey) { mutableStateOf(rule.channelSwapAction) }
    var activeTabIndex by remember(rule.deviceKey) { mutableIntStateOf(0) }
    var conflictBundleTarget by remember { mutableStateOf<Pair<EqPreset, IndependentPeqPreset>?>(null) }

    val devIcon = when (rule.category) {
        DeviceCategory.SPEAKER -> LucideIcons.Speaker
        DeviceCategory.BLUETOOTH -> LucideIcons.Bluetooth
        DeviceCategory.USB_DAC -> LucideIcons.Usb
        DeviceCategory.WIRED -> LucideIcons.Headphones
    }

    val topActions = listOf(
        DeviceRuleAction.FOLLOW_MANUAL,
        DeviceRuleAction.PRESET,
        DeviceRuleAction.EQ_OFF
    )
    val swapActions = listOf(
        DeviceSwapRuleAction.FOLLOW_MANUAL,
        DeviceSwapRuleAction.FORCE_OFF,
        DeviceSwapRuleAction.FORCE_ON
    )

    GlassModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = expressiveColors.primaryContainer.copy(alpha = 0.72f),
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = devIcon,
                            contentDescription = null,
                            tint = expressiveColors.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.displayDeviceName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.rule_sheet_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Device base action selector
            ExpressiveSegmentBar(
                labels = listOf(
                    stringResource(R.string.rule_sheet_action_manual),
                    stringResource(R.string.rule_sheet_action_preset),
                    stringResource(R.string.rule_sheet_action_eq_off),
                ),
                selectedIndex = topActions.indexOf(draftAction).coerceAtLeast(0),
                onSelect = { idx -> draftAction = topActions[idx] }
            )

            // Per-device L⇄R channel swap selector (shown whenever EQ is not bypassed on this device)
            if (draftAction != DeviceRuleAction.EQ_OFF) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.rule_swap_section_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ExpressiveSegmentBar(
                        labels = listOf(
                            stringResource(R.string.rule_swap_follow_manual),
                            stringResource(R.string.rule_swap_force_off),
                            stringResource(R.string.rule_swap_force_on)
                        ),
                        selectedIndex = swapActions.indexOf(draftSwapAction).coerceAtLeast(0),
                        onSelect = { idx -> draftSwapAction = swapActions[idx] }
                    )
                }
            }

            // Tabbed preset assignment (Saved Common/Bundle vs Independent L/R Presets)
            if (draftAction == DeviceRuleAction.PRESET) {
                ExpressiveSegmentBar(
                    labels = listOf(
                        stringResource(R.string.rule_sheet_tab_saved, presets.size),
                        stringResource(R.string.rule_sheet_tab_lr, independentPresets.size)
                    ),
                    selectedIndex = activeTabIndex,
                    onSelect = { activeTabIndex = it }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 160.dp)
                ) {
                    if (activeTabIndex == 0) {
                        val totalRows = presets.size + 1
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(ExpressiveGroupDefaults.Gap)
                        ) {
                            item(key = "__keep_common__") {
                                val selected = draftPresetId == null
                                DeviceSheetOptionRow(
                                    rowIndex = 0,
                                    rowCount = totalRows,
                                    title = stringResource(R.string.rule_common_keep_manual),
                                    subtitle = null,
                                    badges = if (selected) {
                                        listOf(stringResource(R.string.preset_active_badge) to true)
                                    } else {
                                        emptyList()
                                    },
                                    selected = selected,
                                    onClick = { draftPresetId = null }
                                )
                            }
                            itemsIndexed(
                                items = presets,
                                key = { _, p -> p.id }
                            ) { idx, preset ->
                                val isSelectedCommon = draftPresetId == preset.id
                                val linkedIndep = preset.linkedIndependentPresetId?.let { id ->
                                    independentPresets.firstOrNull { it.id == id }
                                }
                                val isFullMatch = isSelectedCommon && (
                                    linkedIndep == null || (
                                        draftIndepAction == DeviceIndependentRuleAction.APPLY_PRESET &&
                                            draftIndepPresetId == linkedIndep.id
                                        )
                                    )
                                val isCommonOnlyMatch = isSelectedCommon && !isFullMatch

                                val badges = buildList {
                                    if (isFullMatch) {
                                        add(stringResource(R.string.preset_active_badge) to true)
                                    } else if (isCommonOnlyMatch) {
                                        add(stringResource(R.string.preset_common_active_badge) to true)
                                    }
                                    if (linkedIndep != null) {
                                        add(stringResource(R.string.preset_type_bundle) to false)
                                    } else {
                                        add(stringResource(R.string.preset_type_common) to false)
                                    }
                                }
                                val sub = if (linkedIndep != null) {
                                    stringResource(
                                        R.string.preset_summary_linked_lr_format,
                                        preset.commonBands.count { it.enabled },
                                        preset.commonPreampDb,
                                        linkedIndep.name
                                    )
                                } else {
                                    stringResource(
                                        R.string.preset_summary_common_format,
                                        preset.commonBands.count { it.enabled },
                                        preset.commonPreampDb
                                    )
                                }

                                DeviceSheetOptionRow(
                                    rowIndex = idx + 1,
                                    rowCount = totalRows,
                                    title = preset.displayName(),
                                    subtitle = sub,
                                    badges = badges,
                                    selected = isSelectedCommon,
                                    onClick = {
                                        val activeAssignedLrId = if (draftIndepAction == DeviceIndependentRuleAction.APPLY_PRESET) {
                                            draftIndepPresetId?.takeIf { id -> independentPresets.any { it.id == id } }
                                        } else {
                                            null
                                        }
                                        val eval = evaluateSavedPresetSelection(
                                            chosenPreset = preset,
                                            activeIndependentId = activeAssignedLrId,
                                            hasDraftIndependent = false,
                                            independentPresets = independentPresets
                                        )
                                        when (eval.outcome) {
                                            BundleSelectionOutcome.APPLY_COMMON_ONLY ->
                                                draftPresetId = preset.id
                                            BundleSelectionOutcome.AUTO_APPLY_BOTH -> {
                                                draftPresetId = preset.id
                                                if (eval.targetIndependentPresetId != null) {
                                                    draftIndepAction = DeviceIndependentRuleAction.APPLY_PRESET
                                                    draftIndepPresetId = eval.targetIndependentPresetId
                                                }
                                            }
                                            BundleSelectionOutcome.PROMPT_COMMON_OR_BOTH -> {
                                                val targetIndep = independentPresets.firstOrNull {
                                                    it.id == eval.targetIndependentPresetId
                                                }
                                                if (targetIndep != null) {
                                                    conflictBundleTarget = preset to targetIndep
                                                } else {
                                                    draftPresetId = preset.id
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        val totalLrRows = independentPresets.size + 2
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(ExpressiveGroupDefaults.Gap)
                        ) {
                            item(key = "__keep_lr__") {
                                val selected = draftIndepAction == DeviceIndependentRuleAction.KEEP_CURRENT
                                DeviceSheetOptionRow(
                                    rowIndex = 0,
                                    rowCount = totalLrRows,
                                    title = stringResource(R.string.rule_lr_keep_current),
                                    subtitle = null,
                                    badges = if (selected) {
                                        listOf(stringResource(R.string.preset_active_badge) to true)
                                    } else {
                                        emptyList()
                                    },
                                    selected = selected,
                                    onClick = {
                                        draftIndepAction = DeviceIndependentRuleAction.KEEP_CURRENT
                                        draftIndepPresetId = null
                                    }
                                )
                            }
                            item(key = "__clear_lr__") {
                                val selected = draftIndepAction == DeviceIndependentRuleAction.CLEAR_NONE
                                DeviceSheetOptionRow(
                                    rowIndex = 1,
                                    rowCount = totalLrRows,
                                    title = stringResource(R.string.lr_preset_none_title),
                                    subtitle = stringResource(R.string.lr_preset_none_desc),
                                    badges = if (selected) {
                                        listOf(stringResource(R.string.preset_active_badge) to true)
                                    } else {
                                        emptyList()
                                    },
                                    selected = selected,
                                    onClick = {
                                        draftIndepAction = DeviceIndependentRuleAction.CLEAR_NONE
                                        draftIndepPresetId = null
                                    }
                                )
                            }
                            itemsIndexed(
                                items = independentPresets,
                                key = { _, item -> item.id }
                            ) { idx, indep ->
                                val selected = draftIndepAction == DeviceIndependentRuleAction.APPLY_PRESET &&
                                    draftIndepPresetId == indep.id
                                DeviceSheetOptionRow(
                                    rowIndex = idx + 2,
                                    rowCount = totalLrRows,
                                    title = indep.name,
                                    subtitle = stringResource(
                                        R.string.lr_summary_format,
                                        indep.leftBands.count { it.enabled }, indep.leftPreampDb,
                                        indep.rightBands.count { it.enabled }, indep.rightPreampDb
                                    ),
                                    badges = if (selected) {
                                        listOf(stringResource(R.string.preset_active_badge) to true)
                                    } else {
                                        emptyList()
                                    },
                                    selected = selected,
                                    onClick = {
                                        draftIndepAction = DeviceIndependentRuleAction.APPLY_PRESET
                                        draftIndepPresetId = indep.id
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Footer Cancel / Apply
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel), softWrap = false, maxLines = 1)
                }
                Button(
                    onClick = {
                        onApply(
                            draftAction,
                            draftPresetId,
                            draftIndepAction,
                            draftIndepPresetId,
                            draftSwapAction
                        )
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = expressiveColors.primary,
                        contentColor = expressiveColors.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Icon(LucideIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_apply),
                        fontWeight = FontWeight.Bold,
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }
        }
    }

    conflictBundleTarget?.let { (bundlePreset, bundleIndep) ->
        val currentAssignedLrName = independentPresets
            .firstOrNull { it.id == draftIndepPresetId }?.name
            ?: stringResource(R.string.lr_preset_none_title)
        BundlePresetConflictDialog(
            bundleName = bundlePreset.displayName(),
            bundleLrName = bundleIndep.name,
            currentLrName = currentAssignedLrName,
            onDismiss = { conflictBundleTarget = null },
            onCommonOnly = {
                conflictBundleTarget = null
                draftPresetId = bundlePreset.id
            },
            onBoth = {
                conflictBundleTarget = null
                draftPresetId = bundlePreset.id
                draftIndepAction = DeviceIndependentRuleAction.APPLY_PRESET
                draftIndepPresetId = bundleIndep.id
            }
        )
    }
}

@Composable
private fun DeviceSheetOptionRow(
    rowIndex: Int,
    rowCount: Int,
    title: String,
    subtitle: String?,
    badges: List<Pair<String, Boolean>>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val expressiveColors = LocalQuveExpressiveColorScheme.current ?: MaterialTheme.colorScheme
    val bg = if (selected) {
        expressiveColors.primaryContainer.copy(alpha = 0.44f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveItem(rowIndex, rowCount, bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        PresetBadgeFlowRow(
            badges = badges.map { (label, primaryStyle) ->
                PresetBadgeSpec(
                    text = label,
                    containerColor = if (primaryStyle) {
                        expressiveColors.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (primaryStyle) {
                        expressiveColors.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
