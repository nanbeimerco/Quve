package com.superglobalpeq.app

import android.app.Application
import com.superglobalpeq.app.data.QuveSettings
import com.superglobalpeq.app.service.PeqEngineManager

/**
 * Application entry point so [QuveSettings] and [PeqEngineManager] initialize reliably
 * even when the OS restarts [com.superglobalpeq.app.service.PeqForegroundService] in the background.
 */
class QuveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        QuveSettings.initialize(this)
        PeqEngineManager.initialize(this)
    }
}
