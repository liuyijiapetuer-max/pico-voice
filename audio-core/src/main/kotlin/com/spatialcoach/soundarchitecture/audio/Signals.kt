package com.spatialcoach.soundarchitecture.audio

import kotlin.math.PI
import kotlin.math.sin

object Signals {
    fun sine(freqHz: Double, sampleRate: Int, samples: Int, amplitude: Double = 0.5): FloatArray {
        val twoPi = 2.0 * PI * freqHz / sampleRate
        return FloatArray(samples) { i -> (amplitude * sin(twoPi * i)).toFloat() }
    }

    fun silence(samples: Int): FloatArray = FloatArray(samples)

    fun impulses(sampleRate: Int, count: Int, intervalMs: Long, amplitude: Float = 0.9f): FloatArray {
        val interval = (sampleRate * intervalMs / 1000L).toInt().coerceAtLeast(1)
        val width = (sampleRate * 0.008).toInt().coerceAtLeast(4)
        val total = interval * count
        val out = FloatArray(total)
        for (n in 0 until count) {
            val start = n * interval
            for (k in 0 until width) {
                if (start + k < out.size) {
                    val env = 1f - k / width.toFloat()
                    out[start + k] = amplitude * env
                }
            }
        }
        return out
    }

    /**
     * 内置演示音轨：单音、力度变化、滑音和一串起音，全部是合成正弦，不含商业歌曲。
     */
    fun demoMelody(sampleRate: Int = 44_100): FloatArray {
        val parts = ArrayList<FloatArray>()
        fun tone(hz: Double, seconds: Double, amp: Double) =
            sine(hz, sampleRate, (sampleRate * seconds).toInt(), amp)
        fun rest(seconds: Double) = silence((sampleRate * seconds).toInt())
        parts += tone(Tuning.A4_HZ, 1.4, 0.45)
        parts += rest(0.25)
        parts += tone(Tuning.hzFromMidi(72.0), 1.0, 0.38) // C5
        parts += tone(Tuning.hzFromMidi(67.0), 0.9, 0.50) // G4 稍强
        parts += tone(Tuning.hzFromMidi(64.0), 0.8, 0.18) // E4 偏弱
        parts += rest(0.3)
        repeat(4) {
            parts += tone(Tuning.A4_HZ, 0.16, 0.55)
            parts += rest(0.10)
        }
        // A4 -> A#4 的慢滑，方便看出音准偏移
        val glideSamples = (sampleRate * 1.6).toInt()
        val start = Tuning.A4_HZ
        val end = Tuning.hzFromMidi(70.0)
        parts += FloatArray(glideSamples) { i ->
            val t = i / glideSamples.toDouble()
            val hz = start + (end - start) * t
            (0.4 * sin(2.0 * PI * hz * i / sampleRate)).toFloat()
        }
        parts += rest(0.4)
        val total = parts.sumOf { it.size }
        val out = FloatArray(total)
        var o = 0
        for (p in parts) {
            p.copyInto(out, o)
            o += p.size
        }
        return out
    }
}
