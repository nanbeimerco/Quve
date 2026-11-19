package com.superglobalpeq.app.shootout

import com.superglobalpeq.app.model.ShootoutOutcome
import com.superglobalpeq.app.model.ShootoutSessionRecord
import com.superglobalpeq.app.model.ShootoutVote
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

data class GlickoRating(
    val rating: Double = Glicko2.INITIAL_RATING,
    val deviation: Double = Glicko2.INITIAL_DEVIATION,
    val volatility: Double = Glicko2.INITIAL_VOLATILITY
)

data class GlickoGame(
    val opponentRating: Double,
    val opponentDeviation: Double,
    val score: Double
)

/** Glicko-2 as specified by Glickman (2013), with the Illinois volatility iteration. */
object Glicko2 {
    const val INITIAL_RATING = 1500.0
    const val INITIAL_DEVIATION = 350.0
    const val INITIAL_VOLATILITY = 0.06
    private const val SCALE = 173.7178
    private const val TAU = 0.5
    private const val EPSILON = 1e-6

    private fun g(phi: Double): Double = 1.0 / sqrt(1.0 + 3.0 * phi * phi / (PI * PI))

    private fun expected(mu: Double, opponentMu: Double, opponentPhi: Double): Double =
        1.0 / (1.0 + exp(-g(opponentPhi) * (mu - opponentMu)))

    fun update(player: GlickoRating, games: List<GlickoGame>): GlickoRating {
        val mu = (player.rating - INITIAL_RATING) / SCALE
        val phi = player.deviation / SCALE
        val sigma = player.volatility
        if (games.isEmpty()) {
            return player.copy(deviation = SCALE * sqrt(phi * phi + sigma * sigma))
        }

        var inverseVariance = 0.0
        var scoreSum = 0.0
        for (game in games) {
            val opponentMu = (game.opponentRating - INITIAL_RATING) / SCALE
            val opponentPhi = game.opponentDeviation / SCALE
            val weight = g(opponentPhi)
            val e = expected(mu, opponentMu, opponentPhi)
            inverseVariance += weight * weight * e * (1.0 - e)
            scoreSum += weight * (game.score - e)
        }
        val variance = 1.0 / inverseVariance
        val delta = variance * scoreSum
        val nextSigma = solveVolatility(delta, phi, variance, sigma)
        val preRatingPhi = sqrt(phi * phi + nextSigma * nextSigma)
        val nextPhi = 1.0 / sqrt(1.0 / (preRatingPhi * preRatingPhi) + 1.0 / variance)
        val nextMu = mu + nextPhi * nextPhi * scoreSum
        return GlickoRating(
            rating = INITIAL_RATING + SCALE * nextMu,
            deviation = SCALE * nextPhi,
            volatility = nextSigma
        )
    }

    private fun solveVolatility(delta: Double, phi: Double, variance: Double, sigma: Double): Double {
        val a = ln(sigma * sigma)
        fun f(x: Double): Double {
            val ex = exp(x)
            val denominator = phi * phi + variance + ex
            return ex * (delta * delta - phi * phi - variance - ex) /
                (2.0 * denominator * denominator) - (x - a) / (TAU * TAU)
        }

        var lower = a
        var upper = if (delta * delta > phi * phi + variance) {
            ln(delta * delta - phi * phi - variance)
        } else {
            var k = 1
            while (f(a - k * TAU) < 0.0) k++
            a - k * TAU
        }
        var fLower = f(lower)
        var fUpper = f(upper)
        while (abs(upper - lower) > EPSILON) {
            val candidate = lower + (lower - upper) * fLower / (fUpper - fLower)
            val fCandidate = f(candidate)
            if (fCandidate * fUpper <= 0.0) {
                lower = upper
                fLower = fUpper
            } else {
                fLower /= 2.0
            }
            upper = candidate
            fUpper = fCandidate
        }
        return exp(lower / 2.0)
    }
}

data class ShootoutMatchRecord(
    val wins: Int = 0,
    val draws: Int = 0,
    val losses: Int = 0
) {
    val games: Int
        get() = wins + draws + losses

    /** Share of available points, with a tie worth half a win. */
    val scoreRate: Double
        get() = if (games == 0) 0.0 else (wins + 0.5 * draws) / games
}

data class ShootoutStanding(
    val contestantId: String,
    val rating: GlickoRating,
    val record: ShootoutMatchRecord
)

data class ShootoutAnalysis(
    val standings: List<ShootoutStanding>,
    /** Provisional rating per contestant after each vote; index 0 is the starting rating. */
    val ratingHistory: Map<String, FloatArray>,
    val headToHead: Map<Pair<String, String>, ShootoutMatchRecord>
) {
    fun recordBetween(contestant: String, opponent: String): ShootoutMatchRecord =
        headToHead[contestant to opponent] ?: ShootoutMatchRecord()
}

data class ShootoutPairing(
    val contestantA: String,
    val contestantB: String
)

object ShootoutAnalyzer {
    /** Enough games for Glicko-2 per rating period without making early results wait too long. */
    private fun periodLength(contestants: Int): Int = max(4, contestants)

    private class Replay(ids: List<String>) {
        val committed = ids.associateWith { GlickoRating() }.toMutableMap()
        val pending = ids.associateWith { ArrayList<GlickoGame>() }

        fun add(vote: ShootoutVote) {
            val ratingA = committed.getValue(vote.contestantA)
            val ratingB = committed.getValue(vote.contestantB)
            val scoreA = when (vote.outcome) {
                ShootoutOutcome.A -> 1.0
                ShootoutOutcome.B -> 0.0
                ShootoutOutcome.TIE -> 0.5
            }
            pending.getValue(vote.contestantA) += GlickoGame(ratingB.rating, ratingB.deviation, scoreA)
            pending.getValue(vote.contestantB) += GlickoGame(ratingA.rating, ratingA.deviation, 1.0 - scoreA)
        }

        fun provisional(id: String): GlickoRating {
            val games = pending.getValue(id)
            return if (games.isEmpty()) committed.getValue(id) else Glicko2.update(committed.getValue(id), games)
        }

        fun commit() {
            val updated = committed.keys.associateWith { Glicko2.update(committed.getValue(it), pending.getValue(it)) }
            committed.putAll(updated)
            pending.values.forEach { it.clear() }
        }
    }

    private fun replay(session: ShootoutSessionRecord, onVote: ((Int, Replay) -> Unit)? = null): Replay {
        val ids = session.contestants.map { it.id }
        val state = Replay(ids)
        val period = periodLength(ids.size)
        session.votes.forEachIndexed { index, vote ->
            state.add(vote)
            onVote?.invoke(index + 1, state)
            if ((index + 1) % period == 0) state.commit()
        }
        return state
    }

    fun analyze(session: ShootoutSessionRecord): ShootoutAnalysis {
        val ids = session.contestants.map { it.id }
        val history = ids.associateWith { id ->
            FloatArray(session.votes.size + 1).also { it[0] = Glicko2.INITIAL_RATING.toFloat() }
        }
        val state = replay(session) { voteNumber, replay ->
            ids.forEach { id ->
                history.getValue(id)[voteNumber] = replay.provisional(id).rating.toFloat()
            }
        }

        val records = HashMap<String, ShootoutMatchRecord>()
        val headToHead = HashMap<Pair<String, String>, ShootoutMatchRecord>()
        fun apply(contestant: String, opponent: String, outcome: Int) {
            fun ShootoutMatchRecord.plus() = when {
                outcome > 0 -> copy(wins = wins + 1)
                outcome < 0 -> copy(losses = losses + 1)
                else -> copy(draws = draws + 1)
            }
            records[contestant] = (records[contestant] ?: ShootoutMatchRecord()).plus()
            val key = contestant to opponent
            headToHead[key] = (headToHead[key] ?: ShootoutMatchRecord()).plus()
        }
        for (vote in session.votes) {
            val forA = when (vote.outcome) {
                ShootoutOutcome.A -> 1
                ShootoutOutcome.B -> -1
                ShootoutOutcome.TIE -> 0
            }
            apply(vote.contestantA, vote.contestantB, forA)
            apply(vote.contestantB, vote.contestantA, -forA)
        }

        val standings = ids.map { id ->
            ShootoutStanding(id, state.provisional(id), records[id] ?: ShootoutMatchRecord())
        }.sortedWith(
            compareByDescending<ShootoutStanding> { it.rating.rating }
                .thenByDescending { it.record.scoreRate }
        )
        return ShootoutAnalysis(standings, history, headToHead)
    }

    /**
     * Chooses the next blind pair. The result depends only on the seed and vote log, so an
     * interrupted session resumes on the same pair and the same A/B assignment.
     */
    fun nextPairing(session: ShootoutSessionRecord): ShootoutPairing {
        val ids = session.contestants.map { it.id }
        val state = replay(session)
        val random = Random(session.seed xor (session.votes.size * 0x9E3779B97F4A7C15uL.toLong()))
        val last = session.votes.lastOrNull()
        val meetings = HashMap<Pair<String, String>, Int>()
        fun key(a: String, b: String) = if (a < b) a to b else b to a
        session.votes.forEach { vote ->
            val pair = key(vote.contestantA, vote.contestantB)
            meetings[pair] = (meetings[pair] ?: 0) + 1
        }

        var best: Pair<String, String>? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (i in ids.indices) {
            for (j in i + 1 until ids.size) {
                val first = state.provisional(ids[i])
                val second = state.provisional(ids[j])
                val pair = key(ids[i], ids[j])
                var score = first.deviation + second.deviation -
                    0.35 * abs(first.rating - second.rating) -
                    90.0 * (meetings[pair] ?: 0) +
                    random.nextGaussian() * 8.0
                if (ids.size > 2 && last != null && pair == key(last.contestantA, last.contestantB)) {
                    score -= 10_000.0
                }
                if (score > bestScore) {
                    bestScore = score
                    best = ids[i] to ids[j]
                }
            }
        }
        val chosen = checkNotNull(best) { "A shootout needs at least two contestants" }
        return if (random.nextBoolean()) {
            ShootoutPairing(chosen.first, chosen.second)
        } else {
            ShootoutPairing(chosen.second, chosen.first)
        }
    }
}
