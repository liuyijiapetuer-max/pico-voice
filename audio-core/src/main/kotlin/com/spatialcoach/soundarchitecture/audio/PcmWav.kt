package com.spatialcoach.soundarchitecture.audio

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

/** 单声道 16-bit PCM WAV，供文件回放和单测读写。 */
object PcmWav {
    fun encodeMono16(samples: FloatArray, sampleRate: Int): ByteArray {
        val dataSize = samples.size * 2
        val out = ByteArrayOutputStream(44 + dataSize)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(1) // mono
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)
        out.write(header.array())
        val pcm = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            val v = (s * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
            pcm.putShort(v)
        }
        out.write(pcm.array())
        return out.toByteArray()
    }

    fun writeMono16(path: Path, samples: FloatArray, sampleRate: Int) {
        Files.write(path, encodeMono16(samples, sampleRate))
    }

    fun decodeMono16(bytes: ByteArray): Pair<Int, FloatArray> {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun tag(): String {
            val b = ByteArray(4)
            buf.get(b)
            return String(b, Charsets.US_ASCII)
        }
        require(tag() == "RIFF") { "not a RIFF file" }
        buf.int
        require(tag() == "WAVE") { "not a WAVE file" }
        var sampleRate = 0
        var bits = 0
        var channels = 0
        var data: ByteArray? = null
        while (buf.remaining() >= 8) {
            val id = tag()
            val size = buf.int
            if (id == "fmt ") {
                val format = buf.short.toInt()
                channels = buf.short.toInt()
                sampleRate = buf.int
                buf.int
                buf.short
                bits = buf.short.toInt()
                if (size > 16) buf.position(buf.position() + (size - 16))
                require(format == 1 && channels == 1 && bits == 16) {
                    "only mono 16-bit PCM is supported"
                }
            } else if (id == "data") {
                data = ByteArray(size)
                buf.get(data)
            } else {
                buf.position(buf.position() + size)
            }
        }
        val payload = requireNotNull(data) { "missing data chunk" }
        val samples = FloatArray(payload.size / 2)
        val pcm = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        for (i in samples.indices) {
            samples[i] = pcm.short / 32768f
        }
        return sampleRate to samples
    }

    fun decodeMono16(stream: InputStream): Pair<Int, FloatArray> = decodeMono16(stream.readBytes())

    fun readMono16(path: Path): Pair<Int, FloatArray> = decodeMono16(Files.readAllBytes(path))
}
