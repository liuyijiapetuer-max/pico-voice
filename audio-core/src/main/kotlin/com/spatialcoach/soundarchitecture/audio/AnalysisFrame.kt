package com.spatialcoach.soundarchitecture.audio

/**
 * 一帧分析结果。专业模式和练习模式共用这一份数据，只是呈现不同。
 *
 * @param pitchHz 单音基频；无法稳定估计时为 null
 * @param cents 相对最近音的音分偏差
 * @param spectrum 幅度谱（线性，未做 dB），长度通常为 fftSize/2
 * @param spectrumHzPerBin 每个 bin 的频率宽度
 * @param rms 均方根幅度，0..1 量级
 * @param peak 本窗峰值幅度
 * @param dbfs RMS 的 dBFS，20 * log10(rms)
 * @param onset 本帧是否检出起音
 * @param onsetIntervalMs 与上一拍的间隔；尚无两拍时为 null
 * @param rhythmStability 近几次起音间隔的规整程度，0..1
 */
data class AnalysisFrame(
    val timestampMs: Long,
    val sampleRate: Int,
    val pitchHz: Double?,
    val midi: Int?,
    val noteName: String?,
    val cents: Double?,
    val spectrum: FloatArray,
    val spectrumHzPerBin: Double,
    val rms: Double,
    val peak: Double,
    val dbfs: Double,
    val onset: Boolean,
    val onsetIntervalMs: Long?,
    val rhythmStability: Double?,
) {
    companion object {
        fun silent(timestampMs: Long = 0L, sampleRate: Int = 44_100, bins: Int = 1024): AnalysisFrame =
            AnalysisFrame(
                timestampMs = timestampMs,
                sampleRate = sampleRate,
                pitchHz = null,
                midi = null,
                noteName = null,
                cents = null,
                spectrum = FloatArray(bins),
                spectrumHzPerBin = sampleRate.toDouble() / (bins * 2),
                rms = 0.0,
                peak = 0.0,
                dbfs = -96.0,
                onset = false,
                onsetIntervalMs = null,
                rhythmStability = null,
            )
    }
}
