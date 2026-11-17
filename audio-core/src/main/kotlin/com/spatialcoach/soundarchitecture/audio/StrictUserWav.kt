package com.spatialcoach.soundarchitecture.audio

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToLong

/**
 * 用户导入 WAV 的窄格式解码：只接受 RIFF/WAVE、PCM16、单声道、44100 Hz。
 * 不做重采样、不裁开头静音、不平移时间。解码前检查尺寸与时长，避免把过大文件读进内存。
 */
class UserWavException(message: String) : IllegalArgumentException(message)

data class WavLimits(
    val maxBytes: Int = 3_000_000,
    val maxDurationMs: Long = 20_000L,
    val sampleRate: Int = 44_100,
    val channels: Int = 1,
    val bits: Int = 16,
)

data class DecodedUserWav(
    val sampleRate: Int,
    val samples: FloatArray,
    val durationMs: Long,
    val byteSize: Int,
)

object StrictUserWav {
    val DEFAULT_LIMITS = WavLimits()

    fun decode(bytes: ByteArray, limits: WavLimits = DEFAULT_LIMITS): DecodedUserWav {
        if (bytes.size > limits.maxBytes) {
            throw UserWavException("文件过大（${bytes.size} 字节），超过 ${limits.maxBytes} 字节上限。")
        }
        if (bytes.size < 44) {
            throw UserWavException("文件过短（${bytes.size} 字节），不是完整的 WAV。")
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun tag(): String {
            if (buf.remaining() < 4) throw UserWavException("RIFF 结构不完整，缺少块标记。")
            val b = ByteArray(4)
            buf.get(b)
            return String(b, Charsets.US_ASCII)
        }
        if (tag() != "RIFF") throw UserWavException("不是 RIFF 文件。")
        if (buf.remaining() < 4) throw UserWavException("RIFF 头缺少尺寸字段。")
        buf.int
        if (tag() != "WAVE") throw UserWavException("不是 WAVE 文件。")

        var sampleRate = 0
        var bits = 0
        var channels = 0
        var format = 0
        var sawFmt = false
        var data: ByteArray? = null

        while (buf.remaining() >= 8) {
            val id = tag()
            val size = buf.int
            if (size < 0) throw UserWavException("RIFF 块大小非法（$id: $size）。")
            val padded = size + (size and 1)
            if (buf.remaining() < size) {
                throw UserWavException("RIFF 块 $id 声明 $size 字节，实际剩余 ${buf.remaining()} 字节，文件被截断。")
            }
            val start = buf.position()
            when (id) {
                "fmt " -> {
                    if (size < 16) throw UserWavException("fmt 块过短。")
                    format = buf.short.toInt() and 0xFFFF
                    channels = buf.short.toInt()
                    sampleRate = buf.int
                    buf.int // byte rate
                    buf.short // block align
                    bits = buf.short.toInt() and 0xFFFF
                    sawFmt = true
                }
                "data" -> {
                    data = ByteArray(size)
                    buf.get(data)
                }
                else -> {
                    // skip unknown chunk
                }
            }
            buf.position(start)
            if (buf.remaining() < padded) {
                throw UserWavException("RIFF 块 $id 的对齐填充不完整。")
            }
            buf.position(start + padded)
        }
        if (!sawFmt) throw UserWavException("缺少 fmt 块。")
        if (format != 1) throw UserWavException("音频格式是 $format，只支持 PCM（格式 1）。")
        if (channels != limits.channels) {
            throw UserWavException("声道数是 $channels，只支持 ${limits.channels} 声道（单声道）。")
        }
        if (bits != limits.bits) {
            throw UserWavException("位深是 $bits bit，只支持 ${limits.bits}-bit PCM。")
        }
        if (sampleRate != limits.sampleRate) {
            throw UserWavException("采样率是 $sampleRate Hz，只支持 ${limits.sampleRate} Hz。")
        }
        val payload = data ?: throw UserWavException("缺少 data 块。")
        if (payload.size % 2 != 0) throw UserWavException("data 块大小不是 16-bit 样本的整数倍。")
        val samples = FloatArray(payload.size / 2)
        val pcm = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        for (i in samples.indices) {
            samples[i] = pcm.short / 32768f
        }
        val durationMs = if (sampleRate <= 0) 0L else (samples.size * 1000L + sampleRate / 2) / sampleRate
        if (durationMs > limits.maxDurationMs) {
            throw UserWavException("时长约 ${durationMs} ms，超过 ${limits.maxDurationMs} ms 上限。")
        }
        return DecodedUserWav(
            sampleRate = sampleRate,
            samples = samples,
            durationMs = durationMs,
            byteSize = bytes.size,
        )
    }

    /**
     * 先按已知长度或上限读取，再解码。不关闭 [stream]，由调用方释放。
     */
    fun readLimited(stream: InputStream, knownLength: Long?, limits: WavLimits = DEFAULT_LIMITS): ByteArray {
        if (knownLength != null && knownLength >= 0) {
            if (knownLength < 44) throw UserWavException("文件过短（$knownLength 字节），不是完整的 WAV。")
            if (knownLength > limits.maxBytes) {
                throw UserWavException("文件过大（$knownLength 字节），超过 ${limits.maxBytes} 字节上限，已拒绝读入。")
            }
        }
        val cap = limits.maxBytes
        val out = ByteArray(cap + 1)
        var n = 0
        while (n < out.size) {
            val r = stream.read(out, n, out.size - n)
            if (r < 0) break
            n += r
        }
        if (n > cap) throw UserWavException("文件超过 ${limits.maxBytes} 字节上限，已停止读入。")
        return out.copyOf(n)
    }

    fun decodeStream(
        stream: InputStream,
        knownLength: Long? = null,
        limits: WavLimits = DEFAULT_LIMITS,
    ): DecodedUserWav = decode(readLimited(stream, knownLength, limits), limits)

    fun durationMs(sampleCount: Int, sampleRate: Int): Long =
        if (sampleRate <= 0) 0L else (sampleCount * 1000.0 / sampleRate).roundToLong()
}
