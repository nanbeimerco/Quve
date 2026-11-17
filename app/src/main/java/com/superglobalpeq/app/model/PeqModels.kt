package com.superglobalpeq.app.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs
import kotlin.math.round

const val PREAMP_MIN_DB = -60.0
const val PREAMP_MAX_DB = 12.0
const val PREAMP_SLIDER_SPLIT_DB = -24.0
const val PREAMP_SLIDER_SPLIT_FRACTION = 0.30f

/**
 * Clamps a preamp gain value to [PREAMP_MIN_DB, PREAMP_MAX_DB].
 */
fun clampPreampDb(db: Double): Double = db.coerceIn(PREAMP_MIN_DB, PREAMP_MAX_DB)

/**
 * Maps a preamp gain onto the 2-zone continuous slider track (`0f..1f`):
 * - Left 30% (`0.00f..0.30f`): `-60.0..-24.0 dB` deep attenuation zone
 * - Right 70% (`0.30f..1.00f`): `-24.0..+12.0 dB` precision EQ headroom zone
 */
fun preampDbToSliderPos(db: Double): Float {
    val clamped = clampPreampDb(db)
    return if (clamped <= PREAMP_SLIDER_SPLIT_DB) {
        val ratio = (clamped - PREAMP_MIN_DB) / (PREAMP_SLIDER_SPLIT_DB - PREAMP_MIN_DB)
        (ratio * PREAMP_SLIDER_SPLIT_FRACTION).toFloat().coerceIn(0f, PREAMP_SLIDER_SPLIT_FRACTION)
    } else {
        val ratio = (clamped - PREAMP_SLIDER_SPLIT_DB) / (PREAMP_MAX_DB - PREAMP_SLIDER_SPLIT_DB)
        (PREAMP_SLIDER_SPLIT_FRACTION + ratio.toFloat() * (1f - PREAMP_SLIDER_SPLIT_FRACTION))
            .coerceIn(PREAMP_SLIDER_SPLIT_FRACTION, 1f)
    }
}

/**
 * Inverse 2-zone mapping from normalized slider position (`0f..1f`) to dB:
 * - Left zone (`<= 30%`) snaps to `0.5 dB` steps
 * - Right zone (`> 30%`) snaps to `0.1 dB` steps
 */
fun sliderPosToPreampDb(pos: Float): Double {
    val p = pos.coerceIn(0f, 1f).toDouble()
    val split = PREAMP_SLIDER_SPLIT_FRACTION.toDouble()
    return if (p <= split) {
        val raw = PREAMP_MIN_DB + (p / split) * (PREAMP_SLIDER_SPLIT_DB - PREAMP_MIN_DB)
        clampPreampDb(round(raw * 2.0) / 2.0)
    } else {
        val raw = PREAMP_SLIDER_SPLIT_DB +
            ((p - split) / (1.0 - split)) * (PREAMP_MAX_DB - PREAMP_SLIDER_SPLIT_DB)
        clampPreampDb(round(raw * 10.0) / 10.0)
    }
}

enum class FilterType(
    val apoCode: String,
    val labelEn: String,
    val supportsGain: Boolean
) {
    PK("PK", "PK (Peaking)", true),
    LSC("LSC", "LSC (Low Shelf)", true),
    HSC("HSC", "HSC (High Shelf)", true),
    LP("LP", "LP (Low Pass)", false),
    HP("HP", "HP (High Pass)", false),
    NO("NO", "NO (Notch)", false),
    BP("BP", "BP (Band Pass)", false),
    AP("AP", "AP (All Pass)", false);

    companion object {
        fun fromApoToken(token: String): FilterType? {
            return when (token.trim().uppercase()) {
                "PK", "PEQ", "MODAL" -> PK
                "LSC", "LS", "LOWSHELF" -> LSC
                "HSC", "HS", "HIGHSHELF" -> HSC
                "LP", "LPQ", "LOWPASS" -> LP
                "HP", "HPQ", "HIGHPASS" -> HP
                "NO", "NOTCH" -> NO
                "BP", "BANDPASS" -> BP
                "AP", "ALLPASS" -> AP
                else -> null
            }
        }
    }
}

enum class PeqChannelTarget {
    COMMON,
    LEFT,
    RIGHT
}

enum class SwapPosition {
    BEFORE_INDEPENDENT_EQ,
    AFTER_INDEPENDENT_EQ
}

data class PeqBand(
    val id: String = UUID.randomUUID().toString(),
    val enabled: Boolean = true,
    val type: FilterType = FilterType.PK,
    val frequencyHz: Double = 1000.0,
    val gainDb: Double = 0.0,
    val q: Double = 1.0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("enabled", enabled)
        put("type", type.name)
        put("frequencyHz", frequencyHz)
        put("gainDb", gainDb)
        put("q", q)
    }

    companion object {
        fun fromJson(obj: JSONObject): PeqBand = PeqBand(
            id = obj.optString("id", UUID.randomUUID().toString()),
            enabled = obj.optBoolean("enabled", true),
            type = FilterType.entries.firstOrNull { it.name == obj.optString("type") } ?: FilterType.PK,
            frequencyHz = obj.optDouble("frequencyHz", 1000.0).coerceIn(10.0, 22000.0),
            gainDb = obj.optDouble("gainDb", 0.0).coerceIn(-30.0, 30.0),
            q = obj.optDouble("q", 1.0).coerceIn(0.05, 30.0)
        )
    }
}

private fun parseBandsJson(obj: JSONObject, key: String): List<PeqBand> {
    val arr = obj.optJSONArray(key) ?: return emptyList()
    return buildList(arr.length()) {
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { add(PeqBand.fromJson(it)) }
        }
    }
}

private fun bandsToJson(bands: List<PeqBand>): JSONArray =
    JSONArray().apply { bands.forEach { put(it.toJson()) } }

private fun bandsEqualWithTolerance(a: List<PeqBand>, other: List<PeqBand>): Boolean {
    if (a.size != other.size) return false
    for (i in a.indices) {
        val x = a[i]
        val y = other[i]
        if (x.enabled != y.enabled || x.type != y.type) return false
        if (abs(x.frequencyHz - y.frequencyHz) > 1e-3) return false
        if (abs(x.gainDb - y.gainDb) > 1e-4) return false
        if (abs(x.q - y.q) > 1e-4) return false
    }
    return true
}

data class IndependentPeqBaseline(
    val leftPreampDb: Double = 0.0,
    val leftBands: List<PeqBand> = emptyList(),
    val rightPreampDb: Double = 0.0,
    val rightBands: List<PeqBand> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("leftPreampDb", leftPreampDb)
        put("leftBands", bandsToJson(leftBands))
        put("rightPreampDb", rightPreampDb)
        put("rightBands", bandsToJson(rightBands))
    }

    companion object {
        fun fromPreset(preset: IndependentPeqPreset): IndependentPeqBaseline = IndependentPeqBaseline(
            leftPreampDb = preset.leftPreampDb,
            leftBands = preset.leftBands.map { it.copy() },
            rightPreampDb = preset.rightPreampDb,
            rightBands = preset.rightBands.map { it.copy() }
        )

        fun fromJson(obj: JSONObject): IndependentPeqBaseline = IndependentPeqBaseline(
            leftPreampDb = obj.optDouble("leftPreampDb", 0.0),
            leftBands = parseBandsJson(obj, "leftBands"),
            rightPreampDb = obj.optDouble("rightPreampDb", 0.0),
            rightBands = parseBandsJson(obj, "rightBands")
        )
    }
}

/** Standalone L/R independent EQ preset managed in the "L/R Presets" section. */
data class IndependentPeqPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val leftPreampDb: Double = 0.0,
    val leftBands: List<PeqBand> = emptyList(),
    val rightPreampDb: Double = 0.0,
    val rightBands: List<PeqBand> = emptyList(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val baseline: IndependentPeqBaseline? = null
) {
    val isModifiedFromBaseline: Boolean
        get() = baseline != null && !matchesBaseline(baseline)

    val hasAnyFilterOrGain: Boolean
        get() = leftBands.isNotEmpty() || rightBands.isNotEmpty() ||
            abs(leftPreampDb) > 1e-4 || abs(rightPreampDb) > 1e-4

    fun matchesBaseline(b: IndependentPeqBaseline): Boolean =
        abs(leftPreampDb - b.leftPreampDb) <= 1e-4 &&
            abs(rightPreampDb - b.rightPreampDb) <= 1e-4 &&
            bandsEqualWithTolerance(leftBands, b.leftBands) &&
            bandsEqualWithTolerance(rightBands, b.rightBands)

    fun revertToBaseline(): IndependentPeqPreset {
        val b = baseline ?: return this
        return copy(
            leftPreampDb = b.leftPreampDb,
            leftBands = b.leftBands.map { it.copy() },
            rightPreampDb = b.rightPreampDb,
            rightBands = b.rightBands.map { it.copy() },
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    fun withCapturedBaseline(): IndependentPeqPreset =
        copy(baseline = IndependentPeqBaseline.fromPreset(this))

    fun preampFor(target: PeqChannelTarget): Double = when (target) {
        PeqChannelTarget.COMMON -> 0.0
        PeqChannelTarget.LEFT -> leftPreampDb
        PeqChannelTarget.RIGHT -> rightPreampDb
    }

    fun bandsFor(target: PeqChannelTarget): List<PeqBand> = when (target) {
        PeqChannelTarget.COMMON -> emptyList()
        PeqChannelTarget.LEFT -> leftBands
        PeqChannelTarget.RIGHT -> rightBands
    }

    fun withTarget(
        target: PeqChannelTarget,
        preampDb: Double = preampFor(target),
        bands: List<PeqBand> = bandsFor(target)
    ): IndependentPeqPreset = when (target) {
        PeqChannelTarget.COMMON -> this
        PeqChannelTarget.LEFT -> copy(
            leftPreampDb = preampDb,
            leftBands = bands,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        PeqChannelTarget.RIGHT -> copy(
            rightPreampDb = preampDb,
            rightBands = bands,
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("leftPreampDb", leftPreampDb)
        put("leftBands", bandsToJson(leftBands))
        put("rightPreampDb", rightPreampDb)
        put("rightBands", bandsToJson(rightBands))
        put("updatedAtEpochMs", updatedAtEpochMs)
        if (baseline != null) {
            put("baseline", baseline.toJson())
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): IndependentPeqPreset {
            val baseline = obj.optJSONObject("baseline")?.let { IndependentPeqBaseline.fromJson(it) }
            return IndependentPeqPreset(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "L/R"),
                leftPreampDb = obj.optDouble("leftPreampDb", 0.0),
                leftBands = parseBandsJson(obj, "leftBands"),
                rightPreampDb = obj.optDouble("rightPreampDb", 0.0),
                rightBands = parseBandsJson(obj, "rightBands"),
                updatedAtEpochMs = obj.optLong("updatedAtEpochMs", System.currentTimeMillis()),
                baseline = baseline
            )
        }
    }
}

data class EqPresetBaseline(
    val commonPreampDb: Double = 0.0,
    val commonBands: List<PeqBand> = emptyList(),
    val leftPreampDb: Double = 0.0,
    val leftBands: List<PeqBand> = emptyList(),
    val rightPreampDb: Double = 0.0,
    val rightBands: List<PeqBand> = emptyList(),
    val channelSwap: Boolean = false,
    val swapPosition: SwapPosition = SwapPosition.BEFORE_INDEPENDENT_EQ
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("commonPreampDb", commonPreampDb)
        put("commonBands", bandsToJson(commonBands))
        put("leftPreampDb", leftPreampDb)
        put("leftBands", bandsToJson(leftBands))
        put("rightPreampDb", rightPreampDb)
        put("rightBands", bandsToJson(rightBands))
        put("channelSwap", channelSwap)
        put("swapPosition", swapPosition.name)
    }

    companion object {
        fun fromPreset(preset: EqPreset): EqPresetBaseline = EqPresetBaseline(
            commonPreampDb = preset.commonPreampDb,
            commonBands = preset.commonBands.map { it.copy() },
            leftPreampDb = preset.leftPreampDb,
            leftBands = preset.leftBands.map { it.copy() },
            rightPreampDb = preset.rightPreampDb,
            rightBands = preset.rightBands.map { it.copy() },
            channelSwap = preset.channelSwap,
            swapPosition = preset.swapPosition
        )

        fun fromJson(obj: JSONObject): EqPresetBaseline {
            val posName = obj.optString("swapPosition", SwapPosition.BEFORE_INDEPENDENT_EQ.name)
            val swapPos = SwapPosition.entries.firstOrNull { it.name == posName }
                ?: SwapPosition.BEFORE_INDEPENDENT_EQ
            return EqPresetBaseline(
                commonPreampDb = obj.optDouble("commonPreampDb", 0.0),
                commonBands = parseBandsJson(obj, "commonBands"),
                leftPreampDb = obj.optDouble("leftPreampDb", 0.0),
                leftBands = parseBandsJson(obj, "leftBands"),
                rightPreampDb = obj.optDouble("rightPreampDb", 0.0),
                rightBands = parseBandsJson(obj, "rightBands"),
                channelSwap = obj.optBoolean("channelSwap", false),
                swapPosition = swapPos
            )
        }
    }
}

data class EqPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val commonPreampDb: Double = 0.0,
    val commonBands: List<PeqBand> = emptyList(),
    val leftPreampDb: Double = 0.0,
    val leftBands: List<PeqBand> = emptyList(),
    val rightPreampDb: Double = 0.0,
    val rightBands: List<PeqBand> = emptyList(),
    val channelSwap: Boolean = false,
    val swapPosition: SwapPosition = SwapPosition.BEFORE_INDEPENDENT_EQ,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val baseline: EqPresetBaseline? = null,
    val linkedIndependentPresetId: String? = null
) {
    val isModifiedFromBaseline: Boolean
        get() = baseline != null && !matchesBaseline(baseline)

    val isCommonModifiedFromBaseline: Boolean
        get() = baseline != null && (
            abs(commonPreampDb - baseline.commonPreampDb) > 1e-4 ||
                !bandsEqualWithTolerance(commonBands, baseline.commonBands)
            )

    val hasIndependentSnapshot: Boolean
        get() = leftBands.isNotEmpty() || rightBands.isNotEmpty() ||
            abs(leftPreampDb) > 1e-4 || abs(rightPreampDb) > 1e-4

    val hasIndependentPart: Boolean
        get() = linkedIndependentPresetId != null || hasIndependentSnapshot

    fun matchesBaseline(b: EqPresetBaseline): Boolean =
        abs(commonPreampDb - b.commonPreampDb) <= 1e-4 &&
            abs(leftPreampDb - b.leftPreampDb) <= 1e-4 &&
            abs(rightPreampDb - b.rightPreampDb) <= 1e-4 &&
            channelSwap == b.channelSwap &&
            swapPosition == b.swapPosition &&
            bandsEqualWithTolerance(commonBands, b.commonBands) &&
            bandsEqualWithTolerance(leftBands, b.leftBands) &&
            bandsEqualWithTolerance(rightBands, b.rightBands)

    fun revertToBaseline(): EqPreset {
        val b = baseline ?: return this
        return copy(
            commonPreampDb = b.commonPreampDb,
            commonBands = b.commonBands.map { it.copy() },
            leftPreampDb = b.leftPreampDb,
            leftBands = b.leftBands.map { it.copy() },
            rightPreampDb = b.rightPreampDb,
            rightBands = b.rightBands.map { it.copy() },
            channelSwap = b.channelSwap,
            swapPosition = b.swapPosition,
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    fun revertCommonToBaseline(): EqPreset {
        val b = baseline ?: return this
        return copy(
            commonPreampDb = b.commonPreampDb,
            commonBands = b.commonBands.map { it.copy() },
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    fun withCapturedBaseline(): EqPreset = copy(baseline = EqPresetBaseline.fromPreset(this))

    fun preampFor(target: PeqChannelTarget): Double = when (target) {
        PeqChannelTarget.COMMON -> commonPreampDb
        PeqChannelTarget.LEFT -> leftPreampDb
        PeqChannelTarget.RIGHT -> rightPreampDb
    }

    fun bandsFor(target: PeqChannelTarget): List<PeqBand> = when (target) {
        PeqChannelTarget.COMMON -> commonBands
        PeqChannelTarget.LEFT -> leftBands
        PeqChannelTarget.RIGHT -> rightBands
    }

    fun withTarget(
        target: PeqChannelTarget,
        preampDb: Double = preampFor(target),
        bands: List<PeqBand> = bandsFor(target)
    ): EqPreset = when (target) {
        PeqChannelTarget.COMMON -> copy(
            commonPreampDb = preampDb,
            commonBands = bands,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        PeqChannelTarget.LEFT -> copy(
            leftPreampDb = preampDb,
            leftBands = bands,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        PeqChannelTarget.RIGHT -> copy(
            rightPreampDb = preampDb,
            rightBands = bands,
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("commonPreampDb", commonPreampDb)
        put("commonBands", bandsToJson(commonBands))
        put("leftPreampDb", leftPreampDb)
        put("leftBands", bandsToJson(leftBands))
        put("rightPreampDb", rightPreampDb)
        put("rightBands", bandsToJson(rightBands))
        put("channelSwap", channelSwap)
        put("swapPosition", swapPosition.name)
        put("updatedAtEpochMs", updatedAtEpochMs)
        if (baseline != null) {
            put("baseline", baseline.toJson())
        }
        if (linkedIndependentPresetId != null) {
            put("linkedIndependentPresetId", linkedIndependentPresetId)
        }
    }

    companion object {
        fun fromJsonWithSwapFlag(obj: JSONObject): Pair<EqPreset, Boolean> {
            val hasSwapKey = obj.has("channelSwap")
            val posName = obj.optString("swapPosition", SwapPosition.BEFORE_INDEPENDENT_EQ.name)
            val swapPos = SwapPosition.entries.firstOrNull { it.name == posName }
                ?: SwapPosition.BEFORE_INDEPENDENT_EQ
            val baseline = obj.optJSONObject("baseline")?.let { EqPresetBaseline.fromJson(it) }
            val linkedId = if (obj.isNull("linkedIndependentPresetId")) {
                null
            } else {
                obj.optString("linkedIndependentPresetId").takeIf { it.isNotEmpty() }
            }
            val preset = EqPreset(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "Untitled"),
                commonPreampDb = obj.optDouble("commonPreampDb", 0.0),
                commonBands = parseBandsJson(obj, "commonBands"),
                leftPreampDb = obj.optDouble("leftPreampDb", 0.0),
                leftBands = parseBandsJson(obj, "leftBands"),
                rightPreampDb = obj.optDouble("rightPreampDb", 0.0),
                rightBands = parseBandsJson(obj, "rightBands"),
                channelSwap = obj.optBoolean("channelSwap", false),
                swapPosition = swapPos,
                updatedAtEpochMs = obj.optLong("updatedAtEpochMs", System.currentTimeMillis()),
                baseline = baseline,
                linkedIndependentPresetId = linkedId
            )
            return preset to hasSwapKey
        }

        fun fromJson(obj: JSONObject): EqPreset = fromJsonWithSwapFlag(obj).first
    }
}

data class ActiveDspConfig(
    val bypass: Boolean = false,
    val channelSwap: Boolean = false,
    val swapPosition: SwapPosition = SwapPosition.BEFORE_INDEPENDENT_EQ,
    val preset: EqPreset
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("bypass", bypass)
        put("channelSwap", channelSwap)
        put("swapPosition", swapPosition.name)
        put("preset", preset.toJson())
    }

    fun toJsonString(): String = toJson().toString()

    companion object {
        fun fromJsonString(json: String): ActiveDspConfig {
            val obj = JSONObject(json)
            val posName = obj.optString("swapPosition", SwapPosition.BEFORE_INDEPENDENT_EQ.name)
            val swapPos = SwapPosition.entries.firstOrNull { it.name == posName }
                ?: SwapPosition.BEFORE_INDEPENDENT_EQ
            val presetObj = obj.getJSONObject("preset")
            return ActiveDspConfig(
                bypass = obj.optBoolean("bypass", false),
                channelSwap = obj.optBoolean("channelSwap", false),
                swapPosition = swapPos,
                preset = EqPreset.fromJson(presetObj)
            )
        }
    }
}

enum class DeviceCategory {
    SPEAKER,
    BLUETOOTH,
    USB_DAC,
    WIRED
}

enum class DeviceRuleAction {
    FOLLOW_MANUAL,
    PRESET,
    EQ_OFF
}

/** Per-device action applied specifically to the independent L/R preset layer. */
enum class DeviceIndependentRuleAction {
    KEEP_CURRENT,
    CLEAR_NONE,
    APPLY_PRESET
}

/** Per-device rule controlling whether L⇄R channel swap follows manual toggle or is pinned. */
enum class DeviceSwapRuleAction {
    FOLLOW_MANUAL,
    FORCE_OFF,
    FORCE_ON
}

data class OutputDeviceSnapshot(
    val deviceKey: String,
    val displayName: String,
    val category: DeviceCategory,
    val address: String = "",
    val isActive: Boolean = false
)

data class DeviceRoutingRule(
    val deviceKey: String,
    val displayName: String,
    val category: DeviceCategory,
    val action: DeviceRuleAction = DeviceRuleAction.FOLLOW_MANUAL,
    val presetId: String? = null,
    val independentAction: DeviceIndependentRuleAction = DeviceIndependentRuleAction.KEEP_CURRENT,
    val independentPresetId: String? = null,
    val channelSwapAction: DeviceSwapRuleAction = DeviceSwapRuleAction.FOLLOW_MANUAL
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("deviceKey", deviceKey)
        put("displayName", displayName)
        put("category", category.name)
        put("action", action.name)
        put("presetId", presetId ?: JSONObject.NULL)
        put("independentAction", independentAction.name)
        put("independentPresetId", independentPresetId ?: JSONObject.NULL)
        put("channelSwapAction", channelSwapAction.name)
    }

    companion object {
        fun fromJson(obj: JSONObject): DeviceRoutingRule {
            val cat = DeviceCategory.entries.firstOrNull { it.name == obj.optString("category") }
                ?: DeviceCategory.SPEAKER
            val action = DeviceRuleAction.entries.firstOrNull { it.name == obj.optString("action") }
                ?: DeviceRuleAction.FOLLOW_MANUAL
            val pid = if (obj.isNull("presetId")) null else obj.optString("presetId").takeIf { it.isNotEmpty() }
            val indepAction = DeviceIndependentRuleAction.entries.firstOrNull {
                it.name == obj.optString("independentAction")
            } ?: DeviceIndependentRuleAction.KEEP_CURRENT
            val indepPid = if (obj.isNull("independentPresetId")) {
                null
            } else {
                obj.optString("independentPresetId").takeIf { it.isNotEmpty() }
            }
            val swapAction = DeviceSwapRuleAction.entries.firstOrNull {
                it.name == obj.optString("channelSwapAction")
            } ?: DeviceSwapRuleAction.FOLLOW_MANUAL
            return DeviceRoutingRule(
                deviceKey = obj.optString("deviceKey"),
                displayName = obj.optString("displayName", cat.name),
                category = cat,
                action = action,
                presetId = pid,
                independentAction = indepAction,
                independentPresetId = indepPid,
                channelSwapAction = swapAction
            )
        }
    }
}

/**
 * Combines the selected Common preset layer and optional L/R independent layer plus
 * current global/per-device channel swap state into a single DSP preset snapshot.
 */
fun composeEffectivePreset(
    commonPreset: EqPreset,
    independentPreset: IndependentPeqPreset?,
    channelSwap: Boolean,
    swapPosition: SwapPosition
): EqPreset = commonPreset.copy(
    leftPreampDb = independentPreset?.leftPreampDb ?: 0.0,
    leftBands = independentPreset?.leftBands?.map { it.copy() } ?: emptyList(),
    rightPreampDb = independentPreset?.rightPreampDb ?: 0.0,
    rightBands = independentPreset?.rightBands?.map { it.copy() } ?: emptyList(),
    channelSwap = channelSwap,
    swapPosition = swapPosition
)

enum class BundleSelectionOutcome {
    APPLY_COMMON_ONLY,
    AUTO_APPLY_BOTH,
    PROMPT_COMMON_OR_BOTH
}

data class BundleSelectionEvaluation(
    val outcome: BundleSelectionOutcome,
    val targetIndependentPresetId: String?
)

/**
 * Resolves how selecting [chosenPreset] from Saved Presets behaves given the currently active
 * L/R state ([activeIndependentId], [hasDraftIndependent]) and available [independentPresets].
 * When [chosenPreset] includes L/R data linked to an existing L/R preset:
 * - If no L/R is currently active (and no draft L/R edits), applies both without prompting.
 * - If the same L/R preset is already active (and no draft edits), applies cleanly without prompting.
 * - Otherwise requests explicit user choice between changing Common-only vs both Common and L/R.
 */
fun evaluateSavedPresetSelection(
    chosenPreset: EqPreset,
    activeIndependentId: String?,
    hasDraftIndependent: Boolean,
    independentPresets: List<IndependentPeqPreset>
): BundleSelectionEvaluation {
    val resolvedIndepId = chosenPreset.linkedIndependentPresetId?.takeIf { id ->
        independentPresets.any { it.id == id }
    }
    if (resolvedIndepId == null) {
        return BundleSelectionEvaluation(
            outcome = BundleSelectionOutcome.APPLY_COMMON_ONLY,
            targetIndependentPresetId = null
        )
    }
    if (!hasDraftIndependent && (activeIndependentId == null || activeIndependentId == resolvedIndepId)) {
        return BundleSelectionEvaluation(
            outcome = BundleSelectionOutcome.AUTO_APPLY_BOTH,
            targetIndependentPresetId = resolvedIndepId
        )
    }
    return BundleSelectionEvaluation(
        outcome = BundleSelectionOutcome.PROMPT_COMMON_OR_BOTH,
        targetIndependentPresetId = resolvedIndepId
    )
}

/** Per-app processing mode relative to global settings. */
enum class AppAudioMode {
    STANDARD,
    BYPASS,
    LOW_LATENCY
}

/** Installed package candidate shown in the per-app routing mode selector. */
data class AppBypassCandidate(
    val packageName: String,
    val label: String,
    val uid: Int,
    val isSystemApp: Boolean,
    val hasLauncherEntry: Boolean
)

/** Resolved per-app rule entry for display in Routing tab. */
data class AppRuleItem(
    val packageName: String,
    val displayName: String,
    val uid: Int?,
    val isInstalled: Boolean,
    val mode: AppAudioMode
)

/**
 * Merges mandatory loopback-prevention UIDs ([serviceUid], [clientAppUid]) with user-selected
 * bypass UIDs, dropping non-positive entries while preserving insertion order without duplicates.
 */
fun mergeExcludedUids(
    serviceUid: Int,
    clientAppUid: Int,
    extraExcludedUids: IntArray?
): IntArray {
    val merged = LinkedHashSet<Int>()
    if (serviceUid > 0) merged.add(serviceUid)
    if (clientAppUid > 0) merged.add(clientAppUid)
    extraExcludedUids?.forEach { uid ->
        if (uid > 0) merged.add(uid)
    }
    return merged.toIntArray()
}

/** Deterministic result of evaluating [deviceRules] for an active [deviceKey]. */
data class DeviceRuleEvaluationOutcome(
    val matchedRule: DeviceRoutingRule?,
    val selectedCommonPresetId: String,
    val selectedIndependentPresetId: String?,
    val draftIndependentPreset: IndependentPeqPreset?,
    val channelSwap: Boolean,
    val swapPosition: SwapPosition,
    val pausedByDeviceRule: Boolean,
    val commonChanged: Boolean,
    val independentChanged: Boolean,
    val channelSwapChanged: Boolean,
    val clearedDraftIndependent: Boolean,
    val effectivePreset: EqPreset
)

/**
 * Resolves the effective preset, L/R preset, channel swap, and EQ-OFF pause state for [deviceKey].
 * Shared between the app-side manager and the UID 2000 UserService autonomous monitor.
 */
fun resolveDeviceRoutingOutcome(
    deviceKey: String?,
    deviceRules: List<DeviceRoutingRule>,
    presets: List<EqPreset>,
    independentPresets: List<IndependentPeqPreset>,
    currentCommonPresetId: String,
    currentIndependentPresetId: String?,
    draftIndependentPreset: IndependentPeqPreset?,
    currentChannelSwap: Boolean,
    swapPosition: SwapPosition
): DeviceRuleEvaluationOutcome {
    val rule = deviceKey?.let { key -> deviceRules.firstOrNull { it.deviceKey == key } }
    var nextCommonId = currentCommonPresetId
    var nextIndepId = currentIndependentPresetId
    var nextDraftIndep = draftIndependentPreset
    var nextSwap = currentChannelSwap
    var pausedByRule = false
    var clearedDraft = false

    if (rule != null) {
        if (rule.action != DeviceRuleAction.EQ_OFF) {
            nextSwap = when (rule.channelSwapAction) {
                DeviceSwapRuleAction.FOLLOW_MANUAL -> currentChannelSwap
                DeviceSwapRuleAction.FORCE_OFF -> false
                DeviceSwapRuleAction.FORCE_ON -> true
            }
        }

        when (rule.action) {
            DeviceRuleAction.FOLLOW_MANUAL -> {
                pausedByRule = false
            }
            DeviceRuleAction.EQ_OFF -> {
                pausedByRule = true
            }
            DeviceRuleAction.PRESET -> {
                pausedByRule = false
                rule.presetId?.let { targetId ->
                    if (presets.any { it.id == targetId }) {
                        nextCommonId = targetId
                    }
                }
                when (rule.independentAction) {
                    DeviceIndependentRuleAction.KEEP_CURRENT -> Unit
                    DeviceIndependentRuleAction.CLEAR_NONE -> {
                        nextIndepId = null
                        if (nextDraftIndep != null) clearedDraft = true
                        nextDraftIndep = null
                    }
                    DeviceIndependentRuleAction.APPLY_PRESET -> {
                        val targetId = rule.independentPresetId
                        if (targetId != null && independentPresets.any { it.id == targetId }) {
                            nextIndepId = targetId
                            if (nextDraftIndep != null) clearedDraft = true
                            nextDraftIndep = null
                        }
                    }
                }
            }
        }
    }

    val commonPreset = presets.firstOrNull { it.id == nextCommonId }
        ?: presets.firstOrNull()
        ?: EqPreset(name = "Flat")
    val resolvedCommonId = commonPreset.id
    val resolvedIndepPreset = nextIndepId?.let { id ->
        independentPresets.firstOrNull { it.id == id }
    }
    val resolvedIndepId = resolvedIndepPreset?.id
    val effectiveIndep = resolvedIndepPreset ?: nextDraftIndep
    val effectivePreset = composeEffectivePreset(
        commonPreset = commonPreset,
        independentPreset = effectiveIndep,
        channelSwap = nextSwap,
        swapPosition = swapPosition
    )

    return DeviceRuleEvaluationOutcome(
        matchedRule = rule,
        selectedCommonPresetId = resolvedCommonId,
        selectedIndependentPresetId = resolvedIndepId,
        draftIndependentPreset = nextDraftIndep,
        channelSwap = nextSwap,
        swapPosition = swapPosition,
        pausedByDeviceRule = pausedByRule,
        commonChanged = resolvedCommonId != currentCommonPresetId,
        independentChanged = resolvedIndepId != currentIndependentPresetId,
        channelSwapChanged = nextSwap != currentChannelSwap,
        clearedDraftIndependent = clearedDraft,
        effectivePreset = effectivePreset
    )
}

/**
 * Snapshot synced to the UID 2000 UserService over AIDL so per-device rules still switch
 * immediately when Background Keep-Alive is enabled and the app process is killed.
 */
data class AutonomousDevicePolicy(
    val enabled: Boolean = false,
    val clientAppUid: Int = -1,
    val excludedUids: List<Int> = emptyList(),
    val globalLowLatency: Boolean = false,
    val lowLatencyUids: List<Int> = emptyList(),
    val presets: List<EqPreset> = emptyList(),
    val independentPresets: List<IndependentPeqPreset> = emptyList(),
    val deviceRules: List<DeviceRoutingRule> = emptyList(),
    val selectedPresetId: String = "",
    val selectedIndependentPresetId: String? = null,
    val draftIndependentPreset: IndependentPeqPreset? = null,
    val channelSwap: Boolean = false,
    val swapPosition: SwapPosition = SwapPosition.BEFORE_INDEPENDENT_EQ,
    val pausedByDeviceRule: Boolean = false,
    val activeDeviceKey: String? = null
) {
    fun toJsonString(): String = JSONObject().apply {
        put("enabled", enabled)
        put("clientAppUid", clientAppUid)
        put("excludedUids", JSONArray().apply { for (u in excludedUids) put(u) })
        put("globalLowLatency", globalLowLatency)
        put("lowLatencyUids", JSONArray().apply { for (u in lowLatencyUids) put(u) })
        put("presets", JSONArray().apply { for (p in presets) put(p.toJson()) })
        put("independentPresets", JSONArray().apply { for (p in independentPresets) put(p.toJson()) })
        put("deviceRules", JSONArray().apply { for (r in deviceRules) put(r.toJson()) })
        put("selectedPresetId", selectedPresetId)
        put("selectedIndependentPresetId", selectedIndependentPresetId ?: JSONObject.NULL)
        put("draftIndependentPreset", draftIndependentPreset?.toJson() ?: JSONObject.NULL)
        put("channelSwap", channelSwap)
        put("swapPosition", swapPosition.name)
        put("pausedByDeviceRule", pausedByDeviceRule)
        put("activeDeviceKey", activeDeviceKey ?: JSONObject.NULL)
    }.toString()

    companion object {
        private fun parseIntList(arr: JSONArray?): List<Int> {
            if (arr == null || arr.length() == 0) return emptyList()
            val list = ArrayList<Int>(arr.length())
            for (i in 0 until arr.length()) {
                val v = arr.optInt(i, -1)
                if (v > 0) list.add(v)
            }
            return list
        }

        fun fromJsonString(json: String?): AutonomousDevicePolicy {
            if (json.isNullOrBlank()) return AutonomousDevicePolicy()
            val obj = JSONObject(json)
            val enabled = obj.optBoolean("enabled", false)
            if (!enabled) {
                return AutonomousDevicePolicy(enabled = false)
            }
            val posName = obj.optString("swapPosition", SwapPosition.BEFORE_INDEPENDENT_EQ.name)
            val swapPos = SwapPosition.entries.firstOrNull { it.name == posName }
                ?: SwapPosition.BEFORE_INDEPENDENT_EQ

            val presetArr = obj.optJSONArray("presets") ?: JSONArray()
            val presets = ArrayList<EqPreset>(presetArr.length())
            for (i in 0 until presetArr.length()) {
                presetArr.optJSONObject(i)?.let { presets.add(EqPreset.fromJson(it)) }
            }

            val indepArr = obj.optJSONArray("independentPresets") ?: JSONArray()
            val indepPresets = ArrayList<IndependentPeqPreset>(indepArr.length())
            for (i in 0 until indepArr.length()) {
                indepArr.optJSONObject(i)?.let { indepPresets.add(IndependentPeqPreset.fromJson(it)) }
            }

            val rulesArr = obj.optJSONArray("deviceRules") ?: JSONArray()
            val rules = ArrayList<DeviceRoutingRule>(rulesArr.length())
            for (i in 0 until rulesArr.length()) {
                rulesArr.optJSONObject(i)?.let { rules.add(DeviceRoutingRule.fromJson(it)) }
            }

            val selectedIndep = if (obj.isNull("selectedIndependentPresetId")) {
                null
            } else {
                obj.optString("selectedIndependentPresetId").takeIf { it.isNotEmpty() }
            }
            val draftIndep = obj.optJSONObject("draftIndependentPreset")?.let {
                IndependentPeqPreset.fromJson(it)
            }
            val activeDevKey = if (obj.isNull("activeDeviceKey")) {
                null
            } else {
                obj.optString("activeDeviceKey").takeIf { it.isNotEmpty() }
            }

            return AutonomousDevicePolicy(
                enabled = true,
                clientAppUid = obj.optInt("clientAppUid", -1),
                excludedUids = parseIntList(obj.optJSONArray("excludedUids")),
                globalLowLatency = obj.optBoolean("globalLowLatency", false),
                lowLatencyUids = parseIntList(obj.optJSONArray("lowLatencyUids")),
                presets = presets,
                independentPresets = indepPresets,
                deviceRules = rules,
                selectedPresetId = obj.optString("selectedPresetId", ""),
                selectedIndependentPresetId = selectedIndep,
                draftIndependentPreset = draftIndep,
                channelSwap = obj.optBoolean("channelSwap", false),
                swapPosition = swapPos,
                pausedByDeviceRule = obj.optBoolean("pausedByDeviceRule", false),
                activeDeviceKey = activeDevKey
            )
        }
    }
}
