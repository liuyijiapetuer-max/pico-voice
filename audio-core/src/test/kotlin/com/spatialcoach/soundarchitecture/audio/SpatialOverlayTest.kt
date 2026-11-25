package com.spatialcoach.soundarchitecture.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpatialOverlayTest {

    private val reference = PracticeFragments.REFERENCE_V1

    @Test
    fun referenceOnlyUsesWhiteWireframeAtScoreTimes() {
        val scene = SpatialOverlay.layout(reference)
        val refs = scene.members.filter { it.role == OverlayRole.REFERENCE }
        assertEquals(4, refs.size)
        assertTrue(refs.all { it.style == OverlayStyle.WIREFRAME })
        assertEquals(1.2f, scene.widthMeters)
        assertEquals(EvaluationPolicy.PRERECORDED_BANNER, scene.sourceBanner)
        assertNull(SpatialOverlay.selectionReadout(scene))
        val starts = refs.map { it.center.x - it.size.x / 2f }
        assertTrue(starts[0] < starts[1] && starts[1] < starts[2] && starts[2] < starts[3])
        val a4y = StageMapping.yFromMidi(69.0)
        val c5y = StageMapping.yFromMidi(72.0)
        assertTrue(abs(refs[0].center.y - a4y) <= 0.001f)
        assertTrue(refs[1].center.y > refs[0].center.y)
        assertTrue(abs(refs[1].center.y - c5y) <= 0.001f)
        assertTrue(scene.placementNote.contains("尚未完成对实体乐器的真机锚定"))
    }

    @Test
    fun sharpLateAndWrongNoteAreNotTimeStretched() {
        val take = PracticeFragments.buildTake(
            takeId = "first-fixture",
            events = listOf(
                ev(0, 400, 69, "A4", hz = Tuning.hzFromMidi(69.0 + 0.25), confidence = 0.95),
                ev(500, 900, 72, "C5", confidence = 0.95),
                ev(1080, 1480, 67, "G4", confidence = 0.95),
                ev(1500, 1900, 65, "F4", confidence = 0.95),
            ),
            rendition = PracticeFragments.Rendition.FIRST_TAKE,
        )
        val aligned = TakeComparator.align(take, reference)
        val scene = SpatialOverlay.layout(reference, current = aligned, source = InputSource.FILE_SYNTHETIC)
        val refs = scene.members.filter { it.role == OverlayRole.REFERENCE }
        val perfs = scene.members.filter { it.role == OverlayRole.PERFORMANCE }
        assertEquals(4, perfs.size)
        val a4 = perfs.first { it.referenceIndex == 0 }
        val g4 = perfs.first { it.referenceIndex == 2 }
        val last = perfs.first { it.referenceIndex == 3 }
        val refA4 = refs.first { it.referenceIndex == 0 }
        val refG4 = refs.first { it.referenceIndex == 2 }
        val refE4 = refs.first { it.referenceIndex == 3 }
        assertNotNull(a4.centsVsTarget)
        assertTrue(a4.centsVsTarget!! >= 15.0, "cents=${a4.centsVsTarget}")
        assertTrue(a4.center.y > refA4.center.y)
        val g4Start = g4.center.x - g4.size.x / 2f
        val refG4Start = refG4.center.x - refG4.size.x / 2f
        assertTrue(g4Start > refG4Start + 0.02f, "late G4 should sit later on the time axis")
        assertEquals("F4", last.event?.noteName)
        assertTrue(last.center.y > refE4.center.y)
        // 实际时值按自身 duration 映射，不拉到参考长度以外的统一伸缩。
        val window = scene.windowEndMs.toFloat()
        val expectedG4Len = (400f / window) * scene.widthMeters
        assertTrue(abs(g4.size.x - expectedG4Len.coerceAtLeast(SpatialOverlay.MIN_LENGTH_M)) <= 0.02f)
        assertTrue(scene.members.any { it.role == OverlayRole.DEVIATION })
        assertNull(SpatialOverlay.selectionReadout(scene))
    }

    @Test
    fun missingNoteIsAGapAndKeepsReferenceWireframe() {
        val take = PracticeFragments.buildTake(
            takeId = "missing-fixture",
            events = listOf(
                ev(0, 400, 69, "A4"),
                ev(500, 900, 72, "C5"),
                ev(1000, 1400, 67, "G4"),
            ),
            rendition = PracticeFragments.Rendition.MISSING_TAKE,
        )
        val aligned = TakeComparator.align(take, reference)
        assertTrue(aligned.items.any { it.kind == AlignmentKind.MISSING })
        val scene = SpatialOverlay.layout(reference, current = aligned)
        assertEquals(4, scene.members.count { it.role == OverlayRole.REFERENCE })
        val gap = scene.members.filter { it.role == OverlayRole.MISSING }
        assertEquals(1, gap.size)
        assertEquals(OverlayStyle.GAP, gap.single().style)
        assertEquals(3, scene.members.count { it.role == OverlayRole.PERFORMANCE })
        assertEquals("E4", gap.single().target?.noteName)
    }

    @Test
    fun lowConfidenceBecomesDashedUnknownWithoutCents() {
        val take = PracticeFragments.buildTake(
            takeId = "unknown-fixture",
            events = listOf(
                ev(0, 400, 69, "A4"),
                ev(500, 900, 72, "C5", confidence = 0.2, stable = false),
                ev(1000, 1400, 67, "G4"),
                ev(1500, 1900, 64, "E4"),
            ),
        )
        val aligned = TakeComparator.align(take, reference)
        val scene = SpatialOverlay.layout(reference, current = aligned)
        val unknown = scene.members.filter { it.role == OverlayRole.UNKNOWN }
        assertEquals(1, unknown.size)
        assertEquals(OverlayStyle.DASHED, unknown.single().style)
        assertEquals(EvaluationPolicy.UNKNOWN_LABEL, unknown.single().label)
        assertNull(unknown.single().centsVsTarget)
        assertNull(unknown.single().timingErrorMs)
        val selected = scene.copy(selectedId = unknown.single().id)
        val text = SpatialOverlay.selectionReadout(selected)
        assertNotNull(text)
        assertTrue(text.contains("未知"))
        assertFalse(text.contains("音分 +") || text.contains("音分 -") || text.contains("音分 0"))
    }

    @Test
    fun midiOverlayOmitsAcousticCentsButKeepsTiming() {
        val take = PracticeFragments.buildTake(
            takeId = "midi-fixture",
            events = listOf(
                ev(80, 480, 69, "A4"),
                ev(500, 900, 72, "C5"),
                ev(1000, 1400, 67, "G4"),
                ev(1500, 1900, 64, "E4"),
            ),
            source = InputSource.MIDI_EVENTS,
        )
        val aligned = TakeComparator.align(take, reference)
        val scene = SpatialOverlay.layout(reference, current = aligned, source = InputSource.MIDI_EVENTS)
        val first = scene.members.first { it.role == OverlayRole.PERFORMANCE && it.referenceIndex == 0 }
        assertNull(first.centsVsTarget)
        assertNotNull(first.timingErrorMs)
        val selected = scene.copy(selectedId = first.id)
        val text = SpatialOverlay.selectionReadout(selected)
        assertNotNull(text)
        assertTrue(text.contains("不发布音分") || text.contains("不评价声学音准"))
        assertTrue(text.contains("时差"))
        val audition = SpatialOverlay.auditionPlan(selected, OverlayAuditionSide.PERFORMANCE)
        assertFalse(audition.available)
        assertTrue(audition.reason!!.contains("MIDI"))
    }

    @Test
    fun retakeKeepsPreviousContourAndSelectionShowsNumbers() {
        val first = TakeComparator.align(
            PracticeFragments.buildTake(
                "first",
                listOf(
                    ev(0, 400, 69, "A4", hz = Tuning.hzFromMidi(69.25)),
                    ev(500, 900, 72, "C5"),
                    ev(1080, 1480, 67, "G4"),
                    ev(1500, 1900, 65, "F4"),
                ),
            ),
            reference,
        )
        val retake = TakeComparator.align(
            PracticeFragments.buildTake(
                "retake",
                listOf(
                    ev(0, 400, 69, "A4"),
                    ev(500, 900, 72, "C5"),
                    ev(1012, 1412, 67, "G4"),
                    ev(1500, 1900, 64, "E4"),
                ),
            ),
            reference,
        )
        val scene = SpatialOverlay.layout(reference, current = retake, previous = first)
        assertTrue(scene.members.any { it.role == OverlayRole.PREVIOUS })
        assertEquals(4, scene.members.count { it.role == OverlayRole.PREVIOUS })
        assertTrue(scene.members.filter { it.role == OverlayRole.PREVIOUS }.all { !it.selectable })
        val perf = scene.members.first { it.role == OverlayRole.PERFORMANCE && it.referenceIndex == 0 }
        assertNull(SpatialOverlay.selectionReadout(scene, perf))
        val selected = scene.copy(selectedId = perf.id)
        val text = SpatialOverlay.selectionReadout(selected, perf)
        assertNotNull(text)
        assertTrue(text.contains("音分") || text.contains("时差") || text.contains("演奏"))
    }

    @Test
    fun referenceAuditionIsLabeledPrerecordedAndUserWavActualIsUnavailable() {
        val take = PracticeFragments.buildTake(
            takeId = "user",
            events = listOf(
                ev(0, 400, 69, "A4"),
                ev(500, 900, 72, "C5"),
                ev(1000, 1400, 67, "G4"),
                ev(1500, 1900, 64, "E4"),
            ),
            source = InputSource.USER_WAV_FILE,
        )
        val aligned = TakeComparator.align(take, reference)
        val base = SpatialOverlay.layout(reference, current = aligned, source = InputSource.USER_WAV_FILE)
        val member = base.members.first { it.role == OverlayRole.PERFORMANCE }
        val selected = base.copy(selectedId = member.id)
        val refPlan = SpatialOverlay.auditionPlan(selected, OverlayAuditionSide.REFERENCE)
        assertTrue(refPlan.available)
        assertTrue(refPlan.prerecorded)
        assertEquals(EvaluationPolicy.PRERECORDED_BANNER, refPlan.banner)
        assertNotNull(refPlan.clip)
        assertTrue(refPlan.clip!!.label.contains("预录"))
        val perfPlan = SpatialOverlay.auditionPlan(selected, OverlayAuditionSide.PERFORMANCE)
        assertFalse(perfPlan.available)
        assertTrue(perfPlan.reason!!.contains("未保留原音频切片"))
    }

    @Test
    fun viewCyclesThroughOrthogonalModes() {
        assertEquals(OverlayViewKind.ORTHO_PITCH, SpatialOverlay.cycleView(OverlayViewKind.PERSPECTIVE))
        assertEquals(OverlayViewKind.ORTHO_RHYTHM, SpatialOverlay.cycleView(OverlayViewKind.ORTHO_PITCH))
        assertEquals(OverlayViewKind.PERSPECTIVE, SpatialOverlay.cycleView(OverlayViewKind.ORTHO_RHYTHM))
        val rot = SpatialOverlay.viewRotationDeg(OverlayViewKind.ORTHO_RHYTHM)
        assertEquals(-90f, rot.x)
        assertEquals(0f, SpatialOverlay.viewRotationDeg(OverlayViewKind.ORTHO_PITCH).x)
        assertTrue(SpatialOverlay.viewLabel(OverlayViewKind.ORTHO_RHYTHM).contains("俯视"))
    }

    @Test
    fun overlayHitSelectsMemberAndDoesNotFollowHand() {
        val scene = SpatialOverlay.layout(reference)
        val id = scene.members.first { it.role == OverlayRole.REFERENCE }.id
        val result = StageInteraction.onHit(id, 0f, 0f, pressed = true, frame = AnalysisFrame.silent(), overlay = scene)
        assertNotNull(result)
        assertEquals(InteractionProbe.Kind.OVERLAY, result.first.kind)
        assertEquals(id, result.first.overlayId)
        assertFalse(result.second)
        assertNotNull(result.first.overlayReadout)
        assertTrue(result.first.overlayReadout!!.contains("参考"))
    }

    @Test
    fun lateNoteDoesNotShareReferenceStartX() {
        val onTime = ev(1000, 1400, 67, "G4")
        val late = ev(1080, 1480, 67, "G4")
        val a = SpatialOverlay.layout(
            reference,
            current = TakeComparator.align(PracticeFragments.buildTake("a", fourWith(onTime)), reference),
        )
        val b = SpatialOverlay.layout(
            reference,
            current = TakeComparator.align(PracticeFragments.buildTake("b", fourWith(late)), reference),
        )
        val ax = a.members.first { it.role == OverlayRole.PERFORMANCE && it.referenceIndex == 2 }.center.x
        val bx = b.members.first { it.role == OverlayRole.PERFORMANCE && it.referenceIndex == 2 }.center.x
        assertTrue(bx > ax, "late=$bx onTime=$ax")
    }

    private fun fourWith(third: NoteEvent): List<NoteEvent> = listOf(
        ev(0, 400, 69, "A4"),
        ev(500, 900, 72, "C5"),
        third,
        ev(1500, 1900, 64, "E4"),
    )

    private fun ev(
        startMs: Long,
        endMs: Long,
        midi: Int,
        name: String,
        hz: Double? = Tuning.hzFromMidi(midi.toDouble()),
        confidence: Double? = 0.9,
        stable: Boolean = true,
    ): NoteEvent = NoteEvent(
        startMs = startMs,
        endMs = endMs,
        pitchHz = hz,
        midi = midi,
        noteName = name,
        centsVsNearest = hz?.let { Tuning.centsFromNearest(it) },
        pitchConfidence = confidence,
        stable = stable,
        glideSuspected = false,
    )
}
