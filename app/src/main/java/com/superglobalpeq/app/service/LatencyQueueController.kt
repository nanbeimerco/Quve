package com.superglobalpeq.app.service

import kotlin.math.abs

/**
 * Tracks AudioTrack queue depth and smoothly sheds excess backlog in Low-Latency mode
 * without introducing DC/step clicks or breaking steady-state 1024-frame REMOTE_SUBMIX bursts.
 *
 * Notice how the audio loop MUST still run the DSP biquad filter on every read block before
 * passing samples to this controller so IIR filter state stays continuous across dropped blocks.
 */
internal class LatencyQueueController(
    private val silencePeakThreshold: Int = DEFAULT_SILENCE_PEAK_THRESHOLD,
    private val highWaterFrames: Long = DEFAULT_HIGH_WATER_FRAMES,
    private val dropStopFrames: Long = DEFAULT_DROP_STOP_FRAMES,
    private val maxFloorFrames: Long = DEFAULT_MAX_FLOOR_FRAMES,
    private val targetFloorFrames: Long = DEFAULT_TARGET_FLOOR_FRAMES,
    private val floorWindowBlocks: Int = DEFAULT_FLOOR_WINDOW_BLOCKS,
    private val fadeFrames: Int = DEFAULT_FADE_FRAMES
) {
    enum class Decision {
        /** Write buffer untouched. */
        WRITE,
        /** Crossfade down to 0 over [fadeFrames] at the tail of this block, then write. */
        WRITE_FADE_OUT,
        /** Silent excess block dropped directly with no fade needed. */
        DROP_SILENCE,
        /** Audible excess block dropped after a prior [WRITE_FADE_OUT]. */
        DROP_AUDIO,
        /** Crossfade up from 0 over [fadeFrames] at the head of this block, then write. */
        WRITE_FADE_IN
    }

    private enum class Phase {
        STEADY,
        DROPPING_AUDIO,
        NEED_FADE_IN
    }

    private var phase: Phase = Phase.STEADY
    private var lastRawHeadU32: Long = 0L
    private var hasSeenHead: Boolean = false
    private var headWrapOffset: Long = 0L

    private var windowCount: Int = 0
    private var windowMinQueue: Long = Long.MAX_VALUE
    private var trimRemainingFrames: Long = 0L

    fun reset() {
        phase = Phase.STEADY
        lastRawHeadU32 = 0L
        hasSeenHead = false
        headWrapOffset = 0L
        resetFloorWindow()
        trimRemainingFrames = 0L
    }

    private fun resetFloorWindow() {
        windowCount = 0
        windowMinQueue = Long.MAX_VALUE
    }

    /**
     * Converts [AudioTrack.getPlaybackHeadPosition] (unsigned 32-bit wrapped int) into a monotonic
     * 64-bit played-frame count and returns the frames currently queued inside AudioTrack.
     */
    fun computeQueuedFrames(framesWritten: Long, playbackHeadPosition: Int): Long {
        val rawU32 = playbackHeadPosition.toLong() and 0xFFFF_FFFFL
        if (hasSeenHead && rawU32 < lastRawHeadU32 && (lastRawHeadU32 - rawU32) > 0x8000_0000L) {
            headWrapOffset += 0x1_0000_0000L
        }
        hasSeenHead = true
        lastRawHeadU32 = rawU32
        val totalPlayed = headWrapOffset + rawU32
        return (framesWritten - totalPlayed).coerceAtLeast(0L)
    }

    /**
     * Evaluates the next [blockFrames] interleaved stereo PCM16 frames and optionally applies a
     * short linear fade-in or fade-out directly on [pcm16].
     *
     * @return the [Decision] indicating whether the caller should write or drop [pcm16].
     */
    fun evaluateAndShape(
        pcm16: ShortArray,
        sampleCount: Int,
        queuedFrames: Long,
        lowLatencyActive: Boolean
    ): Decision {
        if (!lowLatencyActive) {
            phase = Phase.STEADY
            resetFloorWindow()
            trimRemainingFrames = 0L
            return Decision.WRITE
        }

        val blockFrames = sampleCount / 2
        if (blockFrames <= 0) return Decision.WRITE

        // Update sliding window minimum to catch constant floor latency hiding beneath bursts.
        if (phase == Phase.STEADY && trimRemainingFrames <= 0L) {
            if (queuedFrames < windowMinQueue) {
                windowMinQueue = queuedFrames
            }
            windowCount++
            if (windowCount >= floorWindowBlocks) {
                if (windowMinQueue > maxFloorFrames) {
                    trimRemainingFrames = (windowMinQueue - targetFloorFrames).coerceAtLeast(0L)
                }
                resetFloorWindow()
            }
        }

        val silent = isSilent(pcm16, sampleCount)

        if (phase == Phase.DROPPING_AUDIO) {
            val doneHighWater = queuedFrames <= dropStopFrames
            val doneTrim = trimRemainingFrames <= 0L
            if (doneHighWater && doneTrim) {
                phase = Phase.NEED_FADE_IN
                // Fall through so the current block becomes the fade-in resumption block.
            } else {
                if (trimRemainingFrames > 0L) {
                    trimRemainingFrames = (trimRemainingFrames - blockFrames).coerceAtLeast(0L)
                }
                if (silent) {
                    // Still stay in NEED_FADE_IN after silent drops? No, prior block was faded out
                    // cleanly to zero; silence is already zero so resume cleanly.
                    return Decision.DROP_AUDIO
                }
                return Decision.DROP_AUDIO
            }
        }

        if (phase == Phase.NEED_FADE_IN) {
            phase = Phase.STEADY
            resetFloorWindow()
            applyFadeIn(pcm16, sampleCount)
            return Decision.WRITE_FADE_IN
        }

        val needsSpikeTrim = queuedFrames > highWaterFrames
        val needsFloorTrim = trimRemainingFrames > 0L

        if (needsSpikeTrim || needsFloorTrim) {
            if (silent) {
                if (trimRemainingFrames > 0L) {
                    trimRemainingFrames = (trimRemainingFrames - blockFrames).coerceAtLeast(0L)
                }
                resetFloorWindow()
                return Decision.DROP_SILENCE
            } else {
                // Fade out smoothly on this block so subsequent dropped blocks do not cause a step click.
                applyFadeOut(pcm16, sampleCount)
                phase = Phase.DROPPING_AUDIO
                resetFloorWindow()
                return Decision.WRITE_FADE_OUT
            }
        }

        return Decision.WRITE
    }

    private fun isSilent(pcm16: ShortArray, sampleCount: Int): Boolean {
        val threshold = silencePeakThreshold
        for (i in 0 until sampleCount) {
            val v = pcm16[i].toInt()
            if (v == Int.MIN_VALUE || abs(v) > threshold) {
                return false
            }
        }
        return true
    }

    private fun applyFadeOut(pcm16: ShortArray, sampleCount: Int) {
        val totalFrames = sampleCount / 2
        if (totalFrames <= 0) return
        val fade = fadeFrames.coerceIn(1, totalFrames)
        val startFrame = totalFrames - fade
        for (f in startFrame until totalFrames) {
            val pos = f - startFrame
            val gain = (fade - 1 - pos).toFloat() / fade.toFloat()
            val idx = f * 2
            pcm16[idx] = (pcm16[idx] * gain).toInt().coerceIn(-32768, 32767).toShort()
            pcm16[idx + 1] = (pcm16[idx + 1] * gain).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun applyFadeIn(pcm16: ShortArray, sampleCount: Int) {
        val totalFrames = sampleCount / 2
        if (totalFrames <= 0) return
        val fade = fadeFrames.coerceIn(1, totalFrames)
        for (f in 0 until fade) {
            val gain = f.toFloat() / fade.toFloat()
            val idx = f * 2
            pcm16[idx] = (pcm16[idx] * gain).toInt().coerceIn(-32768, 32767).toShort()
            pcm16[idx + 1] = (pcm16[idx + 1] * gain).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    companion object {
        const val DEFAULT_SILENCE_PEAK_THRESHOLD = 24
        // REMOTE_SUBMIX bursts 1024 frames (~21.3 ms); keep headroom above targetFloor + 1024 (~1264),
        // well below AudioTrack bufferSizeInFrames (2304) so AudioTrack.write never backpressures REMOTE_SUBMIX.
        const val DEFAULT_HIGH_WATER_FRAMES = 1440L
        const val DEFAULT_DROP_STOP_FRAMES = 1056L
        // If trough across ~64 ms stays above 8 ms, shave constant floor down toward 5 ms.
        const val DEFAULT_MAX_FLOOR_FRAMES = 384L
        const val DEFAULT_TARGET_FLOOR_FRAMES = 240L
        const val DEFAULT_FLOOR_WINDOW_BLOCKS = 16
        const val DEFAULT_FADE_FRAMES = 96 // 2 ms @ 48 kHz
    }
}
