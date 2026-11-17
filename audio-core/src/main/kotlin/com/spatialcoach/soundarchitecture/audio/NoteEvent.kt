package com.spatialcoach.soundarchitecture.audio

/**
 * 从单旋律分析帧切出的一个音符事件。
 * [centsVsNearest] 只表示相对最近律音的音准，不等于相对谱面目标。
 * [centsVsTarget] / [targetMidi] 仅在与参考对齐之后才填写；目标未知时必须为 null，不得据此判错音。
 * [beatIndex] 仅在有显式 [RhythmReference] 时填写。
 */
data class NoteEvent(
    val startMs: Long,
    val endMs: Long,
    val pitchHz: Double?,
    val midi: Int?,
    val noteName: String?,
    val centsVsNearest: Double?,
    val pitchConfidence: Double?,
    val stable: Boolean,
    val glideSuspected: Boolean,
    val centsVsTarget: Double? = null,
    val targetMidi: Int? = null,
    val targetNoteName: String? = null,
    val beatIndex: Int? = null,
    val timingErrorMs: Long? = null,
    val expectedStartMs: Long? = null,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

enum class InputSource {
    FILE_SYNTHETIC,
    USER_WAV_FILE,
    ;

    val label: String
        get() = when (this) {
            FILE_SYNTHETIC -> "内置合成示范（不是用户演奏）"
            USER_WAV_FILE -> "录音文件分析（不是实时采集）"
        }
}

/**
 * 一次可追溯的演奏记录。两次比较必须同一 [fragmentId]、同一节拍参考、同一目标表，且都已确认参考。
 * [timeWindowStartMs] 到 [timeWindowEndMs] 是分析用的文件窗口；默认从 0 到文件时长，不裁开头静音、不平移对齐。
 */
data class PerformanceTake(
    val takeId: String,
    val fragmentId: String,
    val source: InputSource,
    val rhythm: RhythmReference?,
    val targets: List<TargetNote>?,
    val events: List<NoteEvent>,
    val rendition: String? = null,
    val referenceConfirmed: Boolean = false,
    val sourceName: String? = null,
    val audioDurationMs: Long? = null,
    val timeWindowStartMs: Long? = null,
    val timeWindowEndMs: Long? = null,
)

data class TargetNote(
    val startMs: Long,
    val durationMs: Long,
    val midi: Int? = null,
) {
    val endMs: Long get() = startMs + durationMs
    val noteName: String? get() = midi?.let { Tuning.noteName(it) }
    val frequencyHz: Double? get() = midi?.let { Tuning.hzFromMidi(it.toDouble()) }
}

data class PracticeReference(
    val fragmentId: String,
    val rhythm: RhythmReference?,
    val targets: List<TargetNote>,
    val description: String,
) {
    val hasPitchTargets: Boolean get() = targets.any { it.midi != null }
}
