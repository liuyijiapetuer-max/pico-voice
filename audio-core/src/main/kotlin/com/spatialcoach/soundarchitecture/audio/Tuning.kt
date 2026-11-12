package com.spatialcoach.soundarchitecture.audio

import kotlin.math.ln
import kotlin.math.roundToInt

/** 十二平均律，A4 = 440 Hz。 */
object Tuning {
    const val A4_HZ = 440.0
    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun midiFromHz(freqHz: Double): Double =
        69.0 + 12.0 * (ln(freqHz / A4_HZ) / ln(2.0))

    fun hzFromMidi(midi: Double): Double =
        A4_HZ * Math.pow(2.0, (midi - 69.0) / 12.0)

    fun nearestMidi(freqHz: Double): Int = midiFromHz(freqHz).roundToInt()

    /** 相对最近音的音分，正为偏高，负为偏低。 */
    fun centsFromNearest(freqHz: Double): Double {
        val midi = midiFromHz(freqHz)
        return (midi - midi.roundToInt()) * 100.0
    }

    fun noteName(midi: Int): String {
        val index = Math.floorMod(midi, 12)
        val octave = midi / 12 - 1
        return NOTE_NAMES[index] + octave
    }
}
