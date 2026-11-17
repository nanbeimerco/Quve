package com.superglobalpeq.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superglobalpeq.app.R
import com.superglobalpeq.app.model.BundleSelectionOutcome
import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.model.IndependentPeqPreset
import com.superglobalpeq.app.model.evaluateSavedPresetSelection
import com.superglobalpeq.app.ui.icons.LucideIcons
import com.superglobalpeq.app.ui.theme.LocalQuveExpressiveColorScheme

@Composable
fun PresetsTab(
    presets: List<EqPreset>,
    selectedPresetId: String,
    activeCommonPreset: EqPreset,
    independentPresets: List<IndependentPeqPreset>,
    selectedIndependentPresetId: String?,
    draftIndependentPreset: IndependentPeqPreset?,
    onSelectSavedPreset: (presetId: String, applyIndependentToo: Boolean) -> Unit,
    onRevertCommonPreset: (String) -> Unit,
    onSaveCommonAsNew: (newName: String, sourcePresetId: String, revertSourceToOriginal: Boolean, includeActiveLr: Boolean) -> Unit,
    onRenamePreset: (String, String) -> Unit,
    onDuplicatePreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onSelectIndependentPreset: (String?) -> Unit,
    onRevertIndependentPreset: (String) -> Unit,
    onSaveIndependentAsNew: (newName: String, sourceIndependentId: String?, revertSourceToOriginal: Boolean) -> Unit,
    onDiscardDraftIndependent: () -> Unit,
    onRenameIndependentPreset: (String, String) -> Unit,
    onDuplicateIndependentPreset: (String) -> Unit,
    onDeleteIndependentPreset: (String) -> Unit,
    onImportApoFile: () -> Unit,
    onOpenPasteApoModal: () -> Unit,
    onExportApoFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    var saveAsTarget by remember { mutableStateOf<EqPreset?>(null) }
    var renameTarget by remember { mutableStateOf<EqPreset?>(null) }
    var deleteTarget by remember { mutableStateOf<EqPreset?>(null) }
    var conflictBundleTarget by remember { mutableStateOf<Pair<EqPreset, IndependentPeqPreset>?>(null) }

    var saveIndepAsTarget by remember { mutableStateOf<IndependentPeqPreset?>(null) }
    var saveIndepFromDraft by remember { mutableStateOf(false) }
    var renameIndepTarget by remember { mutableStateOf<IndependentPeqPreset?>(null) }
    var deleteIndepTarget by remember { mutableStateOf<IndependentPeqPreset?>(null) }

    val activeIndepForSave = selectedIndependentPresetId?.let { id ->
        independentPresets.firstOrNull { it.id == id }
    } ?: draftIndependentPreset

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Equalizer APO Import / Export Card
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
                Text(
                    text = stringResource(R.string.apo_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.apo_card_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onImportApoFile,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(LucideIcons.FolderInput, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_import_file), fontSize = 12.sp, softWrap = false, maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = onOpenPasteApoModal,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(LucideIcons.Plus, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_paste_text), fontSize = 12.sp, softWrap = false, maxLines = 1)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportApoFile,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(LucideIcons.FileOutput, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_export_apo), fontSize = 12.sp, softWrap = false, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { saveAsTarget = activeCommonPreset },
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(LucideIcons.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_save_as_new), fontSize = 12.sp, softWrap = false, maxLines = 1)
                    }
                }
            }
        }

        // Section 1: Saved Presets (Common-only + Common & L/R Bundles)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ExpressiveGroupTitle(text = stringResource(R.string.saved_presets_title, presets.size))
            Text(
                text = stringResource(R.string.saved_presets_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(2.dp))
            ExpressiveGroup {
                presets.forEachIndexed { idx, preset ->
                    val isSelectedCommon = preset.id == selectedPresetId
                    val linkedIndep = preset.linkedIndependentPresetId?.let { id ->
                        independentPresets.firstOrNull { it.id == id }
                    }
                    val isFullBundleActive = isSelectedCommon &&
                        (linkedIndep == null || (selectedIndependentPresetId == linkedIndep.id && draftIndependentPreset == null))
                    val isCommonOnlyActive = isSelectedCommon && !isFullBundleActive
                    val isModified = preset.isCommonModifiedFromBaseline
                    val shownName = preset.displayName()
                    var menuExpanded by remember(preset.id) { mutableStateOf(false) }

                    val rowColor = when {
                        isFullBundleActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
                        isCommonOnlyActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
                        else -> ExpressiveGroupDefaults.containerColor()
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .expressiveItem(idx, presets.size, rowColor)
                            .clickable {
                                val eval = evaluateSavedPresetSelection(
                                    chosenPreset = preset,
                                    activeIndependentId = selectedIndependentPresetId,
                                    hasDraftIndependent = draftIndependentPreset != null,
                                    independentPresets = independentPresets
                                )
                                when (eval.outcome) {
                                    BundleSelectionOutcome.APPLY_COMMON_ONLY ->
                                        onSelectSavedPreset(preset.id, false)
                                    BundleSelectionOutcome.AUTO_APPLY_BOTH ->
                                        onSelectSavedPreset(preset.id, true)
                                    BundleSelectionOutcome.PROMPT_COMMON_OR_BOTH -> {
                                        val targetIndep = independentPresets.firstOrNull { it.id == eval.targetIndependentPresetId }
                                        if (targetIndep != null) {
                                            conflictBundleTarget = preset to targetIndep
                                        } else {
                                            onSelectSavedPreset(preset.id, false)
                                        }
                                    }
                                }
                            }
                            .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = shownName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val savedBadges = buildList {
                                    if (isModified) {
                                        add(
                                            PresetBadgeSpec(
                                                text = "(${stringResource(R.string.preset_edited_badge)})",
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        )
                                    }
                                    if (isFullBundleActive) {
                                        add(
                                            PresetBadgeSpec(
                                                text = stringResource(R.string.preset_active_badge),
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                            )
                                        )
                                    } else if (isCommonOnlyActive) {
                                        add(
                                            PresetBadgeSpec(
                                                text = stringResource(R.string.preset_common_active_badge),
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                            )
                                        )
                                    }
                                    add(
                                        PresetBadgeSpec(
                                            text = stringResource(
                                                if (linkedIndep != null) R.string.preset_type_bundle else R.string.preset_type_common
                                            ),
                                            containerColor = if (linkedIndep != null) {
                                                MaterialTheme.colorScheme.tertiaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                            },
                                            contentColor = if (linkedIndep != null) {
                                                MaterialTheme.colorScheme.onTertiaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    )
                                }
                                PresetBadgeFlowRow(badges = savedBadges)
                                Text(
                                    text = if (linkedIndep != null) {
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
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(
                                        imageVector = LucideIcons.MoreVertical,
                                        contentDescription = stringResource(R.string.cd_preset_actions),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                GlassDropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    if (isModified) {
                                        GlassMenuItem(
                                            text = stringResource(R.string.action_revert_to_imported),
                                            icon = LucideIcons.RotateCcw,
                                            onClick = {
                                                menuExpanded = false
                                                onRevertCommonPreset(preset.id)
                                            }
                                        )
                                    }
                                    GlassMenuItem(
                                        text = stringResource(R.string.action_save_as_new),
                                        icon = LucideIcons.Save,
                                        onClick = {
                                            menuExpanded = false
                                            saveAsTarget = preset
                                        }
                                    )
                                    GlassMenuItem(
                                        text = stringResource(R.string.action_rename),
                                        icon = LucideIcons.Pencil,
                                        onClick = {
                                            menuExpanded = false
                                            renameTarget = preset
                                        }
                                    )
                                    GlassMenuItem(
                                        text = stringResource(R.string.action_duplicate),
                                        icon = LucideIcons.Copy,
                                        onClick = {
                                            menuExpanded = false
                                            onDuplicatePreset(preset.id)
                                        }
                                    )
                                    GlassMenuDivider()
                                    GlassMenuItem(
                                        text = stringResource(R.string.action_delete),
                                        icon = LucideIcons.Trash2,
                                        destructive = true,
                                        enabled = presets.size > 1,
                                        onClick = {
                                            menuExpanded = false
                                            deleteTarget = preset
                                        }
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = isModified) {
                            ModifiedInlineActionRow(
                                onRevert = { onRevertCommonPreset(preset.id) },
                                onSaveAsNew = { saveAsTarget = preset }
                            )
                        }
                    }
                }
            }
        }

        // Section 2: L/R Presets (Independent Left/Right)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ExpressiveGroupTitle(text = stringResource(R.string.lr_presets_title, independentPresets.size))
            Text(
                text = stringResource(R.string.lr_presets_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Unsaved draft banner when L/R was edited starting from "No L/R"
            AnimatedVisibility(visible = draftIndependentPreset != null && selectedIndependentPresetId == null) {
                draftIndependentPreset?.let { draft ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.lr_draft_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.lr_summary_format,
                                            draft.leftBands.count { it.enabled }, draft.leftPreampDb,
                                            draft.rightBands.count { it.enabled }, draft.rightPreampDb
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = onDiscardDraftIndependent,
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 34.dp)
                                ) {
                                    Icon(LucideIcons.X, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        stringResource(R.string.action_discard_lr_draft),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        softWrap = false,
                                        maxLines = 1
                                    )
                                }
                                Button(
                                    onClick = {
                                        saveIndepFromDraft = true
                                        saveIndepAsTarget = draft
                                    },
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 34.dp)
                                ) {
                                    Icon(LucideIcons.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        stringResource(R.string.action_save_as_lr_preset),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        softWrap = false,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(2.dp))
            val totalLrRows = independentPresets.size + 1
            ExpressiveGroup {
                // Row 0: Fixed "No L/R (0 dB)" clear entry
                val isNoneActive = selectedIndependentPresetId == null && draftIndependentPreset == null
                val noneColor = if (isNoneActive) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                } else {
                    ExpressiveGroupDefaults.containerColor()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .expressiveItem(0, totalLrRows, noneColor)
                        .clickable { onSelectIndependentPreset(null) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.lr_preset_none_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isNoneActive) {
                            PresetBadgeFlowRow(
                                badges = listOf(
                                    PresetBadgeSpec(
                                        text = stringResource(R.string.preset_active_badge),
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    )
                                )
                            )
                        }
                        Text(
                            text = stringResource(R.string.lr_preset_none_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Standalone L/R presets
                independentPresets.forEachIndexed { idx, indep ->
                    val isSelectedLr = selectedIndependentPresetId == indep.id && draftIndependentPreset == null
                    val isIndepModified = indep.isModifiedFromBaseline
                    var menuExpanded by remember(indep.id) { mutableStateOf(false) }

                    val rowColor = if (isSelectedLr) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
                    } else {
                        ExpressiveGroupDefaults.containerColor()
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .expressiveItem(idx + 1, totalLrRows, rowColor)
                            .clickable { onSelectIndependentPreset(indep.id) }
                            .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = indep.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val lrBadges = buildList {
                                    if (isIndepModified) {
                                        add(
                                            PresetBadgeSpec(
                                                text = "(${stringResource(R.string.preset_edited_badge)})",
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        )
                                    }
                                    if (isSelectedLr) {
                                        add(
                                            PresetBadgeSpec(
                                                text = stringResource(R.string.preset_active_badge),
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                            )
                                        )
                                    }
                                }
                                PresetBadgeFlowRow(badges = lrBadges)
                                Text(
                                    text = stringResource(
                                        R.string.lr_summary_format,
                                        indep.leftBands.count { it.enabled }, indep.leftPreampDb,
                                        indep.rightBands.count { it.enabled }, indep.rightPreampDb
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(
                                        imageVector = LucideIcons.MoreVertical,
                                        contentDescription = stringResource(R.string.cd_preset_actions),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                GlassDropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    if (isIndepModified) {
                                        GlassMenuItem(
                                            text = stringResource(R.string.action_revert_to_imported),
                                            icon = LucideIcons.RotateCcw,
                                            onClick = {
                                                menuExpanded = false
                                                onRevertIndependentPreset(indep.id)
                                            }
                                        )
                                    }
                                    GlassMenuItem(
                                        text = stringResource(R.string.action_save_as_new),
                                        icon = LucideIcons.Save,
                                        onClick = {
                                            menuExpanded = false
                                            saveIndepFromDraft = false
                                            saveIndepAsTarget = indep
                                        }
                                    )
                                    GlassMenuItem(
                                        text = stringResource(R.string.action_rename),
                                        icon = LucideIcons.Pencil,
                                        onClick = {
                                            menuExpanded = false
                                            renameIndepTarget = indep
                                        }
                                    )
                                    GlassMenuItem(
                                        text = stringResource(R.string.action_duplicate),
                                        icon = LucideIcons.Copy,
                                        onClick = {
                                            menuExpanded = false
                                            onDuplicateIndependentPreset(indep.id)
                                        }
                                    )
                                    GlassMenuDivider()
                                    GlassMenuItem(
                                        text = stringResource(R.string.action_delete),
                                        icon = LucideIcons.Trash2,
                                        destructive = true,
                                        onClick = {
                                            menuExpanded = false
                                            deleteIndepTarget = indep
                                        }
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = isIndepModified) {
                            ModifiedInlineActionRow(
                                onRevert = { onRevertIndependentPreset(indep.id) },
                                onSaveAsNew = {
                                    saveIndepFromDraft = false
                                    saveIndepAsTarget = indep
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    conflictBundleTarget?.let { (bundlePreset, bundleIndep) ->
        val currentLrName = activeIndepForSave?.name ?: stringResource(R.string.lr_preset_none_title)
        BundlePresetConflictDialog(
            bundleName = bundlePreset.displayName(),
            bundleLrName = bundleIndep.name,
            currentLrName = currentLrName,
            onDismiss = { conflictBundleTarget = null },
            onCommonOnly = {
                conflictBundleTarget = null
                onSelectSavedPreset(bundlePreset.id, false)
            },
            onBoth = {
                conflictBundleTarget = null
                onSelectSavedPreset(bundlePreset.id, true)
            }
        )
    }

    saveAsTarget?.let { target ->
        SaveAsNewPresetDialog(
            sourcePreset = target,
            activeIndependentName = activeIndepForSave?.takeIf { it.hasAnyFilterOrGain }?.name,
            onDismiss = { saveAsTarget = null },
            onConfirm = { newName, revertSource, includeLr ->
                onSaveCommonAsNew(newName, target.id, revertSource, includeLr)
                saveAsTarget = null
            }
        )
    }

    renameTarget?.let { p ->
        NameInputDialog(
            title = stringResource(R.string.dialog_rename_title),
            initialName = p.displayName(),
            confirmLabel = stringResource(R.string.action_rename),
            onDismiss = { renameTarget = null },
            onConfirm = {
                onRenamePreset(p.id, it)
                renameTarget = null
            }
        )
    }

    deleteTarget?.let { p ->
        val expressiveColors = LocalQuveExpressiveColorScheme.current ?: MaterialTheme.colorScheme
        GlassAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(stringResource(R.string.dialog_delete_message, p.displayName())) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePreset(p.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.action_delete), softWrap = false, maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = expressiveColors.primary)
                ) {
                    Text(stringResource(R.string.action_cancel), softWrap = false, maxLines = 1)
                }
            }
        )
    }

    saveIndepAsTarget?.let { source ->
        SaveAsNewIndependentPresetDialog(
            sourcePreset = source,
            isDraft = saveIndepFromDraft,
            onDismiss = { saveIndepAsTarget = null },
            onConfirm = { newName, revertSource ->
                onSaveIndependentAsNew(
                    newName,
                    if (saveIndepFromDraft) null else source.id,
                    revertSource
                )
                saveIndepAsTarget = null
            }
        )
    }

    renameIndepTarget?.let { indep ->
        NameInputDialog(
            title = stringResource(R.string.dialog_rename_lr_title),
            initialName = indep.name,
            confirmLabel = stringResource(R.string.action_rename),
            fieldLabel = stringResource(R.string.field_lr_preset_name),
            onDismiss = { renameIndepTarget = null },
            onConfirm = {
                onRenameIndependentPreset(indep.id, it)
                renameIndepTarget = null
            }
        )
    }

    deleteIndepTarget?.let { indep ->
        val expressiveColors = LocalQuveExpressiveColorScheme.current ?: MaterialTheme.colorScheme
        val linkedBundleNameList = mutableListOf<String>()
        for (p in presets) {
            if (p.linkedIndependentPresetId == indep.id) {
                linkedBundleNameList.add(p.displayName())
            }
        }
        val linkedBundleNames = linkedBundleNameList.joinToString(", ")
        GlassAlertDialog(
            onDismissRequest = { deleteIndepTarget = null },
            title = { Text(stringResource(R.string.dialog_delete_lr_title)) },
            text = {
                Text(
                    if (linkedBundleNames.isNotEmpty()) {
                        stringResource(R.string.dialog_delete_lr_linked_note, indep.name, linkedBundleNames)
                    } else {
                        stringResource(R.string.dialog_delete_lr_message, indep.name)
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteIndependentPreset(indep.id)
                        deleteIndepTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.action_delete), softWrap = false, maxLines = 1)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteIndepTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = expressiveColors.primary)
                ) {
                    Text(stringResource(R.string.action_cancel), softWrap = false, maxLines = 1)
                }
            }
        )
    }
}

internal data class PresetBadgeSpec(
    val text: String,
    val containerColor: androidx.compose.ui.graphics.Color,
    val contentColor: androidx.compose.ui.graphics.Color,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PresetBadgeFlowRow(
    badges: List<PresetBadgeSpec>,
    modifier: Modifier = Modifier,
) {
    if (badges.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        badges.forEach { spec ->
            PresetStatusPill(
                text = spec.text,
                containerColor = spec.containerColor,
                contentColor = spec.contentColor,
            )
        }
    }
}

@Composable
internal fun PresetStatusPill(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Surface(color = containerColor, shape = CircleShape) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ModifiedInlineActionRow(
    onRevert: () -> Unit,
    onSaveAsNew: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalButton(
            onClick = onRevert,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 34.dp)
        ) {
            Icon(LucideIcons.RotateCcw, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                text = stringResource(R.string.action_revert_to_original),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedButton(
            onClick = onSaveAsNew,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 34.dp)
        ) {
            Icon(LucideIcons.Save, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                text = stringResource(R.string.action_save_as_new_short),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BundlePresetConflictDialog(
    bundleName: String,
    bundleLrName: String,
    currentLrName: String,
    onDismiss: () -> Unit,
    onCommonOnly: () -> Unit,
    onBoth: () -> Unit,
) {
    val expressiveColors = LocalQuveExpressiveColorScheme.current ?: MaterialTheme.colorScheme
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_bundle_conflict_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(
                        R.string.dialog_bundle_conflict_message,
                        bundleName,
                        bundleLrName,
                        currentLrName
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                FilledTonalButton(
                    onClick = onCommonOnly,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_apply_common_only), softWrap = false, maxLines = 1)
                }
                Button(
                    onClick = onBoth,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = expressiveColors.primary,
                        contentColor = expressiveColors.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_apply_bundle_both), softWrap = false, maxLines = 1)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = expressiveColors.primary)
            ) {
                Text(stringResource(R.string.action_cancel), softWrap = false, maxLines = 1)
            }
        }
    )
}

@Composable
fun SaveAsNewPresetDialog(
    sourcePreset: EqPreset,
    activeIndependentName: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (newName: String, revertSourceToOriginal: Boolean, includeActiveLr: Boolean) -> Unit
) {
    val sourceShownName = sourcePreset.displayName()
    val customSuffix = stringResource(R.string.default_custom_suffix)
    var name by remember(sourcePreset.id) { mutableStateOf("$sourceShownName ($customSuffix)") }
    var revertOriginal by remember(sourcePreset.id) { mutableStateOf(false) }
    var includeActiveLr by remember(sourcePreset.id, activeIndependentName) {
        mutableStateOf(
            activeIndependentName != null && sourcePreset.linkedIndependentPresetId != null
        )
    }

    val trimmed = name.trim()
    val sameAsSource = trimmed.equals(sourceShownName, ignoreCase = true) ||
        trimmed.equals(sourcePreset.name, ignoreCase = true)
    val expressiveColors = LocalQuveExpressiveColorScheme.current ?: MaterialTheme.colorScheme

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_save_as_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_preset_name)) },
                    isError = sameAsSource && trimmed.isNotEmpty(),
                    supportingText = if (sameAsSource && trimmed.isNotEmpty()) {
                        {
                            Text(
                                text = stringResource(R.string.dialog_save_as_same_name_error, sourceShownName),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = expressiveColors.primary,
                        focusedLabelColor = expressiveColors.primary,
                        cursorColor = expressiveColors.primary,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (activeIndependentName != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { includeActiveLr = !includeActiveLr }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_save_as_include_lr, activeIndependentName),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        QuveExpressiveSwitch(
                            checked = includeActiveLr,
                            onCheckedChange = { includeActiveLr = it }
                        )
                    }
                }
                if (sourcePreset.isCommonModifiedFromBaseline) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { revertOriginal = !revertOriginal }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_save_as_revert_source, sourceShownName),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        QuveExpressiveSwitch(
                            checked = revertOriginal,
                            onCheckedChange = { revertOriginal = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(trimmed, revertOriginal, includeActiveLr) },
                enabled = trimmed.isNotEmpty() && !sameAsSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = expressiveColors.primary,
                    contentColor = expressiveColors.onPrimary
                )
            ) {
                Text(stringResource(R.string.action_save), softWrap = false, maxLines = 1)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = expressiveColors.primary)
            ) {
                Text(stringResource(R.string.action_cancel), softWrap = false, maxLines = 1)
            }
        }
    )
}

@Composable
fun SaveAsNewIndependentPresetDialog(
    sourcePreset: IndependentPeqPreset,
    isDraft: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (newName: String, revertSourceToOriginal: Boolean) -> Unit
) {
    val customSuffix = stringResource(R.string.default_custom_suffix)
    var name by remember(sourcePreset.id, isDraft) {
        mutableStateOf(
            if (isDraft) sourcePreset.name else "${sourcePreset.name} ($customSuffix)"
        )
    }
    var revertOriginal by remember(sourcePreset.id) { mutableStateOf(false) }

    val trimmed = name.trim()
    val sameAsSource = !isDraft && trimmed.equals(sourcePreset.name, ignoreCase = true)
    val expressiveColors = LocalQuveExpressiveColorScheme.current ?: MaterialTheme.colorScheme

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_save_lr_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_lr_preset_name)) },
                    isError = sameAsSource && trimmed.isNotEmpty(),
                    supportingText = if (sameAsSource && trimmed.isNotEmpty()) {
                        {
                            Text(
                                text = stringResource(R.string.dialog_save_as_same_name_error, sourcePreset.name),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = expressiveColors.primary,
                        focusedLabelColor = expressiveColors.primary,
                        cursorColor = expressiveColors.primary,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isDraft && sourcePreset.isModifiedFromBaseline) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { revertOriginal = !revertOriginal }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_save_as_revert_source, sourcePreset.name),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        QuveExpressiveSwitch(
                            checked = revertOriginal,
                            onCheckedChange = { revertOriginal = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(trimmed, revertOriginal) },
                enabled = trimmed.isNotEmpty() && !sameAsSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = expressiveColors.primary,
                    contentColor = expressiveColors.onPrimary
                )
            ) {
                Text(stringResource(R.string.action_save), softWrap = false, maxLines = 1)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = expressiveColors.primary)
            ) {
                Text(stringResource(R.string.action_cancel), softWrap = false, maxLines = 1)
            }
        }
    )
}

@Composable
private fun NameInputDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    fieldLabel: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val resolvedFieldLabel = fieldLabel ?: stringResource(R.string.field_preset_name)
    val expressiveColors = LocalQuveExpressiveColorScheme.current ?: MaterialTheme.colorScheme
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(resolvedFieldLabel) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = expressiveColors.primary,
                    focusedLabelColor = expressiveColors.primary,
                    cursorColor = expressiveColors.primary,
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = expressiveColors.primary,
                    contentColor = expressiveColors.onPrimary
                )
            ) {
                Text(confirmLabel, softWrap = false, maxLines = 1)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = expressiveColors.primary)
            ) {
                Text(stringResource(R.string.action_cancel), softWrap = false, maxLines = 1)
            }
        }
    )
}
