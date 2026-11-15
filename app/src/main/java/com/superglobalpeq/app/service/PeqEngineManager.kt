package com.superglobalpeq.app.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Binder
import android.os.DeadObjectException
import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import android.util.Log
import androidx.annotation.StringRes
import com.superglobalpeq.app.BuildConfig
import com.superglobalpeq.app.R
import com.superglobalpeq.app.data.PeqPreferencesRepository
import com.superglobalpeq.app.data.QuveSettings
import com.superglobalpeq.app.dsp.DefaultPresets
import com.superglobalpeq.app.dsp.EqualizerApoParser
import com.superglobalpeq.app.dsp.FrequencyResponseCalculator
import com.superglobalpeq.app.dsp.FrequencyResponseCurves
import com.superglobalpeq.app.model.ActiveDspConfig
import com.superglobalpeq.app.model.AppBypassCandidate
import com.superglobalpeq.app.model.BypassedAppItem
import com.superglobalpeq.app.model.DeviceCategory
import com.superglobalpeq.app.model.DeviceRoutingRule
import com.superglobalpeq.app.model.DeviceRuleAction
import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.model.FilterType
import com.superglobalpeq.app.model.OutputDeviceSnapshot
import com.superglobalpeq.app.model.PeqBand
import com.superglobalpeq.app.model.PeqChannelTarget
import com.superglobalpeq.app.model.SwapPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class ShizukuStatus {
    NOT_RUNNING,
    PERMISSION_REQUIRED,
    READY
}

data class EngineTelemetry(
    val isRunning: Boolean = false,
    val uid: Int = -1,
    val sampleRate: Int = 48000,
    val framesProcessed: Long = 0L,
    val excludedUidCount: Int = 0,
    val inPeakDbfs: Double = -120.0,
    val inRmsDbfs: Double = -120.0,
    val outPeakDbfs: Double = -120.0,
    val outRmsDbfs: Double = -120.0,
    val lastError: String? = null
)

data class AppUiState(
    val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_RUNNING,
    val serviceBound: Boolean = false,
    val masterPowerOn: Boolean = false,
    val pausedByDeviceRule: Boolean = false,
    val telemetry: EngineTelemetry = EngineTelemetry(),
    val presets: List<EqPreset> = emptyList(),
    val selectedPresetId: String = DefaultPresets.FLAT_PRESET_ID,
    val editingTarget: PeqChannelTarget = PeqChannelTarget.COMMON,
    val channelSwap: Boolean = false,
    val swapPosition: SwapPosition = SwapPosition.BEFORE_INDEPENDENT_EQ,
    val connectedDevices: List<OutputDeviceSnapshot> = emptyList(),
    val activeDevice: OutputDeviceSnapshot? = null,
    val deviceRules: List<DeviceRoutingRule> = emptyList(),
    val bypassedPackages: Set<String> = emptySet(),
    val bypassedApps: List<BypassedAppItem> = emptyList(),
    val frequencyCurves: FrequencyResponseCurves? = null,
    val statusBanner: String? = null
) {
    val activePreset: EqPreset
        get() = presets.firstOrNull { it.id == selectedPresetId }
            ?: presets.firstOrNull()
            ?: DefaultPresets.createFlatPreset()
}

object PeqEngineManager {
    private const val TAG = "PeqEngineManager"
    private const val SHIZUKU_REQ_CODE = 2001
    private const val USER_SERVICE_PROTOCOL_VERSION = 4
    private const val SHELL_PACKAGE = "com.android.shell"

    private val initialized = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ipcDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "quve-peq-ipc").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val clientDeathToken: IBinder = Binder()

    private lateinit var appContext: Context
    private lateinit var repo: PeqPreferencesRepository
    private lateinit var deviceMonitor: AudioOutputDeviceMonitor

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    @Volatile
    private var remoteService: IAudioLoopbackService? = null
    @Volatile
    private var bindingInProgress = false
    @Volatile
    private var pendingStartAfterBind = false
    private val pendingSyncRestart = AtomicBoolean(false)
    private val syncScheduled = AtomicBoolean(false)
    private val configUpdateDirty = AtomicBoolean(false)
    private val configUpdateScheduled = AtomicBoolean(false)
    private var meterJob: Job? = null
    private var lastAutoAppliedKey: String? = null

    private val userServiceArgs by lazy {
        UserServiceArgs(
            ComponentName(appContext.packageName, AudioLoopbackUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("peq_dsp")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE * 100 + USER_SERVICE_PROTOCOL_VERSION)
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshShizukuStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        remoteService = null
        bindingInProgress = false
        _uiState.update {
            it.copy(
                shizukuStatus = ShizukuStatus.NOT_RUNNING,
                serviceBound = false,
                telemetry = it.telemetry.copy(isRunning = false)
            )
        }
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQ_CODE) {
            refreshShizukuStatus()
            if (grantResult == PackageManager.PERMISSION_GRANTED && _uiState.value.masterPowerOn) {
                syncEngineWithState(restartLoopback = true)
            }
        }
    }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bindingInProgress = false
            val stub = IAudioLoopbackService.Stub.asInterface(service)
            remoteService = stub
            Log.i(TAG, "Shizuku UserService connected")
            _uiState.update { it.copy(serviceBound = true, statusBanner = null) }
            if (stub != null) {
                scope.launch(ipcDispatcher) {
                    try {
                        stub.registerClientToken(clientDeathToken)
                    } catch (t: Throwable) {
                        handleRemoteServiceError(t, stub)
                    }
                }
            }
            if (pendingStartAfterBind || (_uiState.value.masterPowerOn && !_uiState.value.pausedByDeviceRule)) {
                pendingStartAfterBind = false
                syncEngineWithState(restartLoopback = true)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bindingInProgress = false
            remoteService = null
            Log.w(TAG, "Shizuku UserService disconnected")
            _uiState.update {
                it.copy(
                    serviceBound = false,
                    telemetry = it.telemetry.copy(isRunning = false)
                )
            }
            if (_uiState.value.masterPowerOn && !_uiState.value.pausedByDeviceRule) {
                pendingStartAfterBind = true
            }
            refreshShizukuStatus()
        }
    }

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        appContext = context.applicationContext
        com.superglobalpeq.app.data.QuveSettings.initialize(appContext)
        repo = PeqPreferencesRepository(appContext)
        deviceMonitor = AudioOutputDeviceMonitor(appContext)

        val persisted = repo.load()
        val activePreset = persisted.presets.firstOrNull { it.id == persisted.selectedPresetId }
            ?: persisted.presets.first()
        val curves = FrequencyResponseCalculator.calculate(activePreset)

        _uiState.value = AppUiState(
            presets = persisted.presets,
            selectedPresetId = activePreset.id,
            channelSwap = activePreset.channelSwap,
            swapPosition = activePreset.swapPosition,
            deviceRules = persisted.deviceRules,
            bypassedPackages = persisted.bypassedPackages,
            bypassedApps = resolveBypassedAppItems(persisted.bypassedPackages),
            frequencyCurves = curves
        )

        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        appContext.registerReceiver(packageChangeReceiver, pkgFilter, Context.RECEIVER_NOT_EXPORTED)

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refreshShizukuStatus()

        deviceMonitor.start()
        scope.launch {
            deviceMonitor.state.collect { devState ->
                onOutputDevicesUpdated(devState)
            }
        }
    }

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val changedPkg = intent?.data?.schemeSpecificPart ?: return
            val bypassed = _uiState.value.bypassedPackages
            if (changedPkg !in bypassed) return
            _uiState.update {
                it.copy(bypassedApps = resolveBypassedAppItems(it.bypassedPackages))
            }
            if (_uiState.value.masterPowerOn && !_uiState.value.pausedByDeviceRule) {
                syncEngineWithState(restartLoopback = true)
            }
        }
    }

    fun refreshShizukuStatus() {
        val status = when {
            !Shizuku.pingBinder() -> ShizukuStatus.NOT_RUNNING
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuStatus.READY
            else -> ShizukuStatus.PERMISSION_REQUIRED
        }
        _uiState.update { it.copy(shizukuStatus = status) }
        if (status == ShizukuStatus.READY && remoteService == null && !bindingInProgress) {
            bindUserService()
        }
    }

    fun requestShizukuPermission() {
        if (!Shizuku.pingBinder()) {
            _uiState.update {
                it.copy(statusBanner = localizedStr(R.string.banner_shizuku_not_running))
            }
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            refreshShizukuStatus()
        } else {
            Shizuku.requestPermission(SHIZUKU_REQ_CODE)
        }
    }

    private fun bindUserService() {
        if (bindingInProgress || !Shizuku.pingBinder()) return
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return
        bindingInProgress = true
        try {
            Shizuku.bindUserService(userServiceArgs, userServiceConnection)
        } catch (t: Throwable) {
            bindingInProgress = false
            Log.e(TAG, "bindUserService failed", t)
            _uiState.update {
                it.copy(statusBanner = localizedStr(R.string.banner_shizuku_bind_failed, t.message.orEmpty()))
            }
        }
    }

    fun setMasterPower(enabled: Boolean) {
        if (enabled && _uiState.value.shizukuStatus != ShizukuStatus.READY) {
            requestShizukuPermission()
            if (_uiState.value.shizukuStatus != ShizukuStatus.READY) return
        }
        _uiState.update { it.copy(masterPowerOn = enabled, statusBanner = null) }
        evaluateActiveDeviceRuleAndApply(forceRestart = true)
    }

    fun startTelemetryPolling(active: Boolean) {
        if (active) {
            if (meterJob?.isActive == true) return
            meterJob = scope.launch(ipcDispatcher) {
                while (isActive) {
                    val svc = remoteService
                    if (svc != null) {
                        pollTelemetryFromService(svc)
                    }
                    delay(200L)
                }
            }
        } else {
            meterJob?.cancel()
            meterJob = null
        }
    }

    private fun pollTelemetryFromService(svc: IAudioLoopbackService) {
        try {
            val obj = JSONObject(svc.statusJson)
            val err = if (obj.isNull("error")) null else obj.optString("error").takeIf { it.isNotEmpty() }
            val telemetry = EngineTelemetry(
                isRunning = obj.optBoolean("running", false),
                uid = obj.optInt("uid", -1),
                sampleRate = obj.optInt("sampleRate", 48000),
                framesProcessed = obj.optLong("framesProcessed", 0L),
                excludedUidCount = obj.optInt("excludedUidCount", 0),
                inPeakDbfs = obj.optDouble("inPeakDbfs", -120.0),
                inRmsDbfs = obj.optDouble("inRmsDbfs", -120.0),
                outPeakDbfs = obj.optDouble("outPeakDbfs", -120.0),
                outRmsDbfs = obj.optDouble("outRmsDbfs", -120.0),
                lastError = err
            )
            _uiState.update { it.copy(telemetry = telemetry) }
        } catch (t: Throwable) {
            handleRemoteServiceError(t, svc)
        }
    }

    private fun handleRemoteServiceError(t: Throwable, failedSvc: IAudioLoopbackService) {
        if (t !is DeadObjectException && t !is RemoteException) return
        Log.w(TAG, "Remote UserService binder error; resetting binding", t)
        if (remoteService === failedSvc) {
            remoteService = null
            bindingInProgress = false
        }
        _uiState.update {
            it.copy(
                serviceBound = false,
                telemetry = it.telemetry.copy(
                    isRunning = false,
                    inPeakDbfs = -120.0,
                    inRmsDbfs = -120.0,
                    outPeakDbfs = -120.0,
                    outRmsDbfs = -120.0,
                    lastError = t.message ?: t.javaClass.simpleName
                )
            )
        }
        scope.launch(Dispatchers.Main.immediate) {
            val state = _uiState.value
            if (state.masterPowerOn && !state.pausedByDeviceRule) {
                pendingStartAfterBind = true
            }
            refreshShizukuStatus()
        }
    }

    private fun onOutputDevicesUpdated(devState: OutputDevicesState) {
        val currentRules = _uiState.value.deviceRules.toMutableList()
        var rulesChanged = false
        for (dev in devState.connectedDevices) {
            val idx = currentRules.indexOfFirst { it.deviceKey == dev.deviceKey }
            if (idx < 0) {
                currentRules.add(
                    DeviceRoutingRule(
                        deviceKey = dev.deviceKey,
                        displayName = dev.displayName,
                        category = dev.category,
                        action = DeviceRuleAction.FOLLOW_MANUAL,
                        presetId = null
                    )
                )
                rulesChanged = true
            } else if (currentRules[idx].displayName != dev.displayName) {
                currentRules[idx] = currentRules[idx].copy(displayName = dev.displayName)
                rulesChanged = true
            }
        }
        if (rulesChanged) {
            repo.saveDeviceRules(currentRules)
        }

        val activeDev = devState.activeDevice
        val deviceSwitched = activeDev?.deviceKey != _uiState.value.activeDevice?.deviceKey
        _uiState.update {
            it.copy(
                connectedDevices = devState.connectedDevices,
                activeDevice = activeDev,
                deviceRules = if (rulesChanged) currentRules else it.deviceRules
            )
        }

        if (deviceSwitched || lastAutoAppliedKey != activeDev?.deviceKey) {
            lastAutoAppliedKey = activeDev?.deviceKey
            evaluateActiveDeviceRuleAndApply(forceRestart = false)
        }
    }

    fun updateDeviceRule(deviceKey: String, action: DeviceRuleAction, presetId: String?) {
        val rules = _uiState.value.deviceRules.map { rule ->
            if (rule.deviceKey == deviceKey) {
                rule.copy(
                    action = action,
                    presetId = if (action == DeviceRuleAction.PRESET) presetId else null
                )
            } else {
                rule
            }
        }
        repo.saveDeviceRules(rules)
        _uiState.update { it.copy(deviceRules = rules) }
        if (_uiState.value.activeDevice?.deviceKey == deviceKey) {
            evaluateActiveDeviceRuleAndApply(forceRestart = true)
        }
    }

    fun removeDeviceRule(deviceKey: String) {
        if (deviceKey == "speaker") return
        val rules = _uiState.value.deviceRules.filterNot { it.deviceKey == deviceKey }
        repo.saveDeviceRules(rules)
        _uiState.update { it.copy(deviceRules = rules) }
    }

    private fun evaluateActiveDeviceRuleAndApply(forceRestart: Boolean) {
        val state = _uiState.value
        val activeDev = state.activeDevice
        val rule = activeDev?.let { d -> state.deviceRules.firstOrNull { it.deviceKey == d.deviceKey } }

        var newPresetId = state.selectedPresetId
        var pausedByRule = false
        var banner = state.statusBanner

        if (rule != null) {
            when (rule.action) {
                DeviceRuleAction.FOLLOW_MANUAL -> {
                    pausedByRule = false
                }
                DeviceRuleAction.EQ_OFF -> {
                    pausedByRule = true
                    banner = localizedStr(R.string.banner_device_eq_off, rule.displayName)
                }
                DeviceRuleAction.PRESET -> {
                    pausedByRule = false
                    val targetPreset = state.presets.firstOrNull { it.id == rule.presetId }
                    if (targetPreset != null) {
                        newPresetId = targetPreset.id
                        repo.saveSelectedPresetId(newPresetId)
                        banner = localizedStr(
                            R.string.banner_device_auto_preset,
                            rule.displayName,
                            displayPresetName(targetPreset)
                        )
                    }
                }
            }
        }

        val preset = state.presets.firstOrNull { it.id == newPresetId } ?: state.activePreset
        val curves = FrequencyResponseCalculator.calculate(preset)

        _uiState.update {
            it.copy(
                selectedPresetId = preset.id,
                channelSwap = preset.channelSwap,
                swapPosition = preset.swapPosition,
                pausedByDeviceRule = pausedByRule,
                frequencyCurves = curves,
                statusBanner = if (!pausedByRule && rule?.action == DeviceRuleAction.FOLLOW_MANUAL) null else banner
            )
        }

        syncEngineWithState(restartLoopback = forceRestart)
    }

    private fun syncEngineWithState(restartLoopback: Boolean) {
        val state = _uiState.value
        val activePreset = state.activePreset
        val shouldRunLoopback = state.masterPowerOn && !state.pausedByDeviceRule

        if (shouldRunLoopback) {
            val deviceLabel = state.activeDevice?.displayName
                ?: localizedStr(R.string.default_output_label)
            val swapLabel = if (activePreset.channelSwap) " | L⇄R Swap ON" else ""
            PeqForegroundService.startOrUpdate(
                appContext,
                title = localizedStr(R.string.notif_running_title, displayPresetName(activePreset)),
                text = "$deviceLabel$swapLabel"
            )
        } else if (state.masterPowerOn && state.pausedByDeviceRule) {
            val deviceLabel = state.activeDevice?.displayName
                ?: localizedStr(R.string.connected_device_label)
            PeqForegroundService.startOrUpdate(
                appContext,
                title = localizedStr(R.string.notif_paused_title),
                text = localizedStr(R.string.notif_paused_text, deviceLabel)
            )
        } else {
            PeqForegroundService.stop(appContext)
        }

        if (restartLoopback) {
            pendingSyncRestart.set(true)
        }
        if (!syncScheduled.compareAndSet(false, true)) return

        scope.launch(ipcDispatcher) {
            syncScheduled.set(false)
            val restartNow = pendingSyncRestart.getAndSet(false)
            executeEngineSync(restartNow)
        }
    }

    private suspend fun executeEngineSync(restartNow: Boolean) {
        val state = _uiState.value
        val activePreset = state.activePreset
        val shouldRunLoopback = state.masterPowerOn && !state.pausedByDeviceRule

        if (shouldRunLoopback) {
            val svc = remoteService
            if (svc == null) {
                if (restartNow) {
                    pendingSyncRestart.set(true)
                }
                pendingStartAfterBind = true
                withContext(Dispatchers.Main.immediate) {
                    bindUserService()
                }
                return
            }

            val config = ActiveDspConfig(
                bypass = false,
                channelSwap = activePreset.channelSwap,
                swapPosition = activePreset.swapPosition,
                preset = activePreset
            )

            try {
                val runningNow = try {
                    JSONObject(svc.statusJson).optBoolean("running", false)
                } catch (t: Throwable) {
                    if (t is DeadObjectException || t is RemoteException) {
                        throw t
                    }
                    false
                }
                if (!runningNow || restartNow) {
                    val excludedUids = resolveExcludedUids(state.bypassedPackages)
                    svc.startLoopback(Process.myUid(), excludedUids, config.toJsonString())
                } else {
                    svc.updateDspConfig(config.toJsonString())
                }
                pollTelemetryFromService(svc)
            } catch (t: Throwable) {
                handleRemoteServiceError(t, svc)
                if (t !is DeadObjectException && t !is RemoteException) {
                    Log.e(TAG, "syncEngineWithState IO error", t)
                }
            }
        } else {
            pendingStartAfterBind = false
            val svc = remoteService
            if (svc != null) {
                try {
                    svc.stopLoopback()
                } catch (t: Throwable) {
                    handleRemoteServiceError(t, svc)
                }
            }
            _uiState.update {
                it.copy(
                    telemetry = it.telemetry.copy(
                        isRunning = false,
                        inPeakDbfs = -120.0,
                        inRmsDbfs = -120.0,
                        outPeakDbfs = -120.0,
                        outRmsDbfs = -120.0
                    )
                )
            }
        }
    }

    private fun pushLiveDspConfigOnly() {
        configUpdateDirty.set(true)
        if (!configUpdateScheduled.compareAndSet(false, true)) return
        scope.launch(ipcDispatcher) {
            configUpdateScheduled.set(false)
            if (!configUpdateDirty.getAndSet(false)) return@launch
            val state = _uiState.value
            val svc = remoteService ?: return@launch
            if (!state.masterPowerOn || state.pausedByDeviceRule) return@launch
            val preset = state.activePreset
            val configJson = ActiveDspConfig(
                bypass = false,
                channelSwap = preset.channelSwap,
                swapPosition = preset.swapPosition,
                preset = preset
            ).toJsonString()
            try {
                svc.updateDspConfig(configJson)
            } catch (t: Throwable) {
                handleRemoteServiceError(t, svc)
            }
        }
    }

    fun onAppLocaleChanged() {
        if (!initialized.get()) return
        deviceMonitor.refreshDevices()
        val state = _uiState.value
        _uiState.update { it.copy(bypassedApps = resolveBypassedAppItems(it.bypassedPackages)) }
        if (state.masterPowerOn) {
            syncEngineWithState(restartLoopback = false)
        }
    }

    suspend fun loadBypassCandidateApps(): List<AppBypassCandidate> = withContext(Dispatchers.IO) {
        val pm = appContext.packageManager
        val selfPkg = appContext.packageName
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launcherPackages = runCatching {
            pm.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            ).mapTo(HashSet()) { it.activityInfo.packageName }
        }.getOrElse { emptySet() }

        val apps = runCatching {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        }.getOrElse { emptyList() }

        apps.asSequence()
            .filter { it.packageName != selfPkg && it.packageName != SHELL_PACKAGE }
            .map { info ->
                val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val label = runCatching { pm.getApplicationLabel(info).toString().trim() }
                    .getOrNull()
                    ?.ifEmpty { null }
                    ?: info.packageName
                AppBypassCandidate(
                    packageName = info.packageName,
                    label = label,
                    uid = info.uid,
                    isSystemApp = isSystem,
                    hasLauncherEntry = info.packageName in launcherPackages
                )
            }
            .sortedWith(
                compareBy<AppBypassCandidate> { it.label.lowercase(Locale.ROOT) }
                    .thenBy { it.packageName }
            )
            .toList()
    }

    fun setBypassedPackages(packages: Set<String>) {
        val selfPkg = appContext.packageName
        val cleaned = packages.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != selfPkg && it != SHELL_PACKAGE }
            .toSet()
        if (cleaned == _uiState.value.bypassedPackages) return

        repo.saveBypassedPackages(cleaned)
        val items = resolveBypassedAppItems(cleaned)
        val banner = if (cleaned.isEmpty()) {
            localizedStr(R.string.banner_bypass_apps_cleared)
        } else {
            localizedStr(R.string.banner_bypass_apps_updated, cleaned.size)
        }
        _uiState.update {
            it.copy(
                bypassedPackages = cleaned,
                bypassedApps = items,
                statusBanner = banner
            )
        }
        syncEngineWithState(restartLoopback = true)
    }

    fun removeBypassedPackage(packageName: String) {
        val next = _uiState.value.bypassedPackages - packageName
        setBypassedPackages(next)
    }

    fun clearBypassedPackages() {
        setBypassedPackages(emptySet())
    }

    private fun resolveExcludedUids(bypassedPackages: Set<String>): IntArray {
        if (bypassedPackages.isEmpty()) return IntArray(0)
        val pm = appContext.packageManager
        val selfUid = Process.myUid()
        val uids = LinkedHashSet<Int>(bypassedPackages.size)
        for (pkg in bypassedPackages) {
            if (pkg == appContext.packageName || pkg == SHELL_PACKAGE) continue
            try {
                val uid = pm.getPackageUid(pkg, PackageManager.PackageInfoFlags.of(0L))
                if (uid > 0 && uid != selfUid) {
                    uids.add(uid)
                }
            } catch (_: Throwable) {
            }
        }
        return uids.toIntArray()
    }

    private fun resolveBypassedAppItems(bypassedPackages: Set<String>): List<BypassedAppItem> {
        if (bypassedPackages.isEmpty()) return emptyList()
        val pm = appContext.packageManager
        return bypassedPackages.asSequence()
            .map { pkg ->
                try {
                    val info = pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
                    val label = pm.getApplicationLabel(info).toString().trim().ifEmpty { pkg }
                    BypassedAppItem(
                        packageName = pkg,
                        displayName = label,
                        uid = info.uid,
                        isInstalled = true
                    )
                } catch (_: Throwable) {
                    BypassedAppItem(
                        packageName = pkg,
                        displayName = pkg,
                        uid = null,
                        isInstalled = false
                    )
                }
            }
            .sortedWith(
                compareBy<BypassedAppItem> { !it.isInstalled }
                    .thenBy { it.displayName.lowercase(Locale.ROOT) }
                    .thenBy { it.packageName }
            )
            .toList()
    }

    private fun localizedStr(@StringRes id: Int, vararg args: Any): String {
        return QuveSettings.localizedContext(appContext).getString(id, *args)
    }

    private fun displayPresetName(preset: EqPreset): String {
        return if (preset.id == DefaultPresets.FLAT_PRESET_ID && preset.name in DefaultPresets.FLAT_DEFAULT_NAMES) {
            localizedStr(R.string.preset_flat_name)
        } else {
            preset.name
        }
    }

    private fun targetDisplayLabel(target: PeqChannelTarget): String = when (target) {
        PeqChannelTarget.COMMON -> localizedStr(R.string.channel_common_label)
        PeqChannelTarget.LEFT -> localizedStr(R.string.channel_left_label)
        PeqChannelTarget.RIGHT -> localizedStr(R.string.channel_right_label)
    }

    fun selectEditingTarget(target: PeqChannelTarget) {
        _uiState.update { it.copy(editingTarget = target) }
    }

    fun selectPreset(presetId: String) {
        val preset = _uiState.value.presets.firstOrNull { it.id == presetId } ?: return
        repo.saveSelectedPresetId(preset.id)
        val curves = FrequencyResponseCalculator.calculate(preset)
        val shownName = displayPresetName(preset)
        _uiState.update {
            it.copy(
                selectedPresetId = preset.id,
                channelSwap = preset.channelSwap,
                swapPosition = preset.swapPosition,
                frequencyCurves = curves,
                statusBanner = localizedStr(R.string.banner_preset_selected, shownName)
            )
        }
        if (_uiState.value.masterPowerOn && !_uiState.value.pausedByDeviceRule) {
            val deviceLabel = _uiState.value.activeDevice?.displayName
                ?: localizedStr(R.string.default_output_label)
            val swapLabel = if (preset.channelSwap) " | L⇄R Swap ON" else ""
            PeqForegroundService.startOrUpdate(
                appContext,
                title = localizedStr(R.string.notif_running_title, shownName),
                text = "$deviceLabel$swapLabel"
            )
        }
        pushLiveDspConfigOnly()
    }

    fun updateActivePreset(transform: (EqPreset) -> EqPreset) {
        val state = _uiState.value
        val updatedPreset = transform(state.activePreset).copy(updatedAtEpochMs = System.currentTimeMillis())
        val updatedList = state.presets.map { if (it.id == updatedPreset.id) updatedPreset else it }
        repo.savePresets(updatedList)
        val curves = FrequencyResponseCalculator.calculate(updatedPreset)
        _uiState.update {
            it.copy(
                presets = updatedList,
                channelSwap = updatedPreset.channelSwap,
                swapPosition = updatedPreset.swapPosition,
                frequencyCurves = curves
            )
        }
        pushLiveDspConfigOnly()
    }

    fun setTargetPreamp(target: PeqChannelTarget, preampDb: Double) {
        val clamped = preampDb.coerceIn(-24.0, 12.0)
        updateActivePreset { it.withTarget(target, preampDb = clamped) }
    }

    fun autoAdjustCommonPreamp() {
        val suggested = FrequencyResponseCalculator.computeAutoPreampAdjustmentDb(_uiState.value.activePreset)
        setTargetPreamp(PeqChannelTarget.COMMON, suggested)
        _uiState.update {
            it.copy(statusBanner = localizedStr(R.string.banner_auto_preamp_adjusted, suggested))
        }
    }

    fun addBand(target: PeqChannelTarget, frequencyHz: Double = 1000.0, gainDb: Double = 0.0, q: Double = 1.0, type: FilterType = FilterType.PK) {
        val newBand = PeqBand(
            id = UUID.randomUUID().toString(),
            enabled = true,
            type = type,
            frequencyHz = frequencyHz,
            gainDb = gainDb,
            q = q
        )
        updateActivePreset { preset ->
            val current = preset.bandsFor(target)
            preset.withTarget(target, bands = current + newBand)
        }
    }

    fun updateBand(target: PeqChannelTarget, updatedBand: PeqBand) {
        updateActivePreset { preset ->
            val list = preset.bandsFor(target).map { if (it.id == updatedBand.id) updatedBand else it }
            preset.withTarget(target, bands = list)
        }
    }

    fun removeBand(target: PeqChannelTarget, bandId: String) {
        updateActivePreset { preset ->
            val list = preset.bandsFor(target).filterNot { it.id == bandId }
            preset.withTarget(target, bands = list)
        }
    }

    fun setChannelSwap(enabled: Boolean, position: SwapPosition = _uiState.value.activePreset.swapPosition) {
        updateActivePreset { it.copy(channelSwap = enabled, swapPosition = position) }
        repo.saveChannelSwap(enabled, position)
        if (_uiState.value.masterPowerOn && !_uiState.value.pausedByDeviceRule) {
            val activePreset = _uiState.value.activePreset
            val deviceLabel = _uiState.value.activeDevice?.displayName
                ?: localizedStr(R.string.default_output_label)
            val swapLabel = if (enabled) " | L⇄R Swap ON" else ""
            PeqForegroundService.startOrUpdate(
                appContext,
                title = localizedStr(R.string.notif_running_title, displayPresetName(activePreset)),
                text = "$deviceLabel$swapLabel"
            )
        }
    }

    fun revertPresetToBaseline(presetId: String = _uiState.value.selectedPresetId) {
        val state = _uiState.value
        val target = state.presets.firstOrNull { it.id == presetId } ?: return
        if (target.baseline == null) return
        val reverted = target.revertToBaseline()
        val updatedList = state.presets.map { if (it.id == presetId) reverted else it }
        repo.savePresets(updatedList)

        val isSelected = state.selectedPresetId == presetId
        val activeAfter = if (isSelected) reverted else state.activePreset
        val curves = FrequencyResponseCalculator.calculate(activeAfter)
        _uiState.update {
            it.copy(
                presets = updatedList,
                channelSwap = activeAfter.channelSwap,
                swapPosition = activeAfter.swapPosition,
                frequencyCurves = curves,
                statusBanner = localizedStr(R.string.banner_reverted_preset, displayPresetName(reverted))
            )
        }
        if (isSelected) {
            if (state.masterPowerOn && !state.pausedByDeviceRule) {
                val deviceLabel = state.activeDevice?.displayName
                    ?: localizedStr(R.string.default_output_label)
                val swapLabel = if (reverted.channelSwap) " | L⇄R Swap ON" else ""
                PeqForegroundService.startOrUpdate(
                    appContext,
                    title = localizedStr(R.string.notif_running_title, displayPresetName(reverted)),
                    text = "$deviceLabel$swapLabel"
                )
            }
            pushLiveDspConfigOnly()
        }
    }

    fun saveAsNewPreset(
        newName: String,
        sourcePresetId: String = _uiState.value.selectedPresetId,
        revertSourceToBaseline: Boolean = false
    ) {
        val state = _uiState.value
        val source = state.presets.firstOrNull { it.id == sourcePresetId } ?: state.activePreset
        val trimmed = newName.trim().ifEmpty { localizedStr(R.string.preset_custom_fallback) }
        val newPreset = source.copy(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            updatedAtEpochMs = System.currentTimeMillis()
        ).withCapturedBaseline()

        val baseList = if (revertSourceToBaseline && source.baseline != null) {
            val revertedSource = source.revertToBaseline()
            state.presets.map { if (it.id == source.id) revertedSource else it }
        } else {
            state.presets
        }
        val updated = baseList + newPreset
        repo.savePresets(updated)
        repo.saveSelectedPresetId(newPreset.id)
        _uiState.update {
            it.copy(
                presets = updated,
                selectedPresetId = newPreset.id,
                channelSwap = newPreset.channelSwap,
                swapPosition = newPreset.swapPosition,
                frequencyCurves = FrequencyResponseCalculator.calculate(newPreset),
                statusBanner = localizedStr(R.string.banner_saved_new_preset, trimmed)
            )
        }
        if (state.masterPowerOn && !state.pausedByDeviceRule) {
            val deviceLabel = state.activeDevice?.displayName
                ?: localizedStr(R.string.default_output_label)
            val swapLabel = if (newPreset.channelSwap) " | L⇄R Swap ON" else ""
            PeqForegroundService.startOrUpdate(
                appContext,
                title = localizedStr(R.string.notif_running_title, newPreset.name),
                text = "$deviceLabel$swapLabel"
            )
        }
        pushLiveDspConfigOnly()
    }

    fun renamePreset(presetId: String, newName: String) {
        val trimmed = newName.trim().ifEmpty { return }
        val updated = _uiState.value.presets.map {
            if (it.id == presetId) it.copy(name = trimmed, updatedAtEpochMs = System.currentTimeMillis()) else it
        }
        repo.savePresets(updated)
        _uiState.update { it.copy(presets = updated) }
    }

    fun duplicatePreset(presetId: String) {
        val src = _uiState.value.presets.firstOrNull { it.id == presetId } ?: return
        val suffix = localizedStr(R.string.preset_copy_suffix)
        val copy = src.copy(
            id = UUID.randomUUID().toString(),
            name = "${displayPresetName(src)} ($suffix)",
            updatedAtEpochMs = System.currentTimeMillis()
        ).withCapturedBaseline()
        val updated = _uiState.value.presets + copy
        repo.savePresets(updated)
        _uiState.update {
            it.copy(
                presets = updated,
                statusBanner = localizedStr(R.string.banner_duplicated_preset, copy.name)
            )
        }
    }

    fun deletePreset(presetId: String) {
        val state = _uiState.value
        if (state.presets.size <= 1) {
            _uiState.update { it.copy(statusBanner = localizedStr(R.string.banner_cannot_delete_last)) }
            return
        }
        val remaining = state.presets.filterNot { it.id == presetId }
        val nextSelectedId = if (state.selectedPresetId == presetId) remaining.first().id else state.selectedPresetId
        val nextRules = state.deviceRules.map { rule ->
            if (rule.presetId == presetId) {
                rule.copy(action = DeviceRuleAction.FOLLOW_MANUAL, presetId = null)
            } else {
                rule
            }
        }
        repo.savePresets(remaining)
        repo.saveSelectedPresetId(nextSelectedId)
        repo.saveDeviceRules(nextRules)

        val nextActive = remaining.first { it.id == nextSelectedId }
        _uiState.update {
            it.copy(
                presets = remaining,
                selectedPresetId = nextSelectedId,
                channelSwap = nextActive.channelSwap,
                swapPosition = nextActive.swapPosition,
                deviceRules = nextRules,
                frequencyCurves = FrequencyResponseCalculator.calculate(nextActive),
                statusBanner = localizedStr(R.string.banner_deleted_preset)
            )
        }
        pushLiveDspConfigOnly()
    }

    fun importApoAsNewPreset(apoText: String, presetName: String): Int {
        val parsed = EqualizerApoParser.parse(apoText, presetName)
        val p = parsed.preset.withCapturedBaseline()
        val totalBands = p.commonBands.size + p.leftBands.size + p.rightBands.size
        if (totalBands == 0 && p.commonPreampDb == 0.0 && p.leftPreampDb == 0.0 && p.rightPreampDb == 0.0) {
            _uiState.update { it.copy(statusBanner = localizedStr(R.string.banner_apo_no_valid_filters)) }
            return 0
        }
        val updated = _uiState.value.presets + p
        repo.savePresets(updated)
        repo.saveSelectedPresetId(p.id)
        _uiState.update {
            it.copy(
                presets = updated,
                selectedPresetId = p.id,
                channelSwap = p.channelSwap,
                swapPosition = p.swapPosition,
                frequencyCurves = FrequencyResponseCalculator.calculate(p),
                statusBanner = localizedStr(
                    R.string.banner_apo_imported_preset,
                    p.name,
                    p.commonBands.size,
                    p.leftBands.size,
                    p.rightBands.size
                )
            )
        }
        pushLiveDspConfigOnly()
        return totalBands
    }

    fun importApoIntoCurrentTarget(apoText: String, target: PeqChannelTarget): Int {
        val (preamp, bands) = EqualizerApoParser.parseSingleSection(apoText)
        if (bands.isEmpty() && preamp == null) {
            _uiState.update { it.copy(statusBanner = localizedStr(R.string.banner_apo_no_valid_filters)) }
            return 0
        }
        updateActivePreset { preset ->
            preset.withTarget(
                target = target,
                preampDb = preamp ?: preset.preampFor(target),
                bands = bands.ifEmpty { preset.bandsFor(target) }
            )
        }
        _uiState.update {
            it.copy(
                statusBanner = localizedStr(
                    R.string.banner_apo_imported_section,
                    targetDisplayLabel(target),
                    bands.size
                )
            )
        }
        return bands.size
    }

    fun exportActivePresetApoText(): String = EqualizerApoParser.export(_uiState.value.activePreset)

    fun clearBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }
}
