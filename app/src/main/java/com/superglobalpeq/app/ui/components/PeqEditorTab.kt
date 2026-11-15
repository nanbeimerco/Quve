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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superglobalpeq.app.R
import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.model.FilterType
import com.superglobalpeq.app.model.PeqBand
import com.superglobalpeq.app.model.PeqChannelTarget
import com.superglobalpeq.app.ui.icons.LucideIcons
import com.superglobalpeq.app.ui.theme.LocalQuveExpressiveColorScheme
import java.util.Locale
import kotlin.math.ln

@Composable
fun PeqEditorTab(
    preset: EqPreset,
    editingTarget: PeqChannelTarget,
    onSelectTarget: (PeqChannelTarget) -> Unit,
    onPreampChanged: (PeqChannelTarget, Double) -> Unit,
    onAutoHeadroom: () -> Unit,
    onAddBand: (PeqChannelTarget) -> Unit,
    onUpdateBand: (PeqChannelTarget, PeqBand) -> Unit,
    onRemoveBand: (PeqChannelTarget, String) -> Unit,
    onOpenSectionPasteDialog: (PeqChannelTarget) -> Unit,
    onRevertPresetToOriginal: () -> Unit,
    onSaveModifiedAsNew: (newName: String, revertSourceToOriginal: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val preampDb = preset.preampFor(editingTarget)
    val bands = preset.bandsFor(editingTarget)
    var editingNumericBand by remember { mutableStateOf<PeqBand?>(null) }
    var showSaveAsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Modified preset quick bar: 1-tap Revert or Save as New
        AnimatedVisibility(visible = preset.isModifiedFromBaseline) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.60f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.preset_name_edited_format, preset.displayName()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = onRevertPresetToOriginal,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 30.dp)
                    ) {
                        Icon(LucideIcons.RotateCcw, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.action_revert_to_original),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                    OutlinedButton(
                        onClick = { showSaveAsDialog = true },
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 30.dp)
                    ) {
                        Icon(LucideIcons.Save, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.action_save_as_new_short),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Material 3 Expressive connected selector for Common / L / R
        val targets = PeqChannelTarget.entries
        ExpressiveSegmentBar(
            labels = targets.map { target ->
                val count = preset.bandsFor(target).count { it.enabled }
                "${target.shortSegmentLabel()} ($count)"
            },
            selectedIndex = targets.indexOf(editingTarget).coerceAtLeast(0),
            onSelect = { idx -> onSelectTarget(targets[idx]) },
            modifier = Modifier.fillMaxWidth()
        )

        // Explanation card for current channel layer
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = when (editingTarget) {
                    PeqChannelTarget.COMMON -> stringResource(R.string.channel_common_desc)
                    PeqChannelTarget.LEFT -> stringResource(R.string.channel_left_desc)
                    PeqChannelTarget.RIGHT -> stringResource(R.string.channel_right_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }

        // Preamp Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editingTarget == PeqChannelTarget.COMMON) {
                            stringResource(R.string.preamp_common_title)
                        } else {
                            stringResource(R.string.preamp_channel_title, editingTarget.shortSegmentLabel())
                        },
                        style = MaterialTheme.typography.titleMedium,
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (editingTarget == PeqChannelTarget.COMMON) {
                        FilledTonalButton(
                            onClick = onAutoHeadroom,
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                        ) {
                            Icon(
                                imageVector = LucideIcons.Sparkles,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.action_auto_headroom),
                                fontSize = 11.sp,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        text = String.format(Locale.US, "%+.1f dB", preampDb),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        softWrap = false,
                        maxLines = 1
                    )
                }
                Slider(
                    value = preampDb.toFloat(),
                    onValueChange = { raw ->
                        val snapped = kotlin.math.round(raw * 10f) / 10.0
                        onPreampChanged(editingTarget, snapped)
                    },
                    valueRange = -24f..12f
                )
            }
        }

        // Action bar above bands
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onAddBand(editingTarget) },
                modifier = Modifier.weight(1f),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(LucideIcons.Plus, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.action_add_band),
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = { onOpenSectionPasteDialog(editingTarget) },
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(LucideIcons.FolderInput, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.action_paste_apo),
                    fontSize = 13.sp,
                    softWrap = false,
                    maxLines = 1
                )
            }
        }

        if (bands.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .expressiveItem(0, 1, MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Text(
                    text = stringResource(R.string.empty_bands_hint, editingTarget.fullLabel()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            ExpressiveGroup(
                title = stringResource(R.string.filters_group_title, editingTarget.shortSegmentLabel(), bands.size)
            ) {
                bands.forEachIndexed { idx, band ->
                    PeqBandCard(
                        index = idx + 1,
                        rowIndex = idx,
                        rowCount = bands.size,
                        band = band,
                        onUpdate = { onUpdateBand(editingTarget, it) },
                        onDelete = { onRemoveBand(editingTarget, band.id) },
                        onOpenNumericEditor = { editingNumericBand = band }
                    )
                }
            }
        }
    }

    if (showSaveAsDialog) {
        SaveAsNewPresetDialog(
            sourcePreset = preset,
            onDismiss = { showSaveAsDialog = false },
            onConfirm = { newName, revertSource ->
                onSaveModifiedAsNew(newName, revertSource)
                showSaveAsDialog = false
            }
        )
    }

    editingNumericBand?.let { band ->
        BandNumericEditDialog(
            band = band,
            onDismiss = { editingNumericBand = null },
            onSave = { updated ->
                onUpdateBand(editingTarget, updated)
                editingNumericBand = null
            }
        )
    }
}

@Composable
private fun PeqBandCard(
    index: Int,
    rowIndex: Int,
    rowCount: Int,
    band: PeqBand,
    onUpdate: (PeqBand) -> Unit,
    onDelete: () -> Unit,
    onOpenNumericEditor: () -> Unit
) {
    var expanded by remember(band.id) { mutableStateOf(false) }

    val rowColor = if (band.enabled) {
        ExpressiveGroupDefaults.containerColor()
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveItem(rowIndex, rowCount, rowColor)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.defaultMinSize(minWidth = 54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "#$index ${band.type.apoCode}",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatFreq(band.frequencyHz),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (band.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = if (band.type.supportsGain) {
                    String.format(Locale.US, "%+.2f dB · Q %.2f", band.gainDb, band.q)
                } else {
                    String.format(Locale.US, "Q %.3f", band.q)
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onOpenNumericEditor,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = LucideIcons.Pencil,
                    contentDescription = stringResource(R.string.cd_numeric_input),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            QuveExpressiveSwitch(
                checked = band.enabled,
                onCheckedChange = { onUpdate(band.copy(enabled = it)) }
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Frequency slider (log 20..20000)
                val logMin = ln(20.0)
                val logMax = ln(20000.0)
                val freqPos = ((ln(band.frequencyHz.coerceIn(20.0, 20000.0)) - logMin) / (logMax - logMin)).toFloat()
                Text(
                    text = stringResource(R.string.band_freq_label, formatFreq(band.frequencyHz)),
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = freqPos,
                    onValueChange = { t ->
                        val newHz = kotlin.math.exp(logMin + t * (logMax - logMin))
                        val roundedHz = if (newHz < 100.0) {
                            kotlin.math.round(newHz * 10.0) / 10.0
                        } else {
                            kotlin.math.round(newHz)
                        }
                        onUpdate(band.copy(frequencyHz = roundedHz))
                    }
                )

                if (band.type.supportsGain) {
                    Text(
                        text = stringResource(R.string.band_gain_label, band.gainDb),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = band.gainDb.toFloat(),
                        onValueChange = { raw ->
                            val rounded = kotlin.math.round(raw * 20f) / 20.0
                            onUpdate(band.copy(gainDb = rounded))
                        },
                        valueRange = -20f..20f
                    )
                }

                val qLogMin = ln(0.1)
                val qLogMax = ln(16.0)
                val qPos = ((ln(band.q.coerceIn(0.1, 16.0)) - qLogMin) / (qLogMax - qLogMin)).toFloat()
                Text(
                    text = stringResource(R.string.band_q_label, band.q),
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = qPos,
                    onValueChange = { t ->
                        val rawQ = kotlin.math.exp(qLogMin + t * (qLogMax - qLogMin))
                        val roundedQ = kotlin.math.round(rawQ * 100.0) / 100.0
                        onUpdate(band.copy(q = roundedQ))
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onOpenNumericEditor) {
                        Icon(LucideIcons.Pencil, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_edit_numeric_type), softWrap = false, maxLines = 1)
                    }
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = onDelete) {
                        Icon(
                            LucideIcons.Trash2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error, softWrap = false, maxLines = 1)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BandNumericEditDialog(
    band: PeqBand,
    onDismiss: () -> Unit,
    onSave: (PeqBand) -> Unit
) {
    var selectedType by remember { mutableStateOf(band.type) }
    var freqText by remember { mutableStateOf(String.format(Locale.US, "%.1f", band.frequencyHz)) }
    var gainText by remember { mutableStateOf(String.format(Locale.US, "%.2f", band.gainDb)) }
    var qText by remember { mutableStateOf(String.format(Locale.US, "%.3f", band.q)) }
    val expressiveColors = LocalQuveExpressiveColorScheme.current ?: MaterialTheme.colorScheme

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_band_detail_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.dialog_filter_type_label), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterType.entries.forEach { t ->
                        FilterChip(
                            selected = selectedType == t,
                            onClick = { selectedType = t },
                            label = {
                                Text(
                                    text = t.apoCode,
                                    fontWeight = FontWeight.Bold,
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = freqText,
                    onValueChange = { freqText = it },
                    label = { Text(stringResource(R.string.dialog_freq_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = expressiveColors.primary,
                        focusedLabelColor = expressiveColors.primary,
                        cursorColor = expressiveColors.primary,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (selectedType.supportsGain) {
                    OutlinedTextField(
                        value = gainText,
                        onValueChange = { gainText = it },
                        label = { Text(stringResource(R.string.dialog_gain_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = expressiveColors.primary,
                            focusedLabelColor = expressiveColors.primary,
                            cursorColor = expressiveColors.primary,
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = qText,
                    onValueChange = { qText = it },
                    label = { Text(stringResource(R.string.dialog_q_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = expressiveColors.primary,
                        focusedLabelColor = expressiveColors.primary,
                        cursorColor = expressiveColors.primary,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val freq = freqText.toDoubleOrNull()?.coerceIn(10.0, 22000.0) ?: band.frequencyHz
                    val gain = gainText.toDoubleOrNull()?.coerceIn(-30.0, 30.0) ?: band.gainDb
                    val q = qText.toDoubleOrNull()?.coerceIn(0.05, 30.0) ?: band.q
                    onSave(
                        band.copy(
                            type = selectedType,
                            frequencyHz = freq,
                            gainDb = gain,
                            q = q
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = expressiveColors.primary,
                    contentColor = expressiveColors.onPrimary
                )
            ) {
                Icon(LucideIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.action_apply), softWrap = false, maxLines = 1)
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

private fun formatFreq(hz: Double): String {
    return if (hz >= 100.0 || hz % 1.0 == 0.0) {
        String.format(Locale.US, "%.0f Hz", hz)
    } else {
        String.format(Locale.US, "%.1f Hz", hz)
    }
}
