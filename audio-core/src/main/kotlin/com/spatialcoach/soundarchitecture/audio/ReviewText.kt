package com.spatialcoach.soundarchitecture.audio

import kotlin.math.abs
import kotlin.math.roundToInt

/** 练习复验用的可读行。不声称 DSP 修音。 */
object ReviewText {
    const val SOURCE_DISCLAIMER = "用户导入的是录音文件分析，不是实时采集，也不是自动修音。"
    const val DEMO_DISCLAIMER = "内置合成示范只用于展示分析路径，不是用户演奏成果。"
    const val LIMITS =
        "只支持单旋律，且只对照固定练习谱 practice-melody-v1（四拍 A4–C5–G4–E4）。慢滑音会标成不够稳定。不做复调，也不自动跟谱。"
    const val REPEAT_LIMIT =
        "相邻同音若缺少可观察的起音或足够无声分隔，或出现短间隔合并、滑音、不够稳定，缺音/多音会标为不可判定，界面不显示确定数字。"
    const val INDETERMINATE_COUNTS_ADVICE =
        "分段不足以给出确定的缺音或多音，也不能据此推断错漏或下改善结论，不发布缺/多音数字。"
    const val RERECORD_CONDITION =
        "如需再比，请在相邻同音之间留出可观察的起音或至少 50 ms 无声分隔后再录；短间隔合并、连续同音重新起奏、滑音或发音不够稳定时，仍无法判定缺/多音。"
    const val WINDOW_BIAS_NOTE =
        "分析窗可能把参考后三音标早约 36–38 ms，参考自身节奏平均偏差约 28 ms。判断赶拍或拖拍请看「相对同处理参考」的差值。相对谱面的原始时差会保留，不会偷偷减掉整段统一延迟。"
    const val NEAREST_VS_TARGET =
        "最近律音只说明当前音准靠近哪个十二平均律音；谱面目标才是 practice-melody-v1 的既定音。二者不同时不得把最近律音当成谱面目标。"

    fun eventLine(event: NoteEvent): String {
        val time = formatTime(event.startMs)
        val name = event.noteName ?: "—"
        val beat = event.beatIndex?.let { "第${it}拍" } ?: "无节拍参考"
        val nearest = event.centsVsNearest?.let { "最近律音${signedCents(it)}" } ?: "无确定最近律音"
        val target = event.centsVsTarget?.let { "谱面目标${signedCents(it)}" } ?: "无谱面目标音分"
        val timing = event.timingErrorMs?.let { "相对谱面时差${signedMs(it)}" } ?: "无谱面时差"
        val flags = buildList {
            if (event.glideSuspected) add("疑似滑音")
            if (!event.stable) add("不够稳定")
        }.joinToString(" ")
        return listOf(time, name, beat, nearest, target, timing, flags).filter { it.isNotBlank() }.joinToString("  ")
    }

    fun alignmentLine(item: AlignmentItem): String {
        val kind = when (item.kind) {
            AlignmentKind.MATCH -> "对准谱面"
            AlignmentKind.WRONG_NOTE -> "错音"
            AlignmentKind.MISSING -> "缺音"
            AlignmentKind.EXTRA -> "多音"
            AlignmentKind.UNALIGNED -> "未对齐"
        }
        val beat = item.refBeatIndex?.let { "第${it}拍" } ?: "无节拍参考"
        val target = item.target?.noteName?.let { "谱面目标$it" } ?: "无谱面目标"
        val got = item.event?.noteName?.let { "最近律音$it" } ?: "未奏出"
        val time = item.event?.startMs?.let { formatTime(it) } ?: item.target?.startMs?.let { "参考${formatTime(it)}" } ?: "—"
        val cents = item.centsVsTarget?.let { signedCents(it) } ?: "—"
        val raw = item.timingErrorMs?.let { signedMs(it) } ?: "—"
        val rel = item.timingVsProcessedReferenceMs?.let { signedMs(it) } ?: "—"
        return "$kind  $time  $beat  $target  $got  相对谱面目标$cents  相对谱面时差$raw  相对同处理参考$rel"
    }

    fun subscoreLine(label: String, score: Subscore?): String =
        if (score == null) "$label  无（没有可计入的对齐样本）"
        else "$label  平均偏差 ${score.meanAbs.roundToInt()} ${score.unit}（n=${score.count}）"

    fun coverageLine(label: String, coverage: AlignmentCoverage): String = coverageDetail(label, coverage)

    fun coverageDetail(label: String, coverage: AlignmentCoverage): String {
        val head = "$label 覆盖率 ${coverage.rateText()}  对准${coverage.correctSlots} 错音${coverage.wrongNotes}"
        return if (coverage.countsDeterminate) {
            "$head 缺音${coverage.missing} 多音${coverage.extra}"
        } else {
            val why = coverage.countabilityReason?.let { " $it" } ?: ""
            "$head 缺音/多音不可判定，不发布确定计数。$why"
        }
    }

    fun takeMetaLine(take: PerformanceTake): String {
        val name = take.sourceName ?: take.takeId
        val window = if (take.timeWindowStartMs != null && take.timeWindowEndMs != null)
            "时间窗口 ${formatTime(take.timeWindowStartMs)}–${formatTime(take.timeWindowEndMs)}（未裁开头静音）"
        else "时间窗口未声明"
        val duration = take.audioDurationMs?.let { "文件时长 ${formatTime(it)}" } ?: "时长未知"
        val confirmed = if (take.referenceConfirmed) "已确认参考 ${take.fragmentId}" else "未确认参考"
        return "$name  ·  ${take.source.label}  ·  $confirmed  ·  $duration  ·  $window"
    }

    fun formatTime(ms: Long): String = "%d.%02ds".format(ms / 1000, (abs(ms) % 1000) / 10)

    fun signedCents(cents: Double): String {
        val v = cents.roundToInt()
        return if (v > 0) "+${v}音分" else "${v}音分"
    }

    fun signedMs(ms: Long): String = if (ms > 0) "+${ms}ms" else "${ms}ms"
}
