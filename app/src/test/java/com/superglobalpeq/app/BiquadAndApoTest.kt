package com.superglobalpeq.app

import com.superglobalpeq.app.data.PeqPreferencesRepository
import com.superglobalpeq.app.dsp.EqualizerApoParser
import com.superglobalpeq.app.dsp.FrequencyResponseCalculator
import com.superglobalpeq.app.dsp.StereoPeqProcessor
import com.superglobalpeq.app.model.ActiveDspConfig
import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.model.FilterType
import com.superglobalpeq.app.model.PeqBand
import com.superglobalpeq.app.model.PeqChannelTarget
import com.superglobalpeq.app.model.SwapPosition
import com.superglobalpeq.app.model.mergeExcludedUids
import com.superglobalpeq.app.service.IAudioLoopbackService
import com.superglobalpeq.app.service.extractProcCmdlineProcessName
import com.superglobalpeq.app.service.isStaleSiblingPeqServiceProcess
import com.superglobalpeq.app.service.parseProcRealUid
import com.superglobalpeq.app.ui.components.GraphSeries
import com.superglobalpeq.app.ui.components.formatInspectFrequency
import com.superglobalpeq.app.ui.components.hasIndependentChannelEq
import com.superglobalpeq.app.ui.components.interpolateInspectReadout
import com.superglobalpeq.app.ui.components.visibleSeriesOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
