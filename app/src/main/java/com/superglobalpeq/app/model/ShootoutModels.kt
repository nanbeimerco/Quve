package com.superglobalpeq.app.model

import org.json.JSONArray
import org.json.JSONObject

const val SHOOTOUT_MIN_CONTESTANTS = 2
const val SHOOTOUT_MAX_CONTESTANTS = 8
const val SHOOTOUT_VOTES_PER_CONTESTANT = 10
const val SHOOTOUT_NO_INDEPENDENT_ID = "__none__"

enum class ShootoutOutcome {
    A,
    B,
    TIE
}

enum class ShootoutSessionStatus {
    RUNNING,
    INTERRUPTED,
    FINISHED
}

/** One blind decision. Sides are stored resolved to preset ids, so A/B never needs replaying. */
data class ShootoutVote(
    val contestantA: String,
    val contestantB: String,
    val outcome: ShootoutOutcome,
    val atEpochMs: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("a", contestantA)
        put("b", contestantB)
        put("outcome", outcome.name)
        put("at", atEpochMs)
    }

    companion object {
        fun fromJson(obj: JSONObject): ShootoutVote? {
            val outcome = ShootoutOutcome.entries.firstOrNull { it.name == obj.optString("outcome") }
                ?: return null
            val a = obj.optString("a").takeIf { it.isNotEmpty() } ?: return null
            val b = obj.optString("b").takeIf { it.isNotEmpty() } ?: return null
            return ShootoutVote(a, b, outcome, obj.optLong("at", 0L))
        }
    }
}

/**
 * Everything a session needs to be replayed: contestant EQs and the shared L/R layer are
 * snapshotted at start, so later preset edits can never change an existing result.
 */
data class ShootoutSessionRecord(
    val id: String,
    val createdAtEpochMs: Long,
    val deviceKey: String,
    val deviceName: String,
    val seed: Long,
    val contestants: List<EqPreset>,
    val independentPreset: IndependentPeqPreset? = null,
    val channelSwap: Boolean = false,
    val swapPosition: SwapPosition = SwapPosition.BEFORE_INDEPENDENT_EQ,
    val votes: List<ShootoutVote> = emptyList(),
    val status: ShootoutSessionStatus = ShootoutSessionStatus.RUNNING,
    /** True when each contestant already contains its own Common and L/R layers. */
    val contestantsComposed: Boolean = false,
    /** True when the swap was set for Shootout rather than inherited from the normal EQ. */
    val channelSwapChosen: Boolean = false
) {
    val recommendedVotes: Int
        get() = contestants.size * SHOOTOUT_VOTES_PER_CONTESTANT

    fun effectivePreset(contestantId: String): EqPreset {
        val contestant = contestants.first { it.id == contestantId }
        if (contestantsComposed) return contestant
        return composeEffectivePreset(contestant, independentPreset, channelSwap, swapPosition)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("createdAt", createdAtEpochMs)
        put("deviceKey", deviceKey)
        put("deviceName", deviceName)
        put("seed", seed)
        put("contestants", JSONArray().apply { contestants.forEach { put(it.toJson()) } })
        put("independentPreset", independentPreset?.toJson() ?: JSONObject.NULL)
        put("channelSwap", channelSwap)
        put("swapPosition", swapPosition.name)
        put("votes", JSONArray().apply { votes.forEach { put(it.toJson()) } })
        put("status", status.name)
        put("contestantsComposed", contestantsComposed)
        put("channelSwapChosen", channelSwapChosen)
    }

    companion object {
        fun fromJson(obj: JSONObject): ShootoutSessionRecord? {
            val id = obj.optString("id").takeIf { it.isNotEmpty() } ?: return null
            val contestantsJson = obj.optJSONArray("contestants") ?: return null
            val contestants = buildList {
                for (i in 0 until contestantsJson.length()) {
                    contestantsJson.optJSONObject(i)?.let { add(EqPreset.fromJson(it)) }
                }
            }
            if (contestants.size < SHOOTOUT_MIN_CONTESTANTS) return null
            val knownIds = contestants.map { it.id }.toSet()
            val votesJson = obj.optJSONArray("votes") ?: JSONArray()
            val votes = buildList {
                for (i in 0 until votesJson.length()) {
                    val vote = votesJson.optJSONObject(i)?.let { ShootoutVote.fromJson(it) }
                    if (vote != null && vote.contestantA in knownIds && vote.contestantB in knownIds) {
                        add(vote)
                    }
                }
            }
            return ShootoutSessionRecord(
                id = id,
                createdAtEpochMs = obj.optLong("createdAt", 0L),
                deviceKey = obj.optString("deviceKey"),
                deviceName = obj.optString("deviceName"),
                seed = obj.optLong("seed", 0L),
                contestants = contestants,
                independentPreset = obj.optJSONObject("independentPreset")
                    ?.let { IndependentPeqPreset.fromJson(it) },
                channelSwap = obj.optBoolean("channelSwap", false),
                swapPosition = SwapPosition.entries.firstOrNull { it.name == obj.optString("swapPosition") }
                    ?: SwapPosition.BEFORE_INDEPENDENT_EQ,
                votes = votes,
                status = ShootoutSessionStatus.entries.firstOrNull { it.name == obj.optString("status") }
                    ?: ShootoutSessionStatus.INTERRUPTED,
                contestantsComposed = obj.optBoolean("contestantsComposed", false),
                channelSwapChosen = obj.optBoolean("channelSwapChosen", false)
            )
        }
    }
}

/** The Shootout setup choices. Null values follow the current normal EQ settings. */
data class ShootoutSetupSelection(
    val commonIds: List<String> = emptyList(),
    val independentId: String? = null,
    val channelSwap: Boolean? = null,
    val swapPosition: SwapPosition? = null
)

fun ShootoutSessionRecord.toSetupSelection(): ShootoutSetupSelection = ShootoutSetupSelection(
    commonIds = contestants.map { it.id.substringBefore("::") },
    independentId = independentPreset?.id ?: SHOOTOUT_NO_INDEPENDENT_ID,
    channelSwap = channelSwap.takeIf { channelSwapChosen },
    swapPosition = swapPosition.takeIf { channelSwapChosen }
)

/** Builds the contestants: each Common EQ, all sharing the single chosen L/R EQ. */
fun composeShootoutContestants(
    commonPresets: List<Pair<EqPreset, String>>,
    independentPreset: IndependentPeqPreset?,
    channelSwap: Boolean,
    swapPosition: SwapPosition
): List<EqPreset> = commonPresets.map { (common, commonName) ->
    composeEffectivePreset(common, independentPreset, channelSwap, swapPosition).copy(
        id = "${common.id}::${independentPreset?.id ?: SHOOTOUT_NO_INDEPENDENT_ID}",
        name = commonName,
        baseline = null,
        linkedIndependentPresetId = null
    )
}

/** Sent to the UID 2000 engine for each round. It carries no contestant names. */
data class ShootoutRoundConfig(
    val roundId: Long,
    val presetA: EqPreset,
    val presetB: EqPreset,
    val trimADb: Double,
    val trimBDb: Double
) {
    fun toJsonString(): String = JSONObject().apply {
        put("roundId", roundId)
        put("presetA", presetA.copy(name = "A", baseline = null).toJson())
        put("presetB", presetB.copy(name = "B", baseline = null).toJson())
        put("trimADb", trimADb)
        put("trimBDb", trimBDb)
    }.toString()

    companion object {
        fun fromJsonString(json: String): ShootoutRoundConfig {
            val obj = JSONObject(json)
            return ShootoutRoundConfig(
                roundId = obj.getLong("roundId"),
                presetA = EqPreset.fromJson(obj.getJSONObject("presetA")),
                presetB = EqPreset.fromJson(obj.getJSONObject("presetB")),
                trimADb = obj.getDouble("trimADb"),
                trimBDb = obj.getDouble("trimBDb")
            )
        }
    }
}
