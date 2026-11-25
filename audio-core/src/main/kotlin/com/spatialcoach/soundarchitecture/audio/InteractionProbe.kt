package com.spatialcoach.soundarchitecture.audio

import kotlin.math.abs
import kotlin.math.roundToInt

/** 用户在舞台扇面、音高尺或叠合构件上点按/拖动时的查看结果。不是实时拾音。 */
data class InteractionProbe(
    val kind: Kind,
    val midi: Double? = null,
    val noteName: String? = null,
    val cents: Double? = null,
    val hz: Double? = null,
    val liveCentsDelta: Double? = null,
    val bandIndex: Int? = null,
    val bandHzLow: Double? = null,
    val bandHzHigh: Double? = null,
    val bandEnergy: Float? = null,
    val followX: Float? = null,
    val followY: Float? = null,
    val overlayId: String? = null,
    val overlayReadout: String? = null,
) {
    enum class Kind { RULER, FAN, OVERLAY }

    fun readout(mode: ViewMode): String =
        if (kind == Kind.OVERLAY) overlayReadout ?: "已选中声音轨道上的一个构件。"
        else if (mode == ViewMode.PROFESSIONAL) professionalText() else practiceText()

    fun professionalText(): String = when (kind) {
        Kind.FAN -> {
            val lo = bandHzLow?.toInt() ?: 0
            val hi = bandHzHigh?.toInt() ?: 0
            "查看第 ${(bandIndex ?: 0) + 1} 带  $lo–$hi Hz"
        }
        Kind.RULER -> buildString {
            val n = noteName ?: "—"
            val freq = hz
            val c = cents
            if (freq != null && c != null) {
                append("$n  ${"%.1f".format(freq)} Hz  ${fmtCents(c)} 音分")
            } else {
                append(n)
            }
            liveCentsDelta?.let { append("  ·  相对当前 ${fmtCents(it)} 音分") }
        }
        Kind.OVERLAY -> overlayReadout ?: "已选中声音轨道构件。"
    }

    fun practiceText(): String = when (kind) {
        Kind.FAN -> "点的是频谱扇面上第 ${(bandIndex ?: 0) + 1} 带，可以对照旁边的频谱窗。"
        Kind.RULER -> {
            val n = noteName ?: "目标音"
            val c = cents
            val near = when {
                c == null -> "对准 $n"
                c > 8 -> "对准尺上比 $n 偏高的位置"
                c < -8 -> "对准尺上比 $n 偏低的位置"
                else -> "对准尺上靠近 $n 的位置"
            }
            val align = liveCentsDelta?.let {
                when {
                    it > 8 -> "，比当前音高一些"
                    it < -8 -> "，比当前音低一些"
                    else -> "，已经接近当前音"
                }
            } ?: ""
            near + align + "。"
        }
        Kind.OVERLAY -> overlayReadout ?: "已选中声音轨道上的一个构件。数值只在选中后展开。"
    }

    companion object {
        fun fmtCents(cents: Double): String {
            val v = cents.roundToInt()
            return if (v > 0) "+$v" else "$v"
        }
    }
}

/**
 * 把 Spatial 命中转换成查看/对准结果。纯逻辑，可单测。
 * [pressed] 为真且当前帧有确定音高时，尺上标记进入跟手。
 * 叠合构件的选择不进入跟手，也不把拖动写成修音。
 */
object StageInteraction {
    fun onHit(
        entityName: String,
        x: Float,
        y: Float,
        pressed: Boolean,
        frame: AnalysisFrame,
        overlay: OverlayScene? = null,
    ): Pair<InteractionProbe, Boolean>? {
        if (StageMapping.isOverlayEntity(entityName)) {
            val member = overlay?.members?.firstOrNull { it.id == entityName }
            val selected = overlay?.let { it.copy(selectedId = entityName) }
            val readout = if (member == null || selected == null) {
                "未找到该叠合构件。"
            } else {
                SpatialOverlay.selectionReadout(selected, member) ?: "已选中构件，但没有可展开的数值。"
            }
            return InteractionProbe(
                kind = InteractionProbe.Kind.OVERLAY,
                overlayId = entityName,
                overlayReadout = readout,
                noteName = member?.target?.noteName ?: member?.event?.noteName,
                cents = member?.centsVsTarget,
            ) to false
        }
        if (StageMapping.isFanEntity(entityName)) {
            val idx = StageMapping.fanIndex(entityName) ?: return null
            val (lo, hi) = StageMapping.bandHzRange(idx)
            val energy = StageMapping.bandEnergy(frame.spectrum, frame.spectrumHzPerBin, idx)
            return InteractionProbe(
                kind = InteractionProbe.Kind.FAN,
                bandIndex = idx,
                bandHzLow = lo,
                bandHzHigh = hi,
                bandEnergy = energy,
            ) to false
        }
        if (StageMapping.isRulerEntity(entityName)) {
            val midi = StageMapping.continuousMidi(x, y)
            val hz = Tuning.hzFromMidi(midi)
            val nearest = Tuning.nearestMidi(hz)
            val cents = Tuning.centsFromNearest(hz)
            val liveDelta = frame.pitchHz?.let { (midi - Tuning.midiFromHz(it)) * 100.0 }
            val follow = pressed && frame.pitchHz != null
            return InteractionProbe(
                kind = InteractionProbe.Kind.RULER,
                midi = midi,
                noteName = Tuning.noteName(nearest),
                cents = cents,
                hz = hz,
                liveCentsDelta = liveDelta,
                followX = StageMapping.clampRulerX(x),
                followY = StageMapping.clampRulerY(y),
            ) to follow
        }
        return null
    }
}
