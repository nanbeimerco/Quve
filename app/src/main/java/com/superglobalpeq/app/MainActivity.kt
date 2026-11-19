package com.superglobalpeq.app

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.superglobalpeq.app.data.QuveSettings
import com.superglobalpeq.app.service.PeqEngineManager
import com.superglobalpeq.app.service.ShizukuStatus
import com.superglobalpeq.app.service.ShootoutManager
import com.superglobalpeq.app.tile.resolveLongPressTab
import com.superglobalpeq.app.ui.MainScreen
import com.superglobalpeq.app.ui.components.QuveTab
import com.superglobalpeq.app.ui.theme.QuveTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_TAB = "extra_open_tab"
        const val EXTRA_REQUEST_SHIZUKU = "extra_request_shizuku"
    }

    private var pendingTabRequest by mutableStateOf<QuveTab?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional notification permission */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        QuveSettings.initialize(applicationContext)
        PeqEngineManager.initialize(applicationContext)
        handleLaunchIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            QuveTheme {
                MainScreen(
                    requestedTab = pendingTabRequest,
                    onConsumeRequestedTab = { pendingTabRequest = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        QuveSettings.syncLocaleFromSystem(this)
        PeqEngineManager.onAppLocaleChanged()
        PeqEngineManager.refreshShizukuStatus()
        PeqEngineManager.startTelemetryPolling(true)
        ShootoutManager.setAppVisible(true)
    }

    override fun onStop() {
        PeqEngineManager.startTelemetryPolling(false)
        if (!isChangingConfigurations) ShootoutManager.setAppVisible(false)
        super.onStop()
    }

    private fun handleLaunchIntent(launchIntent: Intent?) {
        if (launchIntent == null) return
        if (launchIntent.action == TileService.ACTION_QS_TILE_PREFERENCES) {
            val component = launchIntent.getParcelableExtra(
                Intent.EXTRA_COMPONENT_NAME,
                ComponentName::class.java,
            )
            pendingTabRequest = resolveLongPressTab(component?.className)
        } else {
            val tabName = launchIntent.getStringExtra(EXTRA_OPEN_TAB)
            if (tabName != null) {
                QuveTab.entries.firstOrNull { it.name == tabName }?.let {
                    pendingTabRequest = it
                }
            }
        }
        if (launchIntent.getBooleanExtra(EXTRA_REQUEST_SHIZUKU, false)) {
            PeqEngineManager.refreshShizukuStatus()
            if (PeqEngineManager.uiState.value.shizukuStatus != ShizukuStatus.READY) {
                PeqEngineManager.requestShizukuPermission()
            }
        }
    }
}
