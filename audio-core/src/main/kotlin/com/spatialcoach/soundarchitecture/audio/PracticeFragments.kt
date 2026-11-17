package com.spatialcoach.soundarchitecture.audio

import kotlin.math.pow

/**
 * 内置、可授权的合成多音符练习片段。片段 id、目标表、BPM 与节拍原点固定。
 * 只用于文件回放 / 合成示范，不是用户文件，也不是真麦采集，不能当作用户重奏成果。
 */
object PracticeFragments {
    const val ID_V1 = "practice-melody-v1"
    const val SAMPLE_RATE = 44_100

    val RHYTHM_V1 = RhythmReference(bpm = 120.0, originMs = 0L)

    val TARGETS_V1: List<TargetNote> = listOf(
        TargetNote(startMs = 0, durationMs = 400, midi = 69),    // A4 第1拍
        TargetNote(startMs = 500, durationMs = 400, midi = 72),  // C5 第2拍
        TargetNote(startMs = 1000, durationMs = 400, midi = 67), // G4 第3拍
        TargetNote(startMs = 1500, durationMs = 400, midi = 64), // E4 第4拍
    )

    val REFERENCE_V1 = PracticeReference(
        fragmentId = ID_V1,
        rhythm = RHYTHM_V1,
        targets = TARGETS_V1,
        description = "合成练习片段 practice-melody-v1：120 BPM，原点 0 ms，四拍 A4–C5–G4–E4。可授权合成正弦，不含商业歌曲。当前只支持这一份固定练习谱，不支持其他曲目。",
    )

    enum class Rendition(val id: String, val label: String) {
        REFERENCE("reference", "内置参考示范（对准、准点）"),
        FIRST_TAKE("first", "内置偏差示范（不是用户第一遍）"),
        RETAKE("retake", "内置改善示范（不是用户重奏）"),
    }

    data class SynthNote(
        val startMs: Long,
        val durationMs: Long,
        val frequencyHz: Double,
        val amplitude: Double = 0.42,
    )

    fun notesFor(rendition: Rendition): List<SynthNote> = when (rendition) {
        Rendition.REFERENCE -> TARGETS_V1.map { it.toSynth(0.0, 0L) }
        Rendition.FIRST_TAKE -> listOf(
            TARGETS_V1[0].toSynth(cents = 25.0, delayMs = 0L),
            TARGETS_V1[1].toSynth(cents = 0.0, delayMs = 0L),
            TARGETS_V1[2].toSynth(cents = 0.0, delayMs = 80L),
            // 第4拍弹成 F4，作为明确错音，而不是把后续音符整体错位。
            SynthNote(startMs = 1500, durationMs = 400, frequencyHz = Tuning.hzFromMidi(65.0)),
        )
        Rendition.RETAKE -> listOf(
            TARGETS_V1[0].toSynth(cents = 5.0, delayMs = 0L),
            TARGETS_V1[1].toSynth(cents = 0.0, delayMs = 0L),
            TARGETS_V1[2].toSynth(cents = 0.0, delayMs = 12L),
            TARGETS_V1[3].toSynth(cents = 0.0, delayMs = 0L),
        )
    }

    fun render(rendition: Rendition, sampleRate: Int = SAMPLE_RATE, tailMs: Long = 250L): FloatArray =
        renderNotes(notesFor(rendition), sampleRate, tailMs)

    fun renderNotes(
        notes: List<SynthNote>,
        sampleRate: Int = SAMPLE_RATE,
        tailMs: Long = 250L,
    ): FloatArray {
        require(notes.isNotEmpty())
        val lastMs = notes.maxOf { it.startMs + it.durationMs } + tailMs
        val total = ((sampleRate * lastMs) / 1000L).toInt().coerceAtLeast(1)
        val out = FloatArray(total)
        val fade = (sampleRate * 0.008).toInt().coerceAtLeast(4)
        for (note in notes) {
            val start = ((sampleRate * note.startMs) / 1000L).toInt()
            val len = ((sampleRate * note.durationMs) / 1000L).toInt().coerceAtLeast(fade * 2 + 1)
            val tone = Signals.sine(note.frequencyHz, sampleRate, len, note.amplitude)
            applyFade(tone, fade)
            for (i in tone.indices) {
                val j = start + i
                if (j in out.indices) out[j] += tone[i]
            }
        }
        return out
    }

    fun isOfficialReference(reference: PracticeReference): Boolean =
        reference.fragmentId == ID_V1 && reference.targets == TARGETS_V1 && reference.rhythm == RHYTHM_V1

    fun buildTake(
        takeId: String,
        events: List<NoteEvent>,
        reference: PracticeReference = REFERENCE_V1,
        rendition: Rendition? = null,
        source: InputSource = InputSource.FILE_SYNTHETIC,
        referenceConfirmed: Boolean? = null,
        sourceName: String? = null,
        audioDurationMs: Long? = null,
        timeWindowStartMs: Long? = null,
        timeWindowEndMs: Long? = null,
        segmentation: SegmentationDiagnostics = SegmentationDiagnostics(),
    ): PerformanceTake = PerformanceTake(
        takeId = takeId,
        fragmentId = reference.fragmentId,
        source = source,
        rhythm = reference.rhythm,
        targets = reference.targets,
        events = events,
        rendition = rendition?.id,
        referenceConfirmed = referenceConfirmed ?: isOfficialReference(reference),
        sourceName = sourceName ?: rendition?.label,
        audioDurationMs = audioDurationMs,
        timeWindowStartMs = timeWindowStartMs,
        timeWindowEndMs = timeWindowEndMs,
        segmentation = segmentation,
    )

    private fun TargetNote.toSynth(cents: Double, delayMs: Long): SynthNote {
        val base = frequencyHz ?: Tuning.A4_HZ
        val hz = base * 2.0.pow(cents / 1200.0)
        return SynthNote(startMs = startMs + delayMs, durationMs = durationMs, frequencyHz = hz)
    }

    private fun applyFade(samples: FloatArray, fade: Int) {
        val n = samples.size
        val f = fade.coerceAtMost(n / 2)
        if (f <= 0) return
        for (i in 0 until f) {
            val g = i / f.toFloat()
            samples[i] *= g
            samples[n - 1 - i] *= g
        }
    }
}
