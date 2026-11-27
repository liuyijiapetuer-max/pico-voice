package com.spatialcoach.soundarchitecture.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveInputTest {

    private val sr = PracticeFragments.SAMPLE_RATE

    @Test
    fun midiNotesKeepTimingAndDoNotPublishAcousticCents() {
        val messages = listOf(
            noteOn(80, 69),
            noteOff(480, 69),
            noteOn(500, 72),
            noteOff(900, 72),
            noteOn(1000, 67),
            noteOff(1400, 67),
            noteOn(1500, 64),
            noteOff(1900, 64),
        )
        val result = MidiTakeFactory.fromMessages(messages, "midi-1", "test-keyboard", true)
        val take = assertIs<LiveCaptureResult.Ok>(result).take
        assertEquals(InputSource.MIDI_EVENTS, take.source)
        assertNull(take.audio)
        assertEquals(4, take.events.size)
        assertEquals(80L, take.events[0].startMs)
        assertEquals(69, take.events[0].midi)
        assertNull(take.events[0].centsVsNearest)
        assertNull(take.events[0].pitchConfidence)
        assertNull(EvaluationPolicy.publishedCentsVsTarget(take.source, take.events[0], 25.0))
        assertEquals(80L, EvaluationPolicy.publishedTimingMs(take.source, take.events[0], 80L))
    }

    @Test
    fun midiOverlappingNotesAreFlattenedMonophonicallyAndLeadingSilenceIsKept() {
        val messages = listOf(
            noteOn(100, 69),
            noteOn(250, 72),
            noteOff(400, 69),
            noteOff(600, 72),
        )
        val events = MidiNoteParser.monophonicEvents(messages, sessionEndMs = 600)
        assertEquals(2, events.size)
        assertEquals(100L, events[0].startMs)
        assertEquals(250L, events[0].endMs)
        assertEquals(69, events[0].midi)
        assertEquals(250L, events[1].startMs)
        assertEquals(600L, events[1].endMs)
        assertEquals(72, events[1].midi)
    }

    @Test
    fun midiRunningStatusAndNoteOnVelocityZeroAreNoteOff() {
        val packed = byteArrayOf(
            0x90.toByte(), 69, 100,
            72, 90,
            69, 0,
            72, 0,
        )
        val events = MidiNoteParser.monophonicEvents(
            listOf(TimedMidiMessage(0L, packed)),
            sessionEndMs = 0L,
        )
        assertTrue(events.isEmpty())
        val spaced = listOf(
            TimedMidiMessage(0L, byteArrayOf(0x90.toByte(), 69, 100)),
            TimedMidiMessage(400L, byteArrayOf(69, 0)),
        )
        val kept = MidiNoteParser.monophonicEvents(spaced, sessionEndMs = 400L)
        assertEquals(1, kept.size)
        assertEquals(69, kept[0].midi)
        assertEquals(0L, kept[0].startMs)
        assertEquals(400L, kept[0].endMs)
    }

    @Test
    fun emptyMidiOrUnconfirmedReferenceFailsWithoutFallbackTake() {
        val empty = MidiTakeFactory.fromMessages(emptyList(), "midi-empty", "none", true)
        val failed = assertIs<LiveCaptureResult.Failed>(empty)
        assertTrue("未采集到 MIDI" in failed.reason, failed.reason)
        assertTrue("未改用未标明的预录" in failed.reason, failed.reason)
        val unconfirmed = MidiTakeFactory.fromMessages(
            listOf(noteOn(0, 69), noteOff(400, 69)),
            "midi-x",
            "none",
            referenceConfirmed = false,
        )
        val rejected = assertIs<LiveCaptureResult.Failed>(unconfirmed)
        assertTrue("未确认" in rejected.reason, rejected.reason)
    }

    @Test
    fun microphonePcmKeepsOriginalAudioAndMayPublishCents() {
        val samples = PracticeFragments.render(PracticeFragments.Rendition.REFERENCE)
        val result = MicrophoneTakeFactory.fromPcm(samples, sr, "mic-1", "android-mic", true)
        val take = assertIs<LiveCaptureResult.Ok>(result).take
        assertEquals(InputSource.MICROPHONE_MONO, take.source)
        assertFalse(EvaluationPolicy.isPrerecorded(take.source))
        assertNotNull(take.audio)
        assertEquals(samples.size, take.audio!!.samples.size)
        assertTrue(take.events.isNotEmpty())
        val ev = take.events.first { it.stable }
        assertNotNull(EvaluationPolicy.publishedCentsVsTarget(take.source, ev, ev.centsVsNearest ?: 0.0))
        val aligned = TakeComparator.alignWithProcessedReference(take)
        val scene = SpatialOverlay.layout(
            PracticeFragments.REFERENCE_V1,
            current = aligned,
            source = InputSource.MICROPHONE_MONO,
        )
        val member = scene.members.first { it.role == OverlayRole.PERFORMANCE }
        val plan = SpatialOverlay.auditionPlan(scene.copy(selectedId = member.id), OverlayAuditionSide.PERFORMANCE)
        assertTrue(plan.available, plan.reason ?: "ok")
        assertFalse(plan.prerecorded)
        assertNotNull(plan.clip)
        assertTrue("原音频切片" in plan.clip!!.label, plan.clip!!.label)
        assertFalse("按分析音高合成" in plan.clip!!.label)
    }

    @Test
    fun emptyMicrophonePcmFailsHonestly() {
        val failed = assertIs<LiveCaptureResult.Failed>(
            MicrophoneTakeFactory.fromPcm(FloatArray(0), sr, "mic-empty", "android-mic", true),
        )
        assertTrue("未采集到可用的麦克风音频" in failed.reason, failed.reason)
    }

    @Test
    fun coordinatorRejectsMissingPermissionAndDeviceWithoutFakeTake() {
        val mic = LiveCaptureCoordinator()
        assertFalse(mic.begin(LiveInputKind.MICROPHONE_MONO, permissionGranted = false, deviceAvailable = true))
        assertEquals(LiveCapturePhase.FAILED, mic.state.phase)
        assertEquals(EvaluationPolicy.PERMISSION_DENIED, mic.state.lastFailure)
        assertNull(mic.state.take)
        assertTrue("没有改用未标明的预录冒充实时" in mic.state.status)

        val midi = LiveCaptureCoordinator()
        assertFalse(midi.begin(LiveInputKind.MIDI_EVENTS, permissionGranted = true, deviceAvailable = false))
        assertEquals(LiveCapturePhase.FAILED, midi.state.phase)
        assertEquals(EvaluationPolicy.DEVICE_MISSING, midi.state.lastFailure)
        assertNull(midi.state.take)
        assertTrue("未改用未标明的预录冒充实时" in midi.state.status)
    }

    @Test
    fun coordinatorCompletesMidiTakeAndRefusesASecondStartUntilStopped() {
        val coordinator = LiveCaptureCoordinator()
        assertTrue(coordinator.begin(LiveInputKind.MIDI_EVENTS, permissionGranted = true, deviceAvailable = true))
        assertTrue(coordinator.isRecording)
        assertFalse(coordinator.begin(LiveInputKind.MIDI_EVENTS, permissionGranted = true, deviceAvailable = true))
        assertEquals(EvaluationPolicy.CAPTURE_BUSY, coordinator.state.status)
        val take = assertIs<LiveCaptureResult.Ok>(
            MidiTakeFactory.fromMessages(listOf(noteOn(0, 69), noteOff(400, 69)), "m", "kb", true),
        ).take
        assertTrue(coordinator.complete(LiveCaptureResult.Ok(take)))
        assertEquals(LiveCapturePhase.COMPLETED, coordinator.state.phase)
        assertEquals(take, coordinator.state.take)
        assertTrue("不会写入用户第一遍" in coordinator.state.status)
    }

    @Test
    fun userWavImportKeepsOriginalWaveformForActualAudition() {
        val original = FloatArray(sr) { 0f }
        for (i in 8_000 until 12_000) {
            original[i] = if (i % 2 == 0) 0.85f else -0.85f
        }
        val startMs = (8_000L * 1000L) / sr
        val endMs = (12_000L * 1000L) / sr
        val audio = PcmClip(original, sr, "用户导入原音频（square.wav，不是实时采集）", "square.wav")
        val take = PracticeFragments.buildTake(
            takeId = "user-square",
            events = listOf(
                NoteEvent(
                    startMs = startMs,
                    endMs = endMs,
                    pitchHz = Tuning.hzFromMidi(69.0),
                    midi = 69,
                    noteName = "A4",
                    centsVsNearest = 0.0,
                    pitchConfidence = 0.9,
                    stable = true,
                    glideSuspected = false,
                ),
            ),
            source = InputSource.USER_WAV_FILE,
            sourceName = "square.wav",
            audioDurationMs = 1000L,
            timeWindowStartMs = 0L,
            timeWindowEndMs = 1000L,
            audio = audio,
        )
        val aligned = TakeComparator.align(take, PracticeFragments.REFERENCE_V1)
        val scene = SpatialOverlay.layout(
            PracticeFragments.REFERENCE_V1,
            current = aligned,
            source = InputSource.USER_WAV_FILE,
        )
        assertEquals(take, scene.currentTake)
        val member = scene.members.first { it.role == OverlayRole.PERFORMANCE }
        val plan = SpatialOverlay.auditionPlan(scene.copy(selectedId = member.id), OverlayAuditionSide.PERFORMANCE)
        assertTrue(plan.available, plan.reason ?: "ok")
        assertTrue(plan.prerecorded)
        val clip = assertNotNull(plan.clip)
        assertTrue("原音频切片" in clip.label, clip.label)
        assertFalse("按分析音高合成" in clip.label)
        assertTrue(clip.samples.any { abs(it) > 0.7f })
        val flips = clip.samples.toList().zipWithNext().count { (a, b) -> a * b < 0f }
        assertTrue(flips > clip.samples.size / 3, "flips=$flips size=${clip.samples.size}")
        val expected = audio.sliceWindow(startMs, endMs)
        assertNotNull(expected)
        assertEquals(expected!!.samples.size, clip.samples.size)
        assertTrue(expected.samples.indices.all { i -> abs(expected.samples[i] - clip.samples[i]) < 1e-6f })
    }

    @Test
    fun importedOfficialWavBytesRetainFullOriginalAudio() {
        val pcm = PracticeFragments.render(PracticeFragments.Rendition.FIRST_TAKE)
        val bytes = PcmWav.encodeMono16(pcm, sr)
        val result = UserTakeFactory.fromWavBytes(bytes, "first", "first.wav", true)
        val take = assertIs<UserTakeLoadResult.Ok>(result).take
        assertNotNull(take.audio)
        assertEquals("first.wav", take.audio!!.sourceName)
        assertEquals(pcm.size, take.audio!!.samples.size)
    }

    private fun noteOn(timeMs: Long, midi: Int, velocity: Int = 100): TimedMidiMessage =
        TimedMidiMessage(timeMs, byteArrayOf(0x90.toByte(), midi.toByte(), velocity.toByte()))

    private fun noteOff(timeMs: Long, midi: Int): TimedMidiMessage =
        TimedMidiMessage(timeMs, byteArrayOf(0x80.toByte(), midi.toByte(), 0))
}
