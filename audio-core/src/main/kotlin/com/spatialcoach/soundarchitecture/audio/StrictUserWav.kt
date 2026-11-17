package com.spatialcoach.soundarchitecture.audio

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToLong

/**
 * 用户导入 WAV 的窄格式解码：只接受 RIFF/WAVE、PCM16、单声道、44100 Hz。
 * 不做重采样、不裁开头静音、不平移时间。
 * 先根据 data 块大小与帧率检查 20 s 时长上限，再读采样；3 MB 读入上限仍然保留，
 * 避免声明时长短但容器很大的文件占满内存。
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

internal data class WavHeaderInfo(
    val sampleRate: Int,
    val channels: Int,
    val bits: Int,
    val format: Int,
    val declaredByteRate: Int,
    val dataSize: Int,
    val bytesConsumed: Int,
)

object StrictUserWav {
    val DEFAULT_LIMITS = WavLimits()

    fun decode(
        bytes: ByteArray,
        limits: WavLimits = DEFAULT_LIMITS,
        cancelled: () -> Boolean = { false },
    ): DecodedUserWav {
        if (bytes.size > limits.maxBytes) {
            throw UserWavException("文件过大（${bytes.size} 字节），超过 ${limits.maxBytes} 字节上限。")
        }
        return decodeStream(bytes.inputStream(), bytes.size.toLong(), limits, cancelled)
    }

    /**
     * 先按已知长度做 3 MB 资源上限，再读头并按 data 大小/帧率检查时长，最后才读采样。
     * 不关闭 [stream]，由调用方释放。
     */
    fun readLimited(stream: InputStream, knownLength: Long?, limits: WavLimits = DEFAULT_LIMITS): ByteArray {
        checkKnownLength(knownLength, limits)
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
        cancelled: () -> Boolean = { false },
    ): DecodedUserWav {
        checkKnownLength(knownLength, limits)
        val header = readHeaderUntilData(stream, limits, cancelled)
        validateFormat(header, limits)
        val exactDurationMs = exactDurationMsFromDataSize(header.dataSize, header.sampleRate, header.channels, header.bits)
        if (exactDurationMs > limits.maxDurationMs) {
            throw UserWavException(
                "WAV 头声明 data ${header.dataSize} 字节，按时长约 ${exactDurationMs.roundToLong()} ms，超过 ${limits.maxDurationMs} ms 上限，未读入采样。",
            )
        }
        if (header.dataSize > limits.maxBytes) {
            throw UserWavException("data 块 ${header.dataSize} 字节超过 ${limits.maxBytes} 字节读入上限，未读入采样。")
        }
        checkCancelled(cancelled)
        val payload = readExact(stream, header.dataSize, limits, cancelled)
        if (payload.size < header.dataSize) {
            throw UserWavException("RIFF 块 data 声明 ${header.dataSize} 字节，实际读到 ${payload.size} 字节，文件被截断。")
        }
        return samplesFromPayload(payload, header, header.bytesConsumed + payload.size)
    }

    fun durationMs(sampleCount: Int, sampleRate: Int): Long =
        if (sampleRate <= 0) 0L else (sampleCount * 1000.0 / sampleRate).roundToLong()

    fun durationMsFromDataSize(dataSize: Int, sampleRate: Int, channels: Int, bits: Int): Long =
        exactDurationMsFromDataSize(dataSize, sampleRate, channels, bits).roundToLong()

    fun exactDurationMsFromDataSize(dataSize: Int, sampleRate: Int, channels: Int, bits: Int): Double {
        val frameBytes = frameBytes(channels, bits)
        if (sampleRate <= 0 || frameBytes <= 0) {
            throw UserWavException("WAV 头帧率或块对齐非法（rate=$sampleRate, channels=$channels, bits=$bits）。")
        }
        if (dataSize % frameBytes != 0) {
            throw UserWavException("data 块大小 $dataSize 不是一个完整帧（$frameBytes 字节）的整数倍。")
        }
        return dataSize.toDouble() * 1000.0 / (sampleRate.toDouble() * frameBytes)
    }

    private fun checkKnownLength(knownLength: Long?, limits: WavLimits) {
        if (knownLength == null || knownLength < 0) return
        if (knownLength < 44) throw UserWavException("文件过短（$knownLength 字节），不是完整的 WAV。")
        if (knownLength > limits.maxBytes) {
            throw UserWavException("文件过大（$knownLength 字节），超过 ${limits.maxBytes} 字节上限，已拒绝读入。")
        }
    }

    private fun readHeaderUntilData(
        stream: InputStream,
        limits: WavLimits,
        cancelled: () -> Boolean,
    ): WavHeaderInfo {
        var consumed = 0
        fun readExactOrThrow(n: Int, what: String): ByteArray {
            checkCancelled(cancelled)
            val buf = ByteArray(n)
            var off = 0
            while (off < n) {
                val r = stream.read(buf, off, n - off)
                if (r < 0) throw UserWavException("$what 不完整，文件被截断。")
                off += r
                consumed += r
                if (consumed > limits.maxBytes) {
                    throw UserWavException("读入已超过 ${limits.maxBytes} 字节上限，已停止。")
                }
            }
            return buf
        }
        fun tag() = String(readExactOrThrow(4, "RIFF 块标记"), Charsets.US_ASCII)
        fun leInt(what: String): Int =
            ByteBuffer.wrap(readExactOrThrow(4, what)).order(ByteOrder.LITTLE_ENDIAN).int
        fun leUShort(what: String): Int =
            ByteBuffer.wrap(readExactOrThrow(2, what)).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF

        if (tag() != "RIFF") throw UserWavException("不是 RIFF 文件。")
        leInt("RIFF 尺寸")
        if (tag() != "WAVE") throw UserWavException("不是 WAVE 文件。")

        var sampleRate = 0
        var bits = 0
        var channels = 0
        var format = 0
        var declaredByteRate = 0
        var sawFmt = false

        while (true) {
            checkCancelled(cancelled)
            val id = tag()
            val size = leInt("块 $id 大小")
            if (size < 0) throw UserWavException("RIFF 块大小非法（$id: $size）。")
            val padded = size + (size and 1)
            when (id) {
                "fmt " -> {
                    if (size < 16) throw UserWavException("fmt 块过短。")
                    format = leUShort("格式")
                    channels = leUShort("声道数").toShort().toInt()
                    // leUShort already unsigned; channels should be signed short originally - ok for 1
                    channels = channels
                    sampleRate = leInt("采样率")
                    declaredByteRate = leInt("字节率")
                    leUShort("块对齐")
                    bits = leUShort("位深")
                    val remain = padded - 16
                    if (remain > 0) readExactOrThrow(remain, "fmt 余部")
                    sawFmt = true
                }
                "data" -> {
                    if (!sawFmt) throw UserWavException("data 块出现在 fmt 块之前，文件头损坏。")
                    // 此刻只拿到 data 大小，不读采样。
                    return WavHeaderInfo(
                        sampleRate = sampleRate,
                        channels = channels,
                        bits = bits,
                        format = format,
                        declaredByteRate = declaredByteRate,
                        dataSize = size,
                        bytesConsumed = consumed,
                    )
                }
                else -> {
                    if (padded > limits.maxBytes) {
                        throw UserWavException("RIFF 块 $id 声明 $padded 字节，超过读入上限，已拒绝。")
                    }
                    if (padded > 0) readExactOrThrow(padded, "块 $id")
                }
            }
        }
    }

    private fun validateFormat(header: WavHeaderInfo, limits: WavLimits) {
        if (header.format != 1) throw UserWavException("音频格式是 ${header.format}，只支持 PCM（格式 1）。")
        if (header.channels != limits.channels) {
            throw UserWavException("声道数是 ${header.channels}，只支持 ${limits.channels} 声道（单声道）。")
        }
        if (header.bits != limits.bits) {
            throw UserWavException("位深是 ${header.bits} bit，只支持 ${limits.bits}-bit PCM。")
        }
        if (header.sampleRate != limits.sampleRate) {
            throw UserWavException("采样率是 ${header.sampleRate} Hz，只支持 ${limits.sampleRate} Hz。")
        }
        val expectedRate = header.sampleRate * frameBytes(header.channels, header.bits)
        if (header.declaredByteRate != 0 && header.declaredByteRate != expectedRate) {
            throw UserWavException(
                "WAV 头字节率 ${header.declaredByteRate} 与采样率×帧字节 $expectedRate 不一致，视为损坏头。",
            )
        }
    }

    private fun samplesFromPayload(payload: ByteArray, header: WavHeaderInfo, byteSize: Int): DecodedUserWav {
        val frameBytes = frameBytes(header.channels, header.bits)
        if (payload.size % frameBytes != 0) throw UserWavException("data 块大小不是 16-bit 样本的整数倍。")
        val samples = FloatArray(payload.size / 2)
        val pcm = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        for (i in samples.indices) {
            samples[i] = pcm.short / 32768f
        }
        val durationMs = durationMsFromDataSize(payload.size, header.sampleRate, header.channels, header.bits)
        return DecodedUserWav(
            sampleRate = header.sampleRate,
            samples = samples,
            durationMs = durationMs,
            byteSize = byteSize,
        )
    }

    private fun readExact(
        stream: InputStream,
        size: Int,
        limits: WavLimits,
        cancelled: () -> Boolean,
    ): ByteArray {
        val payload = ByteArray(size)
        var off = 0
        while (off < size) {
            checkCancelled(cancelled)
            val r = stream.read(payload, off, size - off)
            if (r < 0) break
            off += r
            if (off > limits.maxBytes) {
                throw UserWavException("读入已超过 ${limits.maxBytes} 字节上限，已停止。")
            }
        }
        return if (off == size) payload else payload.copyOf(off)
    }

    private fun frameBytes(channels: Int, bits: Int): Int {
        if (channels <= 0 || bits <= 0 || bits % 8 != 0) return 0
        return channels * (bits / 8)
    }

    private fun checkCancelled(cancelled: () -> Boolean) {
        if (cancelled()) throw UserWavException("导入已取消或已失效，已停止读入。")
    }
}
