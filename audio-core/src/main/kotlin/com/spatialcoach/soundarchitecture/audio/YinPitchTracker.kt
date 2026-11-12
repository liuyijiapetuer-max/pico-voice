package com.spatialcoach.soundarchitecture.audio

/**
 * 单音基频估计（YIN）。只承诺单音；复调不在 v1 范围。
 */
class YinPitchTracker(
    private val sampleRate: Int,
    private val minHz: Float = 80f,
    private val maxHz: Float = 1200f,
    private val threshold: Float = 0.15f,
) {
    data class Estimate(val frequencyHz: Double, val periodicity: Double)

    fun estimate(samples: FloatArray): Estimate? {
        val n = samples.size
        val tauMin = maxOf(2, (sampleRate / maxHz).toInt())
        val tauMax = minOf(n / 2 - 2, (sampleRate / minHz).toInt())
        if (tauMax <= tauMin + 2) return null

        val difference = FloatArray(tauMax + 1)
        for (tau in tauMin..tauMax) {
            var sum = 0f
            val limit = n - tau
            var j = 0
            while (j < limit) {
                val d = samples[j] - samples[j + tau]
                sum += d * d
                j++
            }
            difference[tau] = sum
        }

        val cmnd = FloatArray(tauMax + 1)
        cmnd[0] = 1f
        var running = 0f
        for (tau in 1..tauMax) {
            running += difference[tau]
            cmnd[tau] = if (running > 0f) difference[tau] * tau / running else 1f
        }

        var tau = tauMin
        while (tau < tauMax && cmnd[tau] >= threshold) tau++
        if (tau >= tauMax) {
            var best = tauMin
            for (t in tauMin..tauMax) {
                if (cmnd[t] < cmnd[best]) best = t
            }
            if (cmnd[best] > 0.45f) return null
            tau = best
        }
        while (tau + 1 <= tauMax && cmnd[tau + 1] < cmnd[tau]) tau++
        val refined = parabolicTau(cmnd, tau)
        if (refined <= 0.0) return null
        val freq = sampleRate / refined
        if (freq < minHz || freq > maxHz) return null
        return Estimate(freq, 1.0 - cmnd[tau].toDouble().coerceIn(0.0, 1.0))
    }

    private fun parabolicTau(cmnd: FloatArray, tau: Int): Double {
        if (tau <= 0 || tau >= cmnd.lastIndex) return tau.toDouble()
        val s0 = cmnd[tau - 1]
        val s1 = cmnd[tau]
        val s2 = cmnd[tau + 1]
        // Vertex of the parabola through (tau-1,s0),(tau,s1),(tau+1,s2).
        val denom = s0 - 2.0 * s1 + s2
        if (denom == 0.0) return tau.toDouble()
        return tau + (s0 - s2) / (2.0 * denom)
    }
}
