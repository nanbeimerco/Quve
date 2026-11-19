package com.superglobalpeq.app.dsp

import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.model.PeqBand
import com.superglobalpeq.app.model.ShootoutRoundConfig
import com.superglobalpeq.app.model.SwapPosition
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Static loudness estimate used for the first moments of a round, before the engine has
 * measured the actual music, plus the shared attenuation that keeps every contestant out of
 * the soft limiter.
 */
object ShootoutLoudness {
    // 1.5 dB of safety plus the most that loudness tracking can raise one side.
    private const val HEADROOM_MARGIN_DB = 4.5
    private const val MIN_TRIM_DB = -48.0
    private const val SAMPLE_RATE = 48000.0

    private val kWeightDb: FloatArray by lazy {
        FloatArray(FrequencyResponseCalculator.defaultFrequencies.size) { index ->
            val f = FrequencyResponseCalculator.defaultFrequencies[index].toDouble()
            (K_SHELF.magnitudeDbAt(f, SAMPLE_RATE) + K_HIGH_PASS.magnitudeDbAt(f, SAMPLE_RATE)).toFloat()
        }
    }

    /** K-weighted gain of [preset] for a pink-spectrum reference; log-spaced bins carry equal power. */
    fun staticGainDb(preset: EqPreset): Double {
        val curves = FrequencyResponseCalculator.calculate(preset, SAMPLE_RATE)
        var weighted = 0.0
        var reference = 0.0
        for (i in curves.frequenciesHz.indices) {
            val k = 10.0.pow(kWeightDb[i] / 10.0)
            val left = 10.0.pow(curves.totalLeftDb[i] / 10.0)
            val right = 10.0.pow(curves.totalRightDb[i] / 10.0)
            weighted += k * 0.5 * (left + right)
            reference += k
        }
        return 10.0 * log10(weighted.coerceAtLeast(1e-12) / reference)
    }

    /** Per-contestant trims: loudness aligned, with one shared headroom offset applied to all. */
    fun trimsDb(presets: List<EqPreset>): List<Double> {
        if (presets.isEmpty()) return emptyList()
        val matched = presets.map { -staticGainDb(it) }
        var loudestPeak = Double.NEGATIVE_INFINITY
        presets.forEachIndexed { index, preset ->
            val curves = FrequencyResponseCalculator.calculate(preset, SAMPLE_RATE)
            loudestPeak = max(loudestPeak, max(curves.peakLeftDb, curves.peakRightDb) + matched[index])
        }
        val headroom = minOf(0.0, -loudestPeak - HEADROOM_MARGIN_DB)
        return matched.map { (it + headroom).coerceAtLeast(MIN_TRIM_DB) }
    }

    internal val K_SHELF = BiquadCoefficients(
        1.53512485958697,
        -2.69169618940638,
        1.19839281085285,
        -1.69065929318241,
        0.73248077421585
    )
    internal val K_HIGH_PASS = BiquadCoefficients(
        1.0,
        -2.0,
        1.0,
        -1.99004745483398,
        0.99007225036621
    )
}

private fun dbToGain(db: Double): Double = 10.0.pow(db / 20.0)

private fun gainToDb(gain: Double): Double = 20.0 * log10(gain.coerceAtLeast(1e-9))

private class BiquadBank(bands: List<PeqBand>, sampleRate: Double) {
    private val coeffs: Array<BiquadCoefficients> =
        bands.filter { it.enabled }.map { BiquadCoefficients.fromBand(it, sampleRate) }.toTypedArray()
    private val z1 = DoubleArray(coeffs.size)
    private val z2 = DoubleArray(coeffs.size)

    fun process(buffer: DoubleArray, frames: Int) {
        for (section in coeffs.indices) {
            val c = coeffs[section]
            val b0 = c.b0
            val b1 = c.b1
            val b2 = c.b2
            val a1 = c.a1
            val a2 = c.a2
            var s1 = z1[section]
            var s2 = z2[section]
            for (i in 0 until frames) {
                val x = buffer[i]
                val y = b0 * x + s1
                s1 = b1 * x - a1 * y + s2
                s2 = b2 * x - a2 * y
                buffer[i] = y
            }
            z1[section] = s1
            z2[section] = s2
        }
    }
}

/** Allocation-free copy of the live EQ signal path, with its own filter memory. */
private class ShootoutChain(preset: EqPreset, sampleRate: Double) {
    private val commonLeft = BiquadBank(preset.commonBands, sampleRate)
    private val commonRight = BiquadBank(preset.commonBands, sampleRate)
    private val independentLeft = BiquadBank(preset.leftBands, sampleRate)
    private val independentRight = BiquadBank(preset.rightBands, sampleRate)
    private val commonGain = 10.0.pow(preset.commonPreampDb / 20.0)
    private val leftGain = 10.0.pow(preset.leftPreampDb / 20.0)
    private val rightGain = 10.0.pow(preset.rightPreampDb / 20.0)
    private val swapBefore = preset.channelSwap && preset.swapPosition == SwapPosition.BEFORE_INDEPENDENT_EQ
    private val swapAfter = preset.channelSwap && preset.swapPosition == SwapPosition.AFTER_INDEPENDENT_EQ

    fun process(
        inputLeft: DoubleArray,
        inputRight: DoubleArray,
        outputLeft: DoubleArray,
        outputRight: DoubleArray,
        frames: Int
    ) {
        for (i in 0 until frames) {
            outputLeft[i] = inputLeft[i] * commonGain
            outputRight[i] = inputRight[i] * commonGain
        }
        commonLeft.process(outputLeft, frames)
        commonRight.process(outputRight, frames)
        if (swapBefore) swap(outputLeft, outputRight, frames)
        for (i in 0 until frames) {
            outputLeft[i] *= leftGain
            outputRight[i] *= rightGain
        }
        independentLeft.process(outputLeft, frames)
        independentRight.process(outputRight, frames)
        if (swapAfter) swap(outputLeft, outputRight, frames)
    }

    private fun swap(left: DoubleArray, right: DoubleArray, frames: Int) {
        for (i in 0 until frames) {
            val tmp = left[i]
            left[i] = right[i]
            right[i] = tmp
        }
    }
}

private class KWeightMeter {
    private var shelfL1 = 0.0
    private var shelfL2 = 0.0
    private var highL1 = 0.0
    private var highL2 = 0.0
    private var shelfR1 = 0.0
    private var shelfR2 = 0.0
    private var highR1 = 0.0
    private var highR2 = 0.0

    /** Returns the summed K-weighted energy of both channels. */
    fun accumulate(left: DoubleArray, right: DoubleArray, frames: Int): Double {
        val s = ShootoutLoudness.K_SHELF
        val h = ShootoutLoudness.K_HIGH_PASS
        var sum = 0.0
        for (i in 0 until frames) {
            val xl = left[i]
            val sl = s.b0 * xl + shelfL1
            shelfL1 = s.b1 * xl - s.a1 * sl + shelfL2
            shelfL2 = s.b2 * xl - s.a2 * sl
            val yl = h.b0 * sl + highL1
            highL1 = h.b1 * sl - h.a1 * yl + highL2
            highL2 = h.b2 * sl - h.a2 * yl

            val xr = right[i]
            val sr = s.b0 * xr + shelfR1
            shelfR1 = s.b1 * xr - s.a1 * sr + shelfR2
            shelfR2 = s.b2 * xr - s.a2 * sr
            val yr = h.b0 * sr + highR1
            highR1 = h.b1 * sr - h.a1 * yr + highR2
            highR2 = h.b2 * sr - h.a2 * yr

            sum += yl * yl + yr * yr
        }
        return sum
    }
}

private class ShootoutRound(config: ShootoutRoundConfig, sampleRate: Double) {
    val id = config.roundId
    val chainA = ShootoutChain(config.presetA, sampleRate)
    val chainB = ShootoutChain(config.presetB, sampleRate)
    val meterA = KWeightMeter()
    val meterB = KWeightMeter()
    val staticTrimA = config.trimADb
    val staticTrimB = config.trimBDb
    val meanTrim = 0.5 * (staticTrimA + staticTrimB)
    var trimA = staticTrimA
    var trimB = staticTrimB
    var gainA = dbToGain(staticTrimA)
    var gainB = dbToGain(staticTrimB)
    var targetGainA = gainA
    var targetGainB = gainB
    var energyA = 0.0
    var energyB = 0.0
    var hopFrames = 0
    var averageA = 0.0
    var averageB = 0.0
    var measuredHops = 0
}

/**
 * Runs two contestant chains beside the live EQ and crossfades between them.
 *
 * Both contestants always process the same audio, so a switch is a pure crossfade between two
 * settled filters and their loudness difference can be measured on the music itself. The live
 * EQ also keeps running, which is what allows an instant, click-free return to it.
 */
class ShootoutProcessor(private val sampleRate: Double = 48000.0) {
    private val pendingRound = AtomicReference<ShootoutRound?>(null)

    @Volatile
    private var engaged = false

    @Volatile
    private var stopRequested = false

    @Volatile
    private var requestedSource = SOURCE_A

    @Volatile
    private var lastMatchErrorDb = 0.0

    @Volatile
    private var activeRoundId = -1L

    private val inputLeft = DoubleArray(MAX_FRAMES)
    private val inputRight = DoubleArray(MAX_FRAMES)
    private val normalLeft = DoubleArray(MAX_FRAMES)
    private val normalRight = DoubleArray(MAX_FRAMES)
    private val aLeft = DoubleArray(MAX_FRAMES)
    private val aRight = DoubleArray(MAX_FRAMES)
    private val bLeft = DoubleArray(MAX_FRAMES)
    private val bRight = DoubleArray(MAX_FRAMES)
    private val outgoingLeft = DoubleArray(MAX_FRAMES)
    private val outgoingRight = DoubleArray(MAX_FRAMES)

    private val gainSmoothing = 1.0 - exp(-1.0 / (sampleRate * GAIN_SMOOTHING_SECONDS))
    private val averageSmoothing = 1.0 - exp(-(HOP_FRAMES / sampleRate) / LOUDNESS_TRACKING_SECONDS)

    private var round: ShootoutRound? = null
    private var outgoingChain: ShootoutChain? = null
    private var outgoingGain = 1.0
    private var source = SOURCE_NORMAL
    private var fadeFrom = SOURCE_NORMAL
    private var fadeRemaining = 0
    private var fadeLength = 0
    private var warmupRemaining = 0

    val isEngaged: Boolean
        get() = engaged

    val matchErrorDb: Double
        get() = lastMatchErrorDb

    val currentRoundId: Long
        get() = activeRoundId

    /** Queues a round from a binder thread; its filters are built here, off the audio thread. */
    fun startRound(config: ShootoutRoundConfig) {
        val next = ShootoutRound(config, sampleRate)
        requestedSource = SOURCE_A
        stopRequested = false
        pendingRound.set(next)
        engaged = true
    }

    fun selectSide(side: Int) {
        requestedSource = if (side == SIDE_B) SOURCE_B else SOURCE_A
    }

    /** Fades back to the live EQ, then disengages. */
    fun requestStop() {
        pendingRound.set(null)
        stopRequested = true
    }

    /** Immediately forgets all state. Only call while the audio thread is stopped. */
    fun reset() {
        pendingRound.set(null)
        engaged = false
        stopRequested = false
        requestedSource = SOURCE_A
        lastMatchErrorDb = 0.0
        activeRoundId = -1L
        round = null
        outgoingChain = null
        source = SOURCE_NORMAL
        fadeRemaining = 0
        warmupRemaining = 0
    }

    fun process(pcm: ShortArray, sampleCount: Int, normal: StereoPeqProcessor) {
        val frames = sampleCount / 2
        if (frames > MAX_FRAMES) {
            normal.processInterleavedPcm16(pcm, sampleCount)
            return
        }
        for (i in 0 until frames) {
            inputLeft[i] = pcm[i * 2] * INV_32768
            inputRight[i] = pcm[i * 2 + 1] * INV_32768
        }
        normal.processInterleavedPcm16(pcm, sampleCount)
        for (i in 0 until frames) {
            normalLeft[i] = pcm[i * 2] * INV_32768
            normalRight[i] = pcm[i * 2 + 1] * INV_32768
        }

        adoptPendingRound()
        processVoices(frames)
        val wroteMix = mix(pcm, frames)
        if (warmupRemaining > 0) {
            warmupRemaining = (warmupRemaining - frames).coerceAtLeast(0)
        }
        if (!wroteMix) finishIfStopped()
    }

    private fun adoptPendingRound() {
        if (fadeRemaining > 0 || stopRequested) return
        val next = pendingRound.getAndSet(null) ?: return
        val current = round
        if (current != null && (source == SOURCE_A || source == SOURCE_B)) {
            outgoingChain = if (source == SOURCE_A) current.chainA else current.chainB
            outgoingGain = if (source == SOURCE_A) current.gainA else current.gainB
            source = SOURCE_OUTGOING
        }
        round = next
        activeRoundId = next.id
        warmupRemaining = WARMUP_FRAMES
    }

    private fun processVoices(frames: Int) {
        outgoingChain?.let { chain ->
            chain.process(inputLeft, inputRight, outgoingLeft, outgoingRight, frames)
            for (i in 0 until frames) {
                outgoingLeft[i] *= outgoingGain
                outgoingRight[i] *= outgoingGain
            }
        }
        val current = round ?: return
        current.chainA.process(inputLeft, inputRight, aLeft, aRight, frames)
        current.chainB.process(inputLeft, inputRight, bLeft, bRight, frames)
        current.energyA += current.meterA.accumulate(aLeft, aRight, frames)
        current.energyB += current.meterB.accumulate(bLeft, bRight, frames)
        current.hopFrames += frames
        if (current.hopFrames >= HOP_FRAMES) {
            if (warmupRemaining > 0) {
                // New filters are still settling, so this energy says nothing about the music.
                current.energyA = 0.0
                current.energyB = 0.0
                current.hopFrames = 0
            } else {
                updateLoudness(current)
            }
        }

        var gainA = current.gainA
        var gainB = current.gainB
        val targetA = current.targetGainA
        val targetB = current.targetGainB
        for (i in 0 until frames) {
            gainA += (targetA - gainA) * gainSmoothing
            gainB += (targetB - gainB) * gainSmoothing
            aLeft[i] *= gainA
            aRight[i] *= gainA
            bLeft[i] *= gainB
            bRight[i] *= gainB
        }
        current.gainA = gainA
        current.gainB = gainB
    }

    private fun updateLoudness(current: ShootoutRound) {
        val samples = current.hopFrames * 2.0
        val meanA = current.energyA / samples
        val meanB = current.energyB / samples
        current.energyA = 0.0
        current.energyB = 0.0
        current.hopFrames = 0
        if (max(meanA, meanB) < SILENCE_POWER || meanA <= 0.0 || meanB <= 0.0) return

        val staticDifference = current.staticTrimA - current.staticTrimB
        if (current.measuredHops == 0) {
            // Only the A/B difference is prior knowledge; the shared trim must not scale the average.
            val middle = sqrt(meanA * meanB)
            val halfDifference = 0.5 * staticDifference
            current.averageA = middle * 10.0.pow(-halfDifference / 10.0)
            current.averageB = middle * 10.0.pow(halfDifference / 10.0)
        }
        // Rounds are short, so early measurements count more until the normal time constant takes over.
        val smoothing = max(1.0 / (current.measuredHops + FAST_START_PRIOR_HOPS + 1), averageSmoothing)
        current.averageA += (meanA - current.averageA) * smoothing
        current.averageB += (meanB - current.averageB) * smoothing
        current.measuredHops++

        val measuredDifference = 10.0 * log10(current.averageB / current.averageA)
        val difference = measuredDifference.coerceIn(
            staticDifference - MAX_TRACKING_DB * 2.0,
            staticDifference + MAX_TRACKING_DB * 2.0
        )
        current.trimA = current.meanTrim + difference * 0.5
        current.trimB = current.meanTrim - difference * 0.5
        current.targetGainA = dbToGain(current.trimA)
        current.targetGainB = dbToGain(current.trimB)
        lastMatchErrorDb = 10.0 * log10(meanA / meanB) + gainToDb(current.gainA) - gainToDb(current.gainB)
    }

    /** Returns false only when the block is entirely the live EQ, already present in [pcm]. */
    private fun mix(pcm: ShortArray, frames: Int): Boolean {
        var frame = 0
        var touched = false
        while (frame < frames) {
            if (fadeRemaining <= 0) beginFadeIfNeeded()
            if (fadeRemaining > 0) {
                val count = minOf(frames - frame, fadeRemaining)
                val fromLeft = leftOf(fadeFrom)
                val fromRight = rightOf(fadeFrom)
                val toLeft = leftOf(source)
                val toRight = rightOf(source)
                val length = fadeLength.toDouble()
                for (i in frame until frame + count) {
                    val position = 1.0 - (fadeRemaining - (i - frame)) / length
                    pcm[i * 2] = quantizeSoftLimited(
                        fromLeft[i] + (toLeft[i] - fromLeft[i]) * position
                    )
                    pcm[i * 2 + 1] = quantizeSoftLimited(
                        fromRight[i] + (toRight[i] - fromRight[i]) * position
                    )
                }
                fadeRemaining -= count
                frame += count
                touched = true
                if (fadeRemaining == 0 && fadeFrom == SOURCE_OUTGOING) outgoingChain = null
            } else {
                if (source == SOURCE_NORMAL) return touched
                val left = leftOf(source)
                val right = rightOf(source)
                for (i in frame until frames) {
                    pcm[i * 2] = quantizeSoftLimited(left[i])
                    pcm[i * 2 + 1] = quantizeSoftLimited(right[i])
                }
                return true
            }
        }
        return true
    }

    private fun beginFadeIfNeeded() {
        val target = when {
            stopRequested -> SOURCE_NORMAL
            round == null || warmupRemaining > 0 || pendingRound.get() != null -> return
            else -> requestedSource
        }
        if (target == source) return
        fadeFrom = source
        fadeLength = when {
            source == SOURCE_NORMAL || target == SOURCE_NORMAL -> NORMAL_FADE_FRAMES
            source == SOURCE_OUTGOING -> ROUND_FADE_FRAMES
            else -> SIDE_FADE_FRAMES
        }
        fadeRemaining = fadeLength
        source = target
    }

    private fun finishIfStopped() {
        if (!stopRequested || source != SOURCE_NORMAL || fadeRemaining > 0) return
        round = null
        outgoingChain = null
        activeRoundId = -1L
        stopRequested = false
        engaged = false
        if (pendingRound.get() != null) engaged = true
    }

    private fun leftOf(selected: Int): DoubleArray = when (selected) {
        SOURCE_A -> aLeft
        SOURCE_B -> bLeft
        SOURCE_OUTGOING -> outgoingLeft
        else -> normalLeft
    }

    private fun rightOf(selected: Int): DoubleArray = when (selected) {
        SOURCE_A -> aRight
        SOURCE_B -> bRight
        SOURCE_OUTGOING -> outgoingRight
        else -> normalRight
    }

    companion object {
        const val SIDE_A = 0
        const val SIDE_B = 1
        const val MAX_FRAMES = 1024

        private const val SOURCE_NORMAL = 0
        private const val SOURCE_A = 1
        private const val SOURCE_B = 2
        private const val SOURCE_OUTGOING = 3

        private const val INV_32768 = 1.0 / 32768.0
        private const val SIDE_FADE_FRAMES = 1440 // 30 ms
        private const val ROUND_FADE_FRAMES = 2880 // 60 ms
        private const val NORMAL_FADE_FRAMES = 24000 // 500 ms
        private const val WARMUP_FRAMES = 4800 // 100 ms lets new filters settle before they are heard
        private const val HOP_FRAMES = 4800 // 100 ms
        private const val LOUDNESS_TRACKING_SECONDS = 1.0
        private const val FAST_START_PRIOR_HOPS = 3
        private const val GAIN_SMOOTHING_SECONDS = 0.1
        private const val MAX_TRACKING_DB = 3.0 // per side, so A and B can move 6 dB apart
        private const val SILENCE_POWER = 1e-7
    }
}
