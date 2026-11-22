package com.superglobalpeq.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
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
import java.io.File
import java.util.Locale

data class OutputDevicesState(
    val connectedDevices: List<OutputDeviceSnapshot> = emptyList(),
    val activeDevice: OutputDeviceSnapshot? = null
)

internal data class UsbAudioDescriptor(
    val vendorId: Int,
    val productId: Int,
    val manufacturerName: String = "",
    val productName: String = "",
    val hasAudioInterface: Boolean = true
) {
    val vidPidHex: String
        get() = "%04x:%04x".format(Locale.US, vendorId and 0xFFFF, productId and 0xFFFF)
}

internal data class ResolvedUsbAudioIdentity(
    val deviceKey: String,
    val displayName: String,
    val address: String
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

private val ALSA_CARD_REGEX = Regex("""card\s*=\s*(\d+)""", RegexOption.IGNORE_CASE)
private val USB_VID_PID_REGEX = Regex("""([0-9a-fA-F]{4}):([0-9a-fA-F]{4})""")
private val USB_AUDIO_PREFIX_REGEX = Regex("""^USB[\s\-_]*Audio\s*[-–—:]\s*""", RegexOption.IGNORE_CASE)
private val WHITESPACE_REGEX = Regex("""\s+""")

private val GENERIC_USB_AUDIO_NAMES = setOf(
    "usb-audio",
    "usb audio",
    "usb audio device",
    "usb dac",
    "usb headset",
    "usb device",
    "usb-c headset",
    "usb-c audio",
    "digital audio",
    "audio"
)

internal fun isGenericUsbAudioName(name: String): Boolean {
    val normalized = normalizeUsbLabel(name).lowercase(Locale.US)
    return normalized.isEmpty() || normalized in GENERIC_USB_AUDIO_NAMES
}

internal fun normalizeUsbLabel(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return raw.trim().replace(WHITESPACE_REGEX, " ")
}

internal fun stripUsbAudioPrefix(raw: String?): String {
    val normalized = normalizeUsbLabel(raw)
    if (normalized.isEmpty()) return ""
    val stripped = USB_AUDIO_PREFIX_REGEX.replaceFirst(normalized, "").trim()
    return stripped.ifEmpty { normalized }
}

internal fun parseAlsaCardNumber(address: String): Int? =
    ALSA_CARD_REGEX.find(address)?.groupValues?.getOrNull(1)?.toIntOrNull()

internal fun readProcAlsaUsbId(cardNumber: Int): String? = try {
    val file = File("/proc/asound/card$cardNumber/usbid")
    if (!file.canRead()) {
        null
    } else {
        val text = file.readText().trim()
        val match = USB_VID_PID_REGEX.find(text) ?: return null
        "${match.groupValues[1].lowercase(Locale.US)}:${match.groupValues[2].lowercase(Locale.US)}"
    }
} catch (_: Throwable) {
    null
}

internal fun readProcAlsaStreamLabel(cardNumber: Int): String? = try {
    val file = File("/proc/asound/card$cardNumber/stream0")
    if (!file.canRead()) {
        null
    } else {
        val firstLine = file.useLines { seq -> seq.firstOrNull() }?.trim().orEmpty()
        if (firstLine.isEmpty()) {
            null
        } else {
            val beforeAt = firstLine.substringBefore(" at usb-").trim()
            normalizeUsbLabel(beforeAt).ifEmpty { null }
        }
    }
} catch (_: Throwable) {
    null
}

private fun isUsbAudioDevice(device: UsbDevice): Boolean {
    if (device.deviceClass == UsbConstants.USB_CLASS_AUDIO) return true
    for (i in 0 until device.interfaceCount) {
        if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_AUDIO) {
            return true
        }
    }
    return false
}

internal fun queryConnectedUsbAudioDescriptors(usbManager: UsbManager?): List<UsbAudioDescriptor> {
    if (usbManager == null) return emptyList()
    return try {
        val devices = usbManager.deviceList?.values ?: return emptyList()
        devices.filter(::isUsbAudioDevice)
            .map { dev ->
                UsbAudioDescriptor(
                    vendorId = dev.vendorId,
                    productId = dev.productId,
                    manufacturerName = normalizeUsbLabel(dev.manufacturerName),
                    productName = normalizeUsbLabel(dev.productName),
                    hasAudioInterface = true
                )
            }
            .sortedWith(compareBy<UsbAudioDescriptor> { it.vidPidHex }.thenBy { it.productName }.thenBy { it.manufacturerName })
    } catch (_: Throwable) {
        emptyList()
    }
}

internal fun formatUsbDisplayName(
    manufacturer: String,
    product: String,
    fallbackAudioProduct: String,
    vidPid: String
): String {
    val cleanMfg = normalizeUsbLabel(manufacturer)
    val cleanProd = stripUsbAudioPrefix(product)
    val cleanFallback = stripUsbAudioPrefix(fallbackAudioProduct)

    val baseName = when {
        cleanProd.isNotEmpty() && !isGenericUsbAudioName(cleanProd) -> {
            if (cleanMfg.isNotEmpty() &&
                !isGenericUsbAudioName(cleanMfg) &&
                !cleanProd.contains(cleanMfg, ignoreCase = true)
            ) {
                "$cleanMfg $cleanProd"
            } else {
                cleanProd
            }
        }
        cleanFallback.isNotEmpty() && !isGenericUsbAudioName(cleanFallback) -> {
            if (cleanMfg.isNotEmpty() &&
                !isGenericUsbAudioName(cleanMfg) &&
                !cleanFallback.contains(cleanMfg, ignoreCase = true)
            ) {
                "$cleanMfg $cleanFallback"
            } else {
                cleanFallback
            }
        }
        cleanMfg.isNotEmpty() && !isGenericUsbAudioName(cleanMfg) -> "$cleanMfg USB DAC"
        cleanProd.isNotEmpty() -> "$cleanProd ($vidPid)"
        else -> "USB DAC ($vidPid)"
    }
    return baseName
}

internal fun resolveUsbAudioIdentity(
    audioProduct: String,
    audioAddress: String,
    usbDescriptors: List<UsbAudioDescriptor>,
    procUsbIdResolver: (Int) -> String? = ::readProcAlsaUsbId,
    procStreamLabelResolver: (Int) -> String? = ::readProcAlsaStreamLabel
): ResolvedUsbAudioIdentity {
    val cardNum = parseAlsaCardNumber(audioAddress)
    val procUsbId = cardNum?.let { procUsbIdResolver(it)?.lowercase(Locale.US) }
    val cleanAudioProduct = stripUsbAudioPrefix(audioProduct)

    val matchedDescriptor: UsbAudioDescriptor? = when {
        usbDescriptors.isEmpty() -> null
        procUsbId != null -> usbDescriptors.firstOrNull { it.vidPidHex == procUsbId }
            ?: if (usbDescriptors.size == 1) usbDescriptors.first() else null
        usbDescriptors.size == 1 -> usbDescriptors.first()
        !isGenericUsbAudioName(cleanAudioProduct) -> {
            usbDescriptors.firstOrNull { desc ->
                val prod = stripUsbAudioPrefix(desc.productName)
                val mfg = normalizeUsbLabel(desc.manufacturerName)
                (prod.isNotEmpty() && (cleanAudioProduct.contains(prod, ignoreCase = true) || prod.contains(cleanAudioProduct, ignoreCase = true))) ||
                    (mfg.isNotEmpty() && cleanAudioProduct.contains(mfg, ignoreCase = true))
            }
        }
        else -> {
            val streamLabel = cardNum?.let { procStreamLabelResolver(it) }.orEmpty()
            if (streamLabel.isNotEmpty()) {
                usbDescriptors.firstOrNull { desc ->
                    val prod = stripUsbAudioPrefix(desc.productName)
                    val mfg = normalizeUsbLabel(desc.manufacturerName)
                    (prod.isNotEmpty() && streamLabel.contains(prod, ignoreCase = true)) ||
                        (mfg.isNotEmpty() && streamLabel.contains(mfg, ignoreCase = true))
                }
            } else {
                null
            }
        }
    }

    if (matchedDescriptor != null) {
        val vidPid = matchedDescriptor.vidPidHex
        val cleanProd = stripUsbAudioPrefix(matchedDescriptor.productName)
        val cleanMfg = normalizeUsbLabel(matchedDescriptor.manufacturerName)
        val labelForKey = when {
            cleanProd.isNotEmpty() -> cleanProd
            cleanMfg.isNotEmpty() -> cleanMfg
            !isGenericUsbAudioName(cleanAudioProduct) -> cleanAudioProduct
            else -> ""
        }.lowercase(Locale.US)
        val key = if (labelForKey.isNotEmpty()) "usb:$vidPid:$labelForKey" else "usb:$vidPid"
        val display = formatUsbDisplayName(
            manufacturer = cleanMfg,
            product = cleanProd,
            fallbackAudioProduct = cleanAudioProduct,
            vidPid = vidPid
        )
        return ResolvedUsbAudioIdentity(
            deviceKey = key,
            displayName = display,
            address = vidPid
        )
    }

    // Fallback 1: /proc/asound/cardN/usbid (+ stream0) when UsbManager was unavailable
    if (procUsbId != null && cardNum != null) {
        val streamLabel = procStreamLabelResolver(cardNum).orEmpty()
        val labelForKey = when {
            streamLabel.isNotEmpty() && !isGenericUsbAudioName(streamLabel) -> streamLabel
            !isGenericUsbAudioName(cleanAudioProduct) -> cleanAudioProduct
            else -> ""
        }.lowercase(Locale.US)
        val key = if (labelForKey.isNotEmpty()) "usb:$procUsbId:$labelForKey" else "usb:$procUsbId"
        val display = formatUsbDisplayName(
            manufacturer = "",
            product = streamLabel,
            fallbackAudioProduct = cleanAudioProduct,
            vidPid = procUsbId
        )
        return ResolvedUsbAudioIdentity(
            deviceKey = key,
            displayName = display,
            address = procUsbId
        )
    }

    // Fallback 2: Use non-generic product name instead of volatile ALSA card=X;device=Y; address
    if (!isGenericUsbAudioName(cleanAudioProduct)) {
        return ResolvedUsbAudioIdentity(
            deviceKey = "usb:name:${cleanAudioProduct.lowercase(Locale.US)}",
            displayName = cleanAudioProduct,
            address = audioAddress
        )
    }

    // Last resort when neither UsbManager, /proc/asound, nor a specific product name is available
    val fallbackSuffix = audioAddress.ifBlank { cleanAudioProduct.ifBlank { "generic" } }
    return ResolvedUsbAudioIdentity(
        deviceKey = "usb:$fallbackSuffix",
        displayName = cleanAudioProduct.ifBlank { "USB DAC" },
        address = audioAddress
    )
}

internal fun classifyOutputDeviceKeyAndCategory(
    type: Int,
    product: String,
    address: String,
    usbDescriptors: List<UsbAudioDescriptor> = emptyList(),
    procUsbIdResolver: (Int) -> String? = ::readProcAlsaUsbId,
    procStreamLabelResolver: (Int) -> String? = ::readProcAlsaStreamLabel
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
            val resolved = resolveUsbAudioIdentity(
                audioProduct = product,
                audioAddress = address,
                usbDescriptors = usbDescriptors,
                procUsbIdResolver = procUsbIdResolver,
                procStreamLabelResolver = procStreamLabelResolver
            )
            resolved.deviceKey to DeviceCategory.USB_DAC
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

private fun extractOutputDeviceKey(
    info: AudioDeviceInfo,
    usbDescriptors: List<UsbAudioDescriptor>
): String? {
    if (!info.isSink) return null
    return classifyOutputDeviceKeyAndCategory(
        type = info.type,
        product = info.productName?.toString()?.trim().orEmpty(),
        address = info.address.trim(),
        usbDescriptors = usbDescriptors
    )?.first
}

/**
 * Shared output-device snapshot & active-sink query used by both the app UI monitor and
 * the UID 2000 UserService autonomous device watcher.
 */
internal fun queryOutputDevicesState(
    audioManager: AudioManager,
    usbManager: UsbManager? = null,
    displayNameResolver: (type: Int, product: String, address: String, category: DeviceCategory) -> String
): OutputDevicesState {
    val usbDescriptors = queryConnectedUsbAudioDescriptors(usbManager)
    val allOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    val activeForMedia = try {
        audioManager.getAudioDevicesForAttributes(MEDIA_OUTPUT_ATTRIBUTES)
    } catch (_: Throwable) {
        emptyList()
    }
    var activeKeys = activeForMedia.mapNotNull { extractOutputDeviceKey(it, usbDescriptors) }.toSet()

    if (activeKeys.isEmpty()) {
        val activeForNav = try {
            audioManager.getAudioDevicesForAttributes(NAV_OUTPUT_ATTRIBUTES)
        } catch (_: Throwable) {
            emptyList()
        }
        activeKeys = activeForNav.mapNotNull { extractOutputDeviceKey(it, usbDescriptors) }.toSet()
    }

    val snapshotsByKey = LinkedHashMap<String, OutputDeviceSnapshot>()
    for (info in allOutputs) {
        if (!info.isSink) continue
        val type = info.type
        val product = info.productName?.toString()?.trim().orEmpty()
        val address = info.address.trim()
        val (key, category) = classifyOutputDeviceKeyAndCategory(
            type = type,
            product = product,
            address = address,
            usbDescriptors = usbDescriptors
        ) ?: continue
        val isActive = key in activeKeys
        val existing = snapshotsByKey[key]
        if (existing == null || isActive) {
            val (resolvedDisplayName, resolvedAddress) = if (category == DeviceCategory.USB_DAC) {
                val usbIdentity = resolveUsbAudioIdentity(
                    audioProduct = product,
                    audioAddress = address,
                    usbDescriptors = usbDescriptors
                )
                usbIdentity.displayName to usbIdentity.address
            } else {
                displayNameResolver(type, product, address, category) to address
            }
            snapshotsByKey[key] = OutputDeviceSnapshot(
                deviceKey = key,
                displayName = resolvedDisplayName,
                category = category,
                address = resolvedAddress,
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
    private val usbManager = appContext.getSystemService(UsbManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(OutputDevicesState())
    val state: StateFlow<OutputDevicesState> = _state.asStateFlow()

    private val delayedRefreshRunnable = Runnable { refreshDevices() }
    private val lateRefreshRunnable = Runnable { refreshDevices() }
    private var usbReceiverRegistered = false

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

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == UsbManager.ACTION_USB_DEVICE_ATTACHED ||
                action == UsbManager.ACTION_USB_DEVICE_DETACHED
            ) {
                refreshDevices()
                scheduleFollowUpRefresh()
            }
        }
    }

    private fun scheduleFollowUpRefresh() {
        handler.removeCallbacks(delayedRefreshRunnable)
        handler.removeCallbacks(lateRefreshRunnable)
        handler.postDelayed(delayedRefreshRunnable, 250L)
        handler.postDelayed(lateRefreshRunnable, 700L)
    }

    fun start() {
        refreshDevices()
        audioManager.registerAudioDeviceCallback(callback, handler)
        if (!usbReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            try {
                appContext.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
                usbReceiverRegistered = true
            } catch (_: Throwable) {
            }
        }
    }

    fun stop() {
        handler.removeCallbacks(delayedRefreshRunnable)
        handler.removeCallbacks(lateRefreshRunnable)
        try {
            audioManager.unregisterAudioDeviceCallback(callback)
        } catch (_: Throwable) {
        }
        if (usbReceiverRegistered) {
            try {
                appContext.unregisterReceiver(usbReceiver)
            } catch (_: Throwable) {
            }
            usbReceiverRegistered = false
        }
    }

    fun refreshDevices() {
        val localized = QuveSettings.localizedContext(appContext)
        _state.value = queryOutputDevicesState(
            audioManager = audioManager,
            usbManager = usbManager
        ) { type, product, address, category ->
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
