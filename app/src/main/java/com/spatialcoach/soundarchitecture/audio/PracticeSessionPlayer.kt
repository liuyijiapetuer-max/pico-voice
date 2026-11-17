package com.spatialcoach.soundarchitecture.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 一次播放内置合成练习片段，同时把同一段 PCM 送进分析引擎并收齐所有帧。
 * 默认输入仍是文件/合成回放，不接真麦。
 */
class PracticeSessionPlayer(
    @Suppress("UNUSED_PARAMETER") context: Context,
    private val onFrame: (AnalysisFrame) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var track: AudioTrack? = null

    /**
     * 同步播放并分析。若中途 [stop]，返回 null，不把半截当作一遍完整演奏。
     */
    fun play(rendition: PracticeFragments.Rendition): List<AnalysisFrame>? {
        stop()
        if (!running.compareAndSet(false, true)) return null
        val sampleRate = PracticeFragments.SAMPLE_RATE
        val samples = PracticeFragments.render(rendition, sampleRate)
        val engine = AnalysisEngine(sampleRate = sampleRate, fftSize = PassageAnalyzer.FFT_SIZE, hopSize = PassageAnalyzer.HOP_SIZE)
        val collected = ArrayList<AnalysisFrame>()
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
        val hop = PassageAnalyzer.HOP_SIZE
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
                    collected += frames
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
        return if (completed) collected else null
    }

    fun stop() {
        running.set(false)
        worker?.join(500)
        worker = null
        val existing = track
        if (existing != null) {
            runCatching { existing.stop() }
            runCatching { existing.release() }
            track = null
        }
    }
}
