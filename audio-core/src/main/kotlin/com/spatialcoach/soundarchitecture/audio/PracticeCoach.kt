package com.spatialcoach.soundarchitecture.audio

import kotlin.math.abs
import kotlin.math.roundToInt

/** 练习模式用的四个可读项：音准、节奏、强弱，再加一句人话。 */
data class PracticeCue(
    val pitchLabel: String,
    val rhythmLabel: String,
    val loudnessLabel: String,
    val sentence: String,
)

object PracticeCoach {
    fun cue(frame: AnalysisFrame): PracticeCue {
        val pitchLabel = when {
            frame.pitchHz == null || frame.cents == null -> "听不到稳定音高"
            abs(frame.cents) <= 10.0 -> "音准稳定（${frame.noteName}）"
            frame.cents > 10.0 -> "偏高约 ${frame.cents.roundToInt()} 音分"
            else -> "偏低约 ${abs(frame.cents).roundToInt()} 音分"
        }
        val loudnessLabel = when {
            frame.dbfs <= -48.0 -> "几乎没有声音"
            frame.dbfs <= -28.0 -> "偏弱"
            frame.dbfs <= -10.0 -> "力度适中"
            else -> "偏强"
        }
        val rhythmLabel = when {
            frame.rhythmStability == null && frame.onsetIntervalMs == null -> "还没有稳定拍点"
            (frame.rhythmStability ?: 0.0) >= 0.7 -> "节奏比较稳"
            else -> "节奏不太稳"
        }
        val sentence = buildSentence(frame, pitchLabel, rhythmLabel, loudnessLabel)
        return PracticeCue(pitchLabel, rhythmLabel, loudnessLabel, sentence)
    }

    private fun buildSentence(
        frame: AnalysisFrame,
        pitchLabel: String,
        rhythmLabel: String,
        loudnessLabel: String,
    ): String {
        val cents = frame.cents
        return when {
            frame.pitchHz == null && frame.dbfs <= -48.0 ->
                "现在很安静，弹出一个单音看看音准。"
            cents != null && abs(cents) > 15.0 && frame.dbfs <= -28.0 ->
                "$pitchLabel，声音再给大一点。"
            cents != null && abs(cents) > 15.0 && cents > 0 ->
                "对着 ${frame.noteName}，再低一点。"
            cents != null && abs(cents) > 15.0 ->
                "对着 ${frame.noteName}，再抬高一点。"
            cents != null && abs(cents) <= 10.0 && frame.dbfs <= -28.0 ->
                "音准已经稳住了，力度再大一点。"
            cents != null && abs(cents) <= 10.0 && (frame.rhythmStability ?: 1.0) < 0.7 && frame.onsetIntervalMs != null ->
                "音准不错，跟着拍点再稳一点。"
            cents != null && abs(cents) <= 10.0 ->
                "音准稳定，保持住。"
            else ->
                "$pitchLabel，$loudnessLabel，$rhythmLabel。"
        }
    }
}
