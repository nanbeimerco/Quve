package com.superglobalpeq.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superglobalpeq.app.R
import com.superglobalpeq.app.data.QuveLanguage
import com.superglobalpeq.app.data.QuveSettings
import com.superglobalpeq.app.model.BypassedAppItem
import com.superglobalpeq.app.model.DeviceCategory
import com.superglobalpeq.app.model.DeviceRoutingRule
import com.superglobalpeq.app.model.DeviceRuleAction
import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.model.OutputDeviceSnapshot
import com.superglobalpeq.app.model.SwapPosition
import com.superglobalpeq.app.service.PeqEngineManager
import com.superglobalpeq.app.ui.icons.LucideIcons

@Composable
fun RoutingTab(
    activePresetName: String,
    activePresetEdited: Boolean = false,
    channelSwap: Boolean,
    swapPosition: SwapPosition,
    onChannelSwapChanged: (Boolean, SwapPosition) -> Unit,
    connectedDevices: List<OutputDeviceSnapshot>,
    activeDevice: OutputDeviceSnapshot?,
    deviceRules: List<DeviceRoutingRule>,
    presets: List<EqPreset>,
    onUpdateDeviceRule: (String, DeviceRuleAction, String?) -> Unit,
    onRemoveDeviceRule: (String) -> Unit,
    bypassedApps: List<BypassedAppItem>,
    onOpenBypassAppPicker: () -> Unit,
    onRemoveBypassApp: (String) -> Unit,
    onClearBypassApps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appLanguage by QuveSettings.appLanguage.collectAsState()
    val connectedKeys = connectedDevices.map { it.deviceKey }.toSet()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Channel Swap Card synced per active preset
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
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = CircleShape
                ) {
                    Text(
                        text = stringResource(
                            R.string.routing_preset_linked,
                            if (activePresetEdited) {
                                stringResource(R.string.preset_name_edited_format, activePresetName)
                            } else {
                                activePresetName
                            },
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

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
                        onUpdate = { action, presetId -> onUpdateDeviceRule(rule.deviceKey, action, presetId) },
                        onRemove = if (rule.deviceKey != "speaker" && !isConnected) {
                            { onRemoveDeviceRule(rule.deviceKey) }
                        } else {
                            null
                        }
                    )
                }
            }
        }

        // App Bypass Exclusion List
        AppBypassSection(
            bypassedApps = bypassedApps,
            onOpenPicker = onOpenBypassAppPicker,
            onRemoveApp = onRemoveBypassApp,
            onClearAll = onClearBypassApps
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
}

@Composable
private fun DeviceRuleRow(
    rowIndex: Int,
    rowCount: Int,
    rule: DeviceRoutingRule,
    isActive: Boolean,
    isConnected: Boolean,
    presets: List<EqPreset>,
    onUpdate: (DeviceRuleAction, String?) -> Unit,
    onRemove: (() -> Unit)?
) {
    var menuExpanded by remember { mutableStateOf(false) }

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
            val found = presets.firstOrNull { it.id == rule.presetId }
            found?.displayName() ?: stringResource(R.string.rule_preset_unselected)
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
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = rule.displayDeviceName(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isActive) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ) {
                            Text(
                                text = stringResource(R.string.device_badge_active),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                softWrap = false,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    } else if (isConnected) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                text = stringResource(R.string.device_badge_connected),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                softWrap = false,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
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

        Box {
            FilledTonalButton(
                onClick = { menuExpanded = true },
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(LucideIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }

            GlassDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                GlassMenuItem(
                    text = stringResource(R.string.rule_menu_follow_manual),
                    icon = LucideIcons.SlidersHorizontal,
                    trailingIcon = if (rule.action == DeviceRuleAction.FOLLOW_MANUAL) LucideIcons.Check else null,
                    onClick = {
                        menuExpanded = false
                        onUpdate(DeviceRuleAction.FOLLOW_MANUAL, null)
                    }
                )
                GlassMenuItem(
                    text = stringResource(R.string.rule_menu_eq_off),
                    icon = LucideIcons.VolumeX,
                    trailingIcon = if (rule.action == DeviceRuleAction.EQ_OFF) LucideIcons.Check else null,
                    onClick = {
                        menuExpanded = false
                        onUpdate(DeviceRuleAction.EQ_OFF, null)
                    }
                )
                if (presets.isNotEmpty()) {
                    GlassMenuDivider()
                    presets.forEach { p ->
                        val activeForRule = rule.action == DeviceRuleAction.PRESET && rule.presetId == p.id
                        GlassMenuItem(
                            text = stringResource(R.string.rule_menu_auto_preset, p.displayName()),
                            icon = LucideIcons.AudioWaveform,
                            value = if (p.channelSwap) "L⇄R" else null,
                            trailingIcon = if (activeForRule) LucideIcons.Check else null,
                            onClick = {
                                menuExpanded = false
                                onUpdate(DeviceRuleAction.PRESET, p.id)
                            }
                        )
                    }
                }
            }
        }
    }
}
