package com.spatialcoach.soundarchitecture.audio

import kotlin.math.abs
import kotlin.math.roundToInt

/** 练习复验用的可读行。不声称 DSP 修音。 */
object ReviewText {
    const val SOURCE_DISCLAIMER = "文件回放（合成音轨）。不是真麦采集，也不是自动修音。"
    const val LIMITS =
        "只支持单旋律。慢滑音会标成不够稳定，不伪装成确定音列。不做复调，也不自动跟谱。"

    fun eventLine(event: NoteEvent): String {
        val time = formatTime(event.startMs)
        val name = event.noteName ?: "—"
        val beat = event.beatIndex?.let { "第${it}拍" } ?: "无节拍参考"
        val cents = event.centsVsTarget?.let { "目标${signedCents(it)}" }
            ?: event.centsVsNearest?.let { "最近律音${signedCents(it)}" }
            ?: "无确定音高"
        val timing = event.timingErrorMs?.let { "时差${signedMs(it)}" } ?: "无时差参考"
        val flags = buildList {
            if (event.glideSuspected) add("疑似滑音")
            if (!event.stable) add("不够稳定")
        }.joinToString(" ")
        return listOf(time, name, beat, cents, timing, flags).filter { it.isNotBlank() }.joinToString("  ")
    }

    fun alignmentLine(item: AlignmentItem): String {
        val kind = when (item.kind) {
            AlignmentKind.MATCH -> "对准"
            AlignmentKind.WRONG_NOTE -> "错音"
            AlignmentKind.MISSING -> "缺音"
            AlignmentKind.EXTRA -> "多音"
            AlignmentKind.UNALIGNED -> "未对齐"
        }
        val beat = item.refBeatIndex?.let { "第${it}拍" } ?: "无节拍参考"
        val target = item.target?.noteName?.let { "目标$it" } ?: "无目标音"
        val got = item.event?.noteName?.let { "实际$it" } ?: "未奏出"
        val time = item.event?.startMs?.let { formatTime(it) } ?: item.target?.startMs?.let { "参考${formatTime(it)}" } ?: "—"
        val cents = item.centsVsTarget?.let { signedCents(it) } ?: "—"
        val timing = item.timingErrorMs?.let { signedMs(it) } ?: "—"
        return "$kind  $time  $beat  $target  $got  音分$cents  时差$timing"
    }

    fun subscoreLine(label: String, score: Subscore?): String =
        if (score == null) "$label  无（没有可计入的对齐样本）"
        else "$label  平均偏差 ${score.meanAbs.roundToInt()} ${score.unit}（n=${score.count}）"

    fun formatTime(ms: Long): String = "%d.%02ds".format(ms / 1000, (abs(ms) % 1000) / 10)

    fun signedCents(cents: Double): String {
        val v = cents.roundToInt()
        return if (v > 0) "+${v}音分" else "${v}音分"
    }

    fun signedMs(ms: Long): String = if (ms > 0) "+${ms}ms" else "${ms}ms"
}
