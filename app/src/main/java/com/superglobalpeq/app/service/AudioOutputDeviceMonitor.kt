package com.superglobalpeq.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.superglobalpeq.app.R
import com.superglobalpeq.app.data.QuveSettings
import com.superglobalpeq.app.model.DeviceCategory
import com.superglobalpeq.app.model.OutputDeviceSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OutputDevicesState(
    val connectedDevices: List<OutputDeviceSnapshot> = emptyList(),
    val activeDevice: OutputDeviceSnapshot? = null
)

private val MEDIA_OUTPUT_ATTRIBUTES: AudioAttributes by lazy {
    AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
}

// Doesn't match our USAGE_MEDIA/GAME/UNKNOWN loopback mix, so helps disambiguate physical sink.
private val NAV_OUTPUT_ATTRIBUTES: AudioAttributes by lazy {
    AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .build()
}

internal fun classifyOutputDeviceKeyAndCategory(
    type: Int,
    product: String,
    address: String
): Pair<String, DeviceCategory>? {
    return when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "speaker" to DeviceCategory.SPEAKER

        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> {
            val idPart = address.ifBlank { product.ifBlank { "bt_$type" } }
            "bt:$idPart" to DeviceCategory.BLUETOOTH
        }

        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> {
            val name = product.ifBlank { "USB DAC" }
            val keySuffix = address.ifBlank { name }
            "usb:$keySuffix" to DeviceCategory.USB_DAC
        }

        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        AudioDeviceInfo.TYPE_AUX_LINE -> "wired:$type" to DeviceCategory.WIRED

        else -> null
    }
}

private fun deviceCategoryPriority(cat: DeviceCategory): Int = when (cat) {
    DeviceCategory.USB_DAC -> 0
    DeviceCategory.BLUETOOTH -> 1
    DeviceCategory.WIRED -> 2
    DeviceCategory.SPEAKER -> 3
}

private fun extractOutputDeviceKey(info: AudioDeviceInfo): String? {
    if (!info.isSink) return null
    return classifyOutputDeviceKeyAndCategory(
        type = info.type,
        product = info.productName?.toString()?.trim().orEmpty(),
        address = info.address.trim()
    )?.first
}

/**
 * Shared output-device snapshot & active-sink query used by both the app UI monitor and
 * the UID 2000 UserService autonomous device watcher.
 */
internal fun queryOutputDevicesState(
    audioManager: AudioManager,
    displayNameResolver: (type: Int, product: String, address: String, category: DeviceCategory) -> String
): OutputDevicesState {
    val allOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    val activeForMedia = try {
        audioManager.getAudioDevicesForAttributes(MEDIA_OUTPUT_ATTRIBUTES)
    } catch (_: Throwable) {
        emptyList()
    }
    var activeKeys = activeForMedia.mapNotNull(::extractOutputDeviceKey).toSet()

    if (activeKeys.isEmpty()) {
        val activeForNav = try {
            audioManager.getAudioDevicesForAttributes(NAV_OUTPUT_ATTRIBUTES)
        } catch (_: Throwable) {
            emptyList()
        }
        activeKeys = activeForNav.mapNotNull(::extractOutputDeviceKey).toSet()
    }

    val snapshotsByKey = LinkedHashMap<String, OutputDeviceSnapshot>()
    for (info in allOutputs) {
        if (!info.isSink) continue
        val type = info.type
        val product = info.productName?.toString()?.trim().orEmpty()
        val address = info.address.trim()
        val (key, category) = classifyOutputDeviceKeyAndCategory(type, product, address) ?: continue
        val isActive = key in activeKeys
        val existing = snapshotsByKey[key]
        if (existing == null || isActive) {
            snapshotsByKey[key] = OutputDeviceSnapshot(
                deviceKey = key,
                displayName = displayNameResolver(type, product, address, category),
                category = category,
                address = address,
                isActive = isActive
            )
        }
    }

    var active = snapshotsByKey.values.firstOrNull { it.isActive }
    if (active == null) {
        active = snapshotsByKey.values.firstOrNull { it.category == DeviceCategory.USB_DAC }
            ?: snapshotsByKey.values.firstOrNull { it.category == DeviceCategory.BLUETOOTH }
            ?: snapshotsByKey.values.firstOrNull { it.category == DeviceCategory.WIRED }
            ?: snapshotsByKey["speaker"]
        if (active != null) {
            snapshotsByKey[active.deviceKey] = active.copy(isActive = true)
            active = snapshotsByKey[active.deviceKey]
        }
    }

    val sorted = snapshotsByKey.values.sortedWith(
        compareBy<OutputDeviceSnapshot> { !it.isActive }
            .thenBy { deviceCategoryPriority(it.category) }
            .thenBy { it.displayName }
    )
    return OutputDevicesState(
        connectedDevices = sorted,
        activeDevice = active
    )
}

class AudioOutputDeviceMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(OutputDevicesState())
    val state: StateFlow<OutputDevicesState> = _state.asStateFlow()

    private val delayedRefreshRunnable = Runnable { refreshDevices() }

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
            scheduleFollowUpRefresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
            scheduleFollowUpRefresh()
        }
    }

    private fun scheduleFollowUpRefresh() {
        handler.removeCallbacks(delayedRefreshRunnable)
        handler.postDelayed(delayedRefreshRunnable, 250L)
    }

    fun start() {
        refreshDevices()
        audioManager.registerAudioDeviceCallback(callback, handler)
    }

    fun stop() {
        handler.removeCallbacks(delayedRefreshRunnable)
        try {
            audioManager.unregisterAudioDeviceCallback(callback)
        } catch (_: Throwable) {
        }
    }

    fun refreshDevices() {
        val localized = QuveSettings.localizedContext(appContext)
        _state.value = queryOutputDevicesState(audioManager) { type, product, address, category ->
            localizedDisplayName(localized, type, product, address, category)
        }
    }

    private fun localizedDisplayName(
        localized: Context,
        type: Int,
        product: String,
        address: String,
        category: DeviceCategory
    ): String {
        return when (category) {
            DeviceCategory.SPEAKER -> localized.getString(R.string.device_built_in_speaker)
            DeviceCategory.BLUETOOTH -> product.ifBlank {
                localized.getString(R.string.device_bt_audio_fallback, address)
            }
            DeviceCategory.USB_DAC -> product.ifBlank { "USB DAC" }
            DeviceCategory.WIRED -> {
                val label = when (type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADSET -> localized.getString(R.string.device_wired_headset)
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> localized.getString(R.string.device_wired_headphones)
                    AudioDeviceInfo.TYPE_LINE_DIGITAL -> localized.getString(R.string.device_digital_line_out)
                    else -> localized.getString(R.string.device_wired_line_out)
                }
                if (product.isNotBlank() && !product.equals(BuildInfoModelHint, true)) {
                    "$label ($product)"
                } else {
                    label
                }
            }
        }
    }

    companion object {
        private const val BuildInfoModelHint = ""
    }
}
