package com.superglobalpeq.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.superglobalpeq.app.data.QuveSettings
import com.superglobalpeq.app.service.PeqEngineManager
import com.superglobalpeq.app.ui.MainScreen
import com.superglobalpeq.app.ui.theme.QuveTheme

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional notification permission */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        QuveSettings.initialize(applicationContext)
        PeqEngineManager.initialize(applicationContext)

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
                MainScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        QuveSettings.syncLocaleFromSystem(this)
        PeqEngineManager.onAppLocaleChanged()
        PeqEngineManager.refreshShizukuStatus()
        PeqEngineManager.startTelemetryPolling(true)
    }

    override fun onStop() {
        PeqEngineManager.startTelemetryPolling(false)
        super.onStop()
    }
}
