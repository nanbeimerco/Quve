package com.superglobalpeq.app.tile

import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.superglobalpeq.app.R
import com.superglobalpeq.app.data.QuveSettings
import com.superglobalpeq.app.dsp.DefaultPresets
import com.superglobalpeq.app.service.AppUiState
import com.superglobalpeq.app.service.PeqEngineManager
import com.superglobalpeq.app.service.ShootoutManager
import com.superglobalpeq.app.service.ShootoutUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class QuveTileEntry(
    val kind: QuveTileKind,
    val serviceClass: Class<out TileService>,
    @param:StringRes @get:StringRes val labelRes: Int,
    @param:StringRes @get:StringRes val descRes: Int,
    @param:DrawableRes @get:DrawableRes val iconRes: Int,
)

object QuveTileRegistry {
    private const val TAG = "QuveTileRegistry"

    val allTiles: List<QuveTileEntry> = listOf(
        QuveTileEntry(
            kind = QuveTileKind.POWER,
            serviceClass = PowerTileService::class.java,
            labelRes = R.string.tile_power_label,
            descRes = R.string.qs_tile_power_desc,
            iconRes = R.drawable.ic_stat_waveform,
        ),
        QuveTileEntry(
            kind = QuveTileKind.PRESET,
            serviceClass = PresetTileService::class.java,
            labelRes = R.string.tile_preset_label,
            descRes = R.string.qs_tile_preset_desc,
            iconRes = R.drawable.ic_tile_presets,
        ),
        QuveTileEntry(
            kind = QuveTileKind.LOW_LATENCY,
            serviceClass = LowLatencyTileService::class.java,
            labelRes = R.string.tile_low_latency_label,
            descRes = R.string.qs_tile_low_latency_desc,
            iconRes = R.drawable.ic_tile_low_latency,
        ),
        QuveTileEntry(
            kind = QuveTileKind.CHANNEL_SWAP,
            serviceClass = ChannelSwapTileService::class.java,
            labelRes = R.string.tile_channel_swap_label,
            descRes = R.string.qs_tile_channel_swap_desc,
            iconRes = R.drawable.ic_tile_channel_swap,
        ),
    )

    fun requestAllListeningStates(context: Context) {
        val appCtx = context.applicationContext
        for (entry in allTiles) {
            runCatching {
                TileService.requestListeningState(appCtx, ComponentName(appCtx, entry.serviceClass))
            }.onFailure { t ->
                Log.w(TAG, "requestListeningState failed for ${entry.serviceClass.simpleName}", t)
            }
        }
    }

    fun requestAddTile(
        context: Context,
        entry: QuveTileEntry,
        onFeedback: (String) -> Unit,
    ) {
        val localized = QuveSettings.localizedContext(context)
        val label = localized.getString(entry.labelRes)
        val statusBarManager = context.getSystemService(StatusBarManager::class.java)
        if (statusBarManager == null) {
            onFeedback(localized.getString(R.string.qs_msg_manual_hint, label))
            return
        }
        runCatching {
            statusBarManager.requestAddTileService(
                ComponentName(context, entry.serviceClass),
                label,
                Icon.createWithResource(context, entry.iconRes),
                context.mainExecutor,
            ) { result ->
                when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                        onFeedback(localized.getString(R.string.qs_msg_added, label))
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                        onFeedback(localized.getString(R.string.qs_msg_already_added, label))
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> Unit
                    else ->
                        onFeedback(localized.getString(R.string.qs_msg_manual_hint, label))
                }
            }
        }.onFailure { t ->
            Log.w(TAG, "requestAddTileService failed for ${entry.serviceClass.simpleName}", t)
            onFeedback(localized.getString(R.string.qs_msg_manual_hint, label))
        }
    }

    fun startActivityAndCollapseCompat(
        service: TileService,
        intent: Intent,
        requestCode: Int,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                service,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            service.startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            service.startActivityAndCollapse(intent)
        }
    }

    fun buildTileStrings(localized: Context): TileStrings = TileStrings(
        powerLabel = localized.getString(R.string.tile_power_label),
        presetLabel = localized.getString(R.string.tile_preset_label),
        lowLatencyLabel = localized.getString(R.string.tile_low_latency_label),
        channelSwapLabel = localized.getString(R.string.tile_channel_swap_label),
        subOff = localized.getString(R.string.tile_sub_off),
        subRuleOff = localized.getString(R.string.tile_sub_rule_off),
        subShizukuOff = localized.getString(R.string.tile_sub_shizuku_off),
        subShizukuPerm = localized.getString(R.string.tile_sub_shizuku_perm),
        formatShootoutRound = { round -> localized.getString(R.string.tile_sub_shootout_round, round) },
        subShootoutActive = localized.getString(R.string.tile_sub_shootout_active),
        subLowLatGlobal = localized.getString(R.string.tile_sub_low_lat_global),
        subLowLatPerApp = localized.getString(R.string.tile_sub_low_lat_per_app),
        subLowLatStandard = localized.getString(R.string.tile_sub_low_lat_standard),
        subSwapOn = localized.getString(R.string.tile_sub_swap_on),
        subSwapOff = localized.getString(R.string.tile_sub_swap_off),
    )

    fun buildTileSnapshotInput(
        localized: Context,
        uiState: AppUiState,
        shootoutState: ShootoutUiState,
    ): TileSnapshotInput {
        val common = uiState.activeCommonPreset
        val commonName = if (
            common.id == DefaultPresets.FLAT_PRESET_ID &&
            common.name in DefaultPresets.FLAT_DEFAULT_NAMES
        ) {
            localized.getString(R.string.preset_flat_name)
        } else {
            common.name
        }
        val summary = formatTilePresetSummary(
            commonName = commonName,
            independentName = uiState.activeIndependentPreset?.name,
        )
        return TileSnapshotInput(
            shizukuStatus = uiState.shizukuStatus,
            masterPowerOn = uiState.masterPowerOn,
            pausedByDeviceRule = uiState.pausedByDeviceRule,
            activePresetSummary = summary,
            channelSwap = uiState.channelSwap,
            globalLowLatency = uiState.globalLowLatency,
            perAppLowLatencyActive = uiState.telemetry.perAppLowLatencyActive,
            shootoutRound = shootoutState.running?.round,
        )
    }
}

/**
 * Observes tile-relevant state changes process-wide and notifies SystemUI only when a
 * tile's visible state or label actually changes (ignoring high-frequency level meters).
 */
object QuveTileSync {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val appCtx = context.applicationContext
        scope.launch {
            combine(
                PeqEngineManager.uiState,
                ShootoutManager.state,
                QuveSettings.appLanguage,
            ) { ui, shootout, lang ->
                TileSyncFingerprint.from(ui, shootout, lang)
            }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    QuveTileRegistry.requestAllListeningStates(appCtx)
                }
        }
    }
}
