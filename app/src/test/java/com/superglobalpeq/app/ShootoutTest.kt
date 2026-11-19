package com.superglobalpeq.app

import com.superglobalpeq.app.dsp.ShootoutLoudness
import com.superglobalpeq.app.dsp.ShootoutProcessor
import com.superglobalpeq.app.dsp.StereoPeqProcessor
import com.superglobalpeq.app.model.ActiveDspConfig
import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.model.FilterType
import com.superglobalpeq.app.model.IndependentPeqPreset
import com.superglobalpeq.app.model.PeqBand
import com.superglobalpeq.app.model.ShootoutOutcome
import com.superglobalpeq.app.model.ShootoutRoundConfig
import com.superglobalpeq.app.model.ShootoutSessionRecord
import com.superglobalpeq.app.model.ShootoutSessionStatus
import com.superglobalpeq.app.model.ShootoutVote
import com.superglobalpeq.app.model.SwapPosition
import com.superglobalpeq.app.model.SHOOTOUT_NO_INDEPENDENT_ID
import com.superglobalpeq.app.model.composeShootoutContestants
import com.superglobalpeq.app.model.toSetupSelection
import com.superglobalpeq.app.shootout.Glicko2
import com.superglobalpeq.app.shootout.GlickoGame
import com.superglobalpeq.app.shootout.GlickoRating
import com.superglobalpeq.app.shootout.ShootoutAnalyzer
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class ShootoutTest {
    private val bass = EqPreset(
        id = "bass",
        name = "Bass",
        commonBands = listOf(PeqBand(type = FilterType.LSC, frequencyHz = 105.0, gainDb = 7.0, q = 0.7))
    )
    private val bright = EqPreset(
        id = "bright",
        name = "Bright",
        commonBands = listOf(
            PeqBand(type = FilterType.PK, frequencyHz = 3000.0, gainDb = 3.0, q = 1.2),
            PeqBand(type = FilterType.HSC, frequencyHz = 7000.0, gainDb = 5.0, q = 0.7)
        )
    )
    private val flat = EqPreset(id = "flat", name = "Flat")

    private fun normal(preset: EqPreset = flat) = StereoPeqProcessor().apply {
        updateConfig(ActiveDspConfig(preset = preset))
    }

    private fun tone(frame: Long): Short {
        val t = frame / 48000.0
        return ((0.12 * sin(2 * PI * 80 * t) + 0.08 * sin(2 * PI * 220 * t)) * 32767).toInt().toShort()
    }

    private class ToneRun(val processor: ShootoutProcessor, val engineNormal: StereoPeqProcessor) {
        var frame = 0L
    }

    private fun ToneRun.block(frames: Int = 480): ShortArray {
        val pcm = ShortArray(frames * 2)
        for (i in 0 until frames) {
            val sample = tone(frame + i)
            pcm[i * 2] = sample
            pcm[i * 2 + 1] = sample
        }
        frame += frames
        if (processor.isEngaged) processor.process(pcm, pcm.size, engineNormal)
        else engineNormal.processInterleavedPcm16(pcm, pcm.size)
        return pcm
    }

    private fun ToneRun.blocks(count: Int): ShortArray {
        val output = ShortArray(count * 960)
        repeat(count) { block().copyInto(output, it * 960) }
        return output
    }

    /** Second difference of the left channel; a tone stays tiny, a click spikes it. */
    private fun maxSecondDifference(pcm: ShortArray): Int {
        var maximum = 0
        var i = 4
        while (i < pcm.size) {
            val value = abs(pcm[i] - 2 * pcm[i - 2] + pcm[i - 4])
            if (value > maximum) maximum = value
            i += 2
        }
        return maximum
    }

    private fun config(id: Long = 1L, trims: List<Double> = listOf(-8.0, -8.0)) = ShootoutRoundConfig(
        roundId = id,
        presetA = bass,
        presetB = bright,
        trimADb = trims[0],
        trimBDb = trims[1]
    )

    @Test
    fun glickoMatchesPublishedExample() {
        val result = Glicko2.update(
            GlickoRating(1500.0, 200.0, 0.06),
            listOf(
                GlickoGame(1400.0, 30.0, 1.0),
                GlickoGame(1550.0, 100.0, 0.0),
                GlickoGame(1700.0, 300.0, 0.0)
            )
        )
        assertEquals(1464.06, result.rating, 0.02)
        assertEquals(151.52, result.deviation, 0.02)
        assertEquals(0.05999, result.volatility, 0.0001)
    }

    private fun session(votes: List<ShootoutVote> = emptyList()) = ShootoutSessionRecord(
        id = "session",
        createdAtEpochMs = 1L,
        deviceKey = "speaker",
        deviceName = "Speaker",
        seed = 1234L,
        contestants = listOf(bass, bright, flat),
        votes = votes
    )

    @Test
    fun analysisCountsWinsTiesAndHeadToHead() {
        val analysis = ShootoutAnalyzer.analyze(
            session(
                listOf(
                    ShootoutVote("bass", "bright", ShootoutOutcome.A, 1L),
                    ShootoutVote("flat", "bass", ShootoutOutcome.B, 2L),
                    ShootoutVote("bright", "flat", ShootoutOutcome.TIE, 3L)
                )
            )
        )
        val top = analysis.standings.first()
        assertEquals("bass", top.contestantId)
        assertEquals(2, top.record.wins)
        assertEquals(1.0, top.record.scoreRate, 0.0)
        val bright = analysis.standings.first { it.contestantId == "bright" }
        assertEquals(0.25, bright.record.scoreRate, 0.0)
        assertEquals(1, analysis.recordBetween("bright", "flat").draws)
        assertEquals(1, analysis.recordBetween("bright", "bass").losses)
        assertEquals(4, analysis.ratingHistory.getValue("bass").size)
        assertEquals(1500f, analysis.ratingHistory.getValue("bass")[0], 0f)
    }

    @Test
    fun pairingIsRepeatableAndAvoidsImmediateRematch() {
        val first = ShootoutAnalyzer.nextPairing(session())
        assertEquals(first, ShootoutAnalyzer.nextPairing(session()))
        val voted = session(listOf(ShootoutVote(first.contestantA, first.contestantB, ShootoutOutcome.TIE, 1L)))
        val next = ShootoutAnalyzer.nextPairing(voted)
        assertNotEquals(
            setOf(first.contestantA, first.contestantB),
            setOf(next.contestantA, next.contestantB)
        )
    }

    @Test
    fun sessionSurvivesJsonRoundTrip() {
        val original = session(listOf(ShootoutVote("bass", "flat", ShootoutOutcome.B, 5L)))
            .copy(status = ShootoutSessionStatus.INTERRUPTED)
        val restored = checkNotNull(ShootoutSessionRecord.fromJson(JSONObject(original.toJson().toString())))
        assertEquals(original.votes, restored.votes)
        assertEquals(original.status, restored.status)
        assertEquals(original.contestants.map { it.id }, restored.contestants.map { it.id })
        assertEquals(7.0, restored.contestants[0].commonBands[0].gainDb, 0.0)
    }

    @Test
    fun everyCommonContestantUsesTheSingleIndependentEq() {
        val leftBand = PeqBand(type = FilterType.PK, frequencyHz = 2000.0, gainDb = -2.0, q = 2.0)
        val independent = IndependentPeqPreset(id = "lr", name = "Cal", leftBands = listOf(leftBand))
        val contestants = composeShootoutContestants(
            listOf(bass to "Bass", flat to "Flat"),
            independent,
            channelSwap = false,
            swapPosition = SwapPosition.BEFORE_INDEPENDENT_EQ
        )
        assertEquals(listOf("Bass", "Flat"), contestants.map { it.name })
        assertEquals(2, contestants.map { it.id }.toSet().size)

        val record = session().copy(contestants = contestants, contestantsComposed = true)
        val restored = checkNotNull(ShootoutSessionRecord.fromJson(JSONObject(record.toJson().toString())))
        contestants.forEach { contestant ->
            assertEquals(-2.0, restored.effectivePreset(contestant.id).leftBands.single().gainDb, 0.0)
        }
        assertEquals(1, restored.effectivePreset(contestants[0].id).commonBands.size)
        assertTrue(restored.effectivePreset(contestants[1].id).commonBands.isEmpty())
    }

    @Test
    fun setupSelectionIsRestoredFromSession() {
        val independent = IndependentPeqPreset(id = "lr", name = "Cal")
        val contestants = composeShootoutContestants(
            listOf(bass to "Bass", flat to "Flat"),
            independent,
            channelSwap = false,
            swapPosition = SwapPosition.BEFORE_INDEPENDENT_EQ
        )
        val withIndependent = session().copy(
            contestants = contestants,
            independentPreset = independent,
            contestantsComposed = true
        ).toSetupSelection()
        assertEquals(listOf("bass", "flat"), withIndependent.commonIds)
        assertEquals("lr", withIndependent.independentId)
        assertEquals(SHOOTOUT_NO_INDEPENDENT_ID, session().toSetupSelection().independentId)
    }

    @Test
    fun setupKeepsOnlyAnExplicitSwapChoice() {
        val inherited = session().copy(channelSwap = true).toSetupSelection()
        assertEquals(null, inherited.channelSwap)
        assertEquals(null, inherited.swapPosition)
        val chosen = session().copy(
            channelSwap = true,
            swapPosition = SwapPosition.AFTER_INDEPENDENT_EQ,
            channelSwapChosen = true
        )
        val restored = checkNotNull(ShootoutSessionRecord.fromJson(JSONObject(chosen.toJson().toString())))
        assertEquals(true, restored.toSetupSelection().channelSwap)
        assertEquals(SwapPosition.AFTER_INDEPENDENT_EQ, restored.toSetupSelection().swapPosition)
    }

    private fun swappedOutputPeaks(position: SwapPosition): Pair<Int, Int> {
        val preset = EqPreset(
            id = "swap",
            name = "Swap",
            leftPreampDb = -12.0,
            channelSwap = true,
            swapPosition = position
        )
        val processor = ShootoutProcessor()
        val live = normal()
        processor.startRound(ShootoutRoundConfig(1L, preset, preset, -6.0, -6.0))
        var frame = 0L
        var last = ShortArray(960)
        repeat(160) {
            val pcm = ShortArray(960)
            for (i in 0 until 480) {
                pcm[i * 2] = (sin(2 * PI * 1000 * (frame + i) / 48000.0) * 12000).toInt().toShort()
            }
            frame += 480
            processor.process(pcm, pcm.size, live)
            last = pcm
        }
        val left = last.filterIndexed { index, _ -> index % 2 == 0 }.maxOf { abs(it.toInt()) }
        val right = last.filterIndexed { index, _ -> index % 2 == 1 }.maxOf { abs(it.toInt()) }
        return left to right
    }

    @Test
    fun swapPositionIsAppliedToContestants() {
        val before = swappedOutputPeaks(SwapPosition.BEFORE_INDEPENDENT_EQ)
        val after = swappedOutputPeaks(SwapPosition.AFTER_INDEPENDENT_EQ)
        assertTrue("before=$before", before.first <= 1 && before.second > 1000)
        assertTrue("after=$after", after.first <= 1 && after.second > 100)
        assertEquals(10.0.pow(-12.0 / 20.0), after.second.toDouble() / before.second, 0.02)
    }

    private fun matchErrorAfter(blocks: Int, trims: List<Double>): Double {
        val run = ToneRun(ShootoutProcessor(), normal())
        run.processor.startRound(config(trims = trims))
        run.blocks(blocks)
        return run.processor.matchErrorDb
    }

    @Test
    fun loudnessTrackingSpeedDoesNotDependOnSharedTrim() {
        val blocks = 150 // 1.5 s, including the 100 ms filter warm-up
        val errors = listOf(6.0, 0.0, -10.0, -20.0).map { shared ->
            matchErrorAfter(blocks, listOf(shared + 1.5, shared - 1.5))
        }
        errors.forEach { assertEquals(errors[0], it, 1e-6) }
        assertTrue("errors=$errors", abs(errors[0]) < 2.0)
    }

    @Test
    fun trackingRaisesOneSideByAtMostThreeDb() {
        val processor = ShootoutProcessor()
        val run = ToneRun(processor, normal())
        // A is 12 dB louder, so tracking wants to raise B beyond its limit.
        val louder = flat.copy(id = "louder", commonPreampDb = 12.0)
        processor.startRound(ShootoutRoundConfig(1L, louder, flat, -12.0, -12.0))
        run.blocks(400)
        processor.selectSide(ShootoutProcessor.SIDE_B)
        run.blocks(100)
        val startFrame = run.frame
        val output = run.blocks(100)
        var inputPeak = 0
        for (i in 0 until 48000) inputPeak = maxOf(inputPeak, abs(tone(startFrame + i).toInt()))
        var outputPeak = 0
        var i = 0
        while (i < output.size) {
            outputPeak = maxOf(outputPeak, abs(output[i].toInt()))
            i += 2
        }
        assertEquals(-9.0, 20.0 * log10(outputPeak.toDouble() / inputPeak), 0.2)
    }

    @Test
    fun trimsAlignLoudnessAndLeaveHeadroom() {
        val presets = listOf(bass, bright, flat)
        val trims = ShootoutLoudness.trimsDb(presets)
        val aligned = presets.indices.map { ShootoutLoudness.staticGainDb(presets[it]) + trims[it] }
        assertTrue(aligned.max() - aligned.min() < 1e-6)
        assertTrue(trims.all { it < 0.0 })
    }

    @Test
    fun abSwitchHasNoClick() {
        val run = ToneRun(ShootoutProcessor(), normal())
        run.processor.startRound(config())
        run.blocks(100)
        val steady = maxSecondDifference(run.blocks(20))
        run.processor.selectSide(ShootoutProcessor.SIDE_B)
        val switching = maxSecondDifference(run.blocks(10))
        run.processor.selectSide(ShootoutProcessor.SIDE_A)
        val switchingBack = maxSecondDifference(run.blocks(10))
        assertTrue("switch=$switching steady=$steady", switching <= steady + 3)
        assertTrue("switch=$switchingBack steady=$steady", switchingBack <= steady + 3)
    }

    @Test
    fun newRoundHasNoClick() {
        val run = ToneRun(ShootoutProcessor(), normal())
        run.processor.startRound(config(1L))
        run.blocks(100)
        val steady = maxSecondDifference(run.blocks(20))
        run.processor.startRound(config(2L))
        val changing = maxSecondDifference(run.blocks(40))
        assertTrue("round=$changing steady=$steady", changing <= steady + 3)
    }

    @Test
    fun stopFadesBackToExactLiveEqAndDisengages() {
        val shootoutRun = ToneRun(ShootoutProcessor(), normal(bass))
        val referenceRun = ToneRun(ShootoutProcessor(), normal(bass))
        shootoutRun.processor.startRound(config())
        repeat(100) {
            shootoutRun.block()
            referenceRun.block()
        }
        shootoutRun.processor.requestStop()
        repeat(60) {
            shootoutRun.block()
            referenceRun.block()
        }
        assertFalse(shootoutRun.processor.isEngaged)
        assertEquals(-1L, shootoutRun.processor.currentRoundId)
        assertArrayEquals(referenceRun.block(), shootoutRun.block())
    }

    @Test
    fun liveEqIsAudibleOnlyUntilTheFirstFadeCompletes() {
        val run = ToneRun(ShootoutProcessor(), normal())
        run.processor.startRound(config(trims = listOf(-20.0, -20.0)))
        val start = run.block()
        val startPeak = start.maxOf { abs(it.toInt()) }
        run.blocks(100)
        val settledPeak = run.block().maxOf { abs(it.toInt()) }
        assertTrue("start=$startPeak settled=$settledPeak", settledPeak < startPeak)
    }

    @Test
    fun trackingMatchesLoudnessOfRealProgramme() {
        val random = Random(9)
        val processor = ShootoutProcessor()
        val live = normal()
        // Deliberately wrong static trims: tracking must correct them from the audio itself.
        processor.startRound(config(trims = listOf(-9.0, -7.0)))
        var low = 0.0
        val aOut = ArrayList<Short>()
        val bOut = ArrayList<Short>()
        fun render(blocks: Int, sink: MutableList<Short>?) {
            repeat(blocks) {
                val pcm = ShortArray(960)
                for (i in 0 until 480) {
                    low += (random.nextGaussian() - low) * 0.02
                    val sample = ((low * 0.5 + random.nextGaussian() * 0.012) * 32767)
                        .toInt().coerceIn(-32000, 32000).toShort()
                    pcm[i * 2] = sample
                    pcm[i * 2 + 1] = sample
                }
                processor.process(pcm, pcm.size, live)
                sink?.addAll(pcm.toList())
            }
        }
        render(600, null)
        render(200, aOut)
        processor.selectSide(ShootoutProcessor.SIDE_B)
        render(30, null)
        render(200, bOut)
        assertTrue("match=${processor.matchErrorDb}", abs(processor.matchErrorDb) < 1.0)
        assertTrue(aOut.isNotEmpty() && bOut.isNotEmpty())
        assertTrue(rmsDb(aOut) > -80.0 && rmsDb(bOut) > -80.0)
    }

    private fun rmsDb(samples: List<Short>): Double {
        var sum = 0.0
        samples.forEach { sum += it.toDouble() * it.toDouble() }
        return 20.0 * log10(sqrt(sum / samples.size).coerceAtLeast(1e-9) / 32768.0)
    }
}
