package com.superglobalpeq.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.ui.icons.LucideIcons
import com.superglobalpeq.app.ui.theme.LocalQuveExpressiveColorScheme

@Composable
fun PresetsTab(
    presets: List<EqPreset>,
    selectedPresetId: String,
    activePreset: EqPreset,
    onSelectPreset: (String) -> Unit,
    onRevertPresetToOriginal: (String) -> Unit,
    onSaveAsNew: (newName: String, sourcePresetId: String, revertSourceToOriginal: Boolean) -> Unit,
    onRenamePreset: (String, String) -> Unit,
    onDuplicatePreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onImportApoFile: () -> Unit,
    onOpenPasteApoModal: () -> Unit,
    onExportApoFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    var saveAsTarget by remember { mutableStateOf<EqPreset?>(null) }
    var renameTarget by remember { mutableStateOf<EqPreset?>(null) }
    var deleteTarget by remember { mutableStateOf<EqPreset?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        onClick = { saveAsTarget = activePreset },
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

        ExpressiveGroup(
            title = stringResource(R.string.saved_presets_title, presets.size)
        ) {
            presets.forEachIndexed { idx, preset ->
                val isSelected = preset.id == selectedPresetId
                val isModified = preset.isModifiedFromBaseline
                val shownName = preset.displayName()
                var menuExpanded by remember(preset.id) { mutableStateOf(false) }

                val rowColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
                } else {
                    ExpressiveGroupDefaults.containerColor()
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .expressiveItem(idx, presets.size, rowColor)
                        .clickable { onSelectPreset(preset.id) }
                        .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = shownName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isModified) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = "(${stringResource(R.string.preset_edited_badge)})",
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            softWrap = false,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = stringResource(R.string.preset_active_badge),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            softWrap = false,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (preset.channelSwap) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = "L⇄R",
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            softWrap = false,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(
                                    R.string.preset_summary_format,
                                    preset.commonBands.count { it.enabled }, preset.commonPreampDb,
                                    preset.leftBands.count { it.enabled }, preset.leftPreampDb,
                                    preset.rightBands.count { it.enabled }, preset.rightPreampDb
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
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
                                            onRevertPresetToOriginal(preset.id)
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

                    // 1-tap Revert to Imported Original + Save Edited as New Preset
                    AnimatedVisibility(visible = isModified) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = { onRevertPresetToOriginal(preset.id) },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 34.dp)
                            ) {
                                Icon(
                                    imageVector = LucideIcons.RotateCcw,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
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
                                onClick = { saveAsTarget = preset },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 34.dp)
                            ) {
                                Icon(
                                    imageVector = LucideIcons.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
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
                }
            }
        }
    }

    saveAsTarget?.let { target ->
        SaveAsNewPresetDialog(
            sourcePreset = target,
            onDismiss = { saveAsTarget = null },
            onConfirm = { newName, revertSource ->
                onSaveAsNew(newName, target.id, revertSource)
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
}

@Composable
fun SaveAsNewPresetDialog(
    sourcePreset: EqPreset,
    onDismiss: () -> Unit,
    onConfirm: (newName: String, revertSourceToOriginal: Boolean) -> Unit
) {
    val sourceShownName = sourcePreset.displayName()
    val customSuffix = stringResource(R.string.default_custom_suffix)
    var name by remember(sourcePreset.id) { mutableStateOf("$sourceShownName ($customSuffix)") }
    var revertOriginal by remember(sourcePreset.id) { mutableStateOf(false) }

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
                if (sourcePreset.isModifiedFromBaseline) {
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
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val expressiveColors = LocalQuveExpressiveColorScheme.current ?: MaterialTheme.colorScheme
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.field_preset_name)) },
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
