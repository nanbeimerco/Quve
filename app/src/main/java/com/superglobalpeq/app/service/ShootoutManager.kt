package com.superglobalpeq.app.service

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Binder
import android.provider.Settings
import android.util.Log
import com.superglobalpeq.app.R
import com.superglobalpeq.app.data.QuveSettings
import com.superglobalpeq.app.data.ShootoutRepository
import com.superglobalpeq.app.dsp.DefaultPresets
import com.superglobalpeq.app.dsp.ShootoutLoudness
import com.superglobalpeq.app.dsp.ShootoutProcessor
import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.model.SHOOTOUT_MAX_CONTESTANTS
import com.superglobalpeq.app.model.SHOOTOUT_MIN_CONTESTANTS
import com.superglobalpeq.app.model.SHOOTOUT_NO_INDEPENDENT_ID
import com.superglobalpeq.app.model.ShootoutOutcome
import com.superglobalpeq.app.model.ShootoutRoundConfig
import com.superglobalpeq.app.model.ShootoutSessionRecord
import com.superglobalpeq.app.model.ShootoutSessionStatus
import com.superglobalpeq.app.model.ShootoutSetupSelection
import com.superglobalpeq.app.model.ShootoutVote
import com.superglobalpeq.app.model.SwapPosition
import com.superglobalpeq.app.model.composeShootoutContestants
import com.superglobalpeq.app.model.toSetupSelection
import com.superglobalpeq.app.shootout.ShootoutAnalysis
import com.superglobalpeq.app.shootout.ShootoutAnalyzer
import com.superglobalpeq.app.shootout.ShootoutPairing
import com.superglobalpeq.app.ui.shootout.ShootoutOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.UUID

enum class ShootoutStopReason {
    USER,
    SCREEN_OFF,
    IDLE,
    DEVICE_CHANGED,
    ENGINE
}

/** Blind state only: nothing here can reveal which EQ is behind A or B. */
data class ShootoutRunningState(
    val sessionId: String,
    val round: Int,
    val votes: Int,
    val recommendedVotes: Int,
    val contestantCount: Int,
    val side: Int = ShootoutProcessor.SIDE_A,
    val heardA: Boolean = true,
    val heardB: Boolean = false
) {
    val canVote: Boolean
        get() = heardA && heardB
}

data class ShootoutHistoryItem(
    val session: ShootoutSessionRecord,
    val winner: EqPreset?
)

data class ShootoutResultsState(
    val session: ShootoutSessionRecord,
    val analysis: ShootoutAnalysis
)

data class ShootoutUiState(
    val running: ShootoutRunningState? = null,
    val resumable: ShootoutSessionRecord? = null,
    val history: List<ShootoutHistoryItem> = emptyList(),
    val results: ShootoutResultsState? = null,
    val overlayAllowed: Boolean = false,
    val lastStopReason: ShootoutStopReason? = null,
    val setup: ShootoutSetupSelection = ShootoutSetupSelection()
)

/**
 * Owns the blind session. It lives only as long as the app process: the engine is told to fall
 * back to the normal EQ when this process dies, and sessions are never restarted automatically.
 * All public functions are called on the main thread.
 */
@SuppressLint("StaticFieldLeak")
object ShootoutManager {
    private const val TAG = "ShootoutManager"
    private const val IDLE_TIMEOUT_MS = 10L * 60L * 1000L
    private const val MAX_STORED_SESSIONS = 30

    private class ActiveRun(
        var session: ShootoutSessionRecord,
        val trimsDb: Map<String, Double>,
        var pairing: ShootoutPairing
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val storage = Dispatchers.IO.limitedParallelism(1)
    private val engineToken = Binder()
    private val random = SecureRandom()
    private val _state = MutableStateFlow(ShootoutUiState())
    private val _openTabRequests = MutableStateFlow(false)
    private val sessions = LinkedHashMap<String, ShootoutSessionRecord>()

    val state: StateFlow<ShootoutUiState> = _state.asStateFlow()
    val openTabRequests: StateFlow<Boolean> = _openTabRequests.asStateFlow()

    private lateinit var appContext: Context
    private lateinit var repository: ShootoutRepository
    private var initialized = false
    private var run: ActiveRun? = null
    private var roundCounter = 0L
    private var appVisible = false
    private var idleJob: Job? = null
    private var screenReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) stop(ShootoutStopReason.SCREEN_OFF)
        }
    }

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        repository = ShootoutRepository(appContext)
        refreshOverlayPermission()
        scope.launch {
            val stored = withContext(storage) {
                repository.loadAll().map { session ->
                    if (session.status == ShootoutSessionStatus.RUNNING) {
                        session.copy(status = ShootoutSessionStatus.INTERRUPTED).also(repository::save)
                    } else {
                        session
                    }
                }
            }
            stored.forEach { sessions[it.id] = it }
            stored.firstOrNull()?.let { latest ->
                _state.update { current ->
                    if (current.setup.commonIds.isEmpty()) {
                        current.copy(setup = latest.toSetupSelection())
                    } else {
                        current
                    }
                }
            }
            publishLibrary()
        }
    }

    fun setAppVisible(visible: Boolean) {
        if (appVisible == visible) return
        appVisible = visible
        if (visible) {
            refreshOverlayPermission()
            if (run != null) _openTabRequests.value = true
        }
        updateOverlay()
        PeqEngineManager.refreshForegroundNotification()
    }

    fun consumeOpenTabRequest() {
        _openTabRequests.value = false
    }

    val showsNotificationControls: Boolean
        get() = run != null && !appVisible && !_state.value.overlayAllowed

    fun updateSetup(selection: ShootoutSetupSelection) {
        _state.update { it.copy(setup = selection) }
    }

    /** Starts a session comparing Common EQs, all using the same L/R EQ. */
    fun start(
        commonPresetIds: List<String>,
        independentPresetId: String,
        channelSwap: Boolean,
        swapPosition: SwapPosition
    ) {
        if (run != null) return
        val app = PeqEngineManager.uiState.value
        if (!isEngineReady(app)) return
        val localized = QuveSettings.localizedContext(appContext)
        val commons = app.presets.filter { it.id in commonPresetIds }.map { preset ->
            val name = if (preset.id == DefaultPresets.FLAT_PRESET_ID && preset.name in DefaultPresets.FLAT_DEFAULT_NAMES) {
                localized.getString(R.string.preset_flat_name)
            } else {
                preset.name
            }
            preset to name
        }
        if (commons.size !in SHOOTOUT_MIN_CONTESTANTS..SHOOTOUT_MAX_CONTESTANTS) return
        val independent = if (independentPresetId == SHOOTOUT_NO_INDEPENDENT_ID) {
            null
        } else {
            (app.independentPresets + listOfNotNull(app.draftIndependentPreset))
                .firstOrNull { it.id == independentPresetId }
                ?: return
        }
        val contestants = composeShootoutContestants(commons, independent, channelSwap, swapPosition)

        _state.value.resumable?.let { previous ->
            if (previous.votes.isEmpty()) {
                removeSession(previous.id)
            } else {
                saveSession(previous.copy(status = ShootoutSessionStatus.FINISHED))
            }
        }
        val setup = _state.value.setup
        _state.update {
            it.copy(
                setup = setup.copy(
                    commonIds = commons.map { (preset, _) -> preset.id },
                    independentId = independentPresetId
                )
            )
        }
        val session = ShootoutSessionRecord(
            id = UUID.randomUUID().toString(),
            createdAtEpochMs = System.currentTimeMillis(),
            deviceKey = app.activeDevice?.deviceKey.orEmpty(),
            deviceName = app.activeDevice?.displayName.orEmpty(),
            seed = random.nextLong(),
            contestants = contestants,
            independentPreset = independent?.copy(baseline = null),
            channelSwap = channelSwap,
            swapPosition = swapPosition,
            contestantsComposed = true,
            channelSwapChosen = setup.channelSwap != null || setup.swapPosition != null
        )
        begin(session)
    }

    fun canResume(session: ShootoutSessionRecord, app: AppUiState): Boolean =
        isEngineReady(app) && session.deviceKey == app.activeDevice?.deviceKey.orEmpty()

    fun resume() {
        if (run != null) return
        val session = _state.value.resumable ?: return
        val app = PeqEngineManager.uiState.value
        if (!canResume(session, app)) return
        begin(session.copy(status = ShootoutSessionStatus.RUNNING))
    }

    private fun isEngineReady(app: AppUiState): Boolean =
        app.masterPowerOn && !app.pausedByDeviceRule && app.serviceBound && app.telemetry.isRunning

    private fun begin(session: ShootoutSessionRecord) {
        val effective = session.contestants.map { session.effectivePreset(it.id) }
        val trims = ShootoutLoudness.trimsDb(effective)
        val active = ActiveRun(
            session = session,
            trimsDb = session.contestants.indices.associate { session.contestants[it].id to trims[it] },
            pairing = ShootoutAnalyzer.nextPairing(session)
        )
        run = active
        saveSession(session)
        registerScreenReceiver()
        _state.update { it.copy(results = null, lastStopReason = null) }
        publishRunning(active)
        pushRound(active)
        touch()
        updateOverlay()
        PeqEngineManager.refreshForegroundNotification()
    }

    private fun publishRunning(active: ActiveRun) {
        val session = active.session
        _state.update {
            it.copy(
                running = ShootoutRunningState(
                    sessionId = session.id,
                    round = session.votes.size + 1,
                    votes = session.votes.size,
                    recommendedVotes = session.recommendedVotes,
                    contestantCount = session.contestants.size
                ),
                resumable = null
            )
        }
    }

    private fun pushRound(active: ActiveRun) {
        val session = active.session
        val pairing = active.pairing
        val config = ShootoutRoundConfig(
            roundId = ++roundCounter,
            presetA = session.effectivePreset(pairing.contestantA),
            presetB = session.effectivePreset(pairing.contestantB),
            trimADb = active.trimsDb.getValue(pairing.contestantA),
            trimBDb = active.trimsDb.getValue(pairing.contestantB)
        )
        val json = config.toJsonString()
        PeqEngineManager.runShootoutEngineCall(
            onFailure = { stop(ShootoutStopReason.ENGINE) }
        ) { service ->
            check(service.startShootoutRound(engineToken, json)) { "Engine rejected the round" }
        }
    }

    fun selectSide(side: Int) {
        val running = _state.value.running ?: return
        touch()
        if (running.side == side && (side == ShootoutProcessor.SIDE_A || running.heardB)) return
        _state.update {
            it.copy(
                running = running.copy(
                    side = side,
                    heardA = running.heardA || side == ShootoutProcessor.SIDE_A,
                    heardB = running.heardB || side == ShootoutProcessor.SIDE_B
                )
            )
        }
        PeqEngineManager.runShootoutEngineCall(
            onFailure = { stop(ShootoutStopReason.ENGINE) }
        ) { service -> service.selectShootoutSide(side) }
        if (showsNotificationControls) PeqEngineManager.refreshForegroundNotification()
    }

    fun toggleSide() {
        val running = _state.value.running ?: return
        selectSide(
            if (running.side == ShootoutProcessor.SIDE_A) ShootoutProcessor.SIDE_B else ShootoutProcessor.SIDE_A
        )
    }

    fun vote(outcome: ShootoutOutcome) {
        val active = run ?: return
        val running = _state.value.running ?: return
        if (!running.canVote) return
        val vote = ShootoutVote(
            contestantA = active.pairing.contestantA,
            contestantB = active.pairing.contestantB,
            outcome = outcome,
            atEpochMs = System.currentTimeMillis()
        )
        active.session = active.session.copy(votes = active.session.votes + vote)
        active.pairing = ShootoutAnalyzer.nextPairing(active.session)
        saveSession(active.session)
        publishRunning(active)
        pushRound(active)
        touch()
        PeqEngineManager.refreshForegroundNotification()
    }

    /** Ends the session and shows its results. */
    fun finish() {
        val active = run ?: return
        if (active.session.votes.isEmpty()) {
            stop(ShootoutStopReason.USER)
            return
        }
        val finished = active.session.copy(status = ShootoutSessionStatus.FINISHED)
        tearDown()
        saveSession(finished)
        _state.update { it.copy(running = null, lastStopReason = null) }
        publishLibrary()
        openResults(finished.id)
    }

    fun finishResumable() {
        val session = _state.value.resumable?.takeIf { it.votes.isNotEmpty() } ?: return
        val finished = session.copy(status = ShootoutSessionStatus.FINISHED)
        saveSession(finished)
        publishLibrary()
        openResults(finished.id)
    }

    fun discardResumable() {
        val session = _state.value.resumable ?: return
        removeSession(session.id)
        publishLibrary()
    }

    /** Interrupts the session; the engine fades back to the normal EQ on its own. */
    fun stop(reason: ShootoutStopReason) {
        val active = run ?: return
        Log.i(TAG, "Shootout interrupted: $reason")
        tearDown()
        // Kept even before the first vote, so one tap can always continue the same comparison.
        saveSession(active.session.copy(status = ShootoutSessionStatus.INTERRUPTED))
        _state.update { it.copy(running = null, lastStopReason = reason) }
        publishLibrary()
    }

    private fun tearDown() {
        run = null
        _state.update { it.copy(running = null) }
        idleJob?.cancel()
        idleJob = null
        unregisterScreenReceiver()
        ShootoutOverlay.hide()
        PeqEngineManager.runShootoutEngineCall { service -> service.stopShootout() }
        PeqEngineManager.refreshForegroundNotification()
    }

    fun onOutputDeviceChanged(deviceKey: String?) {
        val active = run ?: return
        if (active.session.deviceKey != deviceKey.orEmpty()) stop(ShootoutStopReason.DEVICE_CHANGED)
    }

    fun onEngineUnavailable() {
        if (!initialized) return
        stop(ShootoutStopReason.ENGINE)
    }

    /** The loopback restart cleared the engine, so the current round has to be sent again. */
    fun onLoopbackRestarted() {
        val active = run ?: return
        _state.value.running?.let { running ->
            _state.update {
                it.copy(running = running.copy(side = ShootoutProcessor.SIDE_A, heardA = true, heardB = false))
            }
        }
        pushRound(active)
    }

    fun openResults(sessionId: String) {
        val session = sessions[sessionId] ?: return
        scope.launch {
            val analysis = withContext(Dispatchers.Default) { ShootoutAnalyzer.analyze(session) }
            _state.update { it.copy(results = ShootoutResultsState(session, analysis)) }
        }
    }

    fun closeResults() {
        _state.update { it.copy(results = null) }
    }

    private fun removeSession(sessionId: String) {
        sessions.remove(sessionId)
        scope.launch(storage) { repository.delete(sessionId) }
    }

    fun deleteSession(sessionId: String) {
        removeSession(sessionId)
        _state.update { current ->
            current.copy(results = current.results?.takeUnless { it.session.id == sessionId })
        }
        publishLibrary()
    }

    fun clearStopReason() {
        _state.update { it.copy(lastStopReason = null) }
    }

    fun refreshOverlayPermission() {
        if (!initialized) return
        val allowed = Settings.canDrawOverlays(appContext)
        if (allowed != _state.value.overlayAllowed) {
            _state.update { it.copy(overlayAllowed = allowed) }
            updateOverlay()
        }
    }

    /** Tries the Shizuku route first, then falls back to the system permission screen. */
    fun requestOverlayPermission(context: Context) {
        val openSettings = {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${appContext.packageName}")
            )
            runCatching { context.startActivity(intent) }
                .onFailure { Log.w(TAG, "Unable to open overlay settings", it) }
            Unit
        }
        PeqEngineManager.runShootoutEngineCall(
            onFailure = openSettings,
            onSuccess = {
                scope.launch {
                    delay(250L)
                    refreshOverlayPermission()
                    if (!_state.value.overlayAllowed) openSettings()
                }
            }
        ) { service ->
            check(service.allowOverlayForClient()) { "appops was refused" }
        }
    }

    private fun touch() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_TIMEOUT_MS)
            stop(ShootoutStopReason.IDLE)
        }
    }

    private fun updateOverlay() {
        if (!initialized) return
        if (run != null && !appVisible && _state.value.overlayAllowed) {
            ShootoutOverlay.show(appContext)
        } else {
            ShootoutOverlay.hide()
        }
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        appContext.registerReceiver(
            screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            Context.RECEIVER_NOT_EXPORTED
        )
        screenReceiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        runCatching { appContext.unregisterReceiver(screenReceiver) }
        screenReceiverRegistered = false
    }

    private fun saveSession(session: ShootoutSessionRecord) {
        sessions[session.id] = session
        scope.launch(storage) {
            runCatching { repository.save(session) }
                .onFailure { Log.e(TAG, "Unable to save Shootout session", it) }
        }
    }

    private fun publishLibrary() {
        val ordered = sessions.values.sortedByDescending { it.createdAtEpochMs }
        val finished = ordered.filter { it.status == ShootoutSessionStatus.FINISHED }
        finished.drop(MAX_STORED_SESSIONS).forEach { stale ->
            sessions.remove(stale.id)
            scope.launch(storage) { repository.delete(stale.id) }
        }
        val kept = finished.take(MAX_STORED_SESSIONS)
        val resumable = if (run == null) {
            ordered.firstOrNull { it.status == ShootoutSessionStatus.INTERRUPTED }
        } else {
            null
        }
        _state.update { it.copy(resumable = resumable) }
        scope.launch {
            val history = withContext(Dispatchers.Default) {
                kept.map { session ->
                    val winnerId = ShootoutAnalyzer.analyze(session).standings.firstOrNull()?.contestantId
                    ShootoutHistoryItem(session, session.contestants.firstOrNull { it.id == winnerId })
                }
            }
            _state.update { it.copy(history = history) }
        }
    }
}
