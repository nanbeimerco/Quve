package com.superglobalpeq.app.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.superglobalpeq.app.R
import com.superglobalpeq.app.ui.components.displayName
import com.superglobalpeq.app.ui.components.fullLabel

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superglobalpeq.app.data.QuveSettings
import com.superglobalpeq.app.model.BundleSelectionOutcome
import com.superglobalpeq.app.model.DeviceCategory
import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.model.IndependentPeqPreset
import com.superglobalpeq.app.model.PeqChannelTarget
import com.superglobalpeq.app.service.AppUiState
import com.superglobalpeq.app.service.PeqEngineManager
import com.superglobalpeq.app.service.ShizukuStatus
import com.superglobalpeq.app.ui.components.AppRoutingPickerSheet
import com.superglobalpeq.app.ui.components.AppearanceSettingsSheet
import com.superglobalpeq.app.ui.components.BottomFadeScrim
import com.superglobalpeq.app.ui.components.BundlePresetConflictDialog
import com.superglobalpeq.app.ui.components.FloatingBarsTapGuard
import com.superglobalpeq.app.ui.components.FrequencyResponseGraphCard
import com.superglobalpeq.app.ui.components.GlassAlertDialog
import com.superglobalpeq.app.ui.components.GlassMenuController
import com.superglobalpeq.app.ui.components.GlassMenuHost
import com.superglobalpeq.app.ui.components.GlassModalBottomSheet
import com.superglobalpeq.app.ui.components.GlassSheetController
import com.superglobalpeq.app.ui.components.GlassSheetHost
import com.superglobalpeq.app.ui.components.LocalAppBackdrop
import com.superglobalpeq.app.ui.components.LocalGlassMenu
import com.superglobalpeq.app.ui.components.LocalGlassSheets
import com.superglobalpeq.app.ui.components.LocalLiquidGlassEnabled
import com.superglobalpeq.app.ui.components.LocalTopBarGlassBackdrop
import com.superglobalpeq.app.ui.components.PAGE_GUTTER
import com.superglobalpeq.app.ui.components.PeqEditorTab
import com.superglobalpeq.app.ui.components.PresetsTab
import com.superglobalpeq.app.ui.components.QuveFloatingDock
import com.superglobalpeq.app.ui.components.QuveFloatingTopControls
import com.superglobalpeq.app.ui.components.QuveScrollableHeader
import com.superglobalpeq.app.ui.components.QuveTab
import com.superglobalpeq.app.ui.components.RoutingTab
import com.superglobalpeq.app.ui.components.TopFadeScrim
import com.superglobalpeq.app.ui.components.backdrop.backdrops.layerBackdrop
import com.superglobalpeq.app.ui.components.backdrop.backdrops.rememberLayerBackdrop
import com.superglobalpeq.app.ui.components.floatingBarInsets
import com.superglobalpeq.app.ui.components.floatingtabbar.FloatingTabBarInlineBehavior
import com.superglobalpeq.app.ui.components.floatingtabbar.rememberFloatingTabBarScrollConnection
import com.superglobalpeq.app.ui.components.isGlassSupported
import com.superglobalpeq.app.ui.icons.LucideIcons
import kotlinx.coroutines.launch
import java.util.Locale

private data class PasteDialogState(
    val sectionTarget: PeqChannelTarget? // null = whole preset import
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen() {
    val uiState by PeqEngineManager.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentTab by rememberSaveable { mutableStateOf(QuveTab.PEQ) }
    var pasteDialog by remember { mutableStateOf<PasteDialogState?>(null) }
    var pendingImportConflict by remember { mutableStateOf<Pair<EqPreset, IndependentPeqPreset>?>(null) }
    var showAppearanceSheet by rememberSaveable { mutableStateOf(false) }
    var showBypassPickerSheet by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun handleApoImportResult(result: PeqEngineManager.ApoImportResult) {
        val importedPreset = result.importedPreset ?: return
        val eval = result.selectionEvaluation ?: return
        if (eval.outcome == BundleSelectionOutcome.PROMPT_COMMON_OR_BOTH) {
            val latestIndepList = PeqEngineManager.uiState.value.independentPresets
            val targetIndep = latestIndepList.firstOrNull { it.id == eval.targetIndependentPresetId }
            if (targetIndep != null) {
                pendingImportConflict = importedPreset to targetIndep
            } else {
                PeqEngineManager.selectSavedPreset(importedPreset.id, applyIndependentToo = false)
            }
        }
    }

    val lockExpanded by QuveSettings.lockBottomBarExpanded.collectAsState()
    val reduceDynamicBlur by QuveSettings.reduceDynamicBlur.collectAsState()
    val glassSupported = isGlassSupported()
    val glassOn = glassSupported && !reduceDynamicBlur

    val pageBackground = MaterialTheme.colorScheme.background
    val paintBackdrop: ContentDrawScope.() -> Unit = remember(pageBackground) {
        {
            drawRect(pageBackground)
            drawContent()
        }
    }
    val backdrop = rememberLayerBackdrop(onDraw = paintBackdrop)
    val sheetController = remember { GlassSheetController() }
    val menuController = remember { GlassMenuController() }
    var floatingBarsHeightPx by remember { mutableIntStateOf(0) }

    val scrollConnection = rememberFloatingTabBarScrollConnection(
        inlineBehavior = if (lockExpanded) {
            FloatingTabBarInlineBehavior.Never
        } else {
            FloatingTabBarInlineBehavior.OnScrollDown
        }
    )

    val peqScrollState = rememberScrollState()
    val presetsScrollState = rememberScrollState()
    val routingScrollState = rememberScrollState()

    LaunchedEffect(lockExpanded) {
        if (lockExpanded) scrollConnection.expand()
    }

    LaunchedEffect(currentTab) {
        scrollConnection.expand()
    }

    LaunchedEffect(uiState.statusBanner) {
        val msg = uiState.statusBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        PeqEngineManager.clearBanner()
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val displayName = queryFileDisplayName(context, uri)
                    ?.removeSuffix(".txt")
                    ?.removeSuffix(".TXT")
                    ?: com.superglobalpeq.app.data.QuveSettings.localizedContext(context)
                        .getString(R.string.default_apo_import_name)
                val content = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: ""
                handleApoImportResult(PeqEngineManager.importApoAsNewPreset(content, displayName))
            }
        }
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val exported = PeqEngineManager.exportActivePresetApoText()
                context.contentResolver.openOutputStream(uri)
                    ?.bufferedWriter()
                    ?.use { it.write(exported) }
            }
        }
    }

    CompositionLocalProvider(
        LocalAppBackdrop provides backdrop,
        LocalGlassSheets provides sheetController,
        LocalGlassMenu provides menuController,
        LocalLiquidGlassEnabled provides glassOn,
        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBackground)
        ) {
            // 1. Edge-to-edge scrolling page (captured into LayerBackdrop for all glass surfaces)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (glassOn) Modifier.layerBackdrop(backdrop) else Modifier)
                    .nestedScroll(scrollConnection)
            ) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "quve-tab-content"
                ) { tab ->
                    val activeScrollState = when (tab) {
                        QuveTab.PEQ -> peqScrollState
                        QuveTab.PRESETS -> presetsScrollState
                        QuveTab.ROUTING -> routingScrollState
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(activeScrollState)
                            .padding(horizontal = PAGE_GUTTER),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        QuveScrollableHeader(
                            presetName = uiState.activeCommonPreset.displayName(),
                            isPresetModified = uiState.activeCommonPreset.isCommonModifiedFromBaseline,
                            independentPresetName = uiState.activeIndependentPreset?.name,
                            isIndependentModified = uiState.activeSavedIndependentPreset?.isModifiedFromBaseline == true,
                            channelSwap = uiState.channelSwap,
                        )

                        when (tab) {
                            QuveTab.PEQ -> {
                                EngineHeroCard(
                                    uiState = uiState,
                                    onToggleMaster = { PeqEngineManager.setMasterPower(!uiState.masterPowerOn) },
                                    onRefreshShizuku = { PeqEngineManager.refreshShizukuStatus() }
                                )

                                FrequencyResponseGraphCard(
                                    preset = uiState.activePreset,
                                    curves = uiState.frequencyCurves,
                                    editingTarget = uiState.editingTarget,
                                    bypassed = uiState.pausedByDeviceRule || !uiState.masterPowerOn,
                                    channelSwap = uiState.channelSwap,
                                    onAutoPreampClick = { PeqEngineManager.autoAdjustCommonPreamp() },
                                    onSelectTarget = { PeqEngineManager.selectEditingTarget(it) },
                                )

                                PeqEditorTab(
                                    preset = uiState.activePreset,
                                    commonPreset = uiState.activeCommonPreset,
                                    activeSavedIndependent = uiState.activeSavedIndependentPreset,
                                    draftIndependent = uiState.draftIndependentPreset,
                                    editingTarget = uiState.editingTarget,
                                    onSelectTarget = { PeqEngineManager.selectEditingTarget(it) },
                                    onPreampChanged = { t, db -> PeqEngineManager.setTargetPreamp(t, db) },
                                    onAutoHeadroom = { PeqEngineManager.autoAdjustCommonPreamp() },
                                    onAddBand = { t -> PeqEngineManager.addBand(t) },
                                    onUpdateBand = { t, b -> PeqEngineManager.updateBand(t, b) },
                                    onRemoveBand = { t, id -> PeqEngineManager.removeBand(t, id) },
                                    onOpenSectionPasteDialog = { t -> pasteDialog = PasteDialogState(t) },
                                    onRevertCommonToOriginal = {
                                        PeqEngineManager.revertPresetToBaseline(uiState.activeCommonPreset.id)
                                    },
                                    onSaveCommonAsNew = { newName, revertSource, includeLr ->
                                        PeqEngineManager.saveAsNewPreset(
                                            newName = newName,
                                            sourcePresetId = uiState.activeCommonPreset.id,
                                            revertSourceToBaseline = revertSource,
                                            includeActiveIndependent = includeLr
                                        )
                                    },
                                    onRevertIndependentToOriginal = {
                                        PeqEngineManager.revertIndependentPresetToBaseline()
                                    },
                                    onSaveIndependentAsNew = { newName, revertSource ->
                                        PeqEngineManager.saveAsNewIndependentPreset(
                                            newName = newName,
                                            sourceIndependentId = uiState.selectedIndependentPresetId,
                                            revertSourceToBaseline = revertSource
                                        )
                                    },
                                    onDiscardDraftIndependent = {
                                        PeqEngineManager.discardDraftIndependent()
                                    },
                                )
                            }

                            QuveTab.PRESETS -> PresetsTab(
                                presets = uiState.presets,
                                selectedPresetId = uiState.selectedPresetId,
                                activeCommonPreset = uiState.activeCommonPreset,
                                independentPresets = uiState.independentPresets,
                                selectedIndependentPresetId = uiState.selectedIndependentPresetId,
                                draftIndependentPreset = uiState.draftIndependentPreset,
                                onSelectSavedPreset = { id, applyLrToo ->
                                    PeqEngineManager.selectSavedPreset(id, applyLrToo)
                                },
                                onRevertCommonPreset = { PeqEngineManager.revertPresetToBaseline(it) },
                                onSaveCommonAsNew = { newName, sourceId, revertSource, includeLr ->
                                    PeqEngineManager.saveAsNewPreset(
                                        newName = newName,
                                        sourcePresetId = sourceId,
                                        revertSourceToBaseline = revertSource,
                                        includeActiveIndependent = includeLr
                                    )
                                },
                                onRenamePreset = { id, name -> PeqEngineManager.renamePreset(id, name) },
                                onDuplicatePreset = { PeqEngineManager.duplicatePreset(it) },
                                onDeletePreset = { PeqEngineManager.deletePreset(it) },
                                onSelectIndependentPreset = { PeqEngineManager.selectIndependentPreset(it) },
                                onRevertIndependentPreset = { PeqEngineManager.revertIndependentPresetToBaseline(it) },
                                onSaveIndependentAsNew = { newName, sourceId, revertSource ->
                                    PeqEngineManager.saveAsNewIndependentPreset(
                                        newName = newName,
                                        sourceIndependentId = sourceId,
                                        revertSourceToBaseline = revertSource
                                    )
                                },
                                onDiscardDraftIndependent = { PeqEngineManager.discardDraftIndependent() },
                                onRenameIndependentPreset = { id, name -> PeqEngineManager.renameIndependentPreset(id, name) },
                                onDuplicateIndependentPreset = { PeqEngineManager.duplicateIndependentPreset(it) },
                                onDeleteIndependentPreset = { PeqEngineManager.deleteIndependentPreset(it) },
                                onImportApoFile = { openDocLauncher.launch(arrayOf("text/plain", "*/*")) },
                                onOpenPasteApoModal = { pasteDialog = PasteDialogState(null) },
                                onExportApoFile = {
                                    val safeName = uiState.activeCommonPreset.name
                                        .replace(Regex("""[^\w\- ]+"""), "_")
                                        .trim()
                                        .ifEmpty { "preset" }
                                    createDocLauncher.launch("$safeName.txt")
                                }
                            )

                            QuveTab.ROUTING -> RoutingTab(
                                channelSwap = uiState.channelSwap,
                                swapPosition = uiState.swapPosition,
                                onChannelSwapChanged = { en, pos -> PeqEngineManager.setChannelSwap(en, pos) },
                                globalLowLatency = uiState.globalLowLatency,
                                lowLatencyActive = uiState.telemetry.lowLatencyActive,
                                perAppLowLatencyActive = uiState.telemetry.perAppLowLatencyActive,
                                onGlobalLowLatencyChanged = { PeqEngineManager.setGlobalLowLatency(it) },
                                backgroundPersistenceEnabled = uiState.backgroundPersistenceEnabled,
                                onBackgroundPersistenceChanged = { PeqEngineManager.setBackgroundPersistenceEnabled(it) },
                                connectedDevices = uiState.connectedDevices,
                                activeDevice = uiState.activeDevice,
                                deviceRules = uiState.deviceRules,
                                presets = uiState.presets,
                                independentPresets = uiState.independentPresets,
                                onUpdateDeviceRule = { k, a, pid, indepA, indepPid, swapA ->
                                    PeqEngineManager.updateDeviceRule(k, a, pid, indepA, indepPid, swapA)
                                },
                                onRemoveDeviceRule = { PeqEngineManager.removeDeviceRule(it) },
                                appRules = uiState.appRules,
                                onOpenAppRulePicker = { showBypassPickerSheet = true },
                                onChangeAppMode = { pkg, mode -> PeqEngineManager.setAppAudioMode(pkg, mode) },
                                onResetAppRules = { PeqEngineManager.clearAppAudioModes() }
                            )
                        }

                        // Generous clearance so the bottom item always scrolls above the floating dock
                        Spacer(
                            Modifier
                                .navigationBarsPadding()
                                .height(164.dp)
                        )
                    }
                }
            }

            // 2. Top status-bar scrim & floating top-right Liquid Glass capsules (no top band)
            TopFadeScrim(
                pageColor = pageBackground,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            QuveFloatingTopControls(
                shizukuStatus = uiState.shizukuStatus,
                serviceBound = uiState.serviceBound,
                onShizukuClick = { PeqEngineManager.requestShizukuPermission() },
                onOpenAppearance = { showAppearanceSheet = true },
                modifier = Modifier.align(Alignment.TopEnd),
            )

            // 3. Bottom fade floor & tap guard under floating bars
            BottomFadeScrim(
                withMiniPlayer = true,
                pageColor = pageBackground,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            FloatingBarsTapGuard(
                barsHeight = { floatingBarsHeightPx },
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            // 4. Floating Liquid Glass Tab Bar + persistent PEQ Quick Control Pill
            QuveFloatingDock(
                selectedTab = currentTab,
                onSelectTab = { tapped ->
                    if (tapped == currentTab) {
                        val activeScroll = when (tapped) {
                            QuveTab.PEQ -> peqScrollState
                            QuveTab.PRESETS -> presetsScrollState
                            QuveTab.ROUTING -> routingScrollState
                        }
                        coroutineScope.launch { activeScroll.animateScrollTo(0) }
                    } else {
                        currentTab = tapped
                    }
                },
                scrollConnection = scrollConnection,
                uiState = uiState,
                onToggleMaster = { PeqEngineManager.setMasterPower(!uiState.masterPowerOn) },
                onToggleChannelSwap = { PeqEngineManager.setChannelSwap(!uiState.channelSwap, uiState.swapPosition) },
                onOpenQuickTarget = { currentTab = QuveTab.PEQ },
                onHeightChanged = { floatingBarsHeightPx = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            // 5. Snackbar floating cleanly above the dock
            val density = LocalDensity.current
            val bottomNavInset = floatingBarInsets.asPaddingValues().calculateBottomPadding()
            val barsDp = with(density) { floatingBarsHeightPx.toDp() }
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { data ->
                    androidx.compose.material3.Snackbar(
                        snackbarData = data,
                        shape = RoundedCornerShape(18.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        actionColor = MaterialTheme.colorScheme.primary,
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = PAGE_GUTTER,
                        end = PAGE_GUTTER,
                        bottom = bottomNavInset + barsDp.coerceAtLeast(120.dp) + 8.dp
                    )
            )

            // 6. In-window Liquid Glass Dialogs/Sheets & Dropdown Menus
            val glassOverlayActive = glassOn
            GlassSheetHost(sheetController, glassBackdrop = glassOverlayActive)
            GlassMenuHost(menuController, glassBackdrop = glassOverlayActive)
        }

        if (showAppearanceSheet) {
            GlassModalBottomSheet(
                onDismissRequest = { showAppearanceSheet = false }
            ) {
                AppearanceSettingsSheet(
                    onDismiss = { showAppearanceSheet = false }
                )
            }
        }

        if (showBypassPickerSheet) {
            AppRoutingPickerSheet(
                initialModes = uiState.appAudioModes,
                onDismiss = { showBypassPickerSheet = false },
                onApply = { selectedModes ->
                    showBypassPickerSheet = false
                    PeqEngineManager.setAppAudioModes(selectedModes)
                }
            )
        }

        pasteDialog?.let { dlg ->
            PasteApoDialog(
                sectionTarget = dlg.sectionTarget,
                onDismiss = { pasteDialog = null },
                onImportWholePreset = { text, name ->
                    handleApoImportResult(PeqEngineManager.importApoAsNewPreset(text, name))
                    pasteDialog = null
                },
                onImportSection = { text, target ->
                    PeqEngineManager.importApoIntoCurrentTarget(text, target)
                    pasteDialog = null
                }
            )
        }

        pendingImportConflict?.let { (bundlePreset, bundleIndep) ->
            val currentLrName = uiState.activeIndependentPreset?.name
                ?: stringResource(R.string.lr_preset_none_title)
            BundlePresetConflictDialog(
                bundleName = bundlePreset.displayName(),
                bundleLrName = bundleIndep.name,
                currentLrName = currentLrName,
                onDismiss = { pendingImportConflict = null },
                onCommonOnly = {
                    pendingImportConflict = null
                    PeqEngineManager.selectSavedPreset(bundlePreset.id, applyIndependentToo = false)
                },
                onBoth = {
                    pendingImportConflict = null
                    PeqEngineManager.selectSavedPreset(bundlePreset.id, applyIndependentToo = true)
                }
            )
        }
    }
}

@Composable
private fun EngineHeroCard(
    uiState: AppUiState,
    onToggleMaster: () -> Unit,
    onRefreshShizuku: () -> Unit
) {
    val activeRunning = uiState.masterPowerOn && !uiState.pausedByDeviceRule && uiState.telemetry.isRunning
    val containerColor by animateColorAsState(
        targetValue = when {
            activeRunning -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
            uiState.masterPowerOn && uiState.pausedByDeviceRule -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "hero-bg"
    )

    val devIcon = when (uiState.activeDevice?.category) {
        DeviceCategory.USB_DAC -> LucideIcons.Usb
        DeviceCategory.BLUETOOTH -> LucideIcons.Bluetooth
        DeviceCategory.WIRED -> LucideIcons.Headphones
        else -> LucideIcons.Speaker
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val statusTitle = when {
                        activeRunning -> stringResource(R.string.hero_status_running)
                        uiState.masterPowerOn && uiState.pausedByDeviceRule -> stringResource(R.string.hero_status_paused_rule)
                        uiState.masterPowerOn -> stringResource(R.string.hero_status_starting)
                        else -> stringResource(R.string.hero_status_stopped)
                    }
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = devIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = uiState.activeDevice?.displayName ?: stringResource(R.string.hero_detecting_output),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Button(
                    onClick = onToggleMaster,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.masterPowerOn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        contentColor = if (uiState.masterPowerOn) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                ) {
                    Icon(LucideIcons.Power, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (uiState.masterPowerOn) "ON" else "OFF",
                        fontWeight = FontWeight.ExtraBold,
                        softWrap = false,
                        maxLines = 1,
                    )
                }
            }

            if (activeRunning) {
                val inLevel = ((uiState.telemetry.inPeakDbfs + 60.0) / 60.0).toFloat().coerceIn(0f, 1f)
                val outLevel = ((uiState.telemetry.outPeakDbfs + 60.0) / 60.0).toFloat().coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "In %.1f → Out %.1f dBFS",
                                uiState.telemetry.inPeakDbfs,
                                uiState.telemetry.outPeakDbfs
                            ),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = false,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (uiState.telemetry.lowLatencyActive) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = stringResource(R.string.low_latency_badge),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    softWrap = false,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "48kHz / 64-bit",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            softWrap = false,
                            maxLines = 1,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { outLevel.coerceAtLeast(inLevel * 0.25f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                }
            } else if (uiState.shizukuStatus != ShizukuStatus.READY) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.shizukuStatus == ShizukuStatus.NOT_RUNNING) {
                            stringResource(R.string.hero_shizuku_not_running)
                        } else {
                            stringResource(R.string.hero_shizuku_permission_required)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onRefreshShizuku) {
                        Icon(
                            LucideIcons.RotateCcw,
                            contentDescription = stringResource(R.string.action_retry_refresh),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            uiState.telemetry.lastError?.let { err ->
                Text(
                    text = stringResource(R.string.hero_engine_error, err),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun PasteApoDialog(
    sectionTarget: PeqChannelTarget?,
    onDismiss: () -> Unit,
    onImportWholePreset: (String, String) -> Unit,
    onImportSection: (String, PeqChannelTarget) -> Unit
) {
    val defaultImportName = stringResource(R.string.default_apo_import_name)
    var presetName by remember(defaultImportName) { mutableStateOf(defaultImportName) }
    var apoText by remember { mutableStateOf("") }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (sectionTarget == null) {
                    stringResource(R.string.dialog_paste_whole_title)
                } else {
                    stringResource(R.string.dialog_paste_section_title, sectionTarget.fullLabel())
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sectionTarget == null) {
                    OutlinedTextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        label = { Text(stringResource(R.string.field_preset_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = apoText,
                    onValueChange = { apoText = it },
                    label = { Text(stringResource(R.string.field_apo_lines)) },
                    placeholder = { Text("Preamp: -6.0 dB\nFilter 1: ON PK Fc 100 Hz Gain -2.0 dB Q 1.2") },
                    minLines = 5,
                    maxLines = 9,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (sectionTarget == null) {
                        onImportWholePreset(apoText, presetName)
                    } else {
                        onImportSection(apoText, sectionTarget)
                    }
                },
                enabled = apoText.isNotBlank()
            ) {
                Text(stringResource(R.string.action_import), softWrap = false, maxLines = 1)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), softWrap = false, maxLines = 1)
            }
        }
    )
}

private fun queryFileDisplayName(context: android.content.Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
    return uri.lastPathSegment
}
