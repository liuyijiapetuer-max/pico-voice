package com.spatialcoach.soundarchitecture.audio

/**
 * 缺音/多音能否发布为确定计数的检验门。不编造置信分数。
 * 依据：谱面相邻同音是否需要可观察分隔、分段器记录的短无声合并、滑音/不稳定状态。
 * 不能分清时计数为空，比较器不得把缺/多音数字和免责声明一起展示。
 */
enum class CountabilityStatus {
    DETERMINATE,
    INDETERMINATE,
}

data class Countability(
    val status: CountabilityStatus,
    val reason: String? = null,
) {
    val determinate: Boolean get() = status == CountabilityStatus.DETERMINATE
}

object EventCountGate {
    fun assess(
        take: PerformanceTake,
        items: List<AlignmentItem> = emptyList(),
        minSilenceMs: Long = 50L,
    ): Countability {
        val reasons = ArrayList<String>()
        val diagnostics = take.segmentation
        for (issue in diagnostics.issues) {
            when (issue.kind) {
                SegmentationIssueKind.SHORT_SILENCE_SAME_PITCH ->
                    reasons += issue.detail
                SegmentationIssueKind.GLIDE_OR_UNSTABLE ->
                    reasons += issue.detail
                SegmentationIssueKind.REFERENCE_REPEAT_UNSEPARATED ->
                    reasons += issue.detail
            }
        }
        val targets = take.targets.orEmpty()
        val repeatPairs = adjacentSamePitchPairs(targets)
        if (repeatPairs.isNotEmpty()) {
            for ((i, j) in repeatPairs) {
                val a = targets[i]
                val b = targets[j]
                val separatorNeeded = (b.startMs - a.endMs).coerceAtLeast(0L)
                val observed = diagnostics.observedSilences.any { silenceOverlaps(it, a.endMs, b.startMs, minSilenceMs) }
                val eventsInWindow = take.events.filter { eventTouches(it, a.startMs, b.endMs) }
                val splitIntoTwo = eventsInWindow.size >= 2 &&
                    eventsInWindow.zipWithNext().any { (left, right) ->
                        right.startMs - left.endMs >= minSilenceMs ||
                            diagnostics.observedSilences.any { sil ->
                                sil.startMs >= left.startMs && sil.endMs <= right.endMs && sil.durationMs >= minSilenceMs
                            }
                    }
                if (!observed && !splitIntoTwo) {
                    reasons += "谱面第${i + 1}与第${j + 1}个目标是相邻同音（${a.noteName ?: "?"}），需要可观察的起音或至少 ${minSilenceMs} ms 无声分隔（谱面间隔 ${separatorNeeded} ms），但未观察到足够分隔，不能给出确定缺音或多音。事件数=${eventsInWindow.size}，不作按期望音数强行切开。"
                }
            }
        }
        // 连续同音长段覆盖多个谱面同音窗，即使没有 SHORT_SILENCE 标记，也不能把少掉的音写成确定缺音。
        if (repeatPairs.isEmpty()) {
            // 官方互异音高片段不因长音本身不可判定。
        }
        if (take.events.any { it.glideSuspected || !it.stable }) {
            reasons += "存在不够稳定或疑似滑音的事件，不能给出确定缺/多音。"
        }
        val unique = reasons.distinct()
        return if (unique.isEmpty()) {
            Countability(CountabilityStatus.DETERMINATE)
        } else {
            Countability(CountabilityStatus.INDETERMINATE, unique.joinToString(" "))
        }
    }

    fun adjacentSamePitchPairs(targets: List<TargetNote>): List<Pair<Int, Int>> {
        val pairs = ArrayList<Pair<Int, Int>>()
        for (i in 0 until targets.size - 1) {
            val a = targets[i]
            val b = targets[i + 1]
            if (a.midi != null && a.midi == b.midi) {
                pairs += i to (i + 1)
            }
        }
        return pairs
    }

    private fun silenceOverlaps(silence: ObservedSilence, fromMs: Long, toMs: Long, minSilenceMs: Long): Boolean {
        if (silence.durationMs < minSilenceMs) return false
        val start = maxOf(silence.startMs, fromMs - 40L)
        val end = minOf(silence.endMs, toMs + 40L)
        return end - start >= minSilenceMs
    }

    private fun eventTouches(event: NoteEvent, startMs: Long, endMs: Long): Boolean {
        return event.endMs >= startMs && event.startMs <= endMs
    }
}
