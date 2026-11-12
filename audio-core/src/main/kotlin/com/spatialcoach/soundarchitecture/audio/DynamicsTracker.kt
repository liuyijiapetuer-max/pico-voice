package com.spatialcoach.soundarchitecture.audio

import kotlin.math.log10
import kotlin.math.sqrt

object DynamicsTracker {
    const val SILENCE_DBFS = -96.0

    fun measure(samples: FloatArray): Dynamics {
        var sumSq = 0.0
        var peak = 0.0
        for (s in samples) {
            val a = s.toDouble()
            sumSq += a * a
            val abs = kotlin.math.abs(a)
            if (abs > peak) peak = abs
        }
        val rms = if (samples.isEmpty()) 0.0 else sqrt(sumSq / samples.size)
        val dbfs = if (rms <= 1e-8) SILENCE_DBFS else (20.0 * log10(rms)).coerceAtLeast(SILENCE_DBFS)
        return Dynamics(rms = rms, peak = peak, dbfs = dbfs)
    }
}

data class Dynamics(val rms: Double, val peak: Double, val dbfs: Double)
