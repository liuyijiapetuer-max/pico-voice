package com.spatialcoach.soundarchitecture.audio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 用户 WAV 导入：真实编码的 PCM16 单声道 44.1 kHz 文件必须经过解码再进 AnalysisEngine。
 * 失败不得回退成内置合成示范。
 */
class UserWavImportTest {

    private val sr = PracticeFragments.SAMPLE_RATE

    @Test
    fun officialWavRoundTripBuildsTwoComparableTakes() {
        val firstBytes = PcmWav.encodeMono16(PracticeFragments.render(PracticeFragments.Rendition.FIRST_TAKE), sr)
        val retakeBytes = PcmWav.encodeMono16(PracticeFragments.render(PracticeFragments.Rendition.RETAKE), sr)
        val first = success(UserTakeFactory.fromWavBytes(firstBytes, "first", "first.wav", true))
        val retake = success(UserTakeFactory.fromWavBytes(retakeBytes, "retake", "retake.wav", true))
        assertEquals(InputSource.USER_WAV_FILE, first.source)
        assertTrue("不是实时采集" in first.source.label)
        assertEquals("first.wav", first.sourceName)
        assertEquals(0L, first.timeWindowStartMs)
        assertNotNull(first.timeWindowEndMs)
        assertEquals(first.audioDurationMs, first.timeWindowEndMs)
        val comparison = TakeComparator.compare(first, retake)
        assertTrue(comparison.comparable, comparison.rejectedReason ?: "ok")
        val a = assertNotNull(comparison.takeA)
        val b = assertNotNull(comparison.takeB)
        assertEquals(1, a.coverage.wrongNotes)
        assertEquals(0, b.coverage.wrongNotes)
        assertEquals("4/4", a.coverage.rateText())
        assertTrue(comparison.suggestions.any { "不含错音" in it })
        assertTrue(comparison.suggestions.none { it.contains("整体音准") && it.contains("有改善") })
        assertTrue(comparison.suggestions.any { "覆盖率" in it && "错音" in it })
        val g4a = a.items.first { it.referenceIndex == 2 }
        val g4b = b.items.first { it.referenceIndex == 2 }
        assertNotNull(g4a.timingVsProcessedReferenceMs)
        assertTrue(abs(g4a.timingVsProcessedReferenceMs!! - 80L) <= 35L, "rel=${g4a.timingVsProcessedReferenceMs} raw=${g4a.timingErrorMs}")
        // 原始时差保留，不把相对谱面的检测窗偏差说成赶拍。
        assertTrue(g4a.timingErrorMs != g4a.timingVsProcessedReferenceMs)
        assertTrue(comparison.suggestions.any { "相对同处理参考" in it && "不能直接称赶拍" in it })
        assertEquals(ImprovementVerdict.SPECIFIC_EVIDENCE, comparison.verdict)
    }

    @Test
    fun unconfirmedFileIsNotBoundToOfficialFragment() {
        val bytes = PcmWav.encodeMono16(PracticeFragments.render(PracticeFragments.Rendition.REFERENCE), sr)
        val result = UserTakeFactory.fromWavBytes(bytes, "x", "mystery.wav", referenceConfirmed = false)
        val failed = result as UserTakeLoadResult.Failed
        assertTrue("未确认" in failed.reason, failed.reason)
        assertTrue("没有改写" in failed.reason || "拒绝绑定" in failed.reason)
    }

    @Test
    fun unconfirmedTakesAreNotComparableEvenIfMetadataLooksOfficial() {
        val pcm = PracticeFragments.render(PracticeFragments.Rendition.REFERENCE)
        val stamped = PassageAnalyzer.take("stamped", pcm, referenceConfirmed = false, sourceName = "other.wav")
        val official = PassageAnalyzer.takeFromRendition(PracticeFragments.Rendition.REFERENCE)
        assertFalse(stamped.referenceConfirmed)
        val comparison = TakeComparator.compare(stamped, official)
        assertFalse(comparison.comparable)
        assertNotNull(comparison.rejectedReason)
        assertTrue("未确认" in comparison.rejectedReason!!)
    }

    @Test
    fun leadingSilenceIsNotTrimmedOrShifted() {
        val delayed = PracticeFragments.renderNotes(
            PracticeFragments.TARGETS_V1.map { target ->
                PracticeFragments.SynthNote(target.startMs + 500L, target.durationMs, target.frequencyHz!!)
            },
            tailMs = 250L,
        )
        val bytes = PcmWav.encodeMono16(delayed, sr)
        val take = success(UserTakeFactory.fromWavBytes(bytes, "late-start", "late-start.wav", true))
        assertEquals(0L, take.timeWindowStartMs)
        val aligned = TakeComparator.alignWithProcessedReference(take)
        val rel = aligned.items.filter { it.kind == AlignmentKind.MATCH }.map { it.timingVsProcessedReferenceMs!! }
        assertEquals(4, rel.size, "kinds=${aligned.items.map { it.kind to (it.referenceIndex to it.timingErrorMs) }} events=${take.events.map { it.noteName to it.startMs }}")
        for (value in rel) {
            // 后三音相对约 +500；官方首音几乎无窗提前，相对可到约 +464。
            assertTrue(abs(value - 500L) <= 50L, "relative=$rel")
        }
        val raw = aligned.items.mapNotNull { it.timingErrorMs }
        // 原始时差仍在，没有被减成接近 0。
        assertTrue(raw.map { abs(it) }.average() >= 400.0, "raw=$raw")
    }

    @Test
    fun engineResetPreventsOnsetLeakAcrossTakes() {
        val engine = AnalysisEngine(sampleRate = sr, fftSize = 2048, hopSize = 512)
        val first = PracticeFragments.render(PracticeFragments.Rendition.REFERENCE)
        engine.push(first)
        engine.reset()
        val second = PracticeFragments.renderNotes(
            listOf(PracticeFragments.SynthNote(0, 400, Tuning.A4_HZ)),
            tailMs = 200L,
        )
        val frames = engine.push(second)
        val intervals = frames.mapNotNull { it.onsetIntervalMs }
        assertTrue(intervals.none { it > 1_500L }, "onset leaked across reset: $intervals")
    }

    @Test
    fun rejectsStereoAndWrongRateAndBitDepthAndTruncation() {
        val mono = Signals.sine(Tuning.A4_HZ, sr, sr / 2, 0.4)
        val good = PcmWav.encodeMono16(mono, sr)
        assertTrue(UserTakeFactory.fromWavBytes(rewriteFmt(good, channels = 2), "a", "stereo.wav", true) is UserTakeLoadResult.Failed)
        assertTrue(UserTakeFactory.fromWavBytes(rewriteFmt(good, sampleRate = 48_000), "a", "48k.wav", true) is UserTakeLoadResult.Failed)
        assertTrue(UserTakeFactory.fromWavBytes(rewriteFmt(good, bits = 24), "a", "24bit.wav", true) is UserTakeLoadResult.Failed)
        assertTrue(UserTakeFactory.fromWavBytes(good.copyOf(20), "a", "trunc.wav", true) is UserTakeLoadResult.Failed)
        assertTrue(UserTakeFactory.fromWavBytes("NOTWAV".toByteArray(), "a", "x.wav", true) is UserTakeLoadResult.Failed)
        val tooLong = Signals.sine(Tuning.A4_HZ, sr, sr * 25, 0.3)
        val longResult = UserTakeFactory.fromWavBytes(PcmWav.encodeMono16(tooLong, sr), "a", "long.wav", true)
        assertTrue(longResult is UserTakeLoadResult.Failed, longResult.toString())
        assertTrue((longResult as UserTakeLoadResult.Failed).reason.contains("时长") || longResult.reason.contains("过大"))
    }

    @Test
    fun streamIsClosedByCallerAndTooLargeIsRejectedBeforeFullDecode() {
        val bytes = PcmWav.encodeMono16(PracticeFragments.render(PracticeFragments.Rendition.REFERENCE), sr)
        val tracking = TrackingStream(ByteArrayInputStream(bytes))
        tracking.use { stream ->
            val result = UserTakeFactory.fromWavStream(stream, bytes.size.toLong(), "ok", "ok.wav", true)
            assertTrue(result is UserTakeLoadResult.Ok, result.toString())
        }
        assertTrue(tracking.closed)
        val oversized = UserTakeFactory.fromWavStream(
            ByteArrayInputStream(ByteArray(100)),
            knownLength = 4_000_000L,
            takeId = "big",
            sourceName = "big.wav",
            referenceConfirmed = true,
        )
        val failed = oversized as UserTakeLoadResult.Failed
        assertTrue("过大" in failed.reason, failed.reason)
    }

    @Test
    fun lowCoverageRefusesImprovementVerdict() {
        val onlyFirst = PracticeFragments.renderNotes(
            listOf(PracticeFragments.SynthNote(0, 400, Tuning.hzFromMidi(69.0))),
            tailMs = 250L,
        )
        val bytes = PcmWav.encodeMono16(onlyFirst, sr)
        val sparse = success(UserTakeFactory.fromWavBytes(bytes, "sparse", "sparse.wav", true))
        val retake = success(
            UserTakeFactory.fromWavBytes(
                PcmWav.encodeMono16(PracticeFragments.render(PracticeFragments.Rendition.RETAKE), sr),
                "retake",
                "retake.wav",
                true,
            ),
        )
        val comparison = TakeComparator.compare(sparse, retake)
        assertTrue(comparison.comparable)
        assertEquals(ImprovementVerdict.INCONCLUSIVE_LOW_COVERAGE, comparison.verdict)
        assertTrue(comparison.suggestions.any { "不能下改善结论" in it })
        assertTrue(sparse.let { TakeComparator.alignWithProcessedReference(it).coverage.missing >= 2 })
    }

    @Test
    fun samePitchRepeatsAreTestedAndLimitationIsExplicitIfUnreliable() {
        val notes = MutableList(0) { PracticeFragments.SynthNote(0, 0, 0.0) }.toMutableList()
        // 4 个 A4：160 ms 发音 + 100 ms 间隔，贴近内置 demo 的短同音。
        var t = 0L
        repeat(4) {
            notes += PracticeFragments.SynthNote(t, 160, Tuning.A4_HZ)
            t += 260L
        }
        val pcm = PracticeFragments.renderNotes(notes, tailMs = 200L)
        val events = PassageAnalyzer.events(pcm)
        val stableA4 = events.filter { it.stable && it.noteName == "A4" }
        if (stableA4.size == 4) {
            val starts = stableA4.map { it.startMs }
            for (i in 1 until starts.size) {
                val gap = starts[i] - starts[i - 1]
                assertTrue(abs(gap - 260L) <= 80L, "gap=$gap starts=$starts")
            }
        } else {
            assertTrue(
                ReviewText.REPEAT_LIMIT.contains("同音"),
                "短间隔同音分段不可靠时必须有明确限制，events=${events.map { it.noteName to it.startMs }}",
            )
            assertTrue(stableA4.size < 4)
        }
    }

    @Test
    fun fiftyCentBoundaryFollowsNearestMidiNotASilentStamp() {
        // 以 E4 为目标：错音按「最近律音 MIDI 是否等于谱面目标」判定，50 音分是十二平均律中点。
        val e4 = 64
        val cases = listOf(-51.0, -50.0, -49.0, 49.0, 50.0, 51.0)
        val results = cases.associateWith { cents ->
            val hz = Tuning.hzFromMidi(e4.toDouble()) * 2.0.pow(cents / 1200.0)
            val nearest = Tuning.nearestMidi(hz)
            val event = NoteEvent(
                startMs = 1500,
                endMs = 1900,
                pitchHz = hz,
                midi = nearest,
                noteName = Tuning.noteName(nearest),
                centsVsNearest = Tuning.centsFromNearest(hz),
                pitchConfidence = 0.9,
                stable = true,
                glideSuspected = false,
            )
            val target = PracticeFragments.TARGETS_V1[3]
            val vsTarget = TakeComparator.centsVsTarget(event, target)
            val wrong = TakeComparator.isWrongNote(event, target, vsTarget)
            Triple(nearest, vsTarget, wrong)
        }
        assertFalse(results.getValue(49.0).third, " +49 cents should stay E4, ${results[49.0]}")
        assertFalse(results.getValue(-49.0).third, " -49 cents should stay E4, ${results[-49.0]}")
        assertTrue(results.getValue(51.0).third, "+51 cents should be a different nearest pitch, ${results[51.0]}")
        assertTrue(results.getValue(-51.0).third, "-51 cents should be a different nearest pitch, ${results[-51.0]}")
        // ±50 音分落在中点，记录实际舍入，不得把最近律音静默写成目标音。
        val plus50 = results.getValue(50.0)
        val minus50 = results.getValue(-50.0)
        assertTrue(plus50.second != null && abs(plus50.second!!) >= 49.0)
        assertTrue(minus50.second != null && abs(minus50.second!!) >= 49.0)
        if (plus50.first == e4) assertFalse(plus50.third) else assertTrue(plus50.third)
        if (minus50.first == e4) assertFalse(minus50.third) else assertTrue(minus50.third)
        // 真实 PCM：+51 音分的 E4 目标应被标出错音（最近律音变为 F4 一侧）。
        val sharp = PracticeFragments.renderNotes(
            PracticeFragments.TARGETS_V1.mapIndexed { index, target ->
                val cents = if (index == 3) 51.0 else 0.0
                PracticeFragments.SynthNote(target.startMs, target.durationMs, Tuning.hzFromMidi(target.midi!!.toDouble()) * 2.0.pow(cents / 1200.0))
            },
        )
        val aligned = TakeComparator.align(PassageAnalyzer.take("plus51", sharp))
        assertEquals(AlignmentKind.WRONG_NOTE, aligned.items.first { it.referenceIndex == 3 }.kind)
        val mild = PracticeFragments.renderNotes(
            PracticeFragments.TARGETS_V1.mapIndexed { index, target ->
                val cents = if (index == 3) 49.0 else 0.0
                PracticeFragments.SynthNote(target.startMs, target.durationMs, Tuning.hzFromMidi(target.midi!!.toDouble()) * 2.0.pow(cents / 1200.0))
            },
        )
        val mildAligned = TakeComparator.align(PassageAnalyzer.take("plus49", mild))
        val item = mildAligned.items.first { it.referenceIndex == 3 }
        assertEquals(AlignmentKind.MATCH, item.kind)
        assertNotNull(item.centsVsTarget)
        assertTrue(abs(item.centsVsTarget!! - 49.0) <= 8.0, "cents=${item.centsVsTarget}")
    }

    private fun success(result: UserTakeLoadResult): PerformanceTake {
        assertTrue(result is UserTakeLoadResult.Ok, result.toString())
        return (result as UserTakeLoadResult.Ok).take
    }

    private fun rewriteFmt(bytes: ByteArray, channels: Int? = null, sampleRate: Int? = null, bits: Int? = null): ByteArray {
        val copy = bytes.copyOf()
        val buf = ByteBuffer.wrap(copy).order(ByteOrder.LITTLE_ENDIAN)
        // RIFF/WAVE then find fmt
        buf.position(12)
        while (buf.remaining() >= 8) {
            val idBytes = ByteArray(4)
            buf.get(idBytes)
            val size = buf.int
            val start = buf.position()
            if (String(idBytes, Charsets.US_ASCII) == "fmt ") {
                buf.short // format
                if (channels != null) buf.putShort(channels.toShort()) else buf.short
                if (sampleRate != null) buf.putInt(sampleRate) else buf.int
                buf.int
                buf.short
                if (bits != null) buf.putShort(bits.toShort())
                break
            }
            buf.position(start + size + (size and 1))
        }
        return copy
    }

    private class TrackingStream(private val inner: InputStream) : InputStream() {
        var closed: Boolean = false
            private set

        override fun read(): Int = inner.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len)
        override fun close() {
            closed = true
            inner.close()
        }
    }
}
