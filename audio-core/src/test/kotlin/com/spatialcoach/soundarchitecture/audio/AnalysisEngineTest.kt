package com.spatialcoach.soundarchitecture.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalysisEngineTest {

    private val sampleRate = 44_100
    private val fftSize = 2048
    private val engine = AnalysisEngine(sampleRate = sampleRate, fftSize = fftSize, hopSize = fftSize)

    @Test
    fun a4SineIsWithinFiveCents() {
        val samples = Signals.sine(Tuning.A4_HZ, sampleRate, fftSize, 0.5)
        val frame = engine.analyze(samples)
        assertNotNull(frame.pitchHz)
        assertEquals("A4", frame.noteName)
        assertTrue(abs(frame.cents!!) <= 5.0, "cents=${frame.cents} hz=${frame.pitchHz}")
        assertTrue(abs(frame.pitchHz!! - Tuning.A4_HZ) < 2.0, "hz=${frame.pitchHz}")
    }

    @Test
    fun c4SineMapsToC4() {
        val hz = Tuning.hzFromMidi(60.0)
        val frame = engine.analyze(Signals.sine(hz, sampleRate, fftSize, 0.5))
        assertEquals("C4", frame.noteName)
        assertTrue(abs(frame.cents ?: 99.0) <= 8.0, "cents=${frame.cents}")
    }

    @Test
    fun silenceHasNoPitchAndLowLevel() {
        val frame = engine.analyze(Signals.silence(fftSize))
        assertNull(frame.pitchHz)
        assertTrue(frame.rms < 0.002)
        assertTrue(frame.dbfs <= -50.0)
    }

    @Test
    fun spectrumPeakNearOneKilohertz() {
        val frame = engine.analyze(Signals.sine(1000.0, sampleRate, fftSize, 0.6))
        val peakHz = Fft.peakFrequency(frame.spectrum, sampleRate)
        assertTrue(abs(peakHz - 1000.0) < 40.0, "peakHz=$peakHz")
    }

    @Test
    fun louderSineHasHigherRms() {
        val loud = DynamicsTracker.measure(Signals.sine(440.0, sampleRate, fftSize, 0.5))
        val quiet = DynamicsTracker.measure(Signals.sine(440.0, sampleRate, fftSize, 0.1))
        assertTrue(loud.rms / quiet.rms in 4.0..6.0, "ratio=${loud.rms / quiet.rms}")
        assertTrue(loud.dbfs > quiet.dbfs + 10.0)
    }

    @Test
    fun twoImpulsesGiveHalfSecondOnsetInterval() {
        val streamEngine = AnalysisEngine(sampleRate = sampleRate, fftSize = fftSize, hopSize = 512)
        val pcm = Signals.impulses(sampleRate, count = 3, intervalMs = 500L, amplitude = 0.95f)
        val frames = streamEngine.push(pcm)
        val intervals = frames.mapNotNull { it.onsetIntervalMs }
        assertTrue(intervals.isNotEmpty(), "no onset intervals, onsets=${frames.count { it.onset }}")
        assertTrue(intervals.any { abs(it - 500L) <= 80L }, "intervals=$intervals")
    }

    @Test
    fun practiceSentenceMentionsSharpnessWhenCentsAreHigh() {
        val sharp = AnalysisFrame.silent(bins = fftSize / 2).copy(
            pitchHz = Tuning.hzFromMidi(69.3),
            midi = 69,
            noteName = "A4",
            cents = 30.0,
            rms = 0.2,
            peak = 0.3,
            dbfs = -14.0,
        )
        val cue = PracticeCoach.cue(sharp)
        assertTrue("偏高" in cue.pitchLabel)
        assertTrue("低一点" in cue.sentence || "偏高" in cue.sentence)
    }

    @Test
    fun wavRoundTripPreservesSineFrequency() {
        val original = Signals.sine(440.0, sampleRate, fftSize, 0.5)
        val encoded = PcmWav.encodeMono16(original, sampleRate)
        val (sr, decoded) = PcmWav.decodeMono16(encoded)
        assertEquals(sampleRate, sr)
        val frame = engine.analyze(decoded)
        assertEquals("A4", frame.noteName)
    }
}
