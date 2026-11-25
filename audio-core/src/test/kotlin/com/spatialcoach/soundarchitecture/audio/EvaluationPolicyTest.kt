package com.spatialcoach.soundarchitecture.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EvaluationPolicyTest {

    @Test
    fun midiDoesNotPublishAcousticCents() {
        val event = note(midi = 69, name = "A4", centsNearest = 18.0, confidence = 0.95)
        val published = EvaluationPolicy.publishedCentsVsTarget(InputSource.MIDI_EVENTS, event, 18.0)
        assertNull(published)
        assertFalse(EvaluationPolicy.canEvaluateAcousticIntonation(InputSource.MIDI_EVENTS))
        assertTrue(EvaluationPolicy.canEvaluateNoteIdentityAndTiming(InputSource.MIDI_EVENTS))
        val timing = EvaluationPolicy.publishedTimingMs(InputSource.MIDI_EVENTS, event, 80L)
        assertEquals(80L, timing)
        assertTrue(EvaluationPolicy.scopeLine(InputSource.MIDI_EVENTS).contains("不评价声学音准"))
    }

    @Test
    fun microphoneAndFileMayPublishCentsWhenConfident() {
        val event = note(midi = 69, name = "A4", centsNearest = 12.0, confidence = 0.9)
        assertEquals(12.0, EvaluationPolicy.publishedCentsVsTarget(InputSource.MICROPHONE_MONO, event, 12.0))
        assertEquals(12.0, EvaluationPolicy.publishedCentsVsTarget(InputSource.USER_WAV_FILE, event, 12.0))
        assertEquals(12.0, EvaluationPolicy.publishedCentsVsTarget(InputSource.FILE_SYNTHETIC, event, 12.0))
        assertTrue(EvaluationPolicy.isPrerecorded(InputSource.FILE_SYNTHETIC))
        assertTrue(EvaluationPolicy.isPrerecorded(InputSource.USER_WAV_FILE))
        assertFalse(EvaluationPolicy.isPrerecorded(InputSource.MICROPHONE_MONO))
        assertEquals(EvaluationPolicy.PRERECORDED_BANNER, EvaluationPolicy.sourceBanner(InputSource.FILE_SYNTHETIC))
    }

    @Test
    fun lowConfidenceNeverPublishesCentsOrTiming() {
        val weak = note(midi = 69, name = "A4", centsNearest = 30.0, confidence = 0.2, stable = false)
        assertTrue(EvaluationPolicy.isLowConfidence(weak))
        assertNull(EvaluationPolicy.publishedCentsVsTarget(InputSource.USER_WAV_FILE, weak, 30.0))
        assertNull(EvaluationPolicy.publishedTimingMs(InputSource.USER_WAV_FILE, weak, 90L))
    }

    @Test
    fun importAndPermissionMessagesStayHonest() {
        val import = EvaluationPolicy.importFailureMessage("格式不支持。")
        assertTrue(import.startsWith("导入失败："))
        assertTrue(import.contains("没有回退到合成示范"))
        assertTrue(import.contains("不会把预录标成实时"))
        assertTrue(EvaluationPolicy.PERMISSION_DENIED.contains("录音权限未授予"))
        assertTrue(EvaluationPolicy.PERMISSION_DENIED.contains("没有改用未标明的预录冒充实时"))
    }

    private fun note(
        midi: Int,
        name: String,
        centsNearest: Double?,
        confidence: Double?,
        stable: Boolean = true,
    ): NoteEvent = NoteEvent(
        startMs = 0,
        endMs = 400,
        pitchHz = Tuning.hzFromMidi(midi.toDouble()),
        midi = midi,
        noteName = name,
        centsVsNearest = centsNearest,
        pitchConfidence = confidence,
        stable = stable,
        glideSuspected = false,
    )
}
