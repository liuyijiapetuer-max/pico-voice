package com.spatialcoach.soundarchitecture.audio

import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StageMappingTest {

    @Test
    fun a4RoundTripsThroughY() {
        val y = StageMapping.yFromMidi(69.0)
        assertTrue(abs(StageMapping.midiFromY(y) - 69.0) <= 0.05, "y=$y midi=${StageMapping.midiFromY(y)}")
    }

    @Test
    fun twentyFiveCentsMapsRightAndBack() {
        val x = StageMapping.xFromCents(25.0)
        assertTrue(x > 0f, "x=$x")
        assertTrue(abs(StageMapping.centsFromX(x) - 25.0) <= 0.2, "cents=${StageMapping.centsFromX(x)}")
        val left = StageMapping.xFromCents(-25.0)
        assertTrue(left < 0f)
        assertTrue(abs(StageMapping.centsFromX(left) + 25.0) <= 0.2)
    }

    @Test
    fun probeAtA4OnRulerReadsA4AndNearZeroCents() {
        val y = StageMapping.yFromMidi(69.0)
        val result = StageInteraction.onHit("SaPitchRuler", 0f, y, pressed = false, frame = AnalysisFrame.silent())
        assertNotNull(result)
        val (probe, follow) = result
        assertEquals(InteractionProbe.Kind.RULER, probe.kind)
        assertEquals("A4", probe.noteName)
        assertFalse(follow)
        assertNotNull(probe.cents)
        assertTrue(abs(probe.cents!!) <= 1.0, "cents=${probe.cents}")
    }

    @Test
    fun probeTwentyFiveCentsSharpStaysA4AndPositive() {
        val hz = Tuning.A4_HZ * 2.0.pow(25.0 / 1200.0)
        val midi = Tuning.midiFromHz(hz)
        val y = StageMapping.yFromMidi(midi)
        val result = StageInteraction.onHit("SaPitchRulerHit", 0f, y, pressed = false, frame = AnalysisFrame.silent())
        assertNotNull(result)
        val probe = result.first
        assertEquals("A4", probe.noteName)
        assertNotNull(probe.cents)
        assertTrue(probe.cents!! > 0.0, "cents=${probe.cents}")
        assertTrue(abs(probe.cents!! - 25.0) <= 1.0, "cents=${probe.cents}")
    }

    @Test
    fun currentMarkerFollowsHandOnlyWhenPressedAndVoiced() {
        val y = StageMapping.yFromMidi(69.0)
        val voiced = AnalysisFrame.silent().copy(
            pitchHz = Tuning.A4_HZ,
            midi = 69,
            noteName = "A4",
            cents = 0.0,
            rms = 0.2,
        )
        val dragging = StageInteraction.onHit("SaCurrentPitch", 0.1f, y, pressed = true, frame = voiced)
        assertNotNull(dragging)
        assertTrue(dragging.second)
        assertEquals(InteractionProbe.Kind.RULER, dragging.first.kind)
        assertNotNull(dragging.first.followX)
        assertNotNull(dragging.first.followY)

        val released = StageInteraction.onHit("SaCurrentPitch", 0.1f, y, pressed = false, frame = voiced)
        assertNotNull(released)
        assertFalse(released.second)

        val silent = StageInteraction.onHit("SaPitchRuler", 0.1f, y, pressed = true, frame = AnalysisFrame.silent())
        assertNotNull(silent)
        assertFalse(silent.second)
    }

    @Test
    fun fanHitReportsBandAndDoesNotFollow() {
        val frame = AnalysisFrame.silent()
        val result = StageInteraction.onHit("SaBand_3", 0f, 0f, pressed = true, frame = frame)
        assertNotNull(result)
        val (probe, follow) = result
        assertEquals(InteractionProbe.Kind.FAN, probe.kind)
        assertEquals(3, probe.bandIndex)
        assertFalse(follow)
        assertNotNull(probe.bandHzLow)
        assertNotNull(probe.bandHzHigh)
        assertTrue(probe.bandHzHigh!! > probe.bandHzLow!!)
        assertTrue("第 4 带" in probe.professionalText())
        assertTrue("扇面" in probe.practiceText())
    }

    @Test
    fun unknownEntityIsIgnored() {
        assertNull(StageInteraction.onHit("SaFloor", 0f, 0f, pressed = true, frame = AnalysisFrame.silent()))
    }

    @Test
    fun modeToggleIsSingleSwitch() {
        // 文档约定：同一帧读数与可视化都读 AnalysisBus.mode；此处只锁映射本身不依赖模式。
        val y = StageMapping.yFromMidi(69.0)
        val probe = StageInteraction.onHit("SaPitchRuler", 0f, y, pressed = false, frame = AnalysisFrame.silent())!!.first
        val pro = probe.readout(ViewMode.PROFESSIONAL)
        val practice = probe.readout(ViewMode.PRACTICE)
        assertTrue("A4" in pro && "Hz" in pro)
        assertTrue("对准" in practice && "A4" in practice)
        assertTrue(pro != practice)
    }
}
