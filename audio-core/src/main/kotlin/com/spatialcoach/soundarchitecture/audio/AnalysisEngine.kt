package com.spatialcoach.soundarchitecture.audio

/**
 * 可单测的实时分析内核。输入是单声道 float PCM（约 -1..1），输出 [AnalysisFrame]。
 * 专业模式和练习模式都读同一帧。
 */
class AnalysisEngine(
    val sampleRate: Int = 44_100,
    val fftSize: Int = 2048,
    val hopSize: Int = 512,
) {
    init {
        require(Fft.isPowerOfTwo(fftSize))
        require(hopSize > 0 && hopSize <= fftSize)
    }

    private val window = Fft.hann(fftSize)
    private val ring = FloatArray(fftSize)
    private var writeIndex = 0
    private var filled = 0
    private var totalSamples = 0L
    private val pitchTracker = YinPitchTracker(sampleRate)
    private val onsetTracker = OnsetTracker()

    fun reset() {
        ring.fill(0f)
        writeIndex = 0
        filled = 0
        totalSamples = 0L
        onsetTracker.reset()
    }

    /**
     * 追加一批样本。每当累计满一个 hop，就产出一帧；一批里可能有 0 到多帧。
     */
    fun push(samples: FloatArray, offset: Int = 0, length: Int = samples.size - offset): List<AnalysisFrame> {
        val frames = ArrayList<AnalysisFrame>()
        var i = offset
        val end = offset + length
        var sinceHop = filled % hopSize
        if (filled == 0) sinceHop = 0
        while (i < end) {
            ring[writeIndex] = samples[i]
            writeIndex = (writeIndex + 1) % fftSize
            filled = (filled + 1).coerceAtMost(fftSize)
            totalSamples++
            sinceHop++
            i++
            if (filled >= fftSize && sinceHop >= hopSize) {
                sinceHop = 0
                frames += analyzeWindow()
            }
        }
        return frames
    }

    /** 对一整窗已经对齐的样本直接分析（单测方便）。不经过环形缓冲。 */
    fun analyze(samples: FloatArray, timestampMs: Long = 0L): AnalysisFrame {
        require(samples.size == fftSize)
        return analyzeArray(samples, timestampMs)
    }

    private fun analyzeWindow(): AnalysisFrame {
        val snapshot = FloatArray(fftSize)
        val start = writeIndex
        for (i in 0 until fftSize) {
            snapshot[i] = ring[(start + i) % fftSize]
        }
        val timestampMs = ((totalSamples - fftSize) * 1000L) / sampleRate
        return analyzeArray(snapshot, timestampMs)
    }

    private fun analyzeArray(samples: FloatArray, timestampMs: Long): AnalysisFrame {
        val dynamics = DynamicsTracker.measure(samples)
        val spectrum = Fft.magnitudeSpectrum(samples, window)
        val hzPerBin = sampleRate.toDouble() / fftSize
        val voiced = dynamics.rms > 0.01
        val pitch = if (voiced) pitchTracker.estimate(samples) else null
        val hz = pitch?.frequencyHz
        val midi = hz?.let { Tuning.nearestMidi(it) }
        val name = midi?.let { Tuning.noteName(it) }
        val cents = hz?.let { Tuning.centsFromNearest(it) }
        val onset = onsetTracker.update(spectrum, timestampMs)
        return AnalysisFrame(
            timestampMs = timestampMs,
            sampleRate = sampleRate,
            pitchHz = hz,
            midi = midi,
            noteName = name,
            cents = cents,
            spectrum = spectrum,
            spectrumHzPerBin = hzPerBin,
            rms = dynamics.rms,
            peak = dynamics.peak,
            dbfs = dynamics.dbfs,
            onset = onset.onset,
            onsetIntervalMs = onset.intervalMs,
            rhythmStability = onset.stability,
        )
    }
}
