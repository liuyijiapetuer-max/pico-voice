package com.spatialcoach.soundarchitecture.audio

import kotlin.math.abs
import kotlin.math.ln

/**
 * 把单旋律 [AnalysisFrame] 序列切成音符事件。
 *
 * 分段依据：静音间隙、以及确认后的换音。短帧抖动（不足 [confirmFrames] 或短于 [minNoteMs]）会被抑制。
 * 慢滑音会并成一条事件并标记 [NoteEvent.glideSuspected]，不伪装成一串确定音。
 * 只承诺单旋律；复调和自动跟谱都不做。
 */
class NoteSegmenter(
    val minNoteMs: Long = 70L,
    val minSilenceMs: Long = 50L,
    val confirmFrames: Int = 3,
    val centsChangeSplit: Double = 60.0,
    val glideRangeCents: Double = 80.0,
) {
    fun segment(frames: List<AnalysisFrame>): List<NoteEvent> {
        if (frames.isEmpty()) return emptyList()
        val events = ArrayList<NoteEvent>()
        var note = ArrayList<AnalysisFrame>()
        var startBuf = ArrayList<AnalysisFrame>()
        var changeBuf = ArrayList<AnalysisFrame>()
        var silenceBuf = ArrayList<AnalysisFrame>()

        fun voiced(frame: AnalysisFrame): Boolean =
            frame.pitchHz != null && frame.pitchConfidence != null

        fun flushNote() {
            val event = toEvent(note)
            if (event != null) events += event
            note = ArrayList()
            changeBuf = ArrayList()
            silenceBuf = ArrayList()
        }

        for (frame in frames) {
            if (note.isEmpty()) {
                if (voiced(frame)) {
                    startBuf += frame
                    if (startBuf.size >= confirmFrames && consistent(startBuf)) {
                        note = ArrayList(startBuf)
                        startBuf = ArrayList()
                    } else if (!consistent(startBuf)) {
                        val last = startBuf.last()
                        startBuf = arrayListOf(last)
                    }
                } else {
                    startBuf = ArrayList()
                }
                continue
            }

            if (voiced(frame) && sameRegion(note, frame)) {
                note += frame
                changeBuf = ArrayList()
                silenceBuf = ArrayList()
                continue
            }

            if (voiced(frame)) {
                changeBuf += frame
                silenceBuf = ArrayList()
                if (changeBuf.size >= confirmFrames && consistent(changeBuf)) {
                    flushNote()
                    note = ArrayList(changeBuf)
                    changeBuf = ArrayList()
                }
                continue
            }

            changeBuf = ArrayList()
            silenceBuf += frame
            if (spanMs(silenceBuf) >= minSilenceMs) {
                flushNote()
            }
        }
        flushNote()
        return events
    }

    private fun toEvent(note: List<AnalysisFrame>): NoteEvent? {
        val voiced = note.filter { it.pitchHz != null }
        if (voiced.isEmpty()) return null
        val startMs = voiced.first().timestampMs
        val endMs = note.last().timestampMs.coerceAtLeast(startMs)
        val duration = endMs - startMs + impliedFrameMs(note)
        if (duration < minNoteMs) return null
        val hz = median(voiced.map { it.pitchHz!! })
        val confidence = median(voiced.map { it.pitchConfidence ?: 0.0 })
        val midi = Tuning.nearestMidi(hz)
        val cents = Tuning.centsFromNearest(hz)
        val minHz = voiced.minOf { it.pitchHz!! }
        val maxHz = voiced.maxOf { it.pitchHz!! }
        val rangeCents = abs(hzToCents(minHz, maxHz))
        val midis = voiced.map { it.midi ?: Tuning.nearestMidi(it.pitchHz!!) }.toSet()
        val glide = rangeCents >= glideRangeCents && midis.size >= 2
        val stable = !glide && confidence >= AnalysisEngine.MIN_PITCH_CONFIDENCE
        return NoteEvent(
            startMs = startMs,
            endMs = startMs + duration,
            pitchHz = hz,
            midi = midi,
            noteName = Tuning.noteName(midi),
            centsVsNearest = cents,
            pitchConfidence = confidence,
            stable = stable,
            glideSuspected = glide,
        )
    }

    private fun impliedFrameMs(note: List<AnalysisFrame>): Long {
        if (note.size >= 2) {
            val dt = note[1].timestampMs - note[0].timestampMs
            if (dt > 0) return dt
        }
        val sr = note.first().sampleRate
        return if (sr > 0) (512L * 1000L) / sr else 12L
    }

    private fun sameRegion(note: List<AnalysisFrame>, frame: AnalysisFrame): Boolean {
        val hz = frame.pitchHz ?: return false
        val center = centerHz(note) ?: return false
        return abs(hzToCents(center, hz)) < centsChangeSplit
    }

    private fun centerHz(note: List<AnalysisFrame>): Double? {
        val voiced = note.mapNotNull { it.pitchHz }
        if (voiced.isEmpty()) return null
        val tail = voiced.takeLast(8)
        return median(tail)
    }

    private fun consistent(frames: List<AnalysisFrame>): Boolean {
        val hz = frames.mapNotNull { it.pitchHz }
        if (hz.size < frames.size) return false
        val mid = median(hz)
        return hz.all { abs(hzToCents(mid, it)) < centsChangeSplit }
    }

    private fun spanMs(frames: List<AnalysisFrame>): Long {
        if (frames.isEmpty()) return 0L
        return frames.last().timestampMs - frames.first().timestampMs + impliedFrameMs(frames)
    }

    companion object {
        fun hzToCents(fromHz: Double, toHz: Double): Double =
            1200.0 * (ln(toHz / fromHz) / ln(2.0))

        fun median(values: List<Double>): Double {
            val s = values.sorted()
            val n = s.size
            if (n == 0) return 0.0
            return if (n % 2 == 1) s[n / 2] else 0.5 * (s[n / 2 - 1] + s[n / 2])
        }
    }
}
