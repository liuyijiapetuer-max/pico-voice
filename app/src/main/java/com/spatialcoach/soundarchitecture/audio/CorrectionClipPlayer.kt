package com.spatialcoach.soundarchitecture.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 试听实验室夹具的修前/修后 PCM。只用于专业模式干/湿对照，不接麦克风，也不把回放标成真实演唱。
 */
class CorrectionClipPlayer(
    @Suppress("UNUSED_PARAMETER") context: Context,
    private val onFrame: (AnalysisFrame) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var track: AudioTrack? = null

    fun play(clip: PcmClip): Boolean {
        stop()
        if (!running.compareAndSet(false, true)) return false
        val sampleRate = clip.sampleRate
        val samples = clip.samples
        if (sampleRate <= 0 || samples.isEmpty()) {
            running.set(false)
            return false
        }
        val engine = AnalysisEngine(sampleRate = sampleRate, fftSize = 2048, hopSize = 512)
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf.coerceAtLeast(sampleRate / 5))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = audioTrack
        audioTrack.play()
        var completed = false
        val hop = 512
        val shortBuf = ShortArray(hop)
        try {
            var cursor = 0
            while (running.get() && cursor < samples.size) {
                val n = minOf(hop, samples.size - cursor)
                val slice = FloatArray(n)
                for (i in 0 until n) {
                    val s = samples[cursor + i]
                    slice[i] = s
                    shortBuf[i] = (s * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                }
                cursor += n
                audioTrack.write(shortBuf, 0, n)
                val frames = engine.push(slice)
                if (frames.isNotEmpty()) {
                    val latest = frames.last()
                    mainHandler.post { onFrame(latest) }
                }
            }
            completed = running.get() && cursor >= samples.size
        } finally {
            running.set(false)
            runCatching { audioTrack.stop() }
            runCatching { audioTrack.release() }
            if (track === audioTrack) track = null
        }
        return completed
    }

    fun stop() {
        running.set(false)
        val existing = track
        if (existing != null) {
            runCatching { existing.stop() }
            runCatching { existing.release() }
            track = null
        }
    }
}
