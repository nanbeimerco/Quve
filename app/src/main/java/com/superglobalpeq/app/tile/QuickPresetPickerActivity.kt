package com.superglobalpeq.app.tile

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superglobalpeq.app.MainActivity
import com.superglobalpeq.app.R
import com.superglobalpeq.app.data.QuveSettings
import com.superglobalpeq.app.model.BundleSelectionOutcome
import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.model.IndependentPeqPreset
import com.superglobalpeq.app.service.AppUiState
import com.superglobalpeq.app.service.PeqEngineManager
import com.superglobalpeq.app.service.ShizukuStatus
import com.superglobalpeq.app.service.ShootoutManager
import com.superglobalpeq.app.service.ShootoutUiState
import com.superglobalpeq.app.ui.components.ExpressiveGroupDefaults
import com.superglobalpeq.app.ui.components.ExpressiveSegmentBar
import com.superglobalpeq.app.ui.components.PresetBadgeFlowRow
import com.superglobalpeq.app.ui.components.PresetBadgeSpec
import com.superglobalpeq.app.ui.components.QuveGlyphMark
import com.superglobalpeq.app.ui.components.QuveTab
import com.superglobalpeq.app.ui.components.displayName
import com.superglobalpeq.app.ui.components.expressiveItem
import com.superglobalpeq.app.ui.haptics.Haptic
import com.superglobalpeq.app.ui.haptics.rememberHaptics
import com.superglobalpeq.app.ui.icons.LucideIcons
import com.superglobalpeq.app.ui.theme.QuveTheme

class QuickPresetPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                android.R.anim.fade_in,
                android.R.anim.fade_out,
            )
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                android.R.anim.fade_in,
                android.R.anim.fade_out,
            )
        }
        QuveSettings.initialize(applicationContext)
        QuveSettings.syncLocaleFromSystem(this)
        PeqEngineManager.initialize(applicationContext)
        PeqEngineManager.refreshShizukuStatus()

        setContent {
            QuveTheme {
                val uiState by PeqEngineManager.uiState.collectAsState()
                val shootoutState by ShootoutManager.state.collectAsState()
                QuickPresetPickerSheet(
                    uiState = uiState,
                    shootoutState = shootoutState,
                    onDismiss = { finish() },
                    onOpenMainTab = { tab, requestShizuku ->
                        openMainAppTab(tab, requestShizuku)
                    },
                )
            }
        }
    }

    private fun openMainAppTab(tab: QuveTab, requestShizuku: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(MainActivity.EXTRA_OPEN_TAB, tab.name)
            if (requestShizuku) {
                putExtra(MainActivity.EXTRA_REQUEST_SHIZUKU, true)
            }
        }
        startActivity(intent)
        finish()
    }
}

@Composable
private fun QuickPresetPickerSheet(
    uiState: AppUiState,
    shootoutState: ShootoutUiState,
    onDismiss: () -> Unit,
    onOpenMainTab: (QuveTab, Boolean) -> Unit,
) {
    val haptics = rememberHaptics()
    var selectedSegment by rememberSaveable { mutableIntStateOf(0) }
    var conflictBundleTarget by remember { mutableStateOf<Pair<EqPreset, IndependentPeqPreset>?>(null) }

    val activeDeviceLabel = uiState.activeDevice?.displayName
        ?: stringResource(R.string.default_output_label)
    val subtitleText = when {
        uiState.masterPowerOn && uiState.pausedByDeviceRule ->
            stringResource(R.string.qs_picker_status_rule_off, activeDeviceLabel)
        uiState.masterPowerOn ->
            stringResource(R.string.qs_picker_status_running, activeDeviceLabel)
        else ->
            stringResource(R.string.qs_picker_status_stopped, activeDeviceLabel)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuveGlyphMark(
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.qs_picker_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.qs_picker_close),
                            softWrap = false,
                            maxLines = 1,
                        )
                    }
                }

                if (shootoutState.running != null) {
                    // Blind-mode protection: hide all presets while Shootout is active.
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.qs_picker_shootout_blocked),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Button(
                                onClick = { onOpenMainTab(QuveTab.SHOOTOUT, false) },
                                shape = CircleShape,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = stringResource(R.string.qs_picker_open_shootout),
                                    softWrap = false,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                } else {
                    if (uiState.masterPowerOn && uiState.pausedByDeviceRule) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.qs_picker_device_rule_off, activeDeviceLabel),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                FilledTonalButton(
                                    onClick = { onOpenMainTab(QuveTab.ROUTING, false) },
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.qs_picker_open_routing),
                                        fontSize = 12.sp,
                                        softWrap = false,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    } else if (!uiState.masterPowerOn) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.qs_picker_engine_off),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Button(
                                    onClick = {
                                        if (uiState.shizukuStatus == ShizukuStatus.READY) {
                                            haptics.play(Haptic.Select)
                                            PeqEngineManager.setMasterPower(true)
                                        } else {
                                            onOpenMainTab(QuveTab.PEQ, true)
                                        }
                                    },
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.qs_picker_turn_on),
                                        fontSize = 12.sp,
                                        softWrap = false,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }

                    ExpressiveSegmentBar(
                        labels = listOf(
                            stringResource(R.string.qs_picker_tab_saved, uiState.presets.size),
                            stringResource(R.string.qs_picker_tab_lr, uiState.independentPresets.size),
                        ),
                        selectedIndex = selectedSegment,
                        onSelect = { selectedSegment = it },
                    )

                    if (selectedSegment == 0) {
                        SavedPresetsQuickList(
                            uiState = uiState,
                            onSelectPreset = { preset ->
                                haptics.play(Haptic.Select)
                                val eval = PeqEngineManager.evaluateSavedPresetClick(preset.id)
                                when (eval.outcome) {
                                    BundleSelectionOutcome.APPLY_COMMON_ONLY ->
                                        PeqEngineManager.selectSavedPreset(preset.id, applyIndependentToo = false)
                                    BundleSelectionOutcome.AUTO_APPLY_BOTH ->
                                        PeqEngineManager.selectSavedPreset(preset.id, applyIndependentToo = true)
                                    BundleSelectionOutcome.PROMPT_COMMON_OR_BOTH -> {
                                        val targetIndep = eval.targetIndependentPresetId?.let { id ->
                                            uiState.independentPresets.firstOrNull { it.id == id }
                                        }
                                        if (targetIndep != null) {
                                            conflictBundleTarget = preset to targetIndep
                                        } else {
                                            PeqEngineManager.selectSavedPreset(preset.id, applyIndependentToo = false)
                                        }
                                    }
                                }
                            },
                        )
                    } else {
                        IndependentPresetsQuickList(
                            uiState = uiState,
                            onSelectIndependent = { id ->
                                haptics.play(Haptic.Select)
                                PeqEngineManager.selectIndependentPreset(id)
                            },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { onOpenMainTab(QuveTab.PRESETS, false) }) {
                            Text(
                                text = stringResource(R.string.qs_picker_open_presets),
                                softWrap = false,
                                maxLines = 1,
                            )
                        }
                        FilledTonalButton(
                            onClick = onDismiss,
                            shape = CircleShape,
                        ) {
                            Text(
                                text = stringResource(R.string.qs_picker_close),
                                softWrap = false,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    conflictBundleTarget?.let { (bundlePreset, bundleIndep) ->
        val currentLrName = uiState.activeIndependentPreset?.name
            ?: stringResource(R.string.lr_preset_none_title)
        AlertDialog(
            onDismissRequest = { conflictBundleTarget = null },
            title = { Text(stringResource(R.string.dialog_bundle_conflict_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(
                            R.string.dialog_bundle_conflict_message,
                            bundlePreset.displayName(),
                            bundleIndep.name,
                            currentLrName,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FilledTonalButton(
                        onClick = {
                            conflictBundleTarget = null
                            PeqEngineManager.selectSavedPreset(bundlePreset.id, applyIndependentToo = false)
                        },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_apply_common_only), softWrap = false, maxLines = 1)
                    }
                    Button(
                        onClick = {
                            conflictBundleTarget = null
                            PeqEngineManager.selectSavedPreset(bundlePreset.id, applyIndependentToo = true)
                        },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_apply_bundle_both), softWrap = false, maxLines = 1)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { conflictBundleTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text(stringResource(R.string.action_cancel), softWrap = false, maxLines = 1)
                }
            },
        )
    }
}

@Composable
private fun SavedPresetsQuickList(
    uiState: AppUiState,
    onSelectPreset: (EqPreset) -> Unit,
) {
    val presets = uiState.presets
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(ExpressiveGroupDefaults.Gap),
    ) {
        itemsIndexed(presets, key = { _, p -> p.id }) { idx, preset ->
            val selected = preset.id == uiState.selectedPresetId
            val linkedIndep = preset.linkedIndependentPresetId?.let { id ->
                uiState.independentPresets.firstOrNull { it.id == id }
            }
            val rowColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
            val badges = buildList {
                if (preset.isCommonModifiedFromBaseline) {
                    add(
                        PresetBadgeSpec(
                            text = "(${stringResource(R.string.preset_edited_badge)})",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    )
                }
                if (linkedIndep != null) {
                    add(
                        PresetBadgeSpec(
                            text = stringResource(R.string.preset_type_bundle),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    )
                }
            }
            val summaryText = if (linkedIndep != null) {
                stringResource(
                    R.string.preset_summary_linked_lr_format,
                    preset.commonBands.count { it.enabled },
                    preset.commonPreampDb,
                    linkedIndep.name,
                )
            } else {
                stringResource(
                    R.string.preset_summary_common_format,
                    preset.commonBands.count { it.enabled },
                    preset.commonPreampDb,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .expressiveItem(idx, presets.size, rowColor)
                    .clickable { onSelectPreset(preset) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = preset.displayName(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    PresetBadgeFlowRow(badges = badges)
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = LucideIcons.Check,
                        contentDescription = stringResource(R.string.preset_active_badge),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun IndependentPresetsQuickList(
    uiState: AppUiState,
    onSelectIndependent: (String?) -> Unit,
) {
    val indepList = uiState.independentPresets
    val totalCount = indepList.size + 1
    val isNoneActive = uiState.selectedIndependentPresetId == null && uiState.draftIndependentPreset == null

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        uiState.draftIndependentPreset?.takeIf { uiState.selectedIndependentPresetId == null }?.let { draft ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.peq_draft_lr_banner),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = stringResource(
                            R.string.lr_summary_format,
                            draft.leftBands.count { it.enabled },
                            draft.leftPreampDb,
                            draft.rightBands.count { it.enabled },
                            draft.rightPreampDb,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(ExpressiveGroupDefaults.Gap),
        ) {
            item(key = "__lr_none__") {
                val noneColor = if (isNoneActive) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .expressiveItem(0, totalCount, noneColor)
                        .clickable { onSelectIndependent(null) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.lr_preset_none_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isNoneActive) FontWeight.Bold else FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.lr_preset_none_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isNoneActive) {
                        Icon(
                            imageVector = LucideIcons.Check,
                            contentDescription = stringResource(R.string.preset_active_badge),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            itemsIndexed(indepList, key = { _, p -> p.id }) { idx, indep ->
                val selected = indep.id == uiState.selectedIndependentPresetId
                val rowColor = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .expressiveItem(idx + 1, totalCount, rowColor)
                        .clickable { onSelectIndependent(indep.id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = indep.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (indep.isModifiedFromBaseline) {
                            PresetBadgeFlowRow(
                                badges = listOf(
                                    PresetBadgeSpec(
                                        text = "(${stringResource(R.string.preset_edited_badge)})",
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                )
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.lr_summary_format,
                                indep.leftBands.count { it.enabled },
                                indep.leftPreampDb,
                                indep.rightBands.count { it.enabled },
                                indep.rightPreampDb,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (selected) {
                        Icon(
                            imageVector = LucideIcons.Check,
                            contentDescription = stringResource(R.string.preset_active_badge),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
