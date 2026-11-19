package com.superglobalpeq.app

import com.superglobalpeq.app.data.QuveLanguage
import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.model.IndependentPeqPreset
import com.superglobalpeq.app.service.AppUiState
import com.superglobalpeq.app.service.EngineTelemetry
import com.superglobalpeq.app.service.ShizukuStatus
import com.superglobalpeq.app.service.ShootoutRunningState
import com.superglobalpeq.app.service.ShootoutUiState
import com.superglobalpeq.app.tile.TileAvailabilityState
import com.superglobalpeq.app.tile.TileSnapshotInput
import com.superglobalpeq.app.tile.TileStrings
import com.superglobalpeq.app.tile.TileSyncFingerprint
import com.superglobalpeq.app.tile.formatTilePresetSummary
import com.superglobalpeq.app.tile.resolveChannelSwapTileSpec
import com.superglobalpeq.app.tile.resolveLongPressTab
import com.superglobalpeq.app.tile.resolveLowLatencyTileSpec
import com.superglobalpeq.app.tile.resolvePowerTileSpec
import com.superglobalpeq.app.tile.resolvePresetTileSpec
import com.superglobalpeq.app.ui.components.QuveTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class QuickSettingsTileTest {
    private val strings = TileStrings(
        powerLabel = "Quve PEQ",
        presetLabel = "Quve Preset",
        lowLatencyLabel = "Quve Low Latency",
        channelSwapLabel = "Quve L⇄R Swap",
        subOff = "OFF",
        subRuleOff = "Rule OFF",
        subShizukuOff = "Shizuku Off",
        subShizukuPerm = "Permission needed",
        formatShootoutRound = { round -> "Shootout #$round" },
        subShootoutActive = "Shootout active",
        subLowLatGlobal = "Global (4ms)",
        subLowLatPerApp = "Per-App Auto",
        subLowLatStandard = "Standard (10ms)",
        subSwapOn = "Reversed",
        subSwapOff = "Standard",
    )

    private fun baseInput(
        shizukuStatus: ShizukuStatus = ShizukuStatus.READY,
        masterPowerOn: Boolean = true,
        pausedByDeviceRule: Boolean = false,
        activePresetSummary: String = "Harman IE",
        channelSwap: Boolean = false,
        globalLowLatency: Boolean = false,
        perAppLowLatencyActive: Boolean = false,
        shootoutRound: Int? = null,
    ) = TileSnapshotInput(
        shizukuStatus = shizukuStatus,
        masterPowerOn = masterPowerOn,
        pausedByDeviceRule = pausedByDeviceRule,
        activePresetSummary = activePresetSummary,
        channelSwap = channelSwap,
        globalLowLatency = globalLowLatency,
        perAppLowLatencyActive = perAppLowLatencyActive,
        shootoutRound = shootoutRound,
    )

    @Test
    fun formatTilePresetSummary_handlesCommonOnlyAndWithIndependent() {
        assertEquals("Harman IE", formatTilePresetSummary("Harman IE", null))
        assertEquals("Harman IE", formatTilePresetSummary("Harman IE", "   "))
        assertEquals("Harman IE + EarMatch", formatTilePresetSummary("Harman IE", "EarMatch"))
    }

    @Test
    fun powerTile_resolvesShizukuStatesAndPowerStates() {
        val shizukuOff = resolvePowerTileSpec(
            baseInput(shizukuStatus = ShizukuStatus.NOT_RUNNING),
            strings,
        )
        assertEquals(TileAvailabilityState.INACTIVE, shizukuOff.state)
        assertEquals("Shizuku Off", shizukuOff.subtitle)

        val shizukuPerm = resolvePowerTileSpec(
            baseInput(shizukuStatus = ShizukuStatus.PERMISSION_REQUIRED),
            strings,
        )
        assertEquals(TileAvailabilityState.INACTIVE, shizukuPerm.state)
        assertEquals("Permission needed", shizukuPerm.subtitle)

        val powerOff = resolvePowerTileSpec(
            baseInput(masterPowerOn = false),
            strings,
        )
        assertEquals(TileAvailabilityState.INACTIVE, powerOff.state)
        assertEquals("OFF", powerOff.subtitle)

        val commonOnlyOn = resolvePowerTileSpec(
            baseInput(activePresetSummary = "Harman IE"),
            strings,
        )
        assertEquals(TileAvailabilityState.ACTIVE, commonOnlyOn.state)
        assertEquals("Harman IE", commonOnlyOn.subtitle)

        val withLrOn = resolvePowerTileSpec(
            baseInput(activePresetSummary = formatTilePresetSummary("Harman IE", "Unit Cal")),
            strings,
        )
        assertEquals(TileAvailabilityState.ACTIVE, withLrOn.state)
        assertEquals("Harman IE + Unit Cal", withLrOn.subtitle)

        val rulePaused = resolvePowerTileSpec(
            baseInput(masterPowerOn = true, pausedByDeviceRule = true),
            strings,
        )
        assertEquals(TileAvailabilityState.ACTIVE, rulePaused.state)
        assertEquals("Rule OFF", rulePaused.subtitle)

        val shootout = resolvePowerTileSpec(
            baseInput(shootoutRound = 7),
            strings,
        )
        assertEquals(TileAvailabilityState.ACTIVE, shootout.state)
        assertEquals("Shootout #7", shootout.subtitle)
    }

    @Test
    fun presetTile_hidesPresetNameDuringShootoutAndReflectsEngineState() {
        val running = resolvePresetTileSpec(
            baseInput(activePresetSummary = "Studio Neutral"),
            strings,
        )
        assertEquals(TileAvailabilityState.ACTIVE, running.state)
        assertEquals("Studio Neutral", running.subtitle)

        val stopped = resolvePresetTileSpec(
            baseInput(masterPowerOn = false, activePresetSummary = "Studio Neutral"),
            strings,
        )
        assertEquals(TileAvailabilityState.INACTIVE, stopped.state)
        assertEquals("Studio Neutral", stopped.subtitle)

        val rulePaused = resolvePresetTileSpec(
            baseInput(masterPowerOn = true, pausedByDeviceRule = true, activePresetSummary = "Studio Neutral"),
            strings,
        )
        assertEquals(TileAvailabilityState.INACTIVE, rulePaused.state)
        assertEquals("Studio Neutral", rulePaused.subtitle)

        val shootout = resolvePresetTileSpec(
            baseInput(activePresetSummary = "Secret Preset Name", shootoutRound = 3),
            strings,
        )
        assertEquals(TileAvailabilityState.UNAVAILABLE, shootout.state)
        assertEquals("Shootout active", shootout.subtitle)
    }

    @Test
    fun lowLatencyTile_resolvesGlobalPerAppAndStandardModes() {
        val globalOn = resolveLowLatencyTileSpec(
            baseInput(globalLowLatency = true, perAppLowLatencyActive = true),
            strings,
        )
        assertEquals(TileAvailabilityState.ACTIVE, globalOn.state)
        assertEquals("Global (4ms)", globalOn.subtitle)

        val perAppAuto = resolveLowLatencyTileSpec(
            baseInput(globalLowLatency = false, perAppLowLatencyActive = true),
            strings,
        )
        assertEquals(TileAvailabilityState.INACTIVE, perAppAuto.state)
        assertEquals("Per-App Auto", perAppAuto.subtitle)

        val perAppWhilePaused = resolveLowLatencyTileSpec(
            baseInput(
                masterPowerOn = true,
                pausedByDeviceRule = true,
                globalLowLatency = false,
                perAppLowLatencyActive = true,
            ),
            strings,
        )
        assertEquals(TileAvailabilityState.INACTIVE, perAppWhilePaused.state)
        assertEquals("Standard (10ms)", perAppWhilePaused.subtitle)

        val standard = resolveLowLatencyTileSpec(
            baseInput(globalLowLatency = false, perAppLowLatencyActive = false),
            strings,
        )
        assertEquals(TileAvailabilityState.INACTIVE, standard.state)
        assertEquals("Standard (10ms)", standard.subtitle)
    }

    @Test
    fun channelSwapTile_reflectsToggleAndDisablesDuringShootout() {
        val swapOn = resolveChannelSwapTileSpec(
            baseInput(channelSwap = true),
            strings,
        )
        assertEquals(TileAvailabilityState.ACTIVE, swapOn.state)
        assertEquals("Reversed", swapOn.subtitle)

        val swapOff = resolveChannelSwapTileSpec(
            baseInput(channelSwap = false),
            strings,
        )
        assertEquals(TileAvailabilityState.INACTIVE, swapOff.state)
        assertEquals("Standard", swapOff.subtitle)

        val shootout = resolveChannelSwapTileSpec(
            baseInput(channelSwap = true, shootoutRound = 2),
            strings,
        )
        assertEquals(TileAvailabilityState.UNAVAILABLE, shootout.state)
        assertEquals("Shootout active", shootout.subtitle)
    }

    @Test
    fun resolveLongPressTab_mapsAllTileServicesAndFallback() {
        assertEquals(QuveTab.PEQ, resolveLongPressTab("com.superglobalpeq.app.tile.PowerTileService"))
        assertEquals(QuveTab.PRESETS, resolveLongPressTab("com.superglobalpeq.app.tile.PresetTileService"))
        assertEquals(QuveTab.ROUTING, resolveLongPressTab("com.superglobalpeq.app.tile.LowLatencyTileService"))
        assertEquals(QuveTab.ROUTING, resolveLongPressTab("com.superglobalpeq.app.tile.ChannelSwapTileService"))
        assertEquals(QuveTab.PEQ, resolveLongPressTab("com.example.UnknownTileService"))
        assertEquals(QuveTab.PEQ, resolveLongPressTab(null))
    }

    @Test
    fun tileSyncFingerprint_ignoresTelemetryMetersAndTracksRealChanges() {
        val presetA = EqPreset(id = "a", name = "Preset A")
        val presetB = EqPreset(id = "b", name = "Preset B")
        val indep = IndependentPeqPreset(id = "lr1", name = "L/R Cal")

        val baseUi = AppUiState(
            shizukuStatus = ShizukuStatus.READY,
            masterPowerOn = true,
            presets = listOf(presetA, presetB),
            selectedPresetId = presetA.id,
            independentPresets = listOf(indep),
            telemetry = EngineTelemetry(
                isRunning = true,
                framesProcessed = 100L,
                inPeakDbfs = -12.0,
                outPeakDbfs = -14.0,
                queuedFrames = 480L,
            ),
        )
        val meterChangedUi = baseUi.copy(
            telemetry = baseUi.telemetry.copy(
                framesProcessed = 99999L,
                inPeakDbfs = -2.5,
                outPeakDbfs = -3.1,
                queuedFrames = 960L,
            ),
        )
        val shootoutIdle = ShootoutUiState()

        val fp1 = TileSyncFingerprint.from(baseUi, shootoutIdle, QuveLanguage.SYSTEM)
        val fp2 = TileSyncFingerprint.from(meterChangedUi, shootoutIdle, QuveLanguage.SYSTEM)
        assertEquals(fp1, fp2)

        assertNotEquals(fp1, TileSyncFingerprint.from(baseUi.copy(masterPowerOn = false), shootoutIdle, QuveLanguage.SYSTEM))
        assertNotEquals(fp1, TileSyncFingerprint.from(baseUi.copy(selectedPresetId = presetB.id), shootoutIdle, QuveLanguage.SYSTEM))
        assertNotEquals(fp1, TileSyncFingerprint.from(baseUi.copy(selectedIndependentPresetId = indep.id), shootoutIdle, QuveLanguage.SYSTEM))
        assertNotEquals(fp1, TileSyncFingerprint.from(baseUi.copy(channelSwap = true), shootoutIdle, QuveLanguage.SYSTEM))
        assertNotEquals(fp1, TileSyncFingerprint.from(baseUi.copy(globalLowLatency = true), shootoutIdle, QuveLanguage.SYSTEM))
        assertNotEquals(fp1, TileSyncFingerprint.from(baseUi.copy(pausedByDeviceRule = true), shootoutIdle, QuveLanguage.SYSTEM))
        assertNotEquals(
            fp1,
            TileSyncFingerprint.from(
                baseUi.copy(telemetry = baseUi.telemetry.copy(perAppLowLatencyActive = true)),
                shootoutIdle,
                QuveLanguage.SYSTEM,
            ),
        )
        assertNotEquals(
            fp1,
            TileSyncFingerprint.from(
                baseUi,
                ShootoutUiState(
                    running = ShootoutRunningState(
                        sessionId = "s1",
                        round = 1,
                        votes = 0,
                        recommendedVotes = 20,
                        contestantCount = 2,
                    ),
                ),
                QuveLanguage.SYSTEM,
            ),
        )
        assertNotEquals(fp1, TileSyncFingerprint.from(baseUi, shootoutIdle, QuveLanguage.JAPANESE))
    }
}
