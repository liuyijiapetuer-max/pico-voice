package com.spatialcoach.soundarchitecture.audio

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

enum class AlignmentKind {
    MATCH,
    WRONG_NOTE,
    MISSING,
    EXTRA,
    UNALIGNED,
}

data class AlignmentItem(
    val kind: AlignmentKind,
    val referenceIndex: Int? = null,
    val performanceIndex: Int? = null,
    val target: TargetNote? = null,
    val event: NoteEvent? = null,
    val centsVsTarget: Double? = null,
    val timingErrorMs: Long? = null,
    val refBeatIndex: Int? = null,
) {
    val annotatedEvent: NoteEvent?
        get() {
            val ev = event ?: return null
            return ev.copy(
                centsVsTarget = centsVsTarget,
                targetMidi = target?.midi,
                targetNoteName = target?.noteName,
                beatIndex = refBeatIndex ?: ev.beatIndex,
                timingErrorMs = timingErrorMs,
                expectedStartMs = target?.startMs,
            )
        }
}

data class Subscore(
    val meanAbs: Double,
    val count: Int,
    val unit: String,
)

data class TakeAlignment(
    val take: PerformanceTake,
    val items: List<AlignmentItem>,
    val pitch: Subscore?,
    val rhythm: Subscore?,
    val counts: Map<AlignmentKind, Int>,
)

data class TakeComparison(
    val comparable: Boolean,
    val rejectedReason: String? = null,
    val fragmentId: String? = null,
    val takeA: TakeAlignment? = null,
    val takeB: TakeAlignment? = null,
    val suggestions: List<String> = emptyList(),
)

/**
 * 把演奏事件按顺序对齐到同一份参考目标表。
 * 不用贪心最近邻，避免缺一音后整段错位；也不把整段统一延迟悄悄减掉。
 */
object TakeComparator {
    const val TIME_GATE_MS = 700L
    private const val MISS_COST = 1.0
    private const val EXTRA_COST = 1.0
    private const val FAR_MATCH_COST = 5.0

    fun align(take: PerformanceTake, reference: PracticeReference? = null): TakeAlignment {
        val ref = reference ?: PracticeReference(
            fragmentId = take.fragmentId,
            rhythm = take.rhythm,
            targets = take.targets.orEmpty(),
            description = take.fragmentId,
        )
        val usable = take.events.mapIndexed { index, event -> index to event }
        val aligned = ArrayList<AlignmentItem>()
        val skipped = usable.filter { (_, event) -> !event.stable || event.glideSuspected }
        val candidates = usable.filter { (_, event) -> event.stable && !event.glideSuspected }

        if (ref.targets.isEmpty()) {
            for ((index, event) in candidates) {
                aligned += AlignmentItem(
                    kind = AlignmentKind.MATCH,
                    performanceIndex = index,
                    event = withBeat(event, ref.rhythm),
                    refBeatIndex = ref.rhythm?.beatIndex(event.startMs),
                )
            }
            for ((index, event) in skipped) {
                aligned += unaligned(index, event, ref.rhythm)
            }
            return summarize(take, aligned)
        }

        val items = needlemanWunsch(ref, candidates, skipped, take)
        return summarize(take, items)
    }

    fun compare(takeA: PerformanceTake, takeB: PerformanceTake): TakeComparison {
        val reason = incomparability(takeA, takeB)
        if (reason != null) {
            return TakeComparison(comparable = false, rejectedReason = reason)
        }
        val reference = PracticeReference(
            fragmentId = takeA.fragmentId,
            rhythm = takeA.rhythm,
            targets = takeA.targets.orEmpty(),
            description = takeA.fragmentId,
        )
        val alignedA = align(takeA, reference)
        val alignedB = align(takeB, reference)
        return TakeComparison(
            comparable = true,
            fragmentId = takeA.fragmentId,
            takeA = alignedA,
            takeB = alignedB,
            suggestions = suggestions(alignedA, alignedB),
        )
    }

    fun incomparability(takeA: PerformanceTake, takeB: PerformanceTake): String? {
        if (takeA.fragmentId != takeB.fragmentId) {
            return "片段不同（${takeA.fragmentId} 与 ${takeB.fragmentId}），不能比较。"
        }
        if (takeA.rhythm != takeB.rhythm) {
            return "节拍参考不同，不能比较。"
        }
        if (takeA.targets != takeB.targets) {
            return "目标表不同，不能比较。"
        }
        return null
    }

    private fun needlemanWunsch(
        ref: PracticeReference,
        candidates: List<Pair<Int, NoteEvent>>,
        skipped: List<Pair<Int, NoteEvent>>,
        take: PerformanceTake,
    ): List<AlignmentItem> {
        val targets = ref.targets
        val n = targets.size
        val m = candidates.size
        val dp = Array(n + 1) { DoubleArray(m + 1) }
        val prev = Array(n + 1) { IntArray(m + 1) } // 1 diag, 2 up (miss), 3 left (extra)
        for (i in 1..n) {
            dp[i][0] = i * MISS_COST
            prev[i][0] = 2
        }
        for (j in 1..m) {
            dp[0][j] = j * EXTRA_COST
            prev[0][j] = 3
        }
        for (i in 1..n) {
            for (j in 1..m) {
                val match = dp[i - 1][j - 1] + matchCost(targets[i - 1], candidates[j - 1].second)
                val miss = dp[i - 1][j] + MISS_COST
                val extra = dp[i][j - 1] + EXTRA_COST
                when {
                    match <= miss && match <= extra -> {
                        dp[i][j] = match
                        prev[i][j] = 1
                    }
                    miss <= extra -> {
                        dp[i][j] = miss
                        prev[i][j] = 2
                    }
                    else -> {
                        dp[i][j] = extra
                        prev[i][j] = 3
                    }
                }
            }
        }
        val raw = ArrayList<AlignmentItem>()
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && prev[i][j] == 1 -> {
                    raw += pairItem(ref, i - 1, candidates[j - 1], take)
                    i--
                    j--
                }
                i > 0 && (j == 0 || prev[i][j] == 2) -> {
                    raw += missingItem(ref, i - 1)
                    i--
                }
                else -> {
                    raw += extraItem(ref, candidates[j - 1], take)
                    j--
                }
            }
        }
        raw.reverse()
        val items = ArrayList<AlignmentItem>()
        for (item in raw) {
            if (item.kind == AlignmentKind.MATCH || item.kind == AlignmentKind.WRONG_NOTE) {
                val err = item.timingErrorMs
                if (err != null && abs(err) > TIME_GATE_MS) {
                    items += missingItem(ref, item.referenceIndex!!)
                    items += item.copy(
                        kind = AlignmentKind.UNALIGNED,
                        referenceIndex = null,
                        target = null,
                        centsVsTarget = null,
                        timingErrorMs = null,
                        refBeatIndex = item.event?.let { ref.rhythm?.beatIndex(it.startMs) },
                    )
                    continue
                }
            }
            items += item
        }
        for ((index, event) in skipped) {
            items += unaligned(index, event, ref.rhythm)
        }
        return items
    }

    private fun matchCost(target: TargetNote, event: NoteEvent): Double {
        val err = abs(event.startMs - target.startMs)
        if (err > TIME_GATE_MS) return FAR_MATCH_COST
        val cents = centsVsTarget(event, target)
        val wrong = isWrongNote(event, target, cents)
        val pitchCost = when {
            target.midi == null || cents == null -> 0.15
            wrong -> 0.45
            else -> 0.10 + 0.002 * abs(cents)
        }
        return pitchCost + 0.002 * err
    }

    private fun pairItem(
        ref: PracticeReference,
        targetIndex: Int,
        performed: Pair<Int, NoteEvent>,
        take: PerformanceTake,
    ): AlignmentItem {
        val target = ref.targets[targetIndex]
        val event = withBeat(performed.second, ref.rhythm)
        val cents = centsVsTarget(event, target)
        val timing = event.startMs - target.startMs
        val wrong = isWrongNote(event, target, cents)
        val kind = if (wrong) AlignmentKind.WRONG_NOTE else AlignmentKind.MATCH
        val beat = ref.rhythm?.beatIndex(target.startMs) ?: ref.rhythm?.beatIndex(event.startMs)
        return AlignmentItem(
            kind = kind,
            referenceIndex = targetIndex,
            performanceIndex = performed.first,
            target = target,
            event = event,
            centsVsTarget = cents,
            timingErrorMs = timing,
            refBeatIndex = beat,
        )
    }

    private fun missingItem(ref: PracticeReference, targetIndex: Int): AlignmentItem {
        val target = ref.targets[targetIndex]
        return AlignmentItem(
            kind = AlignmentKind.MISSING,
            referenceIndex = targetIndex,
            target = target,
            refBeatIndex = ref.rhythm?.beatIndex(target.startMs),
        )
    }

    private fun extraItem(
        ref: PracticeReference,
        performed: Pair<Int, NoteEvent>,
        take: PerformanceTake,
    ): AlignmentItem {
        val event = withBeat(performed.second, ref.rhythm)
        return AlignmentItem(
            kind = AlignmentKind.EXTRA,
            performanceIndex = performed.first,
            event = event,
            refBeatIndex = ref.rhythm?.beatIndex(event.startMs),
        )
    }

    private fun unaligned(index: Int, event: NoteEvent, rhythm: RhythmReference?): AlignmentItem =
        AlignmentItem(
            kind = AlignmentKind.UNALIGNED,
            performanceIndex = index,
            event = withBeat(event, rhythm),
            refBeatIndex = rhythm?.beatIndex(event.startMs),
        )

    private fun withBeat(event: NoteEvent, rhythm: RhythmReference?): NoteEvent {
        val beat = rhythm?.beatIndex(event.startMs)
        return if (event.beatIndex == beat) event else event.copy(beatIndex = beat)
    }

    fun centsVsTarget(event: NoteEvent, target: TargetNote): Double? {
        val hz = event.pitchHz ?: return null
        val targetHz = target.frequencyHz ?: return null
        return 1200.0 * (ln(hz / targetHz) / ln(2.0))
    }

    fun isWrongNote(event: NoteEvent, target: TargetNote, cents: Double?): Boolean {
        val targetMidi = target.midi ?: return false
        val midi = event.midi ?: return false
        return midi != targetMidi
    }

    private fun summarize(take: PerformanceTake, items: List<AlignmentItem>): TakeAlignment {
        val pitchValues = items.mapNotNull { item ->
            if (item.kind == AlignmentKind.MATCH) item.centsVsTarget else null
        }
        val rhythmValues = items.mapNotNull { item ->
            if (item.kind == AlignmentKind.MATCH || item.kind == AlignmentKind.WRONG_NOTE) {
                item.timingErrorMs?.toDouble()
            } else {
                null
            }
        }
        val counts = AlignmentKind.entries.associateWith { kind -> items.count { it.kind == kind } }
        return TakeAlignment(
            take = take,
            items = items,
            pitch = if (pitchValues.isEmpty()) null else Subscore(
                meanAbs = pitchValues.map { abs(it) }.average(),
                count = pitchValues.size,
                unit = "音分",
            ),
            rhythm = if (rhythmValues.isEmpty()) null else Subscore(
                meanAbs = rhythmValues.map { abs(it) }.average(),
                count = rhythmValues.size,
                unit = "毫秒",
            ),
            counts = counts,
        )
    }

    fun suggestions(a: TakeAlignment, b: TakeAlignment? = null): List<String> {
        val out = ArrayList<String>()
        out += "以下建议来自与参考片段的对照，不是自动修音或 DSP 处理。"
        if (b == null) {
            out += takeAdvice(a, "这一遍")
            return out
        }
        val pitchA = a.pitch
        val pitchB = b.pitch
        if (pitchA != null && pitchB != null) {
            out += when {
                pitchB.meanAbs + 3.0 < pitchA.meanAbs ->
                    "音准：第一遍平均偏差 ${fmt(pitchA.meanAbs)} 音分（${pitchA.count} 个对准音），重奏 ${fmt(pitchB.meanAbs)} 音分（${pitchB.count} 个），有改善。"
                pitchA.meanAbs + 3.0 < pitchB.meanAbs ->
                    "音准：第一遍平均偏差 ${fmt(pitchA.meanAbs)} 音分，重奏 ${fmt(pitchB.meanAbs)} 音分，这一项变差。"
                else ->
                    "音准：两遍平均偏差接近（${fmt(pitchA.meanAbs)} 与 ${fmt(pitchB.meanAbs)} 音分）。"
            }
        }
        val rhythmA = a.rhythm
        val rhythmB = b.rhythm
        if (rhythmA != null && rhythmB != null) {
            out += when {
                rhythmB.meanAbs + 15.0 < rhythmA.meanAbs ->
                    "节奏：第一遍平均时差 ${fmt(rhythmA.meanAbs)} 毫秒（${rhythmA.count} 个已对齐音），重奏 ${fmt(rhythmB.meanAbs)} 毫秒，有改善。未扣除整段统一延迟。"
                rhythmA.meanAbs + 15.0 < rhythmB.meanAbs ->
                    "节奏：第一遍平均时差 ${fmt(rhythmA.meanAbs)} 毫秒，重奏 ${fmt(rhythmB.meanAbs)} 毫秒，这一项变差。未扣除整段统一延迟。"
                else ->
                    "节奏：两遍平均时差接近（${fmt(rhythmA.meanAbs)} 与 ${fmt(rhythmB.meanAbs)} 毫秒）。未扣除整段统一延迟。"
            }
        }
        out += slotAdvice(a, b)
        val wrongA = a.counts[AlignmentKind.WRONG_NOTE] ?: 0
        val wrongB = b.counts[AlignmentKind.WRONG_NOTE] ?: 0
        val missA = a.counts[AlignmentKind.MISSING] ?: 0
        val missB = b.counts[AlignmentKind.MISSING] ?: 0
        val extraA = a.counts[AlignmentKind.EXTRA] ?: 0
        val extraB = b.counts[AlignmentKind.EXTRA] ?: 0
        if (wrongA + missA + extraA + wrongB + missB + extraB > 0) {
            out += "第一遍：错音 $wrongA、缺音 $missA、多音 $extraA；重奏：错音 $wrongB、缺音 $missB、多音 $extraB。"
        }
        return out
    }

    private fun takeAdvice(alignment: TakeAlignment, label: String): List<String> {
        val lines = ArrayList<String>()
        for (item in alignment.items) {
            val beat = item.refBeatIndex?.let { "第${it}拍" } ?: "该音"
            val name = item.target?.noteName ?: item.event?.noteName ?: "音符"
            when (item.kind) {
                AlignmentKind.MATCH -> {
                    val cents = item.centsVsTarget
                    val timing = item.timingErrorMs
                    if (cents != null && abs(cents) >= 10.0) {
                        lines += "$label $beat $name ${dirCents(cents)}约 ${fmt(abs(cents))} 音分。"
                    }
                    if (timing != null && abs(timing) >= 40L) {
                        lines += "$label $beat $name ${dirTime(timing)}约 ${abs(timing)} 毫秒。"
                    }
                }
                AlignmentKind.WRONG_NOTE -> {
                    val got = item.event?.noteName ?: "其他音"
                    lines += "$label $beat 目标是 $name，实际更接近 $got，记为错音，不是把后面的音整体挪位。"
                }
                AlignmentKind.MISSING -> lines += "$label 少了 $beat 的 $name。"
                AlignmentKind.EXTRA -> lines += "$label 多了一个 ${item.event?.noteName ?: "音"}（约 ${item.event?.startMs} ms）。"
                AlignmentKind.UNALIGNED -> lines += "$label 有一个未对齐或不够稳定的音（约 ${item.event?.startMs} ms），未当作确定错音。"
            }
        }
        if (lines.isEmpty()) {
            lines += "$label 已对齐的音在音准和节奏上没有明显偏差。"
        }
        return lines
    }

    private fun slotAdvice(a: TakeAlignment, b: TakeAlignment): List<String> {
        val lines = ArrayList<String>()
        val targets = a.take.targets.orEmpty().indices
        for (index in targets) {
            val ia = a.items.find { it.referenceIndex == index && it.kind != AlignmentKind.UNALIGNED }
            val ib = b.items.find { it.referenceIndex == index && it.kind != AlignmentKind.UNALIGNED }
            if (ia == null && ib == null) continue
            val beat = (ia ?: ib)?.refBeatIndex?.let { "第${it}拍" } ?: "该音"
            val name = (ia ?: ib)?.target?.noteName ?: "音符"
            if (ia?.kind == AlignmentKind.WRONG_NOTE && ib?.kind == AlignmentKind.MATCH) {
                lines += "$beat $name：第一遍弹成了 ${ia.event?.noteName ?: "其他音"}（错音），重奏已对准目标音。"
            } else if (ia?.kind == AlignmentKind.MISSING && ib?.kind != AlignmentKind.MISSING && ib != null) {
                lines += "$beat $name：第一遍缺音，重奏已奏出。"
            } else if (ia?.kind != AlignmentKind.MISSING && ia != null && ib?.kind == AlignmentKind.MISSING) {
                lines += "$beat $name：重奏缺音。"
            }
            val ca = ia?.centsVsTarget
            val cb = ib?.centsVsTarget
            if (ia?.kind == AlignmentKind.MATCH && ib?.kind == AlignmentKind.MATCH && ca != null && cb != null) {
                if (abs(ca) >= 10.0 || abs(cb) >= 10.0) {
                    lines += "$beat $name 音准：第一遍 ${signed(ca)} 音分，重奏 ${signed(cb)} 音分。"
                }
            }
            val ta = ia?.timingErrorMs
            val tb = ib?.timingErrorMs
            if (ta != null && tb != null && (abs(ta) >= 40L || abs(tb) >= 40L) &&
                (ia.kind == AlignmentKind.MATCH || ia.kind == AlignmentKind.WRONG_NOTE) &&
                (ib.kind == AlignmentKind.MATCH || ib.kind == AlignmentKind.WRONG_NOTE)
            ) {
                lines += "$beat $name 节奏：第一遍 ${signed(ta.toDouble())} 毫秒，重奏 ${signed(tb.toDouble())} 毫秒。"
            }
        }
        return lines
    }

    private fun dirCents(cents: Double): String = if (cents > 0) "偏高" else "偏低"
    private fun dirTime(ms: Long): String = if (ms > 0) "晚了" else "早了"
    private fun fmt(value: Double): String = abs(value).roundToInt().toString()
    private fun signed(value: Double): String {
        val v = value.roundToInt()
        return if (v > 0) "+$v" else "$v"
    }
}
