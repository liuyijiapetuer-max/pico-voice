package com.spatialcoach.soundarchitecture.audio

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StrictUserWavHeaderTest {
    private val sr = 44_100
    private val frameBytes = 2

    private fun pcm(durationMs: Int): FloatArray {
        val n = ((sr.toLong() * durationMs) / 1000L).toInt()
        return FloatArray(n) { 0.01f }
    }

    private fun wav(durationMs: Int): ByteArray = PcmWav.encodeMono16(pcm(durationMs), sr)

    @Test
    fun exactlyTwentySecondsIsAccepted() {
        val decoded = StrictUserWav.decode(wav(20_000))
        assertEquals(20_000L, decoded.durationMs)
    }

    @Test
    fun oneFrameOverTwentySecondsRejectedFromHeaderWithoutReadingPayload() {
        val samples = (sr * 20L) + 1
        val payload = samples * frameBytes
        val headerOnly = synthHeader(dataSize = payload.toInt(), byteRate = sr * frameBytes)
        val stream = CountingStream(headerOnly + ByteArray(64)) // 远少于声明的 data
        val ex = assertFailsWith<UserWavException> {
            StrictUserWav.decodeStream(stream, knownLength = null)
        }
        assertTrue("未读入采样" in ex.message!! || "超过" in ex.message!!, ex.message)
        assertTrue(stream.bytesRead <= headerOnly.size + 4, "should stop after header, read=${stream.bytesRead} header=${headerOnly.size}")
    }

    @Test
    fun twentyOneSecondsUnderThreeMegabytesRejectedFromHeader() {
        // 21 s * 44100 * 2 ≈ 1.85 MB，小于 3 MB 资源上限，必须靠头时长拒绝。
        val bytes = wav(21_000)
        assertTrue(bytes.size < 3_000_000)
        val stream = CountingStream(bytes)
        val ex = assertFailsWith<UserWavException> {
            StrictUserWav.decodeStream(stream, knownLength = bytes.size.toLong())
        }
        assertTrue("未读入采样" in (ex.message ?: ""), ex.message)
        assertTrue(stream.bytesRead < 8_000, "header-only expected, read=${stream.bytesRead} file=${bytes.size}")
    }

    @Test
    fun threeMegabyteResourceLimitStillRejectedBeforeRead() {
        val ex = assertFailsWith<UserWavException> {
            StrictUserWav.decodeStream(ByteArrayInputStream(ByteArray(1)), knownLength = 4_000_000)
        }
        assertTrue("上限" in ex.message!!, ex.message)
    }

    @Test
    fun corruptHeaderByteRateMismatchIsRejected() {
        val ok = wav(500)
        val broken = rewriteByteRate(ok, declared = 12345)
        val ex = assertFailsWith<UserWavException> {
            StrictUserWav.decode(broken)
        }
        assertTrue("损坏头" in ex.message!! || "不一致" in ex.message!!, ex.message)
    }

    @Test
    fun dataBeforeFmtIsRejected() {
        val buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(56)
        buf.put("WAVE".toByteArray())
        buf.put("data".toByteArray())
        buf.putInt(8)
        buf.putInt(0)
        buf.putInt(0)
        val ex = assertFailsWith<UserWavException> {
            StrictUserWav.decode(buf.array())
        }
        assertTrue("fmt" in ex.message!!, ex.message)
    }

    @Test
    fun truncatedDataAfterLegalHeaderIsRejected() {
        val header = synthHeader(dataSize = 8_000, byteRate = sr * frameBytes)
        val ex = assertFailsWith<UserWavException> {
            StrictUserWav.decodeStream(ByteArrayInputStream(header + ByteArray(10)), knownLength = null)
        }
        assertTrue("截断" in ex.message!!, ex.message)
    }

    @Test
    fun invalidNegativeLikeHugeChunkSizeRejected() {
        val buf = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(40)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(-8)
        val ex = assertFailsWith<UserWavException> {
            StrictUserWav.decode(buf.array())
        }
        assertTrue("非法" in ex.message!! || "不完整" in ex.message!! || "截断" in ex.message!!, ex.message)
    }

    private fun synthHeader(dataSize: Int, byteRate: Int): ByteArray {
        val buf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16)
        buf.putShort(1)
        buf.putShort(1)
        buf.putInt(sr)
        buf.putInt(byteRate)
        buf.putShort(2)
        buf.putShort(16)
        buf.put("data".toByteArray())
        buf.putInt(dataSize)
        return buf.array()
    }

    private fun rewriteByteRate(bytes: ByteArray, declared: Int): ByteArray {
        val copy = bytes.copyOf()
        val buf = ByteBuffer.wrap(copy).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(12)
        while (buf.remaining() >= 8) {
            val id = ByteArray(4)
            buf.get(id)
            val size = buf.int
            val start = buf.position()
            if (String(id, Charsets.US_ASCII) == "fmt ") {
                buf.short
                buf.short
                buf.int
                buf.putInt(declared)
                break
            }
            buf.position(start + size + (size and 1))
        }
        return copy
    }

    private class CountingStream(data: ByteArray) : InputStream() {
        private val inner = ByteArrayInputStream(data)
        var bytesRead: Int = 0
            private set

        override fun read(): Int {
            val v = inner.read()
            if (v >= 0) bytesRead++
            return v
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = inner.read(b, off, len)
            if (n > 0) bytesRead += n
            return n
        }
    }
}
