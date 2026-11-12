package com.spatialcoach.soundarchitecture.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.spatialcoach.soundarchitecture.audio.AnalysisEngine
import com.spatialcoach.soundarchitecture.audio.PcmWav
import com.spatialcoach.soundarchitecture.audio.Signals
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 默认演示：把合成音轨写成应用私有目录里的 wav，再按文件解码回放，
 * 同时把同一段 PCM 送进分析引擎。麦克风采集路径见 [ReservedMicSource]，默认不用。
 */
class FileDemoPlayer(
    context: Context,
    private val onFrame: (com.spatialcoach.soundarchitecture.audio.AnalysisFrame) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val demoFile = File(context.filesDir, "demo_tones.wav")
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var track: AudioTrack? = null

    val filePath: String get() = demoFile.absolutePath

    fun ensureDemoFile() {
        if (demoFile.exists() && demoFile.length() > 44) return
        val pcm = Signals.demoMelody(SAMPLE_RATE)
        PcmWav.writeMono16(demoFile.toPath(), pcm, SAMPLE_RATE)
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        ensureDemoFile()
        val (sampleRate, samples) = PcmWav.readMono16(demoFile.toPath())
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
        worker = thread(name = "file-demo-player", isDaemon = true) {
            val hop = 512
            val shortBuf = ShortArray(hop)
            var cursor = 0
            try {
                while (running.get()) {
                    if (cursor >= samples.size) cursor = 0
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
            } finally {
                runCatching { audioTrack.stop() }
                runCatching { audioTrack.release() }
                if (track === audioTrack) track = null
            }
        }
    }

    fun stop() {
        running.set(false)
        worker?.join(500)
        worker = null
        track = null
    }

    companion object {
        const val SAMPLE_RATE = 44_100
    }
}
