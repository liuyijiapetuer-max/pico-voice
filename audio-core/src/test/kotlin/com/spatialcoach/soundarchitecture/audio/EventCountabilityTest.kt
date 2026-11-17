package com.spatialcoach.soundarchitecture.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventCountabilityTest {
    private val sr = PracticeFragments.SAMPLE_RATE
    private val a4 = Tuning.A4_HZ

    private fun repeatReference(starts: List<Long>, durationMs: Long = 200L): PracticeReference {
        val targets = starts.map { TargetNote(startMs = it, durationMs = durationMs, midi = 69) }
        return PracticeReference(
            fragmentId = "repeat-a4-probe",
            rhythm = RhythmReference(bpm = 120.0, originMs = 0L),
            targets = targets,
            description = "测试用相邻同音参考，不是官方练习谱。",
        )
    }

    @Test
    fun spacedSamePitchRepeatsAreDeterminateWithStrictTiming() {
        // 200 ms 音 + 200 ms 无声，应能分清，不得靠“期望 4 音”强行切开。
        val starts = listOf(0L, 400L, 800L, 1200L)
        val notes = starts.map { PracticeFragments.SynthNote(it, 200L, a4) }
        val pcm = PracticeFragments.renderNotes(notes, tailMs = 250L)
        val take = PassageAnalyzer.take(
            takeId = "spaced-a4",
            samples = pcm,
            reference = repeatReference(starts, 200L),
            referenceConfirmed = true,
        )
        val events = take.events.filter { it.stable && it.noteName == "A4" }
        assertEquals(4, events.size, "spaced events=${take.events.map { it.noteName to it.startMs }} issues=${take.segmentation.issues}")
        for (i in starts.indices) {
            assertTrue(abs(events[i].startMs - starts[i]) <= 50L, "start[$i]=${events[i].startMs} expected=${starts[i]}")
        }
        val aligned = TakeComparator.align(take)
        assertTrue(aligned.coverage.countsDeterminate, aligned.coverage.countabilityReason)
        assertEquals(0, aligned.coverage.missing)
        assertEquals(0, aligned.coverage.extra)
        assertEquals(4, aligned.coverage.correctSlots)
    }

    @Test
    fun shortGapSamePitchIsIndeterminateAndDoesNotPublishCounts() {
        // 160 ms 音 + 20 ms 间隔，低于可分割无声，不能分清，不得发布确定缺/多音。
        val starts = listOf(0L, 180L, 360L, 540L)
        val notes = starts.map { PracticeFragments.SynthNote(it, 160L, a4) }
        val pcm = PracticeFragments.renderNotes(notes, tailMs = 250L)
        val take = PassageAnalyzer.take(
            takeId = "tight-a4",
            samples = pcm,
            reference = repeatReference(starts, 160L),
            referenceConfirmed = true,
        )
        val aligned = TakeComparator.align(take)
        assertEquals(CountabilityStatus.INDETERMINATE, aligned.coverage.countability, "events=${take.events.map { it.noteName to (it.startMs to it.endMs) }} issues=${take.segmentation.issues}")
        assertNull(aligned.coverage.missing)
        assertNull(aligned.coverage.extra)
        val text = ReviewText.coverageLine("本遍", aligned.coverage)
        assertTrue("不可判定" in text, text)
        assertFalseHasCountDigits(text)
    }

    @Test
    fun legatoSamePitchReattackIsIndeterminate() {
        // 连续同音重新起奏、无间隙：四个 200 ms A4 首尾相接，分段不应被强行切成 4。
        val starts = listOf(0L, 200L, 400L, 600L)
        val notes = starts.map { PracticeFragments.SynthNote(it, 200L, a4) }
        val pcm = PracticeFragments.renderNotes(notes, tailMs = 250L)
        val take = PassageAnalyzer.take(
            takeId = "legato-a4",
            samples = pcm,
            reference = repeatReference(starts, 200L),
            referenceConfirmed = true,
        )
        val aligned = TakeComparator.align(take)
        assertTrue(take.events.size < 4 || !aligned.coverage.countsDeterminate,
            "legato must not be force-split into a confident 4, events=${take.events.map { it.startMs to it.endMs }}")
        assertEquals(CountabilityStatus.INDETERMINATE, aligned.coverage.countability, aligned.coverage.countabilityReason)
        assertNull(aligned.coverage.missing)
        assertNull(aligned.coverage.extra)
    }

    @Test
    fun glideDoesNotPublishDeterminateMissingExtraOrImprovement() {
        val sr = this.sr
        val startHz = Tuning.A4_HZ
        val endHz = Tuning.hzFromMidi(70.0)
        val samples = (sr * 1.6).toInt()
        val pcm = FloatArray(samples) { i ->
            val t = i / samples.toDouble()
            val hz = startHz + (endHz - startHz) * t
            (0.4 * kotlin.math.sin(2.0 * kotlin.math.PI * hz * i / sr)).toFloat()
        }
        val report = PassageAnalyzer.analyze(pcm, sr)
        val take = PracticeFragments.buildTake(
            takeId = "glide-counts",
            events = report.events,
            reference = PracticeFragments.REFERENCE_V1,
            referenceConfirmed = true,
            segmentation = report.diagnostics,
        )
        val aligned = TakeComparator.align(take)
        assertEquals(CountabilityStatus.INDETERMINATE, aligned.coverage.countability, aligned.coverage.countabilityReason)
        assertNull(aligned.coverage.missing)
        assertNull(aligned.coverage.extra)
        val other = PassageAnalyzer.takeFromRendition(PracticeFragments.Rendition.RETAKE)
        val comparison = TakeComparator.compare(take, other)
        assertTrue(comparison.comparable)
        assertEquals(ImprovementVerdict.INCONCLUSIVE_INDETERMINATE_COUNTS, comparison.verdict)
        assertTrue(ReviewText.INDETERMINATE_COUNTS_ADVICE in comparison.suggestions)
        assertTrue(ReviewText.RERECORD_CONDITION in comparison.suggestions)
        assertTrue(comparison.suggestions.none { "第一遍缺音" in it || "重奏缺音" in it || it.startsWith("缺音  第一遍") })
        val line = ReviewText.coverageLine("滑音", aligned.coverage)
        assertTrue("不可判定" in line, line)
        assertTrue(!Regex("缺音\\d").containsMatchIn(line), line)
    }

    @Test
    fun officialDistinctPitchesStillGiveDeterminateMissingCounts() {
        val notes = listOf(
            PracticeFragments.SynthNote(0, 400, Tuning.hzFromMidi(69.0)),
            PracticeFragments.SynthNote(500, 400, Tuning.hzFromMidi(72.0)),
            PracticeFragments.SynthNote(1500, 400, Tuning.hzFromMidi(64.0)),
        )
        val take = PassageAnalyzer.take("missing-g4", PracticeFragments.renderNotes(notes))
        val aligned = TakeComparator.align(take)
        assertTrue(aligned.coverage.countsDeterminate, aligned.coverage.countabilityReason)
        assertEquals(1, aligned.coverage.missing)
        assertEquals(0, aligned.coverage.extra)
    }

    private fun assertFalseHasCountDigits(text: String) {
        assertTrue("缺音/多音不可判定" in text, text)
        assertTrue(!Regex("缺音\\d").containsMatchIn(text), text)
        assertTrue(!Regex("多音\\d").containsMatchIn(text), text)
    }
}
