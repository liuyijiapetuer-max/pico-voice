package com.spatialcoach.soundarchitecture.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 基 2 复数 FFT，原地运算。长度为 2 的幂。 */
object Fft {
    fun isPowerOfTwo(n: Int): Boolean = n > 0 && (n and (n - 1)) == 0

    fun hann(n: Int): FloatArray = FloatArray(n) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))).toFloat()
    }

    /**
     * @return 长度 n/2 的幅度谱（含直流到奈奎斯特之下）。
     */
    fun magnitudeSpectrum(samples: FloatArray, window: FloatArray): FloatArray {
        val n = samples.size
        require(isPowerOfTwo(n)) { "FFT size must be a power of two, got $n" }
        require(window.size == n)
        val real = FloatArray(n) { i -> samples[i] * window[i] }
        val imag = FloatArray(n)
        transform(real, imag)
        val half = n / 2
        val out = FloatArray(half)
        val norm = 2f / n
        for (k in 0 until half) {
            val re = real[k]
            val im = imag[k]
            out[k] = sqrt(re * re + im * im) * norm
        }
        out[0] = out[0] / 2f
        return out
    }

    fun transform(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val half = len / 2
            val step = 2.0 * PI / len
            var i = 0
            while (i < n) {
                var k = 0
                while (k < half) {
                    val angle = step * k
                    val wr = cos(angle).toFloat()
                    val wi = (-sin(angle)).toFloat()
                    val even = i + k
                    val odd = even + half
                    val tr = wr * real[odd] - wi * imag[odd]
                    val ti = wr * imag[odd] + wi * real[odd]
                    real[odd] = real[even] - tr
                    imag[odd] = imag[even] - ti
                    real[even] += tr
                    imag[even] += ti
                    k++
                }
                i += len
            }
            len = len shl 1
        }
    }

    fun peakFrequency(magnitudes: FloatArray, sampleRate: Int): Double {
        if (magnitudes.size < 3) return 0.0
        var best = 1
        for (i in 1 until magnitudes.size) {
            if (magnitudes[i] > magnitudes[best]) best = i
        }
        val hzPerBin = sampleRate.toDouble() / (magnitudes.size * 2)
        return best * hzPerBin
    }
}
