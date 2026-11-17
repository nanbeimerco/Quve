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
import com.superglobalpeq.app.model.AppAudioMode
import com.superglobalpeq.app.model.AppBypassCandidate
import com.superglobalpeq.app.model.AppRuleItem
import com.superglobalpeq.app.model.AutonomousDevicePolicy
import com.superglobalpeq.app.model.BundleSelectionEvaluation
import com.superglobalpeq.app.model.clampPreampDb
import com.superglobalpeq.app.model.BundleSelectionOutcome
import com.superglobalpeq.app.model.DeviceCategory
import com.superglobalpeq.app.model.DeviceIndependentRuleAction
import com.superglobalpeq.app.model.DeviceRoutingRule
import com.superglobalpeq.app.model.DeviceRuleAction
import com.superglobalpeq.app.model.DeviceRuleEvaluationOutcome
import com.superglobalpeq.app.model.DeviceSwapRuleAction
import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.model.EqPresetBaseline
import com.superglobalpeq.app.model.FilterType
import com.superglobalpeq.app.model.IndependentPeqBaseline
import com.superglobalpeq.app.model.IndependentPeqPreset
import com.superglobalpeq.app.model.OutputDeviceSnapshot
import com.superglobalpeq.app.model.PeqBand
import com.superglobalpeq.app.model.PeqChannelTarget
import com.superglobalpeq.app.model.SwapPosition
import com.superglobalpeq.app.model.composeEffectivePreset
import com.superglobalpeq.app.model.evaluateSavedPresetSelection
import com.superglobalpeq.app.model.resolveDeviceRoutingOutcome
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
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
    val globalLowLatency: Boolean = false,
    val lowLatencyActive: Boolean = false,
    val perAppLowLatencyActive: Boolean = false,
    val lowLatencyDroppedBlocks: Long = 0L,
    val queuedFrames: Long = 0L,
    val trackBufferFrames: Int = 0,
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
    val independentPresets: List<IndependentPeqPreset> = emptyList(),
    val selectedIndependentPresetId: String? = null,
    val draftIndependentPreset: IndependentPeqPreset? = null,
    val editingTarget: PeqChannelTarget = PeqChannelTarget.COMMON,
    val channelSwap: Boolean = false,
    val swapPosition: SwapPosition = SwapPosition.BEFORE_INDEPENDENT_EQ,
    val connectedDevices: List<OutputDeviceSnapshot> = emptyList(),
    val activeDevice: OutputDeviceSnapshot? = null,
    val deviceRules: List<DeviceRoutingRule> = emptyList(),
    val globalLowLatency: Boolean = false,
    val appAudioModes: Map<String, AppAudioMode> = emptyMap(),
    val bypassedPackages: Set<String> = emptySet(),
    val appRules: List<AppRuleItem> = emptyList(),
    val backgroundPersistenceEnabled: Boolean = false,
    val frequencyCurves: FrequencyResponseCurves? = null,
    val statusBanner: String? = null
) {
    val activeCommonPreset: EqPreset
        get() = presets.firstOrNull { it.id == selectedPresetId }
            ?: presets.firstOrNull()
            ?: DefaultPresets.createFlatPreset()

    val activeSavedIndependentPreset: IndependentPeqPreset?
        get() = selectedIndependentPresetId?.let { id ->
            independentPresets.firstOrNull { it.id == id }
        }

    val activeIndependentPreset: IndependentPeqPreset?
        get() = activeSavedIndependentPreset ?: draftIndependentPreset

    val activePreset: EqPreset
        get() = composeEffectivePreset(
            commonPreset = activeCommonPreset,
            independentPreset = activeIndependentPreset,
            channelSwap = channelSwap,
            swapPosition = swapPosition
        )

    /** True when the active Common preset is a bundle whose paired L/R is currently overridden. */
    val isActiveBundleCommonOnly: Boolean
        get() {
            val linkedId = activeCommonPreset.linkedIndependentPresetId ?: return false
            if (independentPresets.none { it.id == linkedId }) return false
            return draftIndependentPreset != null || selectedIndependentPresetId != linkedId
        }
}

private const val USER_SERVICE_TAG_BOUND = "peq_dsp_bound"
private const val USER_SERVICE_TAG_DAEMON = "peq_dsp_daemon"

internal fun userServiceTagForPersistence(backgroundPersistenceEnabled: Boolean): String =
    if (backgroundPersistenceEnabled) USER_SERVICE_TAG_DAEMON else USER_SERVICE_TAG_BOUND

internal fun parseJsonIntArray(array: JSONArray?): IntArray {
    if (array == null || array.length() == 0) return IntArray(0)
    val result = IntArray(array.length())
    var count = 0
    for (i in 0 until array.length()) {
        val value = array.optInt(i, -1)
        if (value >= 0) {
            result[count++] = value
        }
    }
    return if (count == result.size) result else result.copyOf(count)
}

internal fun shouldRestartLoopback(
    runningNow: Boolean,
    restartRequested: Boolean,
    remoteUserExcludedUids: IntArray,
    desiredUserExcludedUids: IntArray,
    serviceUid: Int = -1,
    clientAppUid: Int = -1
): Boolean {
    if (!runningNow || restartRequested) return true
    val normalizedRemote = normalizeUserExcludedUids(serviceUid, clientAppUid, remoteUserExcludedUids)
    val normalizedDesired = normalizeUserExcludedUids(serviceUid, clientAppUid, desiredUserExcludedUids)
    return !normalizedRemote.contentEquals(normalizedDesired)
}

object PeqEngineManager {
    private const val TAG = "PeqEngineManager"
    private const val SHIZUKU_REQ_CODE = 2001
    private const val USER_SERVICE_PROTOCOL_VERSION = 7
    private const val SHELL_PACKAGE = "com.android.shell"
    private const val DRAFT_INDEPENDENT_ID = "__draft_lr__"

    private val initialized = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ipcDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "quve-peq-ipc").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val clientDeathToken: IBinder = Binder()
    private val rebindMutex = Mutex()
    private val rebindInProgress = AtomicBoolean(false)
    private val reconcileRemoteStatePending = AtomicBoolean(false)
    private val policySyncDirty = AtomicBoolean(false)
    private val policySyncScheduled = AtomicBoolean(false)

    private lateinit var appContext: Context
    private lateinit var repo: PeqPreferencesRepository
    private lateinit var deviceMonitor: AudioOutputDeviceMonitor

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    @Volatile
    private var remoteService: IAudioLoopbackService? = null
    @Volatile
    private var activeConnection: TrackedUserServiceConnection? = null
    @Volatile
    private var bindingInProgress = false
    @Volatile
    private var pendingStartAfterBind = false
    @Volatile
    private var pendingForceRestartAfterBind = false
    private val pendingSyncRestart = AtomicBoolean(false)
    private val syncScheduled = AtomicBoolean(false)
    private val configUpdateDirty = AtomicBoolean(false)
    private val configUpdateScheduled = AtomicBoolean(false)
    private var meterJob: Job? = null
    private var bindWatchdogJob: Job? = null
    private var lastAutoAppliedKey: String? = null

    private fun buildUserServiceArgs(daemonMode: Boolean): UserServiceArgs {
        return UserServiceArgs(
            ComponentName(appContext.packageName, AudioLoopbackUserService::class.java.name)
        )
            .daemon(daemonMode)
            .tag(userServiceTagForPersistence(daemonMode))
            .processNameSuffix("peq_dsp")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE * 100 + USER_SERVICE_PROTOCOL_VERSION)
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshShizukuStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        remoteService = null
        activeConnection?.let { it.cancelled = true }
        activeConnection = null
        bindingInProgress = false
        bindWatchdogJob?.cancel()
        bindWatchdogJob = null
        _uiState.update {
            it.copy(
                shizukuStatus = ShizukuStatus.NOT_RUNNING,
                serviceBound = false,
                telemetry = resetTelemetryState(it.telemetry, error = null)
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

    private class TrackedUserServiceConnection(
        val args: UserServiceArgs,
        val daemonMode: Boolean
    ) : ServiceConnection {
        val disconnected = CompletableDeferred<Unit>()

        @Volatile
        var cancelled: Boolean = false

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (cancelled || activeConnection !== this) {
                Log.i(TAG, "Ignoring stale UserService connection (daemon=$daemonMode)")
                return
            }
            bindingInProgress = false
            bindWatchdogJob?.cancel()
            bindWatchdogJob = null
            val stub = IAudioLoopbackService.Stub.asInterface(service)
            remoteService = stub
            Log.i(TAG, "Shizuku UserService connected (daemon=$daemonMode)")
            _uiState.update { it.copy(serviceBound = true, statusBanner = null) }
            if (stub != null) {
                scope.launch(ipcDispatcher) {
                    try {
                        // Daemon mode intentionally omits death-link termination so killing the app keeps EQ running.
                        stub.registerClientToken(if (daemonMode) null else clientDeathToken)
                    } catch (t: Throwable) {
                        handleRemoteServiceError(t, stub)
                    }
                }
            }
            val forceRestart = pendingForceRestartAfterBind
            pendingStartAfterBind = false
            pendingForceRestartAfterBind = false
            syncEngineWithState(restartLoopback = forceRestart)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            disconnected.complete(Unit)
            if (activeConnection !== this) return
            activeConnection = null
            bindingInProgress = false
            bindWatchdogJob?.cancel()
            bindWatchdogJob = null
            remoteService = null
            Log.w(TAG, "Shizuku UserService disconnected (daemon=$daemonMode)")
            _uiState.update {
                it.copy(
                    serviceBound = false,
                    telemetry = resetTelemetryState(it.telemetry, error = it.telemetry.lastError)
                )
            }
            if (!cancelled && !rebindInProgress.get()) {
                if (_uiState.value.masterPowerOn && !_uiState.value.pausedByDeviceRule) {
                    pendingStartAfterBind = true
                }
                refreshShizukuStatus()
            }
        }
    }

    private fun resetTelemetryState(prev: EngineTelemetry, error: String?): EngineTelemetry =
        prev.copy(
            isRunning = false,
            lowLatencyActive = false,
            perAppLowLatencyActive = false,
            queuedFrames = 0L,
            trackBufferFrames = 0,
            inPeakDbfs = -120.0,
            inRmsDbfs = -120.0,
            outPeakDbfs = -120.0,
            outRmsDbfs = -120.0,
            lastError = error
        )

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        appContext = context.applicationContext
        com.superglobalpeq.app.data.QuveSettings.initialize(appContext)
        repo = PeqPreferencesRepository(appContext)
        deviceMonitor = AudioOutputDeviceMonitor(appContext)

        val persisted = repo.load()
        val restoredMasterPower = persisted.backgroundPersistenceEnabled && persisted.masterPowerOn
        reconcileRemoteStatePending.set(restoredMasterPower)
        val commonPreset = persisted.presets.firstOrNull { it.id == persisted.selectedPresetId }
            ?: persisted.presets.first()
        val indepPreset = persisted.selectedIndependentPresetId?.let { id ->
            persisted.independentPresets.firstOrNull { it.id == id }
        }
        val effectivePreset = composeEffectivePreset(
            commonPreset = commonPreset,
            independentPreset = indepPreset,
            channelSwap = persisted.channelSwap,
            swapPosition = persisted.swapPosition
        )
        val curves = FrequencyResponseCalculator.calculate(effectivePreset)

        _uiState.value = AppUiState(
            masterPowerOn = restoredMasterPower,
            presets = persisted.presets,
            selectedPresetId = commonPreset.id,
            independentPresets = persisted.independentPresets,
            selectedIndependentPresetId = indepPreset?.id,
            channelSwap = persisted.channelSwap,
            swapPosition = persisted.swapPosition,
            deviceRules = persisted.deviceRules,
            globalLowLatency = persisted.globalLowLatency,
            appAudioModes = persisted.appAudioModes,
            bypassedPackages = persisted.bypassedPackages,
            appRules = resolveAppRuleItems(persisted.appAudioModes),
            backgroundPersistenceEnabled = persisted.backgroundPersistenceEnabled,
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
            val modes = _uiState.value.appAudioModes
            if (changedPkg !in modes) return
            val isBypassed = modes[changedPkg] == AppAudioMode.BYPASS
            _uiState.update {
                it.copy(appRules = resolveAppRuleItems(it.appAudioModes))
            }
            if (_uiState.value.masterPowerOn && !_uiState.value.pausedByDeviceRule) {
                syncEngineWithState(restartLoopback = isBypassed)
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
        if (status == ShizukuStatus.READY && remoteService == null && !bindingInProgress && !rebindInProgress.get()) {
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
        if (rebindInProgress.get() || bindingInProgress || !Shizuku.pingBinder()) return
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return
        val daemonMode = _uiState.value.backgroundPersistenceEnabled
        val args = buildUserServiceArgs(daemonMode)
        val conn = TrackedUserServiceConnection(args = args, daemonMode = daemonMode)
        activeConnection = conn
        bindingInProgress = true
        bindWatchdogJob?.cancel()
        bindWatchdogJob = scope.launch {
            delay(4000L)
            if (bindingInProgress && activeConnection === conn && remoteService == null) {
                Log.w(TAG, "bindUserService timed out waiting for Shizuku connection; resetting bind state")
                bindingInProgress = false
                if (activeConnection === conn) {
                    activeConnection = null
                    conn.cancelled = true
                }
            }
        }
        try {
            Shizuku.bindUserService(args, conn)
        } catch (t: Throwable) {
            bindingInProgress = false
            bindWatchdogJob?.cancel()
            bindWatchdogJob = null
            if (activeConnection === conn) {
                activeConnection = null
            }
            conn.cancelled = true
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
        reconcileRemoteStatePending.set(false)
        val state = _uiState.value
        repo.saveMasterPowerOn(if (state.backgroundPersistenceEnabled) enabled else false)
        if (enabled && remoteService == null) {
            pendingForceRestartAfterBind = true
        }
        _uiState.update { it.copy(masterPowerOn = enabled, statusBanner = null) }
        evaluateActiveDeviceRuleAndApply(forceRestart = true)
    }

    fun setBackgroundPersistenceEnabled(enabled: Boolean) {
        if (!initialized.get()) return
        if (_uiState.value.backgroundPersistenceEnabled == enabled) return
        reconcileRemoteStatePending.set(false)
        repo.saveBackgroundPersistenceEnabled(enabled)
        repo.saveMasterPowerOn(if (enabled) _uiState.value.masterPowerOn else false)
        _uiState.update {
            it.copy(
                backgroundPersistenceEnabled = enabled,
                statusBanner = localizedStr(
                    if (enabled) R.string.banner_background_persistence_on
                    else R.string.banner_background_persistence_off
                )
            )
        }
        syncForegroundServiceForState(_uiState.value)
        rebindUserServiceForModeChange()
    }

    fun shouldKeepServiceSticky(): Boolean {
        val state = _uiState.value
        return state.backgroundPersistenceEnabled && state.masterPowerOn
    }

    fun buildForegroundNotificationContent(): Pair<String, String>? =
        buildForegroundNotificationPair(_uiState.value)

    fun onForegroundServiceRestored() {
        if (!initialized.get()) return
        deviceMonitor.refreshDevices()
        refreshShizukuStatus()
        if (_uiState.value.masterPowerOn) {
            syncEngineWithState(restartLoopback = false)
        }
    }

    private fun rebindUserServiceForModeChange() {
        scope.launch(ipcDispatcher) {
            rebindMutex.withLock {
                val targetDaemon = _uiState.value.backgroundPersistenceEnabled
                val oldConn = activeConnection
                val oldSvc = remoteService
                if (oldConn != null && oldConn.daemonMode == targetDaemon && (oldSvc != null || bindingInProgress)) {
                    scheduleAutoDevicePolicySync()
                    return@withLock
                }
                if (oldConn != null) {
                    rebindInProgress.set(true)
                    try {
                        oldConn.cancelled = true
                        activeConnection = null
                        remoteService = null
                        bindingInProgress = false
                        bindWatchdogJob?.cancel()
                        bindWatchdogJob = null
                        _uiState.update {
                            it.copy(
                                serviceBound = false,
                                telemetry = resetTelemetryState(it.telemetry, error = it.telemetry.lastError)
                            )
                        }
                        if (oldSvc != null) {
                            runCatching { oldSvc.stopLoopback() }
                        }
                        withContext(Dispatchers.Main.immediate) {
                            runCatching {
                                // Option A: remove old UserService entry before destroy() so Shizuku does not auto-respawn a daemon.
                                Shizuku.unbindUserService(oldConn.args, oldConn, true)
                            }
                        }
                        if (oldSvc != null) {
                            runCatching { oldSvc.destroy() }
                        }
                        withTimeoutOrNull(700L) {
                            oldConn.disconnected.await()
                        }
                        // Allow Shizuku MAIN_HANDLER cache eviction to settle before rebinding.
                        delay(60L)
                    } finally {
                        rebindInProgress.set(false)
                    }
                }
                val latestState = _uiState.value
                if (latestState.masterPowerOn && !latestState.pausedByDeviceRule) {
                    pendingStartAfterBind = true
                    pendingForceRestartAfterBind = true
                }
                withContext(Dispatchers.Main.immediate) {
                    refreshShizukuStatus()
                }
            }
        }
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
                globalLowLatency = obj.optBoolean("globalLowLatency", false),
                lowLatencyActive = obj.optBoolean("lowLatencyActive", false),
                perAppLowLatencyActive = obj.optBoolean("perAppLowLatencyActive", false),
                lowLatencyDroppedBlocks = obj.optLong("lowLatencyDroppedBlocks", 0L),
                queuedFrames = obj.optLong("queuedFrames", 0L),
                trackBufferFrames = obj.optInt("trackBufferFrames", 0),
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
            activeConnection?.let { it.cancelled = true }
            activeConnection = null
            bindingInProgress = false
            bindWatchdogJob?.cancel()
            bindWatchdogJob = null
        }
        _uiState.update {
            it.copy(
                serviceBound = false,
                telemetry = resetTelemetryState(
                    it.telemetry,
                    error = t.message ?: t.javaClass.simpleName
                )
            )
        }
        scope.launch(Dispatchers.Main.immediate) {
            if (rebindInProgress.get()) return@launch
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
        } else if (rulesChanged) {
            scheduleAutoDevicePolicySync()
        }
    }

    fun updateDeviceRule(
        deviceKey: String,
        action: DeviceRuleAction,
        presetId: String?,
        independentAction: DeviceIndependentRuleAction = DeviceIndependentRuleAction.KEEP_CURRENT,
        independentPresetId: String? = null,
        channelSwapAction: DeviceSwapRuleAction = DeviceSwapRuleAction.FOLLOW_MANUAL
    ) {
        reconcileRemoteStatePending.set(false)
        val rules = _uiState.value.deviceRules.map { rule ->
            if (rule.deviceKey == deviceKey) {
                val effectiveIndepAction = if (action == DeviceRuleAction.PRESET) {
                    independentAction
                } else {
                    DeviceIndependentRuleAction.KEEP_CURRENT
                }
                rule.copy(
                    action = action,
                    presetId = if (action == DeviceRuleAction.PRESET) presetId else null,
                    independentAction = effectiveIndepAction,
                    independentPresetId = if (
                        action == DeviceRuleAction.PRESET &&
                        effectiveIndepAction == DeviceIndependentRuleAction.APPLY_PRESET
                    ) {
                        independentPresetId
                    } else {
                        null
                    },
                    channelSwapAction = if (action == DeviceRuleAction.EQ_OFF) {
                        DeviceSwapRuleAction.FOLLOW_MANUAL
                    } else {
                        channelSwapAction
                    }
                )
            } else {
                rule
            }
        }
        repo.saveDeviceRules(rules)
        _uiState.update { it.copy(deviceRules = rules) }
        if (_uiState.value.activeDevice?.deviceKey == deviceKey) {
            evaluateActiveDeviceRuleAndApply(forceRestart = true)
        } else {
            scheduleAutoDevicePolicySync()
        }
    }

    fun removeDeviceRule(deviceKey: String) {
        if (deviceKey == "speaker") return
        val rules = _uiState.value.deviceRules.filterNot { it.deviceKey == deviceKey }
        repo.saveDeviceRules(rules)
        _uiState.update { it.copy(deviceRules = rules) }
        scheduleAutoDevicePolicySync()
    }

    private fun evaluateActiveDeviceRuleAndApply(forceRestart: Boolean) {
        val state = _uiState.value
        val activeDev = state.activeDevice
        val outcome = resolveDeviceRoutingOutcome(
            deviceKey = activeDev?.deviceKey,
            deviceRules = state.deviceRules,
            presets = state.presets,
            independentPresets = state.independentPresets,
            currentCommonPresetId = state.selectedPresetId,
            currentIndependentPresetId = state.selectedIndependentPresetId,
            draftIndependentPreset = state.draftIndependentPreset,
            currentChannelSwap = state.channelSwap,
            swapPosition = state.swapPosition
        )

        if (outcome.channelSwapChanged) {
            repo.saveChannelSwap(outcome.channelSwap, state.swapPosition)
        }

        val rule = outcome.matchedRule
        var banner = state.statusBanner
        if (rule != null) {
            when (rule.action) {
                DeviceRuleAction.FOLLOW_MANUAL -> Unit
                DeviceRuleAction.EQ_OFF -> {
                    banner = localizedStr(R.string.banner_device_eq_off, rule.displayName)
                }
                DeviceRuleAction.PRESET -> {
                    val appliedNames = mutableListOf<String>()
                    rule.presetId?.let { targetId ->
                        val targetCommon = state.presets.firstOrNull { it.id == targetId }
                        if (targetCommon != null) {
                            repo.saveSelectedPresetId(outcome.selectedCommonPresetId)
                            appliedNames.add(displayPresetName(targetCommon))
                        }
                    }
                    when (rule.independentAction) {
                        DeviceIndependentRuleAction.KEEP_CURRENT -> Unit
                        DeviceIndependentRuleAction.CLEAR_NONE -> {
                            repo.saveSelectedIndependentPresetId(null)
                            appliedNames.add(localizedStr(R.string.lr_preset_none_short))
                        }
                        DeviceIndependentRuleAction.APPLY_PRESET -> {
                            val targetIndep = rule.independentPresetId?.let { id ->
                                state.independentPresets.firstOrNull { it.id == id }
                            }
                            if (targetIndep != null) {
                                repo.saveSelectedIndependentPresetId(outcome.selectedIndependentPresetId)
                                appliedNames.add("L/R: ${targetIndep.name}")
                            }
                        }
                    }
                    if (appliedNames.isNotEmpty()) {
                        banner = localizedStr(
                            R.string.banner_device_auto_preset,
                            rule.displayName,
                            appliedNames.joinToString(" + ")
                        )
                    } else if (rule.channelSwapAction != DeviceSwapRuleAction.FOLLOW_MANUAL) {
                        banner = localizedStr(
                            R.string.banner_device_auto_preset,
                            rule.displayName,
                            if (outcome.channelSwap) "L⇄R ON" else "L⇄R OFF"
                        )
                    }
                }
            }
        }

        val curves = FrequencyResponseCalculator.calculate(outcome.effectivePreset)
        _uiState.update {
            it.copy(
                selectedPresetId = outcome.selectedCommonPresetId,
                selectedIndependentPresetId = outcome.selectedIndependentPresetId,
                draftIndependentPreset = outcome.draftIndependentPreset,
                channelSwap = outcome.channelSwap,
                pausedByDeviceRule = outcome.pausedByDeviceRule,
                frequencyCurves = curves,
                statusBanner = if (!outcome.pausedByDeviceRule && rule?.action == DeviceRuleAction.FOLLOW_MANUAL) null else banner
            )
        }

        syncEngineWithState(restartLoopback = forceRestart)
    }

    private fun buildForegroundNotificationPair(state: AppUiState): Pair<String, String>? {
        if (!state.masterPowerOn) return null
        if (state.pausedByDeviceRule) {
            val deviceLabel = state.activeDevice?.displayName
                ?: localizedStr(R.string.connected_device_label)
            return localizedStr(R.string.notif_paused_title) to
                localizedStr(R.string.notif_paused_text, deviceLabel)
        }
        val deviceLabel = state.activeDevice?.displayName
            ?: localizedStr(R.string.default_output_label)
        val commonName = displayPresetName(state.activeCommonPreset)
        val titlePreset = state.activeIndependentPreset?.let { indep ->
            "$commonName + L/R: ${indep.name}"
        } ?: commonName
        val swapLabel = if (state.channelSwap) " | L⇄R Swap ON" else ""
        val latencyLabel = if (state.globalLowLatency) " | ${localizedStr(R.string.low_latency_short_label)}" else ""
        return localizedStr(R.string.notif_running_title, titlePreset) to
            "$deviceLabel$swapLabel$latencyLabel"
    }

    private fun syncForegroundServiceForState(state: AppUiState = _uiState.value) {
        val content = buildForegroundNotificationPair(state)
        if (content != null) {
            PeqForegroundService.startOrUpdate(
                appContext,
                title = content.first,
                text = content.second
            )
        } else {
            PeqForegroundService.stop(appContext)
        }
    }

    private fun updateForegroundRunningNotification(state: AppUiState = _uiState.value) {
        if (!state.masterPowerOn || state.pausedByDeviceRule) return
        val content = buildForegroundNotificationPair(state) ?: return
        PeqForegroundService.startOrUpdate(
            appContext,
            title = content.first,
            text = content.second
        )
    }

    private fun syncEngineWithState(restartLoopback: Boolean) {
        val state = _uiState.value
        syncForegroundServiceForState(state)

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

    private fun buildAutoDevicePolicyJson(state: AppUiState): String {
        val enabled = state.backgroundPersistenceEnabled && state.masterPowerOn
        if (!enabled) {
            return AutonomousDevicePolicy(enabled = false).toJsonString()
        }
        val excludedUids = resolveUidsForMode(state.appAudioModes, AppAudioMode.BYPASS).toList()
        val lowLatencyUids = resolveUidsForMode(state.appAudioModes, AppAudioMode.LOW_LATENCY).toList()
        return AutonomousDevicePolicy(
            enabled = true,
            clientAppUid = Process.myUid(),
            excludedUids = excludedUids,
            globalLowLatency = state.globalLowLatency,
            lowLatencyUids = lowLatencyUids,
            presets = state.presets,
            independentPresets = state.independentPresets,
            deviceRules = state.deviceRules,
            selectedPresetId = state.selectedPresetId,
            selectedIndependentPresetId = state.selectedIndependentPresetId,
            draftIndependentPreset = state.draftIndependentPreset,
            channelSwap = state.channelSwap,
            swapPosition = state.swapPosition,
            pausedByDeviceRule = state.pausedByDeviceRule,
            activeDeviceKey = state.activeDevice?.deviceKey
        ).toJsonString()
    }

    private fun scheduleAutoDevicePolicySync() {
        policySyncDirty.set(true)
        if (!policySyncScheduled.compareAndSet(false, true)) return
        scope.launch(ipcDispatcher) {
            policySyncScheduled.set(false)
            if (!policySyncDirty.getAndSet(false)) return@launch
            val state = _uiState.value
            val svc = remoteService ?: return@launch
            if (reconcileRemoteStatePending.get()) return@launch
            try {
                svc.updateAutoDevicePolicy(buildAutoDevicePolicyJson(state))
            } catch (t: Throwable) {
                handleRemoteServiceError(t, svc)
            }
        }
    }

    private fun reconcileStateFromRemoteStatus(statusObj: JSONObject): Boolean {
        val switchCount = statusObj.optInt("autonomousSwitchCount", 0)
        if (switchCount <= 0) return false
        val state = _uiState.value
        val remoteCommonId = statusObj.optString("commonPresetId", "")
            .takeIf { id -> id.isNotEmpty() && state.presets.any { it.id == id } }
            ?: state.selectedPresetId
        val remoteIndepId = if (statusObj.isNull("independentPresetId")) {
            null
        } else {
            statusObj.optString("independentPresetId", "")
                .takeIf { id -> id.isNotEmpty() && state.independentPresets.any { it.id == id } }
        }
        val remoteHasDraft = statusObj.optBoolean("hasDraftIndependent", false)
        val remoteDraftIndep = if (remoteIndepId == null && remoteHasDraft) state.draftIndependentPreset else null
        val remoteSwap = statusObj.optBoolean("channelSwap", state.channelSwap)
        val remotePaused = statusObj.optBoolean("pausedByDeviceRule", state.pausedByDeviceRule)
        val remoteDeviceKey = if (statusObj.isNull("activeDeviceKey")) {
            null
        } else {
            statusObj.optString("activeDeviceKey").takeIf { it.isNotEmpty() }
        }

        val currentActiveKey = state.activeDevice?.deviceKey
        val outcome = if (currentActiveKey != null && currentActiveKey != remoteDeviceKey) {
            resolveDeviceRoutingOutcome(
                deviceKey = currentActiveKey,
                deviceRules = state.deviceRules,
                presets = state.presets,
                independentPresets = state.independentPresets,
                currentCommonPresetId = remoteCommonId,
                currentIndependentPresetId = remoteIndepId,
                draftIndependentPreset = remoteDraftIndep,
                currentChannelSwap = remoteSwap,
                swapPosition = state.swapPosition
            )
        } else {
            val commonPreset = state.presets.firstOrNull { it.id == remoteCommonId } ?: state.activeCommonPreset
            val indepPreset = remoteIndepId?.let { id ->
                state.independentPresets.firstOrNull { it.id == id }
            } ?: remoteDraftIndep
            DeviceRuleEvaluationOutcome(
                matchedRule = currentActiveKey?.let { key -> state.deviceRules.firstOrNull { it.deviceKey == key } },
                selectedCommonPresetId = commonPreset.id,
                selectedIndependentPresetId = remoteIndepId,
                draftIndependentPreset = remoteDraftIndep,
                channelSwap = remoteSwap,
                swapPosition = state.swapPosition,
                pausedByDeviceRule = remotePaused,
                commonChanged = commonPreset.id != state.selectedPresetId,
                independentChanged = remoteIndepId != state.selectedIndependentPresetId,
                channelSwapChanged = remoteSwap != state.channelSwap,
                clearedDraftIndependent = !remoteHasDraft && state.draftIndependentPreset != null,
                effectivePreset = composeEffectivePreset(commonPreset, indepPreset, remoteSwap, state.swapPosition)
            )
        }

        repo.saveSelectedPresetId(outcome.selectedCommonPresetId)
        repo.saveSelectedIndependentPresetId(outcome.selectedIndependentPresetId)
        if (outcome.channelSwap != state.channelSwap) {
            repo.saveChannelSwap(outcome.channelSwap, state.swapPosition)
        }
        val curves = FrequencyResponseCalculator.calculate(outcome.effectivePreset)
        _uiState.update {
            it.copy(
                selectedPresetId = outcome.selectedCommonPresetId,
                selectedIndependentPresetId = outcome.selectedIndependentPresetId,
                draftIndependentPreset = outcome.draftIndependentPreset,
                channelSwap = outcome.channelSwap,
                pausedByDeviceRule = outcome.pausedByDeviceRule,
                frequencyCurves = curves
            )
        }
        lastAutoAppliedKey = currentActiveKey ?: remoteDeviceKey
        return true
    }

    private suspend fun executeEngineSync(restartNow: Boolean) {
        var state = _uiState.value
        val svc = remoteService
        if (svc == null) {
            if (state.masterPowerOn && !state.pausedByDeviceRule) {
                if (restartNow) {
                    pendingSyncRestart.set(true)
                }
                pendingStartAfterBind = true
                withContext(Dispatchers.Main.immediate) {
                    bindUserService()
                }
            } else {
                pendingStartAfterBind = false
                pendingForceRestartAfterBind = false
                _uiState.update {
                    it.copy(
                        telemetry = resetTelemetryState(it.telemetry, error = it.telemetry.lastError)
                    )
                }
            }
            return
        }

        try {
            val statusObj = try {
                JSONObject(svc.statusJson)
            } catch (t: Throwable) {
                if (t is DeadObjectException || t is RemoteException) {
                    throw t
                }
                JSONObject()
            }

            if (reconcileRemoteStatePending.compareAndSet(true, false)) {
                if (reconcileStateFromRemoteStatus(statusObj)) {
                    state = _uiState.value
                    withContext(Dispatchers.Main.immediate) {
                        syncForegroundServiceForState(state)
                    }
                }
            }

            val shouldRunLoopback = state.masterPowerOn && !state.pausedByDeviceRule
            svc.updateAutoDevicePolicy(buildAutoDevicePolicyJson(state))

            if (shouldRunLoopback) {
                val activePreset = state.activePreset
                val config = ActiveDspConfig(
                    bypass = false,
                    channelSwap = activePreset.channelSwap,
                    swapPosition = activePreset.swapPosition,
                    preset = activePreset
                )
                val runningNow = statusObj.optBoolean("running", false)
                val remoteUid = statusObj.optInt("uid", -1)
                val remoteExcludedUids = parseJsonIntArray(statusObj.optJSONArray("userExcludedUids"))
                val desiredExcludedUids = resolveUidsForMode(state.appAudioModes, AppAudioMode.BYPASS)
                val lowLatencyUids = resolveUidsForMode(state.appAudioModes, AppAudioMode.LOW_LATENCY)
                if (
                    shouldRestartLoopback(
                        runningNow = runningNow,
                        restartRequested = restartNow,
                        remoteUserExcludedUids = remoteExcludedUids,
                        desiredUserExcludedUids = desiredExcludedUids,
                        serviceUid = remoteUid,
                        clientAppUid = Process.myUid()
                    )
                ) {
                    svc.startLoopback(
                        Process.myUid(),
                        desiredExcludedUids,
                        state.globalLowLatency,
                        lowLatencyUids,
                        config.toJsonString()
                    )
                } else {
                    svc.updateDspConfig(config.toJsonString())
                    svc.updateLatencyPolicy(state.globalLowLatency, lowLatencyUids)
                }
                pollTelemetryFromService(svc)
            } else {
                pendingStartAfterBind = false
                pendingForceRestartAfterBind = false
                svc.stopLoopback()
                _uiState.update {
                    it.copy(
                        telemetry = resetTelemetryState(it.telemetry, error = it.telemetry.lastError)
                    )
                }
            }
        } catch (t: Throwable) {
            handleRemoteServiceError(t, svc)
            if (t !is DeadObjectException && t !is RemoteException) {
                Log.e(TAG, "syncEngineWithState IO error", t)
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
            if (!state.masterPowerOn) return@launch
            try {
                if (!state.pausedByDeviceRule) {
                    val preset = state.activePreset
                    val configJson = ActiveDspConfig(
                        bypass = false,
                        channelSwap = preset.channelSwap,
                        swapPosition = preset.swapPosition,
                        preset = preset
                    ).toJsonString()
                    svc.updateDspConfig(configJson)
                }
                if (state.backgroundPersistenceEnabled && !reconcileRemoteStatePending.get()) {
                    svc.updateAutoDevicePolicy(buildAutoDevicePolicyJson(state))
                }
            } catch (t: Throwable) {
                handleRemoteServiceError(t, svc)
            }
        }
    }

    fun onAppLocaleChanged() {
        if (!initialized.get()) return
        deviceMonitor.refreshDevices()
        val state = _uiState.value
        _uiState.update { it.copy(appRules = resolveAppRuleItems(it.appAudioModes)) }
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

    fun setGlobalLowLatency(enabled: Boolean) {
        if (_uiState.value.globalLowLatency == enabled) return
        repo.saveGlobalLowLatency(enabled)
        _uiState.update {
            it.copy(
                globalLowLatency = enabled,
                statusBanner = localizedStr(
                    if (enabled) R.string.banner_global_low_latency_on else R.string.banner_global_low_latency_off
                )
            )
        }
        syncEngineWithState(restartLoopback = false)
    }

    fun setAppAudioModes(modes: Map<String, AppAudioMode>) {
        val selfPkg = appContext.packageName
        val cleaned = PeqPreferencesRepository.normalizeAppAudioModes(
            modes.filterKeys { pkg ->
                val trimmed = pkg.trim()
                trimmed.isNotEmpty() && trimmed != selfPkg && trimmed != SHELL_PACKAGE
            }
        )
        val oldModes = _uiState.value.appAudioModes
        if (cleaned == oldModes) return

        val oldBypassed = oldModes.filterValues { it == AppAudioMode.BYPASS }.keys
        val newBypassed = cleaned.filterValues { it == AppAudioMode.BYPASS }.keys
        val bypassChanged = oldBypassed != newBypassed

        repo.saveAppAudioModes(cleaned)
        val items = resolveAppRuleItems(cleaned)
        val banner = if (cleaned.isEmpty()) {
            localizedStr(R.string.banner_app_rules_cleared)
        } else {
            localizedStr(R.string.banner_app_rules_updated, cleaned.size)
        }
        _uiState.update {
            it.copy(
                appAudioModes = cleaned,
                bypassedPackages = newBypassed,
                appRules = items,
                statusBanner = banner
            )
        }
        syncEngineWithState(restartLoopback = bypassChanged)
    }

    fun setAppAudioMode(packageName: String, mode: AppAudioMode) {
        val next = _uiState.value.appAudioModes.toMutableMap()
        if (mode == AppAudioMode.STANDARD) {
            next.remove(packageName)
        } else {
            next[packageName] = mode
        }
        setAppAudioModes(next)
    }

    fun clearAppAudioModes() {
        setAppAudioModes(emptyMap())
    }

    private fun resolveUidsForMode(modes: Map<String, AppAudioMode>, targetMode: AppAudioMode): IntArray {
        val packages = modes.filterValues { it == targetMode }.keys
        if (packages.isEmpty()) return IntArray(0)
        val pm = appContext.packageManager
        val selfUid = Process.myUid()
        val uids = LinkedHashSet<Int>(packages.size)
        for (pkg in packages) {
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

    private fun resolveAppRuleItems(modes: Map<String, AppAudioMode>): List<AppRuleItem> {
        if (modes.isEmpty()) return emptyList()
        val pm = appContext.packageManager
        return modes.entries.asSequence()
            .filter { it.value != AppAudioMode.STANDARD }
            .map { (pkg, mode) ->
                try {
                    val info = pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
                    val label = pm.getApplicationLabel(info).toString().trim().ifEmpty { pkg }
                    AppRuleItem(
                        packageName = pkg,
                        displayName = label,
                        uid = info.uid,
                        isInstalled = true,
                        mode = mode
                    )
                } catch (_: Throwable) {
                    AppRuleItem(
                        packageName = pkg,
                        displayName = pkg,
                        uid = null,
                        isInstalled = false,
                        mode = mode
                    )
                }
            }
            .sortedWith(
                compareBy<AppRuleItem> { !it.isInstalled }
                    .thenBy { it.mode != AppAudioMode.LOW_LATENCY }
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

    data class ApoImportResult(
        val filtersImported: Int,
        val importedPreset: EqPreset? = null,
        val selectionEvaluation: BundleSelectionEvaluation? = null
    )

    fun selectEditingTarget(target: PeqChannelTarget) {
        _uiState.update { it.copy(editingTarget = target) }
    }

    fun evaluateSavedPresetClick(presetId: String): BundleSelectionEvaluation {
        val state = _uiState.value
        val preset = state.presets.firstOrNull { it.id == presetId }
            ?: return BundleSelectionEvaluation(BundleSelectionOutcome.APPLY_COMMON_ONLY, null)
        return evaluateSavedPresetSelection(
            chosenPreset = preset,
            activeIndependentId = state.selectedIndependentPresetId,
            hasDraftIndependent = state.draftIndependentPreset != null,
            independentPresets = state.independentPresets
        )
    }

    fun selectSavedPreset(presetId: String, applyIndependentToo: Boolean) {
        reconcileRemoteStatePending.set(false)
        val state = _uiState.value
        val commonPreset = state.presets.firstOrNull { it.id == presetId } ?: return
        repo.saveSelectedPresetId(commonPreset.id)

        val linkedIndep = if (applyIndependentToo) {
            commonPreset.linkedIndependentPresetId?.let { linkedId ->
                state.independentPresets.firstOrNull { it.id == linkedId }
            }
        } else {
            null
        }

        val nextIndepId = if (linkedIndep != null) {
            repo.saveSelectedIndependentPresetId(linkedIndep.id)
            linkedIndep.id
        } else {
            state.selectedIndependentPresetId
        }
        val nextDraft = if (linkedIndep != null) null else state.draftIndependentPreset
        val effectiveIndep = if (linkedIndep != null) linkedIndep else state.activeIndependentPreset
        val effectivePreset = composeEffectivePreset(
            commonPreset = commonPreset,
            independentPreset = effectiveIndep,
            channelSwap = state.channelSwap,
            swapPosition = state.swapPosition
        )
        val curves = FrequencyResponseCalculator.calculate(effectivePreset)
        val shownName = displayPresetName(commonPreset)
        val banner = if (linkedIndep != null) {
            localizedStr(R.string.banner_bundle_selected, shownName, linkedIndep.name)
        } else {
            localizedStr(R.string.banner_preset_selected, shownName)
        }

        _uiState.update {
            it.copy(
                selectedPresetId = commonPreset.id,
                selectedIndependentPresetId = nextIndepId,
                draftIndependentPreset = nextDraft,
                frequencyCurves = curves,
                statusBanner = banner
            )
        }
        updateForegroundRunningNotification()
        pushLiveDspConfigOnly()
    }

    fun selectIndependentPreset(independentPresetId: String?) {
        reconcileRemoteStatePending.set(false)
        val state = _uiState.value
        val indep = if (independentPresetId == null) {
            null
        } else {
            state.independentPresets.firstOrNull { it.id == independentPresetId } ?: return
        }
        repo.saveSelectedIndependentPresetId(indep?.id)

        val effectivePreset = composeEffectivePreset(
            commonPreset = state.activeCommonPreset,
            independentPreset = indep,
            channelSwap = state.channelSwap,
            swapPosition = state.swapPosition
        )
        val curves = FrequencyResponseCalculator.calculate(effectivePreset)
        val banner = if (indep == null) {
            localizedStr(R.string.banner_lr_cleared)
        } else {
            localizedStr(R.string.banner_lr_selected, indep.name)
        }

        _uiState.update {
            it.copy(
                selectedIndependentPresetId = indep?.id,
                draftIndependentPreset = null,
                frequencyCurves = curves,
                statusBanner = banner
            )
        }
        updateForegroundRunningNotification()
        pushLiveDspConfigOnly()
    }

    private fun updateActiveCommonPreset(transform: (EqPreset) -> EqPreset) {
        reconcileRemoteStatePending.set(false)
        val state = _uiState.value
        val updatedCommon = transform(state.activeCommonPreset).copy(updatedAtEpochMs = System.currentTimeMillis())
        val updatedList = state.presets.map { if (it.id == updatedCommon.id) updatedCommon else it }
        repo.savePresets(updatedList)
        val effectivePreset = composeEffectivePreset(
            commonPreset = updatedCommon,
            independentPreset = state.activeIndependentPreset,
            channelSwap = state.channelSwap,
            swapPosition = state.swapPosition
        )
        val curves = FrequencyResponseCalculator.calculate(effectivePreset)
        _uiState.update {
            it.copy(
                presets = updatedList,
                frequencyCurves = curves
            )
        }
        pushLiveDspConfigOnly()
    }

    private fun updateActiveIndependentPreset(transform: (IndependentPeqPreset) -> IndependentPeqPreset) {
        reconcileRemoteStatePending.set(false)
        val state = _uiState.value
        val savedId = state.selectedIndependentPresetId
        val savedTarget = savedId?.let { id -> state.independentPresets.firstOrNull { it.id == id } }
        if (savedTarget != null) {
            val updatedIndep = transform(savedTarget).copy(updatedAtEpochMs = System.currentTimeMillis())
            val updatedList = state.independentPresets.map { if (it.id == updatedIndep.id) updatedIndep else it }
            repo.saveIndependentPresets(updatedList)
            val effectivePreset = composeEffectivePreset(
                commonPreset = state.activeCommonPreset,
                independentPreset = updatedIndep,
                channelSwap = state.channelSwap,
                swapPosition = state.swapPosition
            )
            val curves = FrequencyResponseCalculator.calculate(effectivePreset)
            _uiState.update {
                it.copy(
                    independentPresets = updatedList,
                    frequencyCurves = curves
                )
            }
        } else {
            val seed = state.draftIndependentPreset ?: IndependentPeqPreset(
                id = DRAFT_INDEPENDENT_ID,
                name = localizedStr(R.string.lr_draft_default_name)
            )
            val updatedDraft = transform(seed).copy(updatedAtEpochMs = System.currentTimeMillis())
            val finalDraft = if (updatedDraft.hasAnyFilterOrGain) updatedDraft else null
            val effectivePreset = composeEffectivePreset(
                commonPreset = state.activeCommonPreset,
                independentPreset = finalDraft,
                channelSwap = state.channelSwap,
                swapPosition = state.swapPosition
            )
            val curves = FrequencyResponseCalculator.calculate(effectivePreset)
            _uiState.update {
                it.copy(
                    draftIndependentPreset = finalDraft,
                    frequencyCurves = curves
                )
            }
        }
        pushLiveDspConfigOnly()
    }

    fun setTargetPreamp(target: PeqChannelTarget, preampDb: Double) {
        val clamped = clampPreampDb(preampDb)
        if (target == PeqChannelTarget.COMMON) {
            updateActiveCommonPreset { it.withTarget(PeqChannelTarget.COMMON, preampDb = clamped) }
        } else {
            updateActiveIndependentPreset { it.withTarget(target, preampDb = clamped) }
        }
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
        if (target == PeqChannelTarget.COMMON) {
            updateActiveCommonPreset { preset ->
                preset.withTarget(PeqChannelTarget.COMMON, bands = preset.commonBands + newBand)
            }
        } else {
            updateActiveIndependentPreset { indep ->
                indep.withTarget(target, bands = indep.bandsFor(target) + newBand)
            }
        }
    }

    fun updateBand(target: PeqChannelTarget, updatedBand: PeqBand) {
        if (target == PeqChannelTarget.COMMON) {
            updateActiveCommonPreset { preset ->
                val list = preset.commonBands.map { if (it.id == updatedBand.id) updatedBand else it }
                preset.withTarget(PeqChannelTarget.COMMON, bands = list)
            }
        } else {
            updateActiveIndependentPreset { indep ->
                val list = indep.bandsFor(target).map { if (it.id == updatedBand.id) updatedBand else it }
                indep.withTarget(target, bands = list)
            }
        }
    }

    fun removeBand(target: PeqChannelTarget, bandId: String) {
        if (target == PeqChannelTarget.COMMON) {
            updateActiveCommonPreset { preset ->
                val list = preset.commonBands.filterNot { it.id == bandId }
                preset.withTarget(PeqChannelTarget.COMMON, bands = list)
            }
        } else {
            updateActiveIndependentPreset { indep ->
                val list = indep.bandsFor(target).filterNot { it.id == bandId }
                indep.withTarget(target, bands = list)
            }
        }
    }

    fun setChannelSwap(enabled: Boolean, position: SwapPosition = _uiState.value.swapPosition) {
        reconcileRemoteStatePending.set(false)
        val state = _uiState.value
        repo.saveChannelSwap(enabled, position)
        val effectivePreset = composeEffectivePreset(
            commonPreset = state.activeCommonPreset,
            independentPreset = state.activeIndependentPreset,
            channelSwap = enabled,
            swapPosition = position
        )
        val curves = FrequencyResponseCalculator.calculate(effectivePreset)
        _uiState.update {
            it.copy(
                channelSwap = enabled,
                swapPosition = position,
                frequencyCurves = curves
            )
        }
        updateForegroundRunningNotification()
        pushLiveDspConfigOnly()
    }

    fun revertPresetToBaseline(presetId: String = _uiState.value.selectedPresetId) {
        val state = _uiState.value
        val target = state.presets.firstOrNull { it.id == presetId } ?: return
        if (target.baseline == null) return
        val reverted = target.revertCommonToBaseline()
        val updatedList = state.presets.map { if (it.id == presetId) reverted else it }
        repo.savePresets(updatedList)

        val isSelected = state.selectedPresetId == presetId
        val nextCommon = if (isSelected) reverted else state.activeCommonPreset
        val effectivePreset = composeEffectivePreset(
            commonPreset = nextCommon,
            independentPreset = state.activeIndependentPreset,
            channelSwap = state.channelSwap,
            swapPosition = state.swapPosition
        )
        val curves = FrequencyResponseCalculator.calculate(effectivePreset)
        _uiState.update {
            it.copy(
                presets = updatedList,
                frequencyCurves = curves,
                statusBanner = localizedStr(R.string.banner_reverted_preset, displayPresetName(reverted))
            )
        }
        if (isSelected) {
            updateForegroundRunningNotification()
            pushLiveDspConfigOnly()
        }
    }

    fun saveAsNewPreset(
        newName: String,
        sourcePresetId: String = _uiState.value.selectedPresetId,
        revertSourceToBaseline: Boolean = false,
        includeActiveIndependent: Boolean = false
    ) {
        val state = _uiState.value
        val source = state.presets.firstOrNull { it.id == sourcePresetId } ?: state.activeCommonPreset
        val trimmed = newName.trim().ifEmpty { localizedStr(R.string.preset_custom_fallback) }

        var updatedIndepList = state.independentPresets
        var linkedIndepId: String? = null
        var bundleLeftPreamp = 0.0
        var bundleLeftBands = emptyList<PeqBand>()
        var bundleRightPreamp = 0.0
        var bundleRightBands = emptyList<PeqBand>()
        var nextSelectedIndepId = state.selectedIndependentPresetId
        var nextDraftIndep = state.draftIndependentPreset

        if (includeActiveIndependent) {
            val activeIndep = state.activeIndependentPreset
            if (activeIndep != null && activeIndep.hasAnyFilterOrGain) {
                val persistedIndep = if (
                    state.selectedIndependentPresetId != null &&
                    state.independentPresets.any { it.id == state.selectedIndependentPresetId }
                ) {
                    activeIndep
                } else {
                    val created = activeIndep.copy(
                        id = UUID.randomUUID().toString(),
                        name = trimmed,
                        updatedAtEpochMs = System.currentTimeMillis()
                    ).withCapturedBaseline()
                    updatedIndepList = updatedIndepList + created
                    repo.saveIndependentPresets(updatedIndepList)
                    created
                }
                linkedIndepId = persistedIndep.id
                bundleLeftPreamp = persistedIndep.leftPreampDb
                bundleLeftBands = persistedIndep.leftBands.map { it.copy() }
                bundleRightPreamp = persistedIndep.rightPreampDb
                bundleRightBands = persistedIndep.rightBands.map { it.copy() }
                nextSelectedIndepId = persistedIndep.id
                nextDraftIndep = null
                repo.saveSelectedIndependentPresetId(persistedIndep.id)
            }
        }

        val newPreset = EqPreset(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            commonPreampDb = source.commonPreampDb,
            commonBands = source.commonBands.map { it.copy() },
            leftPreampDb = bundleLeftPreamp,
            leftBands = bundleLeftBands,
            rightPreampDb = bundleRightPreamp,
            rightBands = bundleRightBands,
            updatedAtEpochMs = System.currentTimeMillis(),
            linkedIndependentPresetId = linkedIndepId
        ).withCapturedBaseline()

        val baseList = if (revertSourceToBaseline && source.baseline != null) {
            val revertedSource = source.revertCommonToBaseline()
            state.presets.map { if (it.id == source.id) revertedSource else it }
        } else {
            state.presets
        }
        val updated = baseList + newPreset
        repo.savePresets(updated)
        repo.saveSelectedPresetId(newPreset.id)

        val effectiveIndep = updatedIndepList.firstOrNull { it.id == nextSelectedIndepId } ?: nextDraftIndep
        val effectivePreset = composeEffectivePreset(
            commonPreset = newPreset,
            independentPreset = effectiveIndep,
            channelSwap = state.channelSwap,
            swapPosition = state.swapPosition
        )
        _uiState.update {
            it.copy(
                presets = updated,
                selectedPresetId = newPreset.id,
                independentPresets = updatedIndepList,
                selectedIndependentPresetId = nextSelectedIndepId,
                draftIndependentPreset = nextDraftIndep,
                frequencyCurves = FrequencyResponseCalculator.calculate(effectivePreset),
                statusBanner = localizedStr(R.string.banner_saved_new_preset, trimmed)
            )
        }
        updateForegroundRunningNotification()
        pushLiveDspConfigOnly()
    }

    fun renamePreset(presetId: String, newName: String) {
        val trimmed = newName.trim().ifEmpty { return }
        val updated = _uiState.value.presets.map {
            if (it.id == presetId) it.copy(name = trimmed, updatedAtEpochMs = System.currentTimeMillis()) else it
        }
        repo.savePresets(updated)
        _uiState.update { it.copy(presets = updated) }
        if (_uiState.value.selectedPresetId == presetId) {
            updateForegroundRunningNotification()
        }
        scheduleAutoDevicePolicySync()
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
        scheduleAutoDevicePolicySync()
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
                if (rule.independentAction == DeviceIndependentRuleAction.KEEP_CURRENT &&
                    rule.channelSwapAction == DeviceSwapRuleAction.FOLLOW_MANUAL
                ) {
                    rule.copy(action = DeviceRuleAction.FOLLOW_MANUAL, presetId = null)
                } else {
                    rule.copy(presetId = null)
                }
            } else {
                rule
            }
        }
        repo.savePresets(remaining)
        repo.saveSelectedPresetId(nextSelectedId)
        repo.saveDeviceRules(nextRules)

        val nextCommon = remaining.first { it.id == nextSelectedId }
        val effectivePreset = composeEffectivePreset(
            commonPreset = nextCommon,
            independentPreset = state.activeIndependentPreset,
            channelSwap = state.channelSwap,
            swapPosition = state.swapPosition
        )
        _uiState.update {
            it.copy(
                presets = remaining,
                selectedPresetId = nextSelectedId,
                deviceRules = nextRules,
                frequencyCurves = FrequencyResponseCalculator.calculate(effectivePreset),
                statusBanner = localizedStr(R.string.banner_deleted_preset)
            )
        }
        updateForegroundRunningNotification()
        pushLiveDspConfigOnly()
    }

    fun revertIndependentPresetToBaseline(independentId: String? = _uiState.value.selectedIndependentPresetId) {
        val state = _uiState.value
        val targetId = independentId ?: return
        val target = state.independentPresets.firstOrNull { it.id == targetId } ?: return
        if (target.baseline == null) return
        val reverted = target.revertToBaseline()
        val updatedList = state.independentPresets.map { if (it.id == targetId) reverted else it }
        repo.saveIndependentPresets(updatedList)

        val isSelected = state.selectedIndependentPresetId == targetId
        val effectiveIndep = if (isSelected) reverted else state.activeIndependentPreset
        val effectivePreset = composeEffectivePreset(
            commonPreset = state.activeCommonPreset,
            independentPreset = effectiveIndep,
            channelSwap = state.channelSwap,
            swapPosition = state.swapPosition
        )
        _uiState.update {
            it.copy(
                independentPresets = updatedList,
                frequencyCurves = FrequencyResponseCalculator.calculate(effectivePreset),
                statusBanner = localizedStr(R.string.banner_reverted_preset, reverted.name)
            )
        }
        if (isSelected) {
            updateForegroundRunningNotification()
            pushLiveDspConfigOnly()
        }
    }

    fun discardDraftIndependent() {
        val state = _uiState.value
        if (state.draftIndependentPreset == null) return
        val effectivePreset = composeEffectivePreset(
            commonPreset = state.activeCommonPreset,
            independentPreset = null,
            channelSwap = state.channelSwap,
            swapPosition = state.swapPosition
        )
        _uiState.update {
            it.copy(
                draftIndependentPreset = null,
                frequencyCurves = FrequencyResponseCalculator.calculate(effectivePreset),
                statusBanner = localizedStr(R.string.banner_lr_cleared)
            )
        }
        updateForegroundRunningNotification()
        pushLiveDspConfigOnly()
    }

    fun saveAsNewIndependentPreset(
        newName: String,
        sourceIndependentId: String? = _uiState.value.selectedIndependentPresetId,
        revertSourceToBaseline: Boolean = false
    ) {
        val state = _uiState.value
        val source = sourceIndependentId?.let { id ->
            state.independentPresets.firstOrNull { it.id == id }
        } ?: state.draftIndependentPreset ?: state.activeIndependentPreset

        val trimmed = newName.trim().ifEmpty { localizedStr(R.string.lr_custom_fallback) }
        val newIndep = IndependentPeqPreset(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            leftPreampDb = source?.leftPreampDb ?: 0.0,
            leftBands = source?.leftBands?.map { it.copy() } ?: emptyList(),
            rightPreampDb = source?.rightPreampDb ?: 0.0,
            rightBands = source?.rightBands?.map { it.copy() } ?: emptyList(),
            updatedAtEpochMs = System.currentTimeMillis()
        ).withCapturedBaseline()

        val baseList = if (revertSourceToBaseline && source != null && sourceIndependentId != null && source.baseline != null) {
            val revertedSrc = source.revertToBaseline()
            state.independentPresets.map { if (it.id == sourceIndependentId) revertedSrc else it }
        } else {
            state.independentPresets
        }
        val updated = baseList + newIndep
        repo.saveIndependentPresets(updated)
        repo.saveSelectedIndependentPresetId(newIndep.id)

        val effectivePreset = composeEffectivePreset(
            commonPreset = state.activeCommonPreset,
            independentPreset = newIndep,
            channelSwap = state.channelSwap,
            swapPosition = state.swapPosition
        )
        _uiState.update {
            it.copy(
                independentPresets = updated,
                selectedIndependentPresetId = newIndep.id,
                draftIndependentPreset = null,
                frequencyCurves = FrequencyResponseCalculator.calculate(effectivePreset),
                statusBanner = localizedStr(R.string.banner_saved_new_lr_preset, trimmed)
            )
        }
        updateForegroundRunningNotification()
        pushLiveDspConfigOnly()
    }

    fun renameIndependentPreset(independentId: String, newName: String) {
        val trimmed = newName.trim().ifEmpty { return }
        val updated = _uiState.value.independentPresets.map {
            if (it.id == independentId) it.copy(name = trimmed, updatedAtEpochMs = System.currentTimeMillis()) else it
        }
        repo.saveIndependentPresets(updated)
        _uiState.update { it.copy(independentPresets = updated) }
        if (_uiState.value.selectedIndependentPresetId == independentId) {
            updateForegroundRunningNotification()
        }
        scheduleAutoDevicePolicySync()
    }

    fun duplicateIndependentPreset(independentId: String) {
        val src = _uiState.value.independentPresets.firstOrNull { it.id == independentId } ?: return
        val suffix = localizedStr(R.string.preset_copy_suffix)
        val copy = src.copy(
            id = UUID.randomUUID().toString(),
            name = "${src.name} ($suffix)",
            updatedAtEpochMs = System.currentTimeMillis()
        ).withCapturedBaseline()
        val updated = _uiState.value.independentPresets + copy
        repo.saveIndependentPresets(updated)
        _uiState.update {
            it.copy(
                independentPresets = updated,
                statusBanner = localizedStr(R.string.banner_duplicated_preset, copy.name)
            )
        }
        scheduleAutoDevicePolicySync()
    }

    fun deleteIndependentPreset(independentId: String) {
        val state = _uiState.value
        val remainingIndep = state.independentPresets.filterNot { it.id == independentId }
        if (remainingIndep.size == state.independentPresets.size) return

        val nextPresets = state.presets.map { preset ->
            if (preset.linkedIndependentPresetId == independentId) {
                val clearedBaseline = preset.baseline?.copy(
                    leftPreampDb = 0.0,
                    leftBands = emptyList(),
                    rightPreampDb = 0.0,
                    rightBands = emptyList()
                )
                preset.copy(
                    leftPreampDb = 0.0,
                    leftBands = emptyList(),
                    rightPreampDb = 0.0,
                    rightBands = emptyList(),
                    baseline = clearedBaseline,
                    linkedIndependentPresetId = null,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            } else {
                preset
            }
        }
        val nextSelectedIndepId = if (state.selectedIndependentPresetId == independentId) {
            null
        } else {
            state.selectedIndependentPresetId
        }
        val nextRules = state.deviceRules.map { rule ->
            if (rule.independentPresetId == independentId) {
                rule.copy(
                    independentAction = DeviceIndependentRuleAction.KEEP_CURRENT,
                    independentPresetId = null
                )
            } else {
                rule
            }
        }

        repo.saveIndependentPresets(remainingIndep)
        repo.savePresets(nextPresets)
        repo.saveSelectedIndependentPresetId(nextSelectedIndepId)
        repo.saveDeviceRules(nextRules)

        val nextIndep = nextSelectedIndepId?.let { id -> remainingIndep.firstOrNull { it.id == id } }
            ?: state.draftIndependentPreset
        val nextCommon = nextPresets.firstOrNull { it.id == state.selectedPresetId } ?: state.activeCommonPreset
        val effectivePreset = composeEffectivePreset(
            commonPreset = nextCommon,
            independentPreset = nextIndep,
            channelSwap = state.channelSwap,
            swapPosition = state.swapPosition
        )
        _uiState.update {
            it.copy(
                presets = nextPresets,
                independentPresets = remainingIndep,
                selectedIndependentPresetId = nextSelectedIndepId,
                deviceRules = nextRules,
                frequencyCurves = FrequencyResponseCalculator.calculate(effectivePreset),
                statusBanner = localizedStr(R.string.banner_deleted_lr_preset)
            )
        }
        updateForegroundRunningNotification()
        pushLiveDspConfigOnly()
    }

    fun importApoAsNewPreset(apoText: String, presetName: String): ApoImportResult {
        val state = _uiState.value
        val parsed = EqualizerApoParser.parse(apoText, presetName).preset
        val hasCommon = parsed.commonBands.isNotEmpty() || abs(parsed.commonPreampDb) > 1e-4
        val hasLr = parsed.leftBands.isNotEmpty() || parsed.rightBands.isNotEmpty() ||
            abs(parsed.leftPreampDb) > 1e-4 || abs(parsed.rightPreampDb) > 1e-4
        val totalBands = parsed.commonBands.size + parsed.leftBands.size + parsed.rightBands.size

        if (!hasCommon && !hasLr) {
            _uiState.update { it.copy(statusBanner = localizedStr(R.string.banner_apo_no_valid_filters)) }
            return ApoImportResult(filtersImported = 0)
        }

        val trimmedName = presetName.trim().ifEmpty { localizedStr(R.string.default_apo_import_name) }

        if (!hasCommon && hasLr) {
            val indep = IndependentPeqPreset(
                name = trimmedName,
                leftPreampDb = parsed.leftPreampDb,
                leftBands = parsed.leftBands,
                rightPreampDb = parsed.rightPreampDb,
                rightBands = parsed.rightBands
            ).withCapturedBaseline()
            val updatedIndep = state.independentPresets + indep
            repo.saveIndependentPresets(updatedIndep)
            repo.saveSelectedIndependentPresetId(indep.id)

            val effectivePreset = composeEffectivePreset(
                commonPreset = state.activeCommonPreset,
                independentPreset = indep,
                channelSwap = state.channelSwap,
                swapPosition = state.swapPosition
            )
            _uiState.update {
                it.copy(
                    independentPresets = updatedIndep,
                    selectedIndependentPresetId = indep.id,
                    draftIndependentPreset = null,
                    frequencyCurves = FrequencyResponseCalculator.calculate(effectivePreset),
                    statusBanner = localizedStr(
                        R.string.banner_apo_imported_lr_preset,
                        indep.name,
                        indep.leftBands.size,
                        indep.rightBands.size
                    )
                )
            }
            updateForegroundRunningNotification()
            pushLiveDspConfigOnly()
            return ApoImportResult(filtersImported = totalBands)
        }

        var nextIndepList = state.independentPresets
        var linkedIndep: IndependentPeqPreset? = null
        if (hasLr) {
            val extracted = IndependentPeqPreset(
                name = trimmedName,
                leftPreampDb = parsed.leftPreampDb,
                leftBands = parsed.leftBands,
                rightPreampDb = parsed.rightPreampDb,
                rightBands = parsed.rightBands
            ).withCapturedBaseline()
            nextIndepList = nextIndepList + extracted
            repo.saveIndependentPresets(nextIndepList)
            linkedIndep = extracted
        }

        val bundlePreset = parsed.copy(
            name = trimmedName,
            linkedIndependentPresetId = linkedIndep?.id
        ).withCapturedBaseline()
        val updatedPresets = state.presets + bundlePreset
        repo.savePresets(updatedPresets)

        val eval = evaluateSavedPresetSelection(
            chosenPreset = bundlePreset,
            activeIndependentId = state.selectedIndependentPresetId,
            hasDraftIndependent = state.draftIndependentPreset != null,
            independentPresets = nextIndepList
        )

        if (eval.outcome == BundleSelectionOutcome.PROMPT_COMMON_OR_BOTH) {
            _uiState.update {
                it.copy(
                    presets = updatedPresets,
                    independentPresets = nextIndepList
                )
            }
            scheduleAutoDevicePolicySync()
            return ApoImportResult(
                filtersImported = totalBands,
                importedPreset = bundlePreset,
                selectionEvaluation = eval
            )
        }

        val applyBoth = eval.outcome == BundleSelectionOutcome.AUTO_APPLY_BOTH
        val nextIndepId = if (applyBoth && linkedIndep != null) {
            repo.saveSelectedIndependentPresetId(linkedIndep.id)
            linkedIndep.id
        } else {
            state.selectedIndependentPresetId
        }
        val nextDraft = if (applyBoth && linkedIndep != null) null else state.draftIndependentPreset
        val effectiveIndep = if (applyBoth && linkedIndep != null) linkedIndep else state.activeIndependentPreset
        repo.saveSelectedPresetId(bundlePreset.id)

        val effectivePreset = composeEffectivePreset(
            commonPreset = bundlePreset,
            independentPreset = effectiveIndep,
            channelSwap = state.channelSwap,
            swapPosition = state.swapPosition
        )
        _uiState.update {
            it.copy(
                presets = updatedPresets,
                selectedPresetId = bundlePreset.id,
                independentPresets = nextIndepList,
                selectedIndependentPresetId = nextIndepId,
                draftIndependentPreset = nextDraft,
                frequencyCurves = FrequencyResponseCalculator.calculate(effectivePreset),
                statusBanner = localizedStr(
                    R.string.banner_apo_imported_preset,
                    bundlePreset.name,
                    bundlePreset.commonBands.size,
                    bundlePreset.leftBands.size,
                    bundlePreset.rightBands.size
                )
            )
        }
        updateForegroundRunningNotification()
        pushLiveDspConfigOnly()
        return ApoImportResult(filtersImported = totalBands)
    }

    fun importApoIntoCurrentTarget(apoText: String, target: PeqChannelTarget): Int {
        val (preamp, bands) = EqualizerApoParser.parseSingleSection(apoText)
        if (bands.isEmpty() && preamp == null) {
            _uiState.update { it.copy(statusBanner = localizedStr(R.string.banner_apo_no_valid_filters)) }
            return 0
        }
        if (target == PeqChannelTarget.COMMON) {
            updateActiveCommonPreset { preset ->
                preset.withTarget(
                    target = target,
                    preampDb = preamp ?: preset.preampFor(target),
                    bands = bands.ifEmpty { preset.bandsFor(target) }
                )
            }
        } else {
            updateActiveIndependentPreset { indep ->
                indep.withTarget(
                    target = target,
                    preampDb = preamp ?: indep.preampFor(target),
                    bands = bands.ifEmpty { indep.bandsFor(target) }
                )
            }
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
