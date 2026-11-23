package com.spatialcoach.soundarchitecture.audio

import kotlin.math.PI
import kotlin.math.sin

/**
 * 实验室夹具：带已知频段干扰或短脉冲的合成文件，供 S1 预览自测。
 * 必须标明是合成文件分析，不得写成用户演奏或专业修音验收素材。
 */
object LabCorrectionFixtures {
    const val HUM_FILE_ID = "lab-correction-hum-v1"
    const val CLICK_FILE_ID = "lab-correction-click-v1"
    const val FILE_ID = HUM_FILE_ID
    const val SAMPLE_RATE = 44_100
    const val TONE_HZ = 440.0
    const val INTERFERER_HZ = 50.0
    val INTERFERER_BAND = FrequencyBand(40.0, 80.0)
    val CLICK_TIMES_MS: LongArray = longArrayOf(200L, 500L, 800L)

    fun humUnderTone(
        durationMs: Long = 1000L,
        toneAmp: Double = 0.25,
        interfererAmp: Double = 0.20,
    ): PcmClip {
        val n = ((SAMPLE_RATE * durationMs) / 1000L).toInt().coerceAtLeast(1)
        val samples = FloatArray(n) { i ->
            val t = i / SAMPLE_RATE.toDouble()
            val tone = toneAmp * sin(2.0 * PI * TONE_HZ * t)
            val interferer = interfererAmp * sin(2.0 * PI * INTERFERER_HZ * t)
            (tone + interferer).toFloat()
        }
        return PcmClip(
            samples = samples,
            sampleRate = SAMPLE_RATE,
            label = CorrectionText.DRY_LABEL,
            sourceName = "实验室夹具 lab-correction-hum-v1（A4 正弦 + 50 Hz 干扰）。${CorrectionText.SOURCE_NOTE}",
        )
    }

    fun clicksOnTone(
        durationMs: Long = 1000L,
        toneAmp: Double = 0.25,
        clickPeak: Float = 0.95f,
        clickFollow: Float = -0.7f,
    ): PcmClip {
        val n = ((SAMPLE_RATE * durationMs) / 1000L).toInt().coerceAtLeast(1)
        val samples = FloatArray(n) { i ->
            val t = i / SAMPLE_RATE.toDouble()
            (toneAmp * sin(2.0 * PI * TONE_HZ * t)).toFloat()
        }
        for (ms in CLICK_TIMES_MS) {
            val i = CorrectionPreview.indexOf(SAMPLE_RATE, ms).coerceIn(0, n - 1)
            samples[i] = clickPeak
            if (i + 1 < n) samples[i + 1] = clickFollow
        }
        return PcmClip(
            samples = samples,
            sampleRate = SAMPLE_RATE,
            label = CorrectionText.DRY_LABEL,
            sourceName = "实验室夹具 lab-correction-click-v1（A4 正弦 + 已知短脉冲）。${CorrectionText.SOURCE_NOTE}",
        )
    }

    fun cleanTone(durationMs: Long = 1000L, toneAmp: Double = 0.25): PcmClip {
        val n = ((SAMPLE_RATE * durationMs) / 1000L).toInt().coerceAtLeast(1)
        val samples = FloatArray(n) { i ->
            val t = i / SAMPLE_RATE.toDouble()
            (toneAmp * sin(2.0 * PI * TONE_HZ * t)).toFloat()
        }
        return PcmClip(
            samples = samples,
            sampleRate = SAMPLE_RATE,
            label = CorrectionText.DRY_LABEL,
            sourceName = "实验室夹具（干净 A4 正弦，无加入干扰或短脉冲）。${CorrectionText.SOURCE_NOTE}",
        )
    }
}
