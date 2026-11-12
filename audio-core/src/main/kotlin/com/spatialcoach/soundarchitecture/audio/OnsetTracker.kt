package com.spatialcoach.soundarchitecture.audio

import kotlin.math.abs
import kotlin.math.max

/** 基于频谱通量的起音检测，并给出简单的间隔规整度。 */
class OnsetTracker(
    private val fluxThreshold: Float = 0.08f,
    private val minIntervalMs: Long = 80L,
    private val historySize: Int = 8,
) {
    private var previous: FloatArray? = null
    private var lastOnsetMs: Long = -1L
    private val intervals = ArrayList<Long>()

    data class Result(
        val onset: Boolean,
        val intervalMs: Long?,
        val stability: Double?,
    )

    fun reset() {
        previous = null
        lastOnsetMs = -1L
        intervals.clear()
    }

    fun update(spectrum: FloatArray, timestampMs: Long): Result {
        val prev = previous
        previous = spectrum.copyOf()
        if (prev == null || prev.size != spectrum.size) {
            return Result(false, lastInterval(), stability())
        }
        var flux = 0f
        for (i in spectrum.indices) {
            val delta = spectrum[i] - prev[i]
            if (delta > 0f) flux += delta
        }
        val enoughGap = lastOnsetMs < 0L || timestampMs - lastOnsetMs >= minIntervalMs
        val onset = flux >= fluxThreshold && enoughGap
        if (onset) {
            if (lastOnsetMs >= 0L) {
                intervals.add(timestampMs - lastOnsetMs)
                if (intervals.size > historySize) intervals.removeAt(0)
            }
            lastOnsetMs = timestampMs
        }
        return Result(onset, lastInterval(), stability())
    }

    private fun lastInterval(): Long? = intervals.lastOrNull()

    private fun stability(): Double? {
        if (intervals.size < 3) return null
        val mean = intervals.average()
        if (mean <= 0.0) return null
        val variance = intervals.map { val d = it - mean; d * d }.average()
        val cv = kotlin.math.sqrt(variance) / mean
        return (1.0 - cv).coerceIn(0.0, 1.0)
    }
}
