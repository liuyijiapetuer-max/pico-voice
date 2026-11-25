package com.spatialcoach.soundarchitecture.audio

import kotlin.math.pow

/**
 * 舞台扇面与音高尺的几何映射。纯计算，便于单测，不依赖 Spatial 运行时。
 * 与 SoundField 的摆放保持同一套常数：尺在前方 z=-1.25，竖直方向 48..84 MIDI，
 * 左右偏移 ±0.35 对应 ±50 音分（右偏高、左偏低）。
 * 声音轨道叠合的时间轴与默认宽度见 [SpatialOverlay]，不把演奏时值拉伸到参考长度。
 */
object StageMapping {
    const val BANDS = 36
    const val FAN_SPAN_DEG = 120f
    const val FLOOR_Y = 0f
    const val RULER_Z = -1.25f
    const val MIDI_LOW = 48.0
    const val MIDI_SPAN = 36.0
    const val Y_BASE = 0.55f
    const val Y_SPAN = 1.25f
    const val CENTS_FULL = 50.0
    const val CENTS_X_RANGE = 0.35f
    const val BAND_MIN_HZ = 80.0
    const val BAND_MAX_HZ = 8000.0

    fun yFromMidi(midi: Double): Float {
        val t = ((midi - MIDI_LOW) / MIDI_SPAN).toFloat()
        return FLOOR_Y + Y_BASE + t.coerceIn(0f, 1f) * Y_SPAN
    }

    fun midiFromY(y: Float): Double {
        val t = ((y - FLOOR_Y - Y_BASE) / Y_SPAN).coerceIn(0f, 1f)
        return MIDI_LOW + t * MIDI_SPAN
    }

    fun xFromCents(cents: Double): Float =
        ((cents / CENTS_FULL).toFloat() * CENTS_X_RANGE).coerceIn(-CENTS_X_RANGE, CENTS_X_RANGE)

    fun centsFromX(x: Float): Double =
        (x.coerceIn(-CENTS_X_RANGE, CENTS_X_RANGE) / CENTS_X_RANGE) * CENTS_FULL

    fun clampRulerX(x: Float): Float = x.coerceIn(-CENTS_X_RANGE, CENTS_X_RANGE)

    fun clampRulerY(y: Float): Float =
        y.coerceIn(FLOOR_Y + Y_BASE, FLOOR_Y + Y_BASE + Y_SPAN)

    /** 把尺上的点还原为连续 MIDI：竖直是音高，横向再叠加音分。 */
    fun continuousMidi(x: Float, y: Float): Double =
        midiFromY(y) + centsFromX(x) / 100.0

    fun startDeg(): Float = -FAN_SPAN_DEG / 2f

    fun stepDeg(): Float = FAN_SPAN_DEG / BANDS

    fun bandIndexFromYaw(yawDeg: Float): Int {
        val t = ((yawDeg - startDeg()) / stepDeg())
        return t.toInt().coerceIn(0, BANDS - 1)
    }

    fun bandHzRange(index: Int): Pair<Double, Double> {
        val i = index.coerceIn(0, BANDS - 1)
        val t0 = i / BANDS.toDouble()
        val t1 = (i + 1) / BANDS.toDouble()
        val ratio = BAND_MAX_HZ / BAND_MIN_HZ
        return BAND_MIN_HZ * ratio.pow(t0) to BAND_MIN_HZ * ratio.pow(t1)
    }

    fun bandEnergy(spectrum: FloatArray, hzPerBin: Double, index: Int): Float {
        if (spectrum.isEmpty() || hzPerBin <= 0.0) return 0f
        val (f0, f1) = bandHzRange(index)
        val i0 = (f0 / hzPerBin).toInt().coerceIn(0, spectrum.lastIndex)
        val i1 = (f1 / hzPerBin).toInt().coerceIn(i0 + 1, spectrum.size)
        var peak = 0f
        for (i in i0 until i1) peak = maxOf(peak, spectrum[i])
        return (peak * 8f).coerceIn(0f, 1f)
    }

    fun isRulerEntity(name: String): Boolean =
        name == "SaPitchRuler" ||
            name == "SaPitchRulerHit" ||
            name.startsWith("SaTick_") ||
            name == "SaCurrentPitch" ||
            name == "SaTargetPitch" ||
            name == "SaPitchError"

    fun isFanEntity(name: String): Boolean = name.startsWith("SaBand_")

    fun isOverlayEntity(name: String): Boolean = SpatialOverlay.isOverlayEntity(name)

    fun fanIndex(name: String): Int? =
        name.removePrefix("SaBand_").toIntOrNull()?.takeIf { it in 0 until BANDS }
}
