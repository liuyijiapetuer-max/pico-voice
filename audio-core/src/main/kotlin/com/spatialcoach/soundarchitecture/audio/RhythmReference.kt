package com.spatialcoach.soundarchitecture.audio

import kotlin.math.floor

/**
 * 显式节拍参考。必须同时给出 BPM 和节拍原点，才能填写「第几拍」。
 * 没有本对象时，beat 必须为 null，不得把起音间隔称作拍。
 */
data class RhythmReference(
    val bpm: Double,
    val originMs: Long,
) {
    init {
        require(bpm > 0.0) { "bpm must be positive" }
    }

    val beatDurationMs: Double get() = 60_000.0 / bpm

    /** 从原点起算的连续拍位置；原点处为 0.0。 */
    fun beatPosition(timeMs: Long): Double =
        (timeMs - originMs) / beatDurationMs

    /**
     * 1-based 拍号。落在原点之前时返回 null，不臆造。
     * 这是「起音落在第几拍的时间格子里」，与是否对准谱面音符无关。
     */
    fun beatIndex(timeMs: Long): Int? {
        val pos = beatPosition(timeMs)
        if (pos < -1e-6) return null
        return floor(pos + 1e-9).toInt() + 1
    }
}
