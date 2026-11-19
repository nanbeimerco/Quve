package com.superglobalpeq.app.tile

import android.content.Intent
import com.superglobalpeq.app.MainActivity
import com.superglobalpeq.app.R
import com.superglobalpeq.app.service.PeqEngineManager
import com.superglobalpeq.app.service.ShizukuStatus
import com.superglobalpeq.app.service.ShootoutManager
import com.superglobalpeq.app.ui.components.QuveTab

class PowerTileService : BaseQuveTileService() {
    override val kind: QuveTileKind = QuveTileKind.POWER
    override val iconRes: Int = R.drawable.ic_stat_waveform

    override fun onTileClicked() {
        PeqEngineManager.refreshShizukuStatus()
        val state = PeqEngineManager.uiState.value
        if (!state.masterPowerOn && state.shizukuStatus != ShizukuStatus.READY) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(MainActivity.EXTRA_OPEN_TAB, QuveTab.PEQ.name)
                putExtra(MainActivity.EXTRA_REQUEST_SHIZUKU, true)
            }
            unlockAndRun {
                QuveTileRegistry.startActivityAndCollapseCompat(this, intent, REQ_POWER_SHIZUKU)
            }
            return
        }
        PeqEngineManager.setMasterPower(!state.masterPowerOn)
    }

    private companion object {
        const val REQ_POWER_SHIZUKU = 4101
    }
}

class PresetTileService : BaseQuveTileService() {
    override val kind: QuveTileKind = QuveTileKind.PRESET
    override val iconRes: Int = R.drawable.ic_tile_presets

    override fun onTileClicked() {
        if (ShootoutManager.state.value.running != null) return
        val intent = Intent(this, QuickPresetPickerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        unlockAndRun {
            QuveTileRegistry.startActivityAndCollapseCompat(this, intent, REQ_PRESET_PICKER)
        }
    }

    private companion object {
        const val REQ_PRESET_PICKER = 4102
    }
}

class LowLatencyTileService : BaseQuveTileService() {
    override val kind: QuveTileKind = QuveTileKind.LOW_LATENCY
    override val iconRes: Int = R.drawable.ic_tile_low_latency

    override fun onTileClicked() {
        val current = PeqEngineManager.uiState.value.globalLowLatency
        PeqEngineManager.setGlobalLowLatency(!current)
    }
}

class ChannelSwapTileService : BaseQuveTileService() {
    override val kind: QuveTileKind = QuveTileKind.CHANNEL_SWAP
    override val iconRes: Int = R.drawable.ic_tile_channel_swap

    override fun onTileClicked() {
        if (ShootoutManager.state.value.running != null) return
        val state = PeqEngineManager.uiState.value
        PeqEngineManager.setChannelSwap(!state.channelSwap, state.swapPosition)
    }
}
