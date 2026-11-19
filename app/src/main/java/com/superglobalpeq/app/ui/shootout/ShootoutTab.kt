package com.superglobalpeq.app.ui.shootout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superglobalpeq.app.R
import com.superglobalpeq.app.dsp.ShootoutProcessor
import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.model.SHOOTOUT_MAX_CONTESTANTS
import com.superglobalpeq.app.model.SHOOTOUT_MIN_CONTESTANTS
import com.superglobalpeq.app.model.SHOOTOUT_NO_INDEPENDENT_ID
import com.superglobalpeq.app.model.ShootoutSessionRecord
import com.superglobalpeq.app.model.SwapPosition
import com.superglobalpeq.app.service.AppUiState
import com.superglobalpeq.app.service.ShootoutManager
import com.superglobalpeq.app.service.ShootoutResultsState
import com.superglobalpeq.app.service.ShootoutRunningState
import com.superglobalpeq.app.service.ShootoutStopReason
import com.superglobalpeq.app.service.ShootoutUiState
import com.superglobalpeq.app.shootout.ShootoutMatchRecord
import com.superglobalpeq.app.ui.components.ExpressiveGroup
import com.superglobalpeq.app.ui.components.ExpressiveGroupDefaults
import com.superglobalpeq.app.ui.components.ExpressiveGroupTitle
import com.superglobalpeq.app.ui.components.GlassAlertDialog
import com.superglobalpeq.app.ui.components.QuveExpressiveSwitch
import com.superglobalpeq.app.ui.components.detailLabel
import com.superglobalpeq.app.ui.components.displayName
import com.superglobalpeq.app.ui.components.expressiveItem
import com.superglobalpeq.app.ui.components.titleLabel
import com.superglobalpeq.app.ui.icons.LucideIcons
import com.superglobalpeq.app.ui.theme.LocalQuveDarkTheme
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private val CARD_SHAPE = RoundedCornerShape(24.dp)

@Composable
fun ShootoutTab(
    appState: AppUiState,
    shootoutState: ShootoutUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val running = shootoutState.running
        val results = shootoutState.results
        when {
            running != null -> ShootoutRunningContent(running)
            results != null -> ShootoutResultsContent(results)
            else -> ShootoutSetupContent(appState, shootoutState)
        }
    }
}

@Composable
private fun ShootoutCard(content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = CARD_SHAPE,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun ShootoutSetupContent(appState: AppUiState, state: ShootoutUiState) {
    val context = LocalContext.current
    // Held by the manager so the choices survive a running session and tab changes.
    val setup = state.setup
    val commonOptions = appState.presets.map { it.id to it.displayName() }
    val independentOptions = buildList {
        add(SHOOTOUT_NO_INDEPENDENT_ID to stringResource(R.string.lr_preset_none_title))
        appState.independentPresets.forEach { add(it.id to it.name) }
        appState.draftIndependentPreset?.let { add(it.id to it.name) }
    }
    val selectedCommon = setup.commonIds.filter { id -> commonOptions.any { it.first == id } }
    val selectedIndependent = (setup.independentId ?: appState.activeIndependentPreset?.id)
        ?.takeIf { id -> independentOptions.any { it.first == id } }
        ?: SHOOTOUT_NO_INDEPENDENT_ID
    val channelSwap = setup.channelSwap ?: appState.channelSwap
    val swapPosition = setup.swapPosition ?: appState.swapPosition
    val engineReady = appState.masterPowerOn && !appState.pausedByDeviceRule &&
        appState.serviceBound && appState.telemetry.isRunning

    state.lastStopReason?.let { reason ->
        ShootoutStopNotice(reason)
    }

    state.resumable?.let { ShootoutResumeCard(it, appState) }

    ShootoutCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = LucideIcons.Trophy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.shootout_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            text = stringResource(R.string.shootout_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = {
                ShootoutManager.start(selectedCommon, selectedIndependent, channelSwap, swapPosition)
            },
            enabled = engineReady && selectedCommon.size in SHOOTOUT_MIN_CONTESTANTS..SHOOTOUT_MAX_CONTESTANTS,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.shootout_start), fontWeight = FontWeight.Bold)
        }
        if (!engineReady) {
            Text(
                text = stringResource(R.string.shootout_engine_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    Text(
        text = stringResource(R.string.shootout_pick_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
    ShootoutPresetPicker(
        title = stringResource(R.string.shootout_pick_common_title, selectedCommon.size),
        options = commonOptions,
        selectedIds = selectedCommon,
        onSelectionChange = { ShootoutManager.updateSetup(setup.copy(commonIds = it)) },
    )
    ShootoutIndependentPicker(
        options = independentOptions,
        selectedId = selectedIndependent,
        onSelect = { ShootoutManager.updateSetup(setup.copy(independentId = it)) },
    )
    ShootoutSwapCard(
        channelSwap = channelSwap,
        swapPosition = swapPosition,
        showPosition = selectedIndependent != SHOOTOUT_NO_INDEPENDENT_ID,
        onChannelSwapChange = {
            ShootoutManager.updateSetup(setup.copy(channelSwap = it, swapPosition = swapPosition))
        },
        onSwapPositionChange = {
            ShootoutManager.updateSetup(setup.copy(channelSwap = channelSwap, swapPosition = it))
        },
    )

    ShootoutCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.shootout_overlay_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(
                        if (state.overlayAllowed) R.string.shootout_overlay_on else R.string.shootout_overlay_off
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.overlayAllowed) {
                Icon(
                    imageVector = LucideIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Spacer(Modifier.width(10.dp))
                FilledTonalButton(onClick = { ShootoutManager.requestOverlayPermission(context) }) {
                    Text(stringResource(R.string.shootout_overlay_allow), maxLines = 1, softWrap = false)
                }
            }
        }
    }

    if (state.history.isNotEmpty()) {
        Column {
            ExpressiveGroupTitle(stringResource(R.string.shootout_history_title))
            ExpressiveGroup {
                state.history.forEachIndexed { index, item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .expressiveItem(index, state.history.size)
                            .clickable { ShootoutManager.openResults(item.session.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.shootout_history_winner,
                                item.winner?.displayName().orEmpty(),
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                R.string.shootout_history_detail,
                                formatDate(item.session.createdAtEpochMs),
                                item.session.votes.size,
                                item.session.deviceName,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShootoutPresetPicker(
    title: String,
    options: List<Pair<String, String>>,
    selectedIds: List<String>,
    onSelectionChange: (List<String>) -> Unit,
) {
    Column {
        ExpressiveGroupTitle(title)
        ExpressiveGroup {
            options.forEachIndexed { index, (id, name) ->
                val checked = id in selectedIds
                val canToggle = checked || selectedIds.size < SHOOTOUT_MAX_CONTESTANTS
                val toggle = {
                    onSelectionChange(if (checked) selectedIds - id else selectedIds + id)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .expressiveItem(
                            index,
                            options.size,
                            if (checked) {
                                ExpressiveGroupDefaults.selectedContainerColor()
                            } else {
                                ExpressiveGroupDefaults.containerColor()
                            },
                        )
                        .clickable(enabled = canToggle, onClick = toggle)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Checkbox(checked = checked, onCheckedChange = { toggle() }, enabled = canToggle)
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShootoutIndependentPicker(
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Column {
        ExpressiveGroupTitle(stringResource(R.string.shootout_pick_lr_title))
        ExpressiveGroup {
            options.forEachIndexed { index, (id, name) ->
                val selected = id == selectedId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .expressiveItem(
                            index,
                            options.size,
                            if (selected) {
                                ExpressiveGroupDefaults.selectedContainerColor()
                            } else {
                                ExpressiveGroupDefaults.containerColor()
                            },
                        )
                        .clickable { onSelect(id) }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    RadioButton(selected = selected, onClick = { onSelect(id) })
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShootoutSwapCard(
    channelSwap: Boolean,
    swapPosition: SwapPosition,
    showPosition: Boolean,
    onChannelSwapChange: (Boolean) -> Unit,
    onSwapPositionChange: (SwapPosition) -> Unit,
) {
    ShootoutCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = LucideIcons.ArrowLeftRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.shootout_swap_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.shootout_swap_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            QuveExpressiveSwitch(checked = channelSwap, onCheckedChange = onChannelSwapChange)
        }
        if (channelSwap && showPosition) {
            Column(verticalArrangement = Arrangement.spacedBy(ExpressiveGroupDefaults.Gap)) {
                val positions = SwapPosition.entries
                positions.forEachIndexed { index, position ->
                    val selected = position == swapPosition
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .expressiveItem(
                                index,
                                positions.size,
                                if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                            )
                            .clickable { onSwapPositionChange(position) }
                            .padding(12.dp),
                    ) {
                        RadioButton(selected = selected, onClick = { onSwapPositionChange(position) })
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(text = position.titleLabel(), style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = position.detailLabel(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShootoutStopNotice(reason: ShootoutStopReason) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = CARD_SHAPE,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                text = stringResource(
                    when (reason) {
                        ShootoutStopReason.USER -> R.string.shootout_stop_user
                        ShootoutStopReason.SCREEN_OFF -> R.string.shootout_stop_screen
                        ShootoutStopReason.IDLE -> R.string.shootout_stop_idle
                        ShootoutStopReason.DEVICE_CHANGED -> R.string.shootout_stop_device
                        ShootoutStopReason.ENGINE -> R.string.shootout_stop_engine
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = ShootoutManager::clearStopReason) {
                Icon(
                    imageVector = LucideIcons.X,
                    contentDescription = stringResource(R.string.shootout_close_results),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ShootoutResumeCard(session: ShootoutSessionRecord, appState: AppUiState) {
    val canResume = ShootoutManager.canResume(session, appState)
    val wrongDevice = session.deviceKey != appState.activeDevice?.deviceKey.orEmpty()
    ShootoutCard {
        Text(
            text = stringResource(R.string.shootout_resume_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(
                R.string.shootout_resume_detail,
                session.votes.size,
                session.contestants.size,
                session.deviceName,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (wrongDevice) {
            Text(
                text = stringResource(R.string.shootout_resume_device, session.deviceName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (!canResume) {
            Text(
                text = stringResource(R.string.shootout_engine_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(
            onClick = ShootoutManager::resume,
            enabled = canResume,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.shootout_resume), fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (session.votes.isNotEmpty()) {
                OutlinedButton(onClick = ShootoutManager::finishResumable, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.shootout_view_results), maxLines = 1, softWrap = false)
                }
            }
            TextButton(onClick = ShootoutManager::discardResumable) {
                Text(stringResource(R.string.shootout_discard), maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun ShootoutRunningContent(running: ShootoutRunningState) {
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    ShootoutCard {
        Text(
            text = stringResource(R.string.shootout_round_title, running.round),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(
                if (running.side == ShootoutProcessor.SIDE_A) R.string.shootout_playing_a else R.string.shootout_playing_b
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ShootoutSideSelector(
            side = running.side,
            onSelect = ShootoutManager::selectSide,
            height = 104.dp,
            labelSize = 40.sp,
        )
        ShootoutVoteButtons(
            enabled = running.canVote,
            onVote = ShootoutManager::vote,
            height = 58.dp,
        )
        if (!running.canVote) {
            Text(
                text = stringResource(R.string.shootout_listen_both),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { (running.votes.toFloat() / running.recommendedVotes).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
        )
        Text(
            text = if (running.votes >= running.recommendedVotes) {
                stringResource(R.string.shootout_progress_done, running.votes)
            } else {
                stringResource(R.string.shootout_progress, running.votes, running.recommendedVotes)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { ShootoutManager.stop(ShootoutStopReason.USER) },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.shootout_pause), maxLines = 1, softWrap = false)
        }
        FilledTonalButton(
            onClick = { confirmFinish = true },
            enabled = running.votes > 0,
            modifier = Modifier.weight(2f),
        ) {
            Text(
                text = stringResource(R.string.shootout_finish),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (confirmFinish) {
        GlassAlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(stringResource(R.string.shootout_finish_title)) },
            text = { Text(stringResource(R.string.shootout_finish_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmFinish = false
                        ShootoutManager.finish()
                    },
                ) {
                    Text(stringResource(R.string.shootout_view_results), maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) {
                    Text(stringResource(R.string.shootout_keep_listening), maxLines = 1, softWrap = false)
                }
            },
        )
    }
}

@Composable
private fun ShootoutResultsContent(results: ShootoutResultsState) {
    val session = results.session
    val analysis = results.analysis
    val contestants = remember(session) { session.contestants.associateBy { it.id } }
    val numbers = remember(session) {
        session.contestants.mapIndexed { index, preset -> preset.id to index + 1 }.toMap()
    }
    val colors = shootoutSeriesColors()
    val colorById = remember(session, colors) {
        session.contestants.mapIndexed { index, preset -> preset.id to colors[index % colors.size] }.toMap()
    }

    ShootoutCard {
        Text(
            text = stringResource(R.string.shootout_results_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.shootout_results_detail, session.votes.size, session.deviceName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = when {
                !session.channelSwap -> stringResource(R.string.shootout_results_swap_off)
                session.independentPreset == null -> stringResource(R.string.shootout_results_swap_on)
                session.swapPosition == SwapPosition.BEFORE_INDEPENDENT_EQ ->
                    stringResource(R.string.shootout_results_swap_before)
                else -> stringResource(R.string.shootout_results_swap_after)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Column {
        ExpressiveGroupTitle(stringResource(R.string.shootout_ranking_title))
        ExpressiveGroup {
            analysis.standings.forEachIndexed { index, standing ->
                val preset = contestants.getValue(standing.contestantId)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .expressiveItem(
                            index,
                            analysis.standings.size,
                            if (index == 0) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
                            } else {
                                ExpressiveGroupDefaults.containerColor()
                            },
                        )
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.width(30.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = preset.displayName(),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                R.string.shootout_rating_line,
                                standing.rating.rating.roundToInt(),
                                standing.rating.deviation.roundToInt(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = stringResource(
                                R.string.shootout_record_line,
                                standing.record.wins,
                                standing.record.draws,
                                standing.record.losses,
                                (standing.record.scoreRate * 100.0).roundToInt(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    ShootoutCard {
        Text(
            text = stringResource(R.string.shootout_history_chart_title),
            style = MaterialTheme.typography.titleMedium,
        )
        ShootoutRatingChart(results, contestants, numbers, colorById)
    }

    ShootoutCard {
        Text(
            text = stringResource(R.string.shootout_league_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.shootout_league_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ShootoutLeagueTable(results, contestants, numbers)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { ShootoutManager.deleteSession(session.id) },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.shootout_delete_result), maxLines = 1, softWrap = false)
        }
        Button(onClick = ShootoutManager::closeResults, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.shootout_close_results), maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun shootoutSeriesColors(): List<Color> = if (LocalQuveDarkTheme.current) {
    listOf(
        Color(0xFF3987E5),
        Color(0xFFD95926),
        Color(0xFF199E70),
        Color(0xFFC98500),
        Color(0xFFD55181),
        Color(0xFF0CA30C),
        Color(0xFF9085E9),
        Color(0xFFE66767),
    )
} else {
    listOf(
        Color(0xFF2A78D6),
        Color(0xFFEB6834),
        Color(0xFF1BAF7A),
        Color(0xFFEDA100),
        Color(0xFFE87BA4),
        Color(0xFF008300),
        Color(0xFF6250D6),
        Color(0xFFE34948),
    )
}

@Composable
private fun ShootoutRatingChart(
    results: ShootoutResultsState,
    contestants: Map<String, EqPreset>,
    numbers: Map<String, Int>,
    colors: Map<String, Color>,
) {
    val history = results.analysis.ratingHistory
    val lastVote = results.session.votes.size
    var selectedVote by remember(results.session.id) { mutableStateOf(lastVote) }
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val labelColor = MaterialTheme.colorScheme.onSurface
    val axisStyle = TextStyle(color = axisColor, fontSize = 10.sp)
    val lineLabelStyle = TextStyle(color = labelColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)

    val allValues = remember(history) { history.values.flatMap { it.asIterable() } }
    val step = remember(allValues) {
        val span = (allValues.max() - allValues.min()).coerceAtLeast(1f)
        listOf(25f, 50f, 100f, 200f, 400f).firstOrNull { span / it <= 4f } ?: 800f
    }
    val minValue = floor(allValues.min() / step) * step
    val maxValue = (ceil(allValues.max() / step) * step).coerceAtLeast(minValue + step)

    fun voteAt(x: Float, plotLeft: Float, plotWidth: Float): Int =
        (((x - plotLeft) / plotWidth).coerceIn(0f, 1f) * lastVote).roundToInt()

    val leftInset = 36.dp
    val rightInset = 20.dp
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(lastVote) {
                val left = leftInset.toPx()
                val width = size.width - left - rightInset.toPx()
                detectTapGestures { selectedVote = voteAt(it.x, left, width) }
            }
            .pointerInput(lastVote) {
                val left = leftInset.toPx()
                val width = size.width - left - rightInset.toPx()
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    selectedVote = voteAt(change.position.x, left, width)
                }
            },
    ) {
        val left = leftInset.toPx()
        val right = size.width - rightInset.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        fun xOf(vote: Int) = left + (right - left) * (vote.toFloat() / lastVote.coerceAtLeast(1))
        fun yOf(value: Float) = bottom - (bottom - top) * ((value - minValue) / (maxValue - minValue))

        var tick = minValue
        while (tick <= maxValue + 0.5f) {
            val y = yOf(tick)
            drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            val label = measurer.measure(tick.roundToInt().toString(), axisStyle)
            drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), y - label.size.height / 2f))
            tick += step
        }
        listOf(0, lastVote).distinct().forEach { vote ->
            val label = measurer.measure(vote.toString(), axisStyle)
            val x = (xOf(vote) - label.size.width / 2f).coerceIn(left, size.width - label.size.width)
            drawText(label, topLeft = Offset(x, bottom + 4.dp.toPx()))
        }

        val selectedX = xOf(selectedVote)
        drawLine(axisColor, Offset(selectedX, top), Offset(selectedX, bottom), strokeWidth = 1.dp.toPx())

        results.session.contestants.forEach { contestant ->
            val values = history.getValue(contestant.id)
            val path = Path()
            values.forEachIndexed { vote, value ->
                if (vote == 0) path.moveTo(xOf(vote), yOf(value)) else path.lineTo(xOf(vote), yOf(value))
            }
            drawPath(
                path = path,
                color = colors.getValue(contestant.id),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        results.session.contestants.forEach { contestant ->
            drawSeriesMarker(
                center = Offset(selectedX, yOf(history.getValue(contestant.id)[selectedVote])),
                color = colors.getValue(contestant.id),
                ring = surface,
            )
        }

        // Numbered line ends give every series an identity that does not rely on colour.
        var previousY = Float.NEGATIVE_INFINITY
        val spacing = 12.dp.toPx()
        results.session.contestants
            .sortedBy { yOf(history.getValue(it.id)[lastVote]) }
            .forEach { contestant ->
                val y = maxOf(yOf(history.getValue(contestant.id)[lastVote]), previousY + spacing)
                previousY = y
                val label = measurer.measure(numbers.getValue(contestant.id).toString(), lineLabelStyle)
                drawText(label, topLeft = Offset(right + 7.dp.toPx(), y - label.size.height / 2f))
            }
    }

    Text(
        text = if (selectedVote == 0) {
            stringResource(R.string.shootout_chart_start)
        } else {
            stringResource(R.string.shootout_chart_position, selectedVote)
        },
        style = MaterialTheme.typography.labelLarge,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        results.session.contestants
            .sortedByDescending { history.getValue(it.id)[selectedVote] }
            .forEach { contestant ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(colors.getValue(contestant.id), CircleShape)
                    )
                    Text(
                        text = "${numbers.getValue(contestant.id)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 7.dp).width(14.dp),
                    )
                    Text(
                        text = contestants.getValue(contestant.id).displayName(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = history.getValue(contestant.id)[selectedVote].roundToInt().toString(),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
    }
    Text(
        text = stringResource(R.string.shootout_chart_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun DrawScope.drawSeriesMarker(center: Offset, color: Color, ring: Color) {
    drawCircle(ring, radius = 6.dp.toPx(), center = center)
    drawCircle(color, radius = 4.dp.toPx(), center = center)
}

@Composable
private fun ShootoutLeagueTable(
    results: ShootoutResultsState,
    contestants: Map<String, EqPreset>,
    numbers: Map<String, Int>,
) {
    val standings = results.analysis.standings
    val nameWidth = 112.dp
    val cellWidth = 54.dp
    val cellHeight = 38.dp
    Row {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Spacer(Modifier.height(24.dp))
            standings.forEach { standing ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .width(nameWidth)
                        .height(cellHeight),
                ) {
                    Text(
                        text = "${numbers.getValue(standing.contestantId)}  " +
                            contestants.getValue(standing.contestantId).displayName(),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                standings.forEach { column ->
                    Text(
                        text = numbers.getValue(column.contestantId).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(cellWidth)
                            .height(24.dp),
                    )
                }
            }
            standings.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    standings.forEach { column ->
                        val record = if (row.contestantId == column.contestantId) {
                            null
                        } else {
                            results.analysis.recordBetween(row.contestantId, column.contestantId)
                        }
                        ShootoutLeagueCell(record, cellWidth, cellHeight)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShootoutLeagueCell(
    record: ShootoutMatchRecord?,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
) {
    val played = record != null && record.games > 0
    val background = if (played) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f + 0.30f * record!!.scoreRate.toFloat())
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(width)
            .height(height)
            .background(background, RoundedCornerShape(6.dp)),
    ) {
        Text(
            text = when {
                record == null -> "-"
                record.games == 0 -> ""
                else -> "${record.wins}-${record.draws}-${record.losses}"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

private fun formatDate(epochMs: Long): String =
    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(epochMs))
