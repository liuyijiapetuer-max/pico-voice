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

data class AlignmentCoverage(
    val referenceSlots: Int,
    val matchedSlots: Int,
    val correctSlots: Int,
    val wrongNotes: Int,
    val missing: Int?,
    val extra: Int?,
    val unaligned: Int,
    val countability: CountabilityStatus = CountabilityStatus.DETERMINATE,
    val countabilityReason: String? = null,
) {
    val rate: Double get() = if (referenceSlots == 0) 0.0 else matchedSlots.toDouble() / referenceSlots
    fun rateText(): String = "$matchedSlots/$referenceSlots"
    val countsDeterminate: Boolean get() = countability == CountabilityStatus.DETERMINATE
    val sufficientForVerdict: Boolean
        get() = countsDeterminate &&
            referenceSlots > 0 &&
            (missing ?: Int.MAX_VALUE) <= 1 &&
            rate + 1e-9 >= 0.75
}

enum class ImprovementVerdict {
    SPECIFIC_EVIDENCE,
    WORSENED,
    MIXED,
    INCONCLUSIVE_LOW_COVERAGE,
    INCONCLUSIVE_INDETERMINATE_COUNTS,
    INCONCLUSIVE,
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
    val processedReferenceTimingMs: Long? = null,
    val timingVsProcessedReferenceMs: Long? = null,
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
    val coverage: AlignmentCoverage,
)

data class TakeComparison(
    val comparable: Boolean,
    val rejectedReason: String? = null,
    val fragmentId: String? = null,
    val takeA: TakeAlignment? = null,
    val takeB: TakeAlignment? = null,
    val suggestions: List<String> = emptyList(),
    val verdict: ImprovementVerdict? = null,
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
        val processed = processedOfficialAlignmentOrNull(reference)
        val alignedA = annotateRelativeTiming(align(takeA, reference), processed)
        val alignedB = annotateRelativeTiming(align(takeB, reference), processed)
        val verdict = verdictOf(alignedA, alignedB)
        return TakeComparison(
            comparable = true,
            fragmentId = takeA.fragmentId,
            takeA = alignedA,
            takeB = alignedB,
            suggestions = suggestions(alignedA, alignedB, verdict),
            verdict = verdict,
        )
    }

    fun alignWithProcessedReference(
        take: PerformanceTake,
        reference: PracticeReference? = null,
    ): TakeAlignment {
        val ref = reference ?: PracticeReference(
            fragmentId = take.fragmentId,
            rhythm = take.rhythm,
            targets = take.targets.orEmpty(),
            description = take.fragmentId,
        )
        return annotateRelativeTiming(align(take, ref), processedOfficialAlignmentOrNull(ref))
    }

    fun processedOfficialAlignmentOrNull(reference: PracticeReference): TakeAlignment? {
        if (!PracticeFragments.isOfficialReference(reference)) return null
        val official = PassageAnalyzer.takeFromRendition(PracticeFragments.Rendition.REFERENCE)
        return align(official, PracticeFragments.REFERENCE_V1)
    }

    fun annotateRelativeTiming(alignment: TakeAlignment, processed: TakeAlignment?): TakeAlignment {
        if (processed == null) return alignment
        val baseline = processed.items
            .filter { it.referenceIndex != null && it.timingErrorMs != null }
            .associate { it.referenceIndex!! to it.timingErrorMs!! }
        val items = alignment.items.map { item ->
            val base = item.referenceIndex?.let { baseline[it] }
            val raw = item.timingErrorMs
            item.copy(
                processedReferenceTimingMs = base,
                timingVsProcessedReferenceMs = if (raw != null && base != null) raw - base else null,
            )
        }
        return alignment.copy(items = items)
    }

    fun incomparability(takeA: PerformanceTake, takeB: PerformanceTake): String? {
        if (!takeA.referenceConfirmed || !takeB.referenceConfirmed) {
            return "未确认两遍都是固定练习谱 ${PracticeFragments.ID_V1} 的演奏，不能比较。"
        }
        if (takeA.fragmentId != PracticeFragments.ID_V1 || takeB.fragmentId != PracticeFragments.ID_V1) {
            return if (takeA.fragmentId != takeB.fragmentId)
                "片段不同（${takeA.fragmentId} 与 ${takeB.fragmentId}），且当前只支持 ${PracticeFragments.ID_V1}，不能比较。"
            else
                "当前只支持固定练习谱 ${PracticeFragments.ID_V1}（四拍 A4–C5–G4–E4），不支持其他曲目。"
        }
        if (takeA.targets != PracticeFragments.TARGETS_V1 || takeB.targets != PracticeFragments.TARGETS_V1) {
            return "固定练习谱只支持既定四拍目标表，不支持其他曲目或改谱。"
        }
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
        // 错音代价须高于“同音且仍在检测窗内、只是整体偏晚”的时间代价，
        // 否则约 500ms 的整段延后会被配到邻近的下一目标，误判成缺音+错音+多音。
        val pitchCost = when {
            target.midi == null || cents == null -> 0.15
            wrong -> 1.25
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
        val referenceSlots = take.targets?.size ?: items.count { it.referenceIndex != null }
        val gate = EventCountGate.assess(take, items)
        val rawMissing = counts[AlignmentKind.MISSING] ?: 0
        val rawExtra = counts[AlignmentKind.EXTRA] ?: 0
        val coverage = AlignmentCoverage(
            referenceSlots = referenceSlots,
            matchedSlots = (counts[AlignmentKind.MATCH] ?: 0) + (counts[AlignmentKind.WRONG_NOTE] ?: 0),
            correctSlots = counts[AlignmentKind.MATCH] ?: 0,
            wrongNotes = counts[AlignmentKind.WRONG_NOTE] ?: 0,
            missing = if (gate.determinate) rawMissing else null,
            extra = if (gate.determinate) rawExtra else null,
            unaligned = counts[AlignmentKind.UNALIGNED] ?: 0,
            countability = gate.status,
            countabilityReason = gate.reason,
        )
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
            coverage = coverage,
        )
    }

    fun suggestions(
        a: TakeAlignment,
        b: TakeAlignment? = null,
        verdict: ImprovementVerdict? = null,
    ): List<String> {
        val out = ArrayList<String>()
        out += "以下建议来自与参考片段的对照，不是自动修音或 DSP 处理。"
        out += ReviewText.WINDOW_BIAS_NOTE
        if (b == null) {
            out += takeAdvice(a, "这一遍")
            out += coverageLine("这一遍", a)
            return out
        }
        out += coverageLine("第一遍", a)
        out += coverageLine("重奏", b)
        out += "错音  第一遍 ${a.coverage.wrongNotes}  ·  重奏 ${b.coverage.wrongNotes}（单列，不折进对准音音分）。"
        out += missingExtraSuggestion(a, b)
        val pitchA = a.pitch
        val pitchB = b.pitch
        if (pitchA != null && pitchB != null) {
            out += "对准音音分（不含错音，不能当作整体音准结论）：第一遍平均 ${fmt(pitchA.meanAbs)} 音分（n=${pitchA.count}），重奏 ${fmt(pitchB.meanAbs)} 音分（n=${pitchB.count}）。"
        }
        val relA = relativeRhythmMean(a)
        val relB = relativeRhythmMean(b)
        if (relA != null && relB != null) {
            out += "节奏相对同处理参考：第一遍平均 ${fmt(relA)} 毫秒（n=${a.coverage.matchedSlots}），重奏 ${fmt(relB)} 毫秒。原始相对谱面时差仍保留，未减整段统一延迟。"
        } else if (a.rhythm != null && b.rhythm != null) {
            out += "节奏相对谱面平均时差：第一遍 ${fmt(a.rhythm.meanAbs)} 毫秒，重奏 ${fmt(b.rhythm.meanAbs)} 毫秒（含检测窗偏差，不能直接称赶拍）。未减整段统一延迟。"
        }
        out += when (verdict ?: verdictOf(a, b)) {
            ImprovementVerdict.INCONCLUSIVE_LOW_COVERAGE ->
                "缺音或未对齐过多（覆盖率不足 ${a.coverage.rateText()} / ${b.coverage.rateText()}），不能下改善结论。"
            ImprovementVerdict.INCONCLUSIVE_INDETERMINATE_COUNTS ->
                "分段不足以给出确定的缺音或多音计数，不能下改善结论，也不发布缺/多音数字。"
            ImprovementVerdict.INCONCLUSIVE ->
                "证据不足以给出改善或变差的结论。"
            ImprovementVerdict.MIXED ->
                "分项有改善也有变差，不能用单一总分概括。"
            ImprovementVerdict.WORSENED ->
                "按错音/缺音/相对同处理参考的节奏与对准音音分看，重奏没有改善。"
            ImprovementVerdict.SPECIFIC_EVIDENCE ->
                "下面只列出有对照证据的分项变化，不是整体分数改善。"
        }
        out += slotAdvice(a, b)
        return out
    }

    fun verdictOf(a: TakeAlignment, b: TakeAlignment): ImprovementVerdict {
        if (!a.coverage.countsDeterminate || !b.coverage.countsDeterminate) {
            return ImprovementVerdict.INCONCLUSIVE_INDETERMINATE_COUNTS
        }
        if (!a.coverage.sufficientForVerdict || !b.coverage.sufficientForVerdict) {
            return ImprovementVerdict.INCONCLUSIVE_LOW_COVERAGE
        }
        val betterWrong = b.coverage.wrongNotes < a.coverage.wrongNotes
        val worseWrong = b.coverage.wrongNotes > a.coverage.wrongNotes
        val betterMissing = (b.coverage.missing ?: 0) < (a.coverage.missing ?: 0)
        val worseMissing = (b.coverage.missing ?: 0) > (a.coverage.missing ?: 0)
        val pitchA = a.pitch?.meanAbs
        val pitchB = b.pitch?.meanAbs
        val betterPitch = pitchA != null && pitchB != null && pitchB + 3.0 < pitchA
        val worsePitch = pitchA != null && pitchB != null && pitchA + 3.0 < pitchB
        val relA = relativeRhythmMean(a)
        val relB = relativeRhythmMean(b)
        val betterRhythm = relA != null && relB != null && relB + 15.0 < relA
        val worseRhythm = relA != null && relB != null && relA + 15.0 < relB
        val better = listOf(betterWrong, betterMissing, betterPitch, betterRhythm).count { it }
        val worse = listOf(worseWrong, worseMissing, worsePitch, worseRhythm).count { it }
        return when {
            better > 0 && worse > 0 -> ImprovementVerdict.MIXED
            better > 0 -> ImprovementVerdict.SPECIFIC_EVIDENCE
            worse > 0 -> ImprovementVerdict.WORSENED
            else -> ImprovementVerdict.INCONCLUSIVE
        }
    }

    fun relativeRhythmMean(alignment: TakeAlignment): Double? {
        val values = alignment.items.mapNotNull { item ->
            if (item.kind == AlignmentKind.MATCH || item.kind == AlignmentKind.WRONG_NOTE) {
                item.timingVsProcessedReferenceMs?.toDouble()
            } else {
                null
            }
        }
        if (values.isEmpty()) return null
        return values.map { abs(it) }.average()
    }

    private fun missingExtraSuggestion(a: TakeAlignment, b: TakeAlignment): String {
        val ca = a.coverage
        val cb = b.coverage
        if (!ca.countsDeterminate || !cb.countsDeterminate) {
            val why = listOfNotNull(ca.countabilityReason, cb.countabilityReason).distinct().joinToString(" ")
            return "缺音/多音不可判定，不发布确定计数。" + if (why.isBlank()) "" else " $why"
        }
        return "缺音  第一遍 ${ca.missing}  ·  重奏 ${cb.missing}；多音  第一遍 ${ca.extra}  ·  重奏 ${cb.extra}。"
    }

    private fun coverageLine(label: String, alignment: TakeAlignment): String {
        return ReviewText.coverageDetail(label, alignment.coverage)
    }

    private fun takeAdvice(alignment: TakeAlignment, label: String): List<String> {
        val lines = ArrayList<String>()
        for (item in alignment.items) {
            val beat = item.refBeatIndex?.let { "第${it}拍" } ?: "该音"
            val name = item.target?.noteName ?: item.event?.noteName ?: "音符"
            when (item.kind) {
                AlignmentKind.MATCH -> {
                    val cents = item.centsVsTarget
                    if (cents != null && abs(cents) >= 10.0) {
                        lines += "$label $beat $name 相对谱面目标 ${dirCents(cents)}约 ${fmt(abs(cents))} 音分（不是相对最近律音）。"
                    }
                    val rel = item.timingVsProcessedReferenceMs
                    val raw = item.timingErrorMs
                    if (rel != null && abs(rel) >= 40L) {
                        lines += "$label $beat $name 相对同处理参考 ${dirTime(rel)}约 ${abs(rel)} 毫秒。相对谱面原始时差 ${raw?.let { signed(it.toDouble()) } ?: "—"} 毫秒，含检测窗偏差，不能直接称赶拍。"
                    } else if (rel == null && raw != null && abs(raw) >= 40L) {
                        lines += "$label $beat $name 相对谱面时差 ${signed(raw.toDouble())} 毫秒（可能含检测窗偏差，不能直接称赶拍）。"
                    }
                }
                AlignmentKind.WRONG_NOTE -> {
                    val got = item.event?.noteName ?: "其他音"
                    lines += "$label $beat 谱面目标是 $name，实际最近律音是 $got，记为错音，不是把后面的音整体挪位。"
                }
                AlignmentKind.MISSING -> if (alignment.coverage.countsDeterminate) {
                    lines += "$label 少了 $beat 的谱面目标 $name。"
                }
                AlignmentKind.EXTRA -> if (alignment.coverage.countsDeterminate) {
                    lines += "$label 多了一个 ${item.event?.noteName ?: "音"}（约 ${item.event?.startMs} ms）。"
                }
                AlignmentKind.UNALIGNED -> lines += "$label 有一个未对齐或不够稳定的音（约 ${item.event?.startMs} ms），未当作确定错音。"
            }
        }
        if (lines.isEmpty()) {
            lines += "$label 已对齐的音没有需要单独列出的音准或相对同处理参考时差。"
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
                lines += "$beat 谱面目标 $name：第一遍最近律音是 ${ia.event?.noteName ?: "其他音"}（错音），重奏已对准谱面目标。"
            } else if (ia?.kind == AlignmentKind.MISSING && ib?.kind != AlignmentKind.MISSING && ib != null) {
                lines += "$beat 谱面目标 $name：第一遍缺音，重奏已奏出。"
            } else if (ia?.kind != AlignmentKind.MISSING && ia != null && ib?.kind == AlignmentKind.MISSING) {
                lines += "$beat 谱面目标 $name：重奏缺音。"
            }
            val ca = ia?.centsVsTarget
            val cb = ib?.centsVsTarget
            if (ia?.kind == AlignmentKind.MATCH && ib?.kind == AlignmentKind.MATCH && ca != null && cb != null) {
                if (abs(ca) >= 10.0 || abs(cb) >= 10.0) {
                    lines += "$beat $name 相对谱面目标音准：第一遍 ${signed(ca)} 音分，重奏 ${signed(cb)} 音分。"
                }
            }
            val ra = ia?.timingVsProcessedReferenceMs
            val rb = ib?.timingVsProcessedReferenceMs
            if (ra != null && rb != null && (abs(ra) >= 40L || abs(rb) >= 40L) &&
                (ia.kind == AlignmentKind.MATCH || ia.kind == AlignmentKind.WRONG_NOTE) &&
                (ib.kind == AlignmentKind.MATCH || ib.kind == AlignmentKind.WRONG_NOTE)
            ) {
                val rawA = ia.timingErrorMs
                val rawB = ib.timingErrorMs
                lines += "$beat $name 相对同处理参考：第一遍 ${signed(ra.toDouble())} 毫秒，重奏 ${signed(rb.toDouble())} 毫秒。相对谱面原始时差 ${rawA?.let { signed(it.toDouble()) } ?: "—"} / ${rawB?.let { signed(it.toDouble()) } ?: "—"} 毫秒（含检测窗偏差，不能直接称赶拍）。"
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
