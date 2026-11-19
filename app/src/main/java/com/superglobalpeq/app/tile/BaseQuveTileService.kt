package com.superglobalpeq.app.tile

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.DrawableRes
import com.superglobalpeq.app.data.QuveSettings
import com.superglobalpeq.app.service.AppUiState
import com.superglobalpeq.app.service.PeqEngineManager
import com.superglobalpeq.app.service.ShootoutManager
import com.superglobalpeq.app.service.ShootoutUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

abstract class BaseQuveTileService : TileService() {
    protected abstract val kind: QuveTileKind

    @get:DrawableRes
    protected abstract val iconRes: Int

    protected abstract fun onTileClicked()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listeningJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        QuveSettings.initialize(applicationContext)
        PeqEngineManager.initialize(applicationContext)
        PeqEngineManager.refreshShizukuStatus()
        renderNow()

        listeningJob?.cancel()
        listeningJob = scope.launch {
            combine(
                PeqEngineManager.uiState,
                ShootoutManager.state,
                QuveSettings.appLanguage,
            ) { ui, shootout, _ ->
                resolveCurrentSpec(ui, shootout)
            }
                .distinctUntilChanged()
                .collect { spec ->
                    applySpec(spec)
                }
        }
    }

    override fun onStopListening() {
        listeningJob?.cancel()
        listeningJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        listeningJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        QuveSettings.initialize(applicationContext)
        PeqEngineManager.initialize(applicationContext)
        onTileClicked()
        renderNow()
    }

    protected fun renderNow() {
        applySpec(
            resolveCurrentSpec(
                uiState = PeqEngineManager.uiState.value,
                shootoutState = ShootoutManager.state.value,
            )
        )
    }

    private fun resolveCurrentSpec(
        uiState: AppUiState,
        shootoutState: ShootoutUiState,
    ): TileDisplaySpec {
        val localized = QuveSettings.localizedContext(this)
        val input = QuveTileRegistry.buildTileSnapshotInput(localized, uiState, shootoutState)
        val strings = QuveTileRegistry.buildTileStrings(localized)
        return resolveTileSpec(kind, input, strings)
    }

    private fun applySpec(spec: TileDisplaySpec) {
        val tile = qsTile ?: return
        tile.state = when (spec.state) {
            TileAvailabilityState.ACTIVE -> Tile.STATE_ACTIVE
            TileAvailabilityState.INACTIVE -> Tile.STATE_INACTIVE
            TileAvailabilityState.UNAVAILABLE -> Tile.STATE_UNAVAILABLE
        }
        tile.label = spec.label
        tile.subtitle = spec.subtitle
        tile.icon = Icon.createWithResource(this, iconRes)
        tile.contentDescription = "${spec.label}, ${spec.subtitle}"
        tile.updateTile()
    }
}
