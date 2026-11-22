package com.superglobalpeq.app

import android.media.AudioDeviceInfo
import com.superglobalpeq.app.data.PeqPreferencesRepository
import com.superglobalpeq.app.dsp.EqualizerApoParser
import com.superglobalpeq.app.dsp.FrequencyResponseCalculator
import com.superglobalpeq.app.dsp.StereoPeqProcessor
import com.superglobalpeq.app.model.ActiveDspConfig
import com.superglobalpeq.app.model.AppAudioMode
import com.superglobalpeq.app.model.AutonomousDevicePolicy
import com.superglobalpeq.app.model.BundleSelectionOutcome
import com.superglobalpeq.app.model.DeviceCategory
import com.superglobalpeq.app.model.DeviceIndependentRuleAction
import com.superglobalpeq.app.model.DeviceRoutingRule
import com.superglobalpeq.app.model.DeviceRuleAction
import com.superglobalpeq.app.model.DeviceSwapRuleAction
import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.model.FilterType
import com.superglobalpeq.app.model.IndependentPeqPreset
import com.superglobalpeq.app.model.PREAMP_MAX_DB
import com.superglobalpeq.app.model.PREAMP_MIN_DB
import com.superglobalpeq.app.model.PREAMP_SLIDER_SPLIT_DB
import com.superglobalpeq.app.model.PREAMP_SLIDER_SPLIT_FRACTION
import com.superglobalpeq.app.model.PeqBand
import com.superglobalpeq.app.model.PeqChannelTarget
import com.superglobalpeq.app.model.SwapPosition
import com.superglobalpeq.app.model.clampPreampDb
import com.superglobalpeq.app.model.composeEffectivePreset
import com.superglobalpeq.app.model.evaluateSavedPresetSelection
import com.superglobalpeq.app.model.mergeExcludedUids
import com.superglobalpeq.app.model.preampDbToSliderPos
import com.superglobalpeq.app.model.resolveDeviceRoutingOutcome
import com.superglobalpeq.app.model.sliderPosToPreampDb
import com.superglobalpeq.app.ui.components.computeAdaptiveGraphYAxis
import com.superglobalpeq.app.service.IAudioLoopbackService
import com.superglobalpeq.app.service.LatencyQueueController
import com.superglobalpeq.app.service.UsbAudioDescriptor
import com.superglobalpeq.app.service.classifyOutputDeviceKeyAndCategory
import com.superglobalpeq.app.service.extractProcCmdlineProcessName
import com.superglobalpeq.app.service.isStaleSiblingPeqServiceProcess
import com.superglobalpeq.app.service.normalizeUserExcludedUids
import com.superglobalpeq.app.service.parseJsonIntArray
import com.superglobalpeq.app.service.parseProcRealUid
import com.superglobalpeq.app.service.resolveUsbAudioIdentity
import com.superglobalpeq.app.service.shouldRestartLoopback
import com.superglobalpeq.app.service.userServiceTagForPersistence
import com.superglobalpeq.app.ui.components.GraphSeries
import com.superglobalpeq.app.ui.components.formatInspectFrequency
import com.superglobalpeq.app.ui.components.hasIndependentChannelEq
import com.superglobalpeq.app.ui.components.interpolateInspectReadout
import com.superglobalpeq.app.ui.components.visibleSeriesOrder
import org.json.JSONArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BiquadAndApoTest {

    @Test
    fun parseDualChannelApoPresetSplitsCommonLeftAndRightCleanly() {
        val result = EqualizerApoParser.parse(SAMPLE_DUAL_CHANNEL_APO, "Dual Channel Test")
        val preset = result.preset

        assertEquals(0, result.warnings.size)
        assertEquals(-9.6, preset.commonPreampDb, 1e-6)
        assertEquals(10, preset.commonBands.size)
        assertEquals(FilterType.PK, preset.commonBands[0].type)
        assertEquals(56.0, preset.commonBands[0].frequencyHz, 1e-6)
        assertEquals(FilterType.LSC, preset.commonBands[2].type)
        assertEquals(FilterType.HSC, preset.commonBands[9].type)

        assertEquals(-1.4, preset.leftPreampDb, 1e-6)
        assertEquals(10, preset.leftBands.size)
        assertEquals(0.18, preset.leftBands[0].gainDb, 1e-6)

        assertEquals(-1.4, preset.rightPreampDb, 1e-6)
        assertEquals(10, preset.rightBands.size)
        assertEquals(-0.18, preset.rightBands[0].gainDb, 1e-6)
        assertEquals(0.54, preset.rightBands[9].gainDb, 1e-6)
    }

    @Test
    fun exportAndReimportPreservesAllSections() {
        val original = createSampleDualChannelPreset()
        val exported = EqualizerApoParser.export(original)
        val reimported = EqualizerApoParser.parse(exported, original.name).preset

        assertEquals(original.commonPreampDb, reimported.commonPreampDb, 1e-3)
        assertEquals(original.leftPreampDb, reimported.leftPreampDb, 1e-3)
        assertEquals(original.rightPreampDb, reimported.rightPreampDb, 1e-3)
        assertEquals(original.commonBands.size, reimported.commonBands.size)
        assertEquals(original.leftBands.size, reimported.leftBands.size)
        assertEquals(original.rightBands.size, reimported.rightBands.size)
    }

    @Test
    fun channelSwapTimingControlsWhichIndependentEqProcessesImpulse() {
        // Hardware calibration: L gets -6 dB preamp, R gets +6 dB preamp.
        val preset = EqPreset(
            id = "test",
            name = "Swap Timing Test",
            commonPreampDb = 0.0,
            commonBands = listOf(PeqBand(type = FilterType.PK, frequencyHz = 1000.0, gainDb = 0.0, q = 1.0)),
            leftPreampDb = -6.020599913279624, // ~0.5x linear
            rightPreampDb = 6.020599913279624  // ~2.0x linear
        )

        // Case 1: BEFORE_INDEPENDENT_EQ (recommended)
        // Musical Left impulse (1.0, 0.0) swaps BEFORE independent EQ -> processed by Right HW calibration (2.0x) on output R.
        val procBefore = StereoPeqProcessor(48000.0)
        procBefore.updateConfig(
            ActiveDspConfig(
                bypass = false,
                channelSwap = true,
                swapPosition = SwapPosition.BEFORE_INDEPENDENT_EQ,
                preset = preset
            )
        )
        val (outL1, outR1) = procBefore.processStereoSampleDouble(1.0, 0.0)
        assertEquals(0.0, outL1, 1e-9)
        assertEquals(2.0, outR1, 1e-4)

        // Case 2: AFTER_INDEPENDENT_EQ
        // Musical Left impulse (1.0, 0.0) processed FIRST by Left HW calibration (0.5x), then swapped to output R.
        val procAfter = StereoPeqProcessor(48000.0)
        procAfter.updateConfig(
            ActiveDspConfig(
                bypass = false,
                channelSwap = true,
                swapPosition = SwapPosition.AFTER_INDEPENDENT_EQ,
                preset = preset
            )
        )
        val (outL2, outR2) = procAfter.processStereoSampleDouble(1.0, 0.0)
        assertEquals(0.0, outL2, 1e-9)
        assertEquals(0.5, outR2, 1e-4)
    }

    @Test
    fun frequencyResponseCalculatorProducesDistinctLeftAndRightTotals() {
        val sample = createSampleDualChannelPreset()
        val curves = FrequencyResponseCalculator.calculate(sample)

        assertEquals(256, curves.frequenciesHz.size)
        // Left and Right have opposite signs in independent filters around 127Hz and 9.7kHz
        var maxDiff = 0f
        for (i in curves.totalLeftDb.indices) {
            val d = abs(curves.totalLeftDb[i] - curves.totalRightDb[i])
            if (d > maxDiff) maxDiff = d
        }
        assertTrue("Expected noticeable L vs R independent difference, got $maxDiff", maxDiff > 1.0f)
    }

    @Test
    fun perPresetChannelSwapRoundTripsThroughApoAndJson() {
        val sample = createSampleDualChannelPreset().copy(
            channelSwap = true,
            swapPosition = SwapPosition.AFTER_INDEPENDENT_EQ
        )

        // JSON serialization & legacy flag check
        val json = sample.toJson()
        val (restoredFromJson, hasSwapFlag) = EqPreset.fromJsonWithSwapFlag(json)
        assertTrue(hasSwapFlag)
        assertTrue(restoredFromJson.channelSwap)
        assertEquals(SwapPosition.AFTER_INDEPENDENT_EQ, restoredFromJson.swapPosition)

        // Legacy JSON missing channelSwap should report hasSwapFlag = false
        val legacyJson = sample.toJson().apply {
            remove("channelSwap")
            remove("swapPosition")
        }
        val (legacyParsed, legacyHasSwapFlag) = EqPreset.fromJsonWithSwapFlag(legacyJson)
        assertFalse(legacyHasSwapFlag)
        assertFalse(legacyParsed.channelSwap)

        // APO comment round-trip
        val exportedApo = EqualizerApoParser.export(sample)
        assertTrue(exportedApo.contains("# Quve-ChannelSwap: true"))
        assertTrue(exportedApo.contains("# Quve-SwapPosition: AFTER_INDEPENDENT_EQ"))
        val reparsed = EqualizerApoParser.parse(exportedApo, "Swapped Copy").preset
        assertTrue(reparsed.channelSwap)
        assertEquals(SwapPosition.AFTER_INDEPENDENT_EQ, reparsed.swapPosition)
    }

    @Test
    fun presetBaselineTracksEditsRevertsAndPersistsInJson() {
        val apoText = """
            Preamp: -4.5 dB
            Filter 1: ON PK Fc 100 Hz Gain 3.0 dB Q 1.2
            Channel: L
            Filter 1: ON PK Fc 2500 Hz Gain -1.5 dB Q 2.0
        """.trimIndent()
        val original = EqualizerApoParser.parse(apoText, "Preset A").preset.withCapturedBaseline()
        assertFalse(original.isModifiedFromBaseline)
        assertTrue(original.baseline != null)

        // Edit common preamp + first band
        val editedBand = original.commonBands.first().copy(gainDb = 5.5)
        val edited = original.copy(
            commonPreampDb = -6.0,
            commonBands = listOf(editedBand),
        )
        assertTrue(edited.isModifiedFromBaseline)

        // JSON persistence preserves baseline and edited state
        val json = edited.toJson()
        val restoredEdited = EqPreset.fromJsonWithSwapFlag(json).first
        assertTrue(restoredEdited.isModifiedFromBaseline)
        assertEquals(-6.0, restoredEdited.commonPreampDb, 1e-6)

        // Revert restores freshly imported values while keeping baseline intact
        val reverted = restoredEdited.revertToBaseline()
        assertFalse(reverted.isModifiedFromBaseline)
        assertEquals(-4.5, reverted.commonPreampDb, 1e-6)
        assertEquals(3.0, reverted.commonBands.first().gainDb, 1e-6)
        assertEquals(-1.5, reverted.leftBands.first().gainDb, 1e-6)
    }

    @Test
    fun mergeExcludedUidsDeduplicatesAndFiltersInvalidEntries() {
        val merged = mergeExcludedUids(
            serviceUid = 2000,
            clientAppUid = 10345,
            extraExcludedUids = intArrayOf(-1, 0, 10345, 10500, 2000, 10620, 10500)
        )
        assertArrayEquals(intArrayOf(2000, 10345, 10500, 10620), merged)
    }

    @Test
    fun bypassedPackagesJsonRoundTripCleansAndSorts() {
        val rawInput = linkedSetOf(" com.spotify.music ", "", "com.google.android.youtube", "com.spotify.music")
        val serialized = PeqPreferencesRepository.serializeBypassedPackagesJson(rawInput)
        val parsed = PeqPreferencesRepository.parseBypassedPackagesJson(serialized)
        assertEquals(setOf("com.google.android.youtube", "com.spotify.music"), parsed)
        assertTrue(PeqPreferencesRepository.parseBypassedPackagesJson(null).isEmpty())
        assertTrue(PeqPreferencesRepository.parseBypassedPackagesJson("not-json").isEmpty())
    }

    @Test
    fun graphSeriesOrderHidesTotalLeftRightWhenCommonOnly() {
        val commonOnly = EqPreset(
            id = "c1",
            name = "Common Only",
            commonPreampDb = -3.0,
            commonBands = listOf(PeqBand(enabled = true, frequencyHz = 100.0, gainDb = 3.0)),
            leftBands = listOf(PeqBand(enabled = false, frequencyHz = 200.0, gainDb = -2.0)),
            rightPreampDb = 0.0,
        )
        assertFalse(hasIndependentChannelEq(commonOnly))
        assertEquals(
            listOf(GraphSeries.COMMON),
            visibleSeriesOrder(hasIndependentLR = false, editingTarget = PeqChannelTarget.COMMON),
        )
        assertEquals(
            listOf(GraphSeries.COMMON),
            visibleSeriesOrder(hasIndependentLR = false, editingTarget = PeqChannelTarget.LEFT),
        )

        val dual = commonOnly.copy(leftPreampDb = -1.0)
        assertTrue(hasIndependentChannelEq(dual))
        assertEquals(
            listOf(GraphSeries.COMMON, GraphSeries.RIGHT, GraphSeries.LEFT),
            visibleSeriesOrder(hasIndependentLR = true, editingTarget = PeqChannelTarget.LEFT),
        )
        assertEquals(
            listOf(GraphSeries.COMMON, GraphSeries.LEFT, GraphSeries.RIGHT),
            visibleSeriesOrder(hasIndependentLR = true, editingTarget = PeqChannelTarget.RIGHT),
        )
        assertEquals(
            listOf(GraphSeries.LEFT, GraphSeries.RIGHT, GraphSeries.COMMON),
            visibleSeriesOrder(hasIndependentLR = true, editingTarget = PeqChannelTarget.COMMON),
        )
    }

    @Test
    fun inspectReadoutInterpolatesLogFrequencyAndFormatsCleanly() {
        val preset = EqPreset(id = "f1", name = "Flat", commonPreampDb = -4.5)
        val curves = FrequencyResponseCalculator.calculate(preset)
        val mid = interpolateInspectReadout(curves, 0.5f)!!
        assertEquals(632.45, mid.freqHz, 1.0)
        assertEquals(-4.5f, mid.commonDb, 1e-3f)
        assertEquals("63.4 Hz", formatInspectFrequency(63.4))
        assertEquals("520 Hz", formatInspectFrequency(520.0))
        assertEquals("1.24 kHz", formatInspectFrequency(1240.0))
        assertEquals("12.5 kHz", formatInspectFrequency(12500.0))
    }

    @Test
    fun aidlDestroyTransactionCodeMatchesShizukuUserServiceProtocol() {
        val field = IAudioLoopbackService.Stub::class.java.getDeclaredField("TRANSACTION_destroy")
        field.isAccessible = true
        assertEquals(16777115, field.getInt(null))
    }

    @Test
    fun staleSiblingProcessMatchingTargetsOnlyOwnPeqServiceSuffixUnderSameUid() {
        val rawCmdline = "com.superglobalpeq.app:peq_dsp\u0000\u0000--extra\u0000".toByteArray()
        assertEquals("com.superglobalpeq.app:peq_dsp", extractProcCmdlineProcessName(rawCmdline))
        assertNull(extractProcCmdlineProcessName(byteArrayOf(0, 0, 0)))

        val uid = parseProcRealUid("Name:\tmain\nUid:\t2000\t2000\t2000\t2000\n")
        assertEquals(2000, uid)
        assertNull(parseProcRealUid("Name:\tmain\nPid:\t123\n"))

        val expected = "com.superglobalpeq.app:peq_dsp"
        assertTrue(isStaleSiblingPeqServiceProcess(1001, 2002, 2000, 2000, expected, expected))
        assertFalse(isStaleSiblingPeqServiceProcess(2002, 2002, 2000, 2000, expected, expected))
        assertFalse(isStaleSiblingPeqServiceProcess(1001, 2002, 1000, 2000, expected, expected))
        assertFalse(isStaleSiblingPeqServiceProcess(1001, 2002, 2000, 2000, "shizuku_server", expected))
        assertFalse(isStaleSiblingPeqServiceProcess(1001, 2002, 2000, 2000, "com.superglobalpeq.app", expected))
        assertFalse(isStaleSiblingPeqServiceProcess(1001, 2002, 2000, 2000, "shizuku_server", "shizuku_server"))
    }

    @Test
    fun appAudioModesJsonRoundTripNormalizesAndFiltersStandard() {
        val raw = mapOf(
            " com.game.example " to AppAudioMode.LOW_LATENCY,
            "com.music.standard" to AppAudioMode.STANDARD,
            "com.player.bypass" to AppAudioMode.BYPASS,
            "" to AppAudioMode.BYPASS,
        )
        val json = PeqPreferencesRepository.serializeAppAudioModesJson(raw)
        val parsed = PeqPreferencesRepository.parseAppAudioModesJson(json)
        assertEquals(
            mapOf(
                "com.game.example" to AppAudioMode.LOW_LATENCY,
                "com.player.bypass" to AppAudioMode.BYPASS,
            ),
            parsed
        )
        assertEquals(
            mapOf("com.legacy.app" to AppAudioMode.BYPASS),
            PeqPreferencesRepository.parseAppAudioModesJson("""{"com.legacy.app":"BYPASS","com.bad":"UNKNOWN_MODE"}""")
        )
        assertTrue(PeqPreferencesRepository.parseAppAudioModesJson(null).isEmpty())
        assertTrue(PeqPreferencesRepository.parseAppAudioModesJson("invalid").isEmpty())
    }

    @Test
    fun latencyQueueControllerStandardModeNeverDrops() {
        val controller = LatencyQueueController()
        val buf = ShortArray(192 * 2) { 12000 }
        val decision = controller.evaluateAndShape(
            pcm16 = buf,
            sampleCount = buf.size,
            queuedFrames = 3840L,
            lowLatencyActive = false
        )
        assertEquals(LatencyQueueController.Decision.WRITE, decision)
        assertEquals(12000.toShort(), buf.first())
        assertEquals(12000.toShort(), buf.last())
    }

    @Test
    fun latencyQueueControllerAllowsNormalRemoteSubmixSawtoothWithoutDrops() {
        val controller = LatencyQueueController()
        val buf = ShortArray(192 * 2) { 10000 }
        // Sawtooth cycling between 256 and 1408 frames (< 1632 highWaterFrames) with low trough
        for (cycle in 0 until 40) {
            val q = if (cycle % 5 == 0) 256L else 1408L
            val decision = controller.evaluateAndShape(buf, buf.size, q, lowLatencyActive = true)
            assertEquals(LatencyQueueController.Decision.WRITE, decision)
        }
    }

    @Test
    fun latencyQueueControllerDropsSilentExcessDirectly() {
        val controller = LatencyQueueController()
        val silent = ShortArray(192 * 2) { 0 }
        val decision = controller.evaluateAndShape(silent, silent.size, queuedFrames = 2200L, lowLatencyActive = true)
        assertEquals(LatencyQueueController.Decision.DROP_SILENCE, decision)
    }

    @Test
    fun latencyQueueControllerFadesAudibleBacklogCleanly() {
        val controller = LatencyQueueController()
        val block1 = ShortArray(192 * 2) { 10000 }
        assertEquals(
            LatencyQueueController.Decision.WRITE_FADE_OUT,
            controller.evaluateAndShape(block1, block1.size, queuedFrames = 2200L, lowLatencyActive = true)
        )
        // Tail sample must smoothly attenuate to 0
        assertEquals(0.toShort(), block1.last())

        val block2 = ShortArray(192 * 2) { 10000 }
        assertEquals(
            LatencyQueueController.Decision.DROP_AUDIO,
            controller.evaluateAndShape(block2, block2.size, queuedFrames = 1700L, lowLatencyActive = true)
        )

        val block3 = ShortArray(192 * 2) { 10000 }
        assertEquals(
            LatencyQueueController.Decision.WRITE_FADE_IN,
            controller.evaluateAndShape(block3, block3.size, queuedFrames = 960L, lowLatencyActive = true)
        )
        // Head sample starts at 0 and tail reaches full level
        assertEquals(0.toShort(), block3.first())
        assertEquals(10000.toShort(), block3.last())

        val block4 = ShortArray(192 * 2) { 10000 }
        assertEquals(
            LatencyQueueController.Decision.WRITE,
            controller.evaluateAndShape(block4, block4.size, queuedFrames = 960L, lowLatencyActive = true)
        )
    }

    @Test
    fun latencyQueueControllerTrimsSustainedHighFloor() {
        val controller = LatencyQueueController()
        val block = ShortArray(192 * 2) { 8000 }
        // Sustain floor at 800 frames (> maxFloorFrames 576) for full window -> trims floor down
        var sawFadeOut = false
        for (i in 0 until LatencyQueueController.DEFAULT_FLOOR_WINDOW_BLOCKS + 2) {
            val q = if (i % 5 == 0) 800L else 1400L
            val d = controller.evaluateAndShape(block, block.size, q, lowLatencyActive = true)
            if (d == LatencyQueueController.Decision.WRITE_FADE_OUT) {
                sawFadeOut = true
                break
            }
        }
        assertTrue(sawFadeOut)
    }

    @Test
    fun latencyQueueControllerHandlesUnsignedPlaybackHeadWraparound() {
        val controller = LatencyQueueController()
        val headBeforeWrap = 0xFFFFFF00.toInt()
        val writtenBeforeWrap = 0xFFFFFF00L + 300L
        assertEquals(300L, controller.computeQueuedFrames(writtenBeforeWrap, headBeforeWrap))

        val headAfterWrap = 0x00000100
        val writtenAfterWrap = 0x1_0000_0100L + 450L
        assertEquals(450L, controller.computeQueuedFrames(writtenAfterWrap, headAfterWrap))
    }

    @Test
    fun independentPresetJsonAndBaselineTracksEditsCleanly() {
        val original = IndependentPeqPreset(
            id = "lr_1",
            name = "IEM L/R",
            leftPreampDb = -1.2,
            leftBands = listOf(PeqBand(type = FilterType.PK, frequencyHz = 120.0, gainDb = 2.5, q = 3.0)),
            rightPreampDb = -0.5,
            rightBands = listOf(PeqBand(type = FilterType.PK, frequencyHz = 120.0, gainDb = -2.5, q = 3.0))
        ).withCapturedBaseline()
        assertFalse(original.isModifiedFromBaseline)
        assertTrue(original.hasAnyFilterOrGain)

        val edited = original.withTarget(PeqChannelTarget.LEFT, preampDb = -2.0)
        assertTrue(edited.isModifiedFromBaseline)

        val restored = IndependentPeqPreset.fromJson(edited.toJson())
        assertTrue(restored.isModifiedFromBaseline)
        assertEquals("IEM L/R", restored.name)
        assertEquals(-2.0, restored.leftPreampDb, 1e-6)

        val reverted = restored.revertToBaseline()
        assertFalse(reverted.isModifiedFromBaseline)
        assertEquals(-1.2, reverted.leftPreampDb, 1e-6)
        assertEquals(2.5, reverted.leftBands.first().gainDb, 1e-6)
        assertEquals(-2.5, reverted.rightBands.first().gainDb, 1e-6)
    }

    @Test
    fun splitPresetCompositionPreservesChannelSwapAndCombinesCommonWithLr() {
        val commonOnly = EqPreset(
            id = "c1",
            name = "Harman",
            commonPreampDb = -5.0,
            commonBands = listOf(PeqBand(frequencyHz = 100.0, gainDb = 4.0)),
            channelSwap = false
        )
        val indep = IndependentPeqPreset(
            id = "lr_a",
            name = "IEM A L/R",
            leftPreampDb = -1.0,
            leftBands = listOf(PeqBand(frequencyHz = 3000.0, gainDb = 1.5)),
            rightPreampDb = -1.0,
            rightBands = listOf(PeqBand(frequencyHz = 3000.0, gainDb = -1.5))
        )

        val composed = composeEffectivePreset(
            commonPreset = commonOnly,
            independentPreset = indep,
            channelSwap = true,
            swapPosition = SwapPosition.AFTER_INDEPENDENT_EQ
        )
        assertEquals(-5.0, composed.commonPreampDb, 1e-6)
        assertEquals(1, composed.commonBands.size)
        assertEquals(-1.0, composed.leftPreampDb, 1e-6)
        assertEquals(1.5, composed.leftBands.first().gainDb, 1e-6)
        assertEquals(-1.5, composed.rightBands.first().gainDb, 1e-6)
        assertTrue(composed.channelSwap)
        assertEquals(SwapPosition.AFTER_INDEPENDENT_EQ, composed.swapPosition)

        val cleared = composeEffectivePreset(
            commonPreset = commonOnly,
            independentPreset = null,
            channelSwap = true,
            swapPosition = SwapPosition.BEFORE_INDEPENDENT_EQ
        )
        assertTrue(cleared.leftBands.isEmpty())
        assertTrue(cleared.rightBands.isEmpty())
        assertEquals(0.0, cleared.leftPreampDb, 1e-6)
        assertTrue(cleared.channelSwap)
    }

    @Test
    fun bundleSelectionDecisionPromptsOnlyWhenDifferentLrIsActive() {
        val lrA = IndependentPeqPreset(id = "lr_a", name = "LR A", leftPreampDb = -1.0)
        val lrB = IndependentPeqPreset(id = "lr_b", name = "LR B", rightPreampDb = -1.0)
        val indepList = listOf(lrA, lrB)

        val commonOnly = EqPreset(id = "p_common", name = "Common Only", commonPreampDb = -2.0)
        val bundleA = EqPreset(
            id = "p_a",
            name = "Bundle A",
            commonPreampDb = -4.0,
            leftPreampDb = -1.0,
            linkedIndependentPresetId = "lr_a"
        )

        // Common-only always switches Common without touching L/R
        assertEquals(
            BundleSelectionOutcome.APPLY_COMMON_ONLY,
            evaluateSavedPresetSelection(commonOnly, activeIndependentId = "lr_b", hasDraftIndependent = false, indepList).outcome
        )
        // Bundle selected when no L/R active -> auto applies both without dialog
        val emptyEval = evaluateSavedPresetSelection(bundleA, activeIndependentId = null, hasDraftIndependent = false, indepList)
        assertEquals(BundleSelectionOutcome.AUTO_APPLY_BOTH, emptyEval.outcome)
        assertEquals("lr_a", emptyEval.targetIndependentPresetId)

        // Bundle selected when same L/R is already active -> auto applies cleanly
        assertEquals(
            BundleSelectionOutcome.AUTO_APPLY_BOTH,
            evaluateSavedPresetSelection(bundleA, activeIndependentId = "lr_a", hasDraftIndependent = false, indepList).outcome
        )
        // Bundle selected when DIFFERENT L/R is active -> prompts Common Only vs Both
        val conflictEval = evaluateSavedPresetSelection(bundleA, activeIndependentId = "lr_b", hasDraftIndependent = false, indepList)
        assertEquals(BundleSelectionOutcome.PROMPT_COMMON_OR_BOTH, conflictEval.outcome)
        assertEquals("lr_a", conflictEval.targetIndependentPresetId)

        // Bundle selected when unsaved L/R draft is active -> prompts so draft is not silently overwritten
        assertEquals(
            BundleSelectionOutcome.PROMPT_COMMON_OR_BOTH,
            evaluateSavedPresetSelection(bundleA, activeIndependentId = null, hasDraftIndependent = true, indepList).outcome
        )
    }

    @Test
    fun migrateLegacyBundlesExtractsIndependentPresetsAndPreservesRules() {
        val dual = createSampleDualChannelPreset().copy(
            id = "ig955",
            name = "IG955",
            channelSwap = true,
            swapPosition = SwapPosition.BEFORE_INDEPENDENT_EQ
        ).withCapturedBaseline()
        val commonOnly = EqPreset(id = "xm5", name = "XM5", commonPreampDb = -8.0).withCapturedBaseline()

        val legacyRules = listOf(
            DeviceRoutingRule(deviceKey = "bt:ig", displayName = "IG BT", category = DeviceCategory.BLUETOOTH, action = DeviceRuleAction.PRESET, presetId = "ig955"),
            DeviceRoutingRule(deviceKey = "bt:xm5", displayName = "XM5", category = DeviceCategory.BLUETOOTH, action = DeviceRuleAction.PRESET, presetId = "xm5")
        )

        val migrated = PeqPreferencesRepository.migrateLegacyBundles(
            presets = listOf(dual, commonOnly),
            existingIndependentPresets = emptyList(),
            deviceRules = legacyRules,
            selectedPresetId = "ig955",
            existingSelectedIndependentId = null,
            fallbackSwap = dual.channelSwap,
            fallbackSwapPosition = dual.swapPosition
        )

        assertEquals(1, migrated.independentPresets.size)
        val extracted = migrated.independentPresets.first()
        assertEquals("IG955", extracted.name)
        assertEquals(10, extracted.leftBands.size)
        assertEquals(10, extracted.rightBands.size)

        val migratedIg = migrated.presets.first { it.id == "ig955" }
        val migratedXm5 = migrated.presets.first { it.id == "xm5" }
        assertEquals(extracted.id, migratedIg.linkedIndependentPresetId)
        assertNull(migratedXm5.linkedIndependentPresetId)
        assertEquals(extracted.id, migrated.selectedIndependentPresetId)
        assertTrue(migrated.channelSwap)

        val igRule = migrated.deviceRules.first { it.deviceKey == "bt:ig" }
        val xm5Rule = migrated.deviceRules.first { it.deviceKey == "bt:xm5" }
        assertEquals(DeviceIndependentRuleAction.APPLY_PRESET, igRule.independentAction)
        assertEquals(extracted.id, igRule.independentPresetId)
        assertEquals(DeviceIndependentRuleAction.CLEAR_NONE, xm5Rule.independentAction)
        assertNull(xm5Rule.independentPresetId)
    }

    @Test
    fun deviceRoutingRuleJsonRoundTripPreservesIndependentAndSwapActions() {
        val rule = DeviceRoutingRule(
            deviceKey = "usb:1",
            displayName = "USB DAC",
            category = DeviceCategory.USB_DAC,
            action = DeviceRuleAction.PRESET,
            presetId = "p_common",
            independentAction = DeviceIndependentRuleAction.APPLY_PRESET,
            independentPresetId = "lr_dac",
            channelSwapAction = DeviceSwapRuleAction.FORCE_ON
        )
        val reparsed = DeviceRoutingRule.fromJson(rule.toJson())
        assertEquals(rule, reparsed)

        val legacyObj = rule.toJson().apply {
            remove("independentAction")
            remove("independentPresetId")
            remove("channelSwapAction")
        }
        val legacyParsed = DeviceRoutingRule.fromJson(legacyObj)
        assertEquals(DeviceIndependentRuleAction.KEEP_CURRENT, legacyParsed.independentAction)
        assertNull(legacyParsed.independentPresetId)
        assertEquals(DeviceSwapRuleAction.FOLLOW_MANUAL, legacyParsed.channelSwapAction)
    }

    @Test
    fun expandedPreampRangeAndTwoZoneSliderMappingWorkContinuously() {
        assertEquals(-60.0, PREAMP_MIN_DB, 1e-6)
        assertEquals(12.0, PREAMP_MAX_DB, 1e-6)
        assertEquals(-60.0, clampPreampDb(-85.0), 1e-6)
        assertEquals(12.0, clampPreampDb(20.0), 1e-6)
        assertEquals(-42.5, clampPreampDb(-42.5), 1e-6)

        // Breakpoint -24.0 dB sits cleanly at 30% of slider travel from both sides
        assertEquals(0.0f, preampDbToSliderPos(-60.0), 1e-4f)
        assertEquals(PREAMP_SLIDER_SPLIT_FRACTION, preampDbToSliderPos(PREAMP_SLIDER_SPLIT_DB), 1e-4f)
        assertEquals(1.0f, preampDbToSliderPos(12.0), 1e-4f)

        assertEquals(-60.0, sliderPosToPreampDb(0.0f), 1e-6)
        assertEquals(-24.0, sliderPosToPreampDb(PREAMP_SLIDER_SPLIT_FRACTION), 1e-6)
        assertEquals(12.0, sliderPosToPreampDb(1.0f), 1e-6)

        // Left zone snaps to 0.5 dB, right zone preserves 0.1 dB precision
        assertEquals(-42.0, sliderPosToPreampDb(0.15f), 1e-6)
        assertEquals(-3.4, sliderPosToPreampDb(preampDbToSliderPos(-3.4)), 1e-6)
        assertEquals(0.0, sliderPosToPreampDb(preampDbToSliderPos(0.0)), 1e-6)
    }

    @Test
    fun autoHeadroomApoParserAndAdaptiveGraphSupportMinus60Db() {
        val extremeBoost = EqPreset(
            id = "ex",
            name = "Extreme",
            commonBands = listOf(
                PeqBand(frequencyHz = 100.0, gainDb = 30.0, q = 0.7),
                PeqBand(frequencyHz = 200.0, gainDb = 10.0, q = 0.7),
            )
        )
        val suggested = FrequencyResponseCalculator.computeAutoPreampAdjustmentDb(extremeBoost)
        assertTrue(suggested < -24.0)
        assertTrue(suggested >= -60.0)

        val parsed = EqualizerApoParser.parse("Preamp: -48.5 dB\nPreamp: -90 dB", "Atten")
        // Second line wins and clamps to -60 dB
        assertEquals(-60.0, parsed.preset.commonPreampDb, 1e-6)
        val single = EqualizerApoParser.parseSingleSection("Preamp: -45.2 dB")
        assertEquals(-45.2, single.first!!, 1e-6)

        val (defaultMin, defaultTicks) = computeAdaptiveGraphYAxis(null, false, -8.0)
        assertEquals(-18f, defaultMin, 1e-4f)
        assertEquals(listOf(12f, 6f, 0f, -6f, -12f, -18f), defaultTicks)

        val (deepMin, deepTicks) = computeAdaptiveGraphYAxis(null, false, -52.0)
        assertEquals(-60f, deepMin, 1e-4f)
        assertTrue(deepTicks.contains(-60f))
        assertTrue(deepTicks.contains(-24f))
    }

    @Test
    fun normalizeUserExcludedUidsSortsDeduplicatesAndDropsSpecialUids() {
        val result = normalizeUserExcludedUids(
            serviceUid = 2000,
            clientAppUid = 10420,
            extraExcludedUids = intArrayOf(10550, 2000, 0, -5, 10300, 10420, 10550, 10111)
        )
        assertArrayEquals(intArrayOf(10111, 10300, 10550), result)
        assertArrayEquals(IntArray(0), normalizeUserExcludedUids(2000, 10420, null))
    }

    @Test
    fun parseJsonIntArrayHandlesNullAndSkipsNegativeEntries() {
        assertArrayEquals(IntArray(0), parseJsonIntArray(null))
        assertArrayEquals(IntArray(0), parseJsonIntArray(JSONArray()))
        val parsed = parseJsonIntArray(JSONArray(listOf(10200, -1, 0, 10350, "bad")))
        assertArrayEquals(intArrayOf(10200, 0, 10350), parsed)
    }

    @Test
    fun userServiceTagForPersistenceSeparatesDaemonAndBoundLifecycles() {
        val daemonTag = userServiceTagForPersistence(backgroundPersistenceEnabled = true)
        val boundTag = userServiceTagForPersistence(backgroundPersistenceEnabled = false)
        assertNotEquals(daemonTag, boundTag)
        assertTrue(daemonTag.isNotEmpty() && boundTag.isNotEmpty())
    }

    @Test
    fun shouldRestartLoopbackAttachesSeamlesslyWhenRunningAndExcludedUidsMatch() {
        // Reattaching to running daemon with same bypassed UIDs (even unsorted / containing client or service UID): no restart.
        assertFalse(
            shouldRestartLoopback(
                runningNow = true,
                restartRequested = false,
                remoteUserExcludedUids = intArrayOf(10111, 10550),
                desiredUserExcludedUids = intArrayOf(10550, 2000, 10420, 10111),
                serviceUid = 2000,
                clientAppUid = 10420
            )
        )

        // Not running yet -> must start loopback.
        assertTrue(
            shouldRestartLoopback(
                runningNow = false,
                restartRequested = false,
                remoteUserExcludedUids = intArrayOf(10111),
                desiredUserExcludedUids = intArrayOf(10111),
                serviceUid = 2000,
                clientAppUid = 10420
            )
        )

        // Explicit restart requested -> must restart loopback.
        assertTrue(
            shouldRestartLoopback(
                runningNow = true,
                restartRequested = true,
                remoteUserExcludedUids = intArrayOf(10111),
                desiredUserExcludedUids = intArrayOf(10111),
                serviceUid = 2000,
                clientAppUid = 10420
            )
        )

        // Bypassed UID list changed while detached -> must restart loopback to update AudioMixingRule.
        assertTrue(
            shouldRestartLoopback(
                runningNow = true,
                restartRequested = false,
                remoteUserExcludedUids = intArrayOf(10111),
                desiredUserExcludedUids = intArrayOf(10111, 10550),
                serviceUid = 2000,
                clientAppUid = 10420
            )
        )
    }

    @Test
    fun classifyOutputDeviceKeyAndCategoryMatchesExpectedDeviceKeys() {
        assertEquals(
            "speaker" to DeviceCategory.SPEAKER,
            classifyOutputDeviceKeyAndCategory(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Speaker", "")
        )
        assertEquals(
            "bt:AA:BB:CC:DD:EE:FF" to DeviceCategory.BLUETOOTH,
            classifyOutputDeviceKeyAndCategory(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Sony", "AA:BB:CC:DD:EE:FF")
        )
        // Without UsbManager descriptors, non-generic product name is preferred over volatile ALSA card address
        assertEquals(
            "usb:name:moondrop" to DeviceCategory.USB_DAC,
            classifyOutputDeviceKeyAndCategory(
                type = AudioDeviceInfo.TYPE_USB_DEVICE,
                product = "USB-Audio - MOONDROP",
                address = "card=1;device=0;",
                procUsbIdResolver = { null },
                procStreamLabelResolver = { null }
            )
        )
        assertEquals(
            "wired:${AudioDeviceInfo.TYPE_WIRED_HEADPHONES}" to DeviceCategory.WIRED,
            classifyOutputDeviceKeyAndCategory(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "", "")
        )
        assertNull(classifyOutputDeviceKeyAndCategory(AudioDeviceInfo.TYPE_REMOTE_SUBMIX, "submix", ""))
    }

    @Test
    fun resolveUsbAudioIdentityDistinguishesDifferentDacsEvenWithGenericAudioDeviceInfo() {
        val appleDongle = UsbAudioDescriptor(
            vendorId = 0x05ac,
            productId = 0x110a,
            manufacturerName = "Apple Inc.",
            productName = "USB-C to 3.5mm Headphone Jack Adapter"
        )
        val fiioKa13 = UsbAudioDescriptor(
            vendorId = 0x2972,
            productId = 0x0047,
            manufacturerName = "FiiO",
            productName = "FiiO KA13"
        )
        val sameChipA = UsbAudioDescriptor(
            vendorId = 0x3302,
            productId = 0x1028,
            manufacturerName = "Brand A",
            productName = "Dongle Alpha"
        )
        val sameChipB = UsbAudioDescriptor(
            vendorId = 0x3302,
            productId = 0x1028,
            manufacturerName = "Brand B",
            productName = "Dongle Beta"
        )

        // Even when Android AudioDeviceInfo reports generic "USB-Audio" + same "card=1;device=0;"
        val idApple = resolveUsbAudioIdentity(
            audioProduct = "USB-Audio",
            audioAddress = "card=1;device=0;",
            usbDescriptors = listOf(appleDongle),
            procUsbIdResolver = { null }
        )
        val idFiioCard1 = resolveUsbAudioIdentity(
            audioProduct = "USB-Audio",
            audioAddress = "card=1;device=0;",
            usbDescriptors = listOf(fiioKa13),
            procUsbIdResolver = { null }
        )
        val idFiioCard2 = resolveUsbAudioIdentity(
            audioProduct = "USB-Audio",
            audioAddress = "card=2;device=0;",
            usbDescriptors = listOf(fiioKa13),
            procUsbIdResolver = { null }
        )

        assertEquals("usb:05ac:110a:usb-c to 3.5mm headphone jack adapter", idApple.deviceKey)
        assertEquals("Apple Inc. USB-C to 3.5mm Headphone Jack Adapter", idApple.displayName)
        assertEquals("usb:2972:0047:fiio ka13", idFiioCard1.deviceKey)
        assertEquals("FiiO KA13", idFiioCard1.displayName)
        // Same DAC stays identical even if ALSA card slot number changes
        assertEquals(idFiioCard1.deviceKey, idFiioCard2.deviceKey)

        // Two DACs sharing the same bridge chip VID:PID still get distinct keys via product name
        val idChipA = resolveUsbAudioIdentity(
            audioProduct = "USB-Audio",
            audioAddress = "card=1;device=0;",
            usbDescriptors = listOf(sameChipA),
            procUsbIdResolver = { null }
        )
        val idChipB = resolveUsbAudioIdentity(
            audioProduct = "USB-Audio",
            audioAddress = "card=1;device=0;",
            usbDescriptors = listOf(sameChipB),
            procUsbIdResolver = { null }
        )
        assertNotEquals(idChipA.deviceKey, idChipB.deviceKey)

        // Multiple simultaneous USB DACs disambiguated via /proc/asound/cardN/usbid
        val multiCard1 = resolveUsbAudioIdentity(
            audioProduct = "USB-Audio",
            audioAddress = "card=1;device=0;",
            usbDescriptors = listOf(appleDongle, fiioKa13),
            procUsbIdResolver = { card -> if (card == 1) "05ac:110a" else "2972:0047" }
        )
        val multiCard2 = resolveUsbAudioIdentity(
            audioProduct = "USB-Audio",
            audioAddress = "card=2;device=0;",
            usbDescriptors = listOf(appleDongle, fiioKa13),
            procUsbIdResolver = { card -> if (card == 1) "05ac:110a" else "2972:0047" }
        )
        assertEquals(idApple.deviceKey, multiCard1.deviceKey)
        assertEquals(idFiioCard1.deviceKey, multiCard2.deviceKey)

        // Fallback to /proc/asound when UsbManager returns empty list
        val procFallback = resolveUsbAudioIdentity(
            audioProduct = "USB-Audio",
            audioAddress = "card=1;device=0;",
            usbDescriptors = emptyList(),
            procUsbIdResolver = { "2972:0047" },
            procStreamLabelResolver = { "FiiO KA13" }
        )
        assertEquals("usb:2972:0047:fiio ka13", procFallback.deviceKey)
        assertEquals("FiiO KA13", procFallback.displayName)
    }

    @Test
    fun resolveDeviceRoutingOutcomeAppliesPresetSwapLrAndEqOffCleanly() {
        val flat = EqPreset(id = "flat", name = "Flat")
        val btPreset = EqPreset(
            id = "bt_eq",
            name = "BT Preset",
            commonBands = listOf(PeqBand(frequencyHz = 100.0, gainDb = 3.0))
        )
        val lrA = IndependentPeqPreset(id = "lr_a", name = "LR A", leftPreampDb = -2.0)
        val lrB = IndependentPeqPreset(id = "lr_b", name = "LR B", rightPreampDb = -3.0)
        val draftLr = IndependentPeqPreset(id = "__draft_lr__", name = "Draft", leftPreampDb = -1.0)

        val rules = listOf(
            DeviceRoutingRule(
                deviceKey = "speaker",
                displayName = "Speaker",
                category = DeviceCategory.SPEAKER,
                action = DeviceRuleAction.EQ_OFF
            ),
            DeviceRoutingRule(
                deviceKey = "bt:11:22",
                displayName = "BT",
                category = DeviceCategory.BLUETOOTH,
                action = DeviceRuleAction.PRESET,
                presetId = "bt_eq",
                independentAction = DeviceIndependentRuleAction.APPLY_PRESET,
                independentPresetId = "lr_b",
                channelSwapAction = DeviceSwapRuleAction.FORCE_ON
            ),
            DeviceRoutingRule(
                deviceKey = "wired:3",
                displayName = "Wired",
                category = DeviceCategory.WIRED,
                action = DeviceRuleAction.PRESET,
                presetId = null,
                independentAction = DeviceIndependentRuleAction.CLEAR_NONE,
                channelSwapAction = DeviceSwapRuleAction.FORCE_OFF
            )
        )

        val btOutcome = resolveDeviceRoutingOutcome(
            deviceKey = "bt:11:22",
            deviceRules = rules,
            presets = listOf(flat, btPreset),
            independentPresets = listOf(lrA, lrB),
            currentCommonPresetId = "flat",
            currentIndependentPresetId = "lr_a",
            draftIndependentPreset = null,
            currentChannelSwap = false,
            swapPosition = SwapPosition.AFTER_INDEPENDENT_EQ
        )
        assertFalse(btOutcome.pausedByDeviceRule)
        assertEquals("bt_eq", btOutcome.selectedCommonPresetId)
        assertEquals("lr_b", btOutcome.selectedIndependentPresetId)
        assertTrue(btOutcome.channelSwap)
        assertEquals(-3.0, btOutcome.effectivePreset.rightPreampDb, 1e-6)

        val speakerOutcome = resolveDeviceRoutingOutcome(
            deviceKey = "speaker",
            deviceRules = rules,
            presets = listOf(flat, btPreset),
            independentPresets = listOf(lrA, lrB),
            currentCommonPresetId = btOutcome.selectedCommonPresetId,
            currentIndependentPresetId = btOutcome.selectedIndependentPresetId,
            draftIndependentPreset = null,
            currentChannelSwap = btOutcome.channelSwap,
            swapPosition = SwapPosition.AFTER_INDEPENDENT_EQ
        )
        assertTrue(speakerOutcome.pausedByDeviceRule)
        assertEquals("bt_eq", speakerOutcome.selectedCommonPresetId)
        assertTrue(speakerOutcome.channelSwap)

        val wiredOutcome = resolveDeviceRoutingOutcome(
            deviceKey = "wired:3",
            deviceRules = rules,
            presets = listOf(flat, btPreset),
            independentPresets = listOf(lrA, lrB),
            currentCommonPresetId = "bt_eq",
            currentIndependentPresetId = null,
            draftIndependentPreset = draftLr,
            currentChannelSwap = true,
            swapPosition = SwapPosition.AFTER_INDEPENDENT_EQ
        )
        assertFalse(wiredOutcome.pausedByDeviceRule)
        assertNull(wiredOutcome.selectedIndependentPresetId)
        assertNull(wiredOutcome.draftIndependentPreset)
        assertTrue(wiredOutcome.clearedDraftIndependent)
        assertFalse(wiredOutcome.channelSwap)
    }

    @Test
    fun autonomousDevicePolicyJsonRoundTripsBothEnabledAndDisabledModes() {
        val disabled = AutonomousDevicePolicy.fromJsonString(
            AutonomousDevicePolicy(enabled = false, clientAppUid = 10420).toJsonString()
        )
        assertFalse(disabled.enabled)
        assertEquals(-1, disabled.clientAppUid)
        assertTrue(disabled.deviceRules.isEmpty())

        val preset = EqPreset(
            id = "p1",
            name = "Target",
            commonPreampDb = -4.5,
            commonBands = listOf(PeqBand(frequencyHz = 1200.0, gainDb = 4.5, q = 1.2))
        )
        val indep = IndependentPeqPreset(id = "i1", name = "EarMix", leftPreampDb = -1.5)
        val draft = IndependentPeqPreset(id = "__draft_lr__", name = "Draft", rightPreampDb = -2.0)
        val rule = DeviceRoutingRule(
            deviceKey = "usb:dac1",
            displayName = "USB DAC",
            category = DeviceCategory.USB_DAC,
            action = DeviceRuleAction.PRESET,
            presetId = "p1",
            independentAction = DeviceIndependentRuleAction.APPLY_PRESET,
            independentPresetId = "i1",
            channelSwapAction = DeviceSwapRuleAction.FORCE_ON
        )
        val original = AutonomousDevicePolicy(
            enabled = true,
            clientAppUid = 10420,
            excludedUids = listOf(10111, 10222),
            globalLowLatency = true,
            lowLatencyUids = listOf(10333),
            presets = listOf(preset),
            independentPresets = listOf(indep),
            deviceRules = listOf(rule),
            selectedPresetId = "p1",
            selectedIndependentPresetId = null,
            draftIndependentPreset = draft,
            channelSwap = true,
            swapPosition = SwapPosition.AFTER_INDEPENDENT_EQ,
            pausedByDeviceRule = false,
            activeDeviceKey = "usb:dac1"
        )
        val restored = AutonomousDevicePolicy.fromJsonString(original.toJsonString())
        assertEquals(original, restored)
    }

    @Test
    fun independentPresetChannelSwapRoundTripAndMigration() {
        val swapOn = IndependentPeqPreset(id = "lr_on", name = "LR On", channelSwap = true)
        val swapOff = IndependentPeqPreset(id = "lr_off", name = "LR Off", channelSwap = false)
        assertTrue(IndependentPeqPreset.fromJson(swapOn.toJson()).channelSwap)
        assertFalse(IndependentPeqPreset.fromJson(swapOff.toJson()).channelSwap)

        // Legacy JSON without channelSwap defaults to false
        val legacyJson = IndependentPeqPreset(id = "legacy", name = "Legacy").toJson().apply {
            remove("channelSwap")
        }
        assertFalse(IndependentPeqPreset.fromJson(legacyJson).channelSwap)

        // Migration from v1.7.0 channelSwapMode == "FORCE_ON"
        val v170OnJson = IndependentPeqPreset(id = "v170_on", name = "V170 On").toJson().apply {
            remove("channelSwap")
            put("channelSwapMode", "FORCE_ON")
        }
        assertTrue(IndependentPeqPreset.fromJson(v170OnJson).channelSwap)
    }

    @Test
    fun deviceRoutingAppliesIndependentPresetSwapWithDeviceRulePrecedence() {
        val common = EqPreset(id = "c1", name = "Common")
        val lrSwapOn = IndependentPeqPreset(id = "lr_on", name = "LR On", channelSwap = true)
        val lrSwapOff = IndependentPeqPreset(id = "lr_off", name = "LR Off", channelSwap = false)
        val indepList = listOf(lrSwapOn, lrSwapOff)

        // Device rule FOLLOW_MANUAL + APPLY_PRESET(channelSwap = true) -> turns swap ON
        val followRule = DeviceRoutingRule(
            deviceKey = "usb:1",
            displayName = "DAC 1",
            category = DeviceCategory.USB_DAC,
            action = DeviceRuleAction.PRESET,
            presetId = "c1",
            independentAction = DeviceIndependentRuleAction.APPLY_PRESET,
            independentPresetId = "lr_on",
            channelSwapAction = DeviceSwapRuleAction.FOLLOW_MANUAL
        )
        val outcomeOn = resolveDeviceRoutingOutcome(
            deviceKey = "usb:1",
            deviceRules = listOf(followRule),
            presets = listOf(common),
            independentPresets = indepList,
            currentCommonPresetId = "c1",
            currentIndependentPresetId = null,
            draftIndependentPreset = null,
            currentChannelSwap = false,
            swapPosition = SwapPosition.BEFORE_INDEPENDENT_EQ
        )
        assertTrue(outcomeOn.channelSwap)
        assertTrue(outcomeOn.channelSwapChanged)

        // Device rule FOLLOW_MANUAL + APPLY_PRESET(channelSwap = false) -> resets swap back to OFF
        val offPresetRule = followRule.copy(independentPresetId = "lr_off")
        val outcomeOff = resolveDeviceRoutingOutcome(
            deviceKey = "usb:1",
            deviceRules = listOf(offPresetRule),
            presets = listOf(common),
            independentPresets = indepList,
            currentCommonPresetId = "c1",
            currentIndependentPresetId = "lr_on",
            draftIndependentPreset = null,
            currentChannelSwap = true,
            swapPosition = SwapPosition.BEFORE_INDEPENDENT_EQ
        )
        assertFalse(outcomeOff.channelSwap)
        assertTrue(outcomeOff.channelSwapChanged)

        // Device rule FOLLOW_MANUAL + CLEAR_NONE -> resets swap back to OFF
        val clearRule = followRule.copy(
            independentAction = DeviceIndependentRuleAction.CLEAR_NONE,
            independentPresetId = null
        )
        val outcomeClear = resolveDeviceRoutingOutcome(
            deviceKey = "usb:1",
            deviceRules = listOf(clearRule),
            presets = listOf(common),
            independentPresets = indepList,
            currentCommonPresetId = "c1",
            currentIndependentPresetId = "lr_on",
            draftIndependentPreset = null,
            currentChannelSwap = true,
            swapPosition = SwapPosition.BEFORE_INDEPENDENT_EQ
        )
        assertFalse(outcomeClear.channelSwap)
        assertTrue(outcomeClear.channelSwapChanged)

        // Device rule FOLLOW_MANUAL + KEEP_CURRENT -> keeps current swap state
        val keepRule = followRule.copy(
            independentAction = DeviceIndependentRuleAction.KEEP_CURRENT,
            independentPresetId = null
        )
        val outcomeKeep = resolveDeviceRoutingOutcome(
            deviceKey = "usb:1",
            deviceRules = listOf(keepRule),
            presets = listOf(common),
            independentPresets = indepList,
            currentCommonPresetId = "c1",
            currentIndependentPresetId = "lr_on",
            draftIndependentPreset = null,
            currentChannelSwap = true,
            swapPosition = SwapPosition.BEFORE_INDEPENDENT_EQ
        )
        assertTrue(outcomeKeep.channelSwap)
        assertFalse(outcomeKeep.channelSwapChanged)

        // Device rule FORCE_OFF overrides independent preset channelSwap = true
        val forceOffRule = followRule.copy(channelSwapAction = DeviceSwapRuleAction.FORCE_OFF)
        val outcomeOverride = resolveDeviceRoutingOutcome(
            deviceKey = "usb:1",
            deviceRules = listOf(forceOffRule),
            presets = listOf(common),
            independentPresets = indepList,
            currentCommonPresetId = "c1",
            currentIndependentPresetId = null,
            draftIndependentPreset = null,
            currentChannelSwap = true,
            swapPosition = SwapPosition.BEFORE_INDEPENDENT_EQ
        )
        assertFalse(outcomeOverride.channelSwap)
        assertTrue(outcomeOverride.channelSwapChanged)
    }

    private fun createSampleDualChannelPreset(): EqPreset =
        EqualizerApoParser.parse(SAMPLE_DUAL_CHANNEL_APO, "Dual Channel Test").preset

    companion object {
        private const val SAMPLE_DUAL_CHANNEL_APO = """
Preamp: -9.6 dB
Filter 1: ON PK Fc 56 Hz Gain 3.42 dB Q 1.000
Filter 2: ON PK Fc 60 Hz Gain -8.52 dB Q 0.531
Filter 3: ON LSC Fc 151 Hz Gain 8.52 dB Q 0.701
Filter 4: ON PK Fc 154 Hz Gain -1.84 dB Q 2.281
Filter 5: ON PK Fc 789 Hz Gain 7.69 dB Q 0.528
Filter 6: ON PK Fc 2328 Hz Gain 2.82 dB Q 1.651
Filter 7: ON PK Fc 4555 Hz Gain 4.93 dB Q 1.628
Filter 8: ON PK Fc 8557 Hz Gain 1.77 dB Q 2.538
Filter 9: ON PK Fc 10696 Hz Gain 8.12 dB Q 0.679
Filter 10: ON HSC Fc 15990 Hz Gain 4.22 dB Q 1.185
Channel: L
Preamp: -1.4 dB
Filter 1: ON LSC Fc 129 Hz Gain 0.18 dB Q 3.000
Filter 2: ON HSC Fc 155 Hz Gain 0.10 dB Q 2.989
Filter 3: ON PK Fc 260 Hz Gain 0.22 dB Q 0.594
Filter 4: ON PK Fc 9706 Hz Gain -2.03 dB Q 2.753
Filter 5: ON PK Fc 10883 Hz Gain 1.42 dB Q 2.222
Filter 6: ON PK Fc 3631 Hz Gain -0.24 dB Q 3.948
Filter 7: ON PK Fc 2835 Hz Gain -0.23 dB Q 0.402
Filter 8: ON PK Fc 127 Hz Gain 2.52 dB Q 4.000
Filter 9: ON PK Fc 121 Hz Gain -2.21 dB Q 3.961
Filter 10: ON PK Fc 12602 Hz Gain -0.54 dB Q 3.800
Channel: R
Preamp: -1.4 dB
Filter 1: ON LSC Fc 129 Hz Gain -0.18 dB Q 3.000
Filter 2: ON HSC Fc 155 Hz Gain -0.10 dB Q 2.989
Filter 3: ON PK Fc 260 Hz Gain -0.22 dB Q 0.594
Filter 4: ON PK Fc 9706 Hz Gain 2.03 dB Q 2.753
Filter 5: ON PK Fc 10883 Hz Gain -1.42 dB Q 2.222
Filter 6: ON PK Fc 3631 Hz Gain 0.24 dB Q 3.948
Filter 7: ON PK Fc 2835 Hz Gain 0.23 dB Q 0.402
Filter 8: ON PK Fc 127 Hz Gain -2.52 dB Q 4.000
Filter 9: ON PK Fc 121 Hz Gain 2.21 dB Q 3.961
Filter 10: ON PK Fc 12602 Hz Gain 0.54 dB Q 3.800
"""
    }
}
