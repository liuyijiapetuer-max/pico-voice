package com.spatialcoach.soundarchitecture.audio

/**
 * 把标准 MIDI 音符事件收成一次可对齐的演奏。
 * 只记录按键音符与时间，不把律音频率写成声学音准，也不合成音色冒充实际演奏。
 */
data class TimedMidiMessage(
    val timeMs: Long,
    val bytes: ByteArray,
    val offset: Int = 0,
    val length: Int = bytes.size - offset,
)

object MidiTakeFactory {
    const val MIN_NOTE_MS = 40L

    fun fromMessages(
        messages: List<TimedMidiMessage>,
        takeId: String,
        sourceName: String,
        referenceConfirmed: Boolean,
        reference: PracticeReference = PracticeFragments.REFERENCE_V1,
        sessionEndMs: Long? = null,
    ): LiveCaptureResult {
        val rejected = UserTakeFactory.bindRejected(referenceConfirmed, reference)
        if (rejected != null) return LiveCaptureResult.Failed(rejected)
        val events = MidiNoteParser.monophonicEvents(messages, sessionEndMs)
        if (events.isEmpty()) {
            return LiveCaptureResult.Failed(EvaluationPolicy.MIDI_NO_NOTES)
        }
        val last = (sessionEndMs ?: events.maxOf { it.endMs }).coerceAtLeast(events.maxOf { it.endMs })
        val take = PracticeFragments.buildTake(
            takeId = takeId,
            events = events,
            reference = reference,
            rendition = null,
            source = InputSource.MIDI_EVENTS,
            referenceConfirmed = true,
            sourceName = sourceName,
            audioDurationMs = last,
            timeWindowStartMs = 0L,
            timeWindowEndMs = last,
            audio = null,
        )
        return LiveCaptureResult.Ok(take)
    }
}

object MidiNoteParser {
    fun monophonicEvents(
        messages: List<TimedMidiMessage>,
        sessionEndMs: Long? = null,
        minNoteMs: Long = MidiTakeFactory.MIN_NOTE_MS,
    ): List<NoteEvent> {
        val events = ArrayList<NoteEvent>()
        var soundingMidi: Int? = null
        var soundingStart = 0L
        var runningStatus = 0
        fun close(atMs: Long) {
            val midi = soundingMidi ?: return
            val end = atMs.coerceAtLeast(soundingStart)
            soundingMidi = null
            val duration = end - soundingStart
            if (duration < minNoteMs) return
            events += midiEvent(soundingStart, end, midi, stable = true)
        }
        for (message in messages.sortedBy { it.timeMs }) {
            for (parsed in parseBuffer(message, runningStatus)) {
                runningStatus = parsed.runningStatus
                when (parsed.kind) {
                    MidiVoiceKind.NOTE_ON -> {
                        if (soundingMidi != null) close(message.timeMs)
                        soundingMidi = parsed.note
                        soundingStart = message.timeMs.coerceAtLeast(0L)
                    }
                    MidiVoiceKind.NOTE_OFF -> {
                        if (soundingMidi != null && soundingMidi == parsed.note) {
                            close(message.timeMs)
                        }
                    }
                    MidiVoiceKind.OTHER -> Unit
                }
            }
        }
        val closeAt = sessionEndMs ?: messages.maxOfOrNull { it.timeMs }
        if (soundingMidi != null && closeAt != null) close(closeAt)
        return events
    }

    private fun midiEvent(startMs: Long, endMs: Long, midi: Int, stable: Boolean): NoteEvent =
        NoteEvent(
            startMs = startMs,
            endMs = endMs,
            pitchHz = Tuning.hzFromMidi(midi.toDouble()),
            midi = midi,
            noteName = Tuning.noteName(midi),
            centsVsNearest = null,
            pitchConfidence = null,
            stable = stable,
            glideSuspected = false,
        )

    internal fun parseBuffer(message: TimedMidiMessage, initialRunningStatus: Int): List<ParsedVoice> {
        val data = message.bytes
        var i = message.offset
        val end = (message.offset + message.length).coerceAtMost(data.size)
        var runningStatus = initialRunningStatus
        val out = ArrayList<ParsedVoice>()
        while (i < end) {
            val b = data[i].toInt() and 0xFF
            if (b == 0xF0) {
                i++
                while (i < end && (data[i].toInt() and 0xFF) != 0xF7) i++
                if (i < end) i++
                runningStatus = 0
                continue
            }
            if (b == 0xF7) {
                i++
                continue
            }
            if (b in 0xF8..0xFF) {
                i++
                continue
            }
            val status: Int
            if (b and 0x80 != 0) {
                status = b
                i++
            } else if (runningStatus and 0x80 != 0) {
                status = runningStatus
            } else {
                i++
                continue
            }
            val command = status and 0xF0
            val dataBytes = when (command) {
                0x80, 0x90, 0xA0, 0xB0, 0xE0 -> 2
                0xC0, 0xD0 -> 1
                else -> {
                    i++
                    continue
                }
            }
            if (i + dataBytes > end) break
            val d1 = data[i].toInt() and 0x7F
            val d2 = if (dataBytes > 1) data[i + 1].toInt() and 0x7F else 0
            i += dataBytes
            runningStatus = status
            val kind = when (command) {
                0x90 -> if (d2 > 0) MidiVoiceKind.NOTE_ON else MidiVoiceKind.NOTE_OFF
                0x80 -> MidiVoiceKind.NOTE_OFF
                else -> MidiVoiceKind.OTHER
            }
            out += ParsedVoice(kind, d1, runningStatus)
        }
        return out
    }

    internal enum class MidiVoiceKind { NOTE_ON, NOTE_OFF, OTHER }

    internal data class ParsedVoice(
        val kind: MidiVoiceKind,
        val note: Int,
        val runningStatus: Int,
    )
}
