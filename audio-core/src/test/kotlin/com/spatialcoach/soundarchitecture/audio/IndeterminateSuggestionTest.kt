package com.spatialcoach.soundarchitecture.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 不可判定时，全部建议字符串都必须是完整、可硬断言的说明，
 * 不得再按对齐项写出「第一遍缺音 / 重奏缺音」等确定错漏或改善结论。
 */
class IndeterminateSuggestionTest {

    private fun official(rendition: PracticeFragments.Rendition): PerformanceTake =
        PassageAnalyzer.takeFromRendition(rendition)

    private fun glideOfficial(takeId: String): PerformanceTake {
        val sr = PracticeFragments.SAMPLE_RATE
        val startHz = Tuning.A4_HZ
        val endHz = Tuning.hzFromMidi(70.0)
        val samples = (sr * 1.6).toInt()
        val pcm = FloatArray(samples) { i ->
            val t = i / samples.toDouble()
            val hz = startHz + (endHz - startHz) * t
            (0.4 * kotlin.math.sin(2.0 * kotlin.math.PI * hz * i / sr)).toFloat()
        }
        val report = PassageAnalyzer.analyze(pcm, sr)
        return PracticeFragments.buildTake(
            takeId = takeId,
            events = report.events,
            reference = PracticeFragments.REFERENCE_V1,
            referenceConfirmed = true,
            sourceName = takeId,
            segmentation = report.diagnostics,
        )
    }

    private val forbiddenFragments = listOf(
        "第一遍缺音",
        "重奏缺音",
        "这一遍 少了",
        "第一遍 少了",
        "重奏 少了",
        "少了 第",
        "重奏已奏出",
        "多了一个",
        "错音  第一遍",
        "下面只列出有对照证据的分项变化",
        "重奏没有改善",
        "分项有改善也有变差",
        "重奏已对准谱面目标",
        "缺音  第一遍",
        "多音  第一遍",
    )

    private fun assertIndeterminateSuggestions(suggestions: List<String>, label: String) {
        val joined = suggestions.joinToString("\n")
        assertTrue(
            ReviewText.INDETERMINATE_COUNTS_ADVICE in suggestions,
            "$label missing exact INDETERMINATE_COUNTS_ADVICE.\n$joined",
        )
        assertTrue(
            ReviewText.RERECORD_CONDITION in suggestions,
            "$label missing exact RERECORD_CONDITION.\n$joined",
        )
        assertTrue(
            "以下建议来自与参考片段的对照，不是自动修音或 DSP 处理。" in suggestions,
            "$label missing disclaimer.\n$joined",
        )
        assertTrue(ReviewText.WINDOW_BIAS_NOTE in suggestions, "$label missing window bias.\n$joined")
        for (line in suggestions) {
            for (frag in forbiddenFragments) {
                assertTrue(frag !in line, "$label must not contain '$frag' in: $line")
            }
        }
        // 完整字符串硬断言：不可判定路径的建议必须恰好包含这些句子，不得用前缀凑合。
        val required = listOf(
            "以下建议来自与参考片段的对照，不是自动修音或 DSP 处理。",
            ReviewText.WINDOW_BIAS_NOTE,
            ReviewText.INDETERMINATE_COUNTS_ADVICE,
            ReviewText.RERECORD_CONDITION,
        )
        assertTrue(suggestions.containsAll(required), "$label required exact lines missing.\n$joined")
        assertTrue(
            suggestions.none { it.contains("缺音") && (it.contains("第一遍 ") || it.contains("重奏缺") || "少了" in it) && "不可判定" !in it && "无法判定" !in it && "不能" !in it },
            "$label still has a definite missing-note inference.\n$joined",
        )
    }

    @Test
    fun firstIndeterminateDoesNotAdviseDeterminateMissingOrImprovement() {
        val comparison = TakeComparator.compare(glideOfficial("glide-first"), official(PracticeFragments.Rendition.RETAKE))
        assertTrue(comparison.comparable)
        assertEquals(ImprovementVerdict.INCONCLUSIVE_INDETERMINATE_COUNTS, comparison.verdict)
        assertTrue(!comparison.takeA!!.coverage.countsDeterminate)
        assertTrue(comparison.takeB!!.coverage.countsDeterminate)
        assertIndeterminateSuggestions(comparison.suggestions, "first-indeterminate")
    }

    @Test
    fun retakeIndeterminateDoesNotAdviseDeterminateMissingOrImprovement() {
        val comparison = TakeComparator.compare(official(PracticeFragments.Rendition.FIRST_TAKE), glideOfficial("glide-retake"))
        assertTrue(comparison.comparable)
        assertEquals(ImprovementVerdict.INCONCLUSIVE_INDETERMINATE_COUNTS, comparison.verdict)
        assertTrue(comparison.takeA!!.coverage.countsDeterminate)
        assertTrue(!comparison.takeB!!.coverage.countsDeterminate)
        assertIndeterminateSuggestions(comparison.suggestions, "retake-indeterminate")
    }

    @Test
    fun bothIndeterminateDoesNotAdviseDeterminateMissingOrImprovement() {
        val comparison = TakeComparator.compare(glideOfficial("glide-a"), glideOfficial("glide-b"))
        assertTrue(comparison.comparable)
        assertEquals(ImprovementVerdict.INCONCLUSIVE_INDETERMINATE_COUNTS, comparison.verdict)
        assertTrue(!comparison.takeA!!.coverage.countsDeterminate)
        assertTrue(!comparison.takeB!!.coverage.countsDeterminate)
        assertIndeterminateSuggestions(comparison.suggestions, "both-indeterminate")
    }

    @Test
    fun determinateOfficialSuggestionsKeepExactFaultAndCountLines() {
        val comparison = TakeComparator.compare(
            official(PracticeFragments.Rendition.FIRST_TAKE),
            official(PracticeFragments.Rendition.RETAKE),
        )
        assertTrue(comparison.comparable)
        assertEquals(ImprovementVerdict.SPECIFIC_EVIDENCE, comparison.verdict)
        val suggestions = comparison.suggestions
        val joined = suggestions.joinToString("\n")
        val exactWrong = "第4拍 谱面目标 E4：第一遍最近律音是 F4（错音），重奏已对准谱面目标。"
        val exactMissingCounts = "缺音  第一遍 0  ·  重奏 0；多音  第一遍 0  ·  重奏 0。"
        val exactWrongCounts = "错音  第一遍 1  ·  重奏 0（单列，不折进对准音音分）。"
        val exactEvidence = "下面只列出有对照证据的分项变化，不是整体分数改善。"
        assertTrue(exactWrong in suggestions, "regression: missing exact wrong-note advice.\n$joined")
        assertTrue(exactMissingCounts in suggestions, "regression: missing exact missing/extra counts.\n$joined")
        assertTrue(exactWrongCounts in suggestions, "regression: missing exact wrong counts.\n$joined")
        assertTrue(exactEvidence in suggestions, "regression: missing exact evidence line.\n$joined")
        assertTrue(ReviewText.INDETERMINATE_COUNTS_ADVICE !in suggestions)
        assertTrue(ReviewText.RERECORD_CONDITION !in suggestions)
    }

    @Test
    fun singleTakeIndeterminateSuggestionsAreExactAndHaveNoFaultInferences() {
        val aligned = TakeComparator.align(glideOfficial("glide-solo"))
        val suggestions = TakeComparator.suggestions(aligned)
        assertIndeterminateSuggestions(suggestions, "single-indeterminate")
        assertTrue(suggestions.none { it.startsWith("这一遍 少了") || "多了一个" in it || "记为错音" in it })
    }
}
