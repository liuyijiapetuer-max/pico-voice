package com.spatialcoach.soundarchitecture.audio

import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 演奏复验：多音 PCM 必须先经过真实 [AnalysisEngine]，再分段、对齐、比较。
 */
class PassageReviewTest {

    private val sr = PracticeFragments.SAMPLE_RATE

    @Test
    fun referenceMelodyYieldsFourNotesThroughEngine() {
        val events = PassageAnalyzer.events(PracticeFragments.render(PracticeFragments.Rendition.REFERENCE))
        val stable = events.filter { it.stable }
        assertTrue(stable.size >= 4, "expected 4 notes, got ${events.map { describe(it) }}")
        val names = stable.take(4).map { it.noteName }
        assertEquals(listOf("A4", "C5", "G4", "E4"), names)
        val starts = stable.take(4).map { it.startMs }
        val expected = listOf(0L, 500L, 1000L, 1500L)
        for (i in expected.indices) {
            assertTrue(abs(starts[i] - expected[i]) <= 80L, "start[$i]=${starts[i]}")
        }
        assertTrue(stable.all { it.beatIndex == null }, "segmenter must not invent beats")
        assertTrue(stable.all { it.centsVsTarget == null }, "no target until alignment")
    }

    @Test
    fun explicitGridFillsBeatsAndMissingGridLeavesBeatNull() {
        val take = PassageAnalyzer.takeFromRendition(PracticeFragments.Rendition.REFERENCE)
        val withGrid = TakeComparator.align(take)
        val beats = withGrid.items.filter { it.kind == AlignmentKind.MATCH }.map { it.refBeatIndex }
        assertEquals(listOf(1, 2, 3, 4), beats)
        val noGridRef = PracticeFragments.REFERENCE_V1.copy(rhythm = null)
        val noGridTake = take.copy(rhythm = null)
        val without = TakeComparator.align(noGridTake, noGridRef)
        assertTrue(without.items.filter { it.kind == AlignmentKind.MATCH }.all { it.refBeatIndex == null })
        assertTrue(without.items.mapNotNull { it.event }.all { it.beatIndex == null })
    }

    @Test
    fun unknownTargetsDoNotMarkWrongNotes() {
        val pcm = PracticeFragments.render(PracticeFragments.Rendition.FIRST_TAKE)
        val events = PassageAnalyzer.events(pcm)
        val noTargets = PracticeReference(
            fragmentId = PracticeFragments.ID_V1,
            rhythm = PracticeFragments.RHYTHM_V1,
            targets = PracticeFragments.TARGETS_V1.map { it.copy(midi = null) },
            description = "times only",
        )
        val take = PracticeFragments.buildTake("plain", events, noTargets)
        val aligned = TakeComparator.align(take, noTargets)
        assertTrue(aligned.items.none { it.kind == AlignmentKind.WRONG_NOTE }, "items=${aligned.items.map { it.kind }}")
        assertTrue(aligned.items.filter { it.kind == AlignmentKind.MATCH }.all { it.centsVsTarget == null })
        assertNull(aligned.pitch)
    }

    @Test
    fun plusTwentyFiveCentsIsIntonationNotWrongNote() {
        val pcm = PracticeFragments.renderNotes(
            PracticeFragments.TARGETS_V1.map { target ->
                PracticeFragments.SynthNote(
                    startMs = target.startMs,
                    durationMs = target.durationMs,
                    frequencyHz = hz(target.midi!!, 25.0),
                )
            },
        )
        val aligned = TakeComparator.align(PassageAnalyzer.take("sharp", pcm))
        val matches = aligned.items.filter { it.kind == AlignmentKind.MATCH }
        assertEquals(4, matches.size)
        assertTrue(aligned.items.none { it.kind == AlignmentKind.WRONG_NOTE })
        for (item in matches) {
            val cents = assertNotNull(item.centsVsTarget)
            assertTrue(cents > 0.0, "cents=$cents")
            assertTrue(abs(cents - 25.0) <= 10.0, "cents=$cents")
        }
        assertNotNull(aligned.pitch)
        assertTrue(abs(aligned.pitch!!.meanAbs - 25.0) <= 10.0, "mean=${aligned.pitch!!.meanAbs}")
    }

    @Test
    fun minusTwentyFiveCentsIsIntonationNotWrongNote() {
        val pcm = PracticeFragments.renderNotes(
            PracticeFragments.TARGETS_V1.map { target ->
                PracticeFragments.SynthNote(
                    startMs = target.startMs,
                    durationMs = target.durationMs,
                    frequencyHz = hz(target.midi!!, -25.0),
                )
            },
        )
        val aligned = TakeComparator.align(PassageAnalyzer.take("flat", pcm))
        val matches = aligned.items.filter { it.kind == AlignmentKind.MATCH }
        assertEquals(4, matches.size)
        assertTrue(aligned.items.none { it.kind == AlignmentKind.WRONG_NOTE })
        for (item in matches) {
            val cents = assertNotNull(item.centsVsTarget)
            assertTrue(cents < 0.0, "cents=$cents")
            assertTrue(abs(cents + 25.0) <= 10.0, "cents=$cents")
        }
    }

    @Test
    fun globalEightyMsDelayIsReportedAndNotSubtracted() {
        val onTime = PassageAnalyzer.take(
            "on-time",
            PracticeFragments.render(PracticeFragments.Rendition.REFERENCE),
        )
        val delayedPcm = PracticeFragments.renderNotes(
            PracticeFragments.TARGETS_V1.map { target ->
                PracticeFragments.SynthNote(
                    startMs = target.startMs + 80L,
                    durationMs = target.durationMs,
                    frequencyHz = target.frequencyHz!!,
                )
            },
        )
        val delayed = PassageAnalyzer.take("late", delayedPcm)
        val a = TakeComparator.align(onTime)
        val b = TakeComparator.align(delayed)
        val errA = a.items.filter { it.kind == AlignmentKind.MATCH }.map { it.timingErrorMs!! }
        val errB = b.items.filter { it.kind == AlignmentKind.MATCH }.map { it.timingErrorMs!! }
        assertEquals(4, errA.size)
        assertEquals(4, errB.size)
        for (i in errA.indices) {
            val delta = errB[i] - errA[i]
            assertTrue(abs(delta - 80L) <= 35L, "delta[$i]=$delta a=${errA[i]} b=${errB[i]}")
        }
        val meanB = errB.map { it.toDouble() }.average()
        val meanA = errA.map { it.toDouble() }.average()
        assertTrue(abs((meanB - meanA) - 80.0) <= 25.0, "meanA=$meanA meanB=$meanB")
        // 窗口定位可能整体偏早，绝对均值不必贴近 +80；若减掉整段统一延迟，均值会靠近 0。
        assertTrue(abs(meanB) >= 25.0, "global delay appears absorbed, meanB=$meanB meanA=$meanA")
        assertNotNull(b.rhythm)
        assertTrue(b.rhythm!!.meanAbs >= 25.0, "rhythm mean=${b.rhythm!!.meanAbs}")
    }

    @Test
    fun missingMiddleNoteDoesNotShiftTheRest() {
        val notes = listOf(
            synth(0, 400, 69),
            synth(500, 400, 72),
            // 缺 G4
            synth(1500, 400, 64),
        )
        val aligned = TakeComparator.align(PassageAnalyzer.take("missing-g4", PracticeFragments.renderNotes(notes)))
        assertEquals(AlignmentKind.MATCH, kindAt(aligned, 0))
        assertEquals("A4", aligned.items.first { it.referenceIndex == 0 }.event?.noteName)
        assertEquals(AlignmentKind.MATCH, kindAt(aligned, 1))
        assertEquals("C5", aligned.items.first { it.referenceIndex == 1 }.event?.noteName)
        assertEquals(AlignmentKind.MISSING, kindAt(aligned, 2))
        assertEquals(AlignmentKind.MATCH, kindAt(aligned, 3))
        val e4 = aligned.items.first { it.referenceIndex == 3 }
        assertEquals("E4", e4.event?.noteName)
        assertEquals(AlignmentKind.MATCH, e4.kind)
        assertEquals(1, aligned.counts[AlignmentKind.MISSING])
        assertEquals(0, aligned.counts[AlignmentKind.WRONG_NOTE] ?: 0)
    }

    @Test
    fun extraNoteIsExplicitAndDoesNotBreakMatches() {
        val notes = PracticeFragments.TARGETS_V1.map { synth(it.startMs, it.durationMs, it.midi!!) } +
            listOf(synth(2200, 400, 62))
        val aligned = TakeComparator.align(PassageAnalyzer.take("extra-d4", PracticeFragments.renderNotes(notes)))
        assertEquals(4, aligned.items.count { it.kind == AlignmentKind.MATCH })
        assertEquals(1, aligned.counts[AlignmentKind.EXTRA])
        val extra = aligned.items.first { it.kind == AlignmentKind.EXTRA }
        assertEquals("D4", extra.event?.noteName)
        assertTrue(abs(extra.event!!.startMs - 2200L) <= 80L, "extra start=${extra.event!!.startMs}")
    }

    @Test
    fun wrongNoteIsExplicitAndDoesNotShiftSequence() {
        val notes = listOf(
            synth(0, 400, 69),
            synth(500, 400, 72),
            synth(1000, 400, 67),
            synth(1500, 400, 65), // F4 instead of E4
        )
        val aligned = TakeComparator.align(PassageAnalyzer.take("wrong-e4", PracticeFragments.renderNotes(notes)))
        assertEquals(AlignmentKind.MATCH, kindAt(aligned, 0))
        assertEquals(AlignmentKind.MATCH, kindAt(aligned, 1))
        assertEquals(AlignmentKind.MATCH, kindAt(aligned, 2))
        val last = aligned.items.first { it.referenceIndex == 3 }
        assertEquals(AlignmentKind.WRONG_NOTE, last.kind)
        assertEquals("F4", last.event?.noteName)
        assertEquals("E4", last.target?.noteName)
        assertEquals(1, aligned.counts[AlignmentKind.WRONG_NOTE])
        assertEquals(0, aligned.counts[AlignmentKind.MISSING] ?: 0)
    }

    @Test
    fun differentFragmentIsRejected() {
        val a = PassageAnalyzer.takeFromRendition(PracticeFragments.Rendition.FIRST_TAKE)
        val b = a.copy(takeId = "other", fragmentId = "other-fragment")
        val comparison = TakeComparator.compare(a, b)
        assertFalse(comparison.comparable)
        assertNotNull(comparison.rejectedReason)
        assertTrue("片段" in comparison.rejectedReason!!)
        assertNull(comparison.takeA)
        assertNull(comparison.takeB)
    }

    @Test
    fun differentRhythmIsRejected() {
        val a = PassageAnalyzer.takeFromRendition(PracticeFragments.Rendition.REFERENCE)
        val b = a.copy(takeId = "shifted", rhythm = RhythmReference(bpm = 90.0, originMs = 0L))
        val comparison = TakeComparator.compare(a, b)
        assertFalse(comparison.comparable)
        assertTrue(comparison.rejectedReason!!.contains("节拍"))
    }

    @Test
    fun firstTakeVersusRetakeImprovesPitchAndRhythmWithExplicitFaults() {
        val first = PassageAnalyzer.takeFromRendition(PracticeFragments.Rendition.FIRST_TAKE)
        val retake = PassageAnalyzer.takeFromRendition(PracticeFragments.Rendition.RETAKE)
        val comparison = TakeComparator.compare(first, retake)
        assertTrue(comparison.comparable, comparison.rejectedReason ?: "ok")
        val a = assertNotNull(comparison.takeA)
        val b = assertNotNull(comparison.takeB)
        assertEquals(AlignmentKind.WRONG_NOTE, kindAt(a, 3))
        assertEquals("F4", a.items.first { it.referenceIndex == 3 }.event?.noteName)
        assertEquals(AlignmentKind.MATCH, kindAt(b, 3))
        assertEquals("E4", b.items.first { it.referenceIndex == 3 }.event?.noteName)
        val sharp = a.items.first { it.referenceIndex == 0 }.centsVsTarget
        assertNotNull(sharp)
        assertTrue(abs(sharp!! - 25.0) <= 10.0, "first A4 cents=$sharp")
        val late = a.items.first { it.referenceIndex == 2 }.timingErrorMs
        val lateB = b.items.first { it.referenceIndex == 2 }.timingErrorMs
        assertNotNull(late)
        assertNotNull(lateB)
        val refG = TakeComparator.align(PassageAnalyzer.takeFromRendition(PracticeFragments.Rendition.REFERENCE))
            .items.first { it.referenceIndex == 2 }.timingErrorMs!!
        assertTrue(abs((late!! - refG) - 80L) <= 35L, "first G4 err=$late ref=$refG")
        assertTrue(abs(lateB!! - refG) <= 40L || abs(lateB) < abs(late) - 20, "retake G4 err=$lateB")
        assertNotNull(a.pitch)
        assertNotNull(b.pitch)
        assertTrue(b.pitch!!.meanAbs + 3.0 < a.pitch!!.meanAbs, "pitch a=${a.pitch} b=${b.pitch}")
        assertTrue(comparison.suggestions.isNotEmpty())
        assertTrue(comparison.suggestions.any { "不是自动修音" in it || "DSP" in it })
        assertTrue(comparison.suggestions.any { "错音" in it || "音准" in it })
        assertTrue(first.source == InputSource.FILE_SYNTHETIC)
        assertEquals(InputSource.FILE_SYNTHETIC.label, first.source.label)
    }

    @Test
    fun whiteNoiseDoesNotInventStableNotes() {
        val pcm = Signals.whiteNoise(sr, amplitude = 0.3, seed = 2L)
        val events = PassageAnalyzer.events(pcm)
        assertTrue(events.none { it.stable }, "noise events=${events.map { describe(it) }}")
    }

    @Test
    fun slowGlideIsMarkedAndNotAConfidentWrongNoteSequence() {
        val start = Tuning.A4_HZ
        val end = Tuning.hzFromMidi(70.0) // A#4
        val samples = (sr * 1.6).toInt()
        val pcm = FloatArray(samples) { i ->
            val t = i / samples.toDouble()
            val hz = start + (end - start) * t
            (0.4 * kotlin.math.sin(2.0 * kotlin.math.PI * hz * i / sr)).toFloat()
        }
        val events = PassageAnalyzer.events(pcm)
        val stable = events.filter { it.stable }
        assertTrue(stable.size <= 1, "glide split into stable notes: ${events.map { describe(it) }}")
        assertTrue(
            events.isEmpty() || events.any { it.glideSuspected } || stable.isEmpty(),
            "expected glide/unstable, got ${events.map { describe(it) }}",
        )
        val fakeTargets = PracticeReference(
            fragmentId = "glide-probe",
            rhythm = null,
            targets = listOf(
                TargetNote(startMs = 0, durationMs = 800, midi = 69),
                TargetNote(startMs = 800, durationMs = 800, midi = 70),
            ),
            description = "not a real score",
        )
        val take = PerformanceTake(
            takeId = "glide",
            fragmentId = "glide-probe",
            source = InputSource.FILE_SYNTHETIC,
            rhythm = null,
            targets = fakeTargets.targets,
            events = events,
        )
        val aligned = TakeComparator.align(take, fakeTargets)
        val confident = aligned.items.filter { it.kind == AlignmentKind.MATCH || it.kind == AlignmentKind.WRONG_NOTE }
        assertTrue(confident.size <= 1, "glide should not become a confident two-note transcription: $confident")
        assertTrue(
            aligned.items.none { it.kind == AlignmentKind.WRONG_NOTE } ||
                aligned.items.any { it.kind == AlignmentKind.UNALIGNED } ||
                events.any { it.glideSuspected },
            "glide must not be disguised as a wrong-note sequence",
        )
    }

    @Test
    fun quietSineBelowGateHasNoEvents() {
        val pcm = Signals.sine(Tuning.A4_HZ, sr, sr, 0.004)
        val events = PassageAnalyzer.events(pcm)
        assertTrue(events.none { it.stable }, "events=${events.map { describe(it) }}")
    }

    private fun kindAt(alignment: TakeAlignment, referenceIndex: Int): AlignmentKind =
        alignment.items.first { it.referenceIndex == referenceIndex }.kind

    private fun synth(startMs: Long, durationMs: Long, midi: Int, cents: Double = 0.0) =
        PracticeFragments.SynthNote(startMs, durationMs, hz(midi, cents))

    private fun hz(midi: Int, cents: Double): Double =
        Tuning.hzFromMidi(midi.toDouble()) * 2.0.pow(cents / 1200.0)

    private fun describe(event: NoteEvent): String =
        "${event.noteName}@${event.startMs}-${event.endMs} stable=${event.stable} glide=${event.glideSuspected}"
}
