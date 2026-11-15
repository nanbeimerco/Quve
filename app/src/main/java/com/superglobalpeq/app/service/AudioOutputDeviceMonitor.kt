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

class AudioOutputDeviceMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(OutputDevicesState())
    val state: StateFlow<OutputDevicesState> = _state.asStateFlow()

    private val mediaAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    // Doesn't match our USAGE_MEDIA/GAME/UNKNOWN loopback mix, so helps disambiguate physical sink.
    private val navAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .build()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
        }
    }

    fun start() {
        refreshDevices()
        audioManager.registerAudioDeviceCallback(callback, handler)
    }

    fun stop() {
        try {
            audioManager.unregisterAudioDeviceCallback(callback)
        } catch (_: Throwable) {
        }
    }

    fun refreshDevices() {
        val allOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val activeForMedia = try {
            audioManager.getAudioDevicesForAttributes(mediaAttributes)
        } catch (_: Throwable) {
            emptyList()
        }
        var activeKeys = activeForMedia.mapNotNull { toDeviceSnapshot(it, true)?.deviceKey }.toSet()
        if (activeKeys.isEmpty()) {
            val activeForNav = try {
                audioManager.getAudioDevicesForAttributes(navAttributes)
            } catch (_: Throwable) {
                emptyList()
            }
            activeKeys = activeForNav.mapNotNull { toDeviceSnapshot(it, true)?.deviceKey }.toSet()
        }

        val snapshotsByKey = LinkedHashMap<String, OutputDeviceSnapshot>()
        for (info in allOutputs) {
            val snap = toDeviceSnapshot(info, isActive = false) ?: continue
            val isActive = snap.deviceKey in activeKeys
            val existing = snapshotsByKey[snap.deviceKey]
            if (existing == null || isActive) {
                snapshotsByKey[snap.deviceKey] = snap.copy(isActive = isActive)
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
                .thenBy { categoryPriority(it.category) }
                .thenBy { it.displayName }
        )
        _state.value = OutputDevicesState(
            connectedDevices = sorted,
            activeDevice = active
        )
    }

    private fun categoryPriority(cat: DeviceCategory): Int = when (cat) {
        DeviceCategory.USB_DAC -> 0
        DeviceCategory.BLUETOOTH -> 1
        DeviceCategory.WIRED -> 2
        DeviceCategory.SPEAKER -> 3
    }

    private fun toDeviceSnapshot(info: AudioDeviceInfo, isActive: Boolean): OutputDeviceSnapshot? {
        if (!info.isSink) return null
        val localized = QuveSettings.localizedContext(appContext)
        val type = info.type
        val product = info.productName?.toString()?.trim().orEmpty()
        val address = info.address?.trim().orEmpty()

        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> OutputDeviceSnapshot(
                deviceKey = "speaker",
                displayName = localized.getString(R.string.device_built_in_speaker),
                category = DeviceCategory.SPEAKER,
                isActive = isActive
            )

            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> {
                val idPart = address.ifBlank { product.ifBlank { "bt_$type" } }
                OutputDeviceSnapshot(
                    deviceKey = "bt:$idPart",
                    displayName = product.ifBlank {
                        localized.getString(R.string.device_bt_audio_fallback, address)
                    },
                    category = DeviceCategory.BLUETOOTH,
                    address = address,
                    isActive = isActive
                )
            }

            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> {
                val name = product.ifBlank { "USB DAC" }
                val keySuffix = address.ifBlank { name }
                OutputDeviceSnapshot(
                    deviceKey = "usb:$keySuffix",
                    displayName = name,
                    category = DeviceCategory.USB_DAC,
                    address = address,
                    isActive = isActive
                )
            }

            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_AUX_LINE -> {
                val label = when (type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADSET -> localized.getString(R.string.device_wired_headset)
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> localized.getString(R.string.device_wired_headphones)
                    AudioDeviceInfo.TYPE_LINE_DIGITAL -> localized.getString(R.string.device_digital_line_out)
                    else -> localized.getString(R.string.device_wired_line_out)
                }
                val display = if (product.isNotBlank() && !product.equals(BuildInfoModelHint, true)) {
                    "$label ($product)"
                } else {
                    label
                }
                OutputDeviceSnapshot(
                    deviceKey = "wired:$type",
                    displayName = display,
                    category = DeviceCategory.WIRED,
                    address = address,
                    isActive = isActive
                )
            }

            else -> null
        }
    }

    companion object {
        private const val BuildInfoModelHint = ""
    }
}
