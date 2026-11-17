package com.superglobalpeq.app.service

import android.annotation.SuppressLint
import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.superglobalpeq.app.dsp.StereoPeqProcessor
import com.superglobalpeq.app.model.ActiveDspConfig
import com.superglobalpeq.app.model.AutonomousDevicePolicy
import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.model.IndependentPeqPreset
import com.superglobalpeq.app.model.mergeExcludedUids
import com.superglobalpeq.app.model.resolveDeviceRoutingOutcome
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.system.exitProcess

internal const val PEQ_USER_SERVICE_PROCESS_SUFFIX = ":peq_dsp"

internal fun extractProcCmdlineProcessName(
    rawCmdlineBytes: ByteArray,
    length: Int = rawCmdlineBytes.size
): String? {
    val safeLen = length.coerceAtMost(rawCmdlineBytes.size)
    if (safeLen <= 0) return null
    var nulIdx = 0
    while (nulIdx < safeLen && rawCmdlineBytes[nulIdx] != 0.toByte()) {
        nulIdx++
    }
    if (nulIdx == 0) return null
    return String(rawCmdlineBytes, 0, nulIdx, StandardCharsets.UTF_8).trim().ifEmpty { null }
}

internal fun parseProcRealUid(statusText: String): Int? {
    for (line in statusText.lineSequence()) {
        if (line.startsWith("Uid:")) {
            val firstToken = line.removePrefix("Uid:").trimStart().takeWhile { !it.isWhitespace() }
            return firstToken.toIntOrNull()
        }
    }
    return null
}

internal fun isStaleSiblingPeqServiceProcess(
    pid: Int,
    myPid: Int,
    uid: Int?,
    myUid: Int,
    processName: String?,
    expectedProcessName: String
): Boolean {
    if (pid <= 0 || pid == myPid) return false
    if (uid == null || uid != myUid) return false
    if (!expectedProcessName.endsWith(PEQ_USER_SERVICE_PROCESS_SUFFIX)) return false
    return processName == expectedProcessName
}

internal fun normalizeUserExcludedUids(
    serviceUid: Int,
    clientAppUid: Int,
    extraExcludedUids: IntArray?
): IntArray {
    if (extraExcludedUids == null || extraExcludedUids.isEmpty()) return IntArray(0)
    val out = HashSet<Int>(extraExcludedUids.size)
    for (uid in extraExcludedUids) {
        if (uid > 0 && uid != serviceUid && uid != clientAppUid) {
            out.add(uid)
        }
    }
    return out.toIntArray().apply { sort() }
}

/**
 * Runs inside Shizuku's UID 2000 (com.android.shell) process via UserService.
 * Uses ROUTE_FLAG_LOOP_BACK (without RENDER) + RULE_MATCH_UID exclusion on UID 2000
 * to intercept system media/game audio without MediaProjection or double playback,
 * processes stereo PCM through 64-bit TDF-II Biquad PEQ, and renders via UID 2000 AudioTrack.
 */
class AudioLoopbackUserService : IAudioLoopbackService.Stub() {
    companion object {
        private const val TAG = "SuperPeqLoopbackSvc"
        private const val SHELL_PKG = "com.android.shell"
        private const val RULE_MATCH_ATTRIBUTE_USAGE = 0x1
        private const val RULE_MATCH_UID = 0x1 shl 2
        private const val ROUTE_FLAG_LOOP_BACK = 0x1 shl 1
        private const val AUDIO_MANAGER_SUCCESS = 0
        private const val SAMPLE_RATE = 48000
        private const val BLOCK_SHORTS_STANDARD = 480 * 2 // 10 ms @ 48 kHz stereo PCM_16BIT
        private const val BLOCK_SHORTS_LOW_LATENCY = 192 * 2 // 4 ms @ 48 kHz (matches FastMixer HAL burst)
        // Kept > DEFAULT_HIGH_WATER_FRAMES (1440) so LatencyQueueController sheds excess before track.write blocks.
        private const val LOW_LATENCY_TRACK_BUFFER_FRAMES = 2304 // 48 ms @ 48 kHz (12 * 192)
        private const val PER_APP_HOLD_OFF_MS = 1200L
        private const val PLAYER_STATE_STARTED = 2
        private const val SIGNAL_SIGTERM = 15
        private const val SIGNAL_SIGKILL = 9
    }

    private class FakeShellContext(base: Context) : ContextWrapper(base) {
        private val shellAttribution: AttributionSource =
            AttributionSource.Builder(Process.myUid())
                .setPackageName(SHELL_PKG)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        setPid(Process.myPid())
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        setDeviceId(0)
                    }
                }
                .build()

        override fun getPackageName(): String = SHELL_PKG
        override fun getOpPackageName(): String = SHELL_PKG
        override fun getAttributionSource(): AttributionSource = shellAttribution
        override fun getApplicationContext(): Context = this
        override fun createAttributionContext(attributionTag: String?): Context = this
        override fun getDeviceId(): Int = 0
    }

    private val processor = StereoPeqProcessor(SAMPLE_RATE.toDouble())
    private val stateLock = Any()
    private val clientLock = Any()
    private val loopRunning = AtomicBoolean(false)
    private val destroying = AtomicBoolean(false)

    @Volatile
    private var linkedClientToken: IBinder? = null

    @Volatile
    private var clientDeathRecipient: IBinder.DeathRecipient? = null

    @Volatile
    private var audioPolicyObj: Any? = null

    @Volatile
    private var audioManager: AudioManager? = null

    @Volatile
    private var shellContext: Context? = null

    @Volatile
    private var activeRecord: AudioRecord? = null

    @Volatile
    private var activeTrack: AudioTrack? = null

    @Volatile
    private var workerThread: Thread? = null

    @Volatile
    private var lastError: String? = null

    @Volatile
    private var excludedUidCount: Int = 0

    @Volatile
    private var activeUserExcludedUids: IntArray = IntArray(0)

    @Volatile
    private var inPeakDbfs: Double = -120.0

    @Volatile
    private var inRmsDbfs: Double = -120.0

    @Volatile
    private var outPeakDbfs: Double = -120.0

    @Volatile
    private var outRmsDbfs: Double = -120.0

    private val framesProcessed = AtomicLong(0L)
    private val lowLatencyDroppedBlocks = AtomicLong(0L)
    private val queueController = LatencyQueueController()
    private val playbackLock = Any()

    @Volatile
    private var globalLowLatencyEnabled: Boolean = false

    @Volatile
    private var lowLatencyUidSet: Set<Int> = emptySet()

    @Volatile
    private var perAppLowLatencyActive: Boolean = false

    @Volatile
    private var perAppHoldOffUntilMs: Long = 0L

    @Volatile
    private var lastQueuedFrames: Long = 0L

    @Volatile
    private var currentTrackBufferFrames: Int = 0

    @Volatile
    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null

    @Volatile
    private var playbackHandlerThread: HandlerThread? = null

    private val deviceMonitorLock = Any()

    @Volatile
    private var deviceMonitorRegistered: Boolean = false

    @Volatile
    private var deviceMonitorCallback: AudioDeviceCallback? = null

    @Volatile
    private var deviceMonitorThread: HandlerThread? = null

    @Volatile
    private var deviceMonitorHandler: Handler? = null

    private val delayedDeviceEvalRunnable = Runnable {
        evaluateAutonomousDeviceChange()
    }

    @Volatile
    private var savedClientAppUid: Int = -1

    @Volatile
    private var savedRawExcludedUids: IntArray = IntArray(0)

    @Volatile
    private var autonomousPolicy: AutonomousDevicePolicy? = null

    @Volatile
    private var autonomousLastDeviceKey: String? = null

    @Volatile
    private var autonomousCurrentCommonId: String = ""

    @Volatile
    private var autonomousCurrentIndepId: String? = null

    @Volatile
    private var autonomousCurrentDraftIndep: IndependentPeqPreset? = null

    @Volatile
    private var autonomousCurrentSwap: Boolean = false

    @Volatile
    private var autonomousPausedByRule: Boolean = false

    @Volatile
    private var autonomousSwitchCount: Int = 0

    private val playbackGetClientUidMethod: Method? by lazy {
        runCatching { AudioPlaybackConfiguration::class.java.getMethod("getClientUid") }.getOrNull()
    }

    private val playbackIsActiveMethod: Method? by lazy {
        runCatching { AudioPlaybackConfiguration::class.java.getMethod("isActive") }.getOrNull()
    }

    private val playbackGetPlayerStateMethod: Method? by lazy {
        runCatching { AudioPlaybackConfiguration::class.java.getMethod("getPlayerState") }.getOrNull()
    }

    init {
        Runtime.getRuntime().addShutdownHook(Thread({
            stopInternal()
        }, "peq-shutdown-hook"))
        terminateStaleSiblingServices()
    }

    private fun readProcessCmdlineName(cmdlineFile: File): String? {
        return try {
            val buf = ByteArray(256)
            FileInputStream(cmdlineFile).use { input ->
                val read = input.read(buf)
                extractProcCmdlineProcessName(buf, read)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun terminateStaleSiblingServices() {
        val myPid = Process.myPid()
        val myUid = Process.myUid()
        val expectedProcessName = readProcessCmdlineName(File("/proc/self/cmdline")) ?: return
        if (!expectedProcessName.endsWith(PEQ_USER_SERVICE_PROCESS_SUFFIX)) return

        val procEntries = File("/proc").listFiles() ?: return
        val signalledPids = ArrayList<Int>()
        for (entry in procEntries) {
            val pid = entry.name.toIntOrNull() ?: continue
            if (pid <= 0 || pid == myPid) continue
            val procName = readProcessCmdlineName(File(entry, "cmdline"))
            if (procName != expectedProcessName) continue
            val uid = try {
                parseProcRealUid(File(entry, "status").readText())
            } catch (_: Throwable) {
                null
            }
            if (!isStaleSiblingPeqServiceProcess(pid, myPid, uid, myUid, procName, expectedProcessName)) {
                continue
            }
            try {
                Log.w(TAG, "Terminating stale sibling PEQ UserService pid=$pid ($procName)")
                Process.sendSignal(pid, SIGNAL_SIGTERM)
                signalledPids.add(pid)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed SIGTERM to stale sibling pid=$pid", t)
            }
        }
        if (signalledPids.isEmpty()) return

        val deadlineNs = System.nanoTime() + 450_000_000L
        var remaining = signalledPids.filter { File("/proc/$it").exists() }
        while (remaining.isNotEmpty() && System.nanoTime() < deadlineNs) {
            try {
                Thread.sleep(40L)
            } catch (_: InterruptedException) {
                break
            }
            remaining = remaining.filter { File("/proc/$it").exists() }
        }
        for (pid in remaining) {
            try {
                Log.w(TAG, "Force-killing unresponsive sibling PEQ UserService pid=$pid")
                Process.sendSignal(pid, SIGNAL_SIGKILL)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed SIGKILL to sibling pid=$pid", t)
            }
        }
    }

    @SuppressLint("PrivateApi")
    private fun ensureShellContext(): Context {
        shellContext?.let { return it }
        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper()
        }
        val atClass = Class.forName("android.app.ActivityThread")
        var at = atClass.getMethod("currentActivityThread").invoke(null)
        if (at == null) {
            at = atClass.getMethod("systemMain").invoke(null)
        }
        val sysCtx = atClass.getMethod("getSystemContext").invoke(at) as Context
        val pkgCtx = sysCtx.createPackageContext(SHELL_PKG, 0)
        val fake = FakeShellContext(pkgCtx)
        shellContext = fake
        audioManager = fake.getSystemService(AudioManager::class.java)
        return fake
    }

    override fun startLoopback(
        clientAppUid: Int,
        excludedUids: IntArray?,
        globalLowLatency: Boolean,
        lowLatencyUids: IntArray?,
        dspConfigJson: String
    ): Boolean {
        synchronized(stateLock) {
            val parsedConfig = try {
                ActiveDspConfig.fromJsonString(dspConfigJson)
            } catch (t: Throwable) {
                Log.e(TAG, "startLoopback config parse failed", t)
                lastError = "${t.javaClass.simpleName}: ${t.message}"
                return false
            }
            return startLoopbackLocked(
                clientAppUid = clientAppUid,
                excludedUids = excludedUids,
                globalLowLatency = globalLowLatency,
                lowLatencyUids = lowLatencyUids,
                parsedConfig = parsedConfig
            )
        }
    }

    private fun startLoopbackLocked(
        clientAppUid: Int,
        excludedUids: IntArray?,
        globalLowLatency: Boolean,
        lowLatencyUids: IntArray?,
        parsedConfig: ActiveDspConfig
    ): Boolean {
        savedClientAppUid = clientAppUid
        savedRawExcludedUids = excludedUids?.copyOf() ?: IntArray(0)
        stopInternal()
        terminateStaleSiblingServices()
        lastError = null
        excludedUidCount = 0
        framesProcessed.set(0L)
        lowLatencyDroppedBlocks.set(0L)
        lastQueuedFrames = 0L
        currentTrackBufferFrames = 0
        queueController.reset()
        inPeakDbfs = -120.0
        inRmsDbfs = -120.0
        outPeakDbfs = -120.0
        outRmsDbfs = -120.0
        globalLowLatencyEnabled = globalLowLatency
        lowLatencyUidSet = lowLatencyUids?.toHashSet() ?: emptySet()
        perAppLowLatencyActive = false
        perAppHoldOffUntilMs = 0L

        return try {
            processor.resetStates()
            processor.updateConfig(parsedConfig)

                val ctx = ensureShellContext()
                registerPlaybackMonitor()
                refreshPerAppLowLatencyFromManager()
                val myUid = Process.myUid()

                val mixingRuleClass = Class.forName("android.media.audiopolicy.AudioMixingRule")
                val mixingRuleBuilderClass = Class.forName("android.media.audiopolicy.AudioMixingRule\$Builder")
                val audioMixClass = Class.forName("android.media.audiopolicy.AudioMix")
                val audioMixBuilderClass = Class.forName("android.media.audiopolicy.AudioMix\$Builder")
                val audioPolicyClass = Class.forName("android.media.audiopolicy.AudioPolicy")
                val audioPolicyBuilderClass = Class.forName("android.media.audiopolicy.AudioPolicy\$Builder")

                val ruleBuilder = mixingRuleBuilderClass.getConstructor().newInstance()
                val addRule = mixingRuleBuilderClass.getMethod(
                    "addRule", AudioAttributes::class.java, Int::class.javaPrimitiveType
                )
                val usages = intArrayOf(
                    AudioAttributes.USAGE_MEDIA,
                    AudioAttributes.USAGE_GAME,
                    AudioAttributes.USAGE_UNKNOWN
                )
                for (usage in usages) {
                    val attr = AudioAttributes.Builder().setUsage(usage).build()
                    addRule.invoke(ruleBuilder, attr, RULE_MATCH_ATTRIBUTE_USAGE)
                }

                val excludeMixRule = mixingRuleBuilderClass.getMethod(
                    "excludeMixRule", Int::class.javaPrimitiveType, Any::class.java
                )
                val allExcludedUids = mergeExcludedUids(
                    serviceUid = myUid,
                    clientAppUid = clientAppUid,
                    extraExcludedUids = excludedUids
                )
                for (uid in allExcludedUids) {
                    excludeMixRule.invoke(ruleBuilder, RULE_MATCH_UID, Integer.valueOf(uid))
                }
                val normalizedUserExcluded = normalizeUserExcludedUids(myUid, clientAppUid, excludedUids)
                activeUserExcludedUids = normalizedUserExcluded
                excludedUidCount = normalizedUserExcluded.size
                val mixingRule = mixingRuleBuilderClass.getMethod("build").invoke(ruleBuilder)

                val mixFormat = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()

                val mixBuilder = audioMixBuilderClass.getConstructor(mixingRuleClass).newInstance(mixingRule)
                audioMixBuilderClass.getMethod("setFormat", AudioFormat::class.java).invoke(mixBuilder, mixFormat)
                audioMixBuilderClass.getMethod("setRouteFlags", Int::class.javaPrimitiveType)
                    .invoke(mixBuilder, ROUTE_FLAG_LOOP_BACK)
                val audioMix = audioMixBuilderClass.getMethod("build").invoke(mixBuilder)

                val policyBuilder = audioPolicyBuilderClass.getConstructor(Context::class.java).newInstance(ctx)
                audioPolicyBuilderClass.getMethod("setLooper", Looper::class.java)
                    .invoke(policyBuilder, Looper.getMainLooper())
                audioPolicyBuilderClass.getMethod("addMix", audioMixClass).invoke(policyBuilder, audioMix)
                val policy = audioPolicyBuilderClass.getMethod("build").invoke(policyBuilder)

                val regStatic = AudioManager::class.java.getDeclaredMethod("registerAudioPolicyStatic", audioPolicyClass)
                regStatic.isAccessible = true
                val regRes = regStatic.invoke(null, policy) as Int
                if (regRes != AUDIO_MANAGER_SUCCESS) {
                    lastError = "registerAudioPolicyStatic failed: code=$regRes"
                    return false
                }
                audioPolicyObj = policy

                val record = createRecordSink(audioPolicyClass, audioMixClass, policy, audioMix, ctx)
                if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                    lastError = "AudioRecord sink initialization failed"
                    stopInternal()
                    return false
                }
                activeRecord = record

                val track = createOutputTrack(ctx)
                if (track.state != AudioTrack.STATE_INITIALIZED) {
                    lastError = "AudioTrack output initialization failed"
                    stopInternal()
                    return false
                }
                val initialLowLatency = isLowLatencyActive()
                applyTrackBufferSizeForMode(track, initialLowLatency)
                activeTrack = track

                loopRunning.set(true)
                val t = Thread({ runAudioLoop(record, track, initialLowLatency) }, "peq-audio-loop")
                workerThread = t
                t.start()
                Log.i(TAG, "Loopback engine started cleanly (uid=$myUid, userExcluded=$excludedUidCount)")
                true
        } catch (t: Throwable) {
            Log.e(TAG, "startLoopback failed", t)
            lastError = "${t.javaClass.simpleName}: ${t.message}"
            stopInternal()
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun createRecordSink(
        audioPolicyClass: Class<*>,
        audioMixClass: Class<*>,
        policy: Any,
        audioMix: Any,
        ctx: Context
    ): AudioRecord? {
        try {
            val direct = audioPolicyClass
                .getMethod("createAudioRecordSink", audioMixClass)
                .invoke(policy, audioMix) as? AudioRecord
            if (direct != null && direct.state == AudioRecord.STATE_INITIALIZED) {
                return direct
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Direct createAudioRecordSink fallback: ${t.cause ?: t}")
        }

        val registration = audioMixClass.getMethod("getRegistration").invoke(audioMix) as String
        val fixedVolTag = AudioRecord::class.java.getField("SUBMIX_FIXED_VOLUME").get(null) as String

        val ab = AudioAttributes.Builder()
        AudioAttributes.Builder::class.java
            .getMethod("setInternalCapturePreset", Int::class.javaPrimitiveType)
            .invoke(ab, MediaRecorder.AudioSource.REMOTE_SUBMIX)
        val addTag = AudioAttributes.Builder::class.java.getMethod("addTag", String::class.java)
        addTag.invoke(ab, "addr=$registration")
        addTag.invoke(ab, fixedVolTag)

        val inFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufBytes = maxOf(minBuf, BLOCK_SHORTS_STANDARD * 2 * 4)

        val rb = AudioRecord.Builder()
            .setContext(ctx)
            .setAudioFormat(inFormat)
            .setBufferSizeInBytes(bufBytes)
        AudioRecord.Builder::class.java
            .getMethod("setAudioAttributes", AudioAttributes::class.java)
            .invoke(rb, ab.build())
        return rb.build()
    }

    private fun createOutputTrack(ctx: Context): AudioTrack {
        val minOutBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val outBufBytes = maxOf(minOutBuf, BLOCK_SHORTS_STANDARD * 2 * 4)
        val builder = AudioTrack.Builder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            builder.setContext(ctx)
        }
        return builder
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(outBufBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
    }

    private fun isLowLatencyActive(): Boolean = globalLowLatencyEnabled || perAppLowLatencyActive

    private fun applyTrackBufferSizeForMode(track: AudioTrack, lowLatency: Boolean) {
        try {
            val capacity = track.bufferCapacityInFrames
            val desired = if (lowLatency) {
                minOf(capacity, LOW_LATENCY_TRACK_BUFFER_FRAMES).coerceAtLeast(BLOCK_SHORTS_LOW_LATENCY / 2)
            } else {
                capacity
            }
            val actual = track.setBufferSizeInFrames(desired)
            currentTrackBufferFrames = if (actual > 0) actual else desired
            Log.i(TAG, "AudioTrack buffer size updated: lowLatency=$lowLatency, desired=$desired, actual=$actual, cap=$capacity")
        } catch (t: Throwable) {
            Log.w(TAG, "setBufferSizeInFrames failed (lowLatency=$lowLatency)", t)
        }
    }

    private fun registerPlaybackMonitor() {
        val am = audioManager ?: return
        unregisterPlaybackMonitor()
        try {
            val thread = HandlerThread("peq-playback-cb").apply { start() }
            playbackHandlerThread = thread
            val handler = Handler(thread.looper)
            val cb = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                    recomputePerAppLowLatency(configs.orEmpty())
                }
            }
            playbackCallback = cb
            am.registerAudioPlaybackCallback(cb, handler)
        } catch (t: Throwable) {
            Log.w(TAG, "registerAudioPlaybackCallback failed", t)
        }
    }

    private fun unregisterPlaybackMonitor() {
        val am = audioManager
        val cb = playbackCallback
        playbackCallback = null
        if (am != null && cb != null) {
            runCatching { am.unregisterAudioPlaybackCallback(cb) }
        }
        playbackHandlerThread?.let { thread ->
            runCatching { thread.quitSafely() }
        }
        playbackHandlerThread = null
    }

    private fun refreshPerAppLowLatencyFromManager() {
        val am = audioManager ?: return
        val configs = runCatching { am.activePlaybackConfigurations }.getOrElse { emptyList() }
        recomputePerAppLowLatency(configs)
    }

    private fun recomputePerAppLowLatency(configs: List<AudioPlaybackConfiguration>) {
        synchronized(playbackLock) {
            val targetUids = lowLatencyUidSet
            if (targetUids.isEmpty()) {
                perAppLowLatencyActive = false
                perAppHoldOffUntilMs = 0L
                return
            }

            var matchedActive = false
            for (config in configs) {
                val usage = config.audioAttributes?.usage ?: continue
                if (usage != AudioAttributes.USAGE_MEDIA &&
                    usage != AudioAttributes.USAGE_GAME &&
                    usage != AudioAttributes.USAGE_UNKNOWN
                ) {
                    continue
                }
                val uid = runCatching { playbackGetClientUidMethod?.invoke(config) as? Int }.getOrNull() ?: -1
                if (uid !in targetUids) continue

                val isAct = runCatching { playbackIsActiveMethod?.invoke(config) as? Boolean }.getOrNull() == true
                val state = runCatching { playbackGetPlayerStateMethod?.invoke(config) as? Int }.getOrNull()
                if (isAct || state == PLAYER_STATE_STARTED) {
                    matchedActive = true
                    break
                }
            }

            if (matchedActive) {
                perAppLowLatencyActive = true
                perAppHoldOffUntilMs = 0L
            } else if (perAppLowLatencyActive) {
                val nowMs = SystemClock.elapsedRealtime()
                if (perAppHoldOffUntilMs == 0L) {
                    perAppHoldOffUntilMs = nowMs + PER_APP_HOLD_OFF_MS
                } else if (nowMs >= perAppHoldOffUntilMs) {
                    perAppLowLatencyActive = false
                    perAppHoldOffUntilMs = 0L
                }
            }
        }
    }

    private fun runAudioLoop(record: AudioRecord, track: AudioTrack, initialLowLatency: Boolean) {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            record.startRecording()
            track.play()

            val buf = ShortArray(BLOCK_SHORTS_STANDARD)
            var appliedLowLatency = initialLowLatency
            var framesWritten = 0L
            var lastMeterNs = System.nanoTime()
            var winInPeak = 0
            var winInSumSq = 0.0
            var winOutPeak = 0
            var winOutSumSq = 0.0
            var winSamples = 0L

            while (loopRunning.get()) {
                val lowLatencyNow = isLowLatencyActive()
                if (lowLatencyNow != appliedLowLatency) {
                    applyTrackBufferSizeForMode(track, lowLatencyNow)
                    appliedLowLatency = lowLatencyNow
                }

                val targetShorts = if (lowLatencyNow) BLOCK_SHORTS_LOW_LATENCY else BLOCK_SHORTS_STANDARD
                val read = record.read(buf, 0, targetShorts)
                if (read < 0) {
                    if (loopRunning.get()) {
                        lastError = "AudioRecord.read error: $read"
                    }
                    break
                }
                if (read == 0) continue

                for (i in 0 until read) {
                    val s = buf[i].toInt()
                    val a = abs(s)
                    if (a > winInPeak) winInPeak = a
                    winInSumSq += s.toDouble() * s.toDouble()
                }

                // Always keep DSP biquad filter state continuous across every captured block.
                processor.processInterleavedPcm16(buf, read)
                val blockFrames = (read / 2).toLong()
                framesProcessed.addAndGet(blockFrames)

                val queuedFrames = queueController.computeQueuedFrames(framesWritten, track.playbackHeadPosition)
                lastQueuedFrames = queuedFrames

                val decision = queueController.evaluateAndShape(
                    pcm16 = buf,
                    sampleCount = read,
                    queuedFrames = queuedFrames,
                    lowLatencyActive = lowLatencyNow
                )

                if (decision == LatencyQueueController.Decision.DROP_SILENCE ||
                    decision == LatencyQueueController.Decision.DROP_AUDIO
                ) {
                    lowLatencyDroppedBlocks.incrementAndGet()
                } else {
                    for (i in 0 until read) {
                        val s = buf[i].toInt()
                        val a = abs(s)
                        if (a > winOutPeak) winOutPeak = a
                        winOutSumSq += s.toDouble() * s.toDouble()
                    }
                    winSamples += read

                    val written = track.write(buf, 0, read)
                    if (written < 0) {
                        if (loopRunning.get()) {
                            lastError = "AudioTrack.write error: $written"
                        }
                        break
                    }
                    if (written > 0) {
                        framesWritten += (written / 2).toLong()
                    }
                }

                val now = System.nanoTime()
                if (now - lastMeterNs >= 180_000_000L) {
                    inPeakDbfs = sampleToDbfs(winInPeak.toDouble())
                    inRmsDbfs = if (winSamples > 0) sampleToDbfs(sqrt(winInSumSq / winSamples)) else -120.0
                    outPeakDbfs = sampleToDbfs(winOutPeak.toDouble())
                    outRmsDbfs = if (winSamples > 0) sampleToDbfs(sqrt(winOutSumSq / winSamples)) else -120.0
                    winInPeak = 0
                    winInSumSq = 0.0
                    winOutPeak = 0
                    winOutSumSq = 0.0
                    winSamples = 0L
                    lastMeterNs = now
                    if (lowLatencyUidSet.isNotEmpty() || perAppLowLatencyActive) {
                        refreshPerAppLowLatencyFromManager()
                    }
                }
            }
        } catch (t: Throwable) {
            if (loopRunning.get()) {
                Log.e(TAG, "Audio loop error", t)
                lastError = "${t.javaClass.simpleName}: ${t.message}"
            }
        } finally {
            loopRunning.set(false)
        }
    }

    private fun sampleToDbfs(sampleAmp: Double): Double {
        if (sampleAmp <= 0.5) return -120.0
        return (20.0 * log10(sampleAmp / 32768.0)).coerceIn(-120.0, 0.0)
    }

    override fun updateDspConfig(dspConfigJson: String) {
        try {
            val parsed = ActiveDspConfig.fromJsonString(dspConfigJson)
            processor.updateConfig(parsed)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to update DSP config", t)
            lastError = "Config update error: ${t.message}"
        }
    }

    override fun updateLatencyPolicy(globalLowLatency: Boolean, lowLatencyUids: IntArray?) {
        synchronized(playbackLock) {
            globalLowLatencyEnabled = globalLowLatency
            lowLatencyUidSet = lowLatencyUids?.toHashSet() ?: emptySet()
            perAppHoldOffUntilMs = 0L
            refreshPerAppLowLatencyFromManager()
        }
    }

    override fun stopLoopback() {
        synchronized(stateLock) {
            stopInternal()
        }
    }

    private fun stopInternal() {
        loopRunning.set(false)
        excludedUidCount = 0
        activeUserExcludedUids = IntArray(0)
        perAppLowLatencyActive = false
        perAppHoldOffUntilMs = 0L
        lastQueuedFrames = 0L
        currentTrackBufferFrames = 0
        unregisterPlaybackMonitor()

        try {
            activeRecord?.let { r ->
                if (r.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    r.stop()
                }
                r.release()
            }
        } catch (_: Throwable) {
        }
        activeRecord = null

        try {
            activeTrack?.let { t ->
                if (t.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    t.stop()
                }
                t.release()
            }
        } catch (_: Throwable) {
        }
        activeTrack = null

        try {
            workerThread?.join(1200L)
        } catch (_: Throwable) {
        }
        workerThread = null

        audioPolicyObj?.let { policy ->
            try {
                val audioPolicyClass = Class.forName("android.media.audiopolicy.AudioPolicy")
                val am = audioManager
                if (am != null) {
                    AudioManager::class.java
                        .getMethod("unregisterAudioPolicy", audioPolicyClass)
                        .invoke(am, policy)
                } else {
                    val unregStatic = AudioManager::class.java
                        .getDeclaredMethod("unregisterAudioPolicyAsyncStatic", audioPolicyClass)
                    unregStatic.isAccessible = true
                    unregStatic.invoke(null, policy)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "unregisterAudioPolicy error", t)
            }
        }
        audioPolicyObj = null
    }

    override fun updateAutoDevicePolicy(policyJson: String) {
        val policy = try {
            AutonomousDevicePolicy.fromJsonString(policyJson)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to parse auto device policy", t)
            return
        }
        synchronized(stateLock) {
            if (!policy.enabled || destroying.get()) {
                autonomousPolicy = null
                autonomousLastDeviceKey = null
                autonomousPausedByRule = false
                autonomousSwitchCount = 0
                unregisterAutonomousDeviceMonitor()
                return
            }

            if (policy.clientAppUid > 0) {
                savedClientAppUid = policy.clientAppUid
            }
            savedRawExcludedUids = policy.excludedUids.toIntArray()
            synchronized(playbackLock) {
                globalLowLatencyEnabled = policy.globalLowLatency
                lowLatencyUidSet = policy.lowLatencyUids.toHashSet()
            }

            autonomousPolicy = policy
            autonomousCurrentCommonId = policy.selectedPresetId
            autonomousCurrentIndepId = policy.selectedIndependentPresetId
            autonomousCurrentDraftIndep = policy.draftIndependentPreset
            autonomousCurrentSwap = policy.channelSwap
            autonomousPausedByRule = policy.pausedByDeviceRule
            autonomousLastDeviceKey = policy.activeDeviceKey
            autonomousSwitchCount = 0

            ensureAutonomousDeviceMonitorRegistered()
            scheduleAutonomousDeviceEvaluation(includeFollowUps = false)
        }
    }

    private val lateDeviceEvalRunnable = Runnable {
        evaluateAutonomousDeviceChange()
    }

    private fun scheduleAutonomousDeviceEvaluation(includeFollowUps: Boolean) {
        val handler = deviceMonitorHandler ?: return
        handler.removeCallbacks(delayedDeviceEvalRunnable)
        handler.removeCallbacks(lateDeviceEvalRunnable)
        handler.post(delayedDeviceEvalRunnable)
        if (includeFollowUps) {
            handler.postDelayed(delayedDeviceEvalRunnable, 250L)
            handler.postDelayed(lateDeviceEvalRunnable, 700L)
        }
    }

    private fun ensureAutonomousDeviceMonitorRegistered() {
        val ctx = ensureShellContext()
        val am = audioManager ?: ctx.getSystemService(AudioManager::class.java) ?: return
        audioManager = am
        synchronized(deviceMonitorLock) {
            if (deviceMonitorRegistered || destroying.get()) return
            val ht = HandlerThread("peq-device-monitor").apply {
                isDaemon = true
                start()
            }
            val handler = Handler(ht.looper)
            val cb = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    scheduleAutonomousDeviceEvaluation(includeFollowUps = true)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    scheduleAutonomousDeviceEvaluation(includeFollowUps = true)
                }
            }
            deviceMonitorThread = ht
            deviceMonitorHandler = handler
            deviceMonitorCallback = cb
            am.registerAudioDeviceCallback(cb, handler)
            deviceMonitorRegistered = true
        }
    }

    private fun unregisterAutonomousDeviceMonitor() {
        val am = audioManager
        synchronized(deviceMonitorLock) {
            val cb = deviceMonitorCallback
            val ht = deviceMonitorThread
            val handler = deviceMonitorHandler
            deviceMonitorRegistered = false
            deviceMonitorCallback = null
            deviceMonitorThread = null
            deviceMonitorHandler = null
            handler?.removeCallbacks(delayedDeviceEvalRunnable)
            handler?.removeCallbacks(lateDeviceEvalRunnable)
            if (am != null && cb != null) {
                try {
                    am.unregisterAudioDeviceCallback(cb)
                } catch (_: Throwable) {
                }
            }
            try {
                ht?.quitSafely()
            } catch (_: Throwable) {
            }
        }
    }

    private fun evaluateAutonomousDeviceChange() {
        synchronized(stateLock) {
            val policy = autonomousPolicy
            if (policy == null || !policy.enabled || destroying.get()) return
            val am = audioManager ?: return
            val activeSnapshot = queryOutputDevicesState(am) { _, product, _, category ->
                product.ifBlank { category.name }
            }.activeDevice ?: return
            val currentKey = activeSnapshot.deviceKey
            if (currentKey == autonomousLastDeviceKey) return

            val outcome = resolveDeviceRoutingOutcome(
                deviceKey = currentKey,
                deviceRules = policy.deviceRules,
                presets = policy.presets,
                independentPresets = policy.independentPresets,
                currentCommonPresetId = autonomousCurrentCommonId,
                currentIndependentPresetId = autonomousCurrentIndepId,
                draftIndependentPreset = autonomousCurrentDraftIndep,
                currentChannelSwap = autonomousCurrentSwap,
                swapPosition = policy.swapPosition
            )

            autonomousLastDeviceKey = currentKey
            autonomousCurrentCommonId = outcome.selectedCommonPresetId
            autonomousCurrentIndepId = outcome.selectedIndependentPresetId
            autonomousCurrentDraftIndep = outcome.draftIndependentPreset
            autonomousCurrentSwap = outcome.channelSwap
            autonomousPausedByRule = outcome.pausedByDeviceRule
            autonomousSwitchCount++

            Log.i(
                TAG,
                "Autonomous device switch: device=$currentKey paused=${outcome.pausedByDeviceRule} " +
                    "common=${outcome.selectedCommonPresetId} indep=${outcome.selectedIndependentPresetId} " +
                    "swap=${outcome.channelSwap}"
            )

            if (outcome.pausedByDeviceRule) {
                if (loopRunning.get()) {
                    stopInternal()
                }
                return
            }

            val config = ActiveDspConfig(
                bypass = false,
                channelSwap = outcome.channelSwap,
                swapPosition = outcome.swapPosition,
                preset = outcome.effectivePreset
            )
            if (loopRunning.get()) {
                processor.updateConfig(config)
            } else if (savedClientAppUid > 0) {
                startLoopbackLocked(
                    clientAppUid = savedClientAppUid,
                    excludedUids = savedRawExcludedUids,
                    globalLowLatency = globalLowLatencyEnabled,
                    lowLatencyUids = lowLatencyUidSet.toIntArray(),
                    parsedConfig = config
                )
            }
        }
    }

    override fun getStatusJson(): String {
        return JSONObject().apply {
            put("running", loopRunning.get())
            put("uid", Process.myUid())
            put("sampleRate", SAMPLE_RATE)
            put("framesProcessed", framesProcessed.get())
            put("excludedUidCount", excludedUidCount)
            put("userExcludedUids", JSONArray().apply {
                for (u in activeUserExcludedUids) put(u)
            })
            put("globalLowLatency", globalLowLatencyEnabled)
            put("lowLatencyActive", isLowLatencyActive())
            put("perAppLowLatencyActive", perAppLowLatencyActive)
            put("lowLatencyDroppedBlocks", lowLatencyDroppedBlocks.get())
            put("queuedFrames", lastQueuedFrames)
            put("trackBufferFrames", currentTrackBufferFrames)
            put("inPeakDbfs", inPeakDbfs)
            put("inRmsDbfs", inRmsDbfs)
            put("outPeakDbfs", outPeakDbfs)
            put("outRmsDbfs", outRmsDbfs)
            put("autonomousPolicyActive", autonomousPolicy?.enabled == true)
            put("autonomousSwitchCount", autonomousSwitchCount)
            put("activeDeviceKey", autonomousLastDeviceKey ?: JSONObject.NULL)
            put("commonPresetId", autonomousCurrentCommonId)
            put("independentPresetId", autonomousCurrentIndepId ?: JSONObject.NULL)
            put("hasDraftIndependent", autonomousCurrentDraftIndep != null)
            put("channelSwap", autonomousCurrentSwap)
            put("pausedByDeviceRule", autonomousPausedByRule)
            put("error", lastError ?: JSONObject.NULL)
        }.toString()
    }

    override fun registerClientToken(clientToken: IBinder?) {
        if (destroying.get()) return
        if (clientToken == null) {
            synchronized(clientLock) {
                unlinkClientTokenLocked()
            }
            return
        }
        var clientAlreadyDead = false
        synchronized(clientLock) {
            if (linkedClientToken === clientToken) return
            unlinkClientTokenLocked()
            val recipient = IBinder.DeathRecipient {
                Log.w(TAG, "Client process binder died; shutting down UserService")
                destroy()
            }
            try {
                clientToken.linkToDeath(recipient, 0)
                linkedClientToken = clientToken
                clientDeathRecipient = recipient
            } catch (t: Throwable) {
                Log.w(TAG, "Client token already dead during registration; destroying", t)
                clientAlreadyDead = true
            }
        }
        if (clientAlreadyDead) {
            destroy()
        }
    }

    private fun unlinkClientTokenLocked() {
        val token = linkedClientToken
        val recipient = clientDeathRecipient
        linkedClientToken = null
        clientDeathRecipient = null
        if (token != null && recipient != null) {
            try {
                token.unlinkToDeath(recipient, 0)
            } catch (_: Throwable) {
            }
        }
    }

    override fun destroy() {
        if (!destroying.compareAndSet(false, true)) return
        Log.i(TAG, "Destroying AudioLoopbackUserService cleanly")
        synchronized(clientLock) {
            unlinkClientTokenLocked()
        }
        unregisterAutonomousDeviceMonitor()
        stopLoopback()
        exitProcess(0)
    }
}
