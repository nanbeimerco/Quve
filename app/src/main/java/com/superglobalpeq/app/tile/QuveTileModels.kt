package com.superglobalpeq.app.tile

import com.superglobalpeq.app.data.QuveLanguage
import com.superglobalpeq.app.service.AppUiState
import com.superglobalpeq.app.service.ShizukuStatus
import com.superglobalpeq.app.service.ShootoutUiState
import com.superglobalpeq.app.ui.components.QuveTab

enum class QuveTileKind {
    POWER,
    PRESET,
    LOW_LATENCY,
    CHANNEL_SWAP,
}

enum class TileAvailabilityState {
    ACTIVE,
    INACTIVE,
    UNAVAILABLE,
}

data class TileDisplaySpec(
    val state: TileAvailabilityState,
    val label: String,
    val subtitle: String,
)

data class TileSnapshotInput(
    val shizukuStatus: ShizukuStatus,
    val masterPowerOn: Boolean,
    val pausedByDeviceRule: Boolean,
    val activePresetSummary: String,
    val channelSwap: Boolean,
    val globalLowLatency: Boolean,
    val perAppLowLatencyActive: Boolean,
    val shootoutRound: Int? = null,
)

data class TileStrings(
    val powerLabel: String,
    val presetLabel: String,
    val lowLatencyLabel: String,
    val channelSwapLabel: String,
    val subOff: String,
    val subRuleOff: String,
    val subShizukuOff: String,
    val subShizukuPerm: String,
    val formatShootoutRound: (Int) -> String,
    val subShootoutActive: String,
    val subLowLatGlobal: String,
    val subLowLatPerApp: String,
    val subLowLatStandard: String,
    val subSwapOn: String,
    val subSwapOff: String,
)

/** Combines Common and optional L/R preset names into a concise tile subtitle. */
fun formatTilePresetSummary(commonName: String, independentName: String?): String {
    val indep = independentName?.trim().orEmpty()
    return if (indep.isEmpty()) commonName else "$commonName + $indep"
}

/** Resolves the display state, label, and subtitle for the PEQ master power tile. */
fun resolvePowerTileSpec(
    input: TileSnapshotInput,
    strings: TileStrings,
): TileDisplaySpec {
    val (state, subtitle) = when {
        input.shizukuStatus == ShizukuStatus.NOT_RUNNING ->
            TileAvailabilityState.INACTIVE to strings.subShizukuOff
        input.shizukuStatus == ShizukuStatus.PERMISSION_REQUIRED ->
            TileAvailabilityState.INACTIVE to strings.subShizukuPerm
        !input.masterPowerOn ->
            TileAvailabilityState.INACTIVE to strings.subOff
        input.shootoutRound != null ->
            TileAvailabilityState.ACTIVE to strings.formatShootoutRound(input.shootoutRound)
        input.pausedByDeviceRule ->
            TileAvailabilityState.ACTIVE to strings.subRuleOff
        else ->
            TileAvailabilityState.ACTIVE to input.activePresetSummary
    }
    return TileDisplaySpec(
        state = state,
        label = strings.powerLabel,
        subtitle = subtitle,
    )
}

/** Resolves the display state, label, and subtitle for the quick preset picker tile. */
fun resolvePresetTileSpec(
    input: TileSnapshotInput,
    strings: TileStrings,
): TileDisplaySpec {
    if (input.shootoutRound != null) {
        return TileDisplaySpec(
            state = TileAvailabilityState.UNAVAILABLE,
            label = strings.presetLabel,
            subtitle = strings.subShootoutActive,
        )
    }
    val active = input.shizukuStatus == ShizukuStatus.READY &&
        input.masterPowerOn &&
        !input.pausedByDeviceRule
    return TileDisplaySpec(
        state = if (active) TileAvailabilityState.ACTIVE else TileAvailabilityState.INACTIVE,
        label = strings.presetLabel,
        subtitle = input.activePresetSummary,
    )
}

/** Resolves the display state, label, and subtitle for the global low-latency tile. */
fun resolveLowLatencyTileSpec(
    input: TileSnapshotInput,
    strings: TileStrings,
): TileDisplaySpec {
    val perAppLive = input.shizukuStatus == ShizukuStatus.READY &&
        input.masterPowerOn &&
        !input.pausedByDeviceRule &&
        input.perAppLowLatencyActive
    val (state, subtitle) = when {
        input.globalLowLatency ->
            TileAvailabilityState.ACTIVE to strings.subLowLatGlobal
        perAppLive ->
            TileAvailabilityState.INACTIVE to strings.subLowLatPerApp
        else ->
            TileAvailabilityState.INACTIVE to strings.subLowLatStandard
    }
    return TileDisplaySpec(
        state = state,
        label = strings.lowLatencyLabel,
        subtitle = subtitle,
    )
}

/** Resolves the display state, label, and subtitle for the L/R channel swap tile. */
fun resolveChannelSwapTileSpec(
    input: TileSnapshotInput,
    strings: TileStrings,
): TileDisplaySpec {
    if (input.shootoutRound != null) {
        return TileDisplaySpec(
            state = TileAvailabilityState.UNAVAILABLE,
            label = strings.channelSwapLabel,
            subtitle = strings.subShootoutActive,
        )
    }
    val (state, subtitle) = if (input.channelSwap) {
        TileAvailabilityState.ACTIVE to strings.subSwapOn
    } else {
        TileAvailabilityState.INACTIVE to strings.subSwapOff
    }
    return TileDisplaySpec(
        state = state,
        label = strings.channelSwapLabel,
        subtitle = subtitle,
    )
}

/** Dispatches tile display resolution for [kind]. */
fun resolveTileSpec(
    kind: QuveTileKind,
    input: TileSnapshotInput,
    strings: TileStrings,
): TileDisplaySpec = when (kind) {
    QuveTileKind.POWER -> resolvePowerTileSpec(input, strings)
    QuveTileKind.PRESET -> resolvePresetTileSpec(input, strings)
    QuveTileKind.LOW_LATENCY -> resolveLowLatencyTileSpec(input, strings)
    QuveTileKind.CHANNEL_SWAP -> resolveChannelSwapTileSpec(input, strings)
}

/**
 * Equality key containing only fields that affect Quick Settings tiles, filtering out
 * high-frequency telemetry updates (e.g. 200 ms peak/RMS meters and frame counters).
 */
data class TileSyncFingerprint(
    val shizukuStatus: ShizukuStatus,
    val masterPowerOn: Boolean,
    val pausedByDeviceRule: Boolean,
    val selectedPresetId: String,
    val activeCommonName: String,
    val activeIndependentName: String?,
    val channelSwap: Boolean,
    val globalLowLatency: Boolean,
    val perAppLowLatencyActive: Boolean,
    val shootoutRound: Int?,
    val language: QuveLanguage,
) {
    companion object {
        fun from(
            uiState: AppUiState,
            shootoutState: ShootoutUiState,
            language: QuveLanguage,
        ): TileSyncFingerprint = TileSyncFingerprint(
            shizukuStatus = uiState.shizukuStatus,
            masterPowerOn = uiState.masterPowerOn,
            pausedByDeviceRule = uiState.pausedByDeviceRule,
            selectedPresetId = uiState.selectedPresetId,
            activeCommonName = uiState.activeCommonPreset.name,
            activeIndependentName = uiState.activeIndependentPreset?.name,
            channelSwap = uiState.channelSwap,
            globalLowLatency = uiState.globalLowLatency,
            perAppLowLatencyActive = uiState.telemetry.perAppLowLatencyActive,
            shootoutRound = shootoutState.running?.round,
            language = language,
        )
    }
}

/** Maps a Quick Settings tile service class name from long-press preferences to its target tab. */
fun resolveLongPressTab(tileClassName: String?): QuveTab {
    val name = tileClassName.orEmpty()
    return when {
        name.endsWith(".PowerTileService") -> QuveTab.PEQ
        name.endsWith(".PresetTileService") -> QuveTab.PRESETS
        name.endsWith(".LowLatencyTileService") -> QuveTab.ROUTING
        name.endsWith(".ChannelSwapTileService") -> QuveTab.ROUTING
        else -> QuveTab.PEQ
    }
}
